package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import kotlinx.coroutines.delay

/**
 * TruthRevealOverlay.kt
 *
 * The payoff for picking the half-faded "4) The truth" at step 89. Takes the
 * whole screen: the calculator glitches out into the same TV static used
 * during dormancy ([DormancyStaticBackground]), then the static becomes the
 * backdrop for a single line of text.
 *
 * Timing lives in EffectsController.runTruthReveal — this composable only
 * renders whatever phase it's handed. All pointer events are swallowed so
 * stray taps can't reach the calculator keyboard underneath.
 *
 * @param phase 1 = glitching static only, 2 = static + the line.
 */
@Composable
fun TruthRevealOverlay(phase: Int) {
    // Phase 1 punches the static in and out to read as a hard glitch rather
    // than a fade; phase 2 settles into steady noise so the text is legible.
    var blackFlash by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        if (phase >= 2) {
            blackFlash = false
            return@LaunchedEffect
        }
        while (true) {
            blackFlash = true
            delay(45)
            blackFlash = false
            delay(110)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!blackFlash) {
            DormancyStaticBackground(modifier = Modifier.fillMaxSize())
        }

        if (phase >= 2) {
            // Scrim keeps the line readable against the noise without hiding
            // the static behind it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
            Text(
                text = "When you're not here yet it talks to you. it counts.",
                fontSize = 26.sp,
                lineHeight = 34.sp,
                color = Color.White,
                fontFamily = CalculatorDisplayFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
