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
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.position
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetPixelFormatType
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferIsPlanar
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSNumber
import com.fictioncutshort.justacalculator.platform.logWarn
import platform.Foundation.NSError
import platform.QuartzCore.CATransaction
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIView
import platform.UIKit.UIWindowScene
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

/**
 * The orientation the capture pipeline should hand frames over in.
 *
 * AVCaptureVideoDataOutput and AVCaptureVideoPreviewLayer both default to the
 * *sensor's* orientation, which is landscape on every iOS device regardless of
 * how it is being held. Nothing corrects for that automatically: the frames
 * simply arrive rotated, and everything downstream inherits the rotation —
 * Vision fails to find a face that is lying on its side, and any coordinates it
 * does return are in the rotated space, so overlays land turned and offset.
 */
@OptIn(ExperimentalForeignApi::class)
private fun currentVideoOrientation(): AVCaptureVideoOrientation {
    val scenes = UIApplication.sharedApplication.connectedScenes.mapNotNull { it as? UIWindowScene }
    // A background scene can report a stale value, so prefer the active one.
    val scene = scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?: scenes.firstOrNull()
    return when (scene?.interfaceOrientation) {
        UIInterfaceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
        UIInterfaceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeLeft
        UIInterfaceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeRight
        // Includes .unknown: portrait is the orientation the story is written
        // for, so it is the safer guess than leaving the sensor default.
        else -> AVCaptureVideoOrientationPortrait
    }
}

/**
 * Points both connections at the current orientation.
 *
 * Called after configuring the session and again on every recomposition, which
 * is what makes it survive a rotation — Compose recomposes when the window
 * resizes, and the connection has to be told again each time.
 */
@OptIn(ExperimentalForeignApi::class)
private fun applyVideoOrientation(state: CameraSession) {
    val orientation = currentVideoOrientation()
    state.videoOutput?.connectionWithMediaType(AVMediaTypeVideo)?.let { conn ->
        if (conn.isVideoOrientationSupported()) conn.videoOrientation = orientation
    }
    state.previewLayer?.connection?.let { conn ->
        if (conn.isVideoOrientationSupported()) conn.videoOrientation = orientation
    }
    // Mirroring is deliberately NOT set here. The front camera is mirrored in
    // software further down, on both the image and the face coordinates so the
    // two agree; letting the connection mirror as well would cancel out.
}

/**
 * Hosts the preview layer and keeps it the size of the view.
 *
 * A CALayer sublayer is not laid out by autoresizing, so it has to be resized
 * by hand — and doing that only from `update` is not enough: `update` runs on
 * recomposition, which can happen before the interop view has been given a
 * real size. The layer then keeps a zero frame and the preview never appears,
 * even though frames are arriving and the scan grid is clearly running off
 * them. layoutSubviews is the callback that is actually guaranteed to fire
 * once the view has bounds, and again whenever they change.
 *
 * MKMapView and the other interop views have no equivalent problem because
 * they *are* the interop view, and Compose sizes that itself.
 */
@OptIn(ExperimentalForeignApi::class)
private class PreviewContainer : UIView(frame = CGRectZero.readValue()) {

    var previewLayer: AVCaptureVideoPreviewLayer? = null

    fun syncPreviewFrame() {
        val layer = previewLayer ?: return
        // Implicit animations would make the preview slide into place on every
        // bounds change, including the first one from zero.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        layer.setFrame(bounds)
        CATransaction.commit()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        syncPreviewFrame()
    }
}

