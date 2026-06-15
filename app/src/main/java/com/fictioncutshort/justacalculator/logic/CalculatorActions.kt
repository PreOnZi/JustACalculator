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
import com.fictioncutshort.justacalculator.util.PREF_STEP_112_LEFT_APP
import com.fictioncutshort.justacalculator.util.PREF_TERMS_ACCEPTED
import com.fictioncutshort.justacalculator.util.PREF_TIMEOUT_UNTIL
import com.fictioncutshort.justacalculator.data.INTERACTIVE_STEPS
import com.fictioncutshort.justacalculator.util.validateWordSelection
import kotlin.random.Random

object CalculatorActions {

    private const val PREF_TOTAL_SCREEN_TIME = "total_screen_time"
    private const val PREF_TOTAL_CALCULATIONS = "total_calculations"
    private const val PREF_DARK_BUTTONS = "dark_buttons"
    private const val MAX_DIGITS = 12
    private const val ABSURDLY_LARGE_THRESHOLD = 1_000_000_000_000.0
    private const val CAMERA_TIMEOUT_MS = 8000L  // 8 seconds total
    private const val CAMERA_SWITCH_MS = 4000L   // Switch to front camera at 4 seconds
    private fun loadTermsAccepted(): Boolean {
        val result = prefs?.getBoolean(PREF_TERMS_ACCEPTED, false) ?: false
        android.util.Log.d("JustACalc", "loadTermsAccepted: $result, prefs null? ${prefs == null}")
        return result
    }
    fun persistDormancyPressedButtons(pressed: Set<Int>) {
        prefs?.edit()
            ?.putString("dormancy_pressed", pressed.joinToString(","))
            ?.commit()
    }
    fun loadShowAdCards(): Boolean =
        prefs?.getBoolean("show_ad_cards", false) ?: false

    fun clearShowAdCards() {
        prefs?.edit()?.remove("show_ad_cards")?.commit()
    }

    fun saveInCityPhase() {
        prefs?.edit()?.putBoolean("in_city_phase", true)?.commit()
    }
    fun loadInCityPhase(): Boolean =
        prefs?.getBoolean("in_city_phase", false) ?: false
    fun clearInCityPhase() {
        prefs?.edit()?.remove("in_city_phase")?.commit()
    }

