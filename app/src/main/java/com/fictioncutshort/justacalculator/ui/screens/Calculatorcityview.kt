package com.fictioncutshort.justacalculator.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

// Collision matches the renderer's GROUND footprint (player only navigates at ground level).
private const val BW_V = 60f    // matches renderer BW_GROUND
private const val BD_V = 50f    // matches renderer BD_GROUND

// City grid pitch — MUST match Cityglrenderer.kt's CELL field. Drives every
// hardcoded coordinate in this file (building footprints, lamp posts, door
// trigger points, player start, lava and wall boundaries, camera framing).
private const val CELL_V = 260f

// Portrait column / row centres (mirror renderer C1..C4 / RA..RE).
private val PC1 = -CELL_V * 1.5f   // -420
private val PC2 = -CELL_V * 0.5f   // -140
private val PC3 =  CELL_V * 0.5f   //  140
private val PC4 =  CELL_V * 1.5f   //  420
private val PRA = -CELL_V * 2f     // -560
private val PRB = -CELL_V * 1f     // -280
private val PRC =  0f
private val PRD =  CELL_V * 1f     //  280
private val PRE =  CELL_V * 2f     //  560

// Landscape column / row centres (mirror buildSceneLandscape lC1..lC4 / lRA..lRE).
private val LSC1 = CELL_V * 1f     //  280
private val LSC2 = CELL_V * 2f     //  560
private val LSC3 = CELL_V * 3f     //  840
private val LSC4 = CELL_V * 4f     // 1120
private val LSRA = -CELL_V * 2f
private val LSRB = -CELL_V * 1f
private val LSRC =  0f
private val LSRD =  CELL_V * 1f
private val LSRE =  CELL_V * 2f

// Portrait constants — start the player one intersection SW of building 1
// (digit 4 in row C col 1 = PC1/PRC).
//
// PLAYER_START_X is pinned to PORTRAIT_LAND_X so the intro's phase-2 forward
// motion has ZERO X drift — the camera "drives" straight up the col-1 /
// col-2 street without swerving when the cuts fire. The trade-off is the
// player starts a bit east of the street centre (street is roughly -330 to
// -190 at CELL=260, centre at -260; we start at -200, about 60 units east of
// centre). Far cheaper than the swerve.
private val PLAYER_START_X = -210f                      // matches PORTRAIT_LAND_X
// Centre of the row between PRC (north wall at Z=64) and PRD (south wall at
// Z=196), giving the player ~66 units of clearance on each side. Previously
// PRC + CELL_V * 0.75 = 195 — that's ONE UNIT north of PRD's wall, so any
// joystick push with a southward component triggered axis-separated collision
// and the player felt stuck until they swept far enough north to clear it.
private val PLAYER_START_Z = (PRC + PRD) * 0.5f         //  130
private val LAVA_FRONT_Z   = PRA - BD_V - 20f           // just past the north city edge

// Landscape constants — match the original "halfway between buildings" feel.
private val PLAYER_START_X_L = (LSC2 + LSC3) * 0.5f     //  700
private val PLAYER_START_Z_L = (LSRD + LSRE) * 0.5f     //  420
private val LAVA_WEST_X_L    = CELL_V * 0.40f           //  112  (was 80 for CELL=200)
private val LAVA_NORTH_Z_L   = CELL_V * 1.00f           //  280  (was 275)
private val LAVA_SOUTH_Z_L   = CELL_V * 2.10f           //  588  (was 580)
private val L_WALL_E         = LAVA_WEST_X_L + 20f      //  east face of landscape wall
// Gap edges match renderer's ground geometry (LSRD+BD_GROUND=330, LSRE-BD_GROUND=510) with ~5u slack.
private val L_GAP_N          = LSRD + BD_V - 5f         // 325
private val L_GAP_S          = LSRE - BD_V + 5f         // 515

private const val DEFAULT_CAM_PITCH = 28f
private const val CAM_DIST         = 165f
private const val CAM_H_BASE       = 8f
private const val CAM_EYE_H        = 55f   // first-person eye height (above ground)
// Portrait intro landing pose (end of phase 1 descent, start of phase 2
// forward motion):
//   X = -200  In the col-1 / col-2 street at every cellStage:
//               · cellStage 0 (CELL=200, BW=85): DEL plot's east edge sits at
//                 x = -300 + 85 = -215, 0 plot's west edge at -185, so the
//                 street is a thin (-215 … -185) strip — X=-200 is in it.
//               · cellStage 3 (CELL=260, BW=60): DEL plot ends at -330, 0
//                 plot starts at -190, so X=-200 sits on 0's west sidewalk.
//             This avoids the "lands in DEL" problem (camera inside the
//             damaged building's volume) we hit when landing at -260 with
//             the compact layout still in place.
//   Y = 117   Standard cinematic landing height.
//   Z = 480   Just south of the city, so phase 2's forward motion has the
//             camera drift north through the col-1 / col-2 street.
private const val PORTRAIT_LAND_X = -210f
private const val PORTRAIT_LAND_Z = 480f
// Sidewalk wraps the digit buildings — match the renderer's mainbuilding scale
// so the camera bump zone follows the actual sidewalk geometry. The model's
// outer XZ is ±5.74; the building proper is ±4.078/±3.063, so the sidewalk
// outer in world units is (footprint × 5.74 / model-body-half).
private val SIDEWALK_X_HALF = BW_V * (5.74f / 4.078f)   // ≈ 84.5
private val SIDEWALK_Z_HALF = BD_V * (5.74f / 3.063f)   // ≈ 93.7
// Vertical step the camera takes when the player crosses onto a sidewalk.
// Tuned to roughly the visual thickness of the sidewalk after the renderer's
// MAIN_BUILDING_HEIGHT_SCALE × buildingHeightScale=2 scaling.
private const val SIDEWALK_BUMP    = 20f

// Portrait collision data — 3×5 digit/function/damaged grid + 1×5 operator column.
private val BLDG_FP: List<FloatArray> = run {
    val out = mutableListOf<FloatArray>()
    val cols = listOf(PC1, PC2, PC3)
    val rows = listOf(PRA, PRB, PRC, PRD, PRE)
    for (cx in cols) for (cz in rows) out.add(floatArrayOf(cx, cz))
    for (cz in rows) out.add(floatArrayOf(PC4, cz))
    out.toList()
}
// Debris collision zones [cx, cz, half-width-x, half-depth-z]. First 4 are the
// operator-column gaps (between rows); last one is the wide south rubble field.
private val DEBRIS_FP: List<FloatArray> = listOf(
    floatArrayOf(PC4, (PRA + PRB) * 0.5f, 175f, 26f),
    floatArrayOf(PC4, (PRB + PRC) * 0.5f, 175f, 26f),
    floatArrayOf(PC4, (PRC + PRD) * 0.5f, 175f, 26f),
    floatArrayOf(PC4, (PRD + PRE) * 0.5f, 175f, 26f),
    floatArrayOf(0f,   PRE + 120f,        430f, 46f),
)

