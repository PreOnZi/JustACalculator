package com.fictioncutshort.justacalculator.ui.components

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fictioncutshort.justacalculator.R
import com.fictioncutshort.justacalculator.logic.TalkAudioHandler
import com.fictioncutshort.justacalculator.ui.screens.PhoneTetrisApp
import kotlinx.coroutines.delay

/**
 * HomeScreenOverlay
 *
 * Phone-homescreen overlay shown at step 1086 (after the rotary dial fails).
 * Renders an Android-like home screen using SVG assets from
 * `assets/phonescreen/phonedetour/` and dispatches each icon tap to its own
 * mini-app, popup, or fake-notification gag.
 *
 * Icon-tap behavior is owned here (not lifted to MainActivity) because most of
 * the gags are self-contained UI — popups, growing dialogs, fake notifications —
 * and lifting per-app state would explode the surface area for nothing.
 *
 * [onIconClick] is kept for upstream observability/logging only — it fires for
 * every icon press but does not gate the local dispatch.
 */
/** Number that, when dialed via the in-app keypad, triggers the fake call. */
private const val MAGIC_CALL_NUMBER = "192837465"

/** ms of free exploration before the calculator nudges the user via the
 *  in-overlay banner. */
private const val EXPLORATION_TIMEOUT_MS = 20_000L

/** Sentinel name for the post-call return tile. Routed specially everywhere. */
private const val RETURN_ICON_NAME = "_calculator_return"

/** How long the iOS-style install animation runs before the tile is tappable. */
private const val INSTALL_DURATION_MS = 3000

