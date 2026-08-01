package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

@Composable
actual fun currentAppContext(): AppContext = IosAppContext

/**
 * iOS exposes discrete haptic styles rather than a duration/amplitude pair, so
 * the Android amplitude (1..255) is bucketed onto the closest impact style.
 * Duration has no analogue and is ignored.
 */
actual fun vibrateDevice(context: AppContext, durationMs: Long, amplitude: Int) {
    val style = when {
        amplitude <= 40 -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
        amplitude <= 120 -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
        else -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
    }
    val generator = UIImpactFeedbackGenerator(style)
    generator.prepare()
    generator.impactOccurred()
}
