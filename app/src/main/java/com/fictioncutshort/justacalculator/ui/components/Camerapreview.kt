package com.fictioncutshort.justacalculator.ui.components

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * CameraPreview.kt
 *
 * Camera viewfinder component used in the "show me around" feature (step 19).
 * Uses CameraX library to display live camera feed.
 *
 * The calculator "sees" through the camera for 8 seconds before timing out
 * and expressing wonder at the visual world.
 */

/**
 * Live camera preview using CameraX.
 *
 * @param modifier Modifier for sizing and clipping
 * @param lifecycleOwner Required for CameraX lifecycle binding
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    // Initialize camera when composable enters composition
    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Build preview use case
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                // Use back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind any previous use cases and bind new ones
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)

            } catch (e: Exception) {
                Log.e("CameraPreview", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Render the PreviewView
    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}