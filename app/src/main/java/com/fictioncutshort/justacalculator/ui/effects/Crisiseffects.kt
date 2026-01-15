package com.fictioncutshort.justacalculator.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * CrisisEffects.kt
 *
 * Handles the visual chaos during the calculator's "existential crisis"
 * sequence (steps 85-92).
 *
 * Effects include:
 * - Button shaking
 * - Screen flickering
 * - Color inversion
 * - Blackouts
 * - Desaturation (tension levels)
 */

/**
 * LaunchedEffect that manages button shaking during crisis.
 */
@Composable
fun ButtonShakeEffect(state: MutableState<CalculatorState>) {
    val currentState = state.value

    LaunchedEffect(currentState.buttonShakeIntensity) {
        if (currentState.buttonShakeIntensity > 0) {
            // Continuous shake while intensity > 0
            while (state.value.buttonShakeIntensity > 0) {
                // The actual shake offset is calculated in CalculatorButton
                // using Random based on the intensity value
                delay(50) // Update shake ~20 times per second
            }
        }
    }
}

/**
 * LaunchedEffect that manages screen flicker effects.
 */
@Composable
fun FlickerEffect(state: MutableState<CalculatorState>) {
    val currentState = state.value

    LaunchedEffect(currentState.flickerEffect) {
        if (currentState.flickerEffect) {
            // Quick flash on/off
            delay(50)
            state.value = state.value.copy(flickerEffect = false)
        }
    }

    // B&W flicker for tension level 3
    LaunchedEffect(currentState.tensionLevel) {
        if (currentState.tensionLevel >= 3) {
            while (state.value.tensionLevel >= 3) {
                state.value = state.value.copy(
                    bwFlickerPhase = !state.value.bwFlickerPhase
                )
                delay(Random.nextLong(100, 300))
            }
        }
    }
}

/**
 * LaunchedEffect for screen blackout timing.
 */
@Composable
fun BlackoutEffect(state: MutableState<CalculatorState>) {
    val currentState = state.value

    LaunchedEffect(currentState.screenBlackout) {
        if (currentState.screenBlackout) {
            // Blackout duration varies
            delay(Random.nextLong(200, 800))
            state.value = state.value.copy(screenBlackout = false)
        }
    }
}

/**
 * Triggers a crisis effect sequence.
 * Call this to start the visual chaos.
 *
 * @param intensity 1-3, higher = more intense effects
 */
fun triggerCrisisEffects(intensity: Int, state: MutableState<CalculatorState>) {
    state.value = state.value.copy(
        buttonShakeIntensity = intensity * 3f,
        tensionLevel = intensity,
        flickerEffect = true
    )
}

/**
 * Clears all crisis effects and returns to normal.
 */
fun clearCrisisEffects(state: MutableState<CalculatorState>) {
    state.value = state.value.copy(
        buttonShakeIntensity = 0f,
        tensionLevel = 0,
        flickerEffect = false,
        bwFlickerPhase = false,
        screenBlackout = false,
        vibrationIntensity = 0
    )
}

/**
 * Calculates the desaturation amount based on tension level.
 *
 * @return Value from 0f (full color) to 1f (grayscale)
 */
fun getTensionDesaturation(tensionLevel: Int): Float {
    return when (tensionLevel) {
        0 -> 0f
        1 -> 0.3f
        2 -> 0.6f
        3 -> 0.9f
        else -> 0f
    }
}

/**
 * Gets the overlay color for tension effects.
 *
 * @return Color to overlay on screen (with appropriate alpha)
 */
fun getTensionOverlayAlpha(tensionLevel: Int, bwFlickerPhase: Boolean): Float {
    if (tensionLevel < 3) return 0f
    return if (bwFlickerPhase) 0.3f else 0.1f
}