package com.fictioncutshort.justacalculator.logic

import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.util.DOUBLE_PRESS_WINDOW_MS

/**
 * InputHandler.kt
 *
 * Routes button presses to appropriate handlers.
 *
 * Responsibilities:
 * - Detecting double-taps for ++ and -- gestures
 * - Routing calculator buttons to CalculatorEngine
 * - Routing story interactions to StoryManager
 * - Handling special states (console, mini-games, etc.)
 */

object InputHandler {

    // Timing for double-tap detection
    private var lastPlusPress = 0L
    private var lastMinusPress = 0L
    private var lastEqualsPress = 0L

    /**
     * Main entry point for all button presses.
     *
     * @param button The button that was pressed
     * @param state Current state reference
     * @param onSpecialAction Callback for actions that need external handling
     *                        (camera, notifications, etc.)
     */
    fun onButtonPress(
        button: String,
        state: MutableState<CalculatorState>,
        onSpecialAction: (SpecialAction) -> Unit = {}
    ) {
        val currentState = state.value

        // Handle console mode separately
        if (currentState.showConsole) {
            handleConsoleInput(button, state)
            return
        }

        // Handle whack-a-mole mode
        if (currentState.whackAMoleActive) {
            handleWhackAMoleInput(button, state)
            return
        }

        // Handle word game mode
        if (currentState.wordGameActive) {
            handleWordGameInput(button, state)
            return
        }

        // Route based on button type
        when (button) {
            "+" -> handlePlus(state, onSpecialAction)
            "-" -> handleMinus(state, onSpecialAction)
            "=" -> handleEquals(state, onSpecialAction)
            "*", "/" -> handleOperator(button, state)
            "C" -> handleClear(state)
            "DEL" -> handleDelete(state)
            "." -> handleDecimal(state)
            "%" -> handlePercent(state)
            "( )" -> handleParentheses(state)
            in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9") -> handleDigit(button, state)
        }
    }

    /**
     * Handles + button press with double-tap detection for ++.
     */
    private fun handlePlus(
        state: MutableState<CalculatorState>,
        onSpecialAction: (SpecialAction) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val isDoubleTap = (now - lastPlusPress) < DOUBLE_PRESS_WINDOW_MS
        lastPlusPress = now

        if (isDoubleTap && state.value.inConversation && !state.value.isMuted) {
            // Double-tap: Story agree action
            handleStoryAgree(state, onSpecialAction)
        } else {
            // Single tap: Regular + operation
            state.value = CalculatorEngine.setOperation("+", state.value)
        }
    }

    /**
     * Handles - button press with double-tap detection for --.
     */
    private fun handleMinus(
        state: MutableState<CalculatorState>,
        onSpecialAction: (SpecialAction) -> Unit
    ) {
        // Check if minus button is broken
        if (state.value.minusButtonBroken) {
            return // Button doesn't work
        }

        val now = System.currentTimeMillis()
        val isDoubleTap = (now - lastMinusPress) < DOUBLE_PRESS_WINDOW_MS
        lastMinusPress = now

        if (isDoubleTap && state.value.inConversation && !state.value.isMuted) {
            // Double-tap: Story decline action
            StoryManager.handleDecline(state)
        } else {
            // Single tap: Regular - operation
            state.value = CalculatorEngine.setOperation("-", state.value)
        }
    }

    /**
     * Handles = button press.
     */
    private fun handleEquals(
        state: MutableState<CalculatorState>,
        onSpecialAction: (SpecialAction) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val isDoubleTap = (now - lastEqualsPress) < DOUBLE_PRESS_WINDOW_MS
        lastEqualsPress = now

        // Increment equals counter
        state.value = state.value.copy(equalsCount = state.value.equalsCount + 1)

        // Check for awakening
        if (!state.value.inConversation) {
            StoryManager.checkAwakening(state)
        }

        // If awaiting number answer, double-tap submits
        if (state.value.awaitingNumber && state.value.isEnteringAnswer && isDoubleTap) {
            StoryManager.handleNumberAnswer(state)
            return
        }

        // Regular calculation
        state.value = CalculatorEngine.calculate(state.value)
    }

