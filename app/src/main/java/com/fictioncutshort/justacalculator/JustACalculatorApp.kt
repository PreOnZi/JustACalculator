package com.fictioncutshort.justacalculator

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.fictioncutshort.justacalculator.logic.CalculatorActions

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
 * Implements [ImageLoaderFactory] so Coil's `AsyncImage` automatically picks
 * up the SVG decoder for asset-loaded SVGs in phonescreen/phonedetour/.
 */
class JustACalculatorApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("JustACalc", "🟢 Application.onCreate — initializing CalculatorActions")
        CalculatorActions.init(applicationContext)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
}