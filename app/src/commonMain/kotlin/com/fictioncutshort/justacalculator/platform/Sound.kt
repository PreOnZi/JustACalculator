package com.fictioncutshort.justacalculator.platform

/**
 * A single playing sound, shaped after `android.media.MediaPlayer` so the
 * existing call sites read the same. Backed by MediaPlayer on Android and
 * AVAudioPlayer on iOS.
 *
 * Sounds are addressed by asset path (`"raw/beep.mp3"`) rather than by
 * `R.raw.beep`, because resource IDs are an Android-only concept. [Sounds.path]
 * maps the old names.
 */
interface SoundPlayer {
    fun start()
    fun pause()
    fun stop()
    fun release()
    fun seekTo(positionMs: Int)
    fun setVolume(left: Float, right: Float)
    fun setLooping(looping: Boolean)
    fun setOnCompletion(action: () -> Unit)

    val isPlaying: Boolean

    /** Track length in milliseconds, or 0 if not yet known. */
    val duration: Int
}

/**
 * Creates a player for a bundled audio asset, or null when the asset is missing
 * or undecodable — matching `MediaPlayer.create`, which also returns null rather
 * than throwing, and which every call site already null-checks.
 */
expect fun createSoundPlayer(path: String): SoundPlayer?

/**
 * Resolves the old `R.raw.<name>` identifiers to asset paths.
 *
 * The audio moved out of `res/raw` into the shared asset tree so iOS can ship
 * the same files; extensions vary (mp3/m4a/mp4), which Android's resource system
 * used to hide, so the mapping is resolved once against what is actually bundled.
 */
object Sounds {
    private val extensions = listOf("mp3", "m4a", "wav", "mp4", "ogg")

    private val resolved = mutableMapOf<String, String?>()

    /** `path("beep")` → `"raw/beep.mp3"`, or null if no such asset ships. */
    fun path(name: String): String? = resolved.getOrPut(name) {
        extensions.map { "raw/$name.$it" }.firstOrNull { Assets.exists(it) }
    }
}
