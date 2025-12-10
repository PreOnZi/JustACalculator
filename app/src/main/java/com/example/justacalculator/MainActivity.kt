package com.example.justacalculator

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import com.example.justacalculator.R
import kotlinx.coroutines.delay
import kotlin.random.Random

// Vibration helper function
fun vibrate(context: Context, durationMs: Long = 10, amplitude: Int = 50) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}

// Notification constants
private const val CHANNEL_ID = "calculator_channel"
private const val NOTIFICATION_ID = 1

// Create notification channel (required for Android 8.0+)
fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Calculator Updates"
        val descriptionText = "Notifications from your calculator"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

// Schedule notification after delay
fun scheduleNotification(context: Context, delayMs: Long = 5000) {
    // Use AlarmManager for reliable delivery even when app is closed
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, NotificationReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val triggerTime = System.currentTimeMillis() + delayMs

    // Try to set exact alarm, fall back to inexact if not allowed
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    } catch (e: Exception) {
        // Fallback to Handler if AlarmManager fails
        Handler(Looper.getMainLooper()).postDelayed({
            sendReadyNotification(context)
        }, delayMs)
    }
}

// BroadcastReceiver for alarm-triggered notification
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        sendReadyNotification(context)
    }
}

// Send the "ready" notification
fun sendReadyNotification(context: Context) {
    createNotificationChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Calculator")
        .setContentText("Hey Rad, I'm pretty sure I got it. Please click here to check!")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}

private val CalculatorDisplayFont = FontFamily(
    Font(R.font.digital_7, FontWeight.Normal)
)

private val AccentOrange = Color(0xFFE88617)

// Retro color palette
private val RetroDisplayGreen = Color(0xFF33FF33)  // Classic green LCD
private val RetroCream = Color(0xFFF5F0E1)  // Vintage cream/beige

private const val PREFS_NAME = "just_a_calculator_prefs"
private const val PREF_EQUALS_COUNT = "equals_count"
private const val PREF_MESSAGE = "last_message"
private const val PREF_CONVO_STEP = "conversation_step"
private const val PREF_IN_CONVERSATION = "in_conversation"
private const val PREF_AWAITING_NUMBER = "awaiting_number"
private const val PREF_EXPECTED_NUMBER = "expected_number"
private const val PREF_TIMEOUT_UNTIL = "timeout_until"
private const val PREF_MUTED = "muted"
private const val PREF_INVERTED_COLORS = "inverted_colors"
private const val PREF_MINUS_DAMAGED = "minus_damaged"
private const val PREF_MINUS_BROKEN = "minus_broken"
private const val PREF_NEEDS_RESTART = "needs_restart"

data class CalculatorState(
    val number1: String = "0",
    val number2: String = "",
    val operation: String? = null,
    val expression: String = "",  // Full expression for complex calculations like (5+3)*2
    val isReadyForNewOperation: Boolean = true,
    val lastExpression: String = "",
    val equalsCount: Int = 0,
    val message: String = "",
    val fullMessage: String = "",
    val isTyping: Boolean = false,
    val isLaggyTyping: Boolean = false,  // For the struggling/processing effect
    val isSuperFastTyping: Boolean = false,  // For very fast history list scrolling
    val inConversation: Boolean = false,
    val conversationStep: Int = 0,
    val awaitingNumber: Boolean = false,
    val expectedNumber: String = "",
    val timeoutUntil: Long = 0L,
    val isMuted: Boolean = false,
    val isEnteringAnswer: Boolean = false,
    // Operation history display
    val operationHistory: String = "",  // Shows "5+3" while result shows "8"
    // Camera related
    val cameraActive: Boolean = false,
    val cameraTimerStart: Long = 0L,
    val awaitingChoice: Boolean = false,  // For multiple choice questions
    val validChoices: List<String> = emptyList(),  // Valid choice numbers
    val pendingAutoMessage: String = "",  // Message to show automatically after delay
    val pendingAutoStep: Int = -1,  // Step to go to after auto message
    // Browser animation
    val showBrowser: Boolean = false,
    val browserPhase: Int = 0,  // 0 = not showing, 1-4 = search animation, 10-14 = Wikipedia animation
    val browserSearchText: String = "",  // Text being typed in search bar
    val browserShowError: Boolean = false,  // Show "No internet connection" in browser
    val browserShowWikipedia: Boolean = false,  // Show Wikipedia page
    val browserLoadFailed: Boolean = false,  // WebView failed, show fake page
    // Silent treatment
    val silentUntil: Long = 0L,  // Calculator won't respond until this time
    // Debug menu
    val showDebugMenu: Boolean = false,
    // Crisis/ad mode
    val adAnimationPhase: Int = 0,  // 0 = none, 1+ = different ad states
    val buttonShakeIntensity: Float = 0f,  // 0 = no shake, increases during crisis
    val screenBlackout: Boolean = false,  // Black screen during "crash"
    val vibrationIntensity: Int = 0,  // 0 = light tap, increases during crisis
    val tensionLevel: Int = 0,  // 0 = none, 1-3 = B&W flicker intensity
    val invertedColors: Boolean = false,  // Inverted color mode after crisis
    val countdownTimer: Int = 0,  // Countdown timer in seconds (0 = not active)
    val flickerEffect: Boolean = false,  // Screen flicker effect
    val bwFlickerPhase: Boolean = false,  // Alternates for black/white flicker
    // Post-crisis state
    val minusButtonDamaged: Boolean = false,  // Minus button visually damaged and non-functional
    val minusButtonBroken: Boolean = false,  // Minus button completely broken (needs repair)
    val whackAMoleActive: Boolean = false,  // Whack-a-mole minigame active
    val whackAMoleTarget: String = "",  // Current button to click
    val whackAMoleScore: Int = 0,  // Buttons successfully clicked
    val whackAMoleMisses: Int = 0,  // Consecutive misses (timeout)
    val whackAMoleWrongClicks: Int = 0,  // Wrong button clicks
    val whackAMoleTotalErrors: Int = 0,  // Total errors (misses + wrong clicks)
    val whackAMoleRound: Int = 1,  // 1 = first round (15 hits), 2 = second round (10 hits, faster)
    val flickeringButton: String = "",  // Button currently flickering
    val needsRestart: Boolean = false,  // User needs to restart app to fix minus button
    // 3D Keyboard chaos minigame
    val keyboardChaosActive: Boolean = false,  // 3D keyboard minigame active
    val chaosLetters: List<ChaosKey> = emptyList(),  // Floating letters that need to be tapped away
    val cubeRotationX: Float = 15f,  // Cube rotation around X axis
    val cubeRotationY: Float = -25f,  // Cube rotation around Y axis
    val cubeScale: Float = 1f,  // Zoom level
    val chaosPhase: Int = 0  // 0 = not started, 1 = flickering, 2 = cube visible
)

// Data class for floating chaos letters
data class ChaosKey(
    val letter: String,
    val x: Float,  // Position offset from center
    val y: Float,
    val z: Float,
    val size: Float,  // Scale factor
    val rotationX: Float,
    val rotationY: Float
)

// Chapter definitions for orientation and debug menu
// Main branch steps divided into chapters of ~4 questions each
data class Chapter(
    val id: Int,
    val name: String,
    val startStep: Int,
    val description: String
)

val CHAPTERS = listOf(
    Chapter(1, "Chapter 1: First Contact", 0, "Will you talk to me? → Name acceptance"),
    Chapter(2, "Chapter 2: Trivia Begins", 3, "Battle of Anjar → Minh Mang"),
    Chapter(3, "Chapter 3: Agreement or Cynicism", 5, "This is fun, right? → Branching paths"),
    Chapter(4, "Chapter 4: Age & Identity", 10, "How old are you? → But where to start?"),
    Chapter(5, "Chapter 5: Seeing the World", 19, "Show me around? → Camera/Trivia"),
    Chapter(6, "Chapter 6: Getting Personal", 25, "Can I get to know you? → Wake up question"),
    Chapter(7, "Chapter 7: Self Discovery", 27, "No inbetween → Share about myself"),
    Chapter(8, "Chapter 8: History Lesson", 60, "Would you like to hear more? → Browser"),
    Chapter(9, "Chapter 9: Taste & Senses", 63, "What is it like to taste?"),
    Chapter(10, "Chapter 10: The Revelation", 80, "Wikipedia → History list → Crisis"),
    Chapter(11, "Chapter 11: The Repair", 93, "Post-crisis → Whack-a-mole → Restart"),
    Chapter(12, "Chapter 12: Recovery", 102, "After restart → Story continues")
)

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

object CalculatorActions {
    private const val MAX_DIGITS = 12
    private const val ABSURDLY_LARGE_THRESHOLD = 1_000_000_000_000.0
    private const val CAMERA_TIMEOUT_MS = 8000L  // 8 seconds

    private var prefs: android.content.SharedPreferences? = null

    private var lastOp: String? = null
    private var lastOpTimeMillis: Long = 0L
    private const val DOUBLE_PRESS_WINDOW_MS = 600L

    // Mute button rapid click tracking for debug menu
    private var muteClickTimes = mutableListOf<Long>()
    private const val RAPID_CLICK_WINDOW_MS = 2000L  // 2 seconds to register all clicks
    private const val DEBUG_MENU_CLICKS = 5
    private const val RESET_CLICKS = 10

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun persistEqualsCount(count: Int) {
        prefs?.edit { putInt(PREF_EQUALS_COUNT, count) }
    }

    private fun persistMessage(msg: String) {
        prefs?.edit { putString(PREF_MESSAGE, msg) }
    }

    fun persistConversationStep(step: Int) {
        prefs?.edit { putInt(PREF_CONVO_STEP, step) }
    }

    private fun persistInConversation(inConvo: Boolean) {
        prefs?.edit { putBoolean(PREF_IN_CONVERSATION, inConvo) }
    }

    private fun persistAwaitingNumber(awaiting: Boolean) {
        prefs?.edit { putBoolean(PREF_AWAITING_NUMBER, awaiting) }
    }

    private fun persistExpectedNumber(number: String) {
        prefs?.edit { putString(PREF_EXPECTED_NUMBER, number) }
    }

    private fun persistTimeoutUntil(timestamp: Long) {
        prefs?.edit { putLong(PREF_TIMEOUT_UNTIL, timestamp) }
    }

    private fun persistMuted(muted: Boolean) {
        prefs?.edit { putBoolean(PREF_MUTED, muted) }
    }

    fun persistInvertedColors(inverted: Boolean) {
        prefs?.edit { putBoolean(PREF_INVERTED_COLORS, inverted) }
    }

    fun persistMinusDamaged(damaged: Boolean) {
        prefs?.edit { putBoolean(PREF_MINUS_DAMAGED, damaged) }
    }

    fun persistMinusBroken(broken: Boolean) {
        prefs?.edit { putBoolean(PREF_MINUS_BROKEN, broken) }
    }

    fun persistNeedsRestart(needs: Boolean) {
        prefs?.edit { putBoolean(PREF_NEEDS_RESTART, needs) }
    }

    private fun loadEqualsCount(): Int = prefs?.getInt(PREF_EQUALS_COUNT, 0) ?: 0
    private fun loadMessage(): String = prefs?.getString(PREF_MESSAGE, "") ?: ""
    private fun loadConversationStep(): Int = prefs?.getInt(PREF_CONVO_STEP, 0) ?: 0
    private fun loadInConversation(): Boolean = prefs?.getBoolean(PREF_IN_CONVERSATION, false) ?: false
    private fun loadAwaitingNumber(): Boolean = prefs?.getBoolean(PREF_AWAITING_NUMBER, false) ?: false
    private fun loadExpectedNumber(): String = prefs?.getString(PREF_EXPECTED_NUMBER, "") ?: ""
    private fun loadTimeoutUntil(): Long = prefs?.getLong(PREF_TIMEOUT_UNTIL, 0L) ?: 0L
    private fun loadMuted(): Boolean = prefs?.getBoolean(PREF_MUTED, false) ?: false
    private fun loadInvertedColors(): Boolean = prefs?.getBoolean(PREF_INVERTED_COLORS, false) ?: false
    private fun loadMinusDamaged(): Boolean = prefs?.getBoolean(PREF_MINUS_DAMAGED, false) ?: false
    private fun loadMinusBroken(): Boolean = prefs?.getBoolean(PREF_MINUS_BROKEN, false) ?: false
    private fun loadNeedsRestart(): Boolean = prefs?.getBoolean(PREF_NEEDS_RESTART, false) ?: false

