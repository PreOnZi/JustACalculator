package com.fictioncutshort.justacalculator.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// CalculatorCityView
//
// Controls:
//   Single joystick (bottom-center)
//     X = rotate camera yaw
//     Y = move forward / backward (push up = forward / pull down = back)
//   No player character shown — camera floats freely through the city
// ─────────────────────────────────────────────────────────────────────────────

private const val BW_V = 85f    // building half-width X — must match renderer
private const val BD_V = 72f    // building half-depth Z — must match renderer

// Portrait constants
private const val PLAYER_START_X = -200f
private const val PLAYER_START_Z =  150f
private const val LAVA_FRONT_Z   = -480f // just past north building edge → lava zone

// Landscape constants
private const val PLAYER_START_X_L = 450f
private const val PLAYER_START_Z_L = 300f
private const val LAVA_WEST_X_L    = 80f    // west of this → lava in landscape
private const val LAVA_NORTH_Z_L   = 275f
private const val LAVA_SOUTH_Z_L   = 580f
private const val L_WALL_E         = 115f   // east face of landscape wall
private const val L_GAP_N          = 265f   // gap north edge (a little slack)
private const val L_GAP_S          = 335f   // gap south edge

private const val DEFAULT_CAM_PITCH = 28f
private const val CAM_DIST         = 165f
private const val CAM_H_BASE       = 8f
private const val CAM_EYE_H        = 55f   // first-person eye height (above ground)

// Portrait collision data
private val BLDG_FP = listOf(
    floatArrayOf(-300f, -400f), floatArrayOf(-100f, -400f), floatArrayOf(100f, -400f),
    floatArrayOf(-300f, -200f), floatArrayOf(-100f, -200f), floatArrayOf(100f, -200f),
    floatArrayOf(-300f,    0f), floatArrayOf(-100f,    0f), floatArrayOf(100f,    0f),
    floatArrayOf(-300f,  200f), floatArrayOf(-100f,  200f), floatArrayOf(100f,  200f),
    floatArrayOf(-300f,  400f), floatArrayOf(-100f,  400f), floatArrayOf(100f,  400f),
    floatArrayOf( 300f, -400f), floatArrayOf( 300f, -200f), floatArrayOf( 300f,    0f),
    floatArrayOf( 300f,  200f), floatArrayOf( 300f,  400f),
)
// Debris collision zones [cx, cz, half-width-x, half-depth-z]
private val DEBRIS_FP = listOf(
    floatArrayOf( 300f, -300f, 175f, 26f),
    floatArrayOf( 300f, -100f, 175f, 26f),
    floatArrayOf( 300f,  100f, 175f, 26f),
    floatArrayOf( 300f,  300f, 175f, 26f),
    floatArrayOf(  0f,   520f, 430f, 46f),
)

// Landscape collision data (buildings shifted +500 in X)
private val BLDG_FP_L = listOf(
    floatArrayOf(200f, -400f), floatArrayOf(400f, -400f), floatArrayOf(600f, -400f),
    floatArrayOf(200f, -200f), floatArrayOf(400f, -200f), floatArrayOf(600f, -200f),
    floatArrayOf(200f,    0f), floatArrayOf(400f,    0f), floatArrayOf(600f,    0f),
    floatArrayOf(200f,  200f), floatArrayOf(400f,  200f), floatArrayOf(600f,  200f),
    floatArrayOf(200f,  400f), floatArrayOf(400f,  400f), floatArrayOf(600f,  400f),
    floatArrayOf(800f, -400f), floatArrayOf(800f, -200f), floatArrayOf(800f,    0f),
    floatArrayOf(800f,  200f), floatArrayOf(800f,  400f),
)
private val DEBRIS_FP_L = listOf(
    floatArrayOf(800f, -300f, 175f, 26f),
    floatArrayOf(800f, -100f, 175f, 26f),
    floatArrayOf(800f,  100f, 175f, 26f),
    floatArrayOf(800f,  300f, 175f, 26f),
    floatArrayOf(500f,  520f, 430f, 46f),
)

