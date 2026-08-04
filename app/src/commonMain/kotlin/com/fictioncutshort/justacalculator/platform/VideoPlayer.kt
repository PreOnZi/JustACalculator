package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A looping video from a bundled asset — the in-fiction social feed clips.
 * Android uses VideoView, iOS an AVPlayer layer.
 *
 * [muted] starts true so a feed can be scrolled in silence and only speaks when
 * the player asks it to, which is what both real apps and app-store review
 * expect. Autoplaying audio was the old behaviour and made the phone detour
 * unusable with several posts on screen at once.
 */
@Composable
expect fun PlatformVideoPlayer(
    assetPath: String,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
)

/**
 * Paths of selfies the player saved in Building 7's vanity room, newest first.
 *
 * Empty until the player has used the vanity room; callers fall back to a stock
 * image, so the feed still renders either way.
 */
expect fun savedSelfiePaths(max: Int = 2): List<String>
