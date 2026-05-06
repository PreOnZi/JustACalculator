package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.ui.components.CalculatorButtonGrid
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.ResponsiveDimensions
import com.fictioncutshort.justacalculator.util.vibrate
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Dormancyoverlay.kt
 *
 * Public composables used by the in-app dormancy phase.
 *
 * After the rant ends the calculator does NOT visually reset — the keyboard
 * keeps its RAD styling. About 10 seconds later the [DormancyStaticBackground]
 * fades into view as a TV-static layer that sits *behind* the calculator UI
 * (so the RAD-styled keyboard stays readable on top of it).
 *
 * Once the notification schedule starts firing (T+6:00, every 30s thereafter)
 * the LCD area is replaced with [DormancyRadGrid] — a 5×4 grid that mirrors
 * the keyboard layout and progressively fills with RAD buttons, one per
 * notification. Each button must be tapped to advance.
 */

// ── TV Static Background ──────────────────────────────────────────────────────

/**
 * Full-area animated TV-static fill. Designed to be drawn as the *background*
 * of the calculator content, behind the keyboard and message areas.
 */
@Composable
fun DormancyStaticBackground(modifier: Modifier = Modifier) {
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(80) // ~12fps noise refresh
            tick++
        }
    }

    Box(
        modifier = modifier.background(Color(0xFF1A1A1A))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cellSize = 6.dp
            val cols = (maxWidth / cellSize).toInt().coerceAtLeast(1)
            val rows = (maxHeight / cellSize).toInt().coerceAtLeast(1)

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
}

// ── RAD Button Grid ──────────────────────────────────────────────────────────

/**
 * 5×4 grid of RAD buttons matching the calculator keyboard's row/column
 * layout. Cells whose index is below [visible] render an interactive RAD
 * button; cells above remain blank placeholders so the grid grows in place
 * over time without reflowing.
 *
 * Visually identical to the keyboard's RAD-mode buttons (dark red, white
 * "RAD" text, 8.dp rounded corners) — once tapped, the cell darkens and
 * the text desaturates so the user can see what's been pressed.
 *
 * @param visible How many cells (1–20) have been revealed so far.
 * @param pressed Set of cell indices the user has already tapped.
 * @param dimensions Used to match the keyboard's button height/spacing.
 * @param onPress Invoked with the cell index when a fresh button is tapped.
 */
@Composable
fun DormancyRadGrid(
    visible: Int,
    pressed: Set<Int>,
    dimensions: ResponsiveDimensions,
    onPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimensions.buttonSpacing)
    ) {
        for (row in 0 until 5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.buttonRowHeight),
                horizontalArrangement = Arrangement.spacedBy(dimensions.buttonSpacing)
            ) {
                for (col in 0 until 4) {
                    val index = row * 4 + col
                    if (index < visible) {
                        DormancyRadCell(
                            isPressed = pressed.contains(index),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onPress(index) }
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DormancyRadCell(
    isPressed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val backgroundColor = if (isPressed) Color(0xFF3A0000) else Color(0xFF8B0000)
    val textColor = if (isPressed) Color(0xFF888888) else Color.White

    Button(
        onClick = {
            if (!isPressed) {
                vibrate(context, 10, 30)
                onClick()
            }
        },
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = Color.Black.copy(alpha = 0.3f)
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp
        )
    ) {
        Text(
            text = "RAD",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CalculatorDisplayFont
        )
    }
}

// ── Full-screen Dormancy ──────────────────────────────────────────────────────

/**
 * Edge-to-edge dormancy layout: TV-static fills the entire screen behind
 * everything, with only the RAD-styled keyboard at the bottom and the
 * progressively-revealed [DormancyRadGrid] floating just above it. The
 * top bezel, ad banner, message text, and mute button are deliberately
 * absent — once dormancy starts, the calculator presents itself as just
 * static and RAD.
 *
 * Rendered in place of the standard calculator layout (not on top), so no
 * letterbox borders show through and nothing else can intrude.
 */
@Composable
fun DormancyScreen(
    state: MutableState<CalculatorState>,
    current: CalculatorState,
    buttonLayout: List<List<String>>,
    dimensions: ResponsiveDimensions,
    currentShakeIntensity: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        DormancyStaticBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
                .padding(horizontal = dimensions.contentPadding),
            verticalArrangement = Arrangement.Bottom
        ) {
            // The dormancy RAD grid sits just above the keyboard, matching
            // the keyboard's row/column structure so the two grids read as
            // a continuous block of red on a noisy background.
            DormancyRadGrid(
                visible = current.dormancyRadVisible,
                pressed = current.dormancyPressedButtons,
                dimensions = dimensions,
                onPress = { index ->
                    val newPressed = state.value.dormancyPressedButtons + index
                    CalculatorActions.persistDormancyPressedButtons(newPressed)
                    state.value = state.value.copy(
                        dormancyPressedButtons = newPressed,
                        vibrationIntensity = newPressed.size
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dimensions.buttonSpacing)
            )

            CalculatorButtonGrid(
                buttonLayout = buttonLayout,
                dimensions = dimensions,
                shakeIntensity = currentShakeIntensity,
                invertedColors = current.invertedColors,
                minusButtonDamaged = current.minusButtonDamaged,
                minusButtonBroken = current.minusButtonBroken,
                flickeringButton = current.flickeringButton,
                darkButtons = current.darkButtons,
                allButtonsRad = current.allButtonsRad,
                radButtonsConverted = current.radButtonsConverted,
                rantMode = current.rantMode,
                onButtonClick = { /* keyboard is inert during dormancy */ },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
