package com.fictioncutshort.justacalculator.logic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.text.toInt
import kotlin.times

/**
 * Handles real-time audio capture and playback with effects
 * for the telephone detour sequence.
 */
class TalkAudioHandler(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isProcessing = false
    private var processingJob: Job? = null

    // Audio configuration
    private val sampleRate = 22050  // Lower sample rate for phone-like quality
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Echo buffer - stores delayed samples
    private val echoDelayMs = 150  // Short delay for quick echo
    private val echoDelaySamples = (sampleRate * echoDelayMs / 1000)
    private val echoBuffer = ShortArray(echoDelaySamples)
    private var echoBufferIndex = 0
    private val echoDecay = 0.4f  // How much the echo fades (0.0 - 1.0)

    /**
     * Start capturing audio and playing it back with echo/distortion effect
     */
    fun startRealtimeEcho() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Reset echo buffer
        echoBuffer.fill(0)
        echoBufferIndex = 0
        isProcessing = true

        // Initialize AudioRecord (microphone input)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize * 2
        )

        // Initialize AudioTrack (speaker output)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioRecord?.startRecording()
        audioTrack?.play()

        // Process audio on a background thread
        processingJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ShortArray(bufferSize / 2)

            while (isProcessing) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readCount > 0) {
                    // Apply echo and distortion effect
                    val processed = applyEchoDistortion(buffer, readCount)
                    audioTrack?.write(processed, 0, readCount)
                }
            }
        }
    }

    /**
     * Stop audio processing
     */
    fun stopRealtimeEcho() {
        isProcessing = false
        processingJob?.cancel()

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    /**
     * Apply echo and distortion effect to audio buffer
     */
    private fun applyEchoDistortion(input: ShortArray, count: Int): ShortArray {
        val output = ShortArray(count)

        for (i in 0 until count) {
            val currentSample = input[i].toInt()

            // Get delayed sample from echo buffer
            val delayedSample = echoBuffer[echoBufferIndex].toInt()

            // Mix current sample with delayed sample (echo effect)
            var mixedSample = currentSample + (delayedSample * echoDecay).toInt()

            // Apply slight distortion (soft clipping)
            mixedSample = (mixedSample * 1.2).toInt()
            mixedSample = mixedSample.coerceIn(-32000, 32000)

            // Store current sample in echo buffer for future delay
            echoBuffer[echoBufferIndex] = mixedSample.toShort()
            echoBufferIndex = (echoBufferIndex + 1) % echoDelaySamples

            output[i] = mixedSample.toShort()
        }

        return output
    }

    /**
     * Play static crackle sound
     */
    fun playStaticSound(onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val staticTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(sampleRate * 2)  // 1 second buffer
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            staticTrack.play()

            // Generate 1.5 seconds of static
            val durationMs = 4500
            val totalSamples = sampleRate * durationMs / 1000
            val chunkSize = 1024
            var samplesWritten = 0

            while (samplesWritten < totalSamples) {
                val chunk = ShortArray(chunkSize)
                for (i in 0 until chunkSize) {
                    // Random noise with varying intensity for crackle effect
                    val intensity = if (Math.random() < 0.1) 8000 else 2000
                    chunk[i] = ((Math.random() * intensity * 2) - intensity).toInt().toShort()
                }
                staticTrack.write(chunk, 0, chunkSize)
                samplesWritten += chunkSize
            }

            delay(200)  // Let it finish playing
            staticTrack.stop()
            staticTrack.release()

            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
    /**
     * Play microphone feedback squeal sound
     */
    fun playFeedbackSqueal(onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val squealTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(sampleRate * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            squealTrack.play()

            // Generate ~1 second of feedback squeal (rising then falling tone)
            val durationMs = 4500
            val totalSamples = sampleRate * durationMs / 1000
            val chunkSize = 512
            var samplesWritten = 0

            while (samplesWritten < totalSamples) {
                val chunk = ShortArray(chunkSize)
                for (i in 0 until chunkSize) {
                    val t = (samplesWritten + i).toDouble() / sampleRate
                    val progress = (samplesWritten + i).toDouble() / totalSamples

                    // Frequency rises then falls (feedback squeal effect)
                    val freq = 800 + (2000 * Math.sin(progress * Math.PI)).toInt()

                    // Add some harmonics for harshness
                    val sample = (Math.sin(2 * Math.PI * freq * t) * 0.5 +
                            Math.sin(4 * Math.PI * freq * t) * 0.3 +
                            Math.sin(6 * Math.PI * freq * t) * 0.2) * 12000

                    chunk[i] = sample.toInt().coerceIn(-32000, 32000).toShort()
                }
                squealTrack.write(chunk, 0, chunkSize)
                samplesWritten += chunkSize
            }

            delay(100)
            squealTrack.stop()
            squealTrack.release()

            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
    /**
     * Play a soft typing click sound
     */
    fun playTypingClick() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val clickTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(1024)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                // Generate a very short click (about 15ms)
                val clickDuration = 0.015  // 15 milliseconds - slightly longer for lower pitch
                val numSamples = (sampleRate * clickDuration).toInt()
                val samples = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    // Quick attack, quick decay - creates a soft "tick" sound
                    val envelope = 1.0 - (i.toDouble() / numSamples)
                    val frequency = 800.0  // Lower frequency = deeper, softer click (was 1800)
                    val sample = Math.sin(2 * Math.PI * frequency * i / sampleRate) * envelope * 200  // 50% quieter (was 2000)
                    samples[i] = sample.toInt().toShort()
                }

                clickTrack.write(samples, 0, numSamples)
                clickTrack.play()

                // Small delay then release
                delay(20)
                clickTrack.stop()
                clickTrack.release()
            } catch (e: Exception) {
                // Ignore audio errors for typing clicks
            }
        }
    }
}
