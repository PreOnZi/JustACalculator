package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * RedButtonPuzzle.kt
 *
 * The panel that opens when the player stands on the red button on the top
 * floor of the city's DEL ruin (which only exists if they ran the optional
 * software update back in the console).
 *
 * The equation reads:
 *
 *     DOOR = [d][d] [op] h[hh] m[mm]
 *
 * and the three answers come from three different places in the game, none of
 * them stated outright:
 *   · the two digits  — 83, the number on the notification icon
 *   · the operator    — ×, hinted at in the trailer
 *   · the h/m pair    — the screen time the calculator quoted during its rant,
 *                       so it is different for every player. Matched within
 *                       [TOLERANCE_MIN] minutes either way, because nobody
 *                       remembers it to the minute.
 *
 * Nothing here validates on its own: [onSolved] is called when every field
 * matches and [onWrong] when it does not — the caller owns what either means,
 * including counting attempts and burning the button out.
 */

/** The digit pair the player must dial in (from the notification icon). */
private const val ANSWER_DIGITS = 83

/** Minutes of slack allowed on the screen-time answer, either side. */
const val TOLERANCE_MIN = 20

/**
 * Wrong answers allowed before the button burns out for good. There is no
 * on-screen counter — the only feedback a wrong answer gives is the sting that
 * plays, so the pressure is felt rather than displayed.
 */
const val RED_BUTTON_MAX_ATTEMPTS = 4

/** Spinner values: 0..9 plus a blank, so a one-digit answer is expressible. */
private val DIGIT_VALUES: List<String> = listOf(" ") + (0..9).map { it.toString() }

private val OPERATORS = listOf("+", "-", "*", "/")

private val PanelBg = Color(0xFF12100E)
private val PanelEdge = Color(0xFF8B2018)
private val Ink = Color(0xFFE8E2D8)
private val Dim = Color(0xFF7A7168)
private val ButtonRed = Color(0xFF8B2018)

/**
 * @param targetMinutes every screen-time figure the calculator has quoted, in
 *        whole minutes. Any one of them is accepted (within [TOLERANCE_MIN]),
 *        because the rant replays and each telling gives a different number —
 *        the player may have noted down any of them, however long ago.
 */
@Composable
fun RedButtonPuzzle(
    targetMinutes: List<Long>,
    onSolved: () -> Unit,
    onWrong: () -> Unit,
    onDismiss: () -> Unit
) {
    // Index into DIGIT_VALUES — 0 is the blank, so both start empty and the
    // player has to choose every field deliberately.
    var d0 by remember { mutableIntStateOf(0) }
    var d1 by remember { mutableIntStateOf(0) }
    var opIdx by remember { mutableIntStateOf(0) }
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }

    fun digitsValue(): Int? {
        val a = DIGIT_VALUES[d0].trim()
        val b = DIGIT_VALUES[d1].trim()
        if (a.isEmpty() && b.isEmpty()) return null
        return (a + b).toIntOrNull()
    }

    fun isCorrect(): Boolean {
        if (digitsValue() != ANSWER_DIGITS) return false
        if (OPERATORS[opIdx] != "*") return false
        val enteredMin = hours * 60L + minutes
        return targetMinutes.any { abs(enteredMin - it) <= TOLERANCE_MIN }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(10.dp))
                .background(PanelBg)
                .border(1.dp, PanelEdge, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "The button",
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(18.dp))

            // ── DOOR = [d][d] [op] h[hh] m[mm] ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DOOR =",
                    color = Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(10.dp))
                Spinner(
                    text = DIGIT_VALUES[d0],
                    onUp = { d0 = (d0 + 1) % DIGIT_VALUES.size },
                    onDown = { d0 = (d0 + DIGIT_VALUES.size - 1) % DIGIT_VALUES.size }
                )
                Spinner(
                    text = DIGIT_VALUES[d1],
                    onUp = { d1 = (d1 + 1) % DIGIT_VALUES.size },
                    onDown = { d1 = (d1 + DIGIT_VALUES.size - 1) % DIGIT_VALUES.size }
                )
                Spacer(Modifier.width(6.dp))
                Spinner(
                    text = OPERATORS[opIdx],
                    onUp = { opIdx = (opIdx + 1) % OPERATORS.size },
                    onDown = { opIdx = (opIdx + OPERATORS.size - 1) % OPERATORS.size }
                )
                Spacer(Modifier.width(6.dp))
                Prefixed("h") {
                    Spinner(
                        text = hours.toString(),
                        onUp = { hours = (hours + 1) % 61 },
                        onDown = { hours = (hours + 60) % 61 }
                    )
                }
                Prefixed("m") {
                    Spinner(
                        text = minutes.toString(),
                        onUp = { minutes = (minutes + 1) % 61 },
                        onDown = { minutes = (minutes + 60) % 61 }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                PanelButton(text = "LEAVE", filled = false, onClick = onDismiss)
                PanelButton(text = "ENTER", filled = true, onClick = {
                    if (isCorrect()) onSolved() else onWrong()
                })
            }
        }
    }
}

/** Small label sitting immediately left of a spinner ("h" / "m"). */
@Composable
private fun Prefixed(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Dim,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 2.dp)
        )
        content()
    }
}

/**
 * One rolling field: a value with a chevron above and below. Tapping either
 * chevron steps the value, wrapping at both ends.
 */
@Composable
private fun Spinner(
    text: String,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Chevron(up = true, onClick = onUp)
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF221E1A))
                .border(1.dp, Color(0xFF3A342E), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
        Chevron(up = false, onClick = onDown)
    }
}

@Composable
private fun Chevron(up: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 22.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (up) "▲" else "▼",
            color = Dim,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun PanelButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) ButtonRed else Color(0xFF221E1A))
            .border(1.dp, if (filled) ButtonRed else Color(0xFF3A342E), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled) Color.White else Dim,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
