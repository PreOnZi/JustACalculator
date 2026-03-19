package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// CALCULATOR CITY  —  first-person perspective
//
// World axes:  X = east/west,  Y = up,  Z = north(-) / south(+)
// Camera faces -Z when yaw=0 (looking north).
//
// Number buttons 1–9 are tall concrete button-shaped buildings arranged in
// the original calculator grid:
//   row 0 (north):  7  8  9   (z = -CELL)
//   row 1 (mid):    4  5  6   (z =  0)
//   row 2 (south):  1  2  3   (z = +CELL)
//
// Destroyed keys (0, ., operators) are rubble craters outside the 3×3 block.
//
// Southern border: lava channel (melted display) — glowing orange/red pool.
// Northern border: oasis.
// Western border:  tall yellow ruined wall (the yellow strip from schematics).
// Eastern/outer borders: shorter ruin fragments.
//
// Intro:  camera starts far south, at street level, looking north.
//         It glides forward through the gap between rows 3 and 2 and into
//         the city's central street, settling at player position.
// ─────────────────────────────────────────────────────────────────────────────

// ── Palette ───────────────────────────────────────────────────────────────────
// Sky — dusk purple/amber instead of near-black
private val SKY_TOP         = Color(0xFF1A1428)
private val SKY_MID         = Color(0xFF2C1E1A)
private val SKY_HORIZON     = Color(0xFF4A2A0A)
private val SKY_BAND        = Color(0xFF3A1E08)   // thin amber band at horizon

// Ground
private val GROUND_DARK     = Color(0xFF0C0B08)
private val GROUND_MID      = Color(0xFF161410)
private val DUST_LINE       = Color(0xFF252018)

// Buildings (button-style: no windows, just concrete)
private val BLDG_FRONT      = Color(0xFF2A2620)   // south/north face (lit side)
private val BLDG_DARK       = Color(0xFF181510)   // side faces (unlit)
private val BLDG_TOP        = Color(0xFF222018)
private val BLDG_STRIPE_L   = Color(0xFF30291F)   // horizontal panel line light
private val BLDG_STRIPE_D   = Color(0xFF141210)   // horizontal panel line dark
private val CRACK_C         = Color(0xFF0C0A06)

// Ruin walls
private val RUIN_MAIN       = Color(0xFF3A3010)   // yellow-tinted ruin (west wall)
private val RUIN_DARK       = Color(0xFF28220A)
private val RUIN_SIDE       = Color(0xFF201A08)
private val RUIN_OTHER      = Color(0xFF201E18)   // east/north border ruins
private val RUIN_OTHER_D    = Color(0xFF141210)

// Rubble
private val RUBBLE_A        = Color(0xFF1E1A12)
private val RUBBLE_B        = Color(0xFF141210)
private val CRATER_C        = Color(0xFF0A0906)

// Lava (melted display strip)
private val LAVA_ROCK       = Color(0xFF1A0A04)
private val LAVA_CRUST      = Color(0xFF2A1208)
private val LAVA_HOT        = Color(0xFFCC3300)
private val LAVA_BRIGHT     = Color(0xFFFF6600)
private val LAVA_GLOW       = Color(0xFFFF9900)
private val LAVA_WHITE      = Color(0xFFFFCC88)

// Oasis
private val OASIS_GRASS     = Color(0xFF263D18)
private val OASIS_BRIGHT    = Color(0xFF3A5E26)
private val OASIS_WATER_D   = Color(0xFF081C2E)
private val OASIS_WATER_L   = Color(0xFF153A52)

// Player
private val BALL_C          = Color(0xFFD4C090)
private val BALL_SHINE      = Color(0xFFFFEECC)

// ── World geometry ────────────────────────────────────────────────────────────
private const val CELL      = 200f   // grid spacing
private const val BLDG_W    = 75f    // building half-width (X)
private const val BLDG_D    = 75f    // building half-depth (Z)
private const val BLDG_H    = 300f   // base height (varies per building)
private const val EYE_H     = 32f    // player eye level
private const val BALL_R    = 11f
private const val FOV_C     = 480f   // perspective constant

// Boundary limits
private val WALL_W_X    = -CELL * 1.55f   // west ruin wall X position
private val WALL_E_X    =  CELL * 1.55f   // east ruin wall X position
private val LAVA_Z      =  CELL * 2.5f    // lava strip Z
private val LAVA_W      =  CELL * 3.8f
private val OASIS_Z     = -CELL * 2.6f