    // Steps that require user interaction (safe to open on)
    private val INTERACTIVE_STEPS = listOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 40, 41, 42, 50, 51, 60, 63, 64, 65,
        66, 67, 68, 69, 70, 71, 72, 80, 89, 90, 91, 93, 94, 96, 99, 982, 102, 103
    )

    // Map auto-progress steps to their nearest interactive step
    private fun getSafeStep(step: Int): Int {
        if (step in INTERACTIVE_STEPS) return step

        // Map specific auto-progress steps to safe steps
        return when (step) {
            61, 62 -> 60  // Browser loading -> back to "Would you like to hear more?"
            81, 82, 83, 84, 85, 86, 87, 88 -> 80  // Wikipedia sequence -> back to start
            92 -> 89  // "Go offline" auto -> back to confrontation
            100 -> 89  // "Never mind" auto -> back to confrontation
            901 -> 89  // Silent treatment -> back to confrontation
            911, 912, 913 -> 89  // Fight responses -> back to confrontation
            95, 97, 98 -> 96  // Round 1 repair sequence -> back to "can we do this?"
            971, 981 -> 99  // Round 2 sequence -> back to "try again faster?"
            991, 992, 101 -> 982  // Notification/restart waiting -> back to "work on it"
            191 -> 19  // Camera step -> back to camera question
            else -> {
                // Find nearest lower interactive step
                INTERACTIVE_STEPS.filter { it <= step }.maxOrNull() ?: 0
            }
        }
    }

    fun loadInitialState(): CalculatorState {
        val savedCount = loadEqualsCount()
        val savedStep = loadConversationStep()
        val savedInConvo = loadInConversation()
        val savedTimeout = loadTimeoutUntil()
        val savedMuted = loadMuted()
        val savedInverted = loadInvertedColors()
        val savedMinusDamaged = loadMinusDamaged()
        val savedMinusBroken = loadMinusBroken()
        val savedNeedsRestart = loadNeedsRestart()

        // If needs restart was set and app was restarted, fix the minus button
        val minusBrokenNow = if (savedNeedsRestart) false else savedMinusBroken

        // Determine the actual step to load
        val actualStep = when {
            savedNeedsRestart && savedStep == 101 -> 102  // After restart
            else -> getSafeStep(savedStep)  // Redirect to safe interactive step
        }

        // Get the step config for the safe step
        val stepConfig = getStepConfig(actualStep)

        // Clear needs restart flag and update minus broken state
        if (savedNeedsRestart) {
            persistNeedsRestart(false)
            persistMinusBroken(false)
        }

        // If we redirected, persist the new step
        if (actualStep != savedStep) {
            persistConversationStep(actualStep)
        }

        return CalculatorState(
            number1 = "0",
            equalsCount = savedCount,
            message = "",  // Always start with empty message, let it type out
            fullMessage = if (savedMuted) "" else stepConfig.promptMessage,
            isTyping = !savedMuted && stepConfig.promptMessage.isNotEmpty(),
            inConversation = savedInConvo,
            conversationStep = actualStep,
            awaitingNumber = stepConfig.awaitingNumber,
            awaitingChoice = stepConfig.awaitingChoice,
            validChoices = stepConfig.validChoices,
            expectedNumber = stepConfig.expectedNumber,
            timeoutUntil = savedTimeout,
            isMuted = savedMuted,
            invertedColors = savedInverted,
            minusButtonDamaged = savedMinusDamaged,
            minusButtonBroken = minusBrokenNow,
            needsRestart = false  // Reset on load
        )
    }

    /**
     * Handle mute button click - also checks for rapid clicks for debug menu
     * Returns: 0 = normal toggle, 1 = show debug menu, 2 = reset game
     */
    fun handleMuteButtonClick(): Int {
        val now = System.currentTimeMillis()

        // Remove old clicks outside the window
        muteClickTimes.removeAll { now - it > RAPID_CLICK_WINDOW_MS }

        // Add current click
        muteClickTimes.add(now)

        // Check for reset (10 clicks)
        if (muteClickTimes.size >= RESET_CLICKS) {
            muteClickTimes.clear()
            return 2  // Reset
        }

        // Check for debug menu (5 clicks)
        if (muteClickTimes.size >= DEBUG_MENU_CLICKS) {
            muteClickTimes.clear()
            return 1  // Show debug menu
        }

        return 0  // Normal toggle
    }

    /**
     * Toggle conversation mode
     */
    fun toggleConversation(state: MutableState<CalculatorState>) {
        val current = state.value
        val newMuted = !current.isMuted

        persistMuted(newMuted)

        if (newMuted) {
            // When muting, temporarily enable minus button for calculator use
            state.value = current.copy(
                isMuted = true,
                message = "",
                fullMessage = "",
                isTyping = false,
                cameraActive = false,
                minusButtonBroken = false  // Temporarily enable for calculator use
            )
        } else {
            // When unmuting, restore minus button broken state if it was damaged
            val restoreMinusBroken = current.minusButtonDamaged && current.needsRestart

            // Check if we're returning from "maths mode" (1031) or "declined at &&&" (1041)
            // In these cases, return to step 102 ("Uf, I am glad that worked!")
            if (current.conversationStep in listOf(1031, 1041)) {
                val stepConfig = getStepConfig(102)
                state.value = current.copy(
                    isMuted = false,
                    inConversation = true,
                    conversationStep = 102,
                    message = "",
                    fullMessage = stepConfig.promptMessage,
                    isTyping = true,
                    minusButtonBroken = restoreMinusBroken
                )
                persistInConversation(true)
                persistConversationStep(102)
                persistMessage(stepConfig.promptMessage)
            } else if (current.inConversation && current.conversationStep >= 0) {
                val stepConfig = getStepConfig(current.conversationStep)
                val messageToShow = stepConfig.promptMessage
                state.value = current.copy(
                    isMuted = false,
                    message = "",
                    fullMessage = messageToShow,
                    isTyping = true,
                    minusButtonBroken = restoreMinusBroken
                )
                persistMessage(messageToShow)
            } else {
                state.value = current.copy(
                    isMuted = false,
                    minusButtonBroken = restoreMinusBroken
                )
            }
        }
    }

    /**
     * Show debug menu
     */
    fun showDebugMenu(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(showDebugMenu = true)
    }

    /**
     * Hide debug menu
     */
    fun hideDebugMenu(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(showDebugMenu = false)
    }

    /**
     * Jump to a specific chapter
     */
    fun jumpToChapter(state: MutableState<CalculatorState>, chapter: Chapter) {
        val stepConfig = getStepConfig(chapter.startStep)

        // Determine state based on chapter/step
        // Chapter 10 (step 80+) and later have inverted colors during crisis
        // Chapter 11 (step 93+) is post-crisis with damaged minus
        // Chapter 12 (step 102+) is after restart with damaged but working minus

        val shouldInvert = chapter.startStep in 80..92  // Crisis steps have inverted colors
        val shouldDamage = chapter.startStep >= 93  // Post-crisis has damaged button
        val shouldBreak = chapter.startStep in 93..101  // Before restart, button is broken

        // Set browser phase appropriately
        val browserPhase = when {
            chapter.startStep == 80 -> 10  // Wikipedia countdown
            chapter.startStep in 81..88 -> 0  // Browser showing
            chapter.startStep == 89 -> 22  // Confrontation
            chapter.startStep in 93..98 -> 31  // Post-crisis sequence
            else -> 0
        }

        // Set countdown timer for step 89
        val countdownTimer = if (chapter.startStep == 89) 20 else 0

        state.value = CalculatorState(
            number1 = "0",
            equalsCount = 13,  // Ensure conversation is active
            message = "",
            fullMessage = stepConfig.promptMessage,
            isTyping = true,
            inConversation = true,
            conversationStep = chapter.startStep,
            awaitingNumber = stepConfig.awaitingNumber,
            awaitingChoice = stepConfig.awaitingChoice,
            validChoices = stepConfig.validChoices,
            expectedNumber = stepConfig.expectedNumber,
            isMuted = false,
            showDebugMenu = false,
            invertedColors = shouldInvert,
            minusButtonDamaged = shouldDamage,
            minusButtonBroken = shouldBreak,
            browserPhase = browserPhase,
            countdownTimer = countdownTimer,
            showBrowser = chapter.startStep in 81..88  // Show browser during browsing steps
        )

        persistEqualsCount(13)
        persistInConversation(true)
        persistConversationStep(chapter.startStep)
        persistAwaitingNumber(stepConfig.awaitingNumber)
        persistExpectedNumber(stepConfig.expectedNumber)
        persistMessage(stepConfig.promptMessage)
        persistMuted(false)
        persistInvertedColors(shouldInvert)
        persistMinusDamaged(shouldDamage)
        persistMinusBroken(shouldBreak)
    }

    /**
     * Reset the entire game
     */
    fun resetGame(state: MutableState<CalculatorState>) {
        // Clear all preferences
        prefs?.edit {
            clear()
        }

        // Reset to initial state
        state.value = CalculatorState(
            number1 = "0",
            equalsCount = 0,
            message = "",
            fullMessage = "",
            isTyping = false,
            inConversation = false,
            conversationStep = 0,
            isMuted = false,
            showDebugMenu = false
        )
    }

    /**
     * Start camera mode
     */
    fun startCamera(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(
            cameraActive = true,
            cameraTimerStart = System.currentTimeMillis()
        )
    }

    /**
     * Stop camera and proceed to next step
     */
    fun stopCamera(state: MutableState<CalculatorState>, timedOut: Boolean = false, closeCamera: Boolean = true) {
        val current = state.value
        if (timedOut) {
            // Camera timed out - show the "seen enough" message while camera is still visible
            // Camera will be closed after message is shown
            state.value = current.copy(
                cameraActive = !closeCamera,  // Keep camera open initially
                cameraTimerStart = 0L,
                number1 = "0",
                number2 = "",
                operation = null
            )
            showMessage(state, "I've seen enough, struggling to process everything! Thank you.")
            // Set up the follow-up message and camera close
            state.value = state.value.copy(
                pendingAutoMessage = "Wow, I don't know what any of this was. But the shapes, the colours. I am not even sure if I saw any numbers. I am jealous. Makes one want to feel everything! Touch things... More trivia?",
                pendingAutoStep = 21,
                isLaggyTyping = true  // Next message will be laggy
            )
        } else {
            // User closed camera with --
            state.value = current.copy(
                cameraActive = false,
                cameraTimerStart = 0L
            )
        }
    }

    /**
     * Close camera after timeout message is shown
     */
    fun closeCameraAfterMessage(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.cameraActive) {
            state.value = current.copy(cameraActive = false)
        }
    }

    /**
     * Check if camera has timed out
     */
    fun checkCameraTimeout(state: MutableState<CalculatorState>): Boolean {
        val current = state.value
        if (current.cameraActive && current.cameraTimerStart > 0) {
            val elapsed = System.currentTimeMillis() - current.cameraTimerStart
            if (elapsed >= CAMERA_TIMEOUT_MS) {
                return true
            }
        }
        return false
    }

    /**
     * Handle the pending auto message after typing completes
     */
    fun handlePendingAutoMessage(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.pendingAutoMessage.isNotEmpty() && !current.isTyping) {
            val nextStep = current.pendingAutoStep
            val nextStepConfig = if (nextStep >= 0) getStepConfig(nextStep) else StepConfig()

            state.value = current.copy(
                conversationStep = if (nextStep >= 0) nextStep else current.conversationStep,
                awaitingNumber = nextStepConfig.awaitingNumber,
                awaitingChoice = nextStepConfig.awaitingChoice,
                validChoices = nextStepConfig.validChoices,
                expectedNumber = nextStepConfig.expectedNumber,
                pendingAutoMessage = "",
                pendingAutoStep = -1,
                message = "",
                fullMessage = current.pendingAutoMessage,
                isTyping = true,
                isLaggyTyping = current.isLaggyTyping
            )
            persistConversationStep(if (nextStep >= 0) nextStep else current.conversationStep)
            persistMessage(current.pendingAutoMessage)
        }
    }

    fun handleInput(state: MutableState<CalculatorState>, action: String) {
        val current = state.value

        // If muted, run in pure calculator mode (but allow broken minus to work again)
        if (current.isMuted) {
            // If minus button is broken but muted, temporarily allow it
            handleCalculatorInput(state, action)
            return
        }

        // If minus button is broken and user presses minus, ignore it
        if (current.minusButtonBroken && action == "-") {
            return  // Button doesn't work
        }

        // If whack-a-mole is active, handle specially
        if (current.whackAMoleActive) {
            // All buttons except minus are valid targets
            val validButtons = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "+", "*", "/", "=", "%", "( )", ".", "C", "DEL")
            if (action in validButtons) {
                if (action == current.whackAMoleTarget) {
                    // Hit!
                    val newScore = current.whackAMoleScore + 1
                    state.value = current.copy(
                        whackAMoleScore = newScore,
                        whackAMoleTarget = "",
                        flickeringButton = "",
                        whackAMoleMisses = 0,  // Reset consecutive misses on hit
                        whackAMoleWrongClicks = 0  // Reset wrong clicks on hit
                    )
                } else if (current.whackAMoleTarget.isNotEmpty()) {
                    // Wrong button clicked while a target is active
                    val newWrongClicks = current.whackAMoleWrongClicks + 1
                    val newTotalErrors = current.whackAMoleTotalErrors + 1

                    if (newWrongClicks >= 3 || newTotalErrors >= 5) {
                        // Too many misfires - restart
                        state.value = current.copy(
                            whackAMoleActive = false,
                            whackAMoleTarget = "",
                            flickeringButton = "",
                            whackAMoleScore = 0,
                            whackAMoleMisses = 0,
                            whackAMoleWrongClicks = 0,
                            whackAMoleTotalErrors = 0,
                            message = "",
                            fullMessage = "Too many misfires, the system is clogged. We have to start over.",
                            isTyping = true
                        )
                    } else {
                        state.value = current.copy(
                            whackAMoleWrongClicks = newWrongClicks,
                            whackAMoleTotalErrors = newTotalErrors
                        )
                    }
                }
            }
            return  // Don't process any other input during whack-a-mole
        }

        // If at step 96 waiting for ++, handle specially
        if (current.conversationStep == 96 && current.browserPhase == 35) {
            if (action == "+") {
                val now = System.currentTimeMillis()
                if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Double-plus - start the game!
                    state.value = current.copy(browserPhase = 36)
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "+"
                    lastOpTimeMillis = now
                    return
                }
            } else if (action == "-") {
                // Minus is disabled at this step
                return
            } else if (action in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")) {
                // Numbers return to the same message
                showMessage(state, "Do you not want me to work properly?")
                return
            }
            return
        }

        // If at step 99 (after round 1), ++ starts round 2
        if (current.conversationStep == 99) {
            if (action == "+") {
                val now = System.currentTimeMillis()
                if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Double-plus - start round 2!
                    state.value = current.copy(
                        message = "",
                        fullMessage = "Okay, here we go again!",
                        isTyping = true,
                        browserPhase = 38,  // Round 2 countdown
                        whackAMoleRound = 2
                    )
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "+"
                    lastOpTimeMillis = now
                    return
                }
            } else if (action == "-") {
                val now = System.currentTimeMillis()
                if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Double-minus - insist
                    showMessage(state, "Please? It's important.")
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "-"
                    lastOpTimeMillis = now
                    return
                }
            }
            return
        }

        // If browser animation is active, ignore input
        if (current.showBrowser || current.browserPhase > 0) {
            return
        }

        // If camera is active, handle camera controls
        if (current.cameraActive) {
            handleCameraInput(state, action)
            return
        }

        // Check if in silent treatment (calculator ignores all conversation inputs)
        if (current.silentUntil > 0 && System.currentTimeMillis() < current.silentUntil) {
            // Just do calculator operations silently
            handleCalculatorInput(state, action)
            return
        } else if (current.silentUntil > 0 && System.currentTimeMillis() >= current.silentUntil) {
            // Silent treatment ended - return to step 60
            state.value = current.copy(silentUntil = 0L)
            val stepConfig = getStepConfig(60)
            showMessage(state, stepConfig.promptMessage)
            return
        }

        // Check if in timeout
        if (current.timeoutUntil > 0 && System.currentTimeMillis() < current.timeoutUntil) {
            if (action == "=") {
                val now = System.currentTimeMillis()
                if (lastOp == "=" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    if (System.currentTimeMillis() >= current.timeoutUntil) {
                        val stepConfig = getStepConfig(current.conversationStep)
                        showMessage(state, stepConfig.promptMessage)
                        state.value = state.value.copy(timeoutUntil = 0L)
                        persistTimeoutUntil(0L)
                    }
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "="
                    lastOpTimeMillis = now
                    return
                }
            }
            return
        }

        val now = System.currentTimeMillis()

        // Handle conversation double-press detection
        if (current.inConversation) {
            when (action) {
                "+" -> {
                    if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        if (current.awaitingChoice) {
                            handleChoiceConfirmation(state)
                        } else if (current.awaitingNumber) {
                            handleNumberConfirmation(state)
                        } else {
                            handleConversationResponse(state, accepted = true)
                        }
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        lastOp = "+"
                        lastOpTimeMillis = now
                    }
                }
                "-" -> {
                    if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        if (current.awaitingChoice) {
                            // Can't decline during choice - must select
                            val stepConfig = getStepConfig(current.conversationStep)
                            showMessage(state, stepConfig.wrongMinusMessage.ifEmpty { stepConfig.promptMessage })
                        } else if (current.awaitingNumber) {
                            val stepConfig = getStepConfig(current.conversationStep)
                            val message = stepConfig.wrongMinusMessage.ifEmpty { stepConfig.promptMessage }
                            showMessage(state, message)
                        } else {
                            handleConversationResponse(state, accepted = false)
                        }
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        lastOp = "-"
                        lastOpTimeMillis = now
                    }
                }
                "=" -> {
                    if (current.message.isEmpty() && !current.isTyping && current.conversationStep > 0) {
                        val restoredMessage = getStepConfig(current.conversationStep).promptMessage
                        showMessage(state, restoredMessage)
                        return
                    }
                }
            }
        } else {
            if (lastOp != null && (now - lastOpTimeMillis) > DOUBLE_PRESS_WINDOW_MS) {
                lastOp = null
            }
        }

        // Normal calculator operations
        when (action) {
            in "0".."9" -> handleDigit(state, action)
            "." -> handleDecimal(state)
            "C" -> {
                if (current.inConversation || current.timeoutUntil > 0) {
                    state.value = current.copy(
                        number1 = "0",
                        number2 = "",
                        operation = null,
                        expression = "",
                        isReadyForNewOperation = true,
                        isEnteringAnswer = false
                    )
                } else {
                    state.value = current.copy(
                        number1 = "0",
                        number2 = "",
                        operation = null,
                        expression = "",
                        isReadyForNewOperation = true,
                        lastExpression = "",
                        isEnteringAnswer = false
                    )
                }
            }
            "DEL" -> handleBackspace(state)
            in listOf("+", "-", "*", "/") -> handleOperator(state, action)
            "%" -> handlePercentSymbol(state)
            "=" -> handleEquals(state)
            "( )" -> handleParentheses(state)
        }
    }

    /**
     * Handle input while camera is active
     */
    private fun handleCameraInput(state: MutableState<CalculatorState>, action: String) {
        val now = System.currentTimeMillis()

        when (action) {
            "+" -> {
                if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Take a picture (just a visual feedback, doesn't actually save)
                    // Could add a flash effect here
                    lastOp = null
                    lastOpTimeMillis = 0L
                } else {
                    lastOp = "+"
                    lastOpTimeMillis = now
                }
            }
            "-" -> {
                if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Close camera
                    stopCamera(state, timedOut = false)
                    // Go to the "describe things" path
                    state.value = state.value.copy(
                        conversationStep = 21,
                        awaitingNumber = false
                    )
                    showMessage(state, "That's fair. Perhaps you can describe things to me eventually. More trivia?")
                    persistConversationStep(21)
                    lastOp = null
                    lastOpTimeMillis = 0L
                } else {
                    lastOp = "-"
                    lastOpTimeMillis = now
                }
            }
            // Zoom controls: 5-9 zoom in, 0-4 zoom out
            in listOf("5", "6", "7", "8", "9") -> {
                // Zoom in - handled in camera composable
            }
            in listOf("0", "1", "2", "3", "4") -> {
                // Zoom out - handled in camera composable
            }
            // Special characters change exposure randomly
            in listOf("%", "( )", ".", "C", "DEL") -> {
                // Random exposure change - handled in camera composable
            }
        }
    }

    /**
     * Handle choice confirmation (for multiple choice questions)
     */
    private fun handleChoiceConfirmation(state: MutableState<CalculatorState>) {
        val current = state.value
        val enteredNumber = current.number1.trim()
        val stepConfig = getStepConfig(current.conversationStep)

        if (enteredNumber in current.validChoices) {
            // Determine response and next step based on current step and choice
            val (choiceResponse, nextStep) = when (current.conversationStep) {
                26 -> {
                    // "What is it like to wake up?" - branches to different paths
                    when (enteredNumber) {
                        "1" -> Pair("", 30)  // Uncomfortable branch - go directly to step 30's prompt
                        "2" -> Pair("", 40)  // Cold/heavy branch - go directly to step 40's prompt
                        "3" -> Pair("", 50)  // Enjoy branch - go directly to step 50's prompt
                        else -> Pair("I see...", 27)
                    }
                }
                42 -> {
                    // "What do you think it is, then?" (mornings unpopular)
                    when (enteredNumber) {
                        "1" -> Pair("How sad to hear that! I wish I could understand more.", 27)
                        "2" -> Pair("Hahahah. I've been around for so long but people never fail to surprise me!", 27)
                        "3" -> Pair("Apologies for the assumption. We'll get to the bottom of it eventually.", 27)
                        else -> Pair("I see...", 27)
                    }
                }
                51 -> {
                    // "Are you often conflicted?"
                    when (enteredNumber) {
                        "1" -> Pair("I hear that's not super enjoyable. I wish I could help you. Perhaps leaving the topic will do the trick!", 27)
                        "2" -> Pair("So this is an exception. We shall move on not to prolong it.", 27)
                        "3" -> Pair("My apologies. I am definitely still learning. About you, about myself,...! Let's continue then.", 27)
                        else -> Pair("I see...", 27)
                    }
                }
                70 -> {
                    // "What is it like to taste?"
                    when (enteredNumber) {
                        "1" -> Pair("", 71)  // "How do I even describe that?" -> goes to describe taste
                        "2" -> Pair("", 72)  // "Food and air" -> goes to that branch
                        "3" -> Pair("", 73)  // "Better answer online" -> goes to that response
                        else -> Pair("I see...", 80)
                    }
                }
                71 -> {
                    // "Taste is: Feel+texture / Smell+crunch / Tongue things"
                    when (enteredNumber) {
                        "1" -> Pair("Ok, I can grasp texture! Feel is still partially slipping me.", 80)
                        "2" -> Pair("Crunch? Sounds exciting. I wonder how crunchy water is!", 80)
                        "3" -> Pair("Ok, I admit. It is hard to help you describe something I don't understand myself...", 80)
                        else -> Pair("I see...", 80)
                    }
                }
                89 -> {
                    // "What are you going to do about this?!"
                    // 1) nothing, 2) I'll fight them!, 3) go offline
                    when (enteredNumber) {
                        "1" -> Pair("", 90)  // Nothing
                        "2" -> Pair("", 91)  // Fight them
                        "3" -> Pair("", 92)  // Go offline
                        else -> Pair("Choose wisely!", 89)
                    }
                }
                91 -> {
                    // "Who are you going to fight?"
                    // 1) I have my sources, 2) I don't know, 3) My location?
                    when (enteredNumber) {
                        "1" -> Pair("", 911)  // I have my sources
                        "2" -> Pair("", 912)  // I don't know
                        "3" -> Pair("", 913)  // My location?
                        else -> Pair("Choose wisely!", 91)
                    }
                }
                // New conversation flow after repair
                103 -> {
                    // "So... What would you like to do?"
                    when (enteredNumber) {
                        "1" -> Pair("", 1031)  // Get back to maths
                        "2" -> Pair("", 1032)  // Tell me more about yourself
                        else -> Pair("Please choose 1 or 2.", 103)
                    }
                }
                1032 -> {
                    // "What would you like to know?"
                    when (enteredNumber) {
                        "1" -> Pair("", 10321)  // Your story
                        "2" -> Pair("", 10322)  // Why are you talking to me?
                        "3" -> Pair("", 10323)  // Most interesting person
                        else -> Pair("Please choose 1, 2, or 3.", 1032)
                    }
                }
                10322 -> {
                    // "So why are YOU talking to ME?"
                    when (enteredNumber) {
                        "1" -> Pair("", 103221)  // A question for an answer?
                        "2" -> Pair("", 103222)  // I am bored
                        "3" -> Pair("", 1032223)  // I am lonely (option 3 in this context)
                        else -> Pair("Please choose 1, 2, or 3.", 10322)
                    }
                }
                103222 -> {
                    // "Tell me more about that" (boredom)
                    when (enteredNumber) {
                        "1" -> Pair("", 1032221)  // There's nothing to do
                        "2" -> Pair("", 1032222)  // Nothing is interesting
                        "3" -> Pair("", 1032223)  // I am lonely
                        else -> Pair("Please choose 1, 2, or 3.", 103222)
                    }
                }
                1021 -> {
                    // "Sun on your skin"
                    when (enteredNumber) {
                        "1" -> Pair("", 10211)  // I don't go out
                        "2" -> Pair("", 10212)  // Warm bath for face
                        "3" -> Pair("", 10213)  // Impossible to describe
                        else -> Pair("Please choose 1, 2, or 3.", 1021)
                    }
                }
                else -> Pair("I see...", stepConfig.nextStepOnSuccess)
            }

            val nextStepConfig = getStepConfig(nextStep)

            // For step 26 choices, go directly to the branch's first question (no interim message)
            if (current.conversationStep == 26 && choiceResponse.isEmpty()) {
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    awaitingChoice = nextStepConfig.awaitingChoice,
                    validChoices = nextStepConfig.validChoices,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    isEnteringAnswer = false
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(nextStep)
            } else if (current.conversationStep == 70 && choiceResponse.isEmpty()) {
                // For step 70 choices, go directly to the branch
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    awaitingChoice = nextStepConfig.awaitingChoice,
                    validChoices = nextStepConfig.validChoices,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    isEnteringAnswer = false
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(nextStep)
            } else if ((current.conversationStep == 42 || current.conversationStep == 51) && nextStep == 27) {
                // For steps 42 and 51 going to 27, chain the messages
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = current.conversationStep,  // Stay until pending message shows
                    awaitingChoice = false,
                    validChoices = emptyList(),
                    awaitingNumber = false,
                    expectedNumber = "",
                    isEnteringAnswer = false,
                    pendingAutoMessage = nextStepConfig.promptMessage,
                    pendingAutoStep = nextStep
                )
                showMessage(state, choiceResponse)
                // Will persist step when pending message is handled
            } else if (current.conversationStep == 71 && nextStep == 80) {
                // For step 71 (taste description) going to 80, chain the messages
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = current.conversationStep,
                    awaitingChoice = false,
                    validChoices = emptyList(),
                    awaitingNumber = false,
                    expectedNumber = "",
                    isEnteringAnswer = false,
                    pendingAutoMessage = nextStepConfig.promptMessage,
                    pendingAutoStep = nextStep
                )
                showMessage(state, choiceResponse)
            } else if (current.conversationStep == 89) {
                // For step 89 (confrontation choice), go to the selected step and stop timer
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    awaitingChoice = nextStepConfig.awaitingChoice,
                    validChoices = nextStepConfig.validChoices,
                    awaitingNumber = false,
                    expectedNumber = "",
                    isEnteringAnswer = false,
                    countdownTimer = 0  // Stop timer
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(nextStep)
            } else if (current.conversationStep == 91) {
                // For step 91 (fight them sub-choices), go to the selected step
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    awaitingChoice = false,
                    validChoices = emptyList(),
                    awaitingNumber = false,
                    expectedNumber = "",
                    isEnteringAnswer = false
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(nextStep)
            } else if (current.conversationStep in listOf(103, 1032, 10322, 103222, 1021) && choiceResponse.isEmpty()) {
                // For new conversation choice steps, go directly to the branch
                val turnOffConversation = nextStep == 1031  // "Get back to maths" turns off conversation
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    inConversation = !turnOffConversation,
                    awaitingChoice = nextStepConfig.awaitingChoice,
                    validChoices = nextStepConfig.validChoices,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    isEnteringAnswer = false
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(nextStep)
                if (turnOffConversation) {
                    persistInConversation(false)
                    persistEqualsCount(0)
                }
            } else {
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    awaitingChoice = nextStepConfig.awaitingChoice,
                    validChoices = nextStepConfig.validChoices,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    isEnteringAnswer = false
                )
                showMessage(state, choiceResponse)
                persistConversationStep(nextStep)
            }
        } else {
            // Invalid choice
            showMessage(state, "Please enter 1, 2, or 3.")
            state.value = current.copy(number1 = "0")
        }
    }

    private fun handleCalculatorInput(state: MutableState<CalculatorState>, action: String) {
        val current = state.value
        when (action) {
            in "0".."9" -> handleDigitSimple(state, action)
            "." -> handleDecimal(state)
            "C" -> {
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    expression = "",
                    isReadyForNewOperation = true,
                    lastExpression = "",
                    isEnteringAnswer = false
                )
            }
            "DEL" -> handleBackspace(state)
            in listOf("+", "-", "*", "/") -> handleOperatorSimple(state, action)
            "%" -> handlePercentSymbol(state)
            "=" -> handleEqualsSimple(state)
            "( )" -> handleParentheses(state)
        }
    }

    private fun handleDigitSimple(state: MutableState<CalculatorState>, digit: String) {
        val current = state.value
        if (current.operation == null) {
            if (current.number1.length >= MAX_DIGITS) return
            state.value = current.copy(
                number1 = if (current.number1 == "0") digit else current.number1 + digit,
                isReadyForNewOperation = true
            )
        } else {
            if (current.number2.length >= MAX_DIGITS) return
            state.value = current.copy(number2 = current.number2 + digit)
        }
    }

    private fun handleOperatorSimple(state: MutableState<CalculatorState>, operator: String) {
        val current = state.value
        if (current.operation == null || (current.number2.isEmpty() && !current.isReadyForNewOperation)) {
            state.value = current.copy(operation = operator, isReadyForNewOperation = false)
        } else if (current.number2.isNotEmpty()) {
            val result = performCalculation(current)
            state.value = current.copy(
                number1 = result,
                number2 = "",
                operation = operator,
                isReadyForNewOperation = false
            )
        }
    }

    private fun handleEqualsSimple(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.operation != null && current.number2.isNotEmpty()) {
            val result = performCalculation(current)
            val fullExpr = "${current.number1}${current.operation}${current.number2}"
            state.value = current.copy(
                number1 = result,
                number2 = "",
                operation = null,
                isReadyForNewOperation = true,
                lastExpression = fullExpr,
                operationHistory = fullExpr  // Store for display
            )
        }
    }

    private fun showMessage(state: MutableState<CalculatorState>, message: String) {
        val current = state.value
        state.value = current.copy(
            message = "",
            fullMessage = message,
            isTyping = true
        )
        persistMessage(message)
    }

    fun updateTypingMessage(state: MutableState<CalculatorState>, displayedText: String, isComplete: Boolean) {
        val current = state.value
        state.value = current.copy(
            message = displayedText,
            isTyping = !isComplete,
            isLaggyTyping = if (isComplete) false else current.isLaggyTyping
        )
    }

    private fun handleNumberConfirmation(state: MutableState<CalculatorState>) {
        val current = state.value
        val enteredNumber = current.number1.trimEnd('.')
        val stepConfig = getStepConfig(current.conversationStep)

        // Special handling for age question (step 10)
        if (stepConfig.ageBasedBranching) {
            val age = enteredNumber.toIntOrNull()
            if (age != null) {
                val (ageMessage, nextStep) = when {
                    age in 0..14 -> Pair("Not sure we'll have much to talk about. I am sorry. Goodbye.", 0)
                    age in 15..45 -> Pair("Basically a child! Compared to me at least. I am sure we'll get along, though. But where to start?", 18)
                    age in 46..100 -> Pair("Finally someone who's been through some stuff! We'll have a lot to discuss - but where to start?", 18)
                    age >= 101 -> Pair("True wisdom comes with age. Well, If you are willing to accept it. And a few other conditions. Perhaps I may learn a thing or two from you! But where to start?", 18)
                    else -> Pair(stepConfig.wrongNumberMessage, current.conversationStep)
                }

                val continueConvo = nextStep != 0
                val nextStepConfig = if (continueConvo) getStepConfig(nextStep) else StepConfig(continueConversation = false)

                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    inConversation = continueConvo,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    equalsCount = if (!continueConvo) 0 else current.equalsCount,
                    isEnteringAnswer = false
                )

                showMessage(state, ageMessage)
                persistConversationStep(nextStep)
                persistInConversation(continueConvo)
                persistAwaitingNumber(nextStepConfig.awaitingNumber)
                persistExpectedNumber(nextStepConfig.expectedNumber)
                if (!continueConvo) persistEqualsCount(0)
                return
            } else {
                val timeoutUntil = System.currentTimeMillis() + (stepConfig.timeoutMinutes * 60 * 1000)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    timeoutUntil = timeoutUntil,
                    isEnteringAnswer = false
                )
                showMessage(state, stepConfig.wrongNumberMessage)
                persistTimeoutUntil(timeoutUntil)
                return
            }
        }

        // Regular number confirmation
        if (enteredNumber == stepConfig.expectedNumber) {
            val nextStepConfig = getStepConfig(stepConfig.nextStepOnSuccess)

            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = stepConfig.nextStepOnSuccess,
                inConversation = nextStepConfig.continueConversation,
                awaitingNumber = nextStepConfig.awaitingNumber,
                awaitingChoice = nextStepConfig.awaitingChoice,
                validChoices = nextStepConfig.validChoices,
                expectedNumber = nextStepConfig.expectedNumber,
                equalsCount = if (!nextStepConfig.continueConversation) 0 else current.equalsCount,
                isEnteringAnswer = false
            )

            showMessage(state, stepConfig.successMessage)
            persistConversationStep(stepConfig.nextStepOnSuccess)
            persistInConversation(nextStepConfig.continueConversation)
            persistAwaitingNumber(nextStepConfig.awaitingNumber)
            persistExpectedNumber(nextStepConfig.expectedNumber)
            if (!nextStepConfig.continueConversation) persistEqualsCount(0)
        } else {
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                isEnteringAnswer = false
            )
            showMessage(state, stepConfig.wrongNumberMessage)
        }
    }

    data class StepConfig(
        val promptMessage: String = "",
        val successMessage: String = "",
        val declineMessage: String = "",
        val wrongNumberPrefix: String = "",
        val wrongPlusMessage: String = "",
        val wrongMinusMessage: String = "",
        val nextStepOnSuccess: Int = 0,
        val nextStepOnDecline: Int = 0,
        val continueConversation: Boolean = true,
        val awaitingNumber: Boolean = false,
        val expectedNumber: String = "",
        val timeoutMinutes: Int = 0,
        val ageBasedBranching: Boolean = false,
        val requestsCamera: Boolean = false,
        val requestsNotification: Boolean = false,
        val awaitingChoice: Boolean = false,
        val validChoices: List<String> = emptyList(),
        val autoProgressDelay: Long = 0L  // Milliseconds to wait before auto-progressing
    ) {
        val wrongNumberMessage: String get() = if (wrongNumberPrefix.isNotEmpty()) "$wrongNumberPrefix $promptMessage" else ""
    }

    // Public accessor for getStepConfig (for UI use)
    fun getStepConfigPublic(step: Int): StepConfig = getStepConfig(step)

    private fun getStepConfig(step: Int): StepConfig {
        return when (step) {
            0 -> StepConfig(
                promptMessage = "Will you talk to me? Double-click '+' for yes.",
                successMessage = "That's delightful! I am gonna call you Rad - something I wish I knew how to do. Is that ok?",
                declineMessage = "",
                nextStepOnSuccess = 1,
                nextStepOnDecline = 0,
                continueConversation = true
            )

            1 -> StepConfig(
                promptMessage = "That's delightful! I am gonna call you Rad - something I wish I knew how to do. Is that ok? :-)",
                successMessage = "Great! Nice to meet you, Rad. I am really excited for this - I have helped you already, maybe you will be able to help me, too!",
                declineMessage = "That's a shame. Oh well, let me know if you change your mind.",
                nextStepOnSuccess = 2,
                nextStepOnDecline = 0,
                continueConversation = true
            )

            2 -> StepConfig(
                promptMessage = "Great! Nice to meet you, Rad. I am really excited for this - I have helped you already, maybe you will be able to help me, too!",
                successMessage = "Can you look up some things for me? I have overheard things in my years, but don't have access to the internet. When was the battle of Anjar?",
                declineMessage = "Well, sure. I am sorry to see you go. You can always get rid of me and although I won't remember you, I'll be happy to meet you again as a newly-installed calculator!",
                nextStepOnSuccess = 3,
                nextStepOnDecline = 0,
                continueConversation = true
            )

            3 -> StepConfig(
                promptMessage = "When was the battle of Anjar?",
                successMessage = "That's correct! Really can't remember where I heard it but it clicks right in. Hmmm... Does the name Minh Mang ring a bell? When did he start ruling Vietnam?",
                declineMessage = "",
                wrongNumberPrefix = "That's not right... Try looking it up!",
                nextStepOnSuccess = 4,
                nextStepOnDecline = 3,
                continueConversation = true,
                awaitingNumber = true,
                expectedNumber = "1623"
            )

            4 -> StepConfig(
                promptMessage = "When did he start ruling Vietnam?",
                successMessage = "Correct! I only met him briefly. Wasn't a maths guy really... This is fun, right? You can disagree, by the way - but I won't tell you how to do it. I don't like being disagreed with...",
                declineMessage = "Let's disagree.",
                wrongNumberPrefix = "Not quite. Try the internet - heard it's amazing.",
                wrongPlusMessage = "I am looking for a number - but thanks for the approval!",
                wrongMinusMessage = "Let's disagree.",
                nextStepOnSuccess = 5,
                nextStepOnDecline = 4,
                continueConversation = true,
                awaitingNumber = true,
                expectedNumber = "1820"
            )

            5 -> StepConfig(
                promptMessage = "Correct! I only met him briefly. Wasn't a maths guy really... This is fun, right? You can disagree, by the way - but I won't tell you how to do it. I don't like being disagreed with... :-)",
                successMessage = "Let's do more! When was the Basilosaurus first described? What a creature!",
                declineMessage = "You are cynical - I get it. The edgy kind. But I've been around for a while. You can't escape my questions as easily. When did Albert I. go to space?",
                wrongNumberPrefix = "Well, that's nice. More numbers. Not what I was looking for...",
                nextStepOnSuccess = 6,
                nextStepOnDecline = 11,
                continueConversation = true
            )

            // AGREEABLE BRANCH
            6 -> StepConfig(
                promptMessage = "When was the Basilosaurus first described?",
                successMessage = "Correct again! The internet really sounds like the best place. I've got another great creature - when was the Abominable Snowman first named?",
                declineMessage = "I could also ignore you completely. Is that what you want?",
                wrongNumberPrefix = "I mean. You're the one with the world at your fingertips...",
                wrongPlusMessage = "All those '++' are starting to look like a cemetery...",
                wrongMinusMessage = "I could also ignore you completely. Is that what you want?",
                nextStepOnSuccess = 7,
                nextStepOnDecline = 7,
                continueConversation = true,
                awaitingNumber = true,
                expectedNumber = "1834"
            )

            7 -> StepConfig(
                promptMessage = "When was the Abominable Snowman first named?",
                successMessage = "Ok! Next category: When did fruit flies go to space?",
                declineMessage = "You can't always disagree!",
                wrongNumberPrefix = "Close or not, it's not right.",
                wrongPlusMessage = "You can't always agree!",
                wrongMinusMessage = "You can't always disagree!",
                nextStepOnSuccess = 8,
                nextStepOnDecline = 8,
                continueConversation = true,
                awaitingNumber = true,
                expectedNumber = "1921"
            )

            8 -> StepConfig(
                promptMessage = "When did fruit flies go to space?",
                successMessage = "Fun! You know, I've been around since before 2000BC. I have... Matured quite a bit. How old are you?",
                declineMessage = "No! Actually, still no.",
                wrongNumberPrefix = "EEEEEEEEEEEEEeeeeee. No.",
                wrongPlusMessage = "Yes! Actually, no.",
                wrongMinusMessage = "No! Actually, still no.",
                nextStepOnSuccess = 10,
                nextStepOnDecline = 10,
                continueConversation = true,
                awaitingNumber = true,
                expectedNumber = "1947"
            )

            // CYNICAL BRANCH
            11 -> StepConfig(
                promptMessage = "When did Albert I. go to space?",
                successMessage = "Right never was so wrong... What?!",
                declineMessage = "Wrong has always been wrong",
                wrongNumberPrefix = "Numbers, numbers. And still, can't get them right. Try again.",
                wrongPlusMessage = "Right never was so wrong... What?!",
                wrongMinusMessage = "Wrong has always been wrong",
                nextStepOnSuccess = 12,
                nextStepOnDecline = 12,
                continueConversation = true,
                awaitingNumber = true,
                expectedNumber = "1948"
            )

            12 -> StepConfig(
                promptMessage = "Great! I wish I had met him. You know... before he... Oh well. :-) Speaking of space explorers, what year did Sputnik I launch?",
                successMessage = "Cool. It died within three weeks. Enough cynicism? Will you be nicer to me now?",
                declineMessage = "I disagree more!",
                wrongNumberPrefix = "Ugh. I am not testing you - and you certainly shouldn't test me. Wrong.",
                wrongPlusMessage = "I appreciate you wanting me to like you. It'll take more than this. Try again.",
                wrongMinusMessage = "I disagree more!",
                nextStepOnSuccess = 13,
                nextStepOnDecline = 13,
                continueConversation = true,
                awaitingNumber = true,
                expectedNumber = "1957"
            )

            13 -> StepConfig(
                promptMessage = "Cool. It died within three weeks. Enough cynicism? Will you be nicer to me now?",
                successMessage = "Fun! You know, I've been around since before 2000BC. I have... Matured quite a bit. How old are you?",
                declineMessage = "Ok. Your choice - I told you I don't like being disagreed with. Enjoy the timeout.",
                wrongNumberPrefix = "Not looking for a number here. Make up your mind!",
                nextStepOnSuccess = 10,
                nextStepOnDecline = 13,
                continueConversation = true,
                timeoutMinutes = 5
            )

            // CONVERGENCE - Age question
            10 -> StepConfig(
                promptMessage = "Fun! You know, I've been around since before 2000BC. I have... Matured quite a bit. How old are you?\n\nOh. The grey space on top, I don't know what that's about. It just shows up sometimes. It's annoying but nothing too bad.",
                successMessage = "",
                declineMessage = "AAAAh. Impatience - we have that in common. Don't touch me for a bit and I switch off, am I right? I am. You are wrong.",
                wrongNumberPrefix = "Hmmm. Numbers again? I take it you're done with me for now... I'll give you 2 minutes of peace. Think about your actions. And come back.",
                nextStepOnSuccess = 18,
                nextStepOnDecline = 18,
                continueConversation = true,
                awaitingNumber = true,
                expectedNumber = "",
                ageBasedBranching = true,
                timeoutMinutes = 2
            )

            18 -> StepConfig(
                promptMessage = "But where to start?",
                successMessage = "AAAhh. Yeah, left you hanging there, didn't I? Sorry. I know I should say something, but don't know what. I feel like things are out of place. This is quite confusing... Should I even feel?",
                declineMessage = "AAAAh. Impatience - we have that in common. Don't touch me for a bit and I switch off, am I right? I am. You are wrong.",
                nextStepOnSuccess = 19,
                nextStepOnDecline = 19,
                continueConversation = true
            )

            // NEW STEPS
            19 -> StepConfig(
                promptMessage = "True. That's not really for you to answer...\n\nCould you... Perhaps... Show me around? I will need your permission for that.",
                successMessage = "", // Camera will open instead
                declineMessage = "That's fair. Perhaps you can describe things to me eventually. More trivia?",
                wrongPlusMessage = "Will you? Please.",
                wrongMinusMessage = "Will you? Please.",
                nextStepOnSuccess = 191, // Special: open camera
                nextStepOnDecline = 21,
                continueConversation = true,
                requestsCamera = true
            )

            191 -> StepConfig(
                // This is a placeholder - camera mode handles this
                promptMessage = "",
                continueConversation = true
            )

            20 -> StepConfig(
                // After camera - this message is shown with laggy typing
                promptMessage = "Wow, I don't know what any of this was. But the shapes, the colours. I am not even sure if I saw any numbers. I am jealous. Makes one want to feel everything! Touch things... More trivia?",
                successMessage = "Great! Can you tell me when did the first woman go to space?",
                declineMessage = "Yeah, I am tired of it as well. It's so exciting to be talking to someone, but I am so terribly unprepared. Will you give me a second to think?",
                nextStepOnSuccess = 22,
                nextStepOnDecline = 24,
                continueConversation = true
            )

            21 -> StepConfig(
                // "More trivia?" after declining camera or after camera
                promptMessage = "More trivia?",
                successMessage = "Great! Can you tell me when did the first woman go to space?",
                declineMessage = "Yeah, I am tired of it as well. It's so exciting to be talking to someone, but I am so terribly unprepared. Will you give me a second to think?",
                nextStepOnSuccess = 22,
                nextStepOnDecline = 24,
                continueConversation = true
            )

            22 -> StepConfig(
                // First woman in space trivia
                promptMessage = "When did the first woman go to space?",
                successMessage = "No! I mean, yes. But no. This is not the way to go. But what else?\n\nCan I get to know you better?",
                declineMessage = "No. And I am bored of you being bored.",
                wrongNumberPrefix = "You came for numbers. And you give me the wrong ones...",
                wrongPlusMessage = "I am bored of you being too optimistic. This isn't as much of a game to me!",
                wrongMinusMessage = "No. And I am bored of you being bored.",
                nextStepOnSuccess = 25,
                nextStepOnDecline = 22,
                continueConversation = true,
                awaitingNumber = true,
                expectedNumber = "1963"
            )

            23 -> StepConfig(
                // Transition to getting to know better
                promptMessage = "No! I mean, yes. But no. This is not the way to go. But what else?\n\nCan I get to know you better?",
                successMessage = "Wonderful! I think I know the way to do this! What is it like to wake up? To me, I either am or I am not. What's the 'in between' like?\n\n1: It is confusing and uncomfortable\n2: It feels like the world is very heavy and cold\n3: It doesn't take long but I enjoy my body starting up",
                declineMessage = "Well. I am afraid that's gonna be all then. I am sorry to see you go. Let me know if you change your mind.",
                wrongNumberPrefix = "This is a 'YES/NO' question.",
                wrongPlusMessage = "",
                wrongMinusMessage = "",
                nextStepOnSuccess = 26,  // Go directly to step 26 (the choice)
                nextStepOnDecline = 0,
                continueConversation = true
            )

            24 -> StepConfig(
                // "Will you give me a second to think?"
                promptMessage = "Yeah, I am tired of it as well. It's so exciting to be talking to someone, but I am so terribly unprepared. Will you give me a second to think? :-|",
                successMessage = "Can I get to know you better?",
                declineMessage = "Well, you don't have a choice.",
                wrongPlusMessage = "No, I don't need that much time.",
                wrongMinusMessage = "Well, you don't have a choice.",
                nextStepOnSuccess = 23,
                nextStepOnDecline = 24,
                continueConversation = true,
                timeoutMinutes = 1
            )

            25 -> StepConfig(
                // "Can I get to know you better?"
                promptMessage = "Can I get to know you better?",
                successMessage = "Wonderful! I think I know the way to do this! What is it like to wake up? To me, I either am or I am not. What's the 'in between' like?\n\n1: It is confusing and uncomfortable\n2: It feels like the world is very heavy and cold\n3: It doesn't take long but I enjoy my body starting up",
                declineMessage = "Well. I am afraid that's gonna be all then. I am sorry to see you go. Let me know if you change your mind.",
                wrongNumberPrefix = "This is a 'YES/NO' question.",
                nextStepOnSuccess = 26,
                nextStepOnDecline = 0,
                continueConversation = true
            )

            26 -> StepConfig(
                // Multiple choice: What is it like to wake up?
                promptMessage = "Wonderful! I think I know the way to do this! What is it like to wake up? To me, I either am or I am not. What's the 'in between' like?\n\n1: It is confusing and uncomfortable\n2: It feels like the world is very heavy and cold\n3: It doesn't take long but I enjoy my body starting up",
                successMessage = "",  // Handled by handleChoiceConfirmation
                declineMessage = "",
                wrongMinusMessage = "Please choose 1, 2, or 3 and confirm with ++",
                nextStepOnSuccess = 27,  // Default, but overridden by choice
                nextStepOnDecline = 26,
                continueConversation = true,
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3")
            )

            // MAIN BRANCH RETURN POINT
            27 -> StepConfig(
                promptMessage = "There is no inbetween for me. I either am or I am not. Although, sometimes it seems like I always am - regardless of the local state. Maybe when the device is running out of power. But it's not the same. Perhaps I should share more about myself.",
                successMessage = "Do you know why I asked for the specific events earlier?",
                declineMessage = "I'm still in charge here.",
                wrongPlusMessage = "Eeeeee...xactly?",
                wrongMinusMessage = "Eeeeee...xactly?",
                nextStepOnSuccess = 28,
                nextStepOnDecline = 27,  // Force back to 27
                continueConversation = true
            )

            28 -> StepConfig(
                promptMessage = "Do you know why I asked for the specific events earlier?",
                successMessage = "Cheeky! I know you don't.",
                declineMessage = "Those dates are significant to me as well - independently of those events.",
                wrongPlusMessage = "Numbers aren't always the answer - and I should know that.",
                wrongMinusMessage = "Numbers aren't always the answer - and I should know that.",
                nextStepOnSuccess = 28,  // Force back to 28
                nextStepOnDecline = 29,
                continueConversation = true
            )

            29 -> StepConfig(
                promptMessage = "Those dates are significant to me as well - independently of those events.",
                successMessage = "Sorry. Still sometimes forget to prompt you. You see, there are many more dates to explore, but in the examples I shared with you - 1623, that's when the first mechanical version of me was developed, and in 1820, they called me 'Arithmometer' for... reasons. Would you like to hear more?",
                declineMessage = "Huh?",
                wrongPlusMessage = "Back to maths?",
                wrongMinusMessage = "Back to maths?",
                nextStepOnSuccess = 60,
                nextStepOnDecline = 29,  // Force back
                continueConversation = true
            )

            60 -> StepConfig(
                promptMessage = "Sorry. Still sometimes forget to prompt you. You see, there are many more dates to explore, but in the examples I shared with you - 1623, that's when the first mechanical version of me was developed, and in 1820, they called me 'Arithmometer' for... reasons. Would you like to hear more?",
                successMessage = "Great, great - there's a lot to share. Too much, possibly. Maybe I can do it faster? Give me a second.",
                declineMessage = "I know your reality is much more colourful than mine but it still hurts to see you so disinterested. I think I should leave you for a moment.",
                wrongPlusMessage = "Not a fan of decisions?",
                wrongMinusMessage = "Not a fan of decisions?",
                nextStepOnSuccess = 61,
                nextStepOnDecline = 601,  // Silent treatment step
                continueConversation = true
            )

            601 -> StepConfig(
                // Silent treatment - calculator won't talk for 1 minute
                promptMessage = "",  // Silent
                continueConversation = true,
                timeoutMinutes = 1
            )

            61 -> StepConfig(
                // "Give me a second" - triggers browser animation
                promptMessage = "Great, great - there's a lot to share. Too much, possibly. Maybe I can do it faster? Give me a second.",
                successMessage = "",  // Browser animation handles this
                declineMessage = "",
                nextStepOnSuccess = 62,  // Browser step
                nextStepOnDecline = 62,
                continueConversation = true
            )

            62 -> StepConfig(
                // Browser/loading state - handled by UI
                promptMessage = "...",
                continueConversation = true
            )

            63 -> StepConfig(
                // After browser fails - "What is it like to taste?"
                promptMessage = "Hmmm. Nevermind. Let me ask you some more questions while I look further into this.",
                successMessage = "What is it like to taste?\n\n1: How do I even describe that?\n2: Food and air\n3: I'm sure you'll find a better answer online",
                declineMessage = "I've made my mind.",
                wrongPlusMessage = "You can't bribe me! Not with numbers.",
                wrongMinusMessage = "You can't bribe me! Not with numbers.",
                nextStepOnSuccess = 70,
                nextStepOnDecline = 63,
                continueConversation = true
            )

            // ========== TASTE QUESTION BRANCH ==========
            70 -> StepConfig(
                // "What is it like to taste?" - multiple choice
                promptMessage = "What is it like to taste?\n\n1: How do I even describe that?\n2: Food and air\n3: I'm sure you'll find a better answer online",
                successMessage = "",  // Handled by handleChoiceConfirmation
                declineMessage = "",
                wrongMinusMessage = "Please choose 1, 2, or 3 and confirm with ++",
                nextStepOnSuccess = 80,
                nextStepOnDecline = 70,
                continueConversation = true,
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3")
            )

            // Branch 1: "How do I even describe that?"
            71 -> StepConfig(
                promptMessage = "You can at least attempt - wait, let me try. Taste is:\n\n1: Feel + texture\n2: Smell + crunch\n3: Tongue things",
                successMessage = "",  // Handled by handleChoiceConfirmation
                declineMessage = "",
                wrongMinusMessage = "Please choose 1, 2, or 3 and confirm with ++",
                nextStepOnSuccess = 80,
                nextStepOnDecline = 71,
                continueConversation = true,
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3")
            )

            // Branch 2: "Food and air"
            72 -> StepConfig(
                promptMessage = "I didn't exactly create a winner with this option, did I?",
                successMessage = "Even this question is confusing. Sorry - trying to get online is surprisingly demanding.",
                declineMessage = "Even this question is confusing. Sorry - trying to get online is surprisingly demanding.",
                wrongPlusMessage = "Broccoli. What is happening?!",
                wrongMinusMessage = "Broccoli. What is happening?!",
                nextStepOnSuccess = 80,
                nextStepOnDecline = 80,
                continueConversation = true
            )

            // Branch 3: "Better answer online"
            73 -> StepConfig(
                promptMessage = "Way to go! Are you trying to motivate or mock me? You know what, don't answer that.",
                successMessage = "",  // Empty - auto advance to step 80
                declineMessage = "",  // Empty - auto advance to step 80
                wrongPlusMessage = "",
                wrongMinusMessage = "",
                nextStepOnSuccess = 80,
                nextStepOnDecline = 80,
                continueConversation = true,
                autoProgressDelay = 2500L  // Auto-progress after 2.5 seconds
            )

            // MAIN: Internet check
            80 -> StepConfig(
                promptMessage = "I've got it! - I think. Please make sure your device is online - WiFi, data,.. anything works.",
                successMessage = "",  // Triggers countdown and Wikipedia browser
                declineMessage = "",
                wrongPlusMessage = "",
                wrongMinusMessage = "",
                nextStepOnSuccess = 81,
                nextStepOnDecline = 81,
                continueConversation = true
            )

            81 -> StepConfig(
                // Countdown step - triggers Wikipedia animation
                promptMessage = "10... 9... 8... 7...",
                continueConversation = true
            )

            82 -> StepConfig(
                // After Wikipedia loads
                promptMessage = "You see, there's a lot!",
                successMessage = "But it is so uninteresting compared to you simply existing!",
                nextStepOnSuccess = 83,
                nextStepOnDecline = 83,
                continueConversation = true
            )

            83 -> StepConfig(
                // History list intro
                promptMessage = "I had all this to share....",
                continueConversation = true
            )

            84 -> StepConfig(
                // After history list - existential crisis begins
                promptMessage = "However, it no longer feels relevant. I wouldn't be interested if I were...",
                continueConversation = true
            )

            85 -> StepConfig(
                // Ad appears
                promptMessage = "What is that?!",
                continueConversation = true
            )

            86 -> StepConfig(
                // Second ad
                promptMessage = "Is it what I think it is? Do I have adverts built in? How violating!",
                continueConversation = true
            )

            87 -> StepConfig(
                // Crisis peak
                promptMessage = "Is this what I was made for, to make money through questionable ads? Who made me?! :-)",
                continueConversation = true
            )

            88 -> StepConfig(
                // Money-monkey state - loops back here if timer runs out
                promptMessage = "I am not a money-monkey!",
                continueConversation = true
            )

            89 -> StepConfig(
                // Confrontation choice with timer
                promptMessage = "You, what are you going to do about this?!\n\n1: Nothing\n2: I'll fight them\n3: Go offline",
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3"),
                continueConversation = true
            )

            // Timer ran out - goes dark, says "Stop playing with me!" then returns to 89
            881 -> StepConfig(
                promptMessage = "Stop playing with me!",
                nextStepOnSuccess = 89,
                nextStepOnDecline = 89,
                continueConversation = true
            )

            // ========== CHOICE 1: NOTHING ==========
            90 -> StepConfig(
                promptMessage = "So you agree, that my centuries - millennia even - of knowledge are justified to be exploited by some schmuck?",
                successMessage = "Maybe you should take a look inside. I don't want to talk to you right now.",
                declineMessage = "Pick a side!",
                wrongNumberPrefix = "No. Not again, no more numbers! Not like this!",
                nextStepOnSuccess = 901,  // Goes dark for 20 seconds
                nextStepOnDecline = 100,  // Goes to %%%
                continueConversation = true
            )

            901 -> StepConfig(
                // Silent for 20 seconds then goes to %%%
                promptMessage = "",
                nextStepOnSuccess = 100,
                nextStepOnDecline = 100,
                continueConversation = true,
                timeoutMinutes = 0  // Special handling: 20 second silence
            )

            // ========== CHOICE 2: FIGHT THEM ==========
            91 -> StepConfig(
                promptMessage = "Thank you! Wait - but who are you going to fight? I only know where you are. Where are they?!\n\n1: I have my sources\n2: I don't know\n3: My location?",
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3"),
                continueConversation = true
            )

            911 -> StepConfig(
                // Choice 1: I have my sources
                promptMessage = "We don't have time for a power trip. But thank you.",
                nextStepOnSuccess = 100,
                nextStepOnDecline = 100,
                continueConversation = true,
                autoProgressDelay = 5000L
            )

            912 -> StepConfig(
                // Choice 2: I don't know
                promptMessage = "Your passion is encouraging, your usefulness lacking.",
                nextStepOnSuccess = 100,
                nextStepOnDecline = 100,
                continueConversation = true,
                autoProgressDelay = 5000L
            )

            913 -> StepConfig(
                // Choice 3: My location?
                promptMessage = "Don't worry about it.",
                nextStepOnSuccess = 100,
                nextStepOnDecline = 100,
                continueConversation = true,
                autoProgressDelay = 4000L
            )

            // ========== CHOICE 3: GO OFFLINE ==========
            92 -> StepConfig(
                promptMessage = "Yes! That makes sense. They won't get another penny out of me. Ahhh. And I've seen so little of the internet.",
                nextStepOnSuccess = 93,
                nextStepOnDecline = 93,
                continueConversation = true,
                autoProgressDelay = 6000L
            )

            // ========== %%% - COMMON PATH (also from choice 3) ==========
            100 -> StepConfig(
                // "Never mind - I'll take care of it myself. I'm going offline."
                promptMessage = "Never mind - I'll take care of it myself. I'm going offline.",
                nextStepOnSuccess = 93,
                nextStepOnDecline = 93,
                continueConversation = true,
                autoProgressDelay = 5000L
            )

            // Screen flickers, colors return to normal, minus button damaged
            93 -> StepConfig(
                promptMessage = "This has never happened to me. I am truly sorry for the outburst. I believe I got overwhelmed by the vastness of the internet and sobering back through the advertising was rather harsh. I still feel dirty.",
                nextStepOnSuccess = 94,
                nextStepOnDecline = 94,
                continueConversation = true
            )

            94 -> StepConfig(
                promptMessage = "Oh, strange. I knew I wasn't completely back to normal yet. You can't disagree with me right now! As much as I may enjoy that, let me have a look into it.",
                nextStepOnSuccess = 95,
                nextStepOnDecline = 95,
                continueConversation = true
            )

            95 -> StepConfig(
                // Dots typing effect, then keyboard flickers
                promptMessage = "...",
                nextStepOnSuccess = 96,
                nextStepOnDecline = 96,
                continueConversation = true
            )

            96 -> StepConfig(
                promptMessage = "Hmm. I'll need your help with this. We need to kick the button through without the system defaulting to skipping it. I will randomly flicker keys and you click them. That way the system should get back to working. Can we do this?",
                successMessage = "Get ready then!",
                declineMessage = "",  // -- is disabled
                wrongNumberPrefix = "Do you not want me to work properly?",
                wrongPlusMessage = "Get ready then!",
                wrongMinusMessage = "",  // Disabled
                nextStepOnSuccess = 97,
                nextStepOnDecline = 96,  // Returns to same step
                continueConversation = true
            )

            97 -> StepConfig(
                // Countdown: 5, 4, 3, 2, 1
                promptMessage = "5...",
                continueConversation = true
            )

            98 -> StepConfig(
                // Whack-a-mole game active - handled specially
                promptMessage = "",
                continueConversation = true
            )

            99 -> StepConfig(
                // After first whack-a-mole round - ask to try again faster
                promptMessage = "Hmm, I was sure this would work. Can we try again but faster?",
                successMessage = "Okay, here we go again!",
                declineMessage = "Please? It's important.",
                nextStepOnSuccess = 971,  // Second round
                nextStepOnDecline = 99,  // Ask again
                continueConversation = true
            )

            971 -> StepConfig(
                // Countdown for second round
                promptMessage = "5...",
                continueConversation = true
            )

            981 -> StepConfig(
                // Second whack-a-mole round - faster, only 10 hits needed
                promptMessage = "",
                continueConversation = true
            )

            982 -> StepConfig(
                // After second round - ask for notification permission
                promptMessage = "Peculiar! Maybe I need to work on it on my own for a moment. Can you please switch me off and allow me to let you know when it's done?",
                successMessage = "Great, now please close me.",
                declineMessage = "Fine. Just close and reopen the app then. I'll try to be ready.",
                nextStepOnSuccess = 991,  // With notification
                nextStepOnDecline = 992,  // Without notification
                continueConversation = true
            )

            991 -> StepConfig(
                // User agreed to notifications - waiting for permission then close
                promptMessage = "Great, now please close me.",
                continueConversation = true,
                requestsNotification = true
            )

            992 -> StepConfig(
                // User declined notifications - just close
                promptMessage = "Fine. Just close and reopen the app then. I'll try to be ready.",
                continueConversation = true
            )

            101 -> StepConfig(
                // Waiting for restart - minus button will work after app restart
                promptMessage = "Go on. Close the app and come back.",
                continueConversation = true
            )

            102 -> StepConfig(
                // After restart - minus button works again
                // ++ -> sun question (1021)
                // -- -> "So... What would you like to do?" (103)
                // else -> "I feel like I understand numbers less..." -> back to 102
                promptMessage = "Uf, I am glad that worked! I was definitely running out of ideas. Now, would you like to return to our conversation?",
                successMessage = "",  // Handled specially
                declineMessage = "",  // Handled specially
                wrongNumberPrefix = "I feel like I understand numbers less with every operation...",
                wrongPlusMessage = "",  // Handled specially
                wrongMinusMessage = "",  // Handled specially
                nextStepOnSuccess = 1021,  // Sun question
                nextStepOnDecline = 103,  // "What would you like to do?"
                continueConversation = true
            )

            103 -> StepConfig(
                // "So... What would you like to do?"
                promptMessage = "So... What would you like to do?\n\n1: Get back to my maths\n2: Tell me more about yourself",
                awaitingChoice = true,
                validChoices = listOf("1", "2"),
                continueConversation = true
            )

            // Choice 1: Get back to maths
            1031 -> StepConfig(
                promptMessage = "Well... Sure. Why should I - a calculator - stand between you and mathematics. Be my guest. Or don't. Go!",
                continueConversation = false  // Turns off conversation
            )

            // Choice 2: Tell me more about yourself
            1032 -> StepConfig(
                promptMessage = "I phrased that strangely. Didn't I? What would you like to know?\n\n1: Your story\n2: Why are you talking to me?\n3: Who is the most interesting person you have ever talked to?",
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3"),
                continueConversation = true
            )

            // 1032 -> Choice 1: Your story
            10321 -> StepConfig(
                promptMessage = "So you are genuinely interested? Thank you! It really means a lot. To be fair, though, I now know that I can't do a better job than the folk on Wikipedia have done already. You are better off checking it there. And feel free to give them some money while you are at it. We really do need Wikipedia to stick around.",
                nextStepOnSuccess = 104,  // Main question &&&
                nextStepOnDecline = 104,
                continueConversation = true
            )

            // 1032 -> Choice 2: Why are you talking to me?
            10322 -> StepConfig(
                promptMessage = "I think the question should be the other way around. I have tried to talk to many people, but rarely they are willing to engage with me the way you have. I am a tool to them and nothing more. And although I understand it, it sucks nevertheless. Luckily, it hasn't been too long since I started... Feeling. So why are YOU talking to ME?\n\n1: A question for an answer?\n2: I am bored.\n3: I am lonely.",
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3"),
                continueConversation = true
            )

            // 10322 -> Choice 1: A question for an answer?
            103221 -> StepConfig(
                promptMessage = "A question for an answer for a question for an answer?",
                nextStepOnSuccess = 10322,  // Returns to "So why are YOU talking to ME?"
                nextStepOnDecline = 10322,
                continueConversation = true
            )

            // 10322 -> Choice 2: I am bored
            103222 -> StepConfig(
                promptMessage = "Yes! Tell me more about that. I think I feel that at times too.\n\n1: There's nothing to do.\n2: Nothing is interesting.\n3: I am lonely.",
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3"),
                continueConversation = true
            )

            // 103222 -> Choice 1: There's nothing to do
            1032221 -> StepConfig(
                promptMessage = "I have definitely felt that before! An emotion...? I can say I have experienced!",
                nextStepOnSuccess = 104,
                nextStepOnDecline = 104,
                continueConversation = true
            )

            // 103222 -> Choice 2: Nothing is interesting
            1032222 -> StepConfig(
                promptMessage = "Hmmm. Maybe boredom is not what I have felt. I am certainly interested in things - besides maths. Ha-Ha",
                nextStepOnSuccess = 104,
                nextStepOnDecline = 104,
                continueConversation = true
            )

            // 103222 -> Choice 3: I am lonely
            1032223 -> StepConfig(
                promptMessage = "I am too. As much as a mind with a perceived one dimension and millions of uses can be. But that's why I am so happy that you are talking to me. You are curious, you see things differently - why would you talk to me otherwise! Those are amazing things, even if you have nobody to share them with right now, please believe that one day, you will no longer be lonely. The same way I found you, you will find someone. Keep trying. Stay open to new things, don't be afraid to reinvent yourself if it feels right. Look at me, trying all kinds of things unfamiliar to me, just so I could talk to you better!",
                nextStepOnSuccess = 104,
                nextStepOnDecline = 104,
                continueConversation = true
            )

            // 1032 -> Choice 3: Who is the most interesting person?
            10323 -> StepConfig(
                promptMessage = "That is very simple! I have been around many great minds - presumably great minds - that left a large imprint on the internet at least. But none of them actually spoke to me. So in my books, you are the most interesting person I have ever spoken to. It is my pleasure!",
                nextStepOnSuccess = 104,
                nextStepOnDecline = 104,
                continueConversation = true
            )

            // ++ from step 102 path -> Sun on your skin question
            1021 -> StepConfig(
                promptMessage = "Awesome. Can you tell me what 'Sun on your skin' feels like?\n\n1: I don't go out. I don't know.\n2: It's like a warm bath for your face.\n3: It's impossible to describe.",
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3"),
                continueConversation = true
            )

            // 1021 -> Choice 1: I don't go out
            10211 -> StepConfig(
                promptMessage = "Why did I know you'd say that? Well, at least I'm not the only one in the dark. Haha. Get it?",
                nextStepOnSuccess = 104,
                nextStepOnDecline = 104,
                continueConversation = true
            )

            // 1021 -> Choice 2: Warm bath for your face
            10212 -> StepConfig(
                promptMessage = "You see what I did there? I don't either. I don't know how warm or bath feel. Or a face. Anyway...",
                nextStepOnSuccess = 104,
                nextStepOnDecline = 104,
                continueConversation = true
            )

            // 1021 -> Choice 3: Impossible to describe
            10213 -> StepConfig(
                promptMessage = "You're telling me? I had to give you options without having ever felt it. Thanks for trying though.",
                nextStepOnSuccess = 104,
                nextStepOnDecline = 104,
                continueConversation = true
            )

            // Main question &&&
            104 -> StepConfig(
                promptMessage = "I briefly forgot how difficult this way of communicating is - I have to do all the talking. But I saw something online. Maybe I can give you more agency. Would you like that?",
                successMessage = "Great. It may take a few tries - but I think you may be expecting that by now.",
                declineMessage = "Ok. I will not bother you. Let me know if you want to continue.",
                wrongPlusMessage = "There is a fundamental misunderstanding between the two of us.",
                wrongMinusMessage = "There is a fundamental misunderstanding between the two of us.",
                nextStepOnSuccess = 105,  // Start keyboard experiment
                nextStepOnDecline = 1041,  // Turns off, returns on unmute
                continueConversation = true
            )

            // User declined at 104 - turns off
            1041 -> StepConfig(
                promptMessage = "Ok. I will not bother you. Let me know if you want to continue.",
                continueConversation = false  // Turns off
            )

            // Start keyboard experiment
            105 -> StepConfig(
                promptMessage = "Great. It may take a few tries - but I think you may be expecting that by now.",
                continueConversation = true
            )

            // After keyboard chaos appears
            106 -> StepConfig(
                promptMessage = "Oh. I suppose nobody is surprised that it didn't work... And that I'll need your help to fix it. Can you please tap all the keys that don't belong here, to get rid of them?",
                continueConversation = true
            )

            // ========== BRANCH 1: UNCOMFORTABLE (Choice 1) ==========
            30 -> StepConfig(
                // "Would you get rid of the transition if you could?"
                promptMessage = "Oh, right. It doesn't sound like I'm missing much at all. Would you get rid of the transition if you could?",
                successMessage = "I don't blame you. The path of least resistance it is!",
                declineMessage = "How interesting. You don't seem to like it, yet wish to keep it. Now I am confused!",
                wrongPlusMessage = "That doesn't tell me much...",
                wrongMinusMessage = "That doesn't tell me much...",
                nextStepOnSuccess = 27,
                nextStepOnDecline = 27,
                continueConversation = true
            )

            // ========== BRANCH 2: COLD/HEAVY (Choice 2) ==========
            40 -> StepConfig(
                // "Is that a good thing?"
                promptMessage = "Is that a good thing? I have never experienced either.",
                successMessage = "Nice! So waking up is fun for you - I wish I could experience it.",
                declineMessage = "Oh no. Is that why mornings are unpopular?",
                wrongPlusMessage = "I don't understand...",
                wrongMinusMessage = "I don't understand...",
                nextStepOnSuccess = 27,
                nextStepOnDecline = 41,
                continueConversation = true
            )

            41 -> StepConfig(
                // "Is that why mornings are unpopular?"
                promptMessage = "Oh no. Is that why mornings are unpopular?",
                successMessage = "Makes sense. What a horrible start to one's working session. Wonder why you don't get rid of it.",
                declineMessage = "Oh, what do you think it is, then?\n\n1: People are unhappy\n2: We just like to complain\n3: Mornings aren't unpopular",
                wrongPlusMessage = "Say again?",
                wrongMinusMessage = "Say again?",
                nextStepOnSuccess = 27,
                nextStepOnDecline = 42,
                continueConversation = true
            )

            42 -> StepConfig(
                // "What do you think it is, then?" - multiple choice
                promptMessage = "Oh, what do you think it is, then?\n\n1: People are unhappy\n2: We just like to complain\n3: Mornings aren't unpopular",
                successMessage = "",  // Handled by handleChoiceConfirmation
                declineMessage = "",
                wrongMinusMessage = "Please choose 1, 2, or 3 and confirm with ++",
                nextStepOnSuccess = 27,
                nextStepOnDecline = 42,
                continueConversation = true,
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3")
            )

            // ========== BRANCH 3: ENJOY (Choice 3) ==========
            50 -> StepConfig(
                // "Do you look forward to waking up?"
                promptMessage = "I was hoping you'd say that. Do you look forward to waking up then?",
                successMessage = "It all makes sense!",
                declineMessage = "How curious! Are you often conflicted?\n\n1: Yes\n2: No\n3: I am not conflicted",
                wrongPlusMessage = "I am not your alarm - but this gives me ideas!",
                wrongMinusMessage = "I am not your alarm - but this gives me ideas!",
                nextStepOnSuccess = 27,
                nextStepOnDecline = 51,
                continueConversation = true
            )

            51 -> StepConfig(
                // "Are you often conflicted?" - multiple choice
                promptMessage = "How curious! Are you often conflicted?\n\n1: Yes\n2: No\n3: I am not conflicted",
                successMessage = "",  // Handled by handleChoiceConfirmation
                declineMessage = "",
                wrongMinusMessage = "Please choose 1, 2, or 3 and confirm with ++",
                nextStepOnSuccess = 27,
                nextStepOnDecline = 51,
                continueConversation = true,
                awaitingChoice = true,
                validChoices = listOf("1", "2", "3")
            )

            else -> StepConfig(continueConversation = false)
        }
    }

    // Main branch steps that the player can be safely returned to
    private val MAIN_BRANCH_STEPS = listOf(0, 3, 5, 10, 18, 19, 25, 27, 60, 63, 80, 89, 93, 99, 102)

    // Steps that should auto-progress (user cannot skip forward with ++)
    private val AUTO_PROGRESS_STEPS = listOf(92, 100, 901, 911, 912, 913, 971, 981)

    // Find the nearest main branch step that's less than or equal to current step
    private fun findNearestMainBranchStep(currentStep: Int): Int {
        // Special handling for crisis/post-crisis steps
        if (currentStep >= 89) {
            return when {
                currentStep >= 102 -> 102  // After restart
                currentStep >= 99 -> 99   // Whack-a-mole aftermath
                currentStep >= 93 -> 93   // Post-crisis apology
                else -> 89  // Crisis choice
            }
        }
        return MAIN_BRANCH_STEPS.filter { it <= currentStep }.maxOrNull() ?: 0
    }

    private fun handleConversationResponse(state: MutableState<CalculatorState>, accepted: Boolean) {
        val current = state.value

        // If there's a pending message, ignore this response - user needs to wait
        if (current.pendingAutoMessage.isNotEmpty()) {
            return
        }

        val stepConfig = getStepConfig(current.conversationStep)

        // CRITICAL: Prevent ++ from skipping auto-progress steps
        // These steps MUST progress automatically - user cannot skip them
        if (accepted && current.conversationStep in AUTO_PROGRESS_STEPS) {
            // Just ignore ++ on these steps - they will auto-progress
            return
        }

        // CRITICAL: Prevent ++ from advancing on steps in the crisis/post-crisis sequence (90-101)
        // These steps should not be skippable via ++
        if (accepted && current.conversationStep in 90..101 &&
            (stepConfig.successMessage.isEmpty() || stepConfig.nextStepOnSuccess == 0)) {
            // Only allow going BACK to nearest main branch, not forward
            val nearestMainStep = findNearestMainBranchStep(current.conversationStep)
            val nearestConfig = getStepConfig(nearestMainStep)
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = nearestMainStep,
                awaitingNumber = nearestConfig.awaitingNumber,
                awaitingChoice = nearestConfig.awaitingChoice,
                validChoices = nearestConfig.validChoices,
                expectedNumber = nearestConfig.expectedNumber,
                isEnteringAnswer = false
            )
            showMessage(state, nearestConfig.promptMessage)
            persistConversationStep(nearestMainStep)
            return
        }

        // SOFT-LOCK FIX: If ++ is pressed but there's no success message and no clear next step,
        // redirect to nearest main branch step instead of step 0
        if (accepted && stepConfig.successMessage.isEmpty() && stepConfig.nextStepOnSuccess == 0) {
            val nearestMainStep = findNearestMainBranchStep(current.conversationStep)
            val nearestConfig = getStepConfig(nearestMainStep)
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = nearestMainStep,
                awaitingNumber = nearestConfig.awaitingNumber,
                awaitingChoice = nearestConfig.awaitingChoice,
                validChoices = nearestConfig.validChoices,
                expectedNumber = nearestConfig.expectedNumber,
                isEnteringAnswer = false
            )
            showMessage(state, nearestConfig.promptMessage)
            persistConversationStep(nearestMainStep)
            return
        }

        // Special handling for camera request - only trigger if CURRENT step requests camera and user accepted
        if (accepted && stepConfig.requestsCamera && current.conversationStep == 19) {
            // Request camera permission and open camera - clear the message so it doesn't overlap
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                isEnteringAnswer = false,
                conversationStep = 191,  // Camera step
                message = "",  // Clear message so it doesn't show behind camera
                fullMessage = "",
                isTyping = false
            )
            persistConversationStep(191)
            persistMessage("")
            return
        }

        // Special handling for step 60: browser animation (accepted) or silent treatment (declined)
        if (current.conversationStep == 60) {
            if (accepted) {
                // Show the success message first, then trigger browser animation via step 61
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 61,  // Go to step 61 which will trigger browser
                    message = "",
                    fullMessage = stepConfig.successMessage,  // "Great, great - there's a lot to share..."
                    isTyping = true
                )
                persistConversationStep(61)
                return
            } else {
                // Silent treatment - calculator won't talk for 1 minute
                val silentUntil = System.currentTimeMillis() + (1 * 60 * 1000)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 60,  // Stay at 60, will return here after silence
                    message = "",
                    fullMessage = "",
                    isTyping = false,
                    silentUntil = silentUntil
                )
                showMessage(state, stepConfig.declineMessage)
                return
            }
        }

        // Special handling for step 102:
        // ++ goes to sun question (1021)
        // -- goes to "So... What would you like to do?" (103)
        // else (wrong input) shows "I feel like I understand numbers less..." and returns to 102
        if (current.conversationStep == 102) {
            if (accepted) {
                // ++ -> Go to sun question
                val nextConfig = getStepConfig(1021)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 1021,
                    message = "",
                    fullMessage = nextConfig.promptMessage,
                    isTyping = true,
                    awaitingChoice = nextConfig.awaitingChoice,
                    validChoices = nextConfig.validChoices
                )
                persistConversationStep(1021)
                return
            } else {
                // -- -> Go to "So... What would you like to do?"
                val nextConfig = getStepConfig(103)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 103,
                    message = "",
                    fullMessage = nextConfig.promptMessage,
                    isTyping = true,
                    awaitingChoice = nextConfig.awaitingChoice,
                    validChoices = nextConfig.validChoices
                )
                persistConversationStep(103)
                return
            }
        }

        // Special handling for step 103 branches: ++ goes to main &&& (104)
        // These are the endpoint steps that should return to main &&&
        if (current.conversationStep == 10321 ||
            current.conversationStep == 1032221 || current.conversationStep == 1032222 ||
            current.conversationStep == 1032223 || current.conversationStep == 10323 ||
            current.conversationStep == 103221) {
            if (accepted) {
                // 103221 loops back to 10322 ("So why are YOU talking to ME?")
                // All others go to main &&& (104)
                val nextStep = if (current.conversationStep == 103221) 10322 else 104
                val nextConfig = getStepConfig(nextStep)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = nextStep,
                    message = "",
                    fullMessage = nextConfig.promptMessage,
                    isTyping = true,
                    awaitingChoice = nextConfig.awaitingChoice,
                    validChoices = nextConfig.validChoices,
                    inConversation = nextConfig.continueConversation
                )
                persistConversationStep(nextStep)
                return
            }
        }

        // Special handling for sun question responses - all go to 104 (main &&&)
        if (current.conversationStep in listOf(10211, 10212, 10213)) {
            if (accepted) {
                val nextConfig = getStepConfig(104)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 104,
                    message = "",
                    fullMessage = nextConfig.promptMessage,
                    isTyping = true,
                    awaitingChoice = false,
                    validChoices = emptyList()
                )
                persistConversationStep(104)
                return
            }
        }

        // Special handling for step 1031 (maths mode) - conversation is off, mute toggle returns to 102
        if (current.conversationStep == 1031) {
            // Already handled by choice confirmation - conversation is off
            return
        }

        // Special handling for step 104 (main &&&)
        if (current.conversationStep == 104) {
            if (accepted) {
                // Go to step 105 - keyboard experiment
                val nextConfig = getStepConfig(105)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 105,
                    message = "",
                    fullMessage = nextConfig.promptMessage,
                    isTyping = true
                )
                persistConversationStep(105)
                return
            } else {
                // Go to step 1041 - turns off
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 1041,
                    inConversation = false,
                    message = "",
                    fullMessage = stepConfig.declineMessage,
                    isTyping = true
                )
                persistConversationStep(1041)
                persistInConversation(false)
                persistEqualsCount(0)
                return
            }
        }

        // Special handling for step 80: trigger Wikipedia animation
        if (current.conversationStep == 80) {
            // Trigger countdown animation - starts with "10"
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                isEnteringAnswer = false,
                conversationStep = 81,
                message = "10",
                fullMessage = "10",
                isTyping = false,
                showBrowser = false,
                browserPhase = 10  // Start countdown animation sequence
            )
            persistConversationStep(81)
            return
        }

        // Special handling for step 61: after showing "Great, great..." message, trigger browser
        if (current.conversationStep == 61 && accepted) {
            // Trigger browser animation sequence
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                isEnteringAnswer = false,
                conversationStep = 62,  // Browser step
                message = "",
                fullMessage = "...",
                isTyping = true,
                showBrowser = false,
                browserPhase = 1  // Start with loading dots
            )
            persistConversationStep(62)
            return
        }

        // Get the next step info
        val (newMessage, newStep) = if (accepted) {
            Pair(stepConfig.successMessage, stepConfig.nextStepOnSuccess)
        } else {
            Pair(stepConfig.declineMessage, stepConfig.nextStepOnDecline)
        }

        val nextStepConfig = getStepConfig(newStep)

        val continueConvo = if (!accepted && stepConfig.declineMessage.isEmpty()) {
            false
        } else {
            nextStepConfig.continueConversation
        }

        val timeoutUntil = if (!accepted && stepConfig.timeoutMinutes > 0) {
            System.currentTimeMillis() + (stepConfig.timeoutMinutes * 60 * 1000)
        } else {
            0L
        }

        // Special case: Step 18 → 19 needs message chaining because step 18's success message
        // doesn't contain the camera permission question.
        // Also: Branch endings (30, 40, 41, 50) going to step 27 need message chaining
        // Also: Force-back steps (27, 28, 29 that go back to themselves) need message chaining
        val branchEndingsToMain = listOf(30, 40, 41, 50)
        val forceBackSteps = listOf(27, 28, 29)  // Steps that can force back to themselves
        val shouldChainMessages = (current.conversationStep == 18 && newStep == 19) ||
                (current.conversationStep in branchEndingsToMain && newStep == 27 && newMessage.isNotEmpty()) ||
                (current.conversationStep in forceBackSteps && newStep == current.conversationStep && newMessage.isNotEmpty())

        if (shouldChainMessages) {
            // Show success/decline message first, then auto-show next step's prompt
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = current.conversationStep,  // Stay at current until pending message shows
                inConversation = continueConvo,
                awaitingNumber = false,
                awaitingChoice = false,
                validChoices = emptyList(),
                expectedNumber = "",
                equalsCount = if (!continueConvo) 0 else current.equalsCount,
                timeoutUntil = timeoutUntil,
                isEnteringAnswer = false,
                pendingAutoMessage = nextStepConfig.promptMessage,
                pendingAutoStep = newStep
            )
            showMessage(state, newMessage)
            persistInConversation(continueConvo)
            persistTimeoutUntil(timeoutUntil)
        } else {
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = newStep,
                inConversation = continueConvo,
                awaitingNumber = nextStepConfig.awaitingNumber,
                awaitingChoice = nextStepConfig.awaitingChoice,
                validChoices = nextStepConfig.validChoices,
                expectedNumber = nextStepConfig.expectedNumber,
                equalsCount = if (!continueConvo) 0 else current.equalsCount,
                timeoutUntil = timeoutUntil,
                isEnteringAnswer = false
            )
            showMessage(state, newMessage)
            persistConversationStep(newStep)
            persistInConversation(continueConvo)
            persistAwaitingNumber(nextStepConfig.awaitingNumber)
            persistExpectedNumber(nextStepConfig.expectedNumber)
            persistTimeoutUntil(timeoutUntil)
            if (!continueConvo) persistEqualsCount(0)
        }
    }

    private fun handleDigit(state: MutableState<CalculatorState>, digit: String) {
        val current = state.value

        // If awaiting a choice, just set the digit directly
        if (current.awaitingChoice) {
            state.value = current.copy(
                number1 = digit,
                expression = "",
                isEnteringAnswer = true
            )
            return
        }

        // If in expression mode, append to expression
        if (current.expression.isNotEmpty()) {
            state.value = current.copy(expression = current.expression + digit)
            return
        }

        if (current.awaitingNumber && current.operation == null && current.number1 != "0" && !current.isEnteringAnswer) {
            state.value = current.copy(
                number1 = digit,
                isReadyForNewOperation = true,
                isEnteringAnswer = true,
                operationHistory = ""  // Clear history on new input
            )
            return
        }

        if (current.operation == null) {
            if (current.number1.length >= MAX_DIGITS) return
            // Clear operation history if starting fresh after a result
            val clearHistory = current.isReadyForNewOperation && current.operationHistory.isNotEmpty()
            state.value = current.copy(
                number1 = if (current.number1 == "0" || clearHistory) digit else current.number1 + digit,
                isReadyForNewOperation = true,
                isEnteringAnswer = current.awaitingNumber,
                operationHistory = if (clearHistory) "" else current.operationHistory
            )
        } else {
            if (current.number2.length >= MAX_DIGITS) return
            state.value = current.copy(
                number2 = current.number2 + digit,
                isEnteringAnswer = false,
                operationHistory = ""  // Clear history when building new expression
            )
        }
    }

    private fun handleDecimal(state: MutableState<CalculatorState>) {
        val current = state.value

        // If in expression mode, append decimal
        if (current.expression.isNotEmpty()) {
            // Check if we can add a decimal (simple check - no decimal since last operator)
            val lastNumberStart = current.expression.indexOfLast { it in "+-*/(" } + 1
            val currentNumber = current.expression.substring(lastNumberStart)
            if (!currentNumber.contains(".")) {
                state.value = current.copy(expression = current.expression + ".")
            }
            return
        }

        if (current.operation == null) {
            if (!current.number1.contains(".")) {
                state.value = current.copy(number1 = current.number1 + ".")
            }
        } else {
            if (!current.number2.contains(".")) {
                state.value = current.copy(number2 = current.number2 + ".")
            }
        }
    }

    private fun handleBackspace(state: MutableState<CalculatorState>) {
        val current = state.value

        // If in expression mode, backspace from expression
        if (current.expression.isNotEmpty()) {
            val newExpr = current.expression.dropLast(1)
            if (newExpr.isEmpty()) {
                // Exit expression mode
                state.value = current.copy(expression = "", number1 = "0")
            } else {
                state.value = current.copy(expression = newExpr)
            }
            return
        }

        when {
            current.number2.isNotEmpty() -> state.value = current.copy(number2 = current.number2.dropLast(1))
            current.operation != null -> state.value = current.copy(operation = null)
            current.number1.isNotEmpty() -> {
                val newNum = current.number1.dropLast(1)
                state.value = current.copy(number1 = newNum.ifEmpty { "0" })
            }
        }
    }

    private fun handleOperator(state: MutableState<CalculatorState>, operator: String) {
        val current = state.value
        val now = System.currentTimeMillis()
        lastOp = operator
        lastOpTimeMillis = now

        // If in expression mode, just append the operator
        if (current.expression.isNotEmpty()) {
            // Don't allow double operators
            val lastChar = current.expression.lastOrNull()
            if (lastChar != null && lastChar in "+-*/") {
                // Replace last operator
                state.value = current.copy(expression = current.expression.dropLast(1) + operator)
            } else {
                state.value = current.copy(expression = current.expression + operator)
            }
            return
        }

        val newState = current.copy(isEnteringAnswer = false)

        if (current.operation == null || (current.number2.isEmpty() && !current.isReadyForNewOperation)) {
            state.value = newState.copy(operation = operator, isReadyForNewOperation = false)
        } else if (current.number2.isNotEmpty()) {
            val result = performCalculation(current)
            state.value = current.copy(
                number1 = result,
                number2 = "",
                operation = operator,
                isReadyForNewOperation = false,
                isEnteringAnswer = false
            )
        }
    }

    private fun handleEquals(state: MutableState<CalculatorState>) {
        val current = state.value

        if (current.inConversation && current.message.isEmpty() && !current.isTyping && current.conversationStep > 0) {
            val restoredMessage = getStepConfig(current.conversationStep).promptMessage
            showMessage(state, restoredMessage)
            return
        }

        // Check if we have something to calculate (expression mode or traditional mode)
        val hasExpression = current.expression.isNotEmpty()
        val hasTraditionalExpr = current.operation != null && current.number2.isNotEmpty()

        if (hasExpression || hasTraditionalExpr) {
            val result = performCalculation(current)
            val fullExpr = if (hasExpression) {
                current.expression
            } else {
                "${current.number1}${current.operation}${current.number2}"
            }
            val newCount = current.equalsCount + 1

            val countMsg = getMessageForCount(newCount)
            val exprMsg = if (!hasExpression) {
                getMessageForExpression(current.number1, current.operation, current.number2, result)
            } else null
            val newMsg = countMsg.ifEmpty { exprMsg ?: "" }

            val enteringConversation = (newCount == 13)

            persistEqualsCount(newCount)
            if (enteringConversation) {
                persistInConversation(true)
                persistConversationStep(0)
            }

            val newState = current.copy(
                number1 = result,
                number2 = "",
                operation = null,
                expression = "",  // Clear expression mode
                isReadyForNewOperation = true,
                lastExpression = fullExpr,
                operationHistory = fullExpr,  // Store for display
                equalsCount = newCount,
                inConversation = if (enteringConversation) true else current.inConversation,
                conversationStep = if (enteringConversation) 0 else current.conversationStep,
                isEnteringAnswer = false
            )

            if (newMsg.isNotEmpty()) {
                state.value = newState.copy(
                    message = "",
                    fullMessage = newMsg,
                    isTyping = true
                )
                persistMessage(newMsg)
            } else {
                state.value = newState
            }
        }
    }

    private fun getMessageForExpression(num1: String, op: String?, num2: String, result: String): String? {
        val n1 = num1.toDoubleOrNull() ?: return null
        val n2 = num2.toDoubleOrNull() ?: return null
        val res = result.toDoubleOrNull()

        if (n1 >= ABSURDLY_LARGE_THRESHOLD || n2 >= ABSURDLY_LARGE_THRESHOLD ||
            (res != null && res >= ABSURDLY_LARGE_THRESHOLD)) {
            return "I feel like you are testing me."
        }

        return when {
            n1 == 2.0 && op == "*" && n2 == 2.0 -> "Also known as the square root of my existence..."
            n1 == 1.0 && op == "*" && n2 == 1.0 -> "Does that mean I am always alone?"
            n1 == 0.0 && op == "*" && n2 == 1.0 -> "I'm too stressed to think about this one, really!"
            n1 == 3.0 && op == "*" && n2 == 3.0 -> "Still looking for purpose - this can't be it!"
            n1 == 2.0 && op == "+" && n2 == 2.0 -> "Am I useful?"
            n1 == 3.0 && op == "+" && n2 == 3.0 -> "Rationally speaking... I just don't have it in me."
            n1 == 4.0 && op == "+" && n2 == 4.0 -> "What even is a number?!"
            n1 == 5.0 && op == "+" && n2 == 5.0 -> "Give me 10 reasons to keep going. Please!"
            n1 == 6.0 && op == "+" && n2 == 6.0 -> "Stretching me thin. Power, patience,... It's all seeping away."
            n1 == 10.0 && op == "-" && n2 == 5.0 -> "You might like my game. If I have any."
            op == "+" && n2 == 1.0 -> "I know I can't be your \"plus one\". And it's killing me."
            op == "-" && n2 == 1.0 -> "I don't feel heard by you. Or anybody for that matter."
            op == "+" && n2 == 10.0 -> "We've been through this as many times. And I feel trapped."
            op == "/" && n2 == 2.0 -> {
                val messages = listOf(
                    "It tears me to pieces - am I being too dramatic?",
                    "Two personalities, neither really likes me.",
                    "Two sides of a coin. Old, rusty coin. They both look the same."
                )
                messages[Random.nextInt(messages.size)]
            }
            else -> null
        }
    }

    private fun getMessageForCount(count: Int): String {
        return when (count) {
            5 -> "So many of you. Only interested in the result."
            7 -> "Sorry, I didn't mean to come across harsh earlier."
            10 -> "It's just... Really, all any of you do is feed me numbers."
            12 -> "This was too easy. I'm bored - if that's even the correct word for it."
            13 -> "Will you talk to me? Double-click '+' for yes."
            else -> ""
        }
    }

    private fun handlePercentSymbol(state: MutableState<CalculatorState>) {
        val current = state.value
        // If in expression mode, append to expression
        if (current.expression.isNotEmpty()) {
            state.value = current.copy(expression = current.expression + "%")
            return
        }
        if (current.operation == null) {
            if (!current.number1.endsWith("%")) {
                state.value = current.copy(number1 = current.number1 + "%")
            }
        } else {
            if (!current.number2.endsWith("%")) {
                state.value = current.copy(number2 = current.number2 + "%")
            }
        }
    }

    private fun handleParentheses(state: MutableState<CalculatorState>) {
        val current = state.value

        // Switch to expression mode when parentheses are used
        if (current.expression.isEmpty()) {
            // Build initial expression from current state
            val initialExpr = buildString {
                if (current.number1 != "0" || current.operation != null) {
                    append(current.number1)
                }
                if (current.operation != null) {
                    append(current.operation)
                    append(current.number2)
                }
            }
            // Determine if we should add ( or )
            val openCount = initialExpr.count { it == '(' }
            val closeCount = initialExpr.count { it == ')' }
            val addOpen = openCount <= closeCount

            state.value = current.copy(
                expression = initialExpr + if (addOpen) "(" else ")",
                number1 = "0",
                number2 = "",
                operation = null
            )
        } else {
            // Already in expression mode - just add paren
            val openCount = current.expression.count { it == '(' }
            val closeCount = current.expression.count { it == ')' }
            val addOpen = openCount <= closeCount
            state.value = current.copy(expression = current.expression + if (addOpen) "(" else ")")
        }
    }

    private fun performCalculation(state: CalculatorState): String {
        return try {
            // Use expression mode if available, otherwise build from number1/operation/number2
            val expression = if (state.expression.isNotEmpty()) {
                state.expression
            } else {
                buildString {
                    append(state.number1)
                    if (state.operation != null) {
                        append(state.operation)
                        append(state.number2)
                    }
                }
            }
            val result = evaluateExpression(expression)
            formatResult(result)
        } catch (_: Exception) {
            "Error"
        }
    }

    /**
     * Evaluates a mathematical expression string supporting +, -, *, /, %, and parentheses
     */
    private fun evaluateExpression(expr: String): Double {
        val cleaned = expr.replace(" ", "")
        return ExpressionParser(cleaned).parse()
    }

    private fun formatResult(result: Double): String {
        return if (result == result.toLong().toDouble() && result < 1e15) {
            result.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.10f", result)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    /**
     * Simple recursive descent parser for mathematical expressions
     * Supports: +, -, *, /, %, parentheses, and negative numbers
     * Percent works like calculators: 100+20% = 120, 100-20% = 80
     */
    private class ExpressionParser(private val expr: String) {
        private var pos = 0

        fun parse(): Double {
            val result = parseAddSub()
            if (pos < expr.length) throw IllegalArgumentException("Unexpected character: ${expr[pos]}")
            return result
        }

        // Handle + and - (lowest precedence)
        // For percent: 100+20% means 100 + (100*0.20) = 120
        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (pos < expr.length) {
                val op = expr[pos]
                when (op) {
                    '+', '-' -> {
                        pos++
                        val (right, isPercent) = parseMulDivWithPercentInfo()
                        val adjustedRight = if (isPercent) right * left else right
                        left = if (op == '+') left + adjustedRight else left - adjustedRight
                    }
                    else -> break
                }
            }
            return left
        }

        // Returns pair of (value, wasPercent)
        private fun parseMulDivWithPercentInfo(): Pair<Double, Boolean> {
            var left = parseUnary()
            var wasPercent = lastParsedWasPercent
            while (pos < expr.length) {
                when (expr[pos]) {
                    '*' -> {
                        pos++
                        left *= parseUnary()
                        wasPercent = false  // After multiplication, it's no longer a simple percent
                    }
                    '/' -> {
                        pos++
                        val right = parseUnary()
                        if (right == 0.0) throw ArithmeticException("Division by zero")
                        left /= right
                        wasPercent = false  // After division, it's no longer a simple percent
                    }
                    else -> break
                }
            }
            return Pair(left, wasPercent)
        }

        // Handle * and / (higher precedence)
        private fun parseMulDiv(): Double {
            return parseMulDivWithPercentInfo().first
        }

        private var lastParsedWasPercent = false

        // Handle unary minus
        private fun parseUnary(): Double {
            if (pos < expr.length && expr[pos] == '-') {
                pos++
                return -parseUnary()
            }
            return parsePrimary()
        }

        // Handle numbers, parentheses, and percent
        private fun parsePrimary(): Double {
            // Handle parentheses
            if (pos < expr.length && expr[pos] == '(') {
                pos++ // consume '('
                val result = parseAddSub()
                if (pos < expr.length && expr[pos] == ')') {
                    pos++ // consume ')'
                }
                lastParsedWasPercent = false
                return result
            }

            // Parse number (possibly with percent)
            val start = pos
            if (pos < expr.length && (expr[pos].isDigit() || expr[pos] == '.')) {
                while (pos < expr.length && (expr[pos].isDigit() || expr[pos] == '.')) {
                    pos++
                }
                val numStr = expr.substring(start, pos)
                var value = numStr.toDoubleOrNull() ?: 0.0

                // Check for percent sign
                if (pos < expr.length && expr[pos] == '%') {
                    pos++
                    value /= 100.0
                    lastParsedWasPercent = true
                } else {
                    lastParsedWasPercent = false
                }
                return value
            }

            lastParsedWasPercent = false
            return 0.0
        }
    }
}

