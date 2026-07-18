package com.fictioncutshort.justacalculator.ui.screens

import com.fictioncutshort.justacalculator.R
import androidx.compose.animation.core.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI
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
    PEXESO,      // Memory game
    BEEP,        // Black-screen "Can you hear the beep?" audio check
    INTRO,       // Black screen: vo001 over radio.mp3, then vo002
    TUNNEL,      // Warp-through transition → city
    CITY         // 3-D calculator cityscape
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
    val imageRes: Int? = null, // Drawable res — null shows placeholder number
)

private fun buildCardStack(): List<AdCard> {
    // ── Swipe-only cards (ids 0–4) → AdsPexeso_21 … AdsPexeso_25 ─────────────
    val swipeImages = listOf(
        R.drawable.adspexeso_21,
        R.drawable.adspexeso_22,
        R.drawable.adspexeso_23,
        R.drawable.adspexeso_24,
        R.drawable.adspexeso_25,
    )
    val swipeOnly = (0 until 5).map { i ->
        AdCard(
            id          = i,
            number      = i + 1,
            color       = CARD_COLORS[i % CARD_COLORS.size],
            accentColor = CARD_COLORS[(i + 5) % CARD_COLORS.size],
            isSwipeable = true,
            imageRes    = swipeImages[i]
        )
    }

    // ── Pexeso cards (ids 5–24) → AdsPexeso_1 … AdsPexeso_20 ────────────────
    val pexeso = (0 until 20).map { i ->
        AdCard(
            id          = i + 5,
            number      = i + 6,
            color       = CARD_COLORS[i % CARD_COLORS.size],
            accentColor = CARD_COLORS[(i + 5) % CARD_COLORS.size],
            isSwipeable = false,
            imageRes    = null  // assigned via PEXESO_PAIR_IMAGES
        )
    }

    return swipeOnly + pexeso
}

// u2500u2500 Retro era visual themes (applied to card borders/decorations) u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
private enum class RetroTheme {
    WINDOWS_XP,    // Bliss-era, beveled edges, Luna blue
    IOS4,          // Glossy, rounded, linen texture feel
    WIN_2016,      // Flat, accent strip at top, bold font
    MINIMALIST,    // Pure, single thin border
    GLITCH         // Offset color layers
}

private val retroThemes = RetroTheme.entries.toTypedArray()


private const val PEEK_PX = 16f

private data class CardDims(
    val cardWPx:  Float,
    val cardHPx:  Float,
    val cellWPx:  Float,
    val cellHPx:  Float,
    val gridOffsetXPx: Float,
    val gridOffsetYPx: Float,
    val cellGapPx: Float,
    val gridTopPaddingPx: Float,
)

private val LocalCardDims = staticCompositionLocalOf<CardDims> {
    error("CardDims not provided")
}

private val AccelerateEasing = Easing { t -> t * t }

// ─────────────────────────────────────────────────────────────────────────────
// MAIN COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// CALCULATOR BACKGROUND (GREYSCALE)
// Renders the real PortraitCalculatorContent composables directly, then applies
// a greyscale ColorMatrix filter over the top. Pixel-perfect match guaranteed.
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// TV STATIC NOISE  (copied from DormancyOverlay)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AdStaticNoise(modifier: Modifier = Modifier, darkened: Boolean = false) {
    // Canvas-based: single draw call per frame instead of hundreds of Box composables
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(80); tick++ } }

    val grayMin   = if (darkened) 5   else 60
    val grayMax   = if (darkened) 55  else 200
    val alphaBase = if (darkened) 0.6f else 0.1f
    val alphaSpan = if (darkened) 0.3f else 0.7f

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val cellPx = 6.dp.toPx()
        val cols = (size.width  / cellPx).toInt() + 1
        val rows = (size.height / cellPx).toInt() + 1
        val rng  = Random(tick * 7919L)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val gray  = rng.nextInt(grayMin, grayMax)
                val alpha = rng.nextFloat() * alphaSpan + alphaBase
                drawRect(
                    color = Color(gray / 255f, gray / 255f, gray / 255f, alpha),
                    topLeft = androidx.compose.ui.geometry.Offset(col * cellPx, row * cellPx),
                    size = androidx.compose.ui.geometry.Size(cellPx, cellPx)
                )
            }
        }
    }
}

@Composable
private fun DamagedCalculatorBackground(modifier: Modifier = Modifier) {
    // TEMP: static noise only, calculator hidden
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        AdStaticNoise(darkened = true)
    }
}


