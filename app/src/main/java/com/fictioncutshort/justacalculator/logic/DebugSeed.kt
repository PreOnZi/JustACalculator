package com.fictioncutshort.justacalculator.logic

import android.content.Context

/**
 * DebugSeed.kt  —  TEMPORARY testing helper.
 *
 * When [ENABLED] is true, every launch drops the player straight into the city
 * at the "just finished Building 5" point, with a stash of every currency, and
 * Building 8's door as the next one open — so the Building 8 gambling flow (and
 * the post-Building-5 coins-lottery popup) can be tested without replaying the
 * whole game.
 *
 * Entry order is [5, 3, 7, 1, 9, 6, 8, 2, 6] (see Calculatorcityview.ENTRY_ORDER),
 * so Building 8 is the 7th entry → entry_progress = 6 makes it the next enterable
 * door. Building 5 is the very first entry, so td_b5_done is set (which is what
 * arms the lottery popup after ~10s of walking).
 *
 * >>> Set ENABLED = false (and relaunch once) to return to normal behaviour. <<<
 */
object DebugSeed {

    const val ENABLED = false

    fun seed(context: Context) {
        if (!ENABLED) return

        // ── Story store: boot directly into the city ──────────────────────────
        context.getSharedPreferences("just_a_calculator_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("story_complete", true)
            .putBoolean("in_city_phase", true)
            .apply()

        // ── City store: progress, night, currencies ───────────────────────────
        val city = context.getSharedPreferences("calc_city", Context.MODE_PRIVATE)
        val e = city.edit()

        e.putBoolean("intro_done", true)

        // Buildings entered so far in ENTRY_ORDER up to (not including) Building 8:
        // 5, 3, 7, 1, 9, 6. Mark those complete; leave 2, 4, 8 untouched.
        for (d in listOf(1, 3, 5, 6, 7, 9)) e.putBoolean("building_done_$d", true)
        e.putBoolean("building_done_2", false)
        e.putBoolean("building_done_4", false)
        e.putBoolean("building_done_8", false)

        // Minigame-completion flags used by the city (door greens, night, etc.).
        for (k in listOf("td_b1_done", "td_b3_done", "td_b5_done", "td_b6_done")) e.putBoolean(k, true)
        e.putBoolean("b3_night_active", true)     // permanent night after Building 3

        e.putInt("entry_progress", 6)             // → next door open is Building 8
        e.putInt("bridge_pieces", 9)              // DEBUG: full bridge across the lava → mute button reachable

        // A consistent "mid-game, about to face the finale" state. Earlier collapse
        // testing left some of these set on the device, which is why the mute-button
        // slider stopped prompting (b10_door_open stuck true) and a stale ending could
        // linger. Reset them so the finale plays clean from here:
        e.putBoolean("b10_door_open", false)      // mute button LOCKED → the rating slider prompts on approach
        e.remove("bridge_crossed")                // not across yet → the one-way crossing + monster sentinel arm fresh
        // No ending has happened yet.
        e.remove("end_chosen")
        e.remove("end_done")
        e.remove("end_name")

        // A stash of every currency to gamble away in Building 8.
        e.putInt("cur_coins", 50)                 // COINS
        e.putInt("cur_keys", 6)                   // KEYS
        e.putInt("b3_giftcards", 25)              // GIFTCARDS (shared with Building 3)
        e.putInt("cur_cookies", 30)               // COOKIES
        e.putInt("cur_stars", 40)                 // STARS

        // Fresh Building 8 run: clear the lottery + completion state so the city
        // popup appears again and the finale is un-settled.
        e.remove("b8_lottery_shown")
        e.remove("b8_lottery_staked")
        e.remove("b8_lottery_settled")
        e.remove("b8_lottery_numbers")

        e.apply()
    }
}
