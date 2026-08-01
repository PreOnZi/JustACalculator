package com.fictioncutshort.justacalculator.platform

/**
 * The story's non-music audio: the per-character typing click, the dormancy
 * static, and the realtime mic echo used during the phone detour.
 *
 * Android implements all of it in TalkAudioHandler (AudioRecord + AudioTrack).
 * The iOS actual is a no-op until that ports — the beats still advance, they are
 * just silent, which is preferable to blocking the whole story on an
 * AVAudioEngine port.
 */
interface TypingClicker {
    fun playTypingClick()

    /** Dormancy static; [onComplete] fires when it finishes. */
    fun playStaticSound(onComplete: () -> Unit = {}) = onComplete()

    /** Begin echoing the mic back at the player. */
    fun startRealtimeEcho(decay: Float = 0.18f, distortion: Float = 1.0f) = Unit

    fun stopRealtimeEcho() = Unit
}
