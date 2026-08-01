package com.fictioncutshort.justacalculator.platform

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
actual fun OnAppLifecycleEvent(onEvent: (AppLifecycleEvent) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(onEvent)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> current(AppLifecycleEvent.STARTED)
                Lifecycle.Event.ON_RESUME -> current(AppLifecycleEvent.RESUMED)
                Lifecycle.Event.ON_PAUSE -> current(AppLifecycleEvent.PAUSED)
                Lifecycle.Event.ON_STOP -> current(AppLifecycleEvent.STOPPED)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
actual fun rememberPermissionRequest(
    permission: AppPermission,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val current by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> current(granted) }

    val name = when (permission) {
        AppPermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        AppPermission.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        AppPermission.CONTACTS -> Manifest.permission.READ_CONTACTS
        AppPermission.CAMERA -> Manifest.permission.CAMERA
    }
    return { launcher.launch(name) }
}
