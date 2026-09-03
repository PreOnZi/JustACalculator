package com.fictioncutshort.justacalculator.gl

actual fun throttleRenderThread(millis: Long) {
    if (millis <= 0) return
    try {
        Thread.sleep(millis)
    } catch (_: InterruptedException) {
        // The render thread is being torn down; returning is the right response.
    }
}

/** The render thread is not the main thread here, so a real sleep is the hitch. */
actual fun hitchRenderThread(millis: Long) = throttleRenderThread(millis)
