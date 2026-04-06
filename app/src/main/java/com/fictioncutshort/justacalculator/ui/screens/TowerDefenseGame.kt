package com.fictioncutshort.justacalculator.ui.screens

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.R
import kotlinx.coroutines.delay
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// TOWER DEFENSE — Building 1 minigame
// ─────────────────────────────────────────────────────────────────────────────

private enum class TDPhase    { PREP, WAVE, WON, LOST, PAUSED }
private enum class TowerState { IDLE, ATTACKING, RETURNING }
private enum class TowerType  { DAMAGE, SLOW }

private const val TOWER_DMG_COST  = 30
private const val TOWER_SLOW_COST = 45

// ── Data classes ──────────────────────────────────────────────────────────────

private data class EnemySpec(
    val hp: Int, val speed: Float, val reward: Int,
    val enemyType: Int,         // 1-5 → td_enemy1 … td_enemy5
    val scale: Float = 1f
)

private data class WaveDef(val enemies: List<EnemySpec>, val spawnInterval: Float = 0.6f)
private data class DecoPath(val pts: List<Pair<Int, Int>>, val color: Color)

private data class LevelDef(
    val gridCols: Int, val gridRows: Int,
    val mainPaths: List<List<Pair<Int, Int>>>,
    val decoPaths: List<DecoPath> = emptyList(),
    val waves: List<WaveDef>,
    val lives: Int,
    val startGold: Int,
    val waveGoldBonus: Int = 15,
    val waveDelay: Float = 5f,
    val rewireEnabled: Boolean = false,
    val firstRewireAtWave: Int = 0   // waveIdx must be >= this before rewiring starts
)

private class LiveEnemy(
    val uid: Int, val spec: EnemySpec, val pathIdx: Int = 0,
    var hp: Int = spec.hp, var segIdx: Int = 0, var segT: Float = 0f,
    var reached: Boolean = false, var speedMult: Float = 1f, var slowTimer: Float = 0f
) { val dead get() = hp <= 0 }

private class PlacedTower(val col: Int, val row: Int, val type: TowerType) {
    val range        = if (type == TowerType.DAMAGE) 2.2f else 2.5f
    val attackSpeed  = 3.0f
    val returnSpeed  = 4.5f
    var state        = TowerState.IDLE
    var posX: Float  = col + 0.5f
    var posY: Float  = row + 0.5f
    var targetUid    = -1
    val baseX: Float = col + 0.5f
    val baseY: Float = row + 0.5f
    var rangeTimer   = 2.0f    // seconds to show range circle after placement
}

// ── Shared deco cables for Level 2 & 3 (same 10×18 grid) ─────────────────────
// All 6 paths route to the brain terminal at (7,9), approaching from the right
// via column 9 / column 8, so they logically "wire into" the brain.

private val LEVEL23_DECO = listOf(
    // Blue: top row → col 9 all the way down → enter brain from right
    DecoPath(listOf(0 to 0, 9 to 0, 9 to 9, 8 to 9, 7 to 9), Color(0xFF4488FF)),
    // Orange: left side → across row 4 → col 8 straight down → brain
    DecoPath(listOf(0 to 3, 0 to 4, 8 to 4, 8 to 9, 7 to 9), Color(0xFFFF8844)),
    // Green: row 11 → col 6 down → row 13 → col 9 → right to brain
    DecoPath(listOf(0 to 11, 6 to 11, 6 to 13, 9 to 13, 9 to 9, 8 to 9, 7 to 9), Color(0xFF44CC66)),
    // Purple: left col to row 3 → across → col 8 straight down → brain
    DecoPath(listOf(0 to 7, 0 to 3, 8 to 3, 8 to 9, 7 to 9), Color(0xFFAA44EE)),
    // Cyan: left-low → col 2 → row 16 → col 9 → right to brain
    DecoPath(listOf(0 to 14, 2 to 14, 2 to 16, 9 to 16, 9 to 9, 8 to 9, 7 to 9), Color(0xFF00CCCC)),
    // Red: very bottom → col 5 → row 15 → col 9 → right to brain
    DecoPath(listOf(0 to 17, 5 to 17, 5 to 15, 9 to 15, 9 to 9, 8 to 9, 7 to 9), Color(0xFFFF3366)),
)

// ── Canonical enemy stats per type (consistent across all levels) ─────────────

private fun mkEnemy(type: Int, reward: Int): EnemySpec = when (type) {
    1    -> EnemySpec(20,  1.2f, reward, 1)
    2    -> EnemySpec(35,  1.5f, reward, 2)
    3    -> EnemySpec(55,  1.8f, reward, 3)
    4    -> EnemySpec(200, 1.0f, reward, 4, 1.8f)  // boss — slow but tanky
    5    -> EnemySpec(400, 0.8f, reward, 5, 2.0f)  // big boss
    else -> EnemySpec(20,  1.2f, reward, 1)
}

// ── Level definitions ─────────────────────────────────────────────────────────

