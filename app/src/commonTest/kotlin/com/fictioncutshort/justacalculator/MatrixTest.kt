package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.gl.Matrix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The replacement for `android.opengl.Matrix` has to agree with the original
 * exactly — a transposed or mis-signed term would not fail to compile, it would
 * render the city subtly wrong (mirrored, inside-out, or camera-behind-geometry)
 * and be painful to trace back. These pin the conventions.
 */
class MatrixTest {

    private fun assertClose(expected: FloatArray, actual: FloatArray, tag: String) {
        assertTrue(
            expected.indices.all { abs(expected[it] - actual[it]) < 1e-4f },
            "$tag\n  expected ${expected.toList()}\n  actual   ${actual.toList()}",
        )
    }

    @Test
    fun identityIsIdentity() {
        val m = FloatArray(16) { 9f }
        Matrix.setIdentityM(m, 0)
        assertClose(
            floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f),
            m, "identity",
        )
    }

    @Test
    fun identityTimesMatrixIsThatMatrix() {
        val id = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        val m = FloatArray(16) { it.toFloat() }
        val out = FloatArray(16)
        Matrix.multiplyMM(out, 0, id, 0, m, 0)
        assertClose(m, out, "I * M == M")
    }

    /** Translation lands in the last column — the column-major convention. */
    @Test
    fun translationOccupiesLastColumn() {
        val m = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        Matrix.translateM(m, 0, 2f, 3f, 4f)
        assertTrue(abs(m[12] - 2f) < 1e-4f, "m[12] should be x, was ${m[12]}")
        assertTrue(abs(m[13] - 3f) < 1e-4f, "m[13] should be y, was ${m[13]}")
        assertTrue(abs(m[14] - 4f) < 1e-4f, "m[14] should be z, was ${m[14]}")
    }

    @Test
    fun scaleScalesTheBasisVectors() {
        val m = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        Matrix.scaleM(m, 0, 2f, 3f, 4f)
        assertTrue(abs(m[0] - 2f) < 1e-4f)
        assertTrue(abs(m[5] - 3f) < 1e-4f)
        assertTrue(abs(m[10] - 4f) < 1e-4f)
    }

    /** 90° about Z must send +X to +Y, not to -Y. */
    @Test
    fun rotationDirectionIsRightHanded() {
        val m = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        Matrix.rotateM(m, 0, 90f, 0f, 0f, 1f)
        val v = floatArrayOf(1f, 0f, 0f, 1f)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, m, 0, v, 0)
        assertClose(floatArrayOf(0f, 1f, 0f, 1f), out, "+X rotated 90° about Z should be +Y")
    }

    @Test
    fun fourRightAngleRotationsReturnToStart() {
        val m = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        repeat(4) { Matrix.rotateM(m, 0, 90f, 0f, 1f, 0f) }
        val id = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        assertClose(id, m, "4 x 90° about Y == identity")
    }

    /** An arbitrary axis must agree with the axis-aligned fast path. */
    @Test
    fun arbitraryAxisAgreesWithFastPath() {
        val fast = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        Matrix.rotateM(fast, 0, 37f, 0f, 1f, 0f)

        val general = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        // Same axis, non-unit length, so it takes the normalising branch.
        Matrix.rotateM(general, 0, 37f, 0f, 5f, 0f)

        assertClose(fast, general, "unit and non-unit Y axis must match")
    }

    /** The camera looks down -Z, so a point in front must land at negative Z. */
    @Test
    fun lookAtPutsTargetInFrontOfCamera() {
        val view = FloatArray(16)
        Matrix.setLookAtM(view, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
        val origin = floatArrayOf(0f, 0f, 0f, 1f)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, view, 0, origin, 0)
        assertTrue(abs(out[0]) < 1e-4f && abs(out[1]) < 1e-4f, "should stay centred")
        assertTrue(abs(out[2] + 5f) < 1e-4f, "target should sit at z=-5, was ${out[2]}")
    }

    @Test
    fun perspectiveHasTheProjectiveTerm() {
        val p = FloatArray(16)
        Matrix.perspectiveM(p, 0, 90f, 1f, 1f, 100f)
        // tan(45°) == 1, so the focal term is 1 at a 90° vertical fov.
        assertTrue(abs(p[0] - 1f) < 1e-4f, "m[0] was ${p[0]}")
        assertTrue(abs(p[5] - 1f) < 1e-4f, "m[5] was ${p[5]}")
        // w = -z is what makes it a perspective rather than an orthographic.
        assertTrue(abs(p[11] + 1f) < 1e-4f, "m[11] must be -1, was ${p[11]}")
        assertTrue(abs(p[15]) < 1e-4f, "m[15] must be 0, was ${p[15]}")
    }

    @Test
    fun perspectiveMapsNearAndFarToClipRange() {
        val p = FloatArray(16)
        val near = 1f
        val far = 100f
        Matrix.perspectiveM(p, 0, 60f, 1.5f, near, far)

        fun projectZ(z: Float): Float {
            val out = FloatArray(4)
            Matrix.multiplyMV(out, 0, p, 0, floatArrayOf(0f, 0f, z, 1f), 0)
            return out[2] / out[3]
        }
        // OpenGL clip space: the near plane maps to -1, the far plane to +1.
        assertTrue(abs(projectZ(-near) + 1f) < 1e-3f, "near -> ${projectZ(-near)}")
        assertTrue(abs(projectZ(-far) - 1f) < 1e-3f, "far -> ${projectZ(-far)}")
    }

    /** Offsets are used to pack several matrices into one array. */
    @Test
    fun offsetsAreRespected() {
        val packed = FloatArray(32) { -1f }
        Matrix.setIdentityM(packed, 16)
        assertTrue(packed.take(16).all { it == -1f }, "must not touch bytes before the offset")
        assertTrue(abs(packed[16] - 1f) < 1e-4f && abs(packed[31] - 1f) < 1e-4f)
    }
}
