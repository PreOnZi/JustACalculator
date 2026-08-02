package com.fictioncutshort.justacalculator.platform

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

private val audioManager: AudioManager
    get() = AppInit.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

internal actual fun platformVolumeFraction(): Float = runCatching {
    val am = audioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}.getOrDefault(1f)

internal actual fun platformIsWirelessOutput(): Boolean = runCatching {
    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    }
}.getOrDefault(false)
