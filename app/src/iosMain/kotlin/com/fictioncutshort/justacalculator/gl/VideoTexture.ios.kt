package com.fictioncutshort.justacalculator.gl

import com.fictioncutshort.justacalculator.platform.AppInit
import com.fictioncutshort.justacalculator.platform.AppPermission
import com.fictioncutshort.justacalculator.platform.Assets
import com.fictioncutshort.justacalculator.platform.hasPermission
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureMultiCamSession
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
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
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
import platform.CoreVideo.CVBufferRelease
import platform.CoreVideo.CVOpenGLESTextureCacheFlush
import platform.CoreVideo.CVOpenGLESTextureCacheRef
import platform.CoreVideo.CVOpenGLESTextureCacheRefVar
import platform.CoreVideo.CVOpenGLESTextureRefVar
import platform.CoreVideo.CVOpenGLESTextureGetName
import platform.CoreVideo.CVOpenGLESTextureRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.posix.memcpy
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.CVPixelBufferGetPixelFormatType
import platform.CoreVideo.CVPixelBufferIsPlanar
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreImage.CIImage
import platform.CoreImage.CIContext
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferRetain
import platform.CoreVideo.kCVPixelBufferIOSurfacePropertiesKey
import platform.QuartzCore.CACurrentMediaTime
import platform.CoreVideo.kCVPixelBufferOpenGLESCompatibilityKey
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.EAGL.EAGLContext
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.darwin.DISPATCH_SOURCE_TYPE_TIMER
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_time
import platform.darwin.dispatch_resume
import platform.darwin.dispatch_source_cancel
import platform.darwin.dispatch_source_set_event_handler
import platform.darwin.dispatch_source_set_timer
import platform.darwin.dispatch_source_t
import platform.darwin.dispatch_source_create
import platform.gles3.GL_BGRA
import platform.gles3.GL_CLAMP_TO_EDGE
import platform.gles3.GL_LINEAR
import platform.gles3.GL_RGBA
import platform.gles3.GL_TEXTURE_2D
import platform.gles3.glTexImage2D
import platform.gles3.glGenTextures
import platform.gles3.GL_TEXTURE_MAG_FILTER
import platform.gles3.GL_TEXTURE_MIN_FILTER
import platform.gles3.GL_TEXTURE_WRAP_S
import platform.gles3.GL_TEXTURE_WRAP_T
import platform.gles3.GL_UNSIGNED_BYTE
import platform.gles3.glBindTexture
import platform.gles3.glTexParameteri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
/**
 * The V-flip a CoreVideo frame needs to sit the right way up.
 *
 * CVPixelBuffer rows run top-down; GL samples bottom-up. Android's
 * SurfaceTexture hands back a matrix that already carries this flip, and the
 * renderers multiply whatever they are given by their own TEX_FLIP_V — so on
 * Android the two cancel out and the picture is upright. Returning identity
 * here left exactly one flip standing, which is why iOS video played upside
 * down.
 */
