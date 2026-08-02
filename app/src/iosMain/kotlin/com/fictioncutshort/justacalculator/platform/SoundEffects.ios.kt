package com.fictioncutshort.justacalculator.platform

/**
 * AVAudioPlayer cannot overlap a sound with itself — calling play() on an
 * already-playing instance just restarts it. SoundPool can, and the tower
 * defence relies on that (several enemies dying at once). So each loaded effect
 * keeps a small ring of identical players and play() picks one that is idle.
 */
actual class SoundEffectPool(private val voicesPerEffect: Int) {

    private val banks = mutableMapOf<Int, List<SoundPlayer>>()
    private var nextId = 1

    actual fun load(assetPath: String): Int {
        val voices = (0 until voicesPerEffect).mapNotNull { createSoundPlayer(assetPath) }
        if (voices.isEmpty()) return 0
        val id = nextId++
        banks[id] = voices
        return id
    }

    actual fun play(id: Int, volume: Float) {
        val voices = banks[id] ?: return
        // Reuse an idle voice; if they are all busy, restart the first — the
        // same audible result as SoundPool dropping its oldest stream.
        val voice = voices.firstOrNull { !it.isPlaying } ?: voices.first()
        voice.setVolume(volume, volume)
        voice.seekTo(0)
        voice.start()
    }

    actual fun release() {
        banks.values.flatten().forEach { it.release() }
        banks.clear()
    }
}

// Four voices per effect covers the overlap the games actually produce without
// preloading a large number of decoders.
actual fun createSoundEffectPool(maxStreams: Int): SoundEffectPool =
    SoundEffectPool(voicesPerEffect = 4)
