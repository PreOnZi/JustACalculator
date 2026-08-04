package com.fictioncutshort.justacalculator.gl

import androidx.compose.ui.graphics.ImageBitmap

/**
 * A throwaway GL context that renders into memory rather than onto the screen,
 * for turning models into flat sprites.
 *
 * Deliberately separate from [PlatformGlSurface]: it must never touch the
 * on-screen context, and it is used from a background thread while that one is
 * mid-frame.
 *
 * Create, [makeCurrent], draw, read back, [release]. Not reusable.
 */
expect class OffscreenGl(size: Int) {
    /** False if the context could not be created; nothing else is safe then. */
    fun makeCurrent(): Boolean

    fun release()
}

/**
 * Builds an image from tightly-packed RGBA bytes.
 *
 * `glReadPixels` hands rows back bottom-up, so [flipVertically] undoes that —
 * doing it here rather than with a platform image transform keeps the two
 * platforms from disagreeing about which way up a sprite is.
 */
expect fun imageBitmapFromRgba(
    width: Int,
    height: Int,
    pixels: ByteArray,
    flipVertically: Boolean = false,
): ImageBitmap

/** Reverses row order in a tightly-packed RGBA buffer. */
internal fun flipRows(width: Int, height: Int, pixels: ByteArray): ByteArray {
    val stride = width * 4
    val out = ByteArray(pixels.size)
    for (row in 0 until height) {
        pixels.copyInto(
            destination = out,
            destinationOffset = (height - 1 - row) * stride,
            startIndex = row * stride,
            endIndex = row * stride + stride,
        )
    }
    return out
}
