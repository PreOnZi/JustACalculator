package com.fictioncutshort.justacalculator.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════
//  TERRAIN DESIGNER
//  ─────────────────────────────────────────────────────────────────────────────
//  Edit the list below to reshape the battlefield.
//
//  Each entry:   x-fraction (0.0 = left edge   →   1.0 = right edge)
//             to y-fraction (0.0 = screen top   →   1.0 = screen bottom)
//
//  Lower y  →  taller hill          Higher y  →  deeper valley
//  Practical range: y ≈ 0.42 (tall peak) … 0.82 (valley floor)
//
//  Rules:
//    • First point x = 0.0, last point x = 1.0
//    • Points must be in strictly ascending x order
//    • Keep adjacent y values within ~0.18 of each other for smooth slopes
// ═══════════════════════════════════════════════════════════════════════════════
private val TERRAIN_POINTS = listOf(
    0.00f to 0.72f,   // left flat — subtle escape gate lives here
    0.08f to 0.71f,
    0.16f to 0.57f,   // first hill
    0.24f to 0.73f,   // dip
    0.33f to 0.76f,   // shallow valley
    0.42f to 0.61f,   // central hill
    0.50f to 0.74f,
    0.58f to 0.64f,   // second hill
    0.68f to 0.77f,   // valley before enemy
    0.78f to 0.67f,   // enemy hill
    0.88f to 0.71f,
    1.00f to 0.72f    // right flat — enemies spawn here
)

// ═══════════════════════════════════════════════════════════════════════════════
//  BALANCE KNOBS — tweak feel without touching game logic
// ═══════════════════════════════════════════════════════════════════════════════
private const val GRAVITY           = 650f    // px/s²
private const val MAX_SHOOT_SPEED   = 920f    // px/s at full power
private const val TANK_MOVE_SPEED   = 130f    // px/s — player movement
private const val EXPLOSION_RADIUS  = 58f     // px
private const val TANK_W            = 52f
private const val TANK_H            = 22f
private const val BARREL_LEN        = 36f
private const val PLAYER_MAX_HP     = 3
private const val ENEMY_HP          = 2
// After these turn numbers an extra enemy tank spawns (one per entry)
private val REINFORCE_AT_TURNS      = listOf(4, 8)
// Turn on which the player's gun permanently jams
private const val GUN_JAM_TURN      = 12
// Player must reach this x-fraction (LEFT side) to escape — gate is intentionally subtle
private const val ESCAPE_X           = 0.04f
// ═══════════════════════════════════════════════════════════════════════════════

// ── Data classes ──────────────────────────────────────────────────────────────

private data class TankState(
    val id: Int,
    val x: Float,
    val hp: Int,
    val isPlayer: Boolean,
    // 0° = right, 90° = straight up, 180° = left
    val gunAngle: Float = if (isPlayer) 55f else 125f,
    val power: Float    = 0.65f
)

private data class Projectile(
    val x: Float, val y: Float,
    val vx: Float, val vy: Float,
    val fromPlayer: Boolean
)

private data class Explosion(val x: Float, val y: Float, val age: Float = 0f)