private val TD_LEVELS = listOf(

    // Level 1 — clean white · single path · 9×16 · brain at (6,13)
    LevelDef(
        gridCols = 9, gridRows = 16,
        mainPaths = listOf(listOf(0 to 3, 6 to 3, 6 to 8, 2 to 8, 2 to 13, 6 to 13)),
        waves = listOf(
            WaveDef(List(10) { mkEnemy(1, 5) },                                         0.75f),
            WaveDef(List(7)  { mkEnemy(1, 5) } + List(7)  { mkEnemy(2, 6) },            0.65f),
            WaveDef(List(5)  { mkEnemy(1, 5) } + List(8)  { mkEnemy(2, 6) },            0.55f)
        ),
        lives = 20, startGold = 55, waveGoldBonus = 22
    ),

    // Level 2 — cable deco · two paths · 10×18 · brain at (7,9)
    LevelDef(
        gridCols = 10, gridRows = 18,
        mainPaths = listOf(
            listOf(0 to 2, 7 to 2, 7 to 5, 3 to 5, 3 to 9, 7 to 9),
            listOf(0 to 15, 4 to 15, 4 to 12, 7 to 12, 7 to 9)
        ),
        decoPaths = LEVEL23_DECO,
        waves = listOf(
            WaveDef(List(12) { mkEnemy(1, 5) },                                                           0.70f),
            WaveDef(List(8)  { mkEnemy(1, 5) } + List(8)  { mkEnemy(2, 6) },                             0.60f),
            WaveDef(List(5)  { mkEnemy(1, 5) } + List(6)  { mkEnemy(2, 6) } + List(6)  { mkEnemy(3, 7) }, 0.50f),
            WaveDef(List(6)  { mkEnemy(1, 5) } + List(5)  { mkEnemy(2, 6) } + List(8)  { mkEnemy(3, 7) }, 0.40f)
        ),
        lives = 15, startGold = 70, waveGoldBonus = 28
    ),

    // Level 3 — same map · rewires after wave 3 · escalating to impossible · brain at (7,9)
    LevelDef(
        gridCols = 10, gridRows = 18,
        mainPaths = listOf(
            listOf(0 to 2, 7 to 2, 7 to 5, 3 to 5, 3 to 9, 7 to 9),
            listOf(0 to 15, 4 to 15, 4 to 12, 7 to 12, 7 to 9)
        ),
        decoPaths = LEVEL23_DECO,
        rewireEnabled = true, firstRewireAtWave = 2,
        waves = listOf(
            // Waves 1-2: warm-up, unchanged
            WaveDef(List(10) { mkEnemy(1, 4) },                                                               0.85f),
            WaveDef(List(8)  { mkEnemy(1, 4) } + List(7)  { mkEnemy(2, 5) },                                0.70f),
            // Waves 3-10: ×3 enemy count vs original, mixed regular+boss throughout
            WaveDef(List(33) { mkEnemy(1, 4) } + List(33) { mkEnemy(2, 5) } + List(24) { mkEnemy(3, 6) },   0.38f),
            WaveDef(List(27) { mkEnemy(1, 4) } + List(27) { mkEnemy(2, 5) } + List(27) { mkEnemy(3, 6) } +
                    List(15) { mkEnemy(4, 8) } + List(20) { mkEnemy(1, 4) },                                  0.35f),
            WaveDef(List(36) { mkEnemy(1, 4) } + List(36) { mkEnemy(2, 5) } + List(30) { mkEnemy(3, 6) } +
                    List(24) { mkEnemy(4, 8) } + List(12) { mkEnemy(5, 12) } + List(18) { mkEnemy(1, 4) },   0.22f),
            WaveDef(List(27) { mkEnemy(1, 4) } + List(27) { mkEnemy(2, 5) } + List(24) { mkEnemy(3, 6) } +
                    List(18) { mkEnemy(4, 8) } + List(9)  { mkEnemy(5, 12) } + List(24) { mkEnemy(2, 5) },   0.25f),
            WaveDef(List(36) { mkEnemy(2, 5) } + List(33) { mkEnemy(3, 6) } + List(18) { mkEnemy(4, 8) } +
                    List(15) { mkEnemy(5, 12) } + List(30) { mkEnemy(1, 4) },                                 0.20f),
            WaveDef(List(54) { mkEnemy(1, 4) } + List(45) { mkEnemy(2, 5) } + List(33) { mkEnemy(3, 6) } +
                    List(18) { mkEnemy(4, 8) } + List(15) { mkEnemy(5, 12) } + List(20) { mkEnemy(2, 5) },   0.15f),
            WaveDef(List(30) { mkEnemy(5, 12) } + List(60) { mkEnemy(1, 4) } + List(30) { mkEnemy(4, 8) } +
                    List(45) { mkEnemy(2, 5) } + List(30) { mkEnemy(3, 6) },                                  0.15f),
            WaveDef(List(80) { mkEnemy(1, 4) } + List(60) { mkEnemy(2, 5) } + List(45) { mkEnemy(3, 6) } +
                    List(30) { mkEnemy(4, 8) } + List(20) { mkEnemy(5, 12) } + List(50) { mkEnemy(1, 4) } +
                    List(20) { mkEnemy(5, 12) },                                                               0.12f)
        ),
        lives = 25, startGold = 120, waveGoldBonus = 30, waveDelay = 2f
    )
)

// ── Geometry helpers ──────────────────────────────────────────────────────────

private fun expandPath(pts: List<Pair<Int, Int>>): Set<Pair<Int, Int>> {
    val out = mutableSetOf<Pair<Int, Int>>()
    for (i in 0 until pts.size - 1) {
        val (c0, r0) = pts[i]; val (c1, r1) = pts[i + 1]
        if (r0 == r1) for (c in minOf(c0, c1)..maxOf(c0, c1)) out += c to r0
        else          for (r in minOf(r0, r1)..maxOf(r0, r1)) out += c0 to r
    }
    return out
}

