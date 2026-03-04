package com.fictioncutshort.justacalculator.ui.components

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.util.*

/**
 * PortraitCalculatorLayout.kt
 *
 * Portrait-specific layout for the calculator.
 * Vertical stack: Messages at top, camera/browser/display in middle, keyboard at bottom.
 */

@Composable
fun PortraitCalculatorContent(
    state: MutableState<CalculatorState>,
    current: CalculatorState,
    displayText: String,
    buttonLayout: List<List<String>>,
    dimensions: ResponsiveDimensions,
    textColor: Color,
    currentShakeIntensity: Float,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = dimensions.contentPadding)
    ) {
        // Mute button + spinner indicator - always present, button visible from step 19
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
            )
        }

        // Main content column (display + buttons)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Calculator number display OR Camera OR Browser
            when {
                current.cameraActive -> {
                    CameraViewWithFloatingDisplay(
                        displayText = displayText,
                        dimensions = dimensions,
                        lifecycleOwner = lifecycleOwner,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                current.showBrowser -> {
                    BrowserViewWithFloatingDisplay(
                        displayText = displayText,
                        browserSearchText = current.browserSearchText,
                        browserShowWikipedia = current.browserShowWikipedia,
                        browserShowError = current.browserShowError,
                        dimensions = dimensions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                else -> {
                    // Normal LCD display
                    CalculatorLcdDisplay(
                        displayText = displayText,
                        operationHistory = current.operationHistory,
                        isReadyForNewOperation = current.isReadyForNewOperation,
                        invertedColors = current.invertedColors,
                        dimensions = dimensions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
                    )
                }
            }

            // RAD button (appears at certain story points)
            RadButton(
                visible = current.radButtonVisible,
                allButtonsRad = current.allButtonsRad,
                dimensions = dimensions
            )

            // Calculator buttons
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

// ---
// CAMERA VIEW COMPONENT
// ---

@Composable
private fun CameraViewWithFloatingDisplay(
    displayText: String,
    dimensions: ResponsiveDimensions,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier
) {
    // Calculate top padding based on screen height to leave room for messages
    val topPadding = (dimensions.screenHeight.value * 0.22f).dp.coerceIn(120.dp, 200.dp)

    Box(
        modifier = modifier
            .padding(top = topPadding, bottom = 8.dp)
    ) {
        CameraPreview(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
            lifecycleOwner = lifecycleOwner
        )

        // Floating calculator display over camera
        FloatingDisplay(
            displayText = displayText,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

// ---
// BROWSER VIEW COMPONENT
// ---

@Composable
private fun BrowserViewWithFloatingDisplay(
    displayText: String,
    browserSearchText: String,
    browserShowWikipedia: Boolean,
    browserShowError: Boolean,
    dimensions: ResponsiveDimensions,
    modifier: Modifier = Modifier
) {
    // Calculate top padding - less than camera to make browser taller
    val topPadding = (dimensions.screenHeight.value * 0.12f).dp.coerceIn(80.dp, 120.dp)

    Box(
        modifier = modifier
            .padding(top = topPadding, bottom = 8.dp)
    ) {
        // Browser container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // URL/Search bar with animated text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(
                            Color(0xFFF0F0F0),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = browserSearchText.ifEmpty { "Search..." },
                        fontSize = if (browserShowWikipedia) 12.sp else 16.sp,
                        fontFamily = if (browserSearchText.isNotEmpty()) CalculatorDisplayFont else null,
                        color = if (browserSearchText.isEmpty()) Color.Gray else Color.Black,
                        maxLines = 1
                    )
                }

                // Content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        browserShowWikipedia -> {
                            WikipediaContent()
                        }
                        browserShowError -> {
                            BrowserErrorContent()
                        }
                        else -> {
                            // Google logo
                            Text(
                                text = "Google",
                                fontSize = 48.sp,
                                fontFamily = CalculatorDisplayFont,
                                color = Color(0xFF4285F4)
                            )
                        }
                    }
                }
            }
        }

        // Floating calculator display over browser
        FloatingDisplay(
            displayText = displayText,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun WikipediaContent() {
    var webViewFailed by remember { mutableStateOf(false) }

    if (!webViewFailed) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    @Suppress("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true

                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                webViewFailed = true
                            }
                        }

                        @Suppress("DEPRECATION")
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            @Suppress("DEPRECATION")
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            webViewFailed = true
                        }
                    }

                    loadUrl("https://en.wikipedia.org/wiki/Calculator")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Fallback: Fake Wikipedia page
        FakeWikipediaContent()
    }
}

@Composable
private fun BrowserErrorContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "âš ",
            fontSize = 48.sp,
            color = Color.Gray
        )
        Text(
            text = "No internet connection",
            fontSize = 20.sp,
            fontFamily = CalculatorDisplayFont,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ---
// FLOATING DISPLAY (used over camera and browser)
// ---

@Composable
private fun FloatingDisplay(
    displayText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Color.White.copy(alpha = 0.85f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = displayText,
            fontSize = 48.sp,
            color = Color(0xFF0A0A0A),
            textAlign = TextAlign.End,
            maxLines = 1,
            fontFamily = CalculatorDisplayFont
        )
    }
}