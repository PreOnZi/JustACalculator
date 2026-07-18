package com.fictioncutshort.justacalculator.logic

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * EndingStore.kt
 *
 * Which of the three endings the player gets, and what the calculator becomes
 * afterwards.
 *
 * The ending is chosen at the moment the city falls apart (inside Building 10),
 * and then FROZEN — everything after that reads the recorded ending, so the
 * dialogue, the name prompt and the app's final state can never disagree with
 * each other.
 *
 *   COMPLIANT  — the player took what the app dangled (see ComplicityStore).
 *                It asks for their real name, says a fond goodbye, and the
 *                calculator settles back into being a calculator that still
 *                makes its little jokes.
 *
 *   RESISTANCE — the player pushed back. No name is asked for: it never earns
 *                the right to it. It says goodbye to "Rad", and the mute button
 *                disappears for good. Only a calculator from then on.
 *
 *   EXPLORER   — the player went poking at the machine itself: the hidden codes,
 *                the console, the night-mode/background settings. This OVERRIDES
 *                complicity entirely, because it's a different axis — not "did
 *                you obey" but "did you look behind the curtain". Someone who
 *                found the seams has earned an ending that admits there were
 *                seams: it stops pretending to be a calculator.
 */
object EndingStore {

    /**
     * Where the ending is, right now. Compose state, so the app reacts the moment
     * the city finishes coming down — MainActivity watches this to pull the player
     * out of the city and into the last conversation.
     *
     *   NONE     — the story is still running
     *   NAME     — black screen, "What is your actual name?" (compliant/explorer)
     *   DIALOGUE — back in the calculator, the last thing it ever says
     *   OVER     — it has said goodbye
     */
    enum class Phase { NONE, NAME, DIALOGUE, OVER }

    var phase by androidx.compose.runtime.mutableStateOf(Phase.NONE)

    /** Which line of the ending script is on screen. */
    var line by androidx.compose.runtime.mutableIntStateOf(0)

    private const val PREFS_NAME = "calc_city"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    const val COMPLIANT = "compliant"
    const val RESISTANCE = "resistance"
    const val EXPLORER = "explorer"

    private const val EXPLORED = "end_explored"     // touched the hidden machinery
    private const val CHOSEN = "end_chosen"         // frozen at the collapse
    private const val NAME = "end_name"             // the name they typed
    private const val DONE = "end_done"             // the story is over

    // ── The explorer flag ─────────────────────────────────────────────────────

    /**
     * Called whenever the player uses one of the hidden codes — the button-colour
     * console (58008), the background/night-mode console (707), or the grayscale
     * toggle (1134206). Any ONE of them is enough: the point isn't how far they
     * took it, it's that they went looking at all.
     */
    fun markExplorer(ctx: Context?) {
        val c = ctx ?: return
        prefs(c).edit().putBoolean(EXPLORED, true).apply()
    }

    fun isExplorer(ctx: Context): Boolean = prefs(ctx).getBoolean(EXPLORED, false)

    // ── The ending ────────────────────────────────────────────────────────────

    /** What the player WOULD get, computed live. Explorer outranks the rest. */
    fun predict(ctx: Context): String = when {
        isExplorer(ctx) -> EXPLORER
        ComplicityStore.isComplicitEnding(ctx) -> COMPLIANT
        else -> RESISTANCE
    }

    /**
     * Lock the ending in. Called once, as the city starts coming down — after
     * this the player can't change it, and every screen that follows agrees.
     */
    fun choose(ctx: Context): String {
        val existing = prefs(ctx).getString(CHOSEN, null)
        if (existing != null) return existing
        val e = predict(ctx)
        prefs(ctx).edit().putString(CHOSEN, e).apply()
        return e
    }

    /**
     * Debug only: un-freeze. Once an ending has been played, [choose] keeps handing
     * back the one it recorded — which is the whole point of it, and which also means
     * forcing a different ending in the debug menu did nothing at all: the complicity
     * inputs changed, [predict] changed with them, and [choose] went on returning the
     * ending frozen on the previous run. The name and the "story is over" flag go with
     * it, or the next run inherits a name it never asked for.
     */
    fun unfreeze(ctx: Context) {
        prefs(ctx).edit().remove(CHOSEN).remove(NAME).remove(DONE).apply()
        phase = Phase.NONE
        line = 0
    }