private enum class Phase {
    INIT,
    PLAYER_TURN,
    PROJECTILE_FLYING,
    ENEMY_THINKING,
    ENEMY_PROJECTILE,
    REINFORCEMENTS,        // brief pause while new enemy tanks appear
    GUN_JAMMED_NOTICE,     // message to player that gun is gone
    GAME_OVER,
    ESCAPED
}

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
fun TankGame(onComplete: () -> Unit, onExit: () -> Unit) {
    val context  = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val prev = activity?.requestedOrientation
        // Lock to landscape for the whole game session — prevents portrait flip-flop.
        // rememberSaveable on showTankGame (in Calculatorcityview) handles any one-time
        // Activity recreation that may result from the first orientation change.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = prev ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    var canvasW       by remember { mutableStateOf(0f) }
    var canvasH       by remember { mutableStateOf(0f) }
    var sizeReady     by remember { mutableStateOf(false) }
    // Mutable runtime copy of TERRAIN_POINTS — deformed by explosions during play
    var terrainPoints by remember { mutableStateOf(TERRAIN_POINTS) }

    var tanks      by remember { mutableStateOf(emptyList<TankState>()) }
    var proj       by remember { mutableStateOf<Projectile?>(null) }
    var explosions by remember { mutableStateOf(emptyList<Explosion>()) }
    var phase      by remember { mutableStateOf(Phase.INIT) }
    var turnCount  by remember { mutableIntStateOf(0) }
    var gunJammed  by remember { mutableStateOf(false) }
    var statusMsg  by remember { mutableStateOf("") }

    // Button hold-state (set by UI, read by game loop)
    var moveLeft   by remember { mutableStateOf(false) }
    var moveRight  by remember { mutableStateOf(false) }
    var aimUp      by remember { mutableStateOf(false) }
    var aimDown    by remember { mutableStateOf(false) }

    // ── Terrain helpers (inline, capture canvasW/H/terrainPoints) ────────────
    fun groundY(px: Float): Float {
        if (canvasW <= 0f) return canvasH * 0.72f
        val xn  = (px / canvasW).coerceIn(0f, 1f)
        val pts = terrainPoints   // runtime state — updates when explosions deform terrain
        if (xn <= pts.first().first) return pts.first().second * canvasH
        if (xn >= pts.last().first)  return pts.last().second  * canvasH
        var i = 0
        while (i < pts.size - 1 && pts[i + 1].first < xn) i++
        val (x0, y0) = pts[i]; val (x1, y1) = pts[i + 1]
        val t = (xn - x0) / (x1 - x0)
        return (y0 + t * (y1 - y0)) * canvasH
    }
    fun tankTopY(tx: Float) = groundY(tx) - TANK_H

    // Crater a small dip in the terrain around explosion x (normalized coords)
    fun deformTerrain(ex: Float) {
        val xn = (ex / canvasW).coerceIn(0f, 1f)
        terrainPoints = terrainPoints.map { (px, py) ->
            val dist = kotlin.math.abs(px - xn)
            if (dist < 0.10f) {
                val t = 1f - dist / 0.10f
                px to (py + 0.038f * t * t).coerceAtMost(0.90f)
            } else px to py
        }
    }

    // ── Spawn initial tanks once canvas size is known ─────────────────────────
    LaunchedEffect(sizeReady) {
        if (!sizeReady) return@LaunchedEffect
        tanks = listOf(
            TankState(id = 0, x = canvasW * 0.22f, hp = PLAYER_MAX_HP, isPlayer = true),
            TankState(id = 1, x = canvasW * 0.85f, hp = ENEMY_HP,      isPlayer = false)
        )
        delay(700)
        phase     = Phase.PLAYER_TURN
        statusMsg = "Your turn — aim and fire"
    }

    // ── Main game loop (physics + movement) ───────────────────────────────────
    LaunchedEffect(Unit) {
        var lastMs = System.currentTimeMillis()
        while (true) {
            delay(16)
            val now = System.currentTimeMillis()
            val dt  = ((now - lastMs) / 1000f).coerceAtMost(0.05f)
            lastMs  = now

            if (!sizeReady || canvasW == 0f) continue
            val ph = phase  // local snapshot — avoids repeated reads

            // ── Player movement + aiming ───────────────────────────────────
            if (ph == Phase.PLAYER_TURN) {
                val p = tanks.firstOrNull { it.isPlayer } ?: continue
                var nx = p.x; var na = p.gunAngle
                if (moveLeft)               nx = p.x - TANK_MOVE_SPEED * dt
                if (moveRight)              nx = (p.x + TANK_MOVE_SPEED * dt).coerceAtMost(canvasW - TANK_W / 2f)
                if (!gunJammed && aimUp)    na = (p.gunAngle + 70f * dt).coerceAtMost(175f)
                if (!gunJammed && aimDown)  na = (p.gunAngle - 70f * dt).coerceAtLeast(5f)

                // Escape gate check — player drives left into the gate (gate is on the left)
                if (nx <= canvasW * ESCAPE_X) {
                    phase     = Phase.ESCAPED
                    statusMsg = "You escaped!"
                    continue
                }
                nx = nx.coerceAtLeast(TANK_W / 2f)  // normal left wall if past gate

                if (nx != p.x || na != p.gunAngle)
                    tanks = tanks.map { if (it.isPlayer) it.copy(x = nx, gunAngle = na) else it }
            }

            // ── Explosion age-out ──────────────────────────────────────────
            if (explosions.isNotEmpty())
                explosions = explosions
                    .map  { it.copy(age = it.age + dt * 1.8f) }
                    .filter { it.age < 1f }

            // ── Projectile physics ─────────────────────────────────────────
            val p = proj
            if (p != null && (ph == Phase.PROJECTILE_FLYING || ph == Phase.ENEMY_PROJECTILE)) {
                val nx  = p.x  + p.vx * dt
                val ny  = p.y  + p.vy * dt
                val nvy = p.vy + GRAVITY * dt

                // Left / right out of bounds — shot missed
                if (nx < -20f || nx > canvasW + 20f || ny > canvasH + 30f) {
                    proj  = null
                    phase = if (p.fromPlayer) nextPhaseAfterPlayerShot(++turnCount, tanks, gunJammed).also {
                        if (it == Phase.REINFORCEMENTS) statusMsg = "Enemy reinforcements!"
                        if (it == Phase.GUN_JAMMED_NOTICE) { gunJammed = true; statusMsg = "Your gun jammed!" }
                        if (it == Phase.ENEMY_THINKING)   statusMsg = "Enemy aiming..."
                    } else {
                        statusMsg = "Your turn"; Phase.PLAYER_TURN
                    }
                    continue
                }

                // Terrain collision
                if (ny >= groundY(nx)) {
                    explosions = explosions + Explosion(nx, groundY(nx))
                    deformTerrain(nx)   // crater the ground at impact

                    // Apply blast damage
                    tanks = tanks.mapNotNull { t ->
                        val dist = hypot(t.x - nx, tankTopY(t.x) - ny)
                        val dmg  = if (dist < EXPLOSION_RADIUS) 1 else 0
                        val newHp = t.hp - dmg
                        when {
                            newHp > 0  -> t.copy(hp = newHp)
                            t.isPlayer -> t.copy(hp = 0)   // keep player alive to show game-over
                            else       -> null              // remove destroyed enemy
                        }
                    }
                    proj = null

                    // Check player death
                    if ((tanks.firstOrNull { it.isPlayer }?.hp ?: 0) <= 0) {
                        phase     = Phase.GAME_OVER
                        statusMsg = "Tank destroyed..."
                        continue
                    }

                    // Advance turn machine
                    phase = if (p.fromPlayer) nextPhaseAfterPlayerShot(++turnCount, tanks, gunJammed).also { next ->
                        if (next == Phase.REINFORCEMENTS) {
                            statusMsg = "Enemy reinforcements!"
                            spawnEnemyTank(tanks, canvasW).let { tanks = it }
                        }
                        if (next == Phase.GUN_JAMMED_NOTICE) { gunJammed = true; statusMsg = "Your gun jammed!" }
                        if (next == Phase.ENEMY_THINKING)     statusMsg = "Enemy aiming..."
                    } else {
                        statusMsg = "Your turn"; Phase.PLAYER_TURN
                    }
                    continue
                }

                proj = p.copy(x = nx, y = ny, vy = nvy)
            }
        }
    }

    // ── Enemy AI: aim + fire after thinking delay ─────────────────────────────
    LaunchedEffect(phase) {
        if (phase != Phase.ENEMY_THINKING) return@LaunchedEffect
        val enemies = tanks.filter { !it.isPlayer && it.hp > 0 }
        val player  = tanks.firstOrNull { it.isPlayer } ?: return@LaunchedEffect
        if (enemies.isEmpty()) { phase = Phase.PLAYER_TURN; statusMsg = "Your turn"; return@LaunchedEffect }

        statusMsg = "Enemy aiming..."
        delay(1100 + Random.nextLong(0, 700))

        val shooter = enemies.random()
        val adx  = abs(player.x - shooter.x)          // horizontal gap (always +ve)
        val dy   = tankTopY(player.x) - (tankTopY(shooter.x) - 2f)  // +ve = player is lower
        val dirX = sign(player.x - shooter.x)          // ±1 — direction to player

        // Physics-based aiming: solve tan(θ) from the standard ballistic equation.
        // A·tan²(θ) - adx·tan(θ) + (A - dy) = 0,  where A = ½g·adx²/v²
        val speed = MAX_SHOOT_SPEED * (0.55f + Random.nextFloat() * 0.40f)
        val v2    = speed.toDouble() * speed.toDouble()
        val g     = GRAVITY.toDouble()
        val A     = 0.5 * g * adx.toDouble() * adx.toDouble() / v2
        val disc  = adx.toDouble() * adx.toDouble() - 4.0 * A * (A - dy.toDouble())

        val baseAngle: Double = if (disc < 0.0) {
            // Target out of range — 45° gives max range
            Math.PI / 4.0
        } else {
            val sq = sqrt(disc)
            val s1 = (adx.toDouble() + sq) / (2.0 * A)   // high-arc
            val s2 = (adx.toDouble() - sq) / (2.0 * A)   // low-arc
            // Mix low/high arc for variety; clamp to reasonable upward angles
            val s  = if (Random.nextFloat() < 0.25f && s1 > 0.1) s1 else s2.coerceAtLeast(0.05)
            atan(s)
        }

        // ±8° noise — accurate enough to be a real threat, missing enough to be fun
        val noise    = Math.toRadians((Random.nextFloat() * 16f - 8f).toDouble())
        val finalAng = (baseAngle + noise).coerceIn(0.10, Math.PI / 2.0 - 0.05)

        proj  = Projectile(
            x = shooter.x, y = tankTopY(shooter.x) - 2f,
            vx = (speed * cos(finalAng) * dirX).toFloat(),
            vy = -(speed * sin(finalAng)).toFloat(),   // always negative = always fires upward
            fromPlayer = false
        )
        phase     = Phase.ENEMY_PROJECTILE
        statusMsg = "Incoming!"
    }

    // ── Reinforcements pause ──────────────────────────────────────────────────
    LaunchedEffect(phase) {
        if (phase != Phase.REINFORCEMENTS) return@LaunchedEffect
        delay(2200)
        // Check if gun jam also fires this turn
        if (turnCount >= GUN_JAM_TURN && !gunJammed) {
            gunJammed = true; phase = Phase.GUN_JAMMED_NOTICE; statusMsg = "Your gun jammed!"
        } else {
            phase = Phase.ENEMY_THINKING; statusMsg = "Enemy aiming..."
        }
    }

    // ── Gun jammed notice ─────────────────────────────────────────────────────
    LaunchedEffect(phase) {
        if (phase != Phase.GUN_JAMMED_NOTICE) return@LaunchedEffect
        delay(2800)
        phase     = Phase.PLAYER_TURN
        statusMsg = "Move only — find a way out"
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF060606))
    ) {
        // Main canvas
        Canvas(Modifier.fillMaxSize()) {
            // Capture size on first draw
            if (canvasW != size.width || canvasH != size.height) {
                canvasW   = size.width
                canvasH   = size.height
                sizeReady = true
            }
            if (!sizeReady) return@Canvas

            drawSky(size.width, size.height)
            drawTerrain(::groundY, size.width, size.height)
            drawEscapeGate(::groundY, size.width)
            tanks.forEach { t -> drawTank(t, tankTopY(t.x), gunJammed && t.isPlayer) }
            proj?.let { drawProjectile(it) }
            explosions.forEach { e -> drawExplosion(e) }
        }

        // Status bar
        if (statusMsg.isNotEmpty()) {
            Text(
                text       = statusMsg,
                color      = Color(0xFFEEEEEE),
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 3.dp)
            )
        }

        // HP display
        HpDisplay(tanks = tanks, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))

        // Turn counter (top-right, not intrusive)
        if (phase != Phase.INIT && phase != Phase.GAME_OVER && phase != Phase.ESCAPED) {
            Text(
                text     = "Turn $turnCount",
                color    = Color(0x55FFFFFF),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 42.dp)
            )
        }

        // Controls (only during player turn)
        if (phase == Phase.PLAYER_TURN) {
            if (!gunJammed) {
                val playerPower = tanks.firstOrNull { it.isPlayer }?.power ?: 0.65f
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .background(Color(0xDD050505), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF444444), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PWR", color = Color(0xFFAAAAAA), fontSize = 11.sp,
                        modifier = Modifier.padding(end = 6.dp))
                    Slider(
                        value         = playerPower,
                        onValueChange = { p -> tanks = tanks.map { if (it.isPlayer) it.copy(power = p) else it } },
                        valueRange    = 0.2f..1.0f,
                        colors        = SliderDefaults.colors(
                            thumbColor            = Color(0xFFFFFFFF),
                            activeTrackColor      = Color(0xFFCCCCCC),
                            inactiveTrackColor    = Color(0xFF333333)
                        ),
                        modifier = Modifier.width(160.dp)
                    )
                }
            }

            TankControls(
                gunJammed   = gunJammed,
                onMoveLeft  = { moveLeft  = it },
                onMoveRight = { moveRight = it },
                onAimUp     = { aimUp     = it },
                onAimDown   = { aimDown   = it },
                onFire = {
                    if (!gunJammed) {
                        val p = tanks.firstOrNull { it.isPlayer } ?: return@TankControls
                        val rad   = Math.toRadians(p.gunAngle.toDouble())
                        val speed = MAX_SHOOT_SPEED * p.power
                        proj  = Projectile(
                            x = p.x, y = tankTopY(p.x) - 2f,
                            vx = (speed * cos(rad)).toFloat(),
                            vy = -(speed * sin(rad)).toFloat(),
                            fromPlayer = true
                        )
                        phase     = Phase.PROJECTILE_FLYING
                        statusMsg = ""
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // End-game overlay
        if (phase == Phase.GAME_OVER || phase == Phase.ESCAPED) {
            GameEndOverlay(
                escaped    = phase == Phase.ESCAPED,
                onContinue = { if (phase == Phase.ESCAPED) onComplete() else onExit() }
            )
        }

        // Exit button
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(30.dp)
                .background(Color(0x66000000), CircleShape)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onExit() },
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = Color(0x88FFFFFF), fontSize = 14.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// State-machine helper: what phase comes after the player fires?
// Called *after* turnCount has already been incremented.
// ─────────────────────────────────────────────────────────────────────────────
private fun nextPhaseAfterPlayerShot(
    newTurn: Int,
    tanks: List<TankState>,
    gunJammed: Boolean
): Phase = when {
    newTurn in REINFORCE_AT_TURNS      -> Phase.REINFORCEMENTS
    newTurn >= GUN_JAM_TURN && !gunJammed -> Phase.GUN_JAMMED_NOTICE
    tanks.none { !it.isPlayer && it.hp > 0 } -> Phase.PLAYER_TURN   // all enemies dead (rare early)
    else                               -> Phase.ENEMY_THINKING
}

private fun spawnEnemyTank(current: List<TankState>, canvasW: Float): List<TankState> {
    val newId  = (current.maxOfOrNull { it.id } ?: 0) + 1
    val spawnX = canvasW * (0.82f + Random.nextFloat() * 0.10f)
    return current + TankState(id = newId, x = spawnX, hp = ENEMY_HP, isPlayer = false, gunAngle = 130f)
}

// ─────────────────────────────────────────────────────────────────────────────
// Drawing
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawSky(w: Float, h: Float) {
    // Deep black gradient — barely perceptible, like staring into a dark screen
    val bands = 20
    for (i in 0 until bands) {
        val frac  = i.toFloat() / bands
        val lum   = 0.02f + frac * 0.06f   // 2%–8% brightness
        drawRect(
            Color(lum, lum, lum, 1f),
            topLeft = Offset(0f, h * frac * 0.62f),
            size    = Size(w, h * 0.62f / bands + 1f)
        )
    }
    // Faint distant ridge — slightly lighter than the sky, no colour
    val ridge = Path().apply {
        moveTo(0f, h * 0.53f)
        cubicTo(w * 0.20f, h * 0.42f, w * 0.35f, h * 0.51f, w * 0.50f, h * 0.46f)
        cubicTo(w * 0.65f, h * 0.41f, w * 0.80f, h * 0.53f, w * 1.0f,  h * 0.48f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(ridge, Color(0xFF111111))
}

private fun DrawScope.drawTerrain(groundY: (Float) -> Float, w: Float, h: Float) {
    val steps = 120
    val fill = Path().apply {
        moveTo(0f, groundY(0f))
        for (i in 1..steps) lineTo(i * w / steps, groundY(i * w / steps))
        lineTo(w, h); lineTo(0f, h); close()
    }
    // Terrain body — very dark, just above pure black
    drawPath(fill, Color(0xFF141414))
    val line = Path().apply {
        moveTo(0f, groundY(0f))
        for (i in 1..steps) lineTo(i * w / steps, groundY(i * w / steps))
    }
    // Soft inner glow just below the surface edge
    drawPath(line, Color(0x1AFFFFFF), style = Stroke(width = 10f))
    // Crisp white outline on the surface
    drawPath(line, Color(0xFFE8E8E8), style = Stroke(width = 2.5f))
}

private fun DrawScope.drawEscapeGate(groundY: (Float) -> Float, w: Float) {
    val gx = w * ESCAPE_X
    val gy = groundY(gx)
    val gh = 58f; val gw = 6f
    // Subtle dark-gray posts — deliberately hard to spot until you look for them
    val postColor = Color(0xFF2A2A2A)
    drawRect(postColor,          topLeft = Offset(gx - gw - 3f, gy - gh), size = Size(gw, gh))
    drawRect(postColor,          topLeft = Offset(gx + 3f,      gy - gh), size = Size(gw, gh))
    drawRect(postColor,          topLeft = Offset(gx - gw - 3f, gy - gh), size = Size(gw * 2f + 6f, 4f))
    // Just a hair of outline so it reads as a structure rather than terrain noise
    drawRect(Color(0x33FFFFFF),  topLeft = Offset(gx - gw - 3f, gy - gh), size = Size(gw, gh),           style = Stroke(1f))
    drawRect(Color(0x33FFFFFF),  topLeft = Offset(gx + 3f,      gy - gh), size = Size(gw, gh),           style = Stroke(1f))
}

private fun DrawScope.drawTank(t: TankState, topY: Float, gunBroken: Boolean) {
    val cx      = t.x
    val dim     = if (t.hp == 1) 0.45f else 1f
    // Player = bright white outline; enemy = mid-gray
    val outline = if (t.isPlayer) Color(1f, 1f, 1f, dim) else Color(0.60f, 0.60f, 0.60f, dim)
    val fill    = Color(0f, 0f, 0f, 0.95f)

    // Tracks — filled dark rect + outline
    val trackTL = Offset(cx - TANK_W / 2f, topY + TANK_H * 0.72f)
    val trackSz = Size(TANK_W, TANK_H * 0.33f)
    drawRect(fill,    topLeft = trackTL, size = trackSz)
    drawRect(outline, topLeft = trackTL, size = trackSz, style = Stroke(width = 1.5f))

    // Body hull
    val hullTL = Offset(cx - TANK_W / 2f + 3f, topY)
    val hullSz = Size(TANK_W - 6f, TANK_H * 0.72f)
    drawRect(fill,    topLeft = hullTL, size = hullSz)
    drawRect(outline, topLeft = hullTL, size = hullSz, style = Stroke(width = 2f))
    // Top-edge highlight shading
    drawRect(Color(1f, 1f, 1f, 0.10f * dim), topLeft = hullTL, size = Size(hullSz.width, 3f))

    // Turret
    val turrC = Offset(cx, topY + TANK_H * 0.22f)
    val turrR = TANK_H * 0.40f
    drawCircle(fill,    radius = turrR, center = turrC)
    drawCircle(outline, radius = turrR, center = turrC, style = Stroke(width = 2f))

    // Barrel
    val barColor = if (gunBroken) Color(0.30f, 0.30f, 0.30f, dim) else outline
    val rad = Math.toRadians(t.gunAngle.toDouble())
    drawLine(
        barColor, strokeWidth = 4f,
        start = turrC,
        end   = Offset(
            (cx + BARREL_LEN * cos(rad)).toFloat(),
            (topY + TANK_H * 0.22f - BARREL_LEN * sin(rad)).toFloat()
        )
    )

    // HP pips above tank
    for (i in 0 until t.hp.coerceAtLeast(0)) {
        drawCircle(outline, radius = 3.5f, center = Offset(cx - (t.hp - 1) * 5f + i * 10f, topY - 9f))
    }

    // Gun broken indicator — ×
    if (gunBroken) {
        val iy = topY - 26f; val r = 6f
        drawLine(Color(0.7f, 0.7f, 0.7f, 0.9f), Offset(cx - r, iy - r), Offset(cx + r, iy + r), strokeWidth = 2.5f)
        drawLine(Color(0.7f, 0.7f, 0.7f, 0.9f), Offset(cx + r, iy - r), Offset(cx - r, iy + r), strokeWidth = 2.5f)
    }
}

private fun DrawScope.drawProjectile(p: Projectile) {
    // Player shell = bright white; enemy shell = mid-gray so you can tell them apart
    val core = if (p.fromPlayer) Color(0xFFFFFFFF) else Color(0xFF999999)
    drawCircle(core.copy(alpha = 0.20f), radius = 10f, center = Offset(p.x, p.y))
    drawCircle(core.copy(alpha = 0.55f), radius = 6f,  center = Offset(p.x, p.y))
    drawCircle(core,                     radius = 3.5f, center = Offset(p.x, p.y))
}

private fun DrawScope.drawExplosion(e: Explosion) {
    val alpha = (1f - e.age).coerceIn(0f, 1f)
    val r     = EXPLOSION_RADIUS * (0.3f + e.age * 0.7f)
    val c     = Offset(e.x, e.y)
    // Expanding rings — white on black looks like a stark inked blast
    drawCircle(Color(1f, 1f, 1f, alpha * 0.55f), r,         center = c, style = Stroke(width = 3f))
    drawCircle(Color(1f, 1f, 1f, alpha * 0.30f), r * 0.65f, center = c, style = Stroke(width = 2f))
    drawCircle(Color(1f, 1f, 1f, alpha * 0.18f), r * 0.35f, center = c)
    // Initial white flash at the moment of impact
    if (e.age < 0.18f) {
        val flash = (1f - e.age / 0.18f) * 0.75f
        drawCircle(Color(1f, 1f, 1f, flash), r * 0.20f, center = c)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TankControls(
    gunJammed: Boolean,
    onMoveLeft:  (Boolean) -> Unit,
    onMoveRight: (Boolean) -> Unit,
    onAimUp:     (Boolean) -> Unit,
    onAimDown:   (Boolean) -> Unit,
    onFire:      () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 20.dp, start = 40.dp, end = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Bottom
    ) {
        // Move buttons (always visible)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoldButton("◀", Color(0xFF111111)) { onMoveLeft(it) }
            HoldButton("▶", Color(0xFF111111)) { onMoveRight(it) }
        }

        // Centre: FIRE or jammed notice
        if (!gunJammed) {
            Box(
                Modifier
                    .size(68.dp)
                    .background(Color(0xFF0A0A0A), CircleShape)
                    .border(2.dp, Color(0xFFEEEEEE), CircleShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onFire() },
                contentAlignment = Alignment.Center
            ) {
                Text("FIRE", color = Color(0xFFEEEEEE), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                "GUN JAMMED",
                color      = Color(0xFFCCCCCC),
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                modifier   = Modifier
                    .background(Color(0xDD0A0A0A), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF555555), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Aim buttons (hidden when gun is jammed)
        if (!gunJammed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldButton("▲", Color(0xFF111111)) { onAimUp(it) }
                HoldButton("▼", Color(0xFF111111)) { onAimDown(it) }
            }
        } else {
            Spacer(Modifier.width(112.dp))
        }
    }
}

@Composable
private fun HoldButton(label: String, baseColor: Color, onDown: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .background(baseColor, RoundedCornerShape(10.dp))
            .border(1.5.dp, Color(0xFFBBBBBB), RoundedCornerShape(10.dp))
            .pointerInput(onDown) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        when (ev.type) {
                            PointerEventType.Press   -> onDown(true)
                            PointerEventType.Release -> onDown(false)
                            else                     -> {}
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HUD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HpDisplay(tanks: List<TankState>, modifier: Modifier = Modifier) {
    Column(modifier.padding(4.dp)) {
        tanks.firstOrNull { it.isPlayer }?.let { p ->
            HpRow(label = "YOU", hp = p.hp, maxHp = PLAYER_MAX_HP, color = Color(0xFFEEEEEE))
        }
        val enemies = tanks.filter { !it.isPlayer }
        if (enemies.isNotEmpty()) {
            HpRow(
                label = "ENEMY ×${enemies.size}",
                hp    = enemies.sumOf { it.hp },
                maxHp = enemies.size * ENEMY_HP,
                color = Color(0xFF888888)
            )
        }
    }
}

@Composable
private fun HpRow(label: String, hp: Int, maxHp: Int, color: Color) {
    Row(
        Modifier
            .background(Color(0xCC050505), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label  ", color = color.copy(alpha = 0.7f), fontSize = 11.sp)
        val filled = hp.coerceIn(0, maxHp)
        val empty  = (maxHp - filled).coerceAtLeast(0)
        repeat(filled) {
            Box(
                Modifier.padding(end = 2.dp).size(10.dp, 8.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
        repeat(empty) {
            Box(
                Modifier.padding(end = 2.dp).size(10.dp, 8.dp)
                    .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// End-game overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameEndOverlay(escaped: Boolean, onContinue: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color(0xFF050505), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF444444), RoundedCornerShape(16.dp))
                .padding(horizontal = 36.dp, vertical = 28.dp)
        ) {
            Text(
                if (escaped) "You got out." else "Tank destroyed.",
                color      = if (escaped) Color(0xFFEEEEEE) else Color(0xFF888888),
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (escaped)
                    "You couldn't win the fight.\nBut you didn't have to."
                else
                    "Sometimes you can't win.\nBut you can always run.",
                color     = Color(0x88FFFFFF),
                fontSize  = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFAAAAAA), RoundedCornerShape(8.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onContinue() }
                    .padding(horizontal = 28.dp, vertical = 10.dp)
            ) {
                Text(
                    if (escaped) "Continue" else "Try again",
                    color      = Color(0xFFDDDDDD),
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
