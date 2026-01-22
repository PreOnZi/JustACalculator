package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.data.ScrambleLetter
import com.fictioncutshort.justacalculator.data.ScrambleSlot
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.RetroDisplayGreen
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay

@Composable
fun ScrambleGameOverlay(
    phase: Int,
    message: String,
    letters: List<ScrambleLetter>,
    slots: List<ScrambleSlot>,
    selectedLetterId: Int,
    punishmentUntil: Long,
    onLetterTap: (Int) -> Unit,
    onSlotTap: (Int) -> Unit,
    onBackToDecisions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (phase) {
            1, 2 -> {
                Text(
                    text = message,
                    fontSize = 28.sp,
                    color = RetroDisplayGreen,
                    fontFamily = CalculatorDisplayFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }

            3 -> {
                val hasSelection = selectedLetterId >= 0

                // Animate slot scale when letter is selected
                val slotScale by animateFloatAsState(
                    targetValue = if (hasSelection) 1.1f else 1f,
                    animationSpec = tween(300),
                    label = "slotScale"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // === SCATTERED LETTERS AREA ===
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        val scatterPositions = listOf(
                            0.05f to 0.1f,
                            0.38f to 0.0f,
                            0.70f to 0.12f,
                            0.12f to 0.50f,
                            0.48f to 0.45f,
                            0.78f to 0.52f,
                            0.22f to 0.85f,
                            0.58f to 0.80f
                        )

                        val unplacedLetters = letters.filter { !it.isPlaced }

                        unplacedLetters.forEachIndexed { index, letter ->
                            val (xPercent, yPercent) = scatterPositions.getOrElse(index) { 0.5f to 0.5f }
                            val isSelected = letter.id == selectedLetterId

                            LetterTile(
                                letter = letter.letter,
                                isSelected = isSelected,
                                onClick = { onLetterTap(letter.id) },
                                modifier = Modifier
                                    .offset(
                                        x = (xPercent * 260).dp,
                                        y = (yPercent * 140).dp
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // === SLOTS GRID (centered) ===
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.scale(slotScale)
                    ) {
                        // Top row: I A M (indices 0, 1, 2)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (i in 0..2) {
                                val slot = slots.getOrNull(i)
                                val placedLetter = letters.find { it.id == slot?.filledBy }

                                SlotTile(
                                    placedLetter = placedLetter,
                                    isHighlighted = hasSelection && slot?.filledBy == -1,
                                    onClick = {
                                        if (hasSelection) {
                                            onSlotTap(i)
                                        } else if (placedLetter != null) {
                                            // Tap placed letter to select it (for moving)
                                            onLetterTap(placedLetter.id)
                                        }
                                    }
                                )
                            }
                        }

                        // Bottom row: S O R R Y (indices 3, 4, 5, 6, 7)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (i in 3..7) {
                                val slot = slots.getOrNull(i)
                                val placedLetter = letters.find { it.id == slot?.filledBy }

                                SlotTile(
                                    placedLetter = placedLetter,
                                    isHighlighted = hasSelection && slot?.filledBy == -1,
                                    onClick = {
                                        if (hasSelection) {
                                            onSlotTap(i)
                                        } else if (placedLetter != null) {
                                            onLetterTap(placedLetter.id)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // Hint text
                    Text(
                        text = if (hasSelection) "Tap a slot to place" else "Tap a letter to select",
                        fontSize = 14.sp,
                        color = RetroDisplayGreen.copy(alpha = 0.5f),
                        fontFamily = CalculatorDisplayFont
                    )
                }
            }

            4, 5 -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = message,
                        fontSize = 26.sp,
                        color = RetroDisplayGreen,
                        fontFamily = CalculatorDisplayFont,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )

                    if (phase == 5) {
                        Spacer(modifier = Modifier.height(40.dp))

                        Button(
                            onClick = onBackToDecisions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RetroDisplayGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .width(220.dp)
                                .height(56.dp)
                        ) {
                            Text(
                                text = "Back to decisions",
                                fontSize = 18.sp,
                                fontFamily = CalculatorDisplayFont,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            // Punishment phase
            10 -> {
                var remainingSeconds by remember { mutableIntStateOf(0) }

                LaunchedEffect(punishmentUntil) {
                    while (true) {
                        val remaining = ((punishmentUntil - System.currentTimeMillis()) / 1000).toInt()
                        remainingSeconds = remaining.coerceAtLeast(0)
                        if (remaining <= 0) {
                            onBackToDecisions()
                            break
                        }
                        delay(1000)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = message,
                        fontSize = 24.sp,
                        color = RetroDisplayGreen,
                        fontFamily = CalculatorDisplayFont,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        text = "${remainingSeconds}s",
                        fontSize = 48.sp,
                        color = RetroDisplayGreen.copy(alpha = 0.5f),
                        fontFamily = CalculatorDisplayFont
                    )
                }
            }
        }
    }
}


@Composable
private fun LetterTile(
    letter: Char,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = tween(200),
        label = "letterScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(55.dp)
            .background(
                if (isSelected) RetroDisplayGreen.copy(alpha = 0.6f)
                else RetroDisplayGreen.copy(alpha = 0.25f),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.toString(),
            fontSize = 32.sp,
            color = if (isSelected) Color.Black else RetroDisplayGreen,
            fontFamily = CalculatorDisplayFont,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SlotTile(
    placedLetter: ScrambleLetter?,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.15f else 1f,
        animationSpec = tween(300),
        label = "slotPulse"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(55.dp)
            .background(
                when {
                    placedLetter != null -> RetroDisplayGreen.copy(alpha = 0.35f)
                    isHighlighted -> RetroDisplayGreen.copy(alpha = 0.2f)
                    else -> Color(0xFF333333)
                },
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (placedLetter != null) {
            Text(
                text = placedLetter.letter.toString(),
                fontSize = 32.sp,
                color = RetroDisplayGreen,
                fontFamily = CalculatorDisplayFont,
                fontWeight = FontWeight.Bold
            )
        }
    }
}