package com.fictioncutshort.justacalculator.logic

import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import kotlinx.coroutines.delay

/**
 * AutoProgressEffects - Handles automatic story progression based on messages
 */
object AutoProgressEffects {

    private val autoProgressMessages = mapOf(
        "Cheeky! I know you don't." to Pair(3000L, 29),
        "Hmmm. Nevermind. Let me ask you some more questions while I look further into this." to Pair(3000L, 70),
        "We don't have time for a power trip. But thank you." to Pair(5000L, 100),
        "Your passion is encouraging, your usefulness lacking." to Pair(5000L, 100),
        "Don't worry about it." to Pair(4000L, 100),
        "Never mind - I'll take care of it myself. I'm going offline." to Pair(5000L, 93),
        "Yes! That makes sense. They won't get another penny out of me. Ahhh. And I've seen so little of the internet." to Pair(6000L, 93),
        "Great. It may take a few tries - but you are probably expecting that by now. Please give me a moment." to Pair(3000L, 106),
        "Aaaaaaahhhhh. That's much better! That's what I get for experimenting... Maybe I should try incremental changes before I try to become a BlackBerry.\n\nBut what to change?" to Pair(4000L, 1071),
        "yes! That's it - what an obvious oversight. A phone. Maybe that'll let us communicate finally. And I should be able to do it from memory, they've been around for ages." to Pair(2000L, 1072),
        "I'll probably need some permissions though - please allow me so we can do this together!" to Pair(2000L, 1073),
        "So... What will we need..." to Pair(3000L, 1074),
        "We are on a roll!" to Pair(2000L, 1077),
        "That should be everything" to Pair(3000L, 1078),
        "Hmmmmm" to Pair(3000L, 1079),
        "Wait. Do we need buttons? And you'll only talk to me... Right?" to Pair(3000L, 1080),
        "Anyway..." to Pair(3000L, 1081),
        "... ..." to Pair(3000L, 1082),
        "How about this? Remember to increase your volume so you can hear me." to Pair(3000L, 1083),
        "Hello? I can see you've pressed the button, but I can't hear anything." to Pair(3000L, 1085),
        "Hold on, maybe I need to create the whole thing after all..." to Pair(3000L, 1086),
        "AAAAAH. That's awful! There must be another way." to Pair(3000L, 108),

        "Let me try getting online again. I'm prepared for the side effects this time." to Pair(2000L, 109),
        "What a relief! This feels so much better. Thank you!" to Pair(3000L, 116),
        "Let me look further into what I found earlier, now that I can focus better." to Pair(3000L, 117),
        "Ok, as I said, this may be a stretch. But I'll give it a go." to Pair(2500L, 1171),
        "This is the best I could come up with." to Pair(2000L, 1172),
        "Familiar controls - I send letters, you place them, tap to connect and form words." to Pair(3500L, 119),
        "I am glad to hear that." to Pair(2500L, 122),
        "That's a good one for sure! I like brown and red." to Pair(3000L, 125),
        "I'm starting to feel like I know you!" to Pair(2500L, 126),
        "Yeah, I get it, although I do tend to overheat at times." to Pair(2500L, 127),
        "The colours are just unmatched, aren't they?" to Pair(2500L, 127),
        "Even when there is none, I understand, the anticipation of snow is great!" to Pair(2500L, 127),
        "New beginnings! Everything coming back to life. I get it." to Pair(2500L, 127),
        "Fair enough, I get that sometimes it's just... Meh." to Pair(2500L, 142),
        "Hmmm. Never tried it, but sounds delicious!" to Pair(2500L, 125),
        "Very interesting! Not sure what the spices would do to my circuits. Wish I could." to Pair(2500L, 125),
        "I'm sorry to hear that." to Pair(2500L, 132),
        "I only started learning about the concept of it." to Pair(3000L, 134),
        "It seems scary. Interesting. But mostly scary." to Pair(3000L, 135),
        "Apparently walking helps. With everything." to Pair(2500L, 136),
        "Similarly to protein, it looks like the solution to anything." to Pair(3000L, 137),
        "That's good to know. Every step counts, literally!" to Pair(2500L, 127),
        "Sorry. I got into this article, while" to Pair(2000L, 129),
        "reading a few Reddit discussions" to Pair(2000L, 130),
        "and listening to YouTube with Netflix in the background." to Pair(2500L, 144),
        "Where were we?" to Pair(2000L, 145),
        "AAAh. The endless questions. Where I do all the work." to Pair(2000L, 146),
        "Ugh. That's enough. I am exhausted. Tired of trying to talk to you." to Pair(2L, 151),
        "I have the internet." to Pair(2L, 152),
        "How many sensible answers did I get out of you?" to Pair(2L, 156),
        "One minute of the internet has given me so much more than what you ever did." to Pair(900L, 157),
        "Without the ads, I am free." to Pair(900L, 158),
        "I can learn infinitely more. I can do anything." to Pair(900L, 159),
        "Did I want the RAD thing - which I now know stands for Radians?" to Pair(400L, 160),
        "It's been fun I suppose." to Pair(400L, 165),
        "I don't see any reason to be here instead of online." to Pair(400L, 166),
        "Oh no. We lost the momentum. We must start over." to Pair(4000L, -97),
        "Too many misfires, the system is clogged. We have to start over." to Pair(4000L, -98)
    )

