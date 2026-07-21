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
            .navigationBarsPadding()
            .padding(horizontal = dimensions.contentPadding)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        // ---
        // LEFT PANEL - Messages, Camera/Browser, Display
        // ---
        Box(
            modifier = Modifier
                .weight(dimensions.leftPanelWeight)
                .fillMaxHeight()
                .padding(end = dimensions.contentPadding / 2)
        ) {
            // Render order matters — later children draw on top in Compose.
            // We want the LCD as the back layer (so camera/browser cover it
            // and only the bottom slice peeks out), then the overlays, and
            // the mute button last so it stays tappable on top of everything.

            // Back layer: green LCD always present. In landscape it sits
            // below the browser/camera overlays so it's partially visible
            // behind them; in the LCD-only state (no overlay) it shows
            // normally.
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
                        lifecycleOwner = lifecycleOwner,
                        useFrontCamera = current.cameraUseFrontCamera
                    )
                }
            }

            // Browser overlay (when active). Same composable as portrait;
            // the internal floating LCD is suppressed since the green LCD
            // beneath now plays that role.
            if (current.showBrowser) {
                BrowserViewWithFloatingDisplay(
                    displayText = displayText,
                    browserSearchText = current.browserSearchText,
                    browserShowWikipedia = current.browserShowWikipedia,
                    browserShowError = current.browserShowError,
                    dimensions = dimensions,
                    showFloatingDisplay = false,
                    // Landscape has the message beside the browser, not above,
                    // so we don't need the portrait top-padding gap.
                    topPadding = 8.dp,
                    // Anchored to the bottom with a gap so the message has
                    // the whole top region free (was centred with 85% height,
                    // which covered multi-line messages) and the green LCD
                    // peeks out below.
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.7f)
                )
            }

            // Mute button + spinner indicator - always present, button visible
            // from step 19. Rendered last so it stays on top of the camera/
            // browser overlays and remains tappable.
            MuteButtonWithSpinner(
                isMuted = current.isMuted,
                isAutoProgressing = current.showSpinner,
                showButton = current.conversationStep >= 19 || current.isMuted,
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
        }

        // ---
        // RIGHT PANEL - Calculator Keyboard
        // ---
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
                    radButtonsConverted = current.radButtonsConverted,
                    rantMode = current.rantMode,
                    onButtonClick = { symbol ->
                        // "RAD" is the post-story relabel of the clear key — same action.
                        CalculatorActions.handleInput(state, if (symbol == "RAD") "C" else symbol)
                    }
                )
            }
        }
    }
}