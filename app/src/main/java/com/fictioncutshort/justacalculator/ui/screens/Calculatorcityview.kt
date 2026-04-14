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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

// ─────────────────────────────────────────────────────────────────────────────
// DOOR INTERACTION — entry order, riddle data, proximity
// ─────────────────────────────────────────────────────────────────────────────

// Required entry order — building 6 is visited twice
private val ENTRY_ORDER = listOf(5, 3, 7, 1, 9, 6, 8, 2, 6)

// Sentinel for the RAD / "mute" button door
private const val RAD_DIGIT = -1

private data class DoorInfo(val digit: Int, val cx: Float, val cz: Float, val face: Int)

// Portrait-mode door positions (face: 0=S 1=N 2=E 3=W).
// Chosen so no two adjacent buildings have facing doors.
private val DOOR_INFOS = listOf(
    DoorInfo(7, -300f, -200f, 3),  // W
    DoorInfo(8, -100f, -200f, 1),  // N
    DoorInfo(9,  100f, -200f, 2),  // E
    DoorInfo(4, -300f,    0f, 0),  // S
    DoorInfo(5, -100f,    0f, 2),  // E
    DoorInfo(6,  100f,    0f, 1),  // N
    DoorInfo(1, -300f,  200f, 2),  // E
    DoorInfo(2, -100f,  200f, 1),  // N
    DoorInfo(3,  100f,  200f, 3),  // W
)
// Landscape: same doors, buildings shifted +500 in X
private val DOOR_INFOS_L = DOOR_INFOS.map { DoorInfo(it.digit, it.cx + 500f, it.cz, it.face) }

// World-space point in front of a door (where player stands to trigger it)
private fun doorApproachPos(d: DoorInfo): Pair<Float, Float> {
    val mx = BW_V + 18f
    val mz = BD_V + 18f
    return when (d.face) {
        0    -> Pair(d.cx, d.cz + mz)    // South door — approach from south
        1    -> Pair(d.cx, d.cz - mz)    // North door — approach from north
        2    -> Pair(d.cx + mx, d.cz)    // East door  — approach from east
        else -> Pair(d.cx - mx, d.cz)    // West door  — approach from west
    }
}

private enum class AnswerType { EXACT, OPEN, TIME_24H, YWDHMS }

private data class Riddle(
    val question: String,
    val type: AnswerType,
    val answer: String = "",       // expected string for EXACT type
    val prefix: String = "",       // display prefix, e.g. "£"
    val allowMinus: Boolean = false
)

private val DOOR_RIDDLES: Map<Int, Riddle> = mapOf(
    1         to Riddle("When was the battle of Anjar?",
                        AnswerType.EXACT, "1623", allowMinus = true),
    2         to Riddle("Did you really read everything about me?\nDo you know your privacy?",
                        AnswerType.EXACT, "113"),
    3         to Riddle("There is an image in the store that hasn't made sense until now...",
                        AnswerType.EXACT, "047"),
    4         to Riddle("What is the time? In 24.", AnswerType.TIME_24H),
    5         to Riddle("How many buildings are there?", AnswerType.EXACT, "21"),
    6         to Riddle("How much do you think life is worth?", AnswerType.OPEN, prefix = "£"),
    7         to Riddle("Back to the store.\nFor the sentence that doesn't quite fit...",
                        AnswerType.EXACT, "0101"),
    8         to Riddle("But if your credit score is under?!", AnswerType.EXACT, "600"),
    9         to Riddle("How much time do we have left?", AnswerType.YWDHMS),
    RAD_DIGIT to Riddle("From 1 to 10, how would you rate your stay in the city?",
                        AnswerType.OPEN)
)

