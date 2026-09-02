package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.ui.screens.PITCH_LIMIT
import com.fictioncutshort.justacalculator.ui.screens.aimForward
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The city gun fires along [aimForward], and so does the first-person camera.
 * They must stay the same vector: the reticle is pinned to the centre of the
 * screen, so anything the two disagree about is the round missing where the
 * player aimed.
 */
class AimForwardTest {

    private fun assertClose(expected: Float, actual: Float, what: String) {
        assertTrue(abs(expected - actual) < 1e-4f, "$what: expected $expected, got $actual")
    }

    @Test
    fun levelAimHasNoVerticalComponent() {
        for (yaw in listOf(-180f, -90f, 0f, 37f, 90f, 180f)) {
            assertClose(0f, aimForward(yaw, 0f)[1], "yaw $yaw at level")
        }
    }

    @Test
    fun lookingUpAimsUpAndLookingDownAimsDown() {
        assertTrue(aimForward(0f, 45f)[1] > 0f, "positive pitch should aim up")
        assertTrue(aimForward(0f, -45f)[1] < 0f, "negative pitch should aim down")
        // Symmetric about level.
        assertClose(aimForward(0f, 45f)[1], -aimForward(0f, -45f)[1], "up/down symmetry")
    }

    @Test
    fun yawZeroPointsDownNegativeZ() {
        // The convention the whole city walks on: yaw 0 is -Z, yaw 90 is +X.
        val ahead = aimForward(0f, 0f)
        assertClose(0f, ahead[0], "x at yaw 0")
        assertClose(-1f, ahead[2], "z at yaw 0")
        val right = aimForward(90f, 0f)
        assertClose(1f, right[0], "x at yaw 90")
        assertClose(0f, right[2], "z at yaw 90")
    }

    @Test
    fun alwaysUnitLength() {
        for (yaw in listOf(-175f, -63f, 0f, 12f, 88f, 179f)) {
            for (pitch in listOf(-PITCH_LIMIT, -30f, 0f, 30f, PITCH_LIMIT)) {
                val a = aimForward(yaw, pitch)
                val len = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
                assertTrue(abs(len - 1f) < 1e-4f, "yaw $yaw pitch $pitch: length $len")
            }
        }
    }

    @Test
    fun pitchTiltsWithoutChangingHeading() {
        // Looking up must not swing the shot sideways: the horizontal part stays
        // on the same bearing, only shorter.
        for (yaw in listOf(0f, 37f, 145f, -100f)) {
            val level = aimForward(yaw, 0f)
            for (pitch in listOf(-PITCH_LIMIT, -20f, 20f, PITCH_LIMIT)) {
                val tilted = aimForward(yaw, pitch)
                val horiz = sqrt(tilted[0] * tilted[0] + tilted[2] * tilted[2])
                assertTrue(horiz > 1e-3f, "yaw $yaw pitch $pitch: no horizontal left")
                assertClose(level[0], tilted[0] / horiz, "yaw $yaw pitch $pitch bearing x")
                assertClose(level[2], tilted[2] / horiz, "yaw $yaw pitch $pitch bearing z")
            }
        }
    }
}
