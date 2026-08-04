package com.fictioncutshort.justacalculator.platform

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@Composable
actual fun PlatformCameraSurface(
    modifier: Modifier,
    useFrontCamera: Boolean,
    scanRows: Int,
    scanCols: Int,
    onScanSamples: ((IntArray) -> Unit)?,
    autoCaptureLabel: String?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    // The analyser outlives a recomposition, so it reads the latest callback
    // rather than the one that was current when it was installed.
    val samplesCallback by rememberUpdatedState(onScanSamples)

    // ImageCapture handle so we can take a photo after binding
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }

    // (Re-)bind camera whenever useFrontCamera changes
    LaunchedEffect(useFrontCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                if (scanRows > 0 && scanCols > 0) {
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            samplesCallback?.invoke(
                                sampleGrid(imageProxy.toBitmap(), scanRows, scanCols)
                            )
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalysis, imageCapture
                )

                imageCaptureRef.value = imageCapture

            } catch (e: Exception) {
                Log.e("CameraPreview", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Auto-take a photo 2 s after each camera binding (rear and then front)
    LaunchedEffect(useFrontCamera, autoCaptureLabel) {
        val label = autoCaptureLabel ?: return@LaunchedEffect
        delay(2000)
        val capture = imageCaptureRef.value ?: return@LaunchedEffect
        val contentValues = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "calculator_${label}_${nowMillis()}.jpg",
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/JustACalculator")
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues,
            )
            .build()

        capture.takePicture(outputOptions, captureExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("CameraPreview", "Photo saved: ${output.savedUri}")
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e("CameraPreview", "Photo capture failed: ${e.message}")
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())
}

/** One centre pixel per cell, packed ARGB, row-major. */
private fun sampleGrid(bitmap: Bitmap, rows: Int, cols: Int): IntArray {
    val out = IntArray(rows * cols)
    return try {
        val cellW = bitmap.width / cols
        val cellH = bitmap.height / rows
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val px = (col * cellW + cellW / 2).coerceAtMost(bitmap.width - 1)
                val py = (row * cellH + cellH / 2).coerceAtMost(bitmap.height - 1)
                out[row * cols + col] = bitmap.getPixel(px, py)
            }
        }
        out
    } catch (_: Exception) {
        out
    }
}
