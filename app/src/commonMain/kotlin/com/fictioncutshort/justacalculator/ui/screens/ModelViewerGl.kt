package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.fictioncutshort.justacalculator.gl.Gl
import com.fictioncutshort.justacalculator.gl.GlRenderer
import com.fictioncutshort.justacalculator.gl.Matrix
import com.fictioncutshort.justacalculator.gl.PlatformGlSurface
import com.fictioncutshort.justacalculator.gl.toGlBuffer
import kotlin.concurrent.Volatile

/**
 * The maze's key inspector, drawn with the shared GL seam.
 *
 * Android has SceneView (Filament) for this and keeps using it; this exists so
 * iOS has the beat at all, and it deliberately copies SceneView's framing —
 * camera 3.5 units back, the model scaled so its longest side is 2 units, and
 * an opening tilt of x=15 y=-25 — so a key looks the same on both platforms.
 *
 * The caption under the panel says "drag to inspect", which is the whole
 * interaction: drag rotates, nothing else. There is no auto-spin, matching
 * SceneView's `isEditable` behaviour where the model sits still until touched.
 */
private const val START_PITCH = 15f
private const val START_YAW = -25f

/** Degrees per dp of drag. Roughly a half-turn across a 300dp panel. */
private const val DRAG_SENSITIVITY = 0.6f

/**
 * Stop just short of the poles; past them the model reads as upside-down.
 *
 * Deliberately not the city's PITCH_LIMIT: that one stops a walking player from
 * bending their neck too far, this one is about a model on a turntable, which
 * can be tipped much further before it looks wrong.
 */
private const val VIEWER_PITCH_LIMIT = 85f

/** The panel colour behind the viewer in MazeGame, so the surface disappears into it. */
private const val BG_R = 0.027f
private const val BG_G = 0.027f
private const val BG_B = 0.027f

@Composable
fun ModelViewerGl(modelFile: String, modifier: Modifier = Modifier) {
    // Keyed on the file: selecting a different key must rebuild the renderer,
    // not quietly keep showing the previous model.
    val renderer = remember(modelFile) { ModelViewerRenderer(modelFile) }
    val density = LocalDensity.current

    PlatformGlSurface(
        renderer = renderer,
        contextVersion = 2,
        targetFps = 30,
        modifier = modifier.pointerInput(modelFile) {
            detectDragGestures { change, drag ->
                change.consume()
                // In dp, so the same swipe turns the key by the same amount on
                // a phone and on an iPad.
                val dx = with(density) { drag.x.toDp().value }
                val dy = with(density) { drag.y.toDp().value }
                renderer.yaw += dx * DRAG_SENSITIVITY
                renderer.pitch = (renderer.pitch + dy * DRAG_SENSITIVITY)
                    .coerceIn(-VIEWER_PITCH_LIMIT, VIEWER_PITCH_LIMIT)
            }
        },
    )
}

private class ModelViewerRenderer(private val modelFile: String) : GlRenderer {

    // Written from the gesture handler on the UI thread, read on the render
    // thread — which is a separate GL thread on Android.
    @Volatile var yaw: Float = START_YAW
    @Volatile var pitch: Float = START_PITCH

    private var model: GltfStaticModel? = null
    private var program = 0
    private var aPos = 0
    private var aNrm = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0

    private var vbos = IntArray(0)
    private var ibos = IntArray(0)

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val modelM = FloatArray(16)
    private val tmp = FloatArray(16)
    private val mvp = FloatArray(16)

    /** Scale that makes the longest side 2 units, matching SceneView's scaleToUnits. */
    private var fit = 1f

