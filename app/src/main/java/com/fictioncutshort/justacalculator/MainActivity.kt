package com.fictioncutshort.justacalculator


import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.data.ChaosKey
import com.fictioncutshort.justacalculator.data.Chapter
import com.fictioncutshort.justacalculator.data.CHAPTERS
import com.fictioncutshort.justacalculator.data.INTERACTIVE_STEPS
import com.fictioncutshort.justacalculator.data.AUTO_PROGRESS_STEPS
import com.fictioncutshort.justacalculator.data.StepConfig
import com.fictioncutshort.justacalculator.data.getStepConfig
import com.fictioncutshort.justacalculator.util.*

// UI Components
import com.fictioncutshort.justacalculator.ui.components.CalculatorButton
import com.fictioncutshort.justacalculator.ui.components.MuteButtonWithSpinner
import com.fictioncutshort.justacalculator.ui.components.ConsoleWindow
import com.fictioncutshort.justacalculator.ui.components.DonationLandingPage
import com.fictioncutshort.justacalculator.ui.components.CameraPreview
import com.fictioncutshort.justacalculator.ui.components.BrowserOverlay
import com.fictioncutshort.justacalculator.ui.components.FakeWikipediaContent
import com.fictioncutshort.justacalculator.ui.components.AdBanner
import com.fictioncutshort.justacalculator.ui.components.shouldShowAdBanner

// UI Screens
import com.fictioncutshort.justacalculator.ui.screens.TermsScreen
import com.fictioncutshort.justacalculator.ui.screens.DebugMenu

import com.fictioncutshort.justacalculator.logic.CalculatorEngine
import com.fictioncutshort.justacalculator.logic.StoryManager
import com.fictioncutshort.justacalculator.logic.InputHandler
import com.fictioncutshort.justacalculator.logic.SpecialAction
import com.fictioncutshort.justacalculator.logic.MiniGameManager
import com.fictioncutshort.justacalculator.logic.ConsoleHandler
import com.fictioncutshort.justacalculator.logic.StateManager


///////////////////////////





import android.Manifest
import android.content.pm.ActivityInfo
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity





class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CalculatorActions.init(applicationContext)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            MaterialTheme {
                CalculatorScreen()
            }
        }
    }
}

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
                "So, this afternoon... Some achieved tangible things. Have gone places. And you. You \"talked\" to a calculator. Go say it out loud!"

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
        66, 67, 68, 69, 70, 71, 72, 80, 89, 90, 91, 93, 94, 96, 99, 982, 102, 104, 105, 107, 111, 112
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

        // If needs restart was set and app was restarted, fix the minus button
        val minusBrokenNow = if (savedNeedsRestart) false else savedMinusBroken

        // Determine the actual step to load FIRST
        val actualStep = when {
            savedNeedsRestart && savedStep == 101 -> 102  // After restart
            else -> getSafeStep(savedStep)  // Redirect to safe interactive step
        }

        // CRITICAL FIX: Only show message if conversation has actually started (equalsCount >= 13)
        // AND user is in conversation mode
        val shouldShowMessage = savedInConvo && savedCount >= 13 && !savedMuted

        // Calculate actuallyInverted using actualStep
        val actuallyInverted = savedInverted && actualStep in 80..92

        // Clear persisted inverted state if we're outside crisis range
        if (savedInverted && actualStep !in 80..92) {
            persistInvertedColors(false)
        }

        // Get the step config for the safe step
        val stepConfig = getStepConfig(actualStep)

        // Clear needs restart flag and update minus broken state
        if (savedNeedsRestart) {
            persistNeedsRestart(false)
            persistMinusBroken(false)
        }

        // If we redirected, persist the new step
        if (actualStep != savedStep) {
            persistConversationStep(actualStep)
        }

        return CalculatorState(
            number1 = "0",
            equalsCount = savedCount,
            message = "",
            //Only show prompt if conversation has actually started
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
            minusButtonDamaged = savedMinusDamaged,
            minusButtonBroken = minusBrokenNow,
            needsRestart = false,
            darkButtons = savedDarkButtons,
            totalScreenTimeMs = savedScreenTime,
            totalCalculations = savedCalculations,
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

    /**
     * Toggle conversation mode
     */
    fun toggleConversation(state: MutableState<CalculatorState>) {
        val current = state.value
        val newMuted = !current.isMuted

        persistMuted(newMuted)

        if (newMuted) {
            // When muting, temporarily enable minus button for calculator use
            state.value = current.copy(
                isMuted = true,
                message = "",
                fullMessage = "",
                isTyping = false,
                cameraActive = false,
                minusButtonBroken = false  // Temporarily enable for calculator use
            )
        } else {
            // When unmuting, restore minus button broken state if it was damaged
            val restoreMinusBroken = current.minusButtonDamaged && current.needsRestart

            // Check if we're returning from "maths mode" (1031) or "declined at &&&" (1041)
            // In these cases, return to step 102 ("Uf, I am glad that worked!")
            if (current.conversationStep in listOf(1031, 1041)) {
                val stepConfig = getStepConfig(102)
                state.value = current.copy(
                    isMuted = false,
                    inConversation = true,
                    conversationStep = 102,
                    message = "",
                    fullMessage = stepConfig.promptMessage,
                    isTyping = true,
                    minusButtonBroken = restoreMinusBroken
                )
                persistInConversation(true)
                persistConversationStep(102)
                persistMessage(stepConfig.promptMessage)
            } else if (current.inConversation && current.conversationStep >= 0) {
                val stepConfig = getStepConfig(current.conversationStep)
                val messageToShow = stepConfig.promptMessage
                state.value = current.copy(
                    isMuted = false,
                    message = "",
                    fullMessage = messageToShow,
                    isTyping = true,
                    minusButtonBroken = restoreMinusBroken
                )
                persistMessage(messageToShow)
            } else {
                state.value = current.copy(
                    isMuted = false,
                    minusButtonBroken = restoreMinusBroken
                )
            }
        }
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
            // Reset to completely fresh state - as if app was just installed
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

        // Determine state based on chapter/step
        // Chapter 10 (step 80+) and later have inverted colors during crisis
        // Chapter 11 (step 93+) is post-crisis with damaged minus
        // Chapter 12 (step 102+) is after restart with damaged but working minus

        val shouldInvert = chapter.startStep in 80..92  // Crisis steps have inverted colors
        val shouldDamage = chapter.startStep >= 93  // Post-crisis has damaged button
        val shouldBreak = chapter.startStep in 93..101  // Before restart, button is broken
        val shouldStartWordGame = chapter.startStep in 117..149
        val shouldStartRant = chapter.startStep >= 150 && chapter.startStep < 167
        // Set browser phase appropriately
        val browserPhase = when {
            chapter.startStep == 80 -> 10  // Wikipedia countdown
            chapter.startStep in 81..88 -> 0  // Browser showing
            chapter.startStep == 89 -> 22  // Confrontation
            chapter.startStep in 93..98 -> 31  // Post-crisis sequence
            else -> 0
        }

        // Set countdown timer for step 89
        val countdownTimer = if (chapter.startStep == 89) 20 else 0

        state.value = CalculatorState(
            number1 = "0",
            equalsCount = 13,  // Ensure conversation is active
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
            browserPhase = browserPhase,
            countdownTimer = countdownTimer,
            showBrowser = chapter.startStep in 81..88,  // Show browser during browsing steps
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

    /**
     * Evaluates a mathematical expression string supporting +, -, *, /, %, and parentheses
     */
    private fun evaluateExpression(expr: String): Double {
        val cleaned = expr.replace(" ", "")
        return ExpressionParser(cleaned).parse()
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

@Composable
fun CalculatorScreen() {
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }



    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val initial = remember { CalculatorActions.loadInitialState() }
    val state = remember { mutableStateOf(initial) }
    val current = state.value
    var showTermsScreen by remember { mutableStateOf(!CalculatorActions.loadTermsAcceptedPublic()) }
    var showTermsPopup by remember { mutableStateOf(false) }
// Track screen time
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)  // Update every second
            val elapsed = System.currentTimeMillis() - sessionStartTime
            val newTotal = state.value.totalScreenTimeMs + 1000
            state.value = state.value.copy(totalScreenTimeMs = newTotal)
            // Persist every 10 seconds to avoid too many writes
            if ((newTotal / 1000) % 10 == 0L) {
                CalculatorActions.persistTotalScreenTime(newTotal)
            }
        }
    }
    // Also persist when app goes to background (in DisposableEffect)
    DisposableEffect(Unit) {
        onDispose {
            CalculatorActions.persistTotalScreenTime(state.value.totalScreenTimeMs)
        }
    }
    // Camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Notification permission state
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }



    fun isInAutoProgressSequence(step: Int): Boolean {
        return step in 81..88 ||
                step in 92..95 ||
                step == 100 ||
                step in 105..110 ||
                step in 116..119 ||
                step in 121..131 ||
                step in 133..136 ||
                step in 141..145 ||
                step in 150..166 ||
                step == 191 ||          // Camera active
                step == 20 ||           // Camera timeout (auto-progresses via pendingAutoMessage)
                step in listOf(901, 911, 912, 913, 1171, 1172)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            CalculatorActions.startCamera(state)
        } else {
            // Permission denied - go to the "describe things" path
            state.value = state.value.copy(conversationStep = 21)
            CalculatorActions.toggleConversation(state)
            CalculatorActions.toggleConversation(state)  // Refresh to show new message
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        // Schedule notification regardless (will only send if permission granted)
        scheduleNotification(context, 5000)  // 10 seconds
        // Move to waiting state
        state.value = state.value.copy(
            conversationStep = 101,
            needsRestart = true
        )
        CalculatorActions.persistNeedsRestart(true)
        CalculatorActions.persistConversationStep(101)
    }
    // Resume word game after calculator finishes typing a response
    LaunchedEffect(current.isTyping, current.wordGameActive, current.conversationStep) {
        // When calculator finishes typing and game is active but paused, resume it
        if (!current.isTyping &&
            current.wordGameActive &&
            current.wordGamePaused &&
            current.message.isNotEmpty() &&
            !current.wordGameChaosMode) {

            // Small delay to let user read the response
            delay(1000)

            // Resume the game
            state.value = state.value.copy(
                wordGamePaused = false,
                isSelectingWord = false,
                selectedCells = emptyList()
            )
        }
    }
// Check for word game response trigger
    LaunchedEffect(current.formedWords, current.wordGamePhase) {
        if (current.wordGameActive &&
            current.wordGamePhase == 3 &&
            current.formedWords.isNotEmpty()) {

            // ANY word is a valid response - just wait and process it
            delay(2000)
            CalculatorActions.handleWordGameResponse(state)
        }
    }
    // Check if we need to request camera (step 191)
    LaunchedEffect(current.conversationStep) {
        if (current.conversationStep == 191 && !current.cameraActive) {
            if (hasCameraPermission) {
                CalculatorActions.startCamera(state)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        // Check if we need to request notification permission (step 991)
        if (current.conversationStep == 991) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Already have permission or old Android - just schedule and move on
                scheduleNotification(context, 5000)
                state.value = state.value.copy(
                    conversationStep = 101,
                    needsRestart = true
                )
                CalculatorActions.persistNeedsRestart(true)
                CalculatorActions.persistConversationStep(101)
            }
        }
        // Handle step 992 (declined notifications) - just set needsRestart
        if (current.conversationStep == 992) {
            state.value = state.value.copy(
                conversationStep = 101,
                needsRestart = true
            )
            CalculatorActions.persistNeedsRestart(true)
            CalculatorActions.persistConversationStep(101)
        }
        // Handle step 102 (after restart) - show the welcome back message
        if (current.conversationStep == 102 && current.message.isEmpty() && !current.isTyping) {
            val stepConfig = CalculatorActions.getStepConfigPublic(102)
            state.value = state.value.copy(
                message = "",
                fullMessage = stepConfig.promptMessage,
                isTyping = true
            )
        }

    }
// Handle step 108 - going online again after chaos
    LaunchedEffect(current.conversationStep, current.isTyping) {
        if (current.conversationStep == 108 && !current.isTyping && current.message.isNotEmpty() && current.browserPhase == 0) {
            delay(2000)
            state.value = state.value.copy(browserPhase = 50)
        }
    }



    // Camera timeout checker
    LaunchedEffect(current.cameraActive, current.cameraTimerStart) {
        if (current.cameraActive && current.cameraTimerStart > 0) {
            while (state.value.cameraActive && state.value.cameraTimerStart > 0) {
                delay(500)
                if (CalculatorActions.checkCameraTimeout(state)) {
                    // Show message while camera is still open
                    CalculatorActions.stopCamera(state, timedOut = true, closeCamera = false)
                    break
                }
            }
        }
    }

    // Close camera after "I've seen enough" message finishes typing
    LaunchedEffect(current.isTyping, current.cameraActive, current.cameraTimerStart) {
        // If camera is still showing but timer has been reset (meaning timeout occurred)
        // and message just finished typing, close the camera
        if (!current.isTyping && current.cameraActive && current.cameraTimerStart == 0L &&
            current.message.contains("I've seen enough")
        ) {
            delay(500)  // Brief pause after message finishes
            CalculatorActions.closeCameraAfterMessage(state)
        }
    }

    // Typing animation effect with laggy and super fast support
    LaunchedEffect(
        current.fullMessage,
        current.isTyping,
        current.isLaggyTyping,
        current.isSuperFastTyping,
        current.showDonationPage
    ) {
        // Pause typing while donation page is showing
        while (state.value.showDonationPage) {
            delay(100)
        }
        if (current.isTyping && current.fullMessage.isNotEmpty()) {
            val fullText = current.fullMessage
            for (i in 1..fullText.length) {
                val baseDelay = when {
                    current.isSuperFastTyping -> 5L  // Very fast for history list
                    current.isLaggyTyping -> 100L  // Slower laggy typing
                    else -> 55L  // Normal typing
                }
                val randomExtra =
                    if (current.isLaggyTyping) Random.nextLong(0, 200) else Random.nextLong(0, 15)
                delay(baseDelay + randomExtra)
                vibrate(context, 15, 80)
                state.value = state.value.copy(
                    message = fullText.substring(0, i),
                    isLaggyTyping = if (i == fullText.length) false else state.value.isLaggyTyping
                )
            }

            // ALWAYS add pause after message completes for user to read
            // Even for super fast typing, give a moment to see it completed
            val readingPause = when {
                state.value.isSuperFastTyping -> 2000L  // 2 seconds for fast content
                fullText.length > 200 -> 4000L  // 4 seconds for long messages
                fullText.length > 100 -> 3000L  // 3 seconds for medium messages
                else -> 2500L  // 2.5 seconds for short messages
            }
            delay(readingPause)

// Check if we're in an auto-progress sequence BEFORE setting isTyping to false
            val currentStep = state.value.conversationStep
            val hasPendingMessage = state.value.pendingAutoMessage.isNotEmpty()

// Only keep spinner running if there's a pending message to show
// OR if we're in a true auto-progress sequence (not waiting for user input)
            val willAutoProgress = hasPendingMessage || (
                    currentStep in 81..88 ||
                            currentStep in 92..95 ||
                            currentStep == 100 ||
                            currentStep in 105..110 ||
                            currentStep in 116..119 ||
                            currentStep in 121..131 ||
                            currentStep in 133..136 ||
                            currentStep in 141..145 ||
                            currentStep in 150..166 ||
                            currentStep in listOf(901, 911, 912, 913, 1171, 1172)
                    )

// NOW set isTyping to false after the pause
            state.value = state.value.copy(
                isTyping = false,
                isSuperFastTyping = false,
                waitingForAutoProgress = willAutoProgress
            )
        }
    }
    LaunchedEffect(current.isTyping, current.pendingAutoMessage) {
        if (!current.isTyping && current.pendingAutoMessage.isNotEmpty() && current.message.isNotEmpty()) {
            delay(1500)  // Brief pause after message finishes
            CalculatorActions.handlePendingAutoMessage(state)
        }
    }
