package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.ui.components.warnBelow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The volume banner has to hold two things at once: never nag a player who has
 * said their Bluetooth speaker is loud enough, and still speak up when the sound
 * genuinely goes away. These pin that trade.
 */
class LowVolumeWarningTest {

    private val bluetoothBar = 0.40f
    private val speakerBar = 0.22f
    private val notAccepted = -1f

    private fun warns(frac: Float, bar: Float, accepted: Float) = frac < warnBelow(bar, accepted)

    @Test
    fun warnsOnAQuietDeviceBeforeAnythingIsAccepted() {
        assertTrue(warns(0.30f, bluetoothBar, notAccepted), "0.30 on Bluetooth is under the bar")
        assertTrue(warns(0.10f, speakerBar, notAccepted), "0.10 on the speaker is under the bar")
        assertFalse(warns(0.60f, bluetoothBar, notAccepted), "0.60 is fine")
    }

    @Test
    fun aLevelTheUserAcceptedIsNeverNaggedAgain() {
        // The reported case: a Bluetooth speaker sitting at a third of full scale,
        // under the bar, which the player has said is loud enough.
        val accepted = 0.30f
        assertFalse(warns(accepted, bluetoothBar, accepted), "the accepted level itself")
        assertFalse(warns(accepted + 0.05f, bluetoothBar, accepted), "nudged up")
        assertFalse(warns(accepted - 0.05f, bluetoothBar, accepted), "a nudge down, within tolerance")
    }

    @Test
    fun turningItDownAfterAcceptingWarnsAgain() {
        val accepted = 0.30f
        assertTrue(warns(0.15f, bluetoothBar, accepted), "clearly turned down since")
        assertTrue(warns(0f, bluetoothBar, accepted), "silence is always worth saying")
    }

    @Test
    fun acceptingOnOneRouteSaysNothingAboutTheOther() {
        // Accepting 0.30 on the speaker must not raise the Bluetooth bar, and the
        // call sites keep the two levels apart — this is the value-level half.
        val onSpeaker = warnBelow(speakerBar, 0.30f)
        val onBluetooth = warnBelow(bluetoothBar, notAccepted)
        assertTrue(onBluetooth > onSpeaker, "an untouched route keeps its own bar")
    }

    @Test
    fun acceptingSilenceStopsTheBannerOnThatRoute() {
        // Their call to make: they were told once and said it was fine.
        assertFalse(warns(0f, speakerBar, 0f), "accepted at mute")
    }

    @Test
    fun acceptingALoudLevelLeavesTheNormalBarInPlace() {
        // Dismissing at 0.9 must not push the bar up to 0.82 and start nagging
        // everyone at ordinary volumes.
        assertFalse(warns(0.50f, bluetoothBar, 0.90f), "0.50 is above the base bar")
        assertTrue(warns(0.30f, bluetoothBar, 0.90f), "0.30 is still under it")
    }
}