/** Holds the session across recompositions and lets `update` reach it. */
@OptIn(ExperimentalForeignApi::class)
private class CameraSession {
    val session = AVCaptureSession()
    var previewLayer: AVCaptureVideoPreviewLayer? = null
    var photoOutput: AVCapturePhotoOutput? = null
    var frameDelegate: FrameDelegate? = null
    var videoOutput: AVCaptureVideoDataOutput? = null
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
            // NOT `?: return`. CVPixelBufferGetBaseAddress is documented to return
            // NULL for a PLANAR buffer — the data lives behind the per-plane
            // accessors instead — so bailing here would drop every YUV frame
            // before the format branch below ever got to look at it.
            val bytes = CVPixelBufferGetBaseAddress(pixelBuffer)?.reinterpret<UByteVar>()
            val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
            val height = CVPixelBufferGetHeight(pixelBuffer).toInt()
            val stride = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
            if (width <= 0 || height <= 0) return
            // Take the frame in whatever format the camera actually hands over.
            // Asking for 32BGRA in videoSettings is not reliable from Kotlin —
            // the dictionary has to bridge to ObjC and, when it does not,
            // AVFoundation silently keeps its default biplanar YUV with no error
            // at all. Reading that as BGRA produced the smeared, skewed picture
            // with a green band; refusing it produced no picture. So: handle
            // both, and let the format decide which reader runs.
            val format = CVPixelBufferGetPixelFormatType(pixelBuffer)
            val planar = CVPixelBufferIsPlanar(pixelBuffer)
            // Interleaved formats genuinely need that base pointer.
            if (!planar && bytes == null) return
            if (!warnedAboutFormat) {
                warnedAboutFormat = true
                logWarn("Camera", "frame format=$format planar=$planar ${width}x$height")
            }

