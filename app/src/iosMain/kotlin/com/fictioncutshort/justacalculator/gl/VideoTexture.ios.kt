package com.fictioncutshort.justacalculator.gl

import com.fictioncutshort.justacalculator.platform.AppInit
import com.fictioncutshort.justacalculator.platform.AppPermission
import com.fictioncutshort.justacalculator.platform.Assets
import com.fictioncutshort.justacalculator.platform.hasPermission
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemVideoOutput
import platform.AVFoundation.addOutput
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setVolume
import platform.AVFoundation.position
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreVideo.CVOpenGLESTextureCacheCreate
import platform.CoreVideo.CVOpenGLESTextureCacheCreateTextureFromImage
import platform.CoreVideo.CVOpenGLESTextureCacheFlush
import platform.CoreVideo.CVOpenGLESTextureCacheRef
import platform.CoreVideo.CVOpenGLESTextureCacheRefVar
import platform.CoreVideo.CVOpenGLESTextureRefVar
import platform.CoreVideo.CVOpenGLESTextureGetName
import platform.CoreVideo.CVOpenGLESTextureRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferRetain
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.EAGL.EAGLContext
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.gles3.GL_BGRA
import platform.gles3.GL_CLAMP_TO_EDGE
import platform.gles3.GL_LINEAR
import platform.gles3.GL_RGBA
import platform.gles3.GL_TEXTURE_2D
import platform.gles3.GL_TEXTURE_MAG_FILTER
import platform.gles3.GL_TEXTURE_MIN_FILTER
import platform.gles3.GL_TEXTURE_WRAP_S
import platform.gles3.GL_TEXTURE_WRAP_T
import platform.gles3.GL_UNSIGNED_BYTE
import platform.gles3.glBindTexture
import platform.gles3.glTexParameteri
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt

/**
 * `CVOpenGLESTextureCache` in place of Android's external-OES textures.
 *
 * The cache maps a `CVPixelBuffer` onto a GL texture with no copy, which is the
 * only way to get camera and video frames onto geometry at frame rate. Frames
 * come from an `AVCaptureVideoDataOutput` for the camera and an
 * `AVPlayerItemVideoOutput` for video — different producers, same conversion.
 */

actual val videoSamplerPreamble: String = ""
actual val videoSamplerType: String = "sampler2D"

@OptIn(ExperimentalForeignApi::class)
private fun identity(out: FloatArray) {
    Matrix.setIdentityM(out, 0)
}

/**
 * Shared conversion half: holds the newest pixel buffer and turns it into a GL
 * texture on demand.
 *
 * The pixel buffer is retained on arrival and released when replaced —
 * otherwise the producer recycles it out from under the GL thread, which shows
 * up as torn or black frames rather than a crash.
 */
@OptIn(ExperimentalForeignApi::class)
private class PixelBufferTexture {

    private var cache: CVOpenGLESTextureCacheRef? = null
    private var texture: CVOpenGLESTextureRef? = null

    @Volatile
    private var pending: CVPixelBufferRef? = null

    @Volatile
    private var released = false

    var textureId: Int = 0
        private set

    fun offer(buffer: CVPixelBufferRef?) {
        if (released || buffer == null) return
        val retained = CVPixelBufferRetain(buffer)
        val previous = pending
        pending = retained
        if (previous != null) CVPixelBufferRelease(previous)
    }

    /** Must run on the GL thread — the cache is bound to the current context. */
    fun update(): Boolean {
        if (released) return false
        val buffer = pending ?: return false
        pending = null

        try {
            if (cache == null) {
                val ctx = EAGLContext.currentContext() ?: return false
                // CoreVideo's header only forward-declares EAGLContext, so the
                // binding types the parameter as an opaque objcnames class.
                @Suppress("CAST_NEVER_SUCCEEDS")
                val opaqueCtx = ctx as objcnames.classes.EAGLContext
                memScoped {
                    val out = alloc<CVOpenGLESTextureCacheRefVar>()
                    val created = CVOpenGLESTextureCacheCreate(
                        allocator = null,
                        cacheAttributes = null,
                        eaglContext = opaqueCtx,
                        textureAttributes = null,
                        cacheOut = out.ptr,
                    )
                    if (created != 0) return false
                    cache = out.value
                }
            }
            val textureCache = cache ?: return false

            // The previous texture must go before a new one is made from the
            // same cache, or the cache holds the old IOSurface alive and stalls.
            texture?.let { CVPixelBufferRelease(it) }
            texture = null

            val width = CVPixelBufferGetWidth(buffer).toInt()
            val height = CVPixelBufferGetHeight(buffer).toInt()

            memScoped {
                val out = alloc<CVOpenGLESTextureRefVar>()
                val status = CVOpenGLESTextureCacheCreateTextureFromImage(
                    allocator = null,
                    textureCache = textureCache,
                    sourceImage = buffer,
                    textureAttributes = null,
                    target = GL_TEXTURE_2D.toUInt(),
                    internalFormat = GL_RGBA,
                    width = width,
                    height = height,
                    format = GL_BGRA.toUInt(),
                    type = GL_UNSIGNED_BYTE.toUInt(),
                    planeIndex = 0u,
                    textureOut = out.ptr,
                )
                if (status != 0) return false
                texture = out.value
            }

            val name = texture?.let { CVOpenGLESTextureGetName(it) }?.toInt() ?: return false
            textureId = name
            glBindTexture(GL_TEXTURE_2D.toUInt(), name.toUInt())
            glTexParameteri(GL_TEXTURE_2D.toUInt(), GL_TEXTURE_MIN_FILTER.toUInt(), GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D.toUInt(), GL_TEXTURE_MAG_FILTER.toUInt(), GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D.toUInt(), GL_TEXTURE_WRAP_S.toUInt(), GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D.toUInt(), GL_TEXTURE_WRAP_T.toUInt(), GL_CLAMP_TO_EDGE)
            CVOpenGLESTextureCacheFlush(textureCache, 0u)
            return true
        } finally {
            CVPixelBufferRelease(buffer)
        }
    }

