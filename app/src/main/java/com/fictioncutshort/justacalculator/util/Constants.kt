package com.fictioncutshort.justacalculator.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
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
 * - Responsive dimensions
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

/** Inverted bezel color - near black */
val BezelInverted = Color(0xFF1A1A1A)

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

// ═══════════════════════════════════════════════════════════════════════════
// RESPONSIVE DIMENSIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Responsive dimension provider that calculates sizes based on screen dimensions.
 * Use this to get consistent, proportional sizing across all devices.
 */
data class ResponsiveDimensions(
    val screenWidth: Dp,
    val screenHeight: Dp,
    val isLandscape: Boolean,
    val isTablet: Boolean,
    val statusBarHeight: Dp,

    // Calculated dimensions
    val adBannerHeight: Dp,
    val buttonRowHeight: Dp,
    val buttonSpacing: Dp,
    val lcdDisplayHeight: Dp,
    val contentPadding: Dp,
    val messageFontSize: Int,
    val displayFontSizeBase: Int,

    // Landscape-specific
    val leftPanelWeight: Float,
    val rightPanelWeight: Float,
    val keyboardWidth: Dp
)

/**
 * Creates responsive dimensions based on current screen configuration.
 * Call this in your Composable to get device-appropriate sizing.
 */
@Composable
fun rememberResponsiveDimensions(): ResponsiveDimensions {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp > 600
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Calculate proportional dimensions
    val shortestDimension = minOf(screenWidth, screenHeight)

    return ResponsiveDimensions(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        isLandscape = isLandscape,
        isTablet = isTablet,
        statusBarHeight = statusBarHeight,

        // Ad banner: ~6% of screen height, min 40dp, max 60dp
        adBannerHeight = (screenHeight.value * 0.06f).dp.coerceIn(40.dp, 60.dp),

        // Button rows: larger proportion in landscape since we have less vertical space
        buttonRowHeight = if (isLandscape) {
            (screenHeight.value * 0.12f).dp.coerceIn(44.dp, 56.dp)
        } else {
            (screenHeight.value * 0.075f).dp.coerceIn(50.dp, 65.dp)
        },

        // Button spacing: proportional to shortest dimension
        buttonSpacing = (shortestDimension.value * 0.02f).dp.coerceIn(4.dp, 10.dp),

        // LCD display height: proportional
        lcdDisplayHeight = if (isLandscape) {
            (screenHeight.value * 0.18f).dp.coerceIn(70.dp, 100.dp)
        } else {
            (screenHeight.value * 0.12f).dp.coerceIn(80.dp, 120.dp)
        },

        // Content padding: proportional
        contentPadding = (shortestDimension.value * 0.04f).dp.coerceIn(12.dp, 20.dp),

        // Font sizes (in sp, returned as Int)
        messageFontSize = if (isLandscape || screenWidth.value < 400) 22 else 28,
        displayFontSizeBase = if (isLandscape) 48 else 58,

        // Landscape panel weights
        leftPanelWeight = 0.58f,
        rightPanelWeight = 0.42f,

        // Keyboard width in landscape (fixed proportion of screen)
        keyboardWidth = if (isLandscape) {
            (screenWidth.value * 0.4f).dp.coerceIn(200.dp, 320.dp)
        } else {
            screenWidth  // Full width in portrait
        }
    )
}

/**
 * Calculate display font size based on text length.
 * Automatically scales down for longer numbers.
 */
fun calculateDisplayFontSize(textLength: Int, baseFontSize: Int): Int {
    return when {
        textLength > 12 -> (baseFontSize * 0.65f).toInt()
        textLength > 10 -> (baseFontSize * 0.78f).toInt()
        textLength > 8 -> (baseFontSize * 0.88f).toInt()
        else -> baseFontSize
    }
}