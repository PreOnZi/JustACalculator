package com.fictioncutshort.justacalculator.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// BUILDING 5 — SOUND MOSAIC PROTOTYPE
//
// One PLACE = THREE 3-second scans, woven into a SINGLE mosaic. Across the walk
// the player visits several places, so they end up with one picture per place
// (not three per place).
//
// Each mosaic works in READING ORDER, not as a fixed spectrogram: it walks the
// recording in time slices, picks the few most prominent tones per slice, and
// drops them into the grid left-to-right, top-to-bottom — the order it heard
// them. The three scans fill the grid in turn (48 tiles each = 144 total), so a
// place's picture is unique and always full.
//
// Tile colour = the tone's frequency (light-spectrum order: low = RED, through
// orange/yellow/green/blue, up to VIOLET for the highest hiss). Brightness = how
// loud that tone was. Tap any tile to read its frequency / loudness / time.
//
// Raw audio is never stored: the PCM buffer lives only long enough to pull the
// tones out, then falls out of scope. Only the abstract tile grid survives.
// ─────────────────────────────────────────────────────────────────────────────

private const val SAMPLE_RATE     = 22_050
private const val FFT_SIZE        = 2048
private const val FFT_HOP         = 1024
private const val REC_SECONDS     = 3
private const val SCANS           = 3      // scans combined into one place-picture
private const val M_COLS          = 12     // grid width
private const val M_ROWS          = 12     // grid height (144 tiles total)
private const val SLICES_PER_SCAN = 16     // 3 scans × 16 slices × 3 tones = 144 tiles
private const val PK_BANDS        = 30     // frequency resolution for peak-picking
private const val PEAKS_PER_SLICE = 3
private const val NBANDS          = 22     // summary bands (for the narration)
private const val FREQ_LO         = 80.0   // lowest tone we colour
private const val FREQ_HI         = 8000.0 // highest (above this = mostly mic hiss)

// Console palette
private val P_BG      = Color(0xFF0A0F0A)
private val P_PANEL   = Color(0xE6111111)
private val P_GREEN   = Color(0xFF33FF66)
private val P_GREEN_D = Color(0xFF33AA55)
private val P_AMBER   = Color(0xFFFFCC44)
private val P_ORANGE  = Color(0xFFFF6600)

// ─────────────────────────────────────────────────────────────────────────────
// PLACE DESCRIPTORS  ← WRITE YOUR OWN LINES HERE
//
// One short, vague line is shown per place. Write as many variations as you like
// in each list; the code picks one deterministically (same sound → same line).
// Colours are deliberately NOT mentioned. What each bucket means:
//
//   QUIET — overall very quiet, whatever the pitch. "Seems peaceful."
//   LOW   — energy sits LOW (< ~350 Hz): heavy, deep, grounded — engines,
//           traffic weight, big/industrial rumble, things with mass.
//   MID   — energy in the MIDDLE (~350–1800 Hz): human, lived-in, occupied —
//           voices, motors, the working body of a place.
//   HIGH  — energy up HIGH (> ~1800 Hz): thin, bright, airy, sharp — hiss,
//           wind, birds, small delicate sounds.
//   BUSY  — loud AND restless (lots changing moment to moment): crowded,
//           a lot happening at once, hard to settle.
//
// So there are 5 buckets. Write ~3–5 lines each (≈15–25 total) and you're done.
// D_WIND is an optional tail appended when it sounds windy — leave "" to skip.
private val D_QUIET = listOf(
    "Hmmm. Seems fairly peaceful.",
    "Well, this appears to be a quiet spot.",
    "Nothing like a "
)
private val D_LOW = listOf(
    "Something heavy sits underneath it all.",
    "Low and weighty. It presses down a little.",
)
private val D_MID = listOf(
    "Feels lived-in. Things are happening.",
    "The middle of things. Occupied.",
)
private val D_HIGH = listOf(
    "Thin and bright up here.",
    "Lots of small, sharp little sounds.",
)
private val D_BUSY = listOf(
    "A lot going on. Hard to settle.",
    "Busy. Relentless, almost.",
)
private const val D_WIND = "  A breeze runs through it."

