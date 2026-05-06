package com.fictioncutshort.justacalculator.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip

// ─────────────────────────────────────────────────────────────────────────────
// WORLD LAYOUT  —  PLUS / CROSS shape (parameterized by level)
// ─────────────────────────────────────────────────────────────────────────────
//
//              [Room N]
//                 |
//   [Room W] -- [Hub] -- [Room E]
//                 |
//              [Room S]
//
// Each RED trap triggers a full maze regeneration at the next level:
//   level 0: roomSize=15  level 1: roomSize=19  level 2: roomSize=23 ...
//
// Traps — two kinds placed in every area:
//   RED    : completely rebuilds the maze bigger and more complex + screen flicker
//   ORANGE : dims the scene (spotlight shrinks around player)

private data class MazeLayout(val level: Int) {
    val roomSize: Int  = 15 + level * 4          // 15, 19, 23, 27 …
    val rCells:   Int  = (roomSize - 1) / 2
    val hubW:     Int  = maxOf(11, roomSize - 4)
    val hubH:     Int  = hubW
    val corr:     Int  = 2
    val corrW:    Int  = 1
    val hubR0:    Int  = 2 + roomSize + corr
    val hubC0:    Int  = 2 + roomSize + corr
    val hubMidR:  Int  = hubR0 + hubH / 2
    val hubMidC:  Int  = hubC0 + hubW / 2
    val roomNR:   Int  = 2
    val roomNC:   Int  = hubMidC - roomSize / 2
    val roomSR:   Int  = hubR0 + hubH + corr
    val roomSC:   Int  = roomNC
    val roomWR:   Int  = hubMidR - roomSize / 2
    val roomWC:   Int  = 2
    val roomER:   Int  = roomWR
    val roomEC:   Int  = hubC0 + hubW + corr
    val wgridRows: Int = roomSR + roomSize + 2
    val wgridCols: Int = roomEC + roomSize + 2
    val hubKeySlots: List<Pair<Int, Int>> = listOf(
        (hubR0 + 2)        to (hubC0 + 2),
        (hubR0 + 2)        to (hubC0 + hubW - 3),
        (hubR0 + hubH / 2) to (hubC0 + hubW / 2),
        (hubR0 + hubH - 3) to (hubC0 + 2),
        (hubR0 + hubH - 3) to (hubC0 + hubW - 3),
    )
}

// ── Colours ───────────────────────────────────────────────────────────────────
private val MC_BG          = Color(0xFFBBB8B0)
private val MC_FLOOR       = Color(0xFF000000)
private val MC_WALL_TOP    = Color(0xFFEEECE8)
private val MC_WALL_LEFT   = Color(0xFFBBB8B0)
private val MC_WALL_RIGHT  = Color(0xFF888480)
private val MC_BALL        = Color(0xFFE8C060)
private val MC_BALL_RIM    = Color(0xFF7A5500)
private val MC_BALL_SHINE  = Color(0xCCFFFFFF)
private val MC_BALL_SHADOW = Color(0x33000000)

// ─────────────────────────────────────────────────────────────────────────────
// APPEARANCE CONFIGURATION — edit these to customise every key and trap
// ─────────────────────────────────────────────────────────────────────────────

/** Shape of the key's bow (the ring at the top). */
private enum class BowStyle {
    RING,         // hollow circle outline
    FILLED,       // solid filled circle with inner dot
    DOUBLE_RING,  // two concentric ring outlines
}

/**
 * A single tooth on a key's shaft.
 * @param shaftFraction  0.0 = very top of shaft, 1.0 = very bottom
 * @param toothLength    1.0 = default length; larger = longer tooth
 */
private data class KeyTooth(
    val shaftFraction: Float,
    val toothLength: Float = 1.0f,
)

/**
 * Full appearance for one real key.
 * There are 5 keys (id 0-4); define one entry per key.
 *
 * @param tint        Key colour
 * @param bowRadius   1.0 = default bow size
 * @param bowStyle    RING / FILLED / DOUBLE_RING
 * @param shaftScale  1.0 = default shaft length
 * @param teeth       List of teeth, each with a shaft position and optional length
 */
private data class KeyAppearance(
    val tint: Color,
    val bowRadius: Float = 1.0f,
    val bowStyle: BowStyle = BowStyle.RING,
    val shaftScale: Float = 1.0f,
    val teeth: List<KeyTooth> = listOf(KeyTooth(0.45f), KeyTooth(0.75f)),
    /**
     * Optional .glb model file path relative to app assets/.
     * Example: "models/keys/key_blue.glb"
     * Place your exported Blender files in app/src/main/assets/models/keys/
     * When set, rolling near this key shows a 3D examine overlay before collecting.
     * When null, the key is collected instantly on contact (canvas-drawn fallback).
     */
    val modelFile: String? = null,
    /**
     * Optional PNG sprite path relative to app assets/ (e.g. "sprites/keys/MazeKey1.png").
     * Render from Blender at X=63.44°, Y=0°, Z=45°, Orthographic, Transparent background.
     * When set the sprite is drawn directly on the Canvas with correct depth ordering.
     */
    val spriteFile: String? = null,
)

/**
 * Appearance for a red-trap decoy key.
 * Up to 10 red traps appear per level; entries are cycled if fewer than 10 are defined.
 */
private data class DecoyKeyAppearance(
    val tint: Color,
    val bowRadius: Float = 1.0f,
    val bowStyle: BowStyle = BowStyle.RING,
    val shaftScale: Float = 1.0f,
    val teeth: List<KeyTooth> = listOf(KeyTooth(0.55f)),
    /**
     * Optional .glb model file path relative to app assets/.
     * Example: "models/decoys/decoy_0.glb"
     * Place your exported Blender files in app/src/main/assets/models/decoys/
     * When set, rolling near this decoy shows the 3D examine overlay (same UI as real keys —
     * the player must judge by shape whether it's genuine). On accept, the red-trap effect fires.
     * When null, the decoy triggers instantly on contact.
     */
    val modelFile: String? = null,
    /** PNG sprite — same convention as KeyAppearance.spriteFile. */
    val spriteFile: String? = null,
)

/**
 * Appearance for an orange floor-trap tile.
 * Up to 10 orange traps appear per level; entries are cycled if fewer are defined.
 *
 * @param floorColor  Replace MC_FLOOR with this colour for this tile
 * @param showEdge    Draw a subtle diamond edge outline on top
 * @param edgeColor   Colour of that edge outline
 */
private data class FloorTrapAppearance(
    val floorColor: Color,
    val showEdge: Boolean = false,
    val edgeColor: Color = Color(0xFF1A0A00),
)

// ── Real keys (id 0-4) ───────────────────────────────────────────────────────
private val KEY_APPEARANCES: List<KeyAppearance> = listOf(
    /* 0 */ KeyAppearance(Color(0xFF00AAFF), bowStyle = BowStyle.RING,        teeth = listOf(KeyTooth(0.45f), KeyTooth(0.75f)), modelFile = "models/keys/MazeKey1.glb",     spriteFile = "sprites/keys/MazeKey1.png"),
    /* 1 */ KeyAppearance(Color(0xFFFF6600), shaftScale = 1.30f,              teeth = listOf(KeyTooth(0.30f), KeyTooth(0.55f), KeyTooth(0.78f)),             modelFile = "models/keys/MazeKeyv1.glb",    spriteFile = "sprites/keys/MazeKeyv1.png"),
    /* 2 */ KeyAppearance(Color(0xFF22DD77), bowRadius = 1.30f, bowStyle = BowStyle.FILLED, teeth = listOf(KeyTooth(0.50f)),                                 modelFile = "models/keys/MazeKeyvv1.glb",   spriteFile = "sprites/keys/MazeKeyvv1.png"),
    /* 3 */ KeyAppearance(Color(0xFFCC44FF), bowStyle = BowStyle.DOUBLE_RING, teeth = listOf(KeyTooth(0.40f), KeyTooth(0.70f)),                              modelFile = "models/keys/MazeKeyvvv1.glb",  spriteFile = "sprites/keys/MazeKeyvvv1.png"),
    /* 4 */ KeyAppearance(Color(0xFFFFDD00), bowRadius = 0.75f, shaftScale = 0.85f, teeth = listOf(KeyTooth(0.55f)),                                        modelFile = "models/keys/MazeKeyvvvv1.glb", spriteFile = "sprites/keys/MazeKeyvvvv1.png"),
)

