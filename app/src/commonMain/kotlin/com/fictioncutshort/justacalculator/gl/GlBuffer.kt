package com.fictioncutshort.justacalculator.gl

/**
 * A native-order float buffer that GL can read directly.
 *
 * Shaped after `java.nio.FloatBuffer` — same `put`/`position`/`clear` chaining —
 * so the renderers' existing buffer plumbing ports unchanged.
 *
 * The memory has to be **native-ordered and non-moving**: `glVertexAttribPointer`
 * and `glBufferData` are handed a raw address and read it later, so a
 * garbage-collected Kotlin array cannot be passed straight through. Android uses
 * a direct ByteBuffer; iOS pins a FloatArray for the lifetime of the buffer.
 */
expect class GlFloatBuffer {
    /** Bulk-writes [src] at the current position and advances it. */
    fun put(src: FloatArray): GlFloatBuffer

    /** Writes [src] at the current position, advancing by [count] floats. */
    fun put(src: FloatArray, offset: Int, count: Int): GlFloatBuffer

    fun position(newPosition: Int): GlFloatBuffer
    fun position(): Int
    fun clear(): GlFloatBuffer
    fun rewind(): GlFloatBuffer

    /** Bulk-reads into [dst] from the current position. */
    fun get(dst: FloatArray): GlFloatBuffer

    val capacity: Int

    /** Releases the pinned memory. Using the buffer afterwards is undefined. */
    fun dispose()

    /** Absolute indexed read — does not move the position. */
    fun get(index: Int): Float

    /** Absolute indexed write — does not move the position. */
    fun put(index: Int, value: Float): GlFloatBuffer

}

/** Allocates an empty buffer of [capacity] floats. */
expect fun glFloatBuffer(capacity: Int): GlFloatBuffer

/** Allocates a buffer holding this array, positioned at 0 and ready to draw. */
fun FloatArray.toGlBuffer(): GlFloatBuffer =
    glFloatBuffer(size).put(this).position(0)
