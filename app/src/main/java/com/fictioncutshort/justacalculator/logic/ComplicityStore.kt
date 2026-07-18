package com.fictioncutshort.justacalculator.logic

import android.content.Context

/**
 * ComplicityStore.kt
 *
 * Which of the two endings the player gets.
 *
 * The axis is COMPLICITY: did the player take what the app kept dangling in
 * front of them, or did they refuse it?
 *
 *   Phase 1 (the calculator) — every conversation step forks on ++ / -- (see
 *     Stepconfig's nextStepOnSuccess / nextStepOnDecline). Going along with the
 *     calculator is complicity; pushing back is refusal. This is a running tally
 *     of both, not a single flag, because one stray tap should not decide an
 *     ending — the SHAPE of a whole playthrough should.
 *
 *   Phase 2 (the city) — two decisions, both deliberate and both late:
 *     · Building 6: use your friends to get through, or PAY to go it alone.
 *       Paying is the purest complicity in the game — it converts money into
 *       the removal of other people.
 *     · Building 10: the rating slider. The game asks the player to score their
 *       stay, and their answer is taken at face value: a high score is approval
 *       of everything that was done to them.
 *
 * Nothing here reads a live UI state — it's all persisted, so the ending can be
 * computed at any point, including after a relaunch.
 *
 * Kept in the same "calc_city" store as the rest of the city so the debug menu's
 * RESET CITY clears it too.
 */
object ComplicityStore {

    private const val PREFS_NAME = "calc_city"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Phase 1 — the conversation ────────────────────────────────────────────
    //
    // NOT every ++/-- is a complicity signal. "++" only means "went along with it"
    // where the calculator is actually asking something OF the player — a
    // permission, an obedience, a consent. Elsewhere the polarity is meaningless
    // or outright inverted: at step 28 ("Do you know why I asked for the specific
    // events earlier?") a "++" is the player LYING, and the calculator answers
    // "Cheeky! I know you don't." Both of its branches go to the same step — it's
    // characterisation, not a choice. Counting it would be noise.
    //
    // So: nothing counts unless it is listed here. The weight is how much the beat
    // actually costs the player — handing over the camera is not the same as
    // indulging one more trivia question.
    //
    // Where "++" means going along with the calculator:
    private val COMPLY_ON_PLUS: Map<Int, Int> = mapOf(
        // Permission grabs — the sharpest signal in the whole phase.
        19 to 3,     // camera: "Could you... Perhaps... Show me around?"
        1071 to 3,   // microphone: voice talking
        1075 to 3,   // location: "Can I have a look [where we are]?"
        1076 to 3,   // contacts: "what's a phone with nobody to call?"
        // Explicit obedience.
        13 to 3,     // "Will you play by the rules now?"
        // "So you agree, that my centuries of knowledge are justified to be
        // exploited by some schmuck?" Agreeing IS the complicit answer — it signs
        // off on the exploitation. Refusing with "--" is the right move, even
        // though it costs you the calculator's goodwill. Heavy: it's a real fork
        // in the story (++ -> 901, -- -> 100), not a passing remark.
        90 to 3,
        // Consent to being profiled / worked on.
        23 to 2,     // "Can I get to know you better?"
        25 to 2,     // same question, second ask
        104 to 2,    // "Maybe I can give you more agency" (-- = "I will not bother you")
        108 to 2,    // "Let me try getting online again" — despite the side effects
        5 to 2,      // "Is this fun for you?" (-- = "I see you figured out how to disagree")
        // Softer: going along with what it wants to do.
        1 to 1,      // "Can I call you Rad?" — consent to being renamed
        2 to 1,      // interest in its queue of questions
        20 to 1,     // "One more legacy question. Please?"
        21 to 1,     // same
        60 to 1,     // "many more dates to explore" (-- = "I am in charge")
    )

    // Where "--" is the compliant answer and "++" is the resistant one. Empty for
    // now, but the mechanism exists: if a beat is written so that agreeing means
    // defying the calculator, list it here rather than bending the caller.
    private val COMPLY_ON_MINUS: Map<Int, Int> = mapOf()

    // Deliberately NOT counted: 0 (must agree to play at all), 28 (the lie —
    // "++" claims you knew, and it answers "Cheeky! I know you don't"),
    // 24 ("Well, you don't have a choice."), 27/29/30/40/41/50 (opinions and
    // characterisation), 26/42/51/70/71/91 (multiple-choice experience questions),
    // and 63/96/97/102/971 (mechanics and navigation).

    /** One entry per step, so re-answering a looping step overwrites rather than stacks. */
    private fun stepKey(step: Int) = "cx_p1_s$step"

    // ── Phase 2 — the two deliberate choices ─────────────────────────────────
    // Building 6: true if the player paid to solo the run rather than leaning on
    // the crowd of friends.
    private const val P2_PAID_TO_SOLO = "cx_b6_paid"
    private const val P2_B6_ANSWERED = "cx_b6_done"
    // Building 10: the 0..10 rating the player gave the city.
    private const val P2_RATING = "cx_b10_rating"
    private const val P2_RATED = "cx_b10_rated"

