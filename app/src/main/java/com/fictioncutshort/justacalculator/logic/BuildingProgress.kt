package com.fictioncutshort.justacalculator.logic

import android.content.Context

/**
 * BuildingProgress.kt
 *
 * Save/restore for phase 2 — the city and the nine buildings inside it.
 *
 * The city itself already survives anything: the player's position is written
 * once a second, so a swipe-kill costs at most a second of walking. What did NOT
 * survive was everything INSIDE a building. Each minigame held its whole state in
 * `remember`, so closing the app mid-level threw the level away, and any currency
 * collected but not yet banked (a building only awards on completion) went with
 * it.
 *
 * Two things live here:
 *
 *  1. THE ACTIVE BUILDING. When the player walks through a door, that building is
 *     marked active; it stays active until the building is completed or exited.
 *     If the app dies while a building is open, the city reopens it on the next
 *     launch instead of dumping the player back on the street. Being inside a
 *     building is a place, and the game should remember you were standing in it.
 *
 *  2. A NAMESPACED KEY/VALUE SCRATCHPAD per building, so each game can save
 *     exactly as much of itself as is worth saving — a level index and lives for
 *     the tower defence, which apps were opened in the phone sim, which
 *     checkpoints were reached on the walk. Completing a building CLEARS its
 *     scratchpad, so a replay always starts clean.
 *
 * Everything is in the shared "calc_city" store, so the debug menu's RESET CITY
 * wipes it, and it sits alongside CurrencyStore and ComplicityStore.
 */
object BuildingProgress {

    private const val PREFS_NAME = "calc_city"
    private const val ACTIVE = "bp_active_building"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── The building the player is currently standing inside ──────────────────

    /** Called when the player walks through a door. */
    fun setActive(ctx: Context, digit: Int) {
        prefs(ctx).edit().putInt(ACTIVE, digit).apply()
    }

    /** Called when a building is closed for any reason — finished or walked out of. */
    fun clearActive(ctx: Context) {
        prefs(ctx).edit().remove(ACTIVE).apply()
    }

    /** The building to reopen on entering the city, or null to stay on the street. */
    fun activeBuilding(ctx: Context): Int? {
        val d = prefs(ctx).getInt(ACTIVE, -1)
        return if (d in 1..9) d else null
    }

    // ── Per-building scratchpad ───────────────────────────────────────────────

    private fun k(digit: Int, key: String) = "bp_${digit}_$key"

    fun putInt(ctx: Context, digit: Int, key: String, v: Int) =
        prefs(ctx).edit().putInt(k(digit, key), v).apply()

    fun getInt(ctx: Context, digit: Int, key: String, def: Int = 0): Int =
        prefs(ctx).getInt(k(digit, key), def)

    fun putFloat(ctx: Context, digit: Int, key: String, v: Float) =
        prefs(ctx).edit().putFloat(k(digit, key), v).apply()

    fun getFloat(ctx: Context, digit: Int, key: String, def: Float = 0f): Float =
        prefs(ctx).getFloat(k(digit, key), def)

    fun putBool(ctx: Context, digit: Int, key: String, v: Boolean) =
        prefs(ctx).edit().putBoolean(k(digit, key), v).apply()

    fun getBool(ctx: Context, digit: Int, key: String, def: Boolean = false): Boolean =
        prefs(ctx).getBoolean(k(digit, key), def)

    fun putString(ctx: Context, digit: Int, key: String, v: String) =
        prefs(ctx).edit().putString(k(digit, key), v).apply()

    fun getString(ctx: Context, digit: Int, key: String, def: String = ""): String =
        prefs(ctx).getString(k(digit, key), def) ?: def

    /** A set of names — which apps were opened, which exhibits were read. */
    fun putSet(ctx: Context, digit: Int, key: String, v: Set<String>) =
        prefs(ctx).edit().putStringSet(k(digit, key), v).apply()

    fun getSet(ctx: Context, digit: Int, key: String): Set<String> =
        prefs(ctx).getStringSet(k(digit, key), emptySet()) ?: emptySet()

    /** A list of numbers — collected key ids, visited checkpoints, tile colours. */
    fun putInts(ctx: Context, digit: Int, key: String, v: List<Int>) =
        putString(ctx, digit, key, v.joinToString(","))

    fun getInts(ctx: Context, digit: Int, key: String): List<Int> =
        getString(ctx, digit, key)
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }

    fun putFloats(ctx: Context, digit: Int, key: String, v: List<Float>) =
        putString(ctx, digit, key, v.joinToString(","))

    fun getFloats(ctx: Context, digit: Int, key: String): List<Float> =
        getString(ctx, digit, key)
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }

    /** True once this building has written anything — i.e. there's a run to resume. */
    fun hasSave(ctx: Context, digit: Int): Boolean =
        prefs(ctx).all.keys.any { it.startsWith("bp_${digit}_") }

    /**
     * Wipe a building's saved run. Called on completion: the next visit should be
     * a fresh start, not a resume into a finished game.
     */
    fun clear(ctx: Context, digit: Int) {
        val p = prefs(ctx)
        val e = p.edit()
        for (key in p.all.keys) if (key.startsWith("bp_${digit}_")) e.remove(key)
        e.apply()
    }
}