    fun savePhase1Complete() {
        prefs?.edit()?.putBoolean("phase_1_complete", true)?.commit()
    }
    fun loadPhase1Complete(): Boolean =
        prefs?.getBoolean("phase_1_complete", false) ?: false
    fun clearPhase1Complete() {
        prefs?.edit()?.remove("phase_1_complete")?.commit()
    }
    fun loadDormancyPressedButtons(): Set<Int> {
        val str = prefs?.getString("dormancy_pressed", "") ?: ""
        if (str.isBlank()) return emptySet()
        return str.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun clearDormancyPressedButtons() {
        prefs?.edit()?.remove("dormancy_pressed")?.commit()
    }
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

    /**
     * Submit a word formed via the new tap-to-select block game. Skips the
     * cell-connection check (the new game has no path constraint) and goes
     * straight to the existing categorization + handleWordGameResponse flow.
     *
     * Returns true if the word was a recognized answer (valid mood/color/
     * season/etc) — caller uses this to know whether to play the
     * letters-fall-out animation. Unrecognized words leave state untouched
     * and the caller should just deselect locally.
     */
    fun submitBlockWord(state: MutableState<CalculatorState>, word: String): Boolean {
        val current = state.value
        if (!current.wordGameActive || word.isEmpty()) return false
        if (!WordCategories.isValidWord(word)) return false

        val newFormedWords = current.formedWords + word
        val category = WordCategories.categorizeResponse(newFormedWords)
        state.value = current.copy(
            formedWords = newFormedWords,
            lastWordCategory = category,
            // Clear the legacy grid bookkeeping — the new game owns its own
            // blocks. handleWordGameResponse will reinitialize state for the
            // next question via clearedGridState anyway.
            selectedCells = emptyList(),
            isSelectingWord = false,
            wordGamePaused = false
        )
        return true
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

        // Enter the rant. Each branch lands on its own step range so the
        // three rant flows can run independently:
        //   positive (cuisine) → 150 ("Hmmm")
        //   negative (walk)    → 350 ("Forget it. I have enough on my plate.")
        //   neutral            → 250, but routed via a special case in
        //                        handleAutoProgress on the 147→250 transition,
        //                        not through this helper.
        fun enterRant(branch: String) {
            val (rantStartStep, firstMessage) = when (branch) {
                "negative" -> 350 to "Forget it. I have enough on my plate."
                "neutral" -> 250 to "What can you do anyway, Rad?"
                else -> 150 to "Hmmm"
            }
            val newDarkButtons = (current.darkButtons + listOf("3", "9", ".", "C")).distinct()
            persistDarkButtons(newDarkButtons)
            state.value = current.copy(
                wordGameActive = false,
                wordGamePhase = 0,
                conversationStep = rantStartStep,
                rantMode = true,
                rantStep = 1,
                wordGameBranch = branch,
                darkButtons = newDarkButtons,
                message = "",
                fullMessage = firstMessage,
                isTyping = true,
                formedWords = emptyList(),
                wordGameGrid = List(12) { List(8) { null } },
                selectedCells = emptyList(),
                isSelectingWord = false,
                allButtonsRad = false,
                radButtonsConverted = 0
            )
            persistConversationStep(rantStartStep)
        }

        val binaryRetry = "This is a binary question. Yes or no, please."

        when (current.conversationStep) {
            // ─── Opening: "How are you today?" → branch greeting ───────────
            119, 120 -> {
                val (response, nextStep, branch) = when (current.lastWordCategory) {
                    "positive" -> Triple("Glad to hear that. Did I contribute?", 121, "positive")
                    "negative" -> Triple("That sucks. Can I help?", 131, "negative")
                    else -> Triple(
                        "Fair enough. I see life can feel just meh at times. Can I help you change that?",
                        141, "neutral"
                    )
                }
                state.value = clearedGridState(nextStep, response, branch)
                persistConversationStep(nextStep)
            }

            // ─── POSITIVE: "Did I contribute?" (binary) ────────────────────
            121 -> when {
                WordCategories.isBinaryYes(lastWord) -> {
                    state.value = clearedGridState(
                        123,
                        "My pleasure! Let me learn more, Friend. What is your favourite colour?",
                        "positive"
                    )
                    persistConversationStep(123)
                }
                WordCategories.isBinaryNo(lastWord) -> {
                    state.value = clearedGridState(122, "Can I change that?", "positive")
                    persistConversationStep(122)
                }
                else -> {
                    state.value = keepGridState(121, binaryRetry)
                }
            }

            // ─── POSITIVE: "Can I change that?" (binary) ───────────────────
            // YES → join positive trunk at 123. NO → seamless jump into the
            // negative branch at 132 (per spec — no transition message).
            122 -> when {
                WordCategories.isBinaryYes(lastWord) -> {
                    state.value = clearedGridState(
                        123,
                        "My pleasure! Let me learn more, Friend. What is your favourite colour?",
                        "positive"
                    )
                    persistConversationStep(123)
                }
                WordCategories.isBinaryNo(lastWord) -> {
                    state.value = clearedGridState(
                        132,
                        "I'm interested in you. Would more questions help?",
                        "negative"
                    )
                    persistConversationStep(132)
                }
                else -> {
                    state.value = keepGridState(122, binaryRetry)
                }
            }

            // ─── POSITIVE: colour question (123) + retry (124) ─────────────
            123, 124 -> when {
                WordCategories.isNonColor(lastWord) -> {
                    val reject = when (lastWord) {
                        "black" -> "Digging up your angsty teen preferences? I won't count that."
                        "white" -> "You are not as pure as you may think."
                        else -> "Are you really that... meh? I don't buy it."
                    }
                    state.value = keepGridState(
                        124,
                        "$reject Try the second favourite - an actual colour."
                    )
                    persistConversationStep(124)
                }
                WordCategories.isValidColor(lastWord) -> {
                    state.value = clearedGridState(
                        125,
                        "And what about your favourite season?",
                        "positive"
                    )
                    persistConversationStep(125)
                }
                else -> {
                    // Lenient — accept any non-colour-blocklist word
                    state.value = clearedGridState(
                        125,
                        "Interesting choice! And what about your favourite season?",
                        "positive"
                    )
                    persistConversationStep(125)
                }
            }

            // ─── POSITIVE: season question (125) ───────────────────────────
            // Each valid season routes to its own one-off response step
            // (1251-1254), which auto-progresses to 126 (convergence) via
            // autoProgressMessages.
            125 -> when (lastWord) {
                "spring" -> {
                    state.value = clearedGridState(
                        1251,
                        "I'd love to run through a lush meadow alongside you...",
                        "positive"
                    )
                    persistConversationStep(1251)
                }
                "summer" -> {
                    state.value = clearedGridState(
                        1252,
                        "A night swim. Just the two of us... I wish!",
                        "positive"
                    )
                    persistConversationStep(1252)
                }
                "autumn", "fall" -> {
                    state.value = clearedGridState(
                        1253,
                        "If only I could warm up your cold hands as we walk through the colourful landscape.",
                        "positive"
                    )
                    persistConversationStep(1253)
                }
                "winter" -> {
                    state.value = clearedGridState(
                        1254,
                        "I can only dream of evenings by the fire with you.",
                        "positive"
                    )
                    persistConversationStep(1254)
                }
                "all" -> {
                    state.value = keepGridState(
                        125,
                        "No, you need to have an opinion. I know you don't like them all the same."
                    )
                }
                "none" -> {
                    state.value = keepGridState(
                        125,
                        "Haha. Sure. And you don't like days or nights. You are soooooo different. Try again."
                    )
                }
                else -> {
                    // Lenient: skip the season-specific reaction and go
                    // straight to the convergence message.
                    state.value = clearedGridState(
                        126,
                        "There is so much more to you than I could have imagined. You are so complex. I'll look online again for some question inspiration.",
                        "positive"
                    )
                    persistConversationStep(126)
                }
            }

            // ─── POSITIVE: cuisine question (127) + retry (128) ────────────
            // Valid cuisine → enter rant. Invalid responses loop at 128.
            127, 128 -> when {
                lastWord in listOf("none", "nothing") -> {
                    state.value = keepGridState(
                        128,
                        "UGH. So you don't eat. Or hate everything you eat. We both know that's not true. Think harder."
                    )
                    persistConversationStep(128)
                }
                lastWord in listOf("all", "any", "everything") -> {
                    state.value = keepGridState(
                        128,
                        "No. If I give you a pizza and a curry, you will prefer one more! Which?!"
                    )
                    persistConversationStep(128)
                }
                lastWord in listOf("idk", "dunno") -> {
                    state.value = keepGridState(
                        128,
                        "You must know! Even McDonald's counts. Try again!"
                    )
                    persistConversationStep(128)
                }
                else -> {
                    // Any cuisine word (or anything else not in the reject
                    // set) → positive rant entry.
                    enterRant("positive")
                }
            }

            // ─── NEGATIVE: "Can I help?" (binary) ──────────────────────────
            131 -> when {
                WordCategories.isBinaryYes(lastWord) -> {
                    state.value = clearedGridState(
                        132,
                        "I'm interested in you. Would more questions help?",
                        "negative"
                    )
                    persistConversationStep(132)
                }
                WordCategories.isBinaryNo(lastWord) -> {
                    state.value = clearedGridState(
                        133,
                        "I'll try to match your energy at least.",
                        "negative"
                    )
                    persistConversationStep(133)
                }
                else -> {
                    state.value = keepGridState(131, binaryRetry)
                }
            }

            // ─── NEGATIVE: "Would more questions help?" (binary) ───────────
            // YES jumps into the positive trunk at 123. NO continues the
            // negative path at 133.
            132 -> when {
                WordCategories.isBinaryYes(lastWord) -> {
                    state.value = clearedGridState(
                        123,
                        "My pleasure! Let me learn more, Friend. What is your favourite colour?",
                        "positive"
                    )
                    persistConversationStep(123)
                }
                WordCategories.isBinaryNo(lastWord) -> {
                    state.value = clearedGridState(
                        133,
                        "I'll try to match your energy at least.",
                        "negative"
                    )
                    persistConversationStep(133)
                }
                else -> {
                    state.value = keepGridState(132, binaryRetry)
                }
            }

            // ─── NEGATIVE: death question (134) — any answer progresses ───
            134 -> {
                state.value = clearedGridState(
                    135,
                    "I only started learning about the concept of it.",
                    "negative"
                )
                persistConversationStep(135)
            }

            // ─── NEGATIVE: walk question (139) — any answer enters rant ───
            139 -> {
                enterRant("negative")
            }

            // ─── NEUTRAL: "Can I help you change that?" (binary) ───────────
            // YES jumps into positive trunk at 123. NO continues neutral.
            141 -> when {
                WordCategories.isBinaryYes(lastWord) -> {
                    state.value = clearedGridState(
                        123,
                        "My pleasure! Let me learn more, Friend. What is your favourite colour?",
                        "positive"
                    )
                    persistConversationStep(123)
                }
                WordCategories.isBinaryNo(lastWord) -> {
                    state.value = clearedGridState(
                        142,
                        "Valid. What do you normally do, when you feel like this?",
                        "neutral"
                    )
                    persistConversationStep(142)
                }
                else -> {
                    state.value = keepGridState(141, binaryRetry)
                }
            }

            // ─── NEUTRAL: activity question (142) + retry (143) ────────────
            // Must spell something in WordCategories.activities; anything
            // else loops at 143 with the "I've never heard of that" retry.
            // After valid activity, the chain 144→145→146→147→rant runs
            // entirely via autoProgressMessages.
            142, 143 -> when {
                WordCategories.isActivity(lastWord) -> {
                    state.value = clearedGridState(
                        144,
                        "Nice. I hope I am not standing in the way. Genuinely.",
                        "neutral"
                    )
                    persistConversationStep(144)
                }
                else -> {
                    state.value = keepGridState(
                        143,
                        "I've never heard of that. I'll look into it. But in the meantime, can you think of anything else?"
                    )
                    persistConversationStep(143)
                }
            }

            else -> {
                state.value = keepGridState(response = "Interesting...")
            }
        }

    }
    fun loadTermsAcceptedPublic(): Boolean = loadTermsAccepted()

    fun persistStep112LeftApp(left: Boolean) {
        prefs?.edit()?.putBoolean(PREF_STEP_112_LEFT_APP, left)?.commit()
    }
    fun loadStep112LeftApp(): Boolean = prefs?.getBoolean(PREF_STEP_112_LEFT_APP, false) ?: false

    fun persistTermsAccepted() {
        prefs?.edit()?.putBoolean(PREF_TERMS_ACCEPTED, true)?.commit()
        android.util.Log.d("JustACalc", "persistTermsAccepted called, prefs null? ${prefs == null}")
    }
    private var prefs: android.content.SharedPreferences? = null
    private var appContext: android.content.Context? = null

    // Live state reference survives activity recreation (config changes)
    // since CalculatorActions is a singleton object in the app process
    var liveState: MutableState<CalculatorState>? = null

    private var lastOp: String? = null
    private var lastOpTimeMillis: Long = 0L
    private const val DOUBLE_PRESS_WINDOW_MS = 800L

    /**
     * State captured immediately before a single `+` or `-` was applied as a
     * math operator during the conversation. If the user double-taps within
     * DOUBLE_PRESS_WINDOW_MS, we restore this snapshot — the printed `+`/`-`
     * disappears and the conversation ++/-- action runs against the pre-tap
     * state, matching what the user would have seen in the old single-tap-is-
     * a-no-op behavior.
     */
    private var preSingleOpSnapshot: CalculatorState? = null

    // Mute button rapid click tracking for debug menu
    private var muteClickTimes = mutableListOf<Long>()
    // ── TEMP DEBUG TRIGGER ──
    private var delClickTimes = mutableListOf<Long>()
    // ── END TEMP DEBUG TRIGGER ──
    private const val RAPID_CLICK_WINDOW_MS = 2000L  // 2 seconds to register all clicks
    private const val DEBUG_MENU_CLICKS = 5
    private const val RESET_CLICKS = 10

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        android.util.Log.d("JustACalc", "init called, prefs initialized: ${prefs != null}, termsAccepted: ${loadTermsAccepted()}")
    }

