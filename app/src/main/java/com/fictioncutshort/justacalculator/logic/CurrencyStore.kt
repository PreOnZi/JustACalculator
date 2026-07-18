package com.fictioncutshort.justacalculator.logic

import android.content.Context

/**
 * CurrencyStore.kt
 *
 * The five collectible currencies and their persistent balances. Every building
 * deposits its final amount here on completion; the city HUD reads the non-zero
 * balances to draw the top-of-screen counters. Balances persist across launches
 * (and all the way to Building 8, where they'll be spent) via the shared
 * "calc_city" SharedPreferences — the same store the rest of the city uses.
 *
 * Each currency has a 3D model in assets/currency/ used to render its HUD icon
 * (see CurrencyIcon.kt).
 */
enum class Currency(
    val prefKey: String,
    val obj: String,
    val mtl: String,
    val order: Int,
) {
    COINS("cur_coins", "currency/coin.obj", "currency/coin.mtl", 0),
    KEYS("cur_keys", "currency/key.obj", "currency/key.mtl", 1),
    // GIFTCARDS reuses Building 3's existing balance key so there's a single
    // source of truth — TankGame already writes/reads "b3_giftcards" directly.
    GIFTCARDS("b3_giftcards", "currency/giftcard.obj", "currency/giftcard.mtl", 2),
    COOKIES("cur_cookies", "currency/cookie.obj", "currency/cookie.mtl", 3),
    STARS("cur_stars", "currency/star.obj", "currency/star.mtl", 4),
}

object CurrencyStore {

    // Shared with Calculatorcityview / TankGame (both use "calc_city").
    private const val PREFS_NAME = "calc_city"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Current balance of a currency (0 if the source building hasn't been visited). */
    fun balance(ctx: Context, c: Currency): Int = prefs(ctx).getInt(c.prefKey, 0)

    /**
     * Deposit [amount] of [c] exactly once per [sourceTag]. Buildings aren't
     * replayable in the final build, but the once-guard keeps a stray re-entry
     * during development from double-depositing. Coins/cookies come from two
     * buildings each — they use distinct tags so both contributions land.
     *
     * GIFTCARDS is never awarded this way; its balance is B3's live economy value.
     */
    fun award(ctx: Context, c: Currency, amount: Int, sourceTag: String) {
        val p = prefs(ctx)
        val awardedKey = "cur_awarded_$sourceTag"
        if (p.getBoolean(awardedKey, false)) return
        val add = amount.coerceAtLeast(0)
        p.edit()
            .putInt(c.prefKey, p.getInt(c.prefKey, 0) + add)
            .putBoolean(awardedKey, true)
            .apply()
    }

    /** Non-zero balances, ordered for the HUD. Empty until a building is visited. */
    fun nonZero(ctx: Context): List<Pair<Currency, Int>> =
        Currency.entries
            .sortedBy { it.order }
            .map { it to balance(ctx, it) }
            .filter { it.second > 0 }

    // ── Building 8 spending ────────────────────────────────────────────────────
    // The gambling building is the only place currency leaves the store. Each of
    // its five rigged games drains one currency to zero; the coins lottery is
    // settled last (see the lottery helpers below).

    /** Overwrite a currency's balance (used to drain a game to exactly zero). */
    fun setBalance(ctx: Context, c: Currency, value: Int) {
        prefs(ctx).edit().putInt(c.prefKey, value.coerceAtLeast(0)).apply()
    }

    /** Remove [amount] (clamped at zero); returns the amount actually removed. */
    fun spend(ctx: Context, c: Currency, amount: Int): Int {
        val have = balance(ctx, c)
        val take = amount.coerceIn(0, have)
        if (take > 0) setBalance(ctx, c, have - take)
        return take
    }

    /** Drain a currency to zero; returns the amount that was taken. */
    fun zero(ctx: Context, c: Currency): Int {
        val had = balance(ctx, c)
        if (had != 0) setBalance(ctx, c, 0)
        return had
    }

    // ── Coins lottery (seeded in the city after Building 5, settled in B8) ──────
    private const val LOTTO_STAKED = "b8_lottery_staked"   // player entered the draw
    private const val LOTTO_NUMBERS = "b8_lottery_numbers" // "n,n,n,n,n,n" the player picked
    private const val LOTTO_SETTLED = "b8_lottery_settled" // draw revealed, coins taken
    private const val LOTTO_SHOWN = "b8_lottery_shown"     // city popup already appeared

    /** Stake ALL coins on [numbers]. The player isn't told coins are the wager. */
    fun stakeCoins(ctx: Context, numbers: List<Int>) {
        prefs(ctx).edit()
            .putBoolean(LOTTO_STAKED, true)
            .putBoolean(LOTTO_SHOWN, true)
            .putString(LOTTO_NUMBERS, numbers.joinToString(","))
            .apply()
    }

    fun lotteryStaked(ctx: Context): Boolean = prefs(ctx).getBoolean(LOTTO_STAKED, false)
    fun lotterySettled(ctx: Context): Boolean = prefs(ctx).getBoolean(LOTTO_SETTLED, false)

    /** Whether the city "draw closing soon" popup has already been shown once. */
    fun lotteryShown(ctx: Context): Boolean = prefs(ctx).getBoolean(LOTTO_SHOWN, false)
    fun markLotteryShown(ctx: Context) {
        prefs(ctx).edit().putBoolean(LOTTO_SHOWN, true).apply()
    }

    /** The six numbers the player entered (empty if never staked). */
    fun lotteryNumbers(ctx: Context): List<Int> =
        prefs(ctx).getString(LOTTO_NUMBERS, "").orEmpty()
            .split(",").mapNotNull { it.trim().toIntOrNull() }

    /** Settle the draw: coins are lost, balance drained to zero. Returns coins lost. */
    fun settleLottery(ctx: Context): Int {
        prefs(ctx).edit().putBoolean(LOTTO_SETTLED, true).apply()
        return zero(ctx, Currency.COINS)
    }

    /** All five currencies drained AND the lottery settled → Building 8 is done. */
    fun building8Complete(ctx: Context): Boolean =
        Currency.entries.all { balance(ctx, it) == 0 } && lotterySettled(ctx)
}
