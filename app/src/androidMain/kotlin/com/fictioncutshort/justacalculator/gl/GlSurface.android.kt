package com.fictioncutshort.justacalculator.gl

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
actual fun PlatformGlSurface(
    renderer: GlRenderer,
    modifier: Modifier,
    contextVersion: Int,
    targetFps: Int,
) {
    // targetFps is unused here: GLSurfaceView renders on its own thread and the
    // renderers pace themselves with throttleRenderThread, which is a real sleep
    // on Android.
    val view = remember { arrayOfNulls<GLSurfaceView>(1) }

    // GLSurfaceView's render thread must be paused with the app, not just with
    // the composition — backgrounding does not detach the view, so without this
    // it keeps drawing (and burning battery) behind the home screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> view[0]?.onPause()
                Lifecycle.Event.ON_RESUME -> view[0]?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view[0]?.onPause()
        }
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
