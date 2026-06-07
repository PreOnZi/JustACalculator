package com.fictioncutshort.justacalculator.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fictioncutshort.justacalculator.R
import com.fictioncutshort.justacalculator.ui.components.DonationLandingPage
import kotlinx.coroutines.delay

// Visual style + behavior tags ────────────────────────────────────────────────
private const val ICON_DIR_FRESH = "phonescreen/phonedetour"
private const val ICON_DIR_USED  = "phonescreen/tankgame"
private const val RETURN_NAME    = "_return_calc"

// Apps that auto-close in 5s (purely-popup style); everything else closes in 10s.
private val SHORT_APPS = setOf("birds", "candy", "TEMU")

// Apps that opt OUT of the standard auto-close timer until they explicitly
// signal they're done (via the onReadyToClose callback). Used by Duolingo so
// the timer can't yank the user out before the upgrade modal has appeared.
private val GATED_CLOSE_APPS = setOf("duo")

// How long the install animation runs on the Just-A-Calculator tile that shows
// after every other app has been visited.
private const val INSTALL_DURATION_MS = 3000

private data class PhoneApp(val name: String, val label: String)

// Grid order matches the original home screen, minus the apps the user asked
// to drop (gemini, gpt, pictures, spotify, uber).
private val GRID_APPS = listOf(
    PhoneApp("airbnb",  "Airbnb"),
    PhoneApp("fbook",   "Facebook"),
    PhoneApp("calc",    "Calculator"),
    PhoneApp("gram",    "Instagram"),
    PhoneApp("TEMU",    "Temu"),
    PhoneApp("camera",  "Camera"),
    PhoneApp("amazon",  "Amazon"),
    PhoneApp("youtube", "YouTube"),
    PhoneApp("discord", "Discord"),
    PhoneApp("tok",     "TikTok"),
    PhoneApp("birds",   "Angry Birds"),
    PhoneApp("candy",   "Candy Crush"),
    PhoneApp("duo",     "Duolingo"),
    PhoneApp("tetris",  "Tetris"),
)

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
    // No runtime permissions are needed for the tank game; each app's own
    // permission gag (if any) lives inside that app, not at building entry.
    var activeApp by remember { mutableStateOf<String?>(null) }
    var visited by remember { mutableStateOf(setOf<String>()) }
    var donationOpen by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    // Gates the auto-close timer for apps in GATED_CLOSE_APPS. Resets per-app
    // on open; for ungated apps it's set true immediately; for gated apps it
    // stays false until the app calls onReadyToClose.
    var appCloseReady by remember { mutableStateOf(false) }

    // Reset/seed the close-ready gate when activeApp changes.
    LaunchedEffect(activeApp) {
        val a = activeApp
        appCloseReady = (a != null && a !in GATED_CLOSE_APPS)
    }

    // Auto-close timer — only runs once the gate is open.
    LaunchedEffect(activeApp, appCloseReady) {
        val a = activeApp ?: return@LaunchedEffect
        if (!appCloseReady) return@LaunchedEffect
        val ms = if (a in SHORT_APPS) 5_000L else 10_000L
        delay(ms)
        if (activeApp == a) {
            visited = visited + a
            activeApp = null
        }
    }

    // "time flies in the apps..." banner → onComplete after a beat.
    LaunchedEffect(finishing) {
        if (finishing) {
            delay(2_200L)
            onComplete()
        }
    }

    val allVisited = visited.containsAll(ALL_APP_NAMES)

    // Calculator install progress — runs once when the user has visited every
    // other app. Mirrors the Phase-1 install animation so the player gets the
    // same "icon downloading" beat before they can tap to exit.
    val installProgress = remember { Animatable(0f) }
    LaunchedEffect(allVisited) {
        if (allVisited && installProgress.value < 1f) {
            installProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = INSTALL_DURATION_MS, easing = LinearEasing)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101418))) {
        AsyncImage(
            model = "file:///android_asset/$ICON_DIR_FRESH/background.svg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Spacer(modifier = Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (allVisited) {
                    item {
                        InstallingCalcTile(
                            label = "Just A Calculator",
                            progress = installProgress.value,
                            onClick = { finishing = true }
                        )
                    }
                }
                items(GRID_APPS, key = { it.name }) { app ->
                    val used = app.name in visited
                    val dir = if (used) ICON_DIR_USED else ICON_DIR_FRESH
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
                    openAd = { donationOpen = true },
                    onReadyToClose = { appCloseReady = true },
                    onClose = {
                        visited = visited + current
                        activeApp = null
                    }
                )
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

        // Completion banner — appears at the moment the user taps the new calc
        // tile, before the city retakes the screen.
        if (finishing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC0A1430)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "time flies in the apps...",
                    color = Color(0xFFE9F2FF),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
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

/** Just-A-Calculator install tile — matches the Phase-1 install animation. */
@Composable
private fun InstallingCalcTile(
    label: String,
    progress: Float,
    onClick: () -> Unit
) {
    val installing = progress < 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                enabled = !installing,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.calc_app_icon),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .alpha(if (installing) 0.35f else 1f)
            )
            if (installing) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 4.dp.toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(
                        size.width - stroke,
                        size.height - stroke
                    )
                    val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f)
                    drawArc(
                        color = Color.White.copy(alpha = 0.25f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
            }
        }
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
// A floating close (X) button is overlaid on top of every app's content so the
// user can always escape, even before the auto-close timer fires.
@Composable
private fun AppShell(
    name: String,
    openAd: () -> Unit,
    onReadyToClose: () -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF2F4F7))) {
        when (name) {
            "fbook"     -> AppSocialFacebook()
            "gram"      -> AppSocialInstagram()
            "tok"       -> AppSocialTikTok()
            "youtube"   -> AppSocialYouTube()
            "airbnb"    -> AppAirbnb()
            "amazon"    -> AppAmazon()
            "birds"     -> AppNagBox(title = "Purchase more birds?", openAd = openAd)
            "candy"     -> AppNagBox(title = "Need more lives? Need more golden bars!", openAd = openAd)
            "calc"      -> AppCalculator()
            "camera"    -> AppCamera(openAd = openAd)
            "discord"   -> AppDiscord(onClose = onClose)
            "duo"       -> AppDuolingo(openAd = openAd, onReadyToClose = onReadyToClose)
            "mail"      -> AppMail(openAd = openAd)
            "message"   -> AppMessages()
            "phone"     -> AppPhone()
            "phonebook" -> AppContacts()
            "TEMU"      -> AppTemu()
            "tetris"    -> AppTetris()
            else        -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(name, color = Color.Black)
            }
        }

        // User-driven close — always available, regardless of auto-close timer.
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

