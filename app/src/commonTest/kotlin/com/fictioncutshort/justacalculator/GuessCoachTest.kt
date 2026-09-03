package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.logic.GuessCoach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The hints tell the player something concrete about their guess, so the thing
 * that matters is that every line offered is actually TRUE of it. A hint that
 * points the wrong way is worse than no hint — the player would trust it.
 */
class GuessCoachTest {

    private fun texts(answer: Int, guess: Int, previous: Int? = null) =
        GuessCoach.hintsFor(answer, guess, previous).map { it.text }

    // ── Direction ─────────────────────────────────────────────────────────────

    @Test
    fun neverPointsTheWrongWay() {
        // Anjar is 1623. Sweep guesses either side and check no line ever sends
        // the player away from the answer.
        for (guess in 1000..2200 step 7) {
            if (guess == 1623) continue
            val t = texts(1623, guess)
            val tooLow = guess < 1623
            if (t.any { it.contains("going up") || it.contains("century after") || it.startsWith("add") }) {
                assertTrue(tooLow, "told to go up from $guess when the answer is 1623")
            }
            if (t.any { it.contains("going down") || it.contains("century before") || it.startsWith("subtract") }) {
                assertFalse(tooLow, "told to go down from $guess when the answer is 1623")
            }
        }
    }

    @Test
    fun addAndSubtractMatchTheGapAndTheDirection() {
        assertTrue("add 2 hundred and you'll be much closer." in texts(1820, 1620))
        assertTrue("subtract 2 hundred and you'll be much closer." in texts(1620, 1820))
        // Rounded to the nearest hundred.
        assertTrue("add 3 hundred and you'll be much closer." in texts(1947, 1670))
    }

    // ── Distance claims ───────────────────────────────────────────────────────

    @Test
    fun distanceClaimsAreOnlyMadeWhenTrue() {
        for (answer in listOf(1623, 1820, 1947)) {
            for (guess in (answer - 400)..(answer + 400)) {
                if (guess == answer) continue
                val d = kotlin.math.abs(answer - guess)
                val t = texts(answer, guess)
                if ("you are only about 10 to 20 years off." in t) {
                    assertTrue(d in 10..20, "claimed 10-20 years at a gap of $d")
                }
                if ("you are in the ballpark." in t) {
                    assertTrue(d <= 25, "claimed ballpark at a gap of $d")
                }
                if ("give and take 20 years..." in t) {
                    assertTrue(d in 15..30, "claimed give-or-take-20 at a gap of $d")
                }
                if ("give it another go, try something completely different." in t) {
                    assertTrue(d > 200, "called it completely different at a gap of $d")
                }
            }
        }
    }

    @Test
    fun centuryLinesOnlyAppearACenturyOut() {
        assertTrue(texts(1947, 1500).any { it.contains("century") })
        assertFalse(texts(1947, 1900).any { it.contains("century") })
        // And close in, the bare direction is offered instead.
        assertTrue("try going up." in texts(1947, 1900))
    }

    // ── Trend ─────────────────────────────────────────────────────────────────

    @Test
    fun trendLinesNeedAPreviousGuessAndTheRightDirection() {
        val progress = listOf("you are getting closer.", "you are getting further.",
                              "you are getting worse at this.")
        // First wrong guess: nothing to compare against.
        assertTrue(texts(1947, 1900).none { it in progress })
        // Moved nearer.
        assertTrue("you are getting closer." in texts(1947, 1930, previous = 1900))
        assertFalse("you are getting further." in texts(1947, 1930, previous = 1900))
        // Moved away.
        assertTrue("you are getting further." in texts(1947, 1900, previous = 1930))
        assertFalse("you are getting closer." in texts(1947, 1900, previous = 1930))
    }

    @Test
    fun onlyTheRegressLinesTakeTheAndConnector() {
        val worse = GuessCoach.hintsFor(1947, 1800, previous = 1930)
        assertTrue(worse.filter { it.regress }.map { it.text }
            .containsAll(listOf("you are getting further.", "you are getting worse at this.")))
        // Everything else must not be marked regress, or it would take "And".
        for (h in GuessCoach.hintsFor(1947, 1930, previous = 1900)) {
            assertFalse(h.regress, "'${h.text}' should not take the And connector")
        }
    }

    // ── Assembly ──────────────────────────────────────────────────────────────

    @Test
    fun theConnectorIsItsOwnSentenceSoAlthoughWorksBeforeAnImperative() {
        assertEquals(
            "Incorrect. Though... Try going up.",
            GuessCoach.compose("Incorrect.", "Though", "try going up.")
        )
        assertEquals(
            "Hmmmm. No. And... You are getting worse at this.",
            GuessCoach.compose("Hmmmm. No.", "And", "you are getting worse at this.")
        )
    }

    @Test
    fun thereIsAlwaysSomethingToSayAboutAWrongYear() {
        for (answer in listOf(1623, 1820, 1834, 1921, 1947, 1948, 1957, 1963)) {
            for (guess in 0..3000 step 13) {
                if (guess == answer) continue
                assertTrue(texts(answer, guess).isNotEmpty(), "nothing to say for $guess vs $answer")
            }
        }
    }

    @Test
    fun aCorrectYearHasNoHints() {
        assertTrue(texts(1623, 1623).isEmpty())
    }

    @Test
    fun onlyPhaseOneYearQuestionsAreCoached() {
        for (s in listOf(3, 4, 6, 7, 8, 11, 12, 22)) {
            assertTrue(GuessCoach.isYearQuestion(s), "step $s should be coached")
        }
        // Step 10 asks the player's age — there is no answer to be near.
        assertFalse(GuessCoach.isYearQuestion(10))
        assertFalse(GuessCoach.isYearQuestion(5))
    }
}