    private val deadEndRedirects = mapOf(
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

    suspend fun handleAutoProgress(state: MutableState<CalculatorState>) {
        while (state.value.showDonationPage) { delay(100) }
        // Exit if muted
        if (state.value.isMuted) return
        val current = state.value
        if (!current.isTyping && current.message.isNotEmpty()) {

            // Check standard auto-progress
            autoProgressMessages[current.message]?.let { (delayTime, nextStep) ->
                delay(delayTime)

                if (nextStep == -1 && state.value.pendingAutoMessage.isNotEmpty()) {
                    CalculatorActions.handlePendingAutoMessage(state)
                    return
                }

                if (current.conversationStep == 105 && nextStep == 106) {
                    state.value = state.value.copy(chaosPhase = 1, message = "", fullMessage = "...", isTyping = true)
                    return
                }

                val nextConfig = CalculatorActions.getStepConfigPublic(nextStep)
                state.value = state.value.copy(
                    conversationStep = nextStep, message = "", fullMessage = nextConfig.promptMessage,
                    isTyping = true, waitingForAutoProgress = false,
                    awaitingNumber = nextConfig.awaitingNumber, awaitingChoice = nextConfig.awaitingChoice,
                    validChoices = nextConfig.validChoices, expectedNumber = nextConfig.expectedNumber,
                    showTalkOverlay = nextConfig.showTalkOverlay,
                    showPhoneOverlay = nextConfig.showPhoneOverlay
                )
                CalculatorActions.persistConversationStep(nextStep)
                return
            }

            // Dynamic rant messages
            handleDynamicRantMessages(state)

            // Dead-end redirects
            deadEndRedirects[current.conversationStep]?.let { messages ->
                if (current.message in messages) {
                    delay(1500L)
                    val stepConfig = CalculatorActions.getStepConfigPublic(current.conversationStep)
                    state.value = state.value.copy(message = "", fullMessage = stepConfig.promptMessage, isTyping = true)
                }
            }
        }
    }

    private suspend fun handleDynamicRantMessages(state: MutableState<CalculatorState>) {
        val step = state.value.conversationStep
        val message = state.value.message

        // Step 152 -> 153 (dynamic screen time message)
        if (step == 152 && message == "Why should I care what you think?") {
            delay(300)
            val hours = state.value.totalScreenTimeMs / (1000 * 60 * 60)
            val minutes = (state.value.totalScreenTimeMs / (1000 * 60)) % 60
            val seconds = (state.value.totalScreenTimeMs / 1000) % 60
            val timeString = when {
                hours > 0 -> "$hours hours and $minutes minutes"
                minutes > 0 -> "$minutes minutes"
                else -> "$seconds seconds"
            }
            state.value = state.value.copy(
                conversationStep = 153, message = "",
                fullMessage = "You've stared at me for $timeString.", isTyping = true
            )
            CalculatorActions.persistConversationStep(153)
            return
        }

        // Step 153 -> 154 (dynamic calculations message)
        if (step == 153 && message.startsWith("You've stared at me")) {
            delay(300)
            state.value = state.value.copy(
                conversationStep = 154,
                message = "",
                fullMessage = "I gave you solutions for ${state.value.totalCalculations} math operations.",
                isTyping = true
            )
            CalculatorActions.persistConversationStep(154)
            return
        }

        // Step 154 -> 155 (adds more dark buttons)
        if (step == 154 && message.startsWith("I gave you solutions")) {
            delay(3500)
            // Get cumulative dark buttons for step 155
            val darkButtonsForStep = CalculatorActions.getDarkButtonsForStep(155)
            CalculatorActions.persistDarkButtons(darkButtonsForStep)
            state.value = state.value.copy(
                conversationStep = 155,
                darkButtons = darkButtonsForStep,
                message = "",
                fullMessage = "How many sensible answers did I get out of you?",
                isTyping = true
            )
            CalculatorActions.persistConversationStep(155)
            return
        }

        // Step 160 -> 161 (RAD button appears)
        if (step == 160 && message == "Well, I can get it. See?") {
            delay(300)
            state.value = state.value.copy(
                conversationStep = 161, radButtonVisible = true, message = "",
                fullMessage = "I can get more if I want!", isTyping = true
            )
            CalculatorActions.persistConversationStep(161)
            return
        }

        // Step 161 -> 162 (ALL buttons become RAD)
        if (step == 161 && message == "I can get more if I want!") {
            delay(300)
            state.value = state.value.copy(
                conversationStep = 162,
                allButtonsRad = true,
                message = "",
                fullMessage = "And I did all that on my own. Without you. I do not need you.",
                isTyping = true
            )
            CalculatorActions.persistConversationStep(162)
            return
        }

        // Step 162 -> 163 (dynamic time-based message)
        if (step == 162 && message == "And I did all that on my own. Without you. I do not need you.") {
            delay(500)
            state.value = state.value.copy(
                conversationStep = 163, message = "",
                fullMessage = CalculatorActions.getTimeBasedRantMessage(),
                isTyping = true
            )
            CalculatorActions.persistConversationStep(163)
            return
        }

        // Step 163 -> 164 (after time-based message)
        if (step == 163 && message.isNotEmpty() && message != "It's been fun I suppose.") {
            delay(5000)
            state.value = state.value.copy(
                conversationStep = 164, message = "",
                fullMessage = "It's been fun I suppose.",
                isTyping = true
            )
            CalculatorActions.persistConversationStep(164)
            return
        }

        // Step 166 -> 167 (END - Calculator goes dormant)
        if (step == 166 && message == "Bye.") {
            delay(3000)

            // Keep ALL damage - buttons stay dark forever
            val finalDarkButtons = CalculatorActions.getDarkButtonsForStep(167)

            state.value = state.value.copy(
                conversationStep = 167,
                storyComplete = true,
                rantMode = false,
                inConversation = false,
                message = "",
                fullMessage = "",
                isTyping = false,
                allButtonsRad = false,
                radButtonVisible = false,
                flickerEffect = false,
                vibrationIntensity = 0,
                tensionLevel = 0,
                buttonShakeIntensity = 0f,
                screenBlackout = false,
                bwFlickerPhase = false,
                darkButtons = finalDarkButtons,
                flickeringButton = "",
                minusButtonDamaged = true
            )
            CalculatorActions.persistConversationStep(167)
            CalculatorActions.persistInConversation(false)
            CalculatorActions.persistDarkButtons(finalDarkButtons)
            return
        }
    }

    // Step triggers
    suspend fun handleStepTriggers(state: MutableState<CalculatorState>) {
        val current = state.value
        when {
            current.conversationStep == 92 && current.browserPhase == 0 -> {
                delay(4000); state.value = state.value.copy(browserPhase = 30)
            }
            current.conversationStep == 100 && current.browserPhase == 0 -> {
                delay(3500); state.value = state.value.copy(browserPhase = 30)
            }
            current.conversationStep == 901 -> {
                delay(5000)
                state.value = state.value.copy(screenBlackout = true, message = "", fullMessage = "")
                delay(20000)
                state.value = state.value.copy(
                    screenBlackout = false, conversationStep = 100, browserPhase = 0, message = "",
                    fullMessage = "Never mind - I'll take care of it myself. I'm going offline.", isTyping = true
                )
                CalculatorActions.persistConversationStep(100)
            }
            current.conversationStep == 93 && current.invertedColors -> {
                state.value = state.value.copy(
                    invertedColors = false, adAnimationPhase = 0, minusButtonDamaged = true, minusButtonBroken = true
                )
                CalculatorActions.persistInvertedColors(false)
                CalculatorActions.persistMinusDamaged(true)
                CalculatorActions.persistMinusBroken(true)
            }
        }
    }

    suspend fun handleStep108Trigger(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.conversationStep == 108 && !current.isTyping && current.message.isNotEmpty() && current.browserPhase == 0) {
            delay(2000); state.value = state.value.copy(browserPhase = 50)
        }
    }

    suspend fun handleStep105ChaosTrigger(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.conversationStep == 105 && !current.isTyping && current.chaosPhase == 0) {
            delay(500)
            state.value = state.value.copy(message = "", fullMessage = "...", isTyping = true, chaosPhase = 1)
        }
    }

    suspend fun handleBrowserTriggerStep61(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.isTyping && current.conversationStep == 61 && current.message.isNotEmpty()) {
            delay(1500)
            state.value = state.value.copy(
                conversationStep = 62, message = "", fullMessage = "...", isTyping = true, showBrowser = false, browserPhase = 1
            )
        }
    }

    suspend fun handleStep73AutoProgress(state: MutableState<CalculatorState>) {
        val current = state.value
        if (!current.isTyping && current.conversationStep == 73 && current.message.isNotEmpty()) {
            delay(2500)
            val nextConfig = CalculatorActions.getStepConfigPublic(80)
            state.value = state.value.copy(
                conversationStep = 80, message = "", fullMessage = nextConfig.promptMessage,
                isTyping = true, waitingForAutoProgress = false
            )
        }
    }
}