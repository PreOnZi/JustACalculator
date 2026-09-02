package com.fictioncutshort.justacalculator.platform

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A looping asset video, composited into the view hierarchy.
 *
 * Two things here are deliberate.
 *
 * The source is an AssetFileDescriptor, not `Assets.uri(...)`. MediaPlayer
 * cannot open a `file:///android_asset/` URI — that form is a WebView
 * convention — so pointing a VideoView at one failed with the platform's "Can't
 * play this video" dialog. Everything else that reads bundled media here
 * (Sound, VideoTexture) already goes through openFd; this was the one that
 * did not. Assets must stay uncompressed in the APK for that to work, which
 * aapt does for .mp4 by default.
 *
 * And it draws to a TextureView rather than a VideoView. VideoView is a
 * SurfaceView: it punches a hole through the window to a surface behind it. The
 * TukTak feed is an overlay above the city's GLSurfaceView, so that hole showed
 * the city straight through the middle of the feed. A TextureView composites
 * like any other view, so the overlay stays opaque.
 */
@Composable
actual fun PlatformVideoPlayer(assetPath: String, modifier: Modifier, muted: Boolean) {
    // Held so a later mute change reaches a player that is already prepared,
    // rather than only applying to the next one.
    val playerRef = remember { mutableStateOf<MediaPlayer?>(null) }
    val volume = if (muted) 0f else 1f

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture, width: Int, height: Int,
                    ) {
                        val mp = MediaPlayer()
                        val opened = runCatching {
                            ctx.assets.openFd(assetPath).use { fd ->
                                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                            }
                            mp.setSurface(Surface(surfaceTexture))
                            mp.isLooping = true
                            mp.setVolume(volume, volume)
                            mp.setOnPreparedListener { it.start() }
                            mp.prepareAsync()
                        }
                        if (opened.isSuccess) {
                            playerRef.value = mp
                        } else {
                            // A missing or undecodable clip leaves the card black
                            // rather than throwing out of a UI callback.
                            logWarn("VideoPlayer", "video open failed for $assetPath")
                            runCatching { mp.release() }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture, width: Int, height: Int,
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(
                        surfaceTexture: SurfaceTexture,
                    ): Boolean {
                        playerRef.value?.let { runCatching { it.release() } }
                        playerRef.value = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
        },
        update = { playerRef.value?.let { runCatching { it.setVolume(volume, volume) } } },
        onRelease = {
            playerRef.value?.let { runCatching { it.release() } }
            playerRef.value = null
        },
    )
}

actual fun savedSelfiePaths(max: Int): List<String> = localCapturePaths(max)