@Composable
fun CalculatorCityView(modifier: Modifier = Modifier) {

    val renderer = remember { CityGLRenderer() }
    val context  = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Intro animation
    val intro = remember { Animatable(0f) }
    var introDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        intro.animateTo(1f, tween(6000, easing = CubicBezierEasing(0.22f, 0f, 0.08f, 1f)))
        renderer.buildingHeightScale = 2f
        renderer.needsRebuild = true
        introDone = true
    }

    // Camera / player state (camera IS the player — first-person)
    val startX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
    val startZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
    var pX     by remember { mutableStateOf(startX) }
    var pZ     by remember { mutableStateOf(startZ) }
    var camYaw by remember { mutableStateOf(0f) }

    // Single joystick input
    var joyX by remember { mutableStateOf(0f) }
    var joyY by remember { mutableStateOf(0f) }

    // Lava respawn state
    var inLava     by remember { mutableStateOf(false) }
    var flashAlpha by remember { mutableStateOf(0f) }

    var lavaShift  by remember { mutableStateOf(0f) }
    var aerialBlend by remember { mutableStateOf(1f) }

    // Fade aerial look → first-person over 2 s after intro ends
    LaunchedEffect(introDone) {
        if (introDone) {
            repeat(120) {
                delay(16)
                aerialBlend = 1f - (it + 1f) / 120f
                renderer.aerialBlend = aerialBlend
            }
        }
    }

    // ── Main game loop ────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            lavaShift = (lavaShift + 0.003f) % 1f
            renderer.lavaShift = lavaShift
            renderer.radAngle  = (renderer.radAngle + 3f) % 360f   // 3°/frame → 2s/revolution

            if (introDone) {
                renderer.fov = 82f

                // Yaw from joystick X; no pitch control (camera stays level)
                camYaw += joyX * 3.0f
                val cr = Math.toRadians(camYaw.toDouble())

                // Forward/backward from joystick Y (push up = forward)
                val jFwd = -joyY
                val spd = 2.2f
                val dx = (sin(cr) * jFwd * spd).toFloat()
                val dz = (-cos(cr) * jFwd * spd).toFloat()

                val xBoundsMin = if (isLandscape) -500f else -370f
                val xBoundsMax = if (isLandscape)  920f else  390f
                val nx = (pX + dx).coerceIn(xBoundsMin, xBoundsMax)
                val nz = (pZ + dz).coerceIn(-600f, 530f)

                // Collision — slightly wider margin for first-person
                fun blocked(tx: Float, tz: Float): Boolean {
                    val bfp = if (isLandscape) BLDG_FP_L else BLDG_FP
                    val dfp = if (isLandscape) DEBRIS_FP_L else DEBRIS_FP
                    for (fp in bfp) {
                        if (tx > fp[0]-BW_V-14f && tx < fp[0]+BW_V+14f &&
                            tz > fp[1]-BD_V-14f && tz < fp[1]+BD_V+14f) return true
                    }
                    for (dz2 in dfp) {
                        if (tx > dz2[0]-dz2[2] && tx < dz2[0]+dz2[2] &&
                            tz > dz2[1]-dz2[3] && tz < dz2[1]+dz2[3]) return true
                    }
                    // Landscape: wall at X=95..115 blocks except at gap Z=265..335
                    if (isLandscape && tx < L_WALL_E && !(tz > L_GAP_N && tz < L_GAP_S)) return true
                    return false
                }
                if (!blocked(nx, nz)) { pX = nx; pZ = nz }
                else {
                    if (!blocked(nx, pZ)) pX = nx
                    if (!blocked(pX, nz)) pZ = nz
                }

                // ── Lava boundary — vibrate + flash + teleport ────────────────
                val inLavaNow = if (isLandscape)
                    pX < LAVA_WEST_X_L && pZ > LAVA_NORTH_Z_L && pZ < LAVA_SOUTH_Z_L
                else
                    pZ < LAVA_FRONT_Z

                if (inLavaNow && !inLava) {
                    inLava = true
                    // Push back out of lava
                    if (isLandscape) pX = LAVA_WEST_X_L + 2f else pZ = LAVA_FRONT_Z + 2f
                    try {
                        @Suppress("DEPRECATION")
                        val vibrator: Vibrator =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                                        as VibratorManager).defaultVibrator
                            } else {
                                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(
                                VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(180)
                        }
                    } catch (_: Exception) { }
                    // Screen flash sequence
                    flashAlpha = 1f;  delay(90)
                    flashAlpha = 0.3f; delay(70)
                    flashAlpha = 1f;  delay(90)
                    flashAlpha = 0f
                    // Teleport to start
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                    inLava = false
                }

                // ── Camera: blend from intro orbit → first-person ─────────────
                // fp goes 0 → 1 as aerialBlend goes 1 → 0
                val fp = (1f - aerialBlend).coerceIn(0f, 1f)

                // Landing position (end of intro, camYaw=0, DEFAULT_CAM_PITCH)
                val cp0  = Math.toRadians(DEFAULT_CAM_PITCH.toDouble())
                val pStartX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                val pStartZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                val landX = pStartX                                           // sin(0)=0
                val landY = 32f + CAM_H_BASE + sin(cp0).toFloat() * CAM_DIST // ~117
                val landZ = pStartZ + cos(cp0).toFloat() * CAM_DIST

                renderer.camX = lerp(landX, pX, fp)
                renderer.camY = lerp(landY, CAM_EYE_H, fp)
                renderer.camZ = lerp(landZ, pZ, fp)

                renderer.aerialMode = aerialBlend > 0.01f

                if (renderer.aerialMode) {
                    // Keep aerial camera looking ahead during blend
                    renderer.introLookZ = pZ - 200f
                    renderer.useLookAt  = false
                } else {
                    // Pure first-person
                    renderer.useLookAt = true
                    renderer.lookAtX   = pX + sin(cr).toFloat()
                    renderer.lookAtY   = CAM_EYE_H
                    renderer.lookAtZ   = pZ - cos(cr).toFloat()
                }

                renderer.playerX    = pX; renderer.playerZ = pZ
                renderer.showPlayer = false

            } else {
                val t  = intro.value
                val t2 = ((t - 0.45f) / 0.55f).coerceIn(0f, 1f)

                if (isLandscape) {
                    // Landscape aerial: camera above, showing buildings-right / lava-left layout
                    val cp0 = Math.toRadians(DEFAULT_CAM_PITCH.toDouble())
                    val landEyeX = PLAYER_START_X_L.toDouble()
                    val landEyeZ = (PLAYER_START_Z_L + cos(cp0) * CAM_DIST).toFloat()
                    val landEyeY = (32f + CAM_H_BASE + sin(cp0) * CAM_DIST).toFloat()
                    if (t < 0.45f) {
                        renderer.aerialMode = true
                        renderer.fov        = 86f
                        renderer.camX       = 150f; renderer.camY = 1100f; renderer.camZ = 50f
                        renderer.camYaw     = 0f;   renderer.camPitch = -90f
                        renderer.introLookZ = 50f
                    } else {
                        val e2 = easeInOut(t2)
                        renderer.aerialMode = true
                        renderer.fov        = lerp(86f, 82f, e2)
                        renderer.camX       = lerp(150f, landEyeX.toFloat(), e2)
                        renderer.camY       = lerp(1100f, landEyeY, e2)
                        renderer.camZ       = lerp(50f, landEyeZ, e2)
                        renderer.camYaw     = 0f
                        renderer.camPitch   = lerp(-90f, -8f, e2)
                        renderer.introLookZ = lerp(50f, PLAYER_START_Z_L - 180f * e2, e2)
                    }
                } else {
                    // Portrait aerial
                    if (t < 0.45f) {
                        renderer.aerialMode  = true
                        renderer.fov         = 86f
                        renderer.camX        = 0f;   renderer.camY = 1300f; renderer.camZ = -420f
                        renderer.camYaw      = 0f;   renderer.camPitch = -90f
                        renderer.introLookZ  = -420f
                    } else {
                        val e2 = easeInOut(t2)
                        val cp0 = Math.toRadians(DEFAULT_CAM_PITCH.toDouble())
                        val landEyeX = PLAYER_START_X - sin(0.0) * cos(cp0).toFloat() * CAM_DIST
                        val landEyeZ = (PLAYER_START_Z + cos(0.0) * cos(cp0) * CAM_DIST).toFloat()
                        val landEyeY = (32f + CAM_H_BASE + sin(cp0) * CAM_DIST).toFloat()
                        renderer.aerialMode  = true
                        renderer.fov         = lerp(86f, 82f, e2)
                        renderer.camX        = lerp(0f, landEyeX.toFloat(), e2)
                        renderer.camY        = lerp(1300f, landEyeY, e2)
                        renderer.camZ        = lerp(-420f, landEyeZ, e2)
                        renderer.camYaw      = 0f
                        renderer.camPitch    = lerp(-90f, -8f, e2)
                        renderer.introLookZ  = lerp(-420f, PLAYER_START_Z - 180f * e2, e2)
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        AndroidView(
            factory = { ctx ->
                renderer.isLandscape = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(8, 8, 8, 0, 24, 0)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (introDone) {
            val screenW   = configuration.screenWidthDp
            val joySize   = (screenW * 0.30f).dp.coerceIn(110.dp, 160.dp)

            // Landscape: joystick on right side, vertically centered
            // Portrait:  joystick at bottom center
            CityJoystick(
                joyDp    = joySize,
                modifier = if (isLandscape) Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                else Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
                onJoy    = { x, y -> joyX = x; joyY = y }
            )
        }

        // Lava flash overlay
        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFF2200).copy(alpha = flashAlpha))
            )
        }
    }
}