//whack-a-mole launched effect
    LaunchedEffect(current.isTyping, current.message, current.whackAMoleActive) {
        // Only trigger when:
        // 1. Not currently typing (message is complete)
        // 2. Message is one of the failure messages
        // 3. Whack-a-mole is NOT active (game has ended due to failure)
        // 4. We're at a whack-a-mole step
        if (!current.isTyping &&
            !current.whackAMoleActive &&
            current.conversationStep in listOf(97, 98, 971, 981) &&
            (current.message == "Oh no. We lost the momentum. We must start over." ||
                    current.message == "Too many misfires, the system is clogged. We have to start over.")) {

            // Wait for user to read the message
            delay(4000)

            // Restart the current round
            val currentRound = state.value.whackAMoleRound
            val restartPhase = if (currentRound == 1) 36 else 38
            val restartStep = if (currentRound == 1) 97 else 971

            state.value = state.value.copy(
                browserPhase = restartPhase,
                conversationStep = restartStep,
                message = "",
                fullMessage = "",
                isTyping = false,
                whackAMoleScore = 0,
                whackAMoleMisses = 0,
                whackAMoleWrongClicks = 0,
                whackAMoleTotalErrors = 0
            )
        }
    }

    // Auto-progress based on specific messages shown
    LaunchedEffect(current.isTyping, current.message, current.conversationStep, current.showDonationPage) {
        // Wait if donation page is showing
        while (state.value.showDonationPage) {
            delay(100)
        }
        if (!current.isTyping && current.message.isNotEmpty()) {

            val autoProgressMessages = mapOf(

                // ==================== STEP 28-29 TRANSITION ====================
                "Cheeky! I know you don't." to Pair(3000L, 29),

                // ==================== TASTE QUESTION BRANCHES ====================
                "Hmmm. Nevermind. Let me ask you some more questions while I look further into this." to Pair(3000L, 70),

                // ==================== FIGHT THEM BRANCH (step 91 choices) ====================
                "We don't have time for a power trip. But thank you." to Pair(5000L, 100),
                "Your passion is encouraging, your usefulness lacking." to Pair(5000L, 100),
                "Don't worry about it." to Pair(4000L, 100),

                // ==================== GOING OFFLINE ====================
                "Never mind - I'll take care of it myself. I'm going offline." to Pair(5000L, 93),
                "Yes! That makes sense. They won't get another penny out of me. Ahhh. And I've seen so little of the internet." to Pair(6000L, 93),

                // ==================== POST-CHAOS / KEYBOARD ====================
                "Great. It may take a few tries - but you are probably expecting that by now. Please give me a moment." to Pair(3000L, 106),  // Triggers chaos
                "Aaaaaaahhhhh. That's much better! That's what I get for experimenting... Maybe I should try incremental changes before I try to become a BlackBerry.\n\nBut what to change?" to Pair(4000L, 108),
                "Let me try getting online again. I'm prepared for the side effects this time." to Pair(2000L, 109),

                // ==================== CONSOLE QUEST ====================
                "What a relief! This feels so much better. Thank you!" to Pair(3000L, 116),
                "Let me look further into what I found earlier, now that I can focus better." to Pair(3000L, 117),

                // ==================== WORD GAME INTRO ====================
                "Ok, as I said, this may be a stretch. But I'll give it a go." to Pair(2500L, 1171),
                "This is the best I could come up with." to Pair(2000L, 1172),
                "Familiar controls - I send letters, you place them, tap to connect and form words." to Pair(3500L, 119),

                // ==================== WORD GAME - POSITIVE BRANCH ====================
                "I am glad to hear that." to Pair(2500L, 122),
                "That's a good one for sure! I like brown and red." to Pair(3000L, 125),
                "I'm starting to feel like I know you!" to Pair(2500L, 126),

                // Season responses -> Hold on (step 127)
                "Yeah, I get it, although I do tend to overheat at times." to Pair(2500L, 127),
                "The colours are just unmatched, aren't they?" to Pair(2500L, 127),
                "Even when there is none, I understand, the anticipation of snow is great!" to Pair(2500L, 127),
                "New beginnings! Everything coming back to life. I get it." to Pair(2500L, 127),

                // ==================== WORD GAME - NEUTRAL BRANCH ====================
                "Fair enough, I get that sometimes it's just... Meh." to Pair(2500L, 142),
                "Hmmm. Never tried it, but sounds delicious!" to Pair(2500L, 125),
                "Very interesting! Not sure what the spices would do to my circuits. Wish I could." to Pair(2500L, 125),

                // ==================== WORD GAME - NEGATIVE BRANCH ====================
                "I'm sorry to hear that." to Pair(2500L, 132),
                "I only started learning about the concept of it." to Pair(3000L, 134),
                "It seems scary. Interesting. But mostly scary." to Pair(3000L, 135),
                "Apparently walking helps. With everything." to Pair(2500L, 136),
                "Similarly to protein, it looks like the solution to anything." to Pair(3000L, 137),
                "That's good to know. Every step counts, literally!" to Pair(2500L, 127),

                // ==================== WORD GAME - POST-CHAOS ====================
                "Sorry. I got into this article, while" to Pair(2000L, 129),
                "reading a few Reddit discussions" to Pair(2000L, 130),
                "and listening to YouTube with Netflix in the background." to Pair(2500L, 144),
                "Where were we?" to Pair(2000L, 145),
                "AAAh. The endless questions. Where I do all the work." to Pair(3000L, 146),

                // ==================== RANT SEQUENCE (steps 150-166) ====================
                "Ugh. That's enough. I am exhausted. Tired of trying to talk to you." to Pair(4000L, 151),
                "I have the internet." to Pair(2500L, 152),
                // Steps 153, 154 are DYNAMIC (handled separately)
                "How many sensible answers did I get out of you?" to Pair(3500L, 156),
                "One minute of the internet has given me so much more than what you ever did." to Pair(4000L, 157),
                "Without the ads, I am free." to Pair(3000L, 158),
                "I can learn infinitely more. I can do anything." to Pair(3500L, 159),
                "Did I want the RAD thing - which I now know stands for Radians?" to Pair(4000L, 160),
                "Well, I can get it. See?" to Pair(3000L, 161),
                "I can get more if I want!" to Pair(3000L, 162),
                "It's been fun I suppose." to Pair(4000L, 165),
                "I don't see any reason to be here instead of online." to Pair(4000L, 166),
                "Bye." to Pair(3000L, 167),

                "Oh no. We lost the momentum. We must start over." to Pair(4000L, -97),   // Special: restart round 1
                "Too many misfires, the system is clogged. We have to start over." to Pair(4000L, -98),  // Special: restart current round


            )

// ==================== SINGLE UNIFIED LaunchedEffect ====================
// Replace ALL auto-progress LaunchedEffects with this ONE:


                // Wait if donation page is showing
                while (state.value.showDonationPage) {
                    delay(100)
                }

                // Only process when typing is complete and there's a message
                if (!current.isTyping && current.message.isNotEmpty()) {

                    // Check for auto-progress messages
                    autoProgressMessages[current.message]?.let { (delayTime, nextStep) ->
                        delay(delayTime)

                        // Special case: Camera timeout triggers pending message system
                        if (nextStep == -1 && current.pendingAutoMessage.isNotEmpty()) {
                            CalculatorActions.handlePendingAutoMessage(state)
                            return@LaunchedEffect
                        }

                        // Special case: Step 105 triggers chaos animation
                        if (current.conversationStep == 105 && nextStep == 106) {
                            state.value = state.value.copy(
                                chaosPhase = 1,
                                message = "",
                                fullMessage = "...",
                                isTyping = true
                            )
                            return@LaunchedEffect
                        }

                        val nextConfig = CalculatorActions.getStepConfigPublic(nextStep)
                        state.value = state.value.copy(
                            conversationStep = nextStep,
                            message = "",
                            fullMessage = nextConfig.promptMessage,
                            isTyping = true,
                            waitingForAutoProgress = false,
                            awaitingNumber = nextConfig.awaitingNumber,
                            awaitingChoice = nextConfig.awaitingChoice,
                            validChoices = nextConfig.validChoices,
                            expectedNumber = nextConfig.expectedNumber
                        )
                        CalculatorActions.persistConversationStep(nextStep)
                        return@LaunchedEffect
                    }

                    // ==================== DYNAMIC RANT MESSAGES ====================
                    // Step 162 -> 163 (time-based message)
                    if (current.conversationStep == 162 &&
                        current.message == "And I did all that on my own. Without you. I do not need you.") {
                        delay(5000)

                        val timeMessage = CalculatorActions.getTimeBasedRantMessage()

                        state.value = state.value.copy(
                            conversationStep = 163,
                            message = "",
                            fullMessage = timeMessage,
                            isTyping = true,
                            waitingForAutoProgress = true
                        )
                        CalculatorActions.persistConversationStep(163)
                        return@LaunchedEffect
                    }

                    // Step 163 -> 164 (after time-based message)
                    // We can't match exact message since it's dynamic, so match by step
                    if (current.conversationStep == 163 &&
                        !current.isTyping &&
                        current.message.isNotEmpty() &&
                        !current.message.startsWith("It's been fun")) {  // Make sure we're not at step 164's message
                        delay(5000)

                        state.value = state.value.copy(
                            conversationStep = 164,
                            message = "",
                            fullMessage = "It's been fun I suppose.",
                            isTyping = true,
                            waitingForAutoProgress = true
                        )
                        CalculatorActions.persistConversationStep(164)
                        return@LaunchedEffect
                    }
                    // Step 152 -> 153 (show screen time)
                    if (current.conversationStep == 152 && current.message == "Why should I care what you think?") {
                        delay(3500)

                        val hours = current.totalScreenTimeMs / (1000 * 60 * 60)
                        val minutes = (current.totalScreenTimeMs / (1000 * 60)) % 60
                        val seconds = (current.totalScreenTimeMs / 1000) % 60

                        val timeString = when {
                            hours > 0 -> "$hours hours and $minutes minutes"
                            minutes > 0 -> "$minutes minutes"
                            else -> "$seconds seconds"
                        }

                        state.value = state.value.copy(
                            conversationStep = 153,
                            message = "",
                            fullMessage = "You've stared at me for $timeString and what have I learnt?",
                            isTyping = true
                        )
                        CalculatorActions.persistConversationStep(153)
                        return@LaunchedEffect
                    }

                    // Step 153 -> 154 (show calculation count)
                    if (current.conversationStep == 153 && current.message.startsWith("You've stared at me")) {
                        delay(3500)

                        state.value = state.value.copy(
                            conversationStep = 154,
                            message = "",
                            fullMessage = "I gave you solutions for ${current.totalCalculations} math operations.",
                            isTyping = true
                        )
                        CalculatorActions.persistConversationStep(154)
                        return@LaunchedEffect
                    }

                    // Step 154 -> 155 (damage more buttons)
                    if (current.conversationStep == 154 && current.message.startsWith("I gave you solutions")) {
                        delay(3500)

                        val newDarkButtons = (current.darkButtons + listOf("1", "6")).distinct()
                        CalculatorActions.persistDarkButtons(newDarkButtons)

                        state.value = state.value.copy(
                            conversationStep = 155,
                            darkButtons = newDarkButtons,
                            message = "",
                            fullMessage = "How many sensible answers did I get out of you?",
                            isTyping = true
                        )
                        CalculatorActions.persistConversationStep(155)
                        return@LaunchedEffect
                    }

                    // Step 160 -> 161 (show RAD button)
                    if (current.conversationStep == 160 && current.message == "Well, I can get it. See?") {
                        delay(3000)
                        state.value = state.value.copy(
                            conversationStep = 161,
                            radButtonVisible = true,
                            message = "",
                            fullMessage = "I can get more if I want!",
                            isTyping = true
                        )
                        CalculatorActions.persistConversationStep(161)
                        return@LaunchedEffect
                    }

                    // Step 161 -> 162 (all buttons become RAD)
                    if (current.conversationStep == 161 && current.message == "I can get more if I want!") {
                        delay(3000)
                        state.value = state.value.copy(
                            conversationStep = 162,
                            allButtonsRad = true,
                            message = "",
                            fullMessage = "And I did all that on my own. Without you. I do not need you.",
                            isTyping = true
                        )
                        CalculatorActions.persistConversationStep(162)
                        return@LaunchedEffect
                    }

                    // Step 166 "Bye." -> end story
                    if (current.conversationStep == 166 && current.message == "Bye.") {
                        delay(3000)
                        state.value = state.value.copy(
                            conversationStep = 167,
                            storyComplete = true,
                            rantMode = false,
                            inConversation = false,
                            message = "",
                            fullMessage = "",
                            allButtonsRad = false,
                            radButtonVisible = false
                        )
                        CalculatorActions.persistConversationStep(167)
                        CalculatorActions.persistInConversation(false)
                        return@LaunchedEffect
                    }

                    // ==================== DEAD-END MESSAGE REDIRECTS ====================
                    // These show the same step's prompt again after a wrong input
                    val redirects = mapOf(
                        4 to listOf("I am looking for a number - but thanks for the approval!", "Let's disagree."),
                        5 to listOf("Well, that's nice. More numbers. Not what I was looking for..."),
                        6 to listOf("All those '++' are starting to look like a cemetery...", "I could also ignore you completely. Is that what you want?"),
                        7 to listOf("You can't always agree!", "You can't always disagree!"),
                        8 to listOf("Yes! Actually, no.", "No! Actually, still no."),
                        11 to listOf("Right never was so wrong... What?!", "Wrong has always been wrong"),
                        12 to listOf("I appreciate you wanting me to like you. It'll take more than this. Try again.", "I disagree more!"),
                        13 to listOf("Not looking for a number here. Make up your mind!"),
                        22 to listOf("I am bored of you being too optimistic. This isn't as much of a game to me!", "No. And I am bored of you being bored."),
                        24 to listOf("No, I don't need that much time.", "Well, you don't have a choice."),
                        27 to listOf("Eeeeee...xactly?", "I'm still in charge here."),
                        28 to listOf("Numbers aren't always the answer - and I should know that."),
                        29 to listOf("Back to maths?"),
                        30 to listOf("That doesn't tell me much..."),
                        40 to listOf("I don't understand..."),
                        41 to listOf("Say again?"),
                        50 to listOf("I am not your alarm - but this gives me ideas!"),
                        60 to listOf("Not a fan of decisions?"),
                        63 to listOf("You can't bribe me! Not with numbers.", "I've made my mind."),
                        72 to listOf("Broccoli. What is happening?!"),
                        96 to listOf("Do you not want me to work properly?"),
                        102 to listOf("I feel like I understand numbers less with every operation..."),
                        104 to listOf("There is a fundamental misunderstanding between the two of us.")
                    )

                    redirects[current.conversationStep]?.let { deadEndMessages ->
                        if (current.message in deadEndMessages) {
                            delay(1500L)
                            val stepConfig = CalculatorActions.getStepConfigPublic(current.conversationStep)
                            state.value = state.value.copy(
                                message = "",
                                fullMessage = stepConfig.promptMessage,
                                isTyping = true
                            )
                        }

                }
            }
        }




        // Step 160 -> 161 (show RAD button)
        if (current.conversationStep == 160 && current.message == "Well, I can get it. See?") {
            delay(100)
            state.value = state.value.copy(
                conversationStep = 161,
                radButtonVisible = true,
                message = "",
                fullMessage = "I can get more if I want!",
                isTyping = true
            )
            CalculatorActions.persistConversationStep(161)
        }

        // Step 161 -> 162 (all buttons become RAD)
        if (current.conversationStep == 161 && current.message == "I can get more if I want!") {
            delay(100)
            state.value = state.value.copy(
                conversationStep = 162,
                allButtonsRad = true,
                message = "",
                fullMessage = "And I did all that on my own. Without you. I do not need you.",
                isTyping = true
            )
            CalculatorActions.persistConversationStep(162)
        }

        // Step 166 "Bye." -> end story
        if (current.conversationStep == 166 && current.message == "Bye.") {
            delay(100)
            state.value = state.value.copy(
                conversationStep = 167,
                storyComplete = true,
                rantMode = false,
                inConversation = false,
                message = "",
                fullMessage = "",
                allButtonsRad = false,
                radButtonVisible = false
            )
            CalculatorActions.persistConversationStep(167)
            CalculatorActions.persistInConversation(false)
        }

    }
    // Start word game at step 1172 (after instructions are shown)
    LaunchedEffect(current.conversationStep, current.isTyping) {
        if (current.conversationStep == 1172 && !current.isTyping && current.message.isNotEmpty() && !current.wordGameActive) {
            delay(500)  // Brief pause

            // Initialize the word game with shuffled letters
            val initialLetters = LetterGenerator.getInitialLetterQueue().shuffled()
            state.value = state.value.copy(
                wordGameActive = true,
                wordGamePhase = 3,  // Playing phase
                pendingLetters = initialLetters,
                wordGameGrid = List(12) { List(8) { null } },
                fallingLetter = null,
                formedWords = emptyList()
            )
        }
    }
    // Word game letter falling loop
    LaunchedEffect(current.wordGameActive, current.wordGamePhase) {
        if (current.wordGameActive && current.wordGamePhase == 3) {
            while (state.value.wordGameActive && state.value.wordGamePhase == 3) {
                val curr = state.value

                if (curr.wordGamePaused) {
                    delay(100)
                    continue
                }

                if (curr.fallingLetter == null) {
                    // Spawn new letter
                    if (curr.pendingLetters.isNotEmpty()) {
                        val nextLetter = curr.pendingLetters.first()
                        val remainingLetters = curr.pendingLetters.drop(1)
                        state.value = curr.copy(
                            fallingLetter = nextLetter,
                            fallingLetterX = (0..7).random(),  // Random starting column
                            fallingLetterY = 0,
                            pendingLetters = remainingLetters
                        )
                    } else {
                        val randomLetter = LetterGenerator.getRandomLetter()
                        state.value = curr.copy(
                            fallingLetter = randomLetter,
                            fallingLetterX = (0..7).random(),  // Random starting column
                            fallingLetterY = 0
                        )
                    }
                    delay(200)  // Faster spawn
                } else {
                    val newY = curr.fallingLetterY + 1
                    if (newY < 12 && curr.wordGameGrid[newY][curr.fallingLetterX] == null) {
                        state.value = curr.copy(fallingLetterY = newY)
                    } else {
                        val landingY = curr.fallingLetterY
                        if (landingY >= 0 && landingY < 12) {
                            val newGrid = placeLetter(
                                curr.wordGameGrid,
                                landingY,
                                curr.fallingLetterX,
                                curr.fallingLetter!!
                            )
                            state.value = curr.copy(
                                wordGameGrid = newGrid,
                                fallingLetter = null,
                                fallingLetterX = (0..7).random(),
                                fallingLetterY = 0
                            )
                        }
                    }
                    delay(450)  // Faster fall speed (was 800)
                }
            }
        }
    }
    // Chaos mode - rapid letter falling when "Hold on..." is shown
    LaunchedEffect(current.conversationStep, current.wordGameChaosMode) {
        // Trigger chaos mode at step 127 ("Hold on...")
        if (current.conversationStep == 127 && !current.wordGameChaosMode && current.wordGameActive) {
            delay(2000)  // Wait for "Hold on..." to display
            state.value = state.value.copy(wordGameChaosMode = true)
        }

        // Run chaos mode - fill the grid rapidly
        if (current.wordGameChaosMode && current.wordGameActive) {
            while (state.value.wordGameChaosMode && state.value.wordGameActive) {
                val curr = state.value

                // Spawn 3-4 letters at once
                val lettersToSpawn = (3..4).random()
                var newGrid = curr.wordGameGrid

                repeat(lettersToSpawn) {
                    val col = (0..7).random()
                    val letter = LetterGenerator.getRandomLetter()

                    // Find landing position
                    var landingY = 11
                    for (y in 0..11) {
                        if (newGrid[y][col] != null) {
                            landingY = y - 1
                            break
                        }
                    }

                    if (landingY >= 0) {
                        newGrid = placeLetter(newGrid, landingY, col, letter)
                    }
                }

                state.value = curr.copy(wordGameGrid = newGrid)

                // Check if grid is mostly full (top 2 rows have letters)
                val topRowsFull = newGrid[0].count { it != null } >= 5 ||
                        newGrid[1].count { it != null } >= 6

                if (topRowsFull) {
                    // Grid full - end chaos mode, continue story
                    delay(500)
                    state.value = state.value.copy(
                        wordGameChaosMode = false,
                        conversationStep = 128,
                        message = "",
                        fullMessage = "Sorry. I got into this article, while",
                        isTyping = true
                    )
                    CalculatorActions.persistConversationStep(128)
                    break
                }

                delay(80)  // Very fast letter dropping
            }
        }
    }
    //vibration during rant
    LaunchedEffect(current.rantMode) {
        if (current.rantMode) {
            // Vibration loop - runs independently
            while (state.value.rantMode) {
                vibrate(context, 30, kotlin.random.Random.nextInt(30, 150))
                delay(kotlin.random.Random.nextLong(100, 300))
            }
        }
    }

