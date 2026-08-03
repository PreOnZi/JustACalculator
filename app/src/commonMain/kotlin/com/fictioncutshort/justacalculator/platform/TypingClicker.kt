package com.fictioncutshort.justacalculator.platform

/**
 * The story's non-music audio: the per-character typing click, the dormancy
 * static, and the realtime mic echo used during the phone detour.
 *
 * All of it is synthesised in shared code by TalkAudioHandler; only the PCM
 * sink and the microphone are platform, behind the `Pcm` seam. The interface
 * survives so call sites can hold a nullable handler without caring.
 */
interface TypingClicker {
    fun playTypingClick()

    /** Dormancy static; [onComplete] fires when it finishes. */
    fun playStaticSound(onComplete: () -> Unit = {}) = onComplete()

    /** Begin echoing the mic back at the player. */
    fun startRealtimeEcho(decay: Float = 0.18f, distortion: Float = 1.0f) = Unit

    fun stopRealtimeEcho() = Unit

    /**
     * Drop captured audio before it reaches the speaker, without tearing the
     * graph down — the call screen mutes the player while the calculator talks.
     */
    fun setEchoMuted(muted: Boolean) = Unit
}