// ── Joystick (camera look + movement) ────────────────────────────────────────

@Composable
fun CityJoystick(modifier: Modifier = Modifier, joyDp: Dp = 114.dp, onJoy: (x: Float, y: Float) -> Unit) {
    var sx by remember { mutableStateOf(0f) }
    var sy by remember { mutableStateOf(0f) }
    Box(modifier = modifier.size(joyDp)) {
        AndroidView(
            factory = { ctx ->
                object : android.view.View(ctx) {
                    override fun onTouchEvent(e: MotionEvent): Boolean {
                        val cx = width / 2f; val cy = height / 2f
                        when (e.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                sx = ((e.x - cx) / cx).coerceIn(-1f, 1f)
                                sy = ((e.y - cy) / cy).coerceIn(-1f, 1f)
                                onJoy(sx, sy)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                sx = 0f; sy = 0f; onJoy(0f, 0f)
                            }
                        }
                        return true
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val or = size.minDimension / 2f
            drawCircle(Color.White.copy(alpha = 0.10f), or, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.30f), or, Offset(cx, cy), style = Stroke(1.8f))
            val kx = cx + sx * or * 0.52f; val ky = cy + sy * or * 0.52f
            drawCircle(Color.White.copy(alpha = 0.50f), or * 0.36f, Offset(kx, ky))
            drawCircle(Color.White.copy(alpha = 0.75f), or * 0.36f, Offset(kx, ky), style = Stroke(1.4f))
        }
    }
}

private fun easeInOut(t: Float) = if (t < 0.5f) 2 * t * t else 1f - (-2 * t + 2f).let { it * it } / 2f
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
