package com.fictioncutshort.justacalculator.gl

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.fictioncutshort.justacalculator.platform.AppInit
import com.fictioncutshort.justacalculator.platform.AppPermission
import com.fictioncutshort.justacalculator.platform.hasPermission
import com.fictioncutshort.justacalculator.platform.logWarn
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

private class CameraTexture(
    private val provider: ProcessCameraProvider,
    private val useCase: Preview,
) : SurfaceTextureSource() {
    override fun release() {
        runCatching { provider.unbind(useCase) }
        super.release()
    }
}

actual fun createCameraTexture(front: Boolean): GlVideoTexture? {
    if (!hasPermission(AppInit.context, AppPermission.CAMERA)) return null
    return try {
        val provider = ProcessCameraProvider.getInstance(AppInit.context).get()
        val source = object : SurfaceTextureSource() {}
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request ->
                val surface = Surface(source.surfaceTexture)
                request.provideSurface(
                    surface,
                    ContextCompat.getMainExecutor(AppInit.context),
                ) { surface.release() }
            }
        }
        val selector = if (front) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        // The room wants both cameras at once, which not every device allows —
        // a failed bind means the caller gets null and draws that wall blank.
        provider.bindToLifecycle(ProcessLifecycleOwner.get(), selector, preview)
        CameraTexture(provider, preview)
    } catch (e: Exception) {
        logWarn("VideoTexture", "camera bind failed: ${e.message}")
        null
    }
}

private class MediaPlayerTexture(
    private val player: MediaPlayer,
) : SurfaceTextureSource(), GlVideoSource {

    override fun play() = player.start()
    override fun pause() = player.pause()
    override fun seekTo(positionMs: Int) = player.seekTo(positionMs)
    override fun setLooping(looping: Boolean) { player.isLooping = looping }
    override fun setVolume(volume: Float) = player.setVolume(volume, volume)
    override val durationMs: Int get() = runCatching { player.duration }.getOrDefault(0)
    override val isPlaying: Boolean get() = runCatching { player.isPlaying }.getOrDefault(false)

    override fun release() {
        runCatching { player.stop() }
        player.release()
        super.release()
    }
}

actual fun createVideoTexture(assetPath: String): GlVideoSource? = try {
    val player = MediaPlayer()
    AppInit.context.assets.openFd(assetPath).use { fd ->
        player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
    }
    val texture = MediaPlayerTexture(player)
    player.setSurface(Surface(texture.surfaceTexture))
    player.prepare()
    texture
} catch (e: Exception) {
    logWarn("VideoTexture", "video open failed for $assetPath: ${e.message}")
    null
}
