package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.platform.AppContext
import com.fictioncutshort.justacalculator.platform.formatDateTime
import com.fictioncutshort.justacalculator.platform.saveImageToGallery
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// THE MOSAIC IN THE CELL  —  conf10
//
// Building 5 had the player walk to three places and hold their phone up while
// it listened. It kept no audio, only a picture: a 12×12 grid where every tile
// is one tone it heard, coloured by frequency and brightened by loudness, laid
// out in the order it heard them.
//
// In the cell, that picture comes back. Seventeen seconds into conf10 it is on
// screen — theirs, with their own date on it. Ten seconds later it is edited in
// front of them, tile by tile, left to right: the scatter of a street becomes
// the shape of a man's voice, low and rhythmic and rising, with black gaps
// where he stops to breathe. Nothing about the picture says "forged". It just
// stops being what they recorded and starts being evidence.
//
// Then it saves itself to their gallery, dated to their own walk, without
// asking. That is the whole point of the beat: the file is on their phone now.
//
// Everything here draws through Building5SoundProto's own colour mapping
// ([hueForFreq]) and grid size, so the forgery is rendered in exactly the
// palette the player already learned to read.
// ─────────────────────────────────────────────────────────────────────────────

/** conf10 runs 32.66 s. The picture appears at 17 s, the edit starts at 27 s and
 *  has to be finished with the track still playing — an edit that outlives the
 *  voice explaining it reads as a loading bar instead of as a threat. */
internal const val CONF_MOSAIC_SHOW_MS     = 17_000L
internal const val CONF_MOSAIC_EDIT_MS     = 27_000L
internal const val CONF_MOSAIC_EDIT_DUR_MS = 4_200L        // done at 31.2 s
internal const val CONF_MOSAIC_HOLD_MS     = 1_800L        // after the save, before conf11

/** Tiles in a mosaic — the number the edit counts up to. */
internal const val CONF_MOSAIC_CELLS = M_ROWS * M_COLS

// A place-picture spans three 3-second scans.
private const val MOSAIC_SPAN_SEC = 9f

/**
 * The mosaic the confrontation puts on screen: the LAST place the player stopped
 * and listened at.
 *
 * Read straight out of [loadCaptures] rather than from anything the player chose
 * to keep — Building 5 persists every completed place whether or not they ever
 * pressed SAVE, so this works for anyone who walked it. For anyone who did not,
 * [strangersWalk] stands in.
 */
internal fun confMosaicCapture(context: AppContext, nowMs: Long): PlaceCapture =
    loadCaptures(context).lastOrNull() ?: strangersWalk(nowMs)

/**
 * The stand-in for a player who never walked Building 5.
 *
 * Dated to yesterday afternoon, because the line only lands if the picture has a
 * date on it they cannot argue with. Deterministic (fixed seed), so it is the
 * same picture on every run rather than a new one each time the ending replays.
 */
internal fun strangersWalk(nowMs: Long): PlaceCapture {
    val rnd = Random(0x5B1A7)
    var t = 0f
    val step = MOSAIC_SPAN_SEC / CONF_MOSAIC_CELLS
    val cells = Array(M_ROWS) {
        Array(M_COLS) {
            t += step
            // A street: weighted toward traffic-weight lows and the busy middle,
            // a thin scatter of high detail, and the occasional tile where
            // nothing stood out at all.
            when (rnd.nextFloat()) {
                in 0.00f..0.10f -> MosaicCell(t, 0f, 0f)
                in 0.10f..0.50f ->
                    MosaicCell(t, 90f + rnd.nextFloat() * 260f, 0.35f + rnd.nextFloat() * 0.5f)
                in 0.50f..0.82f ->
                    MosaicCell(t, 350f + rnd.nextFloat() * 1450f, 0.25f + rnd.nextFloat() * 0.55f)
                else ->
                    MosaicCell(t, 1800f + rnd.nextFloat() * 5200f, 0.15f + rnd.nextFloat() * 0.45f)
            }
        }
    }
    val mosaic = SoundMosaic(cells, "Somewhere you were walking.", "", emptyList())
    return PlaceCapture(
        index = 3,
        mosaic = mosaic,
        lat = 0.0,
        lon = 0.0,
        timeMs = nowMs - 25L * 60L * 60L * 1000L - 17L * 60L * 1000L,   // yesterday, mid-afternoon
        dominantHz = dominantHz(mosaic),
    )
}

/**
 * The same grid, re-scored as a man begging.
 *
 * Built as speech rather than as noise, because that is what makes it read as a
 * person: a fundamental around 110 Hz with its harmonics stacked above it,
 * a vowel formant in the mids, a breath at the end of a phrase, and silence
 * between phrases. Across the nine seconds the pitch climbs and the phrases get
 * louder and shorter — the sound of someone running out of arguments.
 *
 * Deterministic: the same forgery every time the ending is played.
 */
