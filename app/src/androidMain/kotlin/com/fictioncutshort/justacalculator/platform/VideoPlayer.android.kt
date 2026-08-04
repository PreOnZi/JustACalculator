package com.fictioncutshort.justacalculator.platform

import android.net.Uri
import android.widget.VideoView
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun PlatformVideoPlayer(assetPath: String, modifier: Modifier, muted: Boolean) {
    // Held so a later mute change reaches a player that is already prepared,
    // rather than only applying to the next one.
    val playerRef = remember { mutableStateOf<MediaPlayer?>(null) }
    val volume = if (muted) 0f else 1f

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.parse(Assets.uri(assetPath)))
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    playerRef.value = mp
                    mp.setVolume(volume, volume)
                    start()
                }
            }
        },
        update = { playerRef.value?.setVolume(volume, volume) },
        onRelease = {
            playerRef.value = null
            it.stopPlayback()
        },
    )
}

actual fun savedSelfiePaths(max: Int): List<String> = localCapturePaths(max)
