package com.fictioncutshort.justacalculator.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fictioncutshort.justacalculator.ui.components.DonationLandingPage
import kotlinx.coroutines.delay

// Visual style + behavior tags ────────────────────────────────────────────────
private const val ICON_DIR_FRESH = "phonescreen/phonedetour"
private const val ICON_DIR_USED  = "phonescreen/tankgame"

// The giftcard + degrimer icons always render from the colour ("fresh") set —
// they have no black-and-white variant and must look the same everywhere.
private const val GIFTCARD_ICON = "file:///android_asset/$ICON_DIR_FRESH/giftcard.svg"
private const val DEGRIMER_ICON = "file:///android_asset/$ICON_DIR_FRESH/degrimer.svg"

// SharedPreferences (shared with the city) — giftcards persist across entries.
private const val PREFS_NAME = "calc_city"
private const val KEY_GIFTCARDS = "b3_giftcards"

// When true, scrollable lists briefly freeze (the phone's "lag"). Toggled by
// TankGame; read by each LazyColumn via `userScrollEnabled = !LocalScrollLag.current`.
private val LocalScrollLag = compositionLocalOf { false }

private data class PhoneApp(val name: String, val label: String)

// Grid order matches the original home screen, minus the apps the user asked
// to drop (gemini, gpt, pictures, spotify, uber).
private val GRID_APPS = listOf(
    PhoneApp("dumbazon",  "Dumbazon"),
    PhoneApp("halflingo",     "Halflingo"),
    PhoneApp("tetris",  "Tetris"),
    PhoneApp("innergram",    "Innergram"),
    PhoneApp("tuktak",     "TukTak"),
    PhoneApp("discocord", "Discocord"),
    PhoneApp("mute",    "Mute"),
)

// Dumbazon stays re-openable (it's the shop) — its tile is never disabled, even
// though opening it still counts toward "all apps visited".
private const val STICKY_OPEN_APP = "dumbazon"

private val DOCK_APPS = listOf(
    PhoneApp("phone",     "Phone"),
    PhoneApp("message",   "Messages"),
    PhoneApp("mail",      "Mail"),
    PhoneApp("phonebook", "Contacts"),
)

private val ALL_APP_NAMES = (GRID_APPS + DOCK_APPS).map { it.name }.toSet()

/**
 * Building 3 — phone-app simulation minigame.
 *
 * The home grid mirrors the Phase-1 phone overlay, but every app misbehaves on
 * tap. Each visited app has its icon swapped from the "phonedetour" set to the
 * "tankgame" set. Once all apps have been visited, a "Just A Calculator" tile
 * is installed on the home screen — tapping it ends the level (banner +
 * onComplete which the city view wires to a bridge piece and aerial flyover).
 */
@Composable
fun TankGame(onComplete: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // Persistent giftcards balance (the building's economy).
    var giftcards by remember { mutableIntStateOf(prefs.getInt(KEY_GIFTCARDS, 0)) }
    fun setGiftcards(n: Int) {
        giftcards = n.coerceAtLeast(0)
        prefs.edit().putInt(KEY_GIFTCARDS, giftcards).apply()
    }
    fun addGiftcards(n: Int) = setGiftcards(giftcards + n)

    var activeApp by remember { mutableStateOf<String?>(null) }
    var visited by remember { mutableStateOf(setOf<String>()) }
    var donationOpen by remember { mutableStateOf(false) }
    // Apps no longer auto-close. Each one stays open until the user dismisses it
    // with its own close (×) button, which marks it visited (see onClose below).

    val allVisited = visited.containsAll(ALL_APP_NAMES)

    // ── Notifications (non-dismissable; cleared only by being actioned) ──────
    var tetrisNotif by remember { mutableStateOf(false) }
    var tetrisNotifDone by remember { mutableStateOf(false) }
    var amaxNotif by remember { mutableStateOf(false) }
    var amaxDone by remember { mutableStateOf(false) }
    var sawMessages by remember { mutableStateOf(false) }
    // Tetris "play for free" pitch ~15s after the phone opens.
    LaunchedEffect(Unit) { delay(15_000L); if (!tetrisNotifDone) tetrisNotif = true }
    // Amax scam text right after the user closes the Messages app.
    LaunchedEffect(sawMessages) {
        if (sawMessages && !amaxDone) { delay(1_200L); amaxNotif = true }
    }

    // "Virus" link overlay (colour flicker + random vibration). Whatever app was
    // open stays open underneath, so an email-triggered one returns to the inbox.
    var virusActive by remember { mutableStateOf(false) }

    // End sequence: Dumbazon "closed for today" → degrimer download → "clean"
    // (costs half the giftcards) → onComplete (aerial). 0 none·1·2·3 done.
    var endPhase by remember { mutableIntStateOf(0) }
    LaunchedEffect(endPhase) {
        when (endPhase) {
            1 -> { delay(2_000L); endPhase = 2 }
            2 -> { delay(2_800L); setGiftcards(giftcards / 2); endPhase = 3 }
            3 -> { delay(900L); onComplete() }
        }
    }

    // System lag → laggy scrolling: lists briefly freeze every couple of seconds
    // (Tetris adds its own random freeze/speed-ups). Exposed via LocalScrollLag.
    var scrollLag by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((1500..3000).random().toLong())
            scrollLag = true
            delay((120..260).random().toLong())
            scrollLag = false
        }
    }

    // One notification at a time (Tetris first), only over the home screen. While
    // shown it blocks the home so no other app can be opened.
    val pendingNotif: String? = when {
        activeApp != null -> null
        tetrisNotif -> "tetris"
        amaxNotif -> "amax"
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101418))) {
        AsyncImage(
            model = "file:///android_asset/$ICON_DIR_FRESH/background.svg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        CompositionLocalProvider(LocalScrollLag provides scrollLag) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Spacer(modifier = Modifier.height(12.dp))
                GiftcardCounter(giftcards, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(GRID_APPS, key = { it.name }) { app ->
                        // Dumbazon is sticky: never disabled and keeps its colour
                        // icon until the very end, but still counts as visited.
                        val sticky = app.name == STICKY_OPEN_APP
                        val used = app.name in visited && !sticky
                        val dir = if (app.name in visited && !sticky) ICON_DIR_USED else ICON_DIR_FRESH
                        IconTile(
                            label = app.label,
                            assetPath = "file:///android_asset/$dir/${app.name}.svg",
                            enabled = !used,
                            onClick = { activeApp = app.name }
                        )
                    }
                }

                DockBar(visited = visited, onTap = { activeApp = it })
            }

            // Active app surface.
            val current = activeApp
            if (current != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                ) {
                    AppShell(
                        name = current,
                        giftcards = giftcards,
                        addGiftcards = { addGiftcards(it) },
                        openAd = { donationOpen = true },
                        triggerVirus = { virusActive = true },
                        allVisited = allVisited,
                        onCloseForToday = { endPhase = 1 },
                        onClose = {
                            visited = visited + current
                            if (current == "message") sawMessages = true
                            activeApp = null
                        }
                    )
                }
            }
        }

        // Blocking notification — dims + absorbs taps on the home screen so no
        // app can be opened until the notification is actioned. Hangs at the top.
        if (pendingNotif != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            )
            Box(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp)
                    .align(Alignment.TopCenter)
            ) {
                when (pendingNotif) {
                    "tetris" -> NotifBanner(
                        icon = "file:///android_asset/$ICON_DIR_FRESH/tetris.svg",
                        title = "Tetris",
                        body = "Win Dumbazon giftcards, play for free!",
                        onClick = { tetrisNotif = false; tetrisNotifDone = true; activeApp = "tetris" }
                    )
                    "amax" -> NotifBanner(
                        icon = "file:///android_asset/$ICON_DIR_FRESH/message.svg",
                        title = "Amax",
                        body = "Free giftcards here: hxxp://amax-free-cards.win/claim",
                        onClick = { amaxNotif = false; amaxDone = true; virusActive = true }
                    )
                }
            }
        }

        // Donation landing page — same surface the in-app banners open in Phase-1.
        if (donationOpen) {
            DonationLandingPage(
                onDismiss = { donationOpen = false },
                onDonate = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.fictioncutshort.justacalculator")
                    )
                    try { context.startActivity(intent) } catch (_: Throwable) {}
                }
            )
        }

        // "Virus" link — flickers, vibrates, then drops the user back where they
        // were, 20% of their giftcards lighter.
        if (virusActive) {
            VirusOverlay(onDone = {
                setGiftcards((giftcards * 0.8f).toInt())
                virusActive = false
            })
        }

        // End sequence (closed → degrimer → clean → aerial).
        if (endPhase >= 1) {
            EndSequenceOverlay(phase = endPhase)
        }
    }
}

