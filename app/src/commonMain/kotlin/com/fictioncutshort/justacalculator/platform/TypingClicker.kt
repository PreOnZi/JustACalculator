package com.fictioncutshort.justacalculator.platform

/**
 * The per-character click the calculator makes while "typing" a message.
 *
 * Narrowed to this one method on purpose: EffectsController only needs the
 * click, while the full TalkAudioHandler also owns realtime mic echo built on
 * AudioRecord/AudioTrack, which has no shared equivalent yet.
 */
interface TypingClicker {
    fun playTypingClick()
}
