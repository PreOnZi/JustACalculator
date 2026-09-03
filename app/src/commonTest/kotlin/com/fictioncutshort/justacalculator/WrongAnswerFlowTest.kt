package com.fictioncutshort.justacalculator

import androidx.compose.runtime.mutableStateOf
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.data.getStepConfig
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.logic.GuessCoach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the buttons the player actually presses, through the handler the app
 * actually runs.
 *
 * The first version of these tests called StoryManager.handleNumberAnswer,
 * which nothing reaches — InputHandler, its only caller, is never invoked. They
 * passed while the game showed a blank screen and stopped responding. Anything
 * asserting on story flow has to go in via CalculatorActions.handleInput or it
 * proves nothing.
 */
class WrongAnswerFlowTest {

    private fun stateAt(step: Int) = mutableStateOf(
        CalculatorState(
            conversationStep = step,
            inConversation = true,
            awaitingNumber = true,
            isEnteringAnswer = true,
            expectedNumber = getStepConfig(step).expectedNumber,
        )
    )

    /** Types [year] on the keypad and confirms with ++, exactly as a player would. */
    private fun answer(state: androidx.compose.runtime.MutableState<CalculatorState>, year: String) {
        for (c in year) CalculatorActions.handleInput(state, c.toString())
        CalculatorActions.handleInput(state, "+")
        CalculatorActions.handleInput(state, "+")
    }

    @Test
    fun aWrongYearAlwaysLeavesSomethingOnScreen() {
        for (step in listOf(3, 4, 6, 7, 8, 11, 12, 22)) {
            val state = stateAt(step)
            answer(state, "1500")
            val s = state.value
            assertTrue(
                s.fullMessage.isNotEmpty(),
                "step $step: blank screen after a wrong year",
            )
            // A message with nothing to type would wedge the typing effect.
            assertTrue(
                !s.isTyping || s.fullMessage.isNotEmpty(),
                "step $step: typing with nothing to reveal",
            )
        }
    }

    @Test
    fun aWrongYearCarriesAHintAndRestatesTheQuestion() {
        val state = stateAt(3)
        answer(state, "1564")          // 59 short of 1623
        val msg = state.value.fullMessage
        assertTrue(msg.contains("going up", ignoreCase = true), "no upward nudge in: ${msg.take(140)}")
        assertTrue(msg.contains("Anjar"), "the question should still be there")
    }

    @Test
    fun aWrongYearDoesNotAdvanceTheStory() {
        val state = stateAt(3)
        answer(state, "1564")
        assertEquals(3, state.value.conversationStep)
        assertTrue(state.value.awaitingNumber)
    }

    @Test
    fun theRightYearStillAdvances() {
        val state = stateAt(3)
        answer(state, "1623")
        assertEquals(4, state.value.conversationStep, "Anjar should lead to Minh Mang")
        assertTrue(state.value.fullMessage.isNotEmpty())
    }

    @Test
    fun aRetryRestatesOnlyTheQuestion() {
        val state = stateAt(3)
        answer(state, "1564")
        val msg = state.value.fullMessage
        assertTrue(msg.contains("When was the Battle of Anjar?"), "the question should be repeated")
        // Everything the player only needed once must be gone.
        assertTrue("Look it up or guess" !in msg, "the guessing invitation came back: $msg")
        assertTrue("confirm with ++" !in msg, "the ++ instruction came back: $msg")
        assertTrue("too much" !in msg, "the framing came back: $msg")
    }

    @Test
    fun aRetryOnQ2DropsTheGuessingOfferToo() {
        val state = stateAt(4)
        answer(state, "1700")
        val msg = state.value.fullMessage
        assertTrue(msg.contains("When did Minh Mang start ruling Vietnam?"))
        assertTrue("Want to guess again" !in msg, "Q2's offer repeated on a retry: $msg")
        assertTrue("Look it up again" !in msg, "Q2's offer repeated on a retry: $msg")
    }

    @Test
    fun everyRetryIsShorterThanTheFirstAsking() {
        for (step in listOf(3, 4, 6, 7, 8, 11, 12, 22)) {
            val full = getStepConfig(step).promptMessage
            val short = GuessCoach.shortQuestion(step)
            assertTrue(short != null, "step $step has no short form")
            assertTrue(short!!.length <= full.length, "step $step's retry is not shorter")
        }
    }

    @Test
    fun shortQuestionsAreVerbatim() {
        // They must be slices of the real prompt, never a reworded paraphrase —
        // otherwise the retry drifts away from what the calculator actually asked.
        for (step in listOf(3, 4, 6, 7, 8, 11, 12, 22)) {
            val full = getStepConfig(step).promptMessage
            val short = GuessCoach.shortQuestion(step)!!
            assertTrue(full.contains(short), "step $step's short form is not verbatim:\n  $short\n  not in: $full")
        }
    }

    @Test
    fun everyYearQuestionAcceptsItsOwnAnswer() {
        val answers = mapOf(
            3 to "1623", 4 to "1820", 6 to "1834", 7 to "1921",
            8 to "1947", 11 to "1948", 12 to "1957", 22 to "1963",
        )
        for ((step, year) in answers) {
            val state = stateAt(step)
            answer(state, year)
            assertEquals(
                getStepConfig(step).nextStepOnSuccess,
                state.value.conversationStep,
                "step $step did not advance on its correct answer",
            )
        }
    }
}
