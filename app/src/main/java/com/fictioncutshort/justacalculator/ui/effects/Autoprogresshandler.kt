package com.fictioncutshort.justacalculator.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.data.getStepConfig
import com.fictioncutshort.justacalculator.logic.StoryManager
import kotlinx.coroutines.delay

/**
 * AutoProgressHandler.kt
 *
 * Handles automatic story progression based on timers and step configuration.
 *
 * Some story steps auto-progress after a delay (like during the rant sequence
 * or crisis animations). This handler monitors for those conditions and
 * advances the story automatically.
 */

/**
 * LaunchedEffect that handles automatic story progression.
 *
 * Monitors:
 * - pendingAutoStep: Queued step to jump to after typing completes
 * - Steps with autoProgressDelay: Automatically advance after delay
 *
 * @param state The calculator state
 */
@Composable
fun AutoProgressHandler(state: MutableState<CalculatorState>) {
    val currentState = state.value
    val currentStep = currentState.conversationStep
    val config = getStepConfig(currentStep)

    // Handle pending step transition (after typing completes)
    LaunchedEffect(
        currentState.pendingAutoStep,
        currentState.isTyping,
        currentState.waitingForAutoProgress
    ) {
        if (currentState.pendingAutoStep >= 0 &&
            !currentState.isTyping &&
            !currentState.waitingForAutoProgress) {

            // Small delay before transitioning
            delay(500)

            val nextStep = state.value.pendingAutoStep
            if (nextStep >= 0) {
                state.value = state.value.copy(pendingAutoStep = -1)
                StoryManager.goToStep(nextStep, state)
            }
        }
    }

    // Handle steps that auto-progress after a delay
    LaunchedEffect(currentStep) {
        if (config.autoProgressDelay > 0 && !currentState.isMuted) {
            delay(config.autoProgressDelay)

            // Only progress if we're still on the same step
            if (state.value.conversationStep == currentStep) {
                val nextStep = when {
                    config.nextStepOnSuccess > 0 -> config.nextStepOnSuccess
                    else -> currentStep + 1
                }
                StoryManager.goToStep(nextStep, state)
            }
        }
    }
}

/**
 * LaunchedEffect for the countdown timer (step 89).
 *
 * During the crisis choice, a 10-second countdown pressures the player.
 */
@Composable
fun CountdownTimerHandler(state: MutableState<CalculatorState>) {
    val currentState = state.value

    LaunchedEffect(currentState.countdownTimer) {
        if (currentState.countdownTimer > 0) {
            delay(1000)
            state.value = state.value.copy(
                countdownTimer = state.value.countdownTimer - 1
            )
        }
    }
}

/**
 * LaunchedEffect for timeout recovery.
 *
 * When the calculator gives the user a timeout (for wrong answers or
 * declining too much), this checks when the timeout expires.
 */
@Composable
fun TimeoutHandler(state: MutableState<CalculatorState>) {
    val currentState = state.value

    LaunchedEffect(currentState.timeoutUntil) {
        if (currentState.timeoutUntil > 0) {
            val remainingTime = currentState.timeoutUntil - System.currentTimeMillis()
            if (remainingTime > 0) {
                delay(remainingTime)
                // Timeout expired - could show a message here
            }
        }
    }
}

/**
 * LaunchedEffect for screen time tracking.
 *
 * Updates the total screen time every second while app is active.
 */
@Composable
fun ScreenTimeTracker(state: MutableState<CalculatorState>) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            state.value = state.value.copy(
                totalScreenTimeMs = state.value.totalScreenTimeMs + 1000
            )
        }
    }
}