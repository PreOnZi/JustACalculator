package com.fictioncutshort.justacalculator.logic

import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState

/**
 * ConsoleHandler.kt
 *
 * ⚠ NOT WIRED UP. Nothing in the app calls this object: its only caller is
 * InputHandler, which is itself unreferenced. The console the player actually
 * navigates is `CalculatorActions.handleConsoleInput`, which duplicates all of
 * the routing below.
 *
 * Both copies are kept in step for now, but this one is inert — editing it
 * alone changes nothing at runtime. Deleting this file and Inputhandler.kt
 * would remove the trap; left in place pending that call.
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
 *   1: General settings
 *     1: Design settings
 *     2: Date & time
 *       1: Date format (12/24 hour)
 *       2: Set date & time automatically (yes/no)
 *     3: Language & region (nothing — device-controlled)
 *     4: Software update (dimmed until 7384; then re-asks for the admin code)
 *     5: User guide
 *   2: Admin settings (requires code)
 *     1: Permissions
 *     2: Contribute (opens link)
 *     3: Connectivity
 *       1: Network (nothing)
 *       2: Advertising options
 *         1: Banner ads
 *         2: Full-screen ads
 *       3: Data usage (nothing)
 *   3: App info
 *
 * NOTE: the admin menu numbering is written down in two places the player
 * actually reads — the downloaded manual (Filecreation.kt) and nothing else.
 * Renumbering it means editing that file too, or the ad-disabling quest
 * sends the user to the wrong entry.
 */

object ConsoleHandler {

    /** Code to open the console */
    const val CONSOLE_ACCESS_CODE = "353942320485"

    /** Code for admin access */
    const val ADMIN_CODE = "12340"