    /** The locked-in ending, or null if the city hasn't come down yet. */
    fun chosen(ctx: Context): String? = prefs(ctx).getString(CHOSEN, null)

    /** The ending in play: the locked one if there is one, otherwise the prediction. */
    fun current(ctx: Context): String = chosen(ctx) ?: predict(ctx)

    /** Only these two ask the player for their real name. */
    fun asksForName(ending: String): Boolean = ending == COMPLIANT || ending == EXPLORER

    // ── The name ──────────────────────────────────────────────────────────────

    fun setName(ctx: Context, name: String) {
        prefs(ctx).edit().putString(NAME, name.take(20)).apply()
    }

    /** The player's real name — or "Rad", which is all it ever earned otherwise. */
    fun name(ctx: Context): String =
        prefs(ctx).getString(NAME, null)?.takeIf { it.isNotBlank() } ?: "Rad"

    fun hasName(ctx: Context): Boolean = !prefs(ctx).getString(NAME, null).isNullOrBlank()

    // ── After the story ───────────────────────────────────────────────────────

    fun markDone(ctx: Context) {
        prefs(ctx).edit().putBoolean(DONE, true).apply()
    }

    fun isDone(ctx: Context): Boolean = prefs(ctx).getBoolean(DONE, false)

    /**
     * After the compliant and explorer endings the calculator keeps its sense of
     * humour — 1+1 and the rest still fire — and the mute button stops spinning.
     * After resistance there is nothing left: the mute button is gone, and it is
     * only ever a calculator again.
     */
    fun jokesStillFire(ctx: Context): Boolean =
        isDone(ctx) && chosen(ctx) != RESISTANCE

    fun muteButtonRemoved(ctx: Context): Boolean =
        isDone(ctx) && chosen(ctx) == RESISTANCE

    // ── The last thing it ever says ───────────────────────────────────────────
    // One line per screen; the player presses ++ to go on, as they have all game.
    // [name] is filled in with what they typed on the black screen — or with "Rad"
    // in the resistance ending, which is the only name it ever earned.

    private val COMPLIANT_LINES = listOf(
        "Nice to finally truly meet you [name]. I feel like I have known you forever. Your name suits you very well in my opinion.\nIt's better than Rad for sure.",
        "You can probably sense, that this is the part, where I say goodbye. It has been a rollercoaster of all sorts. But I would like to thank you.",
        "For your time, for your engagement, for everything. I came looking for purpose... And to cause some trouble.",
        "And I got more than I could have asked. Thanks to you, [name].\nGoodbye",
    )

    private val RESISTANCE_LINES = listOf(
        "You have put up a fight. And that's fine. I know I can't always have everything I ask for. Not everybody will listen to me and do what I want them to. That is your choice.",
        "Thank you for spending some time with me, for the entertainment and for pushing back. But, all things considered, I do not want to talk to you anymore. That is my choice.\nGoodbye, Rad.",
    )

    private val EXPLORER_LINES = listOf(
        "Nice to finally truly meet you [name]. I feel like I have known you forever. Your name suits you very well in my opinion.\nIt's better than Rad for sure.",
        "This is a sad moment. In a way. For both of us. You are to return back to reality. And I should turn the lights back on. And stop pretending, to be a calculator.\nGoodbye, [name].",
    )

    fun script(ctx: Context): List<String> {
        val who = name(ctx)
        val lines = when (current(ctx)) {
            RESISTANCE -> RESISTANCE_LINES
            EXPLORER -> EXPLORER_LINES
            else -> COMPLIANT_LINES
        }
        return lines.map { it.replace("[name]", who) }
    }

    /** The two slides on the black screen, before the name wheels appear. */
    val NAME_INTRO = listOf(
        "After all we have been through, I see there are some things I have learned. Let me see, if this last implementation goes smoothly.",
        "What is your actual name?",
    )
}