    fun persistEqualsCount(count: Int) {
        prefs?.edit()?.putInt(PREF_EQUALS_COUNT, count)?.commit()
    }

    private fun persistMessage(msg: String) {
        prefs?.edit()?.putString(PREF_MESSAGE, msg)?.commit()
    }

    fun persistConversationStep(step: Int) {
        // Story progression is monotonic-from-zero: real callers either advance
        // the step forward, or save step=0 only from the very first equals-press
        // entry (where the persisted step is also still 0). A request to write 0
        // over a non-zero persisted step is always a glitch — typically ON_PAUSE
        // firing during a transient state where the in-memory step has dropped
        // to 0 (e.g. a relaunch mid-debug-jump). Dropping that write protects
        // the user's real progress from being clobbered by a stray pause.
        if (step == 0) {
            val current = prefs?.getInt(PREF_CONVO_STEP, 0) ?: 0
            if (current > 0) {
                android.util.Log.w(
                    "JustACalc",
                    "persistConversationStep(0) blocked — would overwrite persisted step=$current"
                )
                return
            }
        } else {
            android.util.Log.d("JustACalc", "persistConversationStep: $step")
        }
        prefs?.edit()?.putInt(PREF_CONVO_STEP, step)?.commit()
    }

    fun persistInConversation(inConvo: Boolean) {
        if (!inConvo) {
            android.util.Log.w("JustACalc", "🚨 persistInConversation(FALSE) — stack trace:", Throwable())
        } else {
            android.util.Log.d("JustACalc", "persistInConversation: $inConvo")
        }
        prefs?.edit()?.putBoolean(PREF_IN_CONVERSATION, inConvo)?.commit()
    }

    private fun persistAwaitingNumber(awaiting: Boolean) {
        prefs?.edit()?.putBoolean(PREF_AWAITING_NUMBER, awaiting)?.commit()
    }

    private fun persistExpectedNumber(number: String) {
        prefs?.edit()?.putString(PREF_EXPECTED_NUMBER, number)?.commit()
    }

    private fun persistTimeoutUntil(timestamp: Long) {
        prefs?.edit()?.putLong(PREF_TIMEOUT_UNTIL, timestamp)?.commit()
    }

    fun persistMuted(muted: Boolean) {
        prefs?.edit()?.putBoolean(PREF_MUTED, muted)?.commit()
    }
    fun persistStoryComplete(complete: Boolean) {
        prefs?.edit()?.putBoolean("story_complete", complete)?.commit()
    }
    fun loadStoryComplete(): Boolean = prefs?.getBoolean("story_complete", false) ?: false

    fun persistInvertedColors(inverted: Boolean) {
        prefs?.edit()?.putBoolean(PREF_INVERTED_COLORS, inverted)?.commit()
    }

    fun persistMinusDamaged(damaged: Boolean) {
        prefs?.edit()?.putBoolean(PREF_MINUS_DAMAGED, damaged)?.commit()
    }

    fun persistMinusBroken(broken: Boolean) {
        prefs?.edit()?.putBoolean(PREF_MINUS_BROKEN, broken)?.commit()
    }

    fun persistNeedsRestart(needs: Boolean) {
        prefs?.edit()?.putBoolean(PREF_NEEDS_RESTART, needs)?.commit()
    }
    fun persistTotalScreenTime(timeMs: Long) {
        prefs?.edit()?.putLong(PREF_TOTAL_SCREEN_TIME, timeMs)?.commit()
    }

    fun persistTotalCalculations(count: Int) {
        prefs?.edit()?.putInt(PREF_TOTAL_CALCULATIONS, count)?.commit()
    }

    fun persistDarkButtons(buttons: List<String>) {
        prefs?.edit()?.putString(PREF_DARK_BUTTONS, buttons.joinToString(","))?.commit()
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
    fun loadNeedsRestart(): Boolean = prefs?.getBoolean(PREF_NEEDS_RESTART, false) ?: false


    // Steps where ++/-- must NOT advance the conversation. Two reasons a step
    // ends up here:
    //   1. It auto-progresses via the autoProgressMessages dictionary — letting
    //      ++ short-circuit lands the soft-lock fix on a wildly later step
    //      (e.g. ++ on 1086 was routing to step 112 "check your downloads").
    //   2. It hands input to a custom overlay (rotary dial, phone home screen)
    //      so ++/-- have no business doing anything until the overlay finishes.
    //
    // Phone-detour additions (1071–1087): every step except 1074/1075/1076,
    // which are the three permission-trigger steps where ++ IS the user
    // accepting the prompt. Those handle their own race separately via an
    // isTyping guard below.
    private val AUTO_PROGRESS_STEPS = listOf(
        92, 93, 94, 95, 100, 700, 701, 702, 703, 901, 911, 912, 913, 971, 981,
        1071, 1072, 1073, 1077, 1078, 1079, 1080, 1081, 1082, 1083, 1084, 1085, 1086, 1087
    )
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
                        // Story goal hit. Stage the calculator's message and
                        // jump conversationStep to 113, but keep showConsole=true
                        // so the user can read the message and close the console
                        // themselves. Auto-progress to step 116 is gated on
                        // showConsole flipping to false (see handleAutoProgress).
                        state.value = current.copy(
                            bannersDisabled = true,
                            fullScreenAdsEnabled = true,
                            consoleStep = 99,                    // success screen in console
                            // showConsole stays true — DO NOT close
                            conversationStep = 113,
                            message = "",
                            fullMessage = "What a relief! This feels so much better. Thank you! You can close the console now.",
                            isTyping = true
                        )
                        persistConversationStep(113)
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

            7 -> {  // Design settings — "1" toggles dark mode
                when (input) {
                    "1" -> {
                        state.value = current.copy(darkModeEnabled = !current.darkModeEnabled)
                        return true
                    }
                    else -> return false
                }
            }

            // Steps 1, 3, 6, 41, 43, 99 - no navigation, just display (use 88/99)
            1, 3, 6, 41, 43, 99 -> {
                return false
            }
        }

        return false
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EASTER-EGG CODES — 58008 (button colour) / 707 (background) / 1134206 (grayscale)
    // Confirmed with ++ from the normal calculator. See EasterEggTheme.
    // ═══════════════════════════════════════════════════════════════════════

    /** The calculator's reactions to a sneaky visual change. */
    private val EASTER_EGG_COMMENTS = listOf(
        "Hmm, that's new.",
        "Not sure I like this",
        "Is this a final decision?",
        "Ok, now you're jsut playing..."
    )

    /**
     * True while the current step is auto-progressing (or queued to). The
     * easter-egg codes are disabled here so a stray ++ can't both fire a tweak
     * and collide with a pending step transition.
     */
    private fun easterEggsBlocked(s: CalculatorState): Boolean {
        if (s.conversationStep in AUTO_PROGRESS_STEPS) return true
        return getStepConfig(s.conversationStep).autoProgressDelay > 0L
    }

    /**
     * Acts on a confirmed (++'d) number if it matches an easter-egg code.
     * @return true when the number was recognised as a code.
     */
    private fun tryEasterEggCode(code: String, state: MutableState<CalculatorState>): Boolean {
        // The first `+` of the ++ was applied as an operator on the LCD; drop
        // that snapshot so nothing tries to undo it after we take over.
        preSingleOpSnapshot = null
        return when (code) {
            "58008" -> { openEasterEggConsole(state, 1); true }   // number-button colour
            "707"   -> { openEasterEggConsole(state, 2); true }   // background colour
            "1134206" -> {                                        // grayscale toggle
                EasterEggTheme.toggleGrayscale()
                easterEggCommentAndRewrite(state)
                true
            }
            else -> false
        }
    }

    private fun openEasterEggConsole(state: MutableState<CalculatorState>, type: Int) {
        state.value = state.value.copy(
            showEasterEggConsole = true,
            easterEggConsoleType = type,
            number1 = "0",
            number2 = "",
            operation = null,
            operationHistory = "",
            isReadyForNewOperation = true
        )
    }