// ── Scene data ────────────────────────────────────────────────────────────────

data class CityBuilding(
    val label: String,
    val cx: Float,
    val cz: Float,
    val h: Float = BLDG_H
)

data class CityRubble(val cx: Float, val cz: Float, val r: Float, val seed: Long)

private val BUILDINGS = listOf(
    CityBuilding("7", -CELL, -CELL, BLDG_H * 1.1f),
    CityBuilding("8",  0f,   -CELL, BLDG_H * 1.35f),
    CityBuilding("9",  CELL, -CELL, BLDG_H * 0.92f),
    CityBuilding("4", -CELL,  0f,   BLDG_H * 1.02f),
    CityBuilding("5",  0f,    0f,   BLDG_H * 1.28f),
    CityBuilding("6",  CELL,  0f,   BLDG_H * 0.97f),
    CityBuilding("1", -CELL,  CELL, BLDG_H * 1.06f),
    CityBuilding("2",  0f,    CELL, BLDG_H * 1.18f),
    CityBuilding("3",  CELL,  CELL, BLDG_H * 0.88f),
)

private val RUBBLE = listOf(
    CityRubble(-CELL,  CELL * 2f,  68f, 1L),
    CityRubble( 0f,    CELL * 2f,  60f, 2L),
    CityRubble( CELL,  CELL * 2f,  72f, 3L),
    CityRubble( CELL * 2f, -CELL,  62f, 4L),
    CityRubble( CELL * 2f,  0f,    68f, 5L),
    CityRubble( CELL * 2f,  CELL,  58f, 6L),
    CityRubble(-CELL * 2f,  0f,    54f, 7L),
    CityRubble(-CELL * 2f,  CELL,  64f, 8L),
)

// ── Camera math ───────────────────────────────────────────────────────────────

data class FPCam(
    val x: Float, val y: Float, val z: Float,
    val yaw: Float,    // degrees; 0 = looking toward -Z (north)
    val pitch: Float   // degrees; negative = looking slightly down
)

private fun worldToCam(wx: Float, wy: Float, wz: Float, cam: FPCam): Triple<Float, Float, Float> {
    val tx = wx - cam.x;  val ty = wy - cam.y;  val tz = wz - cam.z
    val yr = Math.toRadians(-cam.yaw.toDouble())
    val cosY = cos(yr).toFloat();  val sinY = sin(yr).toFloat()
    val rx = tx * cosY - tz * sinY
    val rz = tx * sinY + tz * cosY
    val pr = Math.toRadians(-cam.pitch.toDouble())
    val cosP = cos(pr).toFloat();  val sinP = sin(pr).toFloat()
    val ry  = ty * cosP - rz * sinP
    val rz2 = ty * sinP + rz * cosP
    return Triple(rx, ry, rz2)
}

// Returns null if behind camera (cz >= -0.5)
private fun projectW(wx: Float, wy: Float, wz: Float, cam: FPCam, W: Float, H: Float): Offset? {
    val (cx, cy, cz) = worldToCam(wx, wy, wz, cam)
    if (cz >= -0.5f) return null
    val s = FOV_C / (-cz)
    return Offset(W * 0.5f + cx * s, H * 0.5f - cy * s)
}

// Camera-space depth of a world point (more negative = further away = draw first)
private fun camDepth(wx: Float, wy: Float, wz: Float, cam: FPCam): Float =
    worldToCam(wx, wy, wz, cam).third

// ── Composable ────────────────────────────────────────────────────────────────

