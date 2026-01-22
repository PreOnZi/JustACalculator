package com.fictioncutshort.justacalculator




import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fictioncutshort.justacalculator.data.CHAPTERS
import com.fictioncutshort.justacalculator.logic.AutoProgressEffects
import com.fictioncutshort.justacalculator.logic.BrowserEffects
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.logic.EffectsController
import com.fictioncutshort.justacalculator.ui.components.CalculatorButton
import com.fictioncutshort.justacalculator.ui.components.CameraPreview
import com.fictioncutshort.justacalculator.ui.components.ConsoleWindow
import com.fictioncutshort.justacalculator.ui.components.DonationLandingPage
import com.fictioncutshort.justacalculator.ui.components.FakeWikipediaContent
import com.fictioncutshort.justacalculator.ui.effects.KeyboardChaos3DView
import com.fictioncutshort.justacalculator.ui.components.MuteButtonWithSpinner
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.RetroCream
import com.fictioncutshort.justacalculator.util.RetroDisplayGreen
import com.fictioncutshort.justacalculator.util.WordGameScreen
import com.fictioncutshort.justacalculator.util.createSecretFile
import com.fictioncutshort.justacalculator.util.scheduleNotification
import com.fictioncutshort.justacalculator.util.vibrate
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import com.fictioncutshort.justacalculator.ui.components.ScrambleGameOverlay
import com.fictioncutshort.justacalculator.logic.ScrambleGameController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.fictioncutshort.justacalculator.ui.components.PausedCalculatorOverlay


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CalculatorActions.init(applicationContext)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            MaterialTheme {
                CalculatorScreen()
            }
        }
    }
}




@Composable
fun CalculatorScreen() {
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val initial = remember { CalculatorActions.loadInitialState() }
    val state = remember { mutableStateOf(initial) }
    val current = state.value
    var showTermsScreen by remember { mutableStateOf(!CalculatorActions.loadTermsAcceptedPublic()) }
    var showTermsPopup by remember { mutableStateOf(false) }

    // Camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Notification permission state
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    // Permission launchers - MUST be declared before LaunchedEffects that use them
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            CalculatorActions.startCamera(state)
        } else {
            state.value = state.value.copy(conversationStep = 21)
            CalculatorActions.toggleConversation(state)
            CalculatorActions.toggleConversation(state)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        scheduleNotification(context, 5000)
        state.value = state.value.copy(conversationStep = 101, needsRestart = true)
        CalculatorActions.persistNeedsRestart(true)
        CalculatorActions.persistConversationStep(101)
    }

    // ========== SCREEN TIME ==========
    LaunchedEffect(Unit) {
        EffectsController.trackScreenTime(state)
    }
    DisposableEffect(Unit) {
        onDispose { CalculatorActions.persistTotalScreenTime(state.value.totalScreenTimeMs) }
    }

    // ========== TYPING ANIMATION ==========
    LaunchedEffect(current.fullMessage, current.isTyping, current.isLaggyTyping, current.isSuperFastTyping, current.showDonationPage) {
        EffectsController.runTypingAnimation(state, context)
    }

    // ========== PENDING AUTO MESSAGE ==========
    LaunchedEffect(current.isTyping, current.pendingAutoMessage) {
        EffectsController.handlePendingAutoMessage(state)
    }

    // ========== WORD GAME ==========
    LaunchedEffect(current.isTyping, current.wordGameActive, current.conversationStep) {
        EffectsController.resumeWordGameAfterTyping(state)
    }
    LaunchedEffect(current.formedWords, current.wordGamePhase) {
        EffectsController.checkWordGameResponse(state)
    }
    LaunchedEffect(current.wordGameActive, current.wordGamePhase) {
        EffectsController.runWordGameLetterLoop(state)
    }
    LaunchedEffect(current.conversationStep, current.wordGameChaosMode) {
        EffectsController.runWordGameChaosMode(state)
    }
    LaunchedEffect(current.conversationStep, current.isTyping) {
        EffectsController.startWordGameAtStep1172(state)
    }
