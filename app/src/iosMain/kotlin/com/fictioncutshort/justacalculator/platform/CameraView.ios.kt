package com.fictioncutshort.justacalculator.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.delay
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.position
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSError
import platform.QuartzCore.CATransaction
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create

/**
 * AVFoundation counterpart to the CameraX session.
 *
 * Three outputs on one session, mirroring the three CameraX use-cases: a
 * preview layer, a video-data output feeding the scan grid, and a photo output
 * for the one auto-capture.
 *
 * iOS names photo-library assets itself, so `autoCaptureLabel` only decides
 * *whether* to capture here, not what the saved file is called.
 */

/** Holds the session across recompositions and lets `update` reach it. */
@OptIn(ExperimentalForeignApi::class)
private class CameraSession {
    val session = AVCaptureSession()
    var previewLayer: AVCaptureVideoPreviewLayer? = null
    var photoOutput: AVCapturePhotoOutput? = null
    var frameDelegate: FrameDelegate? = null
    var photoDelegate: PhotoDelegate? = null
    var boundFront: Boolean? = null
    var configured = false
}

/**
 * Samples one pixel per grid cell straight out of the BGRA pixel buffer.
 *
 * No intermediate image is built: this runs on the capture queue for every
 * frame, and allocating a full-size bitmap per frame to read a hundred-odd
 * pixels would cost more than everything else the preview does.
 */
