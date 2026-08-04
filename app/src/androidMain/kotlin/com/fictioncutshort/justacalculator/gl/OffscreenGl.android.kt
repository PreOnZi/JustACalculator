package com.fictioncutshort.justacalculator.gl

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.nio.ByteBuffer

/** A minimal EGL pbuffer. */
actual class OffscreenGl actual constructor(size: Int) {

    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val context: EGLContext
    private val surface: EGLSurface

    init {
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

    actual fun makeCurrent(): Boolean = EGL14.eglMakeCurrent(display, surface, surface, context)

    actual fun release() {
        EGL14.eglMakeCurrent(
            display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
        )
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }
}

actual fun imageBitmapFromRgba(
    width: Int,
    height: Int,
    pixels: ByteArray,
    flipVertically: Boolean,
): ImageBitmap {
    val source = if (flipVertically) flipRows(width, height, pixels) else pixels
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(source))
    return bitmap.asImageBitmap()
}
