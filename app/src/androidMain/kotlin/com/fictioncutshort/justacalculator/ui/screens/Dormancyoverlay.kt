package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.logic.DormancyManager
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

// ── Dormancy Countdown ───────────────────────────────────────────────────────

// 5×7 pixel glyphs for the four digits and the colon. 'X' = lit cell. The
// patterns are intentionally chunky and orthogonal so the digits read as
// blocks of the same noise grid behind them.
private val PIXEL_DIGITS: Map<Char, List<String>> = mapOf(
    '0' to listOf(".XXX.", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."),
    '1' to listOf("..X..", ".XX..", "..X..", "..X..", "..X..", "..X..", ".XXX."),
    '2' to listOf(".XXX.", "X...X", "....X", "...X.", "..X..", ".X...", "XXXXX"),
    '3' to listOf("XXXX.", "....X", "....X", ".XXX.", "....X", "....X", "XXXX."),
    '4' to listOf("X...X", "X...X", "X...X", "XXXXX", "....X", "....X", "....X"),
    '5' to listOf("XXXXX", "X....", "X....", "XXXX.", "....X", "....X", "XXXX."),
    '6' to listOf(".XXXX", "X....", "X....", "XXXX.", "X...X", "X...X", ".XXX."),
    '7' to listOf("XXXXX", "....X", "...X.", "..X..", ".X...", "X....", "X...."),
    '8' to listOf(".XXX.", "X...X", "X...X", ".XXX.", "X...X", "X...X", ".XXX."),
    '9' to listOf(".XXX.", "X...X", "X...X", ".XXXX", "....X", "....X", "XXXX."),
    ':' to listOf(".", ".", "X", ".", "X", ".", ".")
)

/**
 * Large, blocky countdown over the static. Each character is drawn as a 5×7
 * grid of square cells (Canvas rects) at a fixed gray, so the digits read as
 * a slightly denser patch of the same noise rather than smoothly antialiased
 * text. Counts down to T+15:30 from rant end and then self-hides.
 */
@Composable
fun DormancyCountdown(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var remainingMs by remember { mutableLongStateOf(computeRemainingMs(context)) }

    LaunchedEffect(Unit) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = computeRemainingMs(context)
        }
    }

    if (remainingMs <= 0) return

    val totalSeconds = (remainingMs / 1000).toInt()
    val mm = "%02d".format(totalSeconds / 60)
    val ss = "%02d".format(totalSeconds % 60)
    val pixelColor = Color(200, 200, 200, (0.14f * 255).toInt())

    // Portrait: stack MM over SS, large cells, centred at the top.
    // Landscape: the right half of the screen is taken by the RAD grid and
    // keyboard (matching the calculator's landscape layout), so the timer
    // sits on the left side instead of overlapping the grid.
    if (isLandscape) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 24.dp, start = 24.dp)
            ) {
                PixelText(text = "$mm:$ss", cellSize = 12.dp, color = pixelColor)
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 20.dp per cell → each line ~220dp wide × 140dp tall, stacked
                // with a small gap. Larger than the single-line layout and
                // visually dominant in the upper screen area without crowding
                // the RAD grid that grows above the keyboard.
                PixelText(text = mm, cellSize = 20.dp, color = pixelColor)
                Spacer(modifier = Modifier.height(20.dp))
                PixelText(text = ss, cellSize = 20.dp, color = pixelColor)
            }
        }
    }
}

@Composable
private fun PixelText(text: String, cellSize: Dp, color: Color) {
    val density = LocalDensity.current
    val cellSizePx = with(density) { cellSize.toPx() }

    // Total pixel width: sum of glyph widths + 1 cell of inter-character gap.
    val charSpacingCells = 1
    val totalCols = text.fold(0) { acc, ch ->
        val w = PIXEL_DIGITS[ch]?.firstOrNull()?.length ?: 0
        acc + w
    } + (text.length - 1).coerceAtLeast(0) * charSpacingCells
    val totalRows = 7

    Canvas(
        modifier = Modifier.size(
            width = cellSize * totalCols,
            height = cellSize * totalRows
        )
    ) {
        var xCell = 0
        for (ch in text) {
            val pattern = PIXEL_DIGITS[ch] ?: continue
            for ((row, rowStr) in pattern.withIndex()) {
                for ((col, pixel) in rowStr.withIndex()) {
                    if (pixel == 'X') {
                        drawRect(
                            color = color,
                            topLeft = Offset((xCell + col) * cellSizePx, row * cellSizePx),
                            size = Size(cellSizePx, cellSizePx)
                        )
                    }
                }
            }
            xCell += pattern.firstOrNull()?.length ?: 0
            xCell += charSpacingCells
        }
    }
}

