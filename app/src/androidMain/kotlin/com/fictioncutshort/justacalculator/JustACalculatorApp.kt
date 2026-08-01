package com.fictioncutshort.justacalculator

import android.app.Application
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.logic.EasterEggTheme

/**
 * Application entry point.
 *
 * Runs before any Activity (including PreviewActivity from compose-ui-tooling).
 * Guarantees CalculatorActions.init() is called once, with `prefs` set, before
 * any composable can call loadInitialState() or any persist*().
 *
 * Without this, the launch ordering depended on which activity ran first —
 * and on debug builds with compose-ui-tooling on the classpath, the launcher
 * could associate the task with PreviewActivity (which never calls init()),
 * leaving `prefs == null` for the entire session and silently no-opping every
 * persistConversationStep / persistInConversation call.
 *
 * Also installs the shared Coil image loader, so `AsyncImage` picks up the SVG
 * decoder for the asset-loaded SVGs in phonescreen/phonedetour/.
 */
class JustACalculatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("JustACalc", "🟢 Application.onCreate — initializing CalculatorActions")
        com.fictioncutshort.justacalculator.platform.AppInit.initialize(applicationContext)
        com.fictioncutshort.justacalculator.platform.installImageLoader()
        CalculatorActions.init(applicationContext)
        EasterEggTheme.init(applicationContext)
        // Guarantee the narration player has a context before ANY screen plays a
        // cue — otherwise the first cue on a session that skipped the intro/TD
        // (e.g. a debug jump straight to a building) silently no-ops and its
        // onComplete fires instantly (Building 9 "Time to go" with no voiceover).
        com.fictioncutshort.justacalculator.logic.VoiceoverManager.init(applicationContext)
    }

}