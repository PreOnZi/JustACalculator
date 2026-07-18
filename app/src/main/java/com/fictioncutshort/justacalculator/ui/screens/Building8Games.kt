package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.fictioncutshort.justacalculator.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fictioncutshort.justacalculator.logic.Currency
import com.fictioncutshort.justacalculator.logic.CurrencyStore
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Building 8 — the arcade "website". A persistent early-2000s / Windows-XP style
// browser wraps every game. Each game is rigged: the player is nudged to bet
// without seeing an amount, wins a few times, then loses everything. The relevant
// currency is always on screen — growing on wins, vanishing at the end ("No X
// left."). The browser's Back button never leaves; it just taunts.
// ─────────────────────────────────────────────────────────────────────────────

private val COMIC = FontFamily.Cursive          // "Comic Sans" stand-in (built-in casual face)
private const val PHONE_FRESH = "phonescreen/phonedetour"   // colourful app icons
private const val PHONE_USED = "phonescreen/tankgame"       // greyed / black icons
private const val CASINO_MODELS = "models/casino"

private fun Currency.label(): String = when (this) {
    Currency.COINS -> "coins"; Currency.KEYS -> "keys"
    Currency.GIFTCARDS -> "gift cards"; Currency.COOKIES -> "cookies"; Currency.STARS -> "stars"
}

private fun tauntFor(c: Currency): String = listOf(
    "The night is still young.",
    "You lose 100% of the bets you don't place.",
    "You can't win without playing.",
    "No pain, no gain.",
    "You still have so much ${c.label()} left!",
    "The house always...is the house!",
).random()

// ═════════════════════════════════════════════════════════════════════════════
// Shared UI helpers
// ═════════════════════════════════════════════════════════════════════════════

/** Running-RGB LED dots around a box perimeter (optionally rounded corners). */
@Composable
fun RgbBorder(
    modifier: Modifier = Modifier,
    dotSpacingDp: Dp = 11.dp,
    dotRadiusDp: Dp = 2.6.dp,
    cornerRadiusDp: Dp = 0.dp,
) {
    val t by rememberInfiniteTransition(label = "rgb").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "rgb"
    )
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val r = cornerRadiusDp.toPx().coerceIn(0f, minOf(w, h) / 2f)
        val straightW = (w - 2f * r).coerceAtLeast(0f)
        val straightH = (h - 2f * r).coerceAtLeast(0f)
        val arc = (Math.PI.toFloat() / 2f) * r
        val perim = 2f * straightW + 2f * straightH + 4f * arc
        val spacing = dotSpacingDp.toPx().coerceAtLeast(1f)
        val n = (perim / spacing).toInt().coerceAtLeast(4)
        val rad = dotRadiusDp.toPx()
        for (i in 0 until n) {
            val d = (i.toFloat() / n) * perim
            val p = roundedPerimeterPoint(d, w, h, r, straightW, straightH, arc)
            val hue = (((i.toFloat() / n) + t) % 1f) * 360f
            drawCircle(Color.hsv(hue, 0.9f, 1f), rad, p)
            drawCircle(Color.hsv(hue, 0.9f, 1f).copy(alpha = 0.3f), rad * 2f, p)
        }
    }
}

private fun roundedPerimeterPoint(
    d0: Float, w: Float, h: Float, r: Float, sw: Float, sh: Float, arc: Float,
): Offset {
    if (r <= 0.5f) {
        var x = d0
        if (x < w) return Offset(x, 0f); x -= w
        if (x < h) return Offset(w, x); x -= h
        if (x < w) return Offset(w - x, h); x -= w
        return Offset(0f, h - x)
    }
    val half = (Math.PI / 2.0).toFloat()
    var d = d0
    // top edge → TR arc → right edge → BR arc → bottom → BL arc → left → TL arc
    if (d < sw) return Offset(r + d, 0f); d -= sw
    if (d < arc) { val a = -half + (d / arc) * half; return Offset(w - r + r * kotlin.math.cos(a), r + r * kotlin.math.sin(a)) }; d -= arc
    if (d < sh) return Offset(w, r + d); d -= sh
    if (d < arc) { val a = (d / arc) * half; return Offset(w - r + r * kotlin.math.cos(a), h - r + r * kotlin.math.sin(a)) }; d -= arc
    if (d < sw) return Offset(w - r - d, h); d -= sw
    if (d < arc) { val a = half + (d / arc) * half; return Offset(r + r * kotlin.math.cos(a), h - r + r * kotlin.math.sin(a)) }; d -= arc
    if (d < sh) return Offset(0f, h - r - d); d -= sh
    val a = (Math.PI).toFloat() + (d / arc) * half
    return Offset(r + r * kotlin.math.cos(a), r + r * kotlin.math.sin(a))
}

/**
 * A vertical scroll-wheel of integers [range]. Drag to scroll; flick it and it
 * keeps spinning with momentum, decelerating and snapping to the nearest number.
 */
