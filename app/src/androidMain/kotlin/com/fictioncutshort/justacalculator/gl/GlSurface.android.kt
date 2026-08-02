package com.fictioncutshort.justacalculator.gl

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
actual fun PlatformGlSurface(
    renderer: GlRenderer,
    modifier: Modifier,
    contextVersion: Int,
) {
    val view = remember { arrayOfNulls<GLSurfaceView>(1) }

    // GLSurfaceView needs its render thread paused with the composition,
    // otherwise it keeps drawing behind an overlay and burns battery.
    DisposableEffect(Unit) {
        onDispose { view[0]?.onPause() }
    }

    AndroidView(
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(contextVersion)
                // RGB888 + 24-bit depth: the city's overlapping geometry
                // z-fights badly on the default 16-bit depth buffer.
                setEGLConfigChooser(8, 8, 8, 0, 24, 0)
                preserveEGLContextOnPause = true
                setRenderer(object : GLSurfaceView.Renderer {
                    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) =
                        renderer.onSurfaceCreated()

                    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) =
                        renderer.onSurfaceChanged(width, height)

                    override fun onDrawFrame(gl: GL10?) = renderer.onDrawFrame()
                })
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                view[0] = this
            }
        },
        onRelease = { it.onPause() },
        modifier = modifier,
    )
}
