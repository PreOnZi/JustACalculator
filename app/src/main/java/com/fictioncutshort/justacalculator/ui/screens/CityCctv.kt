package com.fictioncutshort.justacalculator.ui.screens

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// CityCctv — the three monitors on the desk inside Building 10.
//
// Every screen shows a 2x2 grid of LIVE views of Calculator City, each one taken
// from one of the security cameras hanging off the buildings' corners (the same
// cameraMounts the visible camera models are drawn at). So the desk is watching
// the city with the city's own eyes.
//
// How it works:
//   · One off-screen FBO holds a COLS x ROWS atlas of small views (one cell per
//     feed). CityGLRenderer re-renders the city into ONE cell per frame, round
//     robin — 12 cells means each feed refreshes at ~5 fps, which is both cheap
//     and exactly how a bank of CCTV monitors actually looks.
//   · The three screen quads are lifted straight out of the mute button model
//     (the flat white panels inset in each monitor's bezel), so if the model
//     moves or the desk is re-arranged in Blender, the feeds follow it.
//   · The screens draw with their own textured shader: desaturated, scanlined,
//     grainy, and — once the city has gone to permanent night — pushed into
//     infrared green.
//
// The renderer owns the scene; this class owns the atlas, the screen quads and
// the CCTV look. It calls back into the renderer to draw the world.
// ─────────────────────────────────────────────────────────────────────────────

/** One monitor: a world-space quad plus the block of atlas cells it displays. */
class CctvMonitor(
    private val ox: Float, private val oy: Float, private val oz: Float,   // bottom-left corner
    private val rx: Float, private val ry: Float, private val rz: Float,   // full width vector
    private val ux: Float, private val uy: Float, private val uz: Float,   // full height vector
    val firstCell: Int, val cols: Int, val rows: Int,
) {
    val cellCount get() = cols * rows

    /** Appends this monitor's sub-quads (pos3 + atlasUV2 + cellUV2) to [dst]. */
    fun emit(dst: MutableList<Float>) {
        // Half-texel inset, so a view never bleeds into its neighbour in the atlas.
        val insetU = 0.5f / (CityCctv.COLS * CityCctv.CELL_W)
        val insetV = 0.5f / (CityCctv.ROWS * CityCctv.CELL_H)
        for (r in 0 until rows) for (c in 0 until cols) {
            // Quad-space span of this view. Row 0 is the TOP one (reading order),
            // and the quad's own "up" runs from the bottom edge, hence 1 - r/rows.
            val q0 = c.toFloat() / cols;        val q1 = (c + 1f) / cols
            val p1 = 1f - r.toFloat() / rows;   val p0 = 1f - (r + 1f) / rows

            val cell = firstCell + r * cols + c
            val ac = cell % CityCctv.COLS
            val ar = cell / CityCctv.COLS
            val u0 = ac.toFloat() / CityCctv.COLS + insetU
            val u1 = (ac + 1f) / CityCctv.COLS - insetU
            val v0 = ar.toFloat() / CityCctv.ROWS + insetV
            val v1 = (ar + 1f) / CityCctv.ROWS - insetV

            // (quadU, quadV) → world; (atlasU, atlasV) → texture; (cellU, cellV) →
            // 0..1 inside this one view, which is what the scanlines/vignette ride.
            fun vert(qu: Float, qv: Float, au: Float, av: Float, cu: Float, cv: Float) {
                dst.add(ox + rx * qu + ux * qv)
                dst.add(oy + ry * qu + uy * qv)
                dst.add(oz + rz * qu + uz * qv)
                dst.add(au); dst.add(av)
                dst.add(cu); dst.add(cv)
            }
            vert(q0, p0, u0, v0, 0f, 0f);  vert(q1, p0, u1, v0, 1f, 0f);  vert(q1, p1, u1, v1, 1f, 1f)
            vert(q0, p0, u0, v0, 0f, 0f);  vert(q1, p1, u1, v1, 1f, 1f);  vert(q0, p1, u0, v1, 0f, 1f)
        }
    }
}

