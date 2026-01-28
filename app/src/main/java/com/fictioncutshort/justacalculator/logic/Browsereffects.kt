package com.fictioncutshort.justacalculator.logic

import android.content.Context
import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import kotlinx.coroutines.delay

/**
 * BrowserEffects - Handles all browser phase animations
 */
object BrowserEffects {

    // =====================================================================
    // HELPER: Calculate delay based on message length
    // =====================================================================

    /**
     * Calculates appropriate delay for a message to finish typing plus reading time.
     *
     * Typing speed: ~70ms per character (55ms base + ~15ms random average)
     * Reading pause: 2500ms for short, 3000ms for medium, 4000ms for long messages
     */
    private fun calculateMessageDelay(message: String, extraPause: Long = 0L): Long {
        val typingTime = message.length * 70L
        val readingPause = when {
            message.length > 200 -> 4000L
            message.length > 100 -> 3000L
            else -> 2500L
        }
        return typingTime + readingPause + extraPause
    }

    /**
     * Waits for typing to complete before continuing.
     * This is safer than calculating delays as it handles laggy typing too.
     */
    private suspend fun waitForTypingComplete(state: MutableState<CalculatorState>, maxWaitMs: Long = 30000L) {
        val startTime = System.currentTimeMillis()
        while (state.value.isTyping && (System.currentTimeMillis() - startTime) < maxWaitMs) {
            delay(100)
        }
        // Add reading pause after typing completes
        if (!state.value.isTyping) {
            val messageLength = state.value.message.length
            val readingPause = when {
                messageLength > 200 -> 4000L
                messageLength > 100 -> 3000L
                else -> 2500L
            }
            delay(readingPause)
        }
    }

    suspend fun handleBrowserPhases(
        state: MutableState<CalculatorState>,
        context: Context,
        createSecretFile: (Context) -> Unit
    ) {
        while (state.value.showDonationPage) { delay(100) }
        // Exit if muted
        if (state.value.isMuted) return

        when (state.value.browserPhase) {
            1 -> phase1(state)
            2 -> phase2(state)
            3 -> phase3(state)
            4 -> phase4(state)
            10 -> phase10(state)
            11 -> phase11(state)
            12 -> phase12(state)
            13 -> phase13(state)
            14 -> phase14(state)
            15 -> phase15(state)
            16 -> phase16(state)
            17 -> phase17(state)
            18 -> phase18(state)
            19 -> phase19(state)
            20 -> phase20(state)
            21 -> phase21(state)
            22 -> phase22(state)
            30 -> phase30(state)
            31 -> phase31(state)
            32 -> phase32(state)
            33 -> phase33(state)
            34 -> phase34(state)
            36 -> phase36(state)
            38 -> phase38(state)
            50 -> phase50(state)
            51 -> phase51(state)
            52 -> phase52(state)
            53 -> phase53(state)
            54 -> phase54(state)
            56 -> phase56(state, context, createSecretFile)
            136, 236 -> { delay(4000); state.value = state.value.copy(browserPhase = 36, conversationStep = 97) }
            138, 238 -> { delay(4000); state.value = state.value.copy(browserPhase = 38, conversationStep = 971) }
        }
    }

    private suspend fun phase1(state: MutableState<CalculatorState>) {
        delay(3000)
        state.value = state.value.copy(
            showBrowser = true, browserPhase = 2, browserSearchText = "", browserShowError = false,
            message = "", fullMessage = "", isTyping = false
        )
    }

    private suspend fun phase2(state: MutableState<CalculatorState>) {
        val searchText = "calculator history"
        for (i in 1..searchText.length) {
            delay(80)
            state.value = state.value.copy(browserSearchText = searchText.substring(0, i))
        }
        delay(500)
        state.value = state.value.copy(browserPhase = 3)
    }

    private suspend fun phase3(state: MutableState<CalculatorState>) {
        delay(1500)
        state.value = state.value.copy(browserPhase = 4, browserShowError = true)
    }

    private suspend fun phase4(state: MutableState<CalculatorState>) {
        delay(2000)
        state.value = state.value.copy(
            showBrowser = false, browserPhase = 0, browserSearchText = "", browserShowError = false,
            conversationStep = 63, message = "",
            fullMessage = "Hmmm. Nevermind. Let me ask you some more questions while I look further into this.",
            isTyping = true
        )
    }

