package com.fictioncutshort.justacalculator.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.fictioncutshort.justacalculator.R

/**
 * Constants.kt
 *
 * Centralized location for all app-wide constants including:
 * - Colors (retro theme palette)
 * - Fonts
 * - SharedPreferences keys
 * - Timing constants
 * - Calculator limits
 */

// ═══════════════════════════════════════════════════════════════════════════
// COLORS - Retro calculator theme
// ═══════════════════════════════════════════════════════════════════════════

/** Primary accent color - warm orange for buttons and highlights */
val AccentOrange = Color(0xFFE88617)

/** Classic green LCD display color (used in inverted/crisis mode) */
val RetroDisplayGreen = Color(0xFF33FF33)

/** Vintage cream/beige background color */
val RetroCream = Color(0xFFF5F0E1)

/** LCD display background - greenish gray like old calculators */
val LcdBackground = Color(0xFFCCD5AE)

/** Dark text color for light backgrounds */
val DarkText = Color(0xFF2D2D2D)

/** Top bezel color - dark brown wood tone */
val BezelBrown = Color(0xFF4A3728)

/** Ad banner placeholder color */
val BannerGray = Color(0xFFD4CBC0)

// ═══════════════════════════════════════════════════════════════════════════
// FONTS
// ═══════════════════════════════════════════════════════════════════════════

/** Digital-style font for calculator display and messages */
val CalculatorDisplayFont = FontFamily(
    Font(R.font.digital_7, FontWeight.Normal)
)

// ═══════════════════════════════════════════════════════════════════════════
// SHAREDPREFERENCES KEYS
// ═══════════════════════════════════════════════════════════════════════════

const val PREFS_NAME = "just_a_calculator_prefs"

// Story progress
const val PREF_EQUALS_COUNT = "equals_count"
const val PREF_CONVO_STEP = "conversation_step"
const val PREF_IN_CONVERSATION = "in_conversation"
const val PREF_MESSAGE = "last_message"

// User input state
const val PREF_AWAITING_NUMBER = "awaiting_number"
const val PREF_EXPECTED_NUMBER = "expected_number"
const val PREF_TIMEOUT_UNTIL = "timeout_until"

// Settings
const val PREF_MUTED = "muted"
const val PREF_TERMS_ACCEPTED = "terms_accepted"

// Crisis/repair state
const val PREF_INVERTED_COLORS = "inverted_colors"
const val PREF_MINUS_DAMAGED = "minus_damaged"
const val PREF_MINUS_BROKEN = "minus_broken"
const val PREF_NEEDS_RESTART = "needs_restart"

// Statistics
const val PREF_TOTAL_SCREEN_TIME = "total_screen_time"
const val PREF_TOTAL_CALCULATIONS = "total_calculations"

// Button damage
const val PREF_DARK_BUTTONS = "dark_buttons"

// ═══════════════════════════════════════════════════════════════════════════
// CALCULATOR LIMITS
// ═══════════════════════════════════════════════════════════════════════════

/** Maximum digits allowed in a number */
const val MAX_DIGITS = 12

/** Numbers larger than this trigger "testing me" message */
const val ABSURDLY_LARGE_THRESHOLD = 1_000_000_000_000.0

// ═══════════════════════════════════════════════════════════════════════════
// TIMING CONSTANTS
// ═══════════════════════════════════════════════════════════════════════════

/** Time window for detecting double-tap (++ or --) in milliseconds */
const val DOUBLE_PRESS_WINDOW_MS = 600L

/** Camera auto-timeout in milliseconds */
const val CAMERA_TIMEOUT_MS = 8000L

/** Time window for rapid mute button clicks (debug menu access) */
const val RAPID_CLICK_WINDOW_MS = 2000L

/** Number of rapid clicks to open debug menu */
const val DEBUG_MENU_CLICKS = 5

/** Number of rapid clicks to reset game */
const val RESET_CLICKS = 10

// ═══════════════════════════════════════════════════════════════════════════
// BUTTON LAYOUT
// ═══════════════════════════════════════════════════════════════════════════

/** Standard calculator button layout (5 rows x 4 columns) */
val BUTTON_LAYOUT = listOf(
    listOf("C", "( )", "%", "/"),
    listOf("7", "8", "9", "*"),
    listOf("4", "5", "6", "-"),
    listOf("1", "2", "3", "+"),
    listOf("DEL", "0", ".", "=")
)

/** All valid buttons for whack-a-mole game (minus excluded - it's broken) */
val WHACK_A_MOLE_BUTTONS = listOf(
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
    "+", "*", "/", "=", "%", "( )", ".", "C", "DEL"
)