// Landscape collision data (city shifted east).
private val BLDG_FP_L: List<FloatArray> = run {
    val out = mutableListOf<FloatArray>()
    val cols = listOf(LSC1, LSC2, LSC3)
    val rows = listOf(LSRA, LSRB, LSRC, LSRD, LSRE)
    for (cx in cols) for (cz in rows) out.add(floatArrayOf(cx, cz))
    for (cz in rows) out.add(floatArrayOf(LSC4, cz))
    out.toList()
}
private val DEBRIS_FP_L: List<FloatArray> = listOf(
    floatArrayOf(LSC4, (LSRA + LSRB) * 0.5f, 175f, 26f),
    floatArrayOf(LSC4, (LSRB + LSRC) * 0.5f, 175f, 26f),
    floatArrayOf(LSC4, (LSRC + LSRD) * 0.5f, 175f, 26f),
    floatArrayOf(LSC4, (LSRD + LSRE) * 0.5f, 175f, 26f),
    floatArrayOf((LSC2 + LSC3) * 0.5f, LSRE + 120f, 430f, 46f),
)

// Lamp post collision — positions must match the renderer's addAtmosphere offsets
// (NW corner of the SE-of-intersection building). The (+35, +45) offset is the
// post's footprint origin, kept the same as the renderer.
private val LAMP_FP: List<FloatArray> = run {
    val out = mutableListOf<FloatArray>()
    val xs = listOf((PC1 + PC2) * 0.5f, (PC2 + PC3) * 0.5f, (PC3 + PC4) * 0.5f)
    val zs = listOf((PRA + PRB) * 0.5f, (PRB + PRC) * 0.5f, (PRC + PRD) * 0.5f, (PRD + PRE) * 0.5f)
    for (x in xs) for (z in zs) out.add(floatArrayOf(x + 35f, z + 45f))
    out.toList()
}
private val LAMP_FP_L: List<FloatArray> = run {
    val out = mutableListOf<FloatArray>()
    val xs = listOf((LSC1 + LSC2) * 0.5f, (LSC2 + LSC3) * 0.5f, (LSC3 + LSC4) * 0.5f)
    val zs = listOf((LSRA + LSRB) * 0.5f, (LSRB + LSRC) * 0.5f, (LSRC + LSRD) * 0.5f, (LSRD + LSRE) * 0.5f)
    for (x in xs) for (z in zs) out.add(floatArrayOf(x + 35f, z + 45f))
    out.toList()
}
private const val LAMP_R = 12f   // collision radius around the lamp post

// Stickman collision — circular footprints around each decorative figure. Must
// match Cityglrenderer.kt's addStickmen / addStickmenLandscape world positions.
// [cx, cz, radius]
private const val STICKMAN_R = 30f
private val STICKMAN_FP: List<FloatArray> = listOf(
    floatArrayOf(PC1, PRA - BD_V - 30f, STICKMAN_R),       // behind row A (col 1)
    floatArrayOf(PC3, PRA - BD_V - 30f, STICKMAN_R),       // behind row A (col 3)
    floatArrayOf((PC2 + PC3) * 0.5f, PRE, STICKMAN_R),     // between damaged buildings
    floatArrayOf((PC1 + PC2) * 0.5f, PRE, STICKMAN_R),     // between damaged buildings
    floatArrayOf(-150f, PRE + BD_V + 90f,  STICKMAN_R),    // south rubble field
    floatArrayOf( 175f, PRE + BD_V + 120f, STICKMAN_R),    // south rubble field
)
private val STICKMAN_FP_L: List<FloatArray> = listOf(
    floatArrayOf(LSC1, LSRA - BD_V - 30f, STICKMAN_R),
    floatArrayOf(LSC3, LSRA - BD_V - 30f, STICKMAN_R),
    floatArrayOf((LSC2 + LSC3) * 0.5f, LSRE, STICKMAN_R),
    floatArrayOf((LSC1 + LSC2) * 0.5f, LSRE, STICKMAN_R),
    floatArrayOf(LSC2, LSRE + BD_V + 90f,  STICKMAN_R),
    floatArrayOf(LSC3, LSRE + BD_V + 120f, STICKMAN_R),
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
    DoorInfo(7, PC1, PRB, 3),  // W
    DoorInfo(8, PC2, PRB, 1),  // N
    DoorInfo(9, PC3, PRB, 2),  // E
    DoorInfo(4, PC1, PRC, 0),  // S
    DoorInfo(5, PC2, PRC, 2),  // E
    DoorInfo(6, PC3, PRC, 1),  // N
    DoorInfo(1, PC1, PRD, 2),  // E
    DoorInfo(2, PC2, PRD, 1),  // N
    DoorInfo(3, PC3, PRD, 3),  // W
)
// Landscape: same door faces, columns and rows reused from the landscape grid.
private val DOOR_INFOS_L = listOf(
    DoorInfo(7, LSC1, LSRB, 3),
    DoorInfo(8, LSC2, LSRB, 1),
    DoorInfo(9, LSC3, LSRB, 2),
    DoorInfo(4, LSC1, LSRC, 0),
    DoorInfo(5, LSC2, LSRC, 2),
    DoorInfo(6, LSC3, LSRC, 1),
    DoorInfo(1, LSC1, LSRD, 2),
    DoorInfo(2, LSC2, LSRD, 1),
    DoorInfo(3, LSC3, LSRD, 3),
)

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

private enum class AnswerType { EXACT, RANGE, OPEN, TIME_24H, YWDHMS }

private data class Riddle(
    val question: String,
    val type: AnswerType,
    val answer: String = "",                       // expected string for EXACT type
    val prefix: String = "",                       // display prefix, e.g. "£"
    val prefixOptions: List<String> = emptyList(), // when non-empty, prefix is user-selectable
    val allowMinus: Boolean = false,
    val rangeMin: Int? = null,                     // inclusive lower bound for RANGE type
    val rangeMax: Int? = null                      // inclusive upper bound for RANGE type
)

