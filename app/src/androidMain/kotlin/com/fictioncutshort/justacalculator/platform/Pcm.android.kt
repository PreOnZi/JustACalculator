package com.fictioncutshort.justacalculator.platform

import android.Manifest
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/** AudioRecord and AudioTrack, exactly as the original TalkAudioHandler used them. */

private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

private fun buildTrack(sampleRate: Int, bufferBytes: Int, speech: Boolean): AudioTrack =
    AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(
                    if (speech) AudioAttributes.CONTENT_TYPE_SPEECH
                    else AudioAttributes.CONTENT_TYPE_SONIFICATION
                )
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(bufferBytes)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

actual fun openPcmSink(sampleRate: Int, bufferSamples: Int): PcmSink? = runCatching {
    val track = buildTrack(sampleRate, bufferSamples * 2, speech = false)
    track.play()
    object : PcmSink {
        private var closed = false
        override fun write(samples: ShortArray, count: Int) {
            if (!closed) track.write(samples, 0, count)
        }
        override fun close() {
            if (closed) return
            closed = true
            track.stop()
            track.release()
        }
    }
}.getOrNull()

actual fun startMicEcho(
    process: (samples: ShortArray, count: Int, sampleRate: Int) -> ShortArray?,
): MicEcho? {
    if (ContextCompat.checkSelfPermission(AppInit.context, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }

    // Deliberately low: the detour is meant to sound like a telephone.
    val sampleRate = 22050
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val minBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, ENCODING)
    if (minBytes <= 0) return null

    return runCatching {
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, ENCODING, minBytes * 2,
        )
        val track = buildTrack(sampleRate, minBytes * 2, speech = true)
        record.startRecording()
        track.play()

        object : MicEcho {
            @Volatile private var running = true
            private var job: Job? = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ShortArray(minBytes / 2)
                while (running) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val out = process(buffer, read, sampleRate)
                        if (out != null) track.write(out, 0, read)
                    }
                }
            }

            override fun stop() {
                if (!running) return
                running = false
                job?.cancel()
                job = null
                record.stop(); record.release()
                track.stop(); track.release()
            }
        }
    }.getOrNull()
}

/**
 * Prefers the least-processed source available: UNPROCESSED bypasses the
 * automatic gain and noise suppression that would flatten exactly the ambient
 * detail the mosaic is measuring, and the built-in mic is pinned so a connected
 * headset does not become the sensor.
 */
private fun openRecorder(sampleRate: Int): AudioRecord? {
    val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, ENCODING)
    if (minBuf <= 0) return null
    val bufSize = maxOf(minBuf, sampleRate * 2)
    val sources = buildList {
        if (Build.VERSION.SDK_INT >= 24) add(MediaRecorder.AudioSource.UNPROCESSED)
        add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        add(MediaRecorder.AudioSource.MIC)
    }
    val builtInMic = if (Build.VERSION.SDK_INT >= 23) {
        (AppInit.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    } else null

    for (src in sources) {
        try {
            val rec = AudioRecord(src, sampleRate, AudioFormat.CHANNEL_IN_MONO, ENCODING, bufSize)
            if (rec.state == AudioRecord.STATE_INITIALIZED) {
                if (builtInMic != null) rec.setPreferredDevice(builtInMic)
                return rec
            }
            rec.release()
        } catch (_: Exception) { /* try next source */ }
    }
    return null
}

actual fun recordPcm(seconds: Int, sampleRate: Int): ShortArray? {
    val rec = openRecorder(sampleRate) ?: return null
    val total = sampleRate * seconds
    val out = ShortArray(total)
    return try {
        rec.startRecording()
        var read = 0
        while (read < total) {
            val n = rec.read(out, read, total - read)
            if (n <= 0) break
            read += n
        }
        if (read < total) out.copyOf(read) else out
    } catch (_: Exception) {
        null
    } finally {
        try { rec.stop() } catch (_: Exception) {}
        rec.release()
    }
}
