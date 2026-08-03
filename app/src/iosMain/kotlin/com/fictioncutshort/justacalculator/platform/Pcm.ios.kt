package com.fictioncutshort.justacalculator.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import kotlin.concurrent.AtomicInt
import platform.posix.usleep
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioConverterInputStatus_NoDataNow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.setActive
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt

/**
 * AVAudioEngine counterpart to AudioTrack / AudioRecord.
 *
 * An AVAudioPlayerNode is fed 32-bit float buffers; the shared code works in
 * 16-bit ints, so both directions convert at this boundary. Connecting the
 * player with an explicit format lets the engine resample for us, which is why
 * a 22.05 kHz sink works on hardware running at 48 kHz.
 */

private const val SHORT_SCALE = 32768f

@OptIn(ExperimentalForeignApi::class)
private fun monoFloatFormat(sampleRate: Int): AVAudioFormat? =
    AVAudioFormat(
        commonFormat = AVAudioPCMFormatFloat32,
        sampleRate = sampleRate.toDouble(),
        channels = 1u,
        interleaved = false,
    )

/**
 * Configures the session for the mode the next sound needs.
 *
 * Recording requires PlayAndRecord, which on its own routes playback to the
 * earpiece — DefaultToSpeaker puts it back on the loudspeaker so the echo is
 * audible held at arm's length, matching Android.
 */
@OptIn(ExperimentalForeignApi::class)
private fun activateSession(recording: Boolean): Boolean = runCatching {
    val session = AVAudioSession.sharedInstance()
    if (recording) {
        session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker,
            error = null,
        )
    }
    session.setActive(true, null)
}.isSuccess

/** An engine plus one player node, wired and started. */
@OptIn(ExperimentalForeignApi::class)
private class PlayerGraph(format: AVAudioFormat) {
    val engine = AVAudioEngine()
    val player = AVAudioPlayerNode()

    init {
        engine.attachNode(player)
        engine.connect(player, engine.mainMixerNode, format)
    }

    fun start(): Boolean = runCatching {
        engine.prepare()
        engine.startAndReturnError(null).also { if (it) player.play() }
    }.getOrDefault(false)

    fun stop() {
        runCatching {
            player.stop()
            engine.stop()
        }
    }
}

/** Copies [count] shorts into a fresh float buffer the player can schedule. */
@OptIn(ExperimentalForeignApi::class)
private fun shortsToBuffer(
    format: AVAudioFormat,
    samples: ShortArray,
    count: Int,
): AVAudioPCMBuffer? {
    if (count <= 0) return null
    val buffer = AVAudioPCMBuffer(format, count.toUInt())
    val channel = buffer.floatChannelData?.get(0) ?: return null
    for (i in 0 until count) channel[i] = samples[i] / SHORT_SCALE
    buffer.frameLength = count.toUInt()
    return buffer
}

@OptIn(ExperimentalForeignApi::class)
actual fun openPcmSink(sampleRate: Int, bufferSamples: Int): PcmSink? {
    if (!activateSession(recording = false)) return null
    val format = monoFloatFormat(sampleRate) ?: return null
    val graph = PlayerGraph(format)
    if (!graph.start()) return null

    return object : PcmSink {
        private var closed = false

        override fun write(samples: ShortArray, count: Int) {
            if (closed) return
            // scheduleBuffer queues rather than blocking, so unlike AudioTrack
            // this never applies back-pressure. The generators write a bounded
            // amount (a few seconds at most) and then stop, so the queue stays
            // small enough that the difference does not matter.
            val buffer = shortsToBuffer(format, samples, count) ?: return
            graph.player.scheduleBuffer(buffer, null)
        }

        override fun close() {
            if (closed) return
            closed = true
            graph.stop()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun startMicEcho(
    process: (samples: ShortArray, count: Int, sampleRate: Int) -> ShortArray?,
): MicEcho? {
    // Without this the engine still starts and quietly delivers silence, where
    // Android returns nothing and the caller falls back to no echo.
    if (!hasPermission(AppInit.context, AppPermission.MICROPHONE)) return null
    if (!activateSession(recording = true)) return null

    val engine = AVAudioEngine()
    val input = engine.inputNode
    // Tapping requires the node's own format; the hardware rate is whatever it
    // is, so the delay line downstream is sized from this rather than assumed.
    val inputFormat = input.outputFormatForBus(0u)
    val sampleRate = inputFormat.sampleRate.roundToInt()
    if (sampleRate <= 0) return null

    val playFormat = monoFloatFormat(sampleRate) ?: return null
    val player = AVAudioPlayerNode()
    engine.attachNode(player)
    engine.connect(player, engine.mainMixerNode, playFormat)

    // Reused across callbacks so the audio thread does not allocate per block.
    var scratch = ShortArray(0)

    input.installTapOnBus(0u, 4096u, inputFormat) { buffer, _ ->
        val pcm = buffer ?: return@installTapOnBus
        val count = pcm.frameLength.toInt()
        val channel = pcm.floatChannelData?.get(0) ?: return@installTapOnBus
        if (count <= 0) return@installTapOnBus

        if (scratch.size < count) scratch = ShortArray(count)
        for (i in 0 until count) {
            scratch[i] = (channel[i].coerceIn(-1f, 1f) * SHORT_SCALE).toInt().toShort()
        }

        val out = process(scratch, count, sampleRate) ?: return@installTapOnBus
        val outBuffer = shortsToBuffer(playFormat, out, count) ?: return@installTapOnBus
        player.scheduleBuffer(outBuffer, null)
    }

    val started = runCatching {
        engine.prepare()
        engine.startAndReturnError(null).also { if (it) player.play() }
    }.getOrDefault(false)

    if (!started) {
        runCatching { input.removeTapOnBus(0u) }
        return null
    }

    return object : MicEcho {
        @Volatile private var running = true
        override fun stop() {
            if (!running) return
            running = false
            runCatching {
                input.removeTapOnBus(0u)
                player.stop()
                engine.stop()
            }
        }
    }
}

/**
 * A fixed-rate capture, which the input node cannot give directly: taps only
 * deliver the node's own format, so an AVAudioConverter resamples each block to
 * the rate the caller's analysis assumes.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun recordPcm(seconds: Int, sampleRate: Int): ShortArray? {
    if (!hasPermission(AppInit.context, AppPermission.MICROPHONE)) return null
    if (!activateSession(recording = true)) return null

    val engine = AVAudioEngine()
    val input = engine.inputNode
    val inputFormat = input.outputFormatForBus(0u)
    if (inputFormat.sampleRate <= 0.0) return null

    val target = monoFloatFormat(sampleRate) ?: return null
    val converter = AVAudioConverter(fromFormat = inputFormat, toFormat = target) ?: return null

    val total = sampleRate * seconds
    val out = ShortArray(total)
    val filled = AtomicInt(0)

    input.installTapOnBus(0u, 4096u, inputFormat) { buffer, _ ->
        val pcm = buffer ?: return@installTapOnBus
        if (filled.value >= total) return@installTapOnBus

        // Ratio-scaled capacity, plus slack for the converter's own latency.
        val capacity = (pcm.frameLength.toDouble() * sampleRate / inputFormat.sampleRate).toUInt() + 64u
        val converted = AVAudioPCMBuffer(target, capacity)
        var consumed = false
        converter.convertToBuffer(converted, null) { _, status ->
            // The converter pulls until satisfied; hand over this block once and
            // then report starvation so it returns with what it has.
            if (consumed) {
                status?.pointed?.value = AVAudioConverterInputStatus_NoDataNow
                null
            } else {
                consumed = true
                status?.pointed?.value = AVAudioConverterInputStatus_HaveData
                pcm
            }
        }

        val count = converted.frameLength.toInt()
        val channel = converted.floatChannelData?.get(0) ?: return@installTapOnBus
        val start = filled.value
        val room = minOf(count, total - start)
        for (i in 0 until room) {
            out[start + i] = (channel[i].coerceIn(-1f, 1f) * SHORT_SCALE).toInt().toShort()
        }
        filled.value = start + room
    }

    val started = runCatching {
        engine.prepare()
        engine.startAndReturnError(null)
    }.getOrDefault(false)
    if (!started) {
        runCatching { input.removeTapOnBus(0u) }
        return null
    }

    // Blocking is what the caller wants; it already runs this off the main
    // thread, and the tap fills `out` from the audio thread meanwhile.
    val deadline = nowMillis() + seconds * 1000L + 1500L
    while (filled.value < total && nowMillis() < deadline) {
        usleep(20_000u)
    }

    runCatching {
        input.removeTapOnBus(0u)
        engine.stop()
    }

    val got = filled.value
    return when {
        got <= 0 -> null
        got < total -> out.copyOf(got)
        else -> out
    }
}
