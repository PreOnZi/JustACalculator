package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.gl.glFloatBuffer
import com.fictioncutshort.justacalculator.gl.toGlBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two GlFloatBuffer implementations must agree on position semantics.
 * A mismatch would not fail to compile — it would upload geometry at the wrong
 * offset and render garbage, which is far harder to trace than a crash.
 */
class GlBufferTest {

    @Test
    fun putAdvancesPosition() {
        val b = glFloatBuffer(8)
        assertEquals(0, b.position())
        b.put(floatArrayOf(1f, 2f, 3f))
        assertEquals(3, b.position())
    }

    @Test
    fun toGlBufferLeavesPositionAtZeroReadyToDraw() {
        val b = floatArrayOf(1f, 2f, 3f, 4f).toGlBuffer()
        assertEquals(0, b.position(), "must be rewound so GL reads from the start")
        assertEquals(4, b.capacity)
    }

    @Test
    fun clearAndRewindResetPosition() {
        val b = glFloatBuffer(4).put(floatArrayOf(1f, 2f))
        assertEquals(2, b.position())
        b.clear()
        assertEquals(0, b.position())
        b.put(floatArrayOf(9f)).rewind()
        assertEquals(0, b.position())
    }

    @Test
    fun roundTripsContents() {
        val src = floatArrayOf(1.5f, -2.5f, 3.25f, 0f)
        val out = FloatArray(4)
        src.toGlBuffer().get(out)
        assertTrue(src.contentEquals(out), "expected ${src.toList()}, got ${out.toList()}")
    }

    @Test
    fun readsFromTheCurrentPositionNotTheStart() {
        val b = floatArrayOf(1f, 2f, 3f, 4f).toGlBuffer()
        b.position(2)
        val out = FloatArray(2)
        b.get(out)
        assertTrue(floatArrayOf(3f, 4f).contentEquals(out), "got ${out.toList()}")
    }

    @Test
    fun rangedPutCopiesOnlyTheRequestedWindow() {
        val b = glFloatBuffer(3)
        b.put(floatArrayOf(0f, 1f, 2f, 3f, 4f), 1, 3)
        assertEquals(3, b.position())
        val out = FloatArray(3)
        b.position(0).get(out)
        assertTrue(floatArrayOf(1f, 2f, 3f).contentEquals(out), "got ${out.toList()}")
    }

    @Test
    fun successivePutsAppend() {
        val b = glFloatBuffer(4)
        b.put(floatArrayOf(1f, 2f)).put(floatArrayOf(3f, 4f))
        val out = FloatArray(4)
        b.position(0).get(out)
        assertTrue(floatArrayOf(1f, 2f, 3f, 4f).contentEquals(out), "got ${out.toList()}")
    }
}
