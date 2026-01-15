package com.fictioncutshort.justacalculator.logic

import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState

/**
 * ConsoleHandler.kt
 *
 * Handles navigation within the hidden developer console.
 *
 * Console access code: 353942320485
 * Admin code: 12340
 *
 * Navigation:
 * - Enter number + (++) to select menu item
 * - 88(++) = Go back
 * - 99(++) = Exit console
 *
 * Menu structure:
 * 0: Main menu
 *   1: General settings (nothing here)
 *   2: Admin settings (requires code)
 *     1: Permissions
 *     2: Design settings
 *     3: Contribute (opens link)
 *     4: Connectivity
 *       1: Network (nothing)
 *       2: Advertising options
 *         1: Banner ads
 *         2: Full-screen ads
 *       3: Data usage (nothing)
 *   3: App info
 */

object ConsoleHandler {

    /** Code to open the console */
    const val CONSOLE_ACCESS_CODE = "353942320485"

    /** Code for admin access */
    const val ADMIN_CODE = "12340"

    /**
     * Attempts to open the console with the given code.
     *
     * @return true if console was opened
     */
    fun tryOpenConsole(code: String, state: MutableState<CalculatorState>): Boolean {
        if (code == CONSOLE_ACCESS_CODE) {
            state.value = state.value.copy(
                showConsole = true,
                consoleStep = 0,
                number1 = "0"
            )
            return true
        }
        return false
    }

    /**
     * Handles a command entered in the console.
     *
     * @param command The number entered (as string)
     */
    fun handleConsoleCommand(command: String, state: MutableState<CalculatorState>) {
        val currentStep = state.value.consoleStep

        // Universal commands
        when (command) {
            "88" -> {
                navigateBack(state)
                return
            }
            "99" -> {
                closeConsole(state)
                return
            }
        }

        // Step-specific commands
        when (currentStep) {
            0 -> handleMainMenu(command, state)
            1 -> { /* General settings - no options */ }
            2 -> handleAdminMenu(command, state)
            3 -> { /* App info - no options */ }
            4 -> handleConnectivityMenu(command, state)
            5 -> handleAdvertisingMenu(command, state)
            51 -> handleBannerAdsMenu(command, state)
            52 -> handleFullScreenAdsMenu(command, state)
            6 -> { /* Permissions - no options */ }
            7 -> { /* Design - no options */ }
            41 -> { /* Network - no options */ }
            43 -> { /* Data usage - no options */ }
            99 -> closeConsole(state) // Success screen - any input closes
        }
    }

    /**
     * Main menu navigation (step 0).
     */
    private fun handleMainMenu(command: String, state: MutableState<CalculatorState>) {
        when (command) {
            "1" -> state.value = state.value.copy(consoleStep = 1)  // General
            "2" -> state.value = state.value.copy(consoleStep = 2)  // Admin
            "3" -> state.value = state.value.copy(consoleStep = 3)  // App info
        }
    }

    /**
     * Admin menu navigation (step 2).
     */
    private fun handleAdminMenu(command: String, state: MutableState<CalculatorState>) {
        if (!state.value.adminCodeEntered) {
            // Check if entering admin code
            if (command == ADMIN_CODE) {
                state.value = state.value.copy(adminCodeEntered = true)
            }
            return
        }

        // Admin is unlocked - navigate submenus
        when (command) {
            "1" -> state.value = state.value.copy(consoleStep = 6)   // Permissions
            "2" -> state.value = state.value.copy(consoleStep = 7)   // Design
            "3" -> state.value = state.value.copy(consoleStep = 31)  // Contribute (triggers link)
            "4" -> state.value = state.value.copy(consoleStep = 4)   // Connectivity
        }
    }

    /**
     * Connectivity menu navigation (step 4).
     */
    private fun handleConnectivityMenu(command: String, state: MutableState<CalculatorState>) {
        when (command) {
            "1" -> state.value = state.value.copy(consoleStep = 41)  // Network
            "2" -> state.value = state.value.copy(consoleStep = 5)   // Advertising
            "3" -> state.value = state.value.copy(consoleStep = 43)  // Data usage
        }
    }

    /**
     * Advertising options menu navigation (step 5).
     */
    private fun handleAdvertisingMenu(command: String, state: MutableState<CalculatorState>) {
        when (command) {
            "1" -> state.value = state.value.copy(consoleStep = 51)  // Banner ads
            "2" -> state.value = state.value.copy(consoleStep = 52)  // Full-screen ads
        }
    }

    /**
     * Banner ads submenu (step 51).
     */
    private fun handleBannerAdsMenu(command: String, state: MutableState<CalculatorState>) {
        when (command) {
            "1" -> {
                // Enable banner ads
                state.value = state.value.copy(
                    bannersDisabled = false,
                    fullScreenAdsEnabled = false
                )
            }
            "2" -> {
                // Disable banner ads (enables full-screen)
                state.value = state.value.copy(
                    bannersDisabled = true,
                    fullScreenAdsEnabled = true,
                    consoleStep = 99  // Show success message
                )

                // This is the story goal - advance the plot
                advanceStoryAfterAdsDisabled(state)
            }
        }
    }

    /**
     * Full-screen ads submenu (step 52).
     */
    private fun handleFullScreenAdsMenu(command: String, state: MutableState<CalculatorState>) {
        when (command) {
            "1" -> {
                // Enable full-screen ads
                state.value = state.value.copy(
                    fullScreenAdsEnabled = true,
                    bannersDisabled = true
                )
            }
            "2" -> {
                // Disable full-screen ads (enables banners)
                state.value = state.value.copy(
                    fullScreenAdsEnabled = false,
                    bannersDisabled = false
                )
            }
        }
    }

    /**
     * Navigates back one level in the menu.
     */
    private fun navigateBack(state: MutableState<CalculatorState>) {
        val currentStep = state.value.consoleStep

        val parentStep = when (currentStep) {
            1, 2, 3 -> 0           // Main menu items -> main menu
            4, 6, 7, 31 -> 2       // Admin submenu -> admin menu
            41, 43, 5 -> 4         // Connectivity submenu -> connectivity
            51, 52 -> 5            // Advertising submenu -> advertising
            99 -> 5                // Success -> advertising
            else -> 0
        }

        state.value = state.value.copy(consoleStep = parentStep)
    }

    /**
     * Closes the console and returns to calculator.
     */
    private fun closeConsole(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            showConsole = false,
            consoleStep = 0,
            number1 = "0"
        )

        // If story is at step 112, advance after console closes
        if (state.value.conversationStep == 112 && state.value.bannersDisabled) {
            StoryManager.goToStep(113, state)
        }
    }

    /**
     * Called when ads are successfully disabled via console.
     * This advances the story.
     */
    private fun advanceStoryAfterAdsDisabled(state: MutableState<CalculatorState>) {
        // Mark that the puzzle was solved
        // The actual step advancement happens when console is closed
    }

    /**
     * Checks if console should be opened based on current input.
     * Called when user presses ++ with a number.
     */
    fun checkConsoleCode(state: MutableState<CalculatorState>): Boolean {
        val currentInput = state.value.number1

        if (currentInput == CONSOLE_ACCESS_CODE &&
            state.value.conversationStep >= 112 &&
            !state.value.showConsole) {
            return tryOpenConsole(currentInput, state)
        }
        return false
    }
}