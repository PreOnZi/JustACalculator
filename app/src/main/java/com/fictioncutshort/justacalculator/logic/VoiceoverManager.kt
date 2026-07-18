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
    private var buzzPlayer: MediaPlayer? = null
    private var eq: Equalizer? = null
    private var stutterRunnable: Runnable? = null

    /** True while walking the city streets: voiceover is muffled like a CCTV feed. */
    @Volatile var cctvMode: Boolean = false

    /** True after building 4 / before building 3 is finished: playback stutters. */
    @Volatile var glitchMode: Boolean = false

    /** CCTV muffle attenuation (0..1) applied on top of any per-call volume. */
    private const val CCTV_VOLUME = 0.55f

    /** Volume of the electrical buzz bed that rides under every voiceover. */
    private const val BUZZ_VOLUME = 0.2f

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
        val ctx = appContext ?: run { onComplete?.invoke(); return }
        stopVoice()
        val mp = MediaPlayer.create(ctx, resId) ?: run { onComplete?.invoke(); return }
        voPlayer = mp

        val vol = if (cctv) volume * CCTV_VOLUME else volume
        mp.setVolume(vol, vol)
        if (cctv) attachCctvMuffle(mp.audioSessionId)

        mp.setOnCompletionListener {
            clearStutter()
            releaseEq()
            stopBuzz()
            it.release()
            if (voPlayer === it) voPlayer = null
            onComplete?.invoke()   // a chained clip (playSequence) restarts the buzz
        }
        mp.start()
        startBuzz()
        if (glitch) startStutter(mp)
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

    /** Fade-free stop of just the radio bed (call once the intro's vo002 ends). */
    fun stopRadio() {
        radioPlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
        radioPlayer = null
    }

    private fun stopVoice() {
        clearStutter()
        releaseEq()
        stopBuzz()
        voPlayer?.let {
            it.setOnCompletionListener(null)
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        voPlayer = null
    }

    // The electrical buzz bed under the narration — a single looping clip started
    // whenever a voiceover plays and stopped when it ends (a chained sequence just
    // restarts it, so the bed is continuous under back-to-back clips).
    private fun startBuzz() {
        val ctx = appContext ?: return
        if (buzzPlayer != null) return
        buzzPlayer = MediaPlayer.create(ctx, R.raw.buzz)?.apply {
            isLooping = true
            setVolume(BUZZ_VOLUME, BUZZ_VOLUME)
            runCatching { start() }
        }
    }

    private fun stopBuzz() {
        buzzPlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
        buzzPlayer = null
    }

    /** Stop everything (voiceover + radio). Safe to call on teardown. */
    fun stopAll() {
        stopVoice()
        stopRadio()
    }

    // ── CCTV muffle ────────────────────────────────────────────────────────
    // A cheap "coming out of a camera speaker" tone: pull the top EQ bands down
    // so highs are rolled off. Equalizer isn't guaranteed on every device, so
    // this is best-effort and silently no-ops on failure.
    private fun attachCctvMuffle(sessionId: Int) {
        releaseEq()
        runCatching {
            val e = Equalizer(0, sessionId)
            e.enabled = true
            val bands = e.numberOfBands.toInt()
            val minLevel = e.bandLevelRange[0]
            for (b in 0 until bands) {
                // Roll off the upper half of the spectrum for the muffled feel.
                if (b >= bands / 2) e.setBandLevel(b.toShort(), (minLevel / 2).toShort())
            }
            eq = e
        }
    }

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
