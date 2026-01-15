package com.fictioncutshort.justacalculator.logic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.data.INTERACTIVE_STEPS
import com.fictioncutshort.justacalculator.util.*

/**
 * StateManager.kt
 *
 * Handles saving and loading calculator state to SharedPreferences.
 *
 * This allows the story progress to persist across app restarts.
 * Only "safe" state is saved - we don't save mid-animation states
 * that would break on restore.
 */

object StateManager {

    private lateinit var prefs: SharedPreferences

    /**
     * Initializes the state manager with a context.
     * Must be called before using other methods.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves the current state to SharedPreferences.
     *
     * Only saves "safe" state that can be restored without issues.
     */
    fun saveState(state: CalculatorState) {
        if (!::prefs.isInitialized) return

        prefs.edit().apply {
            // Story progress
            putInt(PREF_EQUALS_COUNT, state.equalsCount)
            putInt(PREF_CONVO_STEP, state.conversationStep)
            putBoolean(PREF_IN_CONVERSATION, state.inConversation)
            putString(PREF_MESSAGE, state.message)

            // User input state
            putBoolean(PREF_AWAITING_NUMBER, state.awaitingNumber)
            putString(PREF_EXPECTED_NUMBER, state.expectedNumber)
            putLong(PREF_TIMEOUT_UNTIL, state.timeoutUntil)

            // Settings
            putBoolean(PREF_MUTED, state.isMuted)

            // Crisis/repair state
            putBoolean(PREF_INVERTED_COLORS, state.invertedColors)
            putBoolean(PREF_MINUS_DAMAGED, state.minusButtonDamaged)
            putBoolean(PREF_MINUS_BROKEN, state.minusButtonBroken)
            putBoolean(PREF_NEEDS_RESTART, state.needsRestart)

            // Statistics
            putLong(PREF_TOTAL_SCREEN_TIME, state.totalScreenTimeMs)
            putInt(PREF_TOTAL_CALCULATIONS, state.totalCalculations)

            // Dark buttons (serialize as comma-separated string)
            putString(PREF_DARK_BUTTONS, state.darkButtons.joinToString(","))

            apply()
        }
    }

    /**
     * Loads saved state from SharedPreferences.
     *
     * Returns a new CalculatorState with restored values,
     * or default state if no saved data exists.
     */
    fun loadState(): CalculatorState {
        if (!::prefs.isInitialized) return CalculatorState()

        val savedStep = prefs.getInt(PREF_CONVO_STEP, 0)

        // Find a safe step to restore to (avoid mid-animation states)
        val safeStep = findSafeStep(savedStep)

        val darkButtonsString = prefs.getString(PREF_DARK_BUTTONS, "") ?: ""
        val darkButtons = if (darkButtonsString.isNotEmpty()) {
            darkButtonsString.split(",").filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        return CalculatorState(
            equalsCount = prefs.getInt(PREF_EQUALS_COUNT, 0),
            conversationStep = safeStep,
            inConversation = prefs.getBoolean(PREF_IN_CONVERSATION, false),
            message = prefs.getString(PREF_MESSAGE, "") ?: "",
            fullMessage = prefs.getString(PREF_MESSAGE, "") ?: "",

            awaitingNumber = prefs.getBoolean(PREF_AWAITING_NUMBER, false),
            expectedNumber = prefs.getString(PREF_EXPECTED_NUMBER, "") ?: "",
            timeoutUntil = prefs.getLong(PREF_TIMEOUT_UNTIL, 0L),

            isMuted = prefs.getBoolean(PREF_MUTED, false),

            invertedColors = prefs.getBoolean(PREF_INVERTED_COLORS, false),
            minusButtonDamaged = prefs.getBoolean(PREF_MINUS_DAMAGED, false),
            minusButtonBroken = prefs.getBoolean(PREF_MINUS_BROKEN, false),
            needsRestart = prefs.getBoolean(PREF_NEEDS_RESTART, false),

            totalScreenTimeMs = prefs.getLong(PREF_TOTAL_SCREEN_TIME, 0L),
            totalCalculations = prefs.getInt(PREF_TOTAL_CALCULATIONS, 0),

            darkButtons = darkButtons
        )
    }

    /**
     * Finds a safe step to restore to.
     *
     * Some steps are mid-animation or auto-progress steps that would
     * break if restored directly. This finds the nearest interactive step.
     */
    private fun findSafeStep(step: Int): Int {
        // If step is in the interactive list, it's safe
        if (step in INTERACTIVE_STEPS) return step

        // Otherwise, find the nearest lower interactive step
        return INTERACTIVE_STEPS.filter { it <= step }.maxOrNull() ?: 0
    }

    /**
     * Checks if terms have been accepted.
     */
    fun hasAcceptedTerms(): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getBoolean(PREF_TERMS_ACCEPTED, false)
    }

    /**
     * Marks terms as accepted.
     */
    fun acceptTerms() {
        if (!::prefs.isInitialized) return
        prefs.edit().putBoolean(PREF_TERMS_ACCEPTED, true).apply()
    }

    /**
     * Resets all saved state (used for debug reset).
     */
    fun resetAll() {
        if (!::prefs.isInitialized) return

        prefs.edit().apply {
            remove(PREF_EQUALS_COUNT)
            remove(PREF_CONVO_STEP)
            remove(PREF_IN_CONVERSATION)
            remove(PREF_MESSAGE)
            remove(PREF_AWAITING_NUMBER)
            remove(PREF_EXPECTED_NUMBER)
            remove(PREF_TIMEOUT_UNTIL)
            remove(PREF_MUTED)
            remove(PREF_INVERTED_COLORS)
            remove(PREF_MINUS_DAMAGED)
            remove(PREF_MINUS_BROKEN)
            remove(PREF_NEEDS_RESTART)
            remove(PREF_TOTAL_SCREEN_TIME)
            remove(PREF_TOTAL_CALCULATIONS)
            remove(PREF_DARK_BUTTONS)
            // Note: Don't remove PREF_TERMS_ACCEPTED
            apply()
        }
    }

    /**
     * Handles post-restart state (after whack-a-mole repair sequence).
     *
     * Called when app restarts and needsRestart was true.
     */
    fun handlePostRestart(state: MutableState<CalculatorState>) {
        if (state.value.needsRestart) {
            // Clear the needs restart flag
            state.value = state.value.copy(
                needsRestart = false,
                minusButtonBroken = false,  // Repair complete!
                minusButtonDamaged = false
            )

            // Go to post-restart step
            StoryManager.goToStep(102, state)

            // Save the updated state
            saveState(state.value)
        }
    }

    /**
     * Updates screen time tracking.
     * Should be called periodically while app is open.
     */
    fun updateScreenTime(deltaMs: Long, state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            totalScreenTimeMs = state.value.totalScreenTimeMs + deltaMs
        )
    }
}