@OptIn(ExperimentalForeignApi::class)
private class FrameDelegate(
    private val rows: Int,
    private val cols: Int,
    private val mirror: Boolean,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {

    var onSamples: ((IntArray) -> Unit)? = null
    var onFaceFrame: ((FaceFrame) -> Unit)? = null

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        val samples = onSamples
        val faces = onFaceFrame
        if (samples == null && faces == null) return
        val pixelBuffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return

        CVPixelBufferLockBaseAddress(pixelBuffer, 0u)
        try {
            val base = CVPixelBufferGetBaseAddress(pixelBuffer) ?: return
            val bytes = base.reinterpret<UByteVar>()
            val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
            val height = CVPixelBufferGetHeight(pixelBuffer).toInt()
            val stride = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
            if (width <= 0 || height <= 0) return

            if (samples != null && rows > 0 && cols > 0) {
                val out = IntArray(rows * cols)
                val cellW = width / cols
                val cellH = height / rows
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val px = (col * cellW + cellW / 2).coerceAtMost(width - 1)
                        val py = (row * cellH + cellH / 2).coerceAtMost(height - 1)
                        val offset = py * stride + px * 4
                        // 32BGRA: blue, green, red, alpha.
                        val b = bytes[offset].toInt()
                        val g = bytes[offset + 1].toInt()
                        val r = bytes[offset + 2].toInt()
                        out[row * cols + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                samples(out)
            }

            if (faces != null) {
                // The buffer is already upright — AVCaptureVideoDataOutput
                // delivers the connection's orientation — so only the front
                // camera's mirroring has to be applied, to both image and
                // coordinates, so they agree.
                val detected = detectFaces(pixelBuffer, width, height)
                val image = bgraToImageBitmap(bytes, width, height, stride, mirror)
                faces(
                    FaceFrame(
                        faces = detected
                            .map { if (mirror) it.mirroredIn(width) else it }
                            .sortedByDescending { it.area }
                            .take(MAX_FACES),
                        image = image,
                    )
                )
            }
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PhotoDelegate(private val label: String) : NSObject(),
    AVCapturePhotoCaptureDelegateProtocol {

    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        if (error != null) return
        val data = didFinishProcessingPhoto.fileDataRepresentation() ?: return

        // Two copies on purpose: the library one is for the player, the local
        // one is the only one the app can read back later.
        UIImageWriteToSavedPhotosAlbum(UIImage(data = data), null, null, null)
        CaptureStore.save("calculator_${label}_${nowMillis()}.jpg", data)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun cameraAt(front: Boolean): AVCaptureDevice? {
    val wanted = if (front) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
    return AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
        ?.filterIsInstance<AVCaptureDevice>()
        ?.firstOrNull { it.position == wanted }
        ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformCameraSurface(
    modifier: Modifier,
    useFrontCamera: Boolean,
    scanRows: Int,
    scanCols: Int,
    onScanSamples: ((IntArray) -> Unit)?,
    autoCaptureLabel: String?,
    onFaceFrame: ((FaceFrame) -> Unit)?,
) {
    val state = remember { CameraSession() }
    val samplesCallback by rememberUpdatedState(onScanSamples)
    val faceCallback by rememberUpdatedState(onFaceFrame)

    UIKitView(
        factory = {
            val container = UIView(frame = CGRectZero.readValue())
            container.clipsToBounds = true

            val layer = AVCaptureVideoPreviewLayer(session = state.session)
            layer.videoGravity = AVLayerVideoGravityResizeAspectFill
            container.layer.addSublayer(layer)
            state.previewLayer = layer
            container
        },
        modifier = modifier.fillMaxSize(),
        update = { container ->
            // The sublayer is not laid out by autoresizing, so it tracks the
            // view by hand — without this it stays zero-sized and the preview
            // never appears.
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            state.previewLayer?.setFrame(container.bounds)
            CATransaction.commit()

            state.frameDelegate?.onSamples = samplesCallback
            state.frameDelegate?.onFaceFrame = faceCallback
        },
    )

    // (Re-)bind the session whenever the requested camera changes.
    LaunchedEffect(useFrontCamera, scanRows, scanCols, onFaceFrame != null) {
        if (!hasPermission(AppInit.context, AppPermission.CAMERA)) return@LaunchedEffect
        if (state.boundFront == useFrontCamera) return@LaunchedEffect
        state.boundFront = useFrontCamera

        val session = state.session
        session.beginConfiguration()

        for (input in session.inputs.toList()) {
            (input as? AVCaptureInput)?.let { session.removeInput(it) }
        }
        val device = cameraAt(useFrontCamera)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }
        if (input != null && session.canAddInput(input)) session.addInput(input)

        if (!state.configured) {
            state.configured = true
            session.sessionPreset = AVCaptureSessionPresetHigh

            if ((scanRows > 0 && scanCols > 0) || onFaceFrame != null) {
                val videoOutput = AVCaptureVideoDataOutput()
                videoOutput.alwaysDiscardsLateVideoFrames = true
                // BGRA rather than the default YUV, so the sampler reads colour
                // components directly instead of converting a frame at a time.
                videoOutput.videoSettings = mapOf(
                    kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA,
                )
                val delegate = FrameDelegate(scanRows, scanCols, mirror = useFrontCamera)
                delegate.onSamples = samplesCallback
                delegate.onFaceFrame = faceCallback
                videoOutput.setSampleBufferDelegate(
                    delegate,
                    queue = dispatch_queue_create("camera.scan", null),
                )
                if (session.canAddOutput(videoOutput)) session.addOutput(videoOutput)
                state.frameDelegate = delegate
            }

            val photoOutput = AVCapturePhotoOutput()
            if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)
            state.photoOutput = photoOutput
        }

        session.commitConfiguration()
        if (!session.isRunning()) session.startRunning()
    }

    // Auto-take a photo 2 s after each binding (rear and then front).
    LaunchedEffect(useFrontCamera, autoCaptureLabel) {
        if (autoCaptureLabel == null) return@LaunchedEffect
        if (!hasPermission(AppInit.context, AppPermission.CAMERA)) return@LaunchedEffect
        delay(2000)
        val output = state.photoOutput ?: return@LaunchedEffect
        val delegate = PhotoDelegate(autoCaptureLabel)
        // Retained for the length of the capture; the output holds it weakly.
        state.photoDelegate = delegate
        runCatching {
            output.capturePhotoWithSettings(AVCapturePhotoSettings.photoSettings(), delegate)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (state.session.isRunning()) state.session.stopRunning()
            state.frameDelegate?.onSamples = null
            state.frameDelegate?.onFaceFrame = null
        }
    }
}

private const val MAX_FACES = 8

/** Reflects a face's geometry about the vertical centre line of a [width]-wide frame. */
private fun DetectedFace.mirroredIn(width: Int): DetectedFace {
    val w = width.toFloat()
    fun flip(p: FacePoint?) = p?.let { FacePoint(w - it.x, it.y) }
    return copy(
        left = w - right,
        right = w - left,
        leftEye = flip(leftEye),
        rightEye = flip(rightEye),
        // Mirroring reverses the sense of the tilt.
        rollDegrees = -rollDegrees,
    )
}

/**
 * Copies a BGRA buffer into an ImageBitmap, optionally mirrored.
 *
 * Skia's N32 pixels are little-endian BGRA on this platform, which is exactly
 * what the capture buffer already holds — so a mirrored frame is a per-row
 * reversal and an un-mirrored one is a straight copy, with no channel swap
 * either way. This full-frame copy is the expensive part of face mode, which is
 * why only the vanity room asks for it.
 */
@OptIn(ExperimentalForeignApi::class)
private fun bgraToImageBitmap(
    bytes: CPointer<UByteVar>,
    width: Int,
    height: Int,
    stride: Int,
    mirror: Boolean,
): ImageBitmap {
    val out = ByteArray(width * height * 4)
    for (y in 0 until height) {
        val rowStart = y * stride
        val destRow = y * width * 4
        for (x in 0 until width) {
            val src = rowStart + x * 4
            val dest = destRow + (if (mirror) width - 1 - x else x) * 4
            out[dest] = bytes[src].toByte()
            out[dest + 1] = bytes[src + 1].toByte()
            out[dest + 2] = bytes[src + 2].toByte()
            out[dest + 3] = 0xFF.toByte()
        }
    }

    val bitmap = Bitmap()
    bitmap.allocN32Pixels(width, height, opaque = true)
    bitmap.installPixels(out)
    return bitmap.asComposeImageBitmap()
}