@Composable
fun AdCardStack(
    onPexesoComplete: () -> Unit,   // Reserved for when the full experience ends
    startAtCity: Boolean = false,
    // Resume straight into the pexeso game (at its start) — used when the app was
    // minimised/killed mid-pexeso. Ignored if startAtCity is also set.
    startAtPexeso: Boolean = false,
    onCityEntered: () -> Unit = {},
    // Reports the coarse phase-2 stage ("adcards" / "pexeso" / "city") as the stack
    // advances, so the host can persist it for minimise/kill restore.
    onStageChanged: (String) -> Unit = {},
    // Passed straight through to the city so its debug menu can jump into phase 1.
    onJumpToPhase1: ((com.fictioncutshort.justacalculator.data.Chapter) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // u2500u2500 State u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
    val cards = remember { buildCardStack() }
    var swipedCount by remember { mutableIntStateOf(0) }       // 0-5 cards swiped
    var phase by remember {
        mutableStateOf(
            when {
                startAtCity   -> AdCardPhase.CITY
                startAtPexeso -> AdCardPhase.PEXESO
                else          -> AdCardPhase.CARDS
            }
        )
    }

    // Persist the city phase whenever it is active — including on the very first
    // composition. Entry is sticky regardless of HOW the player got here (normal
    // tunnel flow, the restore-on-launch re-entry, OR the debug "direct jump"),
    // so once you're in Calculator City, closing/relaunching the app always
    // drops you back in. onCityEntered() (→ saveInCityPhase) is idempotent.
    var previousPhase by remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        android.util.Log.d("JustACalc", "TANK-DEBUG AdCardStack phase=$phase (prev=$previousPhase)")
        if (phase == AdCardPhase.CITY) {
            onCityEntered()
        }
        // Persist the coarse stage so a minimise/kill resumes here. Only CITY emits
        // "city" — that's the phase that also sets in_city_phase (via onCityEntered),
        // and the two must agree for the city-restore path. The momentary COLLAPSING
        // and TUNNEL transitions fall back to "pexeso" (a fully restorable stage) so
        // a kill mid-transition still lands sensibly rather than dropping through.
        when (phase) {
            AdCardPhase.CARDS      -> onStageChanged("adcards")
            AdCardPhase.COLLAPSING,
            AdCardPhase.PEXESO,
            AdCardPhase.BEEP,
            AdCardPhase.INTRO,
            AdCardPhase.TUNNEL     -> onStageChanged("pexeso")
            AdCardPhase.CITY       -> onStageChanged("city")
        }
        previousPhase = phase
    }
    DisposableEffect(Unit) {
        android.util.Log.d("JustACalc", "TANK-DEBUG AdCardStack ENTER composition (startAtCity=$startAtCity, phase=$phase)")
        onDispose { android.util.Log.d("JustACalc", "TANK-DEBUG AdCardStack LEAVE composition") }
    }

    // Hint nudge: auto-nudges the top card left/right after 2s idle
    var hintNudgeTarget by remember { mutableFloatStateOf(0f) }
    var hintActive by remember { mutableStateOf(false) }

    // Pexeso game state
    val pexesoCards = remember { buildPexesoGrid() }
    var pexesoFlipped by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pexesoMatched by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pexesoLocked by remember { mutableStateOf(false) }
    var pexesoEnlarged by remember { mutableStateOf<Int?>(null) }
    var showPairReunion by remember { mutableStateOf(false) }

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

    // ── Collapse trigger + pexeso reveal transition
    // Start fully revealed when we resume directly into the pexeso game.
    var pexesoReveal by remember { mutableFloatStateOf(if (startAtPexeso) 1f else 0f) }
    val pexesoRevealAnim by animateFloatAsState(
        targetValue   = pexesoReveal,
        animationSpec = tween(400),
        label         = "pexesoReveal"
    )

    LaunchedEffect(swipedCount) {
        if (swipedCount >= 5) {
            phase = AdCardPhase.COLLAPSING
            delay(1600)
            phase = AdCardPhase.PEXESO
            pexesoReveal = 1f
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density    = androidx.compose.ui.platform.LocalDensity.current
        val screenWPx  = with(density) { maxWidth.toPx() }
        val screenHPx  = with(density) { maxHeight.toPx() }

        // The card keeps a fixed 0.65 width:height ratio. Fit it inside the
        // screen by whichever axis is the tighter constraint — width-bound in
        // portrait, height-bound in landscape — so the card never overflows or
        // appears oversized/clipped when the screen is wide.
        val CARD_RATIO   = 0.65f                  // width / height
        val maxCardHByH  = screenHPx * 0.82f
        val cardWByWidth = screenWPx * 0.90f
        val cardHByWidth = cardWByWidth / CARD_RATIO
        val (cardWPx, cardHPx) = if (cardHByWidth <= maxCardHByH) {
            cardWByWidth to cardHByWidth
        } else {
            (maxCardHByH * CARD_RATIO) to maxCardHByH
        }

        val gapPx     = with(density) { 8.dp.toPx() }
        val sidePadPx = with(density) { 16.dp.toPx() }
        val headerPx  = with(density) { 100.dp.toPx() }
        val gridW     = screenWPx - sidePadPx * 2
        val cellWPx   = (gridW - gapPx * 3) / 4f
        val cellHPx   = cellWPx / 0.7f

        val dims = remember(screenWPx, screenHPx) {
            CardDims(
                cardWPx = cardWPx, cardHPx = cardHPx,
                cellWPx = cellWPx, cellHPx = cellHPx,
                gridOffsetXPx = sidePadPx - screenWPx / 2f,
                gridOffsetYPx = headerPx  - screenHPx / 2f,
                cellGapPx = gapPx, gridTopPaddingPx = headerPx
            )
        }

        CompositionLocalProvider(LocalCardDims provides dims) {
            Box(modifier = Modifier.fillMaxSize()) {

                // ── Damaged calculator background — always visible ──
                DamagedCalculatorBackground()

                // ── Back 20 cards always present (base layer) ──
                // Visible during CARDS phase as depth behind the swipeable 5.
                // Hidden during COLLAPSING (the animation owns them) and PEXESO.
                if (phase == AdCardPhase.CARDS) {
                    val backCards = cards.drop(5).reversed()
                    val localDensity2 = androidx.compose.ui.platform.LocalDensity.current
                    val bCardWDp = with(localDensity2) { dims.cardWPx.toDp() }
                    val bCardHDp = with(localDensity2) { dims.cardHPx.toDp() }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        backCards.forEachIndexed { bi, card ->
                            key(card.id) {
                                val depth = backCards.size - bi
                                val peekY = minOf(depth * PEEK_PX, 5 * PEEK_PX)
                                val baseAlpha = when {
                                    depth <= 1 -> 0.50f
                                    depth <= 2 -> 0.38f
                                    else       -> 0.25f
                                }
                                Box(
                                    modifier = Modifier
                                        .size(bCardWDp, bCardHDp)
                                        .graphicsLayer {
                                            translationY = peekY
                                            alpha = baseAlpha
                                        }
                                        .background(card.color)
                                        .retroCardBorder(retroThemes[card.id % retroThemes.size], card.accentColor)
                                )
                            }
                        }
                    }
                }

                when (phase) {
                    AdCardPhase.CARDS -> CardStackScene(
                        cards = cards, swipedCount = swipedCount,
                        hintNudgeTarget = hintNudgeTarget,
                        onSwiped = { swipedCount++ }
                    )
                    AdCardPhase.COLLAPSING -> CollapseIntoGridAnimation(
                        adCards = cards, pexesoCards = pexesoCards,
                    )
                    AdCardPhase.TUNNEL -> WarpTunnel(
                        onComplete = { phase = AdCardPhase.CITY },
                        onJumpToPhase1 = onJumpToPhase1
                    )
                    AdCardPhase.BEEP -> BeepCheckScreen(
                        onConfirmed = { phase = AdCardPhase.INTRO }
                    )
                    AdCardPhase.INTRO -> {
                        val introCtx = LocalContext.current
                        // vo001 over the radio bed, then vo002 right after; the radio
                        // plays out its full length under both. Only once vo002 finishes
                        // do we drop into the city (its aerial-flicker intro), so 003 can
                        // fire when the camera starts moving.
                        LaunchedEffect(Unit) {
                            com.fictioncutshort.justacalculator.logic.VoiceoverManager.init(introCtx)
                            com.fictioncutshort.justacalculator.logic.VoiceoverManager.playWithRadio(
                                voRes = R.raw.vo001,
                                radioRes = R.raw.radio,
                                radioVolume = 0.55f,
                                onVoComplete = {
                                    com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(
                                        resId = R.raw.vo002,
                                        cctv = false,
                                        onComplete = {
                                            com.fictioncutshort.justacalculator.logic.VoiceoverManager.stopRadio()
                                            phase = AdCardPhase.CITY
                                        }
                                    )
                                }
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    }
                    AdCardPhase.CITY -> CalculatorCityView(
                        modifier = Modifier.fillMaxSize(),
                        onJumpToPhase1 = onJumpToPhase1
                    )
                    AdCardPhase.PEXESO -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = pexesoRevealAnim }
                    ) {
                        PexesoGame(
                            cards = pexesoCards,
                            flipped = pexesoFlipped, matched = pexesoMatched,
                            enlargedIdx = pexesoEnlarged,
                            showReunion = showPairReunion,
                            onReunionComplete = { phase = AdCardPhase.BEEP },
                            onCardFlip = { idx ->
                                if (pexesoLocked) return@PexesoGame
                                if (pexesoMatched.contains(idx) || pexesoFlipped.contains(idx)) return@PexesoGame
                                val newFlipped = pexesoFlipped + idx
                                pexesoFlipped = newFlipped
                                pexesoEnlarged = idx
                                scope.launch {
                                    delay(1200); pexesoEnlarged = null
                                    if (newFlipped.size == 2) {
                                        pexesoLocked = true; delay(300)
                                        val (a, b) = newFlipped.toList()
                                        if (pexesoCards[a].pairId == pexesoCards[b].pairId) {
                                            pexesoMatched = pexesoMatched + a + b
                                            if (pexesoMatched.size == pexesoCards.size) {
                                                delay(800); showPairReunion = true
                                            }
                                        }
                                        pexesoFlipped = emptySet(); pexesoLocked = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WARP TUNNEL TRANSITION
// Plays between pexeso end and city start:
//   1. White flash (simulates zooming through the card grid)
//   2. Dark tunnel with warp stars rushing outward (~2.5 s)
//   3. City fades in underneath as tunnel fades out (~0.7 s)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WarpTunnel(
    onComplete: () -> Unit,
    onJumpToPhase1: ((com.fictioncutshort.justacalculator.data.Chapter) -> Unit)? = null,
) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(16L); tick++ } }

    // Timing (ticks × 16 ms each)
    val FLASH_END  = 22L   // 352 ms — white flash fades out
    val WARP_END   = 185L  // 2.96 s — warp stars end, city starts fading in
    val TOTAL      = 230L  // 3.68 s — tunnel gone, city fully visible

    LaunchedEffect(Unit) {
        delay(TOTAL * 16L + 80L)
        onComplete()
    }

    val flashAlpha = if (tick <= FLASH_END)
        1f - tick.toFloat() / FLASH_END.toFloat() else 0f

    val cityAlpha = if (tick > WARP_END)
        ((tick - WARP_END).toFloat() / (TOTAL - WARP_END).toFloat()).coerceIn(0f, 1f) else 0f

    val tunnelAlpha = (1f - cityAlpha).coerceIn(0f, 1f)

    // Pre-warm the city compositor a little before it becomes visible
    var showCity by remember { mutableStateOf(false) }
    LaunchedEffect(tick) { if (tick >= WARP_END - 15L && !showCity) showCity = true }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── City underneath (fades in as tunnel fades out) ────────────────────
        if (showCity) {
            CalculatorCityView(
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = cityAlpha },
                onJumpToPhase1 = onJumpToPhase1
            )
        }

        // ── Warp-star canvas ──────────────────────────────────────────────────
        if (tunnelAlpha > 0.01f) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = tunnelAlpha }
            ) {
                drawRect(Color.Black)

                val cx = size.width  / 2f
                val cy = size.height / 2f
                val maxR = sqrt(cx * cx + cy * cy)
                val warpTick = (tick - FLASH_END).coerceAtLeast(0L)

                for (i in 0 until 200) {
                    val rng   = Random(i * 7919L)
                    val angle = rng.nextFloat() * 2f * PI.toFloat()
                    val speed = rng.nextFloat() * 13f + 5f
                    val off   = rng.nextFloat() * maxR * 0.35f

                    val r     = (off + warpTick * speed) % maxR
                    val rPrev = (off + (warpTick - 1).coerceAtLeast(0L) * speed) % maxR
                    if (r < rPrev) continue   // wrapped — skip this frame

                    val cosA = cos(angle.toDouble()).toFloat()
                    val sinA = sin(angle.toDouble()).toFloat()
                    val brightness = (r / maxR).coerceIn(0.1f, 1f)

                    drawLine(
                        color = Color(1f, 1f, brightness * 0.6f + 0.4f, brightness * 0.8f + 0.2f),
                        start = androidx.compose.ui.geometry.Offset(cx + rPrev * cosA, cy + rPrev * sinA),
                        end   = androidx.compose.ui.geometry.Offset(cx + r * cosA, cy + r * sinA),
                        strokeWidth = brightness * 3.5f + 0.5f
                    )
                }
            }
        }

        // ── White flash (zoom-through feel) ───────────────────────────────────
        if (flashAlpha > 0.01f) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha.coerceIn(0f, 1f))))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CARD STACK SCENE  (swipe phase)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardStackScene(
    cards: List<AdCard>,
    swipedCount: Int,
    hintNudgeTarget: Float,
    onSwiped: () -> Unit
) {
    if (swipedCount >= 5) return

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Fixed slots 0–4: always in composition, never re-keyed on swipe
        for (slot in 4 downTo 0) {
            val card = cards[slot]
            val depthOffset = slot - swipedCount

            key(card.id) {
                CardLayer(
                    card            = card,
                    depthOffset     = depthOffset,
                    isTop           = depthOffset == 0,
                    parallaxX       = 0f,
                    parallaxY       = 0f,
                    hintNudgeTarget = if (depthOffset == 0) hintNudgeTarget else 0f,
                    onSwiped        = if (depthOffset == 0) onSwiped else null
                )
            }
        }
    }
}

