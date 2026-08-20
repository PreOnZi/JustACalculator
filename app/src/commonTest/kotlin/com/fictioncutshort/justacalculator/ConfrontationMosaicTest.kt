package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.ui.screens.CONF_MOSAIC_CELLS
import com.fictioncutshort.justacalculator.ui.screens.M_COLS
import com.fictioncutshort.justacalculator.ui.screens.M_ROWS
import com.fictioncutshort.justacalculator.ui.screens.hueForFreq
import com.fictioncutshort.justacalculator.ui.screens.pleadingMosaic
import com.fictioncutshort.justacalculator.ui.screens.strangersWalk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * conf10 shows the player their own sound mosaic and then rewrites it, tile by
 * tile, into a man begging. The forgery is generated rather than authored, so
 * nothing on screen would look broken if it came out wrong — a grid of noise
 * and a grid of speech are both just coloured squares. These pin the properties
 * that make it read as a voice.
 */
class ConfrontationMosaicTest {

    @Test
    fun forgeryFillsTheWholeGrid() {
        val m = pleadingMosaic()
        assertEquals(M_ROWS, m.cells.size)
        for (row in m.cells) assertEquals(M_COLS, row.size)
        assertEquals(144, CONF_MOSAIC_CELLS)
    }

    @Test
    fun forgeryIsDeterministic() {
        val a = pleadingMosaic()
        val b = pleadingMosaic()
        for (r in 0 until M_ROWS) for (c in 0 until M_COLS) {
            assertEquals(a.cells[r][c].freqHz, b.cells[r][c].freqHz, "freq at $r,$c")
            assertEquals(a.cells[r][c].loud, b.cells[r][c].loud, "loud at $r,$c")
        }
    }

    @Test
    fun forgerySoundsLikeAManNotLikeAStreet() {
        val cells = pleadingMosaic().cells.flatMap { it.toList() }
        val voiced = cells.filter { it.freqHz > 0f }
        val gaps = cells.size - voiced.size
        // Silence between phrases: the part that reads as breathing. Present,
        // but nowhere near half the picture.
        assertTrue(gaps in 8..48, "expected some breathing gaps, got $gaps")
        // A male fundamental and its low harmonics dominate. This is the whole
        // reason the beat reads at all: hue runs on log frequency, so a full
        // harmonic stack smears each syllable across the entire rainbow and the
        // forgery comes out looking exactly like the street it replaced. Two
        // thirds of it has to stay in the red/orange/yellow end.
        val low = voiced.count { it.freqHz < 700f }
        assertTrue(low * 3 >= voiced.size * 2, "expected a low-dominated picture, got $low/${voiced.size}")
        // The fundamental itself climbs as he panics: the first phrase is
        // pitched below the last.
        val firstF0 = voiced.first().freqHz
        val lastF0 = voiced.last { it.freqHz < 300f }.freqHz
        assertTrue(lastF0 > firstF0, "pitch should rise: $firstF0 → $lastF0")
        // And it gets louder. Compared in halves, because individual syllables
        // decay inside themselves.
        val half = voiced.size / 2
        val early = voiced.take(half).map { it.loud }.average()
        val late = voiced.drop(half).map { it.loud }.average()
        assertTrue(late > early, "should build: $early → $late")
    }

    @Test
    fun standInIsDatedToTheDayBefore() {
        val now = 1_700_000_000_000L
        val cap = strangersWalk(now)
        val ageH = (now - cap.timeMs) / 3_600_000.0
        assertTrue(ageH in 24.0..30.0, "expected roughly a day earlier, was ${ageH}h")
        assertTrue(cap.dominantHz > 0, "the stand-in needs a dominant tone for its caption")
    }

    @Test
    fun hueSpansTheSpectrumInFrequencyOrder() {
        // The forgery is only legible as a change because it lands on the same
        // colours Building 5 taught: low = red, high = violet.
        assertTrue(hueForFreq(80f) < 5f)
        assertTrue(hueForFreq(8000f) > 280f)
        assertTrue(hueForFreq(110f) < hueForFreq(880f))
        assertTrue(hueForFreq(880f) < hueForFreq(4400f))
        // Out-of-range tones are clamped, not wrapped into the wrong colour.
        assertEquals(hueForFreq(8000f), hueForFreq(20000f))
    }
}