private fun flipV(out: FloatArray) {
    Matrix.setIdentityM(out, 0)
    out[5] = -1f    // v -> -v
    out[13] = 1f    //   -> 1 - v
}

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

    private var cacheUnavailable = false
    private var fallbackTexture = 0
    /** Row-compaction scratch, kept between frames so the fallback does not allocate per frame. */
    private var staging: CPointer<UByteVar>? = null
    private var stagingSize = 0

    /**
     * Copies the pixel buffer into an ordinary GL texture.
     *
     * Slower than the zero-copy cache — this is a per-frame upload — but it
     * works anywhere, including the Simulator, where CVOpenGLESTextureCache is
     * simply not implemented. Used only after the cache has failed.
     */
    private fun uploadDirect(buffer: CVPixelBufferRef): Boolean {
        // Read-only, not 0: an IOSurface-backed buffer is only guaranteed to be
        // mapped for the CPU under the read-only flag. Locking with 0 can hand
        // back an address that is not the pixels at all.
        if (CVPixelBufferLockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly) != 0) return false
        try {
            // Both guards are load-bearing, not defensive padding. For a planar
            // buffer GetBaseAddress returns the *plane descriptor*, a structure
            // of a few kilobytes — uploading width*height*4 from it walks
            // straight off the end of the allocation, which is precisely how
            // this crashed on device (a ~2MB read past a 16KB region).
            if (CVPixelBufferIsPlanar(buffer)) return false
            if (CVPixelBufferGetPixelFormatType(buffer) != kCVPixelFormatType_32BGRA) return false

            val base = CVPixelBufferGetBaseAddress(buffer) ?: return false
            val width = CVPixelBufferGetWidth(buffer).toInt()
            val height = CVPixelBufferGetHeight(buffer).toInt()
            val stride = CVPixelBufferGetBytesPerRow(buffer).toInt()
            val tightStride = width * 4
            if (width <= 0 || height <= 0 || stride < tightStride) return false

            if (fallbackTexture == 0) {
                memScoped {
                    val ids = allocArray<UIntVar>(1)
                    glGenTextures(1, ids)
                    fallbackTexture = ids[0].toInt()
                }
                if (fallbackTexture == 0) return false
                glBindTexture(GL_TEXTURE_2D.toUInt(), fallbackTexture.toUInt())
                glTexParameteri(GL_TEXTURE_2D.toUInt(), GL_TEXTURE_MIN_FILTER.toUInt(), GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D.toUInt(), GL_TEXTURE_MAG_FILTER.toUInt(), GL_LINEAR)
                glTexParameteri(GL_TEXTURE_2D.toUInt(), GL_TEXTURE_WRAP_S.toUInt(), GL_CLAMP_TO_EDGE)
                glTexParameteri(GL_TEXTURE_2D.toUInt(), GL_TEXTURE_WRAP_T.toUInt(), GL_CLAMP_TO_EDGE)
            } else {
                glBindTexture(GL_TEXTURE_2D.toUInt(), fallbackTexture.toUInt())
            }

            // GL reads rows tightly packed; CoreVideo pads them to its own
            // alignment. Where those differ the rows have to be compacted first,
            // or every row after the first is read from the wrong offset.
            val src = base.reinterpret<UByteVar>()
            val source: CPointer<UByteVar> = if (stride == tightStride) {
                src
            } else {
                val needed = tightStride * height
                if (stagingSize != needed) {
                    staging?.let { nativeHeap.free(it.rawValue) }
                    staging = nativeHeap.allocArray<UByteVar>(needed)
                    stagingSize = needed
                }
                val dst = staging ?: return false
                for (y in 0 until height) {
                    // Arithmetic on the raw addresses: CPointer's plus operator
                    // is nullable and does not resolve cleanly for UByteVar here.
                    val to = interpretCPointer<UByteVar>(dst.rawValue + y.toLong() * tightStride)
                    val from = interpretCPointer<UByteVar>(src.rawValue + y.toLong() * stride)
                    if (to == null || from == null) return false
                    memcpy(to, from, tightStride.convert())
                }
                dst
            }

            // BGRA source; GL_BGRA is accepted as an external format on iOS via
            // APPLE_texture_format_BGRA8888, with GL_RGBA as the internal one.
            glTexImage2D(
                GL_TEXTURE_2D.toUInt(), 0, GL_RGBA,
                width, height, 0,
                GL_BGRA.toUInt(), GL_UNSIGNED_BYTE.toUInt(), source,
            )
            textureId = fallbackTexture
            return true
        } finally {
            CVPixelBufferUnlockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
        }
    }

    /** Must run on the GL thread — the cache is bound to the current context. */
    fun update(): Boolean {
        if (released) return false
        val buffer = pending ?: return false
        pending = null

        try {
            // The texture cache is unavailable on the Simulator and can fail on
            // device too; either way falling back to a plain upload keeps the
            // picture on screen rather than leaving a black panel.
            if (cacheUnavailable) return uploadDirect(buffer)

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
                    if (created != 0) {
                        cacheUnavailable = true
                        return uploadDirect(buffer)
                    }
                    cache = out.value
                }
            }
            val textureCache = cache ?: return false

            // The previous texture goes before a new one is made from the same
            // cache, or the cache holds the old IOSurface alive and stalls.
            //
            // CVBufferRelease, not CVPixelBufferRelease: this is a
            // CVOpenGLESTextureRef. Both are CFRelease underneath, but naming
            // the wrong one invites someone to "correct" the type later.
            texture?.let { CVBufferRelease(it) }
            texture = null

            // Flush AFTER dropping the old texture and BEFORE making the next,
            // which is the window where the cache can actually reclaim. Doing it
            // straight after creating a texture — as this briefly did — asks the
            // cache to reclaim the reference the caller is about to read.
            CVOpenGLESTextureCacheFlush(textureCache, 0u)

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
                if (status != 0) {
                    // One failure means this pipeline will not work at all here,
                    // so stop retrying it every frame and copy instead.
                    cacheUnavailable = true
                    return uploadDirect(buffer)
                }
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
        texture?.let { CVBufferRelease(it) }
        texture = null
        cache = null
        // The fallback's row-compaction scratch is malloc'd, so it does not go
        // away with the object.
        staging?.let { nativeHeap.free(it.rawValue) }
        staging = null
        stagingSize = 0
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
private class CameraWall(private val sink: PixelBufferTexture) : GlVideoTexture {
    override val textureId: Int get() = sink.textureId
    override val textureTarget: Int = Gl.GL_TEXTURE_2D
    override fun updateTexImage(): Boolean = sink.update()
    override fun getTransformMatrix(out: FloatArray) = identity(out)
    override fun release() = sink.release()
}

