package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * DormancyOverlay.kt
 *
 * Full-screen overlay shown after the rant ends.
 *
 * Two sub-phases:
 *
 * 1. STATIC — Grey TV static noise effect, no interaction. Lasts until
 *    T+6min (when first notification fires).
 *
 * 2. RAD BUTTONS — Static stays as background texture; RAD buttons appear
 *    one by one (one per 30 seconds, tied to notification schedule). Each
 *    button must be pressed to continue. When all 5 are pressed, triggers
 *    [onAllPressed] to move to phase 2.
 *
 * Vibration intensifies with each button pressed (handled by caller via
 * [onButtonPressed] callback which updates vibrationIntensity in state).
 */
@Composable
fun DormancyOverlay(
    /** Number of RAD buttons currently visible (0 = static only, 1-5 = buttons) */
    radButtonsVisible: Int,
    /** Set of button indices (0-based) that have been pressed */
    pressedButtons: Set<Int>,
    /** Called when a RAD button is tapped — passes button index */
    onButtonPressed: (Int) -> Unit,
    /** Called when all 5 buttons are pressed */
    onAllPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Notify parent when all visible buttons are pressed
    LaunchedEffect(pressedButtons, radButtonsVisible) {
        if (radButtonsVisible > 0 && pressedButtons.size >= radButtonsVisible) {
            delay(800)
            onAllPressed()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // ── TV Static layer (always present) ────────────────────────────────
        StaticNoise()

        // ── Dark overlay to make buttons readable ────────────────────────────
        if (radButtonsVisible > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
            )
        }

        // ── RAD Buttons ──────────────────────────────────────────────────────
        if (radButtonsVisible > 0) {
            RadButtonScatter(
                visible = radButtonsVisible,
                pressed = pressedButtons,
                onPress = onButtonPressed,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ── TV Static Effect ─────────────────────────────────────────────────────────

@Composable
private fun StaticNoise(modifier: Modifier = Modifier) {
    // We use a Canvas-like approach with animated random noise pixels
    // Each "grain" is a tiny Box at a random position, refreshed rapidly.
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(80) // ~12fps noise refresh
            tick++
        }
    }

    // Render a grid of noise cells
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val cellSize = 6.dp
        val cols = (maxWidth / cellSize).toInt().coerceAtLeast(1)
        val rows = (maxHeight / cellSize).toInt().coerceAtLeast(1)

        // Use tick as seed so it changes each frame
        val rng = remember(tick) { Random(tick * 7919L) }

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val gray = rng.nextInt(60, 200)
                val alpha = rng.nextFloat() * 0.7f + 0.1f
                Box(
                    modifier = Modifier
                        .offset(x = cellSize * col, y = cellSize * row)
                        .size(cellSize)
                        .background(Color(gray, gray, gray, (alpha * 255).toInt()))
                )
            }
        }
    }
}

// ── RAD Button Scatter ────────────────────────────────────────────────────────

/**
 * Scatters RAD buttons chaotically across the screen, matching the rant-mode
 * calculator button style (dark red background, white text).
 * Positions are seeded by index so they stay stable across recompositions.
 */
@Composable
private fun RadButtonScatter(
    visible: Int,
    pressed: Set<Int>,
    onPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val screenW = maxWidth
        val screenH = maxHeight

        val btnW = 110.dp
        val btnH = 52.dp

        // Pre-generate stable random positions seeded per-index
        val positions = remember(visible) {
            (0 until visible).map { i ->
                val rng = Random(i * 6271L + 1337L)
                val x = rng.nextFloat() * (screenW.value - btnW.value)
                val y = rng.nextFloat() * (screenH.value - btnH.value)
                Pair(x.dp, y.dp)
            }
        }

        for (i in 0 until visible) {
            val (x, y) = positions[i]
            val isPressed = pressed.contains(i)

            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(400)) + scaleIn(tween(400, easing = EaseOutBack)),
                modifier = Modifier
                    .offset(x = x, y = y)
                    .width(btnW)
                    .height(btnH)
            ) {
                RantStyleRadButton(
                    index = i,
                    isPressed = isPressed,
                    width = btnW,
                    height = btnH,
                    onClick = { if (!isPressed) onPress(i) }
                )
            }
        }
    }
}

@Composable
private fun RantStyleRadButton(
    index: Int,
    isPressed: Boolean,
    width: Dp,
    height: Dp,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val pulseAnim = rememberInfiniteTransition(label = "pulse_$index")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = if (!isPressed) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800 + index * 80, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_$index"
    )

    // Match rant-mode calculator button: dark red bg, white text
    val bgColor = if (isPressed) Color(0xFF3A0000) else Color(0xFF8B0000)
    val borderColor = if (isPressed) Color(0xFF6B0000) else Color(0xFFCC0000)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .height(height)
            .scale(if (!isPressed) scale else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isPressed) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            }
    ) {
        Text(
            text = "RAD",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        )
    }
}