@Composable
fun CalculatorCityView(modifier: Modifier = Modifier) {

    // ── Intro ─────────────────────────────────────────────────────────────────
    // Camera starts far south on the main street axis (x=0, z=+CELL*2.8),
    // at eye level, looking north (yaw=0, pitch=-3).
    // It glides straight north through the gap between rows 1–3, decelerating
    // to a stop at the player's starting position z=+CELL*1.55.
    val intro = remember { Animatable(0f) }
    var introDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(400)
        intro.animateTo(1f, tween(4000, easing = CubicBezierEasing(0.22f, 0f, 0.06f, 1f)))
        introDone = true
    }

    // ── Player ────────────────────────────────────────────────────────────────
    var pX   by remember { mutableStateOf(0f) }
    var pZ   by remember { mutableStateOf(CELL * 1.55f) }
    var pYaw by remember { mutableStateOf(0f) }
    var jFwd  by remember { mutableStateOf(0f) }
    var jTurn by remember { mutableStateOf(0f) }

    // ── Lava animation ────────────────────────────────────────────────────────
    val lavaTr = rememberInfiniteTransition(label = "lava")
    val lavaPulse by lavaTr.animateFloat(0.6f, 1f,
        InfiniteRepeatableSpec(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lp")
    val lavaShift by lavaTr.animateFloat(0f, 1f,
        InfiniteRepeatableSpec(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "ls")

    // ── Game loop ─────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            if (introDone) {
                pYaw += jTurn * 1.4f
                val yr = Math.toRadians(pYaw.toDouble())
                val dx = (sin(yr) * jFwd * 1.6f).toFloat()
                val dz = -(cos(yr) * jFwd * 1.6f).toFloat()
                val nx = (pX + dx).coerceIn(WALL_W_X + 20f, WALL_E_X - 20f)
                val nz = (pZ + dz).coerceIn(-CELL * 2.3f, LAVA_Z - 40f)
                var blocked = false
                for (b in BUILDINGS) {
                    if (nx > b.cx - BLDG_W - BALL_R && nx < b.cx + BLDG_W + BALL_R &&
                        nz > b.cz - BLDG_D - BALL_R && nz < b.cz + BLDG_D + BALL_R) {
                        blocked = true; break
                    }
                }
                if (!blocked) { pX = nx; pZ = nz }
            }
        }
    }

    // ── Camera build ──────────────────────────────────────────────────────────
    // Intro: pure forward glide along X=0 street, from z=CELL*2.8 to z=CELL*1.55
    val t  = easeOut(intro.value)
    val introZ = lerp(CELL * 2.8f, CELL * 1.55f, t)

    val cam = if (!introDone)
        FPCam(0f, EYE_H, introZ, 0f, -3f)   // eye level, looking slightly down, no tilt
    else
        FPCam(pX, EYE_H, pZ, pYaw, -4f)

    // ── Render ────────────────────────────────────────────────────────────────
    Box(modifier = modifier.fillMaxSize()) {

        // Sky gradient — dusk purple/amber
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(SKY_TOP, SKY_MID, SKY_BAND, SKY_HORIZON))
        ))

        Canvas(modifier = Modifier.fillMaxSize()) {
            val W = size.width;  val H = size.height

            fun pw(wx: Float, wy: Float, wz: Float): Offset? =
                projectW(wx, wy, wz, cam, W, H)

            // ── Horizon / ground ─────────────────────────────────────────────
            val horizY = pw(cam.x, 0f, cam.z - 8000f)?.y
                ?.coerceIn(H * 0.15f, H * 0.75f) ?: (H * 0.5f)

            val groundPath = Path().apply {
                moveTo(0f, horizY); lineTo(W, horizY); lineTo(W, H); lineTo(0f, H); close()
            }
            drawPath(groundPath, Brush.verticalGradient(
                listOf(GROUND_DARK, GROUND_MID), startY = horizY, endY = H
            ))

            // Ground grid (perspective vanishing lines)
            for (i in -3..18) {
                val wz = cam.z + i * 55f - ((cam.z / 55f).toInt() * 55f)
                val a = pw(-1000f, 1f, wz);  val b = pw(1000f, 1f, wz)
                if (a != null && b != null && a.y >= horizY - 2f)
                    drawLine(DUST_LINE.copy(alpha = 0.22f), a, b, 0.7f)
            }
            for (i in -8..8) {
                val la = pw(i * 75f, 1f, cam.z + 5f)
                val lb = pw(i * 75f, 1f, cam.z + 1500f)
                if (la != null && lb != null)
                    drawLine(DUST_LINE.copy(alpha = 0.12f), la, lb, 0.7f)
            }

            // ── Oasis ────────────────────────────────────────────────────────
            drawOasis(cam, W, H, ::pw)

            // ── Border ruins: east + north fragments ────────────────────────
            drawBorderRuins(cam, ::pw)

            // ── West yellow ruin wall ────────────────────────────────────────
            drawWestWall(cam, ::pw)

            // ── Rubble craters ───────────────────────────────────────────────
            val sortedRubble = RUBBLE.sortedByDescending { r ->
                camDepth(r.cx, 0f, r.cz, cam)
            }
            for (r in sortedRubble) drawCityRubble(r, ::pw)

            // ── Buildings — occlusion via closest-first + clip accumulation ──────
            // Sort closest face first. Each face is drawn, then its screen polygon
            // is subtracted from the clip region so farther faces cannot paint over it.
            val allFaces = mutableListOf<CityFace>()
            for (b in BUILDINGS) collectBuildingFaces(b, cam, allFaces, ::pw)
            allFaces.sortBy { it.depth }   // most negative = furthest; least negative = closest

            // We accumulate an "occupied" region using save/clipPath(Difference).
            // Each face: draw it, then clip-out its shape from future draws.
            val occupiedPath = Path()
            for (f in allFaces) {
                val pts = f.verts.filterNotNull()
                if (pts.size < 3) continue
                val facePath = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawContext.canvas.save()
                // Clip to the *inverse* of already-occupied area: only draw where not yet covered
                if (!occupiedPath.isEmpty) {
                    val allowed = Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                        op(this, occupiedPath, PathOperation.Difference)
                    }
                    drawContext.canvas.clipPath(allowed)
                }
                drawCityFace(f, cam, ::pw)
                drawContext.canvas.restore()
                // Mark this face's area as occupied
                occupiedPath.addPath(facePath)
            }

            // ── Lava channel ─────────────────────────────────────────────────
            drawLava(cam, ::pw, lavaPulse, lavaShift)

            // ── Player dot (aerial only during intro) ────────────────────────
            if (!introDone) {
                val bs = pw(pX, BALL_R, pZ)
                if (bs != null) {
                    val sh = pw(pX, 0.5f, pZ)
                    if (sh != null) drawCircle(Color.Black.copy(alpha=0.3f), BALL_R*0.9f, sh)
                    drawCircle(BALL_C, BALL_R, bs)
                    drawCircle(BALL_SHINE.copy(alpha=0.45f), BALL_R*0.3f,
                        Offset(bs.x - BALL_R*0.22f, bs.y - BALL_R*0.22f))
                }
            }
        }

        // ── HUD ───────────────────────────────────────────────────────────────
        if (introDone) {
            CityJoystick(
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 44.dp),
                onJoy = { x, y -> jTurn = x; jFwd = -y }
            )
            Text("▲  OASIS",
                color = OASIS_BRIGHT.copy(alpha = 0.6f),
                fontSize = 11.sp, letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp))
        }

        // ── Intro title ───────────────────────────────────────────────────────
        if (!introDone) {
            val raw = intro.value
            val alpha = (raw * 3.5f).coerceIn(0f, 1f) *
                    (1f - ((raw - 0.6f) * 4f).coerceIn(0f, 1f))
            if (alpha > 0.02f) {
                Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CALCULATOR CITY",
                        color = Color(0xFFB89010).copy(alpha = alpha),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, letterSpacing = 5.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("population: 0",
                        color = Color(0xFF6A5008).copy(alpha = alpha * 0.7f),
                        fontSize = 11.sp, textAlign = TextAlign.Center, letterSpacing = 3.sp)
                }
            }
        }
    }
}