@Composable
private fun CardLayer(
    card: AdCard,
    depthOffset: Int, isTop: Boolean,
    parallaxX: Float, parallaxY: Float,
    hintNudgeTarget: Float,
    onSwiped: (() -> Unit)?
) {
    val dims    = LocalCardDims.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cardWDp = with(density) { dims.cardWPx.toDp() }
    val cardHDp = with(density) { dims.cardHPx.toDp() }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging  by remember { mutableStateOf(false) }
    var isFlyingOff by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val nudgeAnim by animateFloatAsState(
        targetValue  = if (!isDragging && !isFlyingOff) hintNudgeTarget else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label        = "nudge"
    )

    val animatedPeekY by animateFloatAsState(
        targetValue   = maxOf(depthOffset, 0) * PEEK_PX,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label         = "peek${card.id}"
    )

    Box(
        modifier = Modifier
            .size(cardWDp, cardHDp)
            .graphicsLayer {
                translationX = when {
                    isFlyingOff -> dragOffsetX + parallaxX
                    isDragging  -> dragOffsetX + nudgeAnim + parallaxX
                    else        -> nudgeAnim + parallaxX
                }
                translationY = when {
                    isDragging || isFlyingOff -> dragOffsetY + animatedPeekY + parallaxY
                    else                      -> animatedPeekY + parallaxY
                }
                rotationZ = if (isDragging || isFlyingOff) dragOffsetX * 0.03f else 0f
                alpha = when {
                    depthOffset < 0  -> 0f   // already swiped — invisible, stays in composition for fly-off
                    isFlyingOff      -> 1f
                    depthOffset == 0 -> 1f
                    depthOffset == 1 -> 0.92f
                    depthOffset == 2 -> 0.82f
                    depthOffset == 3 -> 0.70f
                    else             -> 0.55f
                }
            }
            .then(
                if (isTop && onSwiped != null) Modifier.pointerInput(card.id) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDrag = { change, delta ->
                            change.consume()
                            dragOffsetX += delta.x; dragOffsetY += delta.y
                        },
                        onDragEnd = {
                            if (abs(dragOffsetX) > 220f) {
                                val dir = sign(dragOffsetX)
                                isFlyingOff = true; isDragging = false
                                scope.launch {
                                    dragOffsetX = dir * 1600f; delay(300)
                                    onSwiped()
                                    isFlyingOff = false; dragOffsetX = 0f; dragOffsetY = 0f
                                }
                            } else {
                                isDragging = false; dragOffsetX = 0f; dragOffsetY = 0f
                            }
                        },
                        onDragCancel = {
                            isDragging = false; dragOffsetX = 0f; dragOffsetY = 0f
                        }
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        AdCardFace(card = card)
    }
}

// AD CARD FACE u2014 Placeholder visual
// Each card: solid color background + large number + retro-styled border
// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500

@Composable
private fun AdCardFace(
    card: AdCard
) {
    val theme = retroThemes[card.id % retroThemes.size]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .retroCardBorder(theme, card.accentColor),
        contentAlignment = Alignment.Center
    ) {
        if (card.imageRes != null) {
            // Ad image fills the card — no background color, image fills edge to edge
            androidx.compose.foundation.Image(
                painter = painterResource(id = card.imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Retro decoration overlay on top of image
            RetroDecoration(theme = theme, accentColor = card.accentColor)
        } else {
            // Placeholder until images are assigned
            RetroDecoration(theme = theme, accentColor = card.accentColor)
            Text(
                text = card.number.toString(),
                fontSize = 120.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "AD PLACEHOLDER",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
            )
        }


    }
}

@Composable
private fun RetroDecoration(theme: RetroTheme, accentColor: Color) {
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
                        Text("📁 Advertisement", fontSize = 11.sp, color = Color.White,
                            fontFamily = FontFamily.Monospace)
                        // Window buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("_","□","✕").forEach { btn ->
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
                        "  ▶ Start",
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
                    Text("▐▐▐▐▐ ᵂᶦᵠᶦ 🔋", fontSize = 9.sp, color = Color.White)
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
        RetroTheme.IOS4 -> this.border(2.dp, Color.White.copy(alpha = 0.4f))
        RetroTheme.WIN_2016 -> this.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(0.dp))
        RetroTheme.MINIMALIST -> this.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        RetroTheme.GLITCH -> this.border(2.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
    }
}

// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500
// COLLAPSE ANIMATION u2014 remaining 20 cards fall after 5th swipe
// u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500

// ─────────────────────────────────────────────────────────────────────────────
// COLLAPSE ANIMATION
// Cards fall straight "down" from the overhead camera's POV — they shrink
// in place, land with a scatter, and stay there. Then black fades in.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CollapseIntoGridAnimation(
    adCards: List<AdCard>,
    pexesoCards: List<PexesoCard>,
) {
    val dims = LocalCardDims.current
    val collapseCards = adCards.drop(5)

    val localDensity = androidx.compose.ui.platform.LocalDensity.current
    val cardWDp = with(localDensity) { dims.cardWPx.toDp() }
    val cardHDp = with(localDensity) { dims.cardHPx.toDp() }

    var blackStart by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(750); blackStart = true }
    val blackAlpha by animateFloatAsState(
        targetValue   = if (blackStart) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "blackFade"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

        collapseCards.forEachIndexed { i, card ->
            val rng = remember { Random(card.id * 37 + 11) }

            val landOffsetX = remember { rng.nextFloat() * dims.cardWPx * 0.55f - dims.cardWPx * 0.275f }
            val landOffsetY = remember { rng.nextFloat() * dims.cardHPx * 0.45f - dims.cardHPx * 0.225f }
            val landRotZ    = remember { rng.nextFloat() * 12f - 6f }

            val staggerMs = remember { ((collapseCards.size - 1 - i) * 25L) }
            val duration  = remember { 500 + rng.nextInt(150) }

            var started by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(staggerMs); started = true }

            val p by animateFloatAsState(
                targetValue   = if (started) 1f else 0f,
                animationSpec = tween(duration, easing = FastOutSlowInEasing),
                label         = "fall$i"
            )

            // Match pexeso card by position (collapseCards index == pexesoCards index after shuffle)
            val pexesoCard = pexesoCards.getOrNull(i)
            Box(
                modifier = Modifier
                    .size(cardWDp, cardHDp)
                    .graphicsLayer {
                        val sc = 1f - p * 0.75f
                        scaleX = sc; scaleY = sc
                        translationX = p * landOffsetX
                        translationY = p * landOffsetY
                        rotationZ    = p * landRotZ
                        alpha = 1f
                    }
                    .background(pexesoCard?.color ?: card.color),
                contentAlignment = Alignment.Center
            ) {
                if (pexesoCard?.imageRes != null) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = pexesoCard.imageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = pexesoCard?.symbol ?: card.number.toString(),
                        fontSize = 28.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = blackAlpha }
                .background(Color.Black)
        )
    }
}