@Composable
fun HomeScreenOverlay(
    modifier: Modifier = Modifier,
    audioHandler: TalkAudioHandler? = null,
    onIconClick: (String) -> Unit = {},
    onReturnToCalculator: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Wider grid in landscape so the icons aren't strewn across half a screen
    // each. Dock keeps its existing relative sizing.
    val gridColumns = if (isLandscape) 6 else 4

    // Bottom dock — fixed, always visible.
    val dockIcons = remember {
        listOf(
            HomeIcon(name = "phone",     label = "Phone"),
            HomeIcon(name = "message",   label = "Messages"),
            HomeIcon(name = "mail",      label = "Mail"),
            HomeIcon(name = "phonebook", label = "Contacts")
        )
    }

    // Active app/popup state declared early so gridPages can react to it.
    var activeApp by remember { mutableStateOf<String?>(null) }
    var fakeNotification by remember { mutableStateOf<Pair<String, Long>?>(null) }
    // Sticky onboarding nudge — survives icon taps and never auto-hides; only
    // cleared by the action it asks for (open phone app, then dial the magic
    // number). A tester missed the original 7s/14s nudges because they vanished
    // mid-exploration. While this is non-null, the icon-tap funny notifications
    // are suppressed so they can't displace it.
    var persistentNotification by remember { mutableStateOf<String?>(null) }
    var keypadInitialNumber by remember { mutableStateOf("") }
    var dialedNumber by remember { mutableStateOf("") }
    var exploreNotifFired by remember { mutableStateOf(false) }
    var dialNotifFired by remember { mutableStateOf(false) }
    var callCompleted by remember { mutableStateOf(false) }

    // Grid pages. 8 icons per page where possible. The "Just A Calculator" tile
    // is prepended to page 1 the moment the magic-number call ends; the pager
    // also auto-scrolls to page 0 (see LaunchedEffect below) so the user lands
    // on the install animation directly. Page 1 grows from 8 → 9 icons when
    // that happens; the 4-col grid simply renders a partial 3rd row.
    val gridPages = remember(callCompleted) {
        val page1Base = listOf(
            HomeIcon("gemini",   "Gemini"),
            HomeIcon("airbnb",   "Airbnb"),
            HomeIcon("fbook",    "Facebook"),
            HomeIcon("calc",     "Calculator"),
            HomeIcon("gpt",      "ChatGPT"),
            HomeIcon("gram",     "Instagram"),
            HomeIcon("TEMU",     "Temu"),
            HomeIcon("camera",   "Camera")
        )
        val page1 = if (callCompleted) {
            listOf(HomeIcon(RETURN_ICON_NAME, "Just A Calculator")) + page1Base
        } else {
            page1Base
        }
        listOf(
            page1,
            listOf(
                HomeIcon("amazon",   "Amazon"),
                HomeIcon("pictures", "Photos"),
                HomeIcon("youtube",  "YouTube"),
                HomeIcon("uber",     "Uber"),
                HomeIcon("discord",  "Discord"),
                HomeIcon("spotify",  "Spotify"),
                HomeIcon("tok",      "TikTok"),
                HomeIcon("birds",    "Angry Birds")
            ),
            listOf(
                HomeIcon("candy",    "Candy Crush"),
                HomeIcon("duo",      "Duolingo"),
                HomeIcon("tetris",   "Tetris")
            )
        )
    }

    // iOS-style install progress for the return-to-calculator icon. Animates
    // 0 → 1 over [INSTALL_DURATION_MS] when [callCompleted] flips, then stays
    // at 1 (icon becomes fully opaque and tappable).
    val installProgress = remember { Animatable(0f) }
    LaunchedEffect(callCompleted) {
        if (callCompleted) {
            installProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = INSTALL_DURATION_MS, easing = LinearEasing)
            )
        }
    }

    // 20-second free-exploration window. After it elapses, the calculator nudges
    // the user via an in-app banner (always visible, on-theme, primary signal).
    // Real Android push removed per UX direction — the user is already inside
    // the phone overlay; a system notification on top of the in-overlay banner
    // was redundant and broke the "you're in the fake phone" illusion.
    LaunchedEffect(Unit) {
        delay(EXPLORATION_TIMEOUT_MS)
        if (!exploreNotifFired) {
            exploreNotifFired = true
            persistentNotification = "ok, I think I got it. Go to the phone app."
            fakeNotification = null
        }
    }

    // Once the user opens the phone app *after* the explore-nudge has fired,
    // swap the persistent banner for the dial-the-magic-number nudge. It stays
    // up until the magic number is actually dialed (see fakecall LaunchedEffect
    // below), so the user can't lose it by tapping around.
    LaunchedEffect(activeApp, exploreNotifFired) {
        if (activeApp == "phone" && exploreNotifFired && !dialNotifFired) {
            dialNotifFired = true
            persistentNotification = "Now, dial $MAGIC_CALL_NUMBER and place a call"
            fakeNotification = null
        }
    }

    // Magic number dialed — the fake-call screen takes over, so clear the
    // sticky dial nudge. From here on the icon-tap funny notifications work
    // normally again.
    LaunchedEffect(activeApp) {
        if (activeApp == "fakecall") {
            persistentNotification = null
        }
    }

    // Mail intent fires the moment "mail" becomes active, then resets.
    LaunchedEffect(activeApp) {
        if (activeApp == "mail") {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:fictioncutshort@gmail.com")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.w("JustACalc", "Mail intent failed: ${e.message}")
            }
            activeApp = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── Wallpaper ─────────────────────────────────────────────────────────
        AsyncImage(
            model = "file:///android_asset/phonescreen/phonedetour/background.svg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // ── Foreground content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            val pagerState = rememberPagerState(pageCount = { gridPages.size })
            // After the magic-number call ends, snap the user back to page 0
            // so the freshly "installing" calculator icon is visible without
            // having to swipe back from whatever page they were on.
            LaunchedEffect(callCompleted) {
                if (callCompleted) pagerState.animateScrollToPage(0)
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                IconPage(
                    icons = gridPages[pageIndex],
                    columns = gridColumns,
                    installProgress = installProgress.value,
                    onIconClick = { name ->
                        // Return-to-calculator tile is short-circuited: only
                        // tappable once the install finishes, and it doesn't
                        // route through handleIconTap (no popup or fake-app).
                        if (name == RETURN_ICON_NAME) {
                            if (installProgress.value >= 1f) {
                                onReturnToCalculator()
                            }
                            return@IconPage
                        }
                        onIconClick(name)
                        handleIconTap(
                            name = name,
                            setApp = { activeApp = it },
                            setNotification = { msg ->
                                // Don't let funny per-icon notifications displace
                                // a sticky onboarding nudge.
                                if (persistentNotification == null) {
                                    fakeNotification = msg?.let { it to 4_000L }
                                }
                            }
                        )
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(gridPages.size) { index ->
                    val isCurrent = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isCurrent) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                Color.White.copy(alpha = if (isCurrent) 0.95f else 0.45f)
                            )
                    )
                }
            }

            DockBar(icons = dockIcons, onIconClick = { name ->
                onIconClick(name)
                handleIconTap(
                    name = name,
                    setApp = { activeApp = it },
                    setNotification = { msg ->
                        // Don't let funny per-icon notifications displace
                        // a sticky onboarding nudge.
                        if (persistentNotification == null) {
                            fakeNotification = msg?.let { it to 4_000L }
                        }
                    }
                )
            })
        }

        // ── Active app dispatch ──────────────────────────────────────────────
        // The wrapper Box absorbs taps that don't land on a child clickable —
        // without it, e.g. the empty space in the Tetris screen leaked clicks
        // through to the home-screen icons underneath.
        val current = activeApp
        if (current != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* absorb */ }
            ) {
                AppHost(
                    appName = current,
                    keypadInitialNumber = keypadInitialNumber,
                    dialedNumber = dialedNumber,
                    audioHandler = audioHandler,
                    onClose = {
                        // Hanging up the magic call is the one path that flips
                        // callCompleted — that surfaces the return-to-calculator
                        // icon on the home screen.
                        if (activeApp == "fakecall") {
                            callCompleted = true
                        }
                        activeApp = null
                        keypadInitialNumber = ""
                        dialedNumber = ""
                    },
                    onContactCall = { contact ->
                        keypadInitialNumber = contact.number
                        activeApp = "phone"
                    },
                    onPlaceCall = { number ->
                        // Only the magic number triggers the fake-call screen. Any
                        // other dialed number is a no-op (gag: visual call only).
                        if (number == MAGIC_CALL_NUMBER) {
                            dialedNumber = number
                            activeApp = "fakecall"
                        }
                    }
                )
            }
        }

        // ── Fake calculator notification (top of screen, over everything) ────
        // Sticky onboarding nudge takes precedence: it can't be tapped away or
        // displaced by per-icon funny notifications underneath.
        val sticky = persistentNotification
        val note = fakeNotification
        when {
            sticky != null -> CalcFakeNotification(
                body = sticky,
                persistent = true,
                onDismiss = {}
            )
            note != null -> CalcFakeNotification(
                body = note.first,
                autoHideMs = note.second,
                onDismiss = { fakeNotification = null }
            )
        }
    }
}

