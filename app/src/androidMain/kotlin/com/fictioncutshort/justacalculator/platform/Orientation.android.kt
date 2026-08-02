package com.fictioncutshort.justacalculator.platform

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun LockOrientationWhileVisible() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previous = activity?.requestedOrientation
        // LOCKED freezes the current orientation without requesting a rotation,
        // so WindowManager does not schedule an ActivityRelaunchItem the way
        // switching to SENSOR_LANDSCAPE does.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