// ── Face collection for global painter's sort ─────────────────────────────────

data class CityFace(
    val verts: List<Offset?>,          // projected screen verts (some may be null)
    val color: Color,
    val depth: Float,                  // camera-space Z of face centre (more negative = farther)
    val isZFace: Boolean = false,
    val faceZWorld: Float = 0f,
    val building: CityBuilding? = null // non-null for Z faces that need detail pass
)

private fun collectBuildingFaces(
    b: CityBuilding, cam: FPCam,
    out: MutableList<CityFace>,
    pw: (Float, Float, Float) -> Offset?
) {
    val x0 = b.cx - BLDG_W;  val x1 = b.cx + BLDG_W
    val z0 = b.cz - BLDG_D;  val z1 = b.cz + BLDG_D

    // Corners: bot 0-3, top 4-7
    // 0=x0z0 1=x1z0 2=x0z1 3=x1z1  4=x0z0top 5=x1z0top 6=x0z1top 7=x1z1top
    val p = Array(8) { i ->
        val xi = if (i % 2 == 0) x0 else x1
        val yi = if (i < 4) 0f else b.h
        val zi = when (i) { 0, 1, 4, 5 -> z0; else -> z1 }
        pw(xi, yi, zi)
    }

    val seeSouth = cam.z >= z1   // camera is south of (or on) the south face plane
    val seeNorth = cam.z <= z0   // camera is north of (or on) the north face plane
    val seeEast  = cam.x >= x1   // camera is east of the east face plane
    val seeWest  = cam.x <= x0   // camera is west of the west face plane

    // Fallback: if camera is somehow inside the building box (shouldn't happen
    // with collision, but guards against edge cases) show the nearest face per axis.
    val insideX = cam.x > x0 && cam.x < x1
    val insideZ = cam.z > z0 && cam.z < z1
    val inside  = insideX && insideZ

    val showSouth = seeSouth || (inside && cam.z > b.cz)
    val showNorth = seeNorth || (inside && cam.z < b.cz)
    val showEast  = seeEast  || (inside && cam.x > b.cx)
    val showWest  = seeWest  || (inside && cam.x < b.cx)

    // South face (z1)
    if (showSouth) {
        out += CityFace(listOf(p[2],p[3],p[7],p[6]), BLDG_FRONT,
            camDepth(b.cx, b.h * 0.5f, z1, cam), true, z1, b)
    }
    // North face (z0)
    if (showNorth) {
        out += CityFace(listOf(p[0],p[1],p[5],p[4]), BLDG_FRONT,
            camDepth(b.cx, b.h * 0.5f, z0, cam), true, z0, b)
    }
    // East face (x1)
    if (showEast) {
        out += CityFace(listOf(p[1],p[3],p[7],p[5]), BLDG_DARK,
            camDepth(x1, b.h * 0.5f, b.cz, cam))
    }
    // West face (x0)
    if (showWest) {
        out += CityFace(listOf(p[0],p[2],p[6],p[4]), BLDG_DARK,
            camDepth(x0, b.h * 0.5f, b.cz, cam))
    }
    // Top face — depth at top centre
    out += CityFace(listOf(p[4],p[5],p[7],p[6]), BLDG_TOP,
        camDepth(b.cx, b.h, b.cz, cam))
}