/** One live camera: where it hangs, and the patch of city it watches. */
class CctvFeed(
    val ex: Float, val ey: Float, val ez: Float,
    val tx: Float, val ty: Float, val tz: Float,
    val phase: Float,
)

class CityCctv {

    companion object {
        // A 2x2 grid hands each sub-view the whole screen's aspect, so the cells have
        // to carry it too or every feed comes out stretched. The desk's monitors are
        // 16:9-ish (2.57 / 1.52 in model units).
        const val CELL_W = 256
        const val CELL_H = 152
        const val COLS = 4
        const val ROWS = 3
        const val CELLS = COLS * ROWS
        private const val FEED_FOV = 52f
        // How far a feed camera swings, and how far it will lean off its beat to
        // follow the monster once it is out there.
        private const val SWAY_RAD = 0.13f
        private const val WATCH_RANGE = 620f

        /**
         * Lifts the front face out of one flat screen box (a 12-triangle slab from
         * the model, already in world space) and turns it into a monitor quad.
         *
         * The panel is picked geometrically rather than by vertex order: of the
         * box's near-vertical faces, we take the one with the largest AREA × how far
         * its normal points toward ([inX], [inZ]) — the room the desk faces. Area has
         * to be in there: the desk's monitors are small slabs turned at an angle, and
         * a thin side face of one can point more directly at the room than the screen
         * itself does, so scoring on direction alone would texture the monitor's edge.
         * The screen face is several times the area of any side, so it wins.
         *
         * Returns null if [verts] is not a slab we recognise, in which case the
         * monitor simply stays dark.
         */
        fun frontQuad(
            verts: FloatArray, inX: Float, inZ: Float,
            firstCell: Int, cols: Int, rows: Int, push: Float,
        ): CctvMonitor? {
            if (verts.size < 9) return null

            var bestScore = 0f
            var bestDot = 0f
            var nx = 0f; var ny = 0f; var nz = 0f
            var i = 0
            while (i + 8 < verts.size) {
                val ax = verts[i + 3] - verts[i];     val ay = verts[i + 4] - verts[i + 1]; val az = verts[i + 5] - verts[i + 2]
                val bx = verts[i + 6] - verts[i];     val by = verts[i + 7] - verts[i + 1]; val bz = verts[i + 8] - verts[i + 2]
                var cx = ay * bz - az * by
                var cy = az * bx - ax * bz
                var cz = ax * by - ay * bx
                val len = sqrt(cx * cx + cy * cy + cz * cz)
                i += 9
                if (len < 1e-6f) continue
                val area = len * 0.5f                         // |a × b| is twice the triangle's area
                cx /= len; cy /= len; cz /= len
                if (abs(cy) > 0.5f) continue                  // the slab's top / bottom edge
                // Faces come both ways round (culling is off) — take whichever
                // orientation looks into the room and score that.
                val d = cx * inX + cz * inZ
                val dd = abs(d)
                val score = area * dd
                if (score > bestScore) {
                    bestScore = score
                    bestDot = dd
                    val s = if (d < 0f) -1f else 1f
                    nx = cx * s; ny = cy * s; nz = cz * s
                }
            }
            if (bestDot < 0.2f) return null

            // Screen basis: right = up x normal, up = +Y (monitors stand upright).
            val rx = -nz; val rz = nx
            val rl = hypot(rx, rz)
            if (rl < 1e-4f) return null
            val ux = rx / rl; val uz = rz / rl

            // Bound only the verts lying ON the front plane, so the box's depth
            // doesn't inflate the panel.
            var pOx = 0f; var pOy = 0f; var pOz = 0f; var pN = 0
            i = 0
            while (i + 2 < verts.size) {
                val dx = verts[i]; val dy = verts[i + 1]; val dz = verts[i + 2]
                pOx += dx; pOy += dy; pOz += dz; pN++
                i += 3
            }
            pOx /= pN; pOy /= pN; pOz /= pN                    // box centre
            // Depth of every vertex along the normal: the slab's front plane sits at
            // maxD, its back plane at minD. Anything within a quarter of that depth
            // of the front is the face we are after.
            var maxD = -Float.MAX_VALUE
            var minD = Float.MAX_VALUE
            i = 0
            while (i + 2 < verts.size) {
                val d = (verts[i] - pOx) * nx + (verts[i + 1] - pOy) * ny + (verts[i + 2] - pOz) * nz
                if (d > maxD) maxD = d
                if (d < minD) minD = d
                i += 3
            }
            val tol = ((maxD - minD) * 0.25f).coerceAtLeast(1e-3f)
            var minU = Float.MAX_VALUE; var maxU = -Float.MAX_VALUE
            var minV = Float.MAX_VALUE; var maxV = -Float.MAX_VALUE
            i = 0
            while (i + 2 < verts.size) {
                val dx = verts[i] - pOx; val dy = verts[i + 1] - pOy; val dz = verts[i + 2] - pOz
                val d = dx * nx + dy * ny + dz * nz
                if (maxD - d < tol) {
                    val u = dx * ux + dz * uz
                    if (u < minU) minU = u; if (u > maxU) maxU = u
                    if (dy < minV) minV = dy; if (dy > maxV) maxV = dy
                }
                i += 3
            }
            if (maxU - minU < 1e-3f || maxV - minV < 1e-3f) return null

            // A hair of the panel is left showing all round as a bezel.
            val bez = 0.03f
            val u0 = minU + (maxU - minU) * bez; val u1 = maxU - (maxU - minU) * bez
            val v0 = minV + (maxV - minV) * bez; val v1 = maxV - (maxV - minV) * bez
            val px = pOx + nx * (maxD + push)
            val py = pOy + ny * (maxD + push)
            val pz = pOz + nz * (maxD + push)
            return CctvMonitor(
                px + ux * u0, py + v0, pz + uz * u0,
                ux * (u1 - u0), 0f, uz * (u1 - u0),
                0f, v1 - v0, 0f,
                firstCell, cols, rows,
            )
        }
    }