// ── Red-trap decoy keys (16 files — everything that isn't a *1 file) ─────────
private val DECOY_KEY_APPEARANCES: List<DecoyKeyAppearance> = listOf(
    /* 00 */ DecoyKeyAppearance(Color(0xFF991111), bowRadius = 0.85f,                              teeth = listOf(KeyTooth(0.65f)),              modelFile = "models/keys/MazeKey2.glb",      spriteFile = "sprites/keys/MazeKey2.png"),
    /* 01 */ DecoyKeyAppearance(Color(0xFFBB2222), bowRadius = 1.10f, bowStyle = BowStyle.FILLED,  teeth = listOf(KeyTooth(0.40f)),              modelFile = "models/keys/MazeKey3.glb",      spriteFile = "sprites/keys/MazeKey3.png"),
    /* 02 */ DecoyKeyAppearance(Color(0xFF881133), shaftScale = 1.20f,                             teeth = listOf(KeyTooth(0.30f), KeyTooth(0.60f)), modelFile = "models/keys/MazeKeyv2.glb", spriteFile = "sprites/keys/MazeKeyv2.png"),
    /* 03 */ DecoyKeyAppearance(Color(0xFFAA1144), bowStyle = BowStyle.DOUBLE_RING,                teeth = listOf(KeyTooth(0.70f)),              modelFile = "models/keys/MazeKeyv3.glb",     spriteFile = "sprites/keys/MazeKeyv3.png"),
    /* 04 */ DecoyKeyAppearance(Color(0xFFCC1100), bowRadius = 0.70f, shaftScale = 0.80f,          teeth = listOf(KeyTooth(0.50f)),              modelFile = "models/keys/MazeKeyvv2.glb",    spriteFile = "sprites/keys/MazeKeyvv2.png"),
    /* 05 */ DecoyKeyAppearance(Color(0xFF991122), bowRadius = 0.90f, bowStyle = BowStyle.FILLED,  teeth = listOf(KeyTooth(0.35f), KeyTooth(0.65f)), modelFile = "models/keys/MazeKeyvv3.glb", spriteFile = "sprites/keys/MazeKeyvv3.png"),
    /* 06 */ DecoyKeyAppearance(Color(0xFFBB1133), shaftScale = 1.15f,                             teeth = listOf(KeyTooth(0.45f)),              modelFile = "models/keys/MazeKeyvv4.glb",    spriteFile = "sprites/keys/MazeKeyvv4.png"),
    /* 07 */ DecoyKeyAppearance(Color(0xFF880011), bowStyle = BowStyle.RING,                       teeth = listOf(KeyTooth(0.55f), KeyTooth(0.80f)), modelFile = "models/keys/MazeKeyvv5.glb", spriteFile = "sprites/keys/MazeKeyvv5.png"),
    /* 08 */ DecoyKeyAppearance(Color(0xFFAA2200), bowRadius = 1.05f,                              teeth = listOf(KeyTooth(0.42f)),              modelFile = "models/keys/MazeKeyvvv2.glb",   spriteFile = "sprites/keys/MazeKeyvvv2.png"),
    /* 09 */ DecoyKeyAppearance(Color(0xFFCC2211), bowRadius = 0.80f, bowStyle = BowStyle.DOUBLE_RING, teeth = listOf(KeyTooth(0.60f)),          modelFile = "models/keys/MazeKeyvvv3.glb",   spriteFile = "sprites/keys/MazeKeyvvv3.png"),
    /* 10 */ DecoyKeyAppearance(Color(0xFF991111), bowRadius = 0.95f,                              teeth = listOf(KeyTooth(0.48f)),              modelFile = "models/keys/MazeKeyvvv4.glb",   spriteFile = "sprites/keys/MazeKeyvvv4.png"),
    /* 11 */ DecoyKeyAppearance(Color(0xFFBB1100), shaftScale = 1.10f, bowStyle = BowStyle.FILLED, teeth = listOf(KeyTooth(0.38f)),              modelFile = "models/keys/MazeKeyvvv5.glb",   spriteFile = "sprites/keys/MazeKeyvvv5.png"),
    /* 12 */ DecoyKeyAppearance(Color(0xFF881122), bowRadius = 0.75f,                              teeth = listOf(KeyTooth(0.55f), KeyTooth(0.75f)), modelFile = "models/keys/MazeKeyvvvv2.glb", spriteFile = "sprites/keys/MazeKeyvvvv2.png"),
    /* 13 */ DecoyKeyAppearance(Color(0xFFAA1100), bowStyle = BowStyle.DOUBLE_RING, shaftScale = 1.05f, teeth = listOf(KeyTooth(0.45f)),         modelFile = "models/keys/MazeKeyvvvv3.glb",  spriteFile = "sprites/keys/MazeKeyvvvv3.png"),
    /* 14 */ DecoyKeyAppearance(Color(0xFFCC1122), bowRadius = 1.15f,                              teeth = listOf(KeyTooth(0.32f), KeyTooth(0.62f)), modelFile = "models/keys/MazeKeyvvvv4.glb", spriteFile = "sprites/keys/MazeKeyvvvv4.png"),
    /* 15 */ DecoyKeyAppearance(Color(0xFF991133), bowRadius = 0.88f, bowStyle = BowStyle.FILLED,  teeth = listOf(KeyTooth(0.58f)),              modelFile = "models/keys/MazeKeyvvvv5.glb",  spriteFile = "sprites/keys/MazeKeyvvvv5.png"),
)

// ── Key → decoy-slot grouping (by name prefix) ───────────────────────────────
// Key 0 (MazeKey1)     → decoy slots  0,  1  (MazeKey2, MazeKey3)
// Key 1 (MazeKeyv1)    → decoy slots  2,  3  (MazeKeyv2, MazeKeyv3)
// Key 2 (MazeKeyvv1)   → decoy slots  4–7    (MazeKeyvv2–5)
// Key 3 (MazeKeyvvv1)  → decoy slots  8–11   (MazeKeyvvv2–5)
// Key 4 (MazeKeyvvvv1) → decoy slots 12–15   (MazeKeyvvvv2–5)
private val KEY_GROUP_DECOY_SLOTS: Map<Int, Set<Int>> = mapOf(
    0 to setOf(0, 1),
    1 to setOf(2, 3),
    2 to setOf(4, 5, 6, 7),
    3 to setOf(8, 9, 10, 11),
    4 to setOf(12, 13, 14, 15),
)

// ── Orange floor-trap tiles ───────────────────────────────────────────────────
// Warm amber floor + bold orange diamond outline — clearly spottable but still
// dark enough to fit the dungeon aesthetic.
private val FLOOR_TRAP_APPEARANCES: List<FloorTrapAppearance> = listOf(
    FloorTrapAppearance(Color(0xFF2E1504), showEdge = true, edgeColor = Color(0xFFB05A14)),
    FloorTrapAppearance(Color(0xFF2A1203), showEdge = true, edgeColor = Color(0xFFA85216)),
    FloorTrapAppearance(Color(0xFF321705), showEdge = true, edgeColor = Color(0xFFB86018)),
    FloorTrapAppearance(Color(0xFF281002), showEdge = true, edgeColor = Color(0xFFA04E12)),
    FloorTrapAppearance(Color(0xFF301604), showEdge = true, edgeColor = Color(0xFFAC5814)),
    FloorTrapAppearance(Color(0xFF2C1303), showEdge = true, edgeColor = Color(0xFFA45416)),
    FloorTrapAppearance(Color(0xFF2E1403), showEdge = true, edgeColor = Color(0xFFB05C14)),
    FloorTrapAppearance(Color(0xFF291102), showEdge = true, edgeColor = Color(0xFFA25010)),
    FloorTrapAppearance(Color(0xFF311604), showEdge = true, edgeColor = Color(0xFFB45E16)),
    FloorTrapAppearance(Color(0xFF2B1303), showEdge = true, edgeColor = Color(0xFFA6520E)),
)

// ─────────────────────────────────────────────────────────────────────────────
// TRAP / KEY DATA
// ─────────────────────────────────────────────────────────────────────────────

private enum class TrapType { RED, ORANGE }

private data class MazeTrap(
    val id: Int, val row: Int, val col: Int,
    val type: TrapType, var collected: Boolean = false,
    val articleNum: Int = 0,  // only meaningful for ORANGE traps (1–8)
)

private data class MazeKey(val id: Int, val row: Int, val col: Int, var collected: Boolean = false)

// ─────────────────────────────────────────────────────────────────────────────
// MAZE GENERATION — single room + guaranteed exit tunnels
// ─────────────────────────────────────────────────────────────────────────────

private fun buildRoom(
    seed: Int,
    roomSize: Int,
    rCells: Int,
    circular: Boolean,
    exitSouth: Boolean = false,
    exitNorth: Boolean = false,
    exitEast:  Boolean = false,
    exitWest:  Boolean = false,
): Array<IntArray> {
    val grid = Array(roomSize) { IntArray(roomSize) { 0 } }
    val rng = Random(seed)
    val visited = Array(rCells) { BooleanArray(rCells) }

    fun tile(lr: Int, lc: Int) = (1 + lr * 2) to (1 + lc * 2)

    fun masked(lr: Int, lc: Int): Boolean {
        if (!circular) return false
        val (tr, tc) = tile(lr, lc)
        val cx = roomSize / 2f; val cy = roomSize / 2f
        return sqrt((tr - cx).pow(2) + (tc - cy).pow(2)) > cx - 1.5f
    }

    for (lr in 0 until rCells) for (lc in 0 until rCells)
        if (!masked(lr, lc)) { val (tr, tc) = tile(lr, lc); grid[tr][tc] = 1 }

    fun carve(lr: Int, lc: Int) {
        visited[lr][lc] = true
        for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).shuffled(rng)) {
            val nlr = lr + dr; val nlc = lc + dc
            if (nlr !in 0 until rCells || nlc !in 0 until rCells) continue
            if (visited[nlr][nlc] || masked(nlr, nlc)) continue
            val (tr, tc) = tile(lr, lc)
            val wr = tr + dr; val wc = tc + dc
            if (wr !in 0 until roomSize || wc !in 0 until roomSize) continue
            grid[wr][wc] = 1
            carve(nlr, nlc)
        }
    }

    var startLr = rCells / 2; var startLc = rCells / 2
    outer@ for (lr in 0 until rCells) for (lc in 0 until rCells)
        if (!masked(lr, lc)) { startLr = lr; startLc = lc; break@outer }
    carve(startLr, startLc)

    for (lr in 0 until rCells) for (lc in 0 until rCells) {
        if (visited[lr][lc] || masked(lr, lc)) continue
        for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).shuffled(rng)) {
            val nlr = lr + dr; val nlc = lc + dc
            if (nlr !in 0 until rCells || nlc !in 0 until rCells) continue
            if (!visited[nlr][nlc] || masked(nlr, nlc)) continue
            val (tr, tc) = tile(lr, lc)
            val wr = tr + dr; val wc = tc + dc
            if (wr !in 0 until roomSize || wc !in 0 until roomSize) continue
            grid[wr][wc] = 1; carve(lr, lc); break
        }
    }

    repeat(10) {
        val lr = rng.nextInt(rCells - 1); val lc = rng.nextInt(rCells - 1)
        if (!masked(lr, lc) && !masked(lr + 1, lc)) {
            val (tr, tc) = tile(lr, lc)
            if (tr + 1 < roomSize) grid[tr + 1][tc] = 1
        }
    }

    // Carve 3-wide exit tunnels (4 tiles deep) so corridors always connect
    val half = roomSize / 2
    if (exitSouth) for (r in maxOf(0, roomSize-4) until roomSize) for (dc in -1..1) grid[r][(half+dc).coerceIn(0,roomSize-1)] = 1
    if (exitNorth) for (r in 0..minOf(3, roomSize-1))             for (dc in -1..1) grid[r][(half+dc).coerceIn(0,roomSize-1)] = 1
    if (exitEast)  for (c in maxOf(0, roomSize-4) until roomSize) for (dr in -1..1) grid[(half+dr).coerceIn(0,roomSize-1)][c] = 1
    if (exitWest)  for (c in 0..minOf(3, roomSize-1))             for (dr in -1..1) grid[(half+dr).coerceIn(0,roomSize-1)][c] = 1

    return grid
}

