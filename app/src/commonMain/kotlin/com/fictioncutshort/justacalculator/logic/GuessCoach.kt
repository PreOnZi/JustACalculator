package com.fictioncutshort.justacalculator.logic

import com.fictioncutshort.justacalculator.util.NamePool
import kotlin.math.abs

/**
 * The calculator's reply to a wrong year, in Phase 1's "when was…" questions.
 *
 * The player is told to look the answers up, and most will. Some would rather
 * guess, and guessing at a bare "That's not right" is no fun — so a wrong year
 * now comes back with a bearing on how far off it was.
 *
 * A reply is three parts: an opener, a connector, and one hint.
 *
 *     "Incorrect. Though... Try going up."
 *
 * The connector is punctuated as its own trailing-off sentence rather than run
 * into the hint. That is what lets "Although"/"Though" sit in front of an
 * imperative at all — "Incorrect. Although try going up." is broken English —
 * and it reads like the calculator catching itself mid-thought, which is the
 * voice the rest of the script has.
 *
 * Only ever ONE hint per reply. The player can always look it up.
 */
object GuessCoach {

    /** Phase 1's year questions. Everything else keeps its own wrong-answer line. */
    val YEAR_QUESTION_STEPS = setOf(3, 4, 6, 7, 8, 11, 12, 22)

    fun isYearQuestion(step: Int): Boolean = step in YEAR_QUESTION_STEPS

    private val OPENERS = listOf(
        "EEEEEEEEEEEEEeeeeee. No.",
        "No. No. No. NO!",
        "That is, indeed, wrong.",
        "Incorrect.",
        "Computer says 'no'.",
        "WRONG!!!! Sorry, I mean: wrong. :)",
        "Not quite.",
        "Well, I wish it was right, but it isn't.",
        "You may be right elsewhere, but not here.",
        "Hmmmm. No.",
        "Profoundly wrong. I think.",
    )

    /** Every connector but "And", which is reserved for a guess that got worse. */
    private val CONNECTORS = listOf("But", "However", "Although", "Though")
    private const val REGRESS_CONNECTOR = "And"

    /**
     * One candidate hint. [regress] marks the ones that only make sense when the
     * player has just moved away from the answer, and which take "And" instead.
     */
    data class Hint(val text: String, val regress: Boolean = false)

    /**
     * Every hint that is actually TRUE of this guess.
     *
     * Deliberately a filter rather than a band lookup: each line asserts
     * something concrete — a direction, a distance, a trend — and offering one
     * that does not hold would be worse than saying nothing. Far out, only the
     * century-scale lines qualify; close in, only the fine ones do, which is
     * what makes the hints sharpen as the player closes on the year without
     * anything having to track "how precise should I be by now".
     *
     * [previous] is the guess before this one, or null on the first wrong try.
     */
    fun hintsFor(answer: Int, guess: Int, previous: Int?): List<Hint> {
        val d = answer - guess
        val ad = abs(d)
        if (ad == 0) return emptyList()

        val out = ArrayList<Hint>()
        val previousDistance = previous?.let { abs(answer - it) }

        // A century or more out: say it in centuries. "Try going up" is true here
        // too, but it is not the useful thing to say when they are 300 years away.
        if (ad >= 100) {
            out += Hint(if (d > 0) "try the century after." else "try the century before.")
            val hundreds = (ad + 50) / 100
            if (hundreds >= 1) {
                out += Hint(
                    if (d > 0) "add $hundreds hundred and you'll be much closer."
                    else "subtract $hundreds hundred and you'll be much closer."
                )
            }
        }
        if (ad > 200) out += Hint("give it another go, try something completely different.")

        // Inside a century, a bare direction is the most useful thing there is.
        if (ad < 100) out += Hint(if (d > 0) "try going up." else "try going down.")

        // Closing in. The ranges overlap on purpose so there is more than one
        // thing the calculator can say at any given distance.
        if (ad in 10..20) out += Hint("you are only about 10 to 20 years off.")
        if (ad in 15..30) out += Hint("give and take 20 years...")
        if (ad in 1..25) out += Hint("you are in the ballpark.")
        if (previousDistance != null && previousDistance > ad && ad < 100) {
            out += Hint("you are getting closer.")
        }

        if (previousDistance != null && previousDistance < ad) {
            out += Hint("you are getting further.", regress = true)
            out += Hint("you are getting worse at this.", regress = true)
        }
        return out
    }

    /** Assembles the three parts. [hint] is lower-case; it starts a sentence here. */
    fun compose(opener: String, connector: String, hint: String): String =
        "$opener $connector... " + hint.replaceFirstChar { it.uppercase() }

    // ── Session state ─────────────────────────────────────────────────────────
    // Rotations run for the whole playthrough so an opener does not come round
    // again two questions later; the per-question tracking resets on each new
    // question. None of it is persisted: a mid-question relaunch losing the
    // attempt count costs the player nothing.

    private val openerPool = NamePool()
    private val connectorPool = NamePool()
    private var trackedStep = -1
    private var previousGuess: Int? = null
    private var lastHint: String? = null

    /**
     * Whether Q1 took more than one try — i.e. they guessed rather than looked it
     * up. Read straight from prefs rather than cached: a story reset clears the
     * key, and a stale copy here would word Q2 for a playthrough that is over.
     */
    fun guessedQ1(): Boolean = CalculatorActions.loadQ1Guessed()

    private fun markQ1Guessed() {
        if (!guessedQ1()) CalculatorActions.persistQ1Guessed(true)
    }

    /** Clears the per-question tracking when the player moves to a new question. */
    private fun retarget(step: Int) {
        if (trackedStep != step) {
            trackedStep = step
            previousGuess = null
            lastHint = null
        }
    }

    /** Called on a correct answer, so returning to the question later starts clean. */
    fun onCorrect(step: Int) {
        if (trackedStep == step) {
            trackedStep = -1
            previousGuess = null
            lastHint = null
        }
    }

    /**
     * The reply to [guess] at [step], whose answer is [answer]. Returns null when
     * there is nothing honest to say (an unparseable entry), leaving the caller
     * to fall back to the step's own line.
     */
    fun reply(step: Int, answer: String, guess: String): String? {
        // The keypad has a decimal point, so "1623.5" is a reachable entry; take
        // the whole years rather than refusing to coach on it.
        val answerYear = answer.toIntOrNull() ?: answer.toDoubleOrNull()?.toInt() ?: return null
        val guessYear = guess.toIntOrNull() ?: guess.toDoubleOrNull()?.toInt() ?: return null
        retarget(step)

        // Needing a second go at Q1 is what "guessed" means; Q2 asks differently
        // depending on it.
        if (step == 3) markQ1Guessed()

        val candidates = hintsFor(answerYear, guessYear, previousGuess)
        previousGuess = guessYear
        if (candidates.isEmpty()) return null

        // Avoid saying the same thing twice running where there is an alternative.
        val fresh = candidates.filter { it.text != lastHint }
        val pool = fresh.ifEmpty { candidates }
        val hint = pool[openerCounter() % pool.size]
        lastHint = hint.text

        val opener = openerPool.next(OPENERS) { it.first() } ?: OPENERS.first()
        val connector = if (hint.regress) REGRESS_CONNECTOR
        else connectorPool.next(CONNECTORS) { it.first() } ?: CONNECTORS.first()
        return compose(opener, connector, hint.text)
    }

    // Cheap varying index; the pools above own the no-repeat guarantees.
    private var counter = 0
    private fun openerCounter(): Int = counter++
}
