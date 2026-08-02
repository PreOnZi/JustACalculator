package com.fictioncutshort.justacalculator.gl

/**
 * Deliberately does nothing. Blocking here would block the main thread, since
 * GLKView draws from it. See the expect declaration for what this costs.
 */
actual fun throttleRenderThread(millis: Long) = Unit
