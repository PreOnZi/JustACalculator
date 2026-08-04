package com.fictioncutshort.justacalculator.gl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

/**
 * Renders monospaced text into a transparent image, for uploading as a texture.
 *
 * Replaces the `Paint`/`Canvas.drawText` pairs the GL rooms used. A
 * [TextMeasurer] only exists inside a composition, so callers build these in
 * their composable and hand the results to the renderer — the GL thread cannot
 * make one.
 */

/** Text centred in a [width] x [height] image, shrunk until it fits. */
fun renderCenteredTextImage(
    text: String,
    width: Int,
    height: Int,
    measurer: TextMeasurer,
    color: Color = Color.White,
): ImageBitmap {
    var style = TextStyle(
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = (height * 0.42f).sp,
    )
    var measured = measurer.measure(text, style)
    // Shrink until it fits the width with a margin, as the Paint version did.
    while (measured.size.width > width * 0.92f && style.fontSize.value > 8f) {
        style = style.copy(fontSize = (style.fontSize.value * 0.94f).sp)
        measured = measurer.measure(text, style)
    }
    val finalStyle = style
    val finalMeasured = measured
    return drawInto(width, height) {
        drawText(
            textMeasurer = measurer,
            text = text,
            style = finalStyle,
            topLeft = Offset(
                (width - finalMeasured.size.width) / 2f,
                (height - finalMeasured.size.height) / 2f,
            ),
        )
    }
}

/** Text on a tightly-fitted transparent image, with [padding] px of margin. */
fun renderTightTextImage(
    text: String,
    fontSize: TextUnit,
    measurer: TextMeasurer,
    color: Color = Color.White,
    padding: Float = 8f,
): ImageBitmap {
    val style = TextStyle(color = color, fontFamily = FontFamily.Monospace, fontSize = fontSize)
    val measured = measurer.measure(text, style)
    val width = ceil(measured.size.width + padding * 2f).toInt().coerceAtLeast(1)
    val height = ceil(measured.size.height + padding * 2f).toInt().coerceAtLeast(1)
    return drawInto(width, height) {
        drawText(
            textMeasurer = measurer,
            text = text,
            style = style,
            topLeft = Offset(padding, padding),
        )
    }
}

private fun drawInto(
    width: Int,
    height: Int,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
): ImageBitmap {
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(bitmap),
        size = Size(width.toFloat(), height.toFloat()),
        block = block,
    )
    return bitmap
}
