package com.fictioncutshort.justacalculator.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL
import platform.darwin.NSObject
import kotlin.math.roundToInt

/**
 * AVAudioPlayer counterpart to Android's MediaPlayer.
 *
 * The audio session is configured once, on first use, for Playback — otherwise
 * iOS honours the ringer switch and the story's narration goes silent on a
 * muted phone, which is not how the Android build behaves.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun createSoundPlayer(path: String): SoundPlayer? {
    configureAudioSessionOnce()
    val url = NSURL.fileURLWithPath(Assets.uri(path).removePrefix("file://"))
    val player = AVAudioPlayer(contentsOfURL = url, error = null) ?: return null
    return if (player.prepareToPlay()) IosSoundPlayer(player) else null
}

private var audioSessionConfigured = false

@OptIn(ExperimentalForeignApi::class)
private fun configureAudioSessionOnce() {
    if (audioSessionConfigured) return
    audioSessionConfigured = true
    runCatching {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
    }
}

private class IosSoundPlayer(private val player: AVAudioPlayer) : SoundPlayer {

    // Retained so ARC does not collect the delegate while playback is running.
    private var completionDelegate: CompletionDelegate? = null

    override fun start() { player.play() }
    override fun pause() { player.pause() }

    override fun stop() {
        player.stop()
        // MediaPlayer.stop() rewinds; AVAudioPlayer.stop() only pauses.
        player.setCurrentTime(0.0)
    }

    override fun release() {
        player.stop()
        completionDelegate = null
    }

    override fun seekTo(positionMs: Int) {
        player.setCurrentTime(positionMs / 1000.0)
    }

    override fun setVolume(left: Float, right: Float) {
        // AVAudioPlayer has a single volume; Android call sites always pass the
        // same value for both channels.
        player.setVolume((left + right) / 2f)
    }

    override fun setLooping(looping: Boolean) {
        // Negative loops forever, matching MediaPlayer's isLooping.
        player.setNumberOfLoops(if (looping) -1 else 0)
    }

    override fun setOnCompletion(action: () -> Unit) {
        val delegate = CompletionDelegate(action)
        completionDelegate = delegate
        player.setDelegate(delegate)
    }

    override val isPlaying: Boolean get() = player.isPlaying()

    override val duration: Int get() = (player.duration * 1000.0).roundToInt()
}

private class CompletionDelegate(
    private val action: () -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        action()
    }
}