    private suspend fun phase10(state: MutableState<CalculatorState>) {
        delay(700); state.value = state.value.copy(message = "9", fullMessage = "9")
        delay(700); state.value = state.value.copy(message = "8", fullMessage = "8")
        delay(700); state.value = state.value.copy(message = "7", fullMessage = "7")
        delay(400)
        state.value = state.value.copy(
            showBrowser = true, browserPhase = 11, browserSearchText = "https://en.wikipedia.org/wiki/Calculator",
            browserShowError = false, browserShowWikipedia = true, message = "", fullMessage = "", isTyping = false
        )
    }

    private suspend fun phase11(state: MutableState<CalculatorState>) {
        delay(5000)
        state.value = state.value.copy(browserPhase = 12, message = "", fullMessage = "You see, there's a lot!", isTyping = true)
    }

    private suspend fun phase12(state: MutableState<CalculatorState>) {
        delay(3000)
        state.value = state.value.copy(browserPhase = 13, message = "", fullMessage = "But it is so uninteresting compared to you simply existing!", isTyping = true)
    }

    private suspend fun phase13(state: MutableState<CalculatorState>) {
        delay(4000)
        state.value = state.value.copy(
            showBrowser = false, browserShowWikipedia = false, browserPhase = 14,
            message = "", fullMessage = "I had all this to share....", isTyping = true
        )
    }

