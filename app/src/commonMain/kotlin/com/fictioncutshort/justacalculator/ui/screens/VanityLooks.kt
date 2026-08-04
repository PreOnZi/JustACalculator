package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix

/**
 * The vanity room's colour grades.
 *
 * `androidx.compose.ui.graphics.ColorMatrix` has the same 4x5 row-major layout
 * as the `android.graphics` one these were written against, so the coefficients
 * port verbatim. What does not port is the concatenation: Android's
 * `postConcat` and Compose's `timesAssign` multiply in opposite orders, and a
 * grade built the wrong way round still produces a plausible-looking image —
 * so [after] spells the order out and is pinned by tests.
 */

/** A matrix that scales saturation, leaving luminance alone. */
internal fun saturationMatrix(saturation: Float): ColorMatrix =
    ColorMatrix().apply { setToSaturation(saturation) }

/**
 * `this after other` — apply [other] first, then this. Equivalent to the matrix
 * product `this x other`, and to Android's `this.postConcat` read backwards.
 */
internal infix fun ColorMatrix.after(other: ColorMatrix): ColorMatrix {
    val a = this.values
    val b = other.values
    val out = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 5) {
            var sum = 0f
            for (k in 0 until 4) sum += a[row * 5 + k] * b[k * 5 + col]
            // The fifth column is a translation, so it also picks up this
            // matrix's own offset rather than only the product.
            if (col == 4) sum += a[row * 5 + 4]
            out[row * 5 + col] = sum
        }
    }
    return ColorMatrix(out)
}

// ── Colour / lighting "Look" filters ─────────────────────────────────────────
// makeMatrix() returns a fresh ColorMatrix (or null for no grade); overlay is a
// flat scrim drawn on top; vignette darkens the edges. swatch tints the chip.
internal class LookFilter(
    val name: String,
    val swatch: Color,
    val points: Int,
    val makeMatrix: () -> ColorMatrix?,
    val overlay: Color? = null,
    val vignette: Boolean = false,
)

internal fun contrastMatrix(c: Float): ColorMatrix {
    val t = (1f - c) / 2f * 255f
    return ColorMatrix(floatArrayOf(
        c, 0f, 0f, 0f, t,
        0f, c, 0f, 0f, t,
        0f, 0f, c, 0f, t,
        0f, 0f, 0f, 1f, 0f,
    ))
}

internal val LOOKS: List<LookFilter> = listOf(
    LookFilter("None", Color(0xFF3A3A3A), 0, { null }),
    LookFilter("Warm", Color(0xFFE8A24C), 5, {
        ColorMatrix(floatArrayOf(
            1.12f, 0f, 0f, 0f, 12f,
            0f, 1.00f, 0f, 0f, 4f,
            0f, 0f, 0.85f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }),
    LookFilter("Cool", Color(0xFF4C8FE8), 5, {
        ColorMatrix(floatArrayOf(
            0.88f, 0f, 0f, 0f, 0f,
            0f, 1.00f, 0f, 0f, 0f,
            0f, 0f, 1.15f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }),
    LookFilter("Mono", Color(0xFF8A8A8A), 8, { saturationMatrix(0f) }),
    LookFilter("Sepia", Color(0xFF9A7B4F), 8, {
        ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }),
    LookFilter("Vivid", Color(0xFFE83C8F), 12, { saturationMatrix(1.6f) }),
    // Desaturate first, then push contrast — the order matters, hence the
    // explicit concat rather than relying on either platform's operator.
    LookFilter("Noir", Color(0xFF101010), 15, {
        contrastMatrix(1.4f) after saturationMatrix(0f)
    }, overlay = Color(0x14000000), vignette = true),
    LookFilter("Dream", Color(0xFFF6A8C8), 12, {
        ColorMatrix(floatArrayOf(
            1.0f, 0f, 0f, 0f, 16f,
            0f, 1.0f, 0f, 0f, 12f,
            0f, 0f, 1.0f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }, overlay = Color(0x1FFF6FA8)),
    LookFilter("Sunset", Color(0xFFFF7A3D), 15, {
        ColorMatrix(floatArrayOf(
            1.15f, 0f, 0f, 0f, 10f,
            0f, 0.95f, 0f, 0f, 0f,
            0f, 0f, 0.80f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }, overlay = Color(0x26FF7A3D), vignette = true),
)