private fun DrawScope.drawCityFace(
    face: CityFace, cam: FPCam,
    pw: (Float, Float, Float) -> Offset?
) {
    val pts = face.verts.filterNotNull()
    if (pts.size < 3) return

    drawFacePoly(pts, face.color)

    // Horizontal panel stripes
    if (face.verts.size == 4) {
        val v = face.verts
        for (i in 1 until 6) {
            val f = i.toFloat() / 6f
            val v0 = v[0]; val v1 = v[1]; val v2 = v[3]; val v3 = v[2]
            if (v0 != null && v1 != null && v2 != null && v3 != null) {
                val lx = lerp(v0.x, v3.x, f); val ly = lerp(v0.y, v3.y, f)
                val rx = lerp(v1.x, v2.x, f); val ry = lerp(v1.y, v2.y, f)
                val col = if (i % 2 == 0) BLDG_STRIPE_L.copy(alpha = 0.3f)
                else            BLDG_STRIPE_D.copy(alpha = 0.25f)
                drawLine(col, Offset(lx, ly), Offset(rx, ry), 1.5f)
            }
        }
    }

    // Detail pass on Z faces
    if (face.isZFace) {
        val b   = face.building ?: return
        val fz  = face.faceZWorld

        // Entrance arch
        val ew = BLDG_W * 0.3f;  val eh = b.h * 0.16f
        val arch = listOf(
            pw(b.cx - ew, 0f,         fz),
            pw(b.cx + ew, 0f,         fz),
            pw(b.cx + ew, eh,         fz),
            pw(b.cx,      eh * 1.25f, fz),
            pw(b.cx - ew, eh,         fz)
        ).filterNotNull()
        if (arch.size >= 4) drawFacePoly(arch, Color(0xFF040302))

        // Number label
        val lpos = pw(b.cx, b.h * 0.62f, fz)
        if (lpos != null) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(180, 150, 130, 82)
                    textSize = 58f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                drawText(b.label, lpos.x, lpos.y + 20f, paint)
            }
        }

        // Cracks
        val cr = java.util.Random(b.label.hashCode().toLong() * 17L)
        repeat(5) {
            val cx  = b.cx + (cr.nextFloat() - 0.5f) * BLDG_W * 1.6f
            val cy1 = cr.nextFloat() * b.h * 0.82f
            val cx2 = cx + (cr.nextFloat() - 0.5f) * 50f
            val cy2 = cy1 + cr.nextFloat() * 65f + 8f
            val ca  = pw(cx, cy1, fz);  val cb = pw(cx2, cy2, fz)
            if (ca != null && cb != null)
                drawLine(CRACK_C.copy(alpha = 0.55f), ca, cb, 1f)
        }
    }

    // Outline
    drawFacePoly(pts, Color.Black.copy(alpha = 0.2f), style = Stroke(0.8f))
}

