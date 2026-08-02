package com.fictioncutshort.justacalculator.platform

/**
 * Where the sound is going and how loud.
 *
 * The city nags the player once if the volume is too low to hear the narration,
 * and suppresses the nag on Bluetooth, where the system volume does not reflect
 * what the listener actually hears.
 */
object AudioOutput {
    /** Current media volume as 0f..1f. */
    fun volumeFraction(): Float = platformVolumeFraction()

    /** True when audio is routed to a wireless output. */
    fun isWireless(): Boolean = platformIsWirelessOutput()
}

internal expect fun platformVolumeFraction(): Float
internal expect fun platformIsWirelessOutput(): Boolean
