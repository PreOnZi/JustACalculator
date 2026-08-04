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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
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
    onFaceFrame: ((FaceFrame) -> Unit)?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    // The analyser outlives a recomposition, so it reads the latest callback
    // rather than the one that was current when it was installed.
    val samplesCallback by rememberUpdatedState(onScanSamples)
    val faceCallback by rememberUpdatedState(onFaceFrame)

    // Built once and reused; constructing a detector per frame is what makes
    // ML Kit look slow.
    val detector = remember(onFaceFrame != null) {
        if (onFaceFrame == null) null else FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()
        )
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

                if ((scanRows > 0 && scanCols > 0) || detector != null) {
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val raw = try { imageProxy.toBitmap() } catch (_: Exception) { null }
                        if (raw == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        if (scanRows > 0 && scanCols > 0) {
                            samplesCallback?.invoke(sampleGrid(raw, scanRows, scanCols))
                        }
                        if (detector == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        // ML Kit reports coordinates in the upright frame, which
                        // is also the space the caller draws in — so the frame
                        // it receives is corrected to match.
                        val upright = makeUprightMirrored(raw, rotation, useFrontCamera)
                        detector.process(InputImage.fromBitmap(upright, 0))
                            .addOnSuccessListener { detected ->
                                faceCallback?.invoke(
                                    FaceFrame(
                                        faces = detected.map { it.toDetectedFace() }
                                            // Largest first, so the caller's
                                            // "primary" face is the nearest person.
                                            .sortedByDescending { f -> f.area }
                                            .take(MAX_FACES),
                                        image = upright.asImageBitmap(),
                                    )
                                )
                            }
                            .addOnCompleteListener { imageProxy.close() }
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

private const val MAX_FACES = 8

private fun Face.toDetectedFace(): DetectedFace {
    val box = boundingBox
    fun point(landmark: FaceLandmark?): FacePoint? =
        landmark?.position?.let { FacePoint(it.x, it.y) }
    return DetectedFace(
        left = box.left.toFloat(),
        top = box.top.toFloat(),
        right = box.right.toFloat(),
        bottom = box.bottom.toFloat(),
        leftEye = point(getLandmark(FaceLandmark.LEFT_EYE)),
        rightEye = point(getLandmark(FaceLandmark.RIGHT_EYE)),
        rollDegrees = headEulerAngleZ,
    )
}

/**
 * Rotates a sensor frame upright and mirrors it for the front camera, so it
 * matches what the preview shows.
 */
private fun makeUprightMirrored(raw: Bitmap, rotation: Int, front: Boolean): Bitmap {
    if (rotation == 0 && !front) return raw
    val matrix = android.graphics.Matrix().apply {
        postRotate(rotation.toFloat())
        if (front) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}
