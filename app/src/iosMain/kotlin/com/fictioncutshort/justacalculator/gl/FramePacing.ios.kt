package com.fictioncutshort.justacalculator.gl

/**
 * Deliberately does nothing. Blocking here would block the main thread, since
 * GLKView draws from it. See the expect declaration for what this costs.
 */
actual fun throttleRenderThread(millis: Long) = Unit

/**
 * Frames the display link should skip before drawing again. Read and decremented
 * by the tick in [PlatformGlSurface]; a skipped frame leaves the previous one on
 * screen, which is exactly the hitch the glitch wants.
 */
internal var pendingFrameSkips: Int = 0

actual fun hitchRenderThread(millis: Long) {
    if (millis <= 0) return
    // ~60fps, so a frame is about 16ms. Capped so a long roll cannot stall the
    // scene for a noticeable freeze rather than a stutter.
    val frames = ((millis + 8) / 16).toInt().coerceIn(1, 12)
    if (frames > pendingFrameSkips) pendingFrameSkips = frames
}
