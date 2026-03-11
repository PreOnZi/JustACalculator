package com.fictioncutshort.justacalculator.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
// AD CARD STACK OVERLAY
//
// Phase 2 entry point. Triggered after all 5 RAD buttons are pressed.
//
// Flow:
//   1. Full-screen top card appears (ad placeholder: big number + solid color).
//   2. Gyroscope tilt causes the stack below to peek through (parallax).
//      Parallax intensity increases with each card swiped u2014 instability builds.
//   3. Top card auto-nudges left/right to hint at Tinder-swipe mechanic.
//   4. User swipes 5 cards. After each swipe, next card is top.
//   5. After card 5 is swiped, remaining 20 cards "fall" (cascade animation).
//   6. Camera zooms out u2014 transitions into Pexeso (memory card game).
//
// The calculator is visible underneath, rendered in outline-only style by
// the caller (PortraitCalculatorContent / LandscapeCalculatorLayout check
// state.showAdCards and switch to outline mode).
// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500

// u2500u2500 Ad phases u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
enum class AdCardPhase {
    CARDS,       // Swiping through the 5 top cards
    COLLAPSING,  // Stack falls after card 5 swiped
    PEXESO       // Memory game
}

// u2500u2500 Card data u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
private val CARD_COLORS = listOf(
    Color(0xFF1A73E8), // Google blue
    Color(0xFFEA4335), // Google red
    Color(0xFF34A853), // Google green
    Color(0xFFFBBC04), // Google yellow
    Color(0xFFFF6D00), // deep orange
    Color(0xFF6200EE), // purple
    Color(0xFF018786), // teal
    Color(0xFFB00020), // error red
    Color(0xFF37474F), // blue-grey dark
    Color(0xFF00BCD4), // cyan
    Color(0xFF8BC34A), // light green
    Color(0xFFFF5722), // deep orange 2
    Color(0xFF607D8B), // blue-grey
    Color(0xFF9C27B0), // purple 2
    Color(0xFF3E2723), // brown dark
    Color(0xFF004D40), // teal dark
    Color(0xFF1B5E20), // green dark
    Color(0xFF0D47A1), // blue dark
    Color(0xFF880E4F), // pink dark
    Color(0xFF212121), // near black
    Color(0xFFE65100), // orange dark
    Color(0xFF4A148C), // deep purple dark
    Color(0xFF006064), // cyan dark
    Color(0xFF33691E), // light green dark
    Color(0xFFBF360C), // deep orange dark
)

data class AdCard(
    val id: Int,
    val number: Int,           // Displayed number (1-25)
    val color: Color,
    val accentColor: Color,    // For retro border/trim
    val isSwipeable: Boolean,  // Only first 5 are swipeable
)

private fun buildCardStack(): List<AdCard> {
    return (0 until 25).map { i ->
        AdCard(
            id = i,
            number = i + 1,
            color = CARD_COLORS[i % CARD_COLORS.size],
            accentColor = CARD_COLORS[(i + 5) % CARD_COLORS.size],
            isSwipeable = i < 5
        )
    }
}

// u2500u2500 Retro era visual themes (applied to card borders/decorations) u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
private enum class RetroTheme {
    WINDOWS_XP,    // Bliss-era, beveled edges, Luna blue
    IOS4,          // Glossy, rounded, linen texture feel
    WIN_2016,      // Flat, accent strip at top, bold font
    MINIMALIST,    // Pure, single thin border
    GLITCH         // Offset color layers
}

private val retroThemes = RetroTheme.values()

// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
// MAIN COMPOSABLE
// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500

