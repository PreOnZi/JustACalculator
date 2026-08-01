package com.fictioncutshort.justacalculator.platform

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun currentAppContext(): AppContext = LocalContext.current

actual fun vibrateDevice(context: AppContext, durationMs: Long, amplitude: Int) {
    // Service lookup changed in Android 12 (S).
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager =
            context.getSystemService(AppContext.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(AppContext.VIBRATOR_SERVICE) as Vibrator
    }

    // Amplitude control arrived in Android 8 (O).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255))
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}
