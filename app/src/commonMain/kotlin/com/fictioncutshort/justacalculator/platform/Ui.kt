package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * The Compose-side platform seams the UI reaches for again and again:
 * a context to hand to the stores, screen metrics, and haptics.
 */

/**
 * The ambient [AppContext] — replaces `LocalContext.current`, which is
 * Android-only. On Android it resolves to the real Context; on iOS to the
 * process-wide singleton.
 */
@Composable
expect fun currentAppContext(): AppContext

/**
 * Screen metrics that used to come from `LocalConfiguration.current`
 * (`orientation`, `screenWidthDp`, `screenHeightDp`).
 */
data class ScreenMetrics(
    val widthDp: Dp,
    val heightDp: Dp,
    val isLandscape: Boolean,
)

@Composable
fun screenMetrics(): ScreenMetrics {
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    val width = with(density) { size.width.toDp() }
    val height = with(density) { size.height.toDp() }
    return ScreenMetrics(widthDp = width, heightDp = height, isLandscape = width > height)
}

/**
 * A short haptic tick. [amplitude] is the Android 1..255 scale; iOS has no
 * continuous strength control, so it is bucketed onto the closest UIFeedback
 * generator style.
 */
expect fun vibrateDevice(context: AppContext, durationMs: Long = 10, amplitude: Int = 50)