/**
 * Both cameras through AVFoundation.
 *
 * `AVCaptureMultiCamSession` is the only way to run two at once, and it is
 * unsupported on older hardware — [AVCaptureMultiCamSession.multiCamSupported]
 * decides. Where it is missing, one plain session alternates between the two
 * every few seconds, matching what Android does on devices without concurrent
 * camera support.
 */
@OptIn(ExperimentalForeignApi::class)
private class AVDualCameraTextures : DualCameraTextures {

    private val rearSink = PixelBufferTexture()
    private val frontSink = PixelBufferTexture()
    private val rearWall = CameraWall(rearSink)
    private val frontWall = CameraWall(frontSink)
    private val rearDelegate = CameraFrameDelegate(rearSink)
    private val frontDelegate = CameraFrameDelegate(frontSink)

    private var session: AVCaptureSession? = null
    private var alternateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    @Volatile private var rearLive = false
    @Volatile private var frontLive = false

    override val rear: GlVideoTexture get() = rearWall
    override val front: GlVideoTexture get() = frontWall
    override fun isLive(front: Boolean) = if (front) frontLive else rearLive

    private fun deviceAt(front: Boolean): AVCaptureDevice? {
        val wanted = if (front) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
        return AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
            ?.filterIsInstance<AVCaptureDevice>()
            ?.firstOrNull { it.position == wanted }
    }

    private fun makeOutput(delegate: CameraFrameDelegate, label: String) =
        AVCaptureVideoDataOutput().apply {
            alwaysDiscardsLateVideoFrames = true
            // BGRA is what the texture cache maps without a conversion pass.
            videoSettings = mapOf(kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA)
            setSampleBufferDelegate(delegate, queue = dispatch_queue_create(label, null))
        }

    fun start(): Boolean {
        if (AVCaptureMultiCamSession.isMultiCamSupported()) {
            val multi = AVCaptureMultiCamSession()
            multi.beginConfiguration()
            var ok = true
            for (front in listOf(false, true)) {
                val device = deviceAt(front) ?: run { ok = false; null } ?: break
                val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
                    ?: run { ok = false; null } ?: break
                val output = makeOutput(
                    if (front) frontDelegate else rearDelegate,
                    if (front) "door4.cam.front" else "door4.cam.rear",
                )
                if (!multi.canAddInput(input) || !multi.canAddOutput(output)) { ok = false; break }
                multi.addInput(input)
                multi.addOutput(output)
            }
            multi.commitConfiguration()
            if (ok) {
                session = multi
                multi.startRunning()
                rearLive = true
                frontLive = true
                return true
            }
        }

        // Single session, alternating.
        val single = AVCaptureSession()
        single.sessionPreset = AVCaptureSessionPresetHigh
        session = single
        alternateJob = scope.launch {
            var showRear = true
            while (true) {
                bind(single, front = !showRear)
                showRear = !showRear
                delay(5000)
            }
        }
        return true
    }