@Composable
fun NumberWheel(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange = 0..100,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val itemH = 34.dp
    val density = LocalDensity.current
    val itemPx = with(density) { itemH.toPx() }
    var pos by remember { mutableStateOf(value.toFloat()) }
    val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    var fling by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun clampPos() { pos = pos.coerceIn(range.first.toFloat(), range.last.toFloat()) }
    fun emit() { onChange(pos.roundToInt().coerceIn(range.first, range.last)) }

    Box(
        modifier
            .width(60.dp).height(itemH * 3)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141A30))
            .border(1.dp, Color(0xFF3A6FFF).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .pointerInput(range) {
                detectVerticalDragGestures(
                    onDragStart = { fling?.cancel(); tracker.resetTracking() },
                    onDragEnd = {
                        val vPx = tracker.calculateVelocity().y
                        var vUnits = -vPx / itemPx
                        fling = scope.launch {
                            var last = 0L
                            while (kotlin.math.abs(vUnits) > 0.6f) {
                                val fr = withFrameNanos { it }
                                val dt = if (last == 0L) 0.016f else ((fr - last) / 1_000_000_000f)
                                last = fr
                                pos += vUnits * dt
                                clampPos()
                                if (pos <= range.first.toFloat() || pos >= range.last.toFloat()) break
                                vUnits *= kotlin.math.exp(-5f * dt)
                                emit()
                            }
                            val target = pos.roundToInt().coerceIn(range.first, range.last).toFloat()
                            repeat(6) { pos += (target - pos) * 0.4f; emit(); withFrameNanos { } }
                            pos = target; emit()
                        }
                    },
                    onVerticalDrag = { change, dy ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        pos -= dy / itemPx
                        clampPos(); emit()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val center = pos.roundToInt()
        val frac = pos - center
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            for (d in -1..1) {
                val n = center + d
                if (n < range.first || n > range.last) continue
                val yOff = with(density) { ((d - frac) * itemPx).toDp() }
                Text(
                    "$n",
                    color = if (d == 0) Color(0xFFFFD54A) else Color.White.copy(alpha = 0.28f),
                    fontSize = if (d == 0) 26.sp else 15.sp,
                    fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.offset(y = yOff)
                )
            }
        }
    }
}

/** A bet button showing only a currency icon (no amount) + a label. */
@Composable
private fun BetButton(currency: Currency, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val icon = rememberCurrencyIcon(currency)
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Color(0xFFFFC107) else Color(0xFFBFBFBF))
            .border(2.dp, Color(0xFF7A5A00), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon?.let { Image(it, null, Modifier.size(22.dp)) }
        Text(label, color = Color(0xFF2A1E00), fontSize = 16.sp, fontWeight = FontWeight.Bold,
            fontFamily = COMIC)
    }
}

/** Live currency read-out — animates as the balance grows or drops to zero. */
@Composable
private fun CurrencyMeter(currency: Currency, count: Int, modifier: Modifier = Modifier) {
    val icon = rememberCurrencyIcon(currency)
    val shown by animateIntAsState(count, tween(500), label = "meter")
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFF3C4))
            .border(2.dp, Color(0xFFE0B400), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon?.let { Image(it, null, Modifier.size(22.dp)) }
        Text("$shown", color = Color(0xFF5A3E00), fontSize = 18.sp, fontWeight = FontWeight.Black,
            fontFamily = COMIC)
    }
}

/** End-of-game blog card: "No <currency> left." + a link back to the games list. */
@Composable
private fun GameOverBlog(currency: Currency, onDone: () -> Unit, extra: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No ${currency.label()} left.", color = Color(0xFFC00000), fontSize = 26.sp,
            fontWeight = FontWeight.Black, fontFamily = COMIC, textAlign = TextAlign.Center)
        if (extra != null) {
            Spacer(Modifier.height(10.dp))
            Text(extra, color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                fontFamily = COMIC, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        Text("« back to games", color = Color(0xFF0000EE), fontSize = 15.sp, fontFamily = COMIC,
            modifier = Modifier.clickable { onDone() }.padding(8.dp))
    }
}

