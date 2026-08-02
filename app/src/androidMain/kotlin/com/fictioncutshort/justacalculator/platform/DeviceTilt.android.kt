package com.fictioncutshort.justacalculator.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs

private const val SMOOTHING = 0.20f
private const val DEAD_ZONE = 0.06f
private val MAX_ANGLE = (kotlin.math.PI / 6.0).toFloat()

private fun sensorManager(): SensorManager =
    AppInit.context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

private fun tiltSensor(): Sensor? = sensorManager().let {
    it.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: it.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
}

actual fun isTiltAvailable(): Boolean = runCatching { tiltSensor() != null }.getOrDefault(false)

@Composable
actual fun rememberDeviceTilt(): DeviceTilt {
    var tilt by remember { mutableStateOf(DeviceTilt(0f, 0f)) }

    DisposableEffect(Unit) {
        val sensor = tiltSensor() ?: return@DisposableEffect onDispose {}
        val manager = sensorManager()
        val listener = object : SensorEventListener {
            private val rotMat = FloatArray(9)
            private val orientation = FloatArray(3)
            private var smoothX = 0f
            private var smoothY = 0f
            private fun deadZone(v: Float) = if (abs(v) < DEAD_ZONE) 0f else v

            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMat, e.values)
                SensorManager.getOrientation(rotMat, orientation)
                val rawX = deadZone((orientation[2] / MAX_ANGLE).coerceIn(-1f, 1f))
                val rawY = deadZone((-orientation[1] / MAX_ANGLE).coerceIn(-1f, 1f))
                smoothX = smoothX * (1f - SMOOTHING) + rawX * SMOOTHING
                smoothY = smoothY * (1f - SMOOTHING) + rawY * SMOOTHING
                tilt = DeviceTilt(smoothX, smoothY)
            }

            override fun onAccuracyChanged(s: Sensor, a: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return tilt
}
