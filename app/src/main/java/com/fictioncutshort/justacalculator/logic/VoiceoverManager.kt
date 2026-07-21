package com.fictioncutshort.justacalculator.logic

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import com.fictioncutshort.justacalculator.R

/**
 * VoiceoverManager.kt
 *
 * Central playback for the phase-2 city narration (the vo001… clips in res/raw).
 *
 * Story cues are scattered across many screens (city view, the buildings, the
 * monster encounter), so rather than each screen owning a MediaPlayer this object
 * is the single owner. Screens just call [play] / [playWithRadio] at their cue
 * points and flip [cctvMode] / [glitchMode] as the world state changes.
 *
 * Routing:
 *  - Normal: the clip plays flat.
 *  - [cctvMode] (true while the player is walking between houses in the city):
 *    the voiceover is meant to sound like it's coming out of the buildings' CCTV
 *    cameras — attenuated and high-cut (muffled) via an [Equalizer] on the
 *    player's own session. Wind/steps are untouched (they're owned by the city
 *    view). Toggled off whenever the player is inside a building / UI.
 *  - [glitchMode] (true after building 4, until building 3 is finished): playback
 *    stutters — short random pause/resume hiccups — to match the "off" feeling of
 *    the world. Individual cues can opt out (e.g. vo018 plays clean).
 */
object VoiceoverManager {

    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())

    private var voPlayer: MediaPlayer? = null
    private var radioPlayer: MediaPlayer? = null
    private var eq: Equalizer? = null
    private var stutterRunnable: Runnable? = null

    // Cues never cut each other — a play() while one is sounding queues behind it
    // and starts when the current clip ends, so narration can't overlap.
    private data class Pending(
        val resId: Int, val cctv: Boolean, val glitch: Boolean,
        val volume: Float, val onComplete: (() -> Unit)?
    )
    private val queue = ArrayDeque<Pending>()

    /** Legacy flag (kept so callers still compile); the CCTV muffle it drove is gone. */
    @Volatile var cctvMode: Boolean = false

    /** True after building 4 / before building 3 is finished: playback stutters. */
    @Volatile var glitchMode: Boolean = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Play a single voiceover clip.
     *
     * @param resId       res/raw resource (e.g. R.raw.vo004)
     * @param cctv         route through the muffled CCTV path (defaults to the
     *                     current [cctvMode]; pass false to force a clean feed)
     * @param glitch       apply the stutter effect (defaults to [glitchMode])
     * @param volume       base volume 0..1 before CCTV attenuation
     * @param onComplete   invoked on the main thread when the clip finishes (or fails)
     */
    fun play(
        resId: Int,
        cctv: Boolean = cctvMode,
        glitch: Boolean = glitchMode,
        volume: Float = 1f,
        onComplete: (() -> Unit)? = null
    ) {
        if (appContext == null) { onComplete?.invoke(); return }
        // Never cut the clip that's currently sounding — queue behind it. Routing
        // (cctv/glitch/volume) is captured now, at call time.
        queue.addLast(Pending(resId, cctv, glitch, volume, onComplete))
        pump()
    }

    /** Start the next queued clip, if nothing is currently playing. */
    private fun pump() {
        val ctx = appContext ?: return
        if (voPlayer != null) return   // a clip is still sounding; its completion pumps the next
        val next = queue.removeFirstOrNull() ?: return
        val mp = MediaPlayer.create(ctx, next.resId) ?: run {
            next.onComplete?.invoke()
            pump()
            return
        }
        voPlayer = mp

        // The "coming from the buildings' CCTV cameras" muffle is disabled: every
        // cue plays clean and at a consistent volume for the whole game. (The cctv
        // flag on callers is ignored now.)
        mp.setVolume(next.volume, next.volume)

        mp.setOnCompletionListener {
            clearStutter()
            releaseEq()
            it.release()
            if (voPlayer === it) voPlayer = null
            next.onComplete?.invoke()
            pump()   // play whatever queued up while this clip was sounding
        }
        mp.start()
        if (next.glitch) startStutter(mp)
    }

    /**
     * Play a voiceover with the radio bed underneath it (the intro: vo001 over
     * radio.mp3). The radio starts with the voiceover at [radioVolume] and is
     * left running when the voiceover ends — the caller chains the next clip
     * (vo002) while the radio plays out its full length; [stopRadio] ends it.
     */
    fun playWithRadio(
        voRes: Int,
        radioRes: Int,
        radioVolume: Float = 0.35f,
        cctv: Boolean = false,
        onVoComplete: (() -> Unit)? = null
    ) {
        val ctx = appContext ?: run { onVoComplete?.invoke(); return }
        stopRadio()
        MediaPlayer.create(ctx, radioRes)?.let { r ->
            radioPlayer = r
            r.setVolume(radioVolume, radioVolume)
            r.setOnCompletionListener {
                it.release()
                if (radioPlayer === it) radioPlayer = null
            }
            r.start()
        }
        play(voRes, cctv = cctv, onComplete = onVoComplete)
    }

    /**
     * Play a list of clips back to back (e.g. vo013 → vo014 → vo015). Each clip
     * starts when the previous finishes. Routing/glitch are captured at call time.
     */
    fun playSequence(
        resIds: List<Int>,
        cctv: Boolean = cctvMode,
        glitch: Boolean = glitchMode,
        index: Int = 0,
        onAllComplete: (() -> Unit)? = null
    ) {
        if (index >= resIds.size) { onAllComplete?.invoke(); return }
        play(resIds[index], cctv = cctv, glitch = glitch) {
            playSequence(resIds, cctv, glitch, index + 1, onAllComplete)
        }
    }

    /** True while a voiceover clip is actively playing (used to sequence cues across screens). */
    fun isPlaying(): Boolean = runCatching { voPlayer?.isPlaying == true }.getOrDefault(false)

    /**
     * True while a clip is playing OR one is still queued behind it. An action that
     * must not talk over the narration (e.g. the ending collapse) waits on this so
     * it only begins once the whole queue has drained.
     */
    fun isBusy(): Boolean = voPlayer != null || queue.isNotEmpty()

    /** Fade-free stop of just the radio bed (call once the intro's vo002 ends). */
    fun stopRadio() {
        radioPlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
        radioPlayer = null
    }

    private fun stopVoice() {
        clearStutter()
        releaseEq()
        voPlayer?.let {
            it.setOnCompletionListener(null)
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        voPlayer = null
    }

    /** Stop everything (voiceover + radio) and discard anything queued. */
    fun stopAll() {
        queue.clear()
        stopVoice()
        stopRadio()
    }

    // The "CCTV camera speaker" muffle was removed — every cue now plays clean and
    // at a consistent volume. releaseEq is kept as a harmless no-op (eq stays null).
    private fun releaseEq() {
        eq?.let { runCatching { it.release() } }
        eq = null
    }

    // ── Glitch / stutter ───────────────────────────────────────────────────
    private fun startStutter(mp: MediaPlayer) {
        clearStutter()
        val r = object : Runnable {
            override fun run() {
                val p = voPlayer ?: return
                if (p !== mp) return
                runCatching {
                    if (p.isPlaying) {
                        p.pause()
                        main.postDelayed({ runCatching { if (voPlayer === p) p.start() } }, (40..110).random().toLong())
                    }
                }
                main.postDelayed(this, (350..900).random().toLong())
            }
        }
        stutterRunnable = r
        main.postDelayed(r, (350..900).random().toLong())
    }

    private fun clearStutter() {
        stutterRunnable?.let { main.removeCallbacks(it) }
        stutterRunnable = null
    }
}
