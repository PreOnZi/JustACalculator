package com.fictioncutshort.justacalculator.platform

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortBluetoothA2DP
import platform.AVFAudio.AVAudioSessionPortBluetoothHFP
import platform.AVFAudio.AVAudioSessionPortBluetoothLE
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.currentRoute
import platform.AVFAudio.outputVolume

internal actual fun platformVolumeFraction(): Float =
    AVAudioSession.sharedInstance().outputVolume

internal actual fun platformIsWirelessOutput(): Boolean = runCatching {
    // AirPlay is deliberately not counted: unlike Bluetooth, its volume is
    // still meaningfully reflected by the device slider.
    AVAudioSession.sharedInstance().currentRoute.outputs.any { output ->
        val port = (output as? AVAudioSessionPortDescription)?.portType
        port == AVAudioSessionPortBluetoothA2DP ||
            port == AVAudioSessionPortBluetoothHFP ||
            port == AVAudioSessionPortBluetoothLE
    }
}.getOrDefault(false)