private val DOOR_RIDDLES: Map<Int, Riddle> = mapOf(
    1         to Riddle("When was the battle of Anjar?",
                        AnswerType.EXACT, "1623", allowMinus = true),
    2         to Riddle("Did you really read everything about me?\nDo you know your privacy?",
                        AnswerType.EXACT, "113"),
    3         to Riddle("There is a calculation in the store that is missing a result...",
                        AnswerType.EXACT, "2025"),
    4         to Riddle("What is the time? In 24.", AnswerType.TIME_24H),
    5         to Riddle("Back to the store.\nFor the signature that doesn't quite fit...", AnswerType.EXACT, "837983"),
    6         to Riddle("How much do you think life is worth?",
                        AnswerType.OPEN,
                        prefix = "£",
                        prefixOptions = listOf("£", "€", "USD", "CAD", "AUD", "CZK", "SEK")),
    7         to Riddle("How many fingers on a hand?",
                        AnswerType.RANGE, rangeMin = 0, rangeMax = 7),
    8         to Riddle("How much time do we have left?", AnswerType.YWDHMS),
    9         to Riddle("How many fingers would you sacrifice of an extra year?",
                        AnswerType.RANGE, rangeMin = 0, rangeMax = 22),
    RAD_DIGIT to Riddle("From 1 to 10, how would you rate your stay in the city?",
                        AnswerType.OPEN)
)

// Hints shown when the player taps the "?" in the riddle dialog.

private data class Hints(val basic: String, val advanced: String)

private val DOOR_HINTS: Map<Int, Hints> = mapOf(
    1         to Hints("You should know this already.", "Well, you'll have to google it.\nI doubt, that '17th-century Lebanon' will help."),
    2         to Hints("It's in the long version.\nYou are not expected to remember it.", "As per Google Play policy, every app has to have a detailed privacy policy available to the users without having to download the app.\nThat's the document you are after."),
    3         to Hints("It probably looks just like another screenshot.", "Honestly, besides giving you the answers, I cannot be more detailed than this."),
    4         to Hints("Well. I don't know what to tell you.", "Still don't know what to tell you.\nYou'll have to figure this one out."),
    5         to Hints("Translation of the app icon text to binary.", "Ok. The numbers are in the Google Play Store description."),
    6         to Hints("This is up to you.", "Your life, his life, her life, whose life?"),
    7         to Hints("I don't judge.", "Maybe I'd look twice. But hey. I'm sure you are unique regardless."),
    8         to Hints("Don't take it too personally.", "or take it as personally as you'd like. Your royal 'we'."),
    9         to Hints("Can you haggle?", "You can include toes."),
    RAD_DIGIT to Hints("No pressure. Be honest.",       "Actions tend to have consequences."),
)