    fun release() {
        released = true
        pending?.let { CVPixelBufferRelease(it) }
        pending = null
        texture?.let { CVPixelBufferRelease(it) }
        texture = null
        cache = null
        textureId = 0
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CAMERA
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private class CameraFrameDelegate(
    private val sink: PixelBufferTexture,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        sink.offer(CMSampleBufferGetImageBuffer(didOutputSampleBuffer))
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CameraTexture(
    private val session: AVCaptureSession,
    private val sink: PixelBufferTexture,
    private val delegate: CameraFrameDelegate,
) : GlVideoTexture {

    override val textureId: Int get() = sink.textureId
    override val textureTarget: Int = Gl.GL_TEXTURE_2D
    override fun updateTexImage(): Boolean = sink.update()
    override fun getTransformMatrix(out: FloatArray) = identity(out)

    override fun release() {
        if (session.isRunning()) session.stopRunning()
        sink.release()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun createCameraTexture(front: Boolean): GlVideoTexture? {
    if (!hasPermission(AppInit.context, AppPermission.CAMERA)) return null

    val wanted = if (front) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
    val device = AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
        ?.filterIsInstance<AVCaptureDevice>()
        ?.firstOrNull { it.position == wanted }
        ?: return null

    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return null
    val session = AVCaptureSession()
    session.beginConfiguration()
    session.sessionPreset = AVCaptureSessionPresetHigh
    if (!session.canAddInput(input)) {
        session.commitConfiguration()
        return null
    }
    session.addInput(input)

    val sink = PixelBufferTexture()
    val delegate = CameraFrameDelegate(sink)
    val output = AVCaptureVideoDataOutput()
    output.alwaysDiscardsLateVideoFrames = true
    // BGRA is what the texture cache maps without a conversion pass.
    output.videoSettings = mapOf(
        kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA,
    )
    output.setSampleBufferDelegate(
        delegate,
        queue = dispatch_queue_create(if (front) "door4.cam.front" else "door4.cam.rear", null),
    )
    if (!session.canAddOutput(output)) {
        session.commitConfiguration()
        return null
    }
    session.addOutput(output)
    session.commitConfiguration()
    session.startRunning()

    return CameraTexture(session, sink, delegate)
}

// ─────────────────────────────────────────────────────────────────────────────
// VIDEO
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private class VideoTexture(
    private val player: AVPlayer,
    private val output: AVPlayerItemVideoOutput,
    private val sink: PixelBufferTexture,
) : GlVideoSource {

    private var loopObserver: Any? = null

    override val textureId: Int get() = sink.textureId
    override val textureTarget: Int = Gl.GL_TEXTURE_2D
    override fun getTransformMatrix(out: FloatArray) = identity(out)

    /**
     * Pulls at the item's current time rather than being pushed frames, which
     * is how AVPlayerItemVideoOutput works — so unlike the camera there is
     * nothing to retain between calls.
     */
    override fun updateTexImage(): Boolean {
        val time = player.currentTime()
        if (!output.hasNewPixelBufferForItemTime(time)) return false
        val buffer = output.copyPixelBufferForItemTime(time, null) ?: return false
        sink.offer(buffer)
        // offer retains, so the copy's own reference goes here.
        CVPixelBufferRelease(buffer)
        return sink.update()
    }

    override fun play() { player.play() }
    override fun pause() { player.pause() }

    override fun seekTo(positionMs: Int) {
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 600))
    }

    override fun setLooping(looping: Boolean) {
        // AVPlayer has no loop flag; rewinding on end-of-item is the equivalent.
        loopObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        loopObserver = if (!looping) null else {
            NSNotificationCenter.defaultCenter.addObserverForName(
                AVPlayerItemDidPlayToEndTimeNotification, player.currentItem, null,
            ) { _ ->
                player.seekToTime(CMTimeMakeWithSeconds(0.0, 600))
                player.play()
            }
        }
    }

    override fun setVolume(volume: Float) { player.setVolume(volume) }

    override val durationMs: Int
        get() {
            val seconds = player.currentItem?.duration?.let { CMTimeGetSeconds(it) } ?: 0.0
            return if (seconds.isNaN() || seconds < 0) 0 else (seconds * 1000).roundToInt()
        }

    override val isPlaying: Boolean get() = player.rate != 0f

    override fun release() {
        loopObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        loopObserver = null
        player.pause()
        sink.release()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun createVideoTexture(assetPath: String): GlVideoSource? {
    val path = Assets.uri(assetPath).removePrefix("file://")
    val asset = AVURLAsset(NSURL.fileURLWithPath(path), null)
    val item = AVPlayerItem(asset)
    val output = AVPlayerItemVideoOutput(
        pixelBufferAttributes = mapOf(
            kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA,
        ),
    )
    item.addOutput(output)
    val player = AVPlayer(playerItem = item)
    return VideoTexture(player, output, PixelBufferTexture())
}