@Composable
fun AdCardStack(
    onPexesoComplete: () -> Unit,   // Called when pexeso is won u2014 story continues
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // u2500u2500 State u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
    val cards = remember { buildCardStack() }
    var swipedCount by remember { mutableIntStateOf(0) }       // 0-5 cards swiped
    var phase by remember { mutableStateOf(AdCardPhase.CARDS) }

    // Gyroscope tilt (x = roll, y = pitch)
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }

    // Hint nudge: auto-nudges the top card left/right after 2s idle
    var hintNudgeTarget by remember { mutableFloatStateOf(0f) }
    var hintActive by remember { mutableStateOf(false) }

    // Pexeso game state
    val pexesoCards = remember { buildPexesoGrid() }
    var pexesoFlipped by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pexesoMatched by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pexesoLocked by remember { mutableStateOf(false) }

    // u2500u2500 Gyroscope sensor u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rotMatrix = FloatArray(9)
            private val orientation = FloatArray(3)
            private var baseAzimuth = Float.MAX_VALUE
            private var basePitch = Float.MAX_VALUE

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                SensorManager.getOrientation(rotMatrix, orientation)
                val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                val roll  = Math.toDegrees(orientation[2].toDouble()).toFloat()

                if (baseAzimuth == Float.MAX_VALUE) {
                    baseAzimuth = roll
                    basePitch = pitch
                }
                tiltX = (roll - baseAzimuth).coerceIn(-30f, 30f)
                tiltY = (pitch - basePitch).coerceIn(-30f, 30f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        gyro?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // u2500u2500 Hint nudge loop: starts 2.5s after entering, repeats every 4s u2500u2500u2500u2500u2500u2500u2500u2500u2500
    LaunchedEffect(swipedCount, phase) {
        if (phase != AdCardPhase.CARDS) return@LaunchedEffect
        delay(2500)
        while (phase == AdCardPhase.CARDS) {
            hintNudgeTarget = 40f
            hintActive = true
            delay(400)
            hintNudgeTarget = -40f
            delay(400)
            hintNudgeTarget = 0f
            hintActive = false
            delay(4000)
        }
    }

    // u2500u2500 Collapse trigger u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
    LaunchedEffect(swipedCount) {
        if (swipedCount >= 5) {
            delay(600)
            phase = AdCardPhase.COLLAPSING
            delay(1800) // let collapse animation play
            phase = AdCardPhase.PEXESO
        }
    }

    // u2500u2500 Root box (full screen) u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when (phase) {
            AdCardPhase.CARDS -> CardStackScene(
                cards = cards,
                swipedCount = swipedCount,
                tiltX = tiltX,
                tiltY = tiltY,
                hintNudgeTarget = hintNudgeTarget,
                onSwiped = { swipedCount++ }
            )

            AdCardPhase.COLLAPSING -> CollapseAnimation(
                cards = cards,
                tiltX = tiltX,
                tiltY = tiltY
            )

            AdCardPhase.PEXESO -> PexesoGame(
                cards = pexesoCards,
                flipped = pexesoFlipped,
                matched = pexesoMatched,
                locked = pexesoLocked,
                onCardFlip = { idx ->
                    if (pexesoLocked) return@PexesoGame
                    if (pexesoFlipped.contains(idx) || pexesoMatched.contains(idx)) return@PexesoGame

                    val newFlipped = pexesoFlipped + idx
                    pexesoFlipped = newFlipped

                    if (newFlipped.size == 2) {
                        pexesoLocked = true
                        scope.launch {
                            delay(900)
                            val (a, b) = newFlipped.toList()
                            if (pexesoCards[a].pairId == pexesoCards[b].pairId) {
                                pexesoMatched = pexesoMatched + a + b
                                if (pexesoMatched.size == pexesoCards.size) {
                                    delay(800)
                                    onPexesoComplete()
                                }
                            }
                            pexesoFlipped = emptySet()
                            pexesoLocked = false
                        }
                    }
                }
            )
        }
    }
}

// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
// CARD STACK SCENE
// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500