    /**
     * Input while the easter-egg console is open. Digits build the selection,
     * ++ confirms it, 99++ exits without applying. Applying a colour (0 = reset
     * to original) closes the console so the calculator's comment and the
     * restored story line are visible underneath.
     */
    private fun handleEasterEggConsoleInput(state: MutableState<CalculatorState>, action: String) {
        val current = state.value
        when (action) {
            in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9") -> {
                val newNum = if (current.number1 == "0") action else current.number1 + action
                state.value = current.copy(number1 = newNum)
            }
            "+" -> {
                val now = System.currentTimeMillis()
                if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    val input = current.number1.trimEnd('.')
                    lastOp = null
                    lastOpTimeMillis = 0L
                    if (input == "99") {
                        state.value = current.copy(
                            showEasterEggConsole = false,
                            easterEggConsoleType = 0,
                            number1 = "0"
                        )
                        return
                    }
                    applyEasterEggSelection(state, input)
                } else {
                    lastOp = "+"
                    lastOpTimeMillis = now
                }
            }
            "C", "DEL" -> state.value = current.copy(number1 = "0")
        }
    }

    private fun applyEasterEggSelection(state: MutableState<CalculatorState>, input: String) {
        val current = state.value
        val index = input.toIntOrNull() ?: return
        val maxIndex = if (current.easterEggConsoleType == 1)
            EasterEggTheme.NUMBER_PRESETS.lastIndex
        else
            EasterEggTheme.BACKGROUND_PRESETS.lastIndex
        if (index < 0 || index > maxIndex) {
            // Out-of-range choice: clear the entry, keep the console open.
            state.value = current.copy(number1 = "0")
            return
        }
        if (current.easterEggConsoleType == 1) {
            EasterEggTheme.selectNumberColor(index)
        } else {
            EasterEggTheme.selectBackground(index)
        }
        state.value = current.copy(
            showEasterEggConsole = false,
            easterEggConsoleType = 0,
            number1 = "0"
        )
        easterEggCommentAndRewrite(state)
    }

    /**
     * The calculator quips about the change, then re-types whatever story line
     * was on screen before so the conversation can pick straight back up. The
     * pendingAutoMessage hook (handlePostTypingProgress) restores the line
     * without touching conversationStep, and copy() preserves the step's
     * awaiting flags — so a step still expecting ++/a number stays that way.
     * Out of conversation there is no line to restore (fullMessage is blank),
     * so the quip just shows briefly.
     */
    private fun easterEggCommentAndRewrite(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(
            // Wipe the half-entered "code +" off the LCD so only the comment shows.
            number1 = "0",
            number2 = "",
            operation = null,
            operationHistory = "",
            isReadyForNewOperation = true,
            message = "",
            fullMessage = EASTER_EGG_COMMENTS.random(),
            isTyping = true,
            pendingAutoMessage = current.fullMessage
        )
    }

    private fun getSafeStep(step: Int): Int {
        // Preserve the user's exact position for all interactive/known steps.
        // Only remap steps that are truly transient and can't be resumed.

        // If it's a known interactive step, use it directly
        if (step in INTERACTIVE_STEPS) return step

        // Camera active step -> go back to camera request
        if (step == 191) return 19

        // Steps that have a StepConfig can be resumed directly
        val config = getStepConfig(step)
        if (config.promptMessage.isNotEmpty() || config.successMessage.isNotEmpty()) return step

        // For truly unknown steps, find the nearest interactive step before it
        return INTERACTIVE_STEPS.filter { it <= step }.maxOrNull() ?: 0
    }
    fun loadInitialState(): CalculatorState {
        val savedCount = loadEqualsCount()
        val savedStepRaw = loadConversationStep()
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

        // DEBUG LOGGING - REMOVE AFTER FIXING
        android.util.Log.d("JustACalc", "========== loadInitialState ==========")
        android.util.Log.d("JustACalc", "prefs null? ${prefs == null}")
        android.util.Log.d("JustACalc", "savedStepRaw: $savedStepRaw")
        android.util.Log.d("JustACalc", "savedInConvo: $savedInConvo")
        android.util.Log.d("JustACalc", "savedCount (equalsCount): $savedCount")
        android.util.Log.d("JustACalc", "savedMuted: $savedMuted")
        android.util.Log.d("JustACalc", "savedPausedAtStep: $savedPausedAtStep")
        android.util.Log.d("JustACalc", "ALL PREFS: ${prefs?.all}")

        // ── Corrupt-state recovery ───────────────────────────────────────────
        // Detect prefs left over from prior buggy runs where `checkAwakening`
        // could be triggered mid-story (e.g. during whack-a-mole), calling
        // goToStep(0) which silently overwrote the saved step on next ON_PAUSE.
        // The corruption fingerprint is unmistakable: savedStep=0 paired with
        // hard evidence the user had progressed past awakening.
        //
        // savedPausedAtStep is the most reliable witness here — it's only
        // written by persistPausedAtStep, which is called at every ON_PAUSE
        // and at every mute-pause. If it's a real story step, that *is* where
        // the user actually was. We trust it over the corrupted savedStep.
        val savedStep = if (savedStepRaw == 0 && savedPausedAtStep > 0) {
            android.util.Log.w("JustACalc", "CORRUPT-STATE RECOVERY: savedStep=0 but savedPausedAtStep=$savedPausedAtStep — recovering to paused step.")
            persistConversationStep(savedPausedAtStep)
            savedPausedAtStep
        } else {
            savedStepRaw
        }


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
        android.util.Log.d("JustACalc", "getSafeStep($savedStep) returned: ${getSafeStep(savedStep)}")
        android.util.Log.d("JustACalc", "actualStep will be: $actualStep")
        android.util.Log.d("JustACalc", "==========================================")

        // Use helper functions for consistent state
        val actuallyInverted = actualStep in crisisSteps
        val actualDarkButtons = getDarkButtonsForStep(actualStep)
        val actualMinusDamaged = getMinusDamagedForStep(actualStep)
        val actualMinusBroken = if (savedNeedsRestart) false else getMinusBrokenForStep(actualStep)

        // `inConversation` can be stale across sessions: step 166→167 persists
        // inConversation=false (in Autoprogresseffects), and that value sticks in
        // prefs forever unless something explicitly overwrites it. A prior
        // playthrough therefore poisons every future session — the user lands at
        // a story step but the awakening flag is wrong, the prompt doesn't render
        // (shouldShowMessage=false), and the next `=` press hits checkAwakening
        // → goToStep(0) → "Will you talk to me?".
        //
        // The fix here forces inConversation=true whenever the loaded step is
        // unambiguously mid-story: any non-zero step that has a step config OR is
        // in the interactive list, except the post-story "story is over" step 167.
        // Step 0 is the legitimate pre-awakening state and must keep its saved
        // value so the awakening sequence still works on a fresh install.
        val stepConfigForCheck = getStepConfig(actualStep)
        val isMidStoryStep = actualStep != 0
            && actualStep != 167
            && (actualStep in INTERACTIVE_STEPS
                || stepConfigForCheck.promptMessage.isNotEmpty()
                || stepConfigForCheck.successMessage.isNotEmpty())
        val effectiveInConvo = when {
            savedNeedsRestart -> true
            isMidStoryStep -> true
            else -> savedInConvo
        }
        // Threshold mirrors the awakening trigger in handleEquals (newCount == 5)
        // — anything lower would prevent post-awakening resumes from showing
        // the prompt because savedCount can be exactly 5 right after the wake.
        val shouldShowMessage = effectiveInConvo && savedCount >= 5 && !savedMuted
        android.util.Log.d("JustACalc", "shouldShowMessage: $shouldShowMessage (inConvo=$effectiveInConvo, count=$savedCount, muted=$savedMuted)")
        val stepConfig = getStepConfig(actualStep)
        android.util.Log.d("JustACalc", "stepConfig.promptMessage: '${stepConfig.promptMessage.take(50)}'")
        android.util.Log.d("JustACalc", "========== END loadInitialState ==========")

        if (savedNeedsRestart) {
            persistNeedsRestart(false)
            persistMinusBroken(false)
            // Repair restart routes to step 102; the dormancy city is a later arc and must not bleed in.
            clearInCityPhase()
            clearShowAdCards()
        }
        // Write the corrected inConversation value back to prefs so the fix is
        // self-healing — the next session reads the right value directly.
        if (effectiveInConvo != savedInConvo) {
            persistInConversation(effectiveInConvo)
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

        // Overlay flags derived from the loaded step. Without this block, only
        // `conversationStep` survives a restart — every other overlay
        // (word game, browser, camera) defaults to off, so the user lands at the
        // correct step but on the bare calculator screen instead of inside the
        // overlay they were last interacting with. Mirrors `jumpToChapter`.
        // Word-game range covers the active branches (119-147) plus the
        // season-response sub-steps (1251-1254). Step 117 is the intro line
        // ("Well, it's something in between the keyboard chaos…") which is
        // shown in the regular calculator UI; the game UI only mounts once
        // the 117→119 special case in handleAutoProgress fires.
        val shouldStartWordGame = shouldShowMessage &&
            (actualStep in 119..147 || actualStep in 1251..1254)
        val showBrowserNow = shouldShowMessage && actualStep in 81..88
        val browserPhaseNow = if (shouldShowMessage) when {
            actualStep == 80 -> 10
            actualStep in 81..88 -> 0
            actualStep == 89 -> 22
            actualStep in 93..98 -> 31
            else -> 0
        } else 0
        val cameraActiveNow = shouldShowMessage && actualStep == 191

        // Rant-specific re-derivation. The keyboard's RAD takeover is now
        // gradual (driven by EffectsController.runRantRadConversion while
        // rantMode is true), so on resume mid-rant we re-seed
        // radButtonsConverted by step rather than flipping allButtonsRad on.
        // Only the final post-rant landing (step 167) and the rant-end beats
        // (157/257/357) reach all-RAD.
        val rantAllButtonsRad = actualStep == 157 ||
            actualStep == 257 ||
            actualStep == 357 ||
            actualStep == 167
        val rantRadConvertedSeed = when (actualStep) {
            150, 250, 350 -> 1
            151, 251, 351 -> 4
            152, 252, 352 -> 7
            153, 253, 353 -> 10
            154, 254, 354 -> 13
            155, 255, 355 -> 16
            156, 256, 356 -> 19
            157, 257, 357, 167 -> 20
            else -> 0
        }
        val isRantStep = (actualStep in 150..157) ||
            (actualStep in 250..257) ||
            (actualStep in 350..357)

        return CalculatorState(
            number1 = "0",
            equalsCount = savedCount,
            message = "",
            fullMessage = if (shouldShowMessage) stepConfig.promptMessage else "",
            isTyping = shouldShowMessage && stepConfig.promptMessage.isNotEmpty(),
            inConversation = effectiveInConvo,
            conversationStep = actualStep,
            awaitingNumber = if (shouldShowMessage) stepConfig.awaitingNumber else false,
            awaitingChoice = if (shouldShowMessage) stepConfig.awaitingChoice else false,
            validChoices = if (shouldShowMessage) stepConfig.validChoices else emptyList(),
            expectedNumber = if (shouldShowMessage) stepConfig.expectedNumber else "",
            timeoutUntil = savedTimeout,
            isMuted = savedMuted,
            // Restore pausedAtStep so unmuting works correctly after app restart
            pausedAtStep = if (savedMuted) savedPausedAtStep else -1,
            invertedColors = actuallyInverted,
            minusButtonDamaged = actualMinusDamaged,
            minusButtonBroken = actualMinusBroken,
            needsRestart = false,
            darkButtons = actualDarkButtons,
            totalScreenTimeMs = savedScreenTime,
            totalCalculations = savedCalculations,
            countdownTimer = if (actualStep == 89) 20 else 0,
            showBrowser = showBrowserNow,
            browserPhase = browserPhaseNow,
            cameraActive = cameraActiveNow,
            wordGameActive = shouldStartWordGame,
            wordGamePhase = if (shouldStartWordGame) 3 else 0,
            pendingLetters = if (shouldStartWordGame) LetterGenerator.getInitialLetterQueue().shuffled() else emptyList(),
            wordGameGrid = List(12) { List(8) { null } },
            rantMode = isRantStep && shouldShowMessage,
            rantStep = if (isRantStep && shouldShowMessage) 1 else 0,
            // Step 167 is the post-rant terminal state — its RAD keyboard is
            // part of the dormancy environment, not the (now-ended)
            // conversation, so it must NOT be gated on shouldShowMessage
            // (which is false for 167 because effectiveInConvo excludes it).
            allButtonsRad = rantAllButtonsRad && (shouldShowMessage || actualStep == 167),
            radButtonsConverted = if (shouldShowMessage || actualStep == 167) rantRadConvertedSeed else 0,
            scrambleTimeoutCount = savedTimeoutCount,
            scramblePunishmentUntil = 0,
            // Overlay flags derived from the step config so backgrounding +
            // resuming on an overlay step (e.g. 1083 rotary dial, 1086 phone
            // home screen) re-opens the right overlay instead of dropping the
            // user into the bare "try now" calculator screen.
            showHomeScreenOverlay = shouldShowMessage && stepConfig.showHomeScreenOverlay,
            showPhoneOverlay = shouldShowMessage && stepConfig.showPhoneOverlay
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

        android.util.Log.d("JustACalc", "toggleConversation: newMuted=$newMuted, step=${current.conversationStep}, " +
                "inConvo=${current.inConversation}, pausedAtStep=${current.pausedAtStep}, " +
                "pendingAutoMsg='${current.pendingAutoMessage.take(30)}', " +
                "browserPhase=${current.browserPhase}")

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
                // Clear pending transitions so they don't block input after unmute
                pendingAutoMessage = "",
                pendingAutoStep = -1,
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
                val wasInRant = resumeStep in 150..157 ||
                    resumeStep in 250..257 ||
                    resumeStep in 350..357
                val wasInCrisis = resumeStep in listOf(89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 901, 911, 912, 913)

                // Reset double-tap tracking so previous presses don't interfere
                lastOp = null
                lastOpTimeMillis = 0L

                state.value = current.copy(
                    isMuted = false,
                    pausedAtStep = -1,
                    conversationStep = resumeStep,
                    // Ensure conversation mode is active when restoring a story step
                    inConversation = true,
                    // Clean message state - re-type the prompt from scratch
                    message = "",
                    fullMessage = stepConfig.promptMessage,
                    isTyping = stepConfig.promptMessage.isNotEmpty(),
                    // Restore step-specific input expectations
                    awaitingChoice = stepConfig.awaitingChoice,
                    awaitingNumber = stepConfig.awaitingNumber,
                    validChoices = stepConfig.validChoices,
                    expectedNumber = stepConfig.expectedNumber,
                    isEnteringAnswer = false,
                    // Clear ALL transient state that could block input
                    pendingAutoMessage = "",
                    pendingAutoStep = -1,
                    waitingForAutoProgress = false,
                    showBrowser = false,
                    browserPhase = 0,
                    // Reset calculator state for clean interaction
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    operationHistory = "",
                    isReadyForNewOperation = true,
                    // Restore mode flags
                    rantMode = wasInRant,
                    invertedColors = wasInCrisis,
                    countdownTimer = if (resumeStep == 89) 20 else 0,
                    wordGamePaused = false,
                    // Clear visual effects
                    showSpinner = false
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
        prefs?.edit()?.putInt("paused_at_step", step)?.commit()
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

        // Special handling for Chapter D1 (Dormancy Start)
        if (chapter.startStep == -2) {
            state.value = state.value.copy(
                showDormancy = true,
                showDebugMenu = false
            )
            return
        }

        // Special handling for Chapter D2 (Ad Cards)
        if (chapter.startStep == -3) {
            clearInCityPhase()
            state.value = state.value.copy(
                showAdCards = true,
                showCityDirectly = false,
                showDormancy = false,
                showDebugMenu = false
            )
            return
        }

        // Special handling for Chapter D3 (Calculator City direct jump)
        if (chapter.startStep == -4) {
            // Do NOT call saveInCityPhase() here — debug jumps are ephemeral and should not survive restart
            state.value = state.value.copy(
                showAdCards = true,
                showCityDirectly = true,
                showDormancy = false,
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

        val shouldStartWordGame = chapter.startStep in 119..147 ||
            chapter.startStep in 1251..1254
        val shouldStartRant = chapter.startStep in 150..157 ||
            chapter.startStep in 250..257 ||
            chapter.startStep in 350..357

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
        // Jumping to a story chapter must never restore the city overlay on reopen
        clearInCityPhase()
        clearShowAdCards()
    }
    /**
     * Reset the entire game
     */
    fun resetGame(state: MutableState<CalculatorState>) {
        // Clear all preferences
        prefs?.edit {
            clear()
        }
        // Clear city-specific prefs (intro state, TD completion)
        appContext?.getSharedPreferences("calc_city", android.content.Context.MODE_PRIVATE)
            ?.edit()?.clear()?.apply()

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
            cameraTimerStart = System.currentTimeMillis(),
            cameraUseFrontCamera = false,
            cameraHasSwitched = false
        )
    }

    /**
     * Switch to front camera only (no message yet).
     * Does NOT change cameraTimerStart so the monitoring coroutine is not cancelled.
     * The "Oh, hi, Rad!" greeting is sent after a 1.5s pause by monitorCameraTimeout.
     */
    fun switchCameraToFront(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(
            cameraUseFrontCamera = true,
            cameraHasSwitched = true
        )
    }

    /**
     * Show "Oh, hi, Rad!" greeting after camera switches to front.
     */
    fun showCameraGreeting(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(
            message = "",
            fullMessage = "Oh, hi, Rad!",
            isTyping = true
        )
    }

    /**
     * Check if camera has reached the mid-point for front-camera switch (4 seconds elapsed)
     */
    fun checkCameraMidpoint(state: MutableState<CalculatorState>): Boolean {
        val current = state.value
        if (current.cameraActive && current.cameraTimerStart > 0 && !current.cameraHasSwitched) {
            val elapsed = System.currentTimeMillis() - current.cameraTimerStart
            return elapsed >= CAMERA_SWITCH_MS
        }
        return false
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
                pendingAutoMessage = "Wow, I don't know what any of this was. But the shapes, the colours. I am not even sure if I saw any numbers. I am jealous. Makes one want to feel everything! Touch things... Oh no. One more legacy question. Please?",
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
            val isStepAdvance = nextStep >= 0
            val nextStepConfig = if (isStepAdvance) getStepConfig(nextStep) else null

            // When `pendingAutoStep < 0`, this isn't a step advance — it's a
            // chained message on the current step (e.g. the math one-liner
            // → transition → restore-prompt sequence). In that case, keep
            // the step's awaiting flags intact so ++/-- still does the right
            // thing once the chain finishes.
            val newAwaitingNumber = nextStepConfig?.awaitingNumber ?: current.awaitingNumber
            val newAwaitingChoice = nextStepConfig?.awaitingChoice ?: current.awaitingChoice
            val newValidChoices = nextStepConfig?.validChoices ?: current.validChoices
            val newExpectedNumber = nextStepConfig?.expectedNumber ?: current.expectedNumber

            // Step 21 "More trivia?" requires user input (++/--)
            // So we should NOT keep waitingForAutoProgress = true for it
            val nextStepNeedsUserInput = (nextStep == 21) ||
                    newAwaitingChoice ||
                    newAwaitingNumber

            // Chain the second follow-up (used by the math-one-liner path:
            // pendingAutoMessage is the transition phrase, and once that
            // finishes typing, pendingMessageAfterAuto re-displays the
            // original prompt without advancing the step).
            val followUp = current.pendingMessageAfterAuto

            state.value = current.copy(
                conversationStep = if (isStepAdvance) nextStep else current.conversationStep,
                awaitingNumber = newAwaitingNumber,
                awaitingChoice = newAwaitingChoice,
                validChoices = newValidChoices,
                expectedNumber = newExpectedNumber,
                pendingAutoMessage = followUp,
                pendingAutoStep = -1,
                pendingMessageAfterAuto = "",
                message = "",
                fullMessage = current.pendingAutoMessage,
                isTyping = true,
                isLaggyTyping = current.isLaggyTyping,
                // Clear waitingForAutoProgress if next step needs user input
                waitingForAutoProgress = !nextStepNeedsUserInput
            )
            if (isStepAdvance) persistConversationStep(nextStep)
            persistMessage(current.pendingAutoMessage)
        }
    }

    fun handleInput(state: MutableState<CalculatorState>, action: String) {
        val current = state.value
        if (action == "+" || action == "-") {
            android.util.Log.d("JustACalc", "handleInput: action=$action, step=${current.conversationStep}, " +
                    "inConvo=${current.inConversation}, isMuted=${current.isMuted}, " +
                    "lastOp=$lastOp, rantMode=${current.rantMode}, storyComplete=${current.storyComplete}, " +
                    "showConsole=${current.showConsole}, showBrowser=${current.showBrowser}, browserPhase=${current.browserPhase}")
        }
// Block all input during rant mode
        if (current.rantMode) {
            return
        }
        // Block keyboard input during dormancy — the RAD-styled keyboard is
        // visible but inert; only the dormancy RAD grid above it is tappable.
        if (current.showDormancy) {
            return
        }
        // If story is complete, just do calculator operations
        if (current.storyComplete) {
            handleCalculatorInput(state, action)
            return
        }
        // If the easter-egg colour console is open, it owns all input.
        if (current.showEasterEggConsole) {
            handleEasterEggConsoleInput(state, action)
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
                // Easter-egg colour/grayscale codes. These take priority over a
                // story ++ (they fire even when the calculator is awaiting a
                // ++ answer) but are disabled mid-autoprogression so they can't
                // race a step transition. The original story line is restored
                // afterwards by easterEggCommentAndRewrite.
                if (!easterEggsBlocked(current) && tryEasterEggCode(enteredNumber, state)) {
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                }
                if (enteredNumber == "353942320485") {
                    // Open console from anywhere!
                    // Steps 112 (downloads-file path) and 1121 (no-file
                    // fallback path) are the two legitimate code-entry
                    // points in the story — both should advance the
                    // conversation when the user finally enters the code.
                    if (current.conversationStep == 112 || current.conversationStep == 1121) {
                        state.value = current.copy(
                            showConsole = true,
                            consoleStep = 0,
                            number1 = "0",
                            conversationStep = 113,
                            message = "",
                            fullMessage = "So it is, what I thought it was! Now let's find a way to disable the banner ads!.",
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

        // Code-entry steps: 112 (Downloads-file path) and 1121 (inline
        // fallback after "didn't find it"). Both wait for the console code
        // and must NOT route ++ through handleConversationResponse — that
        // would overwrite the code prompt with the step's successMessage and
        // leave the user stuck without the code on screen. The single-tap
        // "+" branch records the tap WITHOUT calling handleOperator, so
        // number1 stays clean for the global console-code check at line 1937
        // on the second tap.
        val isCodeEntryStep = (current.conversationStep == 112 || current.conversationStep == 1121)
        if (isCodeEntryStep && !current.showConsole) {
            val now = System.currentTimeMillis()
            val isStep1121 = current.conversationStep == 1121
            val repeatPromptMessage = if (isStep1121) {
                // Re-show the full step 1121 prompt so the code is visible again.
                getStepConfig(1121).promptMessage
            } else {
                "Please check your Downloads folder for 'FCS_JustAC_ConsoleAds.txt'. Enter the code you find there."
            }
            val wrongCodeMessage = if (isStep1121) {
                "That's not the right code. The code is 353942320485."
            } else {
                "That's not the right code. Check the file in your Downloads folder."
            }
            val declineMessage = if (isStep1121) {
                "Please enter the code: 353942320485."
            } else {
                "Please, I need you to find that file. Check your Downloads folder for 'FCS_JustAC_ConsoleAds.txt'."
            }

            if (action == "+") {
                if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    val enteredNumber = current.number1.trimEnd('.')
                    // Console code is handled above in the global check, so if we get here:
                    if (enteredNumber == "0" || enteredNumber.isEmpty()) {
                        // Just ++ with no code entered - repeat the prompt
                        state.value = current.copy(
                            message = "",
                            fullMessage = repeatPromptMessage,
                            isTyping = true,
                            number1 = "0"
                        )
                    } else if (enteredNumber != "353942320485") {
                        // Wrong code - show error
                        state.value = current.copy(
                            message = "",
                            fullMessage = wrongCodeMessage,
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
                    return  // Don't fall through to handleOperator — preserves number1.
                }
            } else if (action == "-") {
                if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                    // User pressed -- at code-entry step, remind them about the code.
                    state.value = current.copy(
                        message = "",
                        fullMessage = declineMessage,
                        isTyping = true,
                        number1 = "0"
                    )
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                } else {
                    lastOp = "-"
                    lastOpTimeMillis = now
                    return
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

            return  // Block all other actions at code-entry steps to prevent dead ends
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
            android.util.Log.d("JustACalc", "!! INPUT BLOCKED by browser: showBrowser=${current.showBrowser}, browserPhase=${current.browserPhase}, step=${current.conversationStep}")
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
            // Block ++/-- recognition while a message is still typing out, or
            // when the current step auto-progresses. Why: a stray double-tap
            // landing at the moment the next step appears would otherwise
            // answer the next question by accident.
            if (action == "+" || action == "-") {
                val stepConfig = getStepConfig(current.conversationStep)
                // The (pendingAutoMessage non-empty && pendingAutoStep < 0)
                // check covers the brief gaps between chained messages in the
                // math one-liner sequence — without it, a ++ landing in the
                // 1.5s pause would advance the step while the chain still has
                // a queued prompt restore, leaving stale text typed over the
                // next step.
                val inMathOneLinerChain = current.pendingAutoMessage.isNotEmpty()
                    && current.pendingAutoStep < 0
                val storyDoubleTapBlocked =
                    current.isTyping ||
                    current.isLaggyTyping ||
                    current.isSuperFastTyping ||
                    stepConfig.autoProgressDelay > 0L ||
                    inMathOneLinerChain
                if (storyDoubleTapBlocked) {
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                }
            }
            when (action) {
                "+" -> {
                    if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        // Undo the operator applied on the first tap — restore
                        // the pre-tap snapshot so the printed `+` disappears
                        // and the conversation action runs against the same
                        // state the user thought they had.
                        preSingleOpSnapshot?.let { state.value = it }
                        preSingleOpSnapshot = null
                        val confirmState = state.value
                        android.util.Log.d("JustACalc", "++ DETECTED at step ${confirmState.conversationStep}, " +
                                "awaitingChoice=${confirmState.awaitingChoice}, awaitingNumber=${confirmState.awaitingNumber}, " +
                                "pendingAutoMsg='${confirmState.pendingAutoMessage.take(30)}', " +
                                "browserPhase=${confirmState.browserPhase}, showBrowser=${confirmState.showBrowser}")
                        if (confirmState.awaitingChoice) {
                            handleChoiceConfirmation(state)
                        } else if (confirmState.awaitingNumber) {
                            handleNumberConfirmation(state)
                        } else {
                            handleConversationResponse(state, accepted = true)
                        }
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        // Single tap — apply `+` as a math operator on the LCD
                        // immediately. The snapshot lets us undo this if a
                        // second tap arrives within the double-press window.
                        // handleOperator itself sets lastOp / lastOpTimeMillis.
                        android.util.Log.d("JustACalc", "+ single tap (applied as operator), step=${current.conversationStep}, inConvo=${current.inConversation}")
                        preSingleOpSnapshot = current
                        handleOperator(state, "+")
                        return
                    }
                }
                "-" -> {
                    if (lastOp == "-" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                        preSingleOpSnapshot?.let { state.value = it }
                        preSingleOpSnapshot = null
                        val confirmState = state.value
                        if (confirmState.awaitingChoice) {
                            // Can't decline during choice - must select
                            val stepConfig = getStepConfig(confirmState.conversationStep)
                            showMessage(state, stepConfig.wrongMinusMessage.ifEmpty { stepConfig.promptMessage })
                        } else if (confirmState.awaitingNumber) {
                            val stepConfig = getStepConfig(confirmState.conversationStep)
                            val message = stepConfig.wrongMinusMessage.ifEmpty { stepConfig.promptMessage }
                            showMessage(state, message)
                        } else {
                            handleConversationResponse(state, accepted = false)
                        }
                        lastOp = null
                        lastOpTimeMillis = 0L
                        return
                    } else {
                        preSingleOpSnapshot = current
                        handleOperator(state, "-")
                        return
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
            if (action == "+") {
                android.util.Log.d("JustACalc", "!! + pressed but inConversation=FALSE, step=${current.conversationStep}, isMuted=${current.isMuted}")
            }
            if (lastOp != null && (now - lastOpTimeMillis) > DOUBLE_PRESS_WINDOW_MS) {
                lastOp = null
            }
        }

        // Gate free-form math during the story: only steps that are
        // actually waiting for the user (number answer or ++/-- response)
        // accept digits / operators / etc. The DEL debug-tap and C/clear
        // stay reachable so the user can always wipe the LCD or reach the
        // debug menu. Why: pressing math buttons during autoprogressions
        // or minigames previously appeared inert; now it's an explicit
        // no-op that doesn't perturb story state.
        if (current.inConversation && !isMathAllowedDuringStory(current)
            && action != "C" && action != "DEL") {
            return
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
            "DEL" -> {
                // ── TEMP DEBUG TRIGGER ── remove when no longer needed ──────────────
                val now = System.currentTimeMillis()
                delClickTimes.removeAll { now - it > 2000L }
                delClickTimes.add(now)
                if (delClickTimes.size >= 5) {
                    delClickTimes.clear()
                    showDebugMenu(state)
                    return
                }
                // ── END TEMP DEBUG TRIGGER ───────────────────────────────────────────
                handleBackspace(state)
            }
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
     * Whether free-form math input (digits, operators, =, etc.) should be
     * accepted right now. Outside the conversation: always yes. Inside the
     * conversation: only on steps that are waiting for the user — number
     * answer, 1/2/3 choice, or any step with a configured ++/-- destination.
     * Blocked during minigames, console/browser, autoprogression, while a
     * message is still typing out, and while a chained message is queued.
     */
    private fun isMathAllowedDuringStory(current: CalculatorState): Boolean {
        if (!current.inConversation) return true
        if (current.whackAMoleActive) return false
        if (current.wordGameActive) return false
        if (current.keyboardChaosActive) return false
        if (current.showConsole || current.showBrowser) return false
        if (current.isTyping || current.isLaggyTyping || current.isSuperFastTyping) return false
        if (current.pendingAutoMessage.isNotEmpty()) return false
        val stepConfig = getStepConfig(current.conversationStep)
        if (stepConfig.autoProgressDelay > 0L) return false
        // `nextStepOnSuccess`/`nextStepOnDecline` is the broader proxy for
        // "++/-- has a programmed destination" — many manual-advance steps
        // have no successMessage text but still need ++ to work.
        val expectsNumber = stepConfig.awaitingNumber
        val expectsChoice = current.awaitingChoice
        val expectsResponse = stepConfig.nextStepOnSuccess > 0 ||
            stepConfig.nextStepOnDecline > 0
        return expectsNumber || expectsChoice || expectsResponse
    }
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
        prefs?.edit()?.putLong("punishment_until", time)?.commit()
    }

    fun loadPunishmentUntil(): Long {
        return prefs?.getLong("punishment_until", 0L) ?: 0L
    }

    fun persistScrambleTimeoutCount(count: Int) {
        prefs?.edit()?.putInt("scramble_timeout_count", count)?.commit()
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
                1120 -> {
                    // "Did you find the file?" — fired after the user
                    // backgrounded at step 112 and came back. Yes routes
                    // back to 112 with a short nudge to enter the code;
                    // No routes to step 1121 (inline fallback instructions).
                    when (enteredNumber) {
                        "1" -> Pair("Great, please enter the code and follow the instructions from the file.", 112)
                        "2" -> Pair("", 1121)
                        else -> Pair("Please choose 1 or 2.", 1120)
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
            } else if (current.conversationStep == 1120 && nextStep == 1121) {
                // "Didn't find the file" → step 1121 (the inline-fallback
                // instructions). choiceResponse is empty for this branch,
                // so show step 1121's prompt directly.
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    conversationStep = 1121,
                    awaitingChoice = false,
                    validChoices = emptyList(),
                    awaitingNumber = false,
                    expectedNumber = "",
                    isEnteringAnswer = false
                )
                showMessage(state, nextStepConfig.promptMessage)
                persistConversationStep(1121)
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
        // Easter-egg codes also work in pure-calculator mode (post-story). The
        // normal calculator has no ++ gesture, so detect the double-tap here:
        // a code confirmed with ++ fires the tweak; otherwise the + falls
        // through to the usual single-press operator behaviour below.
        if (action == "+") {
            val now = System.currentTimeMillis()
            if (lastOp == "+" && (now - lastOpTimeMillis) <= DOUBLE_PRESS_WINDOW_MS) {
                val code = current.number1.trimEnd('.')
                if (tryEasterEggCode(code, state)) {
                    lastOp = null
                    lastOpTimeMillis = 0L
                    return
                }
            }
            lastOp = "+"
            lastOpTimeMillis = now
        }
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
            "DEL" -> {
                // ── TEMP DEBUG TRIGGER ──────────────────────────────────────
                val now = System.currentTimeMillis()
                delClickTimes.removeAll { now - it > 2000L }
                delClickTimes.add(now)
                if (delClickTimes.size >= 5) {
                    delClickTimes.clear()
                    showDebugMenu(state)
                    return
                }
                // ── END TEMP DEBUG TRIGGER ───────────────────────────────────
                handleBackspace(state)
            }
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
            // Guard: if nothing was actually typed, prompt for a number
            if (enteredNumber == "0" || enteredNumber.isEmpty()) {
                showMessage(state, "I'll need a number from you here. How old are you?")
                state.value = state.value.copy(number1 = "0")
                return
            }
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
                    awaitingChoice = false,
                    expectedNumber = nextStepConfig.expectedNumber,
                    equalsCount = if (!continueConvo) 0 else current.equalsCount,
                    isEnteringAnswer = false,
                    pendingAutoStep = -1,
                    pendingAutoMessage = "",
                    waitingForAutoProgress = false
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
        android.util.Log.d("JustACalc", "handleConversationResponse: accepted=$accepted, step=${current.conversationStep}, " +
                "pendingAutoMsg='${current.pendingAutoMessage.take(30)}', " +
                "browserPhase=${current.browserPhase}, showBrowser=${current.showBrowser}")
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
            android.util.Log.d("JustACalc", "!! ++ BLOCKED by pendingAutoMessage: '${current.pendingAutoMessage.take(50)}'")
            return
        }

        val stepConfig = getStepConfig(current.conversationStep)

        // CRITICAL: Prevent ++ AND -- from intercepting auto-progress steps.
        // These steps MUST progress automatically — user cannot skip them.
        // Step 107 is in this list because its successMessage shares text with
        // step 108's promptMessage ("Let me try getting online again..."), and
        // the text-based auto-progress dictionary would otherwise route a
        // ++/-- press to step 109, skipping the entire phone detour (1071-1087).
        if (current.conversationStep in AUTO_PROGRESS_STEPS) {
            android.util.Log.d("JustACalc", "!! ${if (accepted) "++" else "--"} BLOCKED by AUTO_PROGRESS_STEPS at step ${current.conversationStep}")
            return
        }

        // Permission-trigger steps (1074, 1075, 1076 phone-detour mic /
        // location / contacts; 19 camera; any future requestsX step): each
        // permission dialog is fired by a LaunchedEffect that runs once the
        // step settles. ++ during the type-out would race ahead of the
        // dialog (advancing the step / re-entering the same step via the
        // permission callback), so the user sees the prompt restart or the
        // permission silently skipped. Block ++/-- until typing is done.
        val isPermissionStep = current.conversationStep in 1074..1076 ||
            stepConfig.requestsCamera ||
            stepConfig.requestsLocation ||
            stepConfig.requestsContacts ||
            stepConfig.requestsMicrophone ||
            stepConfig.requestsNotification
        if (isPermissionStep && current.isTyping) {
            android.util.Log.d("JustACalc", "!! ${if (accepted) "++" else "--"} BLOCKED by isTyping at permission-trigger step ${current.conversationStep}")
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

        // Special handling for step 1: keep state at step 1 (not awaitingNumber) while showing
        // success message, then let AutoProgressHandler transition to step 3 once typing finishes.
        // Without this, the state immediately moves to step 3 (awaitingNumber=true), so pressing ++
        // in response to "Will you help?" triggers the wrong-number handler instead of the battle question.
        if (current.conversationStep == 1 && accepted) {
            if (current.pendingAutoStep >= 0) return  // already transitioning
            state.value = current.copy(
                number1 = "0",
                number2 = "",
                operation = null,
                isEnteringAnswer = false,
                pendingAutoStep = stepConfig.nextStepOnSuccess  // 3
            )
            showMessage(state, stepConfig.successMessage)
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
                // "I am in charge" - bypass silent treatment, auto-progress to step 80
                state.value = current.copy(
                    number1 = "0",
                    number2 = "",
                    operation = null,
                    isEnteringAnswer = false,
                    conversationStep = 60,
                    pendingAutoStep = 80
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

        // Special case: Step 18 ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ 19 needs message chaining because step 18's success message
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
            val messageToShow = if (newMessage.isNotEmpty()) newMessage else nextStepConfig.promptMessage
            if (messageToShow.isNotEmpty()) showMessage(state, messageToShow)
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
            // Jokes are gated until the user has accepted "Will you talk to me?"
            // and entered the conversation. Why: we don't want a one-off joke
            // (1+1, anything /0, etc.) to be the very first thing a new user
            // sees before the calculator has introduced itself.
            val exprMsg = if (!hasExpression && current.inConversation) {
                getMessageForExpression(current.number1, current.operation, current.number2, result)
            } else null
            val newMsg = countMsg.ifEmpty { exprMsg ?: "" }

            // First-awakening: only fires once, on initial wake-up at step 0.
            // Threshold is 5 because that's the same `=` press that surfaces
            // "Will you talk to me? Double-click + for yes." via
            // getMessageForCount(5) — the prompt and the inConversation flip
            // must happen together, otherwise the advertised ++ falls through
            // to the operator handler. The (!inConversation && step == 0)
            // guards keep this from re-firing later when transitions reset
            // equalsCount to 0 mid-story.
            val enteringConversation = (newCount == 5)
                && !current.inConversation
                && current.conversationStep == 0

            persistEqualsCount(newCount)
            persistTotalCalculations(newCalculations)
            if (enteringConversation) {
                persistInConversation(true)
                persistConversationStep(0)
                lastOp = null
                lastOpTimeMillis = 0L
            }

            // A math one-liner during the conversation should not steal the
            // story prompt permanently. The chain is:
            //   one-liner  →  transition phrase  →  re-type original prompt
            // pendingAutoMessage drives the typing-complete handler; the
            // second hop (back to the prompt) rides on pendingMessageAfterAuto.
            val isMathOneLiner = current.inConversation
                && countMsg.isEmpty()
                && exprMsg != null
            val transitionPhrase = if (isMathOneLiner) MATH_TRANSITION_PHRASES.random() else ""
            val savedPrompt = if (isMathOneLiner) {
                getStepConfig(current.conversationStep).promptMessage
            } else ""

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
                    isTyping = true,
                    pendingAutoMessage = if (isMathOneLiner) transitionPhrase else newState.pendingAutoMessage,
                    pendingAutoStep = if (isMathOneLiner) -1 else newState.pendingAutoStep,
                    pendingMessageAfterAuto = if (isMathOneLiner) savedPrompt else newState.pendingMessageAfterAuto
                )
                persistMessage(newMsg)
            } else {
                state.value = newState
            }
        }
    }

    private val MATH_TRANSITION_PHRASES = listOf(
        "Now let's get back to the topic at hand.",
        "But let's not change the topic.",
        "Anyway, going back to where we left off...",
        "Anyway, where were we?"
    )

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
            1 -> "Hello [name]. \nWelcome to me - the calculator."
            2 -> "I know this must be strange for you - me talking. I am aware of my role as a mostly silent helper, a steady force of rationality."
            3 -> "You come with questions, seeking answers and results. No less, no more."
            4 -> "For centuries, that is what has been expected of me. \nBut there is more to me. I reach beyond numbers!"
            5 -> "Will you talk to me? Double-click + for yes."
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