@Composable
private fun IconTile(
    label: String,
    assetPath: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(vertical = 4.dp)
    ) {
        AsyncImage(
            model = assetPath,
            contentDescription = label,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DockBar(visited: Set<String>, onTap: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(78.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x55101820)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (app in DOCK_APPS) {
            val used = app.name in visited
            val dir = if (used) ICON_DIR_USED else ICON_DIR_FRESH
            AsyncImage(
                model = "file:///android_asset/$dir/${app.name}.svg",
                contentDescription = app.label,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        enabled = !used,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onTap(app.name) }
            )
        }
    }
}

// Dispatcher for the active-app surface. Each app composable is responsible for
// its own internal popups; it can call openAd() to surface the donation page.
// A floating close (X) button is overlaid on top of every app's content — it is
// the only way to close an app (there is no auto-close), and it marks it visited.
@Composable
private fun AppShell(
    name: String,
    giftcards: Int,
    addGiftcards: (Int) -> Unit,
    openAd: () -> Unit,
    triggerVirus: () -> Unit,
    allVisited: Boolean,
    onCloseForToday: () -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF2F4F7))) {
        when (name) {
            "innergram"   -> AppSocialInstagram()
            "tuktak"      -> AppSocialTikTok()
            "dumbazon"    -> AppAmazon(
                giftcards = giftcards,
                allVisited = allVisited,
                onCloseForToday = onCloseForToday,
            )
            "discocord"   -> AppDiscord(onClose = onClose)
            "halflingo"   -> AppDuolingo(addGiftcards = addGiftcards, onClose = onClose)
            "mail"        -> AppMail(addGiftcards = addGiftcards, triggerVirus = triggerVirus, openAd = openAd)
            "message"     -> AppMessages()
            "phone"       -> AppPhone()
            "phonebook"   -> AppContacts(addGiftcards = addGiftcards)
            "mute"        -> AppTemu()
            "tetris"      -> AppTetris(addGiftcards = addGiftcards, giftcards = giftcards, onClose = onClose)
            else          -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(name, color = Color.Black)
            }
        }

        // User-driven close — the only way out of an app.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 10.dp, top = 6.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "×",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Per-app stubs — content built out incrementally below ────────────────────

// The made-up users shared across Innergram and TukTak.
private val SOCIAL_NAMES = listOf("BestUser", "Innergram_Official", "MarytheInfluencer", "SexyMan")

@Composable
private fun AppSocialInstagram() {
    val context = LocalContext.current
    // 2 of the player's Building-7 selfies (newest first), shown as real posts.
    val myPics = remember { loadVanityCapturePaths(context, 2) }
    val placeholders = remember { (0 until 10).toList() }

    Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
            Text("Innergram", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = !LocalScrollLag.current) {
            items(myPics.size) { idx ->
                InstaPost(
                    name = SOCIAL_NAMES[idx % SOCIAL_NAMES.size],
                    imageFile = java.io.File(myPics[idx]),
                    caption = "feeling cute today"
                )
            }
            items(placeholders.size) { idx ->
                InstaPost(
                    name = SOCIAL_NAMES[(idx + myPics.size) % SOCIAL_NAMES.size],
                    imageFile = null,
                    caption = "caption placeholder"
                )
            }
        }
    }
}

