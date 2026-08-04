package com.fictioncutshort.justacalculator.platform

import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun PlatformVideoPlayer(assetPath: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.parse(Assets.uri(assetPath)))
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(1f, 1f)
                    start()
                }
            }
        },
        onRelease = { it.stopPlayback() },
    )
}

actual fun savedSelfiePaths(max: Int): List<String> = localCapturePaths(max)