@Composable
fun CalculatorCityView(modifier: Modifier = Modifier) {

    val context  = LocalContext.current
    val renderer = remember { CityGLRenderer(context.assets) }
    val configuration = LocalConfiguration.current
    // Mutable state so the game-loop coroutine always reads the current orientation
    var isLandscape by remember { mutableStateOf(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) }
    SideEffect { isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    // Lock device orientation while the city is on-screen. Setting LOCKED only
    // freezes the *current* orientation — it doesn't request a rotation — so the
    // WindowManager has nothing to reconfigure and (on most devices) won't
    // schedule an ActivityRelaunchItem the way changing to SENSOR_LANDSCAPE does.
    // Reverted to UNSPECIFIED on dispose so the calculator regains free rotation
    // when the user backs out of the city.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val prev = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            activity?.requestedOrientation = prev ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Persist intro completion so it doesn't replay on rotation / remount
    val cityPrefs = remember { context.getSharedPreferences("calc_city", android.content.Context.MODE_PRIVATE) }
    val introAlreadyDone = remember { cityPrefs.getBoolean("intro_done", false) }
    val intro = remember { Animatable(if (introAlreadyDone) 1f else 0f) }
    var introDone by remember { mutableStateOf(introAlreadyDone) }
    // Tracks the brief 0.5 s "settle" from the landing pose down to the
    // player's first-person pose at the end of the intro. 0 = at landing,
    // 1 = at player. Pre-set to 1 if the intro is already done so the camera
    // sits at the player position on subsequent entries.
    var landingToPlayerBlend by remember { mutableStateOf(if (introAlreadyDone) 1f else 0f) }

    // Camera / player state (camera IS the player — first-person).
    // Position is persisted per-orientation in the calc_city prefs so it
    // survives backgrounding, process death, and full app closure — on re-entry
    // the player resumes exactly where they left off (once the intro is done).
    val startX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
    val startZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
    val posKey = if (isLandscape) "L" else "P"
    var pX     by rememberSaveable { mutableStateOf(if (introAlreadyDone) cityPrefs.getFloat("pos_x_$posKey", startX) else startX) }
    var pZ     by rememberSaveable { mutableStateOf(if (introAlreadyDone) cityPrefs.getFloat("pos_z_$posKey", startZ) else startZ) }
    var camYaw by rememberSaveable { mutableStateOf(if (introAlreadyDone) cityPrefs.getFloat("pos_yaw_$posKey", 0f) else 0f) }

    // Persist position ~1×/s so even a swipe-kill loses at most a second of walk.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (introDone) {
                cityPrefs.edit()
                    .putFloat("pos_x_$posKey", pX)
                    .putFloat("pos_z_$posKey", pZ)
                    .putFloat("pos_yaw_$posKey", camYaw)
                    .apply()
            }
        }
    }

    // Single joystick input
    var joyX by remember { mutableStateOf(0f) }
    var joyY by remember { mutableStateOf(0f) }

    // First-person eye height — bumped up when the player crosses onto a
    // sidewalk, smoothly ramped back down once they step onto the road.
    var eyeY by remember { mutableStateOf(CAM_EYE_H.toFloat()) }

    // 0..3: drives the renderer's cellStage. Each intro cut increments it,
    // spreading the grid one third of the way from CELL=200 (compact calc-
    // keypad layout) to CELL=260 (full city). Skipped to 3 once the intro
    // has been seen once.
    var cellStageActive by remember { mutableIntStateOf(if (introAlreadyDone) 3 else 0) }
    LaunchedEffect(cellStageActive) {
        renderer.cellStage = cellStageActive
        renderer.needsRebuild = true
    }

    // Lava respawn state
    var inLava     by remember { mutableStateOf(false) }
    var flashAlpha by remember { mutableStateOf(0f) }

    var lavaShift  by remember { mutableStateOf(0f) }
    var aerialBlend by remember { mutableStateOf(if (introAlreadyDone) 0f else 1f) }

    // ── Street-widening transition ────────────────────────────────────────────
    // Buildings shrink from AERIAL → GROUND in 3 discrete cuts during the intro fade,
    // each accompanied by a white flash. After intro, footprint stays at ground; the
    // post-completion aerial fly-over snaps back to aerial and back to ground.
    var whiteFlash by remember { mutableStateOf(0f) }
    val coScope = rememberCoroutineScope()
    val applyFootprint: (Int) -> Unit = { level ->
        val t = level / 3f
        renderer.BW = renderer.BW_AERIAL + (renderer.BW_GROUND - renderer.BW_AERIAL) * t
        renderer.BD = renderer.BD_AERIAL + (renderer.BD_GROUND - renderer.BD_AERIAL) * t
        renderer.needsRebuild = true
    }
    val triggerWhiteFlash: () -> Unit = {
        coScope.launch {
            whiteFlash = 0.55f; delay(70)
            whiteFlash = 0.28f; delay(70)
            whiteFlash = 0f
        }
    }

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
    // Digit currently mid-slide-up + walk-through animation. When non-null, the
    // game-loop suspends joystick movement so the LaunchedEffect can drive pX/pZ
    // and camYaw straight through the now-open doorway.
    var doorOpeningDigit by remember { mutableStateOf<Int?>(null) }

    // ── Building 1 minigame + bridge ──────────────────────────────────────────
    val prefs = cityPrefs   // alias — same SharedPreferences instance
    var towerDefenseCompleted by remember { mutableStateOf(prefs.getBoolean("td_b1_done", false)) }
    var showTowerDefense by remember { mutableStateOf(false) }
    var showMaze         by remember { mutableStateOf(false) }
    var showTankGame     by rememberSaveable { mutableStateOf(false) }
    var showDoor4        by rememberSaveable { mutableStateOf(false) }
    var showBuilding5Map by rememberSaveable { mutableStateOf(false) }
    var showBuilding6Game by rememberSaveable { mutableStateOf(false) }
    var showBuilding7Filter by rememberSaveable { mutableStateOf(false) }
    var showBuilding8Casino by rememberSaveable { mutableStateOf(false) }
    var showBuilding9Flappy by rememberSaveable { mutableStateOf(false) }
    var tankGameCompleted by remember { mutableStateOf(prefs.getBoolean("td_b3_done", false)) }
    var bridgePieces     by remember { mutableIntStateOf(prefs.getInt("bridge_pieces", if (prefs.getBoolean("td_b1_done", false)) 1 else 0)) }
    var forceAerial      by remember { mutableStateOf(false) }

    // Sync green door and bridge pieces on first composition if already completed
    LaunchedEffect(towerDefenseCompleted) {
        if (towerDefenseCompleted) {
            renderer.b1DoorGreen = true
            renderer.needsRebuild = true
        }
    }
    LaunchedEffect(tankGameCompleted) {
        if (tankGameCompleted) {
            renderer.b3DoorGreen = true
            renderer.needsRebuild = true
        }
    }

    // Sync bridge pieces to renderer on first composition
    LaunchedEffect(Unit) {
        renderer.bridgePieces = bridgePieces
    }

    // Restore per-digit completion flags from prefs on first composition so the
    // already-cleared buildings come back with their windows already blacked.
    LaunchedEffect(Unit) {
        for (d in 1..9) {
            renderer.buildingCompleted[d - 1] = prefs.getBoolean("building_done_$d", false)
        }
    }

    // ── Door-open + walk-through sequence ─────────────────────────────────────
    // Triggered by the riddle's onSubmit. Slides the door up over ~700 ms, then
    // auto-walks the player through the now-open opening over ~600 ms while
    // smoothly aligning yaw with the building interior. Once the walk completes,
    // the matching minigame is launched (or, for digits without a minigame,
    // entry-progress is bumped and the player is teleported back).
    LaunchedEffect(doorOpeningDigit) {
        val d = doorOpeningDigit ?: return@LaunchedEffect
        if (d !in 1..9) { doorOpeningDigit = null; return@LaunchedEffect }
        val idx = d - 1
        val door = (if (isLandscape) DOOR_INFOS_L else DOOR_INFOS).firstOrNull { it.digit == d }
        // Slide the door up.
        val openFrames = 42
        for (i in 1..openFrames) {
            delay(16)
            renderer.doorOpenFraction[idx] = i.toFloat() / openFrames
        }
        renderer.doorOpenFraction[idx] = 1f
        delay(150)
        // Auto-walk through the open doorway.
        if (door != null) {
            val (dxStep, dzStep) = when (door.face) {
                0    -> Pair(0f, -1f)   // S door — walk north into building
                1    -> Pair(0f,  1f)   // N door — walk south
                2    -> Pair(-1f, 0f)   // E door — walk west
                else -> Pair( 1f, 0f)   // W door — walk east
            }
            val targetYaw = when (door.face) {
                0    -> 180f
                1    ->   0f
                2    -> -90f
                else ->  90f
            }
            val walkFrames = 38
            val walkSpd    = 2.6f
            for (i in 1..walkFrames) {
                delay(16)
                pX += dxStep * walkSpd
                pZ += dzStep * walkSpd
                val deltaYaw = ((targetYaw - camYaw + 540f) % 360f) - 180f
                camYaw += deltaYaw * 0.12f
            }
        }
        // The act of walking through the door already counts the building as
        // "entered" — flag it now so the windows go dark on return regardless
        // of whether a minigame follows.
        renderer.buildingCompleted[idx] = true
        prefs.edit().putBoolean("building_done_$d", true).apply()

        // Launch the minigame, or just advance progress for non-minigame buildings.
        when (d) {
            1 -> showTowerDefense = true
            2 -> showMaze = true
            3 -> showTankGame = true
            4 -> showDoor4 = true
            5 -> showBuilding5Map = true
            6 -> showBuilding6Game = true
            7 -> showBuilding7Filter = true
            8 -> showBuilding8Casino = true
            9 -> showBuilding9Flappy = true
            else -> {
                entryProgress++
                prefs.edit().putInt("entry_progress", entryProgress).apply()
                renderer.doorOpenFraction[idx] = 0f
                pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                camYaw = 0f
                doorCooldownDigit = d
            }
        }
        doorOpeningDigit = null
    }

    // Reset every door's slide-up fraction whenever no minigame is open. Covers
    // both "minigame finished" and "fresh entry into the city" — ensures the
    // doors are closed when the player can see them.
    LaunchedEffect(showTowerDefense, showMaze, showTankGame, showDoor4, showBuilding5Map, showBuilding6Game, showBuilding7Filter, showBuilding8Casino, showBuilding9Flappy) {
        val anyOpen = showTowerDefense || showMaze || showTankGame || showDoor4 || showBuilding5Map || showBuilding6Game || showBuilding7Filter || showBuilding8Casino || showBuilding9Flappy
        if (!anyOpen) {
            for (i in renderer.doorOpenFraction.indices) renderer.doorOpenFraction[i] = 0f
        }
    }

    // Atmospheric darkening — city gets gloomier with each completed building.
    // Linear ramp from 0 → 1.0 across the 9 possible bridge pieces; pushed to
    // the renderer whenever bridgePieces changes (initial composition + each
    // building's onComplete handler).
    LaunchedEffect(bridgePieces) {
        renderer.darknessLevel = (bridgePieces / 9f).coerceIn(0f, 1f)
    }

    // Hold aerial view for 5 s after a building is completed
    LaunchedEffect(forceAerial) {
        if (forceAerial) {
            applyFootprint(0)         // restore full aerial buildings for the fly-over
            triggerWhiteFlash()
            delay(5000)
            applyFootprint(3)         // back to slim ground footprint
            triggerWhiteFlash()
            forceAerial = false
        }
    }

    // Intro sequence — two overlapping phases on one linear 6 s timeline:
    //
    //   Phase 1 (t = 0 … 0.50, 3 s)  pure aerial (1.2 s) → smooth descent
    //                                (1.8 s). Camera drops from the top-down
    //                                aerial pose to the landing pose. No cuts
    //                                — aerialBlend stays at 1, every
    //                                aerialSkip mesh (sidewalks, lamps,
    //                                cameras, debris) stays hidden.
    //
    //   Phase 2 (t = 0.50 … 1.00, 3 s)  the camera carries on FORWARD at
    //                                constant velocity (landing pose → player
    //                                pose) along a perfectly straight line in
    //                                the col-1 / col-2 street. Pitch lerps
    //                                gently from −8° to 0°; Y drops from 117
    //                                to 55. Three white-flash cuts fire
    //                                AROUND the moving camera, with the city
    //                                expanding outward:
    //                                  · cut 1 (fmT 0.20): cellStage 0 → 1,
    //                                       buildingHeightScale 1 → 2 (city
    //                                       resolves to full height in one
    //                                       flash).
    //                                  · cut 2 (fmT 0.50): cellStage 1 → 2.
    //                                  · cut 3 (fmT 0.80): cellStage 2 → 3,
    //                                       aerialBlend → 0 reveals
    //                                       sidewalks / lamps / cameras /
    //                                       debris in one move.
    //                                The camera's path is uninterrupted by
    //                                the cuts — only the world around it
    //                                changes.
    //
    // landingToPlayerBlend is forced to 1 at intro end so the post-intro
    // game loop's landing→player lerp degenerates to the player pose (no
    // additional settle).
    LaunchedEffect(Unit) {
        if (!introDone) {
            delay(500)
            coroutineScope {
                // Single linear timeline so the forward motion in phase 2
                // has constant velocity (no easing-bezier deceleration).
                launch {
                    intro.animateTo(1f, tween(6000, easing = LinearEasing))
                }
                // Phase 2 cuts: fire at 3 s, then every 0.9 s thereafter.
                // 3 s offset matches the t = 0.50 phase boundary (start of
                // phase 2). aerialBlend transitions smoothly from 1 to 0
                // across the full 3 s of phase 2 so the AO ramp is gradual
                // even though the cellStage / footprint changes are stepped.
                launch {
                    delay(3000)
                    val phase2Frames = 188            // 3 s at 16 ms / frame
                    var level = 0
                    applyFootprint(0)
                    repeat(phase2Frames) {
                        delay(16)
                        val progress = (it + 1f) / phase2Frames
                        aerialBlend = 1f - progress
                        renderer.aerialBlend = aerialBlend
                        val target = when {
                            progress < 0.20f -> 0
                            progress < 0.50f -> 1
                            progress < 0.80f -> 2
                            else             -> 3
                        }
                        if (target != level) {
                            level = target
                            cellStageActive = target
                            if (target == 1) {
                                // Cut 1 also lifts buildings to full
                                // gameplay height under the same white
                                // flash, so they aren't visibly half-scale
                                // any longer than the flash takes.
                                renderer.buildingHeightScale = 2f
                                renderer.needsRebuild = true
                            }
                            applyFootprint(target)
                            triggerWhiteFlash()
                        }
                    }
                }
            }
            introDone = true
            cityPrefs.edit().putBoolean("intro_done", true).apply()
            // Camera is already at the player pose (phase 2 ended there);
            // pin the settle blend at 1 so post-intro's landing→player lerp
            // produces the player pose exactly, no extra glide.
            landingToPlayerBlend = 1f
        } else {
            renderer.buildingHeightScale = 2f
            renderer.needsRebuild = true
            aerialBlend = 0f
            renderer.aerialBlend = 0f
            applyFootprint(3)
            cellStageActive = 3
            landingToPlayerBlend = 1f
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
                // Centred on the grid, so the post-minigame fly-over keeps the
                // calculator-style framing without slewing off to the left.
                if (forceAerial) {
                    renderer.aerialMode = true
                    renderer.fov        = 86f
                    renderer.camX       = if (isLandscape) (LSC1 + LSC4) * 0.5f else 0f
                    renderer.camY       = if (isLandscape) 1100f                 else 1300f
                    renderer.camZ       = 0f
                    renderer.camPitch   = -90f
                    renderer.introLookZ = renderer.camZ
                    renderer.useLookAt  = false
                    continue
                }

                // Yaw from joystick X; no pitch control (camera stays level)
                val doorSeqActive = doorOpeningDigit != null
                if (!doorSeqActive) camYaw += joyX * 3.0f
                val cr = Math.toRadians(camYaw.toDouble())

                // Forward/backward from joystick Y (push up = forward)
                val jFwd = if (doorSeqActive) 0f else -joyY
                val spd = 2.2f
                val dx = (sin(cr) * jFwd * spd).toFloat()
                val dz = (-cos(cr) * jFwd * spd).toFloat()

                val xBoundsMin = if (isLandscape) -CELL_V * 2.5f       else PC1 - BW_V - 10f   // -700 / -490
                val xBoundsMax = if (isLandscape) LSC4 + CELL_V * 0.45f else PC4 + BW_V + 10f   // 1246 / 490
                val nx = (pX + dx).coerceIn(xBoundsMin, xBoundsMax)
                val nz = (pZ + dz).coerceIn(PRA - CELL_V * 0.30f, PRE + CELL_V * 0.25f)         // -644 / 630

                // Collision — slightly wider margin for first-person
                fun blocked(tx: Float, tz: Float): Boolean {
                    val bfp = if (isLandscape) BLDG_FP_L else BLDG_FP
                    val dfp = if (isLandscape) DEBRIS_FP_L else DEBRIS_FP
                    val lfp = if (isLandscape) LAMP_FP_L else LAMP_FP
                    for (fp in bfp) {
                        if (tx > fp[0]-BW_V-14f && tx < fp[0]+BW_V+14f &&
                            tz > fp[1]-BD_V-14f && tz < fp[1]+BD_V+14f) return true
                    }
                    for (dz2 in dfp) {
                        if (tx > dz2[0]-dz2[2] && tx < dz2[0]+dz2[2] &&
                            tz > dz2[1]-dz2[3] && tz < dz2[1]+dz2[3]) return true
                    }
                    for (lp in lfp) {
                        val dxl = tx - lp[0]; val dzl = tz - lp[1]
                        if (dxl*dxl + dzl*dzl < LAMP_R*LAMP_R) return true
                    }
                    val sfp = if (isLandscape) STICKMAN_FP_L else STICKMAN_FP
                    for (sp in sfp) {
                        val dxs = tx - sp[0]; val dzs = tz - sp[1]
                        if (dxs*dxs + dzs*dzs < sp[2]*sp[2]) return true
                    }
                    // Landscape: wall at X=95..115 blocks except at gap Z=265..335
                    if (isLandscape && tx < L_WALL_E && !(tz > L_GAP_N && tz < L_GAP_S)) return true
                    return false
                }
                if (!doorSeqActive) {
                    if (!blocked(nx, nz)) { pX = nx; pZ = nz }
                    else {
                        if (!blocked(nx, pZ)) pX = nx
                        if (!blocked(pX, nz)) pZ = nz
                    }
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
                if (doorPromptDigit == null && riddleDigit == null && orderBlockedDigit == null && doorOpeningDigit == null) {
                    val infos = if (isLandscape) DOOR_INFOS_L else DOOR_INFOS
                    // Only prompt when the player has actually stopped in front of a door,
                    // not while walking through the proximity zone.
                    val stopped = abs(joyX) < 0.12f && abs(joyY) < 0.12f

                    // Any digit building triggers when stopped nearby
                    for (door in infos) {
                        val (ax, az) = doorApproachPos(door)
                        val dsq = (pX - ax) * (pX - ax) + (pZ - az) * (pZ - az)
                        if (doorCooldownDigit == door.digit && dsq > 70f * 70f) doorCooldownDigit = null
                        if (stopped && dsq < 45f * 45f && doorCooldownDigit != door.digit) {
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
                    // Must match the renderer's addRadButton positions.
                    val rbx = if (isLandscape) -CELL_V * 1.25f else 0f             // -350 / 0
                    val rbz = if (isLandscape) -CELL_V * 0.5f  else -CELL_V * 5.75f // -140 / -1610
                    val radDoorZ = rbz + 88f + 18f   // south face of cylinder + approach margin
                    val rdSq = (pX - rbx) * (pX - rbx) + (pZ - radDoorZ) * (pZ - radDoorZ)
                    if (doorCooldownDigit == RAD_DIGIT && rdSq > 70f * 70f) doorCooldownDigit = null
                    if (stopped && rdSq < 45f * 45f && doorCooldownDigit != RAD_DIGIT) doorPromptDigit = RAD_DIGIT
                }

                // ── Camera: blend from landing pose → player first-person ────
                // Driven by landingToPlayerBlend (0 → 1 over the 0.5 s settle
                // that runs AFTER the intro pan), not aerialBlend — the
                // aerialBlend fade now happens DURING the pan so by the time
                // we get here the city is already fully composed and the
                // only remaining motion is the camera glide.
                val fp = landingToPlayerBlend.coerceIn(0f, 1f)

                // Sidewalk step: bump the eye height up when the player is on
                // any digit-building's sidewalk (or inside the doorway zone),
                // then ramp back down once they step off onto the road. The
                // lerp smooths the transition without erasing the "step up"
                // feel — over ~6 frames at 16 ms each. Face-aware: the model
                // rotates 90° for face-2/3 buildings, so their sidewalk zone
                // has X / Z swapped vs face-0/1.
                val onSidewalk = run {
                    val infos = if (isLandscape) DOOR_INFOS_L else DOOR_INFOS
                    var hit = false
                    for (info in infos) {
                        val dx = abs(pX - info.cx); val dz = abs(pZ - info.cz)
                        val xHalf = if (info.face == 0 || info.face == 1) SIDEWALK_X_HALF else SIDEWALK_Z_HALF
                        val zHalf = if (info.face == 0 || info.face == 1) SIDEWALK_Z_HALF else SIDEWALK_X_HALF
                        if (dx <= xHalf && dz <= zHalf) { hit = true; break }
                    }
                    hit
                }
                val eyeTarget = if (onSidewalk) CAM_EYE_H + SIDEWALK_BUMP else CAM_EYE_H.toFloat()
                eyeY += (eyeTarget - eyeY) * 0.20f

                // Landing position (end of intro, start of the 0.5 s settle).
                // MUST match the intro animation's landEye* values so the
                // camera doesn't jump frames when control hands over from
                // intro → post-intro.
                val pStartX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                val pStartZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                // Reference "landing" pose for the post-intro lerp. Must match
                // the intro's phase-2 START pose so the handover is frame-
                // accurate. landingToPlayerBlend is pinned to 1 at intro end,
                // so this lerp collapses to (pX, eyeY, pZ) in practice; the
                // landX/Y/Z values matter only if the lerp is < 1 (i.e. the
                // legacy 0.5 s settle, no longer used).
                val landX = if (isLandscape) pStartX else PORTRAIT_LAND_X
                val landY = CAM_EYE_H.toFloat()                                // eye height — matches new intro landing
                val landZ = if (isLandscape) (pStartZ + 180f) else PORTRAIT_LAND_Z

                renderer.camX = lerp(landX, pX, fp)
                renderer.camY = lerp(landY, eyeY, fp)
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
                    renderer.lookAtY   = eyeY
                    renderer.lookAtZ   = pZ - cos(cr).toFloat()
                }

                renderer.playerX    = pX; renderer.playerZ = pZ
                renderer.showPlayer = false

            } else {
                // Intro on a single linear 6 s timeline, divided into:
                //   t = 0.00 … 0.20  pure aerial pose  (1.2 s, hold)
                //   t = 0.20 … 0.50  descent           (1.8 s, aerial → landing)
                //   t = 0.50 … 1.00  phase 2 forward   (3.0 s, landing → player)
                // The descent ramp uses easeInOut for cinematic feel; phase 2
                // is intentionally LINEAR so the forward velocity is constant
                // and the cuts (which fire from the LaunchedEffect) read as
                // changing the world around an uninterrupted camera path.
                val t = intro.value
                // Landing pose is the END of the descent AND the START of
                // phase 2's forward motion. Pinning it to player-eye height
                // (Y=CAM_EYE_H=55) and player X means phase 2 has ONLY Z
                // motion — no Y drop, no X drift, no pitch change. The
                // camera "drives" straight up the street at constant speed
                // and the cuts don't perturb its trajectory.
                if (isLandscape) {
                    val pStartX_L = PLAYER_START_X_L
                    val pStartZ_L = PLAYER_START_Z_L
                    val landEyeX = pStartX_L
                    val landEyeZ = (pStartZ_L + 180f).toFloat()    // 180 u south of player
                    val landEyeY = CAM_EYE_H.toFloat()              // eye height — close to ground
                    val aerialX = 150f
                    val aerialY = 1100f
                    val aerialZ = 50f
                    when {
                        t < 0.20f -> {
                            renderer.aerialMode = true
                            renderer.fov        = 86f
                            renderer.camX       = aerialX
                            renderer.camY       = aerialY
                            renderer.camZ       = aerialZ
                            renderer.camYaw     = 0f
                            renderer.camPitch   = -90f
                            renderer.introLookZ = aerialZ
                        }
                        t < 0.50f -> {
                            val descentT = ((t - 0.20f) / 0.30f).coerceIn(0f, 1f)
                            val e2 = easeInOut(descentT)
                            renderer.aerialMode = true
                            renderer.fov        = lerp(86f, 82f, e2)
                            renderer.camX       = lerp(aerialX, landEyeX, e2)
                            renderer.camY       = lerp(aerialY, landEyeY, e2)
                            renderer.camZ       = lerp(aerialZ, landEyeZ, e2)
                            renderer.camYaw     = 0f
                            renderer.camPitch   = lerp(-90f, 0f, e2)
                            renderer.introLookZ = lerp(aerialZ, pStartZ_L - 180f * e2, e2)
                        }
                        else -> {
                            // Phase 2 — pure Z motion at constant Y, X, pitch.
                            val fmT = ((t - 0.50f) / 0.50f).coerceIn(0f, 1f)
                            renderer.aerialMode = false
                            renderer.fov        = 82f
                            renderer.camX       = landEyeX
                            renderer.camY       = landEyeY
                            renderer.camZ       = lerp(landEyeZ, pStartZ_L, fmT)
                            renderer.camYaw     = 0f
                            renderer.camPitch   = 0f
                            renderer.introLookZ = pStartZ_L - 180f
                        }
                    }
                } else {
                    val landEyeX = PORTRAIT_LAND_X
                    val landEyeZ = PORTRAIT_LAND_Z
                    val landEyeY = CAM_EYE_H.toFloat()              // eye height — close to ground
                    val aerialX = 0f
                    val aerialY = 1300f
                    val aerialZ = -420f
                    when {
                        t < 0.20f -> {
                            renderer.aerialMode  = true
                            renderer.fov         = 86f
                            renderer.camX        = aerialX
                            renderer.camY        = aerialY
                            renderer.camZ        = aerialZ
                            renderer.camYaw      = 0f
                            renderer.camPitch    = -90f
                            renderer.introLookZ  = aerialZ
                        }
                        t < 0.50f -> {
                            val descentT = ((t - 0.20f) / 0.30f).coerceIn(0f, 1f)
                            val e2 = easeInOut(descentT)
                            renderer.aerialMode  = true
                            renderer.fov         = lerp(86f, 82f, e2)
                            renderer.camX        = lerp(aerialX, landEyeX, e2)
                            renderer.camY        = lerp(aerialY, landEyeY, e2)
                            renderer.camZ        = lerp(aerialZ, landEyeZ, e2)
                            renderer.camYaw      = 0f
                            renderer.camPitch    = lerp(-90f, 0f, e2)
                            renderer.introLookZ  = lerp(aerialZ, PLAYER_START_Z - 180f * e2, e2)
                        }
                        else -> {
                            // Phase 2 — landing (-200, 55, 480) → player
                            // (-200, 55, 195). PURE Z motion at constant Y,
                            // X, and pitch. The camera glides forward in
                            // a perfectly straight line at the same Y, the
                            // cuts rearrange the world around it but don't
                            // touch the camera state. aerialMode is OFF so
                            // the view path uses pitch+yaw (rotation only),
                            // which keeps the look direction stable while
                            // the camera position lerps.
                            val fmT = ((t - 0.50f) / 0.50f).coerceIn(0f, 1f)
                            renderer.aerialMode  = false
                            renderer.fov         = 82f
                            renderer.camX        = landEyeX
                            renderer.camY        = landEyeY
                            renderer.camZ        = lerp(landEyeZ, PLAYER_START_Z, fmT)
                            renderer.camYaw      = 0f
                            renderer.camPitch    = 0f
                            renderer.introLookZ  = PLAYER_START_Z - 180f
                        }
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

        if (introDone && !showTowerDefense && !forceAerial && doorOpeningDigit == null) {
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

        // Street-widening cut flash (white)
        if (whiteFlash > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = whiteFlash))
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
                            if (digit in 1..9) {
                                // Sliding door + walk-through is handled by the
                                // doorOpeningDigit effect below. It animates the
                                // door, moves the camera through it, and then
                                // launches the matching minigame (or just bumps
                                // entryProgress for non-minigame buildings).
                                doorOpeningDigit = digit
                            }
                            // RAD_DIGIT: existing direct behaviour (none here).
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
                    renderer.b2DoorGreen = true
                    renderer.needsRebuild = true
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

        // ── Building 3 phone-app minigame ─────────────────────────────────────
        if (showTankGame) {
            TankGame(
                onComplete = {
                    showTankGame = false
                    tankGameCompleted = true
                    prefs.edit().putBoolean("td_b3_done", true).apply()
                    entryProgress++
                    prefs.edit().putInt("entry_progress", entryProgress).apply()
                    bridgePieces = (bridgePieces + 1).coerceAtMost(9)
                    renderer.bridgePieces = bridgePieces
                    prefs.edit().putInt("bridge_pieces", bridgePieces).apply()
                    renderer.b3DoorGreen = true
                    renderer.needsRebuild = true
                    doorCooldownDigit = 3
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                    forceAerial = true
                }
            )
        }
        // ── Door 4 surveillance & time exhibition room ────────────────────────
        if (showDoor4) {
            Door4Room(
                onComplete = {
                    showDoor4 = false
                    entryProgress++
                    prefs.edit().putInt("entry_progress", entryProgress).apply()
                    bridgePieces = (bridgePieces + 1).coerceAtMost(9)
                    renderer.bridgePieces = bridgePieces
                    prefs.edit().putInt("bridge_pieces", bridgePieces).apply()
                    prefs.edit().putBoolean("td_b4_done", true).apply()
                    renderer.needsRebuild = true
                    doorCooldownDigit = 4
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                    forceAerial = true
                }
            )
        }

        // ── Door 5 real-world walk via map ────────────────────────────────────
        if (showBuilding5Map) {
            Building5Map(
                onComplete = {
                    showBuilding5Map = false
                    entryProgress++
                    prefs.edit().putInt("entry_progress", entryProgress).apply()
                    bridgePieces = (bridgePieces + 1).coerceAtMost(9)
                    renderer.bridgePieces = bridgePieces
                    prefs.edit().putInt("bridge_pieces", bridgePieces).apply()
                    prefs.edit().putBoolean("td_b5_done", true).apply()
                    renderer.needsRebuild = true
                    doorCooldownDigit = 5
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                    forceAerial = true
                },
                onExit = {
                    showBuilding5Map = false
                    doorCooldownDigit = 5
                }
            )
        }

        // ── Building 6 — 3D crowd-runner (prototype; replaces the old 2D mode) ─
        if (showBuilding6Game) {
            Building6Runner(
                onComplete = {
                    showBuilding6Game = false
                    entryProgress++
                    prefs.edit().putInt("entry_progress", entryProgress).apply()
                    bridgePieces = (bridgePieces + 1).coerceAtMost(9)
                    renderer.bridgePieces = bridgePieces
                    prefs.edit().putInt("bridge_pieces", bridgePieces).apply()
                    prefs.edit().putBoolean("td_b6_done", true).apply()
                    renderer.needsRebuild = true
                    doorCooldownDigit = 6
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                    forceAerial = true
                },
                onExit = {
                    showBuilding6Game = false
                    doorCooldownDigit = 6
                }
            )
        }

        // ── Building 7 — vanity face-filter mirror ────────────────────────────
        if (showBuilding7Filter) {
            Building7VanityRoom(
                onComplete = {
                    showBuilding7Filter = false
                    entryProgress++
                    prefs.edit().putInt("entry_progress", entryProgress).apply()
                    prefs.edit().putBoolean("building_done_7", true).apply()
                    renderer.needsRebuild = true
                    doorCooldownDigit = 7
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                }
            )
        }

        // ── Building 8 — gambling room skeleton ───────────────────────────────
        if (showBuilding8Casino) {
            Building8Casino(
                onComplete = {
                    showBuilding8Casino = false
                    entryProgress++
                    prefs.edit().putInt("entry_progress", entryProgress).apply()
                    prefs.edit().putBoolean("building_done_8", true).apply()
                    renderer.needsRebuild = true
                    doorCooldownDigit = 8
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                },
                onExit = {
                    showBuilding8Casino = false
                    doorCooldownDigit = 8
                }
            )
        }

        // ── Building 9 — flappy-style skeleton ────────────────────────────────
        if (showBuilding9Flappy) {
            FlappyBirdGame(
                onComplete = {
                    showBuilding9Flappy = false
                    entryProgress++
                    prefs.edit().putInt("entry_progress", entryProgress).apply()
                    prefs.edit().putBoolean("building_done_9", true).apply()
                    renderer.needsRebuild = true
                    doorCooldownDigit = 9
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                },
                onExit = {
                    showBuilding9Flappy = false
                    doorCooldownDigit = 9
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
    var showHint   by remember { mutableStateOf(false) }
    // Currency-picker state (only meaningful when riddle.prefixOptions is non-empty)
    var selectedPrefix by remember(digit) { mutableStateOf(riddle.prefix) }
    var pickerOpen     by remember { mutableStateOf(false) }

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
            AnswerType.RANGE -> {
                val v = input.toIntOrNull()
                val lo = riddle.rangeMin ?: Int.MIN_VALUE
                val hi = riddle.rangeMax ?: Int.MAX_VALUE
                if (v != null && v in lo..hi) onSubmit()
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
            // Top row: [?] hint on the left, [X] dismiss on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clickable { showHint = true }
                        .padding(4.dp)
                ) {
                    Text("[?]", color = Color(0xFF55AA55), fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace)
                }
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
                    val hasPicker = riddle.prefixOptions.isNotEmpty()
                    val valueText = (if (input.isEmpty()) "" else input) + "_"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1A0D), shape = RoundedCornerShape(3.dp))
                            .border(1.dp, Color(0xFF226633), shape = RoundedCornerShape(3.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedPrefix.isNotEmpty()) {
                                Box(
                                    modifier = if (hasPicker)
                                        Modifier
                                            .clickable { pickerOpen = true }
                                            .padding(end = 6.dp)
                                    else
                                        Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        selectedPrefix + if (hasPicker) " ▾" else "",
                                        color = Color(0xFF33FF66),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (hasPicker) {
                                        DropdownMenu(
                                            expanded = pickerOpen,
                                            onDismissRequest = { pickerOpen = false },
                                            modifier = Modifier
                                                .background(Color(0xFF0D1A0D))
                                                .border(1.dp, Color(0xFF33AA55))
                                        ) {
                                            riddle.prefixOptions.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            opt,
                                                            color = Color(0xFF33FF66),
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 14.sp
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedPrefix = opt
                                                        pickerOpen = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                valueText,
                                color = Color(0xFF33FF66),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
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

            // Building 7 ("vanity") entry disclosure — deliberately small and
            // low-contrast, tucked under the keypad. By entering the door the
            // player consents to being photographed inside and to those images
            // being reused elsewhere in the app.
            if (digit == 7) {
                Text(
                    "By entering you consent to being photographed inside and to those images being used elsewhere in this app.",
                    color = Color(0xFF2E2E2E),
                    fontSize = 7.sp,
                    lineHeight = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
                )
            }

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

        // Hint overlay — sibling of the riddle Column so it stacks above it
        if (showHint) {
            HintDialog(
                hints     = DOOR_HINTS[digit] ?: Hints("(no hint)", "(no hint)"),
                onDismiss = { showHint = false }
            )
        }
    }
}

// ── Hint dialog ───────────────────────────────────────────────────────────────
// Opens with two buttons (Basic / Advanced); clicking one swaps the menu for the
// hint text plus a Back button. Same green-terminal look as the riddle dialog.

@Composable
private fun HintDialog(
    hints: Hints,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<String?>(null) }   // null = menu, "basic" / "advanced" = showing hint

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
            // Dismiss row
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

            // Title — amber LCD-style header
            Text(
                when (selected) {
                    "basic"    -> "BASIC HINT"
                    "advanced" -> "ADVANCED HINT"
                    else       -> "HINTS"
                },
                color = Color(0xFFFFCC44),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (selected == null) {
                HintMenuButton("Basic Hint")    { selected = "basic" }
                Spacer(modifier = Modifier.height(10.dp))
                HintMenuButton("Advanced Hint") { selected = "advanced" }
            } else {
                Text(
                    if (selected == "basic") hints.basic else hints.advanced,
                    color = Color(0xFF33FF66),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                HintMenuButton("Back") { selected = null }
            }
        }
    }
}

@Composable
private fun HintMenuButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1A0D), shape = RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF33AA55), shape = RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFF33FF66), fontSize = 13.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
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