    /**
     * Unlocks the Software Update entry in General settings. Printed at the
     * bottom of the console's own User Guide (step 16) — the player is meant
     * to find it by reading the manual they already have access to.
     */
    const val SOFTWARE_UPDATE_CODE = "7384"

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
            1 -> handleGeneralMenu(command, state)
            2 -> handleAdminMenu(command, state)
            3 -> { /* App info - no options */ }
            4 -> handleConnectivityMenu(command, state)
            5 -> handleAdvertisingMenu(command, state)
            51 -> handleBannerAdsMenu(command, state)
            52 -> handleFullScreenAdsMenu(command, state)
            6 -> { /* Permissions - no options */ }
            7 -> handleDesignMenu(command, state)
            11 -> handleDateTimeMenu(command, state)
            12 -> handleDateFormatMenu(command, state)
            13 -> handleAutoDateTimeMenu(command, state)
            14 -> { /* Language & region - device-controlled, no options */ }
            15 -> handleSoftwareUpdateMenu(command, state)
            16 -> { /* User guide - reference only, no options */ }
            17 -> { /* Update result - only 88/99 respond */ }
            41 -> { /* Network - no options */ }
            43 -> { /* Data usage - no options */ }
            99 -> { /* Success screen — only 88/99 above respond; keeps console
                      open so the user reads the calc-side message before
                      manually closing. Any other input is a no-op. */ }
        }
    }

    /**
     * Design settings menu (step 7). "1" toggles dark mode.
     */
    private fun handleDesignMenu(command: String, state: MutableState<CalculatorState>) {
        when (command) {
            "1" -> state.value = state.value.copy(darkModeEnabled = !state.value.darkModeEnabled)
        }
    }

    /**
     * General settings menu (step 1).
     *
     * Also the place the Software Update unlock code is typed: entering 7384
     * here flips [CalculatorState.consoleUpdateUnlocked] and item 4 stops
     * being dimmed. Selecting 4 while still locked does nothing at all — a
     * disabled entry should not react.
     */
    private fun handleGeneralMenu(command: String, state: MutableState<CalculatorState>) {
        if (command == SOFTWARE_UPDATE_CODE) {
            state.value = state.value.copy(consoleUpdateUnlocked = true)
            return
        }
        when (command) {
            "1" -> state.value = state.value.copy(consoleStep = 7)   // Design
            "2" -> state.value = state.value.copy(consoleStep = 11)  // Date & time
            "3" -> state.value = state.value.copy(consoleStep = 14)  // Language & region
            "4" -> if (state.value.consoleUpdateUnlocked) {
                state.value = state.value.copy(consoleStep = 15)     // Software update
            }
            "5" -> state.value = state.value.copy(consoleStep = 16)  // User guide
        }
    }

    /**
     * Date & time menu (step 11).
     */
    private fun handleDateTimeMenu(command: String, state: MutableState<CalculatorState>) {
        when (command) {
            "1" -> state.value = state.value.copy(consoleStep = 12)  // Date format
            "2" -> state.value = state.value.copy(consoleStep = 13)  // Automatic
        }
    }

    /** Date format submenu (step 12): 1 = 12-hour, 2 = 24-hour. */
    private fun handleDateFormatMenu(command: String, state: MutableState<CalculatorState>) {
        when (command) {
            "1" -> state.value = state.value.copy(consoleDateFormat24h = false)
            "2" -> state.value = state.value.copy(consoleDateFormat24h = true)
        }
    }

    /** Automatic date & time submenu (step 13): 1 = yes, 2 = no. */
    private fun handleAutoDateTimeMenu(command: String, state: MutableState<CalculatorState>) {
        when (command) {
            "1" -> state.value = state.value.copy(consoleAutoDateTime = true)
            "2" -> state.value = state.value.copy(consoleAutoDateTime = false)
        }
    }

    /**
     * Software update (step 15). Gated twice over: the admin code is demanded
     * again on entry, and even then the update prompt only appears when the
     * clock is on 24-hour + manual (see [isUpdateCompatible]).
     */
    private fun handleSoftwareUpdateMenu(command: String, state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.consoleUpdateAdminEntered) {
            if (command == ADMIN_CODE) {
                state.value = current.copy(consoleUpdateAdminEntered = true)
            }
            return
        }

        if (!isUpdateCompatible(current)) return

        when (command) {
            "1" -> state.value = current.copy(consoleStep = 17)  // Update
            "2" -> state.value = current.copy(consoleStep = 1)   // Decline → back
        }
    }

    /**
     * The update prompt is only offered on a 24-hour clock that is being set
     * manually. Any other combination — including "not set" — reports an
     * incompatible date & time format instead.
     */
    fun isUpdateCompatible(state: CalculatorState): Boolean =
        state.consoleDateFormat24h == true && state.consoleAutoDateTime == false

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

        // Admin is unlocked - navigate submenus. Design settings moved to
        // General settings, so everything below it shifted up by one; the
        // downloaded manual's "Connectivity settings (4++)" was updated to
        // match in Filecreation.kt.
        when (command) {
            "1" -> state.value = state.value.copy(consoleStep = 6)   // Permissions
            "2" -> state.value = state.value.copy(consoleStep = 31)  // Contribute (triggers link)
            "3" -> state.value = state.value.copy(consoleStep = 4)   // Connectivity
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
                // Disable banner ads (enables full-screen). Story goal hit:
                // immediately stage the calculator's "What a relief…" message
                // and advance to step 113. Console stays open — the calculator
                // explicitly tells the user "You can close the console now."
                // and the auto-progress to the next step is gated on that.
                val current = state.value
                state.value = current.copy(
                    bannersDisabled = true,
                    fullScreenAdsEnabled = true,
                    consoleStep = 99,  // Console-side success screen
                    conversationStep = 113,
                    message = "",
                    fullMessage = "What a relief! This feels so much better. Thank you! You can close the console now.",
                    isTyping = true
                )
                CalculatorActions.persistConversationStep(113)
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
            1, 2, 3 -> 0                     // Main menu items -> main menu
            7, 11, 14, 15, 16 -> 1           // General submenu -> general
            12, 13 -> 11                     // Date & time submenu -> date & time
            17 -> 15                         // Update result -> software update
            4, 6, 31 -> 2                    // Admin submenu -> admin menu
            41, 43, 5 -> 4                   // Connectivity submenu -> connectivity
            51, 52 -> 5                      // Advertising submenu -> advertising
            99 -> 5                          // Success -> advertising
            else -> 0
        }

        state.value = state.value.copy(consoleStep = parentStep)
    }

    /**
     * Closes the console and returns to calculator. The "What a relief…"
     * message at conversationStep 113 is set up the moment banner ads are
     * disabled (see handleBannerAdsMenu); auto-progress to the next step is
     * gated on showConsole becoming false in handleAutoProgress, so simply
     * dropping showConsole here is enough to unblock the story.
     */
    private fun closeConsole(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            showConsole = false,
            consoleStep = 0,
            number1 = "0"
        )
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