// ─────────────────────────────────────────────────────────────────────────────
// WORLD GRID ASSEMBLY
// ─────────────────────────────────────────────────────────────────────────────

private fun buildWorldGrid(layout: MazeLayout, seed: Int = 0): Array<IntArray> {
    val world = Array(layout.wgridRows) { IntArray(layout.wgridCols) { 0 } }

    fun paste(room: Array<IntArray>, originR: Int, originC: Int) {
        for (r in room.indices) for (c in room[r].indices) {
            val wr = originR + r; val wc = originC + c
            if (wr in world.indices && wc in world[wr].indices) world[wr][wc] = room[r][c]
        }
    }

    paste(buildRoom(seed+1001, layout.roomSize, layout.rCells, circular = false, exitSouth = true), layout.roomNR, layout.roomNC)
    paste(buildRoom(seed+2002, layout.roomSize, layout.rCells, circular = false, exitNorth = true), layout.roomSR, layout.roomSC)
    paste(buildRoom(seed+3003, layout.roomSize, layout.rCells, circular = true,  exitEast  = true), layout.roomWR, layout.roomWC)
    paste(buildRoom(seed+4004, layout.roomSize, layout.rCells, circular = true,  exitWest  = true), layout.roomER, layout.roomEC)

    // Hub: fully open
    for (r in layout.hubR0 until layout.hubR0 + layout.hubH)
        for (c in layout.hubC0 until layout.hubC0 + layout.hubW)
            world[r][c] = 1

    // Corridors (3 tiles wide)
    for (r in layout.roomNR + layout.roomSize until layout.hubR0)
        for (dc in -layout.corrW..layout.corrW)
            world[r][(layout.hubMidC + dc).coerceIn(0, layout.wgridCols-1)] = 1
    for (r in layout.hubR0 + layout.hubH until layout.roomSR)
        for (dc in -layout.corrW..layout.corrW)
            world[r][(layout.hubMidC + dc).coerceIn(0, layout.wgridCols-1)] = 1
    for (c in layout.roomWC + layout.roomSize until layout.hubC0)
        for (dr in -layout.corrW..layout.corrW)
            world[(layout.hubMidR + dr).coerceIn(0, layout.wgridRows-1)][c] = 1
    for (c in layout.hubC0 + layout.hubW until layout.roomEC)
        for (dr in -layout.corrW..layout.corrW)
            world[(layout.hubMidR + dr).coerceIn(0, layout.wgridRows-1)][c] = 1

    return world
}

// ─────────────────────────────────────────────────────────────────────────────
// KEY PLACEMENT
// ─────────────────────────────────────────────────────────────────────────────

