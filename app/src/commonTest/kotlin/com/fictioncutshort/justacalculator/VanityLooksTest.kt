package com.fictioncutshort.justacalculator

import androidx.compose.ui.graphics.ColorMatrix
import com.fictioncutshort.justacalculator.ui.screens.LOOKS
import com.fictioncutshort.justacalculator.ui.screens.after
import com.fictioncutshort.justacalculator.ui.screens.contrastMatrix
import com.fictioncutshort.justacalculator.ui.screens.saturationMatrix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Android's `postConcat` and Compose's `timesAssign` multiply in opposite
 * orders. A grade built the wrong way round still produces a plausible image,
 * so the composition is pinned here rather than eyeballed.
 */
class VanityLooksTest {

    /** Applies a matrix to a colour the way the renderer does. */
    private fun apply(m: ColorMatrix, r: Float, g: Float, b: Float): FloatArray {
        val v = m.values
        fun channel(row: Int) =
            v[row * 5] * r + v[row * 5 + 1] * g + v[row * 5 + 2] * b + v[row * 5 + 4]
        return floatArrayOf(channel(0), channel(1), channel(2))
    }

    private fun assertClose(expected: FloatArray, actual: FloatArray, tag: String) {
        assertTrue(
            expected.indices.all { abs(expected[it] - actual[it]) < 0.05f },
            "$tag\n  expected ${expected.toList()}\n  actual   ${actual.toList()}",
        )
    }

    @Test
    fun afterAppliesRightOperandFirst() {
        val desaturate = saturationMatrix(0f)
        val contrast = contrastMatrix(1.4f)
        val colour = floatArrayOf(200f, 60f, 20f)

        val combined = apply(contrast after desaturate, colour[0], colour[1], colour[2])
        val stepwise = apply(desaturate, colour[0], colour[1], colour[2])
            .let { apply(contrast, it[0], it[1], it[2]) }

        assertClose(stepwise, combined, "contrast after desaturate")
    }

    /**
     * The order has to actually matter, or the test above proves nothing.
     *
     * Not with saturation and contrast, though — those two commute, because
     * contrast is affine per channel and luminance weights sum to 1, so
     * grading then desaturating gives the same grey either way. A scale and an
     * offset do not commute, and that is what is checked here.
     */
    @Test
    fun orderActuallyMatters() {
        val scaleRed = ColorMatrix(FloatArray(20).also {
            it[0] = 2f; it[6] = 1f; it[12] = 1f; it[18] = 1f
        })
        val offsetRed = ColorMatrix(FloatArray(20).also {
            it[0] = 1f; it[4] = 50f; it[6] = 1f; it[12] = 1f; it[18] = 1f
        })

        // offset first, then double: 2(r + 50)
        assertClose(
            floatArrayOf(200f, 30f, 10f),
            apply(scaleRed after offsetRed, 50f, 30f, 10f),
            "scale after offset",
        )
        // double first, then offset: 2r + 50
        assertClose(
            floatArrayOf(150f, 30f, 10f),
            apply(offsetRed after scaleRed, 50f, 30f, 10f),
            "offset after scale",
        )
    }

    @Test
    fun identityAfterIdentityIsIdentity() {
        val identity = ColorMatrix()
        val result = apply(identity after identity, 120f, 90f, 40f)
        assertClose(floatArrayOf(120f, 90f, 40f), result, "identity")
    }

    /** Full desaturation must collapse the channels to one grey. */
    @Test
    fun monoProducesGrey() {
        val mono = LOOKS.first { it.name == "Mono" }.makeMatrix()!!
        val out = apply(mono, 200f, 60f, 20f)
        assertTrue(
            abs(out[0] - out[1]) < 0.5f && abs(out[1] - out[2]) < 0.5f,
            "channels should match, were ${out.toList()}",
        )
    }

    /** Vivid pushes saturation out, so channels must spread further apart. */
    @Test
    fun vividIncreasesSpread() {
        val vivid = LOOKS.first { it.name == "Vivid" }.makeMatrix()!!
        val out = apply(vivid, 200f, 60f, 20f)
        assertTrue(out[0] - out[2] > 180f, "spread was ${out[0] - out[2]}")
    }

    /** "None" is the default and must not grade at all. */
    @Test
    fun noneHasNoMatrix() {
        assertTrue(LOOKS.first().makeMatrix() == null, "the first look should be ungraded")
    }

    @Test
    fun everyLookHasADistinctName() {
        assertTrue(LOOKS.map { it.name }.toSet().size == LOOKS.size, "duplicate look names")
    }
}
