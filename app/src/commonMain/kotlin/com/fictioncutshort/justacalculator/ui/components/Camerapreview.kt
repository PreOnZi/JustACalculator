package com.fictioncutshort.justacalculator.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.fictioncutshort.justacalculator.platform.PlatformCameraSurface

/**
 * Camerapreview.kt
 *
 * Camera viewfinder used in the "show me around" sequence (step 19).
 * Supports rear → front camera switch mid-session.
 * Renders a real-time colour-analysis scan overlay.
 * Auto-takes one photo per camera session and saves it to the gallery.
 *
 * Only the capture session is platform. The scan overlay is the part the
 * player actually looks at, and it is entirely Compose — so it, and the colour
 * classification driving it, stay identical on both platforms.
 */

private const val GRID_ROWS = 14
private const val GRID_COLS = 9

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    useFrontCamera: Boolean = false,
) {
    // One packed ARGB sample per grid cell, row-major, refreshed per frame.
    // A flat array rather than a nested one so the platform side can fill it
    // without allocating on the camera thread.
    var samples by remember { mutableStateOf(IntArray(GRID_ROWS * GRID_COLS)) }

    Box(modifier = modifier) {
        PlatformCameraSurface(
            modifier = Modifier.fillMaxSize(),
            useFrontCamera = useFrontCamera,
            scanRows = GRID_ROWS,
            scanCols = GRID_COLS,
            onScanSamples = { samples = it },
            // Rear then front, one photo each, as the sequence steps through.
            autoCaptureLabel = if (useFrontCamera) "selfie" else "scene",
        )
        CameraScanOverlay(samples = samples, modifier = Modifier.fillMaxSize())
    }
}

/** Unpacks one cell's ARGB sample. Alpha is ignored — the camera is opaque. */
private fun cellColor(packed: Int): Color = Color(
    red = ((packed shr 16) and 0xFF) / 255f,
    green = ((packed shr 8) and 0xFF) / 255f,
    blue = (packed and 0xFF) / 255f,
)

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
    samples: IntArray,
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
                val scanColor = classifyScanColor(cellColor(samples[row * GRID_COLS + col]))
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
