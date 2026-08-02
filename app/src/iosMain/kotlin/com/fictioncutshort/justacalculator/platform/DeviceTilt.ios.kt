package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import kotlin.math.PI
import kotlin.math.abs

private const val SMOOTHING = 0.20f
private const val DEAD_ZONE = 0.06f
private val MAX_ANGLE = (PI / 6.0).toFloat()

private val motionManager by lazy { CMMotionManager() }

actual fun isTiltAvailable(): Boolean = motionManager.deviceMotionAvailable

/**
 * CoreMotion reports attitude directly as roll/pitch, where Android needs a
 * rotation matrix converted to orientation angles first. The normalisation,
 * dead zone and smoothing constants are shared so the maze handles the same on
 * both platforms.
 */
@Composable
actual fun rememberDeviceTilt(): DeviceTilt {
    var tilt by remember { mutableStateOf(DeviceTilt(0f, 0f)) }

    DisposableEffect(Unit) {
        if (!motionManager.deviceMotionAvailable) return@DisposableEffect onDispose {}

        var smoothX = 0f
        var smoothY = 0f
        fun deadZone(v: Float) = if (abs(v) < DEAD_ZONE) 0f else v

        motionManager.deviceMotionUpdateInterval = 1.0 / 60.0
        motionManager.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { motion, _ ->
            val attitude = motion?.attitude ?: return@startDeviceMotionUpdatesToQueue
            val rawX = deadZone((attitude.roll.toFloat() / MAX_ANGLE).coerceIn(-1f, 1f))
            val rawY = deadZone((-attitude.pitch.toFloat() / MAX_ANGLE).coerceIn(-1f, 1f))
            smoothX = smoothX * (1f - SMOOTHING) + rawX * SMOOTHING
            smoothY = smoothY * (1f - SMOOTHING) + rawY * SMOOTHING
            tilt = DeviceTilt(smoothX, smoothY)
        }
        onDispose { motionManager.stopDeviceMotionUpdates() }
    }
    return tilt
}
