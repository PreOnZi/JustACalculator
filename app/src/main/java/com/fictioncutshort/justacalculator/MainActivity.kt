package com.fictioncutshort.justacalculator




import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import com.fictioncutshort.justacalculator.logic.DormancyManager
import com.fictioncutshort.justacalculator.logic.DormancyPhase
import com.fictioncutshort.justacalculator.logic.EffectsController
import com.fictioncutshort.justacalculator.ui.screens.DormancyOverlay
import com.fictioncutshort.justacalculator.ui.screens.AdCardStack
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
import com.fictioncutshort.justacalculator.logic.TalkAudioHandler
import com.fictioncutshort.justacalculator.ui.components.PortraitCalculatorContent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.fictioncutshort.justacalculator.ui.components.PausedCalculatorOverlay
import com.fictioncutshort.justacalculator.ui.components.TalkOverlay
import com.fictioncutshort.justacalculator.ui.components.PhoneOverlay
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import com.fictioncutshort.justacalculator.ui.components.TopBezelBar
import com.fictioncutshort.justacalculator.ui.components.AdBanner
import com.fictioncutshort.justacalculator.ui.components.MessageDisplay
import com.fictioncutshort.justacalculator.ui.components.CalculatorLcdDisplay
import com.fictioncutshort.justacalculator.ui.components.CalculatorButtonGrid
import com.fictioncutshort.justacalculator.ui.components.RadButton
import com.fictioncutshort.justacalculator.ui.components.LandscapeCalculatorContent
import com.fictioncutshort.justacalculator.util.rememberResponsiveDimensions
import com.fictioncutshort.justacalculator.util.BezelBrown
import com.fictioncutshort.justacalculator.util.BezelInverted
import androidx.compose.runtime.saveable.rememberSaveable



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CalculatorActions.init(applicationContext)
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
    val talkAudioHandler = remember { TalkAudioHandler(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // Use live state from singleton if it exists (survives rotation),
    // otherwise create fresh from SharedPreferences
    val state = remember {
        CalculatorActions.liveState ?: mutableStateOf(CalculatorActions.loadInitialState()).also {
            CalculatorActions.liveState = it
        }
    }
    // Re-attach after activity recreation (remember re-runs but singleton keeps state)
    LaunchedEffect(Unit) {
        CalculatorActions.liveState = state
    }
    val current = state.value
    var showTermsScreen by rememberSaveable { mutableStateOf(!CalculatorActions.loadTermsAcceptedPublic()) }
    var showTermsPopup by remember { mutableStateOf(false) }
    var microphonePermissionRequested by remember { mutableStateOf(false) }
    var locationPermissionRequested by remember { mutableStateOf(false) }
    var contactsPermissionRequested by remember { mutableStateOf(false) }


// Lifecycle observer to save state when app goes to background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Save current state when app is paused/minimized
                    val currentState = state.value
                    android.util.Log.d("JustACalc", "ON_PAUSE CALLED - current step: ${currentState.conversationStep}")

                    // Stop rant effects immediately so they don't continue in the background.
                    // The rant step is preserved via persistConversationStep below so it resumes on return.
                    if (currentState.rantMode) {
                        state.value = state.value.copy(
                            rantMode = false,
                            flickerEffect = false,
                            vibrationIntensity = 0
                        )
                    }

                    // Determine the step to save.
                    // Only prefer pendingAutoStep if the current step is a truly transient/mid-animation
                    // step that has no meaningful content to resume from (e.g. step 92, 901 etc.).
                    // For all steps that have a StepConfig message (like the phone detour 1071-1087),
                    // always save the current step — pendingAutoStep points forward and would skip content.
                    val currentStep = currentState.conversationStep
                    val currentStepConfig = CalculatorActions.getStepConfigPublic(currentStep)
                    val currentStepHasContent = currentStepConfig.promptMessage.isNotEmpty() ||
                            currentStepConfig.successMessage.isNotEmpty() ||
                            currentStep in com.fictioncutshort.justacalculator.data.INTERACTIVE_STEPS
                    val stepToSave = if (currentState.pendingAutoStep >= 0 && !currentStepHasContent) {
                        currentState.pendingAutoStep
                    } else {
                        currentStep
                    }
                    android.util.Log.d("JustACalc", "ON_PAUSE: Saving step $stepToSave (current=$currentStep, pending=${currentState.pendingAutoStep}, hasContent=$currentStepHasContent)")


                    // Save all critical state (only using PUBLIC persist functions)
                    CalculatorActions.persistConversationStep(stepToSave)
                    CalculatorActions.persistInConversation(currentState.inConversation)
                    CalculatorActions.persistPausedAtStep(stepToSave)
                    CalculatorActions.persistEqualsCount(currentState.equalsCount)
                    CalculatorActions.persistMuted(currentState.isMuted)
                    CalculatorActions.persistInvertedColors(currentState.invertedColors)
                    CalculatorActions.persistMinusDamaged(currentState.minusButtonDamaged)
                    CalculatorActions.persistMinusBroken(currentState.minusButtonBroken)
                    CalculatorActions.persistDarkButtons(currentState.darkButtons)
                    CalculatorActions.persistTotalScreenTime(currentState.totalScreenTimeMs)
                    CalculatorActions.persistTotalCalculations(currentState.totalCalculations)
                    // Persist dormancy pressed buttons on pause
                    CalculatorActions.persistDormancyPressedButtons(currentState.dormancyPressedButtons)
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Restore rantMode if the user returns to the app while mid-rant
                    val currentState = state.value
                    val step = currentState.conversationStep
                    if (!currentState.rantMode && step in 150..166) {
                        state.value = currentState.copy(rantMode = true)
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    // Additional safety save when app fully goes to background
                    val currentState = state.value
                    val currentStep = currentState.conversationStep
                    val currentStepConfig = CalculatorActions.getStepConfigPublic(currentStep)
                    val currentStepHasContent = currentStepConfig.promptMessage.isNotEmpty() ||
                            currentStepConfig.successMessage.isNotEmpty() ||
                            currentStep in com.fictioncutshort.justacalculator.data.INTERACTIVE_STEPS
                    val stepToSave = if (currentState.pendingAutoStep >= 0 && !currentStepHasContent) {
                        currentState.pendingAutoStep
                    } else {
                        currentStep
                    }
                    CalculatorActions.persistConversationStep(stepToSave)
                    CalculatorActions.persistPausedAtStep(stepToSave)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    // Debug: Track step changes
    LaunchedEffect(state.value.conversationStep) {
        android.util.Log.d("JustACalc", "Step changed to: ${state.value.conversationStep}")
    }

    // ========== DORMANCY: CHECK PHASE ON LAUNCH ==========
    // Reads elapsed time since rant end and restores correct dormancy state.
    // Handles the "user fully quit the app and reopened" path.
    LaunchedEffect(Unit) {
        val dormancyPhase = DormancyManager.getCurrentPhase(context)
        when (dormancyPhase) {
            is DormancyPhase.None -> { /* not in dormancy */ }
            is DormancyPhase.Static -> {
                state.value = state.value.copy(
                    showDormancy = true,
                    dormancyRadVisible = 0
                )
            }
            is DormancyPhase.RadButtons -> {
                val savedPressed = CalculatorActions.loadDormancyPressedButtons()
                state.value = state.value.copy(
                    showDormancy = true,
                    dormancyRadVisible = dormancyPhase.count,
                    dormancyPressedButtons = savedPressed
                )
            }
        }
        // ── Restore ad card state if user closed app mid-Phase 2 ──
        if (CalculatorActions.loadShowAdCards()) {
            state.value = state.value.copy(showAdCards = true)
        }
    }

    // ========== DORMANCY: TRIGGER STATIC AFTER RANT ENDS (IN-APP PATH) ==========
    // If the user never leaves the app after storyComplete, wait 1 min then show static.
    LaunchedEffect(current.storyComplete) {
        if (!current.storyComplete) return@LaunchedEffect
        if (state.value.showDormancy) return@LaunchedEffect
        kotlinx.coroutines.delay(DormancyManager.STATIC_DELAY_MS)
        val phase = DormancyManager.getCurrentPhase(context)
        if (phase != DormancyPhase.None) {
            state.value = state.value.copy(showDormancy = true, dormancyRadVisible = 0)
        }
    }

    // ========== DORMANCY: TICK LOOP — UPDATE RAD BUTTON COUNT WHILE IN-APP ==========
    // Polls every 10 seconds so RAD buttons appear on schedule if user never left the app.
    LaunchedEffect(current.showDormancy) {
        if (!current.showDormancy) return@LaunchedEffect
        while (state.value.showDormancy) {
            kotlinx.coroutines.delay(10_000L)
            val phase = DormancyManager.getCurrentPhase(context)
            when (phase) {
                is DormancyPhase.Static -> {
                    if (state.value.dormancyRadVisible != 0) {
                        state.value = state.value.copy(dormancyRadVisible = 0)
                    }
                }
                is DormancyPhase.RadButtons -> {
                    if (phase.count > state.value.dormancyRadVisible) {
                        state.value = state.value.copy(dormancyRadVisible = phase.count)
                    }
                }
                is DormancyPhase.None -> { /* nothing */ }
            }
        }
    }
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

    // Permission launchers
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
// Microphone permission state
    var hasMicrophonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

// Location permission state
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

// Contacts permission state
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
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
        EffectsController.runTypingAnimation(state, context, talkAudioHandler)
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
                CalculatorActions.persistConversationStep(191)
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
    //MICROPHONE LOCATION CONTACTS PERMISSIONS
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicrophonePermission = granted
        val nextConfig = CalculatorActions.getStepConfigPublic(1075)
        state.value = state.value.copy(
            conversationStep = 1075,
            message = "",
            fullMessage = nextConfig.promptMessage,
            isTyping = true
        )
        CalculatorActions.persistConversationStep(1075)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        val nextConfig = CalculatorActions.getStepConfigPublic(1076)
        state.value = state.value.copy(
            conversationStep = 1076,
            message = "",
            fullMessage = nextConfig.promptMessage,
            isTyping = true
        )
        CalculatorActions.persistConversationStep(1076)
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
        val nextConfig = CalculatorActions.getStepConfigPublic(1077)
        state.value = state.value.copy(
            conversationStep = 1077,
            message = "",
            fullMessage = nextConfig.promptMessage,
            isTyping = true
        )
        CalculatorActions.persistConversationStep(1077)
    }

    // ========== RANT MODE ==========
    LaunchedEffect(current.rantMode) {
        EffectsController.runRantVibration(state, context)
    }
    LaunchedEffect(current.rantMode) {
        EffectsController.runRantFlicker(state)
    }
    // Telephone detour permissions
    LaunchedEffect(current.conversationStep, current.message, current.isTyping) {
        // Microphone - step is 1075 when "Nice!" is shown (success message from 1074)
        if (current.conversationStep == 1075 && current.message == "Nice!" && !current.isTyping) {
            if (!microphonePermissionRequested) {
                microphonePermissionRequested = true
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        // Location - step is 1076 when success message from 1075 is shown
        if (current.conversationStep == 1076 && current.message == " It's a joy to work with you already! " && !current.isTyping) {
            if (!locationPermissionRequested) {
                locationPermissionRequested = true
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        // Contacts - step is 1077 when success message from 1076 is shown
        if (current.conversationStep == 1077 && current.message == "We are on a roll!" && !current.isTyping) {
            if (!contactsPermissionRequested) {
                contactsPermissionRequested = true
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
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

    // ========== DORMANCY: CONTINUOUS VIBRATION LOOP ==========
    // Pulsing loop while dormancy buttons are being pressed.
    // Amplitude and tempo escalate with each button pressed (1→5).
    LaunchedEffect(current.dormancyPressedButtons.size, current.showDormancy) {
        if (!current.showDormancy || current.dormancyPressedButtons.isEmpty()) return@LaunchedEffect
        val pressCount = state.value.dormancyPressedButtons.size
        val amplitude = (pressCount * 50).coerceIn(50, 255)
        val durationMs = (100L + pressCount * 20L).coerceIn(100L, 250L)
        val pauseMs = (400L - pressCount * 40L).coerceIn(100L, 400L)
        while (state.value.showDormancy && state.value.dormancyPressedButtons.size == pressCount) {
            vibrate(context, durationMs, amplitude)
            kotlinx.coroutines.delay(durationMs + pauseMs)
        }
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
        AutoProgressEffects.handleAutoProgress(state, context)
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
    // Dismiss phone overlay after "That's awful" message finishes
    LaunchedEffect(current.conversationStep, current.message, current.isTyping) {
        if (current.conversationStep == 1087 &&
            current.message == "AAAAAH. That's awful! There must be another way." &&
            !current.isTyping) {
            kotlinx.coroutines.delay(2000)  // Let user read the message
            state.value = state.value.copy(
                showPhoneOverlay = false,
                showTalkOverlay = false,
                conversationStep = 108
            )
            // Load step 108's message
            val nextConfig = CalculatorActions.getStepConfigPublic(108)
            state.value = state.value.copy(
                message = "",
                fullMessage = nextConfig.promptMessage,
                isTyping = true
            )
            CalculatorActions.persistConversationStep(108)
        }
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
    val dimensions = rememberResponsiveDimensions()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE


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
                            .height( statusBarPadding.calculateTopPadding())
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
                // Main calculator (not word game)
                Column(
                    modifier = Modifier
                        .then(if (isTablet && !isLandscape) Modifier.widthIn(max = maxContentWidth) else Modifier.fillMaxWidth())
                        .fillMaxHeight()
                ) {
                    // Top bezel - just status bar height
                    TopBezelBar(invertedColors = current.invertedColors)

                    // Ad banner
                    AdBanner(
                        showBanner = showAdBanner,
                        bannersDisabled = current.bannersDisabled,
                        adAnimationPhase = current.adAnimationPhase,
                        postChaosAdPhase = current.postChaosAdPhase,
                        dimensions = dimensions,
                        onAdClick = { CalculatorActions.showDonationPage(state) }
                    )

                    // Main content - switches between portrait and landscape
                    if (isLandscape) {
                        // LANDSCAPE LAYOUT
                        LandscapeCalculatorContent(
                            state = state,
                            current = current,
                            displayText = displayText,
                            buttonLayout = buttonLayout,
                            dimensions = dimensions,
                            textColor = textColor,
                            backgroundColor = backgroundColor,
                            showAdBanner = showAdBanner,
                            currentShakeIntensity = currentShakeIntensity,
                            lifecycleOwner = lifecycleOwner
                        )
                    } else {
                        // PORTRAIT LAYOUT (existing code, but using components)
                        PortraitCalculatorContent(
                            state = state,
                            current = current,
                            displayText = displayText,
                            buttonLayout = buttonLayout,
                            dimensions = dimensions,
                            textColor = textColor,
                            currentShakeIntensity = currentShakeIntensity,
                            lifecycleOwner = lifecycleOwner
                        )

                    }
                }


            }}
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

    // TalkOverlay
    if (current.showTalkOverlay && !current.showPhoneOverlay) {
        TalkOverlay(
            message = current.message,
            onButtonHoldStart = {
                talkAudioHandler.startRealtimeEcho()
            },
            onButtonHoldEnd = {
                talkAudioHandler.stopRealtimeEcho()
                talkAudioHandler.playStaticSound {
                    // After static finishes, progress the story
                    val nextMessage = "Hello? I can see you've pressed the button, but I can't hear anything."
                    state.value = state.value.copy(
                        message = "",
                        fullMessage = nextMessage,
                        isTyping = true
                    )
                }
            }
        )
    }

// PhoneOverlay
    if (current.showPhoneOverlay) {
        PhoneOverlay(
            message = current.message,
            onButtonHoldStart = {
                talkAudioHandler.startRealtimeEcho()
            },
            onButtonHoldEnd = {
                talkAudioHandler.stopRealtimeEcho()
                talkAudioHandler.playFeedbackSqueal {
                    // Show the message but DON'T hide the overlay yet
                    state.value = state.value.copy(
                        message = "",
                        fullMessage = "AAAAAH. That's awful! There must be another way.",
                        isTyping = true,
                        conversationStep = 1087
                    )
                    CalculatorActions.persistConversationStep(1087)
                }
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
    // ========== SPINNER STATE MANAGEMENT ==========
// This prevents the spinner from flickering during step transitions
    LaunchedEffect(
        current.isTyping,
        current.waitingForAutoProgress,
        current.pendingAutoStep,
        current.awaitingChoice,
        current.awaitingNumber,
        current.conversationStep
    ) {
        val shouldShowSpinner = (
                current.isTyping ||
                        ((current.waitingForAutoProgress || current.pendingAutoStep >= 0) &&
                                !current.awaitingChoice && !current.awaitingNumber)
                ) &&
                (current.conversationStep < 167 || current.conversationStep in 1070..1087) &&
                !current.showDonationPage

        if (shouldShowSpinner) {
            // Turn on immediately
            if (!state.value.showSpinner) {
                state.value = state.value.copy(showSpinner = true)
            }
        } else {
            // Delay before turning off to prevent flicker
            kotlinx.coroutines.delay(600)  // Longer than any transition gap
            // Check again after delay - only turn off if still shouldn't show
            val stillShouldHide = !(
                    state.value.isTyping ||
                            ((state.value.waitingForAutoProgress || state.value.pendingAutoStep >= 0) &&
                                    !state.value.awaitingChoice && !state.value.awaitingNumber)
                    )

            if (stillShouldHide && state.value.showSpinner) {
                state.value = state.value.copy(showSpinner = false)
            }
        }
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
    // ========== PHASE 2: AD CARD STACK ==========
    // Full-screen ad cards → swipe 5 → stack collapses → pexeso memory game.
    // Calculator is still rendered underneath in outline-only mode
    // (PortraitCalculatorContent / LandscapeCalculatorLayout check showAdCards).
    if (current.showAdCards) {
        AdCardStack(
            onPexesoComplete = {
                CalculatorActions.clearShowAdCards()
                state.value = state.value.copy(showAdCards = false)
            }
        )}
    // ========== DORMANCY OVERLAY ==========
    // Rendered above all other overlays. Full-screen TV static + RAD buttons
    // after the rant ends. All 5 buttons must be pressed to proceed to Phase 2.
    if (current.showDormancy) {
        DormancyOverlay(
            radButtonsVisible = current.dormancyRadVisible,
            pressedButtons = current.dormancyPressedButtons,
            onButtonPressed = { index ->
                val newPressed = state.value.dormancyPressedButtons + index
                CalculatorActions.persistDormancyPressedButtons(newPressed)
                state.value = state.value.copy(
                    dormancyPressedButtons = newPressed,
                    vibrationIntensity = newPressed.size
                )
            },

                onAllPressed = {
                    DormancyManager.clearDormancy(context)
                    CalculatorActions.clearDormancyPressedButtons()
                    state.value = state.value.copy(
                        showDormancy = false,
                        dormancyRadVisible = 0,
                        dormancyPressedButtons = emptySet(),
                        vibrationIntensity = 0,
                        showAdCards = true
                    )
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