@Composable
private fun AppSocialFacebook() {
    // 4 placeholder posts repeated 3× (≈12 cards) so the user can scroll for a
    // while; copy will be supplied later.
    val posts = remember { (0 until 12).map { i -> i % 4 } }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF0F2F5)).statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().background(Color.White).padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
            Text("facebook", color = Color(0xFF1877F2), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
            items(posts.size) { idx ->
                val n = posts[idx]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFD0D7E2)))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("User name placeholder ${n + 1}", color = Color(0xFF1C1E21), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("3h · 🌐", color = Color(0xFF65676B), fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Post text placeholder ${n + 1} — caption will go here.",
                        color = Color(0xFF1C1E21), fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFCBD5DC))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        for (s in listOf("👍 Like", "💬 Comment", "↗ Share")) {
                            Text(s, color = Color(0xFF65676B), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun AppSocialInstagram() {
    val posts = remember { (0 until 12).map { i -> i % 4 } }
    Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
            Text("Instagram", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(posts.size) { idx ->
                val n = posts[idx]
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFFFC1E3)))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("user_${n + 1}_placeholder", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color(0xFFE3E0DA))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("♡", color = Color.Black, fontSize = 22.sp)
                        Text("💬", color = Color.Black, fontSize = 18.sp)
                        Text("↗", color = Color.Black, fontSize = 20.sp)
                    }
                    Text(
                        "user_${n + 1}_placeholder · caption placeholder",
                        color = Color.Black, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
@Composable
private fun AppSocialTikTok() {
    // Vertical full-bleed cards, one per post, scrollable.
    val posts = remember { (0 until 12).map { i -> i % 4 } }
    LazyColumn(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                    Text("@user_${n + 1}_placeholder", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
@Composable
private fun AppSocialYouTube() {
    val posts = remember { (0 until 12).map { i -> i % 4 } }
    Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(28.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF0000)))
                Spacer(modifier = Modifier.width(8.dp))
                Text("YouTube", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(posts.size) { idx ->
                val n = posts[idx]
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color(0xFFD8D8D8))
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFB7C6D6)))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Video title placeholder ${n + 1}", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("channel placeholder · 12K views · 4 days ago", color = Color(0xFF606060), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun AppAirbnb() {
    // A scrolling list of nothing-but-fees, the joke being that the booking
    // total is buried under endless service charges.
    val labels = listOf(
        "Cleaning fee", "Service fee", "Platform fee", "Convenience fee",
        "Booking protection", "Guest verification fee", "Local taxes",
        "Resort fee", "Linen surcharge", "Towel rental", "Wi-Fi access fee",
        "Smart-lock fee", "Parking fee", "Early-check-in fee", "Late check-out fee",
        "Pet-not-allowed deposit", "Extra-guest fee", "Trash removal",
        "Air-conditioning surcharge", "Heating surcharge", "Welcome-basket fee",
        "Damage-protection plan", "Currency conversion fee", "Payment processing fee",
        "Host courtesy fee", "Property-tax pass-through", "Insurance levy",
        "Eco-fee", "Service-recovery fee", "Concierge gratuity",
        "Booking-confirmation fee", "App-usage fee", "Loyalty-tier upgrade",
        "Service-fee service fee", "Fee-disclosure fee", "Total."
    )
    Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFF385C)).padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
            Text("Reservation summary", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(labels) { lbl ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(lbl, color = Color(0xFF222222), fontSize = 14.sp)
                    Text("$${(7..149).random()}", color = Color(0xFF555555), fontSize = 14.sp)
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEAEAEA)))
            }
        }
    }
}
@Composable
private fun AppAmazon() {
    // Half-a-dozen products, every one labelled "Amazon's Choice".
    data class Prod(val title: String, val price: String, val swatch: Color)
    val items = listOf(
        Prod("Universal phone charger (probably)", "$12.99", Color(0xFF7B8794)),
        Prod("Set of 3 microfibre cloths",          "$6.49",  Color(0xFFAEC7B5)),
        Prod("LED desk lamp, no instructions",      "$23.00", Color(0xFFF1E0A6)),
        Prod("Cable organiser (color may vary)",    "$3.99",  Color(0xFF9FB3C8)),
        Prod("Stainless steel mug, 12oz",           "$14.49", Color(0xFFB7A38C)),
        Prod("Bluetooth earbuds, 4★ avg.",          "$19.99", Color(0xFF6E7A8A))
    )
    Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF131921)).padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
            Text("amazon", color = Color(0xFFFF9900), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { p ->
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Box(modifier = Modifier.size(86.dp).clip(RoundedCornerShape(6.dp)).background(p.swatch))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF232F3E))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Amazon's Choice", color = Color(0xFFFF9900), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(p.title, color = Color(0xFF111111), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(p.price, color = Color(0xFFB12704), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEAEAEA)))
            }
        }
    }
}
@Composable
private fun AppCalculator() {
    // Minimal four-function calculator. Builds an expression by tapping digits
    // and operators; "=" evaluates. Operator precedence is intentionally naive.
    var display by remember { mutableStateOf("0") }
    var pending by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<Char?>(null) }
    var justEval by remember { mutableStateOf(false) }

    fun tap(c: Char) {
        when (c) {
            in '0'..'9' -> {
                if (display == "0" || justEval) { display = c.toString(); justEval = false }
                else display += c
            }
            '.' -> { if (!display.contains('.')) display += "." }
            'C' -> { display = "0"; pending = null; op = null; justEval = false }
            '+', '-', '×', '÷' -> {
                pending = display.toDoubleOrNull() ?: 0.0
                op = c
                justEval = true
            }
            '=' -> {
                val cur = display.toDoubleOrNull() ?: 0.0
                val p = pending; val o = op
                if (p != null && o != null) {
                    val r = when (o) {
                        '+' -> p + cur
                        '-' -> p - cur
                        '×' -> p * cur
                        '÷' -> if (cur != 0.0) p / cur else 0.0
                        else -> cur
                    }
                    display = if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
                    pending = null; op = null; justEval = true
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1B1F))) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(120.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(display, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light)
        }
        val rows = listOf(
            listOf("C", "(", ")", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=")
        )
        Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in rows) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((i, label) in row.withIndex()) {
                        val w = if (label == "0") 2f else 1f
                        Box(
                            modifier = Modifier
                                .weight(w)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (label in setOf("+","-","×","÷","=")) Color(0xFFFF9F0A) else Color(0xFF2C2C2E))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember(label + i) { MutableInteractionSource() }
                                ) { tap(label[0]) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun AppCamera(openAd: () -> Unit) {
    // Plain dark "viewfinder" (no scan animation per spec). After 5s an upgrade
    // popup appears; tapping the CTA opens the donation/Play-Store landing.
    var showUpgrade by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(5_000L); showUpgrade = true }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
        )
        if (showUpgrade) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF5F5F7))
                        .padding(horizontal = 24.dp, vertical = 26.dp)
                ) {
                    Text("Want better photos?", color = Color(0xFF101418), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Upgrade your phone today!", color = Color(0xFF36404B), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1976D2))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { openAd() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Upgrade", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
private fun AppDuolingo(openAd: () -> Unit, onReadyToClose: () -> Unit) {
    // Icelandic word-ordering exercise that the game rigs against the user: every
    // CHECK is marked wrong (even the correct order), burning a life. After 3 lives
    // are gone, an "Upgrade to Duolingo Plus" modal pops up with a CTA that routes
    // through the donation landing page like all the other ads in building 3.
    val prompt = "The cat drinks milk."
    val correct = listOf("Kötturinn", "drekkur", "mjólkina")
    val distractors = listOf("bíllinn", "borðar", "epli", "vatnið")
    val pickWords = remember { (correct + distractors).shuffled() }

    var placed by remember { mutableStateOf(listOf<String>()) }
    var lives by remember { mutableStateOf(3) }
    var showWrongFlash by remember { mutableStateOf(false) }
    var showUpgrade by remember { mutableStateOf(false) }

    // Wrong-answer flash auto-clears after a beat; the wrong answer is then wiped
    // from the placed row so the user can take another doomed swing.
    LaunchedEffect(showWrongFlash) {
        if (showWrongFlash) {
            delay(1_200L)
            placed = emptyList()
            showWrongFlash = false
            if (lives <= 0) showUpgrade = true
        }
    }

    // Signal the outer auto-close gate the moment the upgrade modal appears.
    LaunchedEffect(showUpgrade) {
        if (showUpgrade) onReadyToClose()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFCEF)).statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 56.dp, top = 16.dp, bottom = 16.dp)) {
            // Header row: prompt label on the left, lives (hearts) on the right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Translate this sentence", color = Color(0xFF3C3C3C), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until 3) {
                        Text(
                            if (i < lives) "♥" else "♡",
                            color = if (i < lives) Color(0xFFFF4B4B) else Color(0xFFBDBDBD),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Text(prompt, color = Color(0xFF111111), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Placed-words row (the answer being built).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFFFFF))
                    .padding(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    for ((i, w) in placed.withIndex()) {
                        DuoChip(text = w, onClick = {
                            if (!showWrongFlash) placed = placed.toMutableList().also { it.removeAt(i) }
                        })
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))

            // Pick-list (two rows).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (w in pickWords.take(4)) {
                    if (w in placed) {
                        Box(modifier = Modifier.weight(1f).height(40.dp))
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            DuoChip(text = w, onClick = { if (!showWrongFlash) placed = placed + w })
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (w in pickWords.drop(4)) {
                    if (w in placed) {
                        Box(modifier = Modifier.weight(1f).height(40.dp))
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            DuoChip(text = w, onClick = { if (!showWrongFlash) placed = placed + w })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
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
                        // Every answer is wrong. Burn a life and flash.
                        lives = (lives - 1).coerceAtLeast(0)
                        showWrongFlash = true
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("CHECK", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Wrong-answer banner across the bottom.
        if (showWrongFlash) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xFFFFD8D8))
                    .padding(16.dp)
            ) {
                Text("Oops! That's not right.", color = Color(0xFFCC2929), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Correct answer: Kötturinn drekkur mjólkina", color = Color(0xFFCC2929), fontSize = 12.sp)
            }
        }

        // Out-of-lives upsell modal.
        if (showUpgrade) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* absorb */ },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("You're out of lives!", color = Color(0xFF111111), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Upgrade to Duolingo Plus to get unlimited lives.",
                        color = Color(0xFF555555),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFFC800))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { openAd() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Upgrade Now",
                            color = Color(0xFF111111),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
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
private data class MailMsg(
    val sender: String,
    val subject: String,
    val body: String,
    val cta: String?,
)

private val DOOR3_MAILS = listOf(
    MailMsg(
        sender = "eBay",
        subject = "eBay: There's still time to share your feedback!",
        body = "Your review matters\n\nYour feedback helps fellow buyers make informed decisions and strengthens the eBay community. Take a moment to share feedback on your recent purchase – it's quick, easy, and makes a big impact.",
        cta = "Rate Now",
    ),
    MailMsg(
        sender = "Experian",
        subject = "Experian: Update to your credit score.",
        body = "Hello, your Experian credit score has changed!\nKnowing your score can help you see where you stand with your finances.\n\nRemember, checking your score will never harm it.",
        cta = "Check score now",
    ),
    MailMsg(
        sender = "Spotify",
        subject = "Spotify: Rihanna is coming to your town!",
        body = "Rihanna and 5+ other artists you love are heading out on tour! Don't miss their gigs near you.",
        cta = "View events",
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
private fun AppMail(openAd: () -> Unit) {
    var open by remember { mutableStateOf<Int?>(null) }
    val o = open

    if (o == null) {
        Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFC5221F)).padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
                Text("Inbox", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                            ) { openAd() }
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
private fun AppContacts() {
    // Mix of normal-person contacts and a handful of ad entries with a tiny
    // "AD" marker — same layout regardless of contact-permission outcome.
    data class Row(val name: String, val isAd: Boolean)
    val rows = remember {
        val plain = listOf(
            "Mum", "Dad", "Becky - Manager", "Jamie", "Sam", "Emily", "Alex",
            "Chris", "Priya", "Liam", "Ava", "Noah", "Olivia", "Ethan",
            "Sophie", "Marcus", "Hannah", "Tom (Pub Quiz)", "Aunt Karen",
            "Dr. Patel", "Landlord", "Plumber Steve"
        ).map { Row(it, isAd = false) }
        val ads = listOf(
            "The Insurance Lawyer",
            "The Teeth Doctor",
            "The Best Car Recovery",
            "Cheapest Locksmith In Town",
            "Cash-For-Phones (24h)"
        ).map { Row(it, isAd = true) }
        // Sort plain contacts alphabetically so the list reads like a real
        // address book; ads are interleaved by shuffling them into the result.
        val sorted = plain.sortedBy { it.name }
        val mixed = sorted.toMutableList()
        for ((i, ad) in ads.withIndex()) {
            val pos = ((i + 1) * (mixed.size + 1) / (ads.size + 1)).coerceIn(0, mixed.size)
            mixed.add(pos, ad)
        }
        mixed
    }
    Column(modifier = Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF263238)).padding(start = 14.dp, top = 14.dp, end = 56.dp, bottom = 14.dp)) {
            Text("Contacts", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rows) { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFE0E0E0)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(r.name, color = Color(0xFF111111), fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (r.isAd) {
                        Text(
                            "AD",
                            color = Color(0xFF888888).copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEAEAEA)))
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
            "PAY FOR SHIPPING ONLY",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}
@Composable
private fun AppTetris() {
    // Minimal-but-functional Tetris on a 6×10 board. Each filled cell is rendered
    // as a phone-app icon — both for the currently-falling piece and for locked
    // cells on the board. Standard tetromino shapes (I/O/T/J/L/S/Z) spawn at the
    // top in random orientation; left/right/rotate/drop controls live below the
    // playfield. Filled rows clear; piece can't spawn → game over.
    val cols = 6
    val rows = 10
    val iconPool = remember {
        listOf(
            "fbook", "gram", "tok", "youtube", "airbnb", "amazon",
            "discord", "duo", "calc", "camera", "candy", "birds", "TEMU"
        )
    }
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
        pieceIcons = cells.map { iconPool.random() }
    }

    fun lockPiece() {
        if (pieceCells.isEmpty()) return
        val nb = Array(rows) { board[it].copyOf() }
        for ((i, cell) in pieceCells.withIndex()) {
            val (c, r) = cell
            if (r in 0 until rows && c in 0 until cols) nb[r][c] = pieceIcons[i]
        }
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
            delay(700L)
            if (gameOver) break
            if (pieceCells.isEmpty()) {
                spawn()
            } else if (!tryMove(0, 1)) {
                lockPiece()
                spawn()
            }
        }
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
            Text(
                if (gameOver) "GAME OVER" else "Score $score",
                color = if (gameOver) Color(0xFFFF6464) else Color(0xFFCCD6E8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
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
                                    AsyncImage(
                                        model = "file:///android_asset/$ICON_DIR_FRESH/$drawIcon.svg",
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

@Composable
private fun AppNagBox(title: String, openAd: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101418)), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF7EAD0))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { openAd() }
                .padding(horizontal = 26.dp, vertical = 22.dp)
        ) {
            Text(title, color = Color(0xFF14202F), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AppStub(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, color = Color(0xFF14202F), fontSize = 18.sp)
    }
}
