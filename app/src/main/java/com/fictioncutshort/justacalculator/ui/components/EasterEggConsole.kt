package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.logic.EasterEggTheme

/**
 * EasterEggConsole.kt
 *
 * The hidden colour console reached by typing 58008++ (number-button colour)
 * or 707++ (background colour) on the calculator. Styled like the story
 * ConsoleWindow but with its own palette menu.
 *
 * Navigation:
 * - Enter a number + (++) to apply that swatch
 * - 0 = reset to original
 * - 99 (++) = close without changing
 *
 * Applying a swatch closes the console; the calculator then quips and restores
 * whatever line it was on (handled in CalculatorActions).
 *
 * @param consoleType 1 = number-button colour, 2 = background colour
 * @param currentInput The number currently being typed (shown at the prompt)
 */
@Composable
fun EasterEggConsole(
    consoleType: Int,
    currentInput: String,
    modifier: Modifier = Modifier
) {
    val menuContent = buildEasterEggMenu(consoleType)

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val consoleTopPadding = if (isLandscape) {
        (screenHeight * 0.06f).coerceAtLeast(16.dp)
    } else {
        (screenHeight * 0.18f).coerceAtLeast(100.dp)
    }
    val consoleHeight = if (isLandscape) {
        (screenHeight * 0.55f).coerceIn(140.dp, 240.dp)
    } else {
        (screenHeight * 0.40f).coerceIn(180.dp, 320.dp)
    }
    val consoleFillFraction = if (isLandscape) 0.5f else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = consoleTopPadding + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                start = 12.dp,
                end = 12.dp
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(consoleFillFraction)
                .height(consoleHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .padding(2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A1A0A))
                    .padding(12.dp)
            ) {
                Text(
                    text = menuContent,
                    color = Color(0xFF00FF00),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF001500))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "> ",
                        color = Color(0xFF00FF00),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (currentInput == "0") "_" else "${currentInput}_",
                        color = Color(0xFF00FF00),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun buildEasterEggMenu(consoleType: Int): String {
    val isButtons = consoleType == 1
    val title = if (isButtons) "NUMBER BUTTON COLOUR" else "BACKGROUND COLOUR"
    val presets = if (isButtons) EasterEggTheme.NUMBER_PRESETS else EasterEggTheme.BACKGROUND_PRESETS
    val selected = if (isButtons) EasterEggTheme.numberColorIndex else EasterEggTheme.backgroundIndex

    val lines = StringBuilder()
    lines.append("═══════════════════════════════════\n")
    lines.append("  $title\n")
    lines.append("═══════════════════════════════════\n\n")
    // Index 0 is the "Original" reset entry; list it last as "0. ...".
    for (i in 1..presets.lastIndex) {
        val marker = if (i == selected) "*" else " "
        lines.append(" $marker $i. ${presets[i].name}\n")
    }
    val resetMarker = if (selected == 0) "*" else " "
    lines.append(" $resetMarker 0. Reset to original\n\n")
    lines.append(" Enter number + ++ to apply\n")
    lines.append(" 99. Close\n")
    lines.append("═══════════════════════════════════")
    return lines.toString()
}