@Composable
private fun CardStackScene(
    cards: List<AdCard>,
    swipedCount: Int,
    tiltX: Float,
    tiltY: Float,
    hintNudgeTarget: Float,
    onSwiped: () -> Unit
) {
    // Visible cards: top is [swipedCount], show up to 4 behind it
    val visibleCards = cards.drop(swipedCount).take(5).reversed() // draw back-to-front

    // Parallax intensity increases with each card swiped (instability)
    // swipedCount 0 u2192 multiplier 0.12f; swipedCount 4 u2192 0.55f
    val parallaxMultiplier = 0.12f + swipedCount * 0.10f

    Box(modifier = Modifier.fillMaxSize()) {
        visibleCards.forEachIndexed { stackIndex, card ->
            val isTop = stackIndex == visibleCards.size - 1
            val depthOffset = (visibleCards.size - 1 - stackIndex) // 0 = top, 4 = back

            CardLayer(
                card = card,
                depthOffset = depthOffset,
                isTop = isTop,
                parallaxX = tiltX * parallaxMultiplier * (depthOffset + 1),
                parallaxY = tiltY * parallaxMultiplier * (depthOffset + 1),
                hintNudgeTarget = if (isTop) hintNudgeTarget else 0f,
                onSwiped = if (isTop) onSwiped else null
            )
        }
    }
}

@Composable
private fun CardLayer(
    card: AdCard,
    depthOffset: Int,          // 0 = top card, 1 = one behind, etc.
    isTop: Boolean,
    parallaxX: Float,
    parallaxY: Float,
    hintNudgeTarget: Float,
    onSwiped: (() -> Unit)?
) {
    // Swipe drag state (only top card)
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Hint nudge animation
    val nudgeAnim by animateFloatAsState(
        targetValue = if (!isDragging) hintNudgeTarget else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "nudge"
    )

    // Snap-back animation when released without completing swipe
    val snapX by animateFloatAsState(
        targetValue = if (!isDragging) 0f else dragOffsetX,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "snapX"
    )

    // Scale down cards behind top
    val scaleForDepth = 1f - depthOffset * 0.04f
    // Vertical offset so stack peeks below
    val verticalPeek = (depthOffset * 10).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = verticalPeek)
            .graphicsLayer {
                translationX = if (isTop) {
                    (if (isDragging) dragOffsetX else snapX) + nudgeAnim + parallaxX
                } else {
                    parallaxX
                }
                translationY = if (isTop) {
                    (if (isDragging) dragOffsetY else 0f) + parallaxY
                } else {
                    parallaxY
                }
                scaleX = scaleForDepth
                scaleY = scaleForDepth
                rotationZ = if (isTop && isDragging) dragOffsetX * 0.04f else 0f
                alpha = if (depthOffset >= 4) 0.5f else 1f
            }
            .then(
                if (isTop && onSwiped != null) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                dragOffsetY += dragAmount.y
                            },
                            onDragEnd = {
                                val swipeThreshold = 300f
                                if (abs(dragOffsetX) > swipeThreshold) {
                                    // Fling off screen
                                    val dir = sign(dragOffsetX)
                                    scope.launch {
                                        // Quick fly-off
                                        dragOffsetX = dir * 1200f
                                        delay(300)
                                        isDragging = false
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        onSwiped()
                                    }
                                } else {
                                    isDragging = false
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                            }
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        AdCardFace(
            card = card,
            isTop = isTop,
            swipeHint = isTop && depthOffset == 0
        )
    }
}

// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
// AD CARD FACE u2014 Placeholder visual
// Each card: solid color background + large number + retro-styled border
// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500

