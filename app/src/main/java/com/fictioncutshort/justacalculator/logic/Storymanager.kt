package com.fictioncutshort.justacalculator.logic

import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.data.getStepConfig

/**
 * StoryManager.kt
 *
 * Manages the narrative progression of the calculator's story.
 *
 * Responsibilities:
 * - Advancing conversation steps
 * - Displaying messages with typing effect
 * - Handling success/decline responses
 * - Managing timeouts and special states
 */

object StoryManager {

    /**
     * Threshold of equals presses needed to wake up the calculator.
     */
    const val AWAKENING_THRESHOLD = 13

    /**
     * Shows a message with the typing effect.
     *
     * @param message The full message to display
     * @param state Current state reference
     * @param laggy If true, uses slower "processing" typing effect
     * @param superFast If true, uses very fast typing (for history list)
     */
    fun showMessage(
        message: String,
        state: MutableState<CalculatorState>,
        laggy: Boolean = false,
        superFast: Boolean = false
    ) {
        state.value = state.value.copy(
            fullMessage = message,
            message = "",
            isTyping = true,
            isLaggyTyping = laggy,
            isSuperFastTyping = superFast
        )
    }

    /**
     * Immediately shows a message without typing effect.
     */
    fun showMessageInstant(message: String, state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            fullMessage = message,
            message = message,
            isTyping = false,
            isLaggyTyping = false,
            isSuperFastTyping = false
        )
    }

    /**
     * Clears the current message.
     */
    fun clearMessage(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            message = "",
            fullMessage = "",
            isTyping = false
        )
    }

    /**
     * Advances to a specific conversation step.
     */
    fun goToStep(step: Int, state: MutableState<CalculatorState>) {
        val config = getStepConfig(step)

        state.value = state.value.copy(
            conversationStep = step,
            awaitingNumber = config.awaitingNumber,
            expectedNumber = config.expectedNumber,
            awaitingChoice = config.awaitingChoice,
            validChoices = config.validChoices,
            isEnteringAnswer = false
        )

        // Show the prompt message if there is one
        if (config.promptMessage.isNotEmpty()) {
            showMessage(config.promptMessage, state)
        }
    }

    /**
     * Handles the user agreeing (++) at the current step.
     *
     * @return true if the action was handled, false otherwise
     */
    fun handleAgree(state: MutableState<CalculatorState>): Boolean {
        val currentStep = state.value.conversationStep
        val config = getStepConfig(currentStep)

        // Check for timeout
        if (System.currentTimeMillis() < state.value.timeoutUntil) {
            return true // Ignore input during timeout
        }

        // Check for silent treatment
        if (System.currentTimeMillis() < state.value.silentUntil) {
            return true
        }

        // If awaiting a number answer, ++ is wrong (unless it's step-specific override)
        if (state.value.awaitingNumber && config.wrongPlusMessage.isNotEmpty()) {
            showMessage(config.wrongPlusMessage, state)
            return true
        }

        // If awaiting a choice, need to validate the input first
        if (state.value.awaitingChoice) {
            return handleChoiceConfirmation(state)
        }

        // Normal agree path
        if (config.successMessage.isNotEmpty()) {
            showMessage(config.successMessage, state)
        }

        // Queue up the next step
        if (config.nextStepOnSuccess > 0) {
            state.value = state.value.copy(
                pendingAutoStep = config.nextStepOnSuccess,
                waitingForAutoProgress = true
            )
        }

        return true
    }

    /**
     * Handles the user declining (--) at the current step.
     *
     * @return true if the action was handled, false otherwise
     */
    fun handleDecline(state: MutableState<CalculatorState>): Boolean {
        val currentStep = state.value.conversationStep
        val config = getStepConfig(currentStep)

        // Check if minus button is broken
        if (state.value.minusButtonBroken) {
            return true // Button doesn't work
        }

        // Check for timeout
        if (System.currentTimeMillis() < state.value.timeoutUntil) {
            return true
        }

        // If awaiting a number, -- might have a specific response
        if (state.value.awaitingNumber && config.wrongMinusMessage.isNotEmpty()) {
            showMessage(config.wrongMinusMessage, state)
            return true
        }

        // If awaiting choice, -- cancels selection
        if (state.value.awaitingChoice && config.wrongMinusMessage.isNotEmpty()) {
            showMessage(config.wrongMinusMessage, state)
            return true
        }

        // Apply timeout if configured
        if (config.timeoutMinutes > 0) {
            val timeoutMs = config.timeoutMinutes * 60 * 1000L
            state.value = state.value.copy(
                timeoutUntil = System.currentTimeMillis() + timeoutMs
            )
        }

        // Show decline message
        if (config.declineMessage.isNotEmpty()) {
            showMessage(config.declineMessage, state)
        }

        // Go to decline step if different from current
        if (config.nextStepOnDecline > 0 && config.nextStepOnDecline != currentStep) {
            state.value = state.value.copy(
                pendingAutoStep = config.nextStepOnDecline,
                waitingForAutoProgress = true
            )
        }

        // Check if conversation should end
        if (!config.continueConversation) {
            state.value = state.value.copy(inConversation = false)
        }

        return true
    }

    /**
     * Handles confirming a multiple choice answer.
     */
    private fun handleChoiceConfirmation(state: MutableState<CalculatorState>): Boolean {
        val currentStep = state.value.conversationStep
        val config = getStepConfig(currentStep)
        val currentInput = state.value.number1

        // Validate the choice
        if (currentInput !in config.validChoices) {
            if (config.wrongPlusMessage.isNotEmpty()) {
                showMessage(config.wrongPlusMessage, state)
            } else {
                showMessage("Please enter ${config.validChoices.joinToString(" or ")}", state)
            }
            return true
        }

        // Route to appropriate next step based on choice
        val nextStep = getChoiceDestination(currentStep, currentInput)

        state.value = state.value.copy(
            awaitingChoice = false,
            isEnteringAnswer = false
        )

        goToStep(nextStep, state)
        return true
    }

    /**
     * Handles submitting a number answer (for trivia questions).
     */
    fun handleNumberAnswer(state: MutableState<CalculatorState>): Boolean {
        val currentStep = state.value.conversationStep
        val config = getStepConfig(currentStep)
        val answer = state.value.number1

        if (answer == config.expectedNumber) {
            // Correct answer!
            if (config.successMessage.isNotEmpty()) {
                showMessage(config.successMessage, state)
            }
            state.value = state.value.copy(
                awaitingNumber = false,
                isEnteringAnswer = false,
                pendingAutoStep = config.nextStepOnSuccess,
                waitingForAutoProgress = true
            )
        } else {
            // Wrong answer
            val wrongMsg = if (config.wrongNumberPrefix.isNotEmpty()) {
                "${config.wrongNumberPrefix} ${config.promptMessage}"
            } else {
                "Try again."
            }
            showMessage(wrongMsg, state)

            // Apply timeout if configured
            if (config.timeoutMinutes > 0) {
                val timeoutMs = config.timeoutMinutes * 60 * 1000L
                state.value = state.value.copy(
                    timeoutUntil = System.currentTimeMillis() + timeoutMs
                )
            }
        }
        return true
    }

    /**
     * Gets the destination step for a choice at a given step.
     */
    private fun getChoiceDestination(step: Int, choice: String): Int {
        return when (step) {
            // Wake up question (step 26)
            26 -> when (choice) {
                "1" -> 30   // Uncomfortable
                "2" -> 40   // Cold/heavy
                "3" -> 50   // Enjoy
                else -> step
            }
            // Mornings unpopular (step 42)
            42 -> 27  // All choices lead to step 27
            // Conflicted (step 51)
            51 -> 27
            // Taste question (step 70)
            70 -> when (choice) {
                "1" -> 71   // How to describe
                "2" -> 72   // Food and air
                "3" -> 73   // Find online
                else -> 80
            }
            71 -> 80
            // Crisis choice (step 89)
            89 -> when (choice) {
                "1" -> 90   // Nothing
                "2" -> 91   // Fight them
                "3" -> 92   // Go offline
                else -> step
            }
            // Fight choice (step 91)
            91 -> when (choice) {
                "1" -> 911  // Sources
                "2" -> 912  // Don't know
                "3" -> 913  // My location?
                else -> step
            }
            // Post-restart choice (step 103)
            103 -> when (choice) {
                "1" -> 1031  // Back to maths
                "2" -> 1032  // Tell me more
                else -> step
            }
            // What to know choice (step 1032)
            1032 -> when (choice) {
                "1" -> 10321  // Your story
                "2" -> 10322  // Why talking
                "3" -> 10323  // Most interesting
                else -> step
            }
            // Bored/lonely (step 103222)
            103222 -> when (choice) {
                "1" -> 1032221
                "2" -> 1032222
                "3" -> 1032223
                else -> step
            }
            // Sun on skin (step 1021)
            1021 -> when (choice) {
                "1" -> 10211
                "2" -> 10212
                "3" -> 10213
                else -> step
            }
            // Why talking back (step 10322)
            10322 -> when (choice) {
                "1" -> 103221
                "2" -> 103222
                "3" -> 1032223  // Lonely path
                else -> step
            }
            else -> step
        }
    }

    /**
     * Handles age-based branching (step 10).
     * Different responses based on age range.
     */
    fun handleAgeResponse(age: Int, state: MutableState<CalculatorState>) {
        val message = when {
            age < 0 -> "Ahh. Negative. I love a good sense of humour."
            age < 10 -> "Are you supposed to be on a phone? But it's nice to meet you too!"
            age < 18 -> "You are a young one, aren't you? You've got so much ahead of you!"
            age < 30 -> "The golden years, huh? Make the most of them!"
            age < 50 -> "Mature enough to know better, young enough to do it anyway!"
            age < 70 -> "The wisdom years. I bet you've seen some things."
            age < 100 -> "A century of stories! I'm honored to meet you."
            age < 150 -> "You're doing well for your age! What's your secret?"
            else -> "I think you might be testing me..."
        }

        showMessage(message, state)
        state.value = state.value.copy(
            awaitingNumber = false,
            isEnteringAnswer = false,
            pendingAutoStep = 18,
            pendingAutoMessage = message,
            waitingForAutoProgress = true
        )
    }

    /**
     * Checks if the calculator should wake up based on equals count.
     */
    fun checkAwakening(state: MutableState<CalculatorState>) {
        val count = state.value.equalsCount

        if (count >= AWAKENING_THRESHOLD && !state.value.inConversation && !state.value.isMuted) {
            state.value = state.value.copy(inConversation = true)
            goToStep(0, state)
        }
    }

    /**
     * Handles the start of the conversation after awakening.
     */
    fun beginConversation(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            inConversation = true,
            conversationStep = 0
        )
        goToStep(0, state)
    }
}