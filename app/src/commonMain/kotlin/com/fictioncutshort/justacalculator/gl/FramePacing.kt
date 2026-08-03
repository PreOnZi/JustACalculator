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
 * Frame *rate capping* on iOS is handled instead by `PlatformGlSurface`'s
 * `targetFps`, which sets the display link's preferredFramesPerSecond. That
 * matters more than it sounds: uncapped, the render loop saturates the main
 * thread and starves the Compose coroutines driving the city's intro, so the
 * aerial-to-city transition stops advancing partway through.
 *
 * What is still Android-only is the deliberate frame *stutter* (the
 * post-Building-4 glitch), which varies the delay per frame. Reproducing that
 * needs preferredFramesPerSecond varied at runtime rather than a blocked
 * thread.
 */
expect fun throttleRenderThread(millis: Long)
