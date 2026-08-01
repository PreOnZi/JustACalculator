package com.fictioncutshort.justacalculator.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.data.getStepConfig
import com.fictioncutshort.justacalculator.logic.CalculatorActions
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
                val config = getStepConfig(nextStep)
                state.value = state.value.copy(
                    pendingAutoStep = -1,
                    waitingForAutoProgress = false,
                    conversationStep = nextStep,
                    awaitingNumber = config.awaitingNumber,
                    expectedNumber = config.expectedNumber,
                    awaitingChoice = config.awaitingChoice,
                    validChoices = config.validChoices,
                    isEnteringAnswer = false,
                    fullMessage = config.promptMessage,
                    message = "",
                    isTyping = config.promptMessage.isNotEmpty(),
                    showTalkOverlay = config.showTalkOverlay,
                    showPhoneOverlay = config.showPhoneOverlay,
                    showHomeScreenOverlay = config.showHomeScreenOverlay
                )
                // Persist so phone-detour and other auto-progress steps survive close+reopen.
                CalculatorActions.persistConversationStep(nextStep)
            }
        }
    }

    // Handle steps that auto-progress after a delay
    LaunchedEffect(currentStep) {
        if (config.autoProgressDelay > 0 && !currentState.isMuted) {
            delay(config.autoProgressDelay)

            // Wait for typing to finish before advancing (handles long messages)
            while (state.value.isTyping && state.value.conversationStep == currentStep) {
                delay(100)
            }

            // Only progress if we're still on the same step
            if (state.value.conversationStep == currentStep) {
                if (currentStep == 80) {
                    StoryManager.triggerWikipediaCountdown(state)
                } else {
                    val nextStep = when {
                        config.nextStepOnSuccess > 0 -> config.nextStepOnSuccess
                        else -> currentStep + 1
                    }
                    StoryManager.goToStep(nextStep, state)
                }
            }
        }
    }
}
