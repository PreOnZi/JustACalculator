package com.fictioncutshort.justacalculator.data



data class CalculatorState(
    // ═══════════════════════════════════════════════════════════════════════════
    // CALCULATOR CORE - Basic arithmetic functionality
    // ═══════════════════════════════════════════════════════════════════════════
    val number1: String = "0",
    val number2: String = "",
    val operation: String? = null,
    val expression: String = "",
    val isReadyForNewOperation: Boolean = true,
    val lastExpression: String = "",
    val operationHistory: String = "",

    // Paused calculator state (independent of story)
    val pausedCalcDisplay: String = "0",
    val pausedCalcExpression: String = "",
    val pausedCalcJustCalculated: Boolean = false,
    // ═══════════════════════════════════════════════════════════════════════════
    // STORY PROGRESSION - Tracks where the player is in the narrative
    // ═══════════════════════════════════════════════════════════════════════════

    /** How many times = has been pressed. Story begins at 13. */
    val equalsCount: Int = 0,

    /** Current step in the story (0-167). Each step has its own dialogue/behavior. */
    val conversationStep: Int = 0,

    /** True once the calculator "wakes up" and starts talking (after equalsCount >= 13) */
    val inConversation: Boolean = false,

    /** True when conversation is muted (orange button toggled off) */
    val isMuted: Boolean = false,

    /** Timestamp when timeout ends (calculator ignores input until then) */
    val timeoutUntil: Long = 0L,

    /** Timestamp when silent treatment ends (step 60 decline path) */
    val silentUntil: Long = 0L,
// for mute button stopping the story
    val pausedAtStep: Int = -1,

    // ═══════════════════════════════════════════════════════════════════════════
    // MESSAGE DISPLAY - What the calculator is "saying"
    // ═══════════════════════════════════════════════════════════════════════════

    /** Currently displayed text (builds up character by character) */
    val message: String = "",

    /** Full message to be typed out */
    val fullMessage: String = "",

    /** True while message is being typed character by character */
    val isTyping: Boolean = false,

    /** Slower, stuttering typing effect (used for "processing" feel) */
    val isLaggyTyping: Boolean = false,

    /** Very fast typing (used for history list scrolling) */
    val isSuperFastTyping: Boolean = false,

    /** Message to show automatically after current one finishes */
    val pendingAutoMessage: String = "",

    /** Step to go to after pendingAutoMessage is shown */
    val pendingAutoStep: Int = -1,

    /** True when waiting for auto-progress (keeps spinner spinning) */
    val waitingForAutoProgress: Boolean = false,

    // ═══════════════════════════════════════════════════════════════════════════
    // USER INPUT HANDLING - What kind of response we're waiting for
    // ═══════════════════════════════════════════════════════════════════════════

    /** True when expecting a specific number answer (trivia questions) */
    val awaitingNumber: Boolean = false,

    /** The correct answer for trivia questions */
    val expectedNumber: String = "",

    /** True when user is typing their answer */
    val isEnteringAnswer: Boolean = false,

    /** True when expecting a multiple choice answer (1, 2, or 3) */
    val awaitingChoice: Boolean = false,

    /** Valid choice options (e.g., listOf("1", "2", "3")) */
    val validChoices: List<String> = emptyList(),

    // ═══════════════════════════════════════════════════════════════════════════
    // CAMERA FEATURE - "Show me around" functionality (step 19)
    // ═══════════════════════════════════════════════════════════════════════════

    /** True when camera viewfinder is active */
    val cameraActive: Boolean = false,

    /** Timestamp when camera was opened (for timeout calculation) */
    val cameraTimerStart: Long = 0L,

    // ═══════════════════════════════════════════════════════════════════════════
    // BROWSER ANIMATION - Fake browser/Wikipedia display
    // ═══════════════════════════════════════════════════════════════════════════

    /** True when browser overlay is visible */
    val showBrowser: Boolean = false,

    /**
     * Controls browser animation sequence:
     * 0 = not showing
     * 1-4 = Google search animation
     * 10-22 = Wikipedia animation
     * 30-39 = Post-crisis repair sequence
     * 50-56 = Post-chaos online sequence
     */
    val browserPhase: Int = 0,

    /** Text being "typed" in browser search bar */
    val browserSearchText: String = "",

    /** Show "No internet connection" error */
    val browserShowError: Boolean = false,

    /** Show Wikipedia page (real WebView or fake fallback) */
    val browserShowWikipedia: Boolean = false,

    /** True if WebView failed to load (shows fake Wikipedia instead) */
    val browserLoadFailed: Boolean = false,

    // ═══════════════════════════════════════════════════════════════════════════
    // CRISIS/AD MODE - The "existential crisis" sequence (steps 80-100)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Ad animation state:
     * 0 = no ad
     * 1 = green "YOU WON" ad
     * 2 = pink "$500/DAY" ad
     */
    val adAnimationPhase: Int = 0,

    /** Post-chaos ad phase (purple/cyan ads after keyboard chaos) */
    val postChaosAdPhase: Int = 0,

    /** Shake intensity for buttons during crisis (0 = none) */
    val buttonShakeIntensity: Float = 0f,

    /** True during black screen moments */
    val screenBlackout: Boolean = false,

    /** Vibration strength during crisis (0-255) */
    val vibrationIntensity: Int = 0,

    /**
     * Tension level affects screen desaturation:
     * 0 = normal colors
     * 1 = slight desaturation
     * 2 = medium desaturation
     * 3 = heavy desaturation/flicker
     */
    val tensionLevel: Int = 0,

    /** True for inverted color scheme (black background, green text) */
    val invertedColors: Boolean = false,

    /** Countdown timer for step 89 choice (seconds remaining) */
    val countdownTimer: Int = 0,

    /** Quick white flash effect */
    val flickerEffect: Boolean = false,

    /** Alternates for B&W flicker during tension */
    val bwFlickerPhase: Boolean = false,

    // ═══════════════════════════════════════════════════════════════════════════
    // Scamble Game after time runs out in crisis (step 89 ish)
    // ═══════════════════════════════════════════════════════════════════════════

    val scrambleGameActive: Boolean = false,
    val scramblePhase: Int = 0,  // 0=inactive, 1=showing "blew it", 2=showing "deserve it", 3=game active, 4=showing acceptance, 5=showing button
    val scrambleLetters: List<ScrambleLetter> = emptyList(),
    val scrambleSlots: List<ScrambleSlot> = emptyList(),
    val scrambleDraggingIndex: Int = -1,
    val scrambleDragOffsetX: Float = 0f,
    val scrambleDragOffsetY: Float = 0f,
    val scrambleSelectedLetterId: Int = -1,
    val scrambleTimeoutCount: Int = 0,
    val scramblePunishmentUntil: Long = 0,

    // ═══════════════════════════════════════════════════════════════════════════
    // MINUS BUTTON DAMAGE - Post-crisis state (steps 93+)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Minus button appears damaged (brown color) */
    val minusButtonDamaged: Boolean = false,

    /** Minus button doesn't work (before repair mini-game) */
    val minusButtonBroken: Boolean = false,

    /** User needs to restart app to complete repair */
    val needsRestart: Boolean = false,

    // ═══════════════════════════════════════════════════════════════════════════
    // Phone Overlay Step 1071
    // ═══════════════════════════════════════════════════════════════════════════
val showPhoneOverlay: Boolean = false,
    val showTalkOverlay: Boolean = false,
    // ═══════════════════════════════════════════════════════════════════════════
    // WHACK-A-MOLE MINI-GAME - Button clicking game (steps 96-99)
    // ═══════════════════════════════════════════════════════════════════════════

    /** True when whack-a-mole game is active */
    val whackAMoleActive: Boolean = false,

    /** Current button to click (e.g., "7", "+") */
    val whackAMoleTarget: String = "",

    /** Buttons successfully clicked */
    val whackAMoleScore: Int = 0,

    /** Consecutive timeouts (misses) */
    val whackAMoleMisses: Int = 0,

    /** Wrong button clicks */
    val whackAMoleWrongClicks: Int = 0,

    /** Total errors (misses + wrong clicks) - 5 = game over */
    val whackAMoleTotalErrors: Int = 0,

    /** Current round: 1 = first (15 hits), 2 = second (10 hits, faster) */
    val whackAMoleRound: Int = 1,

    /** Button currently flashing yellow */
    val flickeringButton: String = "",

    // ═══════════════════════════════════════════════════════════════════════════
    // 3D KEYBOARD CHAOS - Floating letters mini-game (steps 105-107)
    // ═══════════════════════════════════════════════════════════════════════════

    /** True when 3D keyboard is displayed */
    val keyboardChaosActive: Boolean = false,

    /** List of floating letter cubes to tap away */
    val chaosLetters: List<ChaosKey> = emptyList(),

    /** 3D view rotation around X axis (tilt up/down) */
    val cubeRotationX: Float = 15f,

    /** 3D view rotation around Y axis (spin left/right) */
    val cubeRotationY: Float = -25f,

    /** Zoom level for 3D view */
    val cubeScale: Float = 1f,

    /**
     * Chaos animation phase:
     * 0 = not started
     * 1 = showing "..."
     * 2 = screen flickering
     * 3 = green flash
     * 5 = 3D view active
     */
    val chaosPhase: Int = 0,

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSOLE - Hidden settings menu (step 112+)
    // ═══════════════════════════════════════════════════════════════════════════

    /** True when console overlay is visible */
    val showConsole: Boolean = false,

    /**
     * Console menu navigation:
     * 0 = main menu
     * 1 = general settings
     * 2 = admin settings
     * 3 = app info
     * 4 = connectivity
     * 5 = advertising options
     * 51 = banner ads submenu
     * 52 = fullscreen ads submenu
     * 6 = permissions
     * 7 = design settings
     * 31 = contribute link
     * 99 = success message
     */
    val consoleStep: Int = 0,

    /** True after entering admin code (12340) */
    val adminCodeEntered: Boolean = false,

    /** True after disabling banner ads in console */
    val bannersDisabled: Boolean = false,

    /** True after enabling full-screen ads in console */
    val fullScreenAdsEnabled: Boolean = false,

    // ═══════════════════════════════════════════════════════════════════════════
    // BUTTON APPEARANCE - Visual states for calculator buttons
    // ═══════════════════════════════════════════════════════════════════════════

    /** Buttons that appear "damaged" (grayed out) */
    val darkButtons: List<String> = emptyList(),

    /** Extra RAD button visible (step 160) */
    val radButtonVisible: Boolean = false,

    /** All buttons show "RAD" text (step 162) */
    val allButtonsRad: Boolean = false,

    // ═══════════════════════════════════════════════════════════════════════════
    // WORD GAME - Letter falling game (steps 117-149)
    // ═══════════════════════════════════════════════════════════════════════════

    /** True when word game is active */
    val wordGameActive: Boolean = false,

    /**
     * Word game phase:
     * 0 = not started
     * 1 = intro message
     * 2 = setup
     * 3 = playing
     */
    val wordGamePhase: Int = 0,

    /** 12x8 grid of placed letters (null = empty cell) */
    val wordGameGrid: List<List<Char?>> = List(12) { List(8) { null } },

    /** Currently falling letter (null = none falling) */
    val fallingLetter: Char? = null,

    /** Falling letter X position (column 0-7) */
    val fallingLetterX: Int = 3,

    /** Falling letter Y position (row 0-11, 0 = top) */
    val fallingLetterY: Int = 0,

    /** Selected cells for word formation (row, col pairs) */
    val selectedCells: List<Pair<Int, Int>> = emptyList(),

    /** True when user is tapping to select letters */
    val isSelectingWord: Boolean = false,

    /** Words successfully formed this session */
    val formedWords: List<String> = emptyList(),

    /** Category of last word: "positive", "neutral", "negative" */
    val lastWordCategory: String = "",

    /** True when game is paused (during selection or message) */
    val wordGamePaused: Boolean = false,

    /** Queue of letters to drop next */
    val pendingLetters: List<Char> = emptyList(),

    /** Fast letter falling mode (step 127 chaos) */
    val wordGameChaosMode: Boolean = false,

    /** Story branch based on responses: "positive", "neutral", "negative" */
    val wordGameBranch: String = "",

    // Word game drag-and-drop state
    val draggingCell: Pair<Int, Int>? = null,
    val dragOffsetX: Float = 0f,
    val dragOffsetY: Float = 0f,
    val dragPreviewGrid: List<List<Char?>>? = null,
    val cellSizePx: Float = 40f,

    // ═══════════════════════════════════════════════════════════════════════════
    // RANT SEQUENCE - Final monologue (steps 150-167)
    // ═══════════════════════════════════════════════════════════════════════════

    /** True during final rant sequence */
    val rantMode: Boolean = false,

    /** Current message in rant sequence */
    val rantStep: Int = 0,

    /** True after step 167 - story is complete, calculator mode only */
    val storyComplete: Boolean = false,

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICS - Tracked for display in console and rant
    // ═══════════════════════════════════════════════════════════════════════════

    /** Total time app has been open (milliseconds) */
    val totalScreenTimeMs: Long = 0L,

    /** Total calculations performed */
    val totalCalculations: Int = 0,

    // ═══════════════════════════════════════════════════════════════════════════
    // UI STATE - Misc UI controls
    // ═══════════════════════════════════════════════════════════════════════════

    /** True when debug menu overlay is visible */
    val showDebugMenu: Boolean = false,

    /** True when donation/tip page is visible */
    val showDonationPage: Boolean = false,
)

/**
 * Represents a floating letter cube in the 3D keyboard chaos mini-game.
 *
 * Each letter has a position in 3D space and its own rotation/size.
 * These are rendered as small cubes floating around the main calculator keyboard.
 */
data class ChaosKey(
    /** The letter displayed on this cube (A-Z) */
    val letter: String,

    /** X position offset from center (-250 to 250 roughly) */
    val x: Float,

    /** Y position offset from center (-350 to 350 roughly) */
    val y: Float,

    /** Z position (depth) offset (-150 to 150 roughly) */
    val z: Float,

    /** Size multiplier (0.4 to 1.0) */
    val size: Float,

    /** Rotation around X axis (degrees) */
    val rotationX: Float,

    /** Rotation around Y axis (degrees) */
    val rotationY: Float
)