data class PexesoCard(
    val id: Int,
    val pairId: Int,      // cards with same pairId are a match
    val imageRes: Int?,   // face-up image — each card in the pair has its own
    // Fallback color + symbol used until images are assigned
    val color: Color,
    val symbol: String
)

private val PEXESO_SYMBOLS = listOf("u2605","u25c6","u25b2","u25cf","u25a0","u2726","u2b1f","u2b21","u2b22","u2b23")
private val PEXESO_COLORS  = listOf(
    Color(0xFF1A73E8), Color(0xFFEA4335), Color(0xFF34A853), Color(0xFFFBBC04),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722), Color(0xFF607D8B),
    Color(0xFF4CAF50), Color(0xFFFF9800)
)

// ── Pexeso pair definitions ───────────────────────────────────────────────────
// AdsPexeso_1.png … AdsPexeso_20.png → drawable names: adspexeso_1 … adspexeso_20
// Pairs: (1,2), (3,4), (5,6), (7,8), (9,10), (11,12), (13,14), (15,16), (17,18), (19,20)
// Each pair matches its own two cards — identity fixed, grid position shuffles each launch.
private val PEXESO_PAIR_IMAGES: List<Pair<Int?, Int?>> = listOf(
    Pair(R.drawable.adspexeso_1,  R.drawable.adspexeso_2),  // pair 0
    Pair(R.drawable.adspexeso_3,  R.drawable.adspexeso_4),  // pair 1
    Pair(R.drawable.adspexeso_5,  R.drawable.adspexeso_6),  // pair 2
    Pair(R.drawable.adspexeso_7,  R.drawable.adspexeso_8),  // pair 3
    Pair(R.drawable.adspexeso_9,  R.drawable.adspexeso_10), // pair 4
    Pair(R.drawable.adspexeso_11, R.drawable.adspexeso_12), // pair 5
    Pair(R.drawable.adspexeso_13, R.drawable.adspexeso_14), // pair 6
    Pair(R.drawable.adspexeso_15, R.drawable.adspexeso_16), // pair 7
    Pair(R.drawable.adspexeso_17, R.drawable.adspexeso_18), // pair 8
    Pair(R.drawable.adspexeso_19, R.drawable.adspexeso_20), // pair 9
)

