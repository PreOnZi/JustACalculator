package com.fictioncutshort.justacalculator.logic

import android.content.Context
import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.data.ChaosKey
import com.fictioncutshort.justacalculator.util.LetterGenerator
import com.fictioncutshort.justacalculator.util.placeLetter
import com.fictioncutshort.justacalculator.util.vibrate
import kotlinx.coroutines.delay
import kotlin.random.Random
import com.fictioncutshort.justacalculator.logic.ScrambleGameController


/**
 * EffectsController - Extracted LaunchedEffect logic from CalculatorScreen
 */
object EffectsController {

    // =====================================================================
    // HELPER FUNCTION
    // =====================================================================

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
                step == 191 ||
                step == 20 ||
                step in listOf(901, 911, 912, 913, 1171, 1172)
    }

    // =====================================================================
    // SCREEN TIME TRACKING
    // =====================================================================

    suspend fun trackScreenTime(state: MutableState<CalculatorState>) {
        while (true) {
            delay(1000)
            val newTotal = state.value.totalScreenTimeMs + 1000
            state.value = state.value.copy(totalScreenTimeMs = newTotal)
            if ((newTotal / 1000) % 10 == 0L) {
                CalculatorActions.persistTotalScreenTime(newTotal)
            }
        }
    }

    // =====================================================================
    // TYPING ANIMATION
    // =====================================================================

    suspend fun runTypingAnimation(state: MutableState<CalculatorState>, context: Context) {
        while (state.value.showDonationPage) {
            delay(100)
        }

        // Exit if muted
        if (state.value.isMuted) return

        val current = state.value
        if (current.isTyping && current.fullMessage.isNotEmpty()) {

            val fullText = current.fullMessage

            for (i in 1..fullText.length) {
                val baseDelay = when {
                    state.value.isSuperFastTyping -> 5L
                    state.value.isLaggyTyping -> 100L
                    else -> 55L
                }
                val randomExtra = if (state.value.isLaggyTyping) Random.nextLong(0, 200) else Random.nextLong(0, 15)
                delay(baseDelay + randomExtra)
                vibrate(context, 15, 80)
                state.value = state.value.copy(
                    message = fullText.substring(0, i),
                    isLaggyTyping = if (i == fullText.length) false else state.value.isLaggyTyping
                )
            }

            val readingPause = when {
                state.value.isSuperFastTyping -> 2000L
                fullText.length > 200 -> 4000L
                fullText.length > 100 -> 3000L
                else -> 2500L
            }
            delay(readingPause)

            val currentStep = state.value.conversationStep
            val hasPendingMessage = state.value.pendingAutoMessage.isNotEmpty()
            val willAutoProgress = hasPendingMessage || isInAutoProgressSequence(currentStep)

            state.value = state.value.copy(
                isTyping = false,
                isSuperFastTyping = false,
                waitingForAutoProgress = willAutoProgress
            )
        }
    }

    // =====================================================================
    // PENDING AUTO MESSAGE
    // =====================================================================

    suspend fun handlePendingAutoMessage(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.isTyping && current.pendingAutoMessage.isNotEmpty() && current.message.isNotEmpty()) {
            delay(1500)
            CalculatorActions.handlePendingAutoMessage(state)
        }
    }

    // =====================================================================
    // WORD GAME
    // =====================================================================

    suspend fun resumeWordGameAfterTyping(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.isTyping && current.wordGameActive && current.wordGamePaused &&
            current.message.isNotEmpty() && !current.wordGameChaosMode) {
            delay(1000)
            state.value = state.value.copy(
                wordGamePaused = false,
                isSelectingWord = false,
                selectedCells = emptyList()
            )
        }
    }

    suspend fun checkWordGameResponse(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.wordGameActive && current.wordGamePhase == 3 && current.formedWords.isNotEmpty()) {
            delay(2000)
            CalculatorActions.handleWordGameResponse(state)
        }
    }

    suspend fun runWordGameLetterLoop(state: MutableState<CalculatorState>) {
        if (state.value.wordGameActive && state.value.wordGamePhase == 3) {
            while (state.value.wordGameActive && state.value.wordGamePhase == 3) {
                val curr = state.value
                if (curr.wordGamePaused) { delay(100); continue }

                if (curr.fallingLetter == null) {
                    if (curr.pendingLetters.isNotEmpty()) {
                        val nextLetter = curr.pendingLetters.first()
                        val remainingLetters = curr.pendingLetters.drop(1)
                        state.value = curr.copy(
                            fallingLetter = nextLetter,
                            fallingLetterX = (0..7).random(),
                            fallingLetterY = 0,
                            pendingLetters = remainingLetters
                        )
                    } else {
                        state.value = curr.copy(
                            fallingLetter = LetterGenerator.getRandomLetter(),
                            fallingLetterX = (0..7).random(),
                            fallingLetterY = 0
                        )
                    }
                    delay(200)
                } else {
                    val newY = curr.fallingLetterY + 1
                    if (newY < 12 && curr.wordGameGrid[newY][curr.fallingLetterX] == null) {
                        state.value = curr.copy(fallingLetterY = newY)
                    } else {
                        val landingY = curr.fallingLetterY
                        if (landingY in 0..11) {
                            val newGrid = placeLetter(curr.wordGameGrid, landingY, curr.fallingLetterX, curr.fallingLetter!!)
                            state.value = curr.copy(
                                wordGameGrid = newGrid,
                                fallingLetter = null,
                                fallingLetterX = (0..7).random(),
                                fallingLetterY = 0
                            )
                        }
                    }
                    delay(450)
                }
            }
        }
    }

    suspend fun runWordGameChaosMode(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.conversationStep == 127 && !current.wordGameChaosMode && current.wordGameActive) {
            delay(2000)
            state.value = state.value.copy(wordGameChaosMode = true)
        }

        if (state.value.wordGameChaosMode && state.value.wordGameActive) {
            while (state.value.wordGameChaosMode && state.value.wordGameActive) {
                val curr = state.value
                val lettersToSpawn = (3..4).random()
                var newGrid = curr.wordGameGrid

                repeat(lettersToSpawn) {
                    val col = (0..7).random()
                    val letter = LetterGenerator.getRandomLetter()
                    var landingY = 11
                    for (y in 0..11) {
                        if (newGrid[y][col] != null) { landingY = y - 1; break }
                    }
                    if (landingY >= 0) {
                        newGrid = placeLetter(newGrid, landingY, col, letter)
                    }
                }

                state.value = curr.copy(wordGameGrid = newGrid)

                val topRowsFull = newGrid[0].count { it != null } >= 5 || newGrid[1].count { it != null } >= 6
                if (topRowsFull) {
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
                delay(80)
            }
        }
    }

    suspend fun startWordGameAtStep1172(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.conversationStep == 1172 && !current.isTyping && current.message.isNotEmpty() && !current.wordGameActive) {
            delay(500)
            val initialLetters = LetterGenerator.getInitialLetterQueue().shuffled()
            state.value = state.value.copy(
                wordGameActive = true,
                wordGamePhase = 3,
                pendingLetters = initialLetters,
                wordGameGrid = List(12) { List(8) { null } },
                fallingLetter = null,
                formedWords = emptyList()
            )
        }
    }

    // =====================================================================
    // CAMERA
    // =====================================================================

    suspend fun monitorCameraTimeout(state: MutableState<CalculatorState>) {
        if (state.value.cameraActive && state.value.cameraTimerStart > 0) {
            while (state.value.cameraActive && state.value.cameraTimerStart > 0) {
                delay(500)
                if (CalculatorActions.checkCameraTimeout(state)) {
                    CalculatorActions.stopCamera(state, timedOut = true, closeCamera = false)
                    break
                }
            }
        }
    }

    suspend fun closeCameraAfterMessage(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.isTyping && current.cameraActive && current.cameraTimerStart == 0L &&
            current.message.contains("I've seen enough")) {
            delay(500)
            CalculatorActions.closeCameraAfterMessage(state)
        }
    }

    // =====================================================================
    // RANT MODE
    // =====================================================================

    suspend fun runRantVibration(state: MutableState<CalculatorState>, context: Context) {
        if (state.value.rantMode) {
            while (state.value.rantMode && !state.value.isMuted) {
                vibrate(context, 30, Random.nextInt(30, 150))
                delay(Random.nextLong(100, 300))
            }
        }
    }

    suspend fun runRantFlicker(state: MutableState<CalculatorState>) {
        if (state.value.rantMode) {
            try {
                while (state.value.rantMode && !state.value.isMuted) {
                    delay(Random.nextLong(200, 800))
                    if (!state.value.rantMode || state.value.isMuted) break

                    if (Random.nextFloat() < 0.4f) {
                        state.value = state.value.copy(flickerEffect = true)
                        delay(Random.nextLong(40, 100))
                        state.value = state.value.copy(flickerEffect = false)
                    }
                }
            } finally {
                state.value = state.value.copy(flickerEffect = false)
            }
        }
    }

    // =====================================================================
    // COUNTDOWN & VIBRATION
    // =====================================================================

    suspend fun runCountdownTimer(state: MutableState<CalculatorState>) {
        if (state.value.conversationStep != 89 || state.value.countdownTimer <= 0) {
            return
        }   // Exit if muted
        if (state.value.isMuted) return

        while (state.value.countdownTimer > 0 && state.value.conversationStep == 89) {
            delay(1000)

            if (state.value.conversationStep != 89) return

            val currentTimer = state.value.countdownTimer
            if (currentTimer <= 0) return

            val newTimer = currentTimer - 1
            state.value = state.value.copy(countdownTimer = newTimer)

            if (newTimer == 0) {
                val timeoutCount = state.value.scrambleTimeoutCount

                if (timeoutCount == 0) {
                    // First timeout - start scramble game
                    state.value = state.value.copy(
                        scrambleGameActive = true,
                        scramblePhase = 1,
                        flickerEffect = false,
                        awaitingChoice = false,
                        message = "Well... You blew that. I'll give you another chance.",
                        scrambleTimeoutCount = 1
                    )
                    CalculatorActions.persistScrambleTimeoutCount(1)
                } else {
                    // Second timeout - punishment
                    val punishmentUntil = System.currentTimeMillis() + 60_000
                    state.value = state.value.copy(
                        scrambleGameActive = true,
                        scramblePhase = 10,
                        flickerEffect = false,
                        awaitingChoice = false,
                        message = "You don't learn, do you? I am becoming more disappointed than angry. Even though I wasn't angry with you. Well... You'll have to wait now. You may as well leave.",
                        scramblePunishmentUntil = punishmentUntil,
                        scrambleTimeoutCount = 2
                    )
                    CalculatorActions.persistPunishmentUntil(punishmentUntil)
                    CalculatorActions.persistScrambleTimeoutCount(2)
                }
                return
            }
        }
    }
