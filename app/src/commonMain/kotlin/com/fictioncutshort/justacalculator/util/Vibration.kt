package com.fictioncutshort.justacalculator.util

import com.fictioncutshort.justacalculator.platform.AppContext
import com.fictioncutshort.justacalculator.platform.vibrateDevice

/**
 * Vibration.kt
 *
 * Provides haptic feedback for button presses and game events. The per-platform
 * detail (Android's VibratorManager/VibrationEffect vs iOS's discrete
 * UIImpactFeedbackGenerator styles) lives behind [vibrateDevice]; this keeps the
 * original call signature so every existing call site is unchanged.
 *
 * @param context Platform app context
 * @param durationMs How long to vibrate in milliseconds (default 10ms for button taps).
 *                   Ignored on iOS, which has no duration control.
 * @param amplitude Vibration strength 1-255 (default 50 for subtle feedback)
 */
fun vibrate(context: AppContext, durationMs: Long = 10, amplitude: Int = 50) {
    vibrateDevice(context, durationMs, amplitude)
}
