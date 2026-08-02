package com.fictioncutshort.justacalculator.gl

/**
 * Throttles the render loop by [millis].
 *
 * Android's renderers run on GLSurfaceView's dedicated render thread, where
 * sleeping is the normal way to cap the frame rate and cut sustained GPU load.
 *
 * On iOS this is a **no-op**: GLKView's delegate is called on the main thread,
 * so sleeping there would freeze the whole UI, not just the scene. CADisplayLink
 * paces frames instead — see [PlatformGlSurface].
 *
 * This means the deliberate frame *stutter* effects (the post-Building-4 glitch)
 * are currently Android-only. Reproducing them on iOS needs the display link's
 * preferredFramesPerSecond to be varied rather than the thread blocked.
 */
expect fun throttleRenderThread(millis: Long)
