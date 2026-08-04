package com.fictioncutshort.justacalculator.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthUnit
import kotlin.math.roundToInt

/**
 * Skia's own SVG renderer, which is already linked in — Compose draws through
 * Skia on this platform, so nothing new is pulled in for this.
 */
actual fun renderSvgAsset(assetPath: String, targetWidth: Int): ImageBitmap? = try {
    val bytes = Assets.readBytes(assetPath)
    val svg = SVGDOM(Data.makeFromBytes(bytes))
    val root = svg.root

    // Prefer the viewBox: an SVG's width/height attributes are often absent, or
    // given in physical units that say nothing about its proportions.
    val aspect = root?.let { r ->
        val box = r.viewBox
        if (box != null && box.width > 0f) {
            box.height / box.width
        } else {
            val w = r.width.takeIf { it.unit != SVGLengthUnit.PERCENTAGE }?.value ?: 0f
            val h = r.height.takeIf { it.unit != SVGLengthUnit.PERCENTAGE }?.value ?: 0f
            if (w > 0f) h / w else 1f
        }
    } ?: 1f

    val height = (targetWidth * aspect).roundToInt().coerceAtLeast(1)
    // setContainerSize makes the document scale to the surface rather than
    // being drawn at its intrinsic size in the corner.
    svg.setContainerSize(targetWidth.toFloat(), height.toFloat())

    val surface = Surface.makeRasterN32Premul(targetWidth, height)
    svg.render(surface.canvas)
    // Snapshot back into a Bitmap: only Bitmap has the Compose bridge.
    val bitmap = Bitmap().apply { allocN32Pixels(targetWidth, height) }
    if (!surface.readPixels(bitmap, 0, 0)) null else bitmap.asComposeImageBitmap()
} catch (e: Exception) {
    logWarn("SvgRaster", "SVG render failed for $assetPath: ${e.message}")
    null
}