            if (samples != null && rows > 0 && cols > 0) {
                val out = IntArray(rows * cols)
                val cellW = width / cols
                val cellH = height / rows
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val px = (col * cellW + cellW / 2).coerceAtMost(width - 1)
                        val py = (row * cellH + cellH / 2).coerceAtMost(height - 1)
                        val rgb = if (planar) {
                            yuvPixel(pixelBuffer, px, py, format)
                        } else {
                            val offset = py * stride + px * 4
                            // 32BGRA: blue, green, red, alpha.
                            val b = bytes!![offset].toInt()
                            val g = bytes[offset + 1].toInt()
                            val r = bytes[offset + 2].toInt()
                            (r shl 16) or (g shl 8) or b
                        }
                        out[row * cols + col] = (0xFF shl 24) or rgb
                    }
                }
                samples(out)
            }

            if (faces != null) {
                // Upright because the connection was told to be (see
                // applyVideoOrientation) — NOT because the output does it on
                // its own; it hands over the sensor's landscape orientation
                // unless asked otherwise. Only the front camera's mirroring is
                // applied here, to both image and coordinates so they agree.
                val detected = detectFaces(pixelBuffer, width, height)
                val image = if (planar) {
                    biplanarToImageBitmap(pixelBuffer, width, height, format, mirror)
                } else {
                    bgraToImageBitmap(bytes!!, width, height, stride, mirror)
                }
                // A planar buffer whose planes could not be mapped: skip this
                // frame rather than publish a face frame with no picture in it.
                if (image == null) return
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
            val container = PreviewContainer()
            container.clipsToBounds = true

            val layer = AVCaptureVideoPreviewLayer(session = state.session)
            layer.videoGravity = AVLayerVideoGravityResizeAspectFill
            container.layer.addSublayer(layer)
            container.previewLayer = layer
            state.previewLayer = layer
            container
        },
        modifier = modifier.fillMaxSize(),
        update = { container ->
            // Belt and braces alongside PreviewContainer.layoutSubviews: this
            // runs on recomposition, that one runs on layout, and only the
            // latter is guaranteed to happen after the view has a real size.
            container.syncPreviewFrame()

            state.frameDelegate?.onSamples = samplesCallback
            state.frameDelegate?.onFaceFrame = faceCallback
            applyVideoOrientation(state)
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
                // The value has to be an NSNumber. kCVPixelFormatType_32BGRA is a
                // raw OSType (UInt); handing that straight to an ObjC dictionary
                // does not necessarily bridge, and AVFoundation answers an
                // unreadable videoSettings by silently keeping its DEFAULT format
                // — biplanar YUV. The frame reader below assumes 4-byte BGRA, so
                // that shows up as a smeared, diagonally-skewed picture with a
                // green/magenta band where the chroma plane starts.
                videoOutput.videoSettings = mapOf(
                    kCVPixelBufferPixelFormatTypeKey to
                        NSNumber(unsignedInt = kCVPixelFormatType_32BGRA),
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
                state.videoOutput = videoOutput
            }

            val photoOutput = AVCapturePhotoOutput()
            if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)
            state.photoOutput = photoOutput
        }

        session.commitConfiguration()
        // After commit: the connections only exist once the outputs are added.
        applyVideoOrientation(state)
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
 * BT.601 YCbCr -> RGB for the two biplanar formats AVFoundation hands out by
 * default ('420v' video-range and '420f' full-range).
 *
 * Plane 0 is luma at full resolution; plane 1 is Cb/Cr interleaved at half
 * resolution in both axes, which is why the chroma index halves x and y. Each
 * plane carries its OWN stride — they are not the same number, and using
 * CVPixelBufferGetBytesPerRow (which reports plane 0's) for both is a classic
 * way to get a skewed picture.
 */
private fun yuvToRgb(yy: Int, cb: Int, cr: Int, videoRange: Boolean): Int {
    // Video range packs luma into 16..235; full range uses the whole byte.
    val y = if (videoRange) ((yy - 16).coerceAtLeast(0) * 255) / 219 else yy
    val u = cb - 128
    val v = cr - 128
    val r = (y + 1.402f * v).toInt().coerceIn(0, 255)
    val g = (y - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
    val b = (y + 1.772f * u).toInt().coerceIn(0, 255)
    return (r shl 16) or (g shl 8) or b
}

private fun isVideoRange(format: UInt): Boolean =
    format == kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange

/** One pixel out of a biplanar buffer, as 0xRRGGBB. */
@OptIn(ExperimentalForeignApi::class)
private fun yuvPixel(pb: CVPixelBufferRef?, x: Int, y: Int, format: UInt): Int {
    val yBase = CVPixelBufferGetBaseAddressOfPlane(pb, 0u)?.reinterpret<UByteVar>() ?: return 0
    val cBase = CVPixelBufferGetBaseAddressOfPlane(pb, 1u)?.reinterpret<UByteVar>() ?: return 0
    val yStride = CVPixelBufferGetBytesPerRowOfPlane(pb, 0u).toInt()
    val cStride = CVPixelBufferGetBytesPerRowOfPlane(pb, 1u).toInt()
    val luma = yBase[y * yStride + x].toInt()
    val ci = (y / 2) * cStride + (x / 2) * 2
    return yuvToRgb(luma, cBase[ci].toInt(), cBase[ci + 1].toInt(), isVideoRange(format))
}

/** Whole biplanar frame -> ImageBitmap, optionally mirrored. */
@OptIn(ExperimentalForeignApi::class)
private fun biplanarToImageBitmap(
    pb: CVPixelBufferRef?,
    width: Int,
    height: Int,
    format: UInt,
    mirror: Boolean,
): ImageBitmap? {
    val yBase = CVPixelBufferGetBaseAddressOfPlane(pb, 0u)?.reinterpret<UByteVar>() ?: return null
    val cBase = CVPixelBufferGetBaseAddressOfPlane(pb, 1u)?.reinterpret<UByteVar>() ?: return null
    val yStride = CVPixelBufferGetBytesPerRowOfPlane(pb, 0u).toInt()
    val cStride = CVPixelBufferGetBytesPerRowOfPlane(pb, 1u).toInt()
    val videoRange = isVideoRange(format)

    val out = ByteArray(width * height * 4)
    for (y in 0 until height) {
        val yRow = y * yStride
        val cRow = (y / 2) * cStride
        val destRow = y * width * 4
        for (x in 0 until width) {
            val ci = cRow + (x / 2) * 2
            val rgb = yuvToRgb(
                yBase[yRow + x].toInt(), cBase[ci].toInt(), cBase[ci + 1].toInt(), videoRange,
            )
            val dest = destRow + (if (mirror) width - 1 - x else x) * 4
            // Skia N32 is BGRA on this platform — same order bgraToImageBitmap writes.
            out[dest] = (rgb and 0xFF).toByte()
            out[dest + 1] = ((rgb shr 8) and 0xFF).toByte()
            out[dest + 2] = ((rgb shr 16) and 0xFF).toByte()
            out[dest + 3] = 0xFF.toByte()
        }
    }

    val bitmap = Bitmap()
    bitmap.allocN32Pixels(width, height, opaque = true)
    bitmap.installPixels(out)
    return bitmap.asComposeImageBitmap()
}

/** One-shot guard so a bad pixel format logs once, not once per frame. */
private var warnedAboutFormat = false

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