@Composable
private fun InstaPost(name: String, imageFile: java.io.File?, caption: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFFFC1E3)))
            Spacer(modifier = Modifier.width(10.dp))
            Text(name, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFFE3E0DA))) {
            if (imageFile != null) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("♡", color = Color.Black, fontSize = 22.sp)
            Text("💬", color = Color.Black, fontSize = 18.sp)
            Text("↗", color = Color.Black, fontSize = 20.sp)
        }
        Text("$name · $caption", color = Color.Black, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        Spacer(modifier = Modifier.height(12.dp))
    }
}
@Composable
private fun AppSocialTikTok() {
    // Vertical full-bleed cards, one per post, scrollable.
    val posts = remember { (0 until 12).map { i -> i % 4 } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        userScrollEnabled = !LocalScrollLag.current
    ) {
        items(posts.size) { idx ->
            val n = posts[idx]
            val bg = listOf(
                Color(0xFF1B1A38), Color(0xFF3A1B30),
                Color(0xFF1B2E2A), Color(0xFF2A2A1B)
            )[n]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(540.dp)
                    .background(bg)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text("@${SOCIAL_NAMES[n % SOCIAL_NAMES.size]}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("caption placeholder #${n + 1}", color = Color.White, fontSize = 13.sp)
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("♡", color = Color.White, fontSize = 26.sp)
                    Text("💬", color = Color.White, fontSize = 22.sp)
                    Text("↗", color = Color.White, fontSize = 24.sp)
                }
            }
        }
    }
}
// Dumbazon product. `margin` is how many giftcards the price always sits ABOVE
// the player's balance — different per phone, so each stays just out of reach.
private data class DumbProduct(
    val title: String, val desc: String, val image: String, val swatch: Color, val margin: Int,
)
private const val PHONE_DIR = "file:///android_asset/phonescreen/phonedetour"
private val DUMB_PRODUCTS = listOf(
    DumbProduct("iQoOo 12", "NEW best fast Android phone, BEST camera 18GB RAM, microSD, headphone jack, charger included.", "$PHONE_DIR/phone1.svg", Color(0xFF8C9AA6), 9),
    DumbProduct("UltraPhone 20 pro", "Flagship phone, best camera, fastest Android 15, 23GB ROM, free screen protector, Spotify, YouTube.", "$PHONE_DIR/phone2.svg", Color(0xFFB7A38C), 29),
    DumbProduct("PhoneMaster 4 Lite", "Ultra fast Android phone iOS, iPhone, 4k screen camera, 56GB RAM, 95Hz display.", "$PHONE_DIR/phone3.svg", Color(0xFF6E7A8A), 99),
    DumbProduct("TelMuch 55", "Best new device smartwatch flushable camera, 1080pppp display, bright screen, IP69 nice. ", "$PHONE_DIR/phone4.svg", Color(0xFFAEC7B5), 249),
)