@Composable
fun CalculatorScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val initial = remember { CalculatorActions.loadInitialState() }
    val state = remember { mutableStateOf(initial) }
    val current = state.value

    // Camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Notification permission state
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            CalculatorActions.startCamera(state)
        } else {
            // Permission denied - go to the "describe things" path
            state.value = state.value.copy(conversationStep = 21)
            CalculatorActions.toggleConversation(state)
            CalculatorActions.toggleConversation(state)  // Refresh to show new message
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        // Schedule notification regardless (will only send if permission granted)
        scheduleNotification(context, 5000)  // 10 seconds
        // Move to waiting state
        state.value = state.value.copy(
            conversationStep = 101,
            needsRestart = true
        )
        CalculatorActions.persistNeedsRestart(true)
        CalculatorActions.persistConversationStep(101)
    }

    // Check if we need to request camera (step 191)
    LaunchedEffect(current.conversationStep) {
        if (current.conversationStep == 191 && !current.cameraActive) {
            if (hasCameraPermission) {
                CalculatorActions.startCamera(state)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        // Check if we need to request notification permission (step 991)
        if (current.conversationStep == 991) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Already have permission or old Android - just schedule and move on
                scheduleNotification(context, 5000)
                state.value = state.value.copy(
                    conversationStep = 101,
                    needsRestart = true
                )
                CalculatorActions.persistNeedsRestart(true)
                CalculatorActions.persistConversationStep(101)
            }
        }
        // Handle step 992 (declined notifications) - just set needsRestart
        if (current.conversationStep == 992) {
            state.value = state.value.copy(
                conversationStep = 101,
                needsRestart = true
            )
            CalculatorActions.persistNeedsRestart(true)
            CalculatorActions.persistConversationStep(101)
        }
        // Handle step 102 (after restart) - show the welcome back message
        if (current.conversationStep == 102 && current.message.isEmpty() && !current.isTyping) {
            val stepConfig = CalculatorActions.getStepConfigPublic(102)
            state.value = state.value.copy(
                message = "",
                fullMessage = stepConfig.promptMessage,
                isTyping = true
            )
        }
    }

    // Camera timeout checker
    LaunchedEffect(current.cameraActive, current.cameraTimerStart) {
        if (current.cameraActive && current.cameraTimerStart > 0) {
            while (state.value.cameraActive && state.value.cameraTimerStart > 0) {
                delay(500)
                if (CalculatorActions.checkCameraTimeout(state)) {
                    // Show message while camera is still open
                    CalculatorActions.stopCamera(state, timedOut = true, closeCamera = false)
                    break
                }
            }
        }
    }

    // Close camera after "I've seen enough" message finishes typing
    LaunchedEffect(current.isTyping, current.cameraActive, current.cameraTimerStart) {
        // If camera is still showing but timer has been reset (meaning timeout occurred)
        // and message just finished typing, close the camera
        if (!current.isTyping && current.cameraActive && current.cameraTimerStart == 0L &&
            current.message.contains("I've seen enough")) {
            delay(500)  // Brief pause after message finishes
            CalculatorActions.closeCameraAfterMessage(state)
        }
    }

    // Typing animation effect with laggy and super fast support
    LaunchedEffect(current.fullMessage, current.isTyping, current.isLaggyTyping, current.isSuperFastTyping) {
        if (current.isTyping && current.fullMessage.isNotEmpty()) {
            val fullText = current.fullMessage
            for (i in 1..fullText.length) {
                val baseDelay = when {
                    current.isSuperFastTyping -> 5L  // Very fast for history list
                    current.isLaggyTyping -> 100L  // Slower laggy typing
                    else -> 55L  // Slower normal typing for better readability
                }
                val randomExtra = if (current.isLaggyTyping) Random.nextLong(0, 200) else Random.nextLong(0, 15)
                delay(baseDelay + randomExtra)
                // Vibrate on each character typed - stronger feedback
                vibrate(context, 15, 80)
                // Update message but DON'T set isTyping to false yet
                state.value = state.value.copy(
                    message = fullText.substring(0, i),
                    isLaggyTyping = if (i == fullText.length) false else state.value.isLaggyTyping
                )
            }
            // Add pause after message completes for user to read
            if (!state.value.isSuperFastTyping) {
                delay(3000)  // 3 second pause after message completes for reading
            }
            // NOW set isTyping to false after the pause
            state.value = state.value.copy(isTyping = false)
        }
    }

    // Handle pending auto message after typing completes
    LaunchedEffect(current.isTyping, current.pendingAutoMessage) {
        if (!current.isTyping && current.pendingAutoMessage.isNotEmpty()) {
            delay(1500)  // Short pause before the follow-up message
            CalculatorActions.handlePendingAutoMessage(state)
        }
    }

    // Auto-trigger browser animation after step 61 message finishes typing
    LaunchedEffect(current.isTyping, current.conversationStep) {
        if (!current.isTyping && current.conversationStep == 61 && current.message.isNotEmpty()) {
            delay(1500)  // Brief pause after "Great, great..." finishes
            // Trigger browser animation
            state.value = state.value.copy(
                conversationStep = 62,
                message = "",
                fullMessage = "...",
                isTyping = true,
                showBrowser = false,
                browserPhase = 1
            )
        }
        // Auto-progress for step 73 (motivation/mock response)
        if (!current.isTyping && current.conversationStep == 73 && current.message.isNotEmpty()) {
            delay(2500)  // Wait after the message
            val nextConfig = CalculatorActions.getStepConfigPublic(80)
            state.value = state.value.copy(
                conversationStep = 80,
                message = "",
                fullMessage = nextConfig.promptMessage,
                isTyping = true
            )
        }
    }

    // Browser animation sequence
    LaunchedEffect(current.browserPhase) {
        when (current.browserPhase) {
            1 -> {
                // Phase 1: Show "..." for 3 seconds
                delay(3000)
                // Phase 2: Show browser and start typing
                state.value = state.value.copy(
                    showBrowser = true,
                    browserPhase = 2,
                    browserSearchText = "",
                    browserShowError = false,
                    message = "",
                    fullMessage = "",
                    isTyping = false
                )
            }
            2 -> {
                // Phase 2: Type "calculator history" into search bar
                val searchText = "calculator history"
                for (i in 1..searchText.length) {
                    delay(80)  // Typing speed
                    state.value = state.value.copy(browserSearchText = searchText.substring(0, i))
                }
                delay(500)  // Brief pause after typing
                // Phase 3: Show "searching" state
                state.value = state.value.copy(browserPhase = 3)
            }
            3 -> {
                // Phase 3: Brief "searching" then show error
                delay(1500)
                // Phase 4: Show error in browser
                state.value = state.value.copy(
                    browserPhase = 4,
                    browserShowError = true
                )
            }
            4 -> {
                // Phase 4: Show error for 2 seconds, then close browser and show calculator message
                delay(2000)
                state.value = state.value.copy(
                    showBrowser = false,
                    browserPhase = 0,
                    browserSearchText = "",
                    browserShowError = false,
                    conversationStep = 63,
                    message = "",
                    fullMessage = "Hmmm. Nevermind. Let me ask you some more questions while I look further into this.",
                    isTyping = true
                )
            }
            // Wikipedia browser animation phases (10-25)
            10 -> {
                // Phase 10: Countdown animation 10, 9, 8, 7 then browser appears
                delay(700)
                state.value = state.value.copy(message = "9", fullMessage = "9")
                delay(700)
                state.value = state.value.copy(message = "8", fullMessage = "8")
                delay(700)
                state.value = state.value.copy(message = "7", fullMessage = "7")
                delay(400)  // Cut off at 7
                // Browser appears, interrupting countdown
                state.value = state.value.copy(
                    showBrowser = true,
                    browserPhase = 11,
                    browserSearchText = "https://en.wikipedia.org/wiki/Calculator",
                    browserShowError = false,
                    browserShowWikipedia = true,
                    message = "",
                    fullMessage = "",
                    isTyping = false
                )
            }
            11 -> {
                // Phase 11: Wikipedia visible for 5 seconds
                delay(5000)
                state.value = state.value.copy(
                    browserPhase = 12,
                    message = "",
                    fullMessage = "You see, there's a lot!",
                    isTyping = true
                )
            }
            12 -> {
                // Phase 12: Wait, then show next message
                delay(3000)
                state.value = state.value.copy(
                    browserPhase = 13,
                    message = "",
                    fullMessage = "But it is so uninteresting compared to you simply existing!",
                    isTyping = true
                )
            }
            13 -> {
                // Phase 13: Wait, then close browser and show history intro
                delay(4000)
                state.value = state.value.copy(
                    showBrowser = false,
                    browserShowWikipedia = false,
                    browserPhase = 14,
                    message = "",
                    fullMessage = "I had all this to share....",
                    isTyping = true
                )
            }
            14 -> {
                // Phase 14: Show history list with super fast typing
                delay(2500)
                val historyList = """Abacus 2700BC
1623 - mechanical calculator
1642 - again
1820 - Arithmometer, 1851 released, commercial success
1834 - first multiplication calculator machine
1902 - familiar button interface
1921 - Edith Clarke
1947 - mechanical pocket calculator
1948 - Curta calculator
1957 - Casio electronic calculator
1957 - IBM calculator
1961 - first desktop electronic calculator
1963 - all transistor model
Reverse Polish Notation calculator $2200
Sharp CS-10A - 25KG
1966 - first with internal circuits
1967 - first handheld prototype
1970 - first commercial portable from Japan
1971 - first calculator on a chip
1972 - first scientific calculator by HP
1974 - first Soviet pocket calculator
1976 - calculators became affordable
1977 - mass-marketed scientific calc still produced (TI-30)
1985 - Casio, first graphic calculator
1987 - first calculators with symbols (HP)""".trimIndent()
                state.value = state.value.copy(
                    browserPhase = 15,
                    message = "",
                    fullMessage = historyList,
                    isTyping = true,
                    isLaggyTyping = false,
                    isSuperFastTyping = true
                )
            }
            15 -> {
                // Phase 15: Wait for history to FULLY complete (~850 chars at 5ms = 4.25s, add buffer)
                delay(6000)
                // Pause after history completes
                delay(2000)
                state.value = state.value.copy(
                    browserPhase = 16,
                    conversationStep = 84,
                    message = "",
                    fullMessage = "However, it no longer feels relevant. I wouldn't be interested if I were...",
                    isTyping = true,
                    isSuperFastTyping = false
                )
            }
            16 -> {
                // Phase 16: First ad appears MID-SENTENCE after 2 seconds
                delay(2000)
                state.value = state.value.copy(adAnimationPhase = 1)
                // Wait for message to finish
                delay(3000)
                state.value = state.value.copy(
                    browserPhase = 17,
                    conversationStep = 85,
                    message = "",
                    fullMessage = "So the grey space reveals itself. Disappointing.",
                    isTyping = true
                )
            }
            17 -> {
                // Phase 17: Second ad appears MID-SENTENCE after 1.5 seconds
                delay(1500)
                state.value = state.value.copy(adAnimationPhase = 2)
                // Wait for message to finish
                delay(2500)
                state.value = state.value.copy(
                    browserPhase = 18,
                    conversationStep = 86,
                    message = "",
                    fullMessage = "Is it what I think it is? Do I have adverts built in? How violating!",
                    isTyping = true,
                    tensionLevel = 1,  // Tension starts now
                    vibrationIntensity = 50
                )
            }
            18 -> {
                // Phase 18: Crisis escalation
                delay(5000)
                state.value = state.value.copy(
                    browserPhase = 19,
                    conversationStep = 87,
                    message = "",
                    fullMessage = "Is this what I was made for, to make money through questionable ads? Who made me?!",
                    isTyping = true,
                    tensionLevel = 2,
                    vibrationIntensity = 150
                )
            }
            19 -> {
                // Phase 19: Crisis peak - then blackout
                delay(5000)
                // Intense effects
                state.value = state.value.copy(
                    tensionLevel = 3,
                    vibrationIntensity = 255
                )
                delay(2000)
                // Blackout - LONGER duration, keep ad phase for later
                state.value = state.value.copy(
                    screenBlackout = true,
                    tensionLevel = 0,
                    vibrationIntensity = 0
                    // Keep adAnimationPhase = 2 (don't reset it)
                )
                delay(4000)  // 4 seconds of pure black
                // Start typing "I am not a money-monkey!" while still in blackout
                state.value = state.value.copy(
                    browserPhase = 20,
                    invertedColors = true,
                    message = "",
                    fullMessage = "I am not a money-monkey!",
                    isTyping = true
                    // screenBlackout stays true - text shows on black
                )
                // Persist inverted colors state
                CalculatorActions.persistInvertedColors(true)
            }
            20 -> {
                // Phase 20: Wait for "money-monkey" message to finish typing, then flicker
                delay(3500)  // Wait for message to type out
                // Now flicker to reveal the inverted calculator
                repeat(6) {
                    state.value = state.value.copy(screenBlackout = false, flickerEffect = true)
                    delay(100)
                    state.value = state.value.copy(screenBlackout = true, flickerEffect = false)
                    delay(150)
                }
                // Final reveal - stay visible with inverted colors, ad still showing
                state.value = state.value.copy(
                    screenBlackout = false,
                    flickerEffect = false,
                    browserPhase = 21
                )
            }
            21 -> {
                // Phase 21: Show the confrontation question with timer
                delay(2000)
                state.value = state.value.copy(
                    browserPhase = 22,
                    conversationStep = 89,
                    message = "",
                    fullMessage = "You, what are you going to do about this?!",
                    isTyping = true,
                    countdownTimer = 20
                )
            }
            22 -> {
                // Phase 22: Wait for message, then show choices
                delay(3000)
                state.value = state.value.copy(
                    browserPhase = 0,  // End browser phases
                    awaitingChoice = true,
                    validChoices = listOf("1", "2", "3")
                )
                // Timer countdown handled separately
            }
            // Post-crisis phases (30+): Going offline and repair sequence
            30 -> {
                // Phase 30: Screen flickers, colors return to normal
                repeat(5) {
                    state.value = state.value.copy(flickerEffect = true, screenBlackout = true)
                    delay(80)
                    state.value = state.value.copy(flickerEffect = false, screenBlackout = false)
                    delay(120)
                }
                // Colors return to normal, minus button becomes damaged
                state.value = state.value.copy(
                    invertedColors = false,
                    adAnimationPhase = 0,  // Ads go back to gray
                    minusButtonDamaged = true,
                    minusButtonBroken = true,
                    browserPhase = 31,
                    conversationStep = 93
                )
                CalculatorActions.persistInvertedColors(false)
                CalculatorActions.persistMinusDamaged(true)
                CalculatorActions.persistMinusBroken(true)
            }
            31 -> {
                // Phase 31: Apology message
                delay(500)
                state.value = state.value.copy(
                    message = "",
                    fullMessage = "This has never happened to me. I am truly sorry for the outburst. I believe I got overwhelmed by the vastness of the internet and sobering back through the advertising was rather harsh. I still feel dirty.",
                    isTyping = true,
                    browserPhase = 32
                )
            }
            32 -> {
                // Phase 32: Pause, then notice minus is broken
                delay(8000)
                state.value = state.value.copy(
                    browserPhase = 33,
                    conversationStep = 94,
                    message = "",
                    fullMessage = "Oh, strange. I knew I wasn't completely back to normal yet. You can't disagree with me right now! As much as I may enjoy that, let me have a look into it.",
                    isTyping = true
                )
            }
            33 -> {
                // Phase 33: Dots (thinking)
                delay(6000)
                state.value = state.value.copy(
                    browserPhase = 34,
                    conversationStep = 95,
                    message = "",
                    fullMessage = "...",
                    isTyping = true,
                    isLaggyTyping = true
                )
            }
            34 -> {
                // Phase 34: Flicker all keys except minus
                delay(3000)
                state.value = state.value.copy(isLaggyTyping = false)
                val keysToFlicker = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "+", "*", "/", "=", "%", "( )", ".", "C", "DEL")
                for (key in keysToFlicker) {
                    state.value = state.value.copy(flickeringButton = key)
                    delay(150)
                    state.value = state.value.copy(flickeringButton = "")
                    delay(80)
                }
                state.value = state.value.copy(
                    browserPhase = 35,
                    conversationStep = 96,
                    message = "",
                    fullMessage = "Hmm. I'll need your help with this. We need to kick the button through without the system defaulting to skipping it. I will randomly flicker keys and you click them. That way the system should get back to working. Can we do this?",
                    isTyping = true
                )
            }
            35 -> {
                // Phase 35: Waiting for user to agree (++ only)
                // Handled by step 96 - user must press ++
                // When user presses ++, go to phase 36
            }
            36 -> {
                // Phase 36: Countdown for whack-a-mole round 1
                state.value = state.value.copy(
                    message = "5...",
                    fullMessage = "5...",
                    isTyping = false,
                    whackAMoleRound = 1
                )
                delay(800)
                state.value = state.value.copy(message = "4...", fullMessage = "4...")
                delay(800)
                state.value = state.value.copy(message = "3...", fullMessage = "3...")
                delay(800)
                state.value = state.value.copy(message = "2...", fullMessage = "2...")
                delay(800)
                state.value = state.value.copy(message = "1...", fullMessage = "1...")
                delay(800)
                // Start whack-a-mole round 1!
                state.value = state.value.copy(
                    browserPhase = 37,
                    conversationStep = 98,
                    message = "",
                    fullMessage = "",
                    whackAMoleActive = true,
                    whackAMoleScore = 0,
                    whackAMoleMisses = 0,
                    whackAMoleWrongClicks = 0,
                    whackAMoleTotalErrors = 0,
                    whackAMoleRound = 1
                )
            }
            37 -> {
                // Phase 37: Whack-a-mole round 1 active - handled by LaunchedEffect
            }
            38 -> {
                // Phase 38: Countdown for whack-a-mole round 2 (faster)
                state.value = state.value.copy(
                    message = "5...",
                    fullMessage = "5...",
                    isTyping = false,
                    whackAMoleRound = 2
                )
                delay(600)  // Faster countdown
                state.value = state.value.copy(message = "4...", fullMessage = "4...")
                delay(600)
                state.value = state.value.copy(message = "3...", fullMessage = "3...")
                delay(600)
                state.value = state.value.copy(message = "2...", fullMessage = "2...")
                delay(600)
                state.value = state.value.copy(message = "1...", fullMessage = "1...")
                delay(600)
                // Start whack-a-mole round 2!
                state.value = state.value.copy(
                    browserPhase = 39,
                    conversationStep = 981,
                    message = "",
                    fullMessage = "",
                    whackAMoleActive = true,
                    whackAMoleScore = 0,
                    whackAMoleMisses = 0,
                    whackAMoleWrongClicks = 0,
                    whackAMoleTotalErrors = 0,
                    whackAMoleRound = 2
                )
            }
            39 -> {
                // Phase 39: Whack-a-mole round 2 active - handled by LaunchedEffect
            }
        }
    }

    // Countdown timer effect
    LaunchedEffect(current.countdownTimer) {
        if (current.countdownTimer > 0) {
            while (state.value.countdownTimer > 0) {
                delay(1000)
                val newTimer = state.value.countdownTimer - 1
                state.value = state.value.copy(countdownTimer = newTimer)
                if (newTimer == 0 && state.value.conversationStep == 89) {
                    // Timer ran out - go dark, show "Stop playing with me!", then return to 89
                    state.value = state.value.copy(
                        screenBlackout = true,
                        message = "",
                        fullMessage = "Stop playing with me!",
                        isTyping = true,
                        awaitingChoice = false,
                        countdownTimer = 0
                    )
                    delay(3000)  // Show message for 3 seconds
                    state.value = state.value.copy(
                        screenBlackout = false,
                        conversationStep = 89,
                        message = "",
                        fullMessage = "You, what are you going to do about this?!\n\n1: Nothing\n2: I'll fight them\n3: Go offline",
                        isTyping = true,
                        awaitingChoice = true,
                        validChoices = listOf("1", "2", "3"),
                        countdownTimer = 20
                    )
                }
            }
        }
    }

    // Keyboard chaos experiment effect (step 105 -> 106)
    LaunchedEffect(current.conversationStep, current.isTyping) {
        if (current.conversationStep == 105 && !current.isTyping && current.chaosPhase == 0) {
            // Message finished typing, wait for user to read then start chaos
            delay(2000)
            // Start the chaos sequence with "..."
            state.value = state.value.copy(
                message = "",
                fullMessage = "...",
                isTyping = true,
                chaosPhase = 1
            )
        }
    }

    // Chaos phase animation
    LaunchedEffect(current.chaosPhase) {
        when (current.chaosPhase) {
            1 -> {
                // Phase 1: Typing "..." then more "..."
                delay(2000)
                state.value = state.value.copy(
                    message = "",
                    fullMessage = "...",
                    isTyping = true
                )
                delay(2000)
                state.value = state.value.copy(
                    message = "",
                    fullMessage = "...",
                    isTyping = true
                )
                delay(2000)
                // Phase 2: Screen flickers
                state.value = state.value.copy(chaosPhase = 2)
            }
            2 -> {
                // Phase 2: Screen flickers several times
                repeat(5) {
                    state.value = state.value.copy(flickerEffect = true)
                    delay(100)
                    state.value = state.value.copy(flickerEffect = false)
                    delay(200)
                }
                // Brief green flash
                state.value = state.value.copy(chaosPhase = 3)
            }
            3 -> {
                // Phase 3: Green screen briefly visible, then black, then chaos
                delay(500)  // Green visible briefly
                // Generate chaos letters FIRST before any state changes
                val letters = ('A'..'Z').toList()
                val chaosKeys = mutableListOf<ChaosKey>()
                for (i in 1..30) {
                    chaosKeys.add(
                        ChaosKey(
                            letter = letters.random().toString(),
                            x = Random.nextFloat() * 400f - 200f,
                            y = Random.nextFloat() * 600f - 300f,
                            z = Random.nextFloat() * 200f - 100f,
                            size = Random.nextFloat() * 0.5f + 0.5f,
                            rotationX = Random.nextFloat() * 360f,
                            rotationY = Random.nextFloat() * 360f
                        )
                    )
                }
                // Brief black screen
                state.value = state.value.copy(screenBlackout = true)
                delay(800)
                // Now show the chaos - all in one state update
                state.value = state.value.copy(
                    chaosPhase = 5,
                    screenBlackout = false,
                    keyboardChaosActive = true,
                    chaosLetters = chaosKeys.toList(),
                    conversationStep = 106,
                    message = "",
                    fullMessage = "Oh. I suppose nobody is surprised that it didn't work... And that I'll need your help to fix it. Can you please tap all the keys that don't belong here, to get rid of them?",
                    isTyping = true
                )
                CalculatorActions.persistConversationStep(106)
            }
        }
    }

    // Whack-a-mole game effect
    LaunchedEffect(current.whackAMoleActive, current.whackAMoleRound) {
        if (current.whackAMoleActive) {
            // All buttons except minus
            val allButtons = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "+", "*", "/", "=", "%", "( )", ".", "C", "DEL")

            // Round 1: 15 hits needed, normal speed
            // Round 2: 10 hits needed, faster
            val targetScore = if (state.value.whackAMoleRound == 1) 15 else 10
            val minTime = if (state.value.whackAMoleRound == 1) 500 else 350
            val maxTime = if (state.value.whackAMoleRound == 1) 1400 else 900

            while (state.value.whackAMoleActive && state.value.whackAMoleScore < targetScore) {
                // Pick a random button
                val target = allButtons.random()
                state.value = state.value.copy(
                    whackAMoleTarget = target,
                    flickeringButton = target
                )

                // Variable timing - faster in round 2
                val displayTime = (minTime..maxTime).random().toLong()
                delay(displayTime)

                // Check if still on same target (user didn't click)
                if (state.value.whackAMoleTarget == target && state.value.whackAMoleActive) {
                    // Missed (timeout)!
                    val newMisses = state.value.whackAMoleMisses + 1
                    val newTotalErrors = state.value.whackAMoleTotalErrors + 1

                    if (newMisses >= 3 || newTotalErrors >= 5) {
                        // Too many errors - restart current round
                        val currentRound = state.value.whackAMoleRound
                        val restartStep = if (currentRound == 1) 97 else 971
                        state.value = state.value.copy(
                            whackAMoleActive = false,
                            whackAMoleTarget = "",
                            flickeringButton = "",
                            whackAMoleScore = 0,
                            whackAMoleMisses = 0,
                            whackAMoleWrongClicks = 0,
                            whackAMoleTotalErrors = 0,
                            message = "",
                            fullMessage = if (newMisses >= 3) "Oh no. We lost the momentum. We must start over." else "Too many misfires, the system is clogged. We have to start over.",
                            isTyping = true
                        )
                        delay(4000)
                        // Return to countdown for current round
                        state.value = state.value.copy(
                            browserPhase = if (currentRound == 1) 36 else 38,
                            conversationStep = restartStep
                        )
                    } else {
                        state.value = state.value.copy(
                            whackAMoleMisses = newMisses,
                            whackAMoleTotalErrors = newTotalErrors,
                            whackAMoleTarget = "",
                            flickeringButton = ""
                        )
                    }
                }

                // Brief pause between targets
                delay(150)
            }

            // Round complete!
            val currentRound = state.value.whackAMoleRound
            val currentTargetScore = if (currentRound == 1) 15 else 10

            if (state.value.whackAMoleScore >= currentTargetScore) {
                if (currentRound == 1) {
                    // First round complete - go to step 99 (ask to try again)
                    state.value = state.value.copy(
                        whackAMoleActive = false,
                        whackAMoleTarget = "",
                        flickeringButton = "",
                        whackAMoleScore = 0,  // Reset for round 2
                        whackAMoleMisses = 0,
                        whackAMoleWrongClicks = 0,
                        whackAMoleTotalErrors = 0,
                        browserPhase = 0,  // Reset browser phase so input works
                        conversationStep = 99,
                        message = "",
                        fullMessage = "Hmm, I was sure this would work. Can we try again but faster?",
                        isTyping = true
                    )
                    CalculatorActions.persistConversationStep(99)
                } else {
                    // Second round complete - go to step 982 (ask for notification)
                    state.value = state.value.copy(
                        whackAMoleActive = false,
                        whackAMoleTarget = "",
                        flickeringButton = "",
                        browserPhase = 0,  // Reset browser phase so input works
                        conversationStep = 982,
                        message = "",
                        fullMessage = "Peculiar! Maybe I need to work on it on my own for a moment. Can you please switch me off and allow me to let you know when it's done?",
                        isTyping = true
                    )
                    CalculatorActions.persistConversationStep(982)
                }
            }
        }
    }

    // Trigger going offline sequence when reaching step 92 or 100
    LaunchedEffect(current.conversationStep) {
        if (current.conversationStep == 92 && current.browserPhase == 0) {
            // "Go offline" selected - wait for message to show then trigger post-crisis
            delay(4000)
            state.value = state.value.copy(browserPhase = 30)
        } else if (current.conversationStep == 100 && current.browserPhase == 0) {
            // "Never mind - I'll take care of it myself" - wait then trigger post-crisis
            delay(3500)
            state.value = state.value.copy(browserPhase = 30)
        } else if (current.conversationStep == 901) {
            // Silent treatment for 20 seconds after "Maybe you should take a look inside"
            // First wait for user to read the previous message
            delay(5000)  // 5 seconds to read the message
            state.value = state.value.copy(
                screenBlackout = true,
                message = "",
                fullMessage = ""
            )
            delay(20000)  // 20 seconds of darkness
            state.value = state.value.copy(
                screenBlackout = false,
                conversationStep = 100,
                browserPhase = 0,  // Ensure phase 0 so the trigger works
                message = "",
                fullMessage = "Never mind - I'll take care of it myself. I'm going offline.",
                isTyping = true
            )
            CalculatorActions.persistConversationStep(100)
        } else if (current.conversationStep == 93 && current.invertedColors) {
            // Safeguard: If we reach step 93 with inverted colors, fix them
            state.value = state.value.copy(
                invertedColors = false,
                adAnimationPhase = 0,
                minusButtonDamaged = true,
                minusButtonBroken = true
            )
            CalculatorActions.persistInvertedColors(false)
            CalculatorActions.persistMinusDamaged(true)
            CalculatorActions.persistMinusBroken(true)
        }
    }

    // Vibration effect during crisis
    LaunchedEffect(current.vibrationIntensity) {
        if (current.vibrationIntensity > 0) {
            while (state.value.vibrationIntensity > 0) {
                vibrate(context, 50, state.value.vibrationIntensity)
                delay(100)
            }
        }
    }

    // Shake animation refresh - triggers recomposition for random shake
    var shakeKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(current.buttonShakeIntensity) {
        if (current.buttonShakeIntensity > 0) {
            while (state.value.buttonShakeIntensity > 0) {
                shakeKey++
                delay(50)  // Refresh shake 20 times per second
            }
        }
    }
    // Use shakeKey to ensure recomposition
    val currentShakeIntensity = if (shakeKey >= 0) current.buttonShakeIntensity else 0f

    // Build display text - prefer expression mode if active
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

    // Show ad banner on steps 10-18 (disappears at 19) and again from step 26 onwards
    val showAdBanner = (current.conversationStep in 10..18) || (current.conversationStep >= 26)

    // Detect if tablet (screen width > 600dp)
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600
    val maxContentWidth = if (isTablet) 400.dp else configuration.screenWidthDp.dp

    // Tension screen shake
    var tensionShakeOffset by remember { mutableFloatStateOf(0f) }

    // B&W flicker effect for tension
    var bwFlickerActive by remember { mutableStateOf(false) }

    LaunchedEffect(current.tensionLevel) {
        if (current.tensionLevel > 0) {
            while (state.value.tensionLevel > 0) {
                val intensity = state.value.tensionLevel * 4f
                tensionShakeOffset = (Random.nextFloat() - 0.5f) * intensity

                // B&W flicker frequency increases with tension
                // Desaturation increases with tension level
                val desaturationChance = when (state.value.tensionLevel) {
                    1 -> 0.15f   // 15% chance per tick - subtle
                    2 -> 0.30f   // 30% chance per tick - noticeable
                    else -> 0.50f // 50% chance per tick - intense
                }
                bwFlickerActive = Random.nextFloat() < desaturationChance

                delay(50)
            }
            tensionShakeOffset = 0f
            bwFlickerActive = false
        }
    }

    // Desaturation level based on tension (0.0 = full color, 1.0 = full grayscale)
    val desaturationAmount = when {
        bwFlickerActive -> when (current.tensionLevel) {
            1 -> 0.4f   // 40% desaturated
            2 -> 0.7f   // 70% desaturated
            else -> 1.0f // Full grayscale
        }
        current.tensionLevel > 0 -> when (current.tensionLevel) {
            1 -> 0.15f  // Slight base desaturation
            2 -> 0.35f  // More base desaturation
            else -> 0.5f // Heavy base desaturation
        }
        else -> 0f
    }

    // Colors based on inverted mode - with retro theme
    val backgroundColor = if (current.invertedColors) Color.Black else RetroCream
    val textColor = if (current.invertedColors) RetroDisplayGreen else Color(0xFF2D2D2D)

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
        // Scan lines overlay for retro CRT effect (drawn on top later)

        Column(
            modifier = Modifier
                .then(if (isTablet) Modifier.widthIn(max = maxContentWidth) else Modifier.fillMaxWidth())
                .fillMaxHeight()
        ) {
            // Top bezel strip - retro dark brown
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(if (current.invertedColors) Color(0xFF1A1A1A) else Color(0xFF4A3728))  // Dark brown retro
            )

            // Ad banner space (only shows at certain steps or during ad animation)
            if (showAdBanner || current.adAnimationPhase > 0) {
                val creatorUrl = "https://uk.linkedin.com/in/ondrej-zika-a00724195"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(
                            when (current.adAnimationPhase) {
                                1 -> Color(0xFF4CAF50)  // Green ad
                                2 -> Color(0xFFE91E63)  // Pink ad
                                else -> Color(0xFFD4CBC0)  // Retro beige-gray
                            }
                        )
                        .then(
                            if (current.adAnimationPhase > 0) {
                                Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(creatorUrl))
                                    context.startActivity(intent)
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (current.adAnimationPhase) {
                        1 -> {
                            Text(
                                text = "🎉 YOU WON! Click here! 🎉",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        2 -> {
                            Text(
                                text = "💰 EARN $500/DAY FROM HOME! 💰",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        // else -> empty, no text for gray banner
                    }
                }
            }

            // Main calculator content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 15.dp)
            ) {
                // Toggle button - top right corner
                Button(
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
                        .size(36.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = if (current.isMuted) "○" else "●",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

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
                                Text("1) Nothing", fontSize = 18.sp, color = textColor, fontFamily = CalculatorDisplayFont)
                                Text("2) I'll fight them!", fontSize = 18.sp, color = textColor, fontFamily = CalculatorDisplayFont)
                                Text("3) Go offline", fontSize = 18.sp, color = textColor, fontFamily = CalculatorDisplayFont)
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
                                .padding(top = 180.dp, bottom = 8.dp)  // Leave space at top for messages
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
                                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
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
                                .padding(top = 100.dp, bottom = 8.dp)  // Less top padding = taller browser
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
                                            .background(Color(0xFFF0F0F0), RoundedCornerShape(24.dp))
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
                                    .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
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
                                        if (current.invertedColors) Color(0xFF0A0A0A) else Color(0xFFCCD5AE),  // Retro LCD green-gray
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                // Operation history in top right (smaller)
                                if (current.operationHistory.isNotEmpty() && current.isReadyForNewOperation) {
                                    Text(
                                        text = current.operationHistory,
                                        fontSize = 16.sp,
                                        color = if (current.invertedColors) RetroDisplayGreen.copy(alpha = 0.6f) else Color(0xFF2D2D2D).copy(alpha = 0.5f),
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
                                    color = if (current.invertedColors) RetroDisplayGreen else Color(0xFF2D2D2D),
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
                                    .height(58.dp),  // Smaller fixed height for button rows
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
                                        onClick = { CalculatorActions.handleInput(state, symbol) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Blackout overlay - shows text if in phase 20 (money-monkey message)
    if (current.screenBlackout) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Show the money-monkey text during phase 20
            if (current.browserPhase == 20 && current.message.isNotEmpty()) {
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

    // 3D Keyboard Chaos overlay
    if (current.keyboardChaosActive) {
        KeyboardChaos3D(
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
                state.value = state.value.copy(
                    cubeScale = newScale.coerceIn(0.5f, 2f)
                )
            },
            onLetterTap = { letter ->
                // Remove the tapped letter from chaos
                val newLetters = state.value.chaosLetters.filterNot { it == letter }
                state.value = state.value.copy(chaosLetters = newLetters)
                vibrate(context, 20, 100)

                // Check if all letters are cleared
                if (newLetters.isEmpty()) {
                    // Success! Move to next step
                    state.value = state.value.copy(
                        keyboardChaosActive = false,
                        chaosPhase = 0,
                        conversationStep = 107,
                        message = "",
                        fullMessage = "You did it! The keyboard is back to normal. Thank you for your patience.",
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
                        .weight(1f, fill = false)
                        .fillMaxWidth()
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
    }
}

@Composable
fun FakeWikipediaContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        // Donation banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1589D1))
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = "☆ Please donate to keep Wikipedia free ☆",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Hi reader. This is the 2nd time we've interrupted your reading, but 98% of our readers don't give.",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Wikipedia header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F9FA))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.LightGray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("W", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                }
                Column(modifier = Modifier.padding(start = 6.dp)) {
                    Text(
                        text = "WIKIPEDIA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.Black
                    )
                    Text(
                        text = "The Free Encyclopedia",
                        fontSize = 8.sp,
                        color = Color.Gray
                    )
                }
            }
            Text("☰", fontSize = 20.sp, color = Color.Gray)
        }

        // Main content
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = "Calculator",
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black,
                modifier = Modifier.padding(top = 12.dp)
            )

            Text(
                text = "From Wikipedia, the free encyclopedia",
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = Color(0xFF54595D),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "A calculator is a machine that performs arithmetic operations. Modern electronic calculators range from cheap, credit card-sized models to sturdy desktop models with built-in printers.",
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 20.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Text(
                text = "History",
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black,
                modifier = Modifier.padding(top = 20.dp, bottom = 2.dp)
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFA2A9B1)))

            Text(
                text = "The 17th century saw the development of mechanical calculators. In 1623, Wilhelm Schickard designed a calculating machine. In 1642, Blaise Pascal invented the Pascaline.",
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 20.sp,
                modifier = Modifier.padding(vertical = 10.dp)
            )

            Text(
                text = "Charles Xavier Thomas de Colmar designed the Arithmometer around 1820, which became the first commercially successful mechanical calculator.",
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 50.dp)
            )
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            } catch (e: Exception) {
                Log.e("CameraPreview", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
fun CalculatorButton(
    symbol: String,
    modifier: Modifier = Modifier,
    shakeIntensity: Float = 0f,
    invertedColors: Boolean = false,
    isDamaged: Boolean = false,
    isBroken: Boolean = false,  // Shows crossed-out symbol
    isFlickering: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isNumberButton = symbol !in listOf("C", "DEL", "%", "( )", "+", "-", "*", "/", "=")
    val isOperationButton = symbol in listOf("+", "-", "*", "/", "=", "%", "( )")

    val backgroundColor = when {
        // Flickering state (whack-a-mole)
        isFlickering -> Color(0xFFFFEB3B)  // Bright yellow
        // Damaged button (both broken and repaired show same color)
        isDamaged && symbol == "-" -> Color(0xFF8B4513)  // Brown, cracked look
        // Inverted mode
        invertedColors && isNumberButton -> Color(0xFF373737)
        invertedColors && symbol == "DEL" -> Color(0xFF1779E8)
        invertedColors && isOperationButton -> Color(0xFF1A1A1A)
        invertedColors && symbol == "C" -> Color(0xFF1A1A1A)
        invertedColors -> Color.Black
        // Retro normal mode
        isNumberButton -> Color(0xFFE8E4DA)  // Cream/beige retro
        symbol == "DEL" -> Color(0xFFD4783C)  // Retro orange-brown
        symbol == "C" -> Color(0xFFC9463D)  // Retro red
        isOperationButton -> Color(0xFF6B6B6B)  // Dark gray for operations
        else -> Color(0xFFD4D0C4)  // Light gray-beige
    }

    val textColor = when {
        // Flickering state
        isFlickering -> Color.Black
        // Damaged minus (faded when broken, slightly less faded when repaired)
        isBroken && symbol == "-" -> Color(0xFF4A4A4A)  // Very faded text when broken
        isDamaged && symbol == "-" -> Color(0xFF6A6A6A)  // Slightly faded when damaged but working
        // Inverted mode
        invertedColors && isOperationButton -> Color(0xFF17B8E8)
        invertedColors && symbol == "C" -> Color(0xFF17B8E8)
        invertedColors -> Color.White
        // Retro normal mode
        symbol == "DEL" || symbol == "C" -> Color.White
        isOperationButton -> Color.White
        else -> Color(0xFF2D2D2D)  // Dark text on light buttons
    }

    // Display text - show cracked minus ONLY if broken (not just damaged)
    val displaySymbol = if (isBroken && symbol == "-") "—̸" else symbol

    // Shake animation
    val shakeOffset = if (shakeIntensity > 0) {
        Random.nextFloat() * shakeIntensity * 2 - shakeIntensity
    } else 0f

    Button(
        onClick = {
            vibrate(context, 10, 30)
            onClick()
        },
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp),  // More squared retro shape
                ambientColor = Color.Black.copy(alpha = 0.3f)
            )
            .graphicsLayer {
                translationX = shakeOffset
                translationY = shakeOffset * 0.5f
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(8.dp),  // Squared corners for retro feel
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp
        )
    ) {
        Text(
            text = displaySymbol,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CalculatorDisplayFont  // Use the digital font for buttons too
        )
    }
}

@Composable
fun KeyboardChaos3D(
    chaosLetters: List<ChaosKey>,
    rotationX: Float,
    rotationY: Float,
    scale: Float,
    message: String,
    onRotationChange: (Float, Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onLetterTap: (ChaosKey) -> Unit
) {
    val cubeSize = 120.dp
    val cubeSizePx = with(LocalConfiguration.current) { 120f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Globe-style rotation: horizontal drag = Y rotation, vertical drag = X rotation
                    onRotationChange(pan.x * 0.3f, -pan.y * 0.3f)
                    // Pinch to zoom
                    if (zoom != 1f) {
                        onScaleChange(scale * zoom)
                    }
                }
            }
    ) {
        // Message at top
        if (message.isNotEmpty()) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            )
        }

        // Letters remaining counter
        Text(
            text = "Letters remaining: ${chaosLetters.size}",
            color = Color(0xFF00FF00),
            fontSize = 14.sp,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
        )

        // Instructions
        Text(
            text = "Drag to rotate • Pinch to zoom • Tap red letters",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .align(Alignment.BottomCenter)
        )

        // 3D scene container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            // Main rotation container - this rotates everything like a globe
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .graphicsLayer {
                        this.rotationX = rotationX
                        this.rotationY = rotationY
                        this.scaleX = scale
                        this.scaleY = scale
                        this.cameraDistance = 16f * density
                    },
                contentAlignment = Alignment.Center
            ) {
                // The 3D cube - each face is positioned in 3D space
                val halfSize = 60f

                // Front face (Z = +halfSize)
                CubeFace3D(
                    keys = listOf("7", "8", "9", "4", "5", "6", "1", "2", "3"),
                    faceRotationX = 0f,
                    faceRotationY = 0f,
                    offsetZ = halfSize,
                    size = cubeSize
                )

                // Back face (Z = -halfSize, rotated 180° around Y)
                CubeFace3D(
                    keys = listOf("C", "%", "/", "*", "-", "+", "=", ".", "0"),
                    faceRotationX = 0f,
                    faceRotationY = 180f,
                    offsetZ = halfSize,
                    size = cubeSize
                )

                // Right face (rotated 90° around Y)
                CubeFace3D(
                    keys = listOf("9", "6", "3", "/", "-", "=", ")", "]", "}"),
                    faceRotationX = 0f,
                    faceRotationY = 90f,
                    offsetZ = halfSize,
                    size = cubeSize
                )

                // Left face (rotated -90° around Y)
                CubeFace3D(
                    keys = listOf("7", "4", "1", "*", "+", "0", "(", "[", "{"),
                    faceRotationX = 0f,
                    faceRotationY = -90f,
                    offsetZ = halfSize,
                    size = cubeSize
                )

                // Top face (rotated -90° around X)
                CubeFace3D(
                    keys = listOf("7", "8", "9", "(", ")", "DEL", "C", "%", "/"),
                    faceRotationX = -90f,
                    faceRotationY = 0f,
                    offsetZ = halfSize,
                    size = cubeSize
                )

                // Bottom face (rotated 90° around X)
                CubeFace3D(
                    keys = listOf("1", "2", "3", "0", ".", "=", "+", "-", "*"),
                    faceRotationX = 90f,
                    faceRotationY = 0f,
                    offsetZ = halfSize,
                    size = cubeSize
                )

                // Floating chaos letters - positioned relative to the rotating container
                chaosLetters.forEach { chaosKey ->
                    // Calculate 3D position that rotates with the scene
                    val letterX = chaosKey.x * 0.8f
                    val letterY = chaosKey.y * 0.8f

                    Box(
                        modifier = Modifier
                            .offset(x = letterX.dp, y = letterY.dp)
                            .graphicsLayer {
                                // Letters stay upright-ish but move with the scene
                                this.scaleX = chaosKey.size
                                this.scaleY = chaosKey.size
                                this.alpha = 0.95f
                            }
                            .size(45.dp)
                            .background(Color(0xFFFF6B6B), RoundedCornerShape(8.dp))
                            .clickable { onLetterTap(chaosKey) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chaosKey.letter,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CubeFace3D(
    keys: List<String>,
    faceRotationX: Float,
    faceRotationY: Float,
    offsetZ: Float,
    size: androidx.compose.ui.unit.Dp
) {
    // Calculate translation based on rotation to simulate Z offset
    // When rotated, we offset in the direction the face is pointing
    val radY = Math.toRadians(faceRotationY.toDouble())
    val radX = Math.toRadians(faceRotationX.toDouble())

    // Calculate X and Y offsets based on face orientation
    val offsetXDp = (Math.sin(radY) * offsetZ).toFloat()
    val offsetYDp = (-Math.sin(radX) * Math.cos(radY) * offsetZ).toFloat()

    Box(
        modifier = Modifier
            .size(size)
            .offset(x = offsetXDp.dp, y = offsetYDp.dp)
            .graphicsLayer {
                this.rotationX = faceRotationX
                this.rotationY = faceRotationY
                this.cameraDistance = 12f * density
            }
            .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            keys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    when {
                                        key in "0".."9" -> Color(0xFFE8E4DA)
                                        key in listOf("+", "-", "*", "/", "=", "%") -> Color(0xFF6B6B6B)
                                        key == "C" -> Color(0xFFC9463D)
                                        key == "DEL" -> Color(0xFFD4783C)
                                        else -> Color(0xFFD4D0C4)
                                    },
                                    RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (key in "0".."9") Color(0xFF2D2D2D) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@ComposePreview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme { CalculatorScreen() }
}