private fun segLen(path: List<Pair<Int, Int>>, idx: Int): Float {
    if (idx >= path.size - 1) return 0f
    val (c0, r0) = path[idx]; val (c1, r1) = path[idx + 1]
    return sqrt((c1 - c0).toFloat().pow(2) + (r1 - r0).toFloat().pow(2))
}

private fun enemyGridPos(e: LiveEnemy, paths: List<List<Pair<Int, Int>>>): Pair<Float, Float> {
    val path = paths.getOrElse(e.pathIdx) { paths[0] }
    val si = e.segIdx.coerceAtMost(path.size - 2)
    val (c0, r0) = path[si]; val (c1, r1) = path[si + 1]
    val t = e.segT.coerceIn(0f, 1f)
    return (c0 + (c1 - c0) * t + 0.5f) to (r0 + (r1 - r0) * t + 0.5f)
}

/**
 * Returns cells that can accept towers: perpendicular to each path segment
 * (beside the path, not on it, and not in the 3×3 area around the brain terminal).
 */
private fun validTowerSpots(
    paths: List<List<Pair<Int, Int>>>,
    cols: Int, rows: Int
): Set<Pair<Int, Int>> {
    val allPathCells = paths.flatMap { expandPath(it) }.toSet()
    val spots = mutableSetOf<Pair<Int, Int>>()
    for (path in paths) {
        for (i in 0 until path.size - 1) {
            val (c0, r0) = path[i]; val (c1, r1) = path[i + 1]
            if (r0 == r1) {                                    // horizontal → above & below
                val minC = minOf(c0, c1)
                for (c in minC..maxOf(c0, c1)) {
                    if ((c - minC) % 2 != 0) continue         // every other cell only
                    if (r0 - 1 >= 0)   spots += c to r0 - 1
                    if (r0 + 1 < rows) spots += c to r0 + 1
                }
            } else {                                           // vertical → left & right
                val minR = minOf(r0, r1)
                for (r in minR..maxOf(r0, r1)) {
                    if ((r - minR) % 2 != 0) continue         // every other cell only
                    if (c0 - 1 >= 0)   spots += c0 - 1 to r
                    if (c0 + 1 < cols) spots += c0 + 1 to r
                }
            }
        }
    }
    spots.removeAll(allPathCells)
    // Remove 5×5 area around each terminal (brain now occupies 3×3)
    for (path in paths) {
        val (tc, tr) = path.last()
        for (dc in -2..2) for (dr in -2..2) spots.remove((tc + dc) to (tr + dr))
    }
    return spots
}

// ── Public entry point ────────────────────────────────────────────────────────

@Composable
fun TowerDefenseGame(onComplete: () -> Unit) {
    var levelIdx       by remember { mutableIntStateOf(0) }
    var resetKey       by remember { mutableIntStateOf(0) }
    var totalReached   by remember { mutableIntStateOf(0) }
    key(levelIdx, resetKey) {
        TDLevelScreen(
            def            = TD_LEVELS[levelIdx],
            levelNum       = levelIdx + 1,
            initialReached = totalReached,
            onWin          = { reached ->
                totalReached = reached
                if (levelIdx < TD_LEVELS.lastIndex) levelIdx++ else onComplete()
            },
            onLose         = { reached ->
                totalReached = reached
                if (levelIdx == 2) onComplete() else resetKey++
            }
        )
    }
}