private fun buildPexesoGrid(): List<PexesoCard> {
    // Each pair produces 2 cards with their own image but the same pairId.
    // Grid positions are shuffled fresh each launch — identity never changes.
    val pairs = PEXESO_PAIR_IMAGES.flatMapIndexed { pairId, (imgA, imgB) ->
        listOf(
            PexesoCard(
                id       = pairId * 2,
                pairId   = pairId,
                imageRes = imgA,
                color    = PEXESO_COLORS[pairId],
                symbol   = PEXESO_SYMBOLS[pairId]
            ),
            PexesoCard(
                id       = pairId * 2 + 1,
                pairId   = pairId,
                imageRes = imgB,
                color    = PEXESO_COLORS[pairId],
                symbol   = PEXESO_SYMBOLS[pairId]
            )
        )
    }
    return pairs.shuffled() // positions random each launch, identity fixed
}

@Composable
private fun PexesoGame(
    cards: List<PexesoCard>,
    flipped: Set<Int>,
    matched: Set<Int>,
    enlargedIdx: Int?,
    showReunion: Boolean,
    onReunionComplete: () -> Unit,
    onCardFlip: (Int) -> Unit
) {
    val matchedCount = matched.size / 2
    val totalPairs = cards.size / 2

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
            // Card grid — sized to fit the available area so it never
            // overflows. Portrait: 4×5 (tall). Landscape: 5×4 (wide) to suit
            // the screen shape.
            val gap = 8.dp
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints {
                    val isLandscape = maxWidth > maxHeight
                    val cols = if (isLandscape) 5 else 4
                    val rows = if (isLandscape) 4 else 5

                    // Cells keep a 0.7 width:height ratio. Find the largest cell
                    // that fits both the width and the height of the area, then
                    // size the grid to that — centered.
                    val cellByWidth  = (maxWidth  - gap * (cols - 1)) / cols
                    val cellByHeight = ((maxHeight - gap * (rows - 1)) / rows) * 0.7f
                    val cellW = minOf(cellByWidth, cellByHeight)
                    val cellH = cellW / 0.7f

                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        (0 until rows).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
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
                                            isEnlarged = enlargedIdx == idx,
                                            onClick = { onCardFlip(idx) },
                                            modifier = Modifier.size(cellW, cellH)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(cellW, cellH))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } // end Column

        // Enlarged card overlay — rendered over the grid in the outer Box
        if (enlargedIdx != null && enlargedIdx < cards.size) {
            val enlargedCard = cards[enlargedIdx]
            val dims = LocalCardDims.current
            val ld = androidx.compose.ui.platform.LocalDensity.current
            val bigWDp = with(ld) { dims.cardWPx.toDp() }
            val bigHDp = with(ld) { dims.cardHPx.toDp() }
            val ea by animateFloatAsState(1f, tween(200), label = "enlA")
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = ea }
                    .background(Color.Black.copy(0.55f))
            )
            Box(
                modifier = Modifier
                    .size(bigWDp, bigHDp)
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = ea }
                    .background(enlargedCard.color)
                    .border(2.dp, Color.White.copy(0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (enlargedCard.imageRes != null) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = enlargedCard.imageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = enlargedCard.symbol,
                        fontSize = 88.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } // end outer Box

    // Pair reunion overlay — shown after all pairs matched
    if (showReunion) {
        PairReunionOverlay(
            cards = cards,
            onComplete = onReunionComplete
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PAIR REUNION OVERLAY
// After all pairs matched: cards animate into side-by-side pair rows,
// hold for 20 seconds, then call onComplete.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PairReunionOverlay(
    cards: List<PexesoCard>,
    onComplete: () -> Unit
) {
    // Group cards by pairId, preserving pair order 0–9
    val pairs = remember(cards) {
        cards.groupBy { it.pairId }
            .entries.sortedBy { it.key }
            .map { it.value.sortedBy { c -> c.id } }
    }

    // Animate in, hold 20s, then call onComplete
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        revealed = true
        delay(20_000)
        onComplete()
    }

    val bgAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(500),
        label = "reunionBg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = bgAlpha }
            .background(Color.Black)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight

            if (isLandscape) {
                // Landscape: 5 pairs × 2 rows, sized to fit without scrolling.
                val gap     = 8.dp
                val pairGap = 16.dp
                val pad     = 16.dp
                val pairsPerRow = 5
                val pairRows    = 2
                val cardsAcross = pairsPerRow * 2
                val availW = maxWidth  - pad * 2
                val availH = maxHeight - pad * 2 - 56.dp   // leave room for the title
                val hGaps  = gap * pairsPerRow + pairGap * (pairsPerRow - 1)
                val cellByWidth  = (availW - hGaps) / cardsAcross
                val cellByHeight = ((availH - gap * (pairRows - 1)) / pairRows) * 0.7f
                val cellW = minOf(cellByWidth, cellByHeight)
                val cellH = cellW / 0.7f

                Column(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ALL MATCHED",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        (0 until pairRows).forEach { r ->
                            Row(horizontalArrangement = Arrangement.spacedBy(pairGap)) {
                                (0 until pairsPerRow).forEach { c ->
                                    val pairIdx = r * pairsPerRow + c
                                    val pair = pairs[pairIdx]
                                    val slideIn by animateFloatAsState(
                                        targetValue = if (revealed) 0f else 1f,
                                        animationSpec = tween(400, pairIdx * 60, FastOutSlowInEasing),
                                        label = "slideL$pairIdx"
                                    )
                                    Row(
                                        modifier = Modifier.graphicsLayer { translationX = slideIn * 300f },
                                        horizontalArrangement = Arrangement.spacedBy(gap)
                                    ) {
                                        pair.forEach { card ->
                                            ReunionCard(card, Modifier.size(cellW, cellH))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Portrait: one pair per row, scrollable.
                val scrollState = androidx.compose.foundation.rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ALL MATCHED",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    pairs.forEachIndexed { pairIdx, pair ->
                        val slideIn by animateFloatAsState(
                            targetValue = if (revealed) 0f else 1f,
                            animationSpec = tween(
                                durationMillis = 400,
                                delayMillis = pairIdx * 60,
                                easing = FastOutSlowInEasing
                            ),
                            label = "slide$pairIdx"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { translationX = slideIn * 300f },
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { card ->
                                ReunionCard(card, Modifier.weight(1f).aspectRatio(0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReunionCard(card: PexesoCard, modifier: Modifier) {
    Box(
        modifier = modifier.background(card.color),
        contentAlignment = Alignment.Center
    ) {
        if (card.imageRes != null) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = card.imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = card.symbol,
                fontSize = 22.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PexesoCardCell(
    card: PexesoCard,
    isFaceUp: Boolean,
    isMatched: Boolean,
    isEnlarged: Boolean,
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
            .background(if (rotation <= 90f) card.color else Color(0xFF2A2A2A))
            .border(
                1.5.dp,
                if (isMatched) Color.White.copy(alpha = 0.3f)
                else if (isFaceUp) card.color
                else Color(0xFF444444),
                shape = RectangleShape
            )
            .zIndex(if (isEnlarged) 5f else 1f)
            .then(
                if (!isMatched) Modifier.pointerInput(card.id, isFaceUp) {
                    detectTapGestures(onTap = { if (!isFaceUp) onClick() })
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            // Face up: show ad image if assigned, otherwise fallback symbol
            if (card.imageRes != null) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = card.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = card.symbol,
                    fontSize = 28.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
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