@Composable
fun CalculatorCityView(modifier: Modifier = Modifier) {

    val renderer = remember { CityGLRenderer() }
    val context  = LocalContext.current
    val configuration = LocalConfiguration.current
    // Mutable state so the game-loop coroutine always reads the current orientation
    var isLandscape by remember { mutableStateOf(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) }
    SideEffect { isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }

    // Persist intro completion so it doesn't replay on rotation / remount
    val cityPrefs = remember { context.getSharedPreferences("calc_city", android.content.Context.MODE_PRIVATE) }
    val introAlreadyDone = remember { cityPrefs.getBoolean("intro_done", false) }
    val intro = remember { Animatable(if (introAlreadyDone) 1f else 0f) }
    var introDone by remember { mutableStateOf(introAlreadyDone) }
    LaunchedEffect(Unit) {
        if (!introDone) {
            delay(500)
            intro.animateTo(1f, tween(6000, easing = CubicBezierEasing(0.22f, 0f, 0.08f, 1f)))
            renderer.buildingHeightScale = 2f
            renderer.needsRebuild = true
            introDone = true
            cityPrefs.edit().putBoolean("intro_done", true).apply()
        } else {
            renderer.buildingHeightScale = 2f
            renderer.needsRebuild = true
        }
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
    var aerialBlend by remember { mutableStateOf(if (introAlreadyDone) 0f else 1f) }

    // ── Door interaction state ────────────────────────────────────────────────
    var doorPromptDigit  by remember { mutableStateOf<Int?>(null) }   // "Enter X?" dialog
    var riddleDigit      by remember { mutableStateOf<Int?>(null) }   // riddle dialog
    var riddleInput      by remember { mutableStateOf("") }
    var entryProgress    by remember { mutableIntStateOf(run {
        val saved  = cityPrefs.getInt("entry_progress", 0)
        // If building 1 was completed before entry_progress existed, treat as at least 1
        val b1Done = cityPrefs.getBoolean("td_b1_done", false)
        if (b1Done && saved < 1) 1 else saved
    }) }
    var orderBlockedDigit by remember { mutableStateOf<Int?>(null) }  // "complete 1-N first" message
    // Per-door cooldown after "NO" — prevents immediate re-trigger
    var doorCooldownDigit by remember { mutableStateOf<Int?>(null) }

    // ── Building 1 minigame + bridge ──────────────────────────────────────────
    val prefs = cityPrefs   // alias — same SharedPreferences instance
    var towerDefenseCompleted by remember { mutableStateOf(prefs.getBoolean("td_b1_done", false)) }
    var showTowerDefense by remember { mutableStateOf(false) }
    var showMaze         by remember { mutableStateOf(false) }
    var bridgePieces     by remember { mutableIntStateOf(prefs.getInt("bridge_pieces", if (prefs.getBoolean("td_b1_done", false)) 1 else 0)) }
    var forceAerial      by remember { mutableStateOf(false) }

    // Sync green door and bridge pieces on first composition if already completed
    LaunchedEffect(towerDefenseCompleted) {
        if (towerDefenseCompleted) {
            renderer.b1DoorGreen = true
            renderer.needsRebuild = true
        }
    }

    // Sync bridge pieces to renderer on first composition
    LaunchedEffect(Unit) {
        renderer.bridgePieces = bridgePieces
    }

    // Hold aerial view for 5 s after a building is completed
    LaunchedEffect(forceAerial) {
        if (forceAerial) {
            delay(5000)
            forceAerial = false
        }
    }

    // Fade aerial look → first-person over 2 s after intro ends
    LaunchedEffect(introDone) {
        if (introDone) {
            if (!introAlreadyDone) {
                // Normal slow fade only on first ever entry
                repeat(120) {
                    delay(16)
                    aerialBlend = 1f - (it + 1f) / 120f
                    renderer.aerialBlend = aerialBlend
                }
            } else {
                aerialBlend = 0f
                renderer.aerialBlend = 0f
            }
        }
    }

    // Keep renderer in sync with orientation; teleport player to safe start for new layout
    LaunchedEffect(isLandscape) {
        renderer.isLandscape = isLandscape
        renderer.needsRebuild = true
        if (introDone) {
            pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
            pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
            camYaw = 0f
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

                // ── Forced aerial pan (after building completion) ─────────────
                if (forceAerial) {
                    renderer.aerialMode = true
                    renderer.fov        = 86f
                    renderer.camX       = if (isLandscape) 150f else 0f
                    renderer.camY       = if (isLandscape) 1100f else 1300f
                    renderer.camZ       = if (isLandscape) 50f else -420f
                    renderer.camPitch   = -90f
                    renderer.introLookZ = renderer.camZ
                    renderer.useLookAt  = false
                    continue
                }

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

                // ── Door proximity detection ──────────────────────────────────
                if (doorPromptDigit == null && riddleDigit == null && orderBlockedDigit == null) {
                    val infos = if (isLandscape) DOOR_INFOS_L else DOOR_INFOS

                    // Any digit building triggers on approach
                    for (door in infos) {
                        val (ax, az) = doorApproachPos(door)
                        val dsq = (pX - ax) * (pX - ax) + (pZ - az) * (pZ - az)
                        if (doorCooldownDigit == door.digit && dsq > 70f * 70f) doorCooldownDigit = null
                        if (dsq < 45f * 45f && doorCooldownDigit != door.digit) {
                            val b1Done = door.digit == 1 && prefs.getBoolean("td_b1_done", false)
                            if (!b1Done) {
                                // TODO: re-enable order enforcement after testing
                                // if (door.digit in 1..9 && door.digit > entryProgress + 1) {
                                //     orderBlockedDigit = door.digit
                                // } else {
                                doorPromptDigit = door.digit
                                // }
                            }
                            break
                        }
                    }

                    // RAD button (portrait: inside lava zone; landscape: behind wall through gap)
                    val rbx = if (isLandscape) -350f else 0f
                    val rbz = if (isLandscape) -100f else -1150f
                    val radDoorZ = rbz + 88f + 18f   // south face of cylinder + approach margin
                    val rdSq = (pX - rbx) * (pX - rbx) + (pZ - radDoorZ) * (pZ - radDoorZ)
                    if (doorCooldownDigit == RAD_DIGIT && rdSq > 70f * 70f) doorCooldownDigit = null
                    if (rdSq < 45f * 45f && doorCooldownDigit != RAD_DIGIT) doorPromptDigit = RAD_DIGIT
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

        if (introDone && !showTowerDefense && !forceAerial) {
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

        // ── "Complete buildings 1-N first" message ────────────────────────────
        if (!showTowerDefense && !forceAerial) {
            orderBlockedDigit?.let { digit ->
                OrderBlockedDialog(
                    digit = digit,
                    onDismiss = {
                        doorCooldownDigit = digit
                        orderBlockedDigit = null
                    }
                )
            }
        }

        // ── "Enter X?" prompt ─────────────────────────────────────────────────
        if (!showTowerDefense && !forceAerial) {
            doorPromptDigit?.let { digit ->
                DoorPromptDialog(
                    digit = digit,
                    onYes = {
                        doorPromptDigit = null
                        riddleInput = ""
                        riddleDigit = digit
                    },
                    onNo  = {
                        doorCooldownDigit = digit
                        doorPromptDigit = null
                    }
                )
            }

            // ── Riddle dialog ─────────────────────────────────────────────────
            riddleDigit?.let { digit ->
                val riddle = DOOR_RIDDLES[digit] ?: return@let
                key(digit) {
                    RiddleDialog(
                        riddle        = riddle,
                        digit         = digit,
                        input         = riddleInput,
                        onInputChange = { riddleInput = it },
                        onSubmit      = {
                            riddleDigit = null
                            riddleInput = ""
                            when {
                                digit == 1 -> showTowerDefense = true   // launch minigame
                                digit == 2 -> showMaze = true           // launch maze
                                digit != RAD_DIGIT -> {
                                    entryProgress++
                                    prefs.edit().putInt("entry_progress", entryProgress).apply()
                                }
                            }
                        },
                        onDismiss     = {
                            riddleDigit = null
                            riddleInput = ""
                        }
                    )
                }
            }
        }

        // ── Building 1 tower-defense minigame ─────────────────────────────────
        if (showTowerDefense) {
            TowerDefenseGame(
                onComplete = {
                    showTowerDefense = false
                    entryProgress++
                    prefs.edit().putInt("entry_progress", entryProgress).apply()
                    bridgePieces = (bridgePieces + 1).coerceAtMost(9)
                    renderer.bridgePieces = bridgePieces
                    prefs.edit().putInt("bridge_pieces", bridgePieces).apply()
                    towerDefenseCompleted = true
                    prefs.edit().putBoolean("td_b1_done", true).apply()
                    renderer.b1DoorGreen = true
                    renderer.needsRebuild = true
                    doorCooldownDigit = 1  // suppress immediate re-trigger on return
                    // Teleport player back to start
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                    forceAerial = true
                }
            )
        }

        // ── Building 2 maze minigame ──────────────────────────────────────────
        if (showMaze) {
            MazeGame(
                onComplete = {
                    showMaze = false
                    entryProgress++
                    prefs.edit().putInt("entry_progress", entryProgress).apply()
                    bridgePieces = (bridgePieces + 1).coerceAtMost(9)
                    renderer.bridgePieces = bridgePieces
                    prefs.edit().putInt("bridge_pieces", bridgePieces).apply()
                    doorCooldownDigit = 2
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                    forceAerial = true
                },
                onExit = {
                    showMaze = false
                    doorCooldownDigit = 2
                }
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

// ─────────────────────────────────────────────────────────────────────────────
// DOOR PROMPT DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DoorPromptDialog(digit: Int, onYes: () -> Unit, onNo: () -> Unit) {
    val label = if (digit == RAD_DIGIT) "MUTE" else digit.toString()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* consume touches */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF111111), shape = RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF33FF66), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                ">>> ENTER [$label] ?",
                color = Color(0xFF33FF66),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CityDialogButton("[ YES ]", Color(0xFFFF6600), onYes)
                CityDialogButton("[ NO  ]", Color(0xFF2A2A2A), onNo)
            }
        }
    }
}

@Composable
private fun OrderBlockedDialog(digit: Int, onDismiss: () -> Unit) {
    val prevCount = digit - 1
    val prereq = if (prevCount == 1) "building 1" else "buildings 1–$prevCount"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* consume touches */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF111111), shape = RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFFFF4444), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                ">>> ACCESS DENIED <<<",
                color = Color(0xFFFF4444),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "You must first complete\n$prereq",
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            CityDialogButton("[ OK ]", Color(0xFF2A2A2A), onDismiss)
        }
    }
}