/**
 * Routes an icon name to the right popup / overlay / fake notification.
 *
 * Mail is special-cased: setting [setApp] to "mail" causes [HomeScreenOverlay]
 * to fire the email intent in a LaunchedEffect (we can't do it here without a
 * Context handle).
 */
private fun handleIconTap(
    name: String,
    setApp: (String?) -> Unit,
    setNotification: (String?) -> Unit
) {
    when (name) {
        // Fake calculator notifications (no app launches at all)
        "calc" -> setNotification("Good try. Remember, you're already inside a calculator.")
        "fbook" -> setNotification("Don't even go there.")
        "tok" -> setNotification("You're already talking to a calculator — you don't need more brain rot.")
        "gpt", "gemini" -> setNotification("Hey, I am up here. You're here to talk to me.")

        // Full-screen apps + popups handled by AppHost
        else -> setApp(name)
    }
}

/**
 * Renders the active mini-app or popup. One slot only — the home screen
 * surface keeps `activeApp` to a single value.
 */
@Composable
private fun AppHost(
    appName: String,
    keypadInitialNumber: String,
    dialedNumber: String,
    audioHandler: TalkAudioHandler?,
    onClose: () -> Unit,
    onContactCall: (PhonebookContact) -> Unit,
    onPlaceCall: (String) -> Unit
) {
    when (appName) {
        // ─── Simple text + Close popups ──────────────────────────────────────
        "airbnb" -> SimplePhoneAppPopup(
            title = "Airbnb",
            body = "We are busy ruining housing availability in your area. We'll be back shortly.",
            onClose = onClose
        )
        "discord" -> SimplePhoneAppPopup(
            title = "Discord",
            body = "Connect to millions of users worldwide. For the low cost of your privacy.",
            onClose = onClose
        )
        "duo" -> SimplePhoneAppPopup(
            title = "Duolingo",
            body = "Can our AI crazy owl bully you into 5 minutes of Icelandic a day?",
            onClose = onClose
        )
        "candy" -> SimplePhoneAppPopup(
            title = "Candy Crush",
            body = "One of the most popular games ever. Ready to get numb?",
            onClose = onClose
        )
        "gram" -> SimplePhoneAppPopup(
            title = "Instagram",
            body = "Your vanity or others'? For sale, for crumbs. That's the Meta promise.",
            onClose = onClose
        )
        "message" -> SimplePhoneAppPopup(
            title = "Messages",
            body = "To green bubble or to blue bubble?!",
            onClose = onClose
        )
        "spotify" -> SimplePhoneAppPopup(
            title = "Spotify",
            body = "Dominating the music industry, yet on the verge of collapsing. Loving life.",
            onClose = onClose
        )
        "TEMU" -> SimplePhoneAppPopup(
            title = "Temu",
            body = "We essentially ship you things so you can throw them away. Living like a billionaire. Very much so.",
            onClose = onClose
        )
        "uber" -> SimplePhoneAppPopup(
            title = "Uber",
            body = "Sharing a car with a stranger. Fun!",
            onClose = onClose
        )
        "youtube" -> SimplePhoneAppPopup(
            title = "YouTube",
            body = "Your favourite makeup artist and a two-hour explanation, why Hitler was a good guy. On one platform!",
            onClose = onClose
        )

        // ─── Choice popup (Amazon) ──────────────────────────────────────────
        "amazon" -> ChoicePhoneAppPopup(
            body = "We have it all. But gladly take more. What would you like to give us this time?",
            choices = listOf("Money", "Data", "Both"),
            followUpFor = { pick ->
                when (pick) {
                    "Both" -> "We were gonna take them regardless. Thank you."
                    else -> "Thank you for confirming your choices, we will take that. And more."
                }
            },
            onClose = onClose
        )

        // ─── Growing popup (Angry Birds) ────────────────────────────────────
        "birds" -> GrowingBirdsPopup(onClose = onClose)

        // ─── Functional mini-apps ───────────────────────────────────────────
        "camera"    -> PhoneCameraApp(onClose = onClose)
        "phone"     -> PhoneKeypadApp(
            onClose = onClose,
            initialNumber = keypadInitialNumber,
            onPlaceCall = onPlaceCall
        )
        "phonebook" -> PhonePhonebookApp(onClose = onClose, onContactCall = onContactCall)
        "pictures"  -> PhonePicturesApp(onClose = onClose)
        "tetris"    -> PhoneTetrisApp(onClose = onClose)

        // ─── Fake call screen (magic number only) ───────────────────────────
        "fakecall" -> {
            // Without an audio handler we'd lose the echo effect, which is the
            // whole point — bail to home rather than render a half-broken call.
            if (audioHandler == null) {
                LaunchedEffect(Unit) { onClose() }
            } else {
                PhoneCallScreen(
                    number = dialedNumber,
                    audioHandler = audioHandler,
                    onHangup = onClose
                )
            }
        }

        // mail is handled outside this composable by the LaunchedEffect that
        // fires the ACTION_SENDTO intent — nothing renders here.
        "mail" -> { /* no-op */ }

        else -> { /* unknown icon — silently ignore */ }
    }
}