// ── Level screen ──────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun TDLevelScreen(
    def: LevelDef, levelNum: Int,
    initialReached: Int = 0,
    onWin: (Int) -> Unit, onLose: (Int) -> Unit
) {
    // ── Sprite loading ────────────────────────────────────────────────────────
    val ctx = LocalContext.current
    val res = ctx.resources
    val brain1Img = remember { BitmapFactory.decodeResource(res, R.drawable.td_brain1).asImageBitmap() }
    val brain2Img = remember { BitmapFactory.decodeResource(res, R.drawable.td_brain2).asImageBitmap() }
    val brain3Img = remember { BitmapFactory.decodeResource(res, R.drawable.td_brain3).asImageBitmap() }
    val enemy1Img = remember { BitmapFactory.decodeResource(res, R.drawable.td_enemy1).asImageBitmap() }
    val enemy2Img = remember { BitmapFactory.decodeResource(res, R.drawable.td_enemy2).asImageBitmap() }
    val enemy3Img = remember { BitmapFactory.decodeResource(res, R.drawable.td_enemy3).asImageBitmap() }
    val enemy4Img = remember { BitmapFactory.decodeResource(res, R.drawable.td_enemy4).asImageBitmap() }
    val enemy5Img = remember { BitmapFactory.decodeResource(res, R.drawable.td_enemy5).asImageBitmap() }
    val towerImg  = remember { BitmapFactory.decodeResource(res, R.drawable.td_tower).asImageBitmap() }
    val tower2Img = remember { BitmapFactory.decodeResource(res, R.drawable.td_tower2).asImageBitmap() }

    val enemyImgs = listOf(enemy1Img, enemy2Img, enemy3Img, enemy4Img, enemy5Img)

    // ── Sound ─────────────────────────────────────────────────────────────────
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(16)   // enough concurrent streams to never cut each other off
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
    }
    // en1–en8: random pool for types 1–3 · en9: enemy4 · en10: enemy5
    val soundIds = remember {
        listOf(R.raw.en1, R.raw.en2, R.raw.en3, R.raw.en4,
               R.raw.en5, R.raw.en6, R.raw.en7, R.raw.en8,
               R.raw.en9, R.raw.en10)
            .map { soundPool.load(ctx, it, 1) }
    }
    DisposableEffect(Unit) { onDispose { soundPool.release() } }

    fun playEnemySound(enemyType: Int, vol: Float = 1f) {
        val id = when (enemyType) {
            4    -> soundIds[8]
            5    -> soundIds[9]
            else -> soundIds[(0 until 8).random()]
        }
        soundPool.play(id, vol, vol, 1, 0, 1f)
    }

    // ── Game state ────────────────────────────────────────────────────────────
    var phase              by remember { mutableStateOf(TDPhase.PREP) }
    var lives              by remember { mutableIntStateOf(def.lives) }
    var gold               by remember { mutableIntStateOf(def.startGold) }
    var waveIdx            by remember { mutableIntStateOf(0) }
    var prepTimer          by remember { mutableFloatStateOf(3f) }

    var spawnTimer         by remember { mutableFloatStateOf(0f) }
    var spawnIdx           by remember { mutableIntStateOf(0) }
    var uidCounter         by remember { mutableIntStateOf(0) }
    var activatedDeco      by remember { mutableIntStateOf(0) }
    var selectedType       by remember { mutableStateOf(TowerType.DAMAGE) }
    var tick               by remember { mutableLongStateOf(0L) }
    var sellMsg            by remember { mutableStateOf("") }
    var sellMsgOn          by remember { mutableStateOf(false) }
    var enemiesReached     by remember { mutableIntStateOf(initialReached) }
    var ambientTimer       by remember { mutableFloatStateOf(2f) }

    val enemies = remember { mutableListOf<LiveEnemy>() }
    val towers  = remember { mutableListOf<PlacedTower>() }

    val activePaths = remember(activatedDeco) {
        def.mainPaths + def.decoPaths.take(activatedDeco).map { it.pts }
    }
    val validSpots  = remember(activatedDeco) { validTowerSpots(activePaths, def.gridCols, def.gridRows) }

    // Sell message auto-hide
    LaunchedEffect(sellMsgOn) { if (sellMsgOn) { delay(1200); sellMsgOn = false } }

    // ── Game loop ─────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        var lastNs = System.nanoTime()
        while (phase != TDPhase.WON && phase != TDPhase.LOST) {
            delay(16)
            val now = System.nanoTime()
            val dt  = ((now - lastNs) / 1_000_000_000f).coerceAtMost(0.05f)
            lastNs  = now
            tick++

            // Fade range circles over time
            for (t in towers) if (t.rangeTimer > 0f) t.rangeTimer = (t.rangeTimer - dt).coerceAtLeast(0f)

            when (phase) {

                TDPhase.PREP -> {
                    prepTimer -= dt
                    if (prepTimer <= 0f) { phase = TDPhase.WAVE; spawnTimer = 0f; spawnIdx = 0 }
                }

                TDPhase.WAVE -> {
                    val wave  = def.waves[waveIdx]
                    val paths = def.mainPaths + def.decoPaths.take(activatedDeco).map { it.pts }

                    // Spawn
                    spawnTimer -= dt
                    if (spawnTimer <= 0f && spawnIdx < wave.enemies.size) {
                        val spec = wave.enemies[spawnIdx++]
                        enemies += LiveEnemy(uidCounter++, spec, uidCounter % paths.size)
                        playEnemySound(spec.enemyType)
                        spawnTimer = wave.spawnInterval
                    }

                    // Ambient sounds: random living enemy makes noise every 1–3 s
                    ambientTimer -= dt
                    if (ambientTimer <= 0f && enemies.isNotEmpty()) {
                        val alive = enemies.filter { !it.dead && !it.reached }
                        if (alive.isNotEmpty()) {
                            playEnemySound(alive.random().spec.enemyType, 0.55f)
                        }
                        ambientTimer = 1f + (0 until 200).random() / 100f  // 1–3 s
                    }

                    val toRemove = mutableListOf<LiveEnemy>()

                    // Move enemies
                    for (e in enemies) {
                        if (e.dead || e.reached) { toRemove += e; continue }
                        if (e.slowTimer > 0f) {
                            e.slowTimer = (e.slowTimer - dt).coerceAtLeast(0f)
                            if (e.slowTimer == 0f) e.speedMult = 1f
                        }
                        val path = paths.getOrElse(e.pathIdx) { paths[0] }
                        val sl = segLen(path, e.segIdx)
                        if (sl > 0f) {
                            e.segT += (e.spec.speed * e.speedMult * dt) / sl
                            while (e.segT >= 1f) {
                                if (e.segIdx < path.size - 2) { e.segIdx++; e.segT -= 1f }
                                else { e.reached = true; lives--; enemiesReached++; break }
                            }
                        }
                        if (e.reached || e.dead) toRemove += e
                    }

                    // Tower movement & attack
                    for (t in towers) {
                        when (t.state) {
                            TowerState.IDLE -> {
                                val tgt = enemies.filter { !it.dead && !it.reached }
                                    .minByOrNull { e ->
                                        enemyGridPos(e, paths).let { (gx, gy) ->
                                            (gx - t.baseX).pow(2) + (gy - t.baseY).pow(2)
                                        }
                                    }?.takeIf { e ->
                                        enemyGridPos(e, paths).let { (gx, gy) ->
                                            sqrt((gx - t.baseX).pow(2) + (gy - t.baseY).pow(2)) < t.range
                                        }
                                    }
                                if (tgt != null) { t.targetUid = tgt.uid; t.state = TowerState.ATTACKING }
                            }
                            TowerState.ATTACKING -> {
                                val tgt = enemies.firstOrNull { it.uid == t.targetUid && !it.dead && !it.reached }
                                if (tgt == null) { t.state = TowerState.RETURNING } else {
                                    val (gx, gy) = enemyGridPos(tgt, paths)
                                    val dx = gx - t.posX; val dy = gy - t.posY
                                    val dist = sqrt(dx * dx + dy * dy)
                                    if (dist < 0.35f) {
                                        when (t.type) {
                                            TowerType.DAMAGE -> { tgt.hp -= 15; if (tgt.dead) { gold += tgt.spec.reward; toRemove += tgt } }
                                            TowerType.SLOW   -> { tgt.speedMult = 0.4f; tgt.slowTimer = 3f }
                                        }
                                        t.state = TowerState.RETURNING
                                    } else {
                                        t.posX += (dx / dist) * t.attackSpeed * dt
                                        t.posY += (dy / dist) * t.attackSpeed * dt
                                    }
                                }
                            }
                            TowerState.RETURNING -> {
                                val dx = t.baseX - t.posX; val dy = t.baseY - t.posY
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist < 0.1f) { t.posX = t.baseX; t.posY = t.baseY; t.targetUid = -1; t.state = TowerState.IDLE }
                                else { t.posX += (dx / dist) * t.returnSpeed * dt; t.posY += (dy / dist) * t.returnSpeed * dt }
                            }
                        }
                    }

                    enemies.removeAll(toRemove.toSet())
                    if (lives <= 0) { phase = TDPhase.LOST; continue }
                    if (spawnIdx >= wave.enemies.size && enemies.isEmpty()) {
                        if (waveIdx >= def.waves.lastIndex) {
                            phase = TDPhase.WON
                        } else {
                            // Give gold + rewire immediately, silently wait 2s then next wave
                            gold += def.waveGoldBonus
                            if (def.rewireEnabled && waveIdx >= def.firstRewireAtWave && activatedDeco < def.decoPaths.size) {
                                val newCells = expandPath(def.decoPaths[activatedDeco].pts)
                                towers.removeAll { it.col to it.row in newCells }
                                activatedDeco++
                            }
                            waveIdx++; enemies.clear(); prepTimer = 2f; phase = TDPhase.PREP
                        }
                    }
                }

                else -> {}
            }
        }
    }

    LaunchedEffect(phase) {
        when (phase) {
            TDPhase.WON  -> { delay(3000); onWin(enemiesReached) }
            TDPhase.LOST -> { delay(6000); onLose(enemiesReached) }
            else -> {}
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    val brainFill = (def.lives - lives).toFloat() / def.lives.toFloat()
    val isLandscape = LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Brain image progresses with damage taken, same across all levels
    val brainImg = when {
        enemiesReached < 3  -> brain1Img
        enemiesReached < 8  -> brain2Img
        else                -> brain3Img
    }

    val hudLabel = when (phase) {
        TDPhase.PREP   -> if (waveIdx == 0) "Level $levelNum" else "Wave ${waveIdx + 1}"
        TDPhase.WON    -> "Complete!"
        TDPhase.LOST   -> "Failed"
        TDPhase.PAUSED -> "Paused"
        else           -> "Wave ${waveIdx + 1}"
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
    if (isLandscape) {
        // ── Landscape: game fills full height, HUD+selector in right sidebar ──
        Row(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                val gameW = constraints.maxWidth.toFloat()
                val gameH = constraints.maxHeight.toFloat()
                val cs    = min(gameW / def.gridCols, gameH / def.gridRows)
                val ox    = (gameW - cs * def.gridCols) / 2f
                val oy    = (gameH - cs * def.gridRows) / 2f

                @Suppress("UNUSED_EXPRESSION") tick

                Canvas(Modifier.fillMaxSize()) {
                    tdDrawBackground(def, levelNum, activatedDeco, validSpots, cs, ox, oy, tick)
                    tdDrawTowers(towers, towerImg, tower2Img, cs, ox, oy)
                    tdDrawEnemies(enemies, activePaths, enemyImgs, cs, ox, oy)
                    def.mainPaths.map { it.last() }.toSet().forEach { cell ->
                        val srcPath = def.mainPaths.first { it.last() == cell }
                        tdDrawBrain(srcPath, brainImg, brainFill, cs, ox, oy)
                    }
                }

                // Tap: place or sell tower
                Box(Modifier.fillMaxSize().pointerInput(activatedDeco, validSpots, cs, ox, oy, selectedType) {
                    detectTapGestures { tap ->
                        if (phase != TDPhase.PREP && phase != TDPhase.WAVE) return@detectTapGestures
                        val col = ((tap.x - ox) / cs).toInt()
                        val row = ((tap.y - oy) / cs).toInt()
                        if (col !in 0 until def.gridCols || row !in 0 until def.gridRows) return@detectTapGestures

                        val existing = towers.firstOrNull { it.col == col && it.row == row }
                        if (existing != null) {
                            // Sell
                            val refund = if (existing.type == TowerType.DAMAGE) TOWER_DMG_COST / 2 else TOWER_SLOW_COST / 2
                            towers.remove(existing)
                            gold += refund
                            sellMsg = "+\$$refund"; sellMsgOn = true
                        } else {
                            // Place
                            val cost = if (selectedType == TowerType.DAMAGE) TOWER_DMG_COST else TOWER_SLOW_COST
                            if (col to row in validSpots && gold >= cost) {
                                towers += PlacedTower(col, row, selectedType)
                                gold -= cost
                            }
                        }
                    }
                })

                TDPhaseOverlays(phase, levelNum, onResume = { phase = TDPhase.WAVE })
                if (sellMsgOn) Text(sellMsg, color = Color(0xFFFFAA00), fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            }
        }
            // ── Landscape sidebar: compact HUD + tower buttons ────────────────
            Column(
                Modifier.width(128.dp).fillMaxHeight()
                    .background(Color(0xFF111111)).padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("LEVEL $levelNum", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(hudLabel, color = Color(0xFFAA88FF), fontSize = 9.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("\$$gold", color = Color(0xFFFFAA00), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("♥ $lives", color = Color(0xFFFF5555), fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                if (phase == TDPhase.WAVE || phase == TDPhase.PAUSED) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .padding(vertical = 8.dp)
                            .pointerInput(phase) {
                                detectTapGestures {
                                    phase = if (phase == TDPhase.WAVE) TDPhase.PAUSED else TDPhase.WAVE
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (phase == TDPhase.PAUSED) "▶  Resume" else "⏸  Pause",
                            color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("Towers", color = Color(0xFF666666), fontSize = 9.sp)
                Spacer(Modifier.height(6.dp))
                TDTypeBtn("Attack", towerImg,  TOWER_DMG_COST,  selectedType == TowerType.DAMAGE) { selectedType = TowerType.DAMAGE }
                Spacer(Modifier.height(8.dp))
                TDTypeBtn("Slow",   tower2Img, TOWER_SLOW_COST, selectedType == TowerType.SLOW)   { selectedType = TowerType.SLOW }
                Spacer(Modifier.height(8.dp))
                Text("Tap tower\nto sell (½)", color = Color(0xFF555555), fontSize = 8.sp, textAlign = TextAlign.Center)
            }
        }
    } else {
        // ── Portrait: HUD top, game middle, selector bottom ───────────────────
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp)
                    .background(Color(0xFF111111)).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LEVEL $levelNum", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(hudLabel, color = Color(0xFFAA88FF), fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\$$gold", color = Color(0xFFFFAA00), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    if (phase == TDPhase.WAVE || phase == TDPhase.PAUSED) {
                        Box(
                            Modifier.background(Color.White, RoundedCornerShape(6.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .pointerInput(phase) {
                                    detectTapGestures {
                                        phase = if (phase == TDPhase.WAVE) TDPhase.PAUSED else TDPhase.WAVE
                                    }
                                }
                        ) {
                            Text(if (phase == TDPhase.PAUSED) "▶" else "⏸",
                                color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val gameW = constraints.maxWidth.toFloat()
                    val gameH = constraints.maxHeight.toFloat()
                    val cs    = min(gameW / def.gridCols, gameH / def.gridRows)
                    val ox    = (gameW - cs * def.gridCols) / 2f
                    val oy    = (gameH - cs * def.gridRows) / 2f
                    @Suppress("UNUSED_EXPRESSION") tick
                    Canvas(Modifier.fillMaxSize()) {
                        tdDrawBackground(def, levelNum, activatedDeco, validSpots, cs, ox, oy, tick)
                        tdDrawTowers(towers, towerImg, tower2Img, cs, ox, oy)
                        tdDrawEnemies(enemies, activePaths, enemyImgs, cs, ox, oy)
                        def.mainPaths.map { it.last() }.toSet().forEach { cell ->
                            val srcPath = def.mainPaths.first { it.last() == cell }
                            tdDrawBrain(srcPath, brainImg, brainFill, cs, ox, oy)
                        }
                    }
                    Box(Modifier.fillMaxSize().pointerInput(activatedDeco, validSpots, cs, ox, oy, selectedType) {
                        detectTapGestures { tap ->
                            if (phase != TDPhase.PREP && phase != TDPhase.WAVE) return@detectTapGestures
                            val col = ((tap.x - ox) / cs).toInt()
                            val row = ((tap.y - oy) / cs).toInt()
                            if (col !in 0 until def.gridCols || row !in 0 until def.gridRows) return@detectTapGestures
                            val existing = towers.firstOrNull { it.col == col && it.row == row }
                            if (existing != null) {
                                val refund = if (existing.type == TowerType.DAMAGE) TOWER_DMG_COST / 2 else TOWER_SLOW_COST / 2
                                towers.remove(existing); gold += refund; sellMsg = "+\$$refund"; sellMsgOn = true
                            } else {
                                val cost = if (selectedType == TowerType.DAMAGE) TOWER_DMG_COST else TOWER_SLOW_COST
                                if (col to row in validSpots && gold >= cost) { towers += PlacedTower(col, row, selectedType); gold -= cost }
                            }
                        }
                    })
                    TDPhaseOverlays(phase, levelNum, onResume = { phase = TDPhase.WAVE })
                    if (sellMsgOn) Text(sellMsg, color = Color(0xFFFFAA00), fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                }
            }
            TowerSelector(selectedType, towerImg, tower2Img) { selectedType = it }
        }
    }
    } // end outer Box
}

// ── UI helpers ────────────────────────────────────────────────────────────────

@Composable
private fun TowerSelector(
    selected: TowerType, towerImg: ImageBitmap, tower2Img: ImageBitmap,
    onSelect: (TowerType) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(96.dp)
            .background(Color(0xFFF0F0F0)).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Place\ntower:", color = Color(0xFF444444), fontSize = 12.sp, textAlign = TextAlign.Center)
        TDTypeBtn("Attack", towerImg,  TOWER_DMG_COST,  selected == TowerType.DAMAGE) { onSelect(TowerType.DAMAGE) }
        TDTypeBtn("Slow",   tower2Img, TOWER_SLOW_COST, selected == TowerType.SLOW)   { onSelect(TowerType.SLOW) }
        Spacer(Modifier.weight(1f))
        Text("Tap placed\ntower to sell\nfor half price", color = Color(0xFF888888), fontSize = 10.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun TDTypeBtn(
    label: String, img: ImageBitmap, cost: Int,
    active: Boolean, onClick: () -> Unit
) {
    val bg = if (active) Color(0xFF222222) else Color(0xFFDDDDDD)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pointerInput(Unit) { detectTapGestures { onClick() } }
    ) {
        Box(
            Modifier.size(58.dp).background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val hw = (size.width  * 0.38f).toInt()
                val hh = (size.height * 0.38f).toInt()
                val cx = size.width  / 2f
                val cy = size.height / 2f
                drawImage(img,
                    dstOffset = IntOffset((cx - hw).toInt(), (cy - hh).toInt()),
                    dstSize   = IntSize(hw * 2, hh * 2))
            }
        }
        Text("\$$cost", color = if (active) Color(0xFF222222) else Color(0xFF666666),
            fontSize = 10.sp, textAlign = TextAlign.Center)
        Text(label, color = if (active) Color(0xFF222222) else Color(0xFF888888),
            fontSize = 9.sp, textAlign = TextAlign.Center)
    }
}


@Composable
private fun BoxScope.TDPhaseOverlays(
    phase: TDPhase, levelNum: Int,
    onResume: () -> Unit
) {
    when (phase) {
        TDPhase.PREP -> {
            // All PREP phases are silent — waves auto-start after countdown
        }
        TDPhase.PAUSED -> Box(Modifier.fillMaxSize().background(Color(0xBB111111)), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PAUSED", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.background(Color(0xFF33AA33), RoundedCornerShape(8.dp))
                        .padding(horizontal = 40.dp, vertical = 14.dp)
                        .pointerInput(Unit) { detectTapGestures { onResume() } },
                    contentAlignment = Alignment.Center
                ) { Text("▶  RESUME", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
        TDPhase.WON -> Box(Modifier.fillMaxSize().background(Color(0xCC004400)), Alignment.Center) {
            val msg = when (levelNum) {
                1    -> "Good, you survived\nthe morning!"
                2    -> "Lunch break was fun..."
                else -> "Complete!"
            }
            Text(msg, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        TDPhase.LOST -> Box(Modifier.fillMaxSize().background(Color(0xCCBB0000)), Alignment.Center) {
            val msg = if (levelNum == 3)
                "So much information\nand so little bandwidth.\nTime for a brain chip\nimplant... Probably."
            else "Defeated\nRetrying…"
            Text(msg, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        else -> {}
    }
}

// ── Canvas draw functions ─────────────────────────────────────────────────────

private fun DrawScope.tdDrawBackground(
    def: LevelDef, levelNum: Int, activatedDeco: Int,
    validSpots: Set<Pair<Int, Int>>,
    cs: Float, ox: Float, oy: Float, tick: Long
) {
    // Base fill — light gray for invalid cells
    drawRect(Color(0xFFDDDDDD), Offset(ox, oy), Size(cs * def.gridCols, cs * def.gridRows))

    // Valid tower spots: white
    for (r in 0 until def.gridRows) for (c in 0 until def.gridCols) {
        if (c to r in validSpots)
            drawRect(Color.White, Offset(ox + c * cs, oy + r * cs), Size(cs, cs))
    }

    // Main paths: wire-style narrow lines instead of filled cells
    for (path in def.mainPaths) {
        for (i in 0 until path.size - 1) {
            val (c0, r0) = path[i]; val (c1, r1) = path[i + 1]
            drawLine(
                Color(0xFF1A1A1A),
                Offset(ox + (c0 + 0.5f) * cs, oy + (r0 + 0.5f) * cs),
                Offset(ox + (c1 + 0.5f) * cs, oy + (r1 + 0.5f) * cs),
                strokeWidth = cs * 0.42f,
                cap = StrokeCap.Round
            )
        }
    }

    // Decorative cables (Level 2+): faint lines; Level 3 activates them one by one
    if (levelNum < 2) return
    val pulse = sin(tick.toFloat() * 0.055f) * 0.12f

    for ((idx, deco) in def.decoPaths.withIndex()) {
        val isActive = idx < activatedDeco
        val alpha = when {
            isActive && idx == activatedDeco - 1 -> (0.85f + pulse).coerceIn(0.7f, 1f)
            isActive  -> 0.85f
            else      -> 0.20f
        }
        val strokeW = if (isActive) cs * 0.30f else 1.5f

        // Wire lines centre-to-centre
        for (i in 0 until deco.pts.size - 1) {
            val (c0, r0) = deco.pts[i]; val (c1, r1) = deco.pts[i + 1]
            drawLine(
                deco.color.copy(alpha = alpha),
                Offset(ox + (c0 + 0.5f) * cs, oy + (r0 + 0.5f) * cs),
                Offset(ox + (c1 + 0.5f) * cs, oy + (r1 + 0.5f) * cs),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
        // Dots at waypoints
        for ((c, r) in deco.pts) {
            drawCircle(deco.color.copy(alpha = alpha), cs * 0.09f,
                Offset(ox + (c + 0.5f) * cs, oy + (r + 0.5f) * cs))
        }
    }

    // Level 3: draw connecting lines from each deco cable endpoint toward the brain terminal
    if (levelNum == 3) {
        val (bc, br) = def.mainPaths.first().last()
        val brainCx = ox + (bc + 0.5f) * cs
        val brainCy = oy + (br + 0.5f) * cs
        for ((idx, deco) in def.decoPaths.withIndex()) {
            val isActive = idx < activatedDeco
            val alpha = if (isActive) 0.65f else 0.08f
            val strokeW = if (isActive) 2.5f else 1f
            val (ec, er) = deco.pts.last()
            drawLine(
                deco.color.copy(alpha = alpha),
                Offset(ox + (ec + 0.5f) * cs, oy + (er + 0.5f) * cs),
                Offset(brainCx, brainCy),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Brain: drawn 2×2 cells, rotated –90° for horizontal exits, with fill bar.
 */
private fun DrawScope.tdDrawBrain(
    pts: List<Pair<Int, Int>>, img: ImageBitmap,
    fill: Float, cs: Float, ox: Float, oy: Float
) {
    val (col, row) = pts.last()
    val cx = ox + (col + 0.5f) * cs
    val cy = oy + (row + 0.5f) * cs
    val isHorizontalExit = pts[pts.size - 2].second == row

    // 3×3 destination rect
    val left = (cx - cs * 1.5f).toInt(); val top  = (cy - cs * 1.5f).toInt()
    val w    = (cs * 3f).toInt();         val h    = (cs * 3f).toInt()

    withTransform(
        transformBlock = { if (isHorizontalExit) rotate(-90f, Offset(cx, cy)) },
        drawBlock      = { drawImage(img, dstOffset = IntOffset(left, top), dstSize = IntSize(w, h)) }
    )

    // Damage fill bar (drawn in screen space, not rotated)
    val barW = cs * 2.2f
    val barH = 7f
    val barX = cx - barW / 2f
    val barY = cy + cs * 1.15f
    drawRect(Color(0xFFCCCCCC), Offset(barX, barY), Size(barW, barH))
    if (fill > 0f) drawRect(Color(0xFFCC2200), Offset(barX, barY), Size(barW * fill.coerceIn(0f, 1f), barH))
}

/** Tower sprites. Range circle shown briefly after placement, then fades. */
private fun DrawScope.tdDrawTowers(
    towers: List<PlacedTower>,
    towerImg: ImageBitmap, tower2Img: ImageBitmap,
    cs: Float, ox: Float, oy: Float
) {
    for (t in towers) {
        val bx = ox + t.baseX * cs; val by = oy + t.baseY * cs
        val cx = ox + t.posX  * cs; val cy = oy + t.posY  * cs
        val hw = (cs * 0.5f).toInt(); val hh = (cs * 0.5f).toInt()
        val img = if (t.type == TowerType.DAMAGE) towerImg else tower2Img

        // Range — visible only briefly after placement
        if (t.rangeTimer > 0f) {
            val rAlpha = (t.rangeTimer / 2f).coerceIn(0f, 1f) * 0.15f
            drawCircle(Color(0xFF000000).copy(alpha = rAlpha), t.range * cs, Offset(bx, by))
        }

        if (t.state != TowerState.IDLE) {
            drawLine(Color(0x44000000), Offset(bx, by), Offset(cx, cy), strokeWidth = 1.5f)
            // Ghost at base
            drawImage(img,
                dstOffset = IntOffset((bx - hw).toInt(), (by - hh).toInt()),
                dstSize   = IntSize(hw * 2, hh * 2), alpha = 0.22f)
        }
        if (t.type == TowerType.SLOW) drawCircle(Color(0x220088FF), cs * 0.36f, Offset(cx, cy))
        drawImage(img,
            dstOffset = IntOffset((cx - hw).toInt(), (cy - hh).toInt()),
            dstSize   = IntSize(hw * 2, hh * 2))
    }
}

/** Enemy sprites with HP bar; slow tint if slowed. */
private fun DrawScope.tdDrawEnemies(
    enemies: List<LiveEnemy>, paths: List<List<Pair<Int, Int>>>,
    enemyImgs: List<ImageBitmap>, cs: Float, ox: Float, oy: Float
) {
    for (e in enemies) {
        if (e.dead || e.reached) continue
        val (gx, gy) = enemyGridPos(e, paths)
        val cx = ox + gx * cs; val cy = oy + gy * cs
        val sz = cs * e.spec.scale
        val hw = (sz * 0.5f).toInt(); val hh = (sz * 0.5f).toInt()

        if (e.speedMult < 1f) drawCircle(Color(0x550088FF), sz * 0.6f, Offset(cx, cy))

        val img = enemyImgs.getOrElse(e.spec.enemyType - 1) { enemyImgs[0] }
        drawImage(img,
            dstOffset = IntOffset((cx - hw).toInt(), (cy - hh).toInt()),
            dstSize   = IntSize(hw * 2, hh * 2))

        val barW = sz * 0.7f
        val barX = cx - barW / 2f; val barY = cy - sz * 0.56f - 5f
        drawRect(Color(0xFFCCCCCC), Offset(barX, barY), Size(barW, 3f))
        val frac = (e.hp.toFloat() / e.spec.hp).coerceIn(0f, 1f)
        val hpCol = when { frac > 0.5f -> Color(0xFF00CC00); frac > 0.25f -> Color(0xFFFF8800); else -> Color(0xFFFF0000) }
        drawRect(hpCol, Offset(barX, barY), Size(barW * frac, 3f))
    }
}
