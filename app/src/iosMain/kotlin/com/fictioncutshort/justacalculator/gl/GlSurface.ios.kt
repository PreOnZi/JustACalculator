package com.fictioncutshort.justacalculator.gl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fictioncutshort.justacalculator.platform.AppLifecycleEvent
import com.fictioncutshort.justacalculator.platform.OnAppLifecycleEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.EAGL.EAGLContext
import platform.EAGL.kEAGLRenderingAPIOpenGLES2
import platform.EAGL.kEAGLRenderingAPIOpenGLES3
import platform.GLKit.GLKView
import platform.GLKit.GLKViewDelegateProtocol
import platform.GLKit.GLKViewDrawableColorFormatRGBA8888
import platform.GLKit.GLKViewDrawableDepthFormat24
import platform.QuartzCore.CADisplayLink
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.darwin.NSObject
import platform.darwin.sel_registerName

/**
 * GLKView is the UIKit counterpart to GLSurfaceView, but it does **not** drive
 * itself: GLKViewController normally owns the render loop, and there is no
 * controller here because the view is hosted inside Compose. A CADisplayLink
 * supplies the equivalent of RENDERMODE_CONTINUOUSLY.
 *
 * GLKView also calls its delegate on the main thread, whereas GLSurfaceView uses
 * a dedicated render thread. The renderers are already written to touch GL only
 * from their callbacks, so that difference is invisible to them — but it does
 * mean a slow frame blocks the UI on iOS in a way it would not on Android.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformGlSurface(
    renderer: GlRenderer,
    modifier: Modifier,
    contextVersion: Int,
    targetFps: Int,
    paused: Boolean,
) {
    val holder = remember { GlSurfaceHolder(renderer) }

    // Stop rendering while the app is not on screen.
    //
    // Two reasons, and the second is not optional: iOS **terminates** an app
    // that issues OpenGL commands while backgrounded (the GPU driver kills the
    // client), so the loop has to be down before the app suspends. It also
    // stops the city rendering behind the app switcher or Control Centre,
    // which is pure heat and battery for pixels nobody sees.
    //
    // Android gets this from GLSurfaceView.onPause/onResume already.
    // Caller-requested pause (an overlay covering the scene) and the lifecycle
    // pause below are independent; either one stops the loop.
    var backgrounded by remember { mutableStateOf(false) }
    LaunchedEffect(paused, backgrounded) { holder.setPaused(paused || backgrounded) }

    OnAppLifecycleEvent { event ->
        when (event) {
            AppLifecycleEvent.PAUSED, AppLifecycleEvent.STOPPED -> backgrounded = true
            AppLifecycleEvent.RESUMED -> backgrounded = false
            else -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose { holder.dispose() }
    }

    UIKitView(
        factory = {
            val api = if (contextVersion >= 3) {
                kEAGLRenderingAPIOpenGLES3
            } else {
                kEAGLRenderingAPIOpenGLES2
            }
            val context = EAGLContext(api)
            EAGLContext.setCurrentContext(context)

            GLKView(frame = CGRectZero.readValue(), context = context).apply {
                // Match the Android config: RGB888 with a 24-bit depth buffer.
                drawableColorFormat = GLKViewDrawableColorFormatRGBA8888
                drawableDepthFormat = GLKViewDrawableDepthFormat24

                delegate = holder
                holder.attach(this, context, targetFps)
            }
        },
        modifier = modifier,
        // The surface is a render target and nothing else. Every gesture over
        // it — the joystick, the look-drag, the door prompts, the key
        // inspector — is a Compose modifier; the GLKView has no gesture
        // recognisers and wants no touches.
        //
        // The default is Cooperative(150ms): "view receives touches with 150ms
        // delay, allowing compose to intercept them". Steering holds the stick
        // far longer than that, so the interop view claimed the sequence and
        // Compose's in-flight gesture was cancelled the moment a second finger
        // arrived — on iPad, reaching up to look stopped the player walking.
        // Android never had this because GLSurfaceView is not clickable and
        // simply lets touches fall through to Compose.
        //
        // Setting this on the GLKView inside `factory` does NOT work: Compose
        // drives userInteractionEnabled from these properties and would put it
        // straight back.
        properties = UIKitInteropProperties(
            isInteractive = false,
            isNativeAccessibilityEnabled = false,
        ),
    )
}

/**
 * Owns the delegate and the display link. Kept out of the composable so ARC has
 * a stable strong reference for as long as the view lives — GLKView holds its
 * delegate weakly, and a delegate collected mid-flight stops the render loop
 * silently.
 */
@OptIn(ExperimentalForeignApi::class)
private class GlSurfaceHolder(
    private val renderer: GlRenderer,
) : NSObject(), GLKViewDelegateProtocol {

    private var displayLink: CADisplayLink? = null
    private var context: EAGLContext? = null
    private var created = false
    private var lastWidth = 0
    private var lastHeight = 0
    private var view: GLKView? = null

    fun attach(view: GLKView, context: EAGLContext, targetFps: Int) {
        this.view = view
        this.context = context
        displayLink = CADisplayLink.displayLinkWithTarget(this, sel_registerName("tick")).apply {
            // The only throttle available on iOS. Uncapped, this loop saturates
            // the main thread and starves the Compose coroutines that drive the
            // city's intro animation — the transition simply stops advancing.
            preferredFramesPerSecond = targetFps.toLong()
            addToRunLoop(NSRunLoop.currentRunLoop, NSRunLoopCommonModes)
        }
    }

    /** Halts the display link without tearing the context down. */
    fun setPaused(paused: Boolean) {
        displayLink?.setPaused(paused)
    }

    @ObjCAction
    fun tick() {
        view?.display()
    }

    override fun glkView(view: GLKView, drawInRect: CValue<CGRect>) {
        context?.let { EAGLContext.setCurrentContext(it) }

        if (!created) {
            renderer.onSurfaceCreated()
            created = true
        }

        val w = view.drawableWidth.toInt()
        val h = view.drawableHeight.toInt()
        if (w != lastWidth || h != lastHeight) {
            lastWidth = w
            lastHeight = h
            renderer.onSurfaceChanged(w, h)
        }

        // The post-Building-4 glitch asks for hitches by dropping frames — see
        // hitchRenderThread. Skipping the draw leaves the last frame up, which
        // reads as a stutter without blocking the main thread.
        if (pendingFrameSkips > 0) {
            pendingFrameSkips--
            return
        }

        renderer.onDrawFrame()
    }

    fun dispose() {
        displayLink?.invalidate()
        displayLink = null
        view = null
        if (EAGLContext.currentContext() == context) EAGLContext.setCurrentContext(null)
        context = null
    }
}
