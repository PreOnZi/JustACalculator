package com.fictioncutshort.justacalculator.gl

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A drop-in replacement for `android.opengl.Matrix`, so the eight functions the
 * renderers use are available on both platforms.
 *
 * Matrices are 4x4 and **column-major** — element `m[i*4 + j]` is column `i`,
 * row `j` — which is what OpenGL expects and what the Android original used.
 * The semantics are matched deliberately, including the in-place mutation and
 * the offset parameters, so the call sites port unchanged.
 *
 * `rotateM`/`translateM`/`scaleM` post-multiply: they apply the transform in
 * the matrix's local space, which is what makes the existing scene-graph code
 * compose correctly.
 */
object Matrix {

    fun setIdentityM(sm: FloatArray, smOffset: Int) {
        for (i in 0 until 16) sm[smOffset + i] = 0f
        for (i in 0 until 16 step 5) sm[smOffset + i] = 1f
    }

    /** `result = lhs * rhs`. [result] must not alias [lhs] or [rhs]. */
    fun multiplyMM(
        result: FloatArray, resultOffset: Int,
        lhs: FloatArray, lhsOffset: Int,
        rhs: FloatArray, rhsOffset: Int,
    ) {
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += lhs[lhsOffset + k * 4 + j] * rhs[rhsOffset + i * 4 + k]
                }
                result[resultOffset + i * 4 + j] = sum
            }
        }
    }

    /** `resultVec = lhsMat * rhsVec`, both 4-component. */
    fun multiplyMV(
        resultVec: FloatArray, resultVecOffset: Int,
        lhsMat: FloatArray, lhsMatOffset: Int,
        rhsVec: FloatArray, rhsVecOffset: Int,
    ) {
        for (j in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) {
                sum += lhsMat[lhsMatOffset + k * 4 + j] * rhsVec[rhsVecOffset + k]
            }
            resultVec[resultVecOffset + j] = sum
        }
    }

    fun translateM(m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        // Only the last column changes: it accumulates the translation expressed
        // in the matrix's own basis.
        for (i in 0 until 4) {
            val mi = mOffset + i
            m[12 + mi] += m[mi] * x + m[4 + mi] * y + m[8 + mi] * z
        }
    }

    fun scaleM(m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            val mi = mOffset + i
            m[mi] *= x
            m[4 + mi] *= y
            m[8 + mi] *= z
        }
    }

    /** Post-multiplies [m] by a rotation of [a] degrees about (x, y, z). */
    fun rotateM(m: FloatArray, mOffset: Int, a: Float, x: Float, y: Float, z: Float) {
        val rotation = FloatArray(16)
        setRotateM(rotation, 0, a, x, y, z)
        val temp = FloatArray(16)
        multiplyMM(temp, 0, m, mOffset, rotation, 0)
        temp.copyInto(m, mOffset, 0, 16)
    }

    fun setRotateM(rm: FloatArray, rmOffset: Int, a: Float, x0: Float, y0: Float, z0: Float) {
        val radians = a * (kotlin.math.PI / 180.0).toFloat()
        val s = sin(radians)
        val c = cos(radians)

        rm[rmOffset + 3] = 0f
        rm[rmOffset + 7] = 0f
        rm[rmOffset + 11] = 0f
        rm[rmOffset + 12] = 0f
        rm[rmOffset + 13] = 0f
        rm[rmOffset + 14] = 0f
        rm[rmOffset + 15] = 1f

        var x = x0
        var y = y0
        var z = z0

        // Axis-aligned rotations are the common case in the scene graph, so they
        // skip the normalise and the general formula.
        if (x == 1f && y == 0f && z == 0f) {
            rm[rmOffset + 5] = c; rm[rmOffset + 10] = c
            rm[rmOffset + 6] = s; rm[rmOffset + 9] = -s
            rm[rmOffset + 1] = 0f; rm[rmOffset + 2] = 0f
            rm[rmOffset + 4] = 0f; rm[rmOffset + 8] = 0f
            rm[rmOffset + 0] = 1f
        } else if (x == 0f && y == 1f && z == 0f) {
            rm[rmOffset + 0] = c; rm[rmOffset + 10] = c
            rm[rmOffset + 8] = s; rm[rmOffset + 2] = -s
            rm[rmOffset + 1] = 0f; rm[rmOffset + 4] = 0f
            rm[rmOffset + 6] = 0f; rm[rmOffset + 9] = 0f
            rm[rmOffset + 5] = 1f
        } else if (x == 0f && y == 0f && z == 1f) {
            rm[rmOffset + 0] = c; rm[rmOffset + 5] = c
            rm[rmOffset + 1] = s; rm[rmOffset + 4] = -s
            rm[rmOffset + 2] = 0f; rm[rmOffset + 6] = 0f
            rm[rmOffset + 8] = 0f; rm[rmOffset + 9] = 0f
            rm[rmOffset + 10] = 1f
        } else {
            val len = length(x, y, z)
            if (len != 1f) {
                val recipLen = 1f / len
                x *= recipLen; y *= recipLen; z *= recipLen
            }
            val nc = 1f - c
            val xy = x * y
            val yz = y * z
            val zx = z * x
            val xs = x * s
            val ys = y * s
            val zs = z * s
            rm[rmOffset + 0] = x * x * nc + c
            rm[rmOffset + 4] = xy * nc - zs
            rm[rmOffset + 8] = zx * nc + ys
            rm[rmOffset + 1] = xy * nc + zs
            rm[rmOffset + 5] = y * y * nc + c
            rm[rmOffset + 9] = yz * nc - xs
            rm[rmOffset + 2] = zx * nc - ys
            rm[rmOffset + 6] = yz * nc + xs
            rm[rmOffset + 10] = z * z * nc + c
        }
    }

    fun setLookAtM(
        rm: FloatArray, rmOffset: Int,
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float,
    ) {
        var fx = centerX - eyeX
        var fy = centerY - eyeY
        var fz = centerZ - eyeZ

        val rlf = 1f / length(fx, fy, fz)
        fx *= rlf; fy *= rlf; fz *= rlf

        // side = forward x up
        var sx = fy * upZ - fz * upY
        var sy = fz * upX - fx * upZ
        var sz = fx * upY - fy * upX
        val rls = 1f / length(sx, sy, sz)
        sx *= rls; sy *= rls; sz *= rls

        // up' = side x forward
        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx

        rm[rmOffset + 0] = sx;  rm[rmOffset + 1] = ux;  rm[rmOffset + 2] = -fx; rm[rmOffset + 3] = 0f
        rm[rmOffset + 4] = sy;  rm[rmOffset + 5] = uy;  rm[rmOffset + 6] = -fy; rm[rmOffset + 7] = 0f
        rm[rmOffset + 8] = sz;  rm[rmOffset + 9] = uz;  rm[rmOffset + 10] = -fz; rm[rmOffset + 11] = 0f
        rm[rmOffset + 12] = 0f; rm[rmOffset + 13] = 0f; rm[rmOffset + 14] = 0f; rm[rmOffset + 15] = 1f

        translateM(rm, rmOffset, -eyeX, -eyeY, -eyeZ)
    }

    fun perspectiveM(
        m: FloatArray, offset: Int,
        fovy: Float, aspect: Float, zNear: Float, zFar: Float,
    ) {
        val f = 1f / tan(fovy * (kotlin.math.PI / 360.0).toFloat())
        val rangeReciprocal = 1f / (zNear - zFar)

        m[offset + 0] = f / aspect
        m[offset + 1] = 0f
        m[offset + 2] = 0f
        m[offset + 3] = 0f

        m[offset + 4] = 0f
        m[offset + 5] = f
        m[offset + 6] = 0f
        m[offset + 7] = 0f

        m[offset + 8] = 0f
        m[offset + 9] = 0f
        m[offset + 10] = (zFar + zNear) * rangeReciprocal
        m[offset + 11] = -1f

        m[offset + 12] = 0f
        m[offset + 13] = 0f
        m[offset + 14] = 2f * zFar * zNear * rangeReciprocal
        m[offset + 15] = 0f
    }

    fun length(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)
}
