package com.fictioncutshort.justacalculator.gl

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import platform.EAGL.EAGLContext
import platform.EAGL.kEAGLRenderingAPIOpenGLES2

/**
 * EAGL counterpart to the EGL pbuffer.
 *
 * iOS has no pbuffer: an EAGLContext draws into a framebuffer, so the offscreen
 * target is a plain FBO with colour and depth renderbuffers. `glReadPixels`
 * reads from whatever framebuffer is bound, which is what the caller does next.
 *
 * No multisampling here. The Android path asks for 4x MSAA and falls back when
 * it is unavailable; on iOS the equivalent needs a second framebuffer and an
 * explicit resolve, which is a lot of machinery for icons this small.
 */
actual class OffscreenGl actual constructor(private val size: Int) {

    private val context: EAGLContext? = EAGLContext(kEAGLRenderingAPIOpenGLES2)
    private val names = IntArray(3)   // framebuffer, colour, depth
    private var previous: EAGLContext? = null

    actual fun makeCurrent(): Boolean {
        val ctx = context ?: return false
        previous = EAGLContext.currentContext()
        if (!EAGLContext.setCurrentContext(ctx)) return false

        val one = IntArray(1)
        Gl.glGenFramebuffers(1, one, 0); names[0] = one[0]
        Gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, names[0])

        Gl.glGenRenderbuffers(1, one, 0); names[1] = one[0]
        Gl.glBindRenderbuffer(Gl.GL_RENDERBUFFER, names[1])
        Gl.glRenderbufferStorage(Gl.GL_RENDERBUFFER, Gl.GL_RGBA8, size, size)
        Gl.glFramebufferRenderbuffer(
            Gl.GL_FRAMEBUFFER, Gl.GL_COLOR_ATTACHMENT0, Gl.GL_RENDERBUFFER, names[1],
        )

        Gl.glGenRenderbuffers(1, one, 0); names[2] = one[0]
        Gl.glBindRenderbuffer(Gl.GL_RENDERBUFFER, names[2])
        Gl.glRenderbufferStorage(Gl.GL_RENDERBUFFER, Gl.GL_DEPTH_COMPONENT16, size, size)
        Gl.glFramebufferRenderbuffer(
            Gl.GL_FRAMEBUFFER, Gl.GL_DEPTH_ATTACHMENT, Gl.GL_RENDERBUFFER, names[2],
        )

        return Gl.glCheckFramebufferStatus(Gl.GL_FRAMEBUFFER) == Gl.GL_FRAMEBUFFER_COMPLETE
    }

    actual fun release() {
        if (names[0] != 0) {
            Gl.glDeleteFramebuffers(1, intArrayOf(names[0]), 0)
            Gl.glDeleteRenderbuffers(1, intArrayOf(names[1]), 0)
            Gl.glDeleteRenderbuffers(1, intArrayOf(names[2]), 0)
            names.fill(0)
        }
        // Hand the thread back whatever context it had, so a caller that
        // borrowed this from inside another render pass is unaffected.
        EAGLContext.setCurrentContext(previous)
        previous = null
    }
}

actual fun imageBitmapFromRgba(
    width: Int,
    height: Int,
    pixels: ByteArray,
    flipVertically: Boolean,
): ImageBitmap {
    val rows = if (flipVertically) flipRows(width, height, pixels) else pixels
    // Skia's N32 is little-endian BGRA here, while GL hands back RGBA — so red
    // and blue swap on the way in.
    val bgra = ByteArray(rows.size)
    var i = 0
    while (i < rows.size) {
        bgra[i] = rows[i + 2]
        bgra[i + 1] = rows[i + 1]
        bgra[i + 2] = rows[i]
        bgra[i + 3] = rows[i + 3]
        i += 4
    }
    val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
    bitmap.installPixels(bgra)
    return bitmap.asComposeImageBitmap()
}
