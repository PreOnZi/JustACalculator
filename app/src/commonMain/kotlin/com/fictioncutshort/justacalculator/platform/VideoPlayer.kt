package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A looping, audible video from a bundled asset — the in-fiction social feed
 * clips. Android uses VideoView, iOS an AVPlayer layer.
 */
@Composable
expect fun PlatformVideoPlayer(assetPath: String, modifier: Modifier = Modifier)

/**
 * Paths of selfies the player saved in Building 7's vanity room, newest first.
 *
 * Empty on iOS until the camera room is ported — callers already fall back to
 * a stock image when there is no capture, so the feed still renders.
 */
expect fun savedSelfiePaths(max: Int = 2): List<String>