    // ── Atlas ────────────────────────────────────────────────────────────────
    private var fbo = 0
    private var tex = 0
    private var depthRb = 0
    private var prog = 0
    private var aPos = 0; private var aAtlas = 0; private var aCell = 0
    private var uMVP = 0; private var uTex = 0; private var uTime = 0; private var uNight = 0

    private var quadBuf: FloatBuffer? = null
    private var quadVerts = 0
    private var nextCell = 0

    var feeds: List<CctvFeed> = emptyList()
    val ready get() = prog != 0 && fbo != 0 && quadBuf != null && feeds.isNotEmpty()

    // Something worth watching. When the monster is out roaming, the cameras near
    // it drift off their beat and follow it — the desk sees it before you do.
    private var watchOn = false
    private var watchX = 0f
    private var watchZ = 0f
    fun watch(x: Float, z: Float, active: Boolean) { watchX = x; watchZ = z; watchOn = active }

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)

    /** (Re)creates the GL objects. Safe to call on every context loss. */
    fun init() {
        release()
        prog = buildProg()
        if (prog == 0) return
        aPos    = GLES20.glGetAttribLocation(prog, "aPosition")
        aAtlas  = GLES20.glGetAttribLocation(prog, "aAtlas")
        aCell   = GLES20.glGetAttribLocation(prog, "aCell")
        uMVP    = GLES20.glGetUniformLocation(prog, "uMVP")
        uTex    = GLES20.glGetUniformLocation(prog, "uTex")
        uTime   = GLES20.glGetUniformLocation(prog, "uTime")
        uNight  = GLES20.glGetUniformLocation(prog, "uNight")

        val w = COLS * CELL_W
        val h = ROWS * CELL_H
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); tex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, w, h, 0,
            GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glGenRenderbuffers(1, ids, 0); depthRb = ids[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthRb)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, w, h)

        GLES20.glGenFramebuffers(1, ids, 0); fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, tex, 0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
            GLES20.GL_RENDERBUFFER, depthRb)
        val ok = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
        // Start every screen black rather than on whatever junk the driver handed us.
        if (ok) {
            GLES20.glViewport(0, 0, w, h)
            GLES20.glClearColor(0.01f, 0.02f, 0.02f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (!ok) { release(); android.util.Log.w("CityGL", "CCTV framebuffer incomplete") }
    }

    fun release() {
        val ids = IntArray(1)
        if (fbo != 0)     { ids[0] = fbo;     GLES20.glDeleteFramebuffers(1, ids, 0);  fbo = 0 }
        if (depthRb != 0) { ids[0] = depthRb; GLES20.glDeleteRenderbuffers(1, ids, 0); depthRb = 0 }
        if (tex != 0)     { ids[0] = tex;     GLES20.glDeleteTextures(1, ids, 0);      tex = 0 }
        if (prog != 0)    { GLES20.glDeleteProgram(prog); prog = 0 }
    }

    /** Bakes the monitors into the single vertex buffer every screen is drawn from. */
    fun setMonitors(monitors: List<CctvMonitor>) {
        if (monitors.isEmpty()) { quadBuf = null; quadVerts = 0; return }
        val data = mutableListOf<Float>()
        for (m in monitors) m.emit(data)
        val arr = FloatArray(data.size) { data[it] }
        quadBuf = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().also { it.put(arr); it.position(0) }
        quadVerts = arr.size / 7
    }

    /**
     * Re-renders [count] feeds into their atlas cells, round robin. [drawScene] is
     * handed the feed's view-projection and eye position and draws the city with
     * it. The caller must restore its own viewport afterwards.
     */
    fun refresh(count: Int, drawScene: (FloatArray, Float, Float, Float) -> Unit) {
        if (!ready) return
        val t = (System.nanoTime() / 1_000_000L) * 0.001f
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClearColor(0.01f, 0.02f, 0.02f, 1f)
        repeat(count.coerceAtMost(feeds.size)) {
            val idx = nextCell % feeds.size
            nextCell = (nextCell + 1) % feeds.size
            val f = feeds[idx]
            val cx = (idx % COLS) * CELL_W
            val cy = (idx / COLS) * CELL_H
            GLES20.glViewport(cx, cy, CELL_W, CELL_H)
            GLES20.glScissor(cx, cy, CELL_W, CELL_H)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            // Slow pan around the camera's beat, so a feed of an empty street still
            // reads as running footage rather than a still.
            var dx = f.tx - f.ex
            var dz = f.tz - f.ez
            val a = sin(t * 0.22f + f.phase) * SWAY_RAD
            val ca = cos(a); val sa = sin(a)
            val sx = dx * ca - dz * sa
            val sz = dx * sa + dz * ca
            dx = sx; dz = sz
            var tx = f.ex + dx
            var ty = f.ty
            var tz = f.ez + dz
            if (watchOn) {
                val d = hypot(watchX - f.ex, watchZ - f.ez)
                if (d < WATCH_RANGE) {
                    val k = (1f - d / WATCH_RANGE).coerceIn(0f, 1f) * 0.85f
                    tx += (watchX - tx) * k
                    ty += (60f - ty) * k
                    tz += (watchZ - tz) * k
                }
            }
            Matrix.perspectiveM(proj, 0, FEED_FOV, CELL_W.toFloat() / CELL_H, 2f, 2600f)
            Matrix.setLookAtM(view, 0, f.ex, f.ey, f.ez, tx, ty, tz, 0f, 1f, 0f)
            Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
            drawScene(mvp, f.ex, f.ey, f.ez)
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /**
     * Draws the three screens into the room. Opaque and self-lit — a monitor is a
     * light source, so it survives the night overlay the rest of the city is under.
     * Leaves no program bound; the caller re-binds its own.
     */
    fun draw(sceneMvp: FloatArray, night: Float) {
        val buf = quadBuf ?: return
        if (prog == 0 || quadVerts == 0) return
        GLES20.glUseProgram(prog)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, sceneMvp, 0)
        GLES20.glUniform1f(uTime, (System.nanoTime() / 1_000_000L % 100_000L) * 0.001f)
        GLES20.glUniform1f(uNight, night.coerceIn(0f, 1f))
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(uTex, 0)

        val stride = 7 * 4
        buf.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, buf)
        GLES20.glEnableVertexAttribArray(aPos)
        buf.position(3)
        GLES20.glVertexAttribPointer(aAtlas, 2, GLES20.GL_FLOAT, false, stride, buf)
        GLES20.glEnableVertexAttribArray(aAtlas)
        buf.position(5)
        GLES20.glVertexAttribPointer(aCell, 2, GLES20.GL_FLOAT, false, stride, buf)
        GLES20.glEnableVertexAttribArray(aCell)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, quadVerts)

        // The scene shader only has attribute 0; leaving these enabled with client
        // pointers into our buffer is how you get a garbage draw two frames later.
        GLES20.glDisableVertexAttribArray(aAtlas)
        GLES20.glDisableVertexAttribArray(aCell)
        buf.position(0)
    }

    // ── Shader ───────────────────────────────────────────────────────────────
    private val VS = """
        uniform mat4 uMVP;
        attribute vec4 aPosition;
        attribute vec2 aAtlas;
        attribute vec2 aCell;
        varying vec2 vAtlas;
        varying vec2 vCell;
        void main(){
            vAtlas = aAtlas;
            vCell  = aCell;
            gl_Position = uMVP * aPosition;
        }""".trimIndent()

    // The CCTV look: greyed down, scanlined, grainy, with a bright seam between
    // views. Once the city has gone to permanent night the feeds flip to infrared
    // — which is the only reason you can still see anything out there at all.
    private val FS = """
        precision mediump float;
        uniform sampler2D uTex;
        uniform float uTime;
        uniform float uNight;
        varying vec2 vAtlas;
        varying vec2 vCell;
        void main(){
            // A tape-roll wobble: one soft band drifts up each view, dragging the
            // sample sideways as it passes.
            float band = fract(vCell.y * 0.5 - uTime * 0.06);
            float tear = smoothstep(0.96, 1.0, band) * 0.004;
            vec3 c = texture2D(uTex, vAtlas + vec2(tear, 0.0)).rgb;

            float l = dot(c, vec3(0.299, 0.587, 0.114));
            vec3 day = mix(c, vec3(l), 0.62) * vec3(0.92, 0.97, 1.0);   // cool, washed out
            vec3 ir  = vec3(l * 0.30, l * 1.25 + 0.05, l * 0.42);       // infrared green
            c = mix(day, ir, uNight);

            // Scanlines + a per-view flicker + grain.
            float scan = 0.86 + 0.14 * abs(sin(vCell.y * 96.0));
            float flick = 0.94 + 0.06 * sin(uTime * 7.0 + vAtlas.x * 41.0);
            float grain = fract(sin(dot(vCell + fract(uTime), vec2(12.9898, 78.233))) * 43758.5453);
            c = c * scan * flick + (grain - 0.5) * 0.045;

            // Vignette, then the black seam that separates one view from the next.
            vec2 d = abs(vCell - 0.5);
            c *= 1.0 - 0.55 * dot(d, d);
            float edge = min(min(vCell.x, 1.0 - vCell.x), min(vCell.y, 1.0 - vCell.y));
            c *= smoothstep(0.0, 0.02, edge);

            gl_FragColor = vec4(max(c, vec3(0.0)) + vec3(0.02, 0.03, 0.03), 1.0);
        }""".trimIndent()

    private fun buildProg(): Int {
        val v = comp(GLES20.GL_VERTEX_SHADER, VS)
        val f = comp(GLES20.GL_FRAGMENT_SHADER, FS)
        if (v == 0 || f == 0) return 0
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f); GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            android.util.Log.w("CityGL", "CCTV link failed: " + GLES20.glGetProgramInfoLog(p))
            GLES20.glDeleteProgram(p)
            return 0
        }
        return p
    }

    private fun comp(type: Int, src: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh, src); GLES20.glCompileShader(sh)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            android.util.Log.w("CityGL", "CCTV shader failed: " + GLES20.glGetShaderInfoLog(sh))
            GLES20.glDeleteShader(sh)
            return 0
        }
        return sh
    }
}