private fun placeKeys(world: Array<IntArray>, layout: MazeLayout, seed: Int = 88812): List<MazeKey> {
    val rng = Random(seed)
    val regions = listOf(
        (layout.roomNR until layout.roomNR + layout.roomSize).flatMap { r -> (layout.roomNC until layout.roomNC + layout.roomSize).map { c -> r to c } },
        (layout.roomSR until layout.roomSR + layout.roomSize).flatMap { r -> (layout.roomSC until layout.roomSC + layout.roomSize).map { c -> r to c } },
        (layout.roomWR until layout.roomWR + layout.roomSize).flatMap { r -> (layout.roomWC until layout.roomWC + layout.roomSize).map { c -> r to c } },
        (layout.roomER until layout.roomER + layout.roomSize).flatMap { r -> (layout.roomEC until layout.roomEC + layout.roomSize).map { c -> r to c } },
        (layout.hubR0  until layout.hubR0  + layout.hubH).flatMap    { r -> (layout.hubC0  until layout.hubC0  + layout.hubW).map  { c -> r to c } },
    )
    return regions.mapIndexed { i, region ->
        val candidates = region.filter { (r, c) ->
            r in world.indices && c in world[r].indices && world[r][c] == 1
        }
        val (r, c) = candidates[rng.nextInt(candidates.size)]
        MazeKey(i, r, c)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TRAP PLACEMENT  (2 red + 2 orange per area; 1 each in hub)
// ─────────────────────────────────────────────────────────────────────────────

private fun placeTraps(
    world: Array<IntArray>,
    layout: MazeLayout,
    keyPositions: Set<Pair<Int, Int>>,
    seed: Int = 55577,
    unusedArticles: List<Int> = (1..8).toList(),
): List<MazeTrap> {
    val rng = Random(seed)
    val traps = mutableListOf<MazeTrap>()
    var id = 0
    var articleIdx = 0  // next article to assign from unusedArticles

    val regions = listOf(
        (layout.roomNR until layout.roomNR + layout.roomSize).flatMap { r -> (layout.roomNC until layout.roomNC + layout.roomSize).map { c -> r to c } },
        (layout.roomSR until layout.roomSR + layout.roomSize).flatMap { r -> (layout.roomSC until layout.roomSC + layout.roomSize).map { c -> r to c } },
        (layout.roomWR until layout.roomWR + layout.roomSize).flatMap { r -> (layout.roomWC until layout.roomWC + layout.roomSize).map { c -> r to c } },
        (layout.roomER until layout.roomER + layout.roomSize).flatMap { r -> (layout.roomEC until layout.roomEC + layout.roomSize).map { c -> r to c } },
        (layout.hubR0  until layout.hubR0  + layout.hubH).flatMap    { r -> (layout.hubC0  until layout.hubC0  + layout.hubW).map  { c -> r to c } },
    )

    for (region in regions) {
        val candidates = region
            .filter { (r, c) ->
                r in world.indices && c in world[r].indices
                    && world[r][c] == 1
                    && (r to c) !in keyPositions
            }
            .shuffled(rng)

        val half = candidates.size / 2
        val perType = if (region.size > 100) 2 else 1   // hub gets 1 of each

        val redPool    = candidates.take(half)
        val orangePool = candidates.drop(half)

        repeat(perType) { idx ->
            if (idx < redPool.size)
                traps.add(MazeTrap(id++, redPool[idx].first, redPool[idx].second, TrapType.RED))
            // Place orange trap only if we still have unseen articles to assign
            if (idx < orangePool.size && articleIdx < unusedArticles.size)
                traps.add(MazeTrap(id++, orangePool[idx].first, orangePool[idx].second, TrapType.ORANGE,
                    articleNum = unusedArticles[articleIdx++]))
        }
    }

    return traps
}

// ─────────────────────────────────────────────────────────────────────────────
// FIND STARTING POSITION  (first open floor tile in Room N)
// ─────────────────────────────────────────────────────────────────────────────

private fun findStart(world: Array<IntArray>, layout: MazeLayout): Pair<Float, Float> {
    for (r in layout.roomNR + 1 until layout.roomNR + layout.roomSize)
        for (c in layout.roomNC + 1 until layout.roomNC + layout.roomSize)
            if (world[r][c] == 1) return (r + 0.5f) to (c + 0.5f)
    return (layout.roomNR + 1.5f) to (layout.roomNC + 1.5f)
}

// ─────────────────────────────────────────────────────────────────────────────
// ISO DRAW HELPERS
// ─────────────────────────────────────────────────────────────────────────────

private const val WH_FACTOR = 0.50f

private fun DrawScope.isoFloor(r: Float, c: Float, ts: Float, camX: Float, camY: Float, color: Color) {
    val half = ts / 2f; val quat = ts / 4f
    val cx = (c - r) * half - camX; val cy = (c + r) * quat - camY
    drawPath(Path().apply {
        moveTo(cx, cy - quat); lineTo(cx + half, cy); lineTo(cx, cy + quat); lineTo(cx - half, cy); close()
    }, color)
}

private fun DrawScope.isoWallSouth(r: Float, c: Float, ts: Float, camX: Float, camY: Float) {
    val half = ts / 2f; val quat = ts / 4f; val wh = ts * WH_FACTOR
    val cx = (c - r) * half - camX; val cy = (c + r) * quat - camY
    drawPath(Path().apply {
        moveTo(cx, cy + quat); lineTo(cx - half, cy)
        lineTo(cx - half, cy - wh); lineTo(cx, cy + quat - wh); close()
    }, MC_WALL_LEFT)
}

private fun DrawScope.isoWallEast(r: Float, c: Float, ts: Float, camX: Float, camY: Float) {
    val half = ts / 2f; val quat = ts / 4f; val wh = ts * WH_FACTOR
    val cx = (c - r) * half - camX; val cy = (c + r) * quat - camY
    drawPath(Path().apply {
        moveTo(cx, cy + quat); lineTo(cx + half, cy)
        lineTo(cx + half, cy - wh); lineTo(cx, cy + quat - wh); close()
    }, MC_WALL_RIGHT)
}

private fun DrawScope.isoWallTop(r: Float, c: Float, ts: Float, camX: Float, camY: Float) {
    val half = ts / 2f; val quat = ts / 4f; val wh = ts * WH_FACTOR
    val cx = (c - r) * half - camX; val cy = (c + r) * quat - camY
    drawPath(Path().apply {
        moveTo(cx, cy - quat - wh); lineTo(cx + half, cy - wh)
        lineTo(cx, cy + quat - wh); lineTo(cx - half, cy - wh); close()
    }, MC_WALL_TOP)
}

/** Draw a pre-rendered PNG sprite centred on its isometric tile position.
 *  The sprite is drawn at its natural aspect ratio, scaled so its height
 *  equals ~1.1× the tile size, and bottom-anchored to the tile surface.
 */
private fun DrawScope.isoSprite(
    r: Float, c: Float, ts: Float, camX: Float, camY: Float,
    bitmap: androidx.compose.ui.graphics.ImageBitmap, alpha: Float = 1f,
) {
    val half = ts / 2f; val quat = ts / 4f
    val cx = (c - r) * half - camX
    val cy = (c + r) * quat - camY
    // Bottom of the sprite sits at the top face of the tile (cy - quat)
    val targetH = ts * 0.70f
    val aspect  = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    val targetW = targetH * aspect
    drawImage(
        image     = bitmap,
        dstOffset = androidx.compose.ui.unit.IntOffset(
            (cx - targetW / 2f).toInt(),
            (cy - quat - targetH).toInt()
        ),
        dstSize   = androidx.compose.ui.unit.IntSize(targetW.toInt(), targetH.toInt()),
        alpha     = alpha,
    )
}

private fun DrawScope.isoBall(r: Float, c: Float, ts: Float, camX: Float, camY: Float) {
    val half = ts / 2f; val quat = ts / 4f
    val cx = (c - r) * half - camX; val cy = (c + r) * quat - camY
    val ballR = ts * 0.22f; val ballY = cy - quat - ballR
    drawOval(MC_BALL_SHADOW,
        topLeft = Offset(cx - ballR * 1.2f, cy - quat - ballR * 0.25f),
        size = androidx.compose.ui.geometry.Size(ballR * 2.4f, ballR * 0.5f))
    drawCircle(MC_BALL, ballR, Offset(cx, ballY))
    drawCircle(MC_BALL_RIM, ballR, Offset(cx, ballY),
        style = androidx.compose.ui.graphics.drawscope.Stroke(ballR * 0.12f))
    drawCircle(MC_BALL_SHINE, ballR * 0.28f, Offset(cx - ballR * 0.28f, ballY - ballR * 0.30f))
}

private fun DrawScope.isoKey(
    r: Float, c: Float, ts: Float, camX: Float, camY: Float,
    appearance: KeyAppearance, alpha: Float = 1f,
) {
    val half = ts / 2f; val quat = ts / 4f
    val cx = (c - r) * half - camX; val cy = (c + r) * quat - camY
    val kr = ts * 0.18f; val keyY = cy - quat - kr * 0.5f
    val tint = appearance.tint.copy(alpha = alpha)
    val bowR = kr * 0.7f * appearance.bowRadius
    val bowCenter = Offset(cx, keyY - kr * 0.6f)
    when (appearance.bowStyle) {
        BowStyle.RING ->
            drawCircle(tint, bowR, bowCenter,
                style = androidx.compose.ui.graphics.drawscope.Stroke(kr * 0.35f))
        BowStyle.FILLED -> {
            drawCircle(tint.copy(alpha = tint.alpha * 0.55f), bowR, bowCenter)
            drawCircle(tint, bowR * 0.45f, bowCenter)
        }
        BowStyle.DOUBLE_RING -> {
            drawCircle(tint, bowR,         bowCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(kr * 0.15f))
            drawCircle(tint, bowR * 0.55f, bowCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(kr * 0.13f))
        }
    }
    val shaftTop    = keyY - kr * 0.25f
    val shaftBottom = keyY + kr * 0.8f * appearance.shaftScale
    drawLine(tint, Offset(cx, shaftTop), Offset(cx, shaftBottom), strokeWidth = kr * 0.35f)
    val shaftLen = shaftBottom - shaftTop
    for (tooth in appearance.teeth) {
        val ty = shaftTop + shaftLen * tooth.shaftFraction
        drawLine(tint, Offset(cx, ty), Offset(cx + kr * 0.5f * tooth.toothLength, ty), strokeWidth = kr * 0.3f)
    }
}

private fun DrawScope.isoDecoyKey(
    r: Float, c: Float, ts: Float, camX: Float, camY: Float,
    appearance: DecoyKeyAppearance,
) {
    val half = ts / 2f; val quat = ts / 4f
    val cx = (c - r) * half - camX; val cy = (c + r) * quat - camY
    val kr = ts * 0.18f; val keyY = cy - quat - kr * 0.5f
    val tint = appearance.tint
    val bowR = kr * 0.7f * appearance.bowRadius
    val bowCenter = Offset(cx, keyY - kr * 0.6f)
    when (appearance.bowStyle) {
        BowStyle.RING ->
            drawCircle(tint, bowR, bowCenter,
                style = androidx.compose.ui.graphics.drawscope.Stroke(kr * 0.35f))
        BowStyle.FILLED -> {
            drawCircle(tint.copy(alpha = 0.55f), bowR, bowCenter)
            drawCircle(tint, bowR * 0.45f, bowCenter)
        }
        BowStyle.DOUBLE_RING -> {
            drawCircle(tint, bowR,         bowCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(kr * 0.15f))
            drawCircle(tint, bowR * 0.55f, bowCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(kr * 0.13f))
        }
    }
    val shaftTop    = keyY - kr * 0.25f
    val shaftBottom = keyY + kr * 0.8f * appearance.shaftScale
    drawLine(tint, Offset(cx, shaftTop), Offset(cx, shaftBottom), strokeWidth = kr * 0.35f)
    val shaftLen = shaftBottom - shaftTop
    for (tooth in appearance.teeth) {
        val ty = shaftTop + shaftLen * tooth.shaftFraction
        drawLine(tint, Offset(cx, ty), Offset(cx + kr * 0.5f * tooth.toothLength, ty), strokeWidth = kr * 0.3f)
    }
}

// Trap ball: a small glowing coloured sphere
private fun DrawScope.isoTrapBall(r: Float, c: Float, ts: Float, camX: Float, camY: Float, color: Color) {
    val half = ts / 2f; val quat = ts / 4f
    val cx = (c - r) * half - camX; val cy = (c + r) * quat - camY
    val br = ts * 0.16f; val by = cy - quat - br
    drawCircle(color.copy(alpha = 0.25f), br * 2.2f, Offset(cx, by))
    drawCircle(color.copy(alpha = 0.50f), br * 1.5f, Offset(cx, by))
    drawCircle(color, br, Offset(cx, by))
    drawCircle(Color.White.copy(alpha = 0.55f), br * 0.28f, Offset(cx - br * 0.28f, by - br * 0.28f))
}

private fun DrawScope.isoFloorTrap(r: Float, c: Float, ts: Float, camX: Float, camY: Float, appearance: FloorTrapAppearance) {
    isoFloor(r, c, ts, camX, camY, appearance.floorColor)
    if (appearance.showEdge) {
        val half = ts / 2f; val quat = ts / 4f
        val cx = (c - r) * half - camX; val cy = (c + r) * quat - camY
        drawPath(Path().apply {
            moveTo(cx, cy - quat); lineTo(cx + half, cy)
            lineTo(cx, cy + quat); lineTo(cx - half, cy); close()
        }, color = appearance.edgeColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
    }
}

// Hub keyhole slot: dim outline when empty, full key shape when filled
private fun DrawScope.isoKeySlot(
    r: Float, c: Float, ts: Float, camX: Float, camY: Float,
    appearance: KeyAppearance, filled: Boolean,
) {
    val half = ts / 2f; val quat = ts / 4f
    val cx = (c - r) * half - camX; val cy = (c + r) * quat - camY
    val kr = ts * 0.20f; val keyY = cy - quat - kr * 0.5f
    val tint = appearance.tint
    if (filled) {
        drawCircle(tint.copy(alpha = 0.30f), kr * 2.0f, Offset(cx, keyY))
        isoKey(r, c, ts, camX, camY, appearance)
    } else {
        drawCircle(tint.copy(alpha = 0.18f), kr * 0.8f, Offset(cx, keyY - kr * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(kr * 0.20f))
        drawLine(tint.copy(alpha = 0.18f),
            Offset(cx, keyY - kr * 0.1f), Offset(cx, keyY + kr * 0.5f),
            strokeWidth = kr * 0.18f)
    }
}

private fun easeInOut(t: Float): Float { val c = t.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

// ─────────────────────────────────────────────────────────────────────────────
// KEY STUDY INTRO — shown before the maze starts; player cycles through all keys
// ─────────────────────────────────────────────────────────────────────────────

/** Flat (non-isometric) key drawn on a plain Canvas — used when no .glb model is set. */
@Composable
private fun KeyPreviewCanvas(appearance: KeyAppearance, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx    = size.width  / 2f
        val kr    = size.width  * 0.14f
        val bowCY = size.height * 0.28f
        val tint  = appearance.tint

        val bowR = kr * 0.7f * appearance.bowRadius
        when (appearance.bowStyle) {
            BowStyle.RING ->
                drawCircle(tint, bowR, Offset(cx, bowCY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(kr * 0.35f))
            BowStyle.FILLED -> {
                drawCircle(tint.copy(alpha = 0.55f), bowR, Offset(cx, bowCY))
                drawCircle(tint, bowR * 0.45f, Offset(cx, bowCY))
            }
            BowStyle.DOUBLE_RING -> {
                drawCircle(tint, bowR,         Offset(cx, bowCY), style = androidx.compose.ui.graphics.drawscope.Stroke(kr * 0.15f))
                drawCircle(tint, bowR * 0.55f, Offset(cx, bowCY), style = androidx.compose.ui.graphics.drawscope.Stroke(kr * 0.13f))
            }
        }
        val shaftTop    = bowCY + bowR + kr * 0.15f
        val shaftBottom = shaftTop + (size.height * 0.52f) * appearance.shaftScale
        drawLine(tint, Offset(cx, shaftTop), Offset(cx, shaftBottom), strokeWidth = kr * 0.35f)
        val shaftLen = shaftBottom - shaftTop
        for (tooth in appearance.teeth) {
            val ty = shaftTop + shaftLen * tooth.shaftFraction
            drawLine(tint, Offset(cx, ty), Offset(cx + kr * 0.5f * tooth.toothLength, ty), strokeWidth = kr * 0.3f)
        }
    }
}

/**
 * Pre-game key study screen.
 * Shows each real key (id 0-4) one at a time so players know what they're searching for.
 * 3D SceneView when modelFile is set; flat canvas drawing otherwise.
 * "skip all" jumps straight to the maze. "next →" / "play →" cycles through keys.
 */
@Composable
private fun KeyStudyIntro(
    onDone: () -> Unit,
    sceneContent: @Composable (modelFile: String, modifier: Modifier) -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    val appearance = KEY_APPEARANCES[index]
    val isLast     = index == KEY_APPEARANCES.lastIndex

    fun advance() { if (isLast) onDone() else index++ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        ) {
            Text(
                "these are your keys",
                color = Color(0xFF666666),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KEY_APPEARANCES.forEachIndexed { i, a ->
                    Box(
                        Modifier.size(8.dp)
                            .background(
                                if (i == index) a.tint else a.tint.copy(alpha = 0.25f),
                                CircleShape,
                            )
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // ── key viewer ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color(0xFF070707), RoundedCornerShape(12.dp))
                    .border(1.dp, appearance.tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val modelFile = appearance.modelFile
                if (modelFile != null) {
                    sceneContent(modelFile, Modifier.fillMaxSize())
                } else {
                    KeyPreviewCanvas(appearance, modifier = Modifier.fillMaxSize().padding(40.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (appearance.modelFile != null) "drag to inspect" else "",
                color = Color(0xFF444444),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clickable(onClick = onDone)
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                ) {
                    Text("skip all", color = Color(0xFF3A3A3A), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .background(appearance.tint.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        .border(1.dp, appearance.tint.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .clickable(onClick = ::advance)
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                ) {
                    Text(if (isLast) "play →" else "next →", color = appearance.tint, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KEY EXAMINE OVERLAY — shown when ball rolls near a key/decoy that has a model
// ─────────────────────────────────────────────────────────────────────────────

private sealed class ExamineTarget {
    abstract val tint: Color
    abstract val modelFile: String

    data class RealKey(
        val key: MazeKey,
        override val tint: Color,
        override val modelFile: String,
    ) : ExamineTarget()

    data class Decoy(
        val trap: MazeTrap,
        override val tint: Color,
        override val modelFile: String,
    ) : ExamineTarget()
}

/** Holds the state for an active article-quiz (orange trap). */
private data class ArticleQuiz(
    val trap: MazeTrap,
    val articleNum: Int,           // 1–8; 1–4 = real, 5–8 = fake
) {
    val isReal: Boolean get() = articleNum <= 4
}

/**
 * Full-screen 3D examine overlay.
 * Both real keys and decoys use identical UI — the player can only tell them apart by shape.
 *
 * collect → calls onAccept  (collects key  OR  triggers red-trap effect)
 * leave   → calls onLeave   (ball resumes, item stays in maze)
 *
 * Model files go in:  app/src/main/assets/models/keys/   and   .../decoys/
 * Blender export:  File → Export → glTF 2.0  →  Format: glTF Binary (.glb)
 */
@Composable
private fun KeyExamineOverlay(
    target: ExamineTarget,
    onAccept: () -> Unit,
    onLeave: () -> Unit,
    sceneContent: @Composable (modelFile: String, modifier: Modifier) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Text(
                "key",
                color = target.tint,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(16.dp))

            // ── 3D viewer ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Color(0xFF080808), RoundedCornerShape(12.dp))
                    .border(1.dp, target.tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            ) {
                sceneContent(target.modelFile, Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "drag to inspect",
                color = Color(0xFF555555),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(32.dp))

            // ── Accept / Leave buttons ──
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                        .clickable(onClick = onLeave)
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                ) {
                    Text("leave", color = Color(0xFF888888), fontFamily = FontFamily.Monospace)
                }
                Box(
                    modifier = Modifier
                        .background(target.tint.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        .border(1.dp, target.tint.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onAccept)
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                ) {
                    Text("collect", color = target.tint, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MazeGame(onComplete: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current

    // ── Mutable layout/world holders (swapped on each red-trap regeneration) ──
    val worldHolder  = remember { mutableStateOf(buildWorldGrid(MazeLayout(0), 0)) }
    val keysHolder   = remember { mutableStateOf(placeKeys(worldHolder.value, MazeLayout(0))) }
    val trapsHolder  = remember {
        val kp = keysHolder.value.map { it.row to it.col }.toHashSet()
        mutableStateOf(placeTraps(worldHolder.value, MazeLayout(0), kp))
    }
    val layoutHolder = remember { mutableStateOf(MazeLayout(0)) }

    // Read current snapshot into vals so the Canvas redraws when they change
    val layout = layoutHolder.value
    val world  = worldHolder.value
    val keys   = keysHolder.value
    val traps  = trapsHolder.value

    // Ball start: first floor tile in Room N
    val (startR0, startC0) = remember { findStart(worldHolder.value, MazeLayout(0)) }
    var ballRow by remember { mutableStateOf(startR0) }
    var ballCol by remember { mutableStateOf(startC0) }
    var velR    by remember { mutableStateOf(0f) }
    var velC    by remember { mutableStateOf(0f) }

    // Camera
    var cameraZoom   by remember { mutableStateOf(0f) }
    var cameraFocusR by remember { mutableStateOf(MazeLayout(0).wgridRows / 2f) }
    var cameraFocusC by remember { mutableStateOf(MazeLayout(0).wgridCols / 2f) }

    // Gyro
    var gyroTiltX by remember { mutableStateOf(0f) }
    var gyroTiltY by remember { mutableStateOf(0f) }
    var gyroR     by remember { mutableStateOf(0f) }
    var gyroC     by remember { mutableStateOf(0f) }
    var arrowR    by remember { mutableStateOf(0f) }
    var arrowC    by remember { mutableStateOf(0f) }

    // Intro phases: 0=aerial, 1=keys glow + warning, 2=zoom, 3=playing
    var introPhase   by remember { mutableIntStateOf(0) }
    var keyGlowAlpha by remember { mutableStateOf(0f) }
    var warnAlpha    by remember { mutableStateOf(0f) }

    // Game state
    val collected      = remember { mutableStateListOf<Int>() }
    val collectedTraps = remember { mutableStateListOf<Int>() }  // triggers recompose on trap pick-up
    var darkTrapCount  by remember { mutableIntStateOf(0) }
    var flickerAlpha   by remember { mutableStateOf(0f) }
    var message        by remember { mutableStateOf<String?>(null) }
    var won            by remember { mutableStateOf(false) }

    // Key study intro (shown before maze starts)
    var keyStudyDone by remember { mutableStateOf(false) }

    // Examine overlay state
    val coroutineScope    = rememberCoroutineScope()
    var examineTarget     by remember { mutableStateOf<ExamineTarget?>(null) }
    var examineCooldown   by remember { mutableStateOf(false) }
    // Pending red-trap effect (set when player accepts a decoy from the overlay)
    var pendingRedTrap    by remember { mutableStateOf<MazeTrap?>(null) }
    // Article quiz shown when rolling over an orange trap
    var articleQuizTarget by remember { mutableStateOf<ArticleQuiz?>(null) }
    // Persistent across rebuilds: which article numbers have been shown
    val seenArticleNums       = remember { mutableStateOf(emptySet<Int>()) }
    // Persistent across rebuilds: which decoy slots (id % 16) have been collected
    val globalCollectedDecoySlots = remember { mutableStateOf(emptySet<Int>()) }

    val chaosRng = remember { Random(System.currentTimeMillis()) }

    // ── SceneView lifecycle guard ─────────────────────────────────────────────
    // Set to false before onComplete() so all Scene composables unmount cleanly
    // (preventing a native crash when rememberEngine() disposes the engine while
    // a frame is still rendering).
    var sceneEnabled by remember { mutableStateOf(true) }

    // ── Shared SceneView engine (lives for the whole game session) ───────────
    // Created here so it is never destroyed during study→game or game→examine
    // transitions. Both KeyStudyIntro and KeyExamineOverlay receive a lambda
    // that closes over these values — no composable owns or destroys the engine.
    val sceneEngine        = rememberEngine()
    val sceneModelLoader   = rememberModelLoader(sceneEngine)
    val sceneCameraNode    = rememberCameraNode(sceneEngine) { position = Position(z = 3.5f) }
    val sceneMainLightNode = rememberMainLightNode(sceneEngine)
    // Composable slot: renders a 3D model using the shared engine.
    // When sceneEnabled is false, renders nothing (safe engine teardown path).
    val sceneContent: @Composable (modelFile: String, modifier: Modifier) -> Unit =
        { modelFile, mod ->
            if (sceneEnabled) {
                val modelNode = remember(modelFile) {
                    ModelNode(
                        modelInstance = sceneModelLoader.createModelInstance(modelFile),
                        scaleToUnits  = 2.0f,
                    ).apply { isEditable = true; rotation = Rotation(x = 15f, y = -25f) }
                }
                Scene(
                    modifier      = mod,
                    engine        = sceneEngine,
                    modelLoader   = sceneModelLoader,
                    cameraNode    = sceneCameraNode,
                    mainLightNode = sceneMainLightNode,
                    childNodes    = remember(modelFile) { listOf(modelNode) },
                )
            }
        }

    // Sensor
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val tiltSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }
    val hasGyro = tiltSensor != null

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val prev = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose { activity?.requestedOrientation = prev ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    DisposableEffect(Unit) {
        if (tiltSensor == null) return@DisposableEffect onDispose {}
        val listener = object : SensorEventListener {
            private val alpha = 0.20f
            private val rotMat = FloatArray(9); private val orientation = FloatArray(3)
            private fun dz(v: Float) = if (abs(v) < 0.06f) 0f else v
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMat, e.values)
                SensorManager.getOrientation(rotMat, orientation)
                val maxA = (Math.PI / 6.0).toFloat()
                val rawC = dz((orientation[2] / maxA).coerceIn(-1f, 1f))
                val rawR = dz((-orientation[1] / maxA).coerceIn(-1f, 1f))
                gyroC = gyroC * (1f - alpha) + rawC * alpha
                gyroR = gyroR * (1f - alpha) + rawR * alpha
                gyroTiltX = gyroC * 0.30f; gyroTiltY = gyroR * 0.30f
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) {}
        }
        sensorManager.registerListener(listener, tiltSensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // ── Intro sequence (starts only after key study is dismissed) ────────────
    LaunchedEffect(keyStudyDone) {
        if (!keyStudyDone) return@LaunchedEffect
        delay(400)
        val glowSteps = 40
        for (i in 1..glowSteps) { keyGlowAlpha = i.toFloat() / glowSteps; delay(20) }
        introPhase = 1
        val warnSteps = 30
        for (i in 1..warnSteps) { warnAlpha = i.toFloat() / warnSteps; delay(20) }
        delay(1400)
        introPhase = 2
        val zoomSteps = 60
        val initLayout = layoutHolder.value
        val fromR = initLayout.wgridRows / 2f; val fromC = initLayout.wgridCols / 2f
        for (i in 1..zoomSteps) {
            val t = easeInOut(i.toFloat() / zoomSteps)
            cameraZoom = t
            cameraFocusR = lerp(fromR, ballRow, t)
            cameraFocusC = lerp(fromC, ballCol, t)
            delay(18)
        }
        cameraZoom = 1f; introPhase = 3
    }

    // ── Red-trap effect triggered after player accepts a decoy in the overlay ──
    LaunchedEffect(pendingRedTrap) {
        val trap = pendingRedTrap ?: return@LaunchedEffect
        val curLayout = layoutHolder.value
        val newLevel  = curLayout.level + 1
        val newLayout = MazeLayout(newLevel)
        val newSeed   = chaosRng.nextInt()
        val newWorld  = buildWorldGrid(newLayout, newSeed)
        val newKeys   = placeKeys(newWorld, newLayout, newSeed + 88812)
        val collectedIds = collected.toHashSet()
        for (k in newKeys) if (k.id in collectedIds) k.collected = true
        val keyPos   = newKeys.filter { !it.collected }.map { it.row to it.col }.toHashSet()
        val unusedArticles = (1..8).filter { it !in seenArticleNums.value }
        val newTraps = placeTraps(newWorld, newLayout, keyPos, newSeed + 55577, unusedArticles)
        // Pre-mark decoy traps whose slot was already collected globally
        val collectedSlots = globalCollectedDecoySlots.value
        for (t in newTraps) {
            if (t.type == TrapType.RED && (t.id % DECOY_KEY_APPEARANCES.size) in collectedSlots) {
                t.collected = true
            }
        }
        for (i in 1..8) { flickerAlpha = i / 8f; delay(10) }
        for (i in 8 downTo 1) { flickerAlpha = i / 8f; delay(8) }
        for (i in 1..8) { flickerAlpha = i / 8f; delay(10) }
        worldHolder.value  = newWorld
        keysHolder.value   = newKeys
        trapsHolder.value  = newTraps
        layoutHolder.value = newLayout
        val (nr, nc) = findStart(newWorld, newLayout)
        ballRow = nr; ballCol = nc
        velR = 0f; velC = 0f
        cameraFocusR = nr; cameraFocusC = nc
        collectedTraps.clear()
        for (i in 8 downTo 1) { flickerAlpha = i / 8f; delay(8) }
        for (i in 1..5) { flickerAlpha = i / 5f; delay(8) }
        for (i in 5 downTo 1) { flickerAlpha = i / 5f; delay(8) }
        flickerAlpha = 0f
        message = "that's not the right key"
        delay(1400); message = null
        pendingRedTrap = null
    }

    // ── Game loop ─────────────────────────────────────────────────────────────
    LaunchedEffect(introPhase) {
        if (introPhase < 3) return@LaunchedEffect
        while (true) {
            delay(16)
            // Always read the latest world/layout/keys/traps from holders
            val curWorld  = worldHolder.value
            val curLayout = layoutHolder.value
            val curKeys   = keysHolder.value
            val curTraps  = trapsHolder.value
            val wRows = curWorld.size
            val wCols = curWorld[0].size

            if (examineTarget != null || articleQuizTarget != null || message != null || won) { velR = 0f; velC = 0f; continue }

            val tiltC = if (hasGyro) gyroC else arrowC
            val tiltR = if (hasGyro) gyroR else arrowR

            val accel    = 0.022f
            val friction = 0.91f
            val maxVel   = 0.18f

            val isoC = tiltC + tiltR; val isoR = -tiltC + tiltR
            velC = (velC + isoC * accel) * friction
            velR = (velR + isoR * accel) * friction
            velC = velC.coerceIn(-maxVel, maxVel)
            velR = velR.coerceIn(-maxVel, maxVel)

            val newCol = ballCol + velC; val newRow = ballRow + velR

            fun solid(col: Float, row: Float): Boolean {
                val c = col.toInt().coerceIn(0, wCols - 1)
                val r = row.toInt().coerceIn(0, wRows - 1)
                return curWorld[r][c] == 0
            }

            val rad = 0.38f; val side = rad * 0.72f
            val okC = when {
                velC > 0 -> !solid(newCol+rad, ballRow-side) && !solid(newCol+rad, ballRow) && !solid(newCol+rad, ballRow+side)
                velC < 0 -> !solid(newCol-rad, ballRow-side) && !solid(newCol-rad, ballRow) && !solid(newCol-rad, ballRow+side)
                else -> true
            }
            if (okC) ballCol = newCol else velC = 0f
            val okR = when {
                velR > 0 -> !solid(ballCol-side, newRow+rad) && !solid(ballCol, newRow+rad) && !solid(ballCol+side, newRow+rad)
                velR < 0 -> !solid(ballCol-side, newRow-rad) && !solid(ballCol, newRow-rad) && !solid(ballCol+side, newRow-rad)
                else -> true
            }
            if (okR) ballRow = newRow else velR = 0f

            cameraFocusR = lerp(cameraFocusR, ballRow, 0.12f)
            cameraFocusC = lerp(cameraFocusC, ballCol, 0.12f)

            // ── Key collection ────────────────────────────────────────────────
            for (key in curKeys) {
                if (key.collected) continue
                val dist = sqrt((ballRow - key.row).pow(2) + (ballCol - key.col).pow(2))
                if (dist < 0.65f && !examineCooldown) {
                    val app = KEY_APPEARANCES[key.id % KEY_APPEARANCES.size]
                    if (app.modelFile != null && examineTarget == null) {
                        // Show 3D examine overlay — player decides whether to take it
                        examineTarget = ExamineTarget.RealKey(key, app.tint, app.modelFile)
                    } else if (app.modelFile == null) {
                        // No model defined — collect instantly (original behaviour)
                        key.collected = true
                        collected.add(key.id)
                        val groupSlots = KEY_GROUP_DECOY_SLOTS[key.id] ?: emptySet()
                        globalCollectedDecoySlots.value = globalCollectedDecoySlots.value + groupSlots
                        for (t in curTraps) {
                            if (t.type == TrapType.RED && (t.id % DECOY_KEY_APPEARANCES.size) in groupSlots) t.collected = true
                        }
                        if (collected.size >= 5) { won = true }
                        else { message = "piece ${collected.size} / 5"; delay(1200); message = null }
                    }
                    break
                }
            }

            // ── Trap collection ───────────────────────────────────────────────
            for (trap in curTraps) {
                if (trap.collected) continue
                val dist = sqrt((ballRow - trap.row).pow(2) + (ballCol - trap.col).pow(2))
                if (dist < 0.60f && !examineCooldown) {
                    when (trap.type) {
                        TrapType.RED -> {
                            val app = DECOY_KEY_APPEARANCES[trap.id % DECOY_KEY_APPEARANCES.size]
                            if (app.modelFile != null && examineTarget == null) {
                                // Show 3D examine overlay — effect fires only if player accepts
                                examineTarget = ExamineTarget.Decoy(trap, app.tint, app.modelFile)
                            } else if (app.modelFile == null) {
                                // No model — trigger maze rebuild instantly (original behaviour)
                                val slot = trap.id % DECOY_KEY_APPEARANCES.size
                                globalCollectedDecoySlots.value = globalCollectedDecoySlots.value + slot
                                trap.collected = true
                                collectedTraps.add(trap.id)
                                val newLevel  = curLayout.level + 1
                                val newLayout = MazeLayout(newLevel)
                                val newSeed   = chaosRng.nextInt()
                                val newWorld  = buildWorldGrid(newLayout, newSeed)
                                val newKeys   = placeKeys(newWorld, newLayout, newSeed + 88812)
                                val collectedIds = collected.toHashSet()
                                for (k in newKeys) if (k.id in collectedIds) k.collected = true
                                val keyPos   = newKeys.filter { !it.collected }.map { it.row to it.col }.toHashSet()
                                val unusedArticles2 = (1..8).filter { it !in seenArticleNums.value }
                                val newTraps = placeTraps(newWorld, newLayout, keyPos, newSeed + 55577, unusedArticles2)
                                val collectedSlots = globalCollectedDecoySlots.value
                                for (t in newTraps) {
                                    if (t.type == TrapType.RED && (t.id % DECOY_KEY_APPEARANCES.size) in collectedSlots) t.collected = true
                                }
                                for (i in 1..8) { flickerAlpha = i / 8f; delay(10) }
                                for (i in 8 downTo 1) { flickerAlpha = i / 8f; delay(8) }
                                for (i in 1..8) { flickerAlpha = i / 8f; delay(10) }
                                worldHolder.value  = newWorld
                                keysHolder.value   = newKeys
                                trapsHolder.value  = newTraps
                                layoutHolder.value = newLayout
                                val (nr, nc) = findStart(newWorld, newLayout)
                                ballRow = nr; ballCol = nc
                                velR = 0f; velC = 0f
                                cameraFocusR = nr; cameraFocusC = nc
                                collectedTraps.clear()
                                for (i in 8 downTo 1) { flickerAlpha = i / 8f; delay(8) }
                                for (i in 1..5) { flickerAlpha = i / 5f; delay(8) }
                                for (i in 5 downTo 1) { flickerAlpha = i / 5f; delay(8) }
                                flickerAlpha = 0f
                                message = "that's not the right key"
                                delay(1400); message = null
                            }
                        }
                        TrapType.ORANGE -> {
                            seenArticleNums.value = seenArticleNums.value + trap.articleNum
                            articleQuizTarget = ArticleQuiz(trap, trap.articleNum)
                        }
                    }
                    break
                }
            }
        }
    }

    LaunchedEffect(won) {
        if (!won) return@LaunchedEffect
        delay(3000)
        sceneEnabled = false  // unmount all SceneView composables before teardown
        delay(400)            // allow Compose to process the unmount
        onComplete()
    }

    // ── Sprite bitmaps — loaded once from assets, drawn in Canvas ────────────
    // Populated when PNGs exist; Canvas falls back to 2D drawing if entry is absent.
    val spriteBitmaps = remember { mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>() }
    LaunchedEffect(Unit) {
        val paths = (KEY_APPEARANCES.mapNotNull { it.spriteFile } +
                DECOY_KEY_APPEARANCES.mapNotNull { it.spriteFile }).distinct()
        for (path in paths) {
            runCatching {
                val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }
                }.asImageBitmap()
                spriteBitmaps[path] = bmp
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(MC_BG)) {

        // Reading these states in composable scope ensures Canvas redraws when they change
        val trapTrigger  = collectedTraps.size
        val darkCount    = darkTrapCount
        val curLayout    = layoutHolder.value
        val curWorld     = worldHolder.value
        val curKeys      = keysHolder.value
        val curTraps     = trapsHolder.value

        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") trapTrigger
            @Suppress("UNUSED_EXPRESSION") darkCount

            val wgridRows = curLayout.wgridRows
            val wgridCols = curLayout.wgridCols
            val sw = size.width; val sh = size.height

            val overviewTs = minOf(sw / ((wgridCols + wgridRows) / 2f),
                sh / ((wgridCols + wgridRows) / 4f) * 0.8f)
            val playTs = minOf(sw / 7f, sh / 7f)
            val ts = lerp(overviewTs, playTs, easeInOut(cameraZoom))

            val focR = cameraFocusR + gyroTiltY * lerp(0f, 0.5f, cameraZoom)
            val focC = cameraFocusC + gyroTiltX * lerp(0f, 0.5f, cameraZoom)
            val half = ts / 2f; val quat = ts / 4f
            val camX = (focC - focR) * half - sw / 2f
            val camY = (focC + focR) * quat - sh / 2f

            val margin = ts * 4f
            fun inView(r: Int, c: Int): Boolean {
                val sx = (c - r) * half - camX; val sy = (c + r) * quat - camY
                return sx > -margin && sx < sw + margin && sy > -margin && sy < sh + margin
            }

            // Pass 1 — floor tiles (orange traps as subtly tinted floor tiles)
            val orangeTrapMap = curTraps
                .filter { !it.collected && it.type == TrapType.ORANGE }
                .associateBy { it.row to it.col }
            for (r in 0 until wgridRows) for (c in 0 until wgridCols) {
                if (!inView(r, c) || curWorld[r][c] != 1) continue
                val orangeTrap = orangeTrapMap[r to c]
                if (orangeTrap != null) {
                    isoFloorTrap(r.toFloat(), c.toFloat(), ts, camX, camY,
                        FLOOR_TRAP_APPEARANCES[orangeTrap.id % FLOOR_TRAP_APPEARANCES.size])
                } else {
                    isoFloor(r.toFloat(), c.toFloat(), ts, camX, camY, MC_FLOOR)
                }
            }

            // Pass 2 — wall faces
            for (depth in 0 until wgridRows + wgridCols) {
                val rMin = maxOf(0, depth - (wgridCols - 1)); val rMax = minOf(wgridRows - 1, depth)
                for (r in rMin..rMax) {
                    val c = depth - r
                    if (c !in 0 until wgridCols || !inView(r, c) || curWorld[r][c] != 0) continue
                    if (r + 1 < wgridRows && curWorld[r+1][c] == 1) isoWallSouth(r.toFloat(), c.toFloat(), ts, camX, camY)
                    if (c + 1 < wgridCols && curWorld[r][c+1] == 1) isoWallEast(r.toFloat(), c.toFloat(), ts, camX, camY)
                }
            }

            // Pass 3 — wall tops, key slots, keys, decoys, ball
            for (depth in 0 until wgridRows + wgridCols) {
                val rMin = maxOf(0, depth - (wgridCols - 1)); val rMax = minOf(wgridRows - 1, depth)
                for (r in rMin..rMax) {
                    val c = depth - r
                    if (c !in 0 until wgridCols || !inView(r, c)) continue
                    if (curWorld[r][c] == 0) {
                        val exposed = (r+1 < wgridRows && curWorld[r+1][c]==1) ||
                            (c+1 < wgridCols && curWorld[r][c+1]==1) ||
                            (r>0 && curWorld[r-1][c]==1) || (c>0 && curWorld[r][c-1]==1)
                        if (exposed) isoWallTop(r.toFloat(), c.toFloat(), ts, camX, camY)
                    }
                }
                for ((slotIdx, slot) in curLayout.hubKeySlots.withIndex()) {
                    val (sr, sc) = slot
                    if (sr + sc != depth || !inView(sr, sc)) continue
                    isoKeySlot(sr.toFloat(), sc.toFloat(), ts, camX, camY,
                        KEY_APPEARANCES[slotIdx % KEY_APPEARANCES.size], filled = slotIdx in collected)
                }

                // Real keys — sprite if PNG loaded, else 2D drawn key
                for (mk in curKeys) {
                    if (mk.collected || mk.row + mk.col != depth || !inView(mk.row, mk.col)) continue
                    val a = if (introPhase <= 1) keyGlowAlpha else 1f
                    val app = KEY_APPEARANCES[mk.id % KEY_APPEARANCES.size]
                    val sprite = app.spriteFile?.let { spriteBitmaps[it] }
                    if (sprite != null) isoSprite(mk.row.toFloat(), mk.col.toFloat(), ts, camX, camY, sprite, a)
                    else isoKey(mk.row.toFloat(), mk.col.toFloat(), ts, camX, camY, app, a)
                }

                // Decoy keys — sprite if PNG loaded, else 2D drawn decoy
                for (trap in curTraps) {
                    if (trap.collected || trap.type != TrapType.RED) continue
                    if (trap.row + trap.col != depth || !inView(trap.row, trap.col)) continue
                    val app = DECOY_KEY_APPEARANCES[trap.id % DECOY_KEY_APPEARANCES.size]
                    val sprite = app.spriteFile?.let { spriteBitmaps[it] }
                    if (sprite != null) isoSprite(trap.row.toFloat(), trap.col.toFloat(), ts, camX, camY, sprite)
                    else isoDecoyKey(trap.row.toFloat(), trap.col.toFloat(), ts, camX, camY, app)
                }

                val br = ballRow.toInt(); val bc = ballCol.toInt()
                if (br + bc == depth && introPhase >= 2) isoBall(ballRow, ballCol, ts, camX, camY)
            }

            // Aerial glow blobs (overview zoom)
            if (cameraZoom < 0.95f && keyGlowAlpha > 0f) {
                for (mk in curKeys) {
                    if (mk.collected) continue
                    val isoX = (mk.col - mk.row) * half - camX
                    val isoY = (mk.col + mk.row) * quat - camY
                    val t = (1f - cameraZoom) * keyGlowAlpha
                    val glowR = lerp(2f, 12f, t)
                    val keyTint = KEY_APPEARANCES[mk.id % KEY_APPEARANCES.size].tint
                    drawCircle(keyTint.copy(alpha = t * 0.4f), glowR * 2f, Offset(isoX, isoY))
                    drawCircle(keyTint.copy(alpha = t * 0.9f), glowR,       Offset(isoX, isoY))
                }
            }

            // Spotlight / darkness (orange-trap effect)
            if (darkCount > 0 && introPhase >= 3) {
                val ballSx = (ballCol - ballRow) * half - camX
                val ballSy = (ballCol + ballRow) * quat - camY
                val spotRadius = minOf(sw, sh) * maxOf(0.09f, 0.85f - darkCount * 0.14f)
                drawRect(
                    brush = Brush.radialGradient(
                        0f    to Color.Transparent,
                        0.70f to Color.Transparent,
                        1.00f to Color.Black,
                        center = Offset(ballSx, ballSy),
                        radius = spotRadius,
                        tileMode = TileMode.Clamp,
                    ),
                    topLeft = Offset.Zero,
                    size = size,
                )
            }
        }

        // ── Screen flicker (red-trap regeneration effect) ─────────────────────
        if (flickerAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flickerAlpha)))
        }

        // ── Key HUD — shown from intro start with fading alpha ─────────────
        val hudAlpha = if (introPhase == 0) keyGlowAlpha else 1f
        if (hudAlpha > 0f) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .background(Color(0xCC000000).copy(alpha = 0.8f * hudAlpha), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 5) {
                    val found = i in collected
                    Box(
                        Modifier.size(14.dp)
                            .background(
                                if (found) KEY_APPEARANCES[i % KEY_APPEARANCES.size].tint.copy(alpha = hudAlpha)
                                else Color(0xFF2A2A2A).copy(alpha = hudAlpha),
                                CircleShape)
                            .border(1.dp,
                                if (found) KEY_APPEARANCES[i % KEY_APPEARANCES.size].tint.copy(alpha = 0.5f * hudAlpha)
                                else Color(0xFF444444).copy(alpha = hudAlpha),
                                CircleShape)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text("${collected.size}/5",
                    color = Color(0xFF888888).copy(alpha = hudAlpha),
                    fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // ── Intro text overlay ────────────────────────────────────────────────
        if (introPhase <= 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "find the keys to escape",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                    if (warnAlpha > 0f) {
                        Text(
                            text = "not all is like it seems",
                            color = Color(0xFFFF8800).copy(alpha = warnAlpha * 0.95f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color(0xCC000000).copy(alpha = warnAlpha * 0.8f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // ── Exit button ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                .clickable(onClick = onExit)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text("EXIT", color = Color(0xFF666666), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        // ── Arrow fallback ────────────────────────────────────────────────────
        if (!hasGyro && introPhase >= 3) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MazeArrowBtn("▲", onDown = { arrowR = -1f }, onUp = { arrowR = 0f })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MazeArrowBtn("◀", onDown = { arrowC = -1f }, onUp = { arrowC = 0f })
                    Spacer(Modifier.size(52.dp))
                    MazeArrowBtn("▶", onDown = { arrowC = 1f }, onUp = { arrowC = 0f })
                }
                MazeArrowBtn("▼", onDown = { arrowR = 1f }, onUp = { arrowR = 0f })
            }
        }

        // ── Feedback message ──────────────────────────────────────────────────
        message?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(msg,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 22.dp, vertical = 10.dp))
            }
        }

        // ── Article quiz overlay (orange trap) ───────────────────────────────
        val currentQuiz = articleQuizTarget
        if (currentQuiz != null) {
            ArticleQuizOverlay(
                quiz = currentQuiz,
                onCorrect = {
                    articleQuizTarget = null
                    currentQuiz.trap.collected = true
                    collectedTraps.add(currentQuiz.trap.id)
                    coroutineScope.launch {
                        message = "nice, you got it right"
                        delay(1400); message = null
                    }
                },
                onWrong = {
                    articleQuizTarget = null
                    currentQuiz.trap.collected = true
                    collectedTraps.add(currentQuiz.trap.id)
                    darkTrapCount++
                    coroutineScope.launch {
                        message = "careful where you roll"
                        delay(1400); message = null
                    }
                },
            )
        }

        // ── 3D key examine overlay ────────────────────────────────────────────
        val currentExamine = examineTarget
        if (currentExamine != null) {
            KeyExamineOverlay(
                target       = currentExamine,
                sceneContent = sceneContent,
                onAccept = {
                    when (currentExamine) {
                        is ExamineTarget.RealKey -> {
                            examineTarget = null
                            val key = currentExamine.key
                            key.collected = true
                            collected.add(key.id)
                            // Wipe all decoys that belong to this key's group
                            val groupSlots = KEY_GROUP_DECOY_SLOTS[key.id] ?: emptySet()
                            globalCollectedDecoySlots.value = globalCollectedDecoySlots.value + groupSlots
                            for (t in trapsHolder.value) {
                                if (t.type == TrapType.RED && (t.id % DECOY_KEY_APPEARANCES.size) in groupSlots) {
                                    t.collected = true
                                }
                            }
                            coroutineScope.launch {
                                if (collected.size >= 5) won = true
                                else { message = "piece ${collected.size} / 5"; delay(1200); message = null }
                            }
                        }
                        is ExamineTarget.Decoy -> {
                            examineTarget = null
                            val trap = currentExamine.trap
                            trap.collected = true
                            collectedTraps.add(trap.id)
                            val slot = trap.id % DECOY_KEY_APPEARANCES.size
                            globalCollectedDecoySlots.value = globalCollectedDecoySlots.value + slot
                            pendingRedTrap = trap  // kicks off the LaunchedEffect rebuild
                        }
                    }
                },
                onLeave = {
                    examineTarget = null
                    examineCooldown = true
                    coroutineScope.launch { delay(600); examineCooldown = false }
                },
            )
        }

        // ── Key study intro (shown before maze starts) ────────────────────────
        // AnimatedVisibility keeps the composable (and its SceneView engine) alive
        // through the 300ms fade-out, preventing a native crash from engine.destroy()
        // firing while the Scene surface is still attached.
        AnimatedVisibility(
            visible = !keyStudyDone,
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            KeyStudyIntro(onDone = { keyStudyDone = true }, sceneContent = sceneContent)
        }

        // ── Win overlay ───────────────────────────────────────────────────────
        if (won) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ALL KEYS FOUND",
                        color = Color(0xFFFFDD44), fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        KEY_APPEARANCES.forEach { a ->
                            val sprite = a.spriteFile?.let { spriteBitmaps[it] }
                            if (sprite != null) {
                                Image(
                                    bitmap = sprite,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                Box(Modifier.size(24.dp).background(a.tint, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ARROW BUTTON
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MazeArrowBtn(label: String, onDown: () -> Unit, onUp: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(Color(0xCC111111), CircleShape)
            .border(1.dp, Color(0xFF333333), CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press   -> onDown()
                            PointerEventType.Release -> onUp()
                            else -> {}
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFF888888), fontSize = 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ARTICLE QUIZ OVERLAY  (orange trap)
// ─────────────────────────────────────────────────────────────────────────────
// Shows a news-article screenshot; player must judge real vs. made up.
// Correct answer → trap removed with no penalty.
// Wrong answer   → trap removed AND spotlight shrinks (darkTrapCount++).

@Composable
private fun ArticleQuizOverlay(
    quiz: ArticleQuiz,
    onCorrect: () -> Unit,
    onWrong: () -> Unit,
) {
    val context = LocalContext.current
    val path = "articles/%02d.png".format(quiz.articleNum)

    var bitmap by remember(quiz.articleNum) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(quiz.articleNum) {
        runCatching {
            val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }
            }.asImageBitmap()
            bitmap = bmp
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                "real or made up?",
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(12.dp))

            // Article image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text("loading...", color = Color(0xFF444444), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // "real news" button
                Box(
                    modifier = Modifier
                        .background(Color(0xFF091409), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF2A5C2A), RoundedCornerShape(8.dp))
                        .clickable { if (quiz.isReal) onCorrect() else onWrong() }
                        .padding(horizontal = 30.dp, vertical = 14.dp),
                ) {
                    Text("real news", color = Color(0xFF4CAF50), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
                // "made up" button
                Box(
                    modifier = Modifier
                        .background(Color(0xFF160808), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF5C2A2A), RoundedCornerShape(8.dp))
                        .clickable { if (!quiz.isReal) onCorrect() else onWrong() }
                        .padding(horizontal = 30.dp, vertical = 14.dp),
                ) {
                    Text("made up", color = Color(0xFFE57373), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
