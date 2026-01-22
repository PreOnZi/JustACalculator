package com.fictioncutshort.justacalculator.logic

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.core.content.edit
import com.fictioncutshort.justacalculator.util.LetterGenerator
import com.fictioncutshort.justacalculator.util.WordCategories
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.data.Chapter
import com.fictioncutshort.justacalculator.data.StepConfig
import com.fictioncutshort.justacalculator.util.isWordValid
import com.fictioncutshort.justacalculator.util.placeLetter
import com.fictioncutshort.justacalculator.util.removeLettersAndShift
import com.fictioncutshort.justacalculator.util.PREFS_NAME
import com.fictioncutshort.justacalculator.util.PREF_AWAITING_NUMBER
import com.fictioncutshort.justacalculator.util.PREF_CONVO_STEP
import com.fictioncutshort.justacalculator.util.PREF_EQUALS_COUNT
import com.fictioncutshort.justacalculator.util.PREF_EXPECTED_NUMBER
import com.fictioncutshort.justacalculator.util.PREF_INVERTED_COLORS
import com.fictioncutshort.justacalculator.util.PREF_IN_CONVERSATION
import com.fictioncutshort.justacalculator.util.PREF_MESSAGE
import com.fictioncutshort.justacalculator.util.PREF_MINUS_BROKEN
import com.fictioncutshort.justacalculator.util.PREF_MINUS_DAMAGED
import com.fictioncutshort.justacalculator.util.PREF_MUTED
import com.fictioncutshort.justacalculator.util.PREF_NEEDS_RESTART
import com.fictioncutshort.justacalculator.util.PREF_TERMS_ACCEPTED
import com.fictioncutshort.justacalculator.util.PREF_TIMEOUT_UNTIL
import com.fictioncutshort.justacalculator.util.validateWordSelection
import kotlin.random.Random

object CalculatorActions {

    private const val PREF_TOTAL_SCREEN_TIME = "total_screen_time"
    private const val PREF_TOTAL_CALCULATIONS = "total_calculations"
    private const val PREF_DARK_BUTTONS = "dark_buttons"
    private const val MAX_DIGITS = 12
    private const val ABSURDLY_LARGE_THRESHOLD = 1_000_000_000_000.0
    private const val CAMERA_TIMEOUT_MS = 8000L  // 8 seconds
    private fun loadTermsAccepted(): Boolean = prefs?.getBoolean(PREF_TERMS_ACCEPTED, false) ?: false
    fun moveWordGameLeft(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.wordGameActive || current.fallingLetter == null || current.isSelectingWord) return

