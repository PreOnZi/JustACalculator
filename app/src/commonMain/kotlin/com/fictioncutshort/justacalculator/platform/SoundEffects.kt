package com.fictioncutshort.justacalculator.platform

/**
 * Low-latency, overlapping sound effects — Android's SoundPool, and a small
 * bank of preloaded players on iOS.
 *
 * Distinct from [SoundPlayer], which is for one long clip at a time (narration,
 * music). This is for short cues that fire repeatedly and must be able to
 * overlap without cutting each other off.
 */
expect class SoundEffectPool {
    /** Preloads [assetPath]; returns a handle, or 0 if the asset is missing. */
    fun load(assetPath: String): Int

    /** Fires a loaded effect at [volume] (0f..1f). Safe to call while it plays. */
    fun play(id: Int, volume: Float)

    fun release()
}

expect fun createSoundEffectPool(maxStreams: Int = 16): SoundEffectPool