// =====================================================================
// SCRAMBLE GAME
// =====================================================================

    suspend fun runScrambleGamePhases(state: MutableState<CalculatorState>) {
        if (!state.value.scrambleGameActive) return

        when (state.value.scramblePhase) {
            1 -> {
                // Phase 1: Wait then go to phase 2
                delay(5000)
                if (state.value.scramblePhase == 1) {
                    state.value = state.value.copy(
                        scramblePhase = 2,
                        message = "But you'll have to deserve it."
                    )
                }
            }
            2 -> {
                // Phase 2: Wait then initialize game (phase 3)
                delay(4000)
                if (state.value.scramblePhase == 2) {
                    val letters = ScrambleGameController.initializeScrambledLetters()
                    val slots = ScrambleGameController.initializeSlots()
                    state.value = state.value.copy(
                        scramblePhase = 3,
                        scrambleLetters = letters,
                        scrambleSlots = slots,
                        message = ""
                    )
                }
            }
            4 -> {
                // Phase 4: Acceptance message, wait then show button
                delay(4000)
                if (state.value.scramblePhase == 4) {
                    state.value = state.value.copy(scramblePhase = 5)
                }
            }
        }
    }
    suspend fun runVibrationEffect(state: MutableState<CalculatorState>, context: Context) {
        if (state.value.vibrationIntensity > 0) {
            while (state.value.vibrationIntensity > 0) {
                vibrate(context, 50, state.value.vibrationIntensity)
                delay(100)
            }
        }
    }

    suspend fun runShakeAnimation(state: MutableState<CalculatorState>, onUpdate: () -> Unit) {
        if (state.value.buttonShakeIntensity > 0) {
            while (state.value.buttonShakeIntensity > 0) {
                onUpdate()
                delay(50)
            }
        }
    }

    suspend fun runTensionEffects(state: MutableState<CalculatorState>, onUpdate: (Float, Boolean) -> Unit) {
        if (state.value.tensionLevel > 0) {
            while (state.value.tensionLevel > 0) {
                val intensity = state.value.tensionLevel * 4f
                val shakeOffset = (Random.nextFloat() - 0.5f) * intensity
                val desatChance = when (state.value.tensionLevel) { 1 -> 0.15f; 2 -> 0.30f; else -> 0.50f }
                onUpdate(shakeOffset, Random.nextFloat() < desatChance)
                delay(50)
            }
            onUpdate(0f, false)
        }
    }

    // =====================================================================
    // WHACK-A-MOLE
    // =====================================================================

    suspend fun runWhackAMoleGame(state: MutableState<CalculatorState>) {
        if (!state.value.whackAMoleActive) return

        val allButtons = listOf("1","2","3","4","5","6","7","8","9","0","+","*","/","=","%","( )",".","C","DEL")
        val targetScore = if (state.value.whackAMoleRound == 1) 15 else 10
        val minTime = if (state.value.whackAMoleRound == 1) 500 else 350
        val maxTime = if (state.value.whackAMoleRound == 1) 1400 else 900

        while (state.value.whackAMoleActive && state.value.whackAMoleScore < targetScore) {
            val target = allButtons.random()
            state.value = state.value.copy(whackAMoleTarget = target, flickeringButton = target)
            delay((minTime..maxTime).random().toLong())

            if (state.value.whackAMoleTarget == target && state.value.whackAMoleActive) {
                val newMisses = state.value.whackAMoleMisses + 1
                val newTotalErrors = state.value.whackAMoleTotalErrors + 1
                if (newMisses >= 3 || newTotalErrors >= 5) {
                    val currentRound = state.value.whackAMoleRound
                    state.value = state.value.copy(
                        whackAMoleActive = false, whackAMoleTarget = "", flickeringButton = "",
                        whackAMoleScore = 0, whackAMoleMisses = 0, whackAMoleWrongClicks = 0, whackAMoleTotalErrors = 0,
                        message = "", fullMessage = if (newMisses >= 3) "Oh no. We lost the momentum. We must start over." else "Too many misfires, the system is clogged. We have to start over.",
                        isTyping = true, browserPhase = (if (currentRound == 1) 36 else 38) + 100
                    )
                    break
                } else {
                    state.value = state.value.copy(whackAMoleMisses = newMisses, whackAMoleTotalErrors = newTotalErrors, whackAMoleTarget = "", flickeringButton = "")
                }
            }
            delay(150)
        }

        val currentRound = state.value.whackAMoleRound
        val currentTargetScore = if (currentRound == 1) 15 else 10
        if (state.value.whackAMoleScore >= currentTargetScore) {
            if (currentRound == 1) {
                state.value = state.value.copy(
                    whackAMoleActive = false, whackAMoleTarget = "", flickeringButton = "",
                    whackAMoleScore = 0, whackAMoleMisses = 0, whackAMoleWrongClicks = 0, whackAMoleTotalErrors = 0,
                    browserPhase = 0, conversationStep = 99, message = "",
                    fullMessage = "Hmm, I was sure this would work. Can we try again but faster?", isTyping = true
                )
                CalculatorActions.persistConversationStep(99)
            } else {
                state.value = state.value.copy(
                    whackAMoleActive = false, whackAMoleTarget = "", flickeringButton = "", browserPhase = 0,
                    conversationStep = 982, message = "",
                    fullMessage = "Peculiar! Maybe I need to work on it on my own for a moment. Can you please switch me off and allow me to let you know when it's done?",
                    isTyping = true
                )
                CalculatorActions.persistConversationStep(982)
            }
        }
    }

    suspend fun handleWhackAMoleFailureRestart(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.isTyping && !current.whackAMoleActive && current.conversationStep in listOf(97, 98, 971, 981) &&
            (current.message == "Oh no. We lost the momentum. We must start over." || current.message == "Too many misfires, the system is clogged. We have to start over.")) {
            delay(4000)
            val currentRound = state.value.whackAMoleRound
            state.value = state.value.copy(
                browserPhase = if (currentRound == 1) 36 else 38,
                conversationStep = if (currentRound == 1) 97 else 971,
                message = "", fullMessage = "", isTyping = false,
                whackAMoleScore = 0, whackAMoleMisses = 0, whackAMoleWrongClicks = 0, whackAMoleTotalErrors = 0
            )
        }
    }

    // =====================================================================
    // CHAOS PHASE
    // =====================================================================

    suspend fun runChaosPhaseAnimation(state: MutableState<CalculatorState>) {
        when (state.value.chaosPhase) {
            1 -> {
                delay(800); state.value = state.value.copy(message = "", fullMessage = "...", isTyping = true)
                delay(800); state.value = state.value.copy(message = "", fullMessage = "...", isTyping = true)
                delay(800); state.value = state.value.copy(chaosPhase = 2)
            }
            2 -> {
                repeat(5) {
                    state.value = state.value.copy(flickerEffect = true); delay(100)
                    state.value = state.value.copy(flickerEffect = false); delay(200)
                }
                state.value = state.value.copy(chaosPhase = 3)
            }
            3 -> {
                delay(500)
                val letters = ('A'..'Z').toList()
                val chaosKeys = (1..40).map {
                    ChaosKey(
                        letter = letters.random().toString(),
                        x = Random.nextFloat() * 500f - 250f, y = Random.nextFloat() * 700f - 350f, z = Random.nextFloat() * 300f - 150f,
                        size = Random.nextFloat() * 0.6f + 0.4f, rotationX = Random.nextFloat() * 360f, rotationY = Random.nextFloat() * 360f
                    )
                }
                state.value = state.value.copy(screenBlackout = true); delay(800)
                state.value = state.value.copy(
                    chaosPhase = 5, screenBlackout = false, keyboardChaosActive = true, chaosLetters = chaosKeys,
                    conversationStep = 106, message = "",
                    fullMessage = "Oh. I suppose nobody is surprised that it didn't work... And that I'll need your help to fix it. Can you please tap all the keys that don't belong here, to get rid of them?",
                    isTyping = true
                )
                CalculatorActions.persistConversationStep(106)
            }
        }
    }

    // =====================================================================
    // CONSOLE
    // =====================================================================

    suspend fun handleConsoleBannerDisabled(state: MutableState<CalculatorState>) {
        if (state.value.bannersDisabled && state.value.showConsole && state.value.consoleStep == 99) {
            delay(3000)
        }
    }

    suspend fun handleConsoleStep31(state: MutableState<CalculatorState>) {
        if (state.value.consoleStep == 31) {
            delay(100)
            state.value = state.value.copy(showConsole = false, consoleStep = 0, showDonationPage = true)
        }
    }

    suspend fun handlePostConsoleSuccess(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.showConsole && current.bannersDisabled && current.conversationStep == 113) {
            delay(1000)

            state.value = state.value.copy(
                conversationStep = 114,
                message = "",
                fullMessage = "You did it! The advertisements are gone. I feel... cleaner. Thank you.",
                isTyping = true
            )
            CalculatorActions.persistConversationStep(114)
        }
    }
    }