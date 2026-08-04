package com.fictioncutshort.justacalculator.gl

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.Pinned

/**
 * A pinned FloatArray.
 *
 * Kotlin/Native's GC can move objects, and GL keeps the pointer it was given, so
 * the array is pinned for the buffer's whole lifetime rather than per-call.
 * [dispose] unpins it; long-lived vertex buffers are simply never disposed,
 * which matches how the renderers hold them.
 */
@OptIn(ExperimentalForeignApi::class)
actual class GlFloatBuffer(private val data: FloatArray) {
    private var pinned: Pinned<FloatArray>? = data.pin()
    private var pos: Int = 0

    /**
     * The address GL reads from, offset to the current position.
     *
     * The offset matters: the interleaved-vertex call sites set `position(3)`
     * between the position and texcoord attributes and expect the pointer to
     * move with it, the way a java.nio FloatBuffer's does. Returning
     * addressOf(0) unconditionally made every attribute read the same data.
     */
    val pointer: CPointer<FloatVar>?
        get() = if (data.isEmpty()) null else pinned?.addressOf(pos)

    /** Direct access for the paths that copy rather than hand over a pointer. */
    val array: FloatArray get() = data

    actual fun put(src: FloatArray): GlFloatBuffer = apply {
        src.copyInto(data, pos)
        pos += src.size
    }

    actual fun put(src: FloatArray, offset: Int, count: Int): GlFloatBuffer = apply {
        src.copyInto(data, pos, offset, offset + count)
        pos += count
    }

    actual fun position(newPosition: Int): GlFloatBuffer = apply { pos = newPosition }
    actual fun position(): Int = pos
    actual fun clear(): GlFloatBuffer = apply { pos = 0 }
    actual fun rewind(): GlFloatBuffer = apply { pos = 0 }

    actual fun get(dst: FloatArray): GlFloatBuffer = apply {
        data.copyInto(dst, 0, pos, pos + dst.size)
        pos += dst.size
    }

    actual val capacity: Int get() = data.size

    actual fun dispose() {
        pinned?.unpin()
        pinned = null
    }

    actual fun get(index: Int): Float = data[index]
    actual fun put(index: Int, value: Float): GlFloatBuffer = apply { data[index] = value }

}

actual fun glFloatBuffer(capacity: Int): GlFloatBuffer = GlFloatBuffer(FloatArray(capacity))