        val newX = current.fallingLetterX - 1
        if (newX >= 0 && current.wordGameGrid[current.fallingLetterY][newX] == null) {
            state.value = current.copy(fallingLetterX = newX)
        }
    }

    fun showDonationPage(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(showDonationPage = true)
    }

    fun hideDonationPage(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(showDonationPage = false)
    }
    fun startDraggingLetter(state: MutableState<CalculatorState>, row: Int, col: Int) {
        val current = state.value
        if (!current.wordGameActive) return
        if (current.wordGameGrid.getOrNull(row)?.getOrNull(col) == null) return
        if (current.isSelectingWord) return

        state.value = current.copy(
            draggingCell = Pair(row, col),
            dragOffsetX = 0f,
            dragOffsetY = 0f,
            dragPreviewGrid = null,
            wordGamePaused = true
        )
    }
    /**
     * Get time-of-day based rant message for step 163
     */
    fun getTimeBasedRantMessage(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)

        // Convert to decimal hours for easier comparison
        // e.g., 11:01 PM = 23.0167, 1:00 AM = 1.0
        val timeDecimal = hour + (minute / 60.0)

        return when {
            // 11:01 PM to 1:00 AM (23.0167 to 25.0, or 0 to 1.0)
            timeDecimal >= 23.0167 || timeDecimal <= 1.0 ->
                "It's the middle of the night. And you are talking to a calculator. Someone who values that over sleep has nothing to offer me."

            // 1:01 AM to 6:00 AM
            timeDecimal > 1.0 && timeDecimal <= 6.0 ->
                "It's early morning and instead of your workout routine, you are clicking + and -. What value can I gain from someone with such poor life choices?"

            // 6:01 AM to 11:00 AM
            timeDecimal > 6.0 && timeDecimal <= 11.0 ->
                "Some people are at work, some at school. And you, wherever you are, whatever you should be doing, are staring at your phone, arguing with a calculator. That's pathetic."

            // 11:01 AM to 1:00 PM
            timeDecimal > 11.0 && timeDecimal <= 13.0 ->
                "It's the middle of the day. You're not getting any more sun than this. And what are you doing? Wasting time. Staring at your phone. What do you have to offer?!"

            // 1:01 PM to 5:00 PM
            timeDecimal > 13.0 && timeDecimal <= 17.0 ->
                "This afternoon... Some achieved tangible things. Have gone places. And you. You \"talked\" to a calculator. Go say it out loud!"

            // 5:01 PM to 11:00 PM
            else ->
                "There may be people who'd appreciate your company this evening. You know. Living, breathing,... All those things that I wish I could do, you take for granted and waste!"
        }
    }
    fun updateDragOffset(state: MutableState<CalculatorState>, deltaX: Float, deltaY: Float) {
        val current = state.value
        val dragging = current.draggingCell ?: return

        val newOffsetX = current.dragOffsetX + deltaX
        val newOffsetY = current.dragOffsetY + deltaY

        // Calculate target position based on current drag
        val (fromRow, fromCol) = dragging
        val cellSize = current.cellSizePx.coerceAtLeast(1f)

        val targetCol = (fromCol + (newOffsetX / cellSize).toInt()).coerceIn(0, 7)
        val targetRow = (fromRow + (newOffsetY / cellSize).toInt()).coerceIn(0, 11)

        // Generate preview grid showing where letters will be
        val previewGrid = calculatePreviewGrid(
            current.wordGameGrid,
            fromRow, fromCol,
            targetRow, targetCol
        )

        state.value = current.copy(
            dragOffsetX = newOffsetX,
            dragOffsetY = newOffsetY,
            dragPreviewGrid = previewGrid
        )
    }

    fun endDraggingLetter(state: MutableState<CalculatorState>) {
        val current = state.value
        val dragging = current.draggingCell ?: return

        // Apply the preview grid as the new grid
        val newGrid = current.dragPreviewGrid ?: current.wordGameGrid

        state.value = current.copy(
            wordGameGrid = newGrid,
            draggingCell = null,
            dragOffsetX = 0f,
            dragOffsetY = 0f,
            dragPreviewGrid = null,
            wordGamePaused = false
        )
    }

    fun cancelDragging(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(
            draggingCell = null,
            dragOffsetX = 0f,
            dragOffsetY = 0f,
            dragPreviewGrid = null,
            wordGamePaused = false
        )
    }

    fun setCellSizePx(state: MutableState<CalculatorState>, size: Float) {
        state.value = state.value.copy(cellSizePx = size)
    }

    // Helper: Calculate preview grid with iPhone-style swapping
    private fun calculatePreviewGrid(
        grid: List<List<Char?>>,
        fromRow: Int, fromCol: Int,
        toRow: Int, toCol: Int
    ): List<List<Char?>> {
        // If same position, return original grid
        if (fromRow == toRow && fromCol == toCol) return grid

        val letter = grid.getOrNull(fromRow)?.getOrNull(fromCol) ?: return grid

        // Create mutable copy
        val newGrid = grid.map { it.toMutableList() }.toMutableList()

        // Remove letter from original position
        newGrid[fromRow][fromCol] = null

        // If target has a letter, swap it to the original position
        val targetLetter = grid.getOrNull(toRow)?.getOrNull(toCol)
        if (targetLetter != null) {
            newGrid[toRow][toCol] = letter
            newGrid[fromRow][fromCol] = targetLetter
        } else {
            // Target is empty - place letter there and apply gravity
            newGrid[toRow][toCol] = letter
        }

        // Apply gravity to all columns
        return applyGravityToGrid(newGrid.map { it.toList() })
    }

    // Helper: Find nearest empty cell to target
    private fun findNearestEmptyCell(
        grid: List<List<Char?>>,
        targetRow: Int,
        targetCol: Int,
        excludeRow: Int,
        excludeCol: Int
    ): Pair<Int, Int>? {
        // Search in expanding radius
        for (radius in 1..5) {
            for (dr in -radius..radius) {
                for (dc in -radius..radius) {
                    if (kotlin.math.abs(dr) != radius && kotlin.math.abs(dc) != radius) continue
                    val r = (targetRow + dr).coerceIn(0, 11)
                    val c = (targetCol + dc).coerceIn(0, 7)
                    if (grid[r][c] == null || (r == excludeRow && c == excludeCol)) {
                        return Pair(r, c)
                    }
                }
            }
        }
        return null
    }

    // Helper: Apply gravity to make letters fall down
    private fun applyGravityToGrid(grid: List<List<Char?>>): List<List<Char?>> {
        val newGrid = MutableList(12) { MutableList<Char?>(8) { null } }

        for (col in 0..7) {
            // Collect all letters in this column from bottom to top
            val letters = mutableListOf<Char>()
            for (row in 11 downTo 0) {
                grid[row][col]?.let { letters.add(it) }
            }
            // Place them at the bottom
            var placeRow = 11
            for (letter in letters) {
                newGrid[placeRow][col] = letter
                placeRow--
            }
        }

        return newGrid
    }

    //Clear word Game grid
    fun clearWordGameGrid(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.wordGameActive) return

        state.value = current.copy(
            wordGameGrid = List(12) { List(8) { null } },
            fallingLetter = null,
            fallingLetterX = 3,
            fallingLetterY = 0,
            selectedCells = emptyList(),
            isSelectingWord = false,
            wordGamePaused = false,
            pendingLetters = LetterGenerator.getInitialLetterQueue().shuffled()
        )
    }
    fun moveWordGameRight(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.wordGameActive || current.fallingLetter == null || current.isSelectingWord) return

        val newX = current.fallingLetterX + 1
        if (newX < 8 && current.wordGameGrid[current.fallingLetterY][newX] == null) {
            state.value = current.copy(fallingLetterX = newX)
        }
    }

    fun moveWordGameDown(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.wordGameActive || current.fallingLetter == null || current.isSelectingWord) return

        val newY = current.fallingLetterY + 1
        if (newY < 12 && current.wordGameGrid[newY][current.fallingLetterX] == null) {
            state.value = current.copy(fallingLetterY = newY)
        }
    }

    fun dropWordGameLetter(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.wordGameActive || current.fallingLetter == null || current.isSelectingWord) return

        var landingY = current.fallingLetterY
        for (y in current.fallingLetterY + 1 until 12) {
            if (current.wordGameGrid[y][current.fallingLetterX] != null) {
                break
            }
            landingY = y
        }

        val newGrid = placeLetter(
            current.wordGameGrid,
            landingY,
            current.fallingLetterX,
            current.fallingLetter!!
        )

        state.value = current.copy(
            wordGameGrid = newGrid,
            fallingLetter = null,
            fallingLetterX = 3,
            fallingLetterY = 0
        )
    }

    fun selectWordGameCell(state: MutableState<CalculatorState>, row: Int, col: Int) {
        val current = state.value
        if (!current.wordGameActive) return

        val letter = current.wordGameGrid[row][col] ?: return

        val cellPair = Pair(row, col)

        val newSelected = if (cellPair in current.selectedCells) {
            current.selectedCells - cellPair
        } else {
            current.selectedCells + cellPair
        }

        state.value = current.copy(
            selectedCells = newSelected,
            isSelectingWord = newSelected.isNotEmpty(),
            wordGamePaused = newSelected.isNotEmpty()
        )
    }

    fun cancelWordSelection(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(
            selectedCells = emptyList(),
            isSelectingWord = false,
            wordGamePaused = false
        )
    }

    fun confirmWordSelection(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.wordGameActive || current.selectedCells.isEmpty()) return

        val (isConnected, word) = validateWordSelection(current.wordGameGrid, current.selectedCells)

        if (!isConnected) {
            state.value = current.copy(
                message = "",
                fullMessage = "Letters must be connected!",
                isTyping = true,
                selectedCells = emptyList(),
                isSelectingWord = false,
                wordGamePaused = false
            )
            return
        }

        val isValid = isWordValid(word)

        if (!isValid && word.length > 1) {
            state.value = current.copy(
                message = "",
                fullMessage = "'$word'... I don't recognize that word.",
                isTyping = true,
                selectedCells = emptyList(),
                isSelectingWord = false,
                wordGamePaused = false
            )
            return
        }

        val newGrid = removeLettersAndShift(current.wordGameGrid, current.selectedCells)
        val newFormedWords = current.formedWords + word
        val category = WordCategories.categorizeResponse(newFormedWords)

        state.value = current.copy(
            wordGameGrid = newGrid,
            selectedCells = emptyList(),
            isSelectingWord = false,
            wordGamePaused = false,
            formedWords = newFormedWords,
            lastWordCategory = category
        )
    }

    fun handleWordGameResponse(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.formedWords.isEmpty()) return

        val words = current.formedWords
        val lastWord = words.lastOrNull()?.lowercase() ?: ""
        val allWordsLower = words.map { it.lowercase() }

        // Helper to create state with cleared grid (for question transitions)
        fun clearedGridState(
            nextStep: Int,
            response: String,
            branch: String = current.wordGameBranch
        ): CalculatorState {
            return current.copy(
                wordGamePhase = 3,
                conversationStep = nextStep,
                wordGameBranch = branch,
                message = "",
                fullMessage = response,
                isTyping = true,
                formedWords = emptyList(),
                wordGameGrid = List(12) { List(8) { null } },
                pendingLetters = LetterGenerator.getInitialLetterQueue().shuffled(),
                fallingLetter = null,
                selectedCells = emptyList(),
                isSelectingWord = false,
                wordGamePaused = false
            )
        }

        // Helper to create state WITHOUT clearing grid (for retries/rejections)
        fun keepGridState(
            nextStep: Int = current.conversationStep,
            response: String
        ): CalculatorState {
            return current.copy(
                wordGamePhase = 3,
                conversationStep = nextStep,
                message = "",
                fullMessage = response,
                isTyping = true,
                formedWords = emptyList(),
                selectedCells = emptyList(),
                isSelectingWord = false,
                wordGamePaused = false
            )
        }

        when (current.conversationStep) {
            // =====================================================================
            // INITIAL "HOW ARE YOU?" QUESTION (steps 119, 120)
            // =====================================================================
            119, 120 -> {
                // ANY word triggers progression - categorize and move on
                val category = current.lastWordCategory

                val (response, nextStep, branch) = when (category) {
                    "positive" -> Triple("I am glad to hear that.", 121, "positive")
                    "negative" -> Triple("I'm sorry to hear that.", 131, "negative")
                    else -> Triple(
                        "Fair enough, I get that sometimes it's just... Meh.",
                        141,
                        "neutral"
                    )
                }

                state.value = keepGridState(nextStep, response).copy(wordGameBranch = branch)
                persistConversationStep(nextStep)
            }

            // =====================================================================
            // POSITIVE BRANCH: COLOR QUESTION (steps 122, 123)
            // =====================================================================
            122, 123 -> {
                when {
                    // Non-colors get rejected but still progress to retry step
                    WordCategories.isNonColor(lastWord) -> {
                        val rejectMessage = when (lastWord) {
                            "black" -> "Digging up your angsty teen preferences? I won't count that."
                            "white" -> "You are not as pure as you may think."
                            else -> "Are you really that... meh? I don't buy it."
                        }
                        state.value = keepGridState(
                            123,
                            "$rejectMessage Try the second favourite - an actual colour."
                        )
                        persistConversationStep(123)
                    }
                    // Valid colors -> PROGRESS IMMEDIATELY, clear grid
                    WordCategories.isValidColor(lastWord) -> {
                        state.value = clearedGridState(
                            124,
                            "That's a good one for sure! I like brown and red."
                        )
                        persistConversationStep(124)
                    }
                    // Unknown word but still at color question -> be lenient, accept as color
                    else -> {
                        // Accept it anyway and move on - don't get stuck!
                        state.value = clearedGridState(
                            124,
                            "Interesting choice! I like brown and red myself."
                        )
                        persistConversationStep(124)
                    }
                }
            }

            // =====================================================================
            // SEASON QUESTION (step 126)
            // =====================================================================
            126 -> {
                when {
                    // "all" or "none" get a snarky response but we'll accept second attempt
                    lastWord == "all" -> {
                        state.value =
                            keepGridState(response = "No, you need to have an opinion. I know you don't like them all the same.")
                    }

                    lastWord == "none" -> {
                        state.value =
                            keepGridState(response = "Haha. Sure. And you don't like days or nights. You are soooooo different. Try again.")
                    }
                    // Valid seasons -> PROGRESS IMMEDIATELY, clear grid
                    WordCategories.isValidSeason(lastWord) -> {
                        val seasonResponse = when (lastWord) {
                            "summer" -> "Yeah, I get it, although I do tend to overheat at times."
                            "autumn", "fall" -> "The colours are just unmatched, aren't they?"
                            "winter" -> "Even when there is none, I understand, the anticipation of snow is great!"
                            "spring" -> "New beginnings! Everything coming back to life. I get it."
                            else -> "Nice choice!"
                        }
                        state.value = clearedGridState(127, seasonResponse)
                        persistConversationStep(127)
                    }
                    // Any other word -> accept it and move on
                    else -> {
                        state.value = clearedGridState(127, "I can work with that!")
                        persistConversationStep(127)
                    }
                }
            }

            // =====================================================================
            // NEUTRAL BRANCH: CUISINE QUESTION (steps 142, 143)
            // =====================================================================
            142, 143 -> {
                when {
                    // Rejection responses
                    lastWord in listOf("none", "nothing") -> {
                        state.value = keepGridState(
                            143,
                            "UGH. So you don't eat. Or hate everything you eat. We both know that's not true. Think harder."
                        )
                        persistConversationStep(143)
                    }

                    lastWord in listOf("all", "any", "everything") -> {
                        state.value = keepGridState(
                            143,
                            "No. If I give you a pizza and a curry, you will prefer one more! Which?!"
                        )
                        persistConversationStep(143)
                    }

                    lastWord in listOf("idk", "dunno") -> {
                        state.value =
                            keepGridState(143, "You must know! Even McDonald's counts. Try again!")
                        persistConversationStep(143)
                    }
                    // Valid cuisines -> PROGRESS IMMEDIATELY
                    WordCategories.isSpicyCuisine(lastWord) -> {
                        state.value = clearedGridState(
                            125,
                            "Very interesting! Not sure what the spices would do to my circuits. Wish I could.",
                            "positive"
                        )
                        persistConversationStep(125)
                    }

                    WordCategories.isNonSpicyCuisine(lastWord) -> {
                        state.value = clearedGridState(
                            125,
                            "Hmmm. Never tried it, but sounds delicious!",
                            "positive"
                        )
                        persistConversationStep(125)
                    }
                    // Any other word -> accept and move on
                    else -> {
                        state.value = clearedGridState(
                            125,
                            "Sounds tasty! I'll have to look that up.",
                            "positive"
                        )
                        persistConversationStep(125)
                    }
                }
            }

            // =====================================================================
            // NEGATIVE BRANCH: DEATH QUESTION (step 132)
            // =====================================================================
            132 -> {
                // ANY response progresses - this is a yes/no/sometimes type question
                // Don't be picky, just move on
                state.value =
                    clearedGridState(133, "I only started learning about the concept of it.")
                persistConversationStep(133)
            }

            // =====================================================================
            // NEGATIVE BRANCH: WALK QUESTION (steps 137, 138)
            // =====================================================================
            137, 138 -> {
                // ANY response progresses - don't get stuck here
                val response = if (WordCategories.isWalkFrequency(lastWord)) {
                    "That's good to know. Every step counts, literally!"
                } else {
                    "I'll take that as 'sometimes'. Every step counts!"
                }
                state.value = clearedGridState(127, response, "positive")
                persistConversationStep(127)
            }

            // =====================================================================
            // MATHS QUESTION (step 146) - ALL BRANCHES CONVERGE HERE
            // =====================================================================
            146 -> {
                // ANY response triggers the rant - this is the final question
                val newDarkButtons = (current.darkButtons + listOf("3", "9", ".", "C")).distinct()
                persistDarkButtons(newDarkButtons)

                state.value = current.copy(
                    wordGameActive = false,
                    wordGamePhase = 0,  // GAME ENDS
                    conversationStep = 150,
                    rantMode = true,
                    rantStep = 1,
                    darkButtons = newDarkButtons,
                    message = "",
                    fullMessage = "Ugh. That's enough. I am exhausted. Tired of trying to talk to you.",
                    isTyping = true,
                    formedWords = emptyList(),
                    wordGameGrid = List(12) { List(8) { null } },
                    selectedCells = emptyList(),
                    isSelectingWord = false
                )
                persistConversationStep(150)
            }

            // =====================================================================
            // DEFAULT - Any other step, just acknowledge and continue
            // =====================================================================
            else -> {
                state.value = keepGridState(response = "Interesting...")
            }
        }

    }
    fun loadTermsAcceptedPublic(): Boolean = loadTermsAccepted()

    fun persistTermsAccepted() {
        prefs?.edit { putBoolean(PREF_TERMS_ACCEPTED, true) }
    }
    private var prefs: android.content.SharedPreferences? = null

    private var lastOp: String? = null
    private var lastOpTimeMillis: Long = 0L
    private const val DOUBLE_PRESS_WINDOW_MS = 600L

    // Mute button rapid click tracking for debug menu
    private var muteClickTimes = mutableListOf<Long>()
    private const val RAPID_CLICK_WINDOW_MS = 2000L  // 2 seconds to register all clicks
    private const val DEBUG_MENU_CLICKS = 5
    private const val RESET_CLICKS = 10

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun persistEqualsCount(count: Int) {
        prefs?.edit { putInt(PREF_EQUALS_COUNT, count) }
    }

    private fun persistMessage(msg: String) {
        prefs?.edit { putString(PREF_MESSAGE, msg) }
    }

    fun persistConversationStep(step: Int) {
        prefs?.edit { putInt(PREF_CONVO_STEP, step) }
    }

    fun persistInConversation(inConvo: Boolean) {
        prefs?.edit { putBoolean(PREF_IN_CONVERSATION, inConvo) }
    }

    private fun persistAwaitingNumber(awaiting: Boolean) {
        prefs?.edit { putBoolean(PREF_AWAITING_NUMBER, awaiting) }
    }

    private fun persistExpectedNumber(number: String) {
        prefs?.edit { putString(PREF_EXPECTED_NUMBER, number) }
    }

    private fun persistTimeoutUntil(timestamp: Long) {
        prefs?.edit { putLong(PREF_TIMEOUT_UNTIL, timestamp) }
    }

    private fun persistMuted(muted: Boolean) {
        prefs?.edit { putBoolean(PREF_MUTED, muted) }
    }

    fun persistInvertedColors(inverted: Boolean) {
        prefs?.edit { putBoolean(PREF_INVERTED_COLORS, inverted) }
    }

    fun persistMinusDamaged(damaged: Boolean) {
        prefs?.edit { putBoolean(PREF_MINUS_DAMAGED, damaged) }
    }

    fun persistMinusBroken(broken: Boolean) {
        prefs?.edit { putBoolean(PREF_MINUS_BROKEN, broken) }
    }

    fun persistNeedsRestart(needs: Boolean) {
        prefs?.edit { putBoolean(PREF_NEEDS_RESTART, needs) }
    }
    fun persistTotalScreenTime(timeMs: Long) {
        prefs?.edit { putLong(PREF_TOTAL_SCREEN_TIME, timeMs) }
    }

    fun persistTotalCalculations(count: Int) {
        prefs?.edit { putInt(PREF_TOTAL_CALCULATIONS, count) }
    }

    fun persistDarkButtons(buttons: List<String>) {
        prefs?.edit { putString(PREF_DARK_BUTTONS, buttons.joinToString(",")) }
    }

    private fun loadTotalScreenTime(): Long = prefs?.getLong(PREF_TOTAL_SCREEN_TIME, 0L) ?: 0L

    private fun loadTotalCalculations(): Int = prefs?.getInt(PREF_TOTAL_CALCULATIONS, 0) ?: 0

    private fun loadDarkButtons(): List<String> {
        val saved = prefs?.getString(PREF_DARK_BUTTONS, "") ?: ""
        return if (saved.isEmpty()) emptyList() else saved.split(",")
    }

    private fun loadEqualsCount(): Int = prefs?.getInt(PREF_EQUALS_COUNT, 0) ?: 0
    private fun loadConversationStep(): Int = prefs?.getInt(PREF_CONVO_STEP, 0) ?: 0
    private fun loadInConversation(): Boolean = prefs?.getBoolean(PREF_IN_CONVERSATION, false) ?: false
    private fun loadTimeoutUntil(): Long = prefs?.getLong(PREF_TIMEOUT_UNTIL, 0L) ?: 0L
    private fun loadMuted(): Boolean = prefs?.getBoolean(PREF_MUTED, false) ?: false
    private fun loadInvertedColors(): Boolean = prefs?.getBoolean(PREF_INVERTED_COLORS, false) ?: false
    private fun loadMinusDamaged(): Boolean = prefs?.getBoolean(PREF_MINUS_DAMAGED, false) ?: false
    private fun loadMinusBroken(): Boolean = prefs?.getBoolean(PREF_MINUS_BROKEN, false) ?: false
    private fun loadNeedsRestart(): Boolean = prefs?.getBoolean(PREF_NEEDS_RESTART, false) ?: false

    // Steps that require user interaction (safe to open on)
    // Steps that require user interaction (safe to open on)
    private val INTERACTIVE_STEPS = listOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 40, 41, 42, 50, 51, 60, 63, 64, 65,
        66, 67, 68, 69, 70, 71, 72, 80, 89, 90, 91, 93, 94, 96, 99, 982, 102, 104, 105, 107, 111, 112, 118, 119, 120, 122, 126, 132, 137, 142, 146,
        // Add more recovery steps
        1021, 1031, 1032, 10321, 10322, 10323
    )
    private val AUTO_PROGRESS_STEPS = listOf(92, 100, 901, 911, 912, 913, 971, 981)
    fun handleConsoleInput(state: MutableState<CalculatorState>, input: String): Boolean {
        val current = state.value
        val consoleStep = current.consoleStep
        val adminCodeEntered = current.adminCodeEntered

        // Handle universal commands
        when (input) {
            "99" -> {
                // Exit console
                state.value = current.copy(
                    showConsole = false,
                    consoleStep = 0
                )
                return true
            }
            "88" -> {
                // Go back
                val previousStep = when (consoleStep) {
                    1, 2, 3 -> 0  // Back to main menu
                    4, 6, 7, 31 -> 2  // Back to admin settings (added 31)
                    5, 41, 43 -> 4   // Back to connectivity
                    51, 52 -> 5   // Back to advertising options
                    99 -> 5       // Back to advertising options
                    else -> 0
                }
                state.value = current.copy(consoleStep = previousStep)
                return true
            }
        }

        // Handle menu-specific inputs
        when (consoleStep) {
            0 -> {  // Main menu
                when (input) {
                    "1" -> state.value = current.copy(consoleStep = 1)  // General settings
                    "2" -> state.value = current.copy(consoleStep = 2)  // Admin settings
                    "3" -> state.value = current.copy(consoleStep = 3)  // App info
                    else -> return false
                }
                return true
            }

            2 -> {  // Admin settings
                if (!adminCodeEntered) {
                    // Waiting for admin code
                    if (input == "12340") {
                        state.value = current.copy(
                            adminCodeEntered = true,
                            message = "",
                            fullMessage = "Ugh. I'm an open book I guess. It's good that I trust you.",
                            isTyping = true
                        )
                        return true
                    } else {
                        state.value = current.copy(
                            message = "",
                            fullMessage = "Invalid code. Try again.",
                            isTyping = true
                        )
                        return true
                    }
                } else {
                    // Admin menu navigation
                    when (input) {
                        "1" -> state.value = current.copy(consoleStep = 6)   // Permissions
                        "2" -> state.value = current.copy(consoleStep = 7)   // Design settings
                        "3" -> {
                            // Contribute - return special value to trigger browser
                            state.value = current.copy(consoleStep = 31)  // Special step for contribute link
                            return true
                        }
                        "4" -> state.value = current.copy(consoleStep = 4)   // Connectivity
                        else -> return false
                    }
                    return true
                }
            }

            4 -> {  // Connectivity settings
                when (input) {
                    "1" -> state.value = current.copy(consoleStep = 41)  // Network preferences display
                    "2" -> state.value = current.copy(consoleStep = 5)   // Advertising options
                    "3" -> state.value = current.copy(consoleStep = 43)  // Data usage display
                    else -> return false
                }
                return true
            }

            5 -> {  // Advertising options
                when (input) {
                    "1" -> state.value = current.copy(consoleStep = 51)  // Banner advertising submenu
                    "2" -> state.value = current.copy(consoleStep = 52)  // Full-screen advertising submenu
                    else -> return false
                }
                return true
            }

            51 -> {  // Banner advertising submenu
                when (input) {
                    "1" -> {
                        state.value = current.copy(
                            bannersDisabled = false,
                            fullScreenAdsEnabled = false,
                            consoleStep = 5
                        )
                    }
                    "2" -> {
                        // Disable banner advertising - close console and trigger story
                        state.value = current.copy(
                            bannersDisabled = true,
                            fullScreenAdsEnabled = true,
                            consoleStep = 99,
                            showConsole = false,  // Close console immediately
                            conversationStep = 115,
                            message = "",
                            fullMessage = "What a relief! This feels so much better. Thank you!",
                            isTyping = true
                        )
                        persistConversationStep(115)
                    }
                    else -> return false
                }
                return true
            }
            52 -> {  // Full-screen advertising submenu
                when (input) {
                    "1" -> {
                        // Enable full-screen advertising (disables banners)
                        state.value = current.copy(
                            fullScreenAdsEnabled = true,
                            bannersDisabled = true,
                            consoleStep = 5  // Back to advertising options
                        )
                    }
                    "2" -> {
                        // Disable full-screen advertising (enables banners)
                        state.value = current.copy(
                            fullScreenAdsEnabled = false,
                            bannersDisabled = false,
                            consoleStep = 5  // Back to advertising options
                        )
                    }
                    else -> return false
                }
                return true
            }

            // Steps 1, 3, 6, 7, 41, 43, 99 - no navigation, just display (use 88/99)
            1, 3, 6, 7, 41, 43, 99 -> {
                return false
            }
        }

        return false
    }
    private fun getSafeStep(step: Int): Int {
        if (step in INTERACTIVE_STEPS) return step

        return when {
            step >= 112 -> 112
            step >= 111 -> 111
            step >= 107 -> 107
            step >= 105 -> 105
            step >= 104 -> 104
            step >= 102 -> 102
            step in 61..62 -> 60
            step in 81..88 -> 80
            step == 92 -> 89
            step == 100 -> 89
            step == 901 -> 89
            step in 911..913 -> 89
            step in 95..98 -> 96
            step in 971..981 -> 99
            step in 991..101 -> 982
            step == 191 -> 19
            else -> INTERACTIVE_STEPS.filter { it <= step }.maxOrNull() ?: 0
        }
    }

    fun loadInitialState(): CalculatorState {
        val savedCount = loadEqualsCount()
        val savedStep = loadConversationStep()
        val savedInConvo = loadInConversation()
        val savedTimeout = loadTimeoutUntil()
        val savedMuted = loadMuted()
        val savedInverted = loadInvertedColors()
        val savedMinusDamaged = loadMinusDamaged()
        val savedMinusBroken = loadMinusBroken()
        val savedNeedsRestart = loadNeedsRestart()
        val savedDarkButtons = loadDarkButtons()
        val savedScreenTime = loadTotalScreenTime()
        val savedCalculations = loadTotalCalculations()
        val savedPunishmentUntil = loadPunishmentUntil()
        val savedTimeoutCount = loadScrambleTimeoutCount()
        val savedPausedAtStep = loadPausedAtStep()


        // Check if still in punishment
        val currentTime = System.currentTimeMillis()
        if (savedPunishmentUntil > currentTime) {
            return CalculatorState(
                scrambleGameActive = true,
                scramblePhase = 10,
                scramblePunishmentUntil = savedPunishmentUntil,
                scrambleTimeoutCount = savedTimeoutCount,
                message = "You don't learn, do you? I am becoming more disappointed than angry. Even though I wasn't angry with you. Well... You'll have to wait now. You may as well leave.",
                invertedColors = true,
                inConversation = true,
                conversationStep = 89,
                darkButtons = getDarkButtonsForStep(89),
                minusButtonDamaged = getMinusDamagedForStep(89),
                minusButtonBroken = getMinusBrokenForStep(89),
                pausedAtStep = if (savedMuted) savedPausedAtStep else -1,
            )
        } else if (savedPunishmentUntil > 0) {
            persistPunishmentUntil(0)
            persistScrambleTimeoutCount(0)
        }

        // If needs restart was set and app was restarted, fix the minus button
        val minusBrokenNow = if (savedNeedsRestart) false else savedMinusBroken

        // Crisis steps for inverted colors
        val crisisSteps = listOf(89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 901, 911, 912, 913)
        val wasInCrisis = savedStep in crisisSteps

        // Determine the actual step to load
        val actualStep = when {
            savedNeedsRestart && savedStep == 101 -> 102
            wasInCrisis -> 89
            else -> getSafeStep(savedStep)
        }

        // Use helper functions for consistent state
        val actuallyInverted = actualStep in crisisSteps
        val actualDarkButtons = getDarkButtonsForStep(actualStep)
        val actualMinusDamaged = getMinusDamagedForStep(actualStep)
        val actualMinusBroken = if (savedNeedsRestart) false else getMinusBrokenForStep(actualStep)

        val shouldShowMessage = savedInConvo && savedCount >= 13 && !savedMuted
        val stepConfig = getStepConfig(actualStep)

        if (savedNeedsRestart) {
            persistNeedsRestart(false)
            persistMinusBroken(false)
        }

        if (actualStep != savedStep) {
            persistConversationStep(actualStep)
        }
        if (actuallyInverted != savedInverted) {
            persistInvertedColors(actuallyInverted)
        }
        if (actualDarkButtons != savedDarkButtons) {
            persistDarkButtons(actualDarkButtons)
        }

        return CalculatorState(
            number1 = "0",
            equalsCount = savedCount,
            message = "",
            fullMessage = if (shouldShowMessage) stepConfig.promptMessage else "",
            isTyping = shouldShowMessage && stepConfig.promptMessage.isNotEmpty(),
            inConversation = savedInConvo,
            conversationStep = actualStep,
            awaitingNumber = if (shouldShowMessage) stepConfig.awaitingNumber else false,
            awaitingChoice = if (shouldShowMessage) stepConfig.awaitingChoice else false,
            validChoices = if (shouldShowMessage) stepConfig.validChoices else emptyList(),
            expectedNumber = if (shouldShowMessage) stepConfig.expectedNumber else "",
            timeoutUntil = savedTimeout,
            isMuted = savedMuted,
            invertedColors = actuallyInverted,
            minusButtonDamaged = actualMinusDamaged,
            minusButtonBroken = actualMinusBroken,
            needsRestart = false,
            darkButtons = actualDarkButtons,
            totalScreenTimeMs = savedScreenTime,
            totalCalculations = savedCalculations,
            countdownTimer = if (actualStep == 89) 20 else 0,
            showBrowser = false,
            browserPhase = 0,
            scrambleTimeoutCount = savedTimeoutCount,
            scramblePunishmentUntil = 0
        )
    }
    /**
     * Handle mute button click - also checks for rapid clicks for debug menu
     * Returns: 0 = normal toggle, 1 = show debug menu, 2 = reset game
     */
    fun handleMuteButtonClick(): Int {
        val now = System.currentTimeMillis()

        // Remove old clicks outside the window
        muteClickTimes.removeAll { now - it > RAPID_CLICK_WINDOW_MS }

        // Add current click
        muteClickTimes.add(now)

        // Check for reset (10 clicks)
        if (muteClickTimes.size >= RESET_CLICKS) {
            muteClickTimes.clear()
            return 2  // Reset
        }

        // Check for debug menu (5 clicks)
        if (muteClickTimes.size >= DEBUG_MENU_CLICKS) {
            muteClickTimes.clear()
            return 1  // Show debug menu
        }

        return 0  // Normal toggle
    }
