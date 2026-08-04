package com.fictioncutshort.justacalculator.ui.screens

import com.fictioncutshort.justacalculator.gl.Gl
import com.fictioncutshort.justacalculator.gl.GlFloatBuffer
import com.fictioncutshort.justacalculator.gl.Matrix
import com.fictioncutshort.justacalculator.gl.OffscreenGl
import com.fictioncutshort.justacalculator.gl.imageBitmapFromRgba
import com.fictioncutshort.justacalculator.gl.toGlBuffer
import com.fictioncutshort.justacalculator.platform.PlatformLock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ModelImageBitmap.kt
 *
 * Renders any Wavefront .obj (+ optional .mtl) to a still, transparent-background
 * bitmap for use as a flat 2D icon/sprite. Uses a throwaway offscreen GL context
 * so it never touches the main GLSurfaceView. ObjLoader gives positions only, so a
 * flat per-triangle normal is computed on the CPU for simple two-sided shading.
 * Results are cached per (obj, mtl, size, tilt, turn) — a model at a fixed pose
 * renders once.
 *
 * The currency HUD icons (CurrencyIcon.kt) delegate here; Building 8 also uses it
 * to render the arcade cabinet, gift boxes, scam cups/button and mystery-box
 * prizes as sprites.
 */
object ModelBitmapRenderer {

    // Sprites are rendered on a background thread and read from composition, so
    // the cache is guarded rather than concurrent — there is no multiplatform
    // ConcurrentHashMap, and contention here is nil.
    private val lock = PlatformLock()
    private val cache = mutableMapOf<String, ImageBitmap>()

    /**
     * Cached sprite for a model; renders on first request (null on failure).
     * [tilt]/[turn] are degrees applied around X/Y for the 3/4 view.
     */
    fun get(
        objPath: String,
        mtlPath: String?,
        sizePx: Int = 128,
        tilt: Float = -22f,
        turn: Float = 32f,
        colorGamma: Float = 1f,   // <1 brightens dark materials
        fitSpan: Float = 1.7f,    // model span after scaling (smaller = more margin)
    ): ImageBitmap? {
        val key = "$objPath|$mtlPath|$sizePx|$tilt|$turn|$colorGamma|$fitSpan"
        lock.withLock { cache[key] }?.let { return it }
        val rendered = runCatching {
            render(objPath, mtlPath, sizePx, tilt, turn, colorGamma, fitSpan)
        }.getOrNull() ?: return null
        lock.withLock { cache[key] = rendered }
        return rendered
    }

