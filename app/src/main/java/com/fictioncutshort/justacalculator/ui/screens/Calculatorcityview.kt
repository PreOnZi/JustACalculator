package com.fictioncutshort.justacalculator.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaPlayer
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import com.fictioncutshort.justacalculator.R
import com.fictioncutshort.justacalculator.logic.Currency
import com.fictioncutshort.justacalculator.logic.CurrencyStore
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

// Ambient audio mix levels (0..1) for the looping wind bed and footstep bed.
// Two wind loops play offset by half the clip at this (lower) volume each, so
// their fade-tails overlap and the bed never drops to silence at the loop seam.
private const val WIND_VOL = 0.26f
private const val STEPS_VOL = 0.6f

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
// How high the eye climbs over the arched bridge (peak, above the flat approach).
private const val BRIDGE_PEAK_RISE = 45f
// Half-width of the walkable bridge deck (the rails act as walls here).
private const val BRIDGE_DECK_HALF = 16f
// Depth of the lava strip (world units), from its south front edge northward.
private const val LAVA_DEPTH = 360f
// North (far) edge of the lava strip, and the centre of the bridge deck over it.
private val LAVA_NORTH_Z  = LAVA_FRONT_Z - LAVA_DEPTH
private val BRIDGE_MID_Z  = LAVA_FRONT_Z - LAVA_DEPTH * 0.5f
// Once the player has crossed, the city is behind them for good: deaths respawn
// here, on the far bank, instead of back at the city start.
private const val NORTH_RESPAWN_X = 0f
private val NORTH_RESPAWN_Z = LAVA_NORTH_Z - 70f

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

// Required entry order. A building's door stays shut — no prompt, no riddle —
// until every building before it in this chain has been COMPLETED (not merely
// entered).
private val ENTRY_ORDER = listOf(1, 7, 4, 3, 6, 2, 5, 8, 9)

// Completion is recorded under one key per building, written by each building's
// onComplete. The older per-building flags were inconsistent (td_b1_done,
// building_done_7, and Building 2 had none at all), and "building_done_N" is set
// merely by WALKING IN, so it can't be used to gate the order.
private fun isBuildingComplete(prefs: android.content.SharedPreferences, d: Int): Boolean =
    prefs.getBoolean("completed_$d", false)

// The first building in the chain ahead of `digit` that hasn't been finished, or
// null when `digit` is allowed to be entered.
private fun missingPrereq(prefs: android.content.SharedPreferences, digit: Int): Int? {
    val idx = ENTRY_ORDER.indexOf(digit)
    if (idx <= 0) return null                       // not gated, or the first building
    for (i in 0 until idx) {
        val need = ENTRY_ORDER[i]
        if (!isBuildingComplete(prefs, need)) return need
    }
    return null
}

// Seeds "completed_N" from the legacy flags so a save in progress isn't reset.
// Runs once; after this, only onComplete writes these.
private fun migrateCompletionFlags(prefs: android.content.SharedPreferences) {
    if (prefs.getBoolean("completion_migrated", false)) return
    val legacy = mapOf(
        1 to "td_b1_done", 2 to "building_done_2", 3 to "td_b3_done",
        4 to "td_b4_done", 5 to "td_b5_done",     6 to "td_b6_done",
        7 to "building_done_7", 8 to "building_done_8", 9 to "building_done_9",
    )
    val e = prefs.edit()
    for ((d, key) in legacy) if (prefs.getBoolean(key, false)) e.putBoolean("completed_$d", true)
    e.putBoolean("completion_migrated", true).apply()
}

// The crossing is one-way: once the player steps off the north end of the bridge,
// the city is sealed behind them, the monster plants itself mid-deck facing back
// (the way home is shut), and deaths respawn on the far bank rather than the start.
private const val ONE_WAY_BRIDGE = true

// How long the player stands inside the mute button before the city starts to
// come apart. The voiceover runs underneath from the moment they're inside.
private const val COLLAPSE_STARTS_AT_MS = 30_000L
// Used only until res/raw/ending_vo exists — then the real recording's length
// drives the sequence and this is ignored.
private const val ENDING_VO_PLACEHOLDER_MS = 75_000L

private fun buzzEnding(context: android.content.Context, ms: Long) {
    try {
        @Suppress("DEPRECATION")
        val vib: Vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vib.vibrate(ms)
    } catch (_: Throwable) {}
}

// Sentinel for the RAD / "mute" button door — Building 10.
private const val RAD_DIGIT = -1
// Building 10's body. The city's mute button is blown up to RAD_SCALE (matching
// Cityglrenderer.RAD_SCALE) so its interior is walkable at human scale — at 1×
// the props inside the model barely reach the player's knee. Everything below is
// derived from the renderer's r0 = 88 × RAD_SCALE (door half-width = r0 × 0.30).
private const val RAD_SCALE      = 3f
private const val RAD_R0         = 88f * RAD_SCALE     // model's outer radius, 264
// The drum is a zero-thickness tube sitting exactly on r0, so the solid band is
// a thin skin around it — thin enough that the props hugging the wall inside
// stay reachable, thick enough that a 2.2/frame step can't tunnel through.
private const val RAD_R          = RAD_R0 + 6f
private const val RAD_WALL_INNER = RAD_R0 - 14f
// The doorway is the opening the model already carries in its wall: it runs
// 90°..101° around the drum (0° = +X, 90° = +Z / south), so it sits just west of
// due-south. The centre matches Cityglrenderer's RAD_DOOR_DEG; the passable window
// is kept a shade NARROWER than the hole (±5° vs ±5.75°) so the player can never
// squeeze through where the wall actually is.
private const val RAD_DOOR_DEG      = 95.5f
private const val RAD_DOOR_HALF_DEG = 5f