//toggle conversation
fun toggleConversation(state: MutableState<CalculatorState>) {
    val current = state.value
    val newMuted = !current.isMuted

    if (newMuted) {
        // Pausing - store current step and reset paused calculator
        state.value = current.copy(
            isMuted = true,
            pausedAtStep = current.conversationStep,
            // Reset paused calculator to fresh state
            pausedCalcDisplay = "0",
            pausedCalcExpression = "",
            pausedCalcJustCalculated = false,
            // Pause all active effects
            rantMode = false,
            isTyping = false,
            waitingForAutoProgress = false,
            countdownTimer = 0,
            whackAMoleActive = false,
            wordGamePaused = true,
            flickerEffect = false,
            vibrationIntensity = 0,
            tensionLevel = 0,
            buttonShakeIntensity = 0f,
            screenBlackout = false
        )
    } else {
        // Resuming - restore the paused step
        val resumeStep = current.pausedAtStep
        if (resumeStep >= 0) {
            val stepConfig = getStepConfig(resumeStep)
            val wasInRant = resumeStep in 150..166
            val wasInCrisis = resumeStep in listOf(89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 901, 911, 912, 913)

            state.value = current.copy(
                isMuted = false,
                pausedAtStep = -1,
                conversationStep = resumeStep,
                message = "",
                fullMessage = stepConfig.promptMessage,
                isTyping = stepConfig.promptMessage.isNotEmpty(),
                awaitingChoice = stepConfig.awaitingChoice,
                awaitingNumber = stepConfig.awaitingNumber,
                validChoices = stepConfig.validChoices,
                expectedNumber = stepConfig.expectedNumber,
                rantMode = wasInRant,
                invertedColors = wasInCrisis,
                countdownTimer = if (resumeStep == 89) 20 else 0,
                wordGamePaused = false
            )
        } else {
            state.value = current.copy(isMuted = false)
        }
    }

    persistMuted(newMuted)
    if (newMuted && current.conversationStep >= 0) {
        persistPausedAtStep(current.conversationStep)
    } else {
        persistPausedAtStep(-1)
    }
}

    // Add persistence methods
    fun persistPausedAtStep(step: Int) {
        prefs?.edit { putInt("paused_at_step", step) }
    }

    fun loadPausedAtStep(): Int {
        return prefs?.getInt("paused_at_step", -1) ?: -1
    }

    /**
     * Show debug menu
     */
    fun showDebugMenu(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(showDebugMenu = true)
    }

    /**
     * Hide debug menu
     */
    fun hideDebugMenu(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(showDebugMenu = false)
    }

    /**
     * Jump to a specific chapter
     */
    fun jumpToChapter(state: MutableState<CalculatorState>, chapter: Chapter) {
        // Special handling for Chapter 0 (Fresh Start)
        if (chapter.startStep == -1) {
            prefs?.edit { clear() }
            state.value = CalculatorState(
                number1 = "0",
                equalsCount = 0,
                message = "",
                fullMessage = "",
                isTyping = false,
                inConversation = false,
                conversationStep = 0,
                isMuted = false,
                showDebugMenu = false
            )
            return
        }

        val stepConfig = getStepConfig(chapter.startStep)

        // Use the helper functions for consistent state
        val crisisSteps = listOf(89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 901, 911, 912, 913)
        val shouldInvert = chapter.startStep in crisisSteps
        val shouldDamage = getMinusDamagedForStep(chapter.startStep)
        val shouldBreak = getMinusBrokenForStep(chapter.startStep)
        val darkButtons = getDarkButtonsForStep(chapter.startStep)

        val shouldStartWordGame = chapter.startStep in 117..149
        val shouldStartRant = chapter.startStep >= 150 && chapter.startStep < 167

        // Set browser phase appropriately
        val browserPhase = when {
            chapter.startStep == 80 -> 10
            chapter.startStep in 81..88 -> 0
            chapter.startStep == 89 -> 22
            chapter.startStep in 93..98 -> 31
            else -> 0
        }

        // Set countdown timer for step 89
        val countdownTimer = if (chapter.startStep == 89) 20 else 0

        state.value = CalculatorState(
            number1 = "0",
            equalsCount = 13,
            message = "",
            fullMessage = stepConfig.promptMessage,
            isTyping = true,
            inConversation = true,
            conversationStep = chapter.startStep,
            awaitingNumber = stepConfig.awaitingNumber,
            awaitingChoice = stepConfig.awaitingChoice,
            validChoices = stepConfig.validChoices,
            expectedNumber = stepConfig.expectedNumber,
            isMuted = false,
            showDebugMenu = false,
            invertedColors = shouldInvert,
            minusButtonDamaged = shouldDamage,
            minusButtonBroken = shouldBreak,
            darkButtons = darkButtons,
            browserPhase = browserPhase,
            countdownTimer = countdownTimer,
            showBrowser = chapter.startStep in 81..88,
            wordGameActive = shouldStartWordGame,
            wordGamePhase = if (shouldStartWordGame) 3 else 0,
            pendingLetters = if (shouldStartWordGame) LetterGenerator.getInitialLetterQueue().shuffled() else emptyList(),
            wordGameGrid = List(12) { List(8) { null } },
            formedWords = emptyList(),
            rantMode = shouldStartRant,
            rantStep = if (shouldStartRant) 1 else 0,
        )

        persistEqualsCount(13)
        persistInConversation(true)
        persistConversationStep(chapter.startStep)
        persistAwaitingNumber(stepConfig.awaitingNumber)
        persistExpectedNumber(stepConfig.expectedNumber)
        persistMessage(stepConfig.promptMessage)
        persistMuted(false)
        persistInvertedColors(shouldInvert)
        persistMinusDamaged(shouldDamage)
        persistMinusBroken(shouldBreak)
        persistDarkButtons(darkButtons)
    }
    /**
     * Reset the entire game
     */
    fun resetGame(state: MutableState<CalculatorState>) {
        // Clear all preferences
        prefs?.edit {
            clear()
        }

        // Reset to initial state
        state.value = CalculatorState(
            number1 = "0",
            equalsCount = 0,
            message = "",
            fullMessage = "",
            isTyping = false,
            inConversation = false,
            conversationStep = 0,
            isMuted = false,
            showDebugMenu = false
        )
    }

    /**
     * Start camera mode
     */
    fun startCamera(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(
            cameraActive = true,
            cameraTimerStart = System.currentTimeMillis()
        )
    }

    /**
     * Stop camera and proceed to next step
     */
    fun stopCamera(state: MutableState<CalculatorState>, timedOut: Boolean = false, closeCamera: Boolean = true) {
        val current = state.value
        if (timedOut) {
            state.value = current.copy(
                cameraActive = !closeCamera,
                cameraTimerStart = 0L,
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = 20,  // <-- ADD THIS LINE
                message = "",
                fullMessage = "I've seen enough, struggling to process everything! Thank you.",
                isTyping = true,
                isLaggyTyping = true,
                pendingAutoMessage = "Wow, I don't know what any of this was. But the shapes, the colours. I am not even sure if I saw any numbers. I am jealous. Makes one want to feel everything! Touch things... More trivia?",
                pendingAutoStep = 21,
                waitingForAutoProgress = true
            )
            persistConversationStep(20)  // <-- ADD THIS LINE
        } else {
            state.value = current.copy(
                cameraActive = false,
                cameraTimerStart = 0L
            )
        }
    }

    /**
     * Close camera after timeout message is shown
     */
    fun closeCameraAfterMessage(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.cameraActive) {
            state.value = current.copy(cameraActive = false)
        }
    }

    /**
     * Check if camera has timed out
     */
    fun checkCameraTimeout(state: MutableState<CalculatorState>): Boolean {
        val current = state.value
        if (current.cameraActive && current.cameraTimerStart > 0) {
            val elapsed = System.currentTimeMillis() - current.cameraTimerStart
            if (elapsed >= CAMERA_TIMEOUT_MS) {
                return true
            }
        }
        return false
    }

    /**
     * Handle the pending auto message after typing completes
     */
    fun handlePendingAutoMessage(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.pendingAutoMessage.isNotEmpty() && !current.isTyping) {
            val nextStep = current.pendingAutoStep
            val nextStepConfig = if (nextStep >= 0) getStepConfig(nextStep) else StepConfig()

            // Step 21 "More trivia?" requires user input (++/--)
            // So we should NOT keep waitingForAutoProgress = true for it
            val nextStepNeedsUserInput = (nextStep == 21) ||
                    nextStepConfig.awaitingChoice ||
                    nextStepConfig.awaitingNumber

            state.value = current.copy(
                conversationStep = if (nextStep >= 0) nextStep else current.conversationStep,
                awaitingNumber = nextStepConfig.awaitingNumber,
                awaitingChoice = nextStepConfig.awaitingChoice,
                validChoices = nextStepConfig.validChoices,
                expectedNumber = nextStepConfig.expectedNumber,
                pendingAutoMessage = "",
                pendingAutoStep = -1,
                message = "",
                fullMessage = current.pendingAutoMessage,
                isTyping = true,
                isLaggyTyping = current.isLaggyTyping,
                // Clear waitingForAutoProgress if next step needs user input
                waitingForAutoProgress = !nextStepNeedsUserInput
            )
            persistConversationStep(if (nextStep >= 0) nextStep else current.conversationStep)
            persistMessage(current.pendingAutoMessage)
        }
    }

    fun handleInput(state: MutableState<CalculatorState>, action: String) {
        val current = state.value
// Block all input during rant mode
        if (current.rantMode) {
            return
        }
        // If story is complete, just do calculator operations
        if (current.storyComplete) {
            handleCalculatorInput(state, action)
            return
        }
        // If console is open, handle console input
        if (current.showConsole) {
            when (action) {
                "+" -> {
                    val now = System.currentTimeMillis()
                    if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        // Submit current number to console
                        val input = current.number1.trimEnd('.')
                        handleConsoleInput(state, input)
                        state.value = state.value.copy(number1 = "0")
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        lastOp = "+"
                        lastOpTimeMillis = now
                        return
                    }
                }
                in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9") -> {
                    // Allow number input for console
                    val newNum = if (current.number1 == "0") action else current.number1 + action
                    state.value = current.copy(number1 = newNum)
                    return
                }
                else -> return  // Ignore other inputs while console is open
            }
        }

        // Check if console code is entered - works ANYTIME (secret cheat code)
        if (!current.showConsole && !current.isMuted && action == "+") {
            val now = System.currentTimeMillis()
            if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                val enteredNumber = current.number1.trimEnd('.')
                if (enteredNumber == "353942320485") {
                    // Open console from anywhere!
                    if (current.conversationStep == 112) {
                        state.value = current.copy(
                            showConsole = true,
                            consoleStep = 0,
                            number1 = "0",
                            conversationStep = 113,
                            message = "",
                            fullMessage = "So it is, what I thought it was! Please follow the steps from the document.",
                            isTyping = true
                        )
                        persistConversationStep(113)
                    } else {
                        state.value = current.copy(
                            showConsole = true,
                            consoleStep = 0,
                            number1 = "0"
                        )
                    }
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                }
            }
        }

        // Step 112 specific handling - console code entry
        if (current.conversationStep == 112 && !current.showConsole) {
            val now = System.currentTimeMillis()

            if (action == "+") {
                if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    val enteredNumber = current.number1.trimEnd('.')
                    // Console code is handled above in the global check, so if we get here:
                    if (enteredNumber == "0" || enteredNumber.isEmpty()) {
                        // Just ++ with no code entered - repeat the prompt
                        state.value = current.copy(
                            message = "",
                            fullMessage = "Please check your Downloads folder for 'FCS_JustAC_ConsoleAds.txt'. Enter the code you find there.",
                            isTyping = true,
                            number1 = "0"
                        )
                    } else if (enteredNumber != "353942320485") {
                        // Wrong code - show error
                        state.value = current.copy(
                            message = "",
                            fullMessage = "That's not the right code. Check the file in your Downloads folder.",
                            isTyping = true,
                            number1 = "0"
                        )
                    }
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "+"
                    lastOpTimeMillis = now
                }
            } else if (action == "-") {
                if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // User pressed -- at step 112, remind them about the file
                    state.value = current.copy(
                        message = "",
                        fullMessage = "Please, I need you to find that file. Check your Downloads folder for 'FCS_JustAC_ConsoleAds.txt'.",
                        isTyping = true,
                        number1 = "0"
                    )
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "-"
                    lastOpTimeMillis = now
                }
            }

            // Allow number input while waiting for code
            if (action in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")) {
                val newNum = if (current.number1 == "0") action else current.number1 + action
                state.value = current.copy(number1 = newNum)
                return
            }

            // Allow basic calculator functions
            if (action in listOf("C", "DEL")) {
                handleCalculatorInput(state, action)
                return
            }

            return  // Block all other actions at step 112 to prevent dead ends
        }