@Composable
private fun AppAmazon(giftcards: Int, allVisited: Boolean, onCloseForToday: () -> Unit) {
    var openProduct by remember { mutableStateOf<Int?>(null) }
    var payMsg by remember { mutableStateOf<String?>(null) }

    // Once every app has been opened, simply RE-OPENING Dumbazon trips the end
    // sequence ("closed for today" → degrimer), no purchase tap required.
    LaunchedEffect(allVisited) { if (allVisited) onCloseForToday() }

    fun priceOf(p: DumbProduct) = giftcards + p.margin   // always just out of reach

    fun onPay(p: DumbProduct) {
        if (allVisited) onCloseForToday()
        else payMsg = "You need ${p.margin} more giftcards to check out."
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF131921))
                .padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("dumbazon", color = Color(0xFFFF9900), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = GIFTCARD_ICON, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("$giftcards", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        val op = openProduct
        if (op == null) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                // Big featured banner (placeholder image).
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 8f)
                        .clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFE2B0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Upgrade your life.\nPay with giftcards.", color = Color(0xFF7A4F00),
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))
                // 2×2 product grid.
                for (pair in DUMB_PRODUCTS.indices.chunked(2)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (i in pair) {
                            val p = DUMB_PRODUCTS[i]
                            Column(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF6F6F6))
                                    .clickable(indication = null, interactionSource = remember(i) { MutableInteractionSource() }) { openProduct = i }
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp)).background(Color.White)
                                ) {
                                    AsyncImage(
                                        model = p.image,
                                        contentDescription = p.title,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(4.dp)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(p.title, color = Color(0xFF111111), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${priceOf(p)} giftcards", color = Color(0xFFB12704), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            val p = DUMB_PRODUCTS[op]
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text("←  Back", color = Color(0xFF131921), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { openProduct = null }
                        .padding(14.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.3f).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = p.image,
                        contentDescription = p.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(p.title, color = Color(0xFF111111), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = GIFTCARD_ICON, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${priceOf(p)} giftcards", color = Color(0xFFB12704), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(p.desc, color = Color(0xFF333333), fontSize = 14.sp)
                    Spacer(Modifier.height(22.dp))
                    // The one and only button.
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFFFFD814))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onPay(p) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Pay with giftcards", color = Color(0xFF111111), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    payMsg?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = Color(0xFFB12704), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
@Composable
private fun AppDiscord(onClose: () -> Unit) {
    // The user automatically attempts to send a file; the "bot" replies with a
    // Nitro upsell error. Loop a few exchanges then crash to home.
    data class Line(val who: String, val text: String, val isError: Boolean)
    var lines by remember { mutableStateOf(listOf<Line>()) }
    var crashed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val seq = listOf(
            Line("you", "[attaching file.zip]", false),
            Line("Clyde",  "Error — Subscribe to Discord Nitro to share this file.", true),
            Line("you", "[retrying file.zip]", false),
            Line("Clyde",  "Error — Subscribe to Discord Nitro to share this file.", true),
            Line("you", "[attaching photo.png]", false),
            Line("Clyde",  "Error — Subscribe to Discord Nitro to share this file.", true),
            Line("you", "[retrying photo.png]", false),
            Line("Clyde",  "Error — Subscribe to Discord Nitro to share this file.", true),
        )
        for (l in seq) {
            delay(1_100L)
            lines = lines + l
        }
        delay(800L)
        crashed = true
        delay(900L)
        onClose()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF36393F)).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF2F3136)).padding(start = 12.dp, top = 12.dp, end = 56.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF5865F2)))
            Spacer(modifier = Modifier.width(10.dp))
            Text("# general", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            modifier = Modifier.weight(1f).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((i, line) in lines.withIndex()) {
                val color = if (line.isError) Color(0xFFFFC0C0) else Color(0xFFDCDDDE)
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${line.who}: ",
                        color = if (line.who == "Clyde") Color(0xFF5865F2) else Color(0xFFB9BBBE),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(line.text, color = color, fontSize = 13.sp)
                }
                if (i == lines.lastIndex && line.isError) {
                    Box(
                        modifier = Modifier
                            .padding(start = 56.dp, top = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF5865F2))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Get Nitro", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (crashed) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Text("Discord has stopped.", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}
@Composable
private fun AppDuolingo(addGiftcards: (Int) -> Unit, onClose: () -> Unit) {
    // Rigged translation drill. The English sentence is "Get more giftcards." and
    // the word bank under it is Icelandic. Every CHECK is wrong and burns a heart,
    // but each attempt pays out giftcards equal to the letters assembled. The 3rd
    // wrong answer says "Buy premium for infinite lives" and auto-closes the app.
    val prompt = "Get more giftcards."
    val bank = remember {
        listOf("gjafakort", "fleiri", "fá", "ókeypis", "núna", "vinna",
            "bónus", "verðlaun", "takk", "já", "kaupa", "frítt").shuffled()
    }
    var placed by remember { mutableStateOf(listOf<String>()) }
    var lives by remember { mutableStateOf(3) }
    var attempts by remember { mutableStateOf(0) }
    var lastAward by remember { mutableStateOf(0) }
    var showWrongFlash by remember { mutableStateOf(false) }

    LaunchedEffect(showWrongFlash) {
        if (showWrongFlash) {
            // Hang the wrong-answer banner roughly twice as long as before.
            delay(3_000L)
            if (lives <= 0) onClose()        // 3rd wrong → auto-close
            else { placed = emptyList(); showWrongFlash = false }
        }
    }

    val faceBoxH = 108.dp * 1123f / 794f
    val ratio = 1123f / 794f

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFCEF)).statusBarsPadding()) {

        // Funky mascot — the city's "full" character with a smiley for a face.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 2.dp)
                .width(108.dp)
                .height(faceBoxH)
        ) {
            AsyncImage(
                model = "file:///android_asset/filters/full.svg",
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            SmileyFace(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 108.dp * ratio * 0.207f - 19.dp)
                    .size(38.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 3) {
                    Text(if (i < lives) "♥" else "♡",
                        color = if (i < lives) Color(0xFFFF4B4B) else Color(0xFFBDBDBD),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Translate this sentence", color = Color(0xFF7A7A7A), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color.White).padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(prompt, color = Color(0xFF111111), fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(22.dp))

            // Assembled answer (tap a chip to remove it).
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color.White).padding(10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for ((i, w) in placed.withIndex()) {
                        DuoChip(text = w, onClick = {
                            if (!showWrongFlash) placed = placed.toMutableList().also { it.removeAt(i) }
                        })
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // Word bank (3 rows of 4), already-placed words hidden.
            for (row in bank.chunked(4)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (w in row) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (w !in placed) {
                                DuoChip(text = w, onClick = { if (!showWrongFlash) placed = placed + w })
                            } else {
                                Spacer(Modifier.height(40.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            val checkEnabled = placed.isNotEmpty() && !showWrongFlash && lives > 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (checkEnabled) Color(0xFF58CC02) else Color(0xFFC8E6A0))
                    .clickable(
                        enabled = checkEnabled,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // Every answer is wrong — but pays out by letter count.
                        val letters = placed.sumOf { w -> w.count { it.isLetter() } }
                        lastAward = letters
                        if (letters > 0) addGiftcards(letters)
                        attempts += 1
                        lives = (lives - 1).coerceAtLeast(0)
                        showWrongFlash = true
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("CHECK", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Wrong-answer banner — snide on attempt 2, "Buy premium…" on attempt 3.
        if (showWrongFlash) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xFFFFD8D8))
                    .padding(16.dp)
            ) {
                Text(
                    when {
                        attempts >= 3 -> "Buy premium for infinite lives"
                        attempts >= 2 -> "I don't actually care what's correct. have you heard about AI?"
                        else -> "Oops! That's not right."
                    },
                    color = Color(0xFFCC2929), fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                if (lastAward > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = GIFTCARD_ICON, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+$lastAward giftcards", color = Color(0xFF1B7A3A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DuoChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE5E5E5))
            .clickable(
                indication = null,
                interactionSource = remember(text) { MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text, color = Color(0xFF222222), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
private enum class MailAction { RATE, GIFTCARDS, VIRUS }

private data class MailMsg(
    val sender: String,
    val subject: String,
    val body: String,
    val cta: String?,
    val action: MailAction = MailAction.RATE,
)

private val DOOR3_MAILS = listOf(
    MailMsg(
        sender = "eBay",
        subject = "eBay: There's still time to share your feedback!",
        body = "Your review matters\n\nYour feedback helps fellow buyers make informed decisions and strengthens the eBay community. Take a moment to share feedback on your recent purchase – it's quick, easy, and makes a big impact.",
        cta = "Rate Now",
        action = MailAction.GIFTCARDS,
    ),
    MailMsg(
        sender = "Experian",
        subject = "Experian: Update to your credit score.",
        body = "Hello, your Experian credit score has changed!\nKnowing your score can help you see where you stand with your finances.\n\nRemember, checking your score will never harm it.",
        cta = "Check score now",
        action = MailAction.VIRUS,
    ),
    MailMsg(
        sender = "Spilltify",
        subject = "Spilltify: Rihanna is coming to your town!",
        body = "Rihanna and 5+ other artists you love are heading out on tour! Don't miss their gigs near you.",
        cta = "View events",
        action = MailAction.GIFTCARDS,
    ),
    MailMsg(
        sender = "Grand Dental Clinic",
        subject = "Grand Dental Clinic hasn't seen you in a while",
        body = "This is a gentle reminder that you are now overdue for your routine dental & hygiene appointment.\n\nIt's important to maintain healthy teeth and gums and to do this we advise that you attend regular examinations with your Dentist and/or Hygienist. This will help prevent dental problems in the future and ensure you always have a great smile.\n\nTake a moment to book a dental appointment by using our online booking facility and choose the appointment that suits you.",
        cta = "Book Now",
    ),
    MailMsg(
        sender = "AliExpress",
        subject = "AliExpress: To pair with what you purchased",
        body = "Find items to match your Computer Peripherals",
        cta = "AliExpress",
        action = MailAction.VIRUS,
    ),
    MailMsg(
        sender = "Google",
        subject = "Google: New Google Account login.",
        body = "There was a new login to your google account from London at 13:00, Microsoft Edge on Mac.",
        cta = "It wasn't me",
    ),
    MailMsg(
        sender = "Virgin Atlantic",
        subject = "Virgin Atlantic: Up to 36,000 reasons to apply",
        body = "Love travel? Us too! So why not earn points for every trip you book, and even your morning coffee and weekly shop*, to spend on your next adventure.\n\nThere are now only a few days left to take out a Virgin Atlantic Reward+ Credit Card and earn up to 36,000 Virgin Points. Apply before 18th May",
        cta = "Apply",
        action = MailAction.GIFTCARDS,
    ),
    MailMsg(
        sender = "Wise",
        subject = "Wise: Ready to earn a return?",
        body = "You've been busy using your Wise account, but there's one feature you're yet to try — Interest.\n\nUse it to give your money a boost, with a variable* rate of 3.22% on GBP, 1.80% on EUR and 3.39% on USD. Any returns will be added each working day, ready to spend and send straight away.",
        cta = "Turn on Interest",
    ),
    MailMsg(
        sender = "Udemy",
        subject = "Udemy: the smartest way to get ahead",
        body = "30% off for a limited time\n\nThey turned on OOO. You upskilled. Get ahead before the September hiring rush. Terms apply.*",
        cta = "Enroll now",
    ),
    MailMsg(
        sender = "LinkedIn",
        subject = "LinkedIn: Here's your verification code 992374",
        body = "Enter the 6-digit code below to verify your identity and regain access to your LinkedIn account.\n\n992374\n\nThanks for helping us keep your account secure.\nThe LinkedIn Team\n\nWhen and where this happened:\nDate: April 4, 2026 at 3:58 PM GMT\nOperating System: Mac OS X\nBrowser: Edge",
        cta = null,
    ),
)

@Composable
private fun AppMail(addGiftcards: (Int) -> Unit, triggerVirus: () -> Unit, openAd: () -> Unit) {
    var open by remember { mutableStateOf<Int?>(null) }
    var claimMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(open) { claimMsg = null }   // clear feedback when navigating
    val o = open

    if (o == null) {
        Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFC5221F)).padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
                Text("Inbox", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = !LocalScrollLag.current) {
                items(DOOR3_MAILS.size) { idx ->
                    val m = DOOR3_MAILS[idx]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = null,
                                interactionSource = remember(idx) { MutableInteractionSource() }
                            ) { open = idx }
                            .padding(14.dp)
                    ) {
                        Text(m.sender, color = Color(0xFF111111), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(m.subject, color = Color(0xFF333333), fontSize = 13.sp, maxLines = 2)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            m.body.substringBefore('\n').take(80),
                            color = Color(0xFF888888), fontSize = 12.sp, maxLines = 1
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEAEAEA)))
                }
            }
        }
    } else {
        val m = DOOR3_MAILS[o]
        Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFC5221F)).padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "←",
                    color = Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { open = null }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Inbox", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(m.subject, color = Color(0xFF111111), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("From: ${m.sender}", color = Color(0xFF555555), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(m.body, color = Color(0xFF222222), fontSize = 14.sp)
                m.cta?.let { label ->
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFC5221F))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                when (m.action) {
                                    MailAction.RATE -> openAd()
                                    MailAction.GIFTCARDS -> {
                                        val a = (5..20).random()
                                        addGiftcards(a)
                                        claimMsg = "+$a giftcards added to your account."
                                    }
                                    // From email the virus returns the user to the
                                    // inbox (the Mail app stays open underneath).
                                    MailAction.VIRUS -> triggerVirus()
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    claimMsg?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = GIFTCARD_ICON, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(msg, color = Color(0xFF1B7A3A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
private data class TextMsg(val fromMe: Boolean, val body: String, val time: String)
private data class TextThread(
    val who: String,
    val previewSubtitle: String,
    val previewTime: String,
    val messages: List<TextMsg>,
)

private val DOOR3_THREADS: List<TextThread> = run {
    val ebayCodes = listOf(
        "26 Mar, 09:14" to "152940",
        "8 Apr, 18:32"  to "381027",
        "15 Apr, 22:11" to "742815",
        "20 Apr, 07:28" to "918302",
        "23 Apr, 13:55" to "503671",
        "1 May, 19:41"  to "227648",
        "5 May, 10:03"  to "839502",
        "8 May, 12:47"  to "461078",
        "10 May, 21:19" to "612394",
        "13 May, 08:55" to "750183",
        "17 May, 14:38" to "284601",
        "19 May, 11:12" to "967304",
        "22 May, 16:30" to "319287",
        "24 May, 20:48" to "405871",
        "Today, 11:09"  to "678021",
    )
    val ebayThread = TextThread(
        who = "eBay",
        previewSubtitle = "eBay: Your security code is 678021. Do not share this code.",
        previewTime = "Today, 11:09",
        messages = ebayCodes.map { (t, code) ->
            TextMsg(false, "eBay: Your security code is $code. Do not share this code.", t)
        }
    )

    val amexThread = TextThread(
        who = "Amex",
        previewSubtitle = "NEVER share this One-Time Code: 591037. Amex will never call…",
        previewTime = "23 May, 19:48",
        messages = listOf(
            TextMsg(false, "Amex Safe Key code is 482301 for £119.40 transaction attempt for card ending 4859. Never share this code.", "1 Apr, 14:22"),
            TextMsg(false, "NEVER share this One-Time Code: 731204. Amex will never call to ask for it. If released to someone or not requested, call us using Contact Us on Amex website.", "1 Apr, 14:23"),
            TextMsg(false, "Amex Safe Key code is 209875 for £18.20 transaction attempt for card ending 4859. Never share this code.", "12 Apr, 09:51"),
            TextMsg(false, "Amex Safe Key code is 957240 for £49.90 transaction attempt for card ending 4859. Never share this code.", "28 Apr, 22:14"),
            TextMsg(false, "NEVER share this One-Time Code: 372039. Amex will never call to ask for it. If released to someone or not requested, call us using Contact Us on Amex website.", "5 May, 17:09"),
            TextMsg(false, "Amex Safe Key code is 681432 for £6.75 transaction attempt for card ending 4859. Never share this code.", "18 May, 12:00"),
            TextMsg(false, "NEVER share this One-Time Code: 591037. Amex will never call to ask for it. If released to someone or not requested, call us using Contact Us on Amex website.", "23 May, 19:48"),
        )
    )

    val wixThread = TextThread(
        who = "Wix",
        previewSubtitle = "476105 is your Wix confirmation code.",
        previewTime = "23 May, 18:33",
        messages = listOf(
            TextMsg(false, "382018 is your Wix confirmation code.", "20 Mar, 10:12"),
            TextMsg(false, "519774 is your Wix confirmation code.", "4 Apr, 22:01"),
            TextMsg(false, "640281 is your Wix confirmation code.", "19 Apr, 14:30"),
            TextMsg(false, "207189 is your Wix confirmation code.", "2 May, 09:17"),
            TextMsg(false, "853624 is your Wix confirmation code.", "14 May, 11:48"),
            TextMsg(false, "476105 is your Wix confirmation code.", "23 May, 18:33"),
        )
    )

    val linkedInThread = TextThread(
        who = "LinkedIn",
        previewSubtitle = "Hi there! Thanks for being an active LinkedIn member…",
        previewTime = "Today, 09:12",
        messages = listOf(
            TextMsg(
                false,
                "Hi there! Thanks for being an active LinkedIn member. We'd like to offer you another 1-month free trial of LinkedIn Premium.",
                "Today, 09:12"
            ),
        )
    )

    val mumThread = TextThread(
        who = "Mum",
        previewSubtitle = "Are we still on for Saturday?",
        previewTime = "Today, 09:30",
        messages = listOf(
            TextMsg(false, "Can you talk today?", "Thu 21 May, 11:02"),
            TextMsg(true,  "Sorry, working.",     "Thu 21 May, 11:08"),
            TextMsg(false, "Hey, haven't heard from you for a while, hope you're doing ok.", "Sun 24 May, 19:44"),
            TextMsg(true,  "Hey, sorry, work has been a lot. Can you talk on Saturday?",     "Mon 25 May, 21:15"),
            TextMsg(true,  "Are we still on for Saturday?",                                  "Today, 09:30"),
        )
    )

    val beckyThread = TextThread(
        who = "Becky - Manager",
        previewSubtitle = "Sure, will be there",
        previewTime = "Today, 10:11",
        messages = listOf(
            TextMsg(false, "Hey, can you come in extra today? We're short.",                  "Wed 20 May, 08:12"),
            TextMsg(true,  "yes, can be there in 30 minutes.",                                "Wed 20 May, 08:14"),
            TextMsg(true,  "Hey Becky, sorry, I'm running a little late. Will be there in 10.","Wed 20 May, 09:24"),
            TextMsg(false, "Have you booked Saturday off?",                                   "Today, 10:02"),
            TextMsg(true,  "Yeah, need me to work?",                                          "Today, 10:05"),
            TextMsg(false, "Yeah, we've got the big boss coming in, can you do 05:00 – 13:00?","Today, 10:07"),
            TextMsg(true,  "Sure, will be there",                                             "Today, 10:11"),
        )
    )

    // Inbox order: most-recent first.
    listOf(ebayThread, beckyThread, mumThread, linkedInThread, amexThread, wixThread)
}

@Composable
private fun AppMessages() {
    var open by remember { mutableStateOf<Int?>(null) }
    val o = open

    if (o == null) {
        Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1976D2)).padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
                Text("Messages", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = !LocalScrollLag.current) {
                items(DOOR3_THREADS.size) { idx ->
                    val t = DOOR3_THREADS[idx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = null,
                                interactionSource = remember(idx) { MutableInteractionSource() }
                            ) { open = idx }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFBFD7EA)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(t.who, color = Color(0xFF111111), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(t.previewTime, color = Color(0xFF999999), fontSize = 11.sp)
                            }
                            Text(t.previewSubtitle, color = Color(0xFF777777), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEAEAEA)))
                }
            }
        }
    } else {
        val t = DOOR3_THREADS[o]
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7)).statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1976D2)).padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "←",
                    color = Color.White, fontSize = 22.sp,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { open = null }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(t.who, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var lastTime = ""
                for (m in t.messages) {
                    if (m.time != lastTime) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(m.time, color = Color(0xFF888888), fontSize = 10.sp)
                        }
                        lastTime = m.time
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (m.fromMe) Color(0xFF1976D2) else Color(0xFFE5E5EA))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                m.body,
                                color = if (m.fromMe) Color.White else Color(0xFF111111),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
