package com.fictioncutshort.justacalculator.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix as AndroidMatrix
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ModelBitmap.kt
 *
 * Renders any Wavefront .obj (+ optional .mtl) to a still, transparent-background
 * bitmap for use as a flat 2D icon/sprite. Uses a throwaway offscreen EGL pbuffer
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

    private val cache = ConcurrentHashMap<String, Bitmap>()

    /**
     * Cached sprite for a model; renders on first request (null on failure).
     * [tilt]/[turn] are degrees applied around X/Y for the 3/4 view.
     */
    fun get(
        context: Context,
        objPath: String,
        mtlPath: String?,
        sizePx: Int = 128,
        tilt: Float = -22f,
        turn: Float = 32f,
        colorGamma: Float = 1f,   // <1 brightens dark materials
        fitSpan: Float = 1.7f,    // model span after scaling (smaller = more margin)
    ): Bitmap? {
        val key = "$objPath|$mtlPath|$sizePx|$tilt|$turn|$colorGamma|$fitSpan"
        return cache[key] ?: runCatching { render(context, objPath, mtlPath, sizePx, tilt, turn, colorGamma, fitSpan) }
            .getOrNull()
            ?.also { cache[key] = it }
    }

    // ── Offscreen render ──────────────────────────────────────────────────────
    private fun render(
        context: Context, objPath: String, mtlPath: String?,
        size: Int, tilt: Float, turn: Float, colorGamma: Float, fitSpan: Float,
    ): Bitmap {
        val groups = ObjLoader.load(context.assets, objPath, mtlPath)

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

        val egl = Egl(size)
        try {
            egl.makeCurrent()
            GLES20.glViewport(0, 0, size, size)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)   // obj winding is unreliable
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            val prog = buildProgram()
            GLES20.glUseProgram(prog)
            val aPos = GLES20.glGetAttribLocation(prog, "aPos")
            val aNrm = GLES20.glGetAttribLocation(prog, "aNormal")
            val uMVP = GLES20.glGetUniformLocation(prog, "uMVP")
            val uModel = GLES20.glGetUniformLocation(prog, "uModel")
            val uColor = GLES20.glGetUniformLocation(prog, "uColor")

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

            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)

            for (g in groups) {
                val buf = buildInterleaved(g.verts) ?: continue
                // Groups with no material land at (0,0,0); nudge to a neutral gray.
                val emptyMat = g.r == 0f && g.g == 0f && g.b == 0f
                var r = if (emptyMat) 0.6f else g.r
                var gg = if (emptyMat) 0.6f else g.g
                var b = if (emptyMat) 0.6f else g.b
                if (colorGamma != 1f) { r = r.pow(colorGamma); gg = gg.pow(colorGamma); b = b.pow(colorGamma) }
                GLES20.glUniform3f(uColor, r, gg, b)
                buf.position(0)
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 6 * 4, buf)
                GLES20.glEnableVertexAttribArray(aPos)
                buf.position(3)
                GLES20.glVertexAttribPointer(aNrm, 3, GLES20.GL_FLOAT, false, 6 * 4, buf)
                GLES20.glEnableVertexAttribArray(aNrm)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, g.verts.size / 3)
            }

            // Read pixels (bottom-up) → Bitmap, then flip vertically.
            val pix = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pix)
            pix.rewind()
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(pix)
            val flip = AndroidMatrix().apply { postScale(1f, -1f) }
            return Bitmap.createBitmap(bmp, 0, 0, size, size, flip, false)
        } finally {
            egl.release()
        }
    }

    /** Position(3)+flat-normal(3) interleaved buffer, one flat normal per triangle. */
    private fun buildInterleaved(verts: FloatArray): FloatBuffer? {
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
        return ByteBuffer.allocateDirect(out.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(out); position(0) }
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
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f); GLES20.glLinkProgram(p)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        return s
    }

    // ── Minimal EGL pbuffer wrapper ───────────────────────────────────────────
    private class Egl(size: Int) {
        private val display: EGLDisplay
        private val context: EGLContext
        private val surface: EGLSurface

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)
            val cfg = chooseConfig()
            val ctxAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, cfg, EGL14.EGL_NO_CONTEXT, ctxAttrs, 0)
            val surfAttrs = intArrayOf(EGL14.EGL_WIDTH, size, EGL14.EGL_HEIGHT, size, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, cfg, surfAttrs, 0)
        }

        private fun chooseConfig(): EGLConfig {
            val base = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
            )
            val out = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            // Try with 4x MSAA for smoother edges; fall back if unavailable.
            val msaa = base + intArrayOf(EGL14.EGL_SAMPLE_BUFFERS, 1, EGL14.EGL_SAMPLES, 4, EGL14.EGL_NONE)
            if (EGL14.eglChooseConfig(display, msaa, 0, out, 0, 1, num, 0) && num[0] > 0) return out[0]!!
            val plain = base + intArrayOf(EGL14.EGL_NONE)
            EGL14.eglChooseConfig(display, plain, 0, out, 0, 1, num, 0)
            return out[0]!!
        }

        fun makeCurrent() = EGL14.eglMakeCurrent(display, surface, surface, context)

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
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
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = null, objPath, mtlPath, sizePx, tilt, turn, colorGamma, fitSpan) {
        value = withContext(Dispatchers.Default) {
            ModelBitmapRenderer.get(context, objPath, mtlPath, sizePx, tilt, turn, colorGamma, fitSpan)?.asImageBitmap()
        }
    }
    return state.value
}
