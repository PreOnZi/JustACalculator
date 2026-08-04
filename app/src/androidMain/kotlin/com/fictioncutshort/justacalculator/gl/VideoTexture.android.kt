package com.fictioncutshort.justacalculator.gl

import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.PresetReverb
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.fictioncutshort.justacalculator.platform.AppInit
import com.fictioncutshort.justacalculator.platform.AppPermission
import com.fictioncutshort.justacalculator.platform.hasPermission
import com.fictioncutshort.justacalculator.platform.logWarn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/** External-OES textures fed by a SurfaceTexture, as the room always did. */

actual val videoSamplerPreamble: String = "#extension GL_OES_EGL_image_external : require\n"
actual val videoSamplerType: String = "samplerExternalOES"

/** Creates an external-OES texture with the filtering the room expects. */
private fun createExternalTexture(): Int {
    val ids = IntArray(1)
    GLES20.glGenTextures(1, ids, 0)
    val id = ids[0]
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR,
    )
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR,
    )
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE,
    )
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE,
    )
    return id
}

/** Shared half: an OES texture plus the SurfaceTexture writing into it. */
private open class SurfaceTextureSource : GlVideoTexture {

    protected val id = createExternalTexture()
    val surfaceTexture: SurfaceTexture = SurfaceTexture(id).also {
        it.setOnFrameAvailableListener { dirty = true }
    }

    @Volatile
    private var dirty = false

    override val textureId: Int get() = id
    override val textureTarget: Int = GLES11Ext.GL_TEXTURE_EXTERNAL_OES

    override fun updateTexImage(): Boolean {
        if (!dirty) return false
        dirty = false
        return runCatching { surfaceTexture.updateTexImage() }.isSuccess
    }

    override fun getTransformMatrix(out: FloatArray) {
        surfaceTexture.getTransformMatrix(out)
    }

    override fun release() {
        runCatching { surfaceTexture.release() }
        GLES20.glDeleteTextures(1, intArrayOf(id), 0)
    }
}

/**
 * Both cameras through CameraX.
 *
 * Prefers the concurrent-camera API. Where that is unsupported — most devices —
 * it alternates every few seconds, which is what the room has always done: two
 * walls that take turns being live reads as a glitching feed rather than a
 * broken one.
 */
private class CameraXDualTextures(
    private val provider: ProcessCameraProvider,
    private val rearSource: SurfaceTextureSource,
    private val frontSource: SurfaceTextureSource,
) : DualCameraTextures {

    override val rear: GlVideoTexture get() = rearSource
    override val front: GlVideoTexture get() = frontSource

    @Volatile private var rearLive = false
    @Volatile private var frontLive = false
    private var alternateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun isLive(front: Boolean) = if (front) frontLive else rearLive

    private fun previewFor(source: SurfaceTextureSource): Preview =
        Preview.Builder().build().also { preview ->
            preview.setSurfaceProvider { request ->
                source.surfaceTexture.setDefaultBufferSize(
                    request.resolution.width, request.resolution.height,
                )
                val surface = Surface(source.surfaceTexture)
                request.provideSurface(
                    surface, ContextCompat.getMainExecutor(AppInit.context),
                ) { surface.release() }
            }
        }

    fun start() {
        val owner = ProcessLifecycleOwner.get()
        provider.unbindAll()

        val concurrentSupported =
            runCatching { provider.availableConcurrentCameraInfos.isNotEmpty() }
                .getOrDefault(false)

        if (concurrentSupported) {
            val bound = runCatching {
                provider.bindToLifecycle(
                    listOf(
                        SingleCameraConfig(
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            UseCaseGroup.Builder().addUseCase(previewFor(rearSource)).build(),
                            owner,
                        ),
                        SingleCameraConfig(
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            UseCaseGroup.Builder().addUseCase(previewFor(frontSource)).build(),
                            owner,
                        ),
                    )
                )
            }.isSuccess
            if (bound) {
                rearLive = true
                frontLive = true
                return
            }
            provider.unbindAll()
        }

        alternateJob = scope.launch {
            var showRear = true
            while (true) {
                runCatching {
                    provider.unbindAll()
                    if (showRear) {
                        provider.bindToLifecycle(
                            owner, CameraSelector.DEFAULT_BACK_CAMERA, previewFor(rearSource),
                        )
                        rearLive = true; frontLive = false
                    } else {
                        provider.bindToLifecycle(
                            owner, CameraSelector.DEFAULT_FRONT_CAMERA, previewFor(frontSource),
                        )
                        rearLive = false; frontLive = true
                    }
                }
                showRear = !showRear
                delay(5000)
            }
        }
    }

    override fun release() {
        alternateJob?.cancel()
        alternateJob = null
        scope.cancel()
        runCatching { provider.unbindAll() }
        rearLive = false; frontLive = false
        rearSource.release()
        frontSource.release()
    }
}

