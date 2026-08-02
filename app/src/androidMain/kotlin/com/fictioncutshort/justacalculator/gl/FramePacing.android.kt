package com.fictioncutshort.justacalculator.gl

actual fun throttleRenderThread(millis: Long) {
    if (millis <= 0) return
    try {
        Thread.sleep(millis)
    } catch (_: InterruptedException) {
        // The render thread is being torn down; returning is the right response.
    }
}
