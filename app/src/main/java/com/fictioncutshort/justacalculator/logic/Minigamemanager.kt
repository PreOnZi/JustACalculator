package com.fictioncutshort.justacalculator.logic

import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.data.ChaosKey
import com.fictioncutshort.justacalculator.util.WHACK_A_MOLE_BUTTONS
import kotlin.random.Random

/**
 * MiniGameManager.kt
 *
 * Manages the mini-games within the calculator story:
 *
 * 1. WHACK-A-MOLE (steps 96-99)
 *    - Buttons flash yellow one at a time
 *    - Player must tap the flashing button
 *    - Two rounds: 15 hits, then 10 hits (faster)
 *    - 5 total errors = game over, retry
 *
 * 2. KEYBOARD CHAOS (steps 105-107)
 *    - 3D floating letter cubes appear
 *    - Player taps letters to dismiss them
 *    - Calculator "accidentally" added a keyboard
 */

object MiniGameManager {

    // ═══════════════════════════════════════════════════════════════════════════
    // WHACK-A-MOLE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Starts the whack-a-mole mini-game.
     *
     * @param round 1 = first round (15 hits, slower), 2 = second round (10 hits, faster)
     */
    fun startWhackAMole(round: Int, state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            whackAMoleActive = true,
            whackAMoleScore = 0,
            whackAMoleMisses = 0,
            whackAMoleWrongClicks = 0,
            whackAMoleTotalErrors = 0,
            whackAMoleRound = round,
            flickeringButton = ""
        )

        // First target will be set by the LaunchedEffect in the UI
    }

    /**
     * Selects a new random target button.
     */
    fun selectNewTarget(state: MutableState<CalculatorState>) {
        val currentTarget = state.value.whackAMoleTarget
        var newTarget: String

        // Ensure we don't pick the same button twice in a row
        do {
            newTarget = WHACK_A_MOLE_BUTTONS.random()
        } while (newTarget == currentTarget)

        state.value = state.value.copy(
            whackAMoleTarget = newTarget,
            flickeringButton = newTarget
        )
    }

    /**
     * Handles a button press during whack-a-mole.
     */
    fun handleWhackAMolePress(button: String, state: MutableState<CalculatorState>) {
        if (!state.value.whackAMoleActive) return

        val target = state.value.whackAMoleTarget

        if (button == target) {
            // Correct hit!
            val newScore = state.value.whackAMoleScore + 1
            val targetScore = if (state.value.whackAMoleRound == 1) 15 else 10

            if (newScore >= targetScore) {
                // Round complete!
                endWhackAMoleRound(state)
            } else {
                state.value = state.value.copy(
                    whackAMoleScore = newScore,
                    flickeringButton = ""
                )
                // New target will be selected by LaunchedEffect
            }
        } else if (button in WHACK_A_MOLE_BUTTONS) {
            // Wrong button pressed
            val newWrongClicks = state.value.whackAMoleWrongClicks + 1
            val newTotalErrors = state.value.whackAMoleTotalErrors + 1

            if (newTotalErrors >= 5) {
                // Game over - too many errors
                failWhackAMole(state)
            } else {
                state.value = state.value.copy(
                    whackAMoleWrongClicks = newWrongClicks,
                    whackAMoleTotalErrors = newTotalErrors
                )
            }
        }
    }

    /**
     * Called when player misses (timeout).
     */
    fun handleWhackAMoleMiss(state: MutableState<CalculatorState>) {
        if (!state.value.whackAMoleActive) return

        val newMisses = state.value.whackAMoleMisses + 1
        val newTotalErrors = state.value.whackAMoleTotalErrors + 1

        if (newTotalErrors >= 5) {
            failWhackAMole(state)
        } else {
            state.value = state.value.copy(
                whackAMoleMisses = newMisses,
                whackAMoleTotalErrors = newTotalErrors,
                flickeringButton = ""
            )
            // New target will be selected by LaunchedEffect
        }
    }

    /**
     * Ends the current whack-a-mole round successfully.
     */
    private fun endWhackAMoleRound(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            whackAMoleActive = false,
            flickeringButton = ""
        )

        if (state.value.whackAMoleRound == 1) {
            // First round complete - go to step 99 to ask for round 2
            StoryManager.goToStep(99, state)
        } else {
            // Second round complete - repair successful, go to step 982
            StoryManager.goToStep(982, state)
        }
    }

    /**
     * Called when player fails whack-a-mole (5 errors).
     */
    private fun failWhackAMole(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            whackAMoleActive = false,
            flickeringButton = ""
        )

        // Go to retry step
        StoryManager.goToStep(99, state)
        StoryManager.showMessage("Hmm, that didn't quite work. Let's try again?", state)
    }

    /**
     * Gets the timeout duration for current round.
     * Round 2 is faster.
     */
    fun getWhackAMoleTimeout(round: Int): Long {
        return if (round == 1) 1500L else 1000L
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KEYBOARD CHAOS (3D floating letters)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generates the initial set of chaos letters for the 3D keyboard.
     */
    fun generateChaosLetters(): List<ChaosKey> {
        val letters = ('A'..'Z').toList()
        return letters.map { letter ->
            ChaosKey(
                letter = letter.toString(),
                x = Random.nextFloat() * 500f - 250f,  // -250 to 250
                y = Random.nextFloat() * 700f - 350f,  // -350 to 350
                z = Random.nextFloat() * 300f - 150f,  // -150 to 150
                size = Random.nextFloat() * 0.6f + 0.4f,  // 0.4 to 1.0
                rotationX = Random.nextFloat() * 360f,
                rotationY = Random.nextFloat() * 360f
            )
        }
    }

    /**
     * Starts the keyboard chaos mini-game.
     */
    fun startKeyboardChaos(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            keyboardChaosActive = true,
            chaosLetters = generateChaosLetters(),
            chaosPhase = 5,  // Active phase
            cubeRotationX = 15f,
            cubeRotationY = -25f,
            cubeScale = 1f
        )
    }

    /**
     * Removes a letter from the chaos field when tapped.
     *
     * @param letter The letter to remove
     * @return true if letter was found and removed
     */
    fun removeChaosLetter(letter: String, state: MutableState<CalculatorState>): Boolean {
        val currentLetters = state.value.chaosLetters
        val newLetters = currentLetters.filter { it.letter != letter }

        if (newLetters.size < currentLetters.size) {
            state.value = state.value.copy(chaosLetters = newLetters)

            // Check if all letters are cleared
            if (newLetters.isEmpty()) {
                endKeyboardChaos(state)
            }
            return true
        }
        return false
    }

    /**
     * Ends the keyboard chaos mini-game.
     */
    private fun endKeyboardChaos(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            keyboardChaosActive = false,
            chaosPhase = 0
        )

        // Proceed to step 107
        StoryManager.goToStep(107, state)
    }

    /**
     * Updates the 3D view rotation (from drag gestures).
     */
    fun updateChaosRotation(
        deltaX: Float,
        deltaY: Float,
        state: MutableState<CalculatorState>
    ) {
        state.value = state.value.copy(
            cubeRotationY = state.value.cubeRotationY + deltaX * 0.5f,
            cubeRotationX = (state.value.cubeRotationX + deltaY * 0.5f).coerceIn(-60f, 60f)
        )
    }

    /**
     * Updates the 3D view scale (from pinch gestures).
     */
    fun updateChaosScale(scaleFactor: Float, state: MutableState<CalculatorState>) {
        val newScale = (state.value.cubeScale * scaleFactor).coerceIn(0.5f, 2.0f)
        state.value = state.value.copy(cubeScale = newScale)
    }
}