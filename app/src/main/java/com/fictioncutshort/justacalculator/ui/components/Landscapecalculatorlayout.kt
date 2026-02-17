package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.util.*

/**
 * LandscapeCalculatorLayout.kt
 *
 * Landscape-specific layout for the calculator.
 * Left side: Messages, camera/browser, display
 * Right side: Calculator keyboard
 */

@Composable
fun LandscapeCalculatorContent(
    state: MutableState<CalculatorState>,
    current: CalculatorState,
    displayText: String,
    buttonLayout: List<List<String>>,
    dimensions: ResponsiveDimensions,
    textColor: Color,
    backgroundColor: Color,
    showAdBanner: Boolean,
    currentShakeIntensity: Float,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = dimensions.contentPadding)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // LEFT PANEL - Messages, Camera/Browser, Display
        // ═══════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .weight(dimensions.leftPanelWeight)
                .fillMaxHeight()
                .padding(end = dimensions.contentPadding / 2)
        ) {
            // Mute button - top right of left panel
            MuteButtonWithSpinner(
                isMuted = current.isMuted,
                isAutoProgressing = current.showSpinner,
                onClick = {
                    val result = CalculatorActions.handleMuteButtonClick()
                    when (result) {
                        1 -> CalculatorActions.showDebugMenu(state)
                        2 -> CalculatorActions.resetGame(state)
                        else -> CalculatorActions.toggleConversation(state)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp)
            )

            // Message display - top left
            if (current.message.isNotEmpty() && !current.isMuted) {
                MessageDisplay(
                    message = current.message,
                    countdownTimer = current.countdownTimer,
                    conversationStep = current.conversationStep,
                    awaitingChoice = current.awaitingChoice,
                    textColor = textColor,
                    dimensions = dimensions,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, end = 50.dp)
                        .fillMaxWidth(0.9f)
                )
            }

            // Camera preview (when active)
            if (current.cameraActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.8f)
                        .fillMaxHeight(0.5f)
                        .padding(vertical = 8.dp)
                ) {
                    CameraPreview(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        lifecycleOwner = lifecycleOwner
                    )
                }
            }

            // Browser overlay (when active)
            if (current.showBrowser) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.6f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                ) {
                    // Browser content would go here
                    // This is simplified - you may want to include the full browser UI
                }
            }

            // LCD Display - bottom of left panel (when not showing camera/browser)
            if (!current.cameraActive && !current.showBrowser) {
                CalculatorLcdDisplay(
                    displayText = displayText,
                    operationHistory = current.operationHistory,
                    isReadyForNewOperation = current.isReadyForNewOperation,
                    invertedColors = current.invertedColors,
                    dimensions = dimensions,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, end = 8.dp)
                )
            } else {
                // Floating display over camera/browser
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            Color.White.copy(alpha = 0.85f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val displayFontSize = calculateDisplayFontSize(displayText.length, 40)
                    Text(
                        text = displayText,
                        fontSize = displayFontSize.sp,
                        color = Color(0xFF0A0A0A),
                        maxLines = 1,
                        fontFamily = CalculatorDisplayFont
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // RIGHT PANEL - Calculator Keyboard
        // ═══════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .weight(dimensions.rightPanelWeight)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.width(dimensions.keyboardWidth),
                verticalArrangement = Arrangement.Center
            ) {
                // RAD button (if visible)
                RadButton(
                    visible = current.radButtonVisible,
                    allButtonsRad = current.allButtonsRad,
                    dimensions = dimensions
                )

                // Calculator button grid
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
                    rantMode = current.rantMode,
                    onButtonClick = { symbol ->
                        CalculatorActions.handleInput(state, symbol)
                    }
                )
            }
        }
    }
}