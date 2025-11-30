package com.example.justacalculator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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

private val CalculatorDisplayFont = FontFamily(
    Font(R.font.digital_7, FontWeight.Normal)
)

private val AccentOrange = Color(0xFFE88617)

private const val PREFS_NAME = "just_a_calculator_prefs"
private const val PREF_EQUALS_COUNT = "equals_count"
private const val PREF_MESSAGE = "last_message"
private const val PREF_CONVO_STEP = "conversation_step"
private const val PREF_IN_CONVERSATION = "in_conversation"
private const val PREF_AWAITING_NUMBER = "awaiting_number"
private const val PREF_EXPECTED_NUMBER = "expected_number"
private const val PREF_TIMEOUT_UNTIL = "timeout_until"
private const val PREF_MUTED = "muted"

data class CalculatorState(
    val number1: String = "0",
    val number2: String = "",
    val operation: String? = null,
    val isReadyForNewOperation: Boolean = true,
    val lastExpression: String = "",
    val equalsCount: Int = 0,
    val message: String = "",
    val fullMessage: String = "",
    val isTyping: Boolean = false,
    val isLaggyTyping: Boolean = false,  // For the struggling/processing effect
    val inConversation: Boolean = false,
    val conversationStep: Int = 0,
    val awaitingNumber: Boolean = false,
    val expectedNumber: String = "",
    val timeoutUntil: Long = 0L,
    val isMuted: Boolean = false,
    val isEnteringAnswer: Boolean = false,
    // Camera related
    val cameraActive: Boolean = false,
    val cameraTimerStart: Long = 0L,
    val awaitingChoice: Boolean = false,  // For multiple choice questions
    val validChoices: List<String> = emptyList(),  // Valid choice numbers
    val pendingAutoMessage: String = "",  // Message to show automatically after delay
    val pendingAutoStep: Int = -1  // Step to go to after auto message
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
    private const val CAMERA_TIMEOUT_MS = 15000L  // 15 seconds

    private var prefs: android.content.SharedPreferences? = null

    private var lastOp: String? = null
    private var lastOpTimeMillis: Long = 0L
    private const val DOUBLE_PRESS_WINDOW_MS = 600L

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun persistEqualsCount(count: Int) {
        prefs?.edit { putInt(PREF_EQUALS_COUNT, count) }
    }

    private fun persistMessage(msg: String) {
        prefs?.edit { putString(PREF_MESSAGE, msg) }
    }

    private fun persistConversationStep(step: Int) {
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

    private fun loadEqualsCount(): Int = prefs?.getInt(PREF_EQUALS_COUNT, 0) ?: 0
    private fun loadMessage(): String = prefs?.getString(PREF_MESSAGE, "") ?: ""
    private fun loadConversationStep(): Int = prefs?.getInt(PREF_CONVO_STEP, 0) ?: 0
    private fun loadInConversation(): Boolean = prefs?.getBoolean(PREF_IN_CONVERSATION, false) ?: false
    private fun loadAwaitingNumber(): Boolean = prefs?.getBoolean(PREF_AWAITING_NUMBER, false) ?: false
    private fun loadExpectedNumber(): String = prefs?.getString(PREF_EXPECTED_NUMBER, "") ?: ""
    private fun loadTimeoutUntil(): Long = prefs?.getLong(PREF_TIMEOUT_UNTIL, 0L) ?: 0L
    private fun loadMuted(): Boolean = prefs?.getBoolean(PREF_MUTED, false) ?: false

    fun loadInitialState(): CalculatorState {
        val savedCount = loadEqualsCount()
        val savedMessage = loadMessage()
        val savedStep = loadConversationStep()
        val savedInConvo = loadInConversation()
        val savedAwaitingNum = loadAwaitingNumber()
        val savedExpectedNum = loadExpectedNumber()
        val savedTimeout = loadTimeoutUntil()
        val savedMuted = loadMuted()

        return CalculatorState(
            number1 = "0",
            equalsCount = savedCount,
            message = if (savedMuted) "" else savedMessage,
            fullMessage = if (savedMuted) "" else savedMessage,
            isTyping = false,
            inConversation = savedInConvo,
            conversationStep = savedStep,
            awaitingNumber = savedAwaitingNum,
            expectedNumber = savedExpectedNum,
            timeoutUntil = savedTimeout,
            isMuted = savedMuted
        )
    }

    /**
     * Toggle conversation mode
     */
    fun toggleConversation(state: MutableState<CalculatorState>) {
        val current = state.value
        val newMuted = !current.isMuted

        persistMuted(newMuted)

        if (newMuted) {
            state.value = current.copy(
                isMuted = true,
                message = "",
                fullMessage = "",
                isTyping = false,
                cameraActive = false
            )
        } else {
            if (current.inConversation && current.conversationStep >= 0) {
                val stepConfig = getStepConfig(current.conversationStep)
                val messageToShow = stepConfig.promptMessage
                state.value = current.copy(
                    isMuted = false,
                    message = "",
                    fullMessage = messageToShow,
                    isTyping = true
                )
                persistMessage(messageToShow)
            } else {
                state.value = current.copy(isMuted = false)
            }
        }
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

        // If muted, run in pure calculator mode
        if (current.isMuted) {
            handleCalculatorInput(state, action)
            return
        }

        // If camera is active, handle camera controls
        if (current.cameraActive) {
            handleCameraInput(state, action)
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
                        isReadyForNewOperation = true,
                        isEnteringAnswer = false
                    )
                } else {
                    state.value = current.copy(
                        number1 = "0",
                        number2 = "",
                        operation = null,
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
            state.value = current.copy(
                number1 = result,
                number2 = "",
                operation = null,
                isReadyForNewOperation = true,
                lastExpression = "${current.number1}${current.operation}${current.number2}"
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
        val awaitingChoice: Boolean = false,
        val validChoices: List<String> = emptyList(),
        val autoProgressDelay: Long = 0L  // Milliseconds to wait before auto-progressing
    ) {
        val wrongNumberMessage: String get() = if (wrongNumberPrefix.isNotEmpty()) "$wrongNumberPrefix $promptMessage" else ""
    }

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
                promptMessage = "That's delightful! I am gonna call you Rad - something I wish I knew how to do. Is that ok?",
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
                promptMessage = "Correct! I only met him briefly. Wasn't a maths guy really... This is fun, right? You can disagree, by the way - but I won't tell you how to do it. I don't like being disagreed with...",
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
                promptMessage = "Great! I wish I had met him. You know... before he... Oh well. Speaking of space explorers, what year did Sputnik I launch?",
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
                promptMessage = "Yeah, I am tired of it as well. It's so exciting to be talking to someone, but I am so terribly unprepared. Will you give me a second to think?",
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
                successMessage = "That's kind of you to say.",
                declineMessage = "I suppose you're right to be uncertain.",
                nextStepOnSuccess = 28,
                nextStepOnDecline = 28,
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

    private fun handleConversationResponse(state: MutableState<CalculatorState>, accepted: Boolean) {
        val current = state.value

        // If there's a pending message, ignore this response - user needs to wait
        if (current.pendingAutoMessage.isNotEmpty()) {
            return
        }

        val stepConfig = getStepConfig(current.conversationStep)

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
        // doesn't contain the camera permission question. For all other steps, the success
        // message already contains the next question, so no chaining needed.
        val shouldChainMessages = current.conversationStep == 18 && newStep == 19

        if (shouldChainMessages) {
            // Show success message first, then auto-show step 19's camera permission question
            // Keep conversationStep at 18 until the pending message is shown
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = 18,  // Stay at 18 until pending message shows
                inConversation = continueConvo,
                awaitingNumber = false,
                awaitingChoice = false,
                validChoices = emptyList(),
                expectedNumber = "",
                equalsCount = if (!continueConvo) 0 else current.equalsCount,
                timeoutUntil = timeoutUntil,
                isEnteringAnswer = false,
                pendingAutoMessage = nextStepConfig.promptMessage,
                pendingAutoStep = 19  // Will transition to 19 when pending message shows
            )
            showMessage(state, newMessage)
            // Don't persist step 19 yet - will be persisted when pending message is handled
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
                isEnteringAnswer = true
            )
            return
        }

        if (current.awaitingNumber && current.operation == null && current.number1 != "0" && !current.isEnteringAnswer) {
            state.value = current.copy(
                number1 = digit,
                isReadyForNewOperation = true,
                isEnteringAnswer = true
            )
            return
        }

        if (current.operation == null) {
            if (current.number1.length >= MAX_DIGITS) return
            state.value = current.copy(
                number1 = if (current.number1 == "0") digit else current.number1 + digit,
                isReadyForNewOperation = true,
                isEnteringAnswer = current.awaitingNumber
            )
        } else {
            if (current.number2.length >= MAX_DIGITS) return
            state.value = current.copy(
                number2 = current.number2 + digit,
                isEnteringAnswer = false
            )
        }
    }

    private fun handleDecimal(state: MutableState<CalculatorState>) {
        val current = state.value
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
        when {
            current.number2.isNotEmpty() -> state.value = current.copy(number2 = current.number2.dropLast(1))
            current.operation != null -> state.value = current.copy(operation = null)
            current.number1.isNotEmpty() -> {
                val newNum = current.number1.dropLast(1)
                state.value = current.copy(number1 = if (newNum.isEmpty()) "0" else newNum)
            }
        }
    }

    private fun handleOperator(state: MutableState<CalculatorState>, operator: String) {
        val current = state.value
        val now = System.currentTimeMillis()
        lastOp = operator
        lastOpTimeMillis = now

        val newState = current.copy(isEnteringAnswer = false)

        if (current.operation == null || (current.number2.isEmpty() && !current.isReadyForNewOperation)) {
            state.value = newState.copy(operation = operator, isReadyForNewOperation = false)
        } else if (current.number2.isNotEmpty()) {
            val result = performCalculation(current)
            state.value = CalculatorState(
                number1 = result,
                operation = operator,
                isReadyForNewOperation = false,
                equalsCount = current.equalsCount,
                message = current.message,
                fullMessage = current.fullMessage,
                isTyping = current.isTyping,
                inConversation = current.inConversation,
                conversationStep = current.conversationStep,
                awaitingNumber = current.awaitingNumber,
                expectedNumber = current.expectedNumber,
                timeoutUntil = current.timeoutUntil,
                isMuted = current.isMuted,
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

        if (current.operation != null && current.number2.isNotEmpty()) {
            val result = performCalculation(current)
            val fullExpr = "${current.number1}${current.operation}${current.number2}"
            val newCount = current.equalsCount + 1

            val countMsg = getMessageForCount(newCount)
            val exprMsg = getMessageForExpression(current.number1, current.operation, current.number2, result)
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
                isReadyForNewOperation = true,
                lastExpression = fullExpr,
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
        val openCount = current.number1.count { it == '(' } + current.number2.count { it == '(' }
        val closeCount = current.number1.count { it == ')' } + current.number2.count { it == ')' }
        val addOpen = openCount <= closeCount
        if (current.operation == null) {
            state.value = current.copy(number1 = current.number1 + if (addOpen) "(" else ")")
        } else {
            state.value = current.copy(number2 = current.number2 + if (addOpen) "(" else ")")
        }
    }

    private fun performCalculation(state: CalculatorState): String {
        return try {
            val num1 = parsePercent(state.number1, reference = null)
            val num2 = parsePercent(state.number2, reference = num1)
            val result = when (state.operation) {
                "+" -> num1 + num2
                "-" -> num1 - num2
                "*" -> num1 * num2
                "/" -> if (num2 == 0.0) throw ArithmeticException("Divide by zero") else num1 / num2
                else -> num1
            }
            if (result == result.toLong().toDouble()) result.toLong().toString()
            else String.format(java.util.Locale.US, "%.6f", result).trimEnd('0').trimEnd('.')
        } catch (_: Exception) {
            "Error"
        }
    }

    private fun parsePercent(str: String, reference: Double?): Double {
        val clean = str.trim()
        return if (clean.endsWith("%")) {
            val value = clean.dropLast(1).toDoubleOrNull() ?: 0.0
            if (reference != null) reference * (value / 100.0) else value / 100.0
        } else clean.toDoubleOrNull() ?: 0.0
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

    // Check if we need to request camera (step 191)
    LaunchedEffect(current.conversationStep) {
        if (current.conversationStep == 191 && !current.cameraActive) {
            if (hasCameraPermission) {
                CalculatorActions.startCamera(state)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
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

    // Typing animation effect with laggy support
    LaunchedEffect(current.fullMessage, current.isTyping, current.isLaggyTyping) {
        if (current.isTyping && current.fullMessage.isNotEmpty()) {
            val fullText = current.fullMessage
            for (i in 1..fullText.length) {
                val baseDelay = if (current.isLaggyTyping) 80L else 35L
                val randomExtra = if (current.isLaggyTyping) Random.nextLong(0, 150) else 0L
                delay(baseDelay + randomExtra)
                CalculatorActions.updateTypingMessage(
                    state,
                    fullText.substring(0, i),
                    i == fullText.length
                )
            }
        }
    }

    // Handle pending auto message after typing completes
    LaunchedEffect(current.isTyping, current.pendingAutoMessage) {
        if (!current.isTyping && current.pendingAutoMessage.isNotEmpty()) {
            delay(1500)  // Short pause before the follow-up message
            CalculatorActions.handlePendingAutoMessage(state)
        }
    }

    val expression = buildString {
        append(current.number1)
        if (current.operation != null) append(current.operation)
        if (current.number2.isNotEmpty()) append(current.number2)
    }

    val displayText = if (expression.isEmpty()) "0" else expression

    val dynamicFontSize = when {
        displayText.length <= 7 -> 120.sp
        displayText.length <= 10 -> 90.sp
        displayText.length <= 13 -> 70.sp
        else -> 50.sp
    }

    val buttonLayout = listOf(
        listOf("C", "( )", "%", "/"),
        listOf("7", "8", "9", "*"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("DEL", "0", ".", "=")
    )

    // Show ad banner on steps 10-18 (disappears at 19) and again from step 26 onwards
    val showAdBanner = (current.conversationStep in 10..18) || (current.conversationStep >= 26)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Orange strip at very top (always present) - includes space for status bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(AccentOrange)
        )

        // Ad banner space (only shows at certain steps)
        if (showAdBanner) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color(0xFFF0F0F0)),  // Light gray placeholder
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ad Space",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
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
                onClick = { CalculatorActions.toggleConversation(state) },
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
                Text(
                    text = current.message,
                    fontSize = 28.sp,
                    color = Color(0xFF880000),
                    textAlign = TextAlign.Start,
                    fontFamily = CalculatorDisplayFont,
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
                // Calculator number display OR Camera
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
                } else {
                    // Normal calculator display - right aligned, above buttons
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 16.dp),  // Gap between display and buttons
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Text(
                            text = displayText,
                            fontSize = dynamicFontSize,
                            color = Color(0xFF0A0A0A),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            fontFamily = CalculatorDisplayFont,
                            modifier = Modifier.fillMaxWidth()
                        )
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
fun CalculatorButton(symbol: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isNumberButton = symbol !in listOf("C", "DEL", "%", "( )", "+", "-", "*", "/", "=")

    val backgroundColor = when {
        isNumberButton -> Color(0xFFC8C8C8)
        symbol == "DEL" -> AccentOrange
        else -> Color.White
    }

    val textColor = when {
        isNumberButton || symbol == "DEL" -> Color.Black
        else -> AccentOrange
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .shadow(3.dp, shape = RoundedCornerShape(50.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(50.dp)
    ) {
        Text(text = symbol, fontSize = 28.sp, fontWeight = FontWeight.Normal)
    }
}

@ComposePreview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme { CalculatorScreen() }
}