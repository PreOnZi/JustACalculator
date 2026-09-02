package com.fictioncutshort.justacalculator.logic

import com.fictioncutshort.justacalculator.platform.MicEcho
import com.fictioncutshort.justacalculator.platform.TypingClicker
import com.fictioncutshort.justacalculator.platform.openPcmSink
import com.fictioncutshort.justacalculator.platform.startMicEcho
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Real-time audio capture and playback with effects, for the telephone detour.
 *
 * All the waveform generation is plain arithmetic and lives here; the PCM sink
 * and the microphone come from the `Pcm` seam.
 */
class TalkAudioHandler : TypingClicker {

    private var echo: MicEcho? = null

    // Phone-quality on purpose. Only the synthesised sounds are pinned to this
    // rate — the mic echo runs at whatever the hardware hands over.
    private val sampleRate = 22050

    // Delay line, allocated on the first captured block because its length
    // depends on the capture rate.
    private val echoDelayMs = 150
    private var echoBuffer = ShortArray(0)
    private var echoBufferIndex = 0

    // Tunable per call to startRealtimeEcho — mutable so the audio thread
    // picks up the active values without re-allocation.
    private var activeEchoDecay = 0.4f
    private var activeDistortion = 1.2f

    // When true, the loop drops captured audio before it reaches the speaker —
    // used by PhoneCallScreen to mute the mic while the calculator is talking.
    @Volatile
    private var echoMuted = false

    /**
     * Suspend / resume mic-echo without tearing down the audio graph. Cheaper
     * than stop+start and avoids the audible glitch from re-binding.
     */
    override fun setEchoMuted(muted: Boolean) {
        echoMuted = muted
    }

    /**
     * Start capturing audio and playing it back with echo/distortion effect.
     *
     * @param decay      0..1, how much of the prior signal is mixed back. Lower
     *                   values produce a tamer, telephone-like effect.
     * @param distortion soft-clipping gain; 1.0 = none. Lower values reduce the
     *                   harsh saturation that sells the rotary-phone fail.
     */
    override fun startRealtimeEcho(decay: Float, distortion: Float) {
        activeEchoDecay = decay
        activeDistortion = distortion
        echoMuted = false
        echoBuffer = ShortArray(0)
        echoBufferIndex = 0

        echo?.stop()
        echo = startMicEcho { samples, count, rate ->
            if (echoBuffer.isEmpty()) {
                echoBuffer = ShortArray((rate * echoDelayMs / 1000).coerceAtLeast(1))
                echoBufferIndex = 0
            }
            // The delay line updates either way, so unmuting resumes mid-stream
            // rather than replaying a stale block.
            val processed = applyEchoDistortion(samples, count)
            if (echoMuted) null else processed
        }
    }

    /** Stop audio processing. */
    override fun stopRealtimeEcho() {
        echo?.stop()
        echo = null
    }

    /** Apply echo and distortion effect to an audio block. */
    private fun applyEchoDistortion(input: ShortArray, count: Int): ShortArray {
        val output = ShortArray(count)

        for (i in 0 until count) {
            val currentSample = input[i].toInt()

            // Get delayed sample from echo buffer
            val delayedSample = echoBuffer[echoBufferIndex].toInt()

            // Mix current sample with delayed sample (echo effect)
            var mixedSample = currentSample + (delayedSample * activeEchoDecay).toInt()

            // Apply soft-clipping distortion. Distortion of 1.0 = passthrough.
            mixedSample = (mixedSample * activeDistortion).toInt()
            mixedSample = mixedSample.coerceIn(-32000, 32000)

            // Store current sample in echo buffer for future delay
            echoBuffer[echoBufferIndex] = mixedSample.toShort()
            echoBufferIndex = (echoBufferIndex + 1) % echoBuffer.size

            output[i] = mixedSample.toShort()
        }

        return output
    }