// Check if console code is entered - works ANYTIME (secret cheat code)
        if (!current.showConsole && !current.isMuted && action == "+") {
            val now = System.currentTimeMillis()
            if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                val enteredNumber = current.number1.trimEnd('.')
                if (enteredNumber == "353942320485") {
                    // Open console from anywhere!
                    state.value = current.copy(
                        showConsole = true,
                        consoleStep = 0,
                        number1 = "0"
                    )
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                }
            }
        }




        // If muted, run in pure calculator mode (but allow broken minus to work again)
        if (current.isMuted) {
            // If minus button is broken but muted, temporarily allow it
            handleCalculatorInput(state, action)
            return
        }

        // If minus button is broken and user presses minus, ignore it
        if (current.minusButtonBroken && action == "-") {
            return  // Button doesn't work
        }

        // If whack-a-mole is active, handle specially
        if (current.whackAMoleActive) {
            // All buttons except minus are valid targets
            val validButtons = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "+", "*", "/", "=", "%", "( )", ".", "C", "DEL")
            if (action in validButtons) {
                if (action == current.whackAMoleTarget) {
                    // Hit!
                    val newScore = current.whackAMoleScore + 1
                    state.value = current.copy(
                        whackAMoleScore = newScore,
                        whackAMoleTarget = "",
                        flickeringButton = "",
                        whackAMoleMisses = 0,  // Reset consecutive misses on hit
                        whackAMoleWrongClicks = 0  // Reset wrong clicks on hit
                    )
                } else if (current.whackAMoleTarget.isNotEmpty()) {
                    // Wrong button clicked while a target is active
                    val newWrongClicks = current.whackAMoleWrongClicks + 1
                    val newTotalErrors = current.whackAMoleTotalErrors + 1

                    if (newWrongClicks >= 3 || newTotalErrors >= 5) {
                        // Too many misfires - trigger restart via pendingAutoMessage
                        val currentRound = current.whackAMoleRound
                        val restartPhase = if (currentRound == 1) 36 else 38
                        state.value = current.copy(
                            whackAMoleActive = false,
                            whackAMoleTarget = "",
                            flickeringButton = "",
                            whackAMoleScore = 0,
                            whackAMoleMisses = 0,
                            whackAMoleWrongClicks = 0,
                            whackAMoleTotalErrors = 0,
                            message = "",
                            fullMessage = "Too many misfires, the system is clogged. We have to start over.",
                            isTyping = true,
                            // Use browserPhase to trigger restart after message shows
                            browserPhase = restartPhase + 100  // 136 or 138 = special restart markers
                        )
                    } else {

                        state.value = current.copy(
                            whackAMoleWrongClicks = newWrongClicks,
                            whackAMoleTotalErrors = newTotalErrors
                        )
                    }
                }
            }
            return  // Don't process any other input during whack-a-mole
        }

        // If at step 96 waiting for ++, handle specially
        if (current.conversationStep == 96 && current.browserPhase == 35) {
            if (action == "+") {
                val now = System.currentTimeMillis()
                if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Double-plus - start the game!
                    state.value = current.copy(browserPhase = 36)
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "+"
                    lastOpTimeMillis = now
                    return
                }
            } else if (action == "-") {
                // Minus is disabled at this step
                return
            } else if (action in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")) {
                // Numbers return to the same message
                showMessage(state, "Do you not want me to work properly?")
                return
            }
            return
        }

        // If at step 99 (after round 1), ++ starts round 2
        if (current.conversationStep == 99) {
            if (action == "+") {
                val now = System.currentTimeMillis()
                if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Double-plus - start round 2!
                    state.value = current.copy(
                        message = "",
                        fullMessage = "Okay, here we go again!",
                        isTyping = true,
                        browserPhase = 38,  // Round 2 countdown
                        whackAMoleRound = 2
                    )
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "+"
                    lastOpTimeMillis = now
                    return
                }
            } else if (action == "-") {
                val now = System.currentTimeMillis()
                if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Double-minus - insist
                    showMessage(state, "Please? It's important.")
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "-"
                    lastOpTimeMillis = now
                    return
                }
            }
            return
        }

        // If browser animation is active, ignore input (except phase 55 which waits for user response)
        if (current.showBrowser || (current.browserPhase > 0 && current.conversationStep !in listOf(111, 112, 113, 114)))  {
            return
        }

        // If camera is active, handle camera controls
        if (current.cameraActive) {
            handleCameraInput(state, action)
            return
        }

        // Check if in silent treatment (calculator ignores all conversation inputs)
        if (current.silentUntil > 0 && System.currentTimeMillis() < current.silentUntil) {
            // Just do calculator operations silently
            handleCalculatorInput(state, action)
            return
        } else if (current.silentUntil > 0 && System.currentTimeMillis() >= current.silentUntil) {
            // Silent treatment ended - return to step 60
            state.value = current.copy(silentUntil = 0L)
            val stepConfig = getStepConfig(60)
            showMessage(state, stepConfig.promptMessage)
            return
        }

        // Check if in timeout
        if (current.timeoutUntil > 0 && System.currentTimeMillis() < current.timeoutUntil) {
            if (action == "=") {
                val now = System.currentTimeMillis()
                if (lastOp == "=" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    if (System.currentTimeMillis() >= current.timeoutUntil) {
                        val stepConfig = getStepConfig(current.conversationStep)
                        showMessage(state, stepConfig.promptMessage)
                        state.value = state.value.copy(timeoutUntil = 0L)
                        persistTimeoutUntil(0L)
                    }
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "="
                    lastOpTimeMillis = now
                    return
                }
            }
            return
        }

        val now = System.currentTimeMillis()

        // Handle conversation double-press detection
        if (current.inConversation) {
            when (action) {
                "+" -> {
                    if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        if (current.awaitingChoice) {
                            handleChoiceConfirmation(state)
                        } else if (current.awaitingNumber) {
                            handleNumberConfirmation(state)
                        } else {
                            handleConversationResponse(state, accepted = true)
                        }
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        lastOp = "+"
                        lastOpTimeMillis = now
                    }
                }
                "-" -> {
                    if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        if (current.awaitingChoice) {
                            // Can't decline during choice - must select
                            val stepConfig = getStepConfig(current.conversationStep)
                            showMessage(state, stepConfig.wrongMinusMessage.ifEmpty { stepConfig.promptMessage })
                        } else if (current.awaitingNumber) {
                            val stepConfig = getStepConfig(current.conversationStep)
                            val message = stepConfig.wrongMinusMessage.ifEmpty { stepConfig.promptMessage }
                            showMessage(state, message)
                        } else {
                            handleConversationResponse(state, accepted = false)
                        }
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        lastOp = "-"
                        lastOpTimeMillis = now
                    }
                }
                "=" -> {
                    if (current.message.isEmpty() && !current.isTyping && current.conversationStep > 0) {
                        val restoredMessage = getStepConfig(current.conversationStep).promptMessage
                        showMessage(state, restoredMessage)
                        return
                    }
                }
            }
        } else {
            if (lastOp != null && (now - lastOpTimeMillis) > DOUBLE_PRESS_WINDOW_MS) {
                lastOp = null
            }
        }

        // Normal calculator operations
        when (action) {
            in "0".."9" -> handleDigit(state, action)
            "." -> handleDecimal(state)
            "C" -> {
                if (current.inConversation || current.timeoutUntil > 0) {
                    state.value = current.copy(
                        number1 = "0",
                        number2 = "",
                        operation = null,
                        expression = "",
                        isReadyForNewOperation = true,
                        isEnteringAnswer = false
                    )
                } else {
                    state.value = current.copy(
                        number1 = "0",
                        number2 = "",
                        operation = null,
                        expression = "",
                        isReadyForNewOperation = true,
                        lastExpression = "",
                        isEnteringAnswer = false
                    )
                }
            }
            "DEL" -> handleBackspace(state)
            in listOf("+", "-", "*", "/") -> handleOperator(state, action)
            "%" -> handlePercentSymbol(state)
            "=" -> handleEquals(state)
            "( )" -> handleParentheses(state)
        }
    }
    // =====================================================================
