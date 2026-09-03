package com.fictioncutshort.justacalculator.util

/**
 * Hands out lines without repeating one until the pool is spent.
 *
 * Two places need this. Building 6's cast is the player's own phonebook, and
 * drawing it at random meant the same two people turned up over and over —
 * which reads as a broken game rather than a small circle of friends. The
 * trivia hints have the same problem: the calculator saying "Incorrect." three
 * times running sounds stuck rather than exasperated.
 *
 * One pool per *role*, never one overall: in Building 6 somebody who came out
 * to shove the boulder is still free to ring you later, and that crossover is
 * the point. It is repeating within a role that is wrong.
 */
internal class NamePool {
    private val used = LinkedHashSet<String>()

    /** The names handed out so far, in the order they were issued. */
    val issued: Set<String> get() = used

    /**
     * Picks from whichever of [candidates] this pool has not issued yet, using
     * [pick] to choose among them — helpers walk the phonebook in order, callers
     * pick at random, and both go through here so neither repeats.
     *
     * Once every candidate has had a turn the pool starts over: the alternative
     * is an unnamed contact, which is worse than a repeat this late on.
     */
    fun next(candidates: List<String>, pick: (List<String>) -> String): String? {
        if (candidates.isEmpty()) return null
        var fresh = candidates.filter { it !in used }
        if (fresh.isEmpty()) {
            used.clear()
            fresh = candidates
        }
        val chosen = pick(fresh)
        used.add(chosen)
        return chosen
    }
}