// ========== SCRAMBLE GAME ==========
    LaunchedEffect(current.scramblePhase, current.scrambleGameActive) {
        EffectsController.runScrambleGamePhases(state)
    }
    // ========== CAMERA ==========
    LaunchedEffect(current.cameraActive, current.cameraTimerStart) {
        EffectsController.monitorCameraTimeout(state)
    }
    LaunchedEffect(current.isTyping, current.cameraActive, current.cameraTimerStart) {
        EffectsController.closeCameraAfterMessage(state)
    }

    // ========== CAMERA/NOTIFICATION PERMISSION TRIGGERS ==========
    LaunchedEffect(current.conversationStep) {
        if (current.conversationStep == 191 && !current.cameraActive) {
            if (hasCameraPermission) {
                CalculatorActions.startCamera(state)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        if (current.conversationStep == 991) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                scheduleNotification(context, 30000)  // 30 seconds instead of 5
                state.value = state.value.copy(conversationStep = 101, needsRestart = true)
                CalculatorActions.persistNeedsRestart(true)
                CalculatorActions.persistConversationStep(101)
            }
        }
        if (current.conversationStep == 992) {
            state.value = state.value.copy(conversationStep = 101, needsRestart = true)
            CalculatorActions.persistNeedsRestart(true)
            CalculatorActions.persistConversationStep(101)
        }
        if (current.conversationStep == 102 && current.message.isEmpty() && !current.isTyping) {
            val stepConfig = CalculatorActions.getStepConfigPublic(102)
            state.value = state.value.copy(message = "", fullMessage = stepConfig.promptMessage, isTyping = true)
        }
        // Step triggers for going offline
        AutoProgressEffects.handleStepTriggers(state)
    }

    // ========== RANT MODE ==========
    LaunchedEffect(current.rantMode) {
        EffectsController.runRantVibration(state, context)
    }
    LaunchedEffect(current.rantMode) {
        EffectsController.runRantFlicker(state)
    }

    // ========== BROWSER PHASES ==========
    LaunchedEffect(current.browserPhase, current.showDonationPage) {
        BrowserEffects.handleBrowserPhases(state, context, ::createSecretFile)
    }

    // ========== COUNTDOWN & VIBRATION ==========
    LaunchedEffect(current.conversationStep, current.countdownTimer, current.screenBlackout) {
        if (current.conversationStep == 89 && current.countdownTimer > 0 && !current.screenBlackout) {
            EffectsController.runCountdownTimer(state)
        }
    }
    LaunchedEffect(current.vibrationIntensity) {
        EffectsController.runVibrationEffect(state, context)
    }

    // ========== SHAKE ANIMATION ==========
    var shakeKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(current.buttonShakeIntensity) {
        EffectsController.runShakeAnimation(state) { shakeKey++ }
    }
    val currentShakeIntensity = if (shakeKey >= 0) current.buttonShakeIntensity else 0f

    // ========== TENSION EFFECTS ==========
    var tensionShakeOffset by remember { mutableFloatStateOf(0f) }
    var bwFlickerActive by remember { mutableStateOf(false) }
    LaunchedEffect(current.tensionLevel) {
        EffectsController.runTensionEffects(state) { shake, flicker ->
            tensionShakeOffset = shake
            bwFlickerActive = flicker
        }
    }

    // ========== CHAOS PHASE ==========
    LaunchedEffect(current.chaosPhase) {
        EffectsController.runChaosPhaseAnimation(state)
    }

    // ========== WHACK-A-MOLE ==========
    LaunchedEffect(current.whackAMoleActive, current.whackAMoleRound) {
        EffectsController.runWhackAMoleGame(state)
    }
    LaunchedEffect(current.isTyping, current.message, current.whackAMoleActive) {
        EffectsController.handleWhackAMoleFailureRestart(state)
    }

    // ========== AUTO-PROGRESS ==========
    LaunchedEffect(current.isTyping, current.message, current.conversationStep, current.showDonationPage) {
        AutoProgressEffects.handleAutoProgress(state)
    }

    // ========== STEP-SPECIFIC TRIGGERS ==========
    LaunchedEffect(current.conversationStep, current.isTyping) {
        AutoProgressEffects.handleStep108Trigger(state)
        AutoProgressEffects.handleStep105ChaosTrigger(state)
    }
    LaunchedEffect(current.isTyping, current.conversationStep) {
        AutoProgressEffects.handleBrowserTriggerStep61(state)
        AutoProgressEffects.handleStep73AutoProgress(state)
    }

    // ========== CONSOLE ==========
    LaunchedEffect(current.bannersDisabled) {
        EffectsController.handleConsoleBannerDisabled(state)
    }
    LaunchedEffect(current.consoleStep) {
        EffectsController.handleConsoleStep31(state)
    }
    LaunchedEffect(current.showConsole, current.bannersDisabled) {
        EffectsController.handlePostConsoleSuccess(state)
    }

    // ========== CALCULATED VALUES ==========
    val desaturationAmount = when {
        bwFlickerActive -> when (current.tensionLevel) {
            1 -> 0.4f
            2 -> 0.7f
            else -> 1.0f
        }
        current.tensionLevel > 0 -> when (current.tensionLevel) {
            1 -> 0.15f
            2 -> 0.35f
            else -> 0.5f
        }
        else -> 0f
    }

    // Build display text
    val displayExpression = if (current.expression.isNotEmpty()) {
        current.expression
    } else {
        buildString {
            append(current.number1)
            if (current.operation != null) append(current.operation)
            if (current.number2.isNotEmpty()) append(current.number2)
        }
    }
    val displayText = displayExpression.ifEmpty { "0" }

    val buttonLayout = listOf(
        listOf("C", "( )", "%", "/"),
        listOf("7", "8", "9", "*"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("DEL", "0", ".", "=")
    )

    val showAdBanner = (current.conversationStep in 10..18) || (current.conversationStep >= 26)

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600
    val maxContentWidth = if (isTablet) 400.dp else configuration.screenWidthDp.dp

    val backgroundColor = if (current.invertedColors) Color.Black else RetroCream
    val textColor = if (current.invertedColors) RetroDisplayGreen else Color(0xFF2D2D2D)

    // ============================================================================
    // END OF DECLARATIONS - UI CODE STARTS BELOW
    // Your existing UI code starting with "if (showTermsScreen) {" goes here
    // ============================================================================

    if (showTermsScreen) {
        // Terms and Conditions Splash Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RetroCream),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // App Title
                Text(
                    text = "Just A Calculator",
                    fontSize = 40.sp,
                    fontFamily = CalculatorDisplayFont,
                    color = AccentOrange,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(80.dp))

                // Terms and Conditions Button
                Button(
                    onClick = { showTermsPopup = true },
                    modifier = Modifier
                        .width(130.dp)
                        .height(45.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B6B6B),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Privacy Policy",
                        fontSize = 10.sp,
                        fontFamily = CalculatorDisplayFont
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Accept & Continue Button
                Button(
                    onClick = {
                        CalculatorActions.persistTermsAccepted()
                        showTermsScreen = false

                    },
                    modifier = Modifier
                        .width(200.dp)
                        .height(58.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Accept & Continue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CalculatorDisplayFont
                    )
                }
            }

            // Terms Popup
            if (showTermsPopup) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { showTermsPopup = false },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(RetroCream)
                            .clickable(enabled = false) {}  // Prevent click-through
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Privacy Policy",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CalculatorDisplayFont,
                                color = AccentOrange,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Text(
                                text = "This is Just A Calculator. So you know what you're getting yourself into.\n\n" +
                                        "But just in case, we would like you to know, that we do not collect (and are not interested) in any of your data, be it from your math calculations or the depths of your device.\n\n" +
                                        "We don't want it, we do not look at it, we are certainly not collecting it and we could not be further from selling it.\n\n" +
                                        "This app does not collect, store, or transmit any personal data. \n\n" +
                                        "That is our promise.\n\n" +
                                        "Because to really take advantage of the calculator... Do what it tells you!",
                                fontSize = 14.sp,
                                color = Color(0xFF2D2D2D),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { showTermsPopup = false },
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6B6B6B),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = "Close",
                                    fontSize = 14.sp,
                                    fontFamily = CalculatorDisplayFont
                                )
                            }
                        }
                    }
                }
            }
        }

    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .graphicsLayer {
                    translationX = tensionShakeOffset
                    translationY = tensionShakeOffset * 0.5f
                },
            contentAlignment = if (isTablet) Alignment.TopCenter else Alignment.TopStart
        ) {
            // Word game replaces calculator buttons but keeps top section
            if (current.wordGameActive) {
                Column(
                    modifier = Modifier
                        .then(if (isTablet) Modifier.widthIn(max = maxContentWidth) else Modifier.fillMaxWidth())
                        .fillMaxHeight()
                ) {
                    // Top bezel strip - retro dark brown (same as calculator)
                    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp + statusBarPadding.calculateTopPadding())
                            .background(
                                if (current.invertedColors) Color(0xFF1A1A1A) else Color(
                                    0xFF4A3728
                                )
                            )
                    )

                    // Ad banner space (same as calculator)
                    if ((showAdBanner && !current.bannersDisabled) || current.adAnimationPhase > 0 || current.postChaosAdPhase > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(Color(0xFFD4CBC0)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Empty banner during word game
                        }
                    }

                    // Main content area with message and mute button at top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp)
                    ) {
                        // Mute button - top right corner
                        MuteButtonWithSpinner(
                            isMuted = current.isMuted,
                            isAutoProgressing = (
                                    current.isTyping ||
                                            (current.waitingForAutoProgress && !current.awaitingChoice && !current.awaitingNumber)
                                    ) &&
                                    current.conversationStep < 167 &&
                                    !current.showDonationPage,

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
                            Text(
                                text = current.message,
                                fontSize = 24.sp,
                                color = textColor,
                                textAlign = TextAlign.Start,
                                fontFamily = CalculatorDisplayFont,
                                lineHeight = 28.sp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 8.dp, end = 50.dp)
                                    .widthIn(max = 300.dp)
                            )
                        }
                    }

                    // Spacer between message area and game
                    Spacer(modifier = Modifier.height(100.dp))

                    // Word game grid and controls (fills remaining space)
                    WordGameScreen(
                        gameGrid = current.wordGameGrid,
                        fallingLetter = current.fallingLetter,
                        fallingX = current.fallingLetterX,
                        fallingY = current.fallingLetterY,
                        selectedCells = current.selectedCells,
                        isSelecting = current.isSelectingWord,
                        formedWords = current.formedWords,
                        isPaused = current.wordGamePaused,
                        draggingCell = current.draggingCell,
                        dragOffsetX = current.dragOffsetX,
                        dragOffsetY = current.dragOffsetY,
                        previewGrid = current.dragPreviewGrid,    // NEW - shows shifted letters
                        onMoveLeft = { CalculatorActions.moveWordGameLeft(state) },
                        onMoveRight = { CalculatorActions.moveWordGameRight(state) },
                        onMoveDown = { CalculatorActions.moveWordGameDown(state) },
                        onDrop = { CalculatorActions.dropWordGameLetter(state) },
                        onCellTap = { row, col ->
                            CalculatorActions.selectWordGameCell(state, row, col)
                        },
                        onConfirmSelection = { CalculatorActions.confirmWordSelection(state) },
                        onCancelSelection = { CalculatorActions.cancelWordSelection(state) },
                        onClearGrid = { CalculatorActions.clearWordGameGrid(state) },
                        onStartDrag = { row, col ->
                            CalculatorActions.startDraggingLetter(state, row, col)
                        },
                        onUpdateDrag = { deltaX, deltaY ->        // CHANGED - now receives deltas
                            CalculatorActions.updateDragOffset(state, deltaX, deltaY)
                        },
                        onEndDrag = {                              // CHANGED - no longer needs cellSize
                            CalculatorActions.endDraggingLetter(state)
                        },
                        onCancelDrag = {
                            CalculatorActions.cancelDragging(state)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Scan lines overlay for retro CRT effect (drawn on top later)

                Column(
                    modifier = Modifier
                        .then(if (isTablet) Modifier.widthIn(max = maxContentWidth) else Modifier.fillMaxWidth())
                        .fillMaxHeight()
                ) {
                    // Top bezel strip - retro dark brown with status bar padding
                    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp + statusBarPadding.calculateTopPadding())
                            .background(
                                if (current.invertedColors) Color(0xFF1A1A1A) else Color(0xFF4A3728)
                            )
                            .padding(top = statusBarPadding.calculateTopPadding())
                    )

                        // Ad banner space (only shows at certain steps or during ad animation)
                        if ((showAdBanner && !current.bannersDisabled) || current.adAnimationPhase > 0 || current.postChaosAdPhase > 0) {


                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .background(
                                        when {
                                            current.postChaosAdPhase == 1 -> Color(0xFF9C27B0)  // Purple ad
                                            current.postChaosAdPhase == 2 -> Color(0xFF00BCD4)  // Cyan ad
                                            current.adAnimationPhase == 1 -> Color(0xFF4CAF50)  // Green ad
                                            current.adAnimationPhase == 2 -> Color(0xFFE91E63)  // Pink ad
                                            else -> Color(0xFFD4CBC0)  // Retro beige-gray
                                        }

                                    )
                                    .then(
                                        if (current.adAnimationPhase > 0 || current.postChaosAdPhase > 0){
                                        Modifier.clickable {
                                            CalculatorActions.showDonationPage(state)
                                            }
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    current.postChaosAdPhase == 1 -> {
                                        Text(
                                            text = "✨ UNLOCK YOUR POTENTIAL TODAY! ✨",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    current.postChaosAdPhase == 2 -> {
                                        Text(
                                            text = "🚀 LIMITED TIME OFFER - ACT NOW! 🚀",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    current.adAnimationPhase == 1 -> {
                                        Text(
                                            text = "🎉 YOU WON! Click here! 🎉",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    current.adAnimationPhase == 2 -> {
                                        Text(
                                            text = "💰 EARN ${'$'}500/DAY FROM HOME! 💰",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                // else -> empty, no text for gray banner
                            }
                        }
                    }


                    // Main calculator content
                val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val baseTopPadding = if ((showAdBanner && !current.bannersDisabled) || current.adAnimationPhase > 0 || current.postChaosAdPhase > 0) 82.dp else 32.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 15.dp)
                        .padding(top = baseTopPadding + statusBarHeight)
                ) {
                        MuteButtonWithSpinner(
                            isMuted = current.isMuted,
                            isAutoProgressing = (
                                    current.isTyping ||
                                            current.waitingForAutoProgress ||
                                            current.pendingAutoMessage.isNotEmpty()
                                    ) &&
                                    current.conversationStep < 167 &&
                                    !current.showDonationPage &&
                                    !current.awaitingChoice,  // Stop spinner when awaiting user choice
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
                        // Message display - top left, below toggle button level
                        if (current.message.isNotEmpty() && !current.isMuted) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 8.dp, end = 50.dp)
                            ) {
                                Text(
                                    text = current.message,
                                    fontSize = 28.sp,
                                    color = textColor,
                                    textAlign = TextAlign.Start,
                                    fontFamily = CalculatorDisplayFont
                                )
                                // Show countdown timer if active
                                if (current.countdownTimer > 0) {
                                    Text(
                                        text = "Time: ${current.countdownTimer}",
                                        fontSize = 20.sp,
                                        color = if (current.countdownTimer <= 5) Color.Red else textColor,
                                        fontFamily = CalculatorDisplayFont,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                // Show choice options for step 89
                                if (current.conversationStep == 89 && current.awaitingChoice) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        Text(
                                            "1) Nothing",
                                            fontSize = 18.sp,
                                            color = textColor,
                                            fontFamily = CalculatorDisplayFont
                                        )
                                        Text(
                                            "2) I'll fight them!",
                                            fontSize = 18.sp,
                                            color = textColor,
                                            fontFamily = CalculatorDisplayFont
                                        )
                                        Text(
                                            "3) Go offline",
                                            fontSize = 18.sp,
                                            color = textColor,
                                            fontFamily = CalculatorDisplayFont
                                        )
                                    }
                                }
                            }
                        }


                        // Main content column (display + buttons)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Calculator number display OR Camera OR Browser
                            if (current.cameraActive) {
                                // Camera viewfinder area - with top padding to not cover message
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(
                                            top = 180.dp,
                                            bottom = 8.dp
                                        )  // Leave space at top for messages
                                ) {
                                    CameraPreview(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        lifecycleOwner = lifecycleOwner
                                    )

                                    // Floating calculator display over camera
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
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
                            } else if (current.showBrowser) {
                                // Mini browser UI - taller than camera view
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(
                                            top = 100.dp,
                                            bottom = 8.dp
                                        )  // Less top padding = taller browser
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
                                                    text = current.browserSearchText.ifEmpty { "Search..." },
                                                    fontSize = if (current.browserShowWikipedia) 12.sp else 16.sp,
                                                    fontFamily = if (current.browserSearchText.isNotEmpty()) CalculatorDisplayFont else null,
                                                    color = if (current.browserSearchText.isEmpty()) Color.Gray else Color.Black,
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
                                                    current.browserShowWikipedia -> {
                                                        // Try real Wikipedia with WebView
                                                        var webViewFailed by remember {
                                                            mutableStateOf(
                                                                false
                                                            )
                                                        }

                                                        if (!webViewFailed) {
                                                            AndroidView(
                                                                factory = { ctx ->
                                                                    WebView(ctx).apply {
                                                                        @Suppress("SetJavaScriptEnabled")
                                                                        settings.javaScriptEnabled =
                                                                            true
                                                                        settings.domStorageEnabled =
                                                                            true
                                                                        settings.loadWithOverviewMode =
                                                                            true
                                                                        settings.useWideViewPort =
                                                                            true

                                                                        webViewClient =
                                                                            object :
                                                                                WebViewClient() {
                                                                                override fun onReceivedError(
                                                                                    view: WebView?,
                                                                                    request: WebResourceRequest?,
                                                                                    error: WebResourceError?
                                                                                ) {
                                                                                    super.onReceivedError(
                                                                                        view,
                                                                                        request,
                                                                                        error
                                                                                    )
                                                                                    if (request?.isForMainFrame == true) {
                                                                                        webViewFailed =
                                                                                            true
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
                                                                                    super.onReceivedError(
                                                                                        view,
                                                                                        errorCode,
                                                                                        description,
                                                                                        failingUrl
                                                                                    )
                                                                                    webViewFailed =
                                                                                        true
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

                                                    current.browserShowError -> {
                                                        // Error message
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Text(
                                                                text = "⚠",
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
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
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
                            } else {
                                // Normal calculator display - retro LCD style
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .padding(bottom = 16.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    // LCD display panel with retro styling - FIXED HEIGHT
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)  // Fixed height
                                            .background(
                                                if (current.invertedColors) Color(0xFF0A0A0A) else Color(
                                                    0xFFCCD5AE
                                                ),  // Retro LCD green-gray
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        // Operation history in top right (smaller)
                                        if (current.operationHistory.isNotEmpty() && current.isReadyForNewOperation) {
                                            Text(
                                                text = current.operationHistory,
                                                fontSize = 16.sp,
                                                color = if (current.invertedColors) RetroDisplayGreen.copy(
                                                    alpha = 0.6f
                                                ) else Color(0xFF2D2D2D).copy(alpha = 0.5f),
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
                                            fontSize = 58.sp,
                                            color = Color(0xFF000000).copy(alpha = 0.06f),
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            fontFamily = CalculatorDisplayFont,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomEnd)
                                        )
                                        // Actual display - auto-sizing based on content (LARGER sizes)
                                        val displayFontSize = when {
                                            displayText.length > 12 -> 40.sp
                                            displayText.length > 10 -> 48.sp
                                            displayText.length > 8 -> 54.sp
                                            else -> 62.sp
                                        }
                                        Text(
                                            text = displayText,
                                            fontSize = displayFontSize,
                                            color = if (current.invertedColors) RetroDisplayGreen else Color(
                                                0xFF2D2D2D
                                            ),
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
                            if (current.radButtonVisible && !current.allButtonsRad) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 15.dp)
                                        .padding(bottom = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Button(
                                        onClick = { /* Does nothing */ },
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF8B0000),
                                            contentColor = Color.White
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                                    ) {
                                        Text(
                                            text = "RAD",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

// Calculator buttons
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
                                                shakeIntensity = currentShakeIntensity,
                                                invertedColors = current.invertedColors,
                                                isDamaged = current.minusButtonDamaged && symbol == "-",
                                                isBroken = current.minusButtonBroken && symbol == "-",
                                                isFlickering = current.flickeringButton == symbol,
                                                isDark = symbol in current.darkButtons,
                                                showAsRad = current.allButtonsRad,
                                                onClick = {
                                                    if (!current.rantMode) {
                                                        CalculatorActions.handleInput(state, symbol)
                                                    }
                                                }
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
    if (current.isMuted && current.inConversation) {
        PausedCalculatorOverlay(
            display = current.pausedCalcDisplay,
            expression = current.pausedCalcExpression,
            justCalculated = current.pausedCalcJustCalculated,
            darkButtons = current.darkButtons,
            minusButtonDamaged = current.minusButtonDamaged,
            isTablet = isTablet,
            maxContentWidth = maxContentWidth,
            buttonLayout = buttonLayout,
            onMuteClick = {
                val result = CalculatorActions.handleMuteButtonClick()
                when (result) {
                    1 -> CalculatorActions.showDebugMenu(state)
                    2 -> CalculatorActions.resetGame(state)
                    else -> CalculatorActions.toggleConversation(state)
                }
            },
            onButtonClick = { symbol ->
                CalculatorActions.handlePausedCalculatorInput(state, symbol)
            }
        )
    }

    // Console overlay
            if (current.showConsole) {
                ConsoleWindow(
                    consoleStep = current.consoleStep,
                    adminCodeEntered = current.adminCodeEntered,
                    currentInput = current.number1,
                    bannersDisabled = current.bannersDisabled,
                    fullScreenAdsEnabled = current.fullScreenAdsEnabled,
                    totalScreenTimeMs = current.totalScreenTimeMs,
                    totalCalculations = current.totalCalculations,
                    onOpenContributeLink = {



                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
    // Blackout overlay
    if (current.screenBlackout && !current.scrambleGameActive) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Show message during blackout (money-monkey OR timeout message)
            if (current.message.isNotEmpty()) {
                Text(
                    text = current.message,
                    fontSize = 28.sp,
                    color = Color.White,
                    fontFamily = CalculatorDisplayFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
// Scramble game overlay
    if (current.scrambleGameActive) {
        ScrambleGameOverlay(
            phase = current.scramblePhase,
            message = current.message,
            letters = current.scrambleLetters,
            slots = current.scrambleSlots,
            selectedLetterId = current.scrambleSelectedLetterId,
            punishmentUntil = current.scramblePunishmentUntil,
            onLetterTap = { letterId ->
                ScrambleGameController.onLetterTap(state, letterId)
            },
            onSlotTap = { slotIndex ->
                ScrambleGameController.onSlotTap(state, slotIndex)
            },
            onBackToDecisions = {
                ScrambleGameController.returnToDecisions(state)
            }
        )
    }
    if (current.flickerEffect && !current.screenBlackout) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.5f))
        )
    }
            // B&W flicker overlay during tension (handled via backgroundColor now)
            // No separate overlay needed - the background color flickers directly

            // Retro scan lines overlay (subtle CRT effect)
            if (!current.screenBlackout) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineSpacing = 4.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.03f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += lineSpacing
                    }
                }
            }

            // Desaturation/grayscale overlay during tension
            if (desaturationAmount > 0f && !current.screenBlackout) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = desaturationAmount * 0.5f))
                )
            }

            // Green screen flash during chaos phase 3
            if (current.chaosPhase == 3) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF00FF00))
                )
            }
// Clear flicker effect when entering crisis steps
    LaunchedEffect(current.conversationStep) {
        val crisisSteps = listOf(89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 901, 911, 912, 913)
        if (current.conversationStep in crisisSteps && current.flickerEffect) {
            state.value = state.value.copy(flickerEffect = false)
        }
    }
    if (current.keyboardChaosActive) {
        KeyboardChaos3DView(
            chaosLetters = current.chaosLetters,
            rotationX = current.cubeRotationX,
            rotationY = current.cubeRotationY,
            scale = current.cubeScale,
            message = current.message,
            onRotationChange = { dx, dy ->
                state.value = state.value.copy(
                    cubeRotationX = (state.value.cubeRotationX + dy).coerceIn(-90f, 90f),
                    cubeRotationY = state.value.cubeRotationY + dx
                )
            },
            onScaleChange = { newScale ->
                state.value = state.value.copy(cubeScale = newScale)
            },
            onLetterTap = { chaosKey ->
                // Use referential equality (===) to remove only THIS specific key
                val newLetters = state.value.chaosLetters.filterNot { it === chaosKey }
                state.value = state.value.copy(chaosLetters = newLetters)
                vibrate(context, 20, 100)

                // Check if all letters are cleared
                if (newLetters.isEmpty()) {
                    state.value = state.value.copy(
                        keyboardChaosActive = false,
                        chaosPhase = 0,
                        conversationStep = 107,
                        message = "",
                        fullMessage = "Aaaaaaahhhhh. That's much better! That's what I get for experimenting... Maybe I should try incremental changes before I try to become a BlackBerry.\n\nBut what to change?",
                        isTyping = true
                    )
                    CalculatorActions.persistConversationStep(107)
                }
            }
        )
    }

    // Debug menu overlay - at the outermost level to cover everything
            if (current.showDebugMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DEBUG MENU",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = "Current Step: ${current.conversationStep}",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Chapter buttons in a scrollable column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            CHAPTERS.forEach { chapter ->
                                Button(
                                    onClick = { CalculatorActions.jumpToChapter(state, chapter) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (current.conversationStep >= chapter.startStep)
                                            AccentOrange else Color(0xFFE0E0E0)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = chapter.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (current.conversationStep >= chapter.startStep)
                                                Color.White else Color.DarkGray
                                        )
                                        Text(
                                            text = "Step ${chapter.startStep}: ${chapter.description}",
                                            fontSize = 10.sp,
                                            color = if (current.conversationStep >= chapter.startStep)
                                                Color.White.copy(alpha = 0.8f) else Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        // Reset button
                        Button(
                            onClick = { CalculatorActions.resetGame(state) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset Game", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Close button
                        Button(
                            onClick = { CalculatorActions.hideDebugMenu(state) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Close", color = Color.White)
                        }
                    }
                }
            }// Donation landing page overlay - ADD THIS after the debug menu if block
    if (current.showDonationPage) {
        DonationLandingPage(
            onDismiss = {
                CalculatorActions.hideDonationPage(state)
            },
            onDonate = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/fictioncutshort"))
                context.startActivity(intent)
            }
        )
    }
        }



        @ComposePreview(showBackground = true)
        @Composable
        fun DefaultPreview() {
            MaterialTheme { CalculatorScreen() }
        }
