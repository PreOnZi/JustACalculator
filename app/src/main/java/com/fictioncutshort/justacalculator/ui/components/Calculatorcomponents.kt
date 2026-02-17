package com.fictioncutshort.justacalculator.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.util.*

/**
 * CalculatorComponents.kt
 *
 * Extracted reusable UI components for the calculator.
 * These components adapt to different screen sizes and orientations.
 */

// ═══════════════════════════════════════════════════════════════════════════
// TOP BAR COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Brown bezel bar that covers the status bar area.
 * Height automatically matches the device's status bar.
 */
@Composable
fun TopBezelBar(
    invertedColors: Boolean,
    modifier: Modifier = Modifier
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBarHeight)
            .background(if (invertedColors) BezelInverted else BezelBrown)
    )
}

/**
 * Ad banner that appears at certain story steps.
 * Handles different ad phases (normal, animated, post-chaos).
 */
@Composable
fun AdBanner(
    showBanner: Boolean,
    bannersDisabled: Boolean,
    adAnimationPhase: Int,
    postChaosAdPhase: Int,
    dimensions: ResponsiveDimensions,
    onAdClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldShow = (showBanner && !bannersDisabled) || adAnimationPhase > 0 || postChaosAdPhase > 0

    if (shouldShow) {
        val backgroundColor = when {
            postChaosAdPhase == 1 -> Color(0xFF9C27B0)  // Purple ad
            postChaosAdPhase == 2 -> Color(0xFF00BCD4)  // Cyan ad
            adAnimationPhase == 1 -> Color(0xFF4CAF50)  // Green ad
            adAnimationPhase == 2 -> Color(0xFFE91E63)  // Pink ad
            else -> BannerGray
        }

        val isClickable = adAnimationPhase > 0 || postChaosAdPhase > 0

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(dimensions.adBannerHeight)
                .background(backgroundColor)
                .then(if (isClickable) Modifier.clickable { onAdClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            when {
                postChaosAdPhase == 1 -> {
                    Text(
                        text = "✨ UNLOCK YOUR POTENTIAL TODAY! ✨",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                postChaosAdPhase == 2 -> {
                    Text(
                        text = "🚀 LIMITED TIME OFFER - ACT NOW! 🚀",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                adAnimationPhase == 1 -> {
                    Text(
                        text = "🎉 YOU WON! Click here! 🎉",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                adAnimationPhase == 2 -> {
                    Text(
                        text = "💰 EARN $500/DAY FROM HOME! 💰",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                // else: empty gray banner
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MESSAGE DISPLAY COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Displays the calculator's story messages with optional countdown and choices.
 */
@Composable
fun MessageDisplay(
    message: String,
    countdownTimer: Int,
    conversationStep: Int,
    awaitingChoice: Boolean,
    textColor: Color,
    dimensions: ResponsiveDimensions,
    modifier: Modifier = Modifier
) {
    if (message.isEmpty()) return

    val maxHeight = if (dimensions.isLandscape) {
        (dimensions.screenHeight.value * 0.6f).dp
    } else {
        200.dp
    }

    Box(
        modifier = modifier
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
    ) {
        Column {
            Text(
                text = message,
                fontSize = dimensions.messageFontSize.sp,
                lineHeight = (dimensions.messageFontSize + 4).sp,
                color = textColor,
                textAlign = TextAlign.Start,
                fontFamily = CalculatorDisplayFont
            )

            // Show countdown timer if active
            if (countdownTimer > 0) {
                Text(
                    text = "Time: $countdownTimer",
                    fontSize = (dimensions.messageFontSize - 6).sp,
                    color = if (countdownTimer <= 5) Color.Red else textColor,
                    fontFamily = CalculatorDisplayFont,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Show choice options for step 89
            if (conversationStep == 89 && awaitingChoice) {
                val choiceFontSize = (dimensions.messageFontSize - 8).sp
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text("1) Nothing", fontSize = choiceFontSize, color = textColor, fontFamily = CalculatorDisplayFont)
                    Text("2) I'll fight them!", fontSize = choiceFontSize, color = textColor, fontFamily = CalculatorDisplayFont)
                    Text("3) Go offline", fontSize = choiceFontSize, color = textColor, fontFamily = CalculatorDisplayFont)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// LCD DISPLAY COMPONENT
// ═══════════════════════════════════════════════════════════════════════════

/**
 * The calculator's LCD display showing numbers and operation history.
 */
@Composable
fun CalculatorLcdDisplay(
    displayText: String,
    operationHistory: String,
    isReadyForNewOperation: Boolean,
    invertedColors: Boolean,
    dimensions: ResponsiveDimensions,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensions.lcdDisplayHeight)
                .background(
                    if (invertedColors) Color(0xFF0A0A0A) else LcdBackground,
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Operation history in top right (smaller)
            if (operationHistory.isNotEmpty() && isReadyForNewOperation) {
                Text(
                    text = operationHistory,
                    fontSize = 16.sp,
                    color = if (invertedColors) RetroDisplayGreen.copy(alpha = 0.6f)
                    else DarkText.copy(alpha = 0.5f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    fontFamily = CalculatorDisplayFont,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp)
                )
            }

            // Shadow/ghost digits effect (like old LCDs)
            Text(
                text = "8888888888888",
                fontSize = dimensions.displayFontSizeBase.sp,
                color = Color(0xFF000000).copy(alpha = 0.06f),
                textAlign = TextAlign.End,
                maxLines = 1,
                fontFamily = CalculatorDisplayFont,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomEnd)
            )

            // Actual display - auto-sizing based on content
            val displayFontSize = calculateDisplayFontSize(displayText.length, dimensions.displayFontSizeBase)
            Text(
                text = displayText,
                fontSize = displayFontSize.sp,
                color = if (invertedColors) RetroDisplayGreen else DarkText,
                textAlign = TextAlign.End,
                maxLines = 1,
                fontFamily = CalculatorDisplayFont,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CALCULATOR BUTTON GRID
// ═══════════════════════════════════════════════════════════════════════════

/**
 * The calculator's button grid (5 rows x 4 columns).
 * Adapts sizing based on orientation and screen size.
 */
@Composable
fun CalculatorButtonGrid(
    buttonLayout: List<List<String>>,
    dimensions: ResponsiveDimensions,
    shakeIntensity: Float,
    invertedColors: Boolean,
    minusButtonDamaged: Boolean,
    minusButtonBroken: Boolean,
    flickeringButton: String,
    darkButtons: List<String>,
    allButtonsRad: Boolean,
    rantMode: Boolean,
    onButtonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(bottom = if (dimensions.isLandscape) 8.dp else dimensions.contentPadding),
        verticalArrangement = Arrangement.spacedBy(dimensions.buttonSpacing)
    ) {
        buttonLayout.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.buttonRowHeight),
                horizontalArrangement = Arrangement.spacedBy(dimensions.buttonSpacing)
            ) {
                row.forEach { symbol ->
                    CalculatorButton(
                        symbol = symbol,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shakeIntensity = shakeIntensity,
                        invertedColors = invertedColors,
                        isDamaged = minusButtonDamaged && symbol == "-",
                        isBroken = minusButtonBroken && symbol == "-",
                        isFlickering = flickeringButton == symbol,
                        isDark = symbol in darkButtons,
                        showAsRad = allButtonsRad,
                        onClick = {
                            if (!rantMode) {
                                onButtonClick(symbol)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RAD BUTTON (appears at certain story points)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun RadButton(
    visible: Boolean,
    allButtonsRad: Boolean,
    dimensions: ResponsiveDimensions,
    modifier: Modifier = Modifier
) {
    if (visible && !allButtonsRad) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = dimensions.contentPadding)
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Button(
                onClick = { /* Does nothing */ },
                modifier = Modifier
                    .width(100.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B0000),
                    contentColor = Color.White
                ),
                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "RAD",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}