// ── Draw: west yellow ruin wall ───────────────────────────────────────────────

private data class WallSegment(val z0: Float, val z1: Float, val h: Float, val thick: Float)

private fun DrawScope.drawWestWall(cam: FPCam, pw: (Float, Float, Float) -> Offset?) {
    val wx = WALL_W_X
    val segments = listOf(
        WallSegment(-CELL * 2.4f,  -CELL * 1.6f,  BLDG_H * 0.85f, 22f),
        WallSegment(-CELL * 1.6f,  -CELL * 0.7f,  BLDG_H * 1.1f,  20f),
        WallSegment(-CELL * 0.7f,   CELL * 0.2f,  BLDG_H * 0.95f, 18f),
        WallSegment( CELL * 0.2f,   CELL * 1.1f,  BLDG_H * 1.05f, 21f),
        WallSegment( CELL * 1.1f,   CELL * 2.2f,  BLDG_H * 0.75f, 19f),
    )
    for ((z0, z1, h, thick) in segments) {
        // Front face (east-facing, visible when cam.x > wx)
        if (cam.x > wx) {
            val front = listOf(
                pw(wx + thick, 0f, z0), pw(wx + thick, 0f, z1),
                pw(wx + thick, h,  z1), pw(wx + thick, h,  z0)
            ).filterNotNull()
            if (front.size >= 3) {
                drawFacePoly(front, RUIN_MAIN)
                // Vertical stripe texture — crumbling concrete
                val r = java.util.Random((z0 * 100).toLong())
                val strips = 5
                for (si in 1 until strips) {
                    val sf = si.toFloat() / strips
                    val v0 = pw(wx + thick, 0f, lerp(z0, z1, sf))
                    val v1 = pw(wx + thick, h,  lerp(z0, z1, sf))
                    if (v0 != null && v1 != null)
                        drawLine(RUIN_DARK.copy(alpha = 0.4f), v0, v1, 1.2f)
                }
            }
            // Top edge — jagged
            val top = listOf(
                pw(wx, h, z0), pw(wx + thick, h, z0),
                pw(wx + thick, h, z1), pw(wx, h, z1)
            ).filterNotNull()
            if (top.size >= 3) drawFacePoly(top, RUIN_DARK)

            // Outline
            if (front.size >= 3) drawFacePoly(front, Color.Black.copy(alpha=0.18f), style=Stroke(0.7f))
        }
        // Thin side slab (depth face, north/south ends)
        val sideN = listOf(pw(wx, 0f, z0), pw(wx+thick, 0f, z0), pw(wx+thick, h, z0), pw(wx, h, z0)).filterNotNull()
        if (sideN.size >= 3) drawFacePoly(sideN, RUIN_SIDE)
    }
}

// ── Draw: east + north border ruin fragments ──────────────────────────────────

private fun DrawScope.drawBorderRuins(cam: FPCam, pw: (Float, Float, Float) -> Offset?) {
    // East wall fragments
    val ex = WALL_E_X
    val eastSegs = listOf(
        Triple(-CELL * 2.0f, -CELL * 1.2f, BLDG_H * 0.55f),
        Triple(-CELL * 0.5f,  CELL * 0.4f, BLDG_H * 0.7f),
        Triple( CELL * 0.8f,  CELL * 1.8f, BLDG_H * 0.5f),
    )
    for ((z0, z1, h) in eastSegs) {
        if (cam.x < ex) {
            val front = listOf(
                pw(ex - 18f, 0f, z0), pw(ex - 18f, 0f, z1),
                pw(ex - 18f, h,  z1), pw(ex - 18f, h,  z0)
            ).filterNotNull()
            if (front.size >= 3) {
                drawFacePoly(front, RUIN_OTHER)
                drawFacePoly(front, Color.Black.copy(alpha=0.15f), style=Stroke(0.7f))
            }
        }
    }

    // North fragments (beyond oasis, gives depth)
    val nz = -CELL * 2.1f
    val northSegs = listOf(-CELL*1.4f to BLDG_H*0.4f, 0f to BLDG_H*0.5f, CELL*1.4f to BLDG_H*0.38f)
    for ((nx, h) in northSegs) {
        val front = listOf(
            pw(nx - 40f, 0f, nz), pw(nx + 40f, 0f, nz),
            pw(nx + 40f, h,  nz), pw(nx - 40f, h,  nz)
        ).filterNotNull()
        if (front.size >= 3) {
            drawFacePoly(front, RUIN_OTHER_D)
            drawFacePoly(front, Color.Black.copy(alpha=0.1f), style=Stroke(0.6f))
        }
    }
}

