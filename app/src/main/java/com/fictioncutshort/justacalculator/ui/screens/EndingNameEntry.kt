package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.RetroCream

/**
 * EndingNameEntry.kt
 *
 * "What is your actual name?"
 *
 * The last thing the calculator ever asks, and the only time it asks for
 * something true. It has spent the whole story calling the player Rad — a name
 * it picked — and it has no text field to offer, because it never had one: the
 * whole point is that it can only speak in digits and wheels. So the name is
 * spelled out one letter at a time, on the same kind of scroller the city's
 * lottery used to take their coins.
 *
 * It starts as a SINGLE column. The player adds as many as they need (up to 20).
 * Nobody is forced to pad their name out to a fixed width.
 *
 * Shown only for the endings that ask (compliant and explorer) — the resistance
 * ending never earns the player's name.
 */

// A blank, so a column can be left empty without forcing a letter.
private const val LETTERS = " ABCDEFGHIJKLMNOPQRSTUVWXYZ'-"
private const val MAX_COLUMNS = 20

@Composable
fun EndingNameEntry(
    prompt: String = "What is your actual name?",
    onConfirm: (String) -> Unit,
) {
    // One column to begin with.
    val letters = remember { mutableStateListOf(1) }   // index into LETTERS; 1 = 'A'

    val name = letters
        .joinToString("") { LETTERS[it.coerceIn(0, LETTERS.lastIndex)].toString() }
        .trim()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroCream),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = prompt,
                color = AccentOrange,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalculatorDisplayFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 28.dp)
            )

            // The wheels. Scrolls sideways once the name outgrows the screen.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(letters.size) { i ->
                    LetterWheel(
                        value = letters[i],
                        onChange = { letters[i] = it },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EndingKey(
                    label = "+ letter",
                    enabled = letters.size < MAX_COLUMNS,
                ) { if (letters.size < MAX_COLUMNS) letters.add(1) }

                EndingKey(
                    label = "- letter",
                    enabled = letters.size > 1,
                ) { if (letters.size > 1) letters.removeAt(letters.lastIndex) }
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = if (name.isEmpty()) " " else name,
                color = AccentOrange,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = CalculatorDisplayFont,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            EndingKey(
                label = "CONFIRM",
                enabled = name.isNotEmpty(),
                wide = true,
            ) { if (name.isNotEmpty()) onConfirm(name) }
        }
    }
}

/**
 * One letter column. Deliberately the same physics as the lottery's NumberWheel —
 * drag, fling, settle — so it feels like a machine the player has met before.
 */
@Composable
private fun LetterWheel(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val itemH = 40.dp
    val density = LocalDensity.current
    val itemPx = with(density) { itemH.toPx() }
    var pos by remember { mutableStateOf(value.toFloat()) }
    val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    var fling by remember { mutableStateOf<Job?>(null) }

    val last = LETTERS.lastIndex
    fun clampPos() { pos = pos.coerceIn(0f, last.toFloat()) }
    fun emit() { onChange(pos.roundToInt().coerceIn(0, last)) }

    Box(
        modifier
            .width(46.dp)
            .height(itemH * 3)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2D2D2D))
            .border(1.dp, AccentOrange.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { fling?.cancel(); tracker.resetTracking() },
                    onDragEnd = {
                        val vPx = tracker.calculateVelocity().y
                        var vUnits = -vPx / itemPx
                        fling = scope.launch {
                            var lastFrame = 0L
                            while (abs(vUnits) > 0.6f) {
                                val fr = withFrameNanos { it }
                                val dt = if (lastFrame == 0L) 0.016f
                                         else ((fr - lastFrame) / 1_000_000_000f)
                                lastFrame = fr
                                pos += vUnits * dt
                                clampPos()
                                if (pos <= 0f || pos >= last.toFloat()) break
                                vUnits *= exp(-5f * dt)
                                emit()
                            }
                            val target = pos.roundToInt().coerceIn(0, last).toFloat()
                            repeat(6) { pos += (target - pos) * 0.4f; emit(); withFrameNanos { } }
                            pos = target; emit()
                        }
                    },
                    onVerticalDrag = { change, dy ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        pos -= dy / itemPx
                        clampPos(); emit()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val center = pos.roundToInt()
        val frac = pos - center
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            for (d in -1..1) {
                val n = center + d
                if (n < 0 || n > last) continue
                val yOff = with(density) { ((d - frac) * itemPx).toDp() }
                Text(
                    text = LETTERS[n].toString(),
                    color = if (d == 0) AccentOrange else Color.White.copy(alpha = 0.3f),
                    fontSize = if (d == 0) 28.sp else 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = CalculatorDisplayFont,
                    modifier = Modifier.offset(y = yOff)
                )
            }
        }
    }
}

@Composable
private fun EndingKey(
    label: String,
    enabled: Boolean = true,
    wide: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .then(if (wide) Modifier.fillMaxWidth(0.7f) else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) AccentOrange else Color(0xFF6B6B6B))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color(0xFFCCCCCC),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CalculatorDisplayFont
        )
    }
}