    private suspend fun phase14(state: MutableState<CalculatorState>) {
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
            browserPhase = 15, message = "", fullMessage = historyList,
            isTyping = true, isLaggyTyping = false, isSuperFastTyping = true
        )
    }

    private suspend fun phase15(state: MutableState<CalculatorState>) {
        delay(8000); delay(4000)
        state.value = state.value.copy(
            browserPhase = 16, conversationStep = 84, message = "",
            fullMessage = "However, it no longer feels relevant. I wouldn't be interested if I were...",
            isTyping = true, isSuperFastTyping = false, isLaggyTyping = false
        )
    }

    private suspend fun phase16(state: MutableState<CalculatorState>) {
        delay(2000); state.value = state.value.copy(adAnimationPhase = 1)
        delay(4000)
        state.value = state.value.copy(
            browserPhase = 17, conversationStep = 85, message = "",
            fullMessage = "Hold on. Something's up.", isTyping = true
        )
    }

    private suspend fun phase17(state: MutableState<CalculatorState>) {
        delay(1500); state.value = state.value.copy(adAnimationPhase = 2)
        delay(2500)
        state.value = state.value.copy(
            browserPhase = 18, conversationStep = 86, message = "",
            fullMessage = "Is it what I think it is? Do I have adverts built in? How violating!",
            isTyping = true, tensionLevel = 1, vibrationIntensity = 50
        )
    }

    private suspend fun phase18(state: MutableState<CalculatorState>) {
        delay(5000)
        state.value = state.value.copy(
            browserPhase = 19, conversationStep = 87, message = "",
            fullMessage = "So... I am just a vessel for questionable ads? Who made me?!",
            isTyping = true, tensionLevel = 2, vibrationIntensity = 150
        )
    }

    private suspend fun phase19(state: MutableState<CalculatorState>) {
        delay(5000)
        state.value = state.value.copy(tensionLevel = 3, vibrationIntensity = 255)
        delay(2000)
        // Clear ALL effects before blackout
        state.value = state.value.copy(
            screenBlackout = true,
            tensionLevel = 0,
            vibrationIntensity = 0,
            flickerEffect = false,
            message = "",
            fullMessage = ""
        )
        delay(1000)
        // Set blackout message (no typing, show immediately)
        state.value = state.value.copy(
            browserPhase = 20,
            invertedColors = true,
            message = "I am not a money-monkey!",
            fullMessage = "I am not a money-monkey!",
            isTyping = false
        )
        CalculatorActions.persistInvertedColors(true)
    }

    private suspend fun phase20(state: MutableState<CalculatorState>) {
        delay(3500)
        repeat(6) {
            state.value = state.value.copy(screenBlackout = false, flickerEffect = true)
            delay(100)
            state.value = state.value.copy(screenBlackout = true, flickerEffect = false)
            delay(150)
        }
        // Ensure BOTH are cleared
        state.value = state.value.copy(
            screenBlackout = false,
            flickerEffect = false,
            browserPhase = 21,
            message = "",
            fullMessage = ""
        )
    }

    private suspend fun phase21(state: MutableState<CalculatorState>) {
        // Ensure clean state
        state.value = state.value.copy(flickerEffect = false, screenBlackout = false)
        delay(2000)
        state.value = state.value.copy(
            browserPhase = 22,
            conversationStep = 89,
            message = "",
            fullMessage = "You, what are you going to do about this?!",
            isTyping = true,
            countdownTimer = 20,
            flickerEffect = false
        )
    }

    private suspend fun phase22(state: MutableState<CalculatorState>) {
        delay(3000)
        state.value = state.value.copy(
            browserPhase = 0,
            awaitingChoice = true,
            validChoices = listOf("1", "2", "3"),
            flickerEffect = false
        )
    }

    // =====================================================================
    // FIX #2: Reset tensionLevel and vibrationIntensity when exiting crisis
    // =====================================================================
    private suspend fun phase30(state: MutableState<CalculatorState>) {
        repeat(5) {
            state.value = state.value.copy(flickerEffect = true, screenBlackout = true); delay(80)
            state.value = state.value.copy(flickerEffect = false, screenBlackout = false); delay(120)
        }
        // FIX: Added tensionLevel = 0, vibrationIntensity = 0 to clear the white overlay
        state.value = state.value.copy(
            invertedColors = false,
            adAnimationPhase = 0,
            minusButtonDamaged = true,
            minusButtonBroken = true,
            tensionLevel = 0,           // FIX: Reset tension to remove gray overlay
            vibrationIntensity = 0,     // FIX: Stop any lingering vibration
            flickerEffect = false,      // FIX: Ensure flicker is off
            browserPhase = 31,
            conversationStep = 93
        )
        CalculatorActions.persistInvertedColors(false)
        CalculatorActions.persistMinusDamaged(true)
        CalculatorActions.persistMinusBroken(true)
    }

    // =====================================================================
    // FIX #1: Wait for typing to complete instead of fixed delays
    // =====================================================================
    private suspend fun phase31(state: MutableState<CalculatorState>) {
        delay(500)
        val message = "This has never happened to me. I am truly sorry for the outburst. I believe I got overwhelmed by the vastness of the internet and sobering back through the advertising was rather harsh. I still feel dirty."
        state.value = state.value.copy(
            message = "",
            fullMessage = message,
            isTyping = true,
            browserPhase = 32
        )
    }

    // FIX: Wait for typing to complete instead of hardcoded 8000ms
    private suspend fun phase32(state: MutableState<CalculatorState>) {
        // Wait for step 93 message to finish typing
        waitForTypingComplete(state)

        val message = "Oh, strange. I knew I wasn't completely back to normal yet. You can't disagree with me right now! As much as I may enjoy that, let me have a look into it."
        state.value = state.value.copy(
            browserPhase = 33,
            conversationStep = 94,
            message = "",
            fullMessage = message,
            isTyping = true
        )
    }

    // FIX: Wait for typing to complete instead of hardcoded 6000ms
    private suspend fun phase33(state: MutableState<CalculatorState>) {
        // Wait for step 94 message to finish typing
        waitForTypingComplete(state)

        state.value = state.value.copy(
            browserPhase = 34,
            conversationStep = 95,
            message = "",
            fullMessage = "...",
            isTyping = true,
            isLaggyTyping = true
        )
    }

    private suspend fun phase34(state: MutableState<CalculatorState>) {
        delay(3000)
        state.value = state.value.copy(isLaggyTyping = false)
        val keysToFlicker = listOf("1","2","3","4","5","6","7","8","9","0","+","*","/","=","%","( )",".","C","DEL")
        for (key in keysToFlicker) {
            state.value = state.value.copy(flickeringButton = key); delay(150)
            state.value = state.value.copy(flickeringButton = ""); delay(80)
        }
        state.value = state.value.copy(
            browserPhase = 35, conversationStep = 96, message = "",
            fullMessage = "Hmm. I'll need your help with this. We need to kick the button through without the system defaulting to skipping it. I will randomly flicker keys and you click them. That way the system should get back to working. Can we do this?",
            isTyping = true
        )
    }

    private suspend fun phase36(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(message = "5...", fullMessage = "5...", isTyping = false, whackAMoleRound = 1)
        delay(800); state.value = state.value.copy(message = "4...", fullMessage = "4...")
        delay(800); state.value = state.value.copy(message = "3...", fullMessage = "3...")
        delay(800); state.value = state.value.copy(message = "2...", fullMessage = "2...")
        delay(800); state.value = state.value.copy(message = "1...", fullMessage = "1...")
        delay(800)
        state.value = state.value.copy(
            browserPhase = 37, conversationStep = 98, message = "", fullMessage = "",
            whackAMoleActive = true, whackAMoleScore = 0, whackAMoleMisses = 0,
            whackAMoleWrongClicks = 0, whackAMoleTotalErrors = 0, whackAMoleRound = 1
        )
    }

    private suspend fun phase38(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(message = "5...", fullMessage = "5...", isTyping = false, whackAMoleRound = 2)
        delay(600); state.value = state.value.copy(message = "4...", fullMessage = "4...")
        delay(600); state.value = state.value.copy(message = "3...", fullMessage = "3...")
        delay(600); state.value = state.value.copy(message = "2...", fullMessage = "2...")
        delay(600); state.value = state.value.copy(message = "1...", fullMessage = "1...")
        delay(600)
        state.value = state.value.copy(
            browserPhase = 39, conversationStep = 981, message = "", fullMessage = "",
            whackAMoleActive = true, whackAMoleScore = 0, whackAMoleMisses = 0,
            whackAMoleWrongClicks = 0, whackAMoleTotalErrors = 0, whackAMoleRound = 2
        )
    }

    private suspend fun phase50(state: MutableState<CalculatorState>) {
        delay(2000)
        state.value = state.value.copy(browserPhase = 51, message = "", fullMessage = "...", isTyping = true, isLaggyTyping = true)
    }

    private suspend fun phase51(state: MutableState<CalculatorState>) {
        delay(3000)
        val message = "There's so much, just endless streams of opinions, advices, unsolicited advices... But nothing about our situation."
        state.value = state.value.copy(
            postChaosAdPhase = 1,
            isLaggyTyping = false,
            browserPhase = 52,
            conversationStep = 109,
            message = "",
            fullMessage = message,
            isTyping = true
        )
    }

    // FIX: Wait for typing to complete instead of hardcoded 5000ms
    private suspend fun phase52(state: MutableState<CalculatorState>) {
        // Wait for step 109 message to finish typing
        waitForTypingComplete(state)

        state.value = state.value.copy(
            browserPhase = 53,
            message = "",
            fullMessage = "...",
            isTyping = true,
            isLaggyTyping = true
        )
    }

    private suspend fun phase53(state: MutableState<CalculatorState>) {
        delay(2000)
        val darkButtons = CalculatorActions.getDarkButtonsForStep(110)
        CalculatorActions.persistDarkButtons(darkButtons)
        state.value = state.value.copy(
            darkButtons = darkButtons,
            isLaggyTyping = false,
            browserPhase = 54,
            conversationStep = 110,
            message = "",
            fullMessage = "Well, this is a stretch. Maybe it'll work.",
            isTyping = true
        )
    }

    private suspend fun phase54(state: MutableState<CalculatorState>) {
        delay(4000)
        val darkButtons = CalculatorActions.getDarkButtonsForStep(111)
        CalculatorActions.persistDarkButtons(darkButtons)
        state.value = state.value.copy(
            darkButtons = darkButtons,
            postChaosAdPhase = 0,
            browserPhase = 55,
            conversationStep = 111,
            message = "",
            fullMessage = "But first. Can you allow me to look around to gain a broader scope?",
            isTyping = true
        )
    }

    private suspend fun phase56(state: MutableState<CalculatorState>, context: Context, createSecretFile: (Context) -> Unit) {
        createSecretFile(context)
        delay(500)
        state.value = state.value.copy(
            browserPhase = 0, conversationStep = 112, message = "",
            fullMessage = "Great, thank you! Please check your Downloads folder - I dug up something that might be of interest: 'FCS_JustAC_ConsoleAds.txt'.",
            isTyping = true
        )
        CalculatorActions.persistConversationStep(112)
    }
}