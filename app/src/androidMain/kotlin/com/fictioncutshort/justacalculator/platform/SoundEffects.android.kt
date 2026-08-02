package com.fictioncutshort.justacalculator.platform

import android.media.AudioAttributes
import android.media.SoundPool

actual class SoundEffectPool(private val pool: SoundPool) {
    actual fun load(assetPath: String): Int = try {
        AppInit.context.assets.openFd(assetPath).use { pool.load(it, 1) }
    } catch (_: Exception) {
        0
    }

    actual fun play(id: Int, volume: Float) {
        if (id != 0) pool.play(id, volume, volume, 1, 0, 1f)
    }

    actual fun release() = pool.release()
}

actual fun createSoundEffectPool(maxStreams: Int): SoundEffectPool = SoundEffectPool(
    SoundPool.Builder()
        .setMaxStreams(maxStreams)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build(),
)