    override fun onSurfaceCreated() {
        val m = model ?: GltfStaticModel.load(modelFile).also { model = it }
        fit = 2f / m.extent

        program = link(VERTEX_SRC, FRAGMENT_SRC)
        aPos = Gl.glGetAttribLocation(program, "aPos")
        aNrm = Gl.glGetAttribLocation(program, "aNrm")
        uMvp = Gl.glGetUniformLocation(program, "uMvp")
        uModel = Gl.glGetUniformLocation(program, "uModel")
        uColor = Gl.glGetUniformLocation(program, "uColor")

        // One VBO + IBO per primitive. The context can be recreated, so these
        // are always regenerated here rather than reused.
        val n = m.primitives.size
        vbos = IntArray(n); ibos = IntArray(n)
        Gl.glGenBuffers(n, vbos, 0)
        Gl.glGenBuffers(n, ibos, 0)
        for (i in 0 until n) {
            val p = m.primitives[i]
            Gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, vbos[i])
            val buf = p.interleaved.toGlBuffer()
            Gl.glBufferData(Gl.GL_ARRAY_BUFFER, p.interleaved.size * 4, buf, Gl.GL_STATIC_DRAW)
            Gl.glBindBuffer(Gl.GL_ELEMENT_ARRAY_BUFFER, ibos[i])
            Gl.glBufferDataInts(Gl.GL_ELEMENT_ARRAY_BUFFER, p.indices, Gl.GL_STATIC_DRAW)
        }
        Gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, 0)
        Gl.glBindBuffer(Gl.GL_ELEMENT_ARRAY_BUFFER, 0)

        Gl.glEnable(Gl.GL_DEPTH_TEST)
        Gl.glDepthFunc(Gl.GL_LEQUAL)
        // Culling stays off: these props are exported from a modelling package
        // with no guarantee of consistent winding, and a hole in a key the
        // player is studying is far worse than the fill cost of a 7k-triangle
        // model.
        Gl.glDisable(Gl.GL_CULL_FACE)
        Gl.glClearColor(BG_R, BG_G, BG_B, 1f)

        Matrix.setLookAtM(view, 0, 0f, 0f, 3.5f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        Gl.glViewport(0, 0, width, height)
        val aspect = if (height == 0) 1f else width.toFloat() / height.toFloat()
        Matrix.perspectiveM(proj, 0, 45f, aspect, 0.1f, 100f)
    }

    override fun onDrawFrame() {
        Gl.glClear(Gl.GL_COLOR_BUFFER_BIT or Gl.GL_DEPTH_BUFFER_BIT)
        val m = model ?: return
        if (program == 0) return

        Matrix.setIdentityM(modelM, 0)
        Matrix.rotateM(modelM, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(modelM, 0, yaw, 0f, 1f, 0f)
        Matrix.scaleM(modelM, 0, fit, fit, fit)
        // Centre the model on the origin before scaling it, so a key whose
        // pivot sits at one end still spins about its middle.
        Matrix.translateM(modelM, 0, -m.center[0], -m.center[1], -m.center[2])

        Matrix.multiplyMM(tmp, 0, view, 0, modelM, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0)

        Gl.glUseProgram(program)
        Gl.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        Gl.glUniformMatrix4fv(uModel, 1, false, modelM, 0)
        Gl.glEnableVertexAttribArray(aPos)
        Gl.glEnableVertexAttribArray(aNrm)

        for (i in m.primitives.indices) {
            val p = m.primitives[i]
            Gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, vbos[i])
            Gl.glBindBuffer(Gl.GL_ELEMENT_ARRAY_BUFFER, ibos[i])
            Gl.glVertexAttribPointerOffset(aPos, 3, Gl.GL_FLOAT, false, 24, 0)
            Gl.glVertexAttribPointerOffset(aNrm, 3, Gl.GL_FLOAT, false, 24, 12)
            val c = p.baseColor
            Gl.glUniform4f(uColor, c[0], c[1], c[2], c[3])
            Gl.glDrawElements(Gl.GL_TRIANGLES, p.indices.size, Gl.GL_UNSIGNED_INT, 0)
        }

        Gl.glDisableVertexAttribArray(aPos)
        Gl.glDisableVertexAttribArray(aNrm)
        Gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, 0)
        Gl.glBindBuffer(Gl.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun link(vs: String, fs: String): Int {
        val v = compile(Gl.GL_VERTEX_SHADER, vs)
        val f = compile(Gl.GL_FRAGMENT_SHADER, fs)
        val p = Gl.glCreateProgram()
        Gl.glAttachShader(p, v); Gl.glAttachShader(p, f); Gl.glLinkProgram(p)
        val ok = IntArray(1); Gl.glGetProgramiv(p, Gl.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("link: " + Gl.glGetProgramInfoLog(p))
        Gl.glDeleteShader(v); Gl.glDeleteShader(f)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = Gl.glCreateShader(type); Gl.glShaderSource(s, src); Gl.glCompileShader(s)
        val ok = IntArray(1); Gl.glGetShaderiv(s, Gl.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("compile: " + Gl.glGetShaderInfoLog(s) + "\n" + src)
        return s
    }

    companion object {
        private const val VERTEX_SRC = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPos;
            attribute vec3 aNrm;
            varying vec3 vNormal;
            void main() {
                // mat3(mat4) is not available in GLSL ES 1.00, so the normal is
                // rotated by the model matrix with w = 0 instead.
                vNormal = normalize((uModel * vec4(aNrm, 0.0)).xyz);
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        private const val FRAGMENT_SRC = """
            precision mediump float;
            uniform vec4 uColor;
            varying vec3 vNormal;
            void main() {
                vec3 n = normalize(vNormal);
                // Key light from the upper front-left, plus a dimmer fill from
                // behind so the unlit side does not go flat black against the
                // near-black panel.
                vec3 key = normalize(vec3(-0.4, 0.8, 0.6));
                vec3 fill = normalize(vec3(0.5, -0.2, -0.7));
                float d = max(dot(n, key), 0.0);
                float f = max(dot(n, fill), 0.0) * 0.25;
                // A touch of specular sells these as metal rather than plastic.
                float spec = pow(max(dot(reflect(-key, n), vec3(0.0, 0.0, 1.0)), 0.0), 24.0) * 0.35;
                vec3 c = uColor.rgb * (0.22 + 0.85 * d + f) + spec;
                gl_FragColor = vec4(clamp(c, 0.0, 1.0), uColor.a);
            }
        """
    }
}
