package com.fictioncutshort.justacalculator.ui.screens

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fictioncutshort.justacalculator.logic.Currency
import com.fictioncutshort.justacalculator.logic.CurrencyStore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Building 8 — "gambling".
//
// A large white room bordered with the surveillance-exhibition blue. A single
// arcade cabinet stands in the far corner. Walk to it (single joystick) and the
// screen lands on a fake "website" listing five rigged games — one per currency
// the player carried through the city. Each game drains its currency to zero;
// the coins lottery (seeded back in the city after Building 5) is settled last.
// Once every currency is spent the building completes.
// ─────────────────────────────────────────────────────────────────────────────

private val SURVEIL_BLUE = Color(0xFF3A6FFF)

@Composable
fun Building8Casino(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val renderer = remember { CasinoRoomRenderer(context) }

    // null = walking the 3D room; "browser" = the arcade screen (games) is open.
    // WHICH games have been played, and what is left, is already persistent - the
    // currency balances themselves are the record (a spent game leaves its currency
    // at zero, which is what building8Complete reads). What wasn't saved was WHERE
    // the player was standing when they left, so that's what's restored here.
    var overlay by remember { mutableStateOf<String?>(com.fictioncutshort.justacalculator.logic.BuildingProgress.getString(context, 8, "overlay").ifBlank { null }) }
    LaunchedEffect(overlay) { com.fictioncutshort.justacalculator.logic.BuildingProgress.putString(context, 8, "overlay", overlay ?: "") }
    var playShow by remember { mutableStateOf(false) }
    var playRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect(0f, 0f, 0f, 0f)) }
    var finished by remember { mutableStateOf(CurrencyStore.building8Complete(context)) }
    var brokeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(finished) { renderer.showDoor = finished }

    // Poll the render thread's proximity flags while walking. Reaching the exit
    // door after finishing just leaves — no prompt.
    LaunchedEffect(overlay, finished) {
        while (overlay == null) {
            playShow = renderer.playShow
            playRect = androidx.compose.ui.geometry.Rect(renderer.playL, renderer.playT, renderer.playR, renderer.playB)
            if (finished && renderer.atDoor) { onComplete(); break }
            delay(40)
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFFF2F5FF))) {
        // ── 3D room ───────────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    preserveEGLContextOnPause = true
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Walking HUD (hidden while an overlay is up) ─────────────────────────
        if (overlay == null) {
            // The arcade's blue screen IS the button: the whole projected screen
            // rectangle is one invisible clickable region (no separate PLAY pill).
            if (playShow) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val l = maxWidth * playRect.left
                    val t = maxHeight * playRect.top
                    val w = maxWidth * (playRect.right - playRect.left)
                    val h = maxHeight * (playRect.bottom - playRect.top)
                    Box(
                        modifier = Modifier.offset(x = l, y = t).size(w, h)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null
                            ) {
                                renderer.joyX = 0f; renderer.joyY = 0f
                                if (finished) brokeDialog = true else overlay = "browser"
                            }
                    )
                }
            }

            // (Reaching the exit door leaves automatically — see the poll effect.)

            CityJoystick(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                onJoy = { x, y -> renderer.joyX = x; renderer.joyY = y }
            )
        }

        // ── The arcade "website": a persistent XP browser wrapping all games ────
        if (overlay == "browser") {
            ArcadeBrowser(
                onExitToRoom = { overlay = null },
                onAllDone = { overlay = null; finished = true },   // teleport back to the room
                onGameReturned = { },
            )
        }

        // "You're broke & not welcome." when tapping the machine after finishing.
        if (brokeDialog) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        brokeDialog = false; onComplete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You're broke & not welcome.", color = Color.White, fontSize = 20.sp,
                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A6FFF))
                            .clickable { brokeDialog = false; onComplete() }
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) { Text("OK", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// CasinoRoomRenderer — the walkable white room with the arcade cabinet.
// GLES20, client-side buffers (the scene is tiny). First-person camera driven by
// the single joystick: X turns, Y walks forward/back.
// ═════════════════════════════════════════════════════════════════════════════

class CasinoRoomRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // Input from the Compose joystick (X = yaw, Y = forward/back; up = forward).
    @Volatile var joyX = 0f
    @Volatile var joyY = 0f
    // True when the player is close enough to the cabinet to use it.
    @Volatile var nearArcade = false

    private data class Mesh(val r: Float, val g: Float, val b: Float,
                            val emissive: Float, val buf: FloatBuffer, val count: Int)

    private val meshes = ArrayList<Mesh>()

    private var prog = 0
    private var aPos = 0; private var aNrm = 0
    private var uMVP = 0; private var uModel = 0; private var uColor = 0; private var uEmis = 0

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val ident = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    // Room + player.
    private val H = 10f              // room half-size (X and Z)
    private val WH = 11f            // wall height (tall room)
    private val EYE = 2.6f          // adult-height first-person eye
    private var pX = 0f
    private var pZ = 7.5f
    private var yaw = 0f            // 0 = facing -Z (into the room)
    private val arcadeX = 6.3f
    private val arcadeZ = -6.3f
    private val arcadeHeight = 4.2f
    private var lastNs = 0L

    // Projected 2D rect (0..1 fractions) of the cabinet's blue screen, so the
    // whole screen becomes the tappable PLAY button. playShow = on-screen & near.
    @Volatile var playShow = false
    @Volatile var playL = 0f; @Volatile var playT = 0f; @Volatile var playR = 1f; @Volatile var playB = 1f
    // World-space AABB of the blue screen mesh (set in buildArcade).
    private var hasScreen = false
    private var sMinX = 0f; private var sMinY = 0f; private var sMinZ = 0f
    private var sMaxX = 0f; private var sMaxY = 0f; private var sMaxZ = 0f

    // Exit door — only appears once every game is finished.
    @Volatile var showDoor = false
    @Volatile var atDoor = false
    private val doorCx = -4f
    private val doorCz = -H + 0.05f
    private val doorMeshes = ArrayList<Mesh>()
    private var buildingDoor = false   // routes addMesh into doorMeshes

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.93f, 0.95f, 1f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)   // interior — both sides visible
        buildProgram()
        meshes.clear(); doorMeshes.clear()
        buildRoom()
        buildArcade()
        buildDoor()
        pX = 0f; pZ = 7.5f; lastNs = 0L
        // Start already looking at the arcade cabinet in the far corner.
        yaw = kotlin.math.atan2(arcadeX - pX, -(arcadeZ - pZ))
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height.coerceAtLeast(1))
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(proj, 0, 60f, aspect, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        if (lastNs == 0L) lastNs = now
        val dt = ((now - lastNs) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
        lastNs = now
        step(dt)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val fwdX = sin(yaw); val fwdZ = -cos(yaw)
        Matrix.setLookAtM(view, 0, pX, EYE, pZ, pX + fwdX, EYE, pZ + fwdZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        Matrix.multiplyMM(mvp, 0, vp, 0, ident, 0)

        // Project the blue screen's 8 AABB corners → a 2D rect = the PLAY button.
        if (hasScreen && nearArcade) {
            var minFx = 1f; var minFy = 1f; var maxFx = 0f; var maxFy = 0f
            var allFront = true
            val clip = FloatArray(4); val p = FloatArray(4)
            for (ci in 0 until 8) {
                p[0] = if (ci and 1 == 0) sMinX else sMaxX
                p[1] = if (ci and 2 == 0) sMinY else sMaxY
                p[2] = if (ci and 4 == 0) sMinZ else sMaxZ
                p[3] = 1f
                Matrix.multiplyMV(clip, 0, vp, 0, p, 0)
                if (clip[3] <= 0.01f) { allFront = false; break }
                val fx = (clip[0] / clip[3]) * 0.5f + 0.5f
                val fy = 1f - ((clip[1] / clip[3]) * 0.5f + 0.5f)
                if (fx < minFx) minFx = fx; if (fx > maxFx) maxFx = fx
                if (fy < minFy) minFy = fy; if (fy > maxFy) maxFy = fy
            }
            if (allFront) {
                playL = minFx.coerceIn(0f, 1f); playT = minFy.coerceIn(0f, 1f)
                playR = maxFx.coerceIn(0f, 1f); playB = maxFy.coerceIn(0f, 1f)
                playShow = (playR - playL) > 0.02f && (playB - playT) > 0.02f
            } else playShow = false
        } else playShow = false

        GLES20.glUseProgram(prog)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, ident, 0)
        val toDraw = if (showDoor) meshes.asSequence() + doorMeshes.asSequence() else meshes.asSequence()
        for (m in toDraw) {
            GLES20.glUniform3f(uColor, m.r, m.g, m.b)
            GLES20.glUniform1f(uEmis, m.emissive)
            m.buf.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 6 * 4, m.buf)
            GLES20.glEnableVertexAttribArray(aPos)
            m.buf.position(3)
            GLES20.glVertexAttribPointer(aNrm, 3, GLES20.GL_FLOAT, false, 6 * 4, m.buf)
            GLES20.glEnableVertexAttribArray(aNrm)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, m.count)
        }
    }

    private fun step(dt: Float) {
        val turn = 2.2f
        val speed = 5.0f
        val dead = 0.14f
        if (kotlin.math.abs(joyX) > dead) yaw += joyX * turn * dt
        val move = if (kotlin.math.abs(joyY) > dead) -joyY else 0f
        if (move != 0f) {
            val fwdX = sin(yaw); val fwdZ = -cos(yaw)
            var nx = pX + fwdX * speed * move * dt
            var nz = pZ + fwdZ * speed * move * dt
            val margin = 0.6f
            nx = nx.coerceIn(-H + margin, H - margin)
            nz = nz.coerceIn(-H + margin, H - margin)
            // Don't walk through the cabinet.
            if (hypot(nx - arcadeX, nz - arcadeZ) > 1.3f) { pX = nx; pZ = nz }
        }
        nearArcade = hypot(pX - arcadeX, pZ - arcadeZ) < 3.0f
        atDoor = showDoor && hypot(pX - doorCx, pZ - doorCz) < 2.8f
    }

    // ── Geometry ───────────────────────────────────────────────────────────────
    private fun buildRoom() {
        val w = 1.0f        // white walls
        val fc = 0.72f      // light-grey floor + ceiling
        // Floor (light grey).
        addQuad(-H, 0f, -H,  H, 0f, -H,  H, 0f, H,  -H, 0f, H, 0f, 1f, 0f, fc, fc, fc, 0f)
        // Ceiling (light grey).
        addQuad(-H, WH, H,  H, WH, H,  H, WH, -H,  -H, WH, -H, 0f, -1f, 0f, fc, fc, fc, 0f)
        // Walls (white; normals face inward).
        addQuad(-H, 0f, H,  H, 0f, H,  H, WH, H,  -H, WH, H, 0f, 0f, -1f, w, w, w, 0f)     // south
        addQuad(H, 0f, -H,  -H, 0f, -H,  -H, WH, -H,  H, WH, -H, 0f, 0f, 1f, w, w, w, 0f)  // north
        addQuad(H, 0f, H,  H, 0f, -H,  H, WH, -H,  H, WH, H, -1f, 0f, 0f, w, w, w, 0f)     // east
        addQuad(-H, 0f, -H,  -H, 0f, H,  -H, WH, H,  -H, WH, -H, 1f, 0f, 0f, w, w, w, 0f)  // west

        // Surveillance-blue framing: thin emissive strips. Blue ≈ (0.23,0.44,1).
        val br = 0.23f; val bg = 0.44f; val bb = 1f
        val t = 0.04f      // half-thickness of a strip (thin)
        val inset = 0.02f  // pull off the wall so it isn't z-fought
        // Floor + ceiling perimeter bands on each wall.
        for (band in listOf(0f, WH)) {
            val y0 = if (band == 0f) 0f else WH - 2 * t
            val y1 = y0 + 2 * t
            // south / north
            addQuad(-H, y0, H - inset, H, y0, H - inset, H, y1, H - inset, -H, y1, H - inset,
                0f, 0f, -1f, br, bg, bb, 1f)
            addQuad(H, y0, -H + inset, -H, y0, -H + inset, -H, y1, -H + inset, H, y1, -H + inset,
                0f, 0f, 1f, br, bg, bb, 1f)
            // east / west
            addQuad(H - inset, y0, H, H - inset, y0, -H, H - inset, y1, -H, H - inset, y1, H,
                -1f, 0f, 0f, br, bg, bb, 1f)
            addQuad(-H + inset, y0, -H, -H + inset, y0, H, -H + inset, y1, H, -H + inset, y1, -H,
                1f, 0f, 0f, br, bg, bb, 1f)
        }
        // Vertical corner strips.
        for (sx in listOf(-1, 1)) for (sz in listOf(-1, 1)) {
            val x = sx * (H - inset); val z = sz * (H - inset)
            addQuad(x - t, 0f, z, x + t, 0f, z, x + t, WH, z, x - t, WH, z, 0f, 0f, -sz.toFloat(),
                br, bg, bb, 1f)
        }
    }

    private fun buildArcade() {
        val groups = runCatching {
            ObjLoader.load(context.assets, "models/casino/arcade.obj", "models/casino/arcade.mtl")
        }.getOrNull() ?: return
        // Fit: raw height ≈ 6.57 → target arcadeHeight units; base on floor; corner + turn.
        val scale = arcadeHeight / 6.57f
        val model = FloatArray(16)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, arcadeX, 0f, arcadeZ)
        Matrix.rotateM(model, 0, 210f, 0f, 1f, 0f)   // screen angled toward the room
        Matrix.scaleM(model, 0, scale, scale, scale)
        // Bake the transform into world-space verts so everything shares one draw path.
        for (g in groups) {
            val transformed = FloatArray(g.verts.size)
            val v = FloatArray(4); val out = FloatArray(4)
            var i = 0
            while (i < g.verts.size) {
                v[0] = g.verts[i]; v[1] = g.verts[i + 1]; v[2] = g.verts[i + 2]; v[3] = 1f
                Matrix.multiplyMV(out, 0, model, 0, v, 0)
                transformed[i] = out[0]; transformed[i + 1] = out[1]; transformed[i + 2] = out[2]
                i += 3
            }
            // The blue screen mesh (Material.001, Kd ≈ 0,0.72,1) → record its world
            // AABB; its projection is the clickable PLAY area.
            if (g.r < 0.1f && g.g > 0.45f && g.b > 0.8f) {
                var j = 0
                sMinX = Float.MAX_VALUE; sMinY = Float.MAX_VALUE; sMinZ = Float.MAX_VALUE
                sMaxX = -Float.MAX_VALUE; sMaxY = -Float.MAX_VALUE; sMaxZ = -Float.MAX_VALUE
                while (j < transformed.size) {
                    val x = transformed[j]; val y = transformed[j + 1]; val z = transformed[j + 2]
                    if (x < sMinX) sMinX = x; if (x > sMaxX) sMaxX = x
                    if (y < sMinY) sMinY = y; if (y > sMaxY) sMaxY = y
                    if (z < sMinZ) sMinZ = z; if (z > sMaxZ) sMaxZ = z
                    j += 3
                }
                hasScreen = true
            }
            val (buf, count) = interleave(transformed) ?: continue
            // The cabinet reads near-black in Blender; lift very dark materials to
            // a legible grey so it looks like a machine, not a silhouette.
            fun grey(c: Float) = (c * 0.6f + 0.34f).coerceIn(0f, 1f)
            addMesh(grey(g.r), grey(g.g), grey(g.b), 0f, buf, count)
        }
    }

    /** Add one quad (two triangles) with a shared flat normal + color. */
    private fun addQuad(
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float,
        nx: Float, ny: Float, nz: Float, r: Float, g: Float, b: Float, emis: Float,
    ) {
        val d = FloatArray(6 * 6)
        var o = 0
        fun vert(x: Float, y: Float, z: Float) {
            d[o++] = x; d[o++] = y; d[o++] = z; d[o++] = nx; d[o++] = ny; d[o++] = nz
        }
        vert(x0, y0, z0); vert(x1, y1, z1); vert(x2, y2, z2)
        vert(x0, y0, z0); vert(x2, y2, z2); vert(x3, y3, z3)
        val buf = ByteBuffer.allocateDirect(d.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(d); position(0) }
        addMesh(r, g, b, emis, buf, 6)
    }

    private fun addMesh(r: Float, g: Float, b: Float, emis: Float, buf: FloatBuffer, count: Int) {
        (if (buildingDoor) doorMeshes else meshes).add(Mesh(r, g, b, emis, buf, count))
    }

    /** A bright green EXIT door on the north wall — built once, drawn only when finished. */
    private fun buildDoor() {
        buildingDoor = true
        val hw = 1.2f; val ht = 4.4f; val z = doorCz
        // Dark panel (recessed).
        addQuad(doorCx - hw, 0f, z, doorCx + hw, 0f, z, doorCx + hw, ht, z, doorCx - hw, ht, z,
            0f, 0f, 1f, 0.06f, 0.10f, 0.07f, 0f)
        // Emissive green frame (4 thin bars just in front of the panel).
        val gr = 0.15f; val gg = 1f; val gb = 0.45f; val t = 0.16f; val zf = z + 0.06f
        addQuad(doorCx - hw - t, 0f, zf, doorCx - hw, 0f, zf, doorCx - hw, ht, zf, doorCx - hw - t, ht, zf, 0f, 0f, 1f, gr, gg, gb, 1f)
        addQuad(doorCx + hw, 0f, zf, doorCx + hw + t, 0f, zf, doorCx + hw + t, ht, zf, doorCx + hw, ht, zf, 0f, 0f, 1f, gr, gg, gb, 1f)
        addQuad(doorCx - hw - t, ht, zf, doorCx + hw + t, ht, zf, doorCx + hw + t, ht + t, zf, doorCx - hw - t, ht + t, zf, 0f, 0f, 1f, gr, gg, gb, 1f)
        buildingDoor = false
    }

    /** Positions → interleaved (pos+flat normal) buffer; returns (buffer, vertCount). */
    private fun interleave(verts: FloatArray): Pair<FloatBuffer, Int>? {
        val triCount = verts.size / 9
        if (triCount == 0) return null
        val out = FloatArray(triCount * 3 * 6)
        var o = 0; var t = 0
        while (t < triCount) {
            val base = t * 9
            val ax = verts[base]; val ay = verts[base + 1]; val az = verts[base + 2]
            val bx = verts[base + 3]; val by = verts[base + 4]; val bz = verts[base + 5]
            val cx = verts[base + 6]; val cy = verts[base + 7]; val cz = verts[base + 8]
            val ux = bx - ax; val uy = by - ay; val uz = bz - az
            val vx = cx - ax; val vy = cy - ay; val vz = cz - az
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            nx /= len; ny /= len; nz /= len
            for (k in 0 until 3) {
                val p = base + k * 3
                out[o++] = verts[p]; out[o++] = verts[p + 1]; out[o++] = verts[p + 2]
                out[o++] = nx; out[o++] = ny; out[o++] = nz
            }
            t++
        }
        val buf = ByteBuffer.allocateDirect(out.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(out); position(0) }
        return buf to triCount * 3
    }

    private fun buildProgram() {
        val vs = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                vNormal = mat3(uModel) * aNormal;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform vec3 uColor;
            uniform float uEmissive;
            varying vec3 vNormal;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 l = normalize(vec3(0.3, 0.9, 0.4));
                float d = abs(dot(n, l));
                float shade = 0.86 + 0.14 * d;     // mostly flat so white reads white
                vec3 col = mix(uColor * shade, uColor, uEmissive);
                gl_FragColor = vec4(col, 1.0);
            }
        """.trimIndent()
        val v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also {
            GLES20.glShaderSource(it, vs); GLES20.glCompileShader(it)
        }
        val f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also {
            GLES20.glShaderSource(it, fs); GLES20.glCompileShader(it)
        }
        prog = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, v); GLES20.glAttachShader(it, f); GLES20.glLinkProgram(it)
        }
        aPos = GLES20.glGetAttribLocation(prog, "aPos")
        aNrm = GLES20.glGetAttribLocation(prog, "aNormal")
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP")
        uModel = GLES20.glGetUniformLocation(prog, "uModel")
        uColor = GLES20.glGetUniformLocation(prog, "uColor")
        uEmis = GLES20.glGetUniformLocation(prog, "uEmissive")
    }
}
