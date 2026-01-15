package com.fictioncutshort.justacalculator.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.logic.StoryManager
import kotlinx.coroutines.delay

/**
 * BrowserAnimationHandler.kt
 *
 * Controls the fake browser animation sequences:
 *
 * 1. GOOGLE SEARCH (steps 61-63):
 *    - Browser opens
 *    - Types "calculator history"
 *    - Shows "No internet" error
 *    - Browser closes
 *
 * 2. WIKIPEDIA (steps 80-82):
 *    - Browser opens
 *    - Types Wikipedia URL
 *    - Shows Wikipedia page (or fake fallback)
 *    - Calculator reacts to content
 *
 * 3. POST-CRISIS (steps 108-110):
 *    - Calculator searches for help
 *    - Creates the secret file
 */

/**
 * LaunchedEffect that drives the browser animation.
 */
@Composable
fun BrowserAnimationHandler(state: MutableState<CalculatorState>) {
    val currentState = state.value
    val browserPhase = currentState.browserPhase

    LaunchedEffect(browserPhase) {
        if (browserPhase == 0 || !currentState.showBrowser) return@LaunchedEffect

        when (browserPhase) {
            // ═══════════════════════════════════════════════════════════════
            // GOOGLE SEARCH SEQUENCE (phases 1-4)
            // ═══════════════════════════════════════════════════════════════
            1 -> {
                // Open browser, start typing search
                delay(500)
                animateSearchText("calculator history", state)
                state.value = state.value.copy(browserPhase = 2)
            }
            2 -> {
                // "Searching..."
                delay(1500)
                state.value = state.value.copy(browserPhase = 3)
            }
            3 -> {
                // Show error
                state.value = state.value.copy(browserShowError = true)
                delay(2000)
                state.value = state.value.copy(browserPhase = 4)
            }
            4 -> {
                // Close browser, continue story
                delay(500)
                state.value = state.value.copy(
                    showBrowser = false,
                    browserPhase = 0,
                    browserSearchText = "",
                    browserShowError = false
                )
                StoryManager.goToStep(63, state)
            }

            // ═══════════════════════════════════════════════════════════════
            // WIKIPEDIA SEQUENCE (phases 10-22)
            // ═══════════════════════════════════════════════════════════════
            10 -> {
                // Open browser
                delay(500)
                state.value = state.value.copy(browserPhase = 11)
            }
            11 -> {
                // Type Wikipedia URL
                animateSearchText("en.wikipedia.org/wiki/Calculator", state)
                state.value = state.value.copy(browserPhase = 12)
            }
            12 -> {
                // Loading...
                delay(1000)
                state.value = state.value.copy(
                    browserShowWikipedia = true,
                    browserPhase = 13
                )
            }
            13 -> {
                // Wikipedia loaded, let user look at it
                delay(5000)
                state.value = state.value.copy(browserPhase = 20)
            }
            20 -> {
                // Calculator starts reacting
                StoryManager.showMessage("You see, there's a lot!", state)
                delay(3000)
                state.value = state.value.copy(browserPhase = 21)
            }
            21 -> {
                // Close Wikipedia, crisis begins
                state.value = state.value.copy(
                    showBrowser = false,
                    browserShowWikipedia = false,
                    browserPhase = 0,
                    browserSearchText = ""
                )
                StoryManager.goToStep(83, state)
            }

            // ═══════════════════════════════════════════════════════════════
            // POST-CRISIS REPAIR SEQUENCE (phases 30-39)
            // ═══════════════════════════════════════════════════════════════
            30 -> {
                // Open browser again
                delay(500)
                animateSearchText("how to remove ads from app", state)
                state.value = state.value.copy(browserPhase = 31)
            }
            31 -> {
                delay(2000)
                StoryManager.showMessage("There's so much, just endless streams...", state)
                state.value = state.value.copy(browserPhase = 32)
            }
            32 -> {
                delay(3000)
                state.value = state.value.copy(
                    showBrowser = false,
                    browserPhase = 0,
                    browserSearchText = ""
                )
                StoryManager.goToStep(110, state)
            }

            // ═══════════════════════════════════════════════════════════════
            // POST-CHAOS ONLINE SEQUENCE (phases 50-56)
            // ═══════════════════════════════════════════════════════════════
            50 -> {
                delay(500)
                animateSearchText("reddit.com/r/calculators", state)
                state.value = state.value.copy(browserPhase = 51)
            }
            51 -> {
                delay(2000)
                state.value = state.value.copy(browserPhase = 52)
            }
            52 -> {
                state.value = state.value.copy(
                    showBrowser = false,
                    browserPhase = 0
                )
                // Continue to word game setup
                StoryManager.goToStep(117, state)
            }
        }
    }
}

/**
 * Animates typing text into the browser search bar.
 */
private suspend fun animateSearchText(text: String, state: MutableState<CalculatorState>) {
    for (i in 1..text.length) {
        state.value = state.value.copy(
            browserSearchText = text.substring(0, i)
        )
        delay(50)
    }
}

/**
 * Starts the Google search animation sequence.
 */
fun startGoogleSearchAnimation(state: MutableState<CalculatorState>) {
    state.value = state.value.copy(
        showBrowser = true,
        browserPhase = 1,
        browserSearchText = "",
        browserShowError = false,
        browserShowWikipedia = false
    )
}

/**
 * Starts the Wikipedia animation sequence.
 */
fun startWikipediaAnimation(state: MutableState<CalculatorState>) {
    state.value = state.value.copy(
        showBrowser = true,
        browserPhase = 10,
        browserSearchText = "",
        browserShowError = false,
        browserShowWikipedia = false
    )
}

/**
 * Starts the post-crisis repair search sequence.
 */
fun startRepairSearchAnimation(state: MutableState<CalculatorState>) {
    state.value = state.value.copy(
        showBrowser = true,
        browserPhase = 30,
        browserSearchText = ""
    )
}