// The spot the player stands on to be prompted for their rating: straight out
// from Building 10's doorway, on the line that runs through it to the centre.
private fun radDoorApproach(bx: Float, bz: Float): Pair<Float, Float> {
    val a = Math.toRadians(RAD_DOOR_DEG.toDouble())
    return Pair(bx + (cos(a) * (RAD_R0 + 22f)).toFloat(),
                bz + (sin(a) * (RAD_R0 + 22f)).toFloat())
}

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
fun CalculatorCityView(
    modifier: Modifier = Modifier,
    // Lets the in-city debug menu leave the city and jump into a phase-1 chapter.
    // Null (the default) hides that section — e.g. if the city is ever hosted
    // somewhere without the calculator behind it.
    onJumpToPhase1: ((com.fictioncutshort.justacalculator.data.Chapter) -> Unit)? = null,
) {

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

    // ── The ending ────────────────────────────────────────────────────────────
    // Standing inside the mute button for long enough ends the story. A voiceover
    // plays; thirty seconds in, the city starts coming apart around the player;
    // when the voiceover finishes, everything goes black and the last conversation
    // happens back on the calculator itself.
    var endBlack by remember { mutableStateOf(0f) }
    var endStarted by remember { mutableStateOf(false) }

    // Debug menu, reached by tapping the joystick 5× and entering the passcode.
    var joyTapCount  by remember { mutableIntStateOf(0) }
    var joyLastTapMs by remember { mutableLongStateOf(0L) }
    var showDebugGate by remember { mutableStateOf(false) }
    var showDebugMenu by remember { mutableStateOf(false) }

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
    // Monster catch cinematic: 0 none · 1 black+buzz · 2 face close-up · 3 black drop.
    var catchPhase by remember { mutableIntStateOf(0) }

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
    // Being inside a building is a PLACE. If the app was killed while one was
    // open, the city reopens it here rather than dropping the player back on the
    // street — so these are seeded from the saved active building, at first
    // composition, before anything can race in and clear it.
    val resumeBuilding = remember {
        com.fictioncutshort.justacalculator.logic.BuildingProgress.activeBuilding(context)
    }
    var showTowerDefense by remember { mutableStateOf(resumeBuilding == 1) }
    var showMaze         by remember { mutableStateOf(resumeBuilding == 2) }
    var showTankGame     by rememberSaveable { mutableStateOf(resumeBuilding == 3) }
    var showDoor4        by rememberSaveable { mutableStateOf(resumeBuilding == 4) }
    var showBuilding5Map by rememberSaveable { mutableStateOf(resumeBuilding == 5) }
    var showBuilding6Game by rememberSaveable { mutableStateOf(resumeBuilding == 6) }
    var showBuilding7Filter by rememberSaveable { mutableStateOf(resumeBuilding == 7) }
    var showBuilding8Casino by rememberSaveable { mutableStateOf(resumeBuilding == 8) }
    var showBuilding9Flappy by rememberSaveable { mutableStateOf(resumeBuilding == 9) }
    // Coins lottery — seeded here in the city ~10s after Building 5 is finished.
    var showCityLottery by rememberSaveable { mutableStateOf(false) }
    // Bumped whenever a building overlay closes, so the currency HUD re-reads
    // the persisted balances a building may have just deposited.
    var currencyRefresh by remember { mutableIntStateOf(0) }
    // Which building was just completed — read by the forceAerial fly-over below to
    // fire the right narration cue when the aerial view lands back in the city.
    var lastCompletedBuilding by remember { mutableIntStateOf(0) }
    // Handoff to the decoupled landing-cue effect. The forceAerial coroutine cancels
    // itself when it sets forceAerial=false, so delayed landing cues (vo018) can't run
    // inline — they run keyed on this instead.
    var landCueBuilding by remember { mutableIntStateOf(0) }
    // Building 6's landing cue (vo027) waits for vo025/026 to finish + a long gap
    // before it plays. Run on its OWN latch, not landCueBuilding, so completing the
    // next building (which reassigns landCueBuilding) can't cancel the pending vo027.
    var b6LandCue by remember { mutableIntStateOf(0) }
    // Guard so the monster's first-encounter line (vo020) only plays once per session.
    // (Not persisted — the persisted flag got set prematurely by the old cancelled
    // coroutine, leaving vo020 permanently muted on affected saves.)
    var monsterLinePlayed by remember { mutableStateOf(false) }
    // Latched true on the first catch; drives vo020 from an effect that can't be
    // cancelled by catchPhase resetting to 0 mid-delay.
    var monsterCaught by remember { mutableStateOf(false) }
    // Fingers the player answered they'd sacrifice at building 9's door riddle —
    // read when the flappy game launches to pick vo033 (1-2) vs vo034 (3+).
    var building9Fingers by remember { mutableIntStateOf(-1) }
    var tankGameCompleted by remember { mutableStateOf(prefs.getBoolean("td_b3_done", false)) }
    var bridgePieces     by remember { mutableIntStateOf(prefs.getInt("bridge_pieces", if (prefs.getBoolean("td_b1_done", false)) 1 else 0)) }
    // Crossing the finished bridge is one-way — once the player reaches the far
    // bank the city is closed off behind them and the monster blocks the deck.
    // With ONE_WAY_BRIDGE off, an already-locked save is released too, so a player
    // who has crossed isn't left stranded on the far bank.
    var crossedBridge    by remember {
        mutableStateOf(ONE_WAY_BRIDGE && prefs.getBoolean("bridge_crossed", false))
    }
    LaunchedEffect(Unit) {
        if (!ONE_WAY_BRIDGE) prefs.edit().putBoolean("bridge_crossed", false).apply()
    }
    // Building 10 — once the rating slider unlocks it, its door stays up and the
    // interior (where the mute buttons are) stays walkable.
    var radDoorOpen      by remember { mutableStateOf(prefs.getBoolean("b10_door_open", false)) }
    var forceAerial      by remember { mutableStateOf(false) }

    // ── Ambient city audio ──────────────────────────────────────────────────
    // Wind: a looping bed running the whole time the city is on screen
    // (isLooping restarts the clip with no gap at the seam). Steps: a looped
    // footstep bed that only plays while the player is actually moving. Wind
    // ducks to silent while a building overlay (minigame) is open so the room's
    // own audio isn't muddied.
    val overlayOpen = showTowerDefense || showMaze || showTankGame || showDoor4 ||
        showBuilding5Map || showBuilding6Game || showBuilding7Filter ||
        showBuilding8Casino || showBuilding9Flappy || showCityLottery ||
        showDebugGate || showDebugMenu
    val isWalking = introDone && !overlayOpen && doorOpeningDigit == null &&
        kotlin.math.abs(joyY) > 0.12f

    // After Building 5 is finished, the "today's draw" lottery popup appears once,
    // after roughly 10s of actual walking. Choosing Play / Lucky Dip quietly
    // stakes ALL the player's coins (settled later as a total loss in Building 8).
    // Reactive: this must flip to true when Building 5 is finished DURING the
    // current city session (a plain remember{} would freeze at the value read
    // when the city first composed, so the popup would only ever fire on a later
    // relaunch). Set to true in the Building 5 onComplete below.
    var b5Done by remember { mutableStateOf(prefs.getBoolean("td_b5_done", false)) }
    LaunchedEffect(b5Done) {
        if (!b5Done || com.fictioncutshort.justacalculator.logic.CurrencyStore.lotteryShown(context)) return@LaunchedEffect
        var walked = 0f
        while (walked < 9f && !com.fictioncutshort.justacalculator.logic.CurrencyStore.lotteryShown(context)) {
            kotlinx.coroutines.delay(200)
            // Read live state each tick (isWalking/overlayOpen are per-recomposition vals).
            val anyOverlay = doorOpeningDigit != null || showBuilding5Map || showBuilding6Game ||
                showBuilding7Filter || showBuilding8Casino || showBuilding9Flappy || showTankGame ||
                showDoor4 || showTowerDefense || showMaze || showCityLottery || forceAerial ||
                doorPromptDigit != null || riddleDigit != null
            if (anyOverlay) continue
            // Mostly credited by walking, but also a slow idle drip so it always fires.
            walked += if (kotlin.math.abs(joyY) > 0.12f) 0.2f else 0.04f
        }
        if (!com.fictioncutshort.justacalculator.logic.CurrencyStore.lotteryShown(context)) showCityLottery = true
    }

    val windPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    val wind2Player = remember { mutableStateOf<MediaPlayer?>(null) }
    val stepsPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    // The monster's beep — looped; its volume is driven by distance in the loop.
    val beepPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        val wind = MediaPlayer.create(context, R.raw.wind)
        if (wind != null) {
            wind.isLooping = true
            wind.setVolume(WIND_VOL, WIND_VOL)
            try { wind.start() } catch (_: Throwable) {}
            windPlayer.value = wind
        }
        // Second wind loop, seeked to the half-way point so its fade-tail lands
        // when the first loop is mid-clip — the bed never goes fully silent.
        val wind2 = MediaPlayer.create(context, R.raw.wind)
        if (wind2 != null) {
            wind2.isLooping = true
            wind2.setVolume(WIND_VOL, WIND_VOL)
            try {
                wind2.seekTo((wind2.duration / 2).coerceAtLeast(0))
                wind2.start()
            } catch (_: Throwable) {}
            wind2Player.value = wind2
        }
        val steps = MediaPlayer.create(context, R.raw.steps)
        if (steps != null) {
            steps.isLooping = true
            steps.setVolume(STEPS_VOL, STEPS_VOL)
            stepsPlayer.value = steps
        }
        val beep = MediaPlayer.create(context, R.raw.beep)
        if (beep != null) {
            beep.isLooping = true
            beep.setVolume(0f, 0f)
            try { beep.start() } catch (_: Throwable) {}
            beepPlayer.value = beep
        }
        onDispose {
            windPlayer.value?.let { try { it.release() } catch (_: Throwable) {} }
            windPlayer.value = null
            wind2Player.value?.let { try { it.release() } catch (_: Throwable) {} }
            wind2Player.value = null
            stepsPlayer.value?.let { try { it.release() } catch (_: Throwable) {} }
            stepsPlayer.value = null
            beepPlayer.value?.let { try { it.release() } catch (_: Throwable) {} }
            beepPlayer.value = null
        }
    }
    // Footsteps follow movement.
    LaunchedEffect(isWalking) {
        val sp = stepsPlayer.value ?: return@LaunchedEffect
        try {
            if (isWalking) { if (!sp.isPlaying) sp.start() } else if (sp.isPlaying) sp.pause()
        } catch (_: Throwable) {}
    }
    // Duck the wind to silent while a minigame overlay is open (keeps it looping
    // — just inaudible — so resuming has no restart click).
    LaunchedEffect(overlayOpen) {
        val v = if (overlayOpen) 0f else WIND_VOL
        windPlayer.value?.let { try { it.setVolume(v, v) } catch (_: Throwable) {} }
        wind2Player.value?.let { try { it.setVolume(v, v) } catch (_: Throwable) {} }
    }

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
        // Seed the completion flags the entry order reads from (once per save).
        migrateCompletionFlags(prefs)
        // Building 8's door gets an RGB frame once Building 5 is finished.
        renderer.b5EntranceGlow = prefs.getBoolean("td_b5_done", false)
        // Building 10's doorway stays open once unlocked.
        renderer.radDoorOpen = radDoorOpen
    }

    // ── The city comes down ───────────────────────────────────────────────────
    // Fires once the player is properly INSIDE the mute button. A voiceover plays
    // over the whole thing (its length is whatever the audio is - the sequence
    // waits for it, so the recording can be swapped without touching this code).
    // Thirty seconds in, the city begins to fall apart around them; when the
    // voiceover ends, the screen goes black and the ending takes over.
    //
    // endStarted must NOT be a key here. It is set below, from inside this very
    // coroutine — and a key changing is what tells Compose to cancel the effect and
    // start it again. The restarted copy then hits the guard on the next line and
    // returns, so the voiceover, the wait and the whole collapse were being killed
    // the instant the player stepped inside. Reading it in the body is enough: it
    // still stops a second run when radDoorOpen recomposes.
    LaunchedEffect(radDoorOpen) {
        if (endStarted || !radDoorOpen) return@LaunchedEffect

        fun insideTheButton(): Boolean {
            val dx = pX - 0f
            val dz = pZ - (-CELL_V * 5.75f)
            return sqrt(dx * dx + dz * dz) < RAD_WALL_INNER
        }

        // The player has to be IN there — and STAY in there. Walking through the
        // door and straight back out used to be enough to arm the whole sequence,
        // and the city would then start coming down thirty seconds later wherever
        // they happened to be by then (halfway back across the bridge, say). So the
        // quiet stretch is now a vigil: step outside during it and the voiceover
        // stops, and nothing happens until they come back and stand there properly.
        var ending = ""
        // Fifteen seconds of just standing there before the ending narration begins —
        // a vigil: stepping outside during it cancels everything, and nothing happens
        // until they come back and stand there properly.
        val ENDING_VO_START_DELAY_MS = 15_000L
        while (true) {
            while (!insideTheButton()) delay(200)

            // The ending is decided HERE and frozen, so every screen after this agrees.
            ending = com.fictioncutshort.justacalculator.logic.EndingStore.choose(context)

            var waited = 0L
            var walkedOut = false
            while (waited < ENDING_VO_START_DELAY_MS) {
                delay(100)
                waited += 100
                if (!insideTheButton()) { walkedOut = true; break }
            }
            if (!walkedOut) break               // they stayed. The city comes down.
        }
        // Past this point the collapse is committed, and they are standing in it.
        endStarted = true

        // vo038 — the ending narration. It starts now (15 s after they stepped in) and
        // the collapse runs for its whole length, so the world goes fully black exactly
        // as the voiceover ends.
        val vo: MediaPlayer? = MediaPlayer.create(context, R.raw.vo038)
        val voMs = vo?.duration?.toLong() ?: ENDING_VO_PLACEHOLDER_MS
        try { vo?.start() } catch (_: Throwable) {}

        val fallMs = voMs.coerceAtLeast(6000L)
        val t0 = System.currentTimeMillis()
        var lastBoom = 0L
        while (true) {
            val e = System.currentTimeMillis() - t0
            val p = (e.toFloat() / fallMs).coerceIn(0f, 1f)
            renderer.collapse = p

            // The phone shakes with the ground.
            buzzEnding(context, (18L + (p * 55f).toLong()))

            // Explosions: white flashes, more often as it goes.
            val gap = (900L - (p * 700f).toLong()).coerceAtLeast(120L)
            if (e - lastBoom > gap) {
                lastBoom = e
                whiteFlash = 0.30f + p * 0.55f
                delay(45)
                whiteFlash = 0f
            }

            // The last stretch fades to black under the noise.
            endBlack = ((p - 0.72f) / 0.28f).coerceIn(0f, 1f)

            if (p >= 1f) break
            delay(30)
        }
        endBlack = 1f
        renderer.collapse = 1f
        try { vo?.stop(); vo?.release() } catch (_: Throwable) {}

        // Out of the city. The rest happens on the calculator.
        delay(700)
        com.fictioncutshort.justacalculator.logic.EndingStore.line = 0
        com.fictioncutshort.justacalculator.logic.EndingStore.phase =
            if (com.fictioncutshort.justacalculator.logic.EndingStore.asksForName(ending))
                com.fictioncutshort.justacalculator.logic.EndingStore.Phase.NAME
            else
                com.fictioncutshort.justacalculator.logic.EndingStore.Phase.DIALOGUE
    }

    // ── Door-open + walk-through sequence ─────────────────────────────────────
    // Triggered by the riddle's onSubmit. Slides the door up over ~700 ms, then
    // auto-walks the player through the now-open opening over ~600 ms while
    // smoothly aligning yaw with the building interior. Once the walk completes,
    // the matching minigame is launched (or, for digits without a minigame,
    // entry-progress is bumped and the player is teleported back).
    LaunchedEffect(doorOpeningDigit) {
        val d = doorOpeningDigit ?: return@LaunchedEffect
        if (d == RAD_DIGIT) {
            // Building 10 — the mute button. The rating slider is its lock: answer it
            // and the black panel filling the doorway is gone, then the player walks
            // in among the buttons. There's no overlay; the interior is part of the
            // city itself, and the door never comes back.
            radDoorOpen = true
            renderer.radDoorOpen = true
            prefs.edit().putBoolean("b10_door_open", true).apply()
            delay(350)
            // Auto-walk in along the line that runs from the doorway to the drum's
            // centre — that's the only line that clears both jambs. Stops short of
            // any furniture standing just inside rather than walking into it.
            val rbx = if (isLandscape) -CELL_V * 1.25f else 0f
            val rbz = if (isLandscape) -CELL_V * 0.5f  else -CELL_V * 5.75f
            val (apX, apZ) = radDoorApproach(rbx, rbz)
            val inX = (rbx - apX); val inZ = (rbz - apZ)
            val inL = sqrt(inX * inX + inZ * inZ).coerceAtLeast(0.001f)
            val dirX = inX / inL; val dirZ = inZ / inL
            val targetYaw = Math.toDegrees(atan2(dirX.toDouble(), -dirZ.toDouble())).toFloat()
            fun propAt(x: Float, z: Float) = renderer.radPropFootprints.any {
                x > it[0] - it[2] - 14f && x < it[0] + it[2] + 14f &&
                z > it[1] - it[3] - 14f && z < it[1] + it[3] + 14f
            }
            var guard = 0
            while (guard++ < 400) {
                val nX = pX + dirX * 3.2f; val nZ = pZ + dirZ * 3.2f
                // Stop once well inside the drum, or if furniture is in the way.
                val dCentre = sqrt((nX - rbx) * (nX - rbx) + (nZ - rbz) * (nZ - rbz))
                if (dCentre < RAD_R0 * 0.55f || propAt(nX, nZ)) break
                delay(16)
                pX = nX; pZ = nZ
                val deltaYaw = ((targetYaw - camYaw + 540f) % 360f) - 180f
                camYaw += deltaYaw * 0.10f
            }
            doorOpeningDigit = null
            return@LaunchedEffect
        }
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

        // The player is now INSIDE. If the app dies here, the city reopens this
        // building on the next launch rather than dropping them back on the street.
        com.fictioncutshort.justacalculator.logic.BuildingProgress.setActive(context, d)

        // Launch the minigame, or just advance progress for non-minigame buildings.
        when (d) {
            1 -> showTowerDefense = true
            2 -> showMaze = true
            3 -> showTankGame = true
            4 -> showDoor4 = true
            5 -> {
                showBuilding5Map = true
                // vo029 — entering building 5 (the correct pin has just been accepted).
                com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo029, cctv = false)
            }
            6 -> showBuilding6Game = true
            7 -> showBuilding7Filter = true
            8 -> {
                showBuilding8Casino = true
                // vo031 — entering building 8.
                com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo031, cctv = false)
            }
            9 -> {
                showBuilding9Flappy = true
                // vo033/034 during the flappy game, keyed to the finger answer given
                // at the door (1-2 fingers → vo033, 3+ → vo034).
                if (building9Fingers in 1..2) {
                    com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo033, cctv = false)
                } else if (building9Fingers >= 3) {
                    com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo034, cctv = false)
                }
            }
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
            // No longer standing inside anything — a relaunch should put the player
            // back on the street, not into a building they already walked out of.
            com.fictioncutshort.justacalculator.logic.BuildingProgress.clearActive(context)
            // A building may have just deposited currency — refresh the HUD.
            currencyRefresh++
        }
    }

    // Atmospheric darkening. Before Building 3 it tracks bridge pieces. Once
    // Building 3 is cleared the city descends into permanent FULL NIGHT — the
    // first time it eases down gradually (the post-landing dusk); on later visits
    // it's already night (persisted via "b3_night_active").
    LaunchedEffect(bridgePieces, tankGameCompleted) {
        if (tankGameCompleted) {
            if (prefs.getBoolean("b3_night_active", false)) {
                renderer.darknessLevel = 1f
            } else {
                while (renderer.darknessLevel < 1f) {
                    renderer.darknessLevel = (renderer.darknessLevel + 0.004f).coerceAtMost(1f)
                    delay(33)
                }
                prefs.edit().putBoolean("b3_night_active", true).apply()
            }
        } else {
            renderer.darknessLevel = (bridgePieces / 9f).coerceIn(0f, 1f)
        }
    }

    // Hold aerial view for 5 s after a building is completed
    LaunchedEffect(forceAerial) {
        if (forceAerial) {
            applyFootprint(0)         // restore full aerial buildings for the fly-over
            triggerWhiteFlash()
            // Building 7's post-completion narration rides the aerial fly-over:
            // vo013 comes in over the view, vo014/vo015 follow right after.
            if (lastCompletedBuilding == 7) {
                com.fictioncutshort.justacalculator.logic.VoiceoverManager.playSequence(
                    listOf(R.raw.vo013, R.raw.vo014, R.raw.vo015), cctv = false
                )
            }
            delay(5000)
            applyFootprint(3)         // back to slim ground footprint
            triggerWhiteFlash()
            // Hand the landing cue to the decoupled effect BEFORE ending the fly-over:
            // setting forceAerial=false cancels this coroutine at its next suspension.
            landCueBuilding = lastCompletedBuilding
            lastCompletedBuilding = 0
            forceAerial = false
        }
    }

    // Landing narration, decoupled from the forceAerial coroutine so delayed cues
    // (vo018) survive the forceAerial=false that ends the fly-over.
    LaunchedEffect(landCueBuilding) {
        when (landCueBuilding) {
            1 -> com.fictioncutshort.justacalculator.logic.VoiceoverManager.playSequence(
                    listOf(R.raw.vo007, R.raw.vo008))
            2 -> com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo028)
            3 -> {
                // Building 3 done: the world settles again (glitch off) as night falls.
                com.fictioncutshort.justacalculator.logic.VoiceoverManager.glitchMode = false
                com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo019)
            }
            4 -> {
                // After building 4 the world feels "off": glitch on. vo018 plays clean,
                // 5 s after landing.
                com.fictioncutshort.justacalculator.logic.VoiceoverManager.glitchMode = true
                delay(5000)
                com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(
                    R.raw.vo018, glitch = false)
            }
            5 -> com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo030)
            6 -> b6LandCue++   // vo027 is delayed — hand it to its own effect (below)
            8 -> com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo032)
        }
        if (landCueBuilding != 0) landCueBuilding = 0
    }

    // Building 6 landing cue (vo027), decoupled onto its own latch so the long
    // pre-roll wait survives the next building reassigning landCueBuilding.
    LaunchedEffect(b6LandCue) {
        if (b6LandCue == 0) return@LaunchedEffect
        // vo025/vo026 started at the building-6 finish and may still be playing.
        // Let it finish, then leave ~8 s of breathing space before vo027 so they
        // don't run into each other.
        while (com.fictioncutshort.justacalculator.logic.VoiceoverManager.isPlaying()) delay(200)
        delay(8000)
        com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo027)
    }

    // Voiceover routing: while the player is out walking the city streets the
    // narration sounds like it's leaking out of the buildings' CCTV cameras
    // (muffled). Inside any building/overlay it plays normally.
    LaunchedEffect(overlayOpen) {
        com.fictioncutshort.justacalculator.logic.VoiceoverManager.cctvMode = !overlayOpen
    }

    // vo020 — the monster's first-encounter line. Fires once, the first time the
    // catch cinematic runs (catchPhase leaves 0), just after the buzz/flash lands.
    LaunchedEffect(catchPhase) {
        if (catchPhase != 0) monsterCaught = true
    }
    LaunchedEffect(monsterCaught) {
        if (monsterCaught && !monsterLinePlayed) {
            monsterLinePlayed = true
            delay(1400)
            com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo020, cctv = true)
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
                    // Aerial view has stabilised and the camera now carries forward
                    // (phase 2). vo003 comes in over the already-running wind, played
                    // flat — the player isn't walking the streets yet, so no CCTV routing.
                    com.fictioncutshort.justacalculator.logic.VoiceoverManager.init(context)
                    com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(
                        R.raw.vo003, cctv = false
                    )
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
            // Landed. From here the player walks the streets, so voiceover routes
            // through the CCTV-camera feel (muffled). vo004 is the first such line.
            com.fictioncutshort.justacalculator.logic.VoiceoverManager.cctvMode = true
            com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo004)
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
        // Night-mode monster roaming state (persists across loop iterations).
        var mX = 0f; var mZ = 0f; var mAngle = 0f
        var mWpX = 0f; var mWpZ = 0f
        var mSpawned = false
        var hideUntil = 0L
        // ── Monster state machine ─────────────────────────────────────────────
        // The monster wanders harmlessly (ROAM) and can be outrun. It only turns
        // lethal once the player looks it straight in the face — and then it
        // commits to one of two attacks immediately, with no wind-up.
        val MS_ROAM = 0
        val MS_DART = 2; val MS_GIANT = 3; val MS_LOCK = 4
        var mState = MS_ROAM
        var mAttack = 0                  // 1 dart, 2 giant
        var mScale = 13f                 // current body scale (grows for the giant)
        var mYBob = 0f                   // legacy vertical offset (kept at 0)
        var mTilt = 0f                   // giant body-slam topple angle (deg)
        var mTiltX = 0f; var mTiltZ = 1f // direction the slam falls toward
        var mDirX = 0f; var mDirZ = 1f   // unit heading the face currently points
        var stateUntil = 0L              // generic phase timer
        var freezeMs = 0L                // continuous time the player has held a lamp circle
        var dartStartDist = 0f           // dart-attack distance at the start (for zig-zag count)
        var slamT = 0f                   // giant-attack body-slam phase
        var zoomT = 0f                   // dart finale: 0→1 camera zoom-onto-face progress
        var monsterLock = false          // freezes player control during the dart finale
        // Yaw so the model's single-faced front leads the travel vector. The
        // offset is auto-measured from the mesh (renderer.monsterFaceYawOffsetDeg).
        fun faceDir(vx: Float, vz: Float): Float =
            Math.toDegrees(atan2(vx.toDouble(), vz.toDouble())).toFloat() +
                renderer.monsterFaceYawOffsetDeg
        fun frand(a: Float, b: Float): Float = a + (Math.random() * (b - a)).toFloat()
        fun buzz(ms: Long) {
            try {
                @Suppress("DEPRECATION")
                val vib: Vibrator =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                    else context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") vib.vibrate(ms)
            } catch (_: Throwable) {}
        }
        var bridgeDarkOverride = false
        while (true) {
            delay(16)
            lavaShift = (lavaShift + 0.003f) % 1f
            renderer.lavaShift = lavaShift

            // Crossing the finished bridge transitions from night → daylight
            // (0 at the lava's south edge, 1 at the far north side).
            val crossDay = if (!isLandscape && bridgePieces >= 9 && pZ < LAVA_FRONT_Z) {
                val lavaN = LAVA_FRONT_Z - 360f
                ((LAVA_FRONT_Z - pZ) / (LAVA_FRONT_Z - lavaN)).coerceIn(0f, 1f)
            } else 0f
            if (crossDay > 0.001f) {
                val baseNight = if (tankGameCompleted) 1f else (bridgePieces / 9f).coerceIn(0f, 1f)
                renderer.darknessLevel = baseNight * (1f - crossDay)
                bridgeDarkOverride = true
            } else if (bridgeDarkOverride) {
                renderer.darknessLevel = if (tankGameCompleted) 1f else (bridgePieces / 9f).coerceIn(0f, 1f)
                bridgeDarkOverride = false
            }
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

                // Yaw from joystick X; no pitch control (camera stays level). The
                // monster's dart finale locks out all control while it closes in.
                val doorSeqActive = doorOpeningDigit != null
                val controlsLocked = doorSeqActive || monsterLock
                if (!controlsLocked) camYaw += joyX * 3.0f
                val cr = Math.toRadians(camYaw.toDouble())

                // Forward/backward from joystick Y (push up = forward)
                val jFwd = if (controlsLocked) 0f else -joyY
                val spd = 2.2f
                val dx = (sin(cr) * jFwd * spd).toFloat()
                val dz = (-cos(cr) * jFwd * spd).toFloat()

                // Portrait west bound extended by one street width (matches the
                // renderer's WEST_LANE) so the player can step into the lane west
                // of building 7 to reach its west-facing door (~x -468).
                val xBoundsMin = if (isLandscape) -CELL_V * 2.5f       else PC1 - BW_V - 150f  // -700 / -600
                val xBoundsMax = if (isLandscape) LSC4 + CELL_V * 0.45f else PC4 + BW_V + 10f   // 1246 / 490
                val nx = (pX + dx).coerceIn(xBoundsMin, xBoundsMax)
                // North bound normally stops at the lava; once the bridge is complete
                // (portrait) it extends north across the lava to the mute button.
                // North bound has to clear the whole of Building 10 (centre
                // -CELL_V*5.75, outer radius RAD_R) so the player can walk right
                // to the back wall inside it.
                // How far north the built bridge reaches: pieces fill from the city
                // (south) edge of the lava northward, 1/9 of the span each.
                val deckEndZ = LAVA_FRONT_Z - (bridgePieces / 9f) * LAVA_DEPTH
                val zBoundsMin = when {
                    !isLandscape && bridgePieces >= 9 -> -CELL_V * 5.75f - RAD_R - 20f
                    // Partial bridge: let them walk out onto the built planks and a
                    // little past the end (so they can step off into the lava).
                    !isLandscape && bridgePieces > 0  -> minOf(PRA - CELL_V * 0.30f, deckEndZ - 25f)
                    else                              -> PRA - CELL_V * 0.30f
                }
                // South bound: normally the city's south edge — but once the bridge
                // has been crossed it clamps to the lava's far bank, so there's no
                // walking back over the deck into the city.
                val zBoundsMax = if (crossedBridge) LAVA_NORTH_Z - 12f
                                 else PRE + CELL_V * 0.25f
                val nz = (pZ + dz).coerceIn(zBoundsMin, zBoundsMax)                              // -644 / 630

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
                    // Building 10 (the mute button) — a sealed body until its door
                    // opens. After that the shell is still solid, but the south
                    // doorway is a gap and the interior is free to walk around in.
                    val rbxC = if (isLandscape) -CELL_V * 1.25f else 0f
                    val rbzC = if (isLandscape) -CELL_V * 0.5f  else -CELL_V * 5.75f
                    val rdx = tx - rbxC; val rdz = tz - rbzC
                    val rd2 = rdx * rdx + rdz * rdz
                    if (radDoorOpen) {
                        val degs = Math.toDegrees(atan2(rdz.toDouble(), rdx.toDouble())).toFloat()
                        val off = abs(((degs - RAD_DOOR_DEG + 540f) % 360f) - 180f)
                        val throughDoorway = off < RAD_DOOR_HALF_DEG
                        if (rd2 < RAD_R * RAD_R && rd2 > RAD_WALL_INNER * RAD_WALL_INNER &&
                            !throughDoorway) return true
                        // Furniture inside Building 10 — the shelves, desks, sofa.
                        // Skipped while the player is already standing in a footprint,
                        // so a bad spawn can always be walked out of rather than
                        // sealing them in on every axis.
                        if (rd2 < RAD_WALL_INNER * RAD_WALL_INNER) {
                            val fps = renderer.radPropFootprints
                            val standingInProp = fps.any {
                                pX > it[0] - it[2] - 10f && pX < it[0] + it[2] + 10f &&
                                pZ > it[1] - it[3] - 10f && pZ < it[1] + it[3] + 10f
                            }
                            if (!standingInProp) for (fp in fps) {
                                if (tx > fp[0] - fp[2] - 10f && tx < fp[0] + fp[2] + 10f &&
                                    tz > fp[1] - fp[3] - 10f && tz < fp[1] + fp[3] + 10f) return true
                            }
                        }
                    } else if (rd2 < RAD_R * RAD_R) return true
                    return false
                }
                if (!controlsLocked) {
                    if (!blocked(nx, nz)) { pX = nx; pZ = nz }
                    else {
                        // Diagonal blocked → slide along a wall. Resolve the axis
                        // the player is pushing hardest along FIRST so a mostly
                        // north/south approach slides past a building corner
                        // instead of being shoved sideways. The old fixed X-first
                        // order made corners "catch" unless you happened to
                        // re-approach at just the right angle.
                        if (abs(dx) >= abs(dz)) {
                            if (!blocked(nx, pZ)) pX = nx
                            if (!blocked(pX, nz)) pZ = nz
                        } else {
                            if (!blocked(pX, nz)) pZ = nz
                            if (!blocked(nx, pZ)) pX = nx
                        }
                    }
                }

                // Bridge rails: while over a built plank keep the player between the
                // rails so they walk the deck (finished OR partial bridge). Past the
                // last plank there's no rail — that's the drop.
                val overDeck = !isLandscape && bridgePieces > 0 &&
                    pZ < LAVA_FRONT_Z && pZ >= deckEndZ
                if (overDeck) pX = pX.coerceIn(-BRIDGE_DECK_HALF, BRIDGE_DECK_HALF)

                // Stepping off the north end of the deck latches the crossing —
                // from here the city is shut behind the player, permanently.
                if (ONE_WAY_BRIDGE &&
                    !isLandscape && bridgePieces >= 9 && !crossedBridge && pZ < LAVA_NORTH_Z - 4f) {
                    crossedBridge = true
                    prefs.edit().putBoolean("bridge_crossed", true).apply()
                }

                // ── Lava boundary — vibrate + flash + teleport ────────────────
                // Only the actual lava STRIP burns (not the green terrain north of
                // it, where the mute button sits); the finished bridge is safe.
                // Safe on a built plank: over the lava, within the deck width, and not
                // past the last piece. Off the end (pZ < deckEndZ) → the lava takes them.
                val onBridge = if (isLandscape) bridgePieces >= 9
                    else bridgePieces > 0 && pZ >= deckEndZ &&
                        kotlin.math.abs(pX) <= BRIDGE_DECK_HALF + 2f
                val inLavaNow = !onBridge && (if (isLandscape)
                    pX < LAVA_WEST_X_L && pZ > LAVA_NORTH_Z_L && pZ < LAVA_SOUTH_Z_L
                else
                    pZ < LAVA_FRONT_Z && pZ > LAVA_FRONT_Z - LAVA_DEPTH)

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
                    // Respawn — at the city start, or on the far bank if the
                    // player has already crossed.
                    if (crossedBridge) {
                        // Facing north — away from the lava, toward the mute button.
                        pX = NORTH_RESPAWN_X; pZ = NORTH_RESPAWN_Z; camYaw = 0f
                    } else {
                        pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                        pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                        camYaw = 0f
                    }
                    inLava = false
                }

                // ── Night-mode monster ────────────────────────────────────────
                run {
                    val nowMs = System.currentTimeMillis()
                    val overlayBusy = showTowerDefense || showMaze || showTankGame || showDoor4 ||
                        showBuilding5Map || showBuilding6Game || showBuilding7Filter ||
                        showBuilding8Casino || showBuilding9Flappy || showCityLottery ||
                        showDebugGate || showDebugMenu
                    // Bridge sentinel: after the crossing the monster stops hunting and
                    // plants itself mid-deck, facing the far bank — the way back is shut.
                    if (crossedBridge) {
                        mState = MS_ROAM; mScale = 13f; mYBob = 0f; mTilt = 0f
                        freezeMs = 0L; monsterLock = false
                        mX = 0f; mZ = BRIDGE_MID_Z
                        mAngle = faceDir(0f, -1f)   // face north, toward the player
                        renderer.monsterX = mX; renderer.monsterZ = mZ; renderer.monsterAngle = mAngle
                        renderer.monsterScale = mScale; renderer.monsterYBob = 0f
                        renderer.monsterTilt = 0f
                        renderer.monsterActive = !overlayBusy
                        renderer.monsterClones = emptyList()
                        val dS = sqrt((pX - mX) * (pX - mX) + (pZ - mZ) * (pZ - mZ))
                        val nearS = (1f - dS / 600f).coerceIn(0f, 1f)
                        val volS = if (overlayBusy) 0f else 0.5f * nearS * nearS
                        beepPlayer.value?.let { try { it.setVolume(volS, volS) } catch (_: Throwable) {} }
                        return@run
                    }
                    val monsterAllowed = tankGameCompleted && !overlayBusy && !doorSeqActive &&
                        doorPromptDigit == null && riddleDigit == null && orderBlockedDigit == null &&
                        nowMs >= hideUntil
                    if (!monsterAllowed) {
                        // Cancel any aggro/attack in progress and go dormant.
                        renderer.monsterActive = false
                        renderer.monsterClones = emptyList()
                        renderer.monsterScale = 13f; renderer.monsterYBob = 0f
                        renderer.monsterTilt = 0f; mTilt = 0f
                        mState = MS_ROAM; mScale = 13f; mYBob = 0f; freezeMs = 0L; monsterLock = false
                        beepPlayer.value?.let { try { it.setVolume(0f, 0f) } catch (_: Throwable) {} }
                    } else {
                        val zMin = PRA - CELL_V * 0.30f
                        val zMax = PRE + CELL_V * 0.25f
                        fun dist(ax: Float, az: Float, bx: Float, bz: Float) =
                            sqrt((ax - bx) * (ax - bx) + (az - bz) * (az - bz))
                        // Step the monster toward (tgx,tgz) at speed, sliding on walls.
                        fun step(tgx: Float, tgz: Float, spd: Float) {
                            val ddx = tgx - mX; val ddz = tgz - mZ
                            val l = sqrt(ddx * ddx + ddz * ddz)
                            if (l < 0.001f) return
                            val nmx = mX + ddx / l * spd; val nmz = mZ + ddz / l * spd
                            when {
                                !blocked(nmx, nmz) -> { mX = nmx; mZ = nmz }
                                !blocked(nmx, mZ)  -> mX = nmx
                                !blocked(mX, nmz)  -> mZ = nmz
                            }
                            mAngle = faceDir(ddx, ddz)
                        }
                        // Line of sight, blocked only by buildings/debris/the wall
                        // (small props don't count as cover).
                        fun losBlocked(ax: Float, az: Float, bx: Float, bz: Float): Boolean {
                            val bfp = if (isLandscape) BLDG_FP_L else BLDG_FP
                            val dfp = if (isLandscape) DEBRIS_FP_L else DEBRIS_FP
                            var i = 1
                            while (i < 26) {
                                val t = i / 26f
                                val x = ax + (bx - ax) * t; val z = az + (bz - az) * t
                                for (fp in bfp) if (x > fp[0]-BW_V-14f && x < fp[0]+BW_V+14f &&
                                    z > fp[1]-BD_V-14f && z < fp[1]+BD_V+14f) return true
                                for (d in dfp) if (x > d[0]-d[2] && x < d[0]+d[2] &&
                                    z > d[1]-d[3] && z < d[1]+d[3]) return true
                                if (isLandscape && x < L_WALL_E && !(z > L_GAP_N && z < L_GAP_S)) return true
                                i++
                            }
                            return false
                        }
                        // Player standing inside a lamp's light circle (a temporary
                        // safe-haven that pauses the monster).
                        fun inLampCircle(): Boolean {
                            val lfp = if (isLandscape) LAMP_FP_L else LAMP_FP
                            for (lp in lfp) {
                                val dx = pX - lp[0]; val dz = pZ - lp[1]
                                if (dx*dx + dz*dz < 80f*80f) return true
                            }
                            return false
                        }
                        // Player's current view forward (unit) and helpers — the
                        // monster can only land a kill from where the player can see
                        // it, so attacks press in from the front.
                        val crView = Math.toRadians(camYaw.toDouble())
                        val fwdVX = sin(crView).toFloat(); val fwdVZ = -cos(crView).toFloat()
                        fun frontX(d: Float) = (pX + fwdVX * d).coerceIn(xBoundsMin, xBoundsMax)
                        fun frontZ(d: Float) = (pZ + fwdVZ * d).coerceIn(zMin, zMax)
                        fun inView(x: Float, z: Float): Boolean {
                            val dx = x - pX; val dz = z - pZ
                            val l = sqrt(dx*dx + dz*dz)
                            if (l < 1f) return true
                            return (dx*fwdVX + dz*fwdVZ) / l > 0.25f
                        }

                        // Spawn far from the player the first time it deploys.
                        if (!mSpawned) {
                            var tries = 0
                            do {
                                mX = frand(xBoundsMin, xBoundsMax); mZ = frand(zMin, zMax); tries++
                            } while ((blocked(mX, mZ) || dist(mX, mZ, pX, pZ) < 280f) && tries < 80)
                            mWpX = mX; mWpZ = mZ; mSpawned = true
                            mState = MS_ROAM; mScale = 13f; mYBob = 0f
                        }

                        // While in a lamp circle during a hunt, the monster freezes;
                        // hold there too long and it detonates, levelling the city.
                        var frozen = false
                        var explodeNow = false
                        if (mState != MS_ROAM && inLampCircle()) {
                            frozen = true
                            freezeMs += 16
                            if (freezeMs > 6500L) explodeNow = true
                            stateUntil += 16   // pause the phase timer while frozen
                        } else {
                            freezeMs = 0L
                        }

                        var killNow = false

                        if (!frozen && !explodeNow) { mTilt = 0f; when (mState) {
                            MS_ROAM -> {
                                // Harmless wander, but moving one cardinal axis at a
                                // time so it tracks the streets parallel to the walls
                                // (never diagonally through the grid). Always moving.
                                if (dist(mX, mZ, mWpX, mWpZ) < 30f) {
                                    var t2 = 0
                                    do { mWpX = frand(xBoundsMin, xBoundsMax); mWpZ = frand(zMin, zMax); t2++ }
                                    while (blocked(mWpX, mWpZ) && t2 < 40)
                                }
                                val dX = mWpX - mX; val dZ = mWpZ - mZ
                                val spd = 1.7f
                                // Cardinal candidates, best-aligned with the waypoint
                                // first (then the sides, then reverse), so it follows
                                // the streets yet can never get boxed in / freeze.
                                val cand = if (abs(dX) >= abs(dZ))
                                    listOf(
                                        floatArrayOf(if (dX >= 0) spd else -spd, 0f),
                                        floatArrayOf(0f, if (dZ >= 0) spd else -spd),
                                        floatArrayOf(0f, if (dZ >= 0) -spd else spd),
                                        floatArrayOf(if (dX >= 0) -spd else spd, 0f))
                                else
                                    listOf(
                                        floatArrayOf(0f, if (dZ >= 0) spd else -spd),
                                        floatArrayOf(if (dX >= 0) spd else -spd, 0f),
                                        floatArrayOf(if (dX >= 0) -spd else spd, 0f),
                                        floatArrayOf(0f, if (dZ >= 0) -spd else spd))
                                var mvx = 0f; var mvz = 0f
                                for (c in cand) if (!blocked(mX + c[0], mZ + c[1])) {
                                    mX += c[0]; mZ += c[1]; mvx = c[0]; mvz = c[1]; break
                                }
                                if (mvx == 0f && mvz == 0f) {
                                    // Fully boxed in (rare) — pick a fresh waypoint.
                                    var t2 = 0
                                    do { mWpX = frand(xBoundsMin, xBoundsMax); mWpZ = frand(zMin, zMax); t2++ }
                                    while (blocked(mWpX, mWpZ) && t2 < 40)
                                } else {
                                    mDirX = if (mvx > 0f) 1f else if (mvx < 0f) -1f else 0f
                                    mDirZ = if (mvz > 0f) 1f else if (mvz < 0f) -1f else 0f
                                    mAngle = faceDir(mvx, mvz)
                                }
                                // Aggro only on DIRECT sight of the face: the player
                                // must be looking at it AND its face must be turned
                                // toward the player. A side/rear glimpse does nothing.
                                // The attack begins on the same frame — no wind-up.
                                val toMx = mX - pX; val toMz = mZ - pZ
                                val dM = sqrt(toMx*toMx + toMz*toMz)
                                if (dM in 60f..520f && !losBlocked(pX, pZ, mX, mZ)) {
                                    val seeMonster = (toMx*fwdVX + toMz*fwdVZ) / dM > 0.62f
                                    val toPx = -toMx / dM; val toPz = -toMz / dM
                                    val faceTowardPlayer = (mDirX*toPx + mDirZ*toPz) > 0.45f
                                    if (seeMonster && faceTowardPlayer) {
                                        mAttack = (1..2).random()
                                        if (mAttack == 1) {
                                            mState = MS_DART
                                            dartStartDist = dM.coerceAtLeast(120f)
                                            stateUntil = nowMs + 1100L
                                        } else { mState = MS_GIANT; slamT = 0f }
                                    }
                                }
                            }
                            MS_DART -> {
                                // Attack 1: rushes the player very fast, ALWAYS facing
                                // them (its face shows, never the flat profile), with
                                // just ~3 quick zig-zags on the way in. The instant it's
                                // on the player it locks and the camera zooms (MS_LOCK).
                                val toX = pX - mX; val toZ = pZ - mZ
                                val d = sqrt(toX*toX + toZ*toZ).coerceAtLeast(0.001f)
                                val ux = toX/d; val uz = toZ/d
                                val perpX = -uz; val perpZ = ux
                                // Zig-zag is tied to closing progress (0→1), so it's a
                                // fixed ~3 swings regardless of how far it started.
                                val progress = ((dartStartDist - d) /
                                    (dartStartDist - 85f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                val weave = sin((progress * 3f * Math.PI).toFloat())
                                val amp = 120f * (1f - progress * 0.7f)
                                val tgx = pX + perpX * weave * amp
                                val tgz = pZ + perpZ * weave * amp
                                step(tgx, tgz, 21f)
                                mAngle = faceDir(toX, toZ)   // keep the face on the player
                                // Lock the moment it's right on top of the player (or as
                                // a fallback once the strafe time is up) — from here.
                                if (d < 85f || nowMs >= stateUntil) {
                                    mState = MS_LOCK
                                    monsterLock = true
                                    zoomT = 0f
                                    mAngle = faceDir(pX - mX, pZ - mZ)
                                }
                            }
                            MS_LOCK -> {
                                // Controls are frozen. The monster holds where it darted
                                // to, faces the camera, and the view zooms hard onto its
                                // face until it fills the screen — then death.
                                mScale = 13f
                                mAngle = faceDir(pX - mX, pZ - mZ)
                                zoomT = (zoomT + 0.030f).coerceAtMost(1f)
                                if (zoomT >= 1f) killNow = true
                            }
                            MS_GIANT -> {
                                // Attack 2: swells huge, works around to the player's
                                // front, and topples its body to smash with its face.
                                mScale = (mScale + 0.85f).coerceAtMost(52f)
                                val fdx = pX - mX; val fdz = pZ - mZ
                                val d = sqrt(fdx*fdx + fdz*fdz).coerceAtLeast(0.001f)
                                step(frontX(45f), frontZ(45f), 5.5f)
                                mAngle = faceDir(fdx, fdz)   // face the player for the slam
                                // Slam cycle: rear up, then crash flat toward the player.
                                slamT += 0.17f
                                val tilt = (sin(slamT.toDouble()).toFloat()).coerceAtLeast(0f) * 88f
                                mTilt = tilt; mTiltX = fdx / d; mTiltZ = fdz / d
                                if (tilt > 62f && d < 70f + mScale * 1.1f) killNow = true
                            }
                        } }

                        // Push transform to the renderer.
                        renderer.monsterX = mX; renderer.monsterZ = mZ; renderer.monsterAngle = mAngle
                        renderer.monsterScale = mScale; renderer.monsterYBob = mYBob
                        renderer.monsterTilt = mTilt; renderer.monsterTiltX = mTiltX; renderer.monsterTiltZ = mTiltZ
                        renderer.monsterActive = true
                        renderer.monsterClones = emptyList()

                        // Beep volume — swells quadratically as it nears. The rate stays
                        // constant; the attack itself is the tell, not a rising pitch.
                        val dRef = dist(mX, mZ, pX, pZ)
                        val near = (1f - dRef / 600f).coerceIn(0f, 1f)
                        val vol = 0.5f * near * near
                        beepPlayer.value?.let { bp ->
                            try { bp.setVolume(vol, vol) } catch (_: Throwable) {}
                        }

                        // ── City-levelling detonation (held a lamp circle too long) ──
                        if (explodeNow) {
                            beepPlayer.value?.let { try { it.setVolume(0f, 0f) } catch (_: Throwable) {} }
                            buzz(900L)
                            whiteFlash = 0.7f; delay(60)
                            whiteFlash = 0.3f; delay(60)
                            whiteFlash = 0.95f; delay(90)
                            whiteFlash = 0f
                            flashAlpha = 1f; delay(140)
                            flashAlpha = 0f
                            catchPhase = 3; delay(420)
                            // restart like a normal kill
                            var t3 = 0; var rx = pX; var rz = pZ
                            do { rx = frand(xBoundsMin, xBoundsMax); rz = frand(zMin, zMax); t3++ }
                            while (blocked(rx, rz) && t3 < 80)
                            pX = rx; pZ = rz; camYaw = frand(0f, 360f)
                            renderer.useLookAt = false
                            renderer.camX = pX; renderer.camY = eyeY; renderer.camZ = pZ
                            renderer.camYaw = camYaw; renderer.camPitch = 0f; renderer.fov = 82f
                            renderer.monsterActive = false; renderer.monsterClones = emptyList()
                            renderer.monsterScale = 13f; renderer.monsterYBob = 0f
                            renderer.monsterTilt = 0f; mTilt = 0f
                            mState = MS_ROAM; mScale = 13f; mYBob = 0f; freezeMs = 0L
                            monsterLock = false
                            mSpawned = false
                            hideUntil = System.currentTimeMillis() + 15_000L
                            catchPhase = 0
                        }

                        // ── Caught — the face is already huge in view (it reached the
                        // camera). Hold the moment, then reset. No dip to black.
                        if (killNow) {
                            beepPlayer.value?.let { try { it.setVolume(0f, 0f) } catch (_: Throwable) {} }
                            buzz(700L)
                            delay(750)
                            renderer.monsterActive = false
                            var t3 = 0; var rx = pX; var rz = pZ
                            do { rx = frand(xBoundsMin, xBoundsMax); rz = frand(zMin, zMax); t3++ }
                            while (blocked(rx, rz) && t3 < 80)
                            pX = rx; pZ = rz; camYaw = frand(0f, 360f)
                            renderer.useLookAt = false
                            renderer.camX = pX; renderer.camY = eyeY; renderer.camZ = pZ
                            renderer.camYaw = camYaw; renderer.camPitch = 0f; renderer.fov = 82f
                            renderer.monsterActive = false; renderer.monsterClones = emptyList()
                            renderer.monsterScale = 13f; renderer.monsterYBob = 0f
                            renderer.monsterTilt = 0f; mTilt = 0f
                            mState = MS_ROAM; mScale = 13f; mYBob = 0f; freezeMs = 0L
                            monsterLock = false
                            mSpawned = false                 // re-place elsewhere after the hide
                            hideUntil = System.currentTimeMillis() + 15_000L
                            catchPhase = 0
                        }
                    }
                }

                // ── Door proximity detection ──────────────────────────────────
                // Doors go inert while the monster is hunting — no hiding indoors.
                if (mState == MS_ROAM &&
                    doorPromptDigit == null && riddleDigit == null && orderBlockedDigit == null && doorOpeningDigit == null) {
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
                                // Out of order: the door stays shut. The player is told
                                // which building is owed, and never sees the riddle.
                                val need = missingPrereq(prefs, door.digit)
                                if (need != null) orderBlockedDigit = door.digit
                                else doorPromptDigit = door.digit
                            }
                            break
                        }
                    }

                    // RAD button (portrait: inside lava zone; landscape: behind wall through gap)
                    // Must match the renderer's addRadButton positions. The prompt sits
                    // just outside Building 10's doorway, which is offset west of due
                    // south (see RAD_DOOR_DEG).
                    val rbx = if (isLandscape) -CELL_V * 1.25f else 0f             // -350 / 0
                    val rbz = if (isLandscape) -CELL_V * 0.5f  else -CELL_V * 5.75f // -140 / -1610
                    val (radDoorX, radDoorZ) = radDoorApproach(rbx, rbz)
                    val rdSq = (pX - radDoorX) * (pX - radDoorX) + (pZ - radDoorZ) * (pZ - radDoorZ)
                    if (doorCooldownDigit == RAD_DIGIT && rdSq > 70f * 70f) doorCooldownDigit = null
                    // Once Building 10 is unlocked the player just walks in — no re-prompt.
                    if (!radDoorOpen && stopped && rdSq < 45f * 45f && doorCooldownDigit != RAD_DIGIT)
                        doorPromptDigit = RAD_DIGIT
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
                // Bridge arch: the eye rises up the ramp, stays level across the
                // middle, and comes back down — following the bridge over the lava.
                val bridgeRise = if (!isLandscape && bridgePieces >= 9 && pZ < LAVA_FRONT_Z) {
                    val lavaN = LAVA_FRONT_Z - 360f
                    val t = ((LAVA_FRONT_Z - pZ) / (LAVA_FRONT_Z - lavaN)).coerceIn(0f, 1f)  // 0 south → 1 north
                    val prof = when {
                        t < 0.18f -> t / 0.18f
                        t > 0.82f -> (1f - t) / 0.18f
                        else -> 1f
                    }
                    BRIDGE_PEAK_RISE * prof
                } else 0f
                val eyeTarget = (if (onSidewalk) CAM_EYE_H + SIDEWALK_BUMP else CAM_EYE_H) + bridgeRise
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

                if (mState == MS_LOCK) {
                    // Dart finale: shove the camera right up onto the monster's face.
                    val faceY = renderer.monsterFaceY
                    val toX = mX - pX; val toZ = mZ - pZ
                    val dl = sqrt(toX*toX + toZ*toZ).coerceAtLeast(0.001f)
                    val ux = toX/dl; val uz = toZ/dl
                    val e = zoomT*zoomT*(3f - 2f*zoomT)        // smoothstep
                    renderer.aerialMode = false
                    renderer.useLookAt  = true
                    renderer.camX = lerp(pX, mX - ux*55f, e)
                    renderer.camY = lerp(eyeY, faceY, e)
                    renderer.camZ = lerp(pZ, mZ - uz*55f, e)
                    renderer.lookAtX = mX; renderer.lookAtY = faceY; renderer.lookAtZ = mZ
                    renderer.fov = lerp(82f, 42f, e)
                } else {
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
                onJoy    = { x, y -> joyX = x; joyY = y },
                onTap    = {
                    // Five quick taps on the stick opens the (passcode-gated) debug
                    // menu. The run resets if the taps stop coming, so ordinary
                    // fidgeting can't accumulate into it.
                    val now = System.currentTimeMillis()
                    joyTapCount = if (now - joyLastTapMs < 900L) joyTapCount + 1 else 1
                    joyLastTapMs = now
                    if (joyTapCount >= 5) {
                        joyTapCount = 0
                        joyX = 0f; joyY = 0f
                        showDebugGate = true
                    }
                }
            )
        }

        // ── Sound credits ────────────────────────────────────────────────────
        // Over the opening fly-in, and only there: it is the one time the city is on
        // screen with nothing to do and nothing else to read. It fades out with the
        // last of the intro, as the player takes the controls.
        if (!introDone) {
            val creditsFade = (1f - ((intro.value - 0.80f) / 0.20f)).coerceIn(0f, 1f)
            if (creditsFade > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 34.dp, start = 24.dp, end = 24.dp)
                        .background(
                            Color.Black.copy(alpha = 0.32f * creditsFade),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Sounds: u_edtmwfwu7c, Jurij & freesound_community from Pixabay",
                        color = Color.White.copy(alpha = 0.82f * creditsFade),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Currency HUD — non-zero balances, top-left. Hidden during buildings,
        // the aerial cut and door sequences (same gate as the joystick).
        if (introDone && !overlayOpen && !forceAerial && doorOpeningDigit == null) {
            CityCurrencyHud(
                refreshKey = currencyRefresh,
                modifier = Modifier.align(Alignment.TopStart),
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

        // Monster catch — black during phases 1 & 3; phase 2 reveals the close-up.
        if (catchPhase == 1 || catchPhase == 3) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }

        // Street-widening cut flash (white)
        if (whiteFlash > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = whiteFlash))
            )
        }

        // ── "Complete building N first" message ───────────────────────────────
        if (!showTowerDefense && !forceAerial) {
            orderBlockedDigit?.let { digit ->
                OrderBlockedDialog(
                    digit = digit,
                    required = missingPrereq(prefs, digit) ?: digit,
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
                        // vo009 fires the moment they agree to enter building 7 — before
                        // the riddle — so it has time to play; vo010 inside the room waits
                        // for it to finish (see Building7VanityRoom).
                        if (digit == 7) {
                            com.fictioncutshort.justacalculator.logic.VoiceoverManager
                                .play(R.raw.vo009, cctv = true)
                        }
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
                        onSubmit      = { rating ->
                            riddleDigit = null
                            // Building 9's riddle asks how many fingers they'd give up;
                            // stash the answer so vo033/vo034 can fire during the flappy game.
                            if (digit == 9) building9Fingers = riddleInput.toIntOrNull() ?: 0
                            // Building 6's riddle ("how much is life worth?") gets a reply
                            // sized to the figure they put in: vo021 (0-1000), vo022
                            // (1001-1,000,000), vo023 (1,000,000+).
                            if (digit == 6) {
                                val worth = riddleInput.toLongOrNull() ?: 0L
                                val worthVo = when {
                                    worth <= 1_000L      -> R.raw.vo021
                                    worth <= 1_000_000L  -> R.raw.vo022
                                    else                 -> R.raw.vo023
                                }
                                com.fictioncutshort.justacalculator.logic.VoiceoverManager
                                    .play(worthVo, cctv = true)
                            }
                            riddleInput = ""
                            if (digit in 1..9) {
                                // Sliding door + walk-through is handled by the
                                // doorOpeningDigit effect below. It animates the
                                // door, moves the camera through it, and then
                                // launches the matching minigame (or just bumps
                                // entryProgress for non-minigame buildings).
                                doorOpeningDigit = digit
                            } else if (digit == RAD_DIGIT) {
                                // Building 10 — the rating unlocks the mute button's
                                // door; the effect below opens it and walks the
                                // player inside. The score itself is kept: the game
                                // asked how the city treated them, and takes the
                                // answer at face value when choosing the ending.
                                if (rating >= 0) {
                                    com.fictioncutshort.justacalculator.logic.ComplicityStore
                                        .recordRating(context, rating)
                                    // Their rating of the city gets a reply: vo035 (1-3),
                                    // vo036 (4-6), vo037 (7-10).
                                    val ratingVo = when {
                                        rating <= 3 -> R.raw.vo035
                                        rating <= 6 -> R.raw.vo036
                                        else        -> R.raw.vo037
                                    }
                                    com.fictioncutshort.justacalculator.logic.VoiceoverManager
                                        .play(ratingVo, cctv = true)
                                }
                                doorOpeningDigit = RAD_DIGIT
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
                    lastCompletedBuilding = 1
                    prefs.edit().putBoolean("td_b1_done", true).apply()
                    prefs.edit().putBoolean("completed_1", true).apply()
                    com.fictioncutshort.justacalculator.logic.BuildingProgress.clear(context, 1)
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
                    prefs.edit().putBoolean("completed_2", true).apply()
                    lastCompletedBuilding = 2
                    com.fictioncutshort.justacalculator.logic.BuildingProgress.clear(context, 2)
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
                    lastCompletedBuilding = 3
                    prefs.edit().putBoolean("td_b3_done", true).apply()
                    prefs.edit().putBoolean("completed_3", true).apply()
                    com.fictioncutshort.justacalculator.logic.BuildingProgress.clear(context, 3)
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
                    lastCompletedBuilding = 4
                    prefs.edit().putBoolean("completed_4", true).apply()
                    com.fictioncutshort.justacalculator.logic.BuildingProgress.clear(context, 4)
                    renderer.needsRebuild = true
                    doorCooldownDigit = 4
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                    forceAerial = true
                }
            )
        }

        // ── Door 5 — real-world walk; sound-scan runs at each arrived spot ────
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
                    b5Done = true   // arm the coins-lottery popup for this session
                    lastCompletedBuilding = 5
                    prefs.edit().putBoolean("completed_5", true).apply()
                    com.fictioncutshort.justacalculator.logic.BuildingProgress.clear(context, 5)
                    renderer.b5EntranceGlow = true   // light up Building 8's entrance
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
                    lastCompletedBuilding = 6
                    prefs.edit().putBoolean("completed_6", true).apply()
                    com.fictioncutshort.justacalculator.logic.BuildingProgress.clear(context, 6)
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
                    // Building 7 lays its bridge piece too (this was missing, so the
                    // 2nd plank never appeared over the lava after finishing it).
                    bridgePieces = (bridgePieces + 1).coerceAtMost(9)
                    renderer.bridgePieces = bridgePieces
                    prefs.edit().putInt("bridge_pieces", bridgePieces).apply()
                    prefs.edit().putBoolean("building_done_7", true).apply()
                    prefs.edit().putBoolean("completed_7", true).apply()
                    com.fictioncutshort.justacalculator.logic.BuildingProgress.clear(context, 7)
                    renderer.needsRebuild = true
                    doorCooldownDigit = 7
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                    // Building 7 has no bridge piece, but we still fly the aerial view so
                    // its post-completion narration (vo013→015) has somewhere to land.
                    lastCompletedBuilding = 7
                    forceAerial = true
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
                    lastCompletedBuilding = 8
                    prefs.edit().putBoolean("completed_8", true).apply()
                    com.fictioncutshort.justacalculator.logic.BuildingProgress.clear(context, 8)
                    // Completing Building 8 lays another bridge piece (aerial reveal).
                    bridgePieces = (bridgePieces + 1).coerceAtMost(9)
                    renderer.bridgePieces = bridgePieces
                    prefs.edit().putInt("bridge_pieces", bridgePieces).apply()
                    renderer.needsRebuild = true
                    doorCooldownDigit = 8
                    pX = if (isLandscape) PLAYER_START_X_L else PLAYER_START_X
                    pZ = if (isLandscape) PLAYER_START_Z_L else PLAYER_START_Z
                    camYaw = 0f
                    forceAerial = true
                },
                onExit = {
                    showBuilding8Casino = false
                    doorCooldownDigit = 8
                }
            )
        }

        // ── Coins lottery seed popup (after Building 5) ───────────────────────
        if (showCityLottery) {
            CityLotteryPopup(onDismiss = { showCityLottery = false; currencyRefresh++ })
        }

        // ── The end of the city ───────────────────────────────────────────────
        // Black-out over the collapse, then the story leaves the city entirely.
        if (endBlack > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = endBlack.coerceIn(0f, 1f)))
            )
        }

        // ── Debug menu (joystick ×5, then the passcode) ───────────────────────
        if (showDebugGate) {
            DebugPasswordGate(
                onUnlock = { showDebugGate = false; showDebugMenu = true },
                onCancel = { showDebugGate = false }
            )
        }
        if (showDebugMenu) {
            CityDebugMenu(
                prefs = prefs,
                // The city holds most of its state in `remember`ed variables, so a
                // write to prefs alone would not show until a restart. Pull the
                // whole lot back in, and tell the renderer to rebuild.
                onApply = {
                    towerDefenseCompleted = prefs.getBoolean("td_b1_done", false)
                    tankGameCompleted     = prefs.getBoolean("td_b3_done", false)
                    bridgePieces          = prefs.getInt("bridge_pieces", 0)
                    crossedBridge         = ONE_WAY_BRIDGE && prefs.getBoolean("bridge_crossed", false)
                    radDoorOpen           = prefs.getBoolean("b10_door_open", false)
                    entryProgress         = prefs.getInt("entry_progress", 0)
                    for (d in 1..9) {
                        renderer.buildingCompleted[d - 1] = prefs.getBoolean("building_done_$d", false)
                    }
                    renderer.bridgePieces   = bridgePieces
                    renderer.radDoorOpen    = radDoorOpen
                    renderer.b1DoorGreen    = prefs.getBoolean("td_b1_done", false)
                    renderer.b3DoorGreen    = prefs.getBoolean("td_b3_done", false)
                    renderer.b5EntranceGlow = prefs.getBoolean("td_b5_done", false)
                    renderer.needsRebuild   = true
                    currencyRefresh++
                },
                onClose = { showDebugMenu = false },
                onJumpToPhase1 = onJumpToPhase1?.let { jump ->
                    { chapter -> showDebugMenu = false; jump(chapter) }
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
                    prefs.edit().putBoolean("completed_9", true).apply()
                    com.fictioncutshort.justacalculator.logic.BuildingProgress.clear(context, 9)
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


// ── Currency HUD (top-left counters) ─────────────────────────────────────────
// Shows only the currencies whose balance is > 0, so a currency stays hidden
// until its building has been visited. Icons are still renders of the 3D
// currency models (see CurrencyIcon.kt). [refreshKey] re-reads balances after a
// building deposits.

@Composable
private fun CityCurrencyHud(refreshKey: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val balances = remember(refreshKey) { CurrencyStore.nonZero(context) }
    if (balances.isEmpty()) return

    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((currency, amount) in balances) {
            val icon = rememberCurrencyIcon(currency)
            Row(
                modifier = Modifier
                    .background(Color(0x88000000), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = currency.name,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    "$amount",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── Joystick (camera look + movement) ────────────────────────────────────────

@Composable
fun CityJoystick(
    modifier: Modifier = Modifier,
    joyDp: Dp = 114.dp,
    onJoy: (x: Float, y: Float) -> Unit,
    onTap: () -> Unit = {},
) {
    var sx by remember { mutableStateOf(0f) }
    var sy by remember { mutableStateOf(0f) }
    Box(modifier = modifier.size(joyDp)) {
        AndroidView(
            factory = { ctx ->
                object : android.view.View(ctx) {
                    // A "tap" is a press that neither lasted nor travelled: that
                    // keeps the debug shortcut from firing during normal steering,
                    // where the stick is held and dragged.
                    var downMs = 0L
                    var downX = 0f
                    var downY = 0f

                    override fun onTouchEvent(e: MotionEvent): Boolean {
                        val cx = width / 2f; val cy = height / 2f
                        when (e.action) {
                            MotionEvent.ACTION_DOWN -> {
                                downMs = System.currentTimeMillis()
                                downX = e.x; downY = e.y
                                sx = ((e.x - cx) / cx).coerceIn(-1f, 1f)
                                sy = ((e.y - cy) / cy).coerceIn(-1f, 1f)
                                onJoy(sx, sy)
                            }
                            MotionEvent.ACTION_MOVE -> {
                                sx = ((e.x - cx) / cx).coerceIn(-1f, 1f)
                                sy = ((e.y - cy) / cy).coerceIn(-1f, 1f)
                                onJoy(sx, sy)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                val held = System.currentTimeMillis() - downMs
                                val moved = kotlin.math.hypot(e.x - downX, e.y - downY)
                                if (e.action == MotionEvent.ACTION_UP &&
                                    held < 260L && moved < 24f) onTap()
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
private fun OrderBlockedDialog(digit: Int, required: Int, onDismiss: () -> Unit) {
    val prereq = "building $required"
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
    // For the mute button (RAD_DIGIT) this carries the 0..10 rating the player
    // gave the city; every other riddle passes -1. The rating is the last of the
    // two phase-2 choices that decide the ending (see ComplicityStore).
    onSubmit: (rating: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var wrongFlash by remember { mutableStateOf(false) }
    val ywdState   = remember { mutableStateListOf("", "", "", "", "", "", "") }
    var ywdActive  by remember { mutableIntStateOf(0) }
    var ywdTick    by remember { mutableIntStateOf(0) }
    // Time-based auto-advance: a short pause after the last digit jumps to the
    // next field, so a single-digit entry moves on without waiting for a second.
    LaunchedEffect(ywdTick) {
        if (ywdTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        if (ywdActive < 6 && ywdState[ywdActive].isNotEmpty()) ywdActive += 1
    }
    var showHint   by remember { mutableStateOf(false) }
    // Mute-button rating slider (0..10).
    var sliderVal  by remember { mutableStateOf(5f) }
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
                    // Deleting on an empty field steps back to the previous one.
                    if (ywdState[ywdActive].isEmpty() && ywdActive > 0) ywdActive -= 1
                    ywdState[ywdActive] = ywdState[ywdActive].dropLast(1)
                } else {
                    if (ywdState[ywdActive].length < 2) ywdState[ywdActive] += key
                    // A full two-digit field jumps immediately; otherwise a short
                    // pause after the last keypress advances (see the effect below).
                    if (ywdState[ywdActive].length >= 2 && ywdActive < 6) ywdActive += 1
                    else ywdTick += 1
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
                if (input == riddle.answer) onSubmit(-1)
                else wrongFlash = true
            }
            AnswerType.RANGE -> {
                val v = input.toIntOrNull()
                val lo = riddle.rangeMin ?: Int.MIN_VALUE
                val hi = riddle.rangeMax ?: Int.MAX_VALUE
                if (v != null && v in lo..hi) onSubmit(-1)
                else wrongFlash = true
            }
            AnswerType.TIME_24H -> {
                val d = input.filter { it.isDigit() }
                val hh = d.take(2).toIntOrNull()
                val mm = d.drop(2).take(2).toIntOrNull()
                if (d.length >= 4 && hh != null && mm != null && hh in 0..23 && mm in 0..59) {
                    val cal = java.util.Calendar.getInstance()
                    val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                        cal.get(java.util.Calendar.MINUTE)
                    val ansMin = hh * 60 + mm
                    var diff = kotlin.math.abs(nowMin - ansMin)
                    diff = minOf(diff, 24 * 60 - diff)   // wrap across midnight
                    if (diff <= 3) onSubmit(-1) else wrongFlash = true
                } else wrongFlash = true
            }
            // OPEN covers the mute button's slider, which is always accepted -
            // the point is what they answered, not whether it was 'right'.
            AnswerType.OPEN,
            AnswerType.YWDHMS -> onSubmit(Math.round(sliderVal))
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

            // Input area — the mute button uses a 0..10 slider, not the keypad.
            if (digit == RAD_DIGIT) {
                Text("${Math.round(sliderVal)}  /  10", color = Color(0xFF33FF66), fontSize = 34.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Slider(
                    value = sliderVal, onValueChange = { sliderVal = it },
                    valueRange = 0f..10f, steps = 9,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Color(0xFF33FF66),
                        activeTrackColor = Color(0xFF33AA55),
                        inactiveTrackColor = Color(0xFF224422),
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                )
            } else {
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

            // Building 7 ("vanity") entry disclosure — deliberately small and
            // low-contrast, tucked at the very bottom under the keypad. By
            // entering the door the player consents to being photographed inside
            // and to those images being reused elsewhere in the app.
            if (digit == 7) {
                Text(
                    "By entering you consent to being photographed inside and to those images being used elsewhere in this app.",
                    color = Color(0xFF4D4D4D),
                    fontSize = 7.sp,
                    lineHeight = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)
                )
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