    // ── Offscreen render ──────────────────────────────────────────────────────
    private fun render(
        objPath: String, mtlPath: String?,
        size: Int, tilt: Float, turn: Float, colorGamma: Float, fitSpan: Float,
    ): ImageBitmap {
        val groups = ObjLoader.load(objPath, mtlPath)

        // Bounding box for auto-fit centering + scaling.
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (g in groups) {
            var i = 0
            while (i < g.verts.size) {
                val x = g.verts[i]; val y = g.verts[i + 1]; val z = g.verts[i + 2]
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
                i += 3
            }
        }
        val cx = (minX + maxX) * 0.5f; val cy = (minY + maxY) * 0.5f; val cz = (minZ + maxZ) * 0.5f
        val extent = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(1e-4f)
        val fit = fitSpan / extent   // model spans ~fitSpan units after scaling

        val gl = OffscreenGl(size)
        try {
            if (!gl.makeCurrent()) error("offscreen GL unavailable")
            Gl.glViewport(0, 0, size, size)
            Gl.glClearColor(0f, 0f, 0f, 0f)
            Gl.glEnable(Gl.GL_DEPTH_TEST)
            Gl.glDisable(Gl.GL_CULL_FACE)   // obj winding is unreliable
            Gl.glClear(Gl.GL_COLOR_BUFFER_BIT or Gl.GL_DEPTH_BUFFER_BIT)

            val prog = buildProgram()
            Gl.glUseProgram(prog)
            val aPos = Gl.glGetAttribLocation(prog, "aPos")
            val aNrm = Gl.glGetAttribLocation(prog, "aNormal")
            val uMVP = Gl.glGetUniformLocation(prog, "uMVP")
            val uModel = Gl.glGetUniformLocation(prog, "uModel")
            val uColor = Gl.glGetUniformLocation(prog, "uColor")

            // model = tilt · turn · scale · center → shows a 3/4 view.
            val model = FloatArray(16)
            Matrix.setIdentityM(model, 0)
            Matrix.rotateM(model, 0, tilt, 1f, 0f, 0f)
            Matrix.rotateM(model, 0, turn, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, fit, fit, fit)
            Matrix.translateM(model, 0, -cx, -cy, -cz)

            val view = FloatArray(16)
            Matrix.setLookAtM(view, 0, 0f, 0f, 3.2f, 0f, 0f, 0f, 0f, 1f, 0f)
            val proj = FloatArray(16)
            Matrix.perspectiveM(proj, 0, 35f, 1f, 0.1f, 20f)
            val vp = FloatArray(16); Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
            val mvp = FloatArray(16); Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)

            Gl.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
            Gl.glUniformMatrix4fv(uModel, 1, false, model, 0)

            for (g in groups) {
                val buf = buildInterleaved(g.verts) ?: continue
                // Groups with no material land at (0,0,0); nudge to a neutral gray.
                val emptyMat = g.r == 0f && g.g == 0f && g.b == 0f
                var r = if (emptyMat) 0.6f else g.r
                var gg = if (emptyMat) 0.6f else g.g
                var b = if (emptyMat) 0.6f else g.b
                if (colorGamma != 1f) { r = r.pow(colorGamma); gg = gg.pow(colorGamma); b = b.pow(colorGamma) }
                Gl.glUniform3f(uColor, r, gg, b)
                buf.position(0)
                Gl.glVertexAttribPointer(aPos, 3, Gl.GL_FLOAT, false, 6 * 4, buf)
                Gl.glEnableVertexAttribArray(aPos)
                buf.position(3)
                Gl.glVertexAttribPointer(aNrm, 3, Gl.GL_FLOAT, false, 6 * 4, buf)
                Gl.glEnableVertexAttribArray(aNrm)
                Gl.glDrawArrays(Gl.GL_TRIANGLES, 0, g.verts.size / 3)
            }

            // GL hands rows back bottom-up; the seam flips them.
            val pixels = ByteArray(size * size * 4)
            Gl.glReadPixels(0, 0, size, size, Gl.GL_RGBA, Gl.GL_UNSIGNED_BYTE, pixels)
            return imageBitmapFromRgba(size, size, pixels, flipVertically = true)
        } finally {
            gl.release()
        }
    }

    /** Position(3)+flat-normal(3) interleaved buffer, one flat normal per triangle. */
    private fun buildInterleaved(verts: FloatArray): GlFloatBuffer? {
        val triCount = verts.size / 9
        if (triCount == 0) return null
        val out = FloatArray(triCount * 3 * 6)
        var o = 0
        var t = 0
        while (t < triCount) {
            val b = t * 9
            val ax = verts[b]; val ay = verts[b + 1]; val az = verts[b + 2]
            val bx = verts[b + 3]; val by = verts[b + 4]; val bz = verts[b + 5]
            val ccx = verts[b + 6]; val ccy = verts[b + 7]; val ccz = verts[b + 8]
            // normal = (B-A) × (C-A)
            val ux = bx - ax; val uy = by - ay; val uz = bz - az
            val vx = ccx - ax; val vy = ccy - ay; val vz = ccz - az
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            nx /= len; ny /= len; nz /= len
            for (k in 0 until 3) {
                val p = b + k * 3
                out[o++] = verts[p]; out[o++] = verts[p + 1]; out[o++] = verts[p + 2]
                out[o++] = nx; out[o++] = ny; out[o++] = nz
            }
            t++
        }
        return out.toGlBuffer()
    }

    private fun buildProgram(): Int {
        val vs = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                vNormal = mat3(uModel[0].xyz, uModel[1].xyz, uModel[2].xyz) * aNormal;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform vec3 uColor;
            varying vec3 vNormal;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 l = normalize(vec3(0.4, 0.8, 0.6));
                float d = abs(dot(n, l));          // two-sided: winding-independent
                float shade = 0.4 + 0.6 * d;
                gl_FragColor = vec4(uColor * shade, 1.0);
            }
        """.trimIndent()
        val v = compile(Gl.GL_VERTEX_SHADER, vs)
        val f = compile(Gl.GL_FRAGMENT_SHADER, fs)
        val p = Gl.glCreateProgram()
        Gl.glAttachShader(p, v); Gl.glAttachShader(p, f); Gl.glLinkProgram(p)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = Gl.glCreateShader(type)
        Gl.glShaderSource(s, src)
        Gl.glCompileShader(s)
        return s
    }

}

/** Compose helper: renders (off the main thread) and caches an arbitrary model sprite. */
@Composable
fun rememberModelBitmap(
    objPath: String,
    mtlPath: String? = null,
    sizePx: Int = 160,
    tilt: Float = -22f,
    turn: Float = 32f,
    colorGamma: Float = 1f,
    fitSpan: Float = 1.7f,
): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, objPath, mtlPath, sizePx, tilt, turn, colorGamma, fitSpan) {
        value = withContext(Dispatchers.Default) {
            ModelBitmapRenderer.get(objPath, mtlPath, sizePx, tilt, turn, colorGamma, fitSpan)
        }
    }
    return state.value
}
