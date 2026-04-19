package com.fictioncutshort.justacalculator.ui.components

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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * CameraPreview.kt
 *
 * Camera viewfinder used in the "show me around" sequence (step 19).
 * Supports rear → front camera switch mid-session.
 * Renders a real-time colour-analysis scan overlay.
 * Auto-takes one photo per camera session and saves it to the gallery.
 */

private const val GRID_ROWS = 14
private const val GRID_COLS = 9

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner,
    useFrontCamera: Boolean = false
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor  = remember { Executors.newSingleThreadExecutor() }

    // Detected colour per grid cell, updated from ImageAnalysis
    val colorGrid: MutableState<Array<Array<Color>>> = remember {
        mutableStateOf(Array(GRID_ROWS) { Array(GRID_COLS) { Color.Transparent } })
    }

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

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        colorGrid.value = extractColorGrid(imageProxy.toBitmap(), GRID_ROWS, GRID_COLS)
                    } finally {
                        imageProxy.close()
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
    LaunchedEffect(useFrontCamera) {
        delay(2000)
        val capture = imageCaptureRef.value ?: return@LaunchedEffect
        val label = if (useFrontCamera) "selfie" else "scene"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "calculator_${label}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/JustACalculator")
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
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

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CameraScanOverlay(colorGrid = colorGrid.value, modifier = Modifier.fillMaxSize())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Colour extraction
// ─────────────────────────────────────────────────────────────────────────────

private fun extractColorGrid(bitmap: Bitmap, rows: Int, cols: Int): Array<Array<Color>> {
    val grid = Array(rows) { Array(cols) { Color.Transparent } }
    return try {
        val cellW = bitmap.width / cols
        val cellH = bitmap.height / rows
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val px = (col * cellW + cellW / 2).coerceAtMost(bitmap.width - 1)
                val py = (row * cellH + cellH / 2).coerceAtMost(bitmap.height - 1)
                val pixel = bitmap.getPixel(px, py)
                grid[row][col] = Color(
                    android.graphics.Color.red(pixel) / 255f,
                    android.graphics.Color.green(pixel) / 255f,
                    android.graphics.Color.blue(pixel) / 255f
                )
            }
        }
        grid
    } catch (e: Exception) {
        grid
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Colour classification → scan tint
// ─────────────────────────────────────────────────────────────────────────────

private fun classifyScanColor(color: Color): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val brightness = (r + g + b) / 3f
    if (brightness < 0.08f) return Color.Transparent
    return when {
        r > g * 1.35f && r > b * 1.35f          -> Color(1f, 0.35f, 0.0f)  // warm/red → orange
        b > r * 1.35f && b > g * 1.1f            -> Color(0.0f, 0.85f, 1f)  // cool/blue → cyan
        g > r * 1.25f && g > b * 1.25f           -> Color(0.1f, 1f, 0.3f)   // green organic
        r > 0.55f && g > 0.35f && b < 0.45f
                && r > g && g > b                -> Color(1f, 0.6f, 0.8f)   // skin-tone → pink
        brightness > 0.82f                        -> Color(1f, 1f, 0.6f)     // bright → yellow
        else                                      -> Color(0.15f, 1f, 0.55f) // default scan green
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scan overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraScanOverlay(
    colorGrid: Array<Array<Color>>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")

    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scanLine"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cellW = w / GRID_COLS
        val cellH = h / GRID_ROWS
        val scanY = h * scanProgress
        val scanRow = (scanProgress * GRID_ROWS).toInt().coerceIn(0, GRID_ROWS - 1)

        // Faint grid lines
        val gridColor = Color(0f, 1f, 0.4f, 0.06f)
        for (col in 0..GRID_COLS) drawLine(gridColor, Offset(col * cellW, 0f), Offset(col * cellW, h), 1f)
        for (row in 0..GRID_ROWS) drawLine(gridColor, Offset(0f, row * cellH), Offset(w, row * cellH), 1f)

        // Colour-reactive cells
        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLS) {
                val scanColor = classifyScanColor(colorGrid[row][col])
                if (scanColor == Color.Transparent) continue
                val dist = kotlin.math.abs(row - scanRow)
                val alpha = when (dist) {
                    0    -> 0.55f + pulse * 0.20f
                    1    -> 0.30f
                    2    -> 0.15f
                    else -> 0.10f
                }
                drawRect(scanColor.copy(alpha = alpha),
                    topLeft = Offset(col * cellW + 1f, row * cellH + 1f),
                    size    = Size(cellW - 2f, cellH - 2f))
                drawRect(scanColor.copy(alpha = 0.35f),
                    topLeft = Offset(col * cellW, row * cellH),
                    size    = Size(cellW, cellH),
                    style   = Stroke(width = 1f))
            }
        }

        // Scan line glow + core
        drawRect(Color(0f, 1f, 0.5f, 0.18f), topLeft = Offset(0f, scanY), size = Size(w, 28f))
        drawLine(Color(0.4f, 1f, 0.6f, 0.9f),  Offset(0f, scanY), Offset(w, scanY), 3f)
        drawLine(Color(1f,   1f, 1f,   0.55f), Offset(0f, scanY), Offset(w, scanY), 1f)

        // Corner HUD brackets
        val bl = 32f; val bs = 2.5f; val bc = Color(0f, 1f, 0.5f, 0.85f); val m = 6f
        drawLine(bc, Offset(m, m),       Offset(m + bl, m),       bs)
        drawLine(bc, Offset(m, m),       Offset(m, m + bl),       bs)
        drawLine(bc, Offset(w-m, m),     Offset(w-m-bl, m),       bs)
        drawLine(bc, Offset(w-m, m),     Offset(w-m, m+bl),       bs)
        drawLine(bc, Offset(m, h-m),     Offset(m+bl, h-m),       bs)
        drawLine(bc, Offset(m, h-m),     Offset(m, h-m-bl),       bs)
        drawLine(bc, Offset(w-m, h-m),   Offset(w-m-bl, h-m),     bs)
        drawLine(bc, Offset(w-m, h-m),   Offset(w-m, h-m-bl),     bs)
    }
}
