package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.RetroCream

@Composable
fun PausedCalculatorOverlay(
    display: String,
    expression: String,
    justCalculated: Boolean,
    darkButtons: List<String>,
    minusButtonDamaged: Boolean,
    isTablet: Boolean,
    maxContentWidth: Dp,
    buttonLayout: List<List<String>>,
    onMuteClick: () -> Unit,
    onButtonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RetroCream),
        contentAlignment = if (isTablet) Alignment.TopCenter else Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .then(if (isTablet) Modifier.widthIn(max = maxContentWidth) else Modifier.fillMaxWidth())
                .fillMaxHeight()
        ) {
            // Top bezel
            val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp + statusBarPadding.calculateTopPadding())
                    .background(Color(0xFF4A3728))
                    .padding(top = statusBarPadding.calculateTopPadding())
            )

            // Main calculator content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 15.dp)
                    .padding(top = 8.dp)
            ) {
                // Mute button (to resume)
                MuteButtonWithSpinner(
                    isMuted = true,
                    isAutoProgressing = false,
                    onClick = onMuteClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp)
                )

                // "Story paused" indicator
                Text(
                    text = "Story paused",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontFamily = CalculatorDisplayFont,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 12.dp)
                )

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Calculator display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(Color(0xFFCCD5AE), RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            // Show expression history after calculation
                            if (justCalculated && expression.isNotEmpty() && expression != display) {
                                Text(
                                    text = "$expression=",
                                    fontSize = 16.sp,
                                    color = Color(0xFF2D2D2D).copy(alpha = 0.5f),
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    fontFamily = CalculatorDisplayFont,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 4.dp)
                                )
                            }

                            // Shadow digits
                            Text(
                                text = "8888888888888",
                                fontSize = 58.sp,
                                color = Color(0xFF000000).copy(alpha = 0.06f),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                fontFamily = CalculatorDisplayFont,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomEnd)
                            )

                            // Actual display
                            val displayFontSize = when {
                                display.length > 15 -> 32.sp
                                display.length > 12 -> 40.sp
                                display.length > 10 -> 48.sp
                                display.length > 8 -> 54.sp
                                else -> 62.sp
                            }
                            Text(
                                text = display,
                                fontSize = displayFontSize,
                                color = Color(0xFF2D2D2D),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                fontFamily = CalculatorDisplayFont,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomEnd)
                            )
                        }
                    }

                    // Calculator buttons - fully functional
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 15.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        buttonLayout.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { symbol ->
                                    CalculatorButton(
                                        symbol = symbol,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        shakeIntensity = 0f,
                                        invertedColors = false,
                                        isDamaged = minusButtonDamaged && symbol == "-",
                                        isBroken = false,
                                        isFlickering = false,
                                        isDark = symbol in darkButtons,
                                        showAsRad = false,
                                        onClick = { onButtonClick(symbol) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}