    private fun bind(session: AVCaptureSession, front: Boolean) {
        session.beginConfiguration()
        for (input in session.inputs.toList()) {
            (input as? AVCaptureInput)?.let { session.removeInput(it) }
        }
        for (output in session.outputs.toList()) {
            (output as? AVCaptureOutput)?.let { session.removeOutput(it) }
        }
        val device = deviceAt(front)
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }
        if (input != null && session.canAddInput(input)) session.addInput(input)
        val output = makeOutput(
            if (front) frontDelegate else rearDelegate,
            if (front) "door4.cam.front" else "door4.cam.rear",
        )
        if (session.canAddOutput(output)) session.addOutput(output)
        session.commitConfiguration()
        if (!session.isRunning()) session.startRunning()
        rearLive = !front
        frontLive = front
    }

    override fun release() {
        alternateJob?.cancel()
        alternateJob = null
        scope.cancel()
        session?.let { if (it.isRunning()) it.stopRunning() }
        session = null
        rearLive = false; frontLive = false
        rearSink.release()
        frontSink.release()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun createDualCameraTextures(): DualCameraTextures? {
    if (!hasPermission(AppInit.context, AppPermission.CAMERA)) return null
    val textures = AVDualCameraTextures()
    return if (textures.start()) textures else null
}

// ─────────────────────────────────────────────────────────────────────────────
// VIDEO
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private class VideoTexture(
    private val player: AVPlayer,
    private val output: AVPlayerItemVideoOutput,
    private val sink: PixelBufferTexture,
    override val videoWidth: Int,
    override val videoHeight: Int,
) : GlVideoSource {

    private var loopObserver: Any? = null

    // Pulling and converting run here, off the render thread.
    private val pumpQueue = dispatch_queue_create("video.pump", null)
    private var pumpTimer: dispatch_source_t = null
    private var releasedFlag = false

    // Conversion scratch, reused across frames.
    private var ciContext: CIContext? = null
    private var bgraBuffer: CVPixelBufferRef? = null
    private var bgraW = 0
    private var bgraH = 0

    override val textureId: Int get() = sink.textureId
    override val textureTarget: Int = Gl.GL_TEXTURE_2D
    override fun getTransformMatrix(out: FloatArray) = flipV(out)

    /**
     * Only uploads. Pulling and converting happen on [pumpQueue].
     *
     * This runs on the GL thread, which on iOS is the MAIN thread — GLKView
     * calls its delegate there. Anything expensive here freezes touch handling
     * and Compose along with it, which is exactly what happened when the format
     * conversion was attempted inline.
     */
    override fun updateTexImage(): Boolean = sink.update()

    /**
     * Fetches the next frame and hands it over as BGRA.
     *
     * The decoder on device delivers planar buffers whatever
     * pixelBufferAttributes asks for, and planar is a format neither the GL
     * texture cache nor a straight upload can take — which is why the wall
     * stayed blank while the audio played. CoreImage does the conversion
     * because it copes with every format a decoder might pick; the context and
     * the destination buffer are reused rather than rebuilt per frame.
     */
    private fun pumpOnce() {
        // Nothing may escape: this runs on a dispatch queue, and Kotlin/Native
        // terminates the process on an unhandled exception off the main thread.
        // A dropped frame is a far better outcome than a dead app.
        runCatching {
            if (releasedFlag) return
            val time = output.itemTimeForHostTime(CACurrentMediaTime())
            if (!output.hasNewPixelBufferForItemTime(time)) return
            val raw = output.copyPixelBufferForItemTime(time, null) ?: return
            try {
                val bgra = asBgra(raw) ?: return
                sink.offer(bgra)
            } finally {
                CVPixelBufferRelease(raw)
            }
        }
    }

    /** Returns [source] as interleaved BGRA, converting only when it is not already. */
    private fun asBgra(source: CVPixelBufferRef): CVPixelBufferRef? {
        if (!CVPixelBufferIsPlanar(source) &&
            CVPixelBufferGetPixelFormatType(source) == kCVPixelFormatType_32BGRA
        ) {
            return source
        }
        val width = CVPixelBufferGetWidth(source).toInt()
        val height = CVPixelBufferGetHeight(source).toInt()
        if (width <= 0 || height <= 0) return null

        if (bgraBuffer == null || bgraW != width || bgraH != height) {
            bgraBuffer?.let { CVPixelBufferRelease(it) }
            bgraBuffer = null
            memScoped {
                val out = alloc<CVPixelBufferRefVar>()
                val created = CVPixelBufferCreate(
                    allocator = null,
                    width = width.convert(),
                    height = height.convert(),
                    pixelFormatType = kCVPixelFormatType_32BGRA,
                    // No attributes dictionary. A Kotlin Map is not a
                    // CFDictionaryRef and casting one to it throws — which is
                    // what killed the pump queue, and an uncaught exception on
                    // a background queue takes the whole process with it.
                    // The default buffer is CPU-mappable, which is all the
                    // upload path needs.
                    pixelBufferAttributes = null,
                    pixelBufferOut = out.ptr,
                )
                if (created != 0) return null
                bgraBuffer = out.value
                bgraW = width
                bgraH = height
            }
        }
        val destination = bgraBuffer ?: return null
        val context = ciContext ?: CIContext.contextWithOptions(null).also { ciContext = it }
        context.render(CIImage.imageWithCVPixelBuffer(source), toCVPixelBuffer = destination)
        return destination
    }

    private fun startPump() {
        if (pumpTimer != null || releasedFlag) return
        val timer = dispatch_source_create(
            DISPATCH_SOURCE_TYPE_TIMER, 0u, 0u, pumpQueue,
        ) ?: return
        // 30Hz: the display link is capped at 30 too, so pulling faster only
        // converts frames nobody will draw.
        dispatch_source_set_timer(
            timer,
            dispatch_time(DISPATCH_TIME_NOW, 0),
            (1_000_000_000L / 30).toULong(),
            5_000_000u,
        )
        dispatch_source_set_event_handler(timer) { pumpOnce() }
        dispatch_resume(timer)
        pumpTimer = timer
    }

    private fun stopPump() {
        pumpTimer?.let { dispatch_source_cancel(it) }
        pumpTimer = null
    }

    override fun play() { player.play(); startPump() }
    override fun pause() { player.pause(); stopPump() }

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

    /**
     * No-op. Reverb on iOS means routing through AVAudioEngine, which would
     * mean giving up AVPlayer's own audio path for a background flourish.
     */
    override fun setReverb(enabled: Boolean) = Unit

    override fun release() {
        // Flag first: the pump may already be mid-frame on its own queue, and
        // it checks this before touching anything.
        releasedFlag = true
        stopPump()
        loopObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        loopObserver = null
        player.pause()
        bgraBuffer?.let { CVPixelBufferRelease(it) }
        bgraBuffer = null
        ciContext = null
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
            // Without this the decoder is free to hand back buffers that are not
            // IOSurface-backed, and CVOpenGLESTextureCacheCreateTextureFromImage
            // then fails for every frame. update() returns false, the texture id
            // stays 0, and the wall quad draws with nothing bound — while the
            // AVPlayer keeps playing the audio. That is exactly how building 4
            // looked on device: framed, audible, blank.
            kCVPixelBufferOpenGLESCompatibilityKey to true,
            kCVPixelBufferIOSurfacePropertiesKey to mapOf<Any?, Any?>(),
        ),
    )
    item.addOutput(output)
    val player = AVPlayer(playerItem = item)
    val (width, height) = videoSize(asset)
    return VideoTexture(player, output, PixelBufferTexture(), width, height)
}

/**
 * Display size of the first video track, with its preferred transform applied
 * — `naturalSize` alone ignores the rotation a portrait recording carries.
 */
@OptIn(ExperimentalForeignApi::class)
private fun videoSize(asset: AVURLAsset): Pair<Int, Int> {
    val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
        ?: return 1280 to 720
    val size = track.naturalSize.useContents { width to height }
    val transform = track.preferredTransform.useContents { floatArrayOf(a.toFloat(), b.toFloat(), c.toFloat(), d.toFloat()) }
    // A quarter-turn puts zeros on the diagonal and non-zeros off it.
    val rotated = transform[0] == 0f && transform[3] == 0f &&
        (transform[1] != 0f || transform[2] != 0f)
    val w = size.first.roundToInt()
    val h = size.second.roundToInt()
    return if (rotated) h to w else w to h
}
