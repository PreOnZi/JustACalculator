package com.fictioncutshort.justacalculator.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibration.kt
 *
 * Provides haptic feedback for button presses and game events.
 * Handles different Android versions which require different APIs.
 */

/**
 * Triggers a short vibration for haptic feedback.
 *
 * @param context Android context
 * @param durationMs How long to vibrate in milliseconds (default 10ms for button taps)
 * @param amplitude Vibration strength 1-255 (default 50 for subtle feedback)
 */
fun vibrate(context: Context, durationMs: Long = 10, amplitude: Int = 50) {
    // Get the vibrator service - API changed in Android 12 (S)
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Trigger vibration - amplitude control added in Android 8 (O)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                durationMs,
                amplitude.coerceIn(1, 255)  // Ensure amplitude is in valid range
            )
        )
    } else {
        // Old API doesn't support amplitude control
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}