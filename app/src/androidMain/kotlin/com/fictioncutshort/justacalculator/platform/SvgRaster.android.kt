package com.fictioncutshort.justacalculator.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.caverock.androidsvg.SVG
import kotlin.math.roundToInt

/** AndroidSVG, bundled via coil-svg. */
actual fun renderSvgAsset(assetPath: String, targetWidth: Int): ImageBitmap? = try {
    val svg = AppInit.context.assets.open(assetPath).use { SVG.getFromInputStream(it) }
    val viewBox = svg.documentViewBox
    val aspect = if (viewBox != null && viewBox.width() > 0f) {
        viewBox.height() / viewBox.width()
    } else 1f
    val height = (targetWidth * aspect).roundToInt().coerceAtLeast(1)
    svg.setDocumentWidth(targetWidth.toFloat())
    svg.setDocumentHeight(height.toFloat())
    val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
    svg.renderToCanvas(Canvas(bitmap))
    bitmap.asImageBitmap()
} catch (e: Exception) {
    logWarn("SvgRaster", "SVG render failed for $assetPath: ${e.message}")
    null
}