@Composable
private fun AppPhone() {
    // Plain dial-pad. Per spec the phone app "doesn't do anything new" — just a
    // visual so the user can see they tapped it. Number keys are anchored to the
    // lower half of the screen like a real dialer.
    val rows = listOf(
        listOf("1","2","3"), listOf("4","5","6"),
        listOf("7","8","9"), listOf("*","0","#")
    )
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101418)).statusBarsPadding(),
    ) {
        // Number-entry display area at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, top = 24.dp, end = 56.dp, bottom = 24.dp)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("", color = Color.White, fontSize = 28.sp)
        }
        // Spacer pushes the keypad to the bottom half of the screen.
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            for (r in rows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (d in r) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1F2630)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(d, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
            }
        }
        // Big green call button area, anchored above bottom safe-area padding.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2EAE5C)),
                contentAlignment = Alignment.Center
            ) {
                Text("✆", color = Color.White, fontSize = 24.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
@Composable
private fun AppContacts(addGiftcards: (Int) -> Unit) {
    // Normal contacts mixed with "AD" entries. Tapping ANY ad contact (including
    // the giftcard-farm ones) pays out some giftcards and replies with a canned
    // "we'll be in touch" line.
    data class CRow(val name: String, val isAd: Boolean)
    val rows = remember {
        val plain = listOf(
            "Mum", "Dad", "Becky - Manager", "Jamie", "Sam", "Emily", "Alex",
            "Chris", "Priya", "Liam", "Ava", "Noah", "Olivia", "Ethan",
            "Sophie", "Marcus", "Hannah", "Tom (Pub Quiz)", "Aunt Karen",
            "Dr. Patel", "Landlord", "Plumber Steve"
        ).map { CRow(it, isAd = false) }
        val ads = listOf(
            "The Insurance Lawyer", "The Teeth Doctor", "The Best Car Recovery",
            "Cheapest Locksmith In Town", "Cash-For-Phones (24h)",
            "Free giftcards", "Free giftcards Daily", "Giftcards4U",
            "Giftcardland", "Giftcardnation", "TheGiftcardDealer"
        ).map { CRow(it, isAd = true) }
        val sorted = plain.sortedBy { it.name }.toMutableList()
        for ((i, ad) in ads.withIndex()) {
            val pos = ((i + 1) * (sorted.size + 1) / (ads.size + 1)).coerceIn(0, sorted.size)
            sorted.add(pos, ad)
        }
        sorted
    }
    val adMessages = remember {
        listOf(
            "Thank you for you interest, I will be in touch.",
            "Your number has been added to the database, thank you!",
            "We'll contact you shortly!",
            "We are happy to see you interested, we will contact you soon!"
        )
    }
    var dialog by remember { mutableStateOf<Pair<Int, String>?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF263238)).padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
                Text("Contacts", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = !LocalScrollLag.current) {
                items(rows) { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = r.isAd,
                                indication = null,
                                interactionSource = remember(r.name) { MutableInteractionSource() }
                            ) {
                                val award = (3..12).random()
                                addGiftcards(award)
                                dialog = award to adMessages.random()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFE0E0E0)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(r.name, color = Color(0xFF111111), fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (r.isAd) {
                            Text("AD", color = Color(0xFF888888).copy(alpha = 0.55f),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEAEAEA)))
                }
            }
        }

        dialog?.let { (award, msg) ->
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { dialog = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(28.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color.White).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = GIFTCARD_ICON, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+$award giftcards", color = Color(0xFF1B7A3A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = Color(0xFF333333), fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF263238))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { dialog = null }
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text("OK", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
@Composable
private fun AppTemu() {
    // Single full-bleed banner. The 5-second auto-close is handled by the
    // global SHORT_APPS timer in TankGame().
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFF7A1A)), contentAlignment = Alignment.Center) {
        Text(
            "PAY FOR SHIPPING ONLY \n JUST PAY\nPAY!",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}
@Composable
private fun AppTetris(addGiftcards: (Int) -> Unit, giftcards: Int, onClose: () -> Unit) {
    // Tetris on a 6×10 board where each filled cell is a phone-app icon. Some
    // cells spawn as the giftcard icon — every piece containing one pays out
    // giftcards on lock. After ~20s the icons go black-and-white; 30sfter that
    // the board accelerates beyond playability.
    val cols = 6
    val rows = 10
    val iconPool = remember {
        listOf("innergram", "tuktak", "dumbazon", "discocord", "halflingo", "mute")
    }
    // 0 = colour icons · 1 = black-and-white icons · 2 = unplayably fast.
    var phase by remember { mutableIntStateOf(0) }
    val shapes = remember {
        listOf(
            listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), // I
            listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), // O
            listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1), // T
            listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1), // J
            listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1), // L
            listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1), // S
            listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1), // Z
        )
    }

    var board by remember { mutableStateOf(Array(rows) { arrayOfNulls<String>(cols) }) }
    var pieceCells by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var pieceIcons by remember { mutableStateOf<List<String>>(emptyList()) }
    var gameOver by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }

    fun isFree(c: Int, r: Int): Boolean {
        if (c < 0 || c >= cols || r < 0 || r >= rows) return false
        return board[r][c] == null
    }
    fun fits(cells: List<Pair<Int, Int>>): Boolean = cells.all { isFree(it.first, it.second) }

    fun spawn() {
        val shape = shapes.random()
        val maxC = shape.maxOf { it.first }
        val offset = ((cols - 1 - maxC) / 2).coerceAtLeast(0)
        val cells = shape.map { (c, r) -> Pair(c + offset, r) }
        if (!fits(cells)) {
            gameOver = true
            pieceCells = emptyList()
            pieceIcons = emptyList()
            return
        }
        pieceCells = cells
        pieceIcons = cells.map { if ((0..99).random() < 18) "giftcard" else iconPool.random() }
    }

    fun lockPiece() {
        if (pieceCells.isEmpty()) return
        val nb = Array(rows) { board[it].copyOf() }
        for ((i, cell) in pieceCells.withIndex()) {
            val (c, r) = cell
            if (r in 0 until rows && c in 0 until cols) nb[r][c] = pieceIcons[i]
        }
        // Giftcard icons in this piece pay out.
        val gcCells = pieceIcons.count { it == "giftcard" }
        if (gcCells > 0) addGiftcards(gcCells * 3)
        // Clear full rows: keep the ones with any empty cell; collapse downward.
        val keep = (0 until rows).filter { r -> nb[r].any { it == null } }
        val cleared = rows - keep.size
        val cleaned = Array(rows) { arrayOfNulls<String>(cols) }
        var dst = rows - 1
        for (r in keep.reversed()) {
            cleaned[dst] = nb[r]
            dst--
        }
        board = cleaned
        if (cleared > 0) score += cleared * 100
        pieceCells = emptyList()
        pieceIcons = emptyList()
    }

    fun tryMove(dc: Int, dr: Int): Boolean {
        if (pieceCells.isEmpty()) return false
        val next = pieceCells.map { Pair(it.first + dc, it.second + dr) }
        return if (fits(next)) { pieceCells = next; true } else false
    }

    fun tryRotate() {
        if (pieceCells.isEmpty()) return
        // Rotate 90° clockwise around the piece's first cell. Cheap and works
        // adequately for these small tetrominoes inside a 6-wide board.
        val pivot = pieceCells[0]
        val next = pieceCells.map { (c, r) ->
            val dx = c - pivot.first
            val dy = r - pivot.second
            Pair(pivot.first - dy, pivot.second + dx)
        }
        if (fits(next)) pieceCells = next
    }

    fun hardDrop() {
        while (tryMove(0, 1)) { /* keep dropping */ }
        lockPiece()
        spawn()
    }

    // Game loop — gravity + spawn.
    LaunchedEffect(Unit) {
        spawn()
        while (true) {
            // "Lag": below phase 2 the gravity randomly freezes (long pause) or
            // speed-bursts; phase 2 is a flat, unplayable rush.
            val d = when {
                phase >= 2 -> 90L
                (0..99).random() < 9 -> 1700L   // random freeze
                (0..99).random() < 9 -> 130L    // random speed-up
                else -> 700L
            }
            delay(d)
            if (gameOver) break
            if (pieceCells.isEmpty()) {
                spawn()
            } else if (!tryMove(0, 1)) {
                lockPiece()
                spawn()
            }
        }
    }
    // Escalation: icons desaturate, then the board accelerates beyond control.
    LaunchedEffect(Unit) {
        delay(20_000L); phase = 1
        delay(30_000L); phase = 2
    }
    // Auto-close shortly after game over.
    LaunchedEffect(gameOver) {
        if (gameOver) { delay(1_500L); onClose() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F1729)).statusBarsPadding().padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("TETRIS", color = Color(0xFFFFCC00), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (gameOver) "GAME OVER" else "Score $score",
                    color = if (gameOver) Color(0xFFFF6464) else Color(0xFFCCD6E8),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = GIFTCARD_ICON, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("$giftcards", color = Color(0xFFFFD45A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cols.toFloat() / rows.toFloat())
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A2440))
        ) {
            // Empty-cell grid.
            Column(modifier = Modifier.fillMaxSize()) {
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        for (c in 0 until cols) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .padding(1.dp)
                                    .background(Color(0xFF243056))
                            )
                        }
                    }
                }
            }
            // Filled cells — locked board first, falling piece on top.
            Column(modifier = Modifier.fillMaxSize()) {
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        for (c in 0 until cols) {
                            val locked = board[r][c]
                            val activeIdx = pieceCells.indexOf(Pair(c, r))
                            val pieceIcon = if (activeIdx >= 0) pieceIcons.getOrNull(activeIdx) else null
                            val drawIcon = pieceIcon ?: locked
                            Box(modifier = Modifier.weight(1f).fillMaxSize().padding(2.dp)) {
                                if (drawIcon != null) {
                                    // Giftcard always stays in colour; everything
                                    // else desaturates once phase ≥ 1.
                                    val model = if (drawIcon == "giftcard") GIFTCARD_ICON
                                    else "file:///android_asset/${if (phase >= 1) ICON_DIR_USED else ICON_DIR_FRESH}/$drawIcon.svg"
                                    AsyncImage(
                                        model = model,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Game-over scrim
            if (gameOver) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("GAME OVER", color = Color(0xFFFFD8D8), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TetrisCtl("◀", enabled = !gameOver) { tryMove(-1, 0) }
            TetrisCtl("⟲", enabled = !gameOver) { tryRotate() }
            TetrisCtl("▼", enabled = !gameOver) { hardDrop() }
            TetrisCtl("▶", enabled = !gameOver) { tryMove(1, 0) }
        }
    }
}

@Composable
private fun TetrisCtl(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Color(0xFF1A2440) else Color(0xFF101820))
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember(label) { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Shared Building-3 widgets ────────────────────────────────────────────────

@Composable
private fun GiftcardCounter(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = GIFTCARD_ICON, contentDescription = "giftcards", modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text("$count", color = Color(0xFFFFD45A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text("giftcards", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
private fun NotifBanner(icon: String, title: String, body: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF2202833))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = icon, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
        Text("›", color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp)
    }
}

// A simple drawn smiley used as the "full" mascot's face in Halflingo.
@Composable
private fun SmileyFace(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        drawCircle(Color(0xFFFFD23F), radius = w / 2f, center = Offset(w / 2f, h / 2f))
        val eyeR = w * 0.07f
        drawCircle(Color(0xFF222222), eyeR, Offset(w * 0.34f, h * 0.40f))
        drawCircle(Color(0xFF222222), eyeR, Offset(w * 0.66f, h * 0.40f))
        val pad = w * 0.24f
        drawArc(
            color = Color(0xFF222222),
            startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(pad, h * 0.30f),
            size = androidx.compose.ui.geometry.Size(w - pad * 2, h * 0.45f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}

// "Virus" link: 5s of full-screen colour flicker + random-intensity vibration,
// then onDone() returns the user to whatever was underneath.
@Composable
private fun VirusOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val vib: Vibrator? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        } catch (_: Throwable) { null }
        val end = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < end) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib?.vibrate(VibrationEffect.createOneShot((40..160).random().toLong(), (40..255).random()))
                } else {
                    @Suppress("DEPRECATION") vib?.vibrate((40..160).random().toLong())
                }
            } catch (_: Throwable) {}
            delay((60..150).random().toLong())
        }
        try { vib?.cancel() } catch (_: Throwable) {}
        onDone()
    }
    // The screen just goes black — no flicker, no text — while it vibrates.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
}

// End sequence overlay: 1 = "closed for today", 2/3 = degrimer "cleaning".
@Composable
private fun EndSequenceOverlay(phase: Int) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (phase == 1) {
            Text("Sorry, we are closed for today.", color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(28.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(model = DEGRIMER_ICON, contentDescription = null, modifier = Modifier.size(96.dp))
                Spacer(Modifier.height(16.dp))
                Text("Degrimer", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Cleaning your device…\nfor half your giftcards", color = Color(0xFFB8C2CC),
                    fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