/** A slowly-drifting linear gradient, for game backgrounds that aren't plain white. */
@Composable
private fun MovingGradient(colors: List<Color>, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "bg").animateFloat(
        0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "bg")
    Canvas(modifier) {
        val shift = t * size.height
        drawRect(Brush.linearGradient(colors,
            start = Offset(0f, -size.height + shift),
            end = Offset(size.width * 0.4f, size.height + shift)))
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// City popup — seeds the coins lottery after Building 5. Stakes ALL coins.
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CityLotteryPopup(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val numbers = remember { mutableStateListOf(0, 0, 0, 0, 0) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF11162B))
                .border(1.dp, Color(0xFF3A6FFF), RoundedCornerShape(18.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Entry to today's draw is ending soon!", color = Color(0xFFFFD54A),
                fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 4.dp))
            Text("Pick your five lucky numbers", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in numbers.indices) {
                    NumberWheel(numbers[i], onChange = { numbers[i] = it })
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BetButton(Currency.COINS, "PLAY") {
                    CurrencyStore.stakeCoins(context, numbers.toList()); onDismiss()
                }
                BetButton(Currency.COINS, "LUCKY DIP") {
                    CurrencyStore.stakeCoins(context, List(5) { (0..100).random() }); onDismiss()
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// The XP browser + blog home
// ═════════════════════════════════════════════════════════════════════════════

private data class GameEntry(
    val id: String, val title: String, val blurb: String, val currency: Currency,
)

private val GAME_ENTRIES = listOf(
    GameEntry("coins", "★ MEGA Number Draw ★", "today's jackpot lottery — results pending!!", Currency.COINS),
    GameEntry("cookies", "Cups & Ball", "follow the ball, DOUBLE your stash!", Currency.COOKIES),
    GameEntry("stars", "Lucky Spinn", "match them all & win it ALL", Currency.STARS),
    GameEntry("giftcard", "Mystery Boxes", "one of these is a jackpot!! (^_^)", Currency.GIFTCARDS),
    GameEntry("keys", "PropTrade Pro", "invest in the housing boom!!", Currency.KEYS),
)

private fun currencyOf(page: String): Currency =
    GAME_ENTRIES.firstOrNull { it.id == page }?.currency ?: Currency.COINS

@Composable
fun ArcadeBrowser(onExitToRoom: () -> Unit, onAllDone: () -> Unit, onGameReturned: () -> Unit) {
    val context = LocalContext.current
    // Which page of the arcade they were on. The games themselves need no saving -
    // a game is "played" exactly when its currency hits zero, and balances persist.
    var page by remember { mutableStateOf(com.fictioncutshort.justacalculator.logic.BuildingProgress.getString(context, 8, "page", "home").ifBlank { "home" }) }
    LaunchedEffect(page) { com.fictioncutshort.justacalculator.logic.BuildingProgress.putString(context, 8, "page", page) }
    var refresh by remember { mutableIntStateOf(0) }
    var taunt by remember { mutableStateOf<String?>(null) }
    var showDrawPopup by remember { mutableStateOf(false) }

    val url = when (page) {
        "home" -> "www.winnerspalace.geocities.com/index.htm"
        else -> "www.winnerspalace.geocities.com/${page}.htm"
    }

    val onDone: () -> Unit = {
        refresh++; onGameReturned()
        // Always return to the menu; the lottery announces itself from there.
        if (CurrencyStore.building8Complete(context)) onAllDone() else page = "home"
    }

    // Back at the menu with the four games spent → the draw announces itself.
    LaunchedEffect(page, refresh) {
        val othersDone = listOf(Currency.COOKIES, Currency.STARS, Currency.GIFTCARDS, Currency.KEYS)
            .all { CurrencyStore.balance(context, it) == 0 }
        if (page == "home" && othersDone && !CurrencyStore.lotterySettled(context)) {
            delay(1400); showDrawPopup = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF2B2B2B)).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ── Windows-XP title bar ──────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF245EDC), Color(0xFF4B8BF5))))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🌐  Winner's Palace — Internet Explorer", color = Color.White,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                for (c in listOf(Color(0xFFBFD4F2), Color(0xFFBFD4F2), Color(0xFFE04343))) {
                    Box(Modifier.padding(start = 4.dp).size(18.dp)
                        .background(c, RoundedCornerShape(3.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(3.dp)))
                }
            }
            // ── Address / navigation bar ──────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFECE9D8))
                    .border(1.dp, Color(0xFFACA899))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF5F4EC))
                        .border(1.dp, Color(0xFF8D8A80), RoundedCornerShape(4.dp))
                        .clickable {
                            // Back never leaves — it just taunts (home included).
                            taunt = tauntFor(if (page == "home") Currency.entries.random() else currencyOf(page))
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("← Back", color = Color(0xFF333333), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text("Address", color = Color(0xFF555555), fontSize = 11.sp)
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(2.dp)).background(Color.White)
                        .border(1.dp, Color(0xFF8D8A80), RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("📄  http://$url", color = Color(0xFF222222), fontSize = 12.sp, maxLines = 1)
                }
            }

            // ── Page body ─────────────────────────────────────────────────────
            Box(Modifier.fillMaxSize().background(Color.White)) {
                when (page) {
                    "home"     -> BlogHome(refresh, onPlay = { page = it })
                    "cookies"  -> CookieCupsGame(onDone)
                    "stars"    -> StarSlotsGame(onDone)
                    "giftcard" -> GiftcardBoxesGame(onDone)
                    "keys"     -> KeysHousingGame(onDone)
                    "coins"    -> CoinsLotteryFinale(onDone)
                }
            }
        }

        // ── Taunt dialog (Back button pressed inside a game) ──────────────────
        taunt?.let { msg ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                .clickable { taunt = null }, contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth(0.8f).clip(RoundedCornerShape(6.dp)).background(Color(0xFFECE9D8))
                        .border(2.dp, Color(0xFF245EDC), RoundedCornerShape(6.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("winnerspalace.geocities.com says:", color = Color.White,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(Color(0xFF245EDC), Color(0xFF4B8BF5))))
                            .padding(8.dp))
                    Text(msg, color = Color.Black, fontSize = 16.sp, fontFamily = COMIC,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
                    Box(
                        Modifier.padding(bottom = 16.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF5F4EC)).border(1.dp, Color(0xFF8D8A80), RoundedCornerShape(4.dp))
                            .clickable { taunt = null }.padding(horizontal = 24.dp, vertical = 6.dp)
                    ) { Text("OK", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // ── "The draw is starting!" announcement (opens the coins finale) ──────
        if (showDrawPopup) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(14.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF3A0A5A), Color(0xFF12002A))))
                        .border(3.dp, Color(0xFFFFD54A), RoundedCornerShape(14.dp)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉  TODAY'S MEGA DRAW  🎉", color = Color(0xFFFFD54A), fontSize = 22.sp,
                        fontWeight = FontWeight.Black, fontFamily = COMIC, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Your ticket is entered. The winning numbers are about to be drawn!",
                        color = Color.White, fontSize = 15.sp, fontFamily = COMIC, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFC107))
                            .clickable { showDrawPopup = false; page = "coins" }
                            .padding(horizontal = 30.dp, vertical = 12.dp)
                    ) {
                        Text("SEE THE DRAW ▶", color = Color(0xFF2A1E00), fontSize = 16.sp,
                            fontWeight = FontWeight.Black, fontFamily = COMIC)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlogHome(refreshKey: Int, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val balances = remember(refreshKey) {
        GAME_ENTRIES.associate { it.id to CurrencyStore.balance(context, it.currency) }
    }
    val othersSpent = remember(refreshKey) {
        listOf(Currency.COOKIES, Currency.STARS, Currency.GIFTCARDS, Currency.KEYS)
            .all { CurrencyStore.balance(context, it) == 0 }
    }
    val lotterySettled = remember(refreshKey) { CurrencyStore.lotterySettled(context) }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(14.dp)) {
        // Blingy banner.
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFFF00A0), Color(0xFF7A00FF), Color(0xFF00A0FF))))
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("~*~ Winner's Palace ~*~", color = Color.White, fontSize = 26.sp,
                fontWeight = FontWeight.Black, fontFamily = COMIC)
        }
        Spacer(Modifier.height(6.dp))
        Text("♦ Everybody's a WINNER here! ♦  Pick a game below!!", color = Color(0xFF008000),
            fontSize = 14.sp, fontFamily = COMIC)
        Text("You are visitor #0000${(1000..9999).random()} ♥", color = Color(0xFF555555),
            fontSize = 11.sp, fontFamily = COMIC)
        Spacer(Modifier.height(12.dp))

        for (e in GAME_ENTRIES) {
            val bal = balances[e.id] ?: 0
            val locked = e.id == "coins" && !othersSpent
            val done = if (e.id == "coins") lotterySettled else bal == 0
            BlogPost(e, locked, done, onPlay)
            Spacer(Modifier.height(12.dp))
        }

        Text("─────────  © 2003 Winner's Palace  ─────────", color = Color(0xFF999999),
            fontSize = 10.sp, fontFamily = COMIC, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun BlogPost(e: GameEntry, locked: Boolean, done: Boolean, onPlay: (String) -> Unit) {
    val icon = rememberCurrencyIcon(e.currency)
    val enabled = !locked && !done
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFFFDF2))
            .border(2.dp, if (enabled) Color(0xFF0000AA) else Color(0xFFBBBBBB), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onPlay(e.id) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Picture" — the game's currency icon in a beveled frame.
        Box(
            Modifier.size(58.dp).background(Color(0xFFECECFF))
                .border(2.dp, Color(0xFF8888CC)),
            contentAlignment = Alignment.Center
        ) { icon?.let { Image(it, null, Modifier.size(46.dp)) } }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = if (enabled) Color(0xFF0000CC) else Color(0xFF888888),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = COMIC)
            Text(
                when {
                    done && e.id == "coins" -> "draw settled."
                    done -> "all spent!"
                    locked -> "awaiting today's draw…"
                    else -> e.blurb
                },
                color = Color(0xFF444444), fontSize = 13.sp, fontFamily = COMIC
            )
            Text(
                when { done -> "" ; locked -> "" ; else -> "» click here to PLAY!" },
                color = Color(0xFFEE0000), fontSize = 13.sp, fontFamily = COMIC, fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 1. COOKIES — three cups & the hidden ball (street-scam)
// ═════════════════════════════════════════════════════════════════════════════

private enum class CupPhase { PREVIEW, BETTING, SHUFFLING, PICK, REVEAL, OVER }

@Composable
fun CookieCupsGame(onDone: () -> Unit) {
    val context = LocalContext.current
    val start = remember { CurrencyStore.balance(context, Currency.COOKIES) }
    if (start <= 0) { GameOverBlogPage(Currency.COOKIES, onDone); return }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Rounds 0,1 you WIN (the ball is genuinely under your pick). The final round
    // the vendor palms it — the ball is under NO cup, and THAT is the loss.
    val rounds = remember { 3 }
    var round by remember { mutableIntStateOf(0) }
    val isPalmed = round >= rounds - 1
    var phase by remember { mutableStateOf(CupPhase.PREVIEW) }
    var meter by remember { mutableIntStateOf(start) }
    var pickedCup by remember { mutableIntStateOf(-1) }

    // Opaque upside-down cup (tilt 180 → you never see inside), brightened.
    val cup = rememberModelBitmap("$CASINO_MODELS/cup.obj", "$CASINO_MODELS/cup.mtl",
        sizePx = 260, tilt = 180f, turn = 18f, colorGamma = 0.5f, fitSpan = 1.4f)
    // The button is a flat red disc — tilt it toward the camera so its face shows.
    val ball = rememberModelBitmap("$CASINO_MODELS/button.obj", "$CASINO_MODELS/button.mtl",
        sizePx = 180, tilt = -72f, turn = 0f, colorGamma = 0.7f)

    val lanePx = with(density) { 118.dp.toPx() }
    fun laneToPx(l: Int) = (l - 1) * lanePx
    val cupLane = remember { mutableStateListOf(0, 1, 2) }              // cupId → lane
    val cupX = remember { List(3) { Animatable(laneToPx(it)) } }        // px
    val cupY = remember { List(3) { Animatable(0f) } }                  // px (negative = up)

    fun resetLanes() {
        cupLane[0] = 0; cupLane[1] = 1; cupLane[2] = 2
        scope.launch { for (i in 0..2) { cupX[i].snapTo(laneToPx(i)); cupY[i].snapTo(0f) } }
    }

    // Cups swap by ARCING through 3D space (up-and-over on the Y axis) — no flat spin.
    fun shuffle() {
        phase = CupPhase.SHUFFLING
        scope.launch {
            repeat(6) {
                var a = (0..2).random(); var b = (0..2).random()
                while (b == a) b = (0..2).random()
                val la = cupLane[a]; val lb = cupLane[b]
                cupLane[a] = lb; cupLane[b] = la
                val arc = lanePx * 0.55f
                launch { cupX[a].animateTo(laneToPx(lb), tween(380, easing = FastOutSlowInEasing)) }
                launch { cupY[a].animateTo(-arc, tween(190)); cupY[a].animateTo(0f, tween(190)) }   // arcs high
                launch { cupX[b].animateTo(laneToPx(la), tween(380, easing = FastOutSlowInEasing)) }
                launch { cupY[b].animateTo(-arc * 0.25f, tween(190)); cupY[b].animateTo(0f, tween(190)) } // low
                delay(400)
            }
            phase = CupPhase.PICK
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.strom), null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.22f },
            contentScale = ContentScale.FillBounds)

        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Cups & Ball", color = Color(0xFF0000CC), fontSize = 24.sp, fontWeight = FontWeight.Black,
                fontFamily = COMIC)
            Spacer(Modifier.height(6.dp))
            CurrencyMeter(Currency.COOKIES, meter)
            Spacer(Modifier.height(8.dp))
            Text(
                when (phase) {
                    CupPhase.PREVIEW -> "Here's the ball. Keep your eye on it!"
                    CupPhase.BETTING -> "Ready when you are…"
                    CupPhase.SHUFFLING -> "Watch carefully!!"
                    CupPhase.PICK -> "Which cup hides the ball?"
                    CupPhase.REVEAL -> if (isPalmed) "It was never there! You're wiped out." else "You found it! Winnings doubled!"
                    CupPhase.OVER -> ""
                },
                color = Color.Black, fontSize = 15.sp, fontFamily = COMIC, textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            if (phase == CupPhase.OVER) {
                GameOverBlog(Currency.COOKIES, onDone)
                Spacer(Modifier.weight(1f))
                return@Column
            }

            // ── Cups row ────────────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.BottomCenter) {
                for (cupId in 0..2) {
                    val revealed = phase == CupPhase.REVEAL
                    val previewLift = phase == CupPhase.PREVIEW && cupLane[cupId] == 1
                    // On a win, only the picked cup lifts; on a palmed loss, all lift (all empty).
                    val lifted = revealed && (isPalmed || cupId == pickedCup)
                    val liftDp = if (lifted || previewLift) (-92).dp else 0.dp   // lift the CUP only
                    Box(
                        Modifier.offset { androidx.compose.ui.unit.IntOffset(
                            cupX[cupId].value.roundToInt(), cupY[cupId].value.roundToInt()) },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Ball stays on the table; lifting the cup above it reveals it.
                        val showBall = previewLift || (revealed && !isPalmed && cupId == pickedCup)
                        if (showBall) ball?.let { Image(it, null, Modifier.size(88.dp).offset(y = (-6).dp)) }
                        cup?.let {
                            Image(it, null, modifier = Modifier.size(172.dp).offset(y = liftDp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = phase == CupPhase.PICK
                                ) { pickedCup = cupId; phase = CupPhase.REVEAL })
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            when (phase) {
                CupPhase.PREVIEW -> BetButton(Currency.COOKIES, "PLACE BET") { phase = CupPhase.BETTING; shuffle() }
                CupPhase.REVEAL -> {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF14A0E0))
                            .border(2.dp, Color(0xFF0A5A80), RoundedCornerShape(8.dp))
                            .clickable {
                                if (isPalmed) {
                                    meter = 0; CurrencyStore.zero(context, Currency.COOKIES); phase = CupPhase.OVER
                                } else {
                                    meter *= 2; round++; pickedCup = -1; resetLanes(); phase = CupPhase.PREVIEW
                                }
                            }
                            .padding(horizontal = 26.dp, vertical = 13.dp)
                    ) {
                        Text(if (isPalmed) "CONTINUE" else "PLAY AGAIN", color = Color.White,
                            fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = COMIC)
                    }
                }
                else -> Spacer(Modifier.height(44.dp))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. STARS — Lucky Spinn slots (slots.svg + 4 vertical reels)
// ═════════════════════════════════════════════════════════════════════════════

private data class SlotSym(val asset: String, val grey: Boolean)

// Colourful symbols shared by all reels + one greyed (black) symbol per reel.
private val SLOT_COLOR = listOf("innergram", "spilltify", "theytube", "tuktak", "dumbazon", "halflingo")
private val SLOT_BLACK = listOf("tetris", "camera", "phone", "message")   // one per reel, all different

@Composable
fun StarSlotsGame(onDone: () -> Unit) {
    val context = LocalContext.current
    val start = remember { CurrencyStore.balance(context, Currency.STARS) }
    if (start <= 0) { GameOverBlogPage(Currency.STARS, onDone); return }

    val scope = rememberCoroutineScope()
    // Each reel's strip: 6 colourful + its own greyed icon (index 6 = "black").
    val reels = remember {
        List(4) { r ->
            SLOT_COLOR.map { SlotSym("file:///android_asset/$PHONE_FRESH/$it.svg", false) } +
                SlotSym("file:///android_asset/$PHONE_USED/${SLOT_BLACK[r]}.svg", true)
        }
    }
    val stripSize = SLOT_COLOR.size + 1
    val blackIdx = stripSize - 1
    // Scripted targets per spin (index into each reel's strip): win, win, near, ALL-BLACK.
    val outcomes = remember {
        listOf(
            listOf(0, 0, 0, 0), listOf(3, 3, 3, 3), listOf(1, 1, 1, 4),
            listOf(blackIdx, blackIdx, blackIdx, blackIdx),
        )
    }
    val scrolls = remember { List(4) { Animatable(0f) } }
    var spinIdx by remember { mutableIntStateOf(0) }
    var spinning by remember { mutableStateOf(false) }
    var meter by remember { mutableIntStateOf(start) }
    var over by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    fun spin() {
        if (spinning || over) return
        spinning = true; result = null
        scope.launch {
            val targets = outcomes[spinIdx]
            val jobs = scrolls.mapIndexed { i, anim ->
                launch {
                    val turns = 4 + i
                    val base = (floor(anim.value / stripSize).toInt() + turns) * stripSize
                    anim.animateTo((base + targets[i]).toFloat(), tween(900 + i * 320, easing = FastOutSlowInEasing))
                }
            }
            jobs.joinAll()
            spinning = false
            val allBlack = targets.all { it == blackIdx }
            if (allBlack) {
                result = "ALL BLACK — YOU LOSE"
                delay(600); meter = 0; CurrencyStore.zero(context, Currency.STARS); over = true
            } else {
                val win = targets.distinct().size == 1
                if (win) { meter *= 2; result = "WINNER!" } else result = "so close…"
                if (spinIdx < outcomes.size - 1) spinIdx++
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B1020)), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        CurrencyMeter(Currency.STARS, meter)
        // Fixed-height result strip so appearing/disappearing text never shifts the machine.
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            result?.let {
                Text(it, color = if (it == "WINNER!") Color(0xFF4AE07A) else Color(0xFFFFC107),
                    fontSize = 15.sp, fontFamily = COMIC, fontWeight = FontWeight.Bold)
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val maxWpx = with(density) { maxWidth.toPx() }
            val maxHpx = with(density) { maxHeight.toPx() }
            val aspect = 1500f / 2000f
            var bw = maxWpx; var bh = bw / aspect
            if (bh > maxHpx) { bh = maxHpx; bw = bh * aspect }
            val bwDp = with(density) { bw.toDp() }
            val bhDp = with(density) { bh.toDp() }

            // Layers: background → spinning reels → the provided shape (slots.svg,
            // whose cut-out reveals the reels) → RGB strips → SPIN button.
            val cutX = bwDp * 0.114f; val cutY = bhDp * 0.291f
            val cutW = bwDp * (0.886f - 0.114f); val cutH = bhDp * (0.709f - 0.291f)
            Box(Modifier.size(bwDp, bhDp)) {
                // 1) Reels, behind, sized a touch LARGER than the cut-out so they
                //    fully fill the hole (any overflow is hidden under the yellow).
                val pad = bwDp * 0.012f
                Box(Modifier.offset(x = cutX - pad, y = cutY - pad).size(cutW + pad * 2, cutH + pad * 2)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(cutW * 0.012f)) {
                        for (i in 0..3) {
                            SlotReel(reels[i], scrolls[i].value, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }

                // 2) Cut-out RGB, drawn BEFORE the shape and hugging the hole edge,
                //    so the shape's corner icons sit on top of it (RGB goes under them).
                val cin = cutW * 0.01f
                Box(Modifier.offset(x = cutX + cin, y = cutY + cin).size(cutW - cin * 2, cutH - cin * 2)) {
                    RgbBorder(Modifier.matchParentSize(), dotSpacingDp = 7.dp, dotRadiusDp = 2.dp, cornerRadiusDp = bwDp * 0.02f)
                }

                // 3) The shape I provided, on top of the reels + cut-out RGB.
                AsyncImage(model = "file:///android_asset/slots.svg", contentDescription = null,
                    modifier = Modifier.fillMaxSize())

                // 4) Outer + title RGB on top — rounded to match the yellow corners.
                RgbBorder(Modifier.fillMaxSize().padding(bwDp * 0.02f),
                    dotSpacingDp = 9.dp, dotRadiusDp = 2.2.dp, cornerRadiusDp = bwDp * 0.22f)
                Box(Modifier.offset(x = bwDp * 0.20f, y = bhDp * 0.045f).size(bwDp * 0.60f, bhDp * 0.185f)) {
                    RgbBorder(Modifier.matchParentSize(), dotSpacingDp = 7.dp, dotRadiusDp = 2.dp, cornerRadiusDp = bhDp * 0.05f)
                }

                // 4) SPIN button — inside the yellow, low centre.
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.padding(bottom = bhDp * 0.075f)) {
                        BetButton(Currency.STARS, if (spinning) "…" else "SPIN", enabled = !spinning && !over) { spin() }
                    }
                }
            }
        }

        if (over) { GameOverBlog(Currency.STARS, onDone); Spacer(Modifier.height(12.dp)) }
    }
}

/** One vertical slot reel showing three symbols (neighbours visible), scrolling. */
@Composable
private fun SlotReel(strip: List<SlotSym>, scroll: Float, modifier: Modifier) {
    val density = LocalDensity.current
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(Color.White)
            .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val itemPx = with(density) { maxHeight.toPx() } / 3f
            val itemDp = with(density) { itemPx.toDp() }
            val center = scroll.roundToInt()
            val frac = scroll - center
            for (d in -1..1) {
                val idx = ((center + d) % strip.size + strip.size) % strip.size
                val s = strip[idx]
                val yOff = with(density) { ((d - frac) * itemPx).toDp() }
                AsyncImage(
                    model = s.asset, contentDescription = null,
                    modifier = Modifier.size(itemDp * 0.86f).offset(y = yOff)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (s.grey) Color(0xFF111111) else Color.White)
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. GIFTCARDS — mystery boxes
// ═════════════════════════════════════════════════════════════════════════════

// Prizes are all worthless junk — NEVER any currency or money.
private val BOX_PRIZES = listOf(
    "models/lampon.obj" to "models/lampon.mtl",
    "$CASINO_MODELS/bomb.obj" to "$CASINO_MODELS/bomb.mtl",
    "models/door.obj" to "models/door.mtl",
    "$CASINO_MODELS/cup.obj" to "$CASINO_MODELS/cup.mtl",
    "$CASINO_MODELS/button.obj" to "$CASINO_MODELS/button.mtl",
    "$CASINO_MODELS/table.obj" to "$CASINO_MODELS/table.mtl",
)

@Composable
fun GiftcardBoxesGame(onDone: () -> Unit) {
    val context = LocalContext.current
    val start = remember { CurrencyStore.balance(context, Currency.GIFTCARDS) }
    if (start <= 0) { GameOverBlogPage(Currency.GIFTCARDS, onDone); return }

    val costPerBox = remember { (start / 9).coerceAtLeast(1) }
    var meter by remember { mutableIntStateOf(start) }
    val boxes = remember { mutableStateListOf(*Array(9) { -2 }) }   // -2 closed, -1 empty, >=0 prize
    val closedBox = rememberModelBitmap("$CASINO_MODELS/boxclosed.obj", "$CASINO_MODELS/boxclosed.mtl", sizePx = 200, tilt = -18f)
    val openBox = rememberModelBitmap("$CASINO_MODELS/boxopen.obj", "$CASINO_MODELS/boxopen.mtl", sizePx = 200, tilt = -18f)
    val giftIcon = rememberCurrencyIcon(Currency.GIFTCARDS)
    val outOfCards = meter <= 0

    Box(Modifier.fillMaxSize()) {
        // Stretched "Kytka" background.
        Image(painterResource(R.drawable.kytka), null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.30f },
            contentScale = ContentScale.FillBounds)

        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Mystery Boxes", color = Color(0xFF0000CC), fontSize = 24.sp, fontWeight = FontWeight.Black,
                fontFamily = COMIC)
            Spacer(Modifier.height(6.dp))
            CurrencyMeter(Currency.GIFTCARDS, meter)
            Spacer(Modifier.height(10.dp))

            for (row in 0..2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0..2) {
                        val idx = row * 3 + col
                        val state = boxes[idx]
                        val opened = state != -2
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .clickable(enabled = !opened && !outOfCards) {
                                    meter = (meter - costPerBox).coerceAtLeast(0)
                                    // ~⅓ empty; the rest is junk. Never any gift cards/money.
                                    boxes[idx] = if ((0..2).random() == 0) -1 else BOX_PRIZES.indices.random()
                                    val openedCount = boxes.count { it != -2 }
                                    if (openedCount >= 9) meter = 0
                                    if (meter <= 0) CurrencyStore.zero(context, Currency.GIFTCARDS)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                !opened -> {
                                    closedBox?.let { Image(it, null, Modifier.fillMaxSize()) }
                                    giftIcon?.let { Image(it, null, Modifier.size(34.dp)) }
                                }
                                state == -1 -> {
                                    openBox?.let { Image(it, null, Modifier.fillMaxSize()) }
                                    Text("empty", color = Color(0xFF777777), fontSize = 11.sp, fontFamily = COMIC,
                                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                                }
                                else -> {
                                    val (obj, mtl) = BOX_PRIZES[state]
                                    val prize = rememberModelBitmap(obj, mtl, sizePx = 140, colorGamma = 0.6f, fitSpan = 1.45f)
                                    openBox?.let { Image(it, null, Modifier.fillMaxSize().align(Alignment.BottomCenter)) }
                                    prize?.let { Image(it, null, Modifier.fillMaxSize(0.6f)) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.weight(1f))
            if (outOfCards) GameOverBlog(Currency.GIFTCARDS, onDone, extra = "but now you have all this trash!")
            else Text("tap a box to open it!", color = Color(0xFF333333), fontSize = 14.sp, fontFamily = COMIC)
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. KEYS — speculative housing day-trading
// ═════════════════════════════════════════════════════════════════════════════

private data class Candle(val open: Float, val close: Float, val high: Float, val low: Float)

/** Month/year label for candle index [i] (weekly candles from Jan 2016). */
private fun candleDate(i: Int): String {
    val months = listOf("Jan", "Mar", "May", "Jul", "Sep", "Nov")
    val year = 2016 + i / 26
    return "${months[(i / 4) % 6]} '${year % 100}"
}

@Composable
fun KeysHousingGame(onDone: () -> Unit) {
    val context = LocalContext.current
    val start = remember { CurrencyStore.balance(context, Currency.KEYS) }
    if (start <= 0) { GameOverBlogPage(Currency.KEYS, onDone); return }

    // A long PRE-EXISTING history of the market rising for years (already on screen
    // when you enter). Live candles are appended after; the crash only starts once
    // the player invests (buys).
    val candles = remember {
        val l = mutableStateListOf<Candle>()
        var p = 3.4f
        for (i in 0 until 70) {
            val open = p
            p = (open * (1f + ((0..7).random() - 2) / 100f)).coerceIn(2f, 55f)   // mostly up, wobbly
            l.add(Candle(open, p, maxOf(open, p) * 1.03f, minOf(open, p) * 0.97f))
        }
        l
    }
    var cash by remember { mutableIntStateOf(start) }
    var shares by remember { mutableStateOf(0f) }
    var price by remember { mutableStateOf(candles.last().close) }
    var invested by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var crashSteps by remember { mutableIntStateOf(0) }
    var liveCount by remember { mutableIntStateOf(0) }
    val meter = (cash + shares * price).roundToInt()
    val scrollState = rememberScrollState()

    // Crash choreography: big drop, small bounce, then all the way down.
    val crashMults = remember { listOf(0.45f, 1.2f, 0.6f, 0.5f, 0.4f, 0.28f, 0.18f) }
    LaunchedEffect(Unit) {
        while (!over) {
            delay(1500)    // same pace before and after the bet
            val open = price
            // Auto-bust if they somehow never invest, so the game can still finish.
            val crashing = invested || liveCount > 26
            price = if (!crashing) {
                (open * (1f + ((0..6).random() - 3) / 100f)).coerceIn(2f, 70f)      // fluctuate ±3%
            } else {
                val m = crashMults.getOrElse(crashSteps) { 0.2f }
                crashSteps++
                (open * m).coerceAtLeast(0.05f)
            }
            candles.add(Candle(open, price, maxOf(open, price) * 1.02f, minOf(open, price) * 0.98f))
            if (candles.size > 260) candles.removeAt(0)
            liveCount++
            if (crashing && crashSteps >= crashMults.size) {
                cash = 0; shares = 0f; CurrencyStore.zero(context, Currency.KEYS); over = true
            }
        }
    }
    // Keep the newest candles in view.
    LaunchedEffect(candles.size) { scrollState.scrollTo(scrollState.maxValue) }

    MovingGradient(listOf(Color(0xFFEAF7EA), Color(0xFFDCEBFF), Color(0xFFF3E9FF)), Modifier.fillMaxSize())
    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PropTrade Pro", color = Color(0xFF0000CC), fontSize = 24.sp, fontWeight = FontWeight.Black,
            fontFamily = COMIC)
        Text("HOUSING · REAL-TIME  ·  £${"%.1f".format(price)}/share", color = Color(0xFF008000),
            fontSize = 12.sp, fontFamily = COMIC)
        Spacer(Modifier.height(6.dp))
        CurrencyMeter(Currency.KEYS, meter)
        Spacer(Modifier.height(12.dp))

        // ── Scrollable candlestick chart with price + date axes ──────────────
        val hi = candles.maxOf { it.high }.coerceAtLeast(1f)
        val lo = candles.minOf { it.low }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0E1428)).border(1.dp, Color(0xFFB0C0E0), RoundedCornerShape(6.dp))) {
            val candleW = 16.dp
            val density = LocalDensity.current
            Row(Modifier.fillMaxSize().horizontalScroll(scrollState).padding(vertical = 8.dp)) {
                Canvas(Modifier.width(candleW * candles.size).fillMaxHeight().padding(start = 6.dp, end = 44.dp)) {
                    val w = size.width; val h = size.height * 0.9f
                    // Headroom above/below so the market "zooms out" as it climbs.
                    val span = (hi - lo).coerceAtLeast(0.01f)
                    val top = hi + span * 0.15f
                    val bot = (lo - span * 0.05f).coerceAtLeast(0f)
                    val range = (top - bot).coerceAtLeast(0.01f)
                    fun yOf(v: Float) = h * (1f - (v - bot) / range)
                    val slot = w / candles.size
                    val grid = Color(0x18FFFFFF)
                    // Horizontal gridlines.
                    for (k in 0..4) { val gy = h * k / 4f; drawLine(grid, Offset(0f, gy), Offset(w, gy), 1f) }
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#8FA6C8")
                        textSize = with(density) { 9.sp.toPx() }
                    }
                    candles.forEachIndexed { i, c ->
                        val cx = slot * (i + 0.5f)
                        if (i % 12 == 0) {
                            drawLine(grid, Offset(cx, 0f), Offset(cx, h), 1f)   // vertical gridline
                            drawContext.canvas.nativeCanvas.drawText(candleDate(i), cx - 12f, h + 14f, paint)
                        }
                        val up = c.close >= c.open
                        val col = if (up) Color(0xFF26C281) else Color(0xFFE04343)
                        drawLine(col, Offset(cx, yOf(c.high)), Offset(cx, yOf(c.low)), strokeWidth = 2f)
                        val tp = yOf(maxOf(c.open, c.close)); val bt = yOf(minOf(c.open, c.close))
                        drawRect(col, topLeft = Offset(cx - slot * 0.3f, tp),
                            size = androidx.compose.ui.geometry.Size(slot * 0.6f, (bt - tp).coerceAtLeast(2f)))
                    }
                }
            }
            // Fixed price axis on the right.
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text("£${"%.0f".format(hi)}", color = Color(0xFF8FA6C8), fontSize = 9.sp)
                Text("£${"%.0f".format((hi + lo) / 2f)}", color = Color(0xFF8FA6C8), fontSize = 9.sp)
                Text("£${"%.0f".format(lo)}", color = Color(0xFF8FA6C8), fontSize = 9.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        if (over) {
            GameOverBlog(Currency.KEYS, onDone, extra = "Hmmm. Have you tried crypto?")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Buy = invest all cash into shares (this is what triggers the crash).
                BetButton(Currency.KEYS, "BUY", enabled = cash > 0) {
                    shares += cash / price; cash = 0; invested = true
                }
                BetButton(Currency.KEYS, "SELL", enabled = shares > 0f) {
                    cash += (shares * price).roundToInt(); shares = 0f
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 5. COINS — the lottery finale
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CoinsLotteryFinale(onDone: () -> Unit) {
    val context = LocalContext.current
    val ticket = remember {
        CurrencyStore.lotteryNumbers(context).ifEmpty { List(5) { (0..100).random() } }
            .also { if (!CurrencyStore.lotteryStaked(context)) CurrencyStore.stakeCoins(context, it) }
    }
    val winning = remember {
        generateSequence { (0..100).random() }.filter { it !in ticket }.distinct().take(5).toList()
    }
    var revealed by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    val coinsAtStake = remember { CurrencyStore.balance(context, Currency.COINS) }
    var meter by remember { mutableIntStateOf(coinsAtStake) }

    LaunchedEffect(Unit) {
        delay(600)
        for (i in 1..winning.size) { revealed = i; delay(650) }
        delay(500)
        meter = 0; CurrencyStore.settleLottery(context); over = true
    }

    MovingGradient(listOf(Color(0xFFFFF3D6), Color(0xFFFFE0F0), Color(0xFFE0ECFF)), Modifier.fillMaxSize())
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("★ MEGA Number Draw ★", color = Color(0xFF0000CC), fontSize = 22.sp, fontWeight = FontWeight.Black,
            fontFamily = COMIC)
        Spacer(Modifier.height(8.dp))
        CurrencyMeter(Currency.COINS, meter)
        Spacer(Modifier.height(18.dp))

        Text("TODAY'S WINNING NUMBERS", color = Color(0xFF444444), fontSize = 13.sp, fontFamily = COMIC)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in winning.indices) NumberBall(if (i < revealed) "${winning[i]}" else "?", Color(0xFFFFC107))
        }
        Spacer(Modifier.height(24.dp))
        Text("YOUR TICKET", color = Color(0xFF444444), fontSize = 13.sp, fontFamily = COMIC)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in ticket) NumberBall("$n", Color(0xFF3A6FFF))
        }

        Spacer(Modifier.weight(1f))
        if (over) GameOverBlog(Currency.COINS, onDone)
        else Text("Drawing…", color = Color(0xFFCC8800), fontSize = 16.sp, fontFamily = COMIC, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NumberBall(label: String, color: Color) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(color).border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = COMIC)
    }
}

/** Full page shown when a currency is already at zero on entry — nothing to gamble. */
@Composable
private fun GameOverBlogPage(currency: Currency, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        GameOverBlog(currency, onDone)
    }
}