/**
 * Calculator return-icon tile rendered with an iOS-style install effect:
 *   - icon body is dimmed while installing
 *   - a circular progress arc tracks around the perimeter
 *   - once [progress] hits 1, the arc disappears and the icon becomes tappable
 *
 * Match the visual size/spacing of [HomeIconTile] so it sits cleanly in the grid.
 */
@Composable
private fun InstallingCalcIconTile(
    label: String,
    iconSize: Dp,
    progress: Float,
    onClick: () -> Unit
) {
    val installing = progress < 1f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !installing, onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center
        ) {
            // App icon — dimmed while installing.
            Image(
                painter = painterResource(id = R.drawable.calc_app_icon),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(iconSize / 5))
                    .alpha(if (installing) 0.35f else 1f)
            )
            // Progress ring — only while installing.
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
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** A single icon-with-label tile. */
@Composable
private fun HomeIconTile(
    icon: HomeIcon,
    iconSize: Dp,
    showLabel: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        AsyncImage(
            model = "file:///android_asset/phonescreen/phonedetour/${icon.name}.svg",
            contentDescription = icon.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(iconSize)
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = icon.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun IconPage(
    icons: List<HomeIcon>,
    columns: Int,
    installProgress: Float,
    onIconClick: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Scale icon size to roughly the column width — ~7 cells of headroom
        // for portrait (4 cols) or ~10 for landscape (6 cols). In landscape
        // we shrink the ceiling and the vertical spacing so every icon fits
        // on the short side without forcing the user to scroll.
        val isWide = columns >= 6
        val divisor = (columns * 1.6f).toInt().coerceAtLeast(4)
        val iconCeil = if (isWide) 52.dp else 72.dp
        val iconFloor = if (isWide) 36.dp else 48.dp
        val iconSize = (maxWidth / divisor).coerceAtLeast(iconFloor).coerceAtMost(iconCeil)
        val rowSpacing = if (isWide) 8.dp else 20.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxSize()
        ) {
            items(icons) { icon ->
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (icon.name == RETURN_ICON_NAME) {
                        InstallingCalcIconTile(
                            label = icon.label,
                            iconSize = iconSize,
                            progress = installProgress,
                            onClick = { onIconClick(icon.name) }
                        )
                    } else {
                        HomeIconTile(
                            icon = icon,
                            iconSize = iconSize,
                            onClick = { onIconClick(icon.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockBar(
    icons: List<HomeIcon>,
    onIconClick: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Wide layouts (landscape phones) get smaller dock icons so the
        // dock row stays short and the grid above isn't pushed off-screen.
        val isWide = maxWidth > 500.dp
        val dockCeil = if (isWide) 60.dp else 80.dp
        val dockFloor = if (isWide) 44.dp else 56.dp
        val dockIconSize = (maxWidth / 6).coerceAtLeast(dockFloor).coerceAtMost(dockCeil)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                icons.forEach { icon ->
                    HomeIconTile(
                        icon = icon,
                        iconSize = dockIconSize,
                        showLabel = false,
                        onClick = { onIconClick(icon.name) }
                    )
                }
            }
        }
    }
}

private data class HomeIcon(val name: String, val label: String)
