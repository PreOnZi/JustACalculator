package com.fictioncutshort.justacalculator.platform

import java.util.Locale

actual fun nowMillis(): Long = System.currentTimeMillis()

// Locale.ROOT so the decimal separator never flips to a comma on e.g. a Czech
// or German device — the original code relied on the default locale, which was
// a latent display bug.
actual fun formatScientific(value: Double, digits: Int): String =
    String.format(Locale.ROOT, "%.${digits}e", value)

actual fun formatFixed(value: Double, digits: Int): String =
    String.format(Locale.ROOT, "%.${digits}f", value)

actual fun logDebug(tag: String, message: String) {
    android.util.Log.d(tag, message)
}

actual fun logWarn(tag: String, message: String) {
    android.util.Log.w(tag, message)
}

actual fun currentTimeOfDay(): TimeOfDay {
    val calendar = java.util.Calendar.getInstance()
    return TimeOfDay(
        hour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
        minute = calendar.get(java.util.Calendar.MINUTE),
    )
}
