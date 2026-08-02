package com.fictioncutshort.justacalculator.platform

/**
 * A minimal mutual-exclusion lock.
 *
 * Kotlin has no common `synchronized`, and the renderers genuinely need one:
 * work is queued from the UI thread and drained on the render thread. (On iOS
 * both happen to be the main thread today, since GLKView draws there — but
 * relying on that would silently break if the render loop ever moves.)
 */
expect class PlatformLock() {
    fun <T> withLock(block: () -> T): T
}
