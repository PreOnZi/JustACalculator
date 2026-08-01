package com.fictioncutshort.justacalculator.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * The direct ByteBuffer the renderers already used — unchanged behaviour, just
 * behind the shared type.
 */
actual class GlFloatBuffer(internal val nio: FloatBuffer) {
    actual fun put(src: FloatArray): GlFloatBuffer = apply { nio.put(src) }

    actual fun put(src: FloatArray, offset: Int, count: Int): GlFloatBuffer =
        apply { nio.put(src, offset, count) }

    actual fun position(newPosition: Int): GlFloatBuffer = apply { nio.position(newPosition) }
    actual fun position(): Int = nio.position()
    actual fun clear(): GlFloatBuffer = apply { nio.clear() }
    actual fun rewind(): GlFloatBuffer = apply { nio.rewind() }
    actual fun get(dst: FloatArray): GlFloatBuffer = apply { nio.get(dst) }

    actual val capacity: Int get() = nio.capacity()

    // The JVM reclaims direct buffers itself; nothing to release.
    actual fun dispose() = Unit
}

actual fun glFloatBuffer(capacity: Int): GlFloatBuffer =
    GlFloatBuffer(
        ByteBuffer.allocateDirect(capacity * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer(),
    )
