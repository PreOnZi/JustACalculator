package com.fictioncutshort.justacalculator.platform

/**
 * Holds the process-wide [AppContext] so platform singletons that cannot take a
 * parameter — [Assets] above all — can reach it.
 *
 * Both entry points call [initialize] before any UI is composed: MainActivity on
 * Android, the ComposeUIViewController factory on iOS.
 */
object AppInit {
    private var stored: AppContext? = null

    fun initialize(context: AppContext) {
        stored = context.applicationScope()
    }

    val context: AppContext
        get() = stored ?: error("AppInit.initialize() must be called before using platform services")

    val isInitialized: Boolean get() = stored != null
}