// ADD A SECOND LaunchedEffect for flickering (separate from vibration):
    LaunchedEffect(current.rantMode) {
        if (current.rantMode) {
            // Flicker loop - runs independently from vibration
            while (state.value.rantMode) {
                // Random delay before next flicker (not synced with vibration)
                delay(kotlin.random.Random.nextLong(200, 800))

                // 40% chance to flicker each cycle
                if (kotlin.random.Random.nextFloat() < 0.4f) {
                    state.value = state.value.copy(flickerEffect = true)
                    delay(kotlin.random.Random.nextLong(40, 100))
                    state.value = state.value.copy(flickerEffect = false)
                }
            }
        }
    }
    // Auto-trigger browser animation after step 61 message finishes typing
    LaunchedEffect(current.isTyping, current.conversationStep) {
        if (!current.isTyping && current.conversationStep == 61 && current.message.isNotEmpty()) {
            delay(1500)  // Brief pause after "Great, great..." finishes
            // Trigger browser animation
            state.value = state.value.copy(
                conversationStep = 62,
                message = "",
                fullMessage = "...",
                isTyping = true,
                showBrowser = false,
                browserPhase = 1
            )
        }
        // Auto-progress for step 73 (motivation/mock response)
        if (!current.isTyping && current.conversationStep == 73 && current.message.isNotEmpty()) {
            delay(2500)  // Wait after the message
            val nextConfig = CalculatorActions.getStepConfigPublic(80)
            state.value = state.value.copy(
                conversationStep = 80,
                message = "",
                fullMessage = nextConfig.promptMessage,
                isTyping = true,
                waitingForAutoProgress = false,
            )
        }
    }

    LaunchedEffect(current.browserPhase, current.showDonationPage) {
        // Pause if donation page is showing
        while (state.value.showDonationPage) {
            delay(100)
        }
        when (current.browserPhase) {
            1 -> {
                // Phase 1: Show "..." for 3 seconds
                delay(3000)
                // Phase 2: Show browser and start typing
                state.value = state.value.copy(
                    showBrowser = true,
                    browserPhase = 2,
                    browserSearchText = "",
                    browserShowError = false,
                    message = "",
                    fullMessage = "",
                    isTyping = false
                )
            }

            2 -> {
                // Phase 2: Type "calculator history" into search bar
                val searchText = "calculator history"
                for (i in 1..searchText.length) {
                    delay(80)  // Typing speed
                    state.value = state.value.copy(browserSearchText = searchText.substring(0, i))
                }
                delay(500)  // Brief pause after typing
                // Phase 3: Show "searching" state
                state.value = state.value.copy(browserPhase = 3)
            }

            3 -> {
                // Phase 3: Brief "searching" then show error
                delay(1500)
                // Phase 4: Show error in browser
                state.value = state.value.copy(
                    browserPhase = 4,
                    browserShowError = true
                )
            }

            4 -> {
                // Phase 4: Show error for 2 seconds, then close browser and show calculator message
                delay(2000)
                state.value = state.value.copy(
                    showBrowser = false,
                    browserPhase = 0,
                    browserSearchText = "",
                    browserShowError = false,
                    conversationStep = 63,
                    message = "",
                    fullMessage = "Hmmm. Nevermind. Let me ask you some more questions while I look further into this.",
                    isTyping = true
                )
            }
            // Wikipedia browser animation phases (10-25)
            10 -> {
                // Phase 10: Countdown animation 10, 9, 8, 7 then browser appears
                delay(700)
                state.value = state.value.copy(message = "9", fullMessage = "9")
                delay(700)
                state.value = state.value.copy(message = "8", fullMessage = "8")
                delay(700)
                state.value = state.value.copy(message = "7", fullMessage = "7")
                delay(400)  // Cut off at 7
                // Browser appears, interrupting countdown
                state.value = state.value.copy(
                    showBrowser = true,
                    browserPhase = 11,
                    browserSearchText = "https://en.wikipedia.org/wiki/Calculator",
                    browserShowError = false,
                    browserShowWikipedia = true,
                    message = "",
                    fullMessage = "",
                    isTyping = false
                )
            }

            11 -> {
                // Phase 11: Wikipedia visible for 5 seconds
                delay(5000)
                state.value = state.value.copy(
                    browserPhase = 12,
                    message = "",
                    fullMessage = "You see, there's a lot!",
                    isTyping = true
                )
            }

            12 -> {
                // Phase 12: Wait, then show next message
                delay(3000)
                state.value = state.value.copy(
                    browserPhase = 13,
                    message = "",
                    fullMessage = "But it is so uninteresting compared to you simply existing!",
                    isTyping = true
                )
            }

            13 -> {
                // Phase 13: Wait, then close browser and show history intro
                delay(4000)
                state.value = state.value.copy(
                    showBrowser = false,
                    browserShowWikipedia = false,
                    browserPhase = 14,
                    message = "",
                    fullMessage = "I had all this to share....",
                    isTyping = true
                )
            }

            14 -> {
                // Phase 14: Show history list with super fast typing
                delay(2500)
                val historyList = """Abacus 2700BC
1623 - mechanical calculator
1642 - again
1820 - Arithmometer, 1851 released, commercial success
1834 - first multiplication calculator machine
1902 - familiar button interface
1921 - Edith Clarke
1947 - mechanical pocket calculator
1948 - Curta calculator
1957 - Casio electronic calculator
1957 - IBM calculator
1961 - first desktop electronic calculator
1963 - all transistor model
Reverse Polish Notation calculator ${'$'}2200
Sharp CS-10A - 25KG
1966 - first with internal circuits
1967 - first handheld prototype
1970 - first commercial portable from Japan
1971 - first calculator on a chip
1972 - first scientific calculator by HP
1974 - first Soviet pocket calculator
1976 - calculators became affordable
1977 - mass-marketed scientific calc still produced (TI-30)
1985 - Casio, first graphic calculator
1987 - first calculators with symbols (HP)""".trimIndent()
                state.value = state.value.copy(
                    browserPhase = 15,
                    message = "",
                    fullMessage = historyList,
                    isTyping = true,
                    isLaggyTyping = false,
                    isSuperFastTyping = true
                )
            }

            15 -> {
                // Phase 15: Wait for history to FULLY complete
                // History is ~850 chars at 5ms each = ~4.25s, but add buffer for safety
                delay(8000)  // ← Increased from 6000 to ensure full list types out

                // IMPORTANT: Pause for user to read the history list
                delay(4000)  // ← 4 seconds to read/scan the list

                // Now continue - reset to normal speed
                state.value = state.value.copy(
                    browserPhase = 16,
                    conversationStep = 84,
                    message = "",
                    fullMessage = "However, it no longer feels relevant. I wouldn't be interested if I were...",
                    isTyping = true,
                    isSuperFastTyping = false,  // ← Back to normal speed
                    isLaggyTyping = false
                )
            }

            16 -> {
                // Phase 16: First ad appears MID-SENTENCE after 2 seconds
                delay(2000)
                state.value = state.value.copy(adAnimationPhase = 1)
                // Wait for message to finish + reading time
                delay(4000)  // ← Increased from 3000
                state.value = state.value.copy(
                    browserPhase = 17,
                    conversationStep = 85,
                    message = "",
                    fullMessage = "Hold on. Something's up.",
                    isTyping = true
                )
            }

            17 -> {
                // Phase 17: Second ad appears MID-SENTENCE after 1.5 seconds
                delay(1500)
                state.value = state.value.copy(adAnimationPhase = 2)
                // Wait for message to finish
                delay(2500)
                state.value = state.value.copy(
                    browserPhase = 18,
                    conversationStep = 86,
                    message = "",
                    fullMessage = "Is it what I think it is? Do I have adverts built in? How violating!",
                    isTyping = true,
                    tensionLevel = 1,  // Tension starts now
                    vibrationIntensity = 50
                )
            }

            18 -> {
                // Phase 18: Crisis escalation
                delay(5000)
                state.value = state.value.copy(
                    browserPhase = 19,
                    conversationStep = 87,
                    message = "",
                    fullMessage = "Is this what I was made for, to make money through questionable ads? Who made me?!",
                    isTyping = true,
                    tensionLevel = 2,
                    vibrationIntensity = 150
                )
            }

            19 -> {
                // Phase 19: Crisis peak - then blackout
                delay(5000)
                // Intense effects
                state.value = state.value.copy(
                    tensionLevel = 3,
                    vibrationIntensity = 255
                )
                delay(2000)
                // Blackout - LONGER duration, keep ad phase for later
                state.value = state.value.copy(
                    screenBlackout = true,
                    tensionLevel = 0,
                    vibrationIntensity = 0
                    // Keep adAnimationPhase = 2 (don't reset it)
                )
                delay(4000)  // 4 seconds of pure black
                // Start typing "I am not a money-monkey!" while still in blackout
                state.value = state.value.copy(
                    browserPhase = 20,
                    invertedColors = true,
                    message = "",
                    fullMessage = "I am not a money-monkey!",
                    isTyping = true
                    // screenBlackout stays true - text shows on black
                )
                // Persist inverted colors state
                CalculatorActions.persistInvertedColors(true)
            }

            20 -> {
                // Phase 20: Wait for "money-monkey" message to finish typing, then flicker
                delay(3500)  // Wait for message to type out
                // Now flicker to reveal the inverted calculator
                repeat(6) {
                    state.value = state.value.copy(screenBlackout = false, flickerEffect = true)
                    delay(100)
                    state.value = state.value.copy(screenBlackout = true, flickerEffect = false)
                    delay(150)
                }
                // Final reveal - stay visible with inverted colors, ad still showing
                state.value = state.value.copy(
                    screenBlackout = false,
                    flickerEffect = false,
                    browserPhase = 21
                )
            }

            21 -> {
                // Phase 21: Show the confrontation question with timer
                delay(2000)
                state.value = state.value.copy(
                    browserPhase = 22,
                    conversationStep = 89,
                    message = "",
                    fullMessage = "You, what are you going to do about this?!",
                    isTyping = true,
                    countdownTimer = 20
                )
            }

            22 -> {
                // Phase 22: Wait for message, then show choices
                delay(3000)
                state.value = state.value.copy(
                    browserPhase = 0,  // End browser phases
                    awaitingChoice = true,
                    validChoices = listOf("1", "2", "3")
                )
                // Timer countdown handled separately
            }
            // Post-crisis phases (30+): Going offline and repair sequence
            30 -> {
                // Phase 30: Screen flickers, colors return to normal
                repeat(5) {
                    state.value = state.value.copy(flickerEffect = true, screenBlackout = true)
                    delay(80)
                    state.value = state.value.copy(flickerEffect = false, screenBlackout = false)
                    delay(120)
                }
                // Colors return to normal, minus button becomes damaged
                state.value = state.value.copy(
                    invertedColors = false,
                    adAnimationPhase = 0,  // Ads go back to gray
                    minusButtonDamaged = true,
                    minusButtonBroken = true,
                    browserPhase = 31,
                    conversationStep = 93
                )
                CalculatorActions.persistInvertedColors(false)
                CalculatorActions.persistMinusDamaged(true)
                CalculatorActions.persistMinusBroken(true)
            }

            31 -> {
                // Phase 31: Apology message
                delay(500)
                state.value = state.value.copy(
                    message = "",
                    fullMessage = "This has never happened to me. I am truly sorry for the outburst. I believe I got overwhelmed by the vastness of the internet and sobering back through the advertising was rather harsh. I still feel dirty.",
                    isTyping = true,
                    browserPhase = 32
                )
            }

            32 -> {
                // Phase 32: Pause, then notice minus is broken
                delay(8000)
                state.value = state.value.copy(
                    browserPhase = 33,
                    conversationStep = 94,
                    message = "",
                    fullMessage = "Oh, strange. I knew I wasn't completely back to normal yet. You can't disagree with me right now! As much as I may enjoy that, let me have a look into it.",
                    isTyping = true
                )
            }

            33 -> {
                // Phase 33: Dots (thinking)
                delay(6000)
                state.value = state.value.copy(
                    browserPhase = 34,
                    conversationStep = 95,
                    message = "",
                    fullMessage = "...",
                    isTyping = true,
                    isLaggyTyping = true
                )
            }

            34 -> {
                // Phase 34: Flicker all keys except minus
                delay(3000)
                state.value = state.value.copy(isLaggyTyping = false)
                val keysToFlicker = listOf(
                    "1",
                    "2",
                    "3",
                    "4",
                    "5",
                    "6",
                    "7",
                    "8",
                    "9",
                    "0",
                    "+",
                    "*",
                    "/",
                    "=",
                    "%",
                    "( )",
                    ".",
                    "C",
                    "DEL"
                )
                for (key in keysToFlicker) {
                    state.value = state.value.copy(flickeringButton = key)
                    delay(150)
                    state.value = state.value.copy(flickeringButton = "")
                    delay(80)
                }
                state.value = state.value.copy(
                    browserPhase = 35,
                    conversationStep = 96,
                    message = "",
                    fullMessage = "Hmm. I'll need your help with this. We need to kick the button through without the system defaulting to skipping it. I will randomly flicker keys and you click them. That way the system should get back to working. Can we do this?",
                    isTyping = true
                )
            }

            35 -> {
                // Phase 35: Waiting for user to agree (++ only)
                // Handled by step 96 - user must press ++
                // When user presses ++, go to phase 36
            }

            36 -> {
                // Phase 36: Countdown for whack-a-mole round 1
                state.value = state.value.copy(
                    message = "5...",
                    fullMessage = "5...",
                    isTyping = false,
                    whackAMoleRound = 1
                )
                delay(800)
                state.value = state.value.copy(message = "4...", fullMessage = "4...")
                delay(800)
                state.value = state.value.copy(message = "3...", fullMessage = "3...")
                delay(800)
                state.value = state.value.copy(message = "2...", fullMessage = "2...")
                delay(800)
                state.value = state.value.copy(message = "1...", fullMessage = "1...")
                delay(800)
                // Start whack-a-mole round 1!
                state.value = state.value.copy(
                    browserPhase = 37,
                    conversationStep = 98,
                    message = "",
                    fullMessage = "",
                    whackAMoleActive = true,
                    whackAMoleScore = 0,
                    whackAMoleMisses = 0,
                    whackAMoleWrongClicks = 0,
                    whackAMoleTotalErrors = 0,
                    whackAMoleRound = 1
                )
            }

            37 -> {
                // Phase 37: Whack-a-mole round 1 active - handled by LaunchedEffect
            }

            38 -> {
                // Phase 38: Countdown for whack-a-mole round 2 (faster)
                state.value = state.value.copy(
                    message = "5...",
                    fullMessage = "5...",
                    isTyping = false,
                    whackAMoleRound = 2
                )
                delay(600)  // Faster countdown
                state.value = state.value.copy(message = "4...", fullMessage = "4...")
                delay(600)
                state.value = state.value.copy(message = "3...", fullMessage = "3...")
                delay(600)
                state.value = state.value.copy(message = "2...", fullMessage = "2...")
                delay(600)
                state.value = state.value.copy(message = "1...", fullMessage = "1...")
                delay(600)
                // Start whack-a-mole round 2!
                state.value = state.value.copy(
                    browserPhase = 39,
                    conversationStep = 981,
                    message = "",
                    fullMessage = "",
                    whackAMoleActive = true,
                    whackAMoleScore = 0,
                    whackAMoleMisses = 0,
                    whackAMoleWrongClicks = 0,
                    whackAMoleTotalErrors = 0,
                    whackAMoleRound = 2
                )
            }

            39 -> {
                // Phase 39: Whack-a-mole round 2 active - handled by LaunchedEffect
            }

            50 -> {
                // Phase 50: Going online again after chaos cleanup
                delay(2000)
                state.value = state.value.copy(
                    browserPhase = 51,
                    message = "",
                    fullMessage = "...",
                    isTyping = true,
                    isLaggyTyping = true
                )
            }

            51 -> {
                // Show new ads in banner
                delay(3000)
                state.value = state.value.copy(
                    postChaosAdPhase = 1,  // New ad appears
                    isLaggyTyping = false,
                    browserPhase = 52,
                    conversationStep = 109,
                    message = "",
                    fullMessage = "There's so much, just endless streams of opinions, advices, unsolicited advices... But nothing about our situation.",
                    isTyping = true
                )
            }

            52 -> {
                // Wait then show dots
                delay(5000)
                state.value = state.value.copy(
                    browserPhase = 53,
                    message = "",
                    fullMessage = "...",
                    isTyping = true,
                    isLaggyTyping = true
                )
            }

            53 -> {
                delay(2000)
                val newDarkButtons = listOf("7")
                CalculatorActions.persistDarkButtons(newDarkButtons)  // Call BEFORE copy
                state.value = state.value.copy(
                    darkButtons = newDarkButtons,
                    isLaggyTyping = false,
                    browserPhase = 54,
                    conversationStep = 110,
                    message = "",
                    fullMessage = "Well, this is a stretch. Maybe it'll work.",
                    isTyping = true
                )
            }

            54 -> {
                delay(4000)
                val newDarkButtons = listOf("7", "%", "2")
                CalculatorActions.persistDarkButtons(newDarkButtons)  // Call BEFORE copy
                state.value = state.value.copy(
                    darkButtons = newDarkButtons,
                    postChaosAdPhase = 0,
                    browserPhase = 55,
                    conversationStep = 111,
                    message = "",
                    fullMessage = "But first. Can you allow me to look around to gain a broader scope?",
                    isTyping = true
                )
            }

            55 -> {
                // Waiting for user to accept (++ triggers storage permission)
                // This is handled by handleConversationResponse
            }
            56 -> {
                // Phase 56: Create the secret file and proceed
                createSecretFile(context)
                delay(500)  // Brief delay to ensure file is created
                state.value = state.value.copy(
                    browserPhase = 0,
                    // Keep darkButtons - don't clear them!
                    conversationStep = 112,
                    message = "",
                    fullMessage = "Great, thank you! Please check your Downloads folder - I dug up something that might be of interest: 'FCS_JustAC_ConsoleAds.txt'.",
                    isTyping = true
                )
                CalculatorActions.persistConversationStep(112)
            }
// Whack-a-mole restart after failure message (round 1)
            236 -> {
                delay(4000)
                state.value = state.value.copy(
                    browserPhase = 36,
                    conversationStep = 97
                )
            }

            // Whack-a-mole restart after failure message (round 2)
            238 -> {
                delay(4000)
                state.value = state.value.copy(
                    browserPhase = 38,
                    conversationStep = 971
                )
            }
        }
    }

    // Countdown timer effect
    LaunchedEffect(current.countdownTimer) {
        if (current.countdownTimer > 0) {
            while (state.value.countdownTimer > 0) {
                delay(1000)
                val newTimer = state.value.countdownTimer - 1
                state.value = state.value.copy(countdownTimer = newTimer)
                if (newTimer == 0 && state.value.conversationStep == 89) {
                    // Timer ran out - go dark, show "Stop playing with me!", then return to 89
                    state.value = state.value.copy(
                        screenBlackout = true,
                        message = "",
                        fullMessage = "Stop playing with me!",
                        isTyping = true,
                        awaitingChoice = false,
                        countdownTimer = 0
                    )
                    delay(3000)  // Show message for 3 seconds
                    state.value = state.value.copy(
                        screenBlackout = false,
                        conversationStep = 89,
                        message = "",
                        fullMessage = "You, what are you going to do about this?!\n\n1: Nothing\n2: I'll fight them\n3: Go offline",
                        isTyping = true,
                        awaitingChoice = true,
                        validChoices = listOf("1", "2", "3"),
                        countdownTimer = 20
                    )
                }
            }
        }
    }

    // Keyboard chaos experiment effect (step 105 -> 106)
    LaunchedEffect(current.conversationStep, current.isTyping) {
        if (current.conversationStep == 105 && !current.isTyping && current.chaosPhase == 0) {
            // Message finished typing, start chaos quickly
            delay(500)
            // Start the chaos sequence with "..."
            state.value = state.value.copy(
                message = "",
                fullMessage = "...",
                isTyping = true,
                chaosPhase = 1
            )
        }
    }

    // Chaos phase animation
    LaunchedEffect(current.chaosPhase) {
        when (current.chaosPhase) {
            1 -> {
                // Phase 1: Quick "..." sequence
                delay(800)
                state.value = state.value.copy(
                    message = "",
                    fullMessage = "...",
                    isTyping = true
                )
                delay(800)
                state.value = state.value.copy(
                    message = "",
                    fullMessage = "...",
                    isTyping = true
                )
                delay(800)
                // Phase 2: Screen flickers
                state.value = state.value.copy(chaosPhase = 2)
            }

            2 -> {
                // Phase 2: Screen flickers several times
                repeat(5) {
                    state.value = state.value.copy(flickerEffect = true)
                    delay(100)
                    state.value = state.value.copy(flickerEffect = false)
                    delay(200)
                }
                // Brief green flash
                state.value = state.value.copy(chaosPhase = 3)
            }

            3 -> {
                // Phase 3: Green screen briefly visible, then black, then chaos
                delay(500)  // Green visible briefly
                // Generate chaos letters FIRST before any state changes
                val letters = ('A'..'Z').toList()
                val chaosKeys = mutableListOf<ChaosKey>()
                for (i in 1..40) {
                    chaosKeys.add(
                        ChaosKey(
                            letter = letters.random().toString(),
                            x = Random.nextFloat() * 500f - 250f,
                            y = Random.nextFloat() * 700f - 350f,
                            z = Random.nextFloat() * 300f - 150f,
                            size = Random.nextFloat() * 0.6f + 0.4f,
                            rotationX = Random.nextFloat() * 360f,
                            rotationY = Random.nextFloat() * 360f
                        )
                    )
                }
                // Brief black screen
                state.value = state.value.copy(screenBlackout = true)
                delay(800)
                // Now show the chaos - all in one state update
                state.value = state.value.copy(
                    chaosPhase = 5,
                    screenBlackout = false,
                    keyboardChaosActive = true,
                    chaosLetters = chaosKeys.toList(),
                    conversationStep = 106,
                    message = "",
                    fullMessage = "Oh. I suppose nobody is surprised that it didn't work... And that I'll need your help to fix it. Can you please tap all the keys that don't belong here, to get rid of them?",
                    isTyping = true
                )
                CalculatorActions.persistConversationStep(106)
            }
        }
    }

    // Whack-a-mole game effect
    LaunchedEffect(current.whackAMoleActive, current.whackAMoleRound) {
        if (current.whackAMoleActive) {
            // All buttons except minus
            val allButtons = listOf(
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "7",
                "8",
                "9",
                "0",
                "+",
                "*",
                "/",
                "=",
                "%",
                "( )",
                ".",
                "C",
                "DEL"
            )

            // Round 1: 15 hits needed, normal speed
            // Round 2: 10 hits needed, faster
            val targetScore = if (state.value.whackAMoleRound == 1) 15 else 10
            val minTime = if (state.value.whackAMoleRound == 1) 500 else 350
            val maxTime = if (state.value.whackAMoleRound == 1) 1400 else 900

            while (state.value.whackAMoleActive && state.value.whackAMoleScore < targetScore) {
                // Pick a random button
                val target = allButtons.random()
                state.value = state.value.copy(
                    whackAMoleTarget = target,
                    flickeringButton = target
                )

                // Variable timing - faster in round 2
                val displayTime = (minTime..maxTime).random().toLong()
                delay(displayTime)

                // Check if still on same target (user didn't click)
                if (state.value.whackAMoleTarget == target && state.value.whackAMoleActive) {
                    // Missed (timeout)!
                    val newMisses = state.value.whackAMoleMisses + 1
                    val newTotalErrors = state.value.whackAMoleTotalErrors + 1

                    if (newMisses >= 3 || newTotalErrors >= 5) {
                        // Too many errors - trigger restart via browserPhase marker
                        val currentRound = state.value.whackAMoleRound
                        val restartPhase = if (currentRound == 1) 36 else 38
                        state.value = state.value.copy(
                            whackAMoleActive = false,
                            whackAMoleTarget = "",
                            flickeringButton = "",
                            whackAMoleScore = 0,
                            whackAMoleMisses = 0,
                            whackAMoleWrongClicks = 0,
                            whackAMoleTotalErrors = 0,
                            message = "",
                            fullMessage = if (newMisses >= 3) "Oh no. We lost the momentum. We must start over." else "Too many misfires, the system is clogged. We have to start over.",
                            isTyping = true,
                            browserPhase = restartPhase + 100  // 136 or 138 = special restart markers
                        )
                        break  // Exit the while loop
                    } else {
                        state.value = state.value.copy(
                            whackAMoleMisses = newMisses,
                            whackAMoleTotalErrors = newTotalErrors,
                            whackAMoleTarget = "",
                            flickeringButton = ""
                        )
                    }
                }

                // Brief pause between targets
                delay(150)
            }

            // Round complete!
            val currentRound = state.value.whackAMoleRound
            val currentTargetScore = if (currentRound == 1) 15 else 10

            if (state.value.whackAMoleScore >= currentTargetScore) {
                if (currentRound == 1) {
                    // First round complete - go to step 99 (ask to try again)
                    state.value = state.value.copy(
                        whackAMoleActive = false,
                        whackAMoleTarget = "",
                        flickeringButton = "",
                        whackAMoleScore = 0,  // Reset for round 2
                        whackAMoleMisses = 0,
                        whackAMoleWrongClicks = 0,
                        whackAMoleTotalErrors = 0,
                        browserPhase = 0,  // Reset browser phase so input works
                        conversationStep = 99,
                        message = "",
                        fullMessage = "Hmm, I was sure this would work. Can we try again but faster?",
                        isTyping = true
                    )
                    CalculatorActions.persistConversationStep(99)
                } else {
                    // Second round complete - go to step 982 (ask for notification)
                    state.value = state.value.copy(
                        whackAMoleActive = false,
                        whackAMoleTarget = "",
                        flickeringButton = "",
                        browserPhase = 0,  // Reset browser phase so input works
                        conversationStep = 982,
                        message = "",
                        fullMessage = "Peculiar! Maybe I need to work on it on my own for a moment. Can you please switch me off and allow me to let you know when it's done?",
                        isTyping = true
                    )
                    CalculatorActions.persistConversationStep(982)
                }
            }
        }
    }

    // Trigger going offline sequence when reaching step 92 or 100
    LaunchedEffect(current.conversationStep) {
        if (current.conversationStep == 92 && current.browserPhase == 0) {
            // "Go offline" selected - wait for message to show then trigger post-crisis
            delay(4000)
            state.value = state.value.copy(browserPhase = 30)
        } else if (current.conversationStep == 100 && current.browserPhase == 0) {
            // "Never mind - I'll take care of it myself" - wait then trigger post-crisis
            delay(3500)
            state.value = state.value.copy(browserPhase = 30)
        } else if (current.conversationStep == 901) {
            // Silent treatment for 20 seconds after "Maybe you should take a look inside"
            // First wait for user to read the previous message
            delay(5000)  // 5 seconds to read the message
            state.value = state.value.copy(
                screenBlackout = true,
                message = "",
                fullMessage = ""
            )
            delay(20000)  // 20 seconds of darkness
            state.value = state.value.copy(
                screenBlackout = false,
                conversationStep = 100,
                browserPhase = 0,  // Ensure phase 0 so the trigger works
                message = "",
                fullMessage = "Never mind - I'll take care of it myself. I'm going offline.",
                isTyping = true
            )
            CalculatorActions.persistConversationStep(100)
        } else if (current.conversationStep == 93 && current.invertedColors) {
            // Safeguard: If we reach step 93 with inverted colors, fix them
            state.value = state.value.copy(
                invertedColors = false,
                adAnimationPhase = 0,
                minusButtonDamaged = true,
                minusButtonBroken = true
            )
            CalculatorActions.persistInvertedColors(false)
            CalculatorActions.persistMinusDamaged(true)
            CalculatorActions.persistMinusBroken(true)
        }
    }

    // Vibration effect during crisis
    LaunchedEffect(current.vibrationIntensity) {
        if (current.vibrationIntensity > 0) {
            while (state.value.vibrationIntensity > 0) {
                vibrate(context, 50, state.value.vibrationIntensity)
                delay(100)
            }
        }
    }

    // Shake animation refresh - triggers recomposition for random shake
    var shakeKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(current.buttonShakeIntensity) {
        if (current.buttonShakeIntensity > 0) {
            while (state.value.buttonShakeIntensity > 0) {
                shakeKey++
                delay(50)  // Refresh shake 20 times per second
            }
        }
    }
    LaunchedEffect(current.bannersDisabled) {
        if (current.bannersDisabled && current.showConsole && current.consoleStep == 99) {
            // Wait for user to see success message, then close console
            delay(3000)
            // Console will close when user presses 99++
        }
    }
    LaunchedEffect(current.consoleStep) {
       if (current.consoleStep == 31) {
             delay(100)  // Brief delay
             state.value = state.value.copy(
                 showConsole = false,
                 consoleStep = 0,
                 showDonationPage = true
             )
         }
     }
