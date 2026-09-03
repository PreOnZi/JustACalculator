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
 * The deliberate frame *stutter* (the post-Building-4 glitch) is a separate
 * seam — see [hitchRenderThread] — precisely because it must NOT be a no-op on
 * iOS the way steady-state capping is.
 */
expect fun throttleRenderThread(millis: Long)

/**
 * One deliberate hitch of roughly [millis], for the post-Building-4 glitch.
 *
 * Separate from [throttleRenderThread] because the two want opposite things on
 * iOS: steady-state capping there is the display link's job and this function
 * must stay out of it, while the stutter has to actually land or the glitch is
 * invisible — which is what happened. The scene ran clean on iOS through the
 * whole stretch where it is supposed to be falling apart.
 *
 * Android sleeps its dedicated render thread. iOS drops whole frames instead,
 * because GLKView draws from the main thread and blocking there would freeze
 * touch handling too — and a dropped frame is what a hitch looks like anyway.
 */
expect fun hitchRenderThread(millis: Long)
