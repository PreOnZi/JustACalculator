package com.fictioncutshort.justacalculator.gl

/**
 * A GL texture that something else keeps writing frames into — a camera feed or
 * a playing video.
 *
 * The two platforms get there by completely different routes. Android binds an
 * external-OES texture to a `SurfaceTexture` and the producer writes straight
 * into it. iOS has no such thing: frames arrive as `CVPixelBuffer`s and a
 * `CVOpenGLESTextureCache` wraps each one in an ordinary 2D texture.
 *
 * Three consequences leak through this interface, because hiding them would
 * mean copying every frame:
 *
 * 1. **[textureTarget] differs** — external-OES versus 2D — so the shader has
 *    to be built for it. See [videoSamplerPreamble] and [videoSamplerType].
 * 2. **[textureId] is only valid after [updateTexImage]**, and may change from
 *    frame to frame: the iOS texture cache hands back a fresh texture each
 *    time. Read it every frame; never cache it.
 * 3. **Everything here must run on the GL thread**, including creation.
 */
interface GlVideoTexture {
    /**
     * The texture name to bind. Re-read after every [updateTexImage] — see the
     * class docs. Zero before the first frame arrives.
     */
    val textureId: Int

    /** `GL_TEXTURE_EXTERNAL_OES` on Android, `GL_TEXTURE_2D` on iOS. */
    val textureTarget: Int

    /**
     * Pulls the newest frame into the texture, if one arrived.
     *
     * Returns false when there is nothing new, so the caller can skip the draw
     * — which is what the Android original used its dirty flags for.
     */
    fun updateTexImage(): Boolean

    /**
     * Writes the 4x4 texture-coordinate transform for the current frame into
     * [out]. Identity when the platform needs no correction.
     */
    fun getTransformMatrix(out: FloatArray)

    fun release()
}

/** A video file rendered into a texture, with its own audio. */
interface GlVideoSource : GlVideoTexture {
    fun play()
    fun pause()
    fun seekTo(positionMs: Int)
    fun setLooping(looping: Boolean)
    fun setVolume(volume: Float)
    val durationMs: Int
    val isPlaying: Boolean
}

/**
 * Lines to put at the top of a fragment shader sampling a [GlVideoTexture], and
 * the sampler type to declare. Android needs the `GL_OES_EGL_image_external`
 * extension; iOS samples an ordinary `sampler2D`.
 */
expect val videoSamplerPreamble: String
expect val videoSamplerType: String

/**
 * Opens the front or rear camera as a texture, or null if it is unavailable.
 *
 * Both cameras at once is what the door room asks for, and neither platform
 * guarantees it — Android needs concurrent-camera support, iOS a multi-cam
 * capable device — so callers must cope with the second one being null.
 */
expect fun createCameraTexture(front: Boolean): GlVideoTexture?

/** Opens a video from the shared asset tree as a texture, or null. */
expect fun createVideoTexture(assetPath: String): GlVideoSource?