@Composable
private fun AdCardFace(
    card: AdCard,
    isTop: Boolean,
    swipeHint: Boolean
) {
    val theme = retroThemes[card.id % retroThemes.size]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(card.color)
            .retroCardBorder(theme, card.accentColor),
        contentAlignment = Alignment.Center
    ) {
        // Retro decoration layer
        RetroDecoration(theme = theme, accentColor = card.accentColor, cardColor = card.color)

        // Big placeholder number
        Text(
            text = card.number.toString(),
            fontSize = 120.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        // "AD PLACEHOLDER" label
        Text(
            text = "AD PLACEHOLDER",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )

        // Swipe hint arrows on first card
        if (swipeHint) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("u2190 SWIPE u2192", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun RetroDecoration(theme: RetroTheme, accentColor: Color, cardColor: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (theme) {
            RetroTheme.WINDOWS_XP -> {
                // Luna-style title bar at top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1F3C88), accentColor, Color(0xFF1F3C88))
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ud83dudcc1 Advertisement", fontSize = 11.sp, color = Color.White,
                            fontFamily = FontFamily.Monospace)
                        // Window buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("_","u25a1","u2715").forEach { btn ->
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(accentColor, RoundedCornerShape(2.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(btn, fontSize = 9.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
                // Bottom taskbar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0xFF1F3C88))
                ) {
                    Text(
                        "  u25b6 Start",
                        fontSize = 12.sp, color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                    )
                }
            }

            RetroTheme.IOS4 -> {
                // Glossy top sheen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.0f))
                            )
                        )
                )
                // Status bar strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("9:41 AM", fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    Text("u2590u2590u2590u2590u2590 u1d42u1da6u1da0u1da6 ud83dudd0b", fontSize = 9.sp, color = Color.White)
                }
            }

            RetroTheme.WIN_2016 -> {
                // Bold accent top strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .align(Alignment.TopCenter)
                        .background(accentColor)
                )
                // Flat bottom bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    Text(
                        "ADVERTISEMENT",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            RetroTheme.MINIMALIST -> {
                // Just thin inner border inset
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                )
            }

            RetroTheme.GLITCH -> {
                // Offset color channel layers
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = 6f; alpha = 0.15f }
                        .background(Color.Red)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = -6f; alpha = 0.15f }
                        .background(Color.Cyan)
                )
                // Glitch stripe
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .offset(y = 80.dp)
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }
        }
    }
}

private fun Modifier.retroCardBorder(theme: RetroTheme, accentColor: Color): Modifier {
    return when (theme) {
        RetroTheme.WINDOWS_XP -> this.border(3.dp,
            Brush.linearGradient(listOf(Color.White.copy(0.6f), accentColor, Color(0xFF003087))),
            RoundedCornerShape(4.dp))
        RetroTheme.IOS4 -> this.border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
        RetroTheme.WIN_2016 -> this.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(0.dp))
        RetroTheme.MINIMALIST -> this.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        RetroTheme.GLITCH -> this.border(2.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
    }
}

// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
// COLLAPSE ANIMATION u2014 remaining 20 cards fall after 5th swipe
// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500

@Composable
private fun CollapseAnimation(
    cards: List<AdCard>,
    tiltX: Float,
    tiltY: Float
) {
    // Animate all remaining cards cascading down with staggered offsets
    val infiniteTransition = rememberInfiniteTransition(label = "collapse")

    // Overall scale zooms out (simulating camera pull-back)
    val globalScale by animateFloatAsState(
        targetValue = 0.1f,
        animationSpec = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
        label = "zoomOut"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = globalScale
                    scaleY = globalScale
                }
        ) {
            cards.drop(5).forEachIndexed { i, card ->
                val fallDelay = i * 60
                var fallen by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(fallDelay.toLong())
                    fallen = true
                }
                val fallY by animateFloatAsState(
                    targetValue = if (fallen) 2000f else 0f,
                    animationSpec = tween(800, easing = AccelerateInterpolatorEasing),
                    label = "fall$i"
                )
                val fallRot by animateFloatAsState(
                    targetValue = if (fallen) Random.nextFloat() * 60f - 30f else 0f,
                    animationSpec = tween(800),
                    label = "rot$i"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = fallY + tiltY * 0.4f
                            translationX = tiltX * 0.4f + i * 2f
                            rotationZ = fallRot
                            alpha = (1f - fallY / 2200f).coerceIn(0f, 1f)
                        }
                        .background(card.color)
                )
            }
        }
    }
}