// ── Draw: rubble / craters ────────────────────────────────────────────────────

private fun DrawScope.drawCityRubble(r: CityRubble, pw: (Float, Float, Float) -> Offset?) {
    val rand = java.util.Random(r.seed)
    val craterPts = (0 until 10).mapNotNull { i ->
        val a = i * 2 * PI.toFloat() / 10f
        pw(r.cx + cos(a) * r.r * 0.82f, 0.8f, r.cz + sin(a) * r.r * 0.6f)
    }
    if (craterPts.size >= 3) {
        drawFacePoly(craterPts, CRATER_C)
        drawFacePoly(craterPts, DUST_LINE.copy(alpha = 0.3f), style = Stroke(1.2f))
    }
    repeat(7) {
        val a   = rand.nextFloat() * 2 * PI.toFloat()
        val d   = rand.nextFloat() * r.r * 0.75f
        val rx  = r.cx + cos(a) * d;  val rz = r.cz + sin(a) * d
        val sw  = 10f + rand.nextFloat() * 28f;  val sh = 5f + rand.nextFloat() * 22f
        val pts = listOf(
            pw(rx - sw*0.5f, 0f, rz - sw*0.3f), pw(rx + sw*0.5f, 0f, rz - sw*0.3f),
            pw(rx + sw*0.35f, sh, rz),          pw(rx - sw*0.35f, sh, rz)
        ).filterNotNull()
        if (pts.size >= 3) {
            drawFacePoly(pts, if (rand.nextBoolean()) RUBBLE_A else RUBBLE_B)
            drawFacePoly(pts, Color.Black.copy(alpha=0.25f), style=Stroke(0.6f))
        }
    }
}

// ── Draw: lava channel ────────────────────────────────────────────────────────

private fun DrawScope.drawLava(
    cam: FPCam,
    pw: (Float, Float, Float) -> Offset?,
    pulse: Float, shift: Float
) {
    val lz = LAVA_Z;  val lw = LAVA_W / 2

    // Hardened lava crust (outer frame)
    val crust = listOf(
        pw(-lw, 0f, lz - 45f), pw(lw, 0f, lz - 45f),
        pw(lw,  0f, lz + 55f), pw(-lw, 0f, lz + 55f)
    ).filterNotNull()
    if (crust.size >= 3) drawFacePoly(crust, LAVA_ROCK)

    // Inner molten surface
    val inner = listOf(
        pw(-lw * 0.88f, 1f, lz - 30f), pw(lw * 0.88f, 1f, lz - 30f),
        pw(lw * 0.88f,  1f, lz + 38f), pw(-lw * 0.88f, 1f, lz + 38f)
    ).filterNotNull()
    if (inner.size >= 3) drawFacePoly(inner, LAVA_HOT.copy(alpha = 0.7f + pulse * 0.3f))

    // Bright lava veins (animated horizontal lines)
    for (i in 0 until 6) {
        val frac = (i.toFloat() / 6f + shift) % 1f
        val vz   = lz - 28f + frac * 60f
        val vw   = lw * (0.5f + pulse * 0.35f)
        val vl   = pw(-vw, 2f, vz);  val vr = pw(vw, 2f, vz)
        if (vl != null && vr != null) {
            val alpha = (1f - abs(frac - 0.5f) * 2f) * pulse
            drawLine(LAVA_BRIGHT.copy(alpha = alpha * 0.8f), vl, vr, 2.5f)
            drawLine(LAVA_GLOW.copy(alpha = alpha * 0.4f),   vl, vr, 5f)
        }
    }

    // Bright pools / hotspots
    val hotspots = listOf(-lw*0.6f to lz - 10f, 0f to lz + 5f, lw*0.5f to lz - 18f)
    for ((hx, hz) in hotspots) {
        val hp = pw(hx, 2f, hz)
        if (hp != null) {
            drawCircle(LAVA_GLOW.copy(alpha = pulse * 0.5f),  22f, hp)
            drawCircle(LAVA_WHITE.copy(alpha = pulse * 0.3f), 10f, hp)
        }
    }

    // Crust rim wall (short vertical slab at north edge of lava — the display remnant)
    val rimH = 40f
    val rim = listOf(
        pw(-lw, 0f,  lz - 45f), pw(lw, 0f,  lz - 45f),
        pw(lw,  rimH, lz - 45f), pw(-lw, rimH, lz - 45f)
    ).filterNotNull()
    if (rim.size >= 3) {
        drawFacePoly(rim, LAVA_CRUST)
        drawFacePoly(rim, LAVA_HOT.copy(alpha = 0.12f * pulse), style = Stroke(1.5f))
    }
}