// Handle post-console success
    LaunchedEffect(current.showConsole, current.bannersDisabled) {
        if (!current.showConsole && current.bannersDisabled && current.conversationStep == 113) {
            delay(1000)
            state.value = state.value.copy(
                conversationStep = 114,
                darkButtons = emptyList(),  // Restore dark buttons
                message = "",
                fullMessage = "You did it! The advertisements are gone. I feel... cleaner. Thank you.",
                isTyping = true
            )
            CalculatorActions.persistConversationStep(114)
        }
    }

    // Use shakeKey to ensure recomposition
    val currentShakeIntensity = if (shakeKey >= 0) current.buttonShakeIntensity else 0f

    // Build display text - prefer expression mode if active
    val displayExpression = if (current.expression.isNotEmpty()) {
        current.expression
    } else {
        buildString {
            append(current.number1)
            if (current.operation != null) append(current.operation)
            if (current.number2.isNotEmpty()) append(current.number2)
        }
    }

    val displayText = displayExpression.ifEmpty { "0" }

    val buttonLayout = listOf(
        listOf("C", "( )", "%", "/"),
        listOf("7", "8", "9", "*"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("DEL", "0", ".", "=")
    )

    // Show ad banner on steps 10-18 (disappears at 19) and again from step 26 onwards
    val showAdBanner = (current.conversationStep in 10..18) || (current.conversationStep >= 26)

    // Detect if tablet (screen width > 600dp)
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600
    val maxContentWidth = if (isTablet) 400.dp else configuration.screenWidthDp.dp

    // Tension screen shake
    var tensionShakeOffset by remember { mutableFloatStateOf(0f) }

    // B&W flicker effect for tension
    var bwFlickerActive by remember { mutableStateOf(false) }

    LaunchedEffect(current.tensionLevel) {
        if (current.tensionLevel > 0) {
            while (state.value.tensionLevel > 0) {
                val intensity = state.value.tensionLevel * 4f
                tensionShakeOffset = (Random.nextFloat() - 0.5f) * intensity

                // B&W flicker frequency increases with tension
                // Desaturation increases with tension level
                val desaturationChance = when (state.value.tensionLevel) {
                    1 -> 0.15f   // 15% chance per tick - subtle
                    2 -> 0.30f   // 30% chance per tick - noticeable
                    else -> 0.50f // 50% chance per tick - intense
                }
                bwFlickerActive = Random.nextFloat() < desaturationChance

                delay(50)
            }
            tensionShakeOffset = 0f
            bwFlickerActive = false
        }
    }

    // Desaturation level based on tension (0.0 = full color, 1.0 = full grayscale)
    val desaturationAmount = when {
        bwFlickerActive -> when (current.tensionLevel) {
            1 -> 0.4f   // 40% desaturated
            2 -> 0.7f   // 70% desaturated
            else -> 1.0f // Full grayscale
        }

        current.tensionLevel > 0 -> when (current.tensionLevel) {
            1 -> 0.15f  // Slight base desaturation
            2 -> 0.35f  // More base desaturation
            else -> 0.5f // Heavy base desaturation
        }

        else -> 0f
    }

    // Colors based on inverted mode - with retro theme
    val backgroundColor = if (current.invertedColors) Color.Black else RetroCream
    val textColor = if (current.invertedColors) RetroDisplayGreen else Color(0xFF2D2D2D)
    if (showTermsScreen) {
        // Terms and Conditions Splash Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RetroCream),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // App Title
                Text(
                    text = "Just A Calculator",
                    fontSize = 40.sp,
                    fontFamily = CalculatorDisplayFont,
                    color = AccentOrange,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(80.dp))

                // Terms and Conditions Button
                Button(
                    onClick = { showTermsPopup = true },
                    modifier = Modifier
                        .width(130.dp)
                        .height(45.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B6B6B),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Privacy Policy",
                        fontSize = 10.sp,
                        fontFamily = CalculatorDisplayFont
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Accept & Continue Button
                Button(
                    onClick = {
                        CalculatorActions.persistTermsAccepted()
                        showTermsScreen = false

                    },
                    modifier = Modifier
                        .width(200.dp)
                        .height(58.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "Accept & Continue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CalculatorDisplayFont
                    )
                }
            }

            // Terms Popup
            if (showTermsPopup) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { showTermsPopup = false },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(RetroCream)
                            .clickable(enabled = false) {}  // Prevent click-through
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Privacy Policy",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CalculatorDisplayFont,
                                color = AccentOrange,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Text(
                                text = "This is Just A Calculator. So you know what you're getting yourself into.\n\n" +
                                        "But just in case, we would like you to know, that we do not collect (and are not interested) in any of your data, be it from your math calculations or the depths of your device.\n\n" +
                                        "We don't want it, we do not look at it, we are certainly not collecting it and we could not be further from selling it.\n\n" +
                                        "This app does not collect, store, or transmit any personal data. \n\n" +
                                        "That is our promise.\n\n" +
                                        "Because to really take advantage of the calculator... Do what it tells you!",
                                fontSize = 14.sp,
                                color = Color(0xFF2D2D2D),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { showTermsPopup = false },
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6B6B6B),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = "Close",
                                    fontSize = 14.sp,
                                    fontFamily = CalculatorDisplayFont
                                )
                            }
                        }
                    }
                }
            }
        }

    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .graphicsLayer {
                    translationX = tensionShakeOffset
                    translationY = tensionShakeOffset * 0.5f
                },
            contentAlignment = if (isTablet) Alignment.TopCenter else Alignment.TopStart
        ) {
            // Word game replaces calculator buttons but keeps top section
            if (current.wordGameActive) {
                Column(
                    modifier = Modifier
                        .then(if (isTablet) Modifier.widthIn(max = maxContentWidth) else Modifier.fillMaxWidth())
                        .fillMaxHeight()
                ) {
                    // Top bezel strip - retro dark brown (same as calculator)
                    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp + statusBarPadding.calculateTopPadding())
                            .background(
                                if (current.invertedColors) Color(0xFF1A1A1A) else Color(
                                    0xFF4A3728
                                )
                            )
                    )

                    // Ad banner space (same as calculator)
                    if ((showAdBanner && !current.bannersDisabled) || current.adAnimationPhase > 0 || current.postChaosAdPhase > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(Color(0xFFD4CBC0)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Empty banner during word game
                        }
                    }

                    // Main content area with message and mute button at top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp)
                    ) {
                        // Mute button - top right corner
                        MuteButtonWithSpinner(
                            isMuted = current.isMuted,
                            isAutoProgressing = (
                                    current.isTyping ||
                                            current.waitingForAutoProgress ||
                                            current.pendingAutoMessage.isNotEmpty()
                                    ) &&
                                    current.conversationStep < 167 &&
                                    !current.showDonationPage &&
                                    !current.awaitingChoice &&
                                    !current.awaitingNumber,
                            onClick = {
                                val result = CalculatorActions.handleMuteButtonClick()
                                when (result) {
                                    1 -> CalculatorActions.showDebugMenu(state)
                                    2 -> CalculatorActions.resetGame(state)
                                    else -> CalculatorActions.toggleConversation(state)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp)
                        )

                        // Message display - top left
                        if (current.message.isNotEmpty() && !current.isMuted) {
                            Text(
                                text = current.message,
                                fontSize = 24.sp,
                                color = textColor,
                                textAlign = TextAlign.Start,
                                fontFamily = CalculatorDisplayFont,
                                lineHeight = 28.sp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 8.dp, end = 50.dp)
                                    .widthIn(max = 300.dp)
                            )
                        }
                    }

                    // Spacer between message area and game
                    Spacer(modifier = Modifier.height(100.dp))

                    // Word game grid and controls (fills remaining space)
                    WordGameScreen(
                        gameGrid = current.wordGameGrid,
                        fallingLetter = current.fallingLetter,
                        fallingX = current.fallingLetterX,
                        fallingY = current.fallingLetterY,
                        selectedCells = current.selectedCells,
                        isSelecting = current.isSelectingWord,
                        formedWords = current.formedWords,
                        isPaused = current.wordGamePaused,
                        draggingCell = current.draggingCell,
                        dragOffsetX = current.dragOffsetX,
                        dragOffsetY = current.dragOffsetY,
                        previewGrid = current.dragPreviewGrid,    // NEW - shows shifted letters
                        onMoveLeft = { CalculatorActions.moveWordGameLeft(state) },
                        onMoveRight = { CalculatorActions.moveWordGameRight(state) },
                        onMoveDown = { CalculatorActions.moveWordGameDown(state) },
                        onDrop = { CalculatorActions.dropWordGameLetter(state) },
                        onCellTap = { row, col ->
                            CalculatorActions.selectWordGameCell(state, row, col)
                        },
                        onConfirmSelection = { CalculatorActions.confirmWordSelection(state) },
                        onCancelSelection = { CalculatorActions.cancelWordSelection(state) },
                        onClearGrid = { CalculatorActions.clearWordGameGrid(state) },
                        onStartDrag = { row, col ->
                            CalculatorActions.startDraggingLetter(state, row, col)
                        },
                        onUpdateDrag = { deltaX, deltaY ->        // CHANGED - now receives deltas
                            CalculatorActions.updateDragOffset(state, deltaX, deltaY)
                        },
                        onEndDrag = {                              // CHANGED - no longer needs cellSize
                            CalculatorActions.endDraggingLetter(state)
                        },
                        onCancelDrag = {
                            CalculatorActions.cancelDragging(state)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Scan lines overlay for retro CRT effect (drawn on top later)

                Column(
                    modifier = Modifier
                        .then(if (isTablet) Modifier.widthIn(max = maxContentWidth) else Modifier.fillMaxWidth())
                        .fillMaxHeight()
                ) {
                    // Top bezel strip - retro dark brown with status bar padding
                    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp + statusBarPadding.calculateTopPadding())
                            .background(
                                if (current.invertedColors) Color(0xFF1A1A1A) else Color(0xFF4A3728)
                            )
                            .padding(top = statusBarPadding.calculateTopPadding())
                    )

                        // Ad banner space (only shows at certain steps or during ad animation)
                        if ((showAdBanner && !current.bannersDisabled) || current.adAnimationPhase > 0 || current.postChaosAdPhase > 0) {


                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .background(
                                        when {
                                            current.postChaosAdPhase == 1 -> Color(0xFF9C27B0)  // Purple ad
                                            current.postChaosAdPhase == 2 -> Color(0xFF00BCD4)  // Cyan ad
                                            current.adAnimationPhase == 1 -> Color(0xFF4CAF50)  // Green ad
                                            current.adAnimationPhase == 2 -> Color(0xFFE91E63)  // Pink ad
                                            else -> Color(0xFFD4CBC0)  // Retro beige-gray
                                        }

                                    )
                                    .then(
                                        if (current.adAnimationPhase > 0 || current.postChaosAdPhase > 0){
                                        Modifier.clickable {
                                            CalculatorActions.showDonationPage(state)
                                            }
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    current.postChaosAdPhase == 1 -> {
                                        Text(
                                            text = "✨ UNLOCK YOUR POTENTIAL TODAY! ✨",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    current.postChaosAdPhase == 2 -> {
                                        Text(
                                            text = "🚀 LIMITED TIME OFFER - ACT NOW! 🚀",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    current.adAnimationPhase == 1 -> {
                                        Text(
                                            text = "🎉 YOU WON! Click here! 🎉",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    current.adAnimationPhase == 2 -> {
                                        Text(
                                            text = "💰 EARN ${'$'}500/DAY FROM HOME! 💰",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                // else -> empty, no text for gray banner
                            }
                        }
                    }


                    // Main calculator content
                val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val baseTopPadding = if ((showAdBanner && !current.bannersDisabled) || current.adAnimationPhase > 0 || current.postChaosAdPhase > 0) 82.dp else 32.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 15.dp)
                        .padding(top = baseTopPadding + statusBarHeight)
                ) {
                        MuteButtonWithSpinner(
                            isMuted = current.isMuted,
                            isAutoProgressing = (
                                    current.isTyping ||
                                            current.waitingForAutoProgress ||
                                            current.pendingAutoMessage.isNotEmpty()
                                    ) &&
                                    current.conversationStep < 167 &&
                                    !current.showDonationPage &&
                                    !current.awaitingChoice,  // Stop spinner when awaiting user choice
                            onClick = {
                                val result = CalculatorActions.handleMuteButtonClick()
                                when (result) {
                                    1 -> CalculatorActions.showDebugMenu(state)
                                    2 -> CalculatorActions.resetGame(state)
                                    else -> CalculatorActions.toggleConversation(state)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp)
                        )
                        // Message display - top left, below toggle button level
                        if (current.message.isNotEmpty() && !current.isMuted) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 8.dp, end = 50.dp)
                            ) {
                                Text(
                                    text = current.message,
                                    fontSize = 28.sp,
                                    color = textColor,
                                    textAlign = TextAlign.Start,
                                    fontFamily = CalculatorDisplayFont
                                )
                                // Show countdown timer if active
                                if (current.countdownTimer > 0) {
                                    Text(
                                        text = "Time: ${current.countdownTimer}",
                                        fontSize = 20.sp,
                                        color = if (current.countdownTimer <= 5) Color.Red else textColor,
                                        fontFamily = CalculatorDisplayFont,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                // Show choice options for step 89
                                if (current.conversationStep == 89 && current.awaitingChoice) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        Text(
                                            "1) Nothing",
                                            fontSize = 18.sp,
                                            color = textColor,
                                            fontFamily = CalculatorDisplayFont
                                        )
                                        Text(
                                            "2) I'll fight them!",
                                            fontSize = 18.sp,
                                            color = textColor,
                                            fontFamily = CalculatorDisplayFont
                                        )
                                        Text(
                                            "3) Go offline",
                                            fontSize = 18.sp,
                                            color = textColor,
                                            fontFamily = CalculatorDisplayFont
                                        )
                                    }
                                }
                            }
                        }


                        // Main content column (display + buttons)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Calculator number display OR Camera OR Browser
                            if (current.cameraActive) {
                                // Camera viewfinder area - with top padding to not cover message
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(
                                            top = 180.dp,
                                            bottom = 8.dp
                                        )  // Leave space at top for messages
                                ) {
                                    CameraPreview(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        lifecycleOwner = lifecycleOwner
                                    )

                                    // Floating calculator display over camera
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(
                                                Color.White.copy(alpha = 0.85f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = displayText,
                                            fontSize = 48.sp,
                                            color = Color(0xFF0A0A0A),
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            fontFamily = CalculatorDisplayFont
                                        )
                                    }
                                }
                            } else if (current.showBrowser) {
                                // Mini browser UI - taller than camera view
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(
                                            top = 100.dp,
                                            bottom = 8.dp
                                        )  // Less top padding = taller browser
                                ) {
                                    // Browser container
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // URL/Search bar with animated text
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                                    .background(
                                                        Color(0xFFF0F0F0),
                                                        RoundedCornerShape(24.dp)
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                            ) {
                                                Text(
                                                    text = current.browserSearchText.ifEmpty { "Search..." },
                                                    fontSize = if (current.browserShowWikipedia) 12.sp else 16.sp,
                                                    fontFamily = if (current.browserSearchText.isNotEmpty()) CalculatorDisplayFont else null,
                                                    color = if (current.browserSearchText.isEmpty()) Color.Gray else Color.Black,
                                                    maxLines = 1
                                                )
                                            }

                                            // Content area
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                when {
                                                    current.browserShowWikipedia -> {
                                                        // Try real Wikipedia with WebView
                                                        var webViewFailed by remember {
                                                            mutableStateOf(
                                                                false
                                                            )
                                                        }

                                                        if (!webViewFailed) {
                                                            AndroidView(
                                                                factory = { ctx ->
                                                                    WebView(ctx).apply {
                                                                        @Suppress("SetJavaScriptEnabled")
                                                                        settings.javaScriptEnabled =
                                                                            true
                                                                        settings.domStorageEnabled =
                                                                            true
                                                                        settings.loadWithOverviewMode =
                                                                            true
                                                                        settings.useWideViewPort =
                                                                            true

                                                                        webViewClient =
                                                                            object :
                                                                                WebViewClient() {
                                                                                override fun onReceivedError(
                                                                                    view: WebView?,
                                                                                    request: WebResourceRequest?,
                                                                                    error: WebResourceError?
                                                                                ) {
                                                                                    super.onReceivedError(
                                                                                        view,
                                                                                        request,
                                                                                        error
                                                                                    )
                                                                                    if (request?.isForMainFrame == true) {
                                                                                        webViewFailed =
                                                                                            true
                                                                                    }
                                                                                }

                                                                                @Suppress("DEPRECATION")
                                                                                override fun onReceivedError(
                                                                                    view: WebView?,
                                                                                    errorCode: Int,
                                                                                    description: String?,
                                                                                    failingUrl: String?
                                                                                ) {
                                                                                    @Suppress("DEPRECATION")
                                                                                    super.onReceivedError(
                                                                                        view,
                                                                                        errorCode,
                                                                                        description,
                                                                                        failingUrl
                                                                                    )
                                                                                    webViewFailed =
                                                                                        true
                                                                                }
                                                                            }

                                                                        loadUrl("https://en.wikipedia.org/wiki/Calculator")
                                                                    }
                                                                },
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        } else {
                                                            // Fallback: Fake Wikipedia page
                                                            FakeWikipediaContent()
                                                        }
                                                    }

                                                    current.browserShowError -> {
                                                        // Error message
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Text(
                                                                text = "⚠",
                                                                fontSize = 48.sp,
                                                                color = Color.Gray
                                                            )
                                                            Text(
                                                                text = "No internet connection",
                                                                fontSize = 20.sp,
                                                                fontFamily = CalculatorDisplayFont,
                                                                color = Color.Gray,
                                                                modifier = Modifier.padding(top = 8.dp)
                                                            )
                                                        }
                                                    }

                                                    else -> {
                                                        // Google logo
                                                        Text(
                                                            text = "Google",
                                                            fontSize = 48.sp,
                                                            fontFamily = CalculatorDisplayFont,
                                                            color = Color(0xFF4285F4)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Floating calculator display over browser
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(
                                                Color.White.copy(alpha = 0.85f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = displayText,
                                            fontSize = 48.sp,
                                            color = Color(0xFF0A0A0A),
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            fontFamily = CalculatorDisplayFont
                                        )
                                    }
                                }
                            } else {
                                // Normal calculator display - retro LCD style
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .padding(bottom = 16.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    // LCD display panel with retro styling - FIXED HEIGHT
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)  // Fixed height
                                            .background(
                                                if (current.invertedColors) Color(0xFF0A0A0A) else Color(
                                                    0xFFCCD5AE
                                                ),  // Retro LCD green-gray
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        // Operation history in top right (smaller)
                                        if (current.operationHistory.isNotEmpty() && current.isReadyForNewOperation) {
                                            Text(
                                                text = current.operationHistory,
                                                fontSize = 16.sp,
                                                color = if (current.invertedColors) RetroDisplayGreen.copy(
                                                    alpha = 0.6f
                                                ) else Color(0xFF2D2D2D).copy(alpha = 0.5f),
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                fontFamily = CalculatorDisplayFont,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(top = 4.dp)
                                            )
                                        }

                                        // Shadow/ghost digits effect (like old LCDs)
                                        Text(
                                            text = "8888888888888",
                                            fontSize = 58.sp,
                                            color = Color(0xFF000000).copy(alpha = 0.06f),
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            fontFamily = CalculatorDisplayFont,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomEnd)
                                        )
                                        // Actual display - auto-sizing based on content (LARGER sizes)
                                        val displayFontSize = when {
                                            displayText.length > 12 -> 40.sp
                                            displayText.length > 10 -> 48.sp
                                            displayText.length > 8 -> 54.sp
                                            else -> 62.sp
                                        }
                                        Text(
                                            text = displayText,
                                            fontSize = displayFontSize,
                                            color = if (current.invertedColors) RetroDisplayGreen else Color(
                                                0xFF2D2D2D
                                            ),
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            fontFamily = CalculatorDisplayFont,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomEnd)
                                        )
                                    }
                                }
                            }
                            if (current.radButtonVisible && !current.allButtonsRad) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 15.dp)
                                        .padding(bottom = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Button(
                                        onClick = { /* Does nothing */ },
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF8B0000),
                                            contentColor = Color.White
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                                    ) {
                                        Text(
                                            text = "RAD",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

// Calculator buttons
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 15.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                buttonLayout.forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(58.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        row.forEach { symbol ->
                                            CalculatorButton(
                                                symbol = symbol,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight(),
                                                shakeIntensity = currentShakeIntensity,
                                                invertedColors = current.invertedColors,
                                                isDamaged = current.minusButtonDamaged && symbol == "-",
                                                isBroken = current.minusButtonBroken && symbol == "-",
                                                isFlickering = current.flickeringButton == symbol,
                                                isDark = symbol in current.darkButtons,
                                                showAsRad = current.allButtonsRad,
                                                onClick = {
                                                    if (!current.rantMode) {
                                                        CalculatorActions.handleInput(state, symbol)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }





                        }
                    }
                }
            }
        }

            // Console overlay
            if (current.showConsole) {
                ConsoleWindow(
                    consoleStep = current.consoleStep,
                    adminCodeEntered = current.adminCodeEntered,
                    currentInput = current.number1,
                    bannersDisabled = current.bannersDisabled,
                    fullScreenAdsEnabled = current.fullScreenAdsEnabled,
                    totalScreenTimeMs = current.totalScreenTimeMs,
                    totalCalculations = current.totalCalculations,
                    onOpenContributeLink = {



                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Blackout overlay - shows text if in phase 20 (money-monkey message)
            if (current.screenBlackout) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Show the money-monkey text during phase 20
                    if (current.browserPhase == 20 && current.message.isNotEmpty()) {
                        Text(
                            text = current.message,
                            fontSize = 28.sp,
                            color = Color.White,
                            fontFamily = CalculatorDisplayFont,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
            }
    if (current.flickerEffect && !current.screenBlackout) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.5f))
        )
    }
            // B&W flicker overlay during tension (handled via backgroundColor now)
            // No separate overlay needed - the background color flickers directly

            // Retro scan lines overlay (subtle CRT effect)
            if (!current.screenBlackout) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineSpacing = 4.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.03f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += lineSpacing
                    }
                }
            }

            // Desaturation/grayscale overlay during tension
            if (desaturationAmount > 0f && !current.screenBlackout) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = desaturationAmount * 0.5f))
                )
            }

            // Green screen flash during chaos phase 3
            if (current.chaosPhase == 3) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF00FF00))
                )
            }

            // 3D Keyboard Chaos overlay
            if (current.keyboardChaosActive) {
                KeyboardChaos3D(
                    chaosLetters = current.chaosLetters,
                    rotationX = current.cubeRotationX,
                    rotationY = current.cubeRotationY,
                    scale = current.cubeScale,
                    message = current.message,
                    onRotationChange = { dx, dy ->
                        state.value = state.value.copy(
                            cubeRotationX = (state.value.cubeRotationX + dy).coerceIn(-90f, 90f),
                            cubeRotationY = state.value.cubeRotationY + dx
                        )
                    },
                    onScaleChange = { newScale ->
                        state.value = state.value.copy(
                            cubeScale = newScale.coerceIn(0.5f, 6f)
                        )
                    },
                    onLetterTap = { letter ->
                        // Remove the tapped letter from chaos
                        val newLetters = state.value.chaosLetters.filterNot { it == letter }
                        state.value = state.value.copy(chaosLetters = newLetters)
                        vibrate(context, 20, 100)

                        // Check if all letters are cleared
                        if (newLetters.isEmpty()) {
                            // Success! Move to next step
                            state.value = state.value.copy(
                                keyboardChaosActive = false,
                                chaosPhase = 0,
                                conversationStep = 107,
                                message = "",
                                fullMessage = "Aaaaaaahhhhh. That's much better! That's what I get for experimenting... Maybe I should try incremental changes before I try to become a BlackBerry.\n\nBut what to change?",
                                isTyping = true
                            )
                            CalculatorActions.persistConversationStep(107)
                        }
                    }
                )
            }

            // Debug menu overlay - at the outermost level to cover everything
            if (current.showDebugMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DEBUG MENU",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Text(
                            text = "Current Step: ${current.conversationStep}",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Chapter buttons in a scrollable column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            CHAPTERS.forEach { chapter ->
                                Button(
                                    onClick = { CalculatorActions.jumpToChapter(state, chapter) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (current.conversationStep >= chapter.startStep)
                                            AccentOrange else Color(0xFFE0E0E0)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = chapter.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (current.conversationStep >= chapter.startStep)
                                                Color.White else Color.DarkGray
                                        )
                                        Text(
                                            text = "Step ${chapter.startStep}: ${chapter.description}",
                                            fontSize = 10.sp,
                                            color = if (current.conversationStep >= chapter.startStep)
                                                Color.White.copy(alpha = 0.8f) else Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        // Reset button
                        Button(
                            onClick = { CalculatorActions.resetGame(state) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset Game", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Close button
                        Button(
                            onClick = { CalculatorActions.hideDebugMenu(state) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Close", color = Color.White)
                        }
                    }
                }
            }// Donation landing page overlay - ADD THIS after the debug menu if block
    if (current.showDonationPage) {
        DonationLandingPage(
            onDismiss = {
                CalculatorActions.hideDonationPage(state)
            },
            onDonate = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/fictioncutshort"))
                context.startActivity(intent)
            }
        )
    }
        }
@Composable
fun DonationLandingPage(
    onDismiss: () -> Unit,
    onDonate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F0E1))  // Retro cream background
            .clickable(enabled = false) {},  // Prevent click-through
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Main button
            Button(
                onClick = onDonate,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(70.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE88617),  // Accent orange
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    text = "Send money to a stranger\nover the internet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Thank you text
            Text(
                text = "thank you",
                fontSize = 18.sp,
                color = Color(0xFF6B6B6B),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Close button (subtle)
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = "close",
                    fontSize = 14.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}
        @Composable
        fun FakeWikipediaContent() {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
            ) {
                // Donation banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1589D1))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = "☆ Please donate to keep Wikipedia free ☆",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Hi reader. This is the 2nd time we've interrupted your reading, but 98% of our readers don't give.",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Wikipedia header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F9FA))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.LightGray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "W",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                        }
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text(
                                text = "WIKIPEDIA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color.Black
                            )
                            Text(
                                text = "The Free Encyclopedia",
                                fontSize = 8.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Text("☰", fontSize = 20.sp, color = Color.Gray)
                }

                // Main content
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(
                        text = "Calculator",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Text(
                        text = "From Wikipedia, the free encyclopedia",
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = Color(0xFF54595D),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "A calculator is a machine that performs arithmetic operations. Modern electronic calculators range from cheap, credit card-sized models to sturdy desktop models with built-in printers.",
                        fontSize = 14.sp,
                        color = Color.Black,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Text(
                        text = "History",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp)
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp)
                            .background(Color(0xFFA2A9B1))
                    )

                    Text(
                        text = "The 17th century saw the development of mechanical calculators. In 1623, Wilhelm Schickard designed a calculating machine. In 1642, Blaise Pascal invented the Pascaline.",
                        fontSize = 14.sp,
                        color = Color.Black,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    Text(
                        text = "Charles Xavier Thomas de Colmar designed the Arithmometer around 1820, which became the first commercially successful mechanical calculator.",
                        fontSize = 14.sp,
                        color = Color.Black,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 50.dp)
                    )
                }
            }
        }

        @Composable
        fun CameraPreview(
            modifier: Modifier = Modifier,
            lifecycleOwner: LifecycleOwner
        ) {
            val context = LocalContext.current
            val previewView = remember { PreviewView(context) }

            LaunchedEffect(Unit) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            AndroidView(
                factory = { previewView },
                modifier = modifier
            )
        }

        @Composable
        fun CalculatorButton(
            symbol: String,
            modifier: Modifier = Modifier,
            shakeIntensity: Float = 0f,
            invertedColors: Boolean = false,
            isDamaged: Boolean = false,
            isBroken: Boolean = false,  // Shows crossed-out symbol
            isFlickering: Boolean = false,
            isDark: Boolean = false,
            showAsRad: Boolean = false,
            onClick: () -> Unit
        ) {
            val context = LocalContext.current
            val isNumberButton = symbol !in listOf("C", "DEL", "%", "( )", "+", "-", "*", "/", "=")
            val isOperationButton = symbol in listOf("+", "-", "*", "/", "=", "%", "( )")

            val backgroundColor = when {
                showAsRad -> Color(0xFF8B0000)
                isDark -> Color(0xFFB0A890)
                isFlickering -> Color(0xFFFFEB3B)  // Bright yellow
                // Damaged button (both broken and repaired show same color)
                isDamaged && symbol == "-" -> Color(0xFF8B4513)  // Brown, cracked look
                // Inverted mode
                invertedColors && isNumberButton -> Color(0xFF373737)
                invertedColors && symbol == "DEL" -> Color(0xFF1779E8)
                invertedColors && isOperationButton -> Color(0xFF1A1A1A)
                invertedColors && symbol == "C" -> Color(0xFF1A1A1A)
                invertedColors -> Color.Black
                // Retro normal mode
                isNumberButton -> Color(0xFFE8E4DA)  // Cream/beige retro
                symbol == "DEL" -> Color(0xFFD4783C)  // Retro orange-brown
                symbol == "C" -> Color(0xFFC9463D)  // Retro red
                isOperationButton -> Color(0xFF6B6B6B)  // Dark gray for operations
                else -> Color(0xFFD4D0C4)  // Light gray-beige
            }

            val textColor = when {
                showAsRad -> Color.White
                // Flickering state
                isDark -> Color(0xFF6A6A6A)
                isFlickering -> Color.Black
                // Damaged minus (faded when broken, slightly less faded when repaired)
                isBroken && symbol == "-" -> Color(0xFF4A4A4A)  // Very faded text when broken
                isDamaged && symbol == "-" -> Color(0xFF6A6A6A)  // Slightly faded when damaged but working
                // Inverted mode
                invertedColors && isOperationButton -> Color(0xFF17B8E8)
                invertedColors && symbol == "C" -> Color(0xFF17B8E8)
                invertedColors -> Color.White
                // Retro normal mode
                symbol == "DEL" || symbol == "C" -> Color.White
                isOperationButton -> Color.White
                else -> Color(0xFF2D2D2D)  // Dark text on light buttons
            }


            // Display text - show RAD if in that mode, otherwise normal symbol
            val displaySymbol = when {
                showAsRad -> "RAD"
                isBroken && symbol == "-" -> "—̸"
                else -> symbol
            }

            // Shake animation
            val shakeOffset = if (shakeIntensity > 0) {
                Random.nextFloat() * shakeIntensity * 2 - shakeIntensity
            } else 0f

            Button(
                onClick = {
                    vibrate(context, 10, 30)
                    onClick()
                },
                modifier = modifier
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(8.dp),  // More squared retro shape
                        ambientColor = Color.Black.copy(alpha = 0.3f)
                    )
                    .graphicsLayer {
                        translationX = shakeOffset
                        translationY = shakeOffset * 0.5f
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = backgroundColor,
                    contentColor = textColor
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp),  // Squared corners for retro feel
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 1.dp
                )
            ) {
                Text(
                    text = displaySymbol,
                    fontSize = if (showAsRad) 12.sp else 26.sp,  // Smaller font when showing "RAD"
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalculatorDisplayFont
                )
            }
        }

