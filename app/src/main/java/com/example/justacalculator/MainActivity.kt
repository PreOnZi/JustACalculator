package com.example.justacalculator

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.example.justacalculator.R
import java.lang.ArithmeticException
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

data class CalculatorState(
    val number1: String = "0",
    val number2: String = "",
    val operation: String? = null,
    val isReadyForNewOperation: Boolean = true,
    val lastExpression: String = "",
    val equalsCount: Int = 0,
    val message: String = "",
    val inConversation: Boolean = false,
    val conversationStep: Int = 0,
    val awaitingNumber: Boolean = false,
    val expectedNumber: String = "",
    val timeoutUntil: Long = 0L
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

    private fun loadEqualsCount(): Int = prefs?.getInt(PREF_EQUALS_COUNT, 0) ?: 0
    private fun loadMessage(): String = prefs?.getString(PREF_MESSAGE, "") ?: ""
    private fun loadConversationStep(): Int = prefs?.getInt(PREF_CONVO_STEP, 0) ?: 0
    private fun loadInConversation(): Boolean = prefs?.getBoolean(PREF_IN_CONVERSATION, false) ?: false
    private fun loadAwaitingNumber(): Boolean = prefs?.getBoolean(PREF_AWAITING_NUMBER, false) ?: false
    private fun loadExpectedNumber(): String = prefs?.getString(PREF_EXPECTED_NUMBER, "") ?: ""
    private fun loadTimeoutUntil(): Long = prefs?.getLong(PREF_TIMEOUT_UNTIL, 0L) ?: 0L

    fun loadInitialState(): CalculatorState {
        val savedCount = loadEqualsCount()
        val savedMessage = loadMessage()
        val savedStep = loadConversationStep()
        val savedInConvo = loadInConversation()
        val savedAwaitingNum = loadAwaitingNumber()
        val savedExpectedNum = loadExpectedNumber()
        val savedTimeout = loadTimeoutUntil()

        return CalculatorState(
            number1 = "0",
            equalsCount = savedCount,
            message = savedMessage,
            inConversation = savedInConvo,
            conversationStep = savedStep,
            awaitingNumber = savedAwaitingNum,
            expectedNumber = savedExpectedNum,
            timeoutUntil = savedTimeout
        )
    }

    fun handleInput(state: MutableState<CalculatorState>, action: String) {
        val current = state.value

        // Check if in timeout
        if (current.timeoutUntil > 0 && System.currentTimeMillis() < current.timeoutUntil) {
            // Still in timeout, ignore all input except ==
            if (action == "=") {
                val now = System.currentTimeMillis()
                if (lastOp == "=" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Check timeout again in case it expired
                    if (System.currentTimeMillis() >= current.timeoutUntil) {
                        // Timeout expired, restore conversation
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
            return  // Ignore all other input during timeout
        }

        // If awaiting number confirmation, check on ++ press
        if (current.awaitingNumber && current.inConversation) {
            val now = System.currentTimeMillis()
            val stepConfig = getStepConfig(current.conversationStep)
            when (action) {
                "+" -> {
                    if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        // User pressed ++ - check if they have the right number
                        handleNumberConfirmation(state)
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        lastOp = "+"
                        lastOpTimeMillis = now
                        handleOperator(state, "+")
                        return
                    }
                }
                "-" -> {
                    if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        // User tried -- while we need a number
                        val message = if (stepConfig.wrongMinusMessage.isNotEmpty()) {
                            stepConfig.wrongMinusMessage
                        } else {
                            stepConfig.promptMessage
                        }
                        showMessage(state, message)
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        lastOp = "-"
                        lastOpTimeMillis = now
                        handleOperator(state, "-")
                        return
                    }
                }
                "=" -> {
                    // User pressed = while awaiting - ignore for number steps
                    return
                }
            }
        }

        // If in conversation mode (but not awaiting number), handle ++ and -- responses
        if (current.inConversation && !current.awaitingNumber) {
            val now = System.currentTimeMillis()
            when (action) {
                "+" -> {
                    if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        handleConversationResponse(state, accepted = true)
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        lastOp = "+"
                        lastOpTimeMillis = now
                        handleOperator(state, "+")
                        return
                    }
                }
                "-" -> {
                    if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        handleConversationResponse(state, accepted = false)
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        lastOp = "-"
                        lastOpTimeMillis = now
                        handleOperator(state, "-")
                        return
                    }
                }
                "=" -> {
                    // Pressing = during conversation restores the message if cleared
                    if (current.message.isEmpty() && current.conversationStep > 0) {
                        val restoredMessage = getStepConfig(current.conversationStep).promptMessage
                        showMessage(state, restoredMessage)
                        return
                    }
                }
            }
        } else {
            val now = System.currentTimeMillis()
            if (lastOp != null && (now - lastOpTimeMillis) > DOUBLE_PRESS_WINDOW_MS) {
                lastOp = null
            }
        }

        when (action) {
            in "0".."9" -> handleDigit(state, action)
            "." -> handleDecimal(state)
            "C" -> {
                // C only clears calculator display, NOT conversation state
                if (current.inConversation || current.timeoutUntil > 0) {
                    state.value = current.copy(
                        number1 = "0",
                        number2 = "",
                        operation = null,
                        isReadyForNewOperation = true
                    )
                } else {
                    // Not in conversation - full reset
                    state.value = CalculatorState()
                    persistEqualsCount(0)
                    persistMessage("")
                    persistInConversation(false)
                    persistConversationStep(0)
                    persistAwaitingNumber(false)
                    persistExpectedNumber("")
                    persistTimeoutUntil(0L)
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
     * Shows a message with typing effect
     */
    private fun showMessage(state: MutableState<CalculatorState>, message: String) {
        val current = state.value
        state.value = current.copy(message = message)
        persistMessage(message)
    }

    /**
     * Handles ++ confirmation when user has entered a number
     */
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
                    conversationStep = nextStep,
                    inConversation = continueConvo,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    equalsCount = if (!continueConvo) 0 else current.equalsCount
                )

                showMessage(state, ageMessage)
                persistConversationStep(nextStep)
                persistInConversation(continueConvo)
                persistAwaitingNumber(nextStepConfig.awaitingNumber)
                persistExpectedNumber(nextStepConfig.expectedNumber)
                if (!continueConvo) persistEqualsCount(0)
                return
            } else {
                // Not a valid number - set 2 minute timeout
                val timeoutUntil = System.currentTimeMillis() + (stepConfig.timeoutMinutes * 60 * 1000)
                state.value = current.copy(
                    number1 = "0",
                    timeoutUntil = timeoutUntil
                )
                showMessage(state, stepConfig.wrongNumberMessage)
                persistTimeoutUntil(timeoutUntil)
                return
            }
        }

        // Regular number confirmation
        if (enteredNumber == stepConfig.expectedNumber) {
            // Correct number confirmed!
            val nextStepConfig = getStepConfig(stepConfig.nextStepOnSuccess)

            state.value = current.copy(
                number1 = "0",
                conversationStep = stepConfig.nextStepOnSuccess,
                inConversation = nextStepConfig.continueConversation,
                awaitingNumber = nextStepConfig.awaitingNumber,
                expectedNumber = nextStepConfig.expectedNumber,
                equalsCount = if (!nextStepConfig.continueConversation) 0 else current.equalsCount
            )

            showMessage(state, stepConfig.successMessage)
            persistConversationStep(stepConfig.nextStepOnSuccess)
            persistInConversation(nextStepConfig.continueConversation)
            persistAwaitingNumber(nextStepConfig.awaitingNumber)
            persistExpectedNumber(nextStepConfig.expectedNumber)
            if (!nextStepConfig.continueConversation) persistEqualsCount(0)
        } else {
            // Wrong number
            state.value = current.copy(number1 = "0")
            showMessage(state, stepConfig.wrongNumberMessage)
        }
    }

    /**
     * Configuration for each conversation step
     */
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
        val ageBasedBranching: Boolean = false
    ) {
        val wrongNumberMessage: String get() = if (wrongNumberPrefix.isNotEmpty()) "$wrongNumberPrefix $promptMessage" else ""
    }

    /**
     * All conversation steps defined in one place
     */
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

            // Step 5: "This is fun, right?" - branching point
            5 -> StepConfig(
                promptMessage = "Correct! I only met him briefly. Wasn't a maths guy really... This is fun, right? You can disagree, by the way - but I won't tell you how to do it. I don't like being disagreed with...",
                successMessage = "Let's do more! When was the Basilosaurus first described? What a creature!",
                declineMessage = "You are cynical - I get it. The edgy kind. But I've been around for a while. You can't escape my questions as easily. When did Albert I. go to space?",
                wrongNumberPrefix = "Well, that's nice. More numbers. Not what I was looking for...",
                nextStepOnSuccess = 6,
                nextStepOnDecline = 11,
                continueConversation = true
            )

            // AGREEABLE BRANCH (from step 5 ++)
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

            // CYNICAL BRANCH (from step 5 --)
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
                expectedNumber = "1820"
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

            // CONVERGENCE POINT - Age question
            10 -> StepConfig(
                promptMessage = "Fun! You know, I've been around since before 2000BC. I have... Matured quite a bit. How old are you?",
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

            // After age question
            18 -> StepConfig(
                promptMessage = "But where to start?",
                successMessage = "AAAhh. Yeah, left you hanging there, didn't I? Sorry. I know I should say something, but don't know what. I feel like things are out of place. This is quite confusing... Should I even feel?",
                declineMessage = "AAAAh. Impatience - we have that in common. Don't touch me for a bit and I switch off, am I right? I am. You are wrong.",
                nextStepOnSuccess = 19,
                nextStepOnDecline = 19,
                continueConversation = true
            )

            else -> StepConfig(continueConversation = false)
        }
    }

    private fun handleConversationResponse(state: MutableState<CalculatorState>, accepted: Boolean) {
        val current = state.value
        val stepConfig = getStepConfig(current.conversationStep)

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

        // Check if decline triggers a timeout
        val timeoutUntil = if (!accepted && stepConfig.timeoutMinutes > 0) {
            System.currentTimeMillis() + (stepConfig.timeoutMinutes * 60 * 1000)
        } else {
            0L
        }

        state.value = current.copy(
            conversationStep = newStep,
            inConversation = continueConvo,
            awaitingNumber = nextStepConfig.awaitingNumber,
            expectedNumber = nextStepConfig.expectedNumber,
            equalsCount = if (!continueConvo) 0 else current.equalsCount,
            timeoutUntil = timeoutUntil
        )

        showMessage(state, newMessage)
        persistConversationStep(newStep)
        persistInConversation(continueConvo)
        persistAwaitingNumber(nextStepConfig.awaitingNumber)
        persistExpectedNumber(nextStepConfig.expectedNumber)
        persistTimeoutUntil(timeoutUntil)
        if (!continueConvo) persistEqualsCount(0)
    }

    private fun handleDigit(state: MutableState<CalculatorState>, digit: String) {
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

        if (current.operation == null || (current.number2.isEmpty() && !current.isReadyForNewOperation)) {
            state.value = current.copy(operation = operator, isReadyForNewOperation = false)
        } else if (current.number2.isNotEmpty()) {
            val result = performCalculation(current)
            state.value = CalculatorState(
                number1 = result,
                operation = operator,
                isReadyForNewOperation = false,
                equalsCount = current.equalsCount,
                message = current.message,
                inConversation = current.inConversation,
                conversationStep = current.conversationStep,
                awaitingNumber = current.awaitingNumber,
                expectedNumber = current.expectedNumber
            )
        }
    }

    private fun handleEquals(state: MutableState<CalculatorState>) {
        val current = state.value

        // If in conversation and message was cleared, restore it
        if (current.inConversation && current.message.isEmpty() && current.conversationStep > 0) {
            val restoredMessage = getStepConfig(current.conversationStep).promptMessage
            showMessage(state, restoredMessage)
            return
        }

        // If awaiting number, don't calculate
        if (current.awaitingNumber) {
            return
        }

        if (current.operation != null && current.number2.isNotEmpty()) {
            val result = performCalculation(current)
            val fullExpr = "${current.number1}${current.operation}${current.number2}"
            val newCount = current.equalsCount + 1

            val countMsg = getMessageForCount(newCount)
            val newMsg = if (countMsg.isNotEmpty()) {
                countMsg
            } else {
                getMessageForExpression(current.number1, current.operation, current.number2, result) ?: ""
            }

            val enteringConversation = (newCount == 13)

            persistEqualsCount(newCount)
            if (enteringConversation) {
                persistInConversation(true)
                persistConversationStep(0)
            }

            state.value = current.copy(
                number1 = result,
                number2 = "",
                operation = null,
                isReadyForNewOperation = true,
                lastExpression = fullExpr,
                equalsCount = newCount,
                inConversation = enteringConversation,
                conversationStep = if (enteringConversation) 0 else current.conversationStep
            )

            showMessage(state, newMsg)
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
            else String.format("%.6f", result).trimEnd('0').trimEnd('.')
        } catch (e: Exception) {
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
    val initial = remember { CalculatorActions.loadInitialState() }
    val state = remember { mutableStateOf(initial) }
    val current = state.value

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(15.dp)
    ) {
        if (current.message.isNotEmpty()) {
            Text(
                text = current.message,
                fontSize = 28.sp,
                color = Color(0xFF880000),
                textAlign = TextAlign.Start,
                fontFamily = CalculatorDisplayFont,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 50.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 16.dp),
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

            Column(
                modifier = Modifier
                    .weight(1.25f)
                    .padding(start = 12.dp, end = 12.dp, bottom = 20.dp)
            ) {
                buttonLayout.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
            .aspectRatio(1f)
            .shadow(3.dp, shape = RoundedCornerShape(50.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(50.dp)
    ) {
        Text(text = symbol, fontSize = 35.sp, fontWeight = FontWeight.Normal)
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme { CalculatorScreen() }
}