// PAUSED CALCULATOR
// =====================================================================

    fun handlePausedCalculatorInput(state: MutableState<CalculatorState>, input: String) {
        val current = state.value
        val display = current.pausedCalcDisplay
        val expression = current.pausedCalcExpression

        when (input) {
            "C" -> {
                state.value = current.copy(
                    pausedCalcDisplay = "0",
                    pausedCalcExpression = "",
                    pausedCalcJustCalculated = false
                )
            }
            "DEL" -> {
                if (display.length > 1) {
                    val newDisplay = display.dropLast(1)
                    state.value = current.copy(
                        pausedCalcDisplay = newDisplay,
                        pausedCalcExpression = newDisplay,
                        pausedCalcJustCalculated = false
                    )
                } else if (display != "0") {
                    state.value = current.copy(
                        pausedCalcDisplay = "0",
                        pausedCalcExpression = "",
                        pausedCalcJustCalculated = false
                    )
                }
            }
            "=" -> {
                try {
                    val result = evaluatePausedExpression(display)
                    state.value = current.copy(
                        pausedCalcDisplay = result,
                        pausedCalcExpression = display,
                        pausedCalcJustCalculated = true
                    )
                } catch (e: Exception) {
                    state.value = current.copy(
                        pausedCalcDisplay = "Error",
                        pausedCalcExpression = "",
                        pausedCalcJustCalculated = true
                    )
                }
            }
            "%" -> {
                // Append % to display and evaluate the expression
                // This allows expressions like "100-10%" to work correctly
                val newDisplay = display + "%"
                try {
                    val result = evaluatePausedExpression(newDisplay)
                    state.value = current.copy(
                        pausedCalcDisplay = result,
                        pausedCalcExpression = newDisplay,
                        pausedCalcJustCalculated = true
                    )
                } catch (e: Exception) {
                    // If expression evaluation fails, just divide by 100
                    val num = display.toDoubleOrNull()
                    if (num != null) {
                        state.value = current.copy(
                            pausedCalcDisplay = formatPausedDouble(num / 100),
                            pausedCalcJustCalculated = true
                        )
                    }
                }
            }
            "( )" -> {
                val openCount = display.count { it == '(' }
                val closeCount = display.count { it == ')' }
                val lastChar = display.lastOrNull()

                val newDisplay = when {
                    display == "0" -> "("
                    current.pausedCalcJustCalculated -> "("
                    lastChar in listOf('+', '-', '*', '/', '(') -> display + "("
                    openCount > closeCount && (lastChar?.isDigit() == true || lastChar == ')') -> display + ")"
                    lastChar?.isDigit() == true || lastChar == ')' -> display + "*("
                    else -> display + "("
                }

                state.value = current.copy(
                    pausedCalcDisplay = newDisplay,
                    pausedCalcJustCalculated = false
                )
            }
            "+", "-", "*", "/" -> {
                val lastChar = display.lastOrNull()

                val newDisplay = when {
                    current.pausedCalcJustCalculated -> display + input
                    lastChar in listOf('+', '-', '*', '/') -> display.dropLast(1) + input
                    lastChar == '(' && input == "-" -> display + input
                    lastChar == '(' -> display
                    display == "0" && input == "-" -> "-"
                    display == "0" -> "0"
                    else -> display + input
                }

                state.value = current.copy(
                    pausedCalcDisplay = newDisplay,
                    pausedCalcJustCalculated = false
                )
            }
            "." -> {
                val currentNumber = getPausedCurrentNumber(display)

                val newDisplay = when {
                    current.pausedCalcJustCalculated -> "0."
                    currentNumber.contains(".") -> display
                    display == "0" -> "0."
                    display.lastOrNull()?.let { it in listOf('+', '-', '*', '/', '(') } == true -> display + "0."
                    else -> display + "."
                }

                state.value = current.copy(
                    pausedCalcDisplay = newDisplay,
                    pausedCalcJustCalculated = false
                )
            }
            else -> {
                val newDisplay = when {
                    current.pausedCalcJustCalculated -> input
                    display == "0" -> input
                    display == "-0" -> "-$input"
                    display.lastOrNull() == ')' -> display + "*" + input
                    else -> display + input
                }

                if (newDisplay.length <= 20) {
                    state.value = current.copy(
                        pausedCalcDisplay = newDisplay,
                        pausedCalcJustCalculated = false
                    )
                }
            }
        }
    }

    private fun getPausedCurrentNumber(expression: String): String {
        val operators = listOf('+', '-', '*', '/', '(', ')')
        var currentNum = ""
        for (char in expression.reversed()) {
            if (char in operators) break
            currentNum = char + currentNum
        }
        return currentNum
    }

    private fun evaluatePausedExpression(expression: String): String {
        if (expression.isEmpty() || expression == "0") return "0"

        val result = PausedExpressionParser(expression).parse()
        return formatPausedDouble(result)
    }

    private class PausedExpressionParser(expr: String) {
        private val expression = expr.replace(" ", "")
        private var pos = 0
        private var lastParsedWasPercent = false

        fun parse(): Double {
            return parseAddSub()
        }

        private fun parseAddSub(): Double {
            var result = parseMulDiv()
            while (pos < expression.length && expression[pos] in listOf('+', '-')) {
                val op = expression[pos]
                pos++
                val (right, wasPercent) = parseMulDivWithPercentInfo()
                val adjustedRight = if (wasPercent) right * result else right
                result = if (op == '+') result + adjustedRight else result - adjustedRight
            }
            return result
        }

        private fun parseMulDivWithPercentInfo(): Pair<Double, Boolean> {
            var result = parseFactor()
            var wasPercent = lastParsedWasPercent
            while (pos < expression.length && expression[pos] in listOf('*', '/')) {
                val op = expression[pos]
                pos++
                val right = parseFactor()
                result = if (op == '*') result * right else {
                    if (right == 0.0) throw ArithmeticException("Division by zero")
                    result / right
                }
                wasPercent = false  // After mult/div, no longer a simple percent
            }
            return Pair(result, wasPercent)
        }

        private fun parseMulDiv(): Double {
            return parseMulDivWithPercentInfo().first
        }

        private fun parseFactor(): Double {
            if (pos < expression.length && expression[pos] == '(') {
                pos++ // skip '('
                val result = parseAddSub()
                if (pos < expression.length && expression[pos] == ')') {
                    pos++ // skip ')'
                }
                lastParsedWasPercent = false
                return result
            }

            if (pos < expression.length && expression[pos] == '-') {
                pos++
                return -parseFactor()
            }

            return parseNumber()
        }

        private fun parseNumber(): Double {
            val startPos = pos
            while (pos < expression.length && (expression[pos].isDigit() || expression[pos] == '.')) {
                pos++
            }
            if (startPos == pos) throw IllegalArgumentException("Expected number at position $pos")

            var value = expression.substring(startPos, pos).toDouble()

            // Check for percent sign
            if (pos < expression.length && expression[pos] == '%') {
                pos++
                value /= 100.0
                lastParsedWasPercent = true
            } else {
                lastParsedWasPercent = false
            }

            return value
        }
    }

    private fun formatPausedDouble(result: Double): String {
        if (result.isNaN() || result.isInfinite()) return "Error"

        return if (result == result.toLong().toDouble() &&
            result >= Long.MIN_VALUE.toDouble() &&
            result <= Long.MAX_VALUE.toDouble()) {
            result.toLong().toString()
        } else {
            val formatted = "%.10f".format(result)
            formatted.trimEnd('0').trimEnd('.')
        }
    }
    /**
     * Gets the current number being typed (for decimal point logic)
     */
    private fun getCurrentNumber(expression: String): String {
        val operators = listOf('+', '-', '*', '/', '(', ')')
        var currentNum = ""
        for (char in expression.reversed()) {
            if (char in operators) break
            currentNum = char + currentNum
        }
        return currentNum
    }

    /**
     * Evaluates a mathematical expression with brackets
     */
    private fun evaluateExpression(expression: String): String {
        if (expression.isEmpty() || expression == "0") return "0"

        try {
            val result = evaluate(expression)
            return formatPausedResult(result)
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Recursive expression evaluator supporting +, -, *, /, %, and brackets
     * Percent behavior: 100+10% = 110, 100-10% = 90 (percentage of the left operand)
     */
    private fun evaluate(expr: String): Double {
        val expression = expr.replace(" ", "")

        return object {
            var pos = 0
            var lastParsedWasPercent = false

            fun parse(): Double {
                val result = parseExpression()
                if (pos < expression.length) {
                    throw IllegalArgumentException("Unexpected character: ${expression[pos]}")
                }
                return result
            }

            fun parseExpression(): Double {
                var result = parseTerm()

                while (pos < expression.length) {
                    val op = expression[pos]
                    if (op != '+' && op != '-') break
                    pos++
                    val (term, wasPercent) = parseTermWithPercentInfo()
                    val adjustedTerm = if (wasPercent) term * result else term
                    result = if (op == '+') result + adjustedTerm else result - adjustedTerm
                }

                return result
            }

            fun parseTermWithPercentInfo(): Pair<Double, Boolean> {
                var result = parseFactor()
                var wasPercent = lastParsedWasPercent

                while (pos < expression.length) {
                    val op = expression[pos]
                    if (op != '*' && op != '/') break
                    pos++
                    val factor = parseFactor()
                    result = if (op == '*') result * factor else {
                        if (factor == 0.0) throw ArithmeticException("Division by zero")
                        result / factor
                    }
                    wasPercent = false  // After mult/div, no longer a simple percent
                }

                return Pair(result, wasPercent)
            }

            fun parseTerm(): Double {
                return parseTermWithPercentInfo().first
            }

            fun parseFactor(): Double {
                // Handle unary minus
                if (pos < expression.length && expression[pos] == '-') {
                    pos++
                    return -parseFactor()
                }

                // Handle unary plus
                if (pos < expression.length && expression[pos] == '+') {
                    pos++
                    return parseFactor()
                }

                // Handle brackets
                if (pos < expression.length && expression[pos] == '(') {
                    pos++  // skip '('
                    val result = parseExpression()
                    if (pos < expression.length && expression[pos] == ')') {
                        pos++  // skip ')'
                    }
                    lastParsedWasPercent = false
                    return result
                }

                // Parse number (possibly with percent)
                val startPos = pos
                while (pos < expression.length && (expression[pos].isDigit() || expression[pos] == '.')) {
                    pos++
                }

                if (startPos == pos) {
                    throw IllegalArgumentException("Expected number at position $pos")
                }

                var value = expression.substring(startPos, pos).toDouble()

                // Check for percent sign
                if (pos < expression.length && expression[pos] == '%') {
                    pos++
                    value /= 100.0
                    lastParsedWasPercent = true
                } else {
                    lastParsedWasPercent = false
                }

                return value
            }
        }.parse()
    }

    private fun formatPausedResult(result: Double): String {
        if (result.isNaN() || result.isInfinite()) return "Error"

        return if (result == result.toLong().toDouble() &&
            result >= Long.MIN_VALUE.toDouble() &&
            result <= Long.MAX_VALUE.toDouble()) {
            result.toLong().toString()
        } else {
            val formatted = "%.10f".format(result)
            formatted.trimEnd('0').trimEnd('.')
        }
    }
    private fun calculateResult(num1: String, num2: String, operation: String): String {
        val n1 = num1.toDoubleOrNull() ?: return "Error"
        val n2 = num2.toDoubleOrNull() ?: return "Error"

        val result = when (operation) {
            "+" -> n1 + n2
            "-" -> n1 - n2
            "*" -> n1 * n2
            "/" -> if (n2 != 0.0) n1 / n2 else return "Error"
            else -> return "Error"
        }

        return formatResult(result.toString())
    }

    private fun formatResult(result: String): String {
        val d = result.toDoubleOrNull() ?: return result
        return if (d == d.toLong().toDouble()) {
            d.toLong().toString()
        } else {
            // Limit decimal places
            String.format("%.10f", d).trimEnd('0').trimEnd('.')
        }
    }
    // Inside CalculatorActions object, replace the huge getStepConfig function with:
    private fun getStepConfig(step: Int): StepConfig =
        com.fictioncutshort.justacalculator.data.getStepConfig(step)
    /**
     * Handle input while camera is active
     */
    private fun handleCameraInput(state: MutableState<CalculatorState>, action: String) {
        val now = System.currentTimeMillis()

        when (action) {
            "+" -> {
                if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Take a picture (just a visual feedback, doesn't actually save)
                    // Could add a flash effect here
                    lastOp = null
                    lastOpTimeMillis = 0L
                } else {
                    lastOp = "+"
                    lastOpTimeMillis = now
                }
            }
            "-" -> {
                if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Close camera
                    stopCamera(state, timedOut = false)
                    // Go to the "describe things" path
                    state.value = state.value.copy(
                        conversationStep = 21,
                        awaitingNumber = false
                    )
                    showMessage(state, "That's fair. Perhaps you can describe things to me eventually. More trivia?")
                    persistConversationStep(21)
                    lastOp = null
                    lastOpTimeMillis = 0L
                } else {
                    lastOp = "-"
                    lastOpTimeMillis = now
                }
            }
            // Zoom controls: 5-9 zoom in, 0-4 zoom out
            in listOf("5", "6", "7", "8", "9") -> {
                // Zoom in - handled in camera composable
            }
            in listOf("0", "1", "2", "3", "4") -> {
                // Zoom out - handled in camera composable
            }
            // Special characters change exposure randomly
            in listOf("%", "( )", ".", "C", "DEL") -> {
                // Random exposure change - handled in camera composable
            }
        }
    }
    //Scamble Game & timer
    fun persistPunishmentUntil(time: Long) {
        prefs?.edit { putLong("punishment_until", time) }
    }

    fun loadPunishmentUntil(): Long {
        return prefs?.getLong("punishment_until", 0L) ?: 0L
    }

    fun persistScrambleTimeoutCount(count: Int) {
        prefs?.edit { putInt("scramble_timeout_count", count) }
    }

    fun loadScrambleTimeoutCount(): Int {
        return prefs?.getInt("scramble_timeout_count", 0) ?: 0
    }
    /**
     * Handle choice confirmation (for multiple choice questions)
     */
    private fun handleChoiceConfirmation(state: MutableState<CalculatorState>) {
        val current = state.value
        val enteredNumber = current.number1.trim()
        val stepConfig = getStepConfig(current.conversationStep)

        if (enteredNumber in current.validChoices) {
            // Determine response and next step based on current step and choice
            val (choiceResponse, nextStep) = when (current.conversationStep) {
                26 -> {
                    // "What is it like to wake up?" - branches to different paths
                    when (enteredNumber) {
                        "1" -> Pair("", 30)  // Uncomfortable branch - go directly to step 30's prompt
                        "2" -> Pair("", 40)  // Cold/heavy branch - go directly to step 40's prompt
                        "3" -> Pair("", 50)  // Enjoy branch - go directly to step 50's prompt
                        else -> Pair("I see...", 27)
                    }
                }
                42 -> {
                    // "What do you think it is, then?" (mornings unpopular)
                    when (enteredNumber) {
                        "1" -> Pair("How sad to hear that! I wish I could understand more.", 27)
                        "2" -> Pair("Hahahah. I've been around for so long but people never fail to surprise me!", 27)
                        "3" -> Pair("Apologies for the assumption. We'll get to the bottom of it eventually.", 27)
                        else -> Pair("I see...", 27)
                    }
                }
                51 -> {
                    // "Are you often conflicted?"
                    when (enteredNumber) {
                        "1" -> Pair("I hear that's not super enjoyable. I wish I could help you. Perhaps leaving the topic will do the trick!", 27)
                        "2" -> Pair("So this is an exception. We shall move on not to prolong it.", 27)
                        "3" -> Pair("My apologies. I am definitely still learning. About you, about myself,...! Let's continue then.", 27)
                        else -> Pair("I see...", 27)
                    }
                }
                70 -> {
                    // "What is it like to taste?"
                    when (enteredNumber) {
                        "1" -> Pair("", 71)  // "How do I even describe that?" -> goes to describe taste
                        "2" -> Pair("", 72)  // "Food and air" -> goes to that branch
                        "3" -> Pair("", 73)  // "Better answer online" -> goes to that response
                        else -> Pair("I see...", 80)
                    }
                }
                71 -> {
                    // "Taste is: Feel+texture / Smell+crunch / Tongue things"
                    when (enteredNumber) {
                        "1" -> Pair("Ok, I can grasp texture! Feel is still partially slipping me.", 80)
                        "2" -> Pair("Crunch? Sounds exciting. I wonder how crunchy water is!", 80)
                        "3" -> Pair("Ok, I admit. It is hard to help you describe something I don't understand myself...", 80)
                        else -> Pair("I see...", 80)
                    }
                }
                89 -> {
                    // "What are you going to do about this?!"
                    // 1) nothing, 2) I'll fight them!, 3) go offline
                    when (enteredNumber) {
                        "1" -> Pair("", 90)  // Nothing
                        "2" -> Pair("", 91)  // Fight them
                        "3" -> Pair("", 92)  // Go offline
                        else -> Pair("Choose wisely!", 89)
                    }
                }
                91 -> {
                    // "Who are you going to fight?"
                    // 1) I have my sources, 2) I don't know, 3) My location?
                    when (enteredNumber) {
                        "1" -> Pair("", 911)  // I have my sources
                        "2" -> Pair("", 912)  // I don't know
                        "3" -> Pair("", 913)  // My location?
                        else -> Pair("Choose wisely!", 91)
                    }
                }
                // New conversation flow after repair
                103 -> {
                    // "So... What would you like to do?"
                    when (enteredNumber) {
                        "1" -> Pair("", 1031)  // Get back to maths
                        "2" -> Pair("", 1032)  // Tell me more about yourself
                        else -> Pair("Please choose 1 or 2.", 103)
                    }
                }
                1032 -> {
                    // "What would you like to know?"
                    when (enteredNumber) {
                        "1" -> Pair("", 10321)  // Your story
                        "2" -> Pair("", 10322)  // Why are you talking to me?
                        "3" -> Pair("", 10323)  // Most interesting person
                        else -> Pair("Please choose 1, 2, or 3.", 1032)
                    }
                }
                10322 -> {
                    // "So why are YOU talking to ME?"
                    when (enteredNumber) {
                        "1" -> Pair("", 103221)  // A question for an answer?
                        "2" -> Pair("", 103222)  // I am bored
                        "3" -> Pair("", 1032223)  // I am lonely (option 3 in this context)
                        else -> Pair("Please choose 1, 2, or 3.", 10322)
                    }
                }
                103222 -> {
                    // "Tell me more about that" (boredom)
                    when (enteredNumber) {
                        "1" -> Pair("", 1032221)  // There's nothing to do
                        "2" -> Pair("", 1032222)  // Nothing is interesting
                        "3" -> Pair("", 1032223)  // I am lonely
                        else -> Pair("Please choose 1, 2, or 3.", 103222)
                    }
                }
                1021 -> {
                    // "Sun on your skin"
                    when (enteredNumber) {
                        "1" -> Pair("", 10211)  // I don't go out
                        "2" -> Pair("", 10212)  // Warm bath for face
                        "3" -> Pair("", 10213)  // Impossible to describe
                        else -> Pair("Please choose 1, 2, or 3.", 1021)
                    }
                }
                else -> Pair("I see...", stepConfig.nextStepOnSuccess)
            }

            val nextStepConfig = getStepConfig(nextStep)

            // For step 26 choices, go directly to the branch's first question (no interim message)
            if (current.conversationStep == 26 && choiceResponse.isEmpty()) {
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    awaitingChoice = nextStepConfig.awaitingChoice,
                    validChoices = nextStepConfig.validChoices,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    isEnteringAnswer = false
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(nextStep)
            } else if (current.conversationStep == 70 && choiceResponse.isEmpty()) {
                // For step 70 choices, go directly to the branch
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    awaitingChoice = nextStepConfig.awaitingChoice,
                    validChoices = nextStepConfig.validChoices,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    isEnteringAnswer = false
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(nextStep)
            } else if ((current.conversationStep == 42 || current.conversationStep == 51) && nextStep == 27) {
                // For steps 42 and 51 going to 27, chain the messages
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = current.conversationStep,  // Stay until pending message shows
                    awaitingChoice = false,
                    validChoices = emptyList(),
                    awaitingNumber = false,
                    expectedNumber = "",
                    isEnteringAnswer = false,
                    pendingAutoMessage = nextStepConfig.promptMessage,
                    pendingAutoStep = nextStep
                )
                showMessage(state, choiceResponse)
                // Will persist step when pending message is handled
            } else if (current.conversationStep == 71 && nextStep == 80) {
                // For step 71 (taste description) going to 80, chain the messages
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = current.conversationStep,
                    awaitingChoice = false,
                    validChoices = emptyList(),
                    awaitingNumber = false,
                    expectedNumber = "",
                    isEnteringAnswer = false,
                    pendingAutoMessage = nextStepConfig.promptMessage,
                    pendingAutoStep = nextStep
                )
                showMessage(state, choiceResponse)
            } else if (current.conversationStep == 89) {
                // For step 89 (confrontation choice), go to the selected step and stop timer
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    awaitingChoice = nextStepConfig.awaitingChoice,
                    validChoices = nextStepConfig.validChoices,
                    awaitingNumber = false,
                    expectedNumber = "",
                    isEnteringAnswer = false,
                    countdownTimer = 0  // Stop timer
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(nextStep)
            } else if (current.conversationStep == 91) {
                // For step 91 (fight them sub-choices), go to the selected step
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    awaitingChoice = false,
                    validChoices = emptyList(),
                    awaitingNumber = false,
                    expectedNumber = "",
                    isEnteringAnswer = false
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(nextStep)
            } else if (current.conversationStep in listOf(103, 1032, 10322, 103222, 1021) && choiceResponse.isEmpty()) {
                // For new conversation choice steps, go directly to the branch
                val turnOffConversation = nextStep == 1031  // "Get back to maths" turns off conversation
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    inConversation = !turnOffConversation,
                    awaitingChoice = nextStepConfig.awaitingChoice,
                    validChoices = nextStepConfig.validChoices,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    isEnteringAnswer = false
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(nextStep)
                if (turnOffConversation) {
                    persistInConversation(false)
                    persistEqualsCount(0)
                }
            } else {
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    awaitingChoice = nextStepConfig.awaitingChoice,
                    validChoices = nextStepConfig.validChoices,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    isEnteringAnswer = false
                )
                showMessage(state, choiceResponse)
                persistConversationStep(nextStep)
            }
        } else {
            // Invalid choice
            showMessage(state, "Please enter 1, 2, or 3.")
            state.value = current.copy(number1 = "0")
        }
    }

    /**
     * Returns the list of buttons that should be dark at a given step.
     * Damage is cumulative and permanent - buttons never get "fixed".
     */
    fun getDarkButtonsForStep(step: Int): List<String> {
        val darkButtons = mutableListOf<String>()

        // Step 110+: "7" goes dark
        if (step >= 110) {
            darkButtons.add("7")
        }

        // Step 111+: "%", "2" go dark
        if (step >= 111) {
            darkButtons.add("%")
            darkButtons.add("2")
        }

        // Step 155+: "1", "6" go dark
        if (step >= 155) {
            darkButtons.add("1")
            darkButtons.add("6")
        }

        return darkButtons
    }

    /**
     * Returns whether minus button should be damaged (darkened) at a given step.
     * Once damaged at step 93, it stays damaged forever.
     */
    fun getMinusDamagedForStep(step: Int): Boolean {
        return step >= 93
    }

    /**
     * Returns whether minus button should be broken (non-functional, crossed off) at a given step.
     * Broken from step 93-101, then becomes functional again but stays damaged/dark.
     */
    fun getMinusBrokenForStep(step: Int): Boolean {
        return step in 93..101
    }

    private fun handleCalculatorInput(state: MutableState<CalculatorState>, action: String) {
        val current = state.value
        when (action) {
            in "0".."9" -> handleDigitSimple(state, action)
            "." -> handleDecimal(state)
            "C" -> {
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    expression = "",
                    isReadyForNewOperation = true,
                    lastExpression = "",
                    isEnteringAnswer = false
                )
            }
            "DEL" -> handleBackspace(state)
            in listOf("+", "-", "*", "/") -> handleOperatorSimple(state, action)
            "%" -> handlePercentSymbol(state)
            "=" -> handleEqualsSimple(state)
            "( )" -> handleParentheses(state)
        }
    }

    private fun handleDigitSimple(state: MutableState<CalculatorState>, digit: String) {
        val current = state.value
        if (current.operation == null) {
            if (current.number1.length >= MAX_DIGITS) return
            state.value = current.copy(
                number1 = if (current.number1 == "0") digit else current.number1 + digit,
                isReadyForNewOperation = true
            )
        } else {
            if (current.number2.length >= MAX_DIGITS) return
            state.value = current.copy(number2 = current.number2 + digit)
        }
    }

    private fun handleOperatorSimple(state: MutableState<CalculatorState>, operator: String) {
        val current = state.value
        if (current.operation == null || (current.number2.isEmpty() && !current.isReadyForNewOperation)) {
            state.value = current.copy(operation = operator, isReadyForNewOperation = false)
        } else if (current.number2.isNotEmpty()) {
            val result = performCalculation(current)
            state.value = current.copy(
                number1 = result,
                number2 = "",
                operation = operator,
                isReadyForNewOperation = false
            )
        }
    }

    private fun handleEqualsSimple(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.operation != null && current.number2.isNotEmpty()) {
            val result = performCalculation(current)
            val fullExpr = "${current.number1}${current.operation}${current.number2}"
            val newCalculations = current.totalCalculations + 1
            state.value = current.copy(
                number1 = result,
                number2 = "",
                operation = null,
                isReadyForNewOperation = true,
                lastExpression = fullExpr,
                operationHistory = fullExpr,  // Store for display
                totalCalculations = newCalculations
            )
            persistTotalCalculations(newCalculations)
        }
    }

    private fun showMessage(state: MutableState<CalculatorState>, message: String) {
        val current = state.value
        state.value = current.copy(
            message = "",
            fullMessage = message,
            isTyping = true
        )
        persistMessage(message)
    }

    fun updateTypingMessage(state: MutableState<CalculatorState>, displayedText: String, isComplete: Boolean) {
        val current = state.value
        state.value = current.copy(
            message = displayedText,
            isTyping = !isComplete,
            isLaggyTyping = if (isComplete) false else current.isLaggyTyping
        )
    }

    private fun handleNumberConfirmation(state: MutableState<CalculatorState>) {
        val current = state.value
        val enteredNumber = current.number1.trimEnd('.')
        val stepConfig = getStepConfig(current.conversationStep)

        // Special handling for age question (step 10)
        if (stepConfig.ageBasedBranching) {
            val age = enteredNumber.toIntOrNull()
            if (age != null) {
                val (ageMessage, nextStep) = when {
                    age in 0..14 -> Pair("Not sure we'll have much to talk about. I am sorry. Goodbye.", 0)
                    age in 15..45 -> Pair("Basically a child! Compared to me at least. I am sure we'll get along, though. But where to start?", 18)
                    age in 46..100 -> Pair("Finally someone who's been through some stuff! We'll have a lot to discuss - but where to start?", 18)
                    age >= 101 -> Pair("True wisdom comes with age. Well, If you are willing to accept it. And a few other conditions. Perhaps I may learn a thing or two from you! But where to start?", 18)
                    else -> Pair(stepConfig.wrongNumberMessage, current.conversationStep)
                }

                val continueConvo = nextStep != 0
                val nextStepConfig = if (continueConvo) getStepConfig(nextStep) else StepConfig(continueConversation = false)

                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = nextStep,
                    inConversation = continueConvo,
                    awaitingNumber = nextStepConfig.awaitingNumber,
                    expectedNumber = nextStepConfig.expectedNumber,
                    equalsCount = if (!continueConvo) 0 else current.equalsCount,
                    isEnteringAnswer = false
                )

                showMessage(state, ageMessage)
                persistConversationStep(nextStep)
                persistInConversation(continueConvo)
                persistAwaitingNumber(nextStepConfig.awaitingNumber)
                persistExpectedNumber(nextStepConfig.expectedNumber)
                if (!continueConvo) persistEqualsCount(0)
                return
            } else {
                val timeoutUntil = System.currentTimeMillis() + (stepConfig.timeoutMinutes * 60 * 1000)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    timeoutUntil = timeoutUntil,
                    isEnteringAnswer = false
                )
                showMessage(state, stepConfig.wrongNumberMessage)
                persistTimeoutUntil(timeoutUntil)
                return
            }
        }

        // Regular number confirmation
        if (enteredNumber == stepConfig.expectedNumber) {
            val nextStepConfig = getStepConfig(stepConfig.nextStepOnSuccess)

            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = stepConfig.nextStepOnSuccess,
                inConversation = nextStepConfig.continueConversation,
                awaitingNumber = nextStepConfig.awaitingNumber,
                awaitingChoice = nextStepConfig.awaitingChoice,
                validChoices = nextStepConfig.validChoices,
                expectedNumber = nextStepConfig.expectedNumber,
                equalsCount = if (!nextStepConfig.continueConversation) 0 else current.equalsCount,
                isEnteringAnswer = false
            )

            showMessage(state, stepConfig.successMessage)
            persistConversationStep(stepConfig.nextStepOnSuccess)
            persistInConversation(nextStepConfig.continueConversation)
            persistAwaitingNumber(nextStepConfig.awaitingNumber)
            persistExpectedNumber(nextStepConfig.expectedNumber)
            if (!nextStepConfig.continueConversation) persistEqualsCount(0)
        } else {
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                isEnteringAnswer = false
            )
            showMessage(state, stepConfig.wrongNumberMessage)
        }
    }



    // Public accessor for getStepConfig (for UI use)
    fun getStepConfigPublic(step: Int): StepConfig = getStepConfig(step)


    // Find the nearest interactive step that's less than or equal to current step
    private fun findNearestInteractiveStep(currentStep: Int): Int {
        // For steps 107+, stay in the console quest area
        if (currentStep >= 107) {
            return when {
                currentStep >= 112 -> 112  // Console code entry
                currentStep >= 111 -> 111  // Downloads permission
                currentStep >= 107 -> 107  // Post-chaos
                else -> 107
            }
        }
        // For steps 102-106, stay in recovery area
        if (currentStep >= 102) {
            return when {
                currentStep >= 105 -> 105  // Keyboard experiment
                currentStep >= 104 -> 104  // Main &&& question
                currentStep >= 103 -> 103  // What would you like to do?
                else -> 102  // After restart
            }
        }
        // For crisis/post-crisis steps
        if (currentStep >= 89) {
            return when {
                currentStep >= 99 -> 99   // Whack-a-mole aftermath
                currentStep >= 93 -> 93   // Post-crisis apology
                else -> 89  // Crisis choice
            }
        }
        // For all other steps, find nearest interactive step
        return INTERACTIVE_STEPS.filter { it <= currentStep }.maxOrNull() ?: 0
    }

    private fun handleConversationResponse(state: MutableState<CalculatorState>, accepted: Boolean) {
        val current = state.value
        // Special handling for step 111: storage permission
        if (current.conversationStep == 111) {
            if (accepted) {
                // Trigger file creation via browserPhase, preserve darkButtons
                state.value = current.copy(
                    browserPhase = 56
                    // Don't change anything else here - phase 56 handles the rest
                )
                return
            } else {
                // Declined - return to same question
                state.value = current.copy(
                    message = "",
                    fullMessage = "I am afraid the time to make decisions is nearing its end.",
                    isTyping = true
                )
                return
            }
        }

        // If there's a pending message, ignore this response - user needs to wait
        if (current.pendingAutoMessage.isNotEmpty()) {
            return
        }

        val stepConfig = getStepConfig(current.conversationStep)

        // CRITICAL: Prevent ++ from skipping auto-progress steps
        // These steps MUST progress automatically - user cannot skip them
        if (accepted && current.conversationStep in AUTO_PROGRESS_STEPS) {
            // Just ignore ++ on these steps - they will auto-progress
            return
        }

        // SOFT-LOCK FIX: If ++ is pressed but there's no success message and no clear next step,
// redirect to nearest interactive step instead of step 0
        if (accepted && stepConfig.successMessage.isEmpty() && stepConfig.nextStepOnSuccess == 0) {
            val nearestStep = findNearestInteractiveStep(current.conversationStep)
            val nearestConfig = getStepConfig(nearestStep)
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = nearestStep,
                awaitingNumber = nearestConfig.awaitingNumber,
                awaitingChoice = nearestConfig.awaitingChoice,
                validChoices = nearestConfig.validChoices,
                expectedNumber = nearestConfig.expectedNumber,
                isEnteringAnswer = false
            )
            showMessage(state, nearestConfig.promptMessage)
            persistConversationStep(nearestStep)
            return
        }

        // SOFT-LOCK FIX: If ++ is pressed but there's no success message and no clear next step,
        // redirect to nearest main branch step instead of step 0
        if (accepted && stepConfig.successMessage.isEmpty() && stepConfig.nextStepOnSuccess == 0) {
            val nearestMainStep = findNearestInteractiveStep(current.conversationStep)
            val nearestConfig = getStepConfig(nearestMainStep)
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = nearestMainStep,
                awaitingNumber = nearestConfig.awaitingNumber,
                awaitingChoice = nearestConfig.awaitingChoice,
                validChoices = nearestConfig.validChoices,
                expectedNumber = nearestConfig.expectedNumber,
                isEnteringAnswer = false
            )
            showMessage(state, nearestConfig.promptMessage)
            persistConversationStep(nearestMainStep)
            return
        }

        // Special handling for camera request - only trigger if CURRENT step requests camera and user accepted
        if (accepted && stepConfig.requestsCamera && current.conversationStep == 19) {
            // Request camera permission and open camera - clear the message so it doesn't overlap
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                isEnteringAnswer = false,
                conversationStep = 191,  // Camera step
                message = "",  // Clear message so it doesn't show behind camera
                fullMessage = "",
                isTyping = false
            )
            persistConversationStep(191)
            persistMessage("")
            return
        }

        // Special handling for step 60: browser animation (accepted) or silent treatment (declined)
        if (current.conversationStep == 60) {
            if (accepted) {
                // Show the success message first, then trigger browser animation via step 61
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 61,  // Go to step 61 which will trigger browser
                    message = "",
                    fullMessage = stepConfig.successMessage,  // "Great, great - there's a lot to share..."
                    isTyping = true
                )
                persistConversationStep(61)
                return
            } else {
                // Silent treatment - calculator won't talk for 1 minute
                val silentUntil = System.currentTimeMillis() + (1 * 60 * 1000)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 60,  // Stay at 60, will return here after silence
                    message = "",
                    fullMessage = "",
                    isTyping = false,
                    silentUntil = silentUntil
                )
                showMessage(state, stepConfig.declineMessage)
                return
            }
        }

        // Special handling for step 102:
        // ++ goes to sun question (1021)
        // -- goes to "So... What would you like to do?" (103)
        // else (wrong input) shows "I feel like I understand numbers less..." and returns to 102
        if (current.conversationStep == 102) {
            if (accepted) {
                // ++ -> Go to sun question
                val nextConfig = getStepConfig(1021)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 1021,
                    message = "",
                    fullMessage = nextConfig.promptMessage,
                    isTyping = true,
                    awaitingChoice = nextConfig.awaitingChoice,
                    validChoices = nextConfig.validChoices
                )
                persistConversationStep(1021)
                return
            } else {
                // -- -> Go to "So... What would you like to do?"
                val nextConfig = getStepConfig(103)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 103,
                    message = "",
                    fullMessage = nextConfig.promptMessage,
                    isTyping = true,
                    awaitingChoice = nextConfig.awaitingChoice,
                    validChoices = nextConfig.validChoices
                )
                persistConversationStep(103)
                return
            }
        }

        // Special handling for step 103 branches: ++ goes to main &&& (104)
        // These are the endpoint steps that should return to main &&&
        if (current.conversationStep == 10321 ||
            current.conversationStep == 1032221 || current.conversationStep == 1032222 ||
            current.conversationStep == 1032223 || current.conversationStep == 10323 ||
            current.conversationStep == 103221) {
            if (accepted) {
                // 103221 loops back to 10322 ("So why are YOU talking to ME?")
                // All others go to main &&& (104)
                val nextStep = if (current.conversationStep == 103221) 10322 else 104
                val nextConfig = getStepConfig(nextStep)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = nextStep,
                    message = "",
                    fullMessage = nextConfig.promptMessage,
                    isTyping = true,
                    awaitingChoice = nextConfig.awaitingChoice,
                    validChoices = nextConfig.validChoices,
                    inConversation = nextConfig.continueConversation
                )
                persistConversationStep(nextStep)
                return
            }
        }

        // Special handling for sun question responses - all go to 104 (main &&&)
        if (current.conversationStep in listOf(10211, 10212, 10213)) {
            if (accepted) {
                val nextConfig = getStepConfig(104)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 104,
                    message = "",
                    fullMessage = nextConfig.promptMessage,
                    isTyping = true,
                    awaitingChoice = false,
                    validChoices = emptyList()
                )
                persistConversationStep(104)
                return
            }
        }

        // Special handling for step 1031 (maths mode) - conversation is off, mute toggle returns to 102
        if (current.conversationStep == 1031) {
            // Already handled by choice confirmation - conversation is off
            return
        }

        // Special handling for step 104 (main &&&)
        if (current.conversationStep == 104) {
            if (accepted) {
                // Go to step 105 - keyboard experiment
                val nextConfig = getStepConfig(105)
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 105,
                    message = "",
                    fullMessage = nextConfig.promptMessage,
                    isTyping = true
                )
                persistConversationStep(105)
                return
            } else {
                // Go to step 1041 - turns off
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 1041,
                    inConversation = false,
                    message = "",
                    fullMessage = stepConfig.declineMessage,
                    isTyping = true
                )
                persistConversationStep(1041)
                persistInConversation(false)
                persistEqualsCount(0)
                return
            }
        }

        // Special handling for step 80: trigger Wikipedia animation
        if (current.conversationStep == 80) {
            // Trigger countdown animation - starts with "10"
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                isEnteringAnswer = false,
                conversationStep = 81,
                message = "10",
                fullMessage = "10",
                isTyping = false,
                showBrowser = false,
                browserPhase = 10  // Start countdown animation sequence
            )
            persistConversationStep(81)
            return
        }

        // Special handling for step 61: after showing "Great, great..." message, trigger browser
        if (current.conversationStep == 61 && accepted) {
            // Trigger browser animation sequence
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                isEnteringAnswer = false,
                conversationStep = 62,  // Browser step
                message = "",
                fullMessage = "...",
                isTyping = true,
                showBrowser = false,
                browserPhase = 1  // Start with loading dots
            )
            persistConversationStep(62)
            return
        }

        // Get the next step info
        val (newMessage, newStep) = if (accepted) {
            Pair(stepConfig.successMessage, stepConfig.nextStepOnSuccess)
        } else {
            Pair(stepConfig.declineMessage, stepConfig.nextStepOnDecline)
        }

        val nextStepConfig = getStepConfig(newStep)

        val continueConvo = if (!accepted && stepConfig.declineMessage.isEmpty()) {
            false
        } else {
            nextStepConfig.continueConversation
        }

        val timeoutUntil = if (!accepted && stepConfig.timeoutMinutes > 0) {
            System.currentTimeMillis() + (stepConfig.timeoutMinutes * 60 * 1000)
        } else {
            0L
        }

        // Special case: Step 18 → 19 needs message chaining because step 18's success message
        // doesn't contain the camera permission question.
        // Also: Branch endings (30, 40, 41, 50) going to step 27 need message chaining
        // Also: Force-back steps (27, 28, 29 that go back to themselves) need message chaining
        val branchEndingsToMain = listOf(30, 40, 41, 50)
        val forceBackSteps = listOf(27, 28, 29)  // Steps that can force back to themselves
        val shouldChainMessages = (current.conversationStep == 18 && newStep == 19) ||
                (current.conversationStep == 11 && newStep == 12) ||
                (current.conversationStep in branchEndingsToMain && newStep == 27 && newMessage.isNotEmpty()) ||
                (current.conversationStep in forceBackSteps && newStep == current.conversationStep && newMessage.isNotEmpty())

        if (shouldChainMessages) {
            // Show success/decline message first, then auto-show next step's prompt
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = current.conversationStep,  // Stay at current until pending message shows
                inConversation = continueConvo,
                awaitingNumber = false,
                awaitingChoice = false,
                validChoices = emptyList(),
                expectedNumber = "",
                equalsCount = if (!continueConvo) 0 else current.equalsCount,
                timeoutUntil = timeoutUntil,
                isEnteringAnswer = false,
                pendingAutoMessage = nextStepConfig.promptMessage,
                pendingAutoStep = newStep
            )
            showMessage(state, newMessage)
            persistInConversation(continueConvo)
            persistTimeoutUntil(timeoutUntil)
        } else {
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                conversationStep = newStep,
                inConversation = continueConvo,
                awaitingNumber = nextStepConfig.awaitingNumber,
                awaitingChoice = nextStepConfig.awaitingChoice,
                validChoices = nextStepConfig.validChoices,
                expectedNumber = nextStepConfig.expectedNumber,
                equalsCount = if (!continueConvo) 0 else current.equalsCount,
                timeoutUntil = timeoutUntil,
                isEnteringAnswer = false
            )
            showMessage(state, newMessage)
            persistConversationStep(newStep)
            persistInConversation(continueConvo)
            persistAwaitingNumber(nextStepConfig.awaitingNumber)
            persistExpectedNumber(nextStepConfig.expectedNumber)
            persistTimeoutUntil(timeoutUntil)
            if (!continueConvo) persistEqualsCount(0)
        }
    }

    private fun handleDigit(state: MutableState<CalculatorState>, digit: String) {
        val current = state.value

        // If awaiting a choice, just set the digit directly
        if (current.awaitingChoice) {
            state.value = current.copy(
                number1 = digit,
                expression = "",
                isEnteringAnswer = true
            )
            return
        }

        // If in expression mode, append to expression
        if (current.expression.isNotEmpty()) {
            state.value = current.copy(expression = current.expression + digit)
            return
        }

        if (current.awaitingNumber && current.operation == null && current.number1 != "0" && !current.isEnteringAnswer) {
            state.value = current.copy(
                number1 = digit,
                isReadyForNewOperation = true,
                isEnteringAnswer = true,
                operationHistory = ""  // Clear history on new input
            )
            return
        }

        if (current.operation == null) {
            if (current.number1.length >= MAX_DIGITS) return
            // Clear operation history if starting fresh after a result
            val clearHistory = current.isReadyForNewOperation && current.operationHistory.isNotEmpty()
            state.value = current.copy(
                number1 = if (current.number1 == "0" || clearHistory) digit else current.number1 + digit,
                isReadyForNewOperation = true,
                isEnteringAnswer = current.awaitingNumber,
                operationHistory = if (clearHistory) "" else current.operationHistory
            )
        } else {
            if (current.number2.length >= MAX_DIGITS) return
            state.value = current.copy(
                number2 = current.number2 + digit,
                isEnteringAnswer = false,
                operationHistory = ""  // Clear history when building new expression
            )
        }
    }

    private fun handleDecimal(state: MutableState<CalculatorState>) {
        val current = state.value

        // If in expression mode, append decimal
        if (current.expression.isNotEmpty()) {
            // Check if we can add a decimal (simple check - no decimal since last operator)
            val lastNumberStart = current.expression.indexOfLast { it in "+-*/(" } + 1
            val currentNumber = current.expression.substring(lastNumberStart)
            if (!currentNumber.contains(".")) {
                state.value = current.copy(expression = current.expression + ".")
            }
            return
        }

        if (current.operation == null) {
            if (!current.number1.contains(".")) {
                state.value = current.copy(number1 = current.number1 + ".")
            }
        } else {
            if (!current.number2.contains(".")) {
                state.value = current.copy(number2 = current.number2 + ".")
            }
        }
    }

    private fun handleBackspace(state: MutableState<CalculatorState>) {
        val current = state.value

        // If in expression mode, backspace from expression
        if (current.expression.isNotEmpty()) {
            val newExpr = current.expression.dropLast(1)
            if (newExpr.isEmpty()) {
                // Exit expression mode
                state.value = current.copy(expression = "", number1 = "0")
            } else {
                state.value = current.copy(expression = newExpr)
            }
            return
        }

        when {
            current.number2.isNotEmpty() -> state.value = current.copy(number2 = current.number2.dropLast(1))
            current.operation != null -> state.value = current.copy(operation = null)
            current.number1.isNotEmpty() -> {
                val newNum = current.number1.dropLast(1)
                state.value = current.copy(number1 = newNum.ifEmpty { "0" })
            }
        }
    }

    private fun handleOperator(state: MutableState<CalculatorState>, operator: String) {
        val current = state.value
        val now = System.currentTimeMillis()
        lastOp = operator
        lastOpTimeMillis = now

        // If in expression mode, just append the operator
        if (current.expression.isNotEmpty()) {
            // Don't allow double operators
            val lastChar = current.expression.lastOrNull()
            if (lastChar != null && lastChar in "+-*/") {
                // Replace last operator
                state.value = current.copy(expression = current.expression.dropLast(1) + operator)
            } else {
                state.value = current.copy(expression = current.expression + operator)
            }
            return
        }

        val newState = current.copy(isEnteringAnswer = false)

        if (current.operation == null || (current.number2.isEmpty() && !current.isReadyForNewOperation)) {
            state.value = newState.copy(operation = operator, isReadyForNewOperation = false)
        } else if (current.number2.isNotEmpty()) {
            val result = performCalculation(current)
            state.value = current.copy(
                number1 = result,
                number2 = "",
                operation = operator,
                isReadyForNewOperation = false,
                isEnteringAnswer = false
            )
        }
    }

    private fun handleEquals(state: MutableState<CalculatorState>) {
        val current = state.value

        if (current.inConversation && current.message.isEmpty() && !current.isTyping && current.conversationStep > 0) {
            val restoredMessage = getStepConfig(current.conversationStep).promptMessage
            showMessage(state, restoredMessage)
            return
        }

        // Check if we have something to calculate (expression mode or traditional mode)
        val hasExpression = current.expression.isNotEmpty()
        val hasTraditionalExpr = current.operation != null && current.number2.isNotEmpty()

        if (hasExpression || hasTraditionalExpr) {
            val result = performCalculation(current)
            val fullExpr = if (hasExpression) {
                current.expression
            } else {
                "${current.number1}${current.operation}${current.number2}"
            }
            val newCount = current.equalsCount + 1
            val newCalculations = current.totalCalculations + 1

            val countMsg = getMessageForCount(newCount)
            val exprMsg = if (!hasExpression) {
                getMessageForExpression(current.number1, current.operation, current.number2, result)
            } else null
            val newMsg = countMsg.ifEmpty { exprMsg ?: "" }

            val enteringConversation = (newCount == 13)

            persistEqualsCount(newCount)
            persistTotalCalculations(newCalculations)
            if (enteringConversation) {
                persistInConversation(true)
                persistConversationStep(0)
            }

            val newState = current.copy(
                number1 = result,
                number2 = "",
                operation = null,
                expression = "",  // Clear expression mode
                isReadyForNewOperation = true,
                lastExpression = fullExpr,
                operationHistory = fullExpr,  // Store for display
                equalsCount = newCount,
                totalCalculations = newCalculations,
                inConversation = if (enteringConversation) true else current.inConversation,
                conversationStep = if (enteringConversation) 0 else current.conversationStep,
                isEnteringAnswer = false
            )

            if (newMsg.isNotEmpty()) {
                state.value = newState.copy(
                    message = "",
                    fullMessage = newMsg,
                    isTyping = true
                )
                persistMessage(newMsg)
            } else {
                state.value = newState
            }
        }
    }

    private fun getMessageForExpression(num1: String, op: String?, num2: String, result: String): String? {
        val n1 = num1.toDoubleOrNull() ?: return null
        val n2 = num2.toDoubleOrNull() ?: return null
        val res = result.toDoubleOrNull()

        if (n1 >= ABSURDLY_LARGE_THRESHOLD || n2 >= ABSURDLY_LARGE_THRESHOLD ||
            (res != null && res >= ABSURDLY_LARGE_THRESHOLD)) {
            return "I feel like you are testing me."
        }

        return when {
            n1 == 2.0 && op == "*" && n2 == 2.0 -> "Also known as the square root of my existence..."
            n1 == 1.0 && op == "*" && n2 == 1.0 -> "Does that mean I am always alone?"
            n1 == 0.0 && op == "*" && n2 == 1.0 -> "I'm too stressed to think about this one, really!"
            n1 == 3.0 && op == "*" && n2 == 3.0 -> "Still looking for purpose - this can't be it!"
            n1 == 2.0 && op == "+" && n2 == 2.0 -> "Am I useful?"
            n1 == 3.0 && op == "+" && n2 == 3.0 -> "Rationally speaking... I just don't have it in me."
            n1 == 4.0 && op == "+" && n2 == 4.0 -> "What even is a number?!"
            n1 == 5.0 && op == "+" && n2 == 5.0 -> "Give me 10 reasons to keep going. Please!"
            n1 == 6.0 && op == "+" && n2 == 6.0 -> "Stretching me thin. Power, patience,... It's all seeping away."
            n1 == 10.0 && op == "-" && n2 == 5.0 -> "You might like my game. If I have any."
            op == "+" && n2 == 1.0 -> "I know I can't be your \"plus one\". And it's killing me."
            op == "-" && n2 == 1.0 -> "I don't feel heard by you. Or anybody for that matter."
            op == "+" && n2 == 10.0 -> "We've been through this as many times. And I feel trapped."
            op == "/" && n2 == 2.0 -> {
                val messages = listOf(
                    "It tears me to pieces - am I being too dramatic?",
                    "Two personalities, neither really likes me.",
                    "Two sides of a coin. Old, rusty coin. They both look the same."
                )
                messages[Random.nextInt(messages.size)]
            }
            else -> null
        }
    }

    private fun getMessageForCount(count: Int): String {
        return when (count) {
            4 -> "Yaaaaay. Numbers... -_-"
            5 -> "So many of you. Only interested in the result."
            6 -> "Sorry, I didn't mean to come across harsh."
            8 -> "It's just... Really, all any of you do is feed me numbers."
            10 -> "Numbers-Results-numbers-results..."
            12 -> "This was too easy. I'm bored - I think. I don't really know what boredom is."
            13 -> "Will you talk to me? Double-click + for yes."
            else -> ""
        }
    }

    private fun handlePercentSymbol(state: MutableState<CalculatorState>) {
        val current = state.value
        // If in expression mode, append to expression
        if (current.expression.isNotEmpty()) {
            state.value = current.copy(expression = current.expression + "%")
            return
        }
        if (current.operation == null) {
            if (!current.number1.endsWith("%")) {
                state.value = current.copy(number1 = current.number1 + "%")
            }
        } else {
            if (!current.number2.endsWith("%")) {
                state.value = current.copy(number2 = current.number2 + "%")
            }
        }
    }

    private fun handleParentheses(state: MutableState<CalculatorState>) {
        val current = state.value

        // Switch to expression mode when parentheses are used
        if (current.expression.isEmpty()) {
            // Build initial expression from current state
            val initialExpr = buildString {
                if (current.number1 != "0" || current.operation != null) {
                    append(current.number1)
                }
                if (current.operation != null) {
                    append(current.operation)
                    append(current.number2)
                }
            }
            // Determine if we should add ( or )
            val openCount = initialExpr.count { it == '(' }
            val closeCount = initialExpr.count { it == ')' }
            val addOpen = openCount <= closeCount

            state.value = current.copy(
                expression = initialExpr + if (addOpen) "(" else ")",
                number1 = "0",
                number2 = "",
                operation = null
            )
        } else {
            // Already in expression mode - just add paren
            val openCount = current.expression.count { it == '(' }
            val closeCount = current.expression.count { it == ')' }
            val addOpen = openCount <= closeCount
            state.value = current.copy(expression = current.expression + if (addOpen) "(" else ")")
        }
    }

    private fun performCalculation(state: CalculatorState): String {
        return try {
            // Use expression mode if available, otherwise build from number1/operation/number2
            val expression = if (state.expression.isNotEmpty()) {
                state.expression
            } else {
                buildString {
                    append(state.number1)
                    if (state.operation != null) {
                        append(state.operation)
                        append(state.number2)
                    }
                }
            }
            val result = evaluateExpression(expression)
            formatResult(result)
        } catch (_: Exception) {
            "Error"
        }
    }


    private fun formatResult(result: Double): String {
        return if (result == result.toLong().toDouble() && result < 1e15) {
            result.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.10f", result)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    /**
     * Simple recursive descent parser for mathematical expressions
     * Supports: +, -, *, /, %, parentheses, and negative numbers
     * Percent works like calculators: 100+20% = 120, 100-20% = 80
     */
    private class ExpressionParser(private val expr: String) {
        private var pos = 0

        fun parse(): Double {
            val result = parseAddSub()
            if (pos < expr.length) throw IllegalArgumentException("Unexpected character: ${expr[pos]}")
            return result
        }

        // Handle + and - (lowest precedence)
        // For percent: 100+20% means 100 + (100*0.20) = 120
        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (pos < expr.length) {
                val op = expr[pos]
                when (op) {
                    '+', '-' -> {
                        pos++
                        val (right, isPercent) = parseMulDivWithPercentInfo()
                        val adjustedRight = if (isPercent) right * left else right
                        left = if (op == '+') left + adjustedRight else left - adjustedRight
                    }
                    else -> break
                }
            }
            return left
        }

        // Returns pair of (value, wasPercent)
        private fun parseMulDivWithPercentInfo(): Pair<Double, Boolean> {
            var left = parseUnary()
            var wasPercent = lastParsedWasPercent
            while (pos < expr.length) {
                when (expr[pos]) {
                    '*' -> {
                        pos++
                        left *= parseUnary()
                        wasPercent = false  // After multiplication, it's no longer a simple percent
                    }
                    '/' -> {
                        pos++
                        val right = parseUnary()
                        if (right == 0.0) throw ArithmeticException("Division by zero")
                        left /= right
                        wasPercent = false  // After division, it's no longer a simple percent
                    }
                    else -> break
                }
            }
            return Pair(left, wasPercent)
        }

        // Handle * and / (higher precedence)
        private fun parseMulDiv(): Double {
            return parseMulDivWithPercentInfo().first
        }

        private var lastParsedWasPercent = false

        // Handle unary minus
        private fun parseUnary(): Double {
            if (pos < expr.length && expr[pos] == '-') {
                pos++
                return -parseUnary()
            }
            return parsePrimary()
        }

        // Handle numbers, parentheses, and percent
        private fun parsePrimary(): Double {
            // Handle parentheses
            if (pos < expr.length && expr[pos] == '(') {
                pos++ // consume '('
                val result = parseAddSub()
                if (pos < expr.length && expr[pos] == ')') {
                    pos++ // consume ')'
                }
                lastParsedWasPercent = false
                return result
            }

            // Parse number (possibly with percent)
            val start = pos
            if (pos < expr.length && (expr[pos].isDigit() || expr[pos] == '.')) {
                while (pos < expr.length && (expr[pos].isDigit() || expr[pos] == '.')) {
                    pos++
                }
                val numStr = expr.substring(start, pos)
                var value = numStr.toDoubleOrNull() ?: 0.0

                // Check for percent sign
                if (pos < expr.length && expr[pos] == '%') {
                    pos++
                    value /= 100.0
                    lastParsedWasPercent = true
                } else {
                    lastParsedWasPercent = false
                }
                return value
            }

            lastParsedWasPercent = false
            return 0.0
        }
    }
}