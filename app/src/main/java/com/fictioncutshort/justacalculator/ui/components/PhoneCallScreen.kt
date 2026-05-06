package com.fictioncutshort.justacalculator.ui.components

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.logic.TalkAudioHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Fake call screen — appears when the user dials the magic number from the
 * keypad and presses the green call button.
 *
 * Sequence (asset paths under `assets/audio/`):
 *  - Press call: `0001.mp3` plays as the ringtone (mic muted).
 *  - On pickup: `0001` is cut off, mic-echo starts (tamed), then the calculator
 *    plays a scripted voice-line sequence with mic muted during each line and
 *    re-enabled during the gaps in between:
 *
 *        +0.5s : 01     [mic muted while playing]
 *        gap 2s
 *               02     [mic muted]
 *        gap 2s
 *               03 → 04 → 05 → 06 → 07   (back-to-back; mic muted throughout)
 *        gap 2s
 *               08     [mic muted]
 *        gap 4s
 *               09     [mic muted]
 *
 *    After 09 finishes, mic stays on and the user is expected to hang up.
 *
 * Audio files are loaded from assets at runtime; if any are missing the
 * corresponding step is skipped silently so the user can drop the files in
 * incrementally without breaking the flow.
 */

private const val RING_DURATION_MS = 3500L

/** Extensions tried for each audio asset, in order. First hit wins. */
private val AUDIO_EXTS = listOf("wav", "mp3", "m4a", "ogg")

private const val RING_BASENAME = "audio/0001"
private val SEQUENCE_BASENAMES = listOf(
    "audio/01", "audio/02", "audio/03", "audio/04", "audio/05",
    "audio/06", "audio/07", "audio/08", "audio/09"
)

@Composable
fun PhoneCallScreen(
    number: String,
    audioHandler: TalkAudioHandler,
    onHangup: () -> Unit
) {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableStateOf(0) }
    // We keep a reference to the live ringtone player so the pickup transition
    // can stop it instantly (otherwise it'd play through to its natural end).
    val ringPlayerRef = remember { arrayOf<MediaPlayer?>(null) }

    // Stage 1: ringing. Plays 0001 in a loop, mic muted, then auto-pickup.
    LaunchedEffect(Unit) {
        audioHandler.startRealtimeEcho(decay = 0.18f, distortion = 1.0f)
        audioHandler.setEchoMuted(true)
        ringPlayerRef[0] = resolveAsset(context, RING_BASENAME)?.let {
            tryStartLoopingAudio(context, it)
        }
        delay(RING_DURATION_MS)
        ringPlayerRef[0]?.let { stopAndRelease(it) }
        ringPlayerRef[0] = null
        connected = true
    }

    // Stage 2: connected. Run the scripted voice-line sequence.
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        // 0.5s after pickup, then 01.
        // Gap 2s, 02. Gap 2s, 03→04→05→06→07. Gap 2s, 08. Gap 4s, 09.
        // Mic muted while each file plays, on between files.
        val gaps = listOf(500L, 2000L, 2000L, 0L, 0L, 0L, 0L, 2000L, 4000L)
        SEQUENCE_BASENAMES.forEachIndexed { i, basename ->
            val gapBefore = gaps[i]
            if (gapBefore > 0) {
                audioHandler.setEchoMuted(false)
                delay(gapBefore)
            }
            audioHandler.setEchoMuted(true)
            val resolved = resolveAsset(context, basename)
            if (resolved != null) {
                playAssetAudio(context, resolved)
            }
        }
        // After 09 the user is expected to hang up — leave mic on.
        audioHandler.setEchoMuted(false)
    }

    // Tear down audio on dispose (covers hangup mid-sequence).
    DisposableEffect(Unit) {
        onDispose {
            ringPlayerRef[0]?.let { stopAndRelease(it) }
            audioHandler.stopRealtimeEcho()
        }
    }

    // Call timer (1Hz once connected).
    LaunchedEffect(connected) {
        if (connected) {
            while (true) {
                delay(1000)
                elapsedSec += 1
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = if (connected) "Calculator" else "Calling…",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = number.ifEmpty { "—" },
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (connected) formatDuration(elapsedSec) else "ringing…",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(56.dp))

            val transition = rememberInfiniteTransition(label = "callPulse")
            val pulse by transition.animateFloat(
                initialValue = 1f,
                targetValue = if (connected) 1.06f else 1.18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = if (connected) 1400 else 700),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .clip(CircleShape)
                    .background(
                        if (connected) Color(0xFF1F4E2A) else Color(0xFF333333)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📞",
                    fontSize = 56.sp
                )
            }

            Spacer(Modifier.weight(1f, fill = true))

            // Hangup button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD93030))
                        .clickable(onClick = onHangup),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 28.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Hang up",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Resolve an audio basename like `audio/01` to the first existing asset across
 * [AUDIO_EXTS]. Lets the user mix WAV/MP3/etc. without re-touching code.
 * Returns the full asset path (e.g. `audio/01.wav`), or null if none exist.
 */
private fun resolveAsset(context: Context, basename: String): String? {
    val parent = basename.substringBeforeLast('/', "")
    val stem = basename.substringAfterLast('/')
    val listed = try {
        context.assets.list(parent)?.toSet() ?: emptySet()
    } catch (_: Exception) {
        return null
    }
    for (ext in AUDIO_EXTS) {
        val candidate = "$stem.$ext"
        if (candidate in listed) {
            return if (parent.isEmpty()) candidate else "$parent/$candidate"
        }
    }
    return null
}

/**
 * Plays an asset audio file and suspends until it finishes (or its enclosing
 * coroutine is cancelled). Missing assets are no-ops — the user can drop the
 * voice-line files in incrementally without breaking the sequence.
 */
private suspend fun playAssetAudio(context: Context, assetPath: String) {
    val mp = try {
        val afd = context.assets.openFd(assetPath)
        MediaPlayer().apply {
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            prepare()
        }.also { afd.close() }
    } catch (e: Exception) {
        return
    }

    suspendCancellableCoroutine<Unit> { cont ->
        mp.setOnCompletionListener {
            stopAndRelease(mp)
            if (cont.isActive) cont.resume(Unit)
        }
        mp.setOnErrorListener { _, _, _ ->
            stopAndRelease(mp)
            if (cont.isActive) cont.resume(Unit)
            true
        }
        cont.invokeOnCancellation { stopAndRelease(mp) }
        try {
            mp.start()
        } catch (e: Exception) {
            stopAndRelease(mp)
            if (cont.isActive) cont.resume(Unit)
        }
    }
}

/**
 * Best-effort: start a looping audio asset (used for the ringtone). Returns
 * the MediaPlayer for explicit stop on pickup, or null if the asset isn't
 * present.
 */
private fun tryStartLoopingAudio(context: Context, assetPath: String): MediaPlayer? {
    return try {
        val afd = context.assets.openFd(assetPath)
        MediaPlayer().apply {
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            isLooping = true
            prepare()
            start()
        }.also { afd.close() }
    } catch (e: Exception) {
        null
    }
}

private fun stopAndRelease(mp: MediaPlayer) {
    try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
    try { mp.release() } catch (_: Exception) {}
}

private fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