private val AccelerateInterpolatorEasing = Easing { t -> t * t }

// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
// PEXESO (MEMORY CARD GAME)
// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500

data class PexesoCard(
    val id: Int,
    val pairId: Int,      // cards with same pairId are a pair
    val color: Color,     // shown when face-up
    val symbol: String    // emoji symbol shown when face-up
)

private val PEXESO_SYMBOLS = listOf("u2605","u25c6","u25b2","u25cf","u25a0","u2726","u2b1f","u2b21","u2b22","u2b23")
private val PEXESO_COLORS  = listOf(
    Color(0xFF1A73E8), Color(0xFFEA4335), Color(0xFF34A853), Color(0xFFFBBC04),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722), Color(0xFF607D8B),
    Color(0xFF4CAF50), Color(0xFFFF9800)
)

private fun buildPexesoGrid(): List<PexesoCard> {
    // 4u00d75 grid = 20 cards = 10 pairs
    val pairs = (0 until 10).flatMap { pairId ->
        listOf(
            PexesoCard(pairId * 2, pairId, PEXESO_COLORS[pairId], PEXESO_SYMBOLS[pairId]),
            PexesoCard(pairId * 2 + 1, pairId, PEXESO_COLORS[pairId], PEXESO_SYMBOLS[pairId])
        )
    }
    return pairs.shuffled()
}

@Composable
private fun PexesoGame(
    cards: List<PexesoCard>,
    flipped: Set<Int>,
    matched: Set<Int>,
    locked: Boolean,
    onCardFlip: (Int) -> Unit
) {
    val matchedCount = matched.size / 2
    val totalPairs = cards.size / 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // Header
        Text(
            text = "MEMORY",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            letterSpacing = 6.sp
        )

        Text(
            text = "$matchedCount / $totalPairs pairs found",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // 4u00d75 grid
        val cols = 4
        val rows = 5
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until rows).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (0 until cols).forEach { col ->
                        val idx = row * cols + col
                        if (idx < cards.size) {
                            val card = cards[idx]
                            val isFaceUp = flipped.contains(idx) || matched.contains(idx)
                            val isMatched = matched.contains(idx)
                            PexesoCardCell(
                                card = card,
                                isFaceUp = isFaceUp,
                                isMatched = isMatched,
                                onClick = { onCardFlip(idx) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PexesoCardCell(
    card: PexesoCard,
    isFaceUp: Boolean,
    isMatched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFaceUp) 0f else 180f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "flip${card.id}"
    )

    val alphaAnim by animateFloatAsState(
        targetValue = if (isMatched) 0.4f else 1f,
        animationSpec = tween(300),
        label = "alpha${card.id}"
    )

    Box(
        modifier = modifier
            .aspectRatio(0.7f)
            .alpha(alphaAnim)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (rotation <= 90f) card.color else Color(0xFF2A2A2A))
            .border(
                1.5.dp,
                if (isMatched) Color.White.copy(alpha = 0.3f)
                else if (isFaceUp) card.color
                else Color(0xFF444444),
                RoundedCornerShape(8.dp)
            )
            .then(if (!isFaceUp && !isMatched) Modifier.pointerInput(Unit) {
                detectDragGestures { _, _ -> } // consume to prevent parent scroll
            } else Modifier)
            .then(if (!isMatched) Modifier
                .pointerInput(card.id) {
                    detectDragGestures(
                        onDragStart = { onClick() },
                        onDrag = { _, _ -> }
                    )
                } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            // Face up: show symbol
            Text(
                text = card.symbol,
                fontSize = 28.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        } else {
            // Face down: card back u2014 retro hatched pattern
            CardBack()
        }
    }
}

@Composable
private fun CardBack() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Simple back pattern: "?"
        Text(
            text = "?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF444444)
        )
    }
}