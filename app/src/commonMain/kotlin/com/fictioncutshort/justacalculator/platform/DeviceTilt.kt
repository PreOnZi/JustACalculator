package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable

/**
 * Device tilt, normalised to -1f..1f over roughly ±30°, smoothed and dead-zoned
 * the same way on both platforms so the maze rolls identically.
 *
 * @param x left/right tilt (roll), @param y front/back tilt (pitch)
 */
data class DeviceTilt(val x: Float, val y: Float)

/**
 * Streams device tilt while composed. Returns zero tilt on devices without a
 * suitable sensor — the maze falls back to on-screen controls, so callers must
 * check [isTiltAvailable] rather than assume motion is coming.
 */
@Composable
expect fun rememberDeviceTilt(): DeviceTilt

/** Whether this device can report tilt at all. */
expect fun isTiltAvailable(): Boolean
