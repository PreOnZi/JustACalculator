package com.fictioncutshort.justacalculator.platform

/**
 * The handful of JVM/Android primitives the shared game code used to reach for
 * directly. Each one has a thin Android actual that keeps the original
 * behaviour byte-for-byte, and an iOS actual built on Foundation.
 */

/** Wall-clock milliseconds since the Unix epoch — was `System.currentTimeMillis()`. */
expect fun nowMillis(): Long

/**
 * Scientific notation with [digits] digits after the point — was `String.format("%.4e", v)`.
 * The calculator display depends on this exact shape, so it stays platform-backed
 * rather than reimplemented by hand.
 */
expect fun formatScientific(value: Double, digits: Int): String

/** Fixed-point with [digits] decimals — was `String.format("%.10f", v)`. */
expect fun formatFixed(value: Double, digits: Int): String

/** Debug logging — was `android.util.Log.d`. */
expect fun logDebug(tag: String, message: String)

/** Warning logging — was `android.util.Log.w`. */
expect fun logWarn(tag: String, message: String)

/** Local wall-clock time of day, used by the step-163 time-based rant. */
data class TimeOfDay(val hour: Int, val minute: Int)

/** Was `java.util.Calendar.getInstance()` + HOUR_OF_DAY / MINUTE. */
expect fun currentTimeOfDay(): TimeOfDay