@Composable
private fun CityDialogButton(label: String, bg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(bg, shape = RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF444444), shape = RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFFEEEEEE), fontSize = 15.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RIDDLE DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RiddleDialog(
    riddle: Riddle,
    digit: Int,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    var wrongFlash by remember { mutableStateOf(false) }
    val ywdState   = remember { mutableStateListOf("", "", "", "", "", "", "") }
    var ywdActive  by remember { mutableIntStateOf(0) }

    // Format digits as HH:MM for TIME_24H display
    val timeDigits  = if (riddle.type == AnswerType.TIME_24H) input.filter { it.isDigit() }.take(4) else ""
    val timeDisplay = when {
        riddle.type != AnswerType.TIME_24H -> ""
        timeDigits.length <= 2 -> timeDigits.padEnd(2, '_') + ":__"
        else -> timeDigits.take(2) + ":" + timeDigits.drop(2).padEnd(2, '_')
    }

    fun handleKey(key: String) {
        wrongFlash = false
        when (riddle.type) {
            AnswerType.YWDHMS -> {
                if (key == "DEL") {
                    ywdState[ywdActive] = ywdState[ywdActive].dropLast(1)
                } else {
                    if (ywdState[ywdActive].length < 5) ywdState[ywdActive] += key
                }
            }
            AnswerType.TIME_24H -> {
                val d = input.filter { it.isDigit() }
                if (key == "DEL") onInputChange(d.dropLast(1))
                else if (d.length < 4) onInputChange(d + key)
            }
            else -> {
                when (key) {
                    "DEL" -> onInputChange(input.dropLast(1))
                    "-"   -> if (input.isEmpty()) onInputChange("-")
                    else  -> onInputChange(input + key)
                }
            }
        }
    }

    fun handleSubmit() {
        when (riddle.type) {
            AnswerType.EXACT -> {
                if (input == riddle.answer) onSubmit()
                else wrongFlash = true
            }
            AnswerType.TIME_24H -> {
                if (input.filter { it.isDigit() }.length >= 4) onSubmit()
                else wrongFlash = true
            }
            AnswerType.OPEN,
            AnswerType.YWDHMS -> onSubmit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* consume */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF111111), shape = RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF33AA55), shape = RoundedCornerShape(4.dp))
                .padding(20.dp)
                .widthIn(min = 280.dp, max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dismiss button
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                ) {
                    Text("[X]", color = Color(0xFF55AA55), fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }

            // Question text — amber, like LCD backlight
            Text(
                riddle.question,
                color = Color(0xFFFFCC44),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Input area
            when (riddle.type) {
                AnswerType.YWDHMS -> {
                    val unitLabels = listOf("Y", "M", "W", "D", "H", "m", "S")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            unitLabels.take(4).forEachIndexed { i, lbl ->
                                YWDBox(lbl, ywdState[i], i == ywdActive) { ywdActive = i }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.Center) {
                            unitLabels.drop(4).forEachIndexed { i, lbl ->
                                YWDBox(lbl, ywdState[i + 4], i + 4 == ywdActive) { ywdActive = i + 4 }
                            }
                        }
                    }
                }
                AnswerType.TIME_24H -> {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0D1A0D), shape = RoundedCornerShape(3.dp))
                            .border(1.dp, Color(0xFF226633), shape = RoundedCornerShape(3.dp))
                            .padding(horizontal = 28.dp, vertical = 10.dp)
                    ) {
                        Text(
                            timeDisplay,
                            color = Color(0xFF33FF66),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp
                        )
                    }
                }
                else -> {
                    val displayed = buildString {
                        if (riddle.prefix.isNotEmpty()) append(riddle.prefix).append(" ")
                        append(if (input.isEmpty()) "" else input)
                        append("_")
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1A0D), shape = RoundedCornerShape(3.dp))
                            .border(1.dp, Color(0xFF226633), shape = RoundedCornerShape(3.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            displayed,
                            color = Color(0xFF33FF66),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (wrongFlash) {
                        Text(
                            "ERR: INCORRECT",
                            color = Color(0xFFFF3300),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            MiniKeyboard(allowMinus = riddle.allowMinus, onKey = ::handleKey)

            Spacer(modifier = Modifier.height(12.dp))

            // ENTER / submit — orange like a calculator = key
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFF6600), shape = RoundedCornerShape(3.dp))
                    .clickable { handleSubmit() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("[ ENTER ]", color = Color.White, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ── YWDHMS input box ──────────────────────────────────────────────────────────

@Composable
private fun YWDBox(label: String, value: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(3.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color(0xFF55AA55), fontSize = 10.sp,
            fontFamily = FontFamily.Monospace)
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 38.dp)
                .background(
                    if (active) Color(0xFF0D2010) else Color(0xFF0A150A),
                    shape = RoundedCornerShape(2.dp)
                )
                .border(
                    width = if (active) 1.5.dp else 0.5.dp,
                    color = if (active) Color(0xFF33FF66) else Color(0xFF224422),
                    shape = RoundedCornerShape(2.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                value,
                color = Color(0xFF33FF66),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Mini numeric keyboard ─────────────────────────────────────────────────────

@Composable
private fun MiniKeyboard(allowMinus: Boolean, onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf(if (allowMinus) "-" else "", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (key in row) {
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.size(width = 54.dp, height = 46.dp))
                    } else {
                        val isDelete = key == "⌫"
                        Box(
                            modifier = Modifier
                                .size(width = 54.dp, height = 46.dp)
                                .background(
                                    if (isDelete) Color(0xFF2A1500) else Color(0xFF222222),
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .border(1.dp,
                                    if (isDelete) Color(0xFF884400) else Color(0xFF333333),
                                    shape = RoundedCornerShape(3.dp))
                                .clickable { onKey(if (isDelete) "DEL" else key) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                key,
                                color = if (isDelete) Color(0xFFFF6633) else Color(0xFFCCCCCC),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun easeInOut(t: Float) = if (t < 0.5f) 2 * t * t else 1f - (-2 * t + 2f).let { it * it } / 2f
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
