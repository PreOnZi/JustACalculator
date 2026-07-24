package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Building 9 — Flappy-style round, timed to its narration.
//
// Tap to flap; steer the dot through gaps in the scrolling barriers, dying and
// retrying freely. The round has no score target — it runs for exactly as long
// as [voRes] plays. When the voiceover ends the game freezes and a single
// "Time to go" button appears; pressing it calls onComplete() (which flies the
// city over the now-finished bridge). The back arrow calls onExit().
// ─────────────────────────────────────────────────────────────────────────────

private data class Barrier(val x: Float, val gapY: Float, val passed: Boolean = false)

// A floating finger to grab as you fly through a gap. Not a currency — the tally
// lives only in Building 9 (persisted so it accrues across every retry within the
// round) and never feeds the city's coin/key/etc. stores.
private data class FloatingFinger(val x: Float, val y: Float)

private const val B9_FINGERS_KEY = "b9_fingers_collected"

@Composable
fun FlappyBirdGame(
    modifier: Modifier = Modifier,
    voRes: Int? = null,
    onComplete: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF4EC0CA))) {
        val density = LocalDensity.current
        val context = LocalContext.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }

        // Collectible fingers rendered from the 3D model as a flat sprite.
        val fingerBmp = rememberModelBitmap("models/finger.obj", "models/finger.mtl",
            sizePx = 128, tilt = -8f, turn = 18f, fitSpan = 1.85f)
        // Cumulative tally, persisted in the city store so it survives every retry
        // (and app kill) but stays trapped in Building 9 — see [B9_FINGERS_KEY].
        val cityPrefs = remember {
            context.getSharedPreferences("calc_city", android.content.Context.MODE_PRIVATE)
        }
        var fingerTotal by remember { mutableIntStateOf(cityPrefs.getInt(B9_FINGERS_KEY, 0)) }

        // Tuning (all relative to the field so it scales to any screen).
        val birdX = w * 0.28f
        // Visual sprite first; the collision radius is then a small fraction of
        // it so hits track the bird's *body* and stay well inside the square
        // bounds of the SVG file (which has transparent margins).
        val spriteSize = h * 0.085f      // drawn bird (larger)
        val birdR = spriteSize * 0.20f   // tight hitbox, nested inside the sprite
        val gapH = h * 0.30f
        val barrierW = w * 0.16f
        val gravity = h * 0.0016f
        val flapV = -h * 0.018f           // flap impulse (smaller = lower hops)
        val scrollV = w * 0.006f
        val spacing = w * 0.62f          // horizontal distance between barriers

        var birdY by remember { mutableStateOf(h * 0.4f) }
        var vel by remember { mutableStateOf(0f) }
        var running by remember { mutableStateOf(false) }
        var dead by remember { mutableStateOf(false) }
        var score by remember { mutableIntStateOf(0) }
        var barriers by remember { mutableStateOf(listOf<Barrier>()) }
        var fingers by remember { mutableStateOf(listOf<FloatingFinger>()) }
        val fingerSize = h * 0.075f
        val fingerR = fingerSize * 0.42f
        // The round ends when the narration ends — not on any score.
        var voDone by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val vm = com.fictioncutshort.justacalculator.logic.VoiceoverManager
            if (voRes != null) {
                vm.play(voRes, cctv = false)
                // Wait for the clip to actually begin (up to ~2.5 s), then run the
                // round until it ends. If it never starts (load failure), fall back
                // to a fixed length so the round can't end instantly OR run forever.
                var waited = 0
                while (!vm.isBusy() && waited < 2500) { delay(50); waited += 50 }
                if (vm.isBusy()) {
                    while (vm.isBusy()) delay(150)
                } else {
                    delay(20_000L)
                }
            } else {
                delay(20_000L)
            }
            voDone = true
        }

        fun reset() {
            birdY = h * 0.4f; vel = 0f; score = 0
            dead = false
            val firstGap = randomGapY(h, gapH)
            barriers = listOf(Barrier(w + barrierW, firstGap))
            // A finger rides in each gap — fly through to grab it. The tally is
            // cumulative, so it is NOT reset here (only the in-field fingers are).
            fingers = listOf(FloatingFinger(w + barrierW + barrierW * 0.5f, firstGap))
            running = true
        }

        fun flap() {
            if (voDone) return   // round's over — the "Time to go" button takes over
            when {
                dead -> reset()
                !running -> { running = true; vel = flapV }
                else -> vel = flapV
            }
        }

        // Physics / scroll loop.
        LaunchedEffect(running, dead, voDone, w, h) {
            if (!running || dead || voDone || w <= 0f || h <= 0f) return@LaunchedEffect
            while (running && !dead && !voDone) {
                delay(16)
                vel += gravity
                birdY += vel

                // Scroll, recycle, and spawn barriers.
                val moved = barriers.map { it.copy(x = it.x - scrollV) }.toMutableList()
                val movedFingers = fingers.map { it.copy(x = it.x - scrollV) }.toMutableList()
                val last = moved.maxByOrNull { it.x }
                if (last == null || last.x < w - spacing) {
                    val gy = randomGapY(h, gapH)
                    moved.add(Barrier(w + barrierW, gy))
                    movedFingers.add(FloatingFinger(w + barrierW + barrierW * 0.5f, gy))
                }
                moved.removeAll { it.x + barrierW < 0f }
                movedFingers.removeAll { it.x + fingerR < 0f }

                // Collect any finger the bird overlaps (grab-through, not lethal).
                val grabbed = movedFingers.filter {
                    val ddx = it.x - birdX; val ddy = it.y - birdY
                    ddx * ddx + ddy * ddy < (birdR + fingerR) * (birdR + fingerR)
                }
                if (grabbed.isNotEmpty()) {
                    movedFingers.removeAll(grabbed.toSet())
                    fingerTotal += grabbed.size
                    cityPrefs.edit().putInt(B9_FINGERS_KEY, fingerTotal).apply()
                }
                fingers = movedFingers

                // Scoring — count a barrier once it passes the bird (display only).
                var gained = 0
                val scored = moved.map { b ->
                    if (!b.passed && b.x + barrierW < birdX) { gained++; b.copy(passed = true) } else b
                }
                if (gained > 0) score += gained

                // Collisions: floor/ceiling or a barrier.
                var hit = birdY - birdR < 0f || birdY + birdR > h
                for (b in scored) {
                    val withinX = birdX + birdR > b.x && birdX - birdR < b.x + barrierW
                    if (withinX && (birdY - birdR < b.gapY - gapH / 2f || birdY + birdR > b.gapY + gapH / 2f)) {
                        hit = true; break
                    }
                }

                barriers = scored
                if (hit) dead = true
            }
        }

        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onTap = { flap() })
            }
        ) {
            // Barriers (green tubes, top + bottom).
            val tube = Color(0xFF73BF2E)
            val tubeDark = Color(0xFF558022)
            for (b in barriers) {
                val topH = b.gapY - gapH / 2f
                val botY = b.gapY + gapH / 2f
                drawRect(tube, Offset(b.x, 0f), Size(barrierW, topH))
                drawRect(tubeDark, Offset(b.x, 0f), Size(barrierW * 0.12f, topH))
                drawRect(tube, Offset(b.x, botY), Size(barrierW, size.height - botY))
                drawRect(tubeDark, Offset(b.x, botY), Size(barrierW * 0.12f, size.height - botY))
            }
            // Ground strip.
            drawRect(Color(0xFFDED895), Offset(0f, size.height - size.height * 0.04f), Size(size.width, size.height * 0.04f))
        }

        // Bird sprite — overlaid on the canvas, centred on (birdX, birdY).
        // birdup while rising / just-flapped (vel < 0), birddown while falling.
        val birdSprite = if (vel < 0f) "birdup" else "birddown"
        val spriteDp = with(density) { spriteSize.toDp() }
        AsyncImage(
            model = "file:///android_asset/flappybird/$birdSprite.svg",
            contentDescription = null,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (birdX - spriteSize / 2f).roundToInt(),
                        (birdY - spriteSize / 2f).roundToInt(),
                    )
                }
                .size(spriteDp),
        )

        // Floating fingers — drawn from the 3D model as flat sprites.
        if (fingerBmp != null) {
            val fingerDp = with(density) { fingerSize.toDp() }
            for (f in fingers) {
                Image(
                    bitmap = fingerBmp,
                    contentDescription = null,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (f.x - fingerSize / 2f).roundToInt(),
                                (f.y - fingerSize / 2f).roundToInt(),
                            )
                        }
                        .size(fingerDp),
                )
            }
        }

        // Score
        Text("$score", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp))

        // Finger tally — cumulative across every attempt in this round.
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fingerBmp != null) {
                Image(bitmap = fingerBmp, contentDescription = null, modifier = Modifier.size(34.dp))
            }
            Text("×$fingerTotal", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace)
        }

        // No exit — the only way out of Building 9 is to hear the voiceover through
        // to the end, at which point the "Time to go" button appears.

        // Start / game-over prompts (suppressed once the round is over).
        val prompt = when {
            voDone -> null
            dead -> "Game over — tap to retry"
            !running -> "Tap to start"
            else -> null
        }
        if (prompt != null) {
            Text(prompt, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Center)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { flap() }
                    .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp))
        }

        // Narration finished → the round is done. One button out.
        if (voDone) {
            Text("Time to go", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Center)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onComplete() }
                    .background(Color(0xE6000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 28.dp, vertical = 16.dp))
        }
    }
}

private fun randomGapY(h: Float, gapH: Float): Float {
    val margin = gapH * 0.6f + h * 0.04f
    return Random.nextFloat() * (h - 2f * margin) + margin
}