    /**
     * Records one conversation fork, from handleConversationResponse. Steps that
     * aren't in either polarity map are ignored outright — most of the script's
     * ++/-- pairs say nothing about complicity.
     *
     * The answer is stored PER STEP, so a step the player loops back onto (several
     * decline branches point at themselves) records their latest answer instead of
     * stacking one opinion up over and over.
     */
    fun recordConversationChoice(ctx: Context?, step: Int, accepted: Boolean) {
        val c = ctx ?: return
        if (step !in COMPLY_ON_PLUS && step !in COMPLY_ON_MINUS) return
        val compliant = if (step in COMPLY_ON_MINUS) !accepted else accepted
        prefs(c).edit().putBoolean(stepKey(step), compliant).apply()
    }

    /** Weight of the beats the player has actually reached and answered. */
    private fun answered(ctx: Context): List<Pair<Int, Boolean>> {
        val p = prefs(ctx)
        val steps = COMPLY_ON_PLUS.keys + COMPLY_ON_MINUS.keys
        return steps.filter { p.contains(stepKey(it)) }
            .map { it to p.getBoolean(stepKey(it), false) }
    }

    private fun weightOf(step: Int) = COMPLY_ON_PLUS[step] ?: COMPLY_ON_MINUS[step] ?: 0

    fun wentAlongCount(ctx: Context): Int = answered(ctx).count { it.second }
    fun pushedBackCount(ctx: Context): Int = answered(ctx).count { !it.second }

    /**
     * Phase 1's contribution, 0..1, where 1 is total compliance — the weighted
     * share of the beats that count. Neutral (0.5) until enough weight has been
     * answered that the shape of a playthrough is actually visible.
     */
    fun phase1Complicity(ctx: Context): Float {
        val ans = answered(ctx)
        val total = ans.sumOf { weightOf(it.first) }
        if (total < 6) return 0.5f
        val compliant = ans.filter { it.second }.sumOf { weightOf(it.first) }
        return compliant.toFloat() / total
    }

    fun recordBuilding6(ctx: Context, paidToSolo: Boolean) {
        prefs(ctx).edit()
            .putBoolean(P2_PAID_TO_SOLO, paidToSolo)
            .putBoolean(P2_B6_ANSWERED, true)
            .apply()
    }

    fun paidToSolo(ctx: Context): Boolean = prefs(ctx).getBoolean(P2_PAID_TO_SOLO, false)
    fun building6Answered(ctx: Context): Boolean = prefs(ctx).getBoolean(P2_B6_ANSWERED, false)

    fun recordRating(ctx: Context, rating: Int) {
        prefs(ctx).edit()
            .putInt(P2_RATING, rating.coerceIn(0, 10))
            .putBoolean(P2_RATED, true)
            .apply()
    }

    fun rating(ctx: Context): Int = prefs(ctx).getInt(P2_RATING, -1)
    fun rated(ctx: Context): Boolean = prefs(ctx).getBoolean(P2_RATED, false)

    /**
     * The whole playthrough as one number, 0..1. Phase 2's two decisions are
     * deliberate and late, so they carry more than the drip of phase 1.
     *
     *   phase 1 conversation .... 40%
     *   Building 6 (pay to solo)  30%
     *   Building 10 (the rating)  30%
     */
    fun score(ctx: Context): Float {
        val p1 = phase1Complicity(ctx)
        val b6 = if (!building6Answered(ctx)) 0.5f else if (paidToSolo(ctx)) 1f else 0f
        val r = rating(ctx)
        val b10 = if (!rated(ctx)) 0.5f else (r.coerceIn(0, 10) / 10f)
        return p1 * 0.40f + b6 * 0.30f + b10 * 0.30f
    }

    /** True when the playthrough reads as complicit — the "became the product" ending. */
    fun isComplicitEnding(ctx: Context): Boolean = score(ctx) >= 0.5f

    /**
     * Debug only: slam every input to one end so an ending can be tested without
     * replaying the whole story.
     */
    fun forceEnding(ctx: Context, complicit: Boolean) {
        val e = prefs(ctx).edit()
        for (step in COMPLY_ON_PLUS.keys + COMPLY_ON_MINUS.keys) {
            e.putBoolean(stepKey(step), complicit)
        }
        e.putBoolean(P2_PAID_TO_SOLO, complicit)
            .putBoolean(P2_B6_ANSWERED, true)
            .putInt(P2_RATING, if (complicit) 10 else 0)
            .putBoolean(P2_RATED, true)
            .apply()
    }

    /** For the debug menu: a readable breakdown of how the ending was reached. */
    fun summary(ctx: Context): String {
        val r = if (rated(ctx)) "${rating(ctx)}/10" else "-"
        val b6 = when {
            !building6Answered(ctx) -> "-"
            paidToSolo(ctx) -> "paid to solo"
            else -> "kept friends"
        }
        val counted = COMPLY_ON_PLUS.size + COMPLY_ON_MINUS.size
        val seen = answered(ctx).size
        return "p1 ${wentAlongCount(ctx)} along / ${pushedBackCount(ctx)} resisted " +
            "($seen of $counted counted beats reached)  b6 $b6  b10 $r  " +
            "=> ${"%.2f".format(score(ctx))} ${if (isComplicitEnding(ctx)) "COMPLICIT" else "REFUSED"}"
    }
}
