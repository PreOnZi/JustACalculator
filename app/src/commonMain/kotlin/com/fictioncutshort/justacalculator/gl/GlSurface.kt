package com.fictioncutshort.justacalculator.gl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The renderer callbacks, matching `GLSurfaceView.Renderer` so the existing
 * renderers implement this by changing their `override` signatures and nothing
 * else. The EGL/GL context objects the Android interface passes are dropped:
 * every renderer ignored them and called the static GL binding instead.
 */
interface GlRenderer {
    /** Context created or recreated — all GL objects must be (re)built here. */
    fun onSurfaceCreated()

    fun onSurfaceChanged(width: Int, height: Int)

    /** Draw one frame. Called continuously on the render thread. */
    fun onDrawFrame()
}

/**
 * Hosts a [GlRenderer] — a `GLSurfaceView` on Android, a `GLKView` driven by a
 * `CADisplayLink` on iOS.
 *
 * Both are configured to match what the renderers already assume: an RGB888
 * colour buffer with a 24-bit depth buffer, and continuous redraw.
 *
 * @param contextVersion 2 or 3. Building 6's runner needs ES 3.0 for vertex
 *   array objects and uniform blocks; everything else runs on ES 2.0.
 */
@Composable
expect fun PlatformGlSurface(
    renderer: GlRenderer,
    modifier: Modifier = Modifier,
    contextVersion: Int = 2,
)
