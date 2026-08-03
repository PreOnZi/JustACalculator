package com.fictioncutshort.justacalculator.platform

/**
 * Raw PCM in and out, for the audio the story synthesises rather than plays
 * from a file: the typing click, the dormancy static, the feedback squeal and
 * the phone detour's mic echo.
 *
 * Everything is **16-bit signed mono**, which is what all the generators
 * already produce. The waveform maths stays in shared code; only the sink and
 * the microphone are platform.
 */

/** A streaming PCM output. Writes block until there is room, like AudioTrack. */
interface PcmSink {
    fun write(samples: ShortArray, count: Int)

    /** Stops playback and frees the device. Safe to call twice. */
    fun close()
}

/**
 * Opens a 16-bit mono output at [sampleRate], or returns null when audio is
 * unavailable. [bufferSamples] is a hint; platforms round it to their own
 * minimum.
 */
expect fun openPcmSink(sampleRate: Int, bufferSamples: Int): PcmSink?

/** A running full-duplex loop. */
interface MicEcho {
    fun stop()
}

/**
 * Starts capturing the microphone and playing the result back.
 *
 * Each captured block is handed to [process] along with the rate it was
 * captured at — the rate is a callback parameter rather than a fixed constant
 * because iOS hands over whatever the hardware is running at, and a delay line
 * measured in milliseconds has to be sized from the real rate. The returned
 * array is what gets played; returning null drops the block, which is how the
 * call screen mutes the player while the calculator is talking.
 *
 * Returns null when the microphone is unavailable or permission was refused —
 * the caller treats that as "no echo" rather than an error.
 */
expect fun startMicEcho(
    process: (samples: ShortArray, count: Int, sampleRate: Int) -> ShortArray?,
): MicEcho?