private fun computeRemainingMs(context: android.content.Context): Long {
    val rantEnd = DormancyManager.getRantEndTime(context)
    if (rantEnd < 0) return 0L
    val elapsed = System.currentTimeMillis() - rantEnd
    return (DormancyManager.DORMANCY_COMPLETE_MS - elapsed).coerceAtLeast(0L)
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
                    // Pressed cells render as empty space — the user wanted
                    // them to disappear (the same gesture they'll use to clear
                    // the grid at the end), not just darken in place.
                    if (index < visible && !pressed.contains(index)) {
                        DormancyRadCell(
                            isPressed = false,
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
    val context = LocalContext.current
    // Local-only: tracks which keyboard cells the user has clicked away
    // during dormancy. Not persisted — on app restart the keyboard re-fills
    // and the user can clear it again. The "additional" grid above keeps its
    // own persisted state in dormancyPressedButtons.
    var hiddenKeyboardSymbols by remember { mutableStateOf(setOf<String>()) }

    // Shared press handlers. The RAD-grid one keeps at least one unpressed cell
    // visible during the countdown (spawning the next one if needed). The
    // keyboard one mirrors the same "always one RAD on screen" rule across the
    // combined grid + keyboard population.
    val onGridPress: (Int) -> Unit = { index ->
        val countdownActive = computeRemainingMs(context) > 0
        val currentPressed = state.value.dormancyPressedButtons
        val visible = state.value.dormancyRadVisible
        val wouldClearGrid = visible - currentPressed.size <= 1
        val atRadCap = visible >= DormancyManager.TOTAL_RAD_BUTTONS
        val blocked = countdownActive && wouldClearGrid && atRadCap
        if (!blocked) {
            val newVisible = if (countdownActive && wouldClearGrid && !atRadCap) {
                visible + 1
            } else visible
            val newPressed = currentPressed + index
            CalculatorActions.persistDormancyPressedButtons(newPressed)
            state.value = state.value.copy(
                dormancyPressedButtons = newPressed,
                dormancyRadVisible = newVisible,
                vibrationIntensity = newPressed.size
            )
        }
    }
    val onKeyboardPress: (String) -> Unit = { symbol ->
        if (symbol !in hiddenKeyboardSymbols) {
            val countdownActive = computeRemainingMs(context) > 0
            val gridLeft = state.value.dormancyRadVisible -
                state.value.dormancyPressedButtons.size
            val keyboardLeft = 20 - hiddenKeyboardSymbols.size
            val totalLeftAfter = gridLeft + keyboardLeft - 1
            if (!countdownActive || totalLeftAfter >= 1) {
                hiddenKeyboardSymbols = hiddenKeyboardSymbols + symbol
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        DormancyStaticBackground(modifier = Modifier.fillMaxSize())

        // Subtle countdown layered between the static and the keyboard — only
        // visible until the full RAD grid is reachable. Acts as a notification
        // fallback for users who declined POST_NOTIFICATIONS.
        DormancyCountdown(modifier = Modifier.fillMaxSize())

        if (dimensions.isLandscape) {
            // Landscape: mirror the calculator's landscape split. Left panel
            // hosts the timer, Part-1 card, AND the new RAD-button grid (the
            // analogue of stacking it above the keyboard in portrait). Right
            // panel hosts only the original (RAD-styled) keyboard at its
            // normal landscape size, so no rows get squished.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .padding(horizontal = dimensions.contentPadding)
                    .padding(top = 8.dp, bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(dimensions.leftPanelWeight)
                        .fillMaxHeight()
                        .padding(end = dimensions.contentPadding / 2),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // Same Column/width/vertical-centre as the keyboard on the
                    // right so the dormancy RAD grid reads as a level
                    // extension of the original keyboard — identical cell
                    // dimensions and identical top/bottom edges.
                    Column(
                        modifier = Modifier.width(dimensions.keyboardWidth),
                        verticalArrangement = Arrangement.Center
                    ) {
                        DormancyRadGrid(
                            visible = current.dormancyRadVisible,
                            pressed = current.dormancyPressedButtons,
                            dimensions = dimensions,
                            onPress = onGridPress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(dimensions.rightPanelWeight)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier.width(dimensions.keyboardWidth),
                        verticalArrangement = Arrangement.Center
                    ) {
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
                            hiddenSymbols = hiddenKeyboardSymbols,
                            onButtonClick = onKeyboardPress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            // Portrait: original stacked layout — RAD grid + keyboard at the
            // bottom, static and timer fill the screen behind everything.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .padding(horizontal = dimensions.contentPadding),
                verticalArrangement = Arrangement.Bottom
            ) {
                DormancyRadGrid(
                    visible = current.dormancyRadVisible,
                    pressed = current.dormancyPressedButtons,
                    dimensions = dimensions,
                    onPress = onGridPress,
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
                    hiddenSymbols = hiddenKeyboardSymbols,
                    onButtonClick = onKeyboardPress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // "Part 1 complete" panel — rendered last in the Box so it sits on
        // top of the static, countdown, RAD grid, and the keyboard. Appears
        // alongside the static the moment the rant ends and persists for the
        // rest of dormancy. The dormancy experience (countdown, RAD grid,
        // notifications) keeps running underneath; this card is the
        // foreground information layer.
        if (current.showEndOfPart1) {
            val panelIsLandscape = dimensions.isLandscape
            if (panelIsLandscape) {
                // Landscape: the RAD grid + keyboard live on the right half
                // (matching the calculator's landscape split), so the panel
                // sits in the left half — below the small single-line timer.
                val leftFraction =
                    dimensions.leftPanelWeight /
                        (dimensions.leftPanelWeight + dimensions.rightPanelWeight)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp, start = 16.dp, bottom = 16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(modifier = Modifier.fillMaxWidth(leftFraction)) {
                        Part1CompletePanel()
                    }
                }
            } else {
                // Portrait: sit below the stacked countdown, above the RAD
                // grid. Top-pad scales with screen height; clamped so it
                // doesn't collide with the keyboard on small phones.
                val panelTopPadding =
                    (dimensions.screenHeight.value * 0.42f).dp.coerceIn(260.dp, 440.dp)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = panelTopPadding, start = 20.dp, end = 20.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Part1CompletePanel()
                }
            }
        }
    }
}
