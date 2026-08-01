package com.fictioncutshort.justacalculator.platform

import android.media.MediaPlayer

/**
 * Straight MediaPlayer, prepared synchronously from the packaged asset — the
 * same behaviour `MediaPlayer.create(context, R.raw.x)` had.
 */
actual fun createSoundPlayer(path: String): SoundPlayer? = try {
    val player = MediaPlayer()
    AppInit.context.assets.openFd(path).use { fd ->
        player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
    }
    player.prepare()
    AndroidSoundPlayer(player)
} catch (_: Exception) {
    // MediaPlayer.create() also swallowed failures and returned null; call sites
    // all null-check, and a missing sound must never take the story down.
    null
}

private class AndroidSoundPlayer(private val player: MediaPlayer) : SoundPlayer {
    override fun start() = player.start()
    override fun pause() = player.pause()
    override fun stop() = player.stop()
    override fun release() = player.release()
    override fun seekTo(positionMs: Int) = player.seekTo(positionMs)
    override fun setVolume(left: Float, right: Float) = player.setVolume(left, right)
    override fun setLooping(looping: Boolean) { player.isLooping = looping }

    override fun setOnCompletion(action: () -> Unit) {
        player.setOnCompletionListener { action() }
    }

    override val isPlaying: Boolean
        get() = runCatching { player.isPlaying }.getOrDefault(false)

    override val duration: Int
        get() = runCatching { player.duration }.getOrDefault(0)
}