private enum class Phase { IDLE, COUNTDOWN, REC, PROCESSING, REVEAL }

internal class MosaicCell(val timeSec: Float, val freqHz: Float, val loud: Float)
private fun emptyCell() = MosaicCell(0f, 0f, 0f)
internal class SoundMosaic(
    val cells: Array<Array<MosaicCell>>,  // [row][col]; reading order, row 0 = top
    val label: String,
    val debug: String,
    val highlights: List<Pair<Int, Int>>  // (row,col) of a few notable tiles
)

/** One finished place: its mosaic plus the metadata shown in the final gallery.
 *  The saved IMAGE never carries lat/lon — only the on-screen gallery does. */
internal class PlaceCapture(
    val index: Int,
    val mosaic: SoundMosaic,
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
    val dominantHz: Int
)

// One scan's raw output, combined with its siblings before normalising.
private class RawTile(val timeSec: Float, val freqHz: Float, val energy: Double)
private class ScanResult(
    val tiles: List<RawTile>,
    val magSum: DoubleArray,
    val frameEnergy: List<Double>,
    val sumSq: Double,
    val samples: Int
)

@Composable
internal fun Building5SoundProto(
    onComplete: (SoundMosaic) -> Unit,
    buttonLabel: String = "Let's find the next stop"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasMic by remember { mutableStateOf(hasMicPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMic = granted }
    LaunchedEffect(Unit) { if (!hasMic) permLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    var phase by remember { mutableStateOf(Phase.IDLE) }
    var countNum by remember { mutableStateOf(3) }
    val scans = remember { mutableStateListOf<ScanResult>() }
    var combined by remember { mutableStateOf<SoundMosaic?>(null) }

    fun runCapture() {
        if (phase != Phase.IDLE) return
        scope.launch {
            for (n in 3 downTo 1) { phase = Phase.COUNTDOWN; countNum = n; delay(900) }
            phase = Phase.REC
            delay(100)
            val pcm = withContext(Dispatchers.IO) { recordSeconds(context, REC_SECONDS) }
            phase = Phase.PROCESSING
            val sr = withContext(Dispatchers.Default) { analyzeScan(pcm, scans.size) }
            scans.add(sr)
            if (scans.size >= SCANS) {
                combined = withContext(Dispatchers.Default) { combineScans(scans) }
                phase = Phase.REVEAL
            } else phase = Phase.IDLE
        }
    }

    Box(Modifier.fillMaxSize().background(P_BG)) {
        val done = combined
        when {
            !hasMic -> MicGate { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            phase == Phase.REVEAL && done != null -> RevealScreen(
                mosaic = done,
                buttonLabel = buttonLabel,
                onDone = { onComplete(done) }
            )
            else -> CaptureScreen(phase, countNum, scans.size, ::runCapture)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SCREENS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.CaptureScreen(phase: Phase, countNum: Int, done: Int, onCapture: () -> Unit) {
    Column(
        Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Scan ${(done + 1).coerceAtMost(SCANS)} / $SCANS",
             color = P_GREEN_D, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(48.dp))

        val big = when (phase) {
            Phase.COUNTDOWN  -> countNum.toString()
            Phase.REC        -> "● REC"
            Phase.PROCESSING -> "..."
            else             -> "TAP TO LISTEN"
        }
        val bigColor = when (phase) {
            Phase.REC        -> Color(0xFFFF3300)
            Phase.PROCESSING -> P_GREEN_D
            else             -> P_GREEN
        }
        Text(big, color = bigColor,
             fontSize = if (phase == Phase.COUNTDOWN) 88.sp else 30.sp,
             fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
             textAlign = TextAlign.Center)
        if (phase == Phase.REC) {
            Spacer(Modifier.height(8.dp))
            Text("listening for ${REC_SECONDS}s…", color = P_GREEN_D, fontSize = 12.sp,
                 fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(48.dp))

        val idle = phase == Phase.IDLE
        Box(
            Modifier
                .background(if (idle) P_ORANGE else Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
                .border(1.dp, P_GREEN_D, RoundedCornerShape(4.dp))
                .then(if (idle) Modifier.clickable { onCapture() } else Modifier)
                .padding(horizontal = 40.dp, vertical = 16.dp)
        ) {
            Text(if (idle) "[ CAPTURE ]" else "[ ... ]",
                 color = if (idle) Color.White else P_GREEN_D,
                 fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun BoxScope.RevealScreen(mosaic: SoundMosaic, buttonLabel: String, onDone: () -> Unit) {
    var sel by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("WHAT I HEARD", color = P_AMBER, fontSize = 16.sp,
             fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        FrequencyLegend()
        Spacer(Modifier.height(14.dp))

        MosaicGrid(mosaic, sel, mosaic.highlights) { r, c -> sel = r to c }
        Spacer(Modifier.height(12.dp))

        // A few notable squares, with their raw numbers. Tapping one selects it
        // on the grid (ringed). The full grid stays tappable for the rest.
        Text("A FEW OF THE SQUARES", color = P_AMBER, fontSize = 12.sp,
             fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text("dB is relative to the loudest tone here", color = P_GREEN_D, fontSize = 9.sp,
             fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        mosaic.highlights.forEach { h ->
            val cell = mosaic.cells[h.first][h.second]
            val on = sel == h
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { sel = h }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.hsv(hueForFreq(cell.freqHz), 0.85f, 0.40f + 0.60f * cell.loud))
                )
                Spacer(Modifier.width(8.dp))
                Text(cellDetail(cell), color = if (on) P_AMBER else P_GREEN,
                     fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(10.dp))

        val s = sel
        if (s != null) {
            Text(cellDetail(mosaic.cells[s.first][s.second]),
                 color = P_AMBER, fontSize = 11.sp,
                 fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        } else {
            Text("tap a tile to inspect it", color = P_GREEN_D, fontSize = 10.sp,
                 fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        Text(mosaic.debug, color = P_GREEN_D, fontSize = 9.sp,
             fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        // The only way onward — no exit, no redo.
        Box(
            Modifier
                .background(P_ORANGE, RoundedCornerShape(3.dp))
                .clickable { onDone() }
                .padding(horizontal = 30.dp, vertical = 14.dp)
        ) {
            Text("[ $buttonLabel ]", color = Color.White, fontSize = 14.sp,
                 fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                 textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MosaicGrid(
    mosaic: SoundMosaic,
    selectedCell: Pair<Int, Int>?,
    highlights: List<Pair<Int, Int>>,
    onTap: (row: Int, col: Int) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, P_GREEN_D, RoundedCornerShape(4.dp))
            .padding(3.dp)
    ) {
        for (r in 0 until M_ROWS) {          // top-to-bottom = reading order
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until M_COLS) {
                    val cell = mosaic.cells[r][c]
                    val empty = cell.freqHz <= 0f
                    val bg = if (empty) Color(0xFF101010)
                             else Color.hsv(hueForFreq(cell.freqHz), 0.85f, 0.40f + 0.60f * cell.loud)
                    val isSel = selectedCell?.let { it.first == r && it.second == c } == true
                    val isHi = highlights.any { it.first == r && it.second == c }
                    val ring = when {
                        isSel -> Modifier.border(1.5.dp, Color.White, RoundedCornerShape(2.dp))
                        isHi  -> Modifier.border(1.5.dp, P_AMBER, RoundedCornerShape(2.dp))
                        else  -> Modifier
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(bg)
                            .then(ring)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onTap(r, c) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FrequencyLegend() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(3.dp))) {
            val steps = 24
            for (i in 0 until steps) {
                val hue = (i.toFloat() / (steps - 1)) * 285f
                Box(Modifier.weight(1f).fillMaxHeight().background(Color.hsv(hue, 0.85f, 0.9f)))
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("80Hz bass", color = P_GREEN_D, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("mids", color = P_GREEN_D, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("8kHz hiss", color = P_GREEN_D, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun BoxScope.MicGate(onRequest: () -> Unit) {
    Column(
        Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("I would like to listen for a moment.\nNothing is recorded — I only keep the picture it makes.",
             color = P_GREEN, fontSize = 13.sp,
             fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .background(P_ORANGE, RoundedCornerShape(3.dp))
                .clickable { onRequest() }
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text("[ ALLOW MICROPHONE ]", color = Color.White, fontSize = 13.sp,
                 fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COLOUR + TILE LABELS
// ─────────────────────────────────────────────────────────────────────────────

/** Frequency → hue, light-spectrum order: low = red (0°) … high = violet (285°). */
private fun hueForFreq(hz: Float): Float {
    if (hz <= 0f) return 0f
    val frac = ((log10(hz.toDouble()) - log10(FREQ_LO)) /
                (log10(FREQ_HI) - log10(FREQ_LO))).coerceIn(0.0, 1.0)
    return (frac * 285.0).toFloat()
}

private fun freqName(hz: Float): String = when {
    hz < 150f  -> "deep bass"
    hz < 350f  -> "bass"
    hz < 800f  -> "low-mid"
    hz < 1800f -> "midrange"
    hz < 4000f -> "upper-mid"
    else       -> "high / hiss"
}

/** dB below the loudest tone in this place (0 = the loudest, negative = quieter). */
private fun cellDb(cell: MosaicCell): Int = ((cell.loud - 1f) * 42f).toInt()

private fun cellDetail(cell: MosaicCell): String =
    if (cell.freqHz <= 0f) "empty — nothing stood out to fill this tile"
    else "≈ ${cell.freqHz.toInt()} Hz (${freqName(cell.freqHz)}) · " +
         "${cellDb(cell)} dB · t ${"%.1f".format(cell.timeSec)}s"

// ─────────────────────────────────────────────────────────────────────────────
// AUDIO CAPTURE  (raw PCM discarded the moment the tones are pulled out)
// ─────────────────────────────────────────────────────────────────────────────

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

private fun openRecorder(context: Context): AudioRecord? {
    val minBuf = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )
    if (minBuf <= 0) return null
    val bufSize = max(minBuf, SAMPLE_RATE * 2)
    val sources = buildList {
        if (Build.VERSION.SDK_INT >= 24) add(MediaRecorder.AudioSource.UNPROCESSED)
        add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        add(MediaRecorder.AudioSource.MIC)
    }
    val builtInMic = if (Build.VERSION.SDK_INT >= 23) {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    } else null

    for (src in sources) {
        try {
            val rec = AudioRecord(
                src, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
            if (rec.state == AudioRecord.STATE_INITIALIZED) {
                if (builtInMic != null) rec.setPreferredDevice(builtInMic)
                return rec
            }
            rec.release()
        } catch (_: Exception) { /* try next source */ }
    }
    return null
}

private fun recordSeconds(context: Context, seconds: Int): ShortArray? {
    val rec = openRecorder(context) ?: return null
    val total = SAMPLE_RATE * seconds
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

// ─────────────────────────────────────────────────────────────────────────────
// DSP
// ─────────────────────────────────────────────────────────────────────────────

private class Features(
    val bands: FloatArray, val centroidHz: Float, val flatness: Float,
    val rms: Float, val windiness: Float, val dynamics: Float
)

/** In-place iterative radix-2 Cooley–Tukey FFT. */
private fun fft(re: FloatArray, im: FloatArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
        j = j or bit
        if (i < j) {
            val tr = re[i]; re[i] = re[j]; re[j] = tr
            val ti = im[i]; im[i] = im[j]; im[j] = ti
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2.0 * Math.PI / len
        val wRe = cos(ang).toFloat(); val wIm = sin(ang).toFloat()
        var i = 0
        while (i < n) {
            var curRe = 1f; var curIm = 0f
            val half = len / 2
            for (k in 0 until half) {
                val aRe = re[i + k]; val aIm = im[i + k]
                val bRe = re[i + k + half] * curRe - im[i + k + half] * curIm
                val bIm = re[i + k + half] * curIm + im[i + k + half] * curRe
                re[i + k] = aRe + bRe; im[i + k] = aIm + bIm
                re[i + k + half] = aRe - bRe; im[i + k + half] = aIm - bIm
                val nRe = curRe * wRe - curIm * wIm
                curIm = curRe * wIm + curIm * wRe
                curRe = nRe
            }
            i += len
        }
        len = len shl 1
    }
}

/** Exactly [k] band indices, strongest first: prominent local maxima preferred,
 *  then next-strongest, so every slice yields [k] tiles (grid stays full). */
private fun pickTopBands(bands: DoubleArray, k: Int): List<Int> {
    val floor = bands.clone().also { it.sort() }[bands.size / 2]
    val maxima = ArrayList<Int>()
    for (i in bands.indices) {
        val e = bands[i]
        val l = if (i > 0) bands[i - 1] else 0.0
        val r = if (i < bands.size - 1) bands[i + 1] else 0.0
        if (e > floor * 1.4 && e >= l && e >= r) maxima.add(i)
    }
    maxima.sortByDescending { bands[it] }
    if (maxima.size >= k) return maxima.take(k)
    val chosen = maxima.toMutableList()
    for (i in bands.indices.sortedByDescending { bands[it] }) {
        if (chosen.size >= k) break
        if (i !in chosen) chosen.add(i)
    }
    return chosen.take(k)
}

/** Analyse one 3-s scan → its raw tiles (energy un-normalised) + summary bits. */
private fun analyzeScan(pcm: ShortArray?, scanIndex: Int): ScanResult {
    val binHz = SAMPLE_RATE.toDouble() / FFT_SIZE
    val half = FFT_SIZE / 2
    val timeOffset = scanIndex * REC_SECONDS

    if (pcm == null || pcm.size < FFT_SIZE)
        return ScanResult(emptyList(), DoubleArray(half), emptyList(), 0.0, 0)

    val pkBin0 = IntArray(PK_BANDS); val pkBin1 = IntArray(PK_BANDS); val pkFreq = DoubleArray(PK_BANDS)
    for (b in 0 until PK_BANDS) {
        val f0 = FREQ_LO * (FREQ_HI / FREQ_LO).pow(b.toDouble() / PK_BANDS)
        val f1 = FREQ_LO * (FREQ_HI / FREQ_LO).pow((b + 1.0) / PK_BANDS)
        pkBin0[b] = (f0 / binHz).toInt().coerceIn(1, half - 1)
        pkBin1[b] = (f1 / binHz).toInt().coerceIn(pkBin0[b] + 1, half)
        pkFreq[b] = FREQ_LO * (FREQ_HI / FREQ_LO).pow((b + 0.5) / PK_BANDS)
    }

    val window = FloatArray(FFT_SIZE) {
        (0.5 - 0.5 * cos(2.0 * Math.PI * it / (FFT_SIZE - 1))).toFloat()
    }
    val re = FloatArray(FFT_SIZE); val im = FloatArray(FFT_SIZE)
    val numFrames = ((pcm.size - FFT_SIZE) / FFT_HOP + 1).coerceAtLeast(1)

    val sliceBands = Array(SLICES_PER_SCAN) { DoubleArray(PK_BANDS) }
    val magSum = DoubleArray(half)
    val frameEnergy = ArrayList<Double>()

    var frame = 0; var pos = 0
    while (pos + FFT_SIZE <= pcm.size) {
        var fe = 0.0
        for (i in 0 until FFT_SIZE) {
            val w = (pcm[pos + i] / 32768f) * window[i]
            re[i] = w; im[i] = 0f; fe += w.toDouble() * w
        }
        frameEnergy.add(fe)
        fft(re, im)
        val slice = (frame * SLICES_PER_SCAN / numFrames).coerceIn(0, SLICES_PER_SCAN - 1)
        for (b in 0 until PK_BANDS) {
            var acc = 0.0
            for (k in pkBin0[b] until pkBin1[b]) {
                val fq = k * binHz
                val hf = if (fq > 4000.0) (4000.0 / fq).pow(0.7) else 1.0
                val m = hypot(re[k].toDouble(), im[k].toDouble()) * hf
                acc += m; magSum[k] += m
            }
            sliceBands[slice][b] += acc
        }
        frame++; pos += FFT_HOP
    }

    val tiles = ArrayList<RawTile>()
    for (s in 0 until SLICES_PER_SCAN) {
        val t = timeOffset + (s + 0.5f) / SLICES_PER_SCAN * REC_SECONDS
        for (b in pickTopBands(sliceBands[s], PEAKS_PER_SLICE))
            tiles.add(RawTile(t, pkFreq[b].toFloat(), sliceBands[s][b]))
    }

    var sumSq = 0.0
    for (sm in pcm) { val v = sm / 32768.0; sumSq += v * v }
    return ScanResult(tiles, magSum, frameEnergy, sumSq, pcm.size)
}

/** Weave the scans' tiles into one 144-tile mosaic + one narration line. */
private fun combineScans(scans: List<ScanResult>): SoundMosaic {
    val capacity = M_ROWS * M_COLS
    val half = FFT_SIZE / 2
    val binHz = SAMPLE_RATE.toDouble() / FFT_SIZE

    val tiles = scans.flatMap { it.tiles }
    var maxE = 1e-9
    for (t in tiles) if (t.energy > maxE) maxE = t.energy

    val cells = Array(M_ROWS) { r ->
        Array(M_COLS) { c ->
            val idx = r * M_COLS + c
            if (idx < tiles.size) {
                val t = tiles[idx]
                val loud = ((20 * log10(t.energy / maxE) + 42) / 42).coerceIn(0.0, 1.0).toFloat()
                MosaicCell(t.timeSec, t.freqHz, loud)
            } else emptyCell()
        }
    }

    // Combined summary across all scans.
    val magSum = DoubleArray(half)
    val frameEnergy = ArrayList<Double>()
    var sumSq = 0.0; var samples = 0
    for (s in scans) {
        for (i in 0 until half) magSum[i] += s.magSum[i]
        frameEnergy.addAll(s.frameEnergy)
        sumSq += s.sumSq; samples += s.samples
    }
    val totalFrames = frameEnergy.size.coerceAtLeast(1)
    for (i in magSum.indices) magSum[i] /= totalFrames
    val rmsRaw = if (samples > 0) sqrt(sumSq / samples) else 0.0
    val summary = summaryFeatures(magSum, binHz, half, rmsRaw, frameEnergy)

    // Highlights: the loudest tile in each of low / mid / high, so the numbers
    // shown span the spectrum rather than clustering on one tone.
    fun strongestIn(lo: Float, hi: Float): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null; var bl = 0f
        for (r in 0 until M_ROWS) for (c in 0 until M_COLS) {
            val cell = cells[r][c]
            if (cell.freqHz >= lo && cell.freqHz < hi && cell.loud > bl) {
                bl = cell.loud; best = r to c
            }
        }
        return best
    }
    val highlights = listOfNotNull(
        strongestIn(1f, 350f), strongestIn(350f, 1800f), strongestIn(1800f, 20000f)
    )

    return SoundMosaic(cells, describeMosaic(cells, summary), debugLine(summary, "phone mic"), highlights)
}

private fun summaryFeatures(
    mag: DoubleArray, binHz: Double, half: Int, rmsRaw: Double, frameEnergy: List<Double>
): Features {
    val db = 20.0 * log10(rmsRaw + 1e-9)
    val rms = (((db + 58.0) / 28.0).coerceIn(0.0, 1.0)).toFloat()

    val meanFE = if (frameEnergy.isNotEmpty()) frameEnergy.average() else 0.0
    val maxFE = frameEnergy.maxOrNull() ?: 0.0
    val crest = if (meanFE > 1e-12) sqrt(maxFE / meanFE) else 1.0
    val dynamics = (((crest - 1.0) / 2.2).coerceIn(0.0, 1.0)).toFloat()

    var lowRaw = 0.0; var allRaw = 0.0
    for (i in mag.indices) {
        val f = i * binHz; allRaw += mag[i]
        if (f in 20.0..120.0) lowRaw += mag[i]
    }
    val windiness = if (allRaw > 0) (lowRaw / allRaw * 3.0).coerceIn(0.0, 1.0).toFloat() else 0f

    var wSum = 0.0; var eSum = 0.0; var logSum = 0.0; var nNZ = 0
    for (i in 1 until half) {
        val m = mag[i]; val f = i * binHz
        wSum += f * m; eSum += m
        if (m > 1e-9) { logSum += ln(m); nNZ++ }
    }
    val centroid = if (eSum > 0) (wSum / eSum).toFloat() else 0f
    val geoMean = if (nNZ > 0) kotlin.math.exp(logSum / nNZ) else 0.0
    val ariMean = if (half > 1) eSum / (half - 1) else 0.0
    val flatness = if (ariMean > 0) (geoMean / ariMean).coerceIn(0.0, 1.0).toFloat() else 0f

    val fLow = 60.0; val fHigh = (SAMPLE_RATE / 2.0) - binHz
    val bands = FloatArray(NBANDS)
    for (b in 0 until NBANDS) {
        val f0 = fLow * (fHigh / fLow).pow(b.toDouble() / NBANDS)
        val f1 = fLow * (fHigh / fLow).pow((b + 1.0) / NBANDS)
        val i0 = (f0 / binHz).toInt().coerceIn(0, half - 1)
        val i1 = (f1 / binHz).toInt().coerceIn(i0 + 1, half)
        var acc = 0.0; for (i in i0 until i1) acc += mag[i]
        bands[b] = acc.toFloat()
    }
    val floor = bands.clone().also { it.sort() }[bands.size / 2]
    for (b in bands.indices) bands[b] = max(0f, bands[b] - floor)
    val bMax = bands.maxOrNull() ?: 0f
    if (bMax > 0f) for (b in bands.indices) bands[b] = bands[b] / bMax

    return Features(bands, centroid, flatness, rms, windiness, dynamics)
}

// ─────────────────────────────────────────────────────────────────────────────
// NARRATION  —  picks one vague line from a bucket (see the lists up top)
// ─────────────────────────────────────────────────────────────────────────────

private fun describeMosaic(cells: Array<Array<MosaicCell>>, f: Features): String {
    // Where does the energy sit? (weighted by tile loudness)
    var warm = 0.0; var mid = 0.0; var bright = 0.0
    for (row in cells) for (cell in row) {
        if (cell.freqHz <= 0f) continue
        val w = cell.loud.toDouble()
        when {
            cell.freqHz < 350f  -> warm += w
            cell.freqHz < 1800f -> mid += w
            else                -> bright += w
        }
    }
    val dom = max(warm, max(mid, bright))

    val bucket = when {
        f.rms < 0.22f                     -> D_QUIET
        f.rms > 0.55f && f.dynamics > 0.5f -> D_BUSY
        dom == warm                       -> D_LOW
        dom == mid                        -> D_MID
        else                              -> D_HIGH
    }
    if (bucket.isEmpty()) return ""
    // Deterministic pick: same sound → same line.
    val hashIx = abs(f.centroidHz.toInt() + (f.rms * 997).toInt() + (f.dynamics * 331).toInt())
    val line = bucket[hashIx % bucket.size]
    val wind = if (f.windiness > 0.5f) D_WIND else ""
    return line + wind
}

/** Compact stats line for tuning — safe to delete once dialled in. */
private fun debugLine(f: Features, src: String): String {
    fun pct(x: Float) = (x * 100).toInt()
    return "$src · cen ${f.centroidHz.toInt()}Hz · flat ${pct(f.flatness)}% · " +
           "loud ${pct(f.rms)}% · dyn ${pct(f.dynamics)}% · wind ${pct(f.windiness)}%"
}

/** Loudest tile's frequency — the "dominating frequency" shown per place. */
internal fun dominantHz(m: SoundMosaic): Int {
    var best = 0f; var hz = 0f
    for (row in m.cells) for (cell in row) {
        if (cell.freqHz > 0f && cell.loud > best) { best = cell.loud; hz = cell.freqHz }
    }
    return hz.toInt()
}

// ─────────────────────────────────────────────────────────────────────────────
// FINAL GALLERY  —  all places at once, with metadata + save
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SoundMosaicGallery(captures: List<PlaceCapture>, onBack: () -> Unit) {
    val context = LocalContext.current
    val timeFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    fun save(cap: PlaceCapture) {
        val ok = saveMosaicToGallery(context, cap)
        Toast.makeText(context, if (ok) "Saved to gallery" else "Save failed",
                       Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxSize().background(P_BG)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("WHAT I HEARD, IN THREE PLACES", color = P_AMBER, fontSize = 15.sp,
                 fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                 textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            FrequencyLegend()
            Spacer(Modifier.height(20.dp))

            captures.forEach { cap ->
                Text("Location ${cap.index}", color = P_GREEN, fontSize = 14.sp,
                     fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text("%.5f, %.5f".format(cap.lat, cap.lon), color = P_GREEN_D,
                     fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text(timeFmt.format(Date(cap.timeMs)), color = P_GREEN_D,
                     fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("dominant ≈ ${cap.dominantHz} Hz (${freqName(cap.dominantHz.toFloat())})",
                     color = P_GREEN_D, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                MosaicGrid(cap.mosaic, null, cap.mosaic.highlights) { _, _ -> }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .background(Color(0xFF2A2A2A), RoundedCornerShape(3.dp))
                        .border(1.dp, P_GREEN_D, RoundedCornerShape(3.dp))
                        .clickable { save(cap) }
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Text("[ SAVE THIS ]", color = P_GREEN, fontSize = 12.sp,
                         fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(28.dp))
            }

            Text("(saved images don't include the location)", color = P_GREEN_D,
                 fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .background(Color(0xFF2A2A2A), RoundedCornerShape(3.dp))
                    .border(1.dp, P_GREEN_D, RoundedCornerShape(3.dp))
                    .clickable { captures.forEach { save(it) } }
                    .padding(horizontal = 24.dp, vertical = 11.dp)
            ) {
                Text("[ SAVE ALL ]", color = P_GREEN, fontSize = 13.sp,
                     fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .background(P_ORANGE, RoundedCornerShape(3.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 30.dp, vertical = 14.dp)
            ) {
                Text("[ BACK TO THE CITY ]", color = Color.White, fontSize = 14.sp,
                     fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Draw a mosaic to a Bitmap for saving. [caption] holds date + dominant Hz —
 *  never the coordinates, so a shared image can't give the place away. */
private fun renderMosaicBitmap(m: SoundMosaic, caption: String?): Bitmap {
    val tile = 64; val gap = 5; val pad = 28
    val gridW = M_COLS * tile + (M_COLS - 1) * gap
    val gridH = M_ROWS * tile + (M_ROWS - 1) * gap
    val footer = if (caption != null) 54 else 0
    val w = gridW + pad * 2
    val h = gridH + pad * 2 + footer
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    c.drawColor(AndroidColor.rgb(10, 15, 10))
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    for (r in 0 until M_ROWS) for (col in 0 until M_COLS) {
        val cell = m.cells[r][col]
        val left = (pad + col * (tile + gap)).toFloat()
        val top = (pad + r * (tile + gap)).toFloat()
        p.color = if (cell.freqHz <= 0f) AndroidColor.rgb(16, 16, 16)
                  else AndroidColor.HSVToColor(floatArrayOf(hueForFreq(cell.freqHz), 0.85f, 0.40f + 0.60f * cell.loud))
        c.drawRoundRect(RectF(left, top, left + tile, top + tile), 6f, 6f, p)
    }
    if (caption != null) {
        p.color = AndroidColor.rgb(51, 170, 85)
        p.typeface = Typeface.MONOSPACE
        p.textSize = 26f
        p.textAlign = Paint.Align.CENTER
        c.drawText(caption, w / 2f, h - footer / 2f + 9f, p)
    }
    return bmp
}

/** Save one place's mosaic to the device gallery (Pictures/JustACalculator). */
private fun saveMosaicToGallery(context: Context, cap: PlaceCapture): Boolean {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(cap.timeMs))
    val caption = "$date   ~${cap.dominantHz} Hz"    // deliberately no coordinates
    val bmp = renderMosaicBitmap(cap.mosaic, caption)
    val name = "sound_mosaic_loc${cap.index}_${cap.timeMs}.png"
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JustACalculator")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (_: Exception) {
        false
    }
}