internal fun pleadingMosaic(): SoundMosaic {
    val rnd = Random(0x5EA1)
    val cells = Array(M_ROWS) { Array(M_COLS) { MosaicCell(0f, 0f, 0f) } }
    val step = MOSAIC_SPAN_SEC / CONF_MOSAIC_CELLS
    var i = 0
    var t = 0f
    fun put(freq: Float, loud: Float) {
        if (i >= CONF_MOSAIC_CELLS) return
        cells[i / M_COLS][i % M_COLS] = MosaicCell(t, freq, loud)
        t += step
        i++
    }
    while (i < CONF_MOSAIC_CELLS) {
        val prog = i.toFloat() / CONF_MOSAIC_CELLS
        // Pitch rises with panic; phrases shorten and get louder.
        val f0 = 104f + 64f * prog + (rnd.nextFloat() - 0.5f) * 9f
        val syl = (6 - (prog * 2.5f).toInt()).coerceIn(3, 6)
        val peak = (0.66f + 0.34f * prog).coerceAtMost(1f)
        for (k in 0 until syl) {
            // Weighted hard toward the fundamental and its second harmonic. A
            // full harmonic stack is what a voice really is, but on a log-hue
            // grid it smears each syllable across the whole rainbow and the
            // forgery ends up looking exactly like the street it replaced. Kept
            // low, the picture turns red and stays red — which is the change the
            // player is meant to see happen.
            val partial = when {
                k == 0 -> f0
                k == 1 -> f0 * 2f
                k == 2 -> if (rnd.nextFloat() < 0.45f) f0 else f0 * 3f
                // The vowel: what stops it reading as a machine tone.
                rnd.nextFloat() < 0.30f -> 520f + rnd.nextFloat() * 680f
                else -> f0 * (1 + rnd.nextInt(2))
            }
            // Bright head, dark tail: a cry, not a hum.
            val fall = 1f - k.toFloat() / (syl + 1f)
            put(partial, (peak * (0.30f + 0.70f * fall * fall) *
                (0.85f + rnd.nextFloat() * 0.3f)).coerceIn(0.08f, 1f))
        }
        // The breath after it. Quiet, and high enough to sit at the violet end.
        if (rnd.nextFloat() < 0.45f) put(3200f + rnd.nextFloat() * 2400f, 0.14f + rnd.nextFloat() * 0.18f)
        // And the gap. Empty tiles read as black — the silence is the part that
        // makes the rhythm legible as breathing rather than as a siren.
        repeat(1 + rnd.nextInt(3)) { put(0f, 0f) }
    }
    return SoundMosaic(cells, "A man, and he is not calm.", "", emptyList())
}

/**
 * Write the finished forgery to the phone's gallery under Building 5's own
 * naming and caption, so it sits in the roll looking exactly like the ones the
 * player saved themselves. Never carries coordinates — [renderMosaic] does not
 * put them in the image.
 */
internal fun saveConfMosaic(
    cap: PlaceCapture,
    edited: SoundMosaic,
    textMeasurer: TextMeasurer,
): Boolean {
    val forged = PlaceCapture(
        index = cap.index,
        mosaic = edited,
        lat = cap.lat,
        lon = cap.lon,
        timeMs = cap.timeMs,             // their date, not today's
        dominantHz = dominantHz(edited),
    )
    return try {
        saveImageToGallery(mosaicFileName(forged), renderMosaic(forged, textMeasurer))
    } catch (_: Throwable) {
        false
    }
}

/** Cell colour — Building 5's mapping, unchanged. */
private fun cellColor(cell: MosaicCell): Color =
    if (cell.freqHz <= 0f) Color(0xFF101010)
    else Color.hsv(hueForFreq(cell.freqHz), 0.85f, 0.40f + 0.60f * cell.loud)

/**
 * The picture, mid-edit.
 *
 * [flipped] tiles of [edited] have replaced the original, in reading order —
 * which is also time order, so the change sweeps across the grid like a write
 * head rather than appearing as a scatter. The tile being written carries a
 * white ring; there is no progress bar anywhere, deliberately.
 *
 * No touch handling at all: the player is locked in a cell, and the panel must
 * not swallow the stick underneath it either.
 */
@Composable
internal fun ConfMosaicOverlay(
    cap: PlaceCapture,
    edited: SoundMosaic,
    flipped: Int,
    saved: Boolean,
) {
    val shown = remember(cap, edited, flipped) {
        Array(M_ROWS) { r ->
            Array(M_COLS) { c ->
                if (r * M_COLS + c < flipped) edited.cells[r][c] else cap.mosaic.cells[r][c]
            }
        }
    }
    // The caption re-reads the picture rather than the record, so the "dominant"
    // figure changes under the player as the tiles do.
    val hz = remember(flipped) {
        var best = 0f; var f = 0f
        for (row in shown) for (cell in row) {
            if (cell.freqHz > 0f && cell.loud > best) { best = cell.loud; f = cell.freqHz }
        }
        f.toInt()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.80f)
                .background(Color(0xE60A0F0A), RoundedCornerShape(10.dp))
                .border(1.5.dp, Color(0xFFE0A24E), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "LOCATION ${cap.index}",
                color = Color(0xFFE0A24E), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                formatDateTime(cap.timeMs),
                color = Color(0xFF33AA55), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF33AA55), RoundedCornerShape(4.dp))
                    .padding(3.dp)
            ) {
                for (r in 0 until M_ROWS) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until M_COLS) {
                            val writing = r * M_COLS + c == flipped - 1 &&
                                flipped in 1 until CONF_MOSAIC_CELLS
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(cellColor(shown[r][c]))
                                    .then(
                                        if (writing)
                                            Modifier.border(1.5.dp, Color.White, RoundedCornerShape(2.dp))
                                        else Modifier
                                    )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                "dominant ≈ $hz Hz (${freqName(hz.toFloat())})",
                color = Color(0xFF33AA55), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            )
            if (saved) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "saved to your gallery",
                    color = Color(0xFFE0A24E), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