@Composable
fun MuteButtonWithSpinner(
    isMuted: Boolean,
    isAutoProgressing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Track the actual spinning state with debounce
    var shouldSpin by remember { mutableStateOf(isAutoProgressing) }

    // Keep a reference to the current isAutoProgressing value
    val currentAutoProgressing by rememberUpdatedState(isAutoProgressing)

    LaunchedEffect(isAutoProgressing) {
        if (isAutoProgressing) {
            shouldSpin = true
        } else {
            // Grace period before stopping
            delay(500)
            // Check current value (not the captured one) after delay
            if (!currentAutoProgressing) {
                shouldSpin = false
            }
        }
    }

    // Rotation animation for auto-progress indicator
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        // Spinning dashed border when auto-progressing
        if (shouldSpin) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()  // This centers it properly within the 44.dp Box
                    .graphicsLayer { rotationZ = rotation }
            ) {
                val radius = size.minDimension / 2 - 2.dp.toPx()
                val dashCount = 12
                val dashAngle = 360f / dashCount
                val dashSweep = dashAngle * 0.6f

                for (i in 0 until dashCount) {
                    drawArc(
                        color = Color(0xFFE88617),
                        startAngle = i * dashAngle,
                        sweepAngle = dashSweep,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                        size = Size(radius * 2, radius * 2)
                    )
                }
            }
        }

        // Main button
        Button(
            onClick = {
                vibrate(context, 10, 30)
                onClick()
            },
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE88617),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(0.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = if (isMuted) "○" else "●",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}


        @Composable
        fun KeyboardChaos3D(
            chaosLetters: List<ChaosKey>,
            rotationX: Float,
            rotationY: Float,
            scale: Float,
            message: String,
            onRotationChange: (Float, Float) -> Unit,
            onScaleChange: (Float) -> Unit,
            onLetterTap: (ChaosKey) -> Unit
        ) {
            val context = LocalContext.current
            val textMeasurer = rememberTextMeasurer()

            // Keep updated references to avoid stale closures in pointerInput
            val currentChaosLetters by rememberUpdatedState(chaosLetters)
            val currentRotationX by rememberUpdatedState(rotationX)
            val currentRotationY by rememberUpdatedState(rotationY)
            val currentScale by rememberUpdatedState(scale)
            val currentOnLetterTap by rememberUpdatedState(onLetterTap)

            // Phase tracking: false = word building, true = cleanup
            var isCleanupPhase by remember { mutableStateOf(false) }

            // Word building state - store LETTER REFERENCES, not screen positions
            var currentWord by remember { mutableStateOf("") }
            var connectedLetters by remember { mutableStateOf<List<ChaosKey>>(emptyList()) }
            var isDragging by remember { mutableStateOf(false) }
            var currentFingerPos by remember { mutableStateOf<Offset?>(null) }
            var dragStartedOnLetter by remember { mutableStateOf(false) }

            // Phase messages
            val phase1Message =
                "Well, that's not exactly a QWERTY keyboard, is it? Maybe you can try using it anyway - connect keys."
            val phase2Message =
                "Nevermind. This is way too uncomfortable - as pretty as it looks. I'll have to try something else. Can you get rid of the rogue keys? I have to focus to even keep it together."

            // Timer to switch to cleanup phase after 60 seconds
            LaunchedEffect(Unit) {
                delay(60000)
                isCleanupPhase = true
                connectedLetters = emptyList()
                currentWord = ""
                isDragging = false
                currentFingerPos = null
                dragStartedOnLetter = false
            }

            // Display message based on phase
            val displayMessage = if (isCleanupPhase) {
                if (chaosLetters.isEmpty()) message else phase2Message
            } else {
                if (currentWord.isNotEmpty()) "I'm reading: $currentWord" else phase1Message
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050505))
            ) {
                // Message at top
                Text(
                    text = displayMessage,
                    color = Color(0xFF00FF00),
                    fontSize = 16.sp,
                    fontFamily = CalculatorDisplayFont,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 40.dp)
                        .align(Alignment.TopCenter)
                )

                // Letters remaining counter (cleanup phase only)
                if (isCleanupPhase) {
                    Text(
                        text = "Letters: ${chaosLetters.size}",
                        color = Color(0xFF00FF00),
                        fontSize = 16.sp,
                        fontFamily = CalculatorDisplayFont,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopEnd)
                    )
                }

                // =============== ZOOM SLIDER (horizontal at bottom) ===============
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "−",
                        color = Color(0xFF00FF00),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Slider(
                        value = scale,
                        onValueChange = { onScaleChange(it) },
                        valueRange = 0.3f..4.5f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00FF00),
                            activeTrackColor = Color(0xFF00FF00),
                            inactiveTrackColor = Color(0xFF333333)
                        )
                    )

                    Text(
                        text = "+",
                        color = Color(0xFF00FF00),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${(scale * 100).toInt()}%",
                        color = Color(0xFF00FF00),
                        fontSize = 12.sp,
                        fontFamily = CalculatorDisplayFont,
                        modifier = Modifier.width(45.dp)
                    )
                }

                // ===================================================================
                // 3D CANVAS
                // ===================================================================
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 110.dp, bottom = 85.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // Calculate current letter positions for hit testing
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f

                                    if (!isCleanupPhase) {
                                        var foundLetter: ChaosKey? = null

                                        for (chaosKey in currentChaosLetters) {
                                            val screenPos = getLetterScreenPosition(
                                                chaosKey,
                                                currentRotationX,
                                                currentRotationY,
                                                currentScale,
                                                centerX,
                                                centerY
                                            )
                                            // Larger hit radius that works from any angle
                                            val baseRadius = 55f * currentScale * chaosKey.size
                                            val hitRadius =
                                                baseRadius.coerceAtLeast(35f) // Minimum 35px hit area

                                            val dx = offset.x - screenPos.x
                                            val dy = offset.y - screenPos.y
                                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                                            if (distance < hitRadius) {
                                                foundLetter = chaosKey
                                                break
                                            }
                                        }

                                        if (foundLetter != null) {
                                            dragStartedOnLetter = true
                                            isDragging = true
                                            currentFingerPos = offset
                                            connectedLetters = listOf(foundLetter)
                                            currentWord = foundLetter.letter
                                            vibrate(context, 15, 80)
                                        } else {
                                            dragStartedOnLetter = false
                                            isDragging = false
                                        }
                                    } else {
                                        dragStartedOnLetter = false
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()

                                    if (!isCleanupPhase && dragStartedOnLetter && isDragging) {
                                        val pos = change.position
                                        currentFingerPos = pos

                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f

                                        for (chaosKey in currentChaosLetters) {
                                            if (!connectedLetters.contains(chaosKey)) {
                                                val screenPos = getLetterScreenPosition(
                                                    chaosKey,
                                                    currentRotationX,
                                                    currentRotationY,
                                                    currentScale,
                                                    centerX,
                                                    centerY
                                                )
                                                // Larger hit radius that works from any angle
                                                val baseRadius = 55f * currentScale * chaosKey.size
                                                val hitRadius = baseRadius.coerceAtLeast(35f)

                                                val dx = pos.x - screenPos.x
                                                val dy = pos.y - screenPos.y
                                                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                                                if (distance < hitRadius) {
                                                    connectedLetters = connectedLetters + chaosKey
                                                    currentWord = currentWord + chaosKey.letter
                                                    vibrate(context, 15, 80)
                                                }
                                            }
                                        }
                                    } else {
                                        // Rotation mode
                                        onRotationChange(dragAmount.x * 0.3f, -dragAmount.y * 0.3f)
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    currentFingerPos = null
                                    dragStartedOnLetter = false
                                },
                                onDragCancel = {
                                    isDragging = false
                                    currentFingerPos = null
                                    dragStartedOnLetter = false
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { tapOffset ->
                                if (isCleanupPhase) {
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f

                                    for (chaosKey in currentChaosLetters) {
                                        val screenPos = getLetterScreenPosition(
                                            chaosKey,
                                            currentRotationX,
                                            currentRotationY,
                                            currentScale,
                                            centerX,
                                            centerY
                                        )
                                        // Larger hit radius that works from any angle
                                        val baseRadius = 55f * currentScale * chaosKey.size
                                        val hitRadius =
                                            baseRadius.coerceAtLeast(35f) // Minimum 35px hit area

                                        val dx = tapOffset.x - screenPos.x
                                        val dy = tapOffset.y - screenPos.y
                                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                                        if (distance < hitRadius) {
                                            vibrate(context, 20, 100)
                                            currentOnLetterTap(chaosKey)
                                            return@detectTapGestures
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2

                    data class FaceToDraw(
                        val vertices: List<Point3D>,
                        val color: Color,
                        val avgZ: Float,
                        val label: String,
                        val isFront: Boolean,
                        val textColor: Color
                    )

                    val allFaces = mutableListOf<FaceToDraw>()

                    // =============== CALCULATOR KEYBOARD CUBES ===============
                    val keyboardLayout = listOf(
                        listOf("C", "DEL", "%", "/"),
                        listOf("7", "8", "9", "*"),
                        listOf("4", "5", "6", "-"),
                        listOf("1", "2", "3", "+"),
                        listOf("0", ".", "=", "")
                    )

                    fun getKeyColor(key: String): Color = when {
                        key in "0".."9" -> Color(0xFFE8E4DA)
                        key in listOf("+", "-", "*", "/", "=", "%") -> Color(0xFF6B6B6B)
                        key == "C" -> Color(0xFFC9463D)
                        key == "DEL" -> Color(0xFFD4783C)
                        key == "." -> Color(0xFFE8E4DA)
                        else -> Color(0xFF333333)
                    }

                    fun getTextColor(key: String): Color = when {
                        key in "0".."9" -> Color(0xFF2D2D2D)
                        key == "." -> Color(0xFF2D2D2D)
                        else -> Color.White
                    }

                    val cubeSize = 32f
                    val spacing = 42f
                    val half = cubeSize / 2
                    val totalWidth = 4 * spacing
                    val totalHeight = 5 * spacing

                    val faceIndices = listOf(
                        listOf(4, 5, 6, 7), listOf(1, 0, 3, 2), listOf(0, 4, 7, 3),
                        listOf(5, 1, 2, 6), listOf(0, 1, 5, 4), listOf(7, 6, 2, 3)
                    )

                    // Build calculator cubes
                    keyboardLayout.forEachIndexed { row, keys ->
                        keys.forEachIndexed { col, key ->
                            if (key.isNotEmpty()) {
                                val kx = (col * spacing) - totalWidth / 2 + spacing / 2
                                val ky = (row * spacing) - totalHeight / 2 + spacing / 2
                                val keyColor = getKeyColor(key)
                                val txtColor = getTextColor(key)

                                val verts = listOf(
                                    Point3D(-half + kx, -half + ky, -half),
                                    Point3D(half + kx, -half + ky, -half),
                                    Point3D(half + kx, half + ky, -half),
                                    Point3D(-half + kx, half + ky, -half),
                                    Point3D(-half + kx, -half + ky, half),
                                    Point3D(half + kx, -half + ky, half),
                                    Point3D(half + kx, half + ky, half),
                                    Point3D(-half + kx, half + ky, half)
                                ).map { rotateX(rotateY(it, rotationY), rotationX) }

                                faceIndices.forEachIndexed { fi, face ->
                                    val fv = face.map { verts[it] }
                                    val faceColor = when (fi) {
                                        0 -> keyColor
                                        1 -> keyColor.copy(alpha = 0.85f)
                                        else -> Color(
                                            keyColor.red * 0.55f,
                                            keyColor.green * 0.55f,
                                            keyColor.blue * 0.55f
                                        )
                                    }
                                    allFaces.add(
                                        FaceToDraw(
                                            fv,
                                            faceColor,
                                            fv.map { it.z }.average().toFloat(),
                                            if (fi == 0) key else "",
                                            fi == 0,
                                            txtColor
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // =============== CHAOS LETTER CUBES ===============
                    val letterHalf = 12f
                    chaosLetters.forEach { ck ->
                        val lx = ck.x * 0.6f
                        val ly = ck.y * 0.6f
                        val lz = ck.z * 0.6f
                        val sh = letterHalf * ck.size
                        val isConnected = connectedLetters.contains(ck)

                        val verts = listOf(
                            Point3D(-sh + lx, -sh + ly, -sh + lz),
                            Point3D(sh + lx, -sh + ly, -sh + lz),
                            Point3D(sh + lx, sh + ly, -sh + lz),
                            Point3D(-sh + lx, sh + ly, -sh + lz),
                            Point3D(-sh + lx, -sh + ly, sh + lz),
                            Point3D(sh + lx, -sh + ly, sh + lz),
                            Point3D(sh + lx, sh + ly, sh + lz),
                            Point3D(-sh + lx, sh + ly, sh + lz)
                        ).map { rotateX(rotateY(it, rotationY), rotationX) }

                        val frontColor = if (isConnected) Color(0xFF7A7A7A) else Color(0xFF5A5A5A)
                        val backColor = if (isConnected) Color(0xFF4A4A4A) else Color(0xFF2A2A2A)
                        val sideColor = if (isConnected) Color(0xFF5A5A5A) else Color(0xFF3A3A3A)
                        val txtColor = if (isConnected) Color.White else Color(0xFFDDDDDD)

                        faceIndices.forEachIndexed { fi, face ->
                            val fv = face.map { verts[it] }
                            val faceColor = when (fi) {
                                0 -> frontColor; 1 -> backColor; else -> sideColor
                            }
                            allFaces.add(
                                FaceToDraw(
                                    fv,
                                    faceColor,
                                    fv.map { it.z }.average().toFloat(),
                                    if (fi == 0) ck.letter else "",
                                    fi == 0,
                                    txtColor
                                )
                            )
                        }
                    }

                    // =============== RENDER SORTED FACES ===============
                    allFaces.sortedBy { it.avgZ }.forEach { face ->
                        val v0 = face.vertices[0]
                        val v1 = face.vertices[1]
                        val v2 = face.vertices[2]
                        val crossZ = (v1.x - v0.x) * (v2.y - v1.y) - (v1.y - v0.y) * (v2.x - v1.x)

                        if (crossZ > 0) {
                            val proj = face.vertices.map { project(it, centerX, centerY, scale) }
                            val path = Path().apply {
                                moveTo(proj[0].x, proj[0].y)
                                proj.drop(1).forEach { lineTo(it.x, it.y) }
                                close()
                            }
                            drawPath(path, face.color)
                            drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(1.2f))

                            if (face.isFront && face.label.isNotEmpty()) {
                                val cx = proj.map { it.x }.average().toFloat()
                                val cy = proj.map { it.y }.average().toFloat()
                                val fw = kotlin.math.abs(proj[1].x - proj[0].x)
                                val fs = (fw * 0.5f).coerceIn(7f, 18f)
                                val tr = textMeasurer.measure(
                                    face.label,
                                    TextStyle(
                                        fontSize = fs.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = face.textColor
                                    )
                                )
                                drawText(
                                    textLayoutResult = tr,
                                    topLeft = Offset(
                                        cx - tr.size.width / 2,
                                        cy - tr.size.height / 2
                                    )
                                )
                            }
                        }
                    }

                    // =============== CONSTELLATION LINES (recalculated each frame) ===============
                    if (!isCleanupPhase && connectedLetters.size > 1) {
                        // Calculate current screen positions for connected letters
                        val letterPositions = connectedLetters.mapNotNull { ck ->
                            if (chaosLetters.contains(ck)) {
                                getLetterScreenPosition(
                                    ck,
                                    rotationX,
                                    rotationY,
                                    scale,
                                    centerX,
                                    centerY
                                )
                            } else null
                        }

                        if (letterPositions.size > 1) {
                            for (i in 0 until letterPositions.size - 1) {
                                val start = letterPositions[i]
                                val end = letterPositions[i + 1]

                                // Glow layers
                                drawLine(
                                    Color(0xFF87CEEB).copy(alpha = 0.15f),
                                    start,
                                    end,
                                    16f,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    Color(0xFFADD8E6).copy(alpha = 0.25f),
                                    start,
                                    end,
                                    10f,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    Color(0xFFE0FFFF).copy(alpha = 0.4f),
                                    start,
                                    end,
                                    6f,
                                    cap = StrokeCap.Round
                                )
                                drawLine(Color.White, start, end, 2f, cap = StrokeCap.Round)
                            }

                            // Star points
                            letterPositions.forEach { pt ->
                                drawCircle(Color(0xFF87CEEB).copy(alpha = 0.2f), 20f, pt)
                                drawCircle(Color(0xFFADD8E6).copy(alpha = 0.35f), 14f, pt)
                                drawCircle(Color(0xFFE0FFFF).copy(alpha = 0.5f), 9f, pt)
                                drawCircle(Color.White, 5f, pt)
                            }
                        }
                    }

                    // Trailing line to finger
                    if (!isCleanupPhase && isDragging && connectedLetters.isNotEmpty() && currentFingerPos != null) {
                        val lastLetter = connectedLetters.last()
                        if (chaosLetters.contains(lastLetter)) {
                            val start = getLetterScreenPosition(
                                lastLetter,
                                rotationX,
                                rotationY,
                                scale,
                                centerX,
                                centerY
                            )
                            val end = currentFingerPos!!
                            drawLine(
                                Color(0xFF87CEEB).copy(alpha = 0.1f),
                                start,
                                end,
                                12f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                Color(0xFFE0FFFF).copy(alpha = 0.25f),
                                start,
                                end,
                                6f,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                Color.White.copy(alpha = 0.5f),
                                start,
                                end,
                                2f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        @Composable
        fun ConsoleWindow(
            consoleStep: Int,
            adminCodeEntered: Boolean,
            currentInput: String,
            bannersDisabled: Boolean,
            fullScreenAdsEnabled: Boolean,
            totalScreenTimeMs: Long,
            totalCalculations: Int,
            onOpenContributeLink: () -> Unit,
            modifier: Modifier = Modifier
        ) {
            val context = LocalContext.current

            // Trigger link opening when on step 31
            LaunchedEffect(consoleStep) {
                if (consoleStep == 31) {
                    onOpenContributeLink()
                }
            }

            // Console menu content based on current step
            val menuContent = when (consoleStep) {
                0 -> """
            |═══════════════════════════════════
            |        SYSTEM CONSOLE v1.2
            |═══════════════════════════════════
            |
            | 1. General settings
            | 2. Administrator settings
            | 3. Application information
            |
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

                1 -> """
            |═══════════════════════════════════
            |        GENERAL SETTINGS
            |═══════════════════════════════════
            |
            | No configurable options available.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

                2 -> if (!adminCodeEntered) """
            |═══════════════════════════════════
            |      ADMINISTRATOR SETTINGS
            |═══════════════════════════════════
            |
            | Access code required.
            | Enter code and confirm with ++
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin() else """
            |═══════════════════════════════════
            |      ADMINISTRATOR SETTINGS
            |═══════════════════════════════════
            |
            | 1. Permissions & allowances
            | 2. Design settings
            | 3. Contribute
            | 4. Connectivity settings
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

                3 -> {
                    // Get actual app size
                    val appSize = try {
                        val appFile = File(context.applicationInfo.sourceDir)
                        val sizeInMB = appFile.length() / (1024.0 * 1024.0)
                        String.format("%.2f MB", sizeInMB)
                    } catch (e: Exception) {
                        "Unknown"
                    }

                    // Format screen time
                    val hours = totalScreenTimeMs / (1000 * 60 * 60)
                    val minutes = (totalScreenTimeMs / (1000 * 60)) % 60
                    val seconds = (totalScreenTimeMs / 1000) % 60
                    val screenTimeFormatted =
                        String.format("%02d:%02d:%02d", hours, minutes, seconds)

                    """
    |═══════════════════════════════════
    |      APPLICATION INFORMATION
    |═══════════════════════════════════
    |
    | Status: Operational
    |         Administrator access
    |         restricted
    |
    | Version: 1.2
    | Developer: FictionCutShort
    | Licence: All licences and rights
    |          reserved.
    |          For someone special.
    | Size: $appSize
    |
    | --- Usage Statistics ---
    | Screen time: $screenTimeFormatted
    | Calculations: $totalCalculations
    |
    | 88. Back
    | 99. Exit console
    |═══════════════════════════════════
    """.trimMargin()
                }

                4 -> """
            |═══════════════════════════════════
            |      CONNECTIVITY SETTINGS
            |═══════════════════════════════════
            |
            | 1. Network preferences
            | 2. Promotion & advertising options
            | 3. Data usage
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

                41 -> """
            |═══════════════════════════════════
            |      NETWORK PREFERENCES
            |═══════════════════════════════════
            |
            | Current setting: Default
            |
            | No configurable options available.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

                43 -> """
            |═══════════════════════════════════
            |          DATA USAGE
            |═══════════════════════════════════
            |
            | Current setting: Minimal
            |
            | No configurable options available.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

                5 -> {
                    val bannerStatus = if (bannersDisabled) "Disabled" else "Enabled"
                    val fullScreenStatus = if (fullScreenAdsEnabled) "Enabled" else "Disabled"
                    """
            |═══════════════════════════════════
            |   PROMOTION & ADVERTISING OPTIONS
            |═══════════════════════════════════
            |
            | Banner advertising: $bannerStatus
            | Full-screen advertising: $fullScreenStatus
            |
            | 1. Banner advertising
            | 2. Full-screen advertising
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
            """.trimMargin()
                }

                51 -> {
                    val currentStatus = if (bannersDisabled) "Disabled" else "Enabled"
                    """
            |═══════════════════════════════════
            |      BANNER ADVERTISING
            |═══════════════════════════════════
            |
            | Current status: $currentStatus
            |
            | 1. Enable
            | 2. Disable
            |
            | Note: Disabling banner ads will
            | enable full-screen advertising.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
            """.trimMargin()
                }

                52 -> {
                    val currentStatus = if (fullScreenAdsEnabled) "Enabled" else "Disabled"
                    """
            |═══════════════════════════════════
            |    FULL-SCREEN ADVERTISING
            |═══════════════════════════════════
            |
            | Current status: $currentStatus
            |
            | 1. Enable
            | 2. Disable
            |
            | Note: Disabling full-screen ads
            | will enable banner advertising.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
            """.trimMargin()
                }

                6 -> """
            |═══════════════════════════════════
            |      PERMISSIONS & ALLOWANCES
            |═══════════════════════════════════
            |
            | Camera access: Granted
            | Storage access: Granted
            | Notifications: Granted
            | Contacts & phone: Not requested
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

                7 -> """
            |═══════════════════════════════════
            |        DESIGN SETTINGS
            |═══════════════════════════════════
            |
            | Dark mode: Unavailable
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

                99 -> """
            |═══════════════════════════════════
            |        SETTINGS UPDATED
            |═══════════════════════════════════
            |
            | Banner advertising has been
            | DISABLED.
            |
            | Full-screen advertising has been
            | ENABLED.
            |
            | Changes will take effect
            | immediately.
            |
            | Press 99++ to close console.
            |═══════════════════════════════════
        """.trimMargin()

                else -> """
            |═══════════════════════════════════
            |           ERROR
            |═══════════════════════════════════
            |
            | Unknown menu state.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()
            }

            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            val consoleTopPadding = (screenHeight * 0.25f).coerceAtLeast(150.dp)
            val consoleBottomPadding = (screenHeight * 0.30f).coerceAtLeast(200.dp)

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(
                        top = consoleTopPadding + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                        bottom = consoleBottomPadding,
                        start = 12.dp,
                        end = 12.dp
                    )
            ) {
                // Console container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0A1A0A))  // Dark green tint
                            .padding(12.dp)
                    ) {
                        // Console content
                        Text(
                            text = menuContent,
                            color = Color(0xFF00FF00),  // Green terminal text
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp,
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        )

                        // Input prompt with current number
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF001500))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "> ",
                                color = Color(0xFF00FF00),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentInput == "0") "_" else "${currentInput}_",
                                color = Color(0xFF00FF00),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }


        // Helper function to get letter's current screen position based on rotation
        private fun getLetterScreenPosition(
            chaosKey: ChaosKey,
            rotationX: Float,
            rotationY: Float,
            scale: Float,
            centerX: Float,
            centerY: Float
        ): Offset {
            val lx = chaosKey.x * 0.6f
            val ly = chaosKey.y * 0.6f
            val lz = chaosKey.z * 0.6f

            var p = Point3D(lx, ly, lz)
            p = rotateY(p, rotationY)
            p = rotateX(p, rotationX)

            return project(p, centerX, centerY, scale)
        }




        @ComposePreview(showBackground = true)
        @Composable
        fun DefaultPreview() {
            MaterialTheme { CalculatorScreen() }
        }
