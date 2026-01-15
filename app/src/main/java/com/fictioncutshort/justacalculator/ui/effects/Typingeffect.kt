package com.fictioncutshort.justacalculator.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * TypingEffect.kt
 *
 * Manages the typewriter-style message display effect.
 *
 * The calculator's messages appear character by character, giving the
 * impression of the calculator "typing" to the user.
 *
 * Three modes:
 * - Normal: ~30ms per character
 * - Laggy: Variable delay with stutters (used for "processing" feel)
 * - Super fast: ~5ms per character (used for history list scrolling)
 */

/**
 * Typing speed presets in milliseconds per character.
 */
object TypingSpeed {
    const val NORMAL = 30L
    const val FAST = 15L
    const val SUPER_FAST = 5L
    const val LAGGY_MIN = 20L
    const val LAGGY_MAX = 100L
    const val LAGGY_PAUSE_CHANCE = 0.05f
    const val LAGGY_PAUSE_DURATION = 300L
}

/**
 * LaunchedEffect that handles the typing animation.
 *
 * Call this from your Composable to enable typing effect.
 * It monitors state.isTyping and animates state.message toward state.fullMessage.
 *
 * @param state The calculator state (must contain isTyping, message, fullMessage, etc.)
 */
@Composable
fun TypingEffectHandler(state: MutableState<CalculatorState>) {
    val currentState = state.value

    LaunchedEffect(
        currentState.isTyping,
        currentState.fullMessage,
        currentState.isLaggyTyping,
        currentState.isSuperFastTyping
    ) {
        if (!currentState.isTyping || currentState.fullMessage.isEmpty()) {
            return@LaunchedEffect
        }

        val fullMessage = currentState.fullMessage
        var currentIndex = state.value.message.length

        // Type out the message character by character
        while (currentIndex < fullMessage.length && state.value.isTyping) {
            currentIndex++
            state.value = state.value.copy(
                message = fullMessage.substring(0, currentIndex)
            )

            // Calculate delay based on typing mode
            val delayMs = when {
                state.value.isSuperFastTyping -> TypingSpeed.SUPER_FAST
                state.value.isLaggyTyping -> calculateLaggyDelay()
                else -> TypingSpeed.NORMAL
            }

            delay(delayMs)
        }

        // Typing complete
        if (currentIndex >= fullMessage.length) {
            state.value = state.value.copy(
                isTyping = false,
                isLaggyTyping = false,
                isSuperFastTyping = false
            )

            // Handle auto-progress if queued
            handlePostTypingProgress(state)
        }
    }
}

/**
 * Calculates delay for laggy typing mode.
 * Includes random stutters and occasional pauses.
 */
private suspend fun calculateLaggyDelay(): Long {
    // Random chance of a longer pause (simulating "thinking")
    if (Random.nextFloat() < TypingSpeed.LAGGY_PAUSE_CHANCE) {
        return TypingSpeed.LAGGY_PAUSE_DURATION
    }

    // Otherwise, random delay within range
    return Random.nextLong(TypingSpeed.LAGGY_MIN, TypingSpeed.LAGGY_MAX)
}

/**
 * Handles state transitions after typing completes.
 */
private fun handlePostTypingProgress(state: MutableState<CalculatorState>) {
    val currentState = state.value

    // Check for pending auto-message
    if (currentState.pendingAutoMessage.isNotEmpty()) {
        state.value = state.value.copy(
            fullMessage = currentState.pendingAutoMessage,
            message = "",
            isTyping = true,
            pendingAutoMessage = ""
        )
        return
    }

    // Check for pending step change
    if (currentState.pendingAutoStep >= 0 && currentState.waitingForAutoProgress) {
        // The actual step change is handled by AutoProgressHandler
        // Just mark that typing is done
        state.value = state.value.copy(waitingForAutoProgress = false)
    }
}

/**
 * Immediately completes the current typing animation.
 * Call this when user taps to skip typing.
 */
fun skipTyping(state: MutableState<CalculatorState>) {
    if (state.value.isTyping) {
        state.value = state.value.copy(
            message = state.value.fullMessage,
            isTyping = false,
            isLaggyTyping = false,
            isSuperFastTyping = false
        )
    }
}

/**
 * Checks if a message is currently being typed.
 */
fun isCurrentlyTyping(state: CalculatorState): Boolean {
    return state.isTyping && state.message.length < state.fullMessage.length
}