// ── Draw: oasis ───────────────────────────────────────────────────────────────

private fun DrawScope.drawOasis(
    cam: FPCam, W: Float, H: Float,
    pw: (Float, Float, Float) -> Offset?
) {
    val ow = CELL * 2.3f;  val oz1 = OASIS_Z - CELL * 0.9f;  val oz2 = OASIS_Z + CELL * 0.3f
    val ground = listOf(pw(-ow/2,0f,oz1), pw(ow/2,0f,oz1), pw(ow/2,0f,oz2), pw(-ow/2,0f,oz2)).filterNotNull()
    if (ground.size >= 3) drawFacePoly(ground, OASIS_GRASS)

    val pool = listOf(pw(-55f,1f,OASIS_Z-55f), pw(55f,1f,OASIS_Z-55f), pw(55f,1f,OASIS_Z-10f), pw(-55f,1f,OASIS_Z-10f)).filterNotNull()
    if (pool.size >= 3) {
        drawFacePoly(pool, OASIS_WATER_D)
        drawFacePoly(pool, OASIS_WATER_L.copy(alpha=0.4f), style=Stroke(1.5f))
    }

    for (tx in listOf(-82f, -28f, 30f, 78f)) {
        val tz = OASIS_Z - 70f
        val trunk = listOf(pw(tx-5f,0f,tz), pw(tx+5f,0f,tz), pw(tx+5f,30f,tz), pw(tx-5f,30f,tz)).filterNotNull()
        if (trunk.size >= 3) drawFacePoly(trunk, Color(0xFF161008))
        val b1=pw(tx-22f,24f,tz); val b2=pw(tx+22f,24f,tz); val tip=pw(tx,82f,tz)
        if (b1!=null && b2!=null && tip!=null) {
            drawFacePoly(listOf(b1,b2,tip), OASIS_BRIGHT)
            drawFacePoly(listOf(b1,b2,tip), OASIS_GRASS, style=Stroke(0.8f))
        }
    }
}

// ── Draw helper ───────────────────────────────────────────────────────────────

private fun DrawScope.drawFacePoly(pts: List<Offset>, color: Color, style: DrawStyle = Fill) {
    if (pts.size < 3) return
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, color, style = style)
}

// ── Joystick ──────────────────────────────────────────────────────────────────

@Composable
fun CityJoystick(modifier: Modifier = Modifier, onJoy: (x: Float, y: Float) -> Unit) {
    var sx by remember { mutableStateOf(0f) }
    var sy by remember { mutableStateOf(0f) }
    Box(modifier = modifier.size(100.dp).pointerInput(Unit) {
        detectDragGestures(
            onDragEnd    = { sx=0f; sy=0f; onJoy(0f,0f) },
            onDragCancel = { sx=0f; sy=0f; onJoy(0f,0f) }
        ) { _, drag ->
            sx = (sx + drag.x / 50f).coerceIn(-1f, 1f)
            sy = (sy + drag.y / 50f).coerceIn(-1f, 1f)
            onJoy(sx, sy)
        }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val cx=size.width/2f; val cy=size.height/2f; val or=size.minDimension/2f
            drawCircle(Color.White.copy(alpha=0.07f), or, Offset(cx,cy))
            drawCircle(Color.White.copy(alpha=0.2f),  or, Offset(cx,cy), style=Stroke(1.5f))
            drawCircle(Color.White.copy(alpha=0.4f),  or*0.36f, Offset(cx+sx*or*0.5f, cy+sy*or*0.5f))
            drawCircle(Color.White.copy(alpha=0.65f), or*0.36f, Offset(cx+sx*or*0.5f, cy+sy*or*0.5f), style=Stroke(1.2f))
        }
    }
}

// ── Easing / lerp ─────────────────────────────────────────────────────────────
private fun easeOut(t: Float)  = 1f - (1f - t) * (1f - t)
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t