    /**
     * Streams [durationMs] of generated audio in [chunkSize] blocks, filling
     * each one with [fill] (given the index of the first sample in the block).
     */
    private fun stream(
        durationMs: Int,
        chunkSize: Int,
        tailMs: Long,
        onComplete: (() -> Unit)?,
        fill: (chunk: ShortArray, startSample: Int, totalSamples: Int) -> Unit,
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            val sink = openPcmSink(sampleRate, sampleRate) ?: run {
                onComplete?.let { withContext(Dispatchers.Main) { it() } }
                return@launch
            }
            val totalSamples = sampleRate * durationMs / 1000
            var written = 0
            val chunk = ShortArray(chunkSize)
            while (written < totalSamples) {
                fill(chunk, written, totalSamples)
                sink.write(chunk, chunkSize)
                written += chunkSize
            }
            delay(tailMs)  // Let it finish playing
            sink.close()
            onComplete?.let { withContext(Dispatchers.Main) { it() } }
        }
    }

    /** Play static crackle sound. */
    override fun playStaticSound(onComplete: () -> Unit) {
        stream(durationMs = 4500, chunkSize = 1024, tailMs = 200, onComplete = onComplete) { chunk, _, _ ->
            for (i in chunk.indices) {
                // Random noise with varying intensity for crackle effect
                val intensity = if (Random.nextDouble() < 0.1) 8000 else 2000
                chunk[i] = ((Random.nextDouble() * intensity * 2) - intensity).toInt().toShort()
            }
        }
    }

    /** Play microphone feedback squeal sound. */
    fun playFeedbackSqueal(onComplete: () -> Unit) {
        stream(durationMs = 4500, chunkSize = 512, tailMs = 100, onComplete = onComplete) { chunk, start, total ->
            for (i in chunk.indices) {
                val t = (start + i).toDouble() / sampleRate
                val progress = (start + i).toDouble() / total

                // Frequency rises then falls (feedback squeal effect)
                val freq = 800 + (2000 * sin(progress * PI)).toInt()

                // Add some harmonics for harshness
                val sample = (sin(2 * PI * freq * t) * 0.5 +
                        sin(4 * PI * freq * t) * 0.3 +
                        sin(6 * PI * freq * t) * 0.2) * 12000

                chunk[i] = sample.toInt().coerceIn(-32000, 32000).toShort()
            }
        }
    }

    /**
     * The click waveform, generated once. About 15ms: a quick attack and decay
     * make a soft "tick", and 800Hz keeps it deep rather than shrill.
     */
    private val clickSamples: ShortArray by lazy {
        val numSamples = (sampleRate * 0.015).toInt()
        ShortArray(numSamples) { i ->
            val envelope = 1.0 - (i.toDouble() / numSamples)
            (sin(2 * PI * 800.0 * i / sampleRate) * envelope * 200).toInt().toShort()
        }
    }

    /**
     * Held open across clicks rather than opened per keystroke. Android tore an
     * AudioTrack down each time, which was cheap; the iOS equivalent is a whole
     * AVAudioEngine, and starting one per character during a typing run is both
     * slow and audibly ragged.
     */
    private var clickSink: com.fictioncutshort.justacalculator.platform.PcmSink? = null

    @Volatile
    private var clickSinkFailed = false

    /**
     * Clicks are played by one pump coroutine, never by the caller.
     *
     * The sink is a single stream track shared by every click, and writing to
     * one from two threads at once is a native crash on Android — AudioTrack
     * back-pressures once its buffer is full, so a click launched per character
     * piled up coroutines across Dispatchers.Default workers and they collided
     * inside releaseBuffer. Superfast typing (5ms/char, e.g. the calculator's
     * history list at step 83) writes faster than ~60ms of buffer drains, which
     * is exactly when it happened.
     *
     * DROP_OLDEST rather than a queue: a backlog would play the clicks behind
     * the text that caused them. Dropping keeps them in time and just thins
     * them out when the typing outruns the speaker.
     */
    private val clickScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    private val clickRequests =
        Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var clickPump: Job? = null

    private fun startClickPump(): Job = clickScope.launch {
        for (click in clickRequests) {
            if (clickSinkFailed) continue
            val sink = clickSink
                ?: openPcmSink(sampleRate, clickSamples.size * 4)?.also { clickSink = it }
                ?: run { clickSinkFailed = true; continue }
            sink.write(clickSamples, clickSamples.size)
        }
    }

    /** Play a soft typing click sound. */
    override fun playTypingClick() {
        if (clickSinkFailed) return
        // Callers are all composition-side (the typing loop, the phone screens),
        // so this stays on one thread and needs no guard of its own.
        if (clickPump == null) clickPump = startClickPump()
        clickRequests.trySend(Unit)
    }

    /** Frees the click output; the story calls this when the phone detour ends. */
    fun release() {
        stopRealtimeEcho()
        clickPump?.cancel()
        clickPump = null
        // Closed on the pump's own thread, behind whatever write is in flight —
        // closing it from here would race the very write this class exists to
        // serialise. A later click just starts a fresh pump and sink.
        clickScope.launch {
            clickSink?.close()
            clickSink = null
        }
    }
}