actual fun createDualCameraTextures(): DualCameraTextures? {
    if (!hasPermission(AppInit.context, AppPermission.CAMERA)) return null
    return try {
        val provider = ProcessCameraProvider.getInstance(AppInit.context).get()
        CameraXDualTextures(
            provider,
            object : SurfaceTextureSource() {},
            object : SurfaceTextureSource() {},
        ).also { it.start() }
    } catch (e: Exception) {
        logWarn("VideoTexture", "camera bind failed: ${e.message}")
        null
    }
}

private class MediaPlayerTexture(
    private val player: MediaPlayer,
    override val videoWidth: Int,
    override val videoHeight: Int,
) : SurfaceTextureSource(), GlVideoSource {

    private var reverb: PresetReverb? = null

    override fun play() = player.start()
    override fun pause() = player.pause()
    override fun seekTo(positionMs: Int) = player.seekTo(positionMs)
    override fun setLooping(looping: Boolean) { player.isLooping = looping }
    override fun setVolume(volume: Float) = player.setVolume(volume, volume)
    override val durationMs: Int get() = runCatching { player.duration }.getOrDefault(0)
    override val isPlaying: Boolean get() = runCatching { player.isPlaying }.getOrDefault(false)

    override fun setReverb(enabled: Boolean) {
        if (!enabled) {
            reverb?.let { runCatching { it.release() } }
            reverb = null
            return
        }
        // Some devices have no PresetReverb at all; failing is fine.
        runCatching {
            val rv = PresetReverb(1, 0).apply {
                preset = PresetReverb.PRESET_LARGEHALL
                this.enabled = true
            }
            player.attachAuxEffect(rv.id)
            player.setAuxEffectSendLevel(0.7f)
            reverb = rv
        }
    }

    override fun release() {
        reverb?.let { runCatching { it.release() } }
        reverb = null
        runCatching { player.stop() }
        player.release()
        super.release()
    }
}

/** Display size from the file's metadata, with the rotation flag applied. */
private fun videoSize(assetPath: String): Pair<Int, Int> {
    var w = 1280
    var h = 720
    runCatching {
        val mmr = MediaMetadataRetriever()
        AppInit.context.assets.openFd(assetPath).use { fd ->
            mmr.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        }
        w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: w
        h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: h
        val rotation =
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation == 90 || rotation == 270) {
            val swap = w; w = h; h = swap
        }
        runCatching { mmr.release() }
    }
    return w to h
}

actual fun createVideoTexture(assetPath: String): GlVideoSource? = try {
    val player = MediaPlayer()
    AppInit.context.assets.openFd(assetPath).use { fd ->
        player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
    }
    val (width, height) = videoSize(assetPath)
    val texture = MediaPlayerTexture(player, width, height)
    player.setSurface(Surface(texture.surfaceTexture))
    player.prepare()
    texture
} catch (e: Exception) {
    logWarn("VideoTexture", "video open failed for $assetPath: ${e.message}")
    null
}
