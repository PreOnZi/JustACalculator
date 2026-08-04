package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setVolume
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGRect

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformVideoPlayer(assetPath: String, modifier: Modifier, muted: Boolean) {
    val player = remember {
        val path = Assets.uri(assetPath).removePrefix("file://")
        AVPlayer(uRL = NSURL.fileURLWithPath(path))
    }

    // AVPlayer has no loop flag; rewinding on the end-of-item notification is
    // the standard equivalent of VideoView's isLooping.
    DisposableEffect(player) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification, null, null,
        ) { _ ->
            player.seekToTime(CMTimeMake(0, 1))
            player.play()
        }
        onDispose {
            player.pause()
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }

    LaunchedEffect(muted) { player.setVolume(if (muted) 0f else 1f) }

    UIKitView(
        factory = {
            // A CALayer does not resize with its host view, so the player layer
            // lives inside a UIView subclass that resizes it in layoutSubviews.
            PlayerHostView(AVPlayerLayer.playerLayerWithPlayer(player)).also {
                player.play()
            }
        },
        modifier = modifier,
    )
}

/** The camera room is not ported, so there are never any captures yet. */
actual fun savedSelfiePaths(max: Int): List<String> = CaptureStore.paths(max, suffix = VANITY_PREFIX)

/**
 * Keeps the AVPlayerLayer matched to the view's bounds. CALayer has no
 * autoresizing, and animating the bounds change would visibly lag the video
 * during rotation, so the implicit animation is disabled.
 */
@OptIn(ExperimentalForeignApi::class)
private class PlayerHostView(private val playerLayer: AVPlayerLayer) : UIView(frame = platform.CoreGraphics.CGRectZero.readValue()) {
    init {
        playerLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.setFrame(bounds)
        CATransaction.commit()
    }
}