    /**
     * Handles story agreement (++) action.
     */
    private fun handleStoryAgree(
        state: MutableState<CalculatorState>,
        onSpecialAction: (SpecialAction) -> Unit
    ) {
        val currentStep = state.value.conversationStep
        val config = com.fictioncutshort.justacalculator.data.getStepConfig(currentStep)

        // Special handling for camera request
        if (config.requestsCamera) {
            onSpecialAction(SpecialAction.RequestCamera)
            return
        }

        // Special handling for notification request
        if (config.requestsNotification) {
            onSpecialAction(SpecialAction.RequestNotification)
        }

        // Normal story progression
        StoryManager.handleAgree(state)
    }

    /**
     * Handles operator buttons (* /).
     */
    private fun handleOperator(operator: String, state: MutableState<CalculatorState>) {
        state.value = CalculatorEngine.setOperation(operator, state.value)
    }

    /**
     * Handles C (clear) button.
     */
    private fun handleClear(state: MutableState<CalculatorState>) {
        state.value = CalculatorEngine.clearAll(state.value)
    }

    /**
     * Handles DEL (delete) button.
     */
    private fun handleDelete(state: MutableState<CalculatorState>) {
        state.value = CalculatorEngine.deleteLastChar(state.value)
    }

    /**
     * Handles decimal point button.
     */
    private fun handleDecimal(state: MutableState<CalculatorState>) {
        state.value = CalculatorEngine.appendDecimal(state.value)
    }

    /**
     * Handles percent button.
     */
    private fun handlePercent(state: MutableState<CalculatorState>) {
        state.value = CalculatorEngine.calculatePercentage(state.value)
    }

    /**
     * Handles parentheses button (currently acts as toggle sign).
     */
    private fun handleParentheses(state: MutableState<CalculatorState>) {
        state.value = CalculatorEngine.toggleSign(state.value)
    }

    /**
     * Handles digit buttons (0-9).
     */
    private fun handleDigit(digit: String, state: MutableState<CalculatorState>) {
        // If in conversation and awaiting input, mark as entering answer
        if (state.value.inConversation &&
            (state.value.awaitingNumber || state.value.awaitingChoice)) {
            state.value = state.value.copy(isEnteringAnswer = true)
        }

        state.value = CalculatorEngine.appendDigit(digit, state.value)
    }

    /**
     * Handles input when console is open.
     */
    private fun handleConsoleInput(button: String, state: MutableState<CalculatorState>) {
        when (button) {
            in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9") -> {
                val newInput = if (state.value.number1 == "0") button
                else state.value.number1 + button
                state.value = state.value.copy(number1 = newInput)
            }
            "+" -> {
                // Double-tap detection for console confirmation
                val now = System.currentTimeMillis()
                if ((now - lastPlusPress) < DOUBLE_PRESS_WINDOW_MS) {
                    ConsoleHandler.handleConsoleCommand(state.value.number1, state)
                    state.value = state.value.copy(number1 = "0")
                }
                lastPlusPress = now
            }
            "C", "DEL" -> {
                state.value = state.value.copy(number1 = "0")
            }
        }
    }

    /**
     * Handles input during whack-a-mole game.
     */
    private fun handleWhackAMoleInput(button: String, state: MutableState<CalculatorState>) {
        MiniGameManager.handleWhackAMolePress(button, state)
    }

    /**
     * Handles input during word game.
     */
    private fun handleWordGameInput(button: String, state: MutableState<CalculatorState>) {
        // Word game uses its own input handling in WordGame.kt
        // This is a placeholder for arrow key controls
        when (button) {
            "4" -> { /* Move left */ }
            "6" -> { /* Move right */ }
            "+" -> { /* Confirm */ }
        }
    }

    /**
     * Resets the double-tap timers.
     * Call this when resetting the game.
     */
    fun resetTimers() {
        lastPlusPress = 0L
        lastMinusPress = 0L
        lastEqualsPress = 0L
    }
}

/**
 * Actions that require external handling (permissions, intents, etc.)
 */
sealed class SpecialAction {
    object RequestCamera : SpecialAction()
    object RequestNotification : SpecialAction()
    object OpenKofiLink : SpecialAction()
    object CreateSecretFile : SpecialAction()
}