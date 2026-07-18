package com.fictioncutshort.justacalculator.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.fictioncutshort.justacalculator.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// ═══════════════════════════════════════════════════════════════════════════════
//  BUILDING 6 — minimal runner sandbox.
//
//  Just ONE character running over the authored modular track tiles
//  (3D assets/Stickmancourse → models/stickmancourse/*.obj), with collectible coins.
//  No walls, rivals or stages — the player, the track and coins.
//
//  The track IS the ground: the character's height is sampled from the real tile
//  meshes (groundAt). Where there's no surface — a carved gap, or off the side —
//  there's nothing to stand on, so the character falls and respawns. Ramps are
//  followed exactly because their height comes from the mesh itself.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Building6Runner(onComplete: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val renderer = remember {
        RunnerRenderer(context).apply {
            // Seed the saved run BEFORE the course is built, so buildTileLayout
            // starts the player with the coins and the friends they already had.
            resumeCoins = com.fictioncutshort.justacalculator.logic.BuildingProgress.getInt(context, 6, "coins", 0)
            resumeRefusals = com.fictioncutshort.justacalculator.logic.BuildingProgress.getInt(context, 6, "refusals", 0)
            resumeFriends = com.fictioncutshort.justacalculator.logic.BuildingProgress.getInt(context, 6, "friends", 0)
            furthestZ = com.fictioncutshort.justacalculator.logic.BuildingProgress.getFloat(context, 6, "stage", 0f)
        }
    }

    // Helper names come from the user's contacts (or a fallback list if no access).
    LaunchedEffect(renderer) { renderer.helperNames = loadRunnerHelperNames(context) }
    // vo024 — comes in about 20 s into the run (cancelled if they finish sooner).
    LaunchedEffect(Unit) {
        com.fictioncutshort.justacalculator.logic.VoiceoverManager.init(context)
        kotlinx.coroutines.delay(20_000)
        com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo024, cctv = false)
    }

    // ── Sound ─────────────────────────────────────────────────────────────────
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(8)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
    }
    val sJump = remember { soundPool.load(context, R.raw.jump, 1) }
    val sCoin = remember { soundPool.load(context, R.raw.coin, 1) }
    val sFight = remember { soundPool.load(context, R.raw.fight, 1) }
    val sPush = remember { soundPool.load(context, R.raw.stonepushing, 1) }
    val sEn5 = remember { soundPool.load(context, R.raw.en5, 1) }
    val runPlayer = remember { MediaPlayer.create(context, R.raw.run)?.apply { isLooping = true; setVolume(0.1f, 0.1f) } }
    val pushPlayer = remember { MediaPlayer.create(context, R.raw.stonepushing)?.apply { isLooping = true; setVolume(0.2f, 0.2f) } }
    val fightPlayer = remember { MediaPlayer.create(context, R.raw.fight)?.apply { isLooping = true; setVolume(0.15f, 0.15f) } }
    val hazPlayer = remember { MediaPlayer.create(context, R.raw.stonepushing)?.apply { isLooping = true; setVolume(0f, 0f) } }
    val ringPlayer = remember { MediaPlayer.create(context, R.raw.phonering)?.apply { isLooping = true; setVolume(0.1f, 0.1f) } }
    val talkPlayer = remember { MediaPlayer.create(context, R.raw.phonetalk)?.apply { isLooping = true; setVolume(0.1f, 0.1f) } }
    val textPlayer = remember { MediaPlayer.create(context, R.raw.texting)?.apply { isLooping = true; setVolume(0.1f, 0.1f) } }
    DisposableEffect(Unit) {
        onDispose { soundPool.release(); runPlayer?.release(); pushPlayer?.release(); fightPlayer?.release(); hazPlayer?.release(); ringPlayer?.release(); talkPlayer?.release(); textPlayer?.release() }
    }

    var coins by remember { mutableStateOf(0) }
    var tollActive by remember { mutableStateOf(false) }
    var tollAmount by remember { mutableStateOf(0) }
    var canPay by remember { mutableStateOf(false) }
    var hillActive by remember { mutableStateOf(false) }
    var hillNeeded by remember { mutableStateOf(0) }
    var hillHave by remember { mutableStateOf(0) }
    var fightActive by remember { mutableStateOf(false) }
    var fightFoes by remember { mutableStateOf(0) }
    var fightAllies by remember { mutableStateOf(0) }
    var gapActive by remember { mutableStateOf(false) }
    var gapClimbers by remember { mutableStateOf(0) }
    var wallActive by remember { mutableStateOf(false) }
    var wallClimbers by remember { mutableStateOf(0) }
    var choiceActive by remember { mutableStateOf(false) }
    var choiceIcon by remember { mutableStateOf("phone") }
    var choiceText by remember { mutableStateOf("") }
    var choiceRightAccepts by remember { mutableStateOf(false) }
    var textingActive by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(3.6f) }
    var noFriendsMsg by remember { mutableStateOf("") }
    var helpDenied by remember { mutableStateOf(false) }
    var finishFade by remember { mutableStateOf(0f) }
    var finishDone by remember { mutableStateOf(false) }
    var lonelyEnding by remember { mutableStateOf(false) }
    LaunchedEffect(finishDone) {
        if (finishDone) {
            // Coins carried to the finish become coin currency (adds to Building 1's coins).
            com.fictioncutshort.justacalculator.logic.CurrencyStore.award(
                context, com.fictioncutshort.justacalculator.logic.Currency.COINS, renderer.coinCount, "b6")
            // How this run was actually run: did the player lean on people, or pay
            // and push their way through alone? One of the two phase-2 inputs to
            // the ending (see ComplicityStore). "Alone" wins ties - the lonely
            // ending already fires at 3 refusals, so match that reading.
            val alone = renderer.lonelyEnding || renderer.refusalCount >= renderer.friendCount
            com.fictioncutshort.justacalculator.logic.ComplicityStore
                .recordBuilding6(context, paidToSolo = alone)
            // vo025 if they leaned on friends, vo026 if they paid/pushed through alone.
            com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(
                if (alone) R.raw.vo026 else R.raw.vo025, cctv = false)
            onComplete()   // fade done → back to the city, building complete
        }
    }
    // Chat bubbles as a back-and-forth exchange (side, widthDp), added over time while texting.
    val chatBubbles = remember { mutableStateListOf<Pair<Boolean, Int>>() }
    LaunchedEffect(textingActive) {
        chatBubbles.clear()
        if (!textingActive) return@LaunchedEffect
        var left = true
        while (true) {
            delay(if (left) 500 else 750)
            chatBubbles.add(left to (55 + (Math.random() * 85).toInt()))
            if (chatBubbles.size > 6) chatBubbles.removeAt(0)
            left = !left
        }
    }
    var paused by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(false) }
    val hintPrefs = remember { context.getSharedPreferences("building6", Context.MODE_PRIVATE) }
    var hintsSeen by remember { mutableStateOf(hintPrefs.getBoolean("hints_seen", false)) }
    LaunchedEffect(gameStarted) {
        if (gameStarted && !hintsSeen) {
            delay(6500)
            hintPrefs.edit().putBoolean("hints_seen", true).apply()
            hintsSeen = true
        }
    }
    var labels by remember { mutableStateOf<List<RunnerRenderer.ScreenLabel>>(emptyList()) }
    var atFinish by remember { mutableStateOf(false) }
    LaunchedEffect(renderer) {
        var pJump = 0; var pCoin = 0; var pFight = 0; var pPush = 0; var pMsg = 0
        fun loop(mp: MediaPlayer?, on: Boolean) { mp?.let { if (on && !it.isPlaying) it.start(); if (!on && it.isPlaying) it.pause() } }
        while (true) {
            withFrameNanos { }
            // sound: one-shots on counter change, the run sound looping while running
            // all quiet — just a little extra dimension, not loud
            if (renderer.jumpSfx != pJump) { pJump = renderer.jumpSfx; soundPool.play(sJump, 0.12f, 0.12f, 1, 0, 1f) }
            if (renderer.coinSfx != pCoin) { pCoin = renderer.coinSfx; soundPool.play(sCoin, 0.11f, 0.11f, 1, 0, 1f) }
            if (renderer.fightSfx != pFight) { pFight = renderer.fightSfx; soundPool.play(sFight, 0.15f, 0.15f, 1, 0, 1f) }
            if (renderer.pushSfx != pPush) { pPush = renderer.pushSfx; soundPool.play(sPush, 0.15f, 0.15f, 1, 0, 1f) }
            if (renderer.msgPopupSfx != pMsg) { pMsg = renderer.msgPopupSfx; soundPool.play(sEn5, 0.13f, 0.13f, 1, 0, 1f) }
            // looping sounds (all silenced while paused): run / phone ring / on-the-phone / texting
            val pd = renderer.paused
            loop(runPlayer, renderer.runningSfx && !pd)
            loop(pushPlayer, renderer.boulderPushing && !pd)
            loop(fightPlayer, renderer.fightingSfxActive && !pd)
            // Little rolling boulders: a quiet rumble that swells with how many are rolling.
            hazPlayer?.let { hp ->
                val n = renderer.hazardRollingCount
                if (n > 0 && !pd) {
                    val v = (0.04f * n).coerceAtMost(0.14f)
                    try { hp.setVolume(v, v); if (!hp.isPlaying) hp.start() } catch (_: Throwable) {}
                } else {
                    try { if (hp.isPlaying) hp.pause() } catch (_: Throwable) {}
                }
            }
            loop(ringPlayer, renderer.callRinging && !pd)
            loop(talkPlayer, renderer.onPhone && !pd)
            loop(textPlayer, renderer.textingActive && !pd)
            atFinish = renderer.atFinish
            coins = renderer.coinCount
            tollActive = renderer.tollActive
            tollAmount = renderer.tollAmount
            canPay = renderer.canPayToll
            hillActive = renderer.hillActive
            hillNeeded = renderer.hillNeeded
            hillHave = renderer.hillHave
            fightActive = renderer.fightActive
            fightFoes = renderer.fightFoes
            fightAllies = renderer.fightAllies
            gapActive = renderer.gapActive
            gapClimbers = renderer.gapClimbers
            wallActive = renderer.wallActive
            wallClimbers = renderer.wallClimbers
            choiceActive = renderer.choiceActive
            choiceIcon = renderer.choiceIcon
            choiceText = renderer.choiceText
            choiceRightAccepts = renderer.choiceRightAccepts
            textingActive = renderer.textingActive
            countdown = renderer.countdown
            noFriendsMsg = renderer.noFriendsMsg
            helpDenied = renderer.helpDenied
            finishFade = renderer.finishFade
            finishDone = renderer.finishDone
            lonelyEnding = renderer.lonelyEnding
            // Save the run as it goes: the coins, the ledger of who they leaned on,
            // and how far they got. Written from the frame poll so a kill at any
            // moment costs the course, not the standing.
            com.fictioncutshort.justacalculator.logic.BuildingProgress.putInt(context, 6, "coins", renderer.coinCount)
            com.fictioncutshort.justacalculator.logic.BuildingProgress.putInt(context, 6, "refusals", renderer.refusalCount)
            com.fictioncutshort.justacalculator.logic.BuildingProgress.putInt(context, 6, "friends", renderer.friendCount)
            com.fictioncutshort.justacalculator.logic.BuildingProgress.putFloat(context, 6, "stage", renderer.furthestZ)
            labels = renderer.labels
            if (renderer.started) gameStarted = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val boxW = maxWidth
        val boxH = maxHeight

        // One GLSurfaceView, kept across recompositions, with its EGL context preserved so
        // backgrounding the app does NOT rebuild the course / wipe your progress.
        val glView = remember {
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(3)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }
        // Pause/resume the render thread with the app lifecycle (minimise → freeze, not lose).
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val obs = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> glView.onPause()
                    Lifecycle.Event.ON_RESUME -> glView.onResume()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(obs)
            onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
        }
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { renderer.jump() } }
                .pointerInput(Unit) { detectDragGestures { _, drag -> renderer.steer(drag.x) } },
            factory = { glView },
        )

        // Helper name tags floating above their heads (projected from the renderer).
        for (lb in labels) {
            Text(
                lb.text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(x = boxW * lb.xFrac - 40.dp, y = boxH * lb.yFrac - 10.dp)
                    .width(80.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                textAlign = TextAlign.Center,
            )
        }

        // Pause (top-left) — freezes the run and opens the pause menu.
        Box(
            modifier = Modifier
                .padding(start = 8.dp, top = 28.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { paused = true; renderer.paused = true } },
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(5.dp).height(18.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
                Box(Modifier.width(5.dp).height(18.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
            }
        }

        // Coin counter (top-right).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 30.dp, end = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFC107).copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("● $coins", color = Color(0xFF3E2723), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // Incoming call/message on approach to a fork — same card as the help popups, but NO
        // gesture modifiers (the player must still be able to steer). Lean left = accept.
        if (choiceActive) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 60.dp)
                        .width(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E2330).copy(alpha = 0.82f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = "file:///android_asset/phonescreen/tankgame/$choiceIcon.svg",
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        choiceText,
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val accept = Color(0xFFA5D6A7); val decline = Color(0xFFFFAB91)
                        Text(if (choiceRightAccepts) "◄ decline" else "◄ accept",
                            color = if (choiceRightAccepts) decline else accept, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (choiceRightAccepts) "accept ►" else "decline ►",
                            color = if (choiceRightAccepts) accept else decline, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Chat bubbles while texting — a back-and-forth of varied-length bubbles (no content).
        if (chatBubbles.isNotEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                for ((left, w) in chatBubbles) {
                    Box(
                        Modifier.align(if (left) Alignment.Start else Alignment.End)
                            .width(w.dp).height(24.dp).clip(RoundedCornerShape(12.dp))
                            .background((if (left) Color(0xFFECEFF1) else Color(0xFF34B7F1)).copy(alpha = 0.92f)),
                    )
                }
            }
        }

        // Start-of-run control hints — shown ONCE ever, and only after the game is actually
        // rendering (no gesture modifiers → taps still reach the game).
        if (gameStarted && !hintsSeen) {
            Column(
                modifier = Modifier.align(Alignment.Center).offset(y = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("TAP to jump", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("SWIPE left / right to move", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Toll dialog: a scrim (consumes taps) + a centred panel.
        if (tollActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable(enabled = true) { },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 60.dp)
                        .width(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E2330).copy(alpha = 0.82f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("TOLL", color = Color(0xFFFFC107), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The toll is $tollAmount coins.",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (helpDenied) noFriendsMsg
                        else if (canPay) "You have $coins."
                        else "You have $coins. You're short.",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DialogButton("Pay", enabled = canPay, accent = Color(0xFF455A64)) { renderer.payToll() }
                        DialogButton(if (helpDenied) "Pay strangers" else "Help", enabled = true, accent = Color(0xFF2E7D32)) { renderer.requestHelp() }
                    }
                }
            }
        }

        // Boulder dialog: same window, Push / Help.
        if (hillActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable(enabled = true) { },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 60.dp)
                        .width(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E2330).copy(alpha = 0.82f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("BOULDER", color = Color(0xFFFFC107), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (helpDenied) noFriendsMsg else "This won't budge alone.",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DialogButton("Push", enabled = true, accent = Color(0xFF455A64)) { renderer.pushBoulder() }
                        DialogButton(if (helpDenied) "Pay strangers" else "Help", enabled = true, accent = Color(0xFF2E7D32)) { renderer.requestPush() }
                    }
                }
            }
        }

        // Ring fight dialog: same window, Fight / Help.
        if (fightActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable(enabled = true) { },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 60.dp)
                        .width(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E2330).copy(alpha = 0.82f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("BLOCKED", color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (helpDenied) noFriendsMsg else "They block the way.",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DialogButton("Fight", enabled = true, accent = Color(0xFF455A64)) { renderer.startFight() }
                        DialogButton(if (helpDenied) "Pay strangers" else "Help", enabled = true, accent = Color(0xFF2E7D32)) { renderer.requestFighter() }
                    }
                }
            }
        }

        // Gap dialog: "might need help for this" — Proceed / Help (stack a human tower).
        // Kept translucent + low on screen so the tower building ahead stays visible.
        if (gapActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable(enabled = true) { },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 60.dp)
                        .width(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E2330).copy(alpha = 0.82f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("GAP", color = Color(0xFFFFC107), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (helpDenied) noFriendsMsg else "Might need some help for this.",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DialogButton("Proceed", enabled = true, accent = Color(0xFF455A64)) { renderer.proceedGap() }
                        DialogButton(if (helpDenied) "Pay strangers" else "Help", enabled = true, accent = Color(0xFF2E7D32)) { renderer.requestClimber() }
                    }
                }
            }
        }

        // Wall dialog: can't jump it — Climb (over the ladder) / Help (add a rung).
        if (wallActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .clickable(enabled = true) { },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 60.dp)
                        .width(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E2330).copy(alpha = 0.82f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("WALL", color = Color(0xFFFFC107), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (helpDenied) noFriendsMsg else "Too high to jump.",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DialogButton("Climb", enabled = true, accent = Color(0xFF455A64)) { renderer.proceedWall() }
                        DialogButton(if (helpDenied) "Pay strangers" else "Help", enabled = true, accent = Color(0xFF2E7D32)) { renderer.requestClimber() }
                    }
                }
            }
        }

        // Finish: sound-effect credits card (in place of the obstacle popup).
        if (atFinish) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Column(
                    modifier = Modifier.padding(bottom = 60.dp).width(300.dp)
                        .clip(RoundedCornerShape(18.dp)).background(Color(0xFF1E2330).copy(alpha = 0.9f)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Sound effects from Pixabay by:", color = Color(0xFFFFC107), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    for (name in listOf("Game Studio", "freesound_community", "Mori_sound", "ŁOPACZAK ut74eroie", "Universfield", "LIECIO", "Elio")) {
                        Text(name, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }

        // Finish fade-to-black (the closing line is voiceover, no on-screen text).
        if (finishFade > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = finishFade.coerceIn(0f, 1f))))
        }

        // Opening 3·2·1·Race! countdown.
        if (countdown > 0f) {
            val txt = when {
                countdown > 2.6f -> "3"
                countdown > 1.6f -> "2"
                countdown > 0.6f -> "1"
                else -> "Race!"
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    txt,
                    color = Color(0xFFFFC107),
                    fontSize = if (txt == "Race!") 64.sp else 96.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Pause menu.
        if (paused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(enabled = true) { },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E2330))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("PAUSED", color = Color(0xFFFFC107), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    DialogButton("Resume", enabled = true, accent = Color(0xFF2E7D32)) {
                        paused = false; renderer.paused = false
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, enabled: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) accent else accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** Helper names from the user's contacts (READ_CONTACTS, if already granted) or a
 *  built-in fallback list when there's no access. */
private val RUNNER_FALLBACK_NAMES = listOf(
    "Mum", "Dad", "Sam", "Alex", "Jordan", "Charlie", "Robin", "Jamie", "Casey", "Riley",
)
private fun loadRunnerHelperNames(context: Context): List<String> {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return RUNNER_FALLBACK_NAMES
    val out = ArrayList<String>()
    try {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            null, null, ContactsContract.Contacts.DISPLAY_NAME + " ASC",
        )?.use { c ->
            val col = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            while (c.moveToNext() && out.size < 50) {
                if (col >= 0) c.getString(col)?.let { if (it.isNotBlank()) out.add(it.trim()) }
            }
        }
    } catch (_: Throwable) { }
    return if (out.isEmpty()) RUNNER_FALLBACK_NAMES else out.shuffled()
}

// ═══════════════════════════════════════════════════════════════════════════════
//  RENDERER
// ═══════════════════════════════════════════════════════════════════════════════
private class RunnerRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // ── Geometry handles ──────────────────────────────────────────────────────
    private class SkinnedMesh(val vao: Int, val indexCount: Int, val color: FloatArray)
    private lateinit var model: GltfSkinnedModel
    private val skinnedMeshes = ArrayList<SkinnedMesh>()

    private var skinProg = 0
    private var flatProg = 0
    private var texProg = 0
    private var boneUbo = 0
    private lateinit var boneBuffer: FloatBuffer

    private var sVP = 0; private var sModel = 0; private var sColor = 0
    private var fVP = 0; private var fModel = 0; private var fColor = 0
    private var tVP = 0; private var tModel = 0; private var tSampler = 0

    // ── Toll sign (toll.png decal on the booth's left sign) ──────────────────────
    private var tollTex = 0
    private var signVao = 0; private var signVerts = 0
    private val SIGN_X = -3.6f       // world x of the sign face (booth's left)
    private val SIGN_Y = 4.2f        // height of the disc centre
    private val SIGN_SIZE = 2.0f     // diameter of the square decal
    private val SIGN_Z_OFF = 0f      // z nudge from the booth centre (toward runner = +)

    private var modelScale = 1f
    private var footOffset = 0f
    private var runClip = 0
    private var idleClip = 0
    private var jumpClip = 0
    private var carryRunClip = 0    // helper jogging with the coin
    private var carryIdleClip = 0   // helper stood holding the coin
    private var pushClip = 0        // shoving the boulder
    private var refuseClip = 0      // head-shake: boulder too heavy alone
    private var waveClip = 0        // helpers waving the player off at the crest
    private var fightIdleClip = 0   // bouncing fight-idle stance (ring)
    private var boxingClip = 0      // throwing punches
    private var climbClip = 0       // climbers stacking into the tower
    private var phoneClip = 0       // accepted a call
    private var textingClip = 0     // accepted a message
    private var handshakeClip = 0   // greeting the friend you stopped to help
    private var climbDownClip = 0   // the column helper coming down
    private var helpWaveClip = 0    // the column helper waving for help
    private val deathClips = IntArray(4)   // 4 death variants

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val modelMat = FloatArray(16)
    private var viewW = 1
    private var viewH = 1

    // ── Authored modular track tiles ────────────────────────────────────────────
    private class TilePiece(val vao: Int, val vertCount: Int, val zMin: Float, val zMax: Float)
    // Tiles are stitched edge-to-edge (each occupies its own length, including the long
    // forks) so nothing bridges across them. zRef = placement offset; [zFar,zNear] is the
    // world span (player enters at zNear, exits toward zFar). topTris = world-space
    // up-facing triangles (9 floats each) for ground tests.
    private class TileInstance(
        val piece: String, val zRef: Float, val zNear: Float, val zFar: Float,
        val fork: Boolean, val color: FloatArray, var topTris: FloatArray,
        val reversed: Boolean = false,   // authored back-to-front → flip opposite to FLIP_Z
        val yOffset: Float = 0f,         // drop an elevated-floor piece so it connects at y≈0
        var done: Boolean = false,       // obstacle instance already cleared → don't re-trigger
    )
    // Pieces authored facing the wrong way get an extra 180° so they run the right way round.
    private val REVERSED_PIECES = setOf("hill", "bouderhill")
    private val tilePieces = HashMap<String, TilePiece>()
    private val tilePositions = HashMap<String, FloatArray>()
    // Extra coloured sub-meshes for a tile (e.g. the toll sign's red/white/black parts).
    private class TilePart(val vao: Int, val vertCount: Int, val color: FloatArray)
    private val tileParts = HashMap<String, List<TilePart>>()
    private val tiles = ArrayList<TileInstance>()
    private var tileStartZ = 0f

    // ── Scenery props (signs/directionals over the track, city lamps + buildings) ──
    private class PropPart(val vao: Int, val count: Int, val color: FloatArray)
    private class PropMesh(val parts: List<PropPart>, val localMinY: Float, val localMaxY: Float)
    private class PropInst(
        val mesh: String, val x: Float, val z: Float, val yOff: Float, val yawDeg: Float,
        val sx: Float, val sy: Float, val sz: Float, val flipZ: Boolean,
    )
    private val propMeshes = HashMap<String, PropMesh>()
    private val props = ArrayList<PropInst>()

    // Tile sizing. Width is scaled more than length so the track is comfortably wide.
    // Vertical scale matches width (3.0) so authored circles stay round (the toll sign) and
    // uprights read tall rather than squished — everything gets correspondingly taller.
    private val SCALE_X = 3.0f                    // authored ±1.0 → ±3.0 (track ~6 wide)
    private val SCALE_Y = 3.0f
    private val SCALE_Z = 1.8f
    // Drop tiles so the WALKABLE FLOOR (authored local y≈0.038) sits at y=0; the rims
    // (local y≈0.17) then stand ~0.24 above it as walls.
    private val TILE_TOP = 0.038f * SCALE_Y
    // Reverses each tile along its length (current far end becomes the front) — applied
    // to both rendering and the ground bake so they always agree.
    private val FLIP_Z = true

    // ── Coins (collectibles floating over the road centre) ──────────────────────
    private class Coin(val x: Float, val y: Float, val z: Float, var taken: Boolean = false)
    private val coins = ArrayList<Coin>()
    private var coinVao = 0
    private var coinVerts = 0
    private val COIN_SCALE = 0.7f
    private val COIN_HOVER = 1.4f                    // float height above the floor
    @Volatile var coinCount = 0

    // ── Toll barrier boom (spans the track just past the booth; lifts when paid) ──
    private var boxVao = 0                           // unit box [0,1]×[-.5,.5]×[-.5,.5]
    private var boxVerts = 0
    private var boulderVao = 0                       // the hill boulder mesh (ball4)
    private var boulderVerts = 0
    private var boulderLocalR = 0.9f                 // authored radius, for scaling
    private val BOULDER_COL = floatArrayOf(0.42f, 0.40f, 0.38f)
    private var barrierLift = 0f                     // 0 = down/blocking, 1 = fully raised
    private val BARRIER_PIVOT_X = -3.4f              // hinge on the LEFT column (world x)
    private val BARRIER_SPAN = 6.8f                  // reaches the right column
    private val BARRIER_Y = 1.3f                     // boom height above the floor
    private val BARRIER_LOCAL_Z = 3.755f            // columns' local z in sptoll (Cube.003/004)
    private val BARRIER_COL = floatArrayOf(0.86f, 0.20f, 0.16f)

    // ── Character ────────────────────────────────────────────────────────────────
    private var charX = 0f
    private var charZ = 0f
    private var charY = 0f
    private var vSpeed = 0f
    private var onGround = true
    private var heading = 0f                         // run direction (rad); 0 = forward (−Z)
    private var roadAhead = false                    // is there road ahead to turn onto (vs a gap)
    @Volatile private var steerDelta = 0f
    private var elapsed = 0f
    private var lastTimeNs = 0L

    private var safeZ = 0f
    private var safeY = 0f
    // Smoothed camera state (lags the character) so the view never jitters with the run.
    private var camX = 0f
    private var camZ = 0f
    private var camHeading = 0f

    private val RUN_SPEED = 10f
    private val STRAFE_SENS = 0.012f                 // drag → lateral movement across the road
    private val HEADING_DEADZONE = 0.06f             // ignore tiny road-angle wobble (anti-twitch)
    private val CHAR_HEIGHT = 2.1f
    private val CAM_BACK = 11f
    private val CAM_HEIGHT = 7f
    private val CAM_CLEARANCE = 3f                  // keep the eye this far above ground behind it
    private val GRAVITY = 24f
    private val JUMP_VEL = 7.5f                     // shorter hop (less far, less high)
    private val FALL_DEATH = 14f                    // fall this far below footing → respawn
    private val GROUND_NY = 0.5f                    // min upward-ness for a standable face
    private val STEP_MAX = 0.33f                    // climb a surface this much higher; taller = wall
                                                    // (scales with SCALE_Y so ramps stay walkable)
    private val HEAD_ROOM = 2.6f                    // surfaces higher than foot+this are overhead
                                                    // (roofs), not walls — you pass under them
    private val STEP_DOWN = 1.7f                    // stick to ground dropping up to this per step
                                                    // (descend slopes/steps without hopping)

    @Volatile private var jumpRequested = false
    @Volatile var countdown = 3.6f                  // 3·2·1·Race! at the start; you're frozen until it clears
    private val COUNTDOWN_GO = 0.6f                  // player starts running when "Race!" shows
    // Sound events (Compose plays them): counters bump on each one-shot; runningSfx loops the run.
    @Volatile var jumpSfx = 0; @Volatile var coinSfx = 0; @Volatile var fightSfx = 0
    @Volatile var pushSfx = 0; @Volatile var fallSfx = 0; @Volatile var runningSfx = false
    @Volatile var boulderPushing = false   // true while the boulder is being shoved uphill
    @Volatile var fightingSfxActive = false // true through the whole fight animation
    @Volatile var hazardRollingCount = 0    // little boulders currently rolling down the hill
    @Volatile var callRinging = false; @Volatile var onPhone = false; @Volatile var msgPopupSfx = 0
    @Volatile var atFinish = false                  // on the finish → show the credits card
    // Finish line: reach the end piece → walk onto the 2nd-place podium (blue=1st, red=3rd),
    // camera pulls back over a sea of tracks, then fade to black → building complete.
    private var finishTimer = 0f
    private var finishPodiumZ = 0f
    private var finishStartX = 0f; private var finishStartZ = 0f; private var finishStartY = 0f
    private var camZoom = 0f                         // 0 = normal chase cam, 1 = pulled-way-back finish cam
    @Volatile var finishFade = 0f                   // 0..1 black-out alpha (Compose reads it)
    @Volatile var finishDone = false                // → Compose calls onComplete (back to city)
    @Volatile var lonelyEnding = false              // refusals ≥ 3 → the "money vs friendships" line
    // Read by Compose at the finish and written to ComplicityStore: this run's
    // answer to "lean on people, or go it alone". It is one of the two phase-2
    // inputs that decide which ending the player gets.
    @Volatile var refusalCount = 0                  // times the player went it alone / paid
    @Volatile var friendCount = 0                   // friends actually banked
    // Set by Compose before the course is built, from the saved run. The TRACK
    // itself restarts - resuming halfway down a procedurally armed course would
    // mean rebuilding every obstacle's armed/spent state - but the player keeps
    // what they earned and what they did: their coins, and their standing with the
    // people they either leaned on or refused.
    @Volatile var resumeCoins = 0
    @Volatile var resumeRefusals = 0
    @Volatile var resumeFriends = 0
    @Volatile var furthestZ = 0f                    // how far down the course they got
    private val FINISH_WALK = 3.4f                   // walk onto the podium
    private val FINISH_HOLD = 5.5f                   // pulled-back "countless tracks" beat
    private val FINISH_FADE = 1.6f                   // fade to black
    private var farZ = -2000f

    fun steer(dxPx: Float) { steerDelta += dxPx }
    fun jump() { jumpRequested = true }
    @Volatile var paused = false          // freeze the sim (pause menu open)
    @Volatile var started = false         // true once the first frame has actually rendered

    // Dialog buttons fire on the UI thread but mutate state the GL thread iterates (helpers,
    // runState…). Queue them and run on the GL thread at the top of update() to avoid crashes.
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()
    private fun drainPending() { while (true) { (pending.poll() ?: return)() } }

    // ── Scripted obstacles: the TOLL + the HILL boulder + summoned helpers ────────
    private enum class RunState {
        RUNNING, TOLL_PROMPT, TOLL_HELP, NEED_PAY, FINISH, HILL_SOLO, HILL_PROMPT, HILL_PUSH, HILL_WATCH,
        FIGHT_PROMPT, FIGHTING, GAP_PROMPT, GAP_TOPPLE, GAP_FALL, WALL_PROMPT, WALL_CLIMB, HAZ_HIT,
        HELP_GREET,
    }
    private var runState = RunState.RUNNING
    private var tollTile: TileInstance? = null
    private var tollPaid = false
    // TEMP debug: skip the obstacle gauntlet and start at the choice/help passages. Flip to
    // false to restore the full run. (Nothing in buildCourse is deleted, only bypassed.)
    private val DEBUG_SKIP_TO_CHOICES = false
    private val DEBUG_FINISH_ONLY = false            // full track unlocked
    private val TOLL_COST = 5                        // fixed cost you cover for a friend on the NEED detour
    private val TOLL_MARGIN = 3                      // early: main toll = your coins + this → not affordable alone
    private val TOLL_CAP = 30                        // …but capped, so later you CAN just pay it (no money wipe)
    @Volatile var tollActive = false                 // dialog visible
    @Volatile var tollAmount = 0
    @Volatile var canPayToll = false

    // The HILL boulder: bigger boulder ⇒ more pushers needed.
    private var hillTile: TileInstance? = null
    private val HILL_BOULDER = "ball4"              // biggest boulder
    private val HILL_HELPERS_NEEDED = 3            // pushers required (scales with size)
    private var boulderX = 0f; private var boulderY = 0f; private var boulderZ = 0f
    private var boulderScale = 1f
    private var boulderRadiusW = 2.0f              // world radius of the placed boulder
    private var boulderDestroyed = false
    private var boulderMaxY = 0f                    // peak height reached (to detect cresting)
    private var boulderSpin = 0f                    // accumulated roll angle (only when moving)
    private var heelZ = 0f                          // world z where the incline starts
    private var hutExplodeZ = 0f                    // world z (in the hut) where it blows up
    private var refuseTimer = 0f                    // >0 while the player head-shakes ("too heavy")
    private val BOULDER_WORLD_R = 2.0f              // placed boulder radius in world units
    private val BOULDER_SOLO_SPEED = 5f            // shove speed on the flat (player alone)
    private val BOULDER_PUSH_SPEED = 5f            // shove speed up the incline (group)
    private val BOULDER_ROLL_SPEED = 16f          // free-roll down into the hut
    private val PUSH_GAP = 0.35f                    // how close pushers stand to the boulder
    private val HILL_STUCK_MARGIN = 5f            // boulder jams this far before the incline
    @Volatile var hillActive = false               // boulder dialog visible
    @Volatile var hillNeeded = 0
    @Volatile var hillHave = 0

    // The RING fight: a cluster of opponents; match their number or be outnumbered → die.
    private var ringTile: TileInstance? = null
    private val OPPONENT_COUNT = 3
    private var ringFoes = 3                          // this ring's mob size (varies 3..5 per instance)
    private var ringStopZ = 0f                      // where the player halts before the mob
    private var fightDone = false
    private var fightTimer = 0f
    private var fightWon = false
    private var deathsApplied = false
    private var playerDying = false
    private var playerDeathClip = 0
    private val FIGHT_CLASH = 1.6f                  // seconds of brawling before it's decided
    private var maxDeathDur = 2f                    // longest death clip — wait for it fully
    private var hazDeathDur = 1.5f                  // the boulder-hit knockdown clip length
    private val ENEMY_COL = floatArrayOf(0.75f, 0.28f, 0.28f)
    @Volatile var fightActive = false              // fight dialog visible
    @Volatile var fightFoes = 0
    @Volatile var fightAllies = 0                  // player + arrived ring allies

    // The GAP human-tower bridge: stack climbers, topple them across; too short → they fall.
    private var gapTile: TileInstance? = null
    private var gapNearZ = 0f; private var gapFarZ = 0f; private var gapWidth = 0f
    private var gapEdgeY = 0f
    private var gapDone = false                     // bridged & crossed
    private var toppleT = 0f                        // 0..1 topple animation
    private var gapFallT = 0f                       // fall animation on a failed topple
    private val BRIDGE_PER_CLIMBER = 2.8f          // reach each toppled climber adds (~4 for this gap)
    private val STACK_H = 1.55f                     // vertical spacing while stacked
    private val TOPPLE_TIME = 1.2f
    private val GAP_FALL_TIME = 0.9f
    private val CLIMB_SPEED = 2.6f                  // how fast a climber rises up the tower
    private val GAP_FALL_SPEED = 10f
    @Volatile var gapActive = false                // gap dialog visible
    @Volatile var gapClimbers = 0
    private var climbBaseX = 0f; private var climbBaseZ = 0f; private var climbBaseY = 0f   // where climbers stack

    // The WALL: can't jump it — climbers form a ladder against it, then the player climbs over.
    private var wallTile: TileInstance? = null
    private var wallNearZ = 0f; private var wallFarZ = 0f; private var wallTopY = 0f; private var wallBaseY = 0f
    private var wallDone = false
    private var wallClimbT = 0f                     // 0..1 player-climb-over animation
    private val WALL_CLIMB_TIME = 1.6f
    @Volatile var wallActive = false
    @Volatile var wallClimbers = 0
    @Volatile var wallNeeded = 0

    // CHOICES STAGE: a fork with an incoming call/message. Lean LEFT to accept, RIGHT to
    // decline (no stopping). Decline → money + a speed boost. Accept → slow + the anim for 3s.
    private enum class ChoiceType { CALL, MESSAGE, HELP, VISIT, NEED }
    private class ChoiceFork(val tile: TileInstance, val type: ChoiceType, var name: String = "", var decided: Boolean = false) {
        // Branch forks (VISIT/NEED) each get their own teleport detour:
        var returnZ = 0f; var detourStartZ = 0f; var detourEndZ = 0f
        var popupSounded = false            // the popup's one-shot sound has played
    }
    private val choiceForks = ArrayList<ChoiceFork>()
    private var choiceIdx = 0
    private val seenNames = LinkedHashSet<String>()   // contacts who've already appeared to help
    private var choiceAcceptTimer = 0f                // >0 while playing the accept animation
    private var choiceAcceptType = ChoiceType.CALL
    private var speedBoostTimer = 0f                  // >0 during a decline speed boost
    private val CHOICE_ACCEPT_TIME = 3f
    private val CHOICE_BOOST_TIME = 5f               // decline speed boost (2s longer than before)
    @Volatile var choiceActive = false               // approach popup visible
    @Volatile var choiceIcon = "phone"               // svg name: phone / message / phonebook
    @Volatile var choiceText = ""                    // "Name is calling", etc.
    @Volatile var choiceRightAccepts = false         // VISIT accepts on the RIGHT (arrows flip)
    @Volatile var textingActive = false              // show message bubbles while texting

    // HELP choice outcome — a helper waves from the basehelp column; on accept the player
    // stops, watches them climb down, and they shake hands.
    private var basehelpTile: TileInstance? = null
    private var columnHelper: Helper? = null
    private var helpAccepted = false
    private var helpGreetDone = false
    private var greetTimer = 0f
    private val COL_X = 4.5f                          // world x of the basehelp column
    private val COL_REL_Z = -8.6f                     // column z offset from the tile zRef (flip)
    private val COL_TOP_Y = 6.6f                      // world y the helper waves from
    private val CLIMB_DOWN_T = 2.4f
    private val GREET_WALK_T = 0.9f                 // walk over from the column after climbing down
    private val SHAKE_T = 3f
    // Branch excursions (forkoutr): accept → teleport to a parallel DETOUR track with the
    // caller, run together (VISIT) or pay their toll (NEED), then teleport back (forkoutl).
    private var visitCompanion: Helper? = null
    private var onDetour = false
    private var activeReturnZ = 0f; private var activeDetourEndZ = 0f
    private var needExcursion = false; private var needPaid = false; private var needTollZ = 0f
    private var needTollTile: TileInstance? = null   // the toll on the NEED detour (own barrier/paid state)
    private var needPayZip = -1f                      // 0..1 coin flying player→requester (no popup)
    private var companionVy = 0f; private var companionGrounded = true  // companion hops gaps
    // Two "competitors" running on straight tracks behind the surround buildings, kept slightly ahead.
    private var competitors = emptyList<Helper>()
    private val COMP_X = 11f                          // closer to the base track (still clear of it / the surround)
    private val COMP_Y = -11f                         // a little lower still
    private val COMP_AHEAD = floatArrayOf(30f, 40f)   // starting lead of each
    private val COMP_SCALE = 1.3f                     // slightly larger than the player
    // Dynamic race: each competitor has its OWN absolute z, running independently.
    private var compZ = floatArrayOf(0f, 0f)
    private val compFactor = floatArrayOf(0.93f, 0.88f)  // per-runner pace (independent, a touch < you)
    private var compDeathLead = floatArrayOf(0f, 0f)
    private var compFrozenPrev = false
    private var compPrevCharZ = 0f; private var compPrevOnDetour = false
    private var backdropRunners = emptyList<Helper>()  // the crowd on the far lanes during the pan-out
    private val LEAD_MIN = 20f                        // never fully shaken (they stay in view ahead)
    private val LEAD_MAX = 62f                        // never run off-screen ahead

    // Consequences bookkeeping: everyone you helped/connected with banks as a friend who'll come
    // help you in the reckoning; every refusal is a lost friend.
    private val friendsBanked = ArrayList<String>()
    private var refusals = 0
    private var reckoningFromZ = -1e9f                // z at which the RECKONING stage begins
    private val PAY_PER_HELPER = 10                   // hire a stranger when nobody will help
    @Volatile var helpDenied = false                 // pressed Help but nobody's coming → offer to pay
    // Friends cover a whole obstacle at a time. If you refused help a lot (≥4), your friends only
    // stretch to HALF the reckoning obstacles; the rest, nobody comes and you pay.
    private var reckoningEntered = false
    private var helpCredits = 0                       // reckoning obstacles friends will still cover
    private var obstacleInReckoning = false           // active obstacle is a reckoning one needing help
    private var obstacleFriendHelped = true           // …and a friend showed up for it (else you pay)
    @Volatile var noFriendsMsg = ""                   // shown when nobody will help this one
    private var noFriendIdx = 0                        // cycle the messages so none repeats back-to-back
    private val NO_FRIEND_MSGS = listOf(
        "Nobody is willing to help", "All friends are busy", "Nobody responded", "You're alone in this one")
    private fun inReckoning() = charZ <= reckoningFromZ

    /** How far down the course the player has got (the course runs toward -z). */
    private fun noteProgress() { if (-charZ > furthestZ) furthestZ = -charZ }
    /** Decide, as an obstacle arms in the reckoning, whether a banked friend covers it. */
    private fun decideObstacleHelp(t: TileInstance) {
        helpDenied = false
        obstacleInReckoning = t.zNear <= reckoningFromZ && t.piece != "bouderhill"
        if (!obstacleInReckoning) { obstacleFriendHelped = true; noFriendsMsg = ""; return }
        if (!reckoningEntered) {
            reckoningEntered = true
            val n = tiles.count { isObstacle(it.piece) && it.piece != "bouderhill" && it.zNear <= reckoningFromZ }
            helpCredits = if (refusals >= 4) n / 2 else n     // refused a lot → friends only cover half
        }
        if (helpCredits > 0) { helpCredits--; obstacleFriendHelped = true; noFriendsMsg = "" }
        else { obstacleFriendHelped = false; noFriendsMsg = NO_FRIEND_MSGS[noFriendIdx % NO_FRIEND_MSGS.size]; noFriendIdx++ }
    }
    /** First "Help" tap when nobody's coming: reveal the pay-a-stranger option instead of
     *  summoning. Returns true → the summon should NOT happen yet. */
    private fun helpBlocked(): Boolean {
        if (!obstacleFriendHelped && !helpDenied) { helpDenied = true; return true }
        return false
    }
    /** A summoned helper: free if a friend's covering this obstacle, else pay a stranger. */
    private fun spendHelperCredit() {
        if (obstacleFriendHelped) return
        coinCount = (coinCount - PAY_PER_HELPER).coerceAtLeast(0)
    }
    // On the NEED detour the toll belongs to the friend you're helping; elsewhere it's the main toll.
    private fun onNeedToll() = onDetour && needExcursion
    private fun activeTollTile() = if (onNeedToll()) needTollTile else tollTile
    private fun activeTollPaid() = if (onNeedToll()) needPaid else tollPaid

    // BOULDER HILL: small rocks fall from above and roll at you; get hit → back to the bottom.
    private var bouderTile: TileInstance? = null
    private var bouderEntranceZ = 0f
    // Multi-instance obstacles: only the nearest un-done one is "live" at a time. Its single
    // refs (tollTile/hillTile/…) point at the active instance; each tile carries its own done.
    private var activeObstacle: TileInstance? = null
    // Any of these piece names (gap/wall come in several sizes → match by prefix).
    private fun isObstacle(p: String) = p == "sptoll" || p == "hill" || p == "spring01blend" ||
        p == "bouderhill" || p.startsWith("spgap") || p.startsWith("spwall")
    private val ACTIVATE_DIST = 120f                 // arm an obstacle once it's this close ahead
    private var bouderCrestZ = 0f                    // top of the ramp — rocks spawn here, roll down
    private class HazBoulder(var x: Float, var y: Float, var z: Float, var vy: Float, var landed: Boolean = false, var spin: Float = 0f)
    private val hazBoulders = ArrayList<HazBoulder>()
    private var hazTimer = 0f
    private var hitTimer = 0f                       // impact-pause before respawning
    private val HAZ_HIT_TIME = 1.1f
    private val HAZ_INTERVAL = 0.7f                 // seconds between rocks (more of them)
    private val HAZ_SPAWN_AHEAD = 46f              // spawn well ahead so there's time to react
    private val HAZ_ROLL_SPEED = 9f               // speed rolling down toward the player
    private val HAZ_R = 0.55f                       // small-boulder radius (world)
    private val HAZ_HIT_R = 0.35f                   // collision half-box (smaller than the rock)
    private val HAZ_LANES = floatArrayOf(-1.9f, 0f, 1.9f)  // fixed fall lanes (clear space between)

    private class Helper(
        var x: Float, var z: Float, var y: Float,
        var targetX: Float, var targetZ: Float,
        val color: FloatArray, val name: String,
        var arrived: Boolean = false,
        var facing: Float = 0f,   // yaw (rad), 0 = forward (−Z), matches player heading
        val carriesCoin: Boolean = false,  // toll helper hauls a coin, then zips it to the player
        var zip: Float = -1f,     // -1 = not zipping; 0..1 = coin flying helper→player
        val isBooth: Boolean = false,  // static toll-booth attendant (always idle, no label)
        val pusher: Boolean = false,   // hill helper come to shove the boulder
        var waving: Boolean = false,   // waving the player off after the boulder's gone
        val opponent: Boolean = false, // ring enemy (fight-idle, no contact label)
        val fighter: Boolean = false,  // ring ally summoned to fight
        var dying: Boolean = false,    // playing a death animation
        var deathClip: Int = 0,        // which of the 4 death variants
        val climber: Boolean = false,  // gap tower climber
        var stackIndex: Int = 0,       // position in the tower (0 = base)
        var climbing: Boolean = false, // rising into the tower
        var pitch: Float = 0f,         // forward tilt (rad); 90° = lying flat (toppled bridge)
        val greeter: Boolean = false,  // the friend you stop to help (shakes hands)
        val companion: Boolean = false, // the friend running alongside on a VISIT
        val scale: Float = 1f,         // per-helper size multiplier (booth attendant is bigger)
    )
    private val helpers = ArrayList<Helper>()
    private var boothNpc: Helper? = null    // idle attendant standing in the toll booth
    private val COIN_ZIP_TIME = 0.45f       // seconds for the coin to fly to the player
    @Volatile var helperNames: List<String> = RUNNER_FALLBACK_NAMES
    private var helperNameIdx = 0
    private var helperColorIdx = 0
    private val HELPER_SPEED = 12f
    private val HELPER_COLORS = arrayOf(
        floatArrayOf(0.35f, 0.55f, 0.95f), floatArrayOf(0.40f, 0.80f, 0.45f),
        floatArrayOf(0.95f, 0.62f, 0.25f), floatArrayOf(0.78f, 0.45f, 0.88f),
    )

    /** A head-tag position published to the Compose overlay (screen fractions, y-down). */
    class ScreenLabel(val text: String, val xFrac: Float, val yFrac: Float)
    @Volatile var labels: List<ScreenLabel> = emptyList()

    fun payToll() = pending.add(::doPayToll)
    private fun doPayToll() {
        if (runState != RunState.TOLL_PROMPT || !canPayToll) return
        coinCount = (coinCount - tollAmount).coerceAtLeast(0)
        resolveToll()
    }

    fun requestHelp() = pending.add(::doRequestHelp)
    private fun doRequestHelp() {
        if (runState != RunState.TOLL_PROMPT) return
        if (helpBlocked()) return                        // reckoning, no friend → reveal "Pay strangers"
        tollActive = false
        // Reckoning friend covers it free; reckoning-no-friend pays a stranger; early toll = ⅓.
        if (obstacleInReckoning) { if (!obstacleFriendHelped) coinCount = (coinCount - PAY_PER_HELPER).coerceAtLeast(0) }
        else coinCount = (coinCount - coinCount / 3).coerceAtLeast(0)
        runState = RunState.TOLL_HELP
        // The helper runs up FROM BEHIND along the same track (not levitating in from the
        // side), hauling a coin, and stops beside the player — on whichever side keeps it ON
        // the track (so it doesn't float off when the player hugs an edge).
        val side = if (charX > 0f) -1.6f else 1.6f
        val sx = charX.coerceIn(-2.5f, 2.5f); val sz = charZ + 24f
        val tx = (charX + side).coerceIn(-2.5f, 2.5f); val tz = charZ
        val name = if (obstacleFriendHelped) nextHelperName() else ""   // a paid stranger has no name tag
        val color = HELPER_COLORS[helperColorIdx % HELPER_COLORS.size]; helperColorIdx++
        helpers.add(Helper(sx, sz, groundAt(sx, sz) ?: charY, tx, tz, color, name, carriesCoin = true))
    }

    private fun resolveToll() {
        if (onNeedToll()) needPaid = true else tollPaid = true
        tollActive = false
        runState = RunState.RUNNING
    }

    private fun arrivedPushers(): Int = helpers.count { it.pusher && it.arrived }

    /** Player pressed PUSH at the boulder: shove it if enough pushers have gathered,
     *  otherwise the player shakes their head (too heavy alone). */
    // Where the pushers stand: x offset + an extra "row" set-back so a crowd stacks up
    // behind. Each one's z hugs the boulder's curved surface at its x (so they touch, not
    // overlap, and not float off).
    private val PUSHER_SLOTS = arrayOf(
        floatArrayOf(-1.3f, 0f), floatArrayOf(1.3f, 0f), floatArrayOf(0f, 1.1f),
        floatArrayOf(-1.3f, 1.1f), floatArrayOf(1.3f, 1.1f),   // extra rows for bigger boulders
    )

    fun pushBoulder() = pending.add(::doPushBoulder)
    private fun doPushBoulder() {
        if (runState != RunState.HILL_PROMPT) return
        if (arrivedPushers() >= hillNeeded) {
            runState = RunState.HILL_PUSH
            boulderMaxY = boulderY
            pushSfx++
        } else {
            refuseTimer = 1.3f                        // play the head-shake
            pushSfx++                                 // still heave at it — the boulder
                                                      // grunts even when it won't budge alone
        }
    }

    /** Summon another pusher (capped at what the boulder needs) — runs up from behind. */
    fun requestPush() = pending.add(::doRequestPush)
    private fun doRequestPush() {
        if (runState != RunState.HILL_PROMPT) return
        if (helpBlocked()) return
        val slot = helpers.count { it.pusher }
        if (slot >= hillNeeded) return               // only as many as the boulder requires
        val sx = charX; val sz = charZ + 24f
        val s = PUSHER_SLOTS[slot.coerceAtMost(PUSHER_SLOTS.size - 1)]
        val tx = s[0]; val tz = boulderZ + boulderRadiusW + PUSH_GAP + s[1]
        val name = if (obstacleFriendHelped) nextHelperName() else ""   // a paid stranger has no name tag
        val color = HELPER_COLORS[helperColorIdx % HELPER_COLORS.size]; helperColorIdx++
        helpers.add(Helper(sx, sz, groundAt(sx, sz) ?: charY, tx, tz, color, name, pusher = true))
        spendHelperCredit()
        hillHave = arrivedPushers()
    }

    /** Pick the next contact name for a summoned helper and remember it (choices later name a
     *  caller from the people who've already appeared to help). */
    private fun nextHelperName(): String {
        val n = if (helperNames.isNotEmpty()) helperNames[helperNameIdx % helperNames.size] else "Friend"
        helperNameIdx++; seenNames.add(n); return n
    }

    private fun arrivedFighters(): Int = helpers.count { it.fighter && it.arrived }
    private fun aliveFoes(): Int = helpers.count { it.opponent && !it.dying }

    /** Start the brawl. Allies = the player + everyone who arrived; if that MATCHES or beats
     *  the opponents nobody good dies (they win), otherwise the good guys are outnumbered. */
    fun startFight() = pending.add(::doStartFight)
    private fun doStartFight() {
        if (runState != RunState.FIGHT_PROMPT) return
        val allies = 1 + arrivedFighters()
        fightWon = allies >= aliveFoes()
        fightActive = false
        fightTimer = 0f
        deathsApplied = false
        playerDying = false
        runState = RunState.FIGHTING
        fightSfx++
    }

    /** Summon a fighter to your side — runs in from behind, lines up beside the player. */
    fun requestFighter() = pending.add(::doRequestFighter)
    private fun doRequestFighter() {
        if (runState != RunState.FIGHT_PROMPT) return
        if (helpBlocked()) return
        val slot = helpers.count { it.fighter }
        if (slot >= ringFoes) return                 // no more than enough to match this mob
        val sx = charX; val sz = charZ + 15f         // just behind the player, on the arena floor
        val tx = charX + (if (slot % 2 == 0) 1 else -1) * (1.4f + 0.5f * (slot / 2))
        val tz = charZ + 0.4f * (slot / 2)
        val name = if (obstacleFriendHelped) nextHelperName() else ""   // a paid stranger has no name tag
        val color = HELPER_COLORS[helperColorIdx % HELPER_COLORS.size]; helperColorIdx++
        helpers.add(Helper(sx, sz, groundAt(sx, sz) ?: charY, tx, tz, color, name, fighter = true))
        spendHelperCredit()
        fightAllies = 1 + arrivedFighters()
    }

    private fun climberCount(): Int = helpers.count { it.climber }
    private fun stackedClimbers(): Int = helpers.count { it.climber && it.arrived }

    /** Summon a climber — runs in from behind, then climbs onto the tower / ladder. Shared by
     *  the gap (tower to topple) and the wall (ladder to climb). */
    fun requestClimber() = pending.add(::doRequestClimber)
    private fun doRequestClimber() {
        if (runState != RunState.GAP_PROMPT && runState != RunState.WALL_PROMPT) return
        if (helpBlocked()) return
        val n = climberCount()
        val sx = 0f; val sz = charZ + 22f
        val name = if (obstacleFriendHelped) nextHelperName() else ""   // a paid stranger has no name tag
        val color = HELPER_COLORS[helperColorIdx % HELPER_COLORS.size]; helperColorIdx++
        helpers.add(Helper(sx, sz, groundAt(sx, sz) ?: climbBaseY, climbBaseX, climbBaseZ,
            color, name, climber = true, stackIndex = n))
        spendHelperCredit()
        gapClimbers = climberCount(); wallClimbers = climberCount()
    }

    /** Raise arrived climbers to their level in the stack (shared gap/wall). */
    private fun stackClimbers(dt: Float) {
        for (h in helpers) if (h.climber && h.arrived) {
            val target = climbBaseY + h.stackIndex * STACK_H
            h.x = climbBaseX; h.z = climbBaseZ; h.facing = 0f
            if (h.y < target - 0.05f) { h.climbing = true; h.y = (h.y + CLIMB_SPEED * dt).coerceAtMost(target) }
            else { h.climbing = false; h.y = target }
        }
    }

    /** Player pressed CLIMB at the wall: go over if the ladder's tall enough, else fall back. */
    fun proceedWall() = pending.add(::doProceedWall)
    private fun doProceedWall() {
        if (runState != RunState.WALL_PROMPT) return
        wallActive = false
        val ladderH = climberCount() * STACK_H
        if (ladderH >= wallTopY - 1f && climberCount() > 0) {
            runState = RunState.WALL_CLIMB; wallClimbT = 0f
        } else {
            refuseTimer = 1.1f                        // shake head — ladder's too short
        }
    }

    /** Commit: topple the tower across the gap. With no climbers the player just tries (and
     *  falls). Resolution happens in updateGap once the animation finishes. */
    fun proceedGap() = pending.add(::doProceedGap)
    private fun doProceedGap() {
        if (runState != RunState.GAP_PROMPT) return
        gapActive = false
        if (climberCount() == 0) {
            // Try to jump it alone → launch off the edge into the void and fall.
            runState = RunState.RUNNING
            charZ = gapNearZ - 0.2f; onGround = false; vSpeed = JUMP_VEL
        } else {
            runState = RunState.GAP_TOPPLE
            toppleT = 0f
        }
    }

    private fun assetExists(path: String): Boolean =
        try { context.assets.open(path).close(); true } catch (e: Exception) { false }

    // Track pieces now live in grouped subfolders; resolve a bare piece name to its file.
    private val TILE_DIRS = listOf("regular", "obstacles", "forks_branch", "forks_choice", "assets", "")
    private fun tilePath(name: String): String? {
        for (d in TILE_DIRS) {
            val p = if (d.isEmpty()) "models/stickmancourse/$name.obj"
                    else "models/stickmancourse/$d/$name.obj"
            if (assetExists(p)) return p
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Sky = the bright horizon colour, so distant tiles fade seamlessly into it.
        GLES30.glClearColor(HORIZON[0], HORIZON[1], HORIZON[2], 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        val candidates = listOf(
            "models/stickman4.glb",
            "models/stickman3.glb", "models/stickman3.0.glb", "models/stickman.glb",
            "models/character.glb",
        )
        val path = candidates.firstOrNull { assetExists(it) } ?: "models/character.glb"
        model = GltfSkinnedModel.load(context.assets, path)
        runClip = model.clipIndex("run")
        idleClip = model.clipIndex("idle")
        jumpClip = model.clipIndex("jump")
        carryRunClip = model.clipIndex("carry_run")
        carryIdleClip = model.clipIndex("carry_idle")
        pushClip = model.clipIndex("push")
        refuseClip = model.clipIndex("refuse")
        waveClip = model.clipIndex("wave")
        fightIdleClip = model.clipIndex("idle_to_fight")
        boxingClip = model.clipIndex("boxing")
        climbClip = model.clipIndex("climb")
        phoneClip = model.clipIndex("phone")
        textingClip = model.clipIndex("texting")
        handshakeClip = model.clipIndex("handshake")
        climbDownClip = model.clipIndex("climb_down")
        helpWaveClip = model.clipIndex("help_wave")
        for (i in 0 until 4) deathClips[i] = model.clipIndex("death${i + 1}")
        maxDeathDur = (0 until 4).maxOf { model.clipDuration(deathClips[it]) }
        hazDeathDur = model.clipDuration(deathClips[1])
        boneBuffer = ByteBuffer.allocateDirect(model.jointCount * 16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()

        buildPrograms()
        buildBoneUbo()
        uploadCharacterMeshes()
        computeModelScale()
        buildTileLayout(40f, farZ)
        loadCoinModel()
        buildBoxMesh()
        loadBoulderModel()
        buildCoins(40f, farZ)
        buildProps(40f, farZ)

        // start the character on the first tile
        charX = 0f; charZ = tileStartZ - 12f; vSpeed = 0f; onGround = true
        charY = groundAt(charX, charZ) ?: 0f
        safeZ = charZ; safeY = charY
        camX = charX; camZ = charZ; camHeading = heading
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width; viewH = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / viewH
        Matrix.perspectiveM(proj, 0, 55f, aspect, 0.5f, 3000f)
    }

    private var lastFrameMs = 0L
    override fun onDrawFrame(gl: GL10?) {
        // Frame-rate cap (~33 fps) to cut sustained GPU/CPU load (heat + battery).
        val fMs = android.os.SystemClock.uptimeMillis()
        val since = fMs - lastFrameMs
        if (lastFrameMs != 0L && since in 0 until 30L) {
            try { Thread.sleep(30L - since) } catch (_: InterruptedException) {}
        }
        lastFrameMs = android.os.SystemClock.uptimeMillis()
        val now = System.nanoTime()
        if (lastTimeNs == 0L) lastTimeNs = now
        val dt = ((now - lastTimeNs) / 1_000_000_000.0).toFloat().coerceAtMost(1f / 24f)
        lastTimeNs = now
        // Paused: keep drawing the frozen frame, but don't advance time or simulate.
        if (!paused) {
            elapsed += dt
            update(dt)
        }

        updateHorizon()                   // shift the sky/fog colour by how far you've run
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        setupCamera()
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        drawTiles()
        drawCompetitorTracks()
        drawProps()
        drawCoins()
        drawBarrier()
        drawBoulder()
        drawHazBoulders()
        drawCharacter()
        drawHelpers()
        started = true                    // a real frame is on screen now
    }

    /** The small falling/rolling rocks on the boulder hill (reuse the boulder mesh, small). */
    private fun drawHazBoulders() {
        if (boulderVao == 0 || hazBoulders.isEmpty()) return
        GLES30.glUseProgram(flatProg)
        GLES30.glUniformMatrix4fv(fVP, 1, false, vp, 0)
        GLES30.glUniform3f(fColor, BOULDER_COL[0], BOULDER_COL[1], BOULDER_COL[2])
        GLES30.glBindVertexArray(boulderVao)
        val s = HAZ_R / boulderLocalR
        for (b in hazBoulders) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, b.x, b.y, b.z)
            Matrix.rotateM(modelMat, 0, b.spin, 1f, 0f, 0f)
            Matrix.scaleM(modelMat, 0, s, s, s)
            GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, boulderVerts, GLES30.GL_UNSIGNED_INT, 0)
        }
        GLES30.glBindVertexArray(0)
    }

    /** The hill boulder (until it's pushed into the hut and destroyed). */
    private fun drawBoulder() {
        if (boulderVao == 0 || boulderDestroyed || hillTile == null) return
        GLES30.glUseProgram(flatProg)
        GLES30.glUniformMatrix4fv(fVP, 1, false, vp, 0)
        GLES30.glUniform3f(fColor, BOULDER_COL[0], BOULDER_COL[1], BOULDER_COL[2])
        GLES30.glBindVertexArray(boulderVao)
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, boulderX, boulderY, boulderZ)
        Matrix.rotateM(modelMat, 0, boulderSpin, 1f, 0f, 0f)     // only rolls while moving
        Matrix.scaleM(modelMat, 0, boulderScale, boulderScale, boulderScale)
        GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, boulderVerts, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    /** The toll.png decal on the booth's left sign, facing the approaching runner. */
    private fun drawSign() {
        if (tollTex == 0 || signVao == 0) return
        val tt = tollTile ?: return
        val cz = (tt.zNear + tt.zFar) * 0.5f + SIGN_Z_OFF
        GLES30.glUseProgram(texProg)
        GLES30.glUniformMatrix4fv(tVP, 1, false, vp, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tollTex)
        GLES30.glUniform1i(tSampler, 0)
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, SIGN_X, SIGN_Y, cz)
        Matrix.scaleM(modelMat, 0, SIGN_SIZE, SIGN_SIZE, 1f)
        GLES30.glUniformMatrix4fv(tModel, 1, false, modelMat, 0)
        GLES30.glBindVertexArray(signVao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, signVerts, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /** Toll boom: a bar spanning the track just past the booth, hinged on the left stub.
     *  Rotates from horizontal (blocking) up to ~82° as [barrierLift] goes 0→1. */
    private fun drawBarrier() {
        if (boxVao == 0) return
        val tt = activeTollTile() ?: return
        // Seat the boom between the two prepared columns (sptoll is flipped, so world
        // z = zRef − SCALE_Z·localz).
        val bz = tt.zRef - SCALE_Z * BARRIER_LOCAL_Z
        val gy = (groundAt(0f, bz) ?: 0f) + BARRIER_Y
        GLES30.glUseProgram(flatProg)
        GLES30.glUniformMatrix4fv(fVP, 1, false, vp, 0)
        GLES30.glUniform3f(fColor, BARRIER_COL[0], BARRIER_COL[1], BARRIER_COL[2])
        GLES30.glBindVertexArray(boxVao)

        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, BARRIER_PIVOT_X, gy, bz)   // hinge point
        Matrix.rotateM(modelMat, 0, 82f * barrierLift, 0f, 0f, 1f) // lift about Z
        Matrix.scaleM(modelMat, 0, BARRIER_SPAN, 0.22f, 0.22f)     // extends +X from hinge
        GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, boxVerts, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    /** Load a prop OBJ once into colored sub-meshes (city props keep their .mtl colours;
     *  the material-less Stickmancourse props get [fallback]). Records the local min-Y so
     *  the prop can be grounded (base at y=0) at draw. */
    private fun loadProp(name: String, objPath: String, mtlPath: String?, fallback: FloatArray) {
        if (propMeshes.containsKey(name) || !assetExists(objPath)) return
        val groups = ObjLoader.load(context.assets, objPath, mtlPath)
        val parts = ArrayList<PropPart>()
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (g in groups) {
            val pos = g.verts
            if (pos.isEmpty()) continue
            var k = 1; while (k < pos.size) { if (pos[k] < minY) minY = pos[k]; if (pos[k] > maxY) maxY = pos[k]; k += 3 }
            val color = if (mtlPath != null) floatArrayOf(g.r, g.g, g.b) else fallback
            parts.add(PropPart(makeVao(pos, computeFlatNormals(pos)), pos.size / 3, color))
        }
        if (parts.isEmpty()) return
        propMeshes[name] = PropMesh(parts, if (minY == Float.MAX_VALUE) 0f else minY, if (maxY == -Float.MAX_VALUE) 0f else maxY)
    }

    /** Place scenery: a directional in the middle of each fork, the big sign over each
     *  fork entrance, street lamps down both sides, and buildings as distant scenery. */
    private fun buildProps(near: Float, far: Float) {
        props.clear()
        loadProp("sign", "models/stickmancourse/assets/sign.obj", null, floatArrayOf(0.93f, 0.93f, 0.96f))
        loadProp("directions", "models/stickmancourse/assets/directions.obj", null, floatArrayOf(0.28f, 0.62f, 0.38f))
        loadProp("directionsl", "models/stickmancourse/assets/directionsl.obj", null, floatArrayOf(0.28f, 0.62f, 0.38f))
        loadProp("directionsr", "models/stickmancourse/assets/directionsr.obj", null, floatArrayOf(0.28f, 0.62f, 0.38f))
        loadProp("lampon", "models/lampon.obj", "models/lampon.mtl", floatArrayOf(0.80f, 0.78f, 0.62f))
        loadProp("surround", "models/stickmancourse/regular/surround.obj", null, floatArrayOf(0.58f, 0.56f, 0.50f))

        // Directional in the middle of each fork (between the arms) + the big sign over its
        // entrance — both with the same scale/flip the track tiles use.
        fun groundY(name: String, sy: Float) = -(propMeshes[name]?.localMinY ?: 0f) * sy
        fun topAt(name: String, sy: Float, top: Float) = top - (propMeshes[name]?.localMaxY ?: 0f) * sy

        // Big sign over each fork entrance: wider so its support poles straddle OUTSIDE
        // the road, NOT flipped (it was back-to-front), and sat a little lower.
        val signSx = 2.3f; val signSy = 1.4f
        // Directional in the middle of each fork: sunk so its base sits under the track but
        // its upper arrow part rides clearly overhead.
        val ps = 1.0f
        // Directionals go ONLY on the external (excursion) return fork — the forkoutl where you
        // leave the visitor. None on the main track. forkoutl bends LEFT; directionsr reads as
        // the left arrow once the track's flip is applied (directionsl pointed the wrong way).
        for (t in tiles) {
            if (t.piece != "forkoutl") continue
            val cz = (t.zNear + t.zFar) * 0.5f
            if (propMeshes.containsKey("directionsr")) props.add(PropInst("directionsr", 0f, cz, topAt("directionsr", ps, 11f), 0f, ps, ps, ps, FLIP_Z))
        }
        // Street lamps standing ON the track's raised edges (rim runs local x 0.875..1.0 →
        // world ~±2.85, its top at world y = SCALE_Y*0.170 - TILE_TOP), arm yawed inward.
        if (propMeshes.containsKey("lampon")) {
            val rimTop = SCALE_Y * 0.170f - TILE_TOP
            val ly = rimTop + groundY("lampon", 0.9f)
            var lz = near - 15f
            var left = true
            while (lz > far + 20f) {
                // One lamp per position, taking turns left/right down the track — but NOT on
                // the special/obstacle pieces (they have their own scenery).
                if (tileAt(lz)?.piece !in SPECIAL_PIECES) {
                    if (left) props.add(PropInst("lampon", -2.85f, lz, ly, 90f, 0.9f, 0.9f, 0.9f, false))
                    else props.add(PropInst("lampon", 2.85f, lz, ly, -90f, 0.9f, 0.9f, 0.9f, false))
                    left = !left
                }
                lz -= 44f
            }
        }
        // Surrounding scenery flanking the MAIN track only (skipped on the excursion detours at
        // draw time). Placed with the same scale/flip as the tiles so it lines up, and tiled
        // along its own z-length so copies meet seamlessly.
        if (propMeshes.containsKey("surround")) {
            val span = SURROUND_RAW_Z * SCALE_Z             // one piece's game-space length
            var sz = near + span
            while (sz > far - span) {
                props.add(PropInst("surround", 0f, sz, -TILE_TOP, 0f, SCALE_X, SCALE_Y, SCALE_Z, FLIP_Z))
                sz -= span
            }
        }
    }
    private val SURROUND_RAW_Z = 371.18f                    // surround.obj z-length (max−min), for seamless tiling

    // Obstacle/special pieces — kept clear of street lamps.
    private val SPECIAL_PIECES = setOf(
        "sptoll", "hill", "spring01blend", "spgap4", "spwall4", "bouderhill",
    )

    private fun drawProps() {
        if (props.isEmpty()) return
        GLES30.glUseProgram(flatProg)
        GLES30.glUniformMatrix4fv(fVP, 1, false, vp, 0)
        for (p in props) {
            if (p.mesh == "surround") {
                if (onDetour) continue                       // surround is for the MAIN track only
                // it's a long piece — cull with a wide window so its length isn't clipped
                if (p.z > charZ + 320f || p.z < charZ - 700f) continue
            } else if (p.z > charZ + 50f || p.z < charZ - 340f) continue
            val mesh = propMeshes[p.mesh] ?: continue
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, p.x, p.yOff, p.z)
            if (p.yawDeg != 0f) Matrix.rotateM(modelMat, 0, p.yawDeg, 0f, 1f, 0f)
            if (p.flipZ) Matrix.rotateM(modelMat, 0, 180f, 0f, 1f, 0f)
            Matrix.scaleM(modelMat, 0, p.sx, p.sy, p.sz)
            GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
            for (part in mesh.parts) {
                GLES30.glUniform3f(fColor, part.color[0], part.color[1], part.color[2])
                GLES30.glBindVertexArray(part.vao)
                GLES30.glDrawElements(GLES30.GL_TRIANGLES, part.count, GLES30.GL_UNSIGNED_INT, 0)
            }
        }
        GLES30.glBindVertexArray(0)
    }

    private fun loadCoinModel() {
        val path = "models/stickmancourse/coin.obj"
        if (!assetExists(path)) return
        val posList = ArrayList<Float>()
        for (g in ObjLoader.load(context.assets, path)) for (v in g.verts) posList.add(v)
        if (posList.isEmpty()) return
        val pos = posList.toFloatArray()
        coinVao = makeVao(pos, computeFlatNormals(pos))
        coinVerts = pos.size / 3
    }

    /** A unit box spanning x∈[0,1], y,z∈[-.5,.5] — reused (scaled) for the toll boom. */
    private fun buildBoxMesh() {
        val v = floatArrayOf(
            0f, -.5f, -.5f, 1f, -.5f, -.5f, 1f, .5f, -.5f, 0f, .5f, -.5f,   // back
            0f, -.5f, .5f, 1f, -.5f, .5f, 1f, .5f, .5f, 0f, .5f, .5f)        // front
        val f = intArrayOf(
            0,1,2, 0,2,3,  4,6,5, 4,7,6,  0,4,5, 0,5,1,
            3,2,6, 3,6,7,  0,3,7, 0,7,4,  1,5,6, 1,6,2)
        val pos = FloatArray(f.size * 3)
        for (i in f.indices) { pos[i*3] = v[f[i]*3]; pos[i*3+1] = v[f[i]*3+1]; pos[i*3+2] = v[f[i]*3+2] }
        boxVao = makeVao(pos, computeFlatNormals(pos))
        boxVerts = pos.size / 3
    }

    /** Load the hill boulder mesh (ball4) + record its authored radius for scaling. */
    private fun loadBoulderModel() {
        val path = tilePath(HILL_BOULDER) ?: return
        val posList = ArrayList<Float>()
        for (g in ObjLoader.load(context.assets, path)) for (v in g.verts) posList.add(v)
        if (posList.isEmpty()) return
        val pos = posList.toFloatArray()
        var maxR = 0f
        var k = 0
        while (k + 3 <= pos.size) {
            val r = kotlin.math.sqrt(pos[k] * pos[k] + pos[k + 1] * pos[k + 1] + pos[k + 2] * pos[k + 2])
            if (r > maxR) maxR = r; k += 3
        }
        boulderLocalR = maxR.coerceAtLeast(0.01f)
        boulderVao = makeVao(pos, computeFlatNormals(pos))
        boulderVerts = pos.size / 3
    }

    /** Load toll.png into a GL texture (once). Leaves tollTex=0 if the asset is missing. */
    private fun loadTollTexture() {
        val path = "textures/toll.png"
        if (!assetExists(path)) return
        val bmp = try { BitmapFactory.decodeStream(context.assets.open(path)) } catch (e: Exception) { null } ?: return
        val ids = IntArray(1); GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        bmp.recycle()
        tollTex = ids[0]

        // Square quad in the X-Y plane (radius .5) facing +Z (toward the approaching runner).
        val pos = floatArrayOf(-.5f,-.5f,0f,  .5f,-.5f,0f,  .5f,.5f,0f,   -.5f,-.5f,0f,  .5f,.5f,0f,  -.5f,.5f,0f)
        val uv  = floatArrayOf( 0f, 1f,       1f, 1f,       1f, 0f,        0f, 1f,       1f, 0f,       0f, 0f)
        val vao = IntArray(1); GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glBindVertexArray(vao[0])
        attrib(0, pos, 3); attrib(1, uv, 2)
        elementBuffer(IntArray(pos.size / 3) { it })
        GLES30.glBindVertexArray(0)
        signVao = vao[0]; signVerts = pos.size / 3
    }

    /** Occasionally float a short row of coins, spread across the track (centre + sides),
     *  wherever there's solid ground. Sparse so they're a reward, not a carpet. */
    private fun buildCoins(near: Float, far: Float) {
        coins.clear()
        val lanes = floatArrayOf(0f, -2.2f, 2.2f, -1.1f, 1.1f)
        var z = near - 12f
        var i = 0
        while (z > far) {
            val ox = lanes[i % lanes.size]
            val g = groundAt(ox, z)
            if (g != null) coins.add(Coin(ox, g + COIN_HOVER, z))
            z -= 28f; i++
        }
        // A trail of coins down the DECLINE lane of each choice fork, to tempt you away from
        // accepting (call/message/help: decline = RIGHT; visit/need: decline = LEFT).
        for (cf in choiceForks) {
            val declineX = if (cf.type == ChoiceType.VISIT || cf.type == ChoiceType.NEED) -2.2f else 2.2f
            for (k in 0 until 5) {
                val cz2 = cf.tile.zNear - 2f - k * 4f
                val g = groundAt(declineX, cz2) ?: continue
                coins.add(Coin(declineX, g + COIN_HOVER, cz2))
            }
        }
    }

    private fun drawCoins() {
        if (coinVao == 0) return
        GLES30.glUseProgram(flatProg)
        GLES30.glUniformMatrix4fv(fVP, 1, false, vp, 0)
        GLES30.glUniform3f(fColor, COIN_COL[0], COIN_COL[1], COIN_COL[2])
        GLES30.glBindVertexArray(coinVao)
        val spin = (elapsed * 150f) % 360f
        for (c in coins) {
            if (c.taken) continue
            if (c.z > charZ + 8f || c.z < charZ - 200f) continue
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, c.x, c.y, c.z)
            Matrix.rotateM(modelMat, 0, spin, 0f, 1f, 0f)
            Matrix.scaleM(modelMat, 0, COIN_SCALE, COIN_SCALE, COIN_SCALE)
            GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, coinVerts, GLES30.GL_UNSIGNED_INT, 0)
        }
        GLES30.glBindVertexArray(0)
    }

    // ── Update ─────────────────────────────────────────────────────────────────
    //  The character runs along a HEADING that follows the road: each frame the
    //  heading eases toward the direction (within a fan ahead) where the track
    //  surface continues, so a curving fork branch bends the run — and the camera,
    //  which sits behind along the heading, turns with it. The player's drag leans
    //  the preferred direction (so you pick a branch / drift across a wide road).
    //  Surfaces within STEP_MAX are walkable (ramps); taller ones are walls; no
    //  surface is a void you fall through.
    private fun update(dt: Float) {
        drainPending()                        // apply queued button actions on the GL thread
        noteProgress()                        // furthest point reached, for the saved run
        updateObstacleActivation()            // arm/retire the nearest obstacle (sets tollTile/hillTile/…)
        collectCoins()                        // pick up coins in ANY state (incl. push/bridge animations)
        // Drop steer input while the player is frozen (greet / prompts / on a call) so it
        // doesn't accumulate and fling them off-track. Texting stays controllable (you walk).
        if (runState != RunState.RUNNING || (choiceAcceptTimer > 0f && choiceAcceptType != ChoiceType.MESSAGE)) steerDelta = 0f
        val fx = kotlin.math.sin(heading); val fz = -kotlin.math.cos(heading)
        val rx = kotlin.math.cos(heading); val rz = kotlin.math.sin(heading)

        // Opening countdown: hold the player at the start line until "Race!".
        if (countdown > 0f) { countdown -= dt; steerDelta = 0f }
        // The player only runs when not stopped at an obstacle (toll). Helpers, the camera
        // and the head-labels keep updating in every state.
        if (runState == RunState.RUNNING && countdown <= COUNTDOWN_GO) updatePlayer(dt, fx, fz, rx, rz)
        updateHelpers(dt)
        updateHill(dt)
        updateFight(dt)
        updateGap(dt)
        updateWall(dt)
        updateHazard(dt)
        updateChoice(dt)
        updateHelpGreet(dt)
        updateVisit(dt)
        updateFinish(dt)
        updateCompetitors(dt)
        runningSfx = runState == RunState.RUNNING && countdown <= COUNTDOWN_GO && onGround &&
            !onPhone   // no run sound in the air, and silenced during the on-the-phone animation
        boulderPushing = runState == RunState.HILL_PUSH   // continuous rumble while shoving uphill
        fightingSfxActive = runState == RunState.FIGHTING // fight sound repeats through the scrap
        atFinish = runState == RunState.FINISH
        publishLabels()
        if (refuseTimer > 0f) refuseTimer -= dt
        // The boulder popup only appears AFTER the head-shake at the heel finishes.
        hillActive = runState == RunState.HILL_PROMPT && refuseTimer <= 0f
        gapActive = runState == RunState.GAP_PROMPT
        wallActive = runState == RunState.WALL_PROMPT && refuseTimer <= 0f
        textingActive = choiceAcceptTimer > 0f && choiceAcceptType == ChoiceType.MESSAGE

        // The toll boom eases up once the toll is paid, down otherwise.
        val liftTarget = if (activeTollPaid()) 1f else 0f
        barrierLift += (liftTarget - barrierLift) * (1f - Math.exp(-dt * 3.5).toFloat())

        // Camera anchor chase: follow the player FAST along the heading (so it stays a
        // fixed distance behind) but only SLOWLY sideways. The slow lateral term means a
        // quick strafe just moves the character across the frame, while any drift built up
        // through a fork's curve is gently corrected afterward (no permanent sideways
        // drift). The camera turns with the smoothed heading, so straight and curved
        // pieces look identical apart from the curve.
        val toX = charX - camX; val toZ = charZ - camZ
        val along = toX * fx + toZ * fz
        val perpX = toX - fx * along; val perpZ = toZ - fz * along
        val kAlong = 1f - Math.exp(-dt * 6.0).toFloat()
        val kPerp = 1f - Math.exp(-dt * 1.5).toFloat()
        camX += fx * along * kAlong + perpX * kPerp
        camZ += fz * along * kAlong + perpZ * kPerp
        camHeading += (heading - camHeading) * (1f - Math.exp(-dt * 3.0).toFloat())
    }

    /** Normal running: steer, follow the road's curve, advance, gravity, coins — plus the
     *  toll trigger that stops the player just in front of the booth. */
    private fun updatePlayer(dt: Float, fx: Float, fz: Float, rx: Float, rz: Float) {
        val strafe = steerDelta * STRAFE_SENS
        steerDelta = 0f

        // heading gently orients along the road's curve; a deadzone + soft ease keep it
        // from twitching frame to frame (which is what jittered the camera).
        val onFork = tileAt(charZ)?.fork == true
        val target = roadDirection(onFork)
        val d = target - heading
        if (kotlin.math.abs(d) > HEADING_DEADZONE) {
            heading += d * (1f - Math.exp(-dt * 2.5).toFloat())
        }

        // forward: a WALL ahead holds you; on a FORK a void ahead with a branch holds; else
        // run on (into a gap you fall / jump). The track never auto-dodges a gap. Speed is
        // slowed while on an accepted call/text, boosted just after declining one.
        val speedMul = when {
            // texting keeps WALKING (in-place clip), a call stops dead
            choiceAcceptTimer > 0f -> if (choiceAcceptType == ChoiceType.MESSAGE) 0.4f else 0f
            speedBoostTimer > 0f -> 1.7f
            else -> 1f
        }
        val aheadX = charX + fx * RUN_SPEED * speedMul * dt
        val aheadZ = charZ + fz * RUN_SPEED * speedMul * dt
        val aheadG = groundAt(aheadX, aheadZ, charY + HEAD_ROOM)
        val blockedWall = aheadG != null && aheadG > charY + STEP_MAX
        val sharpTurn = aheadG == null && onFork && roadAhead
        if (!blockedWall && !sharpTurn) { charX = aheadX; charZ = aheadZ }

        // finish: reach the end piece on the MAIN track → into the podium sequence. (NOT on an
        // excursion detour, which sits far below the end piece in z and would false-trigger it.)
        val endT = tiles.firstOrNull { it.piece == "end" }
        if (endT != null && !onDetour && charZ <= endT.zNear - 16f) { startFinish(endT); return }

        // toll: stop just before the booth. On the NEED detour you've ALREADY agreed to help,
        // so there's NO popup — you automatically hand the requester a coin (NEED_PAY). The main
        // toll still raises the pay/help dialog.
        val tt = activeTollTile()
        if (tt != null && !activeTollPaid() && charZ <= tt.zNear + 1.5f) {
            charZ = tt.zNear + 1.5f; vSpeed = 0f; onGround = true
            if (onNeedToll()) {
                runState = RunState.NEED_PAY; needPayZip = 0f
            } else {
                runState = RunState.TOLL_PROMPT
                // Early (few coins) it's a bit more than you carry → you must call for help. But
                // it's CAPPED, so once you're richer than the cap you can just pay it outright.
                tollAmount = (coinCount + TOLL_MARGIN).coerceAtMost(TOLL_CAP)
                canPayToll = coinCount >= tollAmount
                tollActive = true
            }
            return
        }

        // hill: reach the boulder at the start of the flat → begin pushing it solo
        val ht = hillTile
        if (ht != null && !boulderDestroyed &&
            charZ <= boulderZ + boulderRadiusW + PUSH_GAP + 0.3f && charZ >= ht.zFar) {
            vSpeed = 0f; onGround = true
            runState = RunState.HILL_SOLO
            return
        }

        // ring: stop in front of the mob and raise the fight/help dialog
        val rt = ringTile
        if (rt != null && !fightDone && charZ <= ringStopZ && charZ >= rt.zFar) {
            charZ = ringStopZ; vSpeed = 0f; onGround = true
            charY = groundUnderFeet() ?: charY
            heading = 0f
            runState = RunState.FIGHT_PROMPT
            fightAllies = 1 + arrivedFighters()
            fightActive = true
            return
        }

        // gap: stop at the near edge → "might need help for this" (unless already bridged)
        val gt = gapTile
        if (gt != null && !gapDone && onGround && charZ <= gapNearZ + 1.4f && charZ >= gapFarZ + 0.5f) {
            charZ = gapNearZ + 1.4f; vSpeed = 0f; onGround = true
            charY = groundUnderFeet() ?: charY
            heading = 0f
            helpers.removeAll { it.climber }          // clear any stale climbers from before
            runState = RunState.GAP_PROMPT
            climbBaseX = charX.coerceIn(-2f, 2f)      // tower/bridge forms where the player stands
            climbBaseZ = gapNearZ + 0.4f; climbBaseY = gapEdgeY
            gapActive = true
            gapClimbers = climberCount()
            return
        }

        // wall: stop at the near face → can't jump it → ladder-help dialog (unless already over)
        val wt = wallTile
        if (wt != null && !wallDone && onGround && charZ <= wallNearZ + 1.4f && charZ >= wt.zFar) {
            charZ = wallNearZ + 1.4f; vSpeed = 0f; onGround = true
            charY = groundUnderFeet() ?: charY
            heading = 0f
            helpers.removeAll { it.climber }          // clear the gap bridge / stale climbers
            runState = RunState.WALL_PROMPT
            climbBaseX = charX.coerceIn(-2f, 2f)
            climbBaseZ = wallNearZ + 0.6f; climbBaseY = wallBaseY
            wallActive = true
            wallClimbers = climberCount()
            return
        }

        // help: if the call was accepted, stop by the basehelp column for the greet sequence
        val bt = basehelpTile
        if (bt != null && helpAccepted && !helpGreetDone) {
            val colZ = bt.zRef + COL_REL_Z
            if (charZ <= colZ + 3f && charZ >= bt.zFar) {
                charZ = colZ + 3f; vSpeed = 0f; onGround = true
                charY = groundUnderFeet() ?: charY
                heading = 0f
                runState = RunState.HELP_GREET
                greetTimer = 0f
                return
            }
        }

        // lateral strafe: walkable ground OR an open void steps you across; only a too-high
        // rim (raised outer edge) blocks.
        if (strafe != 0f) {
            val sx = charX + rx * strafe; val sz = charZ + rz * strafe
            val gS = groundAt(sx, sz, charY + HEAD_ROOM)
            if (gS == null || gS <= charY + STEP_MAX) { charX = sx; charZ = sz }
        }

        if (charZ < farZ + 30f && !onDetour) {                     // wrap at the end → restart course
            tollPaid = false; runState = RunState.RUNNING; tollActive = false; helpers.clear()
            hillActive = false; refuseTimer = 0f; playerDying = false; deathsApplied = false
            gapActive = false; wallActive = false
            buildTileLayout(tileStartZ, farZ)                      // rebuild (resets boulder/hut/hill/ring/gap/wall)
            respawnAhead(tileStartZ - 12f)
            return
        }

        // vertical: jump, then either STICK to the ground (following slopes/steps up or down
        // within a step, so descents don't hop) or fall (a real ledge / gap).
        if (jumpRequested) {
            jumpRequested = false
            if (onGround) { vSpeed = JUMP_VEL; onGround = false; jumpSfx++ }
        }
        if (onGround) {
            val g = groundUnderFeet()
            if (g != null && kotlin.math.abs(charY - g) <= STEP_DOWN) {
                charY = g; vSpeed = 0f; safeZ = charZ; safeY = g
            } else {
                onGround = false                        // no ground within a step → start falling
            }
        }
        if (!onGround) {
            vSpeed -= GRAVITY * dt
            charY += vSpeed * dt
            val g = groundUnderFeet()
            if (g != null && charY <= g && vSpeed <= 0f) {
                charY = g; vSpeed = 0f; onGround = true
                safeZ = charZ; safeY = g
            }
        }

        // fell into a gap / off an edge → respawn a piece BEFORE where we fell. Use the last
        // grounded spot (safeZ), not charZ — the character keeps drifting FORWARD while
        // falling, so charZ is already well past the hole and would respawn ahead of it.
        if (charY < safeY - FALL_DEATH) { fallSfx++; respawnBefore(safeZ) }

    }

    /** Pick up any coin the character is over — every frame, so coins are collected while
     *  PUSHING a boulder / crossing a bridge / any animation, not only when free-running. */
    private fun collectCoins() {
        for (c in coins) {
            if (c.taken) continue
            val dz = c.z - charZ
            if (dz < -2.5f || dz > 2.5f) continue
            val dx = c.x - charX
            if (dx * dx + dz * dz < 1.6f) { c.taken = true; coinCount++; coinSfx++ }
        }
    }

    /** Summoned helpers run from the branch toward the player; the first to arrive at the
     *  toll pays the balance and the run resumes (the helper is left behind at the booth). */
    private fun updateHelpers(dt: Float) {
        for (h in helpers) {
            if (h.arrived) {
                // Reached the player: the carried coin zips over, then the toll clears.
                if (h.carriesCoin && h.zip in 0f..1f) {
                    h.zip = (h.zip + dt / COIN_ZIP_TIME).coerceAtMost(1f)
                    if (h.zip >= 1f && runState == RunState.TOLL_HELP && !tollPaid) resolveToll()
                }
                // Opponents keep facing the player; ring allies face the mob (−z); everyone
                // else turns to face the player.
                if (h.opponent) { /* keep set facing */ }
                else if (h.fighter || h.climber || h.companion) h.facing = 0f
                else if (!h.dying) h.facing = kotlin.math.atan2((charX - h.x).toDouble(), -(charZ - h.z).toDouble()).toFloat()
                continue
            }
            val dx = h.targetX - h.x; val dz = h.targetZ - h.z
            val dist = kotlin.math.sqrt(dx * dx + dz * dz)
            if (dist < 0.4f) {
                h.arrived = true
                if (h.carriesCoin) h.zip = 0f            // start the coin hand-off
                if (h.pusher) hillHave = arrivedPushers()
                if (h.fighter) fightAllies = 1 + arrivedFighters()
                continue
            }
            val step = (HELPER_SPEED * dt).coerceAtMost(dist)
            h.x += dx / dist * step; h.z += dz / dist * step
            h.y = groundAt(h.x, h.z, h.y + 3f) ?: h.y      // stay on the surface, not a roof
            h.facing = kotlin.math.atan2(dx.toDouble(), -dz.toDouble()).toFloat()
        }
    }

    /** Roll the boulder forward by [speed], keep it on the surface, and spin it accordingly. */
    private fun rollBoulder(dt: Float, speed: Float) {
        val dz = speed * dt
        boulderZ -= dz
        boulderSpin += dz / boulderRadiusW * (180f / Math.PI.toFloat())
        val g = groundAt(boulderX, boulderZ, boulderY + 3f)   // ride the surface, not the roof
        if (g != null) boulderY = g + boulderRadiusW
        if (boulderY > boulderMaxY) boulderMaxY = boulderY
    }

    /** Z where someone at lane-offset [dx] stands to just TOUCH the boulder's curved back
     *  (the surface recedes toward the sides, so centre pushers stand further back). */
    private fun contactZ(dx: Float, rowBack: Float): Float {
        val rr = boulderRadiusW * boulderRadiusW - dx * dx
        val d = if (rr > 0f) kotlin.math.sqrt(rr) else 0f
        return boulderZ + d + PUSH_GAP + rowBack
    }

    /** Cluster the player (optionally) + pushers against the boulder's back, each touching
     *  the curve at their own lane offset (no half-body-inside, no floating off). */
    private fun positionPushers(includePlayer: Boolean) {
        if (includePlayer) {
            charX += (0f - charX) * 0.2f                 // ease to centre
            charZ = contactZ(charX, 0f)
            charY = groundUnderFeet() ?: charY
            heading = 0f
        }
        var i = 0
        for (h in helpers) if (h.pusher) {
            val s = PUSHER_SLOTS[i.coerceAtMost(PUSHER_SLOTS.size - 1)]; i++
            h.x = s[0]; h.z = contactZ(s[0], s[1])
            h.y = groundAt(h.x, h.z, h.y + 3f) ?: h.y
            h.facing = 0f
        }
    }

    /** Boulder phases: SOLO (player shoves it across the flat), PUSH (group shoves it up to
     *  the crest), WATCH (group idles while it free-rolls into the hut and pops out of
     *  existence). */
    private fun updateHill(dt: Float) {
        when (runState) {
            RunState.HILL_SOLO -> {
                rollBoulder(dt, BOULDER_SOLO_SPEED)
                positionPushers(includePlayer = true)
                if (boulderZ <= heelZ + HILL_STUCK_MARGIN) {   // gets stuck ON the flat, before the climb
                    boulderZ = heelZ + HILL_STUCK_MARGIN
                    runState = RunState.HILL_PROMPT
                    refuseTimer = 1.2f                   // stop, shake head, then the popup shows
                    hillHave = arrivedPushers()
                }
            }
            RunState.HILL_PUSH -> {
                rollBoulder(dt, BOULDER_PUSH_SPEED)
                positionPushers(includePlayer = true)
                if (boulderY < boulderMaxY - 3f) {       // crested → everyone stops to watch
                    runState = RunState.HILL_WATCH
                }
            }
            RunState.HILL_WATCH -> {
                rollBoulder(dt, BOULDER_ROLL_SPEED)      // free-rolls down alone
                if (boulderZ <= hutExplodeZ) {           // deep inside the hut → just gone
                    boulderDestroyed = true
                    runState = RunState.RUNNING          // player runs on
                    for (h in helpers) if (h.pusher) h.waving = true   // helpers wave
                }
            }
            else -> {}
        }
    }

    /** Elapsed time into the death animation (0 until the brawl is decided). */
    private fun deathElapsed(): Float = (fightTimer - FIGHT_CLASH).coerceAtLeast(0f)

    /** Ring brawl: a short clash, then the losers play a death animation. If the good guys
     *  matched the mob they all live (opponents fall); if outnumbered they die and respawn. */
    private fun updateFight(dt: Float) {
        if (runState != RunState.FIGHTING) return
        fightTimer += dt
        if (fightTimer >= FIGHT_CLASH && !deathsApplied) {
            deathsApplied = true
            if (fightWon) {
                for (h in helpers) if (h.opponent) h.dying = true          // the mob goes down
            } else {
                playerDying = true; playerDeathClip = deathClips[0]
                var i = 1
                for (h in helpers) if (h.fighter) { h.dying = true; h.deathClip = deathClips[i++ % 4] }
            }
        }
        // Don't end until every death animation has fully played out.
        if (fightTimer >= FIGHT_CLASH + maxDeathDur + 0.4f) {
            if (fightWon) {
                fightDone = true
                runState = RunState.RUNNING                                // push on past the ring
                for (h in helpers) if (h.fighter) h.waving = true          // mates cheer you on
            } else {
                // outnumbered: dead mates gone, mob back on its feet, respawn before the ring
                playerDying = false
                helpers.removeAll { it.fighter }
                for (h in helpers) if (h.opponent) h.dying = false
                fightAllies = 1
                runState = RunState.RUNNING
                respawnBefore(safeZ)
            }
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** The human-tower bridge: climbers stack at the near edge; on PROCEED the tower topples
     *  across the gap. If the reach spans it, a walkable bridge forms; if not, they fall in
     *  and the player is sent back to summon more. */
    private fun updateGap(dt: Float) {
        when (runState) {
            RunState.GAP_PROMPT -> stackClimbers(dt)
            RunState.GAP_TOPPLE -> {
                toppleT = (toppleT + dt / TOPPLE_TIME).coerceAtMost(1f)
                val lieY = gapEdgeY + 0.3f
                for (h in helpers) if (h.climber) {
                    val stackY = gapEdgeY + h.stackIndex * STACK_H
                    // First body lies right at the near edge (no gap to the track), the rest
                    // tile forward across the void.
                    val lieZ = gapNearZ - 0.3f - h.stackIndex * BRIDGE_PER_CLIMBER
                    h.x = climbBaseX
                    h.z = lerp(gapNearZ + 0.4f, lieZ, toppleT)
                    h.y = lerp(stackY, lieY, toppleT)
                    h.pitch = toppleT * (Math.PI.toFloat() / 2f)
                    h.facing = 0f
                }
                if (toppleT >= 1f) {
                    val reach = climberCount() * BRIDGE_PER_CLIMBER
                    if (reach >= gapWidth) {
                        val by = gapEdgeY + 0.15f             // flush enough to step onto (< STEP_MAX)
                        val x0 = climbBaseX - 2f; val x1 = climbBaseX + 2f  // bridge under the player
                        val add = floatArrayOf(
                            x0, by, gapNearZ, x1, by, gapNearZ, x1, by, gapFarZ,
                            x0, by, gapNearZ, x1, by, gapFarZ, x0, by, gapFarZ)
                        gapTile?.let { it.topTris = it.topTris + add }
                        gapDone = true
                        runState = RunState.RUNNING          // run across the bridge
                    } else {
                        runState = RunState.GAP_FALL; gapFallT = 0f
                    }
                }
            }
            RunState.GAP_FALL -> {
                gapFallT += dt
                for (h in helpers) if (h.climber) h.y -= GAP_FALL_SPEED * dt
                if (gapFallT >= GAP_FALL_TIME) {
                    helpers.removeAll { it.climber }
                    gapClimbers = 0
                    runState = RunState.RUNNING
                    respawnBefore(gapNearZ + 2f)             // back behind the gap → prompt again
                }
            }
            else -> {}
        }
    }

    /** The wall: climbers stack into a ladder; on CLIMB the player scales it and drops over. */
    private fun updateWall(dt: Float) {
        when (runState) {
            RunState.WALL_PROMPT -> stackClimbers(dt)
            RunState.WALL_CLIMB -> {
                wallClimbT = (wallClimbT + dt / WALL_CLIMB_TIME).coerceAtMost(1f)
                val farY = groundAt(climbBaseX, wallFarZ - 1.5f) ?: wallBaseY
                val t = wallClimbT
                charX = climbBaseX; heading = 0f
                val ladderZ = climbBaseZ + 0.55f             // climb the NEAR face of the body-ladder
                if (t < 0.5f) {                              // up the ladder
                    charZ = ladderZ; charY = lerp(wallBaseY, wallTopY + 0.3f, t / 0.5f)
                } else {                                     // over the top and down the far side
                    val u = (t - 0.5f) / 0.5f
                    charZ = lerp(ladderZ, wallFarZ - 1.5f, u)
                    charY = lerp(wallTopY + 0.3f, farY, u)
                }
                if (wallClimbT >= 1f) {
                    wallDone = true
                    charZ = wallFarZ - 1.5f; charY = groundUnderFeet() ?: charY
                    vSpeed = 0f; onGround = true
                    runState = RunState.RUNNING
                }
            }
            else -> {}
        }
    }

    /** Boulder hill: while the player is on it, small rocks drop from above and roll back at
     *  them. A hit sends the player back to the bottom of the hill. */
    private fun updateHazard(dt: Float) {
        hazardRollingCount = hazBoulders.count { it.landed }   // drives the rolling-rock rumble
        // Impact pause: hold on the knocked-down pose, then drop back to the bottom.
        if (runState == RunState.HAZ_HIT) {
            hitTimer += dt
            if (hitTimer >= hazDeathDur + 0.3f) {         // let the knockdown finish first
                hazBoulders.clear()
                respawnAt(bouderEntranceZ)
                runState = RunState.RUNNING
            }
            return
        }
        val bt = bouderTile
        val onHill = bt != null && runState == RunState.RUNNING && charZ <= bt.zNear && charZ >= bt.zFar
        // Only while BELOW the crest (climbing up toward it) — no rocks once you're over the top.
        if (onHill && charZ > bouderCrestZ + 3f) {
            hazTimer += dt
            if (hazTimer >= HAZ_INTERVAL) {
                hazTimer = 0f
                // Drop at the CREST in a fixed lane; it then rolls the whole way DOWN toward you.
                val lane = HAZ_LANES[(Math.random() * HAZ_LANES.size).toInt().coerceIn(0, HAZ_LANES.size - 1)]
                val hz = bouderCrestZ + (Math.random() * 3f).toFloat()
                val g = groundAt(lane, hz, 30f)
                if (g != null) hazBoulders.add(HazBoulder(lane, g + 6f, hz, 0f))
            }
        }
        val it = hazBoulders.iterator()
        while (it.hasNext()) {
            val b = it.next()
            if (!b.landed) {
                b.vy -= GRAVITY * dt; b.y += b.vy * dt
                val g = (groundAt(b.x, b.z, b.y + 4f) ?: -50f) + HAZ_R
                if (b.y <= g) { b.y = g; b.landed = true }
            } else {
                b.z += HAZ_ROLL_SPEED * dt                              // roll straight down its lane
                // sample near the rock's own height so a plateau/roof can't teleport it up/down
                b.y = (groundAt(b.x, b.z, b.y + 2f) ?: (b.y - HAZ_R)) + HAZ_R
                b.spin -= HAZ_ROLL_SPEED / HAZ_R * (180f / Math.PI.toFloat()) * dt
            }
            // hit the player? → knock them down (visible impact), then respawn. Only rocks that
            // have LANDED and are near the player count (a rock still in the air can't kill).
            if (runState == RunState.RUNNING && b.landed &&
                kotlin.math.abs(b.x - charX) < HAZ_R + HAZ_HIT_R &&
                kotlin.math.abs(b.z - charZ) < HAZ_R + HAZ_HIT_R) {
                runState = RunState.HAZ_HIT; hitTimer = 0f; vSpeed = 0f
                return
            }
            // keep rolling until they reach the very bottom (past the entrance), not the moment
            // they pass the player — so they complete the journey.
            if (b.z > (bt?.zNear ?: charZ) + 3f || b.y < -90f) it.remove()
        }
    }

    /** Helped/connected → bank the friend. (Helping delays you, so the racers pull ahead — handled
     *  by the competitor-momentum sim, not a fixed offset.) */
    private fun recordHelped(cf: ChoiceFork) {
        if (cf.name.isNotEmpty() && cf.name !in friendsBanked) friendsBanked.add(cf.name)
        friendCount = friendsBanked.size
    }
    /** Refused → a lost friend. (The decline speed-boost is what closes on the racers.) */
    private fun recordRefused() { refusals++; refusalCount = refusals }

    /** CHOICES: as the player nears a fork with an incoming call/message, show the popup;
     *  when they cross the fork centre, their LANE decides it (left = accept, right = decline).
     *  No stopping — decline gives money + a boost, accept slows them for the 3s animation. */
    private fun updateChoice(dt: Float) {
        if (choiceAcceptTimer > 0f) choiceAcceptTimer -= dt
        if (speedBoostTimer > 0f) speedBoostTimer -= dt
        onPhone = choiceAcceptTimer > 0f && choiceAcceptType == ChoiceType.CALL   // on-the-phone animation
        callRinging = false                              // set true below while a CALL popup is up
        if (runState != RunState.RUNNING) { choiceActive = false; return }
        if (onDetour) { choiceActive = false; return }    // detour z is far below — don't fire other forks
        val cf = choiceForks.firstOrNull { !it.decided }
        if (cf == null) { choiceActive = false; return }
        val isBranch = cf.type == ChoiceType.VISIT || cf.type == ChoiceType.NEED
        val decisionZ = (cf.tile.zNear + cf.tile.zFar) * 0.5f
        // Approach popup — appears WELL before the fork (≈3s of run-up) so there's time to react.
        if (charZ <= cf.tile.zNear + 32f) {
            if (cf.name.isEmpty()) cf.name = seenNames.randomOrNull() ?: helperNames.firstOrNull() ?: "A friend"
            choiceIcon = when (cf.type) { ChoiceType.MESSAGE -> "message"; ChoiceType.HELP, ChoiceType.NEED -> "phonebook"; else -> "phone" }
            choiceText = when (cf.type) {
                ChoiceType.CALL -> "${cf.name} is calling"
                ChoiceType.MESSAGE -> "${cf.name} is messaging"
                ChoiceType.HELP -> "${cf.name} needs help ahead"
                ChoiceType.VISIT -> "${cf.name} wants to see you"
                ChoiceType.NEED -> "${cf.name} needs you"
            }
            choiceRightAccepts = isBranch
            choiceActive = true
            callRinging = cf.type == ChoiceType.CALL     // ring until you pick up (or decline)
            if (!cf.popupSounded) {                      // one-shot when the popup first appears
                cf.popupSounded = true
                if (cf.type == ChoiceType.MESSAGE) msgPopupSfx++
            }
        }
        if (isBranch) {
            // forkoutr shares one lane (x≈±3) until it SPLITS ~23 units in; the accept arm then
            // bends hard RIGHT (x climbs past +4 within a few units). Only once you've actually
            // run onto that bent arm (x well past the shared lane) do we teleport — so you see
            // the fork split and follow the branch first. Straight dead-ends ~35 in → skip before then.
            if (charX > 4.5f && charZ <= cf.tile.zNear) {    // ON this fork AND out on the bent arm
                cf.decided = true; choiceActive = false
                recordHelped(cf); startExcursion(cf)
            } else if (charZ <= cf.tile.zNear - 33f) {       // reached the straight dead-end, never took it
                cf.decided = true; choiceActive = false
                recordRefused(); respawnAt(cf.returnZ, softCam = true, keepX = true)   // glide past, keep your lane
            }
            return
        }
        // Diamond forks: decision at the centre; the lane you're in commits you (left = accept).
        if (charZ <= decisionZ) {
            cf.decided = true
            choiceActive = false
            if (charX < 0f) {
                recordHelped(cf)
                when (cf.type) {
                    ChoiceType.HELP -> helpAccepted = true      // stop + greet at the column
                    else -> {
                        choiceAcceptType = cf.type
                        choiceAcceptTimer = if (cf.type == ChoiceType.CALL) 6.5f else 5.5f
                    }
                }
            } else {                                         // declined → money + boost
                recordRefused()
                speedBoostTimer = CHOICE_BOOST_TIME
                spawnChoiceMoney()
            }
        }
    }

    /** Reached the finish: walk onto the 2nd-place podium (blue=1st centre, red=3rd right), the
     *  camera pulls back over a sea of tracks, then it fades to black → building complete. */
    // Winners' STEPS in the end piece (measured, game space; groundAt fails there — inverted winding):
    //   1st tallest middle: x0 y5.9 · 2nd medium LEFT: x−6 y4.1 · 3rd lowest RIGHT: x+6 y2.5.
    private val STEP_2ND = floatArrayOf(-6f, 4.1f); private val STEP_1ST = floatArrayOf(0f, 5.9f); private val STEP_3RD = floatArrayOf(6f, 2.5f)
    private val finishCompStart = arrayOf(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f))

    private fun startFinish(endT: TileInstance) {
        runState = RunState.FINISH
        finishTimer = 0f; camZoom = 0f
        finishPodiumZ = endT.zRef - SCALE_Z * 21.3f          // winners' STEPS sit at raw z≈21.3
        finishStartX = charX; finishStartZ = charZ; finishStartY = charY
        for (i in competitors.indices) finishCompStart[i] = floatArrayOf(competitors[i].x, compZ[i])
        lonelyEnding = refusals >= 3
        vSpeed = 0f; onGround = true
    }

    private fun updateFinish(dt: Float) {
        if (runState != RunState.FINISH) return
        finishTimer += dt
        val pz = finishPodiumZ
        val yOff = tiles.firstOrNull { it.piece == "end" }?.yOffset ?: 0f
        val faceCam = Math.PI.toFloat()                      // face +z toward the camera
        val t = (finishTimer / FINISH_WALK).coerceIn(0f, 1f)
        // player → 2nd step (medium, left)
        charX = lerp(finishStartX, STEP_2ND[0], t)
        charZ = lerp(finishStartZ, pz, t)
        charY = lerp(finishStartY, STEP_2ND[1] + yOff, t)
        heading = if (t < 0.8f) 0f else faceCam
        // blue → 1st (tall, middle), red → 3rd (low, right); walk over from their lanes.
        competitors.getOrNull(1)?.let { it.x = lerp(finishCompStart[1][0], STEP_1ST[0], t); it.z = lerp(finishCompStart[1][1], pz, t); it.y = lerp(COMP_Y, STEP_1ST[1] + yOff, t); it.facing = faceCam; it.arrived = t >= 1f }
        competitors.getOrNull(0)?.let { it.x = lerp(finishCompStart[0][0], STEP_3RD[0], t); it.z = lerp(finishCompStart[0][1], pz, t); it.y = lerp(COMP_Y, STEP_3RD[1] + yOff, t); it.facing = faceCam; it.arrived = t >= 1f }
        if (finishTimer > FINISH_WALK) camZoom = ((finishTimer - FINISH_WALK) / 1.3f).coerceIn(0f, 1f)
        val fadeAt = FINISH_WALK + FINISH_HOLD
        if (finishTimer > fadeAt) finishFade = ((finishTimer - fadeAt) / FINISH_FADE).coerceIn(0f, 1f)
        if (finishTimer > fadeAt + FINISH_FADE + 0.2f) finishDone = true
    }

    /** The two side-track racers, each with its own momentum: they keep running (slower) while
     *  you're stopped at an obstacle or helping (pulling ahead), drift back a touch while you
     *  cruise, and your decline-boost really dents their lead — but they're clamped so they never
     *  leave the screen. On death they freeze pinned to you and respawn where you do. */
    private fun updateCompetitors(dt: Float) {
        if (runState == RunState.FINISH) return              // held on the podium by updateFinish
        if (competitors.isEmpty()) {
            competitors = listOf(
                Helper(-COMP_X, charZ - COMP_AHEAD[0], COMP_Y, -COMP_X, charZ, floatArrayOf(0.78f, 0.32f, 0.30f), "", scale = COMP_SCALE),
                Helper(COMP_X, charZ - COMP_AHEAD[1], COMP_Y, COMP_X, charZ, floatArrayOf(0.30f, 0.46f, 0.74f), "", scale = COMP_SCALE),
            )
            compZ = floatArrayOf(charZ - COMP_AHEAD[0], charZ - COMP_AHEAD[1]); compPrevCharZ = charZ
        }
        val racing = countdown <= COUNTDOWN_GO
        val deathFreeze = runState == RunState.HAZ_HIT || playerDying || runState == RunState.GAP_FALL
        val obstacleStopped = runState != RunState.RUNNING && !onDetour   // paused at an obstacle/greet
        // A respawn while running (fell in a gap/off a ledge) jumps you backward → shift the racers
        // by the same amount so they respawn WITH you (not left far ahead). Excursion teleports excluded.
        val teleported = onDetour != compPrevOnDetour
        val jumpBack = charZ - compPrevCharZ
        if (racing && !deathFreeze && !teleported && jumpBack > 5f) for (i in compZ.indices) compZ[i] += jumpBack
        compPrevCharZ = charZ; compPrevOnDetour = onDetour
        for (i in competitors.indices) {
            when {
                !racing -> compZ[i] = charZ - COMP_AHEAD[i]                    // wait on the start line
                deathFreeze -> {
                    if (!compFrozenPrev) compDeathLead[i] = charZ - compZ[i]  // capture the gap at death
                    compZ[i] = charZ - compDeathLead[i]                       // pin → respawn with you
                }
                else -> {
                    val base = RUN_SPEED * compFactor[i]
                    compZ[i] -= (if (obstacleStopped) base * 0.5f else base) * dt  // slower while you're stopped
                    if (!onDetour) {                                          // clamp to stay on screen
                        val lead = charZ - compZ[i]
                        if (lead > LEAD_MAX) compZ[i] = charZ - LEAD_MAX
                        if (lead < LEAD_MIN) compZ[i] = charZ - LEAD_MIN
                    }
                }
            }
            competitors[i].let { it.x = if (i == 0) -COMP_X else COMP_X; it.z = compZ[i]; it.y = COMP_Y; it.facing = 0f }
        }
        compFrozenPrev = deathFreeze
    }

    /** Two straight side-tracks the competitors run on, out past the surround buildings. */
    private fun drawCompetitorTracks() {
        if (boxVao == 0 || onDetour) return
        GLES30.glUseProgram(flatProg)
        GLES30.glUniformMatrix4fv(fVP, 1, false, vp, 0)
        GLES30.glUniform3f(fColor, TILE_GREY[0], TILE_GREY[1], TILE_GREY[2])
        GLES30.glBindVertexArray(boxVao)
        for (cx in floatArrayOf(-COMP_X, COMP_X)) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, cx - 2f, COMP_Y - 0.15f, charZ - 150f)  // box x∈[0,1] → centre it; top at COMP_Y
            Matrix.scaleM(modelMat, 0, 4f, 0.3f, 720f)                            // 4 wide (narrower still), flat, long
            GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, boxVerts, GLES30.GL_UNSIGNED_INT, 0)
        }
        // At the finish, each side lane RISES and bends inward to the winners' step (a ramp).
        if (runState == RunState.FINISH) {
            val pz = finishPodiumZ
            val yOff = tiles.firstOrNull { it.piece == "end" }?.yOffset ?: 0f
            val ramps = arrayOf(-COMP_X to STEP_3RD, COMP_X to STEP_1ST)   // red lane→3rd, blue lane→1st
            for ((laneX, step) in ramps) {
                val ax = laneX; val ay = COMP_Y; val bx = step[0]; val by = step[1] + yOff
                val dx = bx - ax; val dy = by - ay
                val len = kotlin.math.sqrt(dx * dx + dy * dy)
                val ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, ax, ay, pz)
                Matrix.rotateM(modelMat, 0, ang, 0f, 0f, 1f)
                Matrix.translateM(modelMat, 0, 0f, -0.2f, -3f)          // centre the width, sit under the top
                Matrix.scaleM(modelMat, 0, len, 0.4f, 6f)              // box extends +x along the ramp
                GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
                GLES30.glDrawElements(GLES30.GL_TRIANGLES, boxVerts, GLES30.GL_UNSIGNED_INT, 0)
            }
        }
        GLES30.glBindVertexArray(0)
    }

    /** Finish pan-out: a sea of parallel tracks fanning off both sides + into the distance. */
    private fun drawFinishBackdrop() {
        if (runState != RunState.FINISH || camZoom < 0.12f || boxVao == 0) return
        GLES30.glUseProgram(flatProg)
        GLES30.glUniformMatrix4fv(fVP, 1, false, vp, 0)
        GLES30.glUniform3f(fColor, TILE_GREY[0], TILE_GREY[1], TILE_GREY[2])
        GLES30.glBindVertexArray(boxVao)
        val cz = finishPodiumZ
        var lane = 16f
        while (lane < 150f) {                                // many lanes on each side, stepped down
            val y = COMP_Y - 3f - lane * 0.12f
            for (sx in floatArrayOf(-lane, lane)) {
                Matrix.setIdentityM(modelMat, 0)
                Matrix.translateM(modelMat, 0, sx - 2f, y, cz + 40f)
                Matrix.scaleM(modelMat, 0, 4f, 0.3f, 520f)
                GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
                GLES30.glDrawElements(GLES30.GL_TRIANGLES, boxVerts, GLES30.GL_UNSIGNED_INT, 0)
            }
            lane += 13f
        }
        GLES30.glBindVertexArray(0)
    }

    /** Accepted a branch fork → teleport onto its detour; the caller falls in beside you. */
    private fun startExcursion(cf: ChoiceFork) {
        onDetour = true; needExcursion = cf.type == ChoiceType.NEED; needPaid = false
        activeReturnZ = cf.returnZ; activeDetourEndZ = cf.detourEndZ
        respawnAt(cf.detourStartZ)
        val name = cf.name
        val color = HELPER_COLORS[helperColorIdx % HELPER_COLORS.size]; helperColorIdx++
        if (needExcursion && needTollTile != null) {
            // The friend is STUCK at the toll ahead — waiting, facing back for you to help.
            needTollZ = needTollTile!!.zNear + 0.6f
            val wx = -1.1f
            visitCompanion = Helper(wx, needTollZ, groundAt(wx, needTollZ) ?: 0f, wx, needTollZ,
                color, name, arrived = true, companion = true, facing = Math.PI.toFloat())
        } else {
            // VISIT: the friend falls in beside you straight away.
            val cx = (charX + 1.8f).coerceIn(-2.5f, 2.5f)
            visitCompanion = Helper(cx, charZ, groundAt(cx, charZ) ?: charY, cx, charZ,
                color, name, arrived = true, companion = true)
        }
        helpers.add(visitCompanion!!)
    }

    /** On a detour: companion runs level with you; NEED deducts their toll partway; at the far
     *  end (forkoutl) you teleport back onto the main track where you left off. */
    private fun updateVisit(dt: Float) {
        if (!onDetour) return
        // NEED toll: no popup — automatically hand a coin to the requester, then the barrier
        // lifts and you run on together.
        if (runState == RunState.NEED_PAY) {
            needPayZip = (needPayZip + dt / 0.9f).coerceAtMost(1f)      // ~0.9s hand-off
            if (needPayZip >= 1f) {
                coinCount = (coinCount - TOLL_COST).coerceAtLeast(0)   // you cover the friend's toll
                needPaid = true                                        // barrier lifts
                needPayZip = -1f
                runState = RunState.RUNNING
            }
        }
        visitCompanion?.let { c ->
            if (needExcursion && !needPaid) {
                // Stuck at the toll — stay put, face back toward you until you cover it.
                c.x = -1.1f; c.z = needTollZ; c.y = groundAt(c.x, c.z) ?: c.y
                c.facing = Math.PI.toFloat()
            } else {
                // VISIT the whole way, or NEED once the toll's paid → run alongside you, but
                // HOP gaps / follow the ground instead of running through the air.
                c.z = charZ
                c.x = (charX + 1.8f).coerceIn(-2.5f, 2.5f)
                val aheadG = groundAt(c.x, c.z - 4f)
                if (companionGrounded && aheadG == null) { companionVy = JUMP_VEL; companionGrounded = false }
                companionVy -= GRAVITY * dt
                var ny = c.y + companionVy * dt
                val g = groundAt(c.x, c.z)
                if (g != null && ny <= g && companionVy <= 0f) { ny = g; companionVy = 0f; companionGrounded = true }
                else companionGrounded = false
                c.y = ny.coerceAtLeast(charY - 2f)          // never sink far below the player
                c.facing = 0f
            }
        }
        // The forkoutl at the detour's end shares one lane until it SPLITS, then bends hard LEFT
        // (game x well negative). Only once you've run onto that bent arm (or reach its straight
        // dead-end) do we teleport back — so you see this fork split and follow it too.
        if (charX < -4.5f || charZ <= activeDetourEndZ - 33f) {
            onDetour = false
            visitCompanion?.let { helpers.remove(it) }        // no lingering companion / label
            visitCompanion = null
            respawnAt(activeReturnZ)
        }
    }

    /** HELP accepted: the column friend climbs down and comes over for a handshake, ending up
     *  right in front of the player so their hands actually meet. */
    private fun updateHelpGreet(dt: Float) {
        if (runState != RunState.HELP_GREET) return
        greetTimer += dt
        val ch = columnHelper ?: run { runState = RunState.RUNNING; helpGreetDone = true; return }
        val colZ0 = (basehelpTile?.zRef ?: charZ) + COL_REL_Z
        val meetX = charX + 0.6f                            // ends up right beside the player
        val meetZ = charZ - 1.2f
        val gy = groundUnderFeet() ?: charY
        val faceX = 2.0f                                    // just OFF the column's near face (track side)
        val faceGy = groundAt(faceX, colZ0) ?: gy          // TRACK ground at the column base (not its top)
        val walkEnd = CLIMB_DOWN_T + GREET_WALK_T
        if (greetTimer < CLIMB_DOWN_T) {                   // 1) climb the near face ALL THE WAY DOWN
            val t = greetTimer / CLIMB_DOWN_T
            ch.x = lerp(COL_X, faceX, t); ch.z = colZ0     // shift to the player-facing face
            ch.y = lerp(COL_TOP_Y, faceGy, t)              // descend from the top to the ground
            ch.facing = (Math.PI * 0.5).toFloat()          // faces INTO the column → back to the player
        } else if (greetTimer < walkEnd) {                 // 2) walk over to the player
            val t = (greetTimer - CLIMB_DOWN_T) / GREET_WALK_T
            ch.x = lerp(faceX, meetX, t); ch.z = lerp(colZ0, meetZ, t)
            ch.y = groundAt(ch.x, ch.z) ?: gy
            ch.facing = kotlin.math.atan2((meetX - faceX).toDouble(), -(meetZ - colZ0).toDouble()).toFloat()
        } else if (greetTimer < walkEnd + SHAKE_T) {       // 3) shake hands, facing each other
            ch.x = meetX; ch.z = meetZ; ch.y = gy
            ch.facing = kotlin.math.atan2((charX - ch.x).toDouble(), -(charZ - ch.z).toDouble()).toFloat()
        } else {
            helpGreetDone = true
            runState = RunState.RUNNING
        }
    }

    /** A little windfall of coins ahead of the player (reward for declining). */
    private fun spawnChoiceMoney() {
        var z = charZ - 6f
        for (i in 0 until 21) {                          // 3× more money for declining
            val x = (i % 3 - 1) * 1.4f
            val g = groundAt(x, z) ?: charY
            coins.add(Coin(x, g + COIN_HOVER, z))
            z -= 3f
        }
    }

    /** Drop the player onto solid ground at world z (centred, facing forward). [softCam] leaves
     *  the camera to glide to the new spot instead of snapping (for a smooth decline skip). */
    private fun respawnAt(z: Float, softCam: Boolean = false, keepX: Boolean = false) {
        val x = if (keepX) charX.coerceIn(-2.5f, 2.5f) else 0f
        charX = x; heading = 0f; charZ = z
        charY = (groundAt(x, z) ?: groundAt(0f, z) ?: groundAt(0f, z - 2f)) ?: 0f
        vSpeed = 0f; onGround = true; safeZ = charZ; safeY = charY
        if (!softCam) { camX = charX; camZ = charZ; camHeading = heading }
    }

    /** Project a world point to screen fractions (x right, y down), or null if behind. */
    private val projTmp = FloatArray(4)
    private val projOut = FloatArray(4)
    private fun project(x: Float, y: Float, z: Float): FloatArray? {
        projTmp[0] = x; projTmp[1] = y; projTmp[2] = z; projTmp[3] = 1f
        Matrix.multiplyMV(projOut, 0, vp, 0, projTmp, 0)
        val w = projOut[3]
        if (w <= 0.0001f) return null
        return floatArrayOf(projOut[0] / w * 0.5f + 0.5f, 1f - (projOut[1] / w * 0.5f + 0.5f))
    }

    private fun publishLabels() {
        if (helpers.isEmpty()) { if (labels.isNotEmpty()) labels = emptyList(); return }
        val list = ArrayList<ScreenLabel>(helpers.size)
        for (h in helpers) {
            if (h.opponent || h.greeter || h.name.isEmpty()) continue   // enemies/column helper: no floating tag
            val p = project(h.x, h.y + CHAR_HEIGHT * 0.95f, h.z) ?: continue
            if (p[0] < -0.25f || p[0] > 1.25f || p[1] < -0.25f || p[1] > 1.25f) continue
            list.add(ScreenLabel(h.name, p[0], p[1]))
        }
        labels = list
    }

    /** The direction the run should go. Non-fork pieces always go straight (cut-outs are
     *  the player's to jump/strafe/fall). On a FORK, follow the lane the player is ACTUALLY
     *  on: march a fan of directions from the player and pick the one whose floor runs
     *  furthest ahead (tie-break nearest the current heading). The void between fork arms
     *  cuts the march short toward the arm you're NOT on, so strafing onto an arm makes it
     *  the longest run and the run + camera bend to follow that arm's curve. */
    private fun roadDirection(onFork: Boolean): Float {
        val sg = groundAt(charX + kotlin.math.sin(heading) * 15f, charZ - kotlin.math.cos(heading) * 15f)
        val straightSolid = sg != null && sg <= charY + 2.0f
        // On the MAIN LANE (near the centre axis), non-fork pieces run straight along world
        // −Z (heading 0): this re-straightens after a rejoining fork and never dodges a
        // cut-out. But if you're out on a BRANCH (well off the axis) — including a branch
        // that runs past where its fork tile ends — keep following the local road so the
        // run/camera don't snap forward and fling you off; you ride the branch to its end.
        // The boulder-hill is a WIDE dodging platform — never redirect the run there, so you can
        // strafe far to either side to avoid rocks (as long as there's floor).
        if (tileAt(charZ)?.piece == "bouderhill") { roadAhead = straightSolid; return 0f }
        val onMainLane = kotlin.math.abs(charX) < 3.5f
        if (!onFork && onMainLane) { roadAhead = straightSolid; return 0f }

        var best = heading; var bestRun = 0; var bestErr = Float.MAX_VALUE
        var a = heading - 1.1f
        while (a <= heading + 1.1f + 1e-4f) {
            val sa = kotlin.math.sin(a); val ca = kotlin.math.cos(a)
            var run = 0; var t = 3f
            while (t <= 33f) {
                val g = groundAt(charX + sa * t, charZ - ca * t)
                if (g != null && g <= charY + 2.0f) { run++; t += 3f } else break
            }
            if (run > 0) {
                val err = kotlin.math.abs(a - heading)
                if (run > bestRun || (run == bestRun && err < bestErr)) {
                    bestRun = run; best = a; bestErr = err
                }
            }
            a += 0.1f
        }
        roadAhead = bestRun > 0
        return best
    }

    /** Drop the character onto the next full-lane solid ground at or beyond [fromZ]
     *  (scanning forward), centred and facing forward — so a fall is never relived. */
    private fun respawnAhead(fromZ: Float) {
        var z = fromZ
        var probes = 0
        while (probes < 800 && z > farZ + 40f) {
            val g = groundAt(0f, z)
            if (g != null) {
                charX = 0f; heading = 0f; charZ = z
                charY = g; vSpeed = 0f; onGround = true; safeZ = z; safeY = g
                camX = charX; camZ = charZ; camHeading = heading
                return
            }
            z -= 1.5f; probes++
        }
        charX = 0f; heading = 0f; charZ = tileStartZ - 12f
        charY = groundAt(0f, charZ) ?: 0f
        vSpeed = 0f; onGround = true; safeZ = charZ; safeY = charY
        camX = charX; camZ = charZ; camHeading = heading
    }

    /** Drop the character a tile BEFORE where they fell (stepping back past any fork
     *  "choice" tiles), centred on the main lane and facing forward — so a fall sends them
     *  back to retry the run-up rather than ahead past the hazard. */
    private fun respawnBefore(fromZ: Float) {
        var idx = tiles.indexOfFirst { fromZ <= it.zNear && fromZ >= it.zFar }
        if (idx < 0) idx = tiles.indexOfLast { fromZ <= it.zNear }.coerceAtLeast(0)
        var j = idx - 1                                   // a piece earlier (higher z)
        while (j > 0 && tiles[j].fork) j--               // never respawn onto a choice tile
        val t = tiles[j.coerceAtLeast(0)]
        val z = (t.zNear + t.zFar) * 0.5f
        val g = groundAt(0f, z) ?: groundAt(0f, t.zNear - 1f)
        charX = 0f; heading = 0f; charZ = z
        charY = g ?: 0f; vSpeed = 0f; onGround = true; safeZ = charZ; safeY = charY
        camX = charX; camZ = charZ; camHeading = heading
    }

    private fun setupCamera() {
        // Finish: fixed cam behind + above the podium, framing the winners' steps (a modest pull-back).
        if (runState == RunState.FINISH) {
            val cz = finishPodiumZ
            val back = 40f + camZoom * 18f
            val up = 11f + camZoom * 10f
            Matrix.setLookAtM(view, 0, 0f, up, cz + back, 0f, 5f, cz, 0f, 1f, 0f)
            return
        }
        // Camera sits behind along the SMOOTHED heading/position and looks ahead, so it
        // turns to follow a curving branch but never jitters with the run.
        val fx = kotlin.math.sin(camHeading); val fz = -kotlin.math.cos(camHeading)
        val eyeX = camX - fx * CAM_BACK; val eyeZ = camZ - fz * CAM_BACK
        // On a descent the ground BEHIND the player (up the slope) is higher than
        // charY + CAM_HEIGHT, which sinks the eye under the track. Keep the eye clear of
        // whatever ground sits beneath it.
        var eyeY = charY + CAM_HEIGHT
        // Cap the sample so a high overhang (the hut roof) isn't mistaken for ground to clear
        // — that was yanking the eye up over the hut ("tripping" the camera).
        val gBehind = groundAt(eyeX, eyeZ, charY + CAM_HEIGHT + 3f)
        if (gBehind != null && gBehind + CAM_CLEARANCE > eyeY) eyeY = gBehind + CAM_CLEARANCE
        Matrix.setLookAtM(
            view, 0,
            eyeX, eyeY, eyeZ,
            camX + fx * 6f, charY + 1.3f, camZ + fz * 6f,
            0f, 1f, 0f,
        )
    }

    // ── Ground sampling from the tile meshes ────────────────────────────────────
    /** The tile whose world span contains [z] (null beyond the track). Tiles are
     *  stitched in descending-z order, so a short linear scan suffices. */
    private fun tileAt(z: Float): TileInstance? {
        for (t in tiles) if (z <= t.zNear && z >= t.zFar) return t
        return null
    }

    /** Ground under the feet, bridging THIN gaps: if there's no surface exactly under the
     *  player but solid ground sits within a few units ahead/behind along the heading
     *  (e.g. the narrow tip where a fork first splits), stay supported instead of dropping
     *  through. Wide gaps (real jumps) still return null and you fall. */
    private fun groundUnderFeet(): Float? {
        val here = groundAt(charX, charZ, charY + HEAD_ROOM)
        if (here != null) return here
        // Bridging thin gaps is ONLY for the narrow tip where a fork first splits — regular
        // gap pieces (centregap, gaps) must let the character drop through.
        if (tileAt(charZ)?.fork != true) return null
        val fx = kotlin.math.sin(heading); val fz = -kotlin.math.cos(heading)
        var d = 1.5f
        while (d <= 4.0f) {
            groundAt(charX + fx * d, charZ + fz * d, charY + HEAD_ROOM)?.let { return it }
            groundAt(charX - fx * d, charZ - fz * d, charY + HEAD_ROOM)?.let { return it }
            d += 1.5f
        }
        return null
    }

    /** Height of the track surface at (x,z), or null if there's nothing to stand on
     *  there (over a carved gap, or past the track edge). [ceil] discards surfaces above it
     *  (e.g. the toll-booth roof overhanging the lane) so overhead geometry isn't a "wall". */
    private fun groundAt(x: Float, z: Float, ceil: Float = Float.MAX_VALUE): Float? {
        val t = tileAt(z) ?: return null
        val tris = t.topTris
        var best: Float? = null
        var i = 0
        while (i + 9 <= tris.size) {
            val y = sampleTri(tris, i, x, z)
            val b = best
            if (y != null && y <= ceil && (b == null || y > b)) best = y
            i += 9
        }
        return best
    }

    /** If (px,pz) is inside triangle i's XZ projection, return the interpolated Y. */
    private fun sampleTri(t: FloatArray, i: Int, px: Float, pz: Float): Float? {
        val ax = t[i]; val ay = t[i + 1]; val az = t[i + 2]
        val bx = t[i + 3]; val by = t[i + 4]; val bz = t[i + 5]
        val cx = t[i + 6]; val cy = t[i + 7]; val cz = t[i + 8]
        val d = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz)
        if (kotlin.math.abs(d) < 1e-6f) return null
        val l1 = ((bz - cz) * (px - cx) + (cx - bx) * (pz - cz)) / d
        val l2 = ((cz - az) * (px - cx) + (ax - cx) * (pz - cz)) / d
        val l3 = 1f - l1 - l2
        if (l1 < -0.001f || l2 < -0.001f || l3 < -0.001f) return null
        return l1 * ay + l2 * by + l3 * cy
    }

    // ── Tile layout (the scripted course) ────────────────────────────────────────
    //  The first scripted run: 10 regular pieces, a right-hand branch (forkin), the TOLL
    //  obstacle (sptoll), 10 more regular pieces, then the HILL obstacle. Pieces are
    //  stitched back-edge to front-edge by each piece's own central-lane length.
    private fun buildCourse(): List<String> {
        val course = ArrayList<String>()
        // Varied "regular" running pieces (mostly footing + ramps, occasional gap/spike), cycled.
        val REGS = listOf(
            "base", "rampm", "base", "base", "gapleft", "base", "rampl", "base", "spikes1", "base",
            "base", "rampr", "base", "centregap", "base", "rampmm", "base", "base", "gapright", "base",
            "base", "rampl", "base", "spikes", "base", "rampadd", "base", "base", "rampm", "base")
        var ri = 0
        fun regs(n: Int) = repeat(n) { course.add(REGS[ri % REGS.size]); ri++ }

        if (DEBUG_FINISH_ONLY) {                           // TEMP: just the last obstacle + the finish
            regs(4); course.add("RECKON"); course.add("spwall4"); regs(6); course.add("end")
            return course
        }
        if (DEBUG_SKIP_TO_CHOICES) {                       // short test run: straight to the choices
            regs(5)
            for (s in listOf("CALL", "MESSAGE", "HELP", "VISIT", "NEED")) { course.add(s); regs(5) }
            regs(4); course.add("end")
            return course
        }

        // 1) START ALONE — 20 regular (the competitors are already off ahead)
        regs(20)
        // 2) HELP STAGE — 4 different obstacles you get HELPED through, 10 regular apart
        // (gap/wall variants + the size-varying boulder keep obstacle difficulty changing)
        for (o in listOf("sptoll", "hill", "spgap2", "spwall3")) { regs(10); course.add(o) }   // spwall3/4 tall enough to need the ladder
        // 3) GROWTH STAGE — 15 regular then an obstacle, ×3 (varied types / helper counts)
        for (o in listOf("spring01blend", "bouderhill", "hill")) { regs(15); course.add(o) }
        // 4) HELPING STAGE — 15 regular, 8 mixed helping scenarios (regular between), 15 regular
        regs(15)
        for (s in listOf("CALL", "MESSAGE", "HELP", "VISIT", "CALL", "MESSAGE", "VISIT", "NEED")) { course.add(s); regs(5) }
        regs(15)
        // 5) RECKONING — the same obstacle KINDS again (friends come or you pay), with a couple of
        // helping chances mixed in to keep it interesting.
        course.add("RECKON")
        for ((i, item) in listOf("sptoll", "CALL", "hill", "spring01blend", "MESSAGE", "spgap4", "spwall4").withIndex()) {
            regs(if (i == 0) 8 else 16); course.add(item)
        }
        // 6) FINISH
        regs(8); course.add("end")
        return course
    }

    /** Lay one tile whose BACK edge sits at [frontZ]; return the new front edge (its far edge).
     *  Also records the special tiles (obstacles, forks). */
    private fun layTile(name: String, frontZ: Float, isDetour: Boolean = false): Float {
        val piece = loadTilePiece(name) ?: return frontZ
        val tp = piece.second
        val reversed = name in REVERSED_PIECES
        val flip = FLIP_Z != reversed
        val a = SCALE_Z * tp.zMin; val b = SCALE_Z * tp.zMax
        val pzMin = if (flip) -b else a
        val pzMax = if (flip) -a else b
        val zRef = frontZ - pzMax
        val zFar = zRef + pzMin
        val fork = name.startsWith("fork")
        val tris = bakeTopTris(piece.first, zRef, flip)
        var minY = Float.MAX_VALUE
        var kk = 1; while (kk < tris.size) { if (tris[kk] < minY) minY = tris[kk]; kk += 3 }
        val yOff = if (minY != Float.MAX_VALUE && minY > 1.5f) -(minY - 0.1f) else 0f
        if (yOff != 0f) { var j = 1; while (j < tris.size) { tris[j] += yOff; j += 3 } }
        tiles.add(TileInstance(name, zRef, frontZ, zFar, fork, TILE_GREY, tris, reversed, yOff))
        // Detour tiles are geometry ONLY (an obstacle piece here is just scenery you help the
        // friend past) — never capture them as the live obstacle/fork mechanics.
        if (isDetour) return zFar
        // Obstacle tiles (sptoll/hill/spring01blend/spgap4/spwall4/bouderhill) are NOT captured
        // here — they're found & armed by piece name at run time (updateObstacleActivation), so
        // repeats work. Only choices + the basehelp column keep a build-time reference.
        if (name == "basehelp") basehelpTile = tiles.last()   // choices are registered by the caller's tokens
        return zFar
    }

    private fun buildTileLayout(near: Float, far: Float) {
        tiles.clear()
        tollTile = null; tollPaid = false; needTollTile = null
        hillTile = null; boulderDestroyed = false; refuseTimer = 0f
        ringTile = null; fightDone = false; fightActive = false
        gapTile = null; gapDone = false; gapActive = false
        wallTile = null; wallDone = false; wallActive = false
        bouderTile = null; hazBoulders.clear(); hazTimer = 0f
        activeObstacle = null
        choiceForks.clear(); choiceActive = false; choiceAcceptTimer = 0f; speedBoostTimer = 0f
        basehelpTile = null; columnHelper = null; helpAccepted = false; helpGreetDone = false
        visitCompanion = null; onDetour = false; needExcursion = false; needPaid = false; needPayZip = -1f
        companionVy = 0f; companionGrounded = true
        friendsBanked.clear()
        // Carry the saved run's social ledger into the new course.
        refusals = resumeRefusals
        repeat(resumeFriends) { friendsBanked.add("friend$it") }
        refusalCount = refusals; friendCount = friendsBanked.size
        lonelyEnding = refusals >= 3
        coinCount = resumeCoins
        reckoningFromZ = -1e9f
        reckoningEntered = false; helpCredits = 0; obstacleInReckoning = false; obstacleFriendHelped = true; noFriendsMsg = ""
        tileStartZ = near
        var frontZ = tileStartZ                         // world z where the next BACK edge sits
        // Choice tokens carry an EXPLICIT type (so any mix/order works); everything else is a tile.
        for (name in buildCourse()) {
            when (name) {
                "CALL" -> { frontZ = layTile("fork", frontZ); choiceForks.add(ChoiceFork(tiles.last(), ChoiceType.CALL)) }
                "MESSAGE" -> { frontZ = layTile("forkud", frontZ); choiceForks.add(ChoiceFork(tiles.last(), ChoiceType.MESSAGE)) }
                "HELP" -> { frontZ = layTile("fork", frontZ); choiceForks.add(ChoiceFork(tiles.last(), ChoiceType.HELP)); frontZ = layTile("basehelp", frontZ) }
                "VISIT" -> { frontZ = layTile("forkoutr", frontZ); choiceForks.add(ChoiceFork(tiles.last(), ChoiceType.VISIT)) }
                "NEED" -> { frontZ = layTile("forkoutr", frontZ); choiceForks.add(ChoiceFork(tiles.last(), ChoiceType.NEED)) }
                "RECKON" -> reckoningFromZ = frontZ           // consequences start here (friend-gate on)
                else -> frontZ = layTile(name, frontZ)
            }
        }

        // VISIT detour: a short parallel track placed FAR below the main course (a gap keeps
        // you from running into it) — only reached by teleport. forkoutl marks the way back.
        val mainEnd = frontZ
        // Each branch fork gets its own parallel DETOUR, placed FAR below the main course (a gap
        // → only reachable by teleport), with a mix of regular pieces + a forkoutl at the end.
        // VISIT = a scenic run together; NEED = the friend is stuck at an OBSTACLE (a toll booth)
        // you help them past (you cover their toll — see updateVisit's needTollZ deduction).
        val visitStretch = listOf("base", "rampm", "base", "gapleft", "spikes1",
                                  "base", "rampl", "centregap", "base", "base")
        val needStretch = listOf("base", "rampm", "base", "base", "sptoll",
                                 "base", "base", "rampl", "base", "base")
        var detourFront = mainEnd - 120f
        for (bf in choiceForks) {
            if (bf.type != ChoiceType.VISIT && bf.type != ChoiceType.NEED) continue
            bf.returnZ = bf.tile.zFar - 2f
            var dz = detourFront
            bf.detourStartZ = dz - 4f
            val stretch = if (bf.type == ChoiceType.NEED) needStretch else visitStretch
            for (n in stretch) {
                dz = layTile(n, dz, isDetour = true)
                if (n == "sptoll") needTollTile = tiles.last()   // the friend's toll (own barrier/paid)
            }
            bf.detourEndZ = dz + 2f
            val fend = layTile("forkoutl", dz, isDetour = true)   // return-branch visual
            detourFront = fend - 340f                      // BIG gap → the next detour is beyond the
                                                           // fog (FOG_FAR 230), so no other track shows
        }
        farZ = mainEnd - 30f                             // MAIN course end (detours are teleport-only)

        // Obstacles are now armed lazily as the player nears each instance (updateObstacleActivation),
        // so the same obstacle type can repeat down the course — no per-obstacle setup here.
        // Helper waving for help from the basehelp column.
        basehelpTile?.let { bt ->
            val cz = bt.zRef + COL_REL_Z
            val name = seenNames.randomOrNull() ?: helperNames.firstOrNull() ?: "Friend"
            val color = HELPER_COLORS[helperColorIdx % HELPER_COLORS.size]; helperColorIdx++
            columnHelper = Helper(COL_X, cz, COL_TOP_Y, COL_X, cz, color, name,
                arrived = true, facing = (-Math.PI * 0.5).toFloat(), greeter = true)
            helpers.add(columnHelper!!)
        }
    }

    /** Arm the nearest un-done obstacle as you approach; retire it once you're past. Only one is
     *  live at a time — its setup runs on arrival and its visuals clear when you leave, so the
     *  SAME obstacle type can appear many times down the course. */
    private fun updateObstacleActivation() {
        if (onDetour) return
        val a = activeObstacle
        if (a != null) {
            if (charZ < a.zFar - 6f) { a.done = true; deactivateObstacle() }   // passed it → done
            return                                                            // one live at a time
        }
        val next = tiles.firstOrNull {
            isObstacle(it.piece) && !it.done && charZ >= it.zFar && charZ <= it.zNear + ACTIVATE_DIST
        } ?: return
        activateObstacle(next)
    }

    private fun activateObstacle(t: TileInstance) {
        activeObstacle = t
        when {
            t.piece == "sptoll" -> { tollTile = t; tollPaid = false; setupBooth() }
            t.piece == "hill" -> { hillTile = t; boulderDestroyed = false; refuseTimer = 0f; setupHill() }
            t.piece == "spring01blend" -> { ringTile = t; fightDone = false; fightActive = false; setupRing() }
            t.piece.startsWith("spgap") -> { gapTile = t; gapDone = false; gapActive = false; setupGap() }
            t.piece.startsWith("spwall") -> { wallTile = t; wallDone = false; wallActive = false; setupWall() }
            t.piece == "bouderhill" -> { bouderTile = t; bouderEntranceZ = t.zNear - 3f; hazBoulders.clear(); hazTimer = 0f; setupBouderCrest() }
        }
        decideObstacleHelp(t)                            // friend covers it, or nobody comes → pay
    }

    private fun deactivateObstacle() {
        // a little catch-up boost after clearing an obstacle (the racers pulled ahead while you stopped)
        if (activeObstacle?.piece != "bouderhill") speedBoostTimer = maxOf(speedBoostTimer, 1.4f)
        activeObstacle = null
        tollTile = null; hillTile = null; ringTile = null; gapTile = null; wallTile = null; bouderTile = null
        boothNpc = null; hazBoulders.clear()
        helpers.removeAll { it.opponent || it.pusher || it.fighter || it.isBooth || it.carriesCoin || it.climber }
        boulderDestroyed = false; refuseTimer = 0f
        fightDone = false; fightActive = false
        gapDone = false; gapActive = false
        wallDone = false; wallActive = false
        tollPaid = false
    }

    /** Idle attendant in the toll booth (booth on the RIGHT, +x; sit them on the floor at y≈0). */
    private fun setupBooth() {
        val tt = tollTile ?: return
        val cz = (tt.zNear + tt.zFar) * 0.5f
        boothNpc = Helper(4.0f, cz, 0f, 4.0f, cz, CHAR_GREY, "",
            arrived = true, facing = (-Math.PI * 0.5).toFloat(), isBooth = true, scale = 1.5f)
    }

    /** Highest walkable point along the boulder-hill centre (ignoring the high structures). */
    private fun setupBouderCrest() {
        val bt = bouderTile ?: return
        var crestZ = bt.zNear; var crestY = -1e9f
        var z = bt.zNear
        while (z > bt.zFar) {
            val g = groundAt(0f, z, 30f)
            if (g != null && g > crestY) { crestY = g; crestZ = z }
            z -= 1f
        }
        bouderCrestZ = crestZ
    }

    /** Find the wall in the spwall tile: the z-band whose top surface is high (the wall top). */
    private fun setupWall() {
        val wt = wallTile ?: return
        wallBaseY = groundAt(0f, wt.zNear - 1f) ?: 0f
        var nearZ = wt.zFar; var farZ = wt.zFar; var top = wallBaseY
        var inWall = false
        var z = wt.zNear
        while (z > wt.zFar) {
            val g = groundAt(0f, z) ?: -99f
            val high = g > wallBaseY + 1.3f          // detect medium+ walls (spwall3=1.6, spwall4=2.0)
            if (!inWall && high) { nearZ = z; inWall = true }
            if (inWall) top = maxOf(top, g)
            if (inWall && !high) { farZ = z; break }
            z -= 0.25f
        }
        wallNearZ = nearZ; wallFarZ = farZ; wallTopY = top
        wallNeeded = kotlin.math.ceil(((wallTopY - wallBaseY) / STACK_H).toDouble()).toInt().coerceAtLeast(1)
    }

    /** Find the gap span in the spgap tile (near edge = where the floor first drops out). */
    private fun setupGap() {
        val gt = gapTile ?: return
        gapEdgeY = groundAt(0f, gt.zNear - 1f) ?: 0f
        var z = gt.zNear; var nearZ = gt.zFar; var farZ = gt.zFar
        // scan from the entrance: first z with no floor = near edge; next z with floor = far edge
        var inGap = false
        while (z > gt.zFar) {
            val solid = groundAt(0f, z) != null
            if (!inGap && !solid) { nearZ = z; inGap = true }
            if (inGap && solid) { farZ = z; break }
            z -= 0.25f
        }
        gapNearZ = nearZ; gapFarZ = farZ; gapWidth = (nearZ - farZ).coerceAtLeast(0f)
        toppleT = 0f; gapFallT = 0f
        gapClimbers = 0
    }

    /** Stage the fight on the open floor JUST BEFORE the columns (cheap centre-line scan for
     *  the first tall structure), facing the incoming player. */
    private fun setupRing() {
        val rt = ringTile ?: return
        var colFront = rt.zFar + 6f
        var z = rt.zNear - 2f                         // skip the entrance lip
        while (z > rt.zFar) {                          // first z with tall geometry over centre
            val g = groundAt(0f, z)
            if (g != null && g > 3f) { colFront = z; break }
            z -= 0.5f
        }
        val cz = (colFront + 6f).coerceIn(rt.zFar + 3f, rt.zNear - 3f)
        val faceUp = Math.PI.toFloat()                // face +z (toward the player running −z)
        ringFoes = 3 + kotlin.math.abs((rt.zNear * 0.17f).toInt()) % 3   // 3..5, varies per ring
        for (i in 0 until ringFoes) {
            val ox = (i - (ringFoes - 1) * 0.5f) * 1.3f
            val oz = cz + (i % 2) * 0.8f              // slight stagger so it reads as a mob
            helpers.add(Helper(ox, oz, groundAt(ox, oz) ?: 0f, ox, oz, ENEMY_COL, "",
                arrived = true, facing = faceUp, opponent = true, deathClip = deathClips[i % 4]))
        }
        ringStopZ = cz + 2.6f                          // halt right up in front of the mob
        fightFoes = ringFoes
        fightAllies = 1
    }

    /** Place the boulder at the START of the approach flat (opposite the hut). The player
     *  pushes it solo across the flat; at the heel of the climb they can't go on alone. */
    private fun setupHill() {
        val ht = hillTile ?: return
        val approachY = groundAt(0f, ht.zNear - 2f) ?: 0f
        // Heel = first z (running from the entrance) where the ground starts climbing. The
        // ceiling keeps the scan on the walkable surface, not the hut roof high above.
        heelZ = ht.zFar
        var z = ht.zNear
        while (z > ht.zFar) {
            val g = groundAt(0f, z, approachY + 12f)
            if (g != null && g > approachY + 2f) { heelZ = z; break }
            z -= 0.5f
        }
        // Boulder SIZE varies per instance (stable per tile) — bigger rock needs more pushers.
        val sizes = floatArrayOf(1.4f, 2.0f, 2.7f)
        val si = kotlin.math.abs((ht.zNear * 0.11f).toInt()) % sizes.size
        boulderRadiusW = sizes[si]
        boulderScale = boulderRadiusW / boulderLocalR
        boulderX = 0f
        boulderZ = ht.zNear - boulderRadiusW - 1f    // at the start of the flat
        boulderY = (groundAt(0f, boulderZ) ?: approachY) + boulderRadiusW
        boulderMaxY = boulderY
        boulderSpin = 0f
        hutExplodeZ = ht.zFar + 6f                   // deep inside the hut (occluded when it vanishes)
        hillNeeded = (si + 2).coerceAtMost(PUSHER_SLOTS.size); hillHave = 0
    }

    /** Transform a piece's local vertex into world space (scale, optional flip, place). */
    private fun toWorld(vx: Float, vy: Float, vz: Float, zRef: Float, out: FloatArray, o: Int,
                        flip: Boolean = FLIP_Z) {
        var x = SCALE_X * vx
        var z = SCALE_Z * vz
        if (flip) { x = -x; z = -z }
        out[o] = x
        out[o + 1] = SCALE_Y * vy - TILE_TOP
        out[o + 2] = zRef + z
    }

    /** Collect the up-facing world-space triangles of a placed tile (for ground tests). */
    private fun bakeTopTris(pos: FloatArray, zRef: Float, flip: Boolean = FLIP_Z): FloatArray {
        val out = ArrayList<Float>()
        val w = FloatArray(9)
        var i = 0
        while (i + 9 <= pos.size) {
            toWorld(pos[i], pos[i + 1], pos[i + 2], zRef, w, 0, flip)
            toWorld(pos[i + 3], pos[i + 4], pos[i + 5], zRef, w, 3, flip)
            toWorld(pos[i + 6], pos[i + 7], pos[i + 8], zRef, w, 6, flip)
            val ux = w[3] - w[0]; val uy = w[4] - w[1]; val uz = w[5] - w[2]
            val vx = w[6] - w[0]; val vy = w[7] - w[1]; val vz = w[8] - w[2]
            var ny = uz * vx - ux * vz
            val len = Math.sqrt(((uy * vz - uz * vy) * (uy * vz - uz * vy) +
                ny * ny + (ux * vy - uy * vx) * (ux * vy - uy * vx)).toDouble()).toFloat().coerceAtLeast(1e-6f)
            ny /= len
            if (ny > GROUND_NY) for (k in 0 until 9) out.add(w[k])
            i += 9
        }
        return out.toFloatArray()
    }

    /** Upload a piece's geometry once (positions from ObjLoader, flat normals here),
     *  cached by name. Returns (positions, piece) — positions reused for ground bake. */
    private fun loadTilePiece(name: String): Pair<FloatArray, TilePiece>? {
        val cached = tilePieces[name]
        if (cached != null) return tilePositions[name]!! to cached
        val path = tilePath(name) ?: return null
        val overrides = TILE_GROUP_COLORS[name]
        val posList = ArrayList<Float>()       // ALL geometry (used for the ground bake)
        val drawList = ArrayList<Float>()      // grey base geometry (overridden groups pulled out)
        val parts = ArrayList<TilePart>()
        if (overrides == null) {
            for (g in ObjLoader.load(context.assets, path)) for (v in g.verts) posList.add(v)
            drawList.addAll(posList)
        } else {
            for ((gname, verts) in loadNamedGroups(path)) {
                for (v in verts) posList.add(v)
                val col = overrides[gname]
                if (col != null) parts.add(TilePart(makeVao(verts, computeFlatNormals(verts)), verts.size / 3, col))
                else for (v in verts) drawList.add(v)
            }
        }
        if (posList.isEmpty()) return null
        tileParts[name] = parts
        val pos = drawList.toFloatArray()
        // Stitch by the CENTRAL through-lane's z-extent (verts near x=0), not the full
        // bounding box — so a fork's side branch can stick out past the road without
        // pushing the next piece away (the main road stays connected).
        val full = posList.toFloatArray()
        var zMin = Float.MAX_VALUE; var zMax = -Float.MAX_VALUE
        var k = 0
        while (k + 3 <= full.size) {
            if (kotlin.math.abs(full[k]) <= 1.2f) { zMin = minOf(zMin, full[k + 2]); zMax = maxOf(zMax, full[k + 2]) }
            k += 3
        }
        if (zMin > zMax) { zMin = -5.67f; zMax = 5.67f }   // fallback: standard tile length
        val piece = TilePiece(makeVao(pos, computeFlatNormals(pos)), pos.size / 3, zMin, zMax)
        tilePieces[name] = piece
        tilePositions[name] = full
        return full to piece
    }

    /** Parse an OBJ into positions grouped by object/group name ('o'/'g' lines), each a
     *  triangle soup. Used to colour individual parts of a tile (the toll sign). */
    private fun loadNamedGroups(path: String): LinkedHashMap<String, FloatArray> {
        val verts = ArrayList<FloatArray>()
        val out = LinkedHashMap<String, ArrayList<Float>>()
        var cur = "(root)"
        context.assets.open(path).bufferedReader().forEachLine { raw ->
            val t = raw.trim()
            when {
                t.startsWith("v ") -> {
                    val p = t.split(Regex("\\s+"))
                    verts.add(floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat()))
                }
                t.startsWith("o ") || t.startsWith("g ") -> { cur = t.substring(2).trim(); out.getOrPut(cur) { ArrayList() } }
                t.startsWith("f ") -> {
                    val idx = t.split(Regex("\\s+")).drop(1).map {
                        val n = it.substringBefore('/').toInt(); if (n < 0) verts.size + n else n - 1
                    }
                    val list = out.getOrPut(cur) { ArrayList() }
                    for (j in 1 until idx.size - 1) for (vi in intArrayOf(idx[0], idx[j], idx[j + 1])) {
                        val v = verts[vi]; list.add(v[0]); list.add(v[1]); list.add(v[2])
                    }
                }
            }
        }
        val result = LinkedHashMap<String, FloatArray>()
        for ((kk, vv) in out) if (vv.isNotEmpty()) result[kk] = vv.toFloatArray()
        return result
    }

    /** One flat normal per triangle (the loader returns a triangle soup, no normals). */
    private fun computeFlatNormals(pos: FloatArray): FloatArray {
        val nrm = FloatArray(pos.size)
        var i = 0
        while (i + 9 <= pos.size) {
            val ux = pos[i + 3] - pos[i]; val uy = pos[i + 4] - pos[i + 1]; val uz = pos[i + 5] - pos[i + 2]
            val vx = pos[i + 6] - pos[i]; val vy = pos[i + 7] - pos[i + 1]; val vz = pos[i + 8] - pos[i + 2]
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val len = Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat().coerceAtLeast(1e-6f)
            nx /= len; ny /= len; nz /= len
            for (k in 0 until 3) { nrm[i + k * 3] = nx; nrm[i + k * 3 + 1] = ny; nrm[i + k * 3 + 2] = nz }
            i += 9
        }
        return nrm
    }

    private fun drawTiles() {
        GLES30.glUseProgram(flatProg)
        GLES30.glUniformMatrix4fv(fVP, 1, false, vp, 0)
        for (t in tiles) {
            if (t.zFar > charZ + 30f || t.zNear < charZ - 300f) continue
            val piece = tilePieces[t.piece] ?: continue
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, 0f, -TILE_TOP + t.yOffset, t.zRef)
            if (FLIP_Z != t.reversed) Matrix.rotateM(modelMat, 0, 180f, 0f, 1f, 0f)
            Matrix.scaleM(modelMat, 0, SCALE_X, SCALE_Y, SCALE_Z)
            GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
            GLES30.glUniform3f(fColor, t.color[0], t.color[1], t.color[2])
            // The finish podium has inward-wound faces (missing front faces) — draw it two-sided.
            if (t.piece == "end") GLES30.glDisable(GLES30.GL_CULL_FACE)
            GLES30.glBindVertexArray(piece.vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, piece.vertCount, GLES30.GL_UNSIGNED_INT, 0)
            // Coloured sub-parts (toll sign: red rim / white face / black text) share the transform.
            for (part in tileParts[t.piece] ?: emptyList()) {
                GLES30.glUniform3f(fColor, part.color[0], part.color[1], part.color[2])
                GLES30.glBindVertexArray(part.vao)
                GLES30.glDrawElements(GLES30.GL_TRIANGLES, part.vertCount, GLES30.GL_UNSIGNED_INT, 0)
            }
            if (t.piece == "end") GLES30.glEnable(GLES30.GL_CULL_FACE)
        }
        GLES30.glBindVertexArray(0)
    }

    // Animation clock: the run cycle is sped up so it matches how fast the camera moves.
    private val RUN_ANIM_RATE = 2.0f
    private fun animPhase(clip: Int): Float = elapsed * (if (clip == runClip) RUN_ANIM_RATE else 1.3f)

    // ── Draw the character (skinned) ─────────────────────────────────────────────
    private fun drawCharacter() {
        GLES30.glUseProgram(skinProg)
        GLES30.glUniformMatrix4fv(sVP, 1, false, vp, 0)
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, 0, boneUbo)

        // Fight death → death; brawling → boxing; sizing up the mob → fight-idle; shoving the
        // boulder → push; head-shake → refuse; reading a dialog / watching → idle; else run/jump.
        val clip = when {
            runState == RunState.HAZ_HIT -> deathClips[1]      // knocked flat by a rock
            playerDying -> playerDeathClip
            runState == RunState.HELP_GREET -> if (greetTimer >= CLIMB_DOWN_T) handshakeClip else idleClip
            choiceAcceptTimer > 0f -> when (choiceAcceptType) {
                ChoiceType.MESSAGE -> textingClip; ChoiceType.HELP -> handshakeClip; else -> phoneClip
            }
            runState == RunState.WALL_CLIMB -> climbClip
            runState == RunState.FIGHTING -> boxingClip
            runState == RunState.FIGHT_PROMPT -> fightIdleClip
            runState == RunState.HILL_SOLO || runState == RunState.HILL_PUSH -> pushClip
            refuseTimer > 0f -> refuseClip
            runState == RunState.FINISH -> if (finishTimer < FINISH_WALK) runClip else idleClip
            runState != RunState.RUNNING -> idleClip
            onGround -> runClip
            else -> jumpClip
        }
        val phase = when {
            runState == RunState.HAZ_HIT -> minOf(hitTimer, model.clipDuration(clip) - 0.03f)
            playerDying -> minOf(deathElapsed(), model.clipDuration(clip) - 0.03f)
            else -> animPhase(clip)
        }
        val bones = model.jointMatrices(clip, phase)
        boneBuffer.clear(); boneBuffer.put(bones); boneBuffer.position(0)
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, boneUbo)
        GLES30.glBufferSubData(GLES30.GL_UNIFORM_BUFFER, 0, bones.size * 4, boneBuffer)

        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, charX, footOffset + charY, charZ)
        Matrix.rotateM(modelMat, 0, 180f - Math.toDegrees(heading.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.scaleM(modelMat, 0, modelScale, modelScale, modelScale)
        GLES30.glUniformMatrix4fv(sModel, 1, false, modelMat, 0)

        for (m in skinnedMeshes) {
            GLES30.glUniform3f(sColor, CHAR_GREY[0], CHAR_GREY[1], CHAR_GREY[2])
            GLES30.glBindVertexArray(m.vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        }
        GLES30.glBindVertexArray(0)
    }

    /** Helper runners + the toll-booth attendant: same skinned mesh as the player, tinted.
     *  Still-moving helpers run; arrived ones (and the attendant) idle. Toll helpers haul a
     *  coin that, on arrival, zips over to the player. */
    private fun drawHelpers() {
        val toDraw = ArrayList<Helper>(helpers.size + 3)
        toDraw.addAll(helpers)
        boothNpc?.let { toDraw.add(it) }
        if (!onDetour) toDraw.addAll(competitors)         // racers on the side-tracks (main track only)
        if (runState == RunState.FINISH) toDraw.addAll(backdropRunners)   // the pan-out crowd
        if (toDraw.isEmpty()) return

        GLES30.glUseProgram(skinProg)
        GLES30.glUniformMatrix4fv(sVP, 1, false, vp, 0)
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, 0, boneUbo)

        for (h in toDraw) {
            // Death → death; fight → box; opponents/arrived allies size up in fight-idle;
            // wave / push / carry as before; the booth attendant + plain helpers idle/run.
            val clip = when {
                h.dying -> h.deathClip
                h.companion && needExcursion && !needPaid -> idleClip   // requester waiting at the toll
                h.companion && !h.waving -> runClip
                h.greeter -> when {
                    runState == RunState.HELP_GREET && greetTimer < CLIMB_DOWN_T -> climbDownClip
                    runState == RunState.HELP_GREET && greetTimer < CLIMB_DOWN_T + GREET_WALK_T -> runClip
                    runState == RunState.HELP_GREET -> handshakeClip
                    helpGreetDone -> idleClip
                    else -> helpWaveClip           // waving from the column
                }
                h.climber && h.climbing -> climbClip
                h.waving -> waveClip
                (h.opponent || h.fighter) && runState == RunState.FIGHTING -> boxingClip
                h.opponent -> fightIdleClip
                h.fighter && h.arrived -> fightIdleClip
                h.pusher && runState == RunState.HILL_PUSH -> pushClip
                h.carriesCoin && h.arrived -> carryIdleClip
                h.carriesCoin -> carryRunClip
                h.arrived -> idleClip
                else -> runClip
            }
            val phase = if (h.dying) minOf(deathElapsed(), model.clipDuration(clip) - 0.03f) else animPhase(clip)
            val bones = model.jointMatrices(clip, phase)
            boneBuffer.clear(); boneBuffer.put(bones); boneBuffer.position(0)
            GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, boneUbo)
            GLES30.glBufferSubData(GLES30.GL_UNIFORM_BUFFER, 0, bones.size * 4, boneBuffer)

            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, h.x, footOffset + h.y, h.z)
            Matrix.rotateM(modelMat, 0, 180f - Math.toDegrees(h.facing.toDouble()).toFloat(), 0f, 1f, 0f)
            if (h.pitch != 0f) Matrix.rotateM(modelMat, 0, Math.toDegrees(h.pitch.toDouble()).toFloat(), 1f, 0f, 0f)
            val ms = modelScale * h.scale
            Matrix.scaleM(modelMat, 0, ms, ms, ms)
            GLES30.glUniformMatrix4fv(sModel, 1, false, modelMat, 0)

            for (m in skinnedMeshes) {
                GLES30.glUniform3f(sColor, h.color[0], h.color[1], h.color[2])
                GLES30.glBindVertexArray(m.vao)
                GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.indexCount, GLES30.GL_UNSIGNED_INT, 0)
            }
        }
        GLES30.glBindVertexArray(0)
        drawHelperCoins()
    }

    /** The coin a toll helper carries — held at chest height while running, then lerped over
     *  to the player during the hand-off (zip). */
    private fun drawHelperCoins() {
        if (coinVao == 0) return
        val chest = CHAR_HEIGHT * 0.55f
        var started = false
        val spin = (elapsed * 220f) % 360f
        for (h in helpers) {
            if (!h.carriesCoin) continue
            if (h.zip >= 1f) continue                    // already delivered
            val cx: Float; val cy: Float; val cz: Float
            if (h.zip in 0f..1f) {                        // flying to the player
                val t = h.zip
                cx = h.x + (charX - h.x) * t
                cy = (h.y + chest) + ((charY + chest) - (h.y + chest)) * t
                cz = h.z + (charZ - h.z) * t
            } else {                                      // held in hand while running up
                cx = h.x; cy = h.y + chest; cz = h.z
            }
            if (!started) {
                GLES30.glUseProgram(flatProg)
                GLES30.glUniformMatrix4fv(fVP, 1, false, vp, 0)
                GLES30.glUniform3f(fColor, COIN_COL[0], COIN_COL[1], COIN_COL[2])
                GLES30.glBindVertexArray(coinVao)
                started = true
            }
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, cx, cy, cz)
            Matrix.rotateM(modelMat, 0, spin, 0f, 1f, 0f)
            Matrix.scaleM(modelMat, 0, COIN_SCALE, COIN_SCALE, COIN_SCALE)
            GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, coinVerts, GLES30.GL_UNSIGNED_INT, 0)
        }
        // NEED toll: you automatically hand a coin to the requester (player → companion).
        if (runState == RunState.NEED_PAY && needPayZip in 0f..1f) {
            val c = visitCompanion
            val t = needPayZip
            val tx = c?.x ?: charX; val ty = c?.y ?: charY; val tz = c?.z ?: charZ
            val cx = charX + (tx - charX) * t
            val cy = (charY + chest) + ((ty + chest) - (charY + chest)) * t
            val cz = charZ + (tz - charZ) * t
            if (!started) {
                GLES30.glUseProgram(flatProg)
                GLES30.glUniformMatrix4fv(fVP, 1, false, vp, 0)
                GLES30.glUniform3f(fColor, COIN_COL[0], COIN_COL[1], COIN_COL[2])
                GLES30.glBindVertexArray(coinVao)
                started = true
            }
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, cx, cy, cz)
            Matrix.rotateM(modelMat, 0, spin, 0f, 1f, 0f)
            Matrix.scaleM(modelMat, 0, COIN_SCALE, COIN_SCALE, COIN_SCALE)
            GLES30.glUniformMatrix4fv(fModel, 1, false, modelMat, 0)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, coinVerts, GLES30.GL_UNSIGNED_INT, 0)
        }
        if (started) GLES30.glBindVertexArray(0)
    }

    // ── GL helpers ──────────────────────────────────────────────────────────────
    private fun buildBoneUbo() {
        val ids = IntArray(1); GLES30.glGenBuffers(1, ids, 0); boneUbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, boneUbo)
        GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, model.jointCount * 16 * 4, null, GLES30.GL_DYNAMIC_DRAW)
        val block = GLES30.glGetUniformBlockIndex(skinProg, "JointBlock")
        GLES30.glUniformBlockBinding(skinProg, block, 0)
    }

    private fun uploadCharacterMeshes() {
        for (p in model.primitives) {
            val vao = IntArray(1); GLES30.glGenVertexArrays(1, vao, 0)
            GLES30.glBindVertexArray(vao[0])
            attrib(0, p.positions, 3)
            attrib(1, p.normals, 3)
            attrib(2, p.joints, 4)
            attrib(3, p.weights, 4)
            elementBuffer(p.indices)
            GLES30.glBindVertexArray(0)
            skinnedMeshes.add(SkinnedMesh(vao[0], p.indices.size, p.baseColor))
        }
    }

    private fun computeModelScale() {
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in model.primitives) {
            var k = 1
            while (k < p.positions.size) { minY = minOf(minY, p.positions[k]); maxY = maxOf(maxY, p.positions[k]); k += 3 }
        }
        val h = (maxY - minY).coerceAtLeast(0.001f)
        modelScale = CHAR_HEIGHT / h
        // Ground on the ANIMATED run pose, not the bind pose: the run clip can sit at a
        // different height, so use the lowest skinned vertex across the cycle.
        footOffset = -animatedMinY(runClip) * modelScale
    }

    /** Lowest skinned vertex Y (model space) sampled across the [clip] cycle. */
    private fun animatedMinY(clip: Int): Float {
        var lo = Float.MAX_VALUE
        for (s in 0 until 12) {
            val bones = model.jointMatrices(clip, s * 0.13f)
            for (p in model.primitives) {
                val n = p.positions.size / 3
                var i = 0
                while (i < n) {
                    val px = p.positions[i * 3]; val py = p.positions[i * 3 + 1]; val pz = p.positions[i * 3 + 2]
                    var y = 0f
                    var b = 0
                    while (b < 4) {
                        val w = p.weights[i * 4 + b]
                        if (w != 0f) {
                            val off = p.joints[i * 4 + b].toInt() * 16
                            y += w * (bones[off + 1] * px + bones[off + 5] * py + bones[off + 9] * pz + bones[off + 13])
                        }
                        b++
                    }
                    if (y < lo) lo = y
                    i++
                }
            }
        }
        return if (lo == Float.MAX_VALUE) 0f else lo
    }

    private fun attrib(loc: Int, data: FloatArray, comps: Int) {
        val ids = IntArray(1); GLES30.glGenBuffers(1, ids, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        val buf = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(data); buf.position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(loc)
        GLES30.glVertexAttribPointer(loc, comps, GLES30.GL_FLOAT, false, 0, 0)
    }

    private fun elementBuffer(idx: IntArray) {
        val ids = IntArray(1); GLES30.glGenBuffers(1, ids, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[0])
        val buf = ByteBuffer.allocateDirect(idx.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        buf.put(idx); buf.position(0)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.size * 4, buf, GLES30.GL_STATIC_DRAW)
    }

    private fun makeVao(pos: FloatArray, nrm: FloatArray): Int {
        val idx = IntArray(pos.size / 3) { it }
        val vao = IntArray(1); GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glBindVertexArray(vao[0])
        attrib(0, pos, 3); attrib(1, nrm, 3); elementBuffer(idx)
        GLES30.glBindVertexArray(0)
        return vao[0]
    }

    private fun buildPrograms() {
        val frag = """
            #version 300 es
            precision mediump float;
            in vec3 vN; in float vFog;
            uniform vec3 uColor; uniform vec3 uHorizon;
            uniform float uFogNear; uniform float uFogFar;
            out vec4 o;
            void main(){
                vec3 L = normalize(vec3(0.35, 1.0, 0.45));
                float d = max(dot(normalize(vN), L), 0.0);
                vec3 c = uColor * (0.42 + 0.58 * d);
                // Fade with distance toward the bright horizon: dark/grey "here", glowing
                // yellow far ahead (the goal the player is chasing).
                float f = clamp((vFog - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);
                o = vec4(mix(c, uHorizon, f), 1.0);
            }
        """.trimIndent()

        val skinVert = """
            #version 300 es
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec3 aNorm;
            layout(location=2) in vec4 aJoints;
            layout(location=3) in vec4 aWeights;
            layout(std140) uniform JointBlock { mat4 uBones[${model.jointCount}]; };
            uniform mat4 uVP; uniform mat4 uModel;
            out vec3 vN; out float vFog;
            void main(){
                mat4 sk = aWeights.x*uBones[int(aJoints.x)]
                        + aWeights.y*uBones[int(aJoints.y)]
                        + aWeights.z*uBones[int(aJoints.z)]
                        + aWeights.w*uBones[int(aJoints.w)];
                vec4 wp = uModel * sk * vec4(aPos, 1.0);
                vN = mat3(uModel) * (mat3(sk) * aNorm);
                gl_Position = uVP * wp;
                vFog = gl_Position.w;
            }
        """.trimIndent()

        val flatVert = """
            #version 300 es
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec3 aNorm;
            uniform mat4 uVP; uniform mat4 uModel;
            out vec3 vN; out float vFog;
            void main(){
                vN = mat3(uModel) * aNorm;
                gl_Position = uVP * uModel * vec4(aPos, 1.0);
                vFog = gl_Position.w;
            }
        """.trimIndent()

        skinProg = linkProgram(skinVert, frag)
        sVP = GLES30.glGetUniformLocation(skinProg, "uVP")
        sModel = GLES30.glGetUniformLocation(skinProg, "uModel")
        sColor = GLES30.glGetUniformLocation(skinProg, "uColor")
        setFogUniforms(skinProg)

        flatProg = linkProgram(flatVert, frag)
        fVP = GLES30.glGetUniformLocation(flatProg, "uVP")
        fModel = GLES30.glGetUniformLocation(flatProg, "uModel")
        fColor = GLES30.glGetUniformLocation(flatProg, "uColor")
        setFogUniforms(flatProg)

        // Textured decal program (toll.png on the sign) — alpha-tested, fog-faded.
        val texVert = """
            #version 300 es
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aUv;
            uniform mat4 uVP; uniform mat4 uModel;
            out vec2 vUv; out float vFog;
            void main(){
                vUv = aUv;
                gl_Position = uVP * uModel * vec4(aPos, 1.0);
                vFog = gl_Position.w;
            }
        """.trimIndent()
        val texFrag = """
            #version 300 es
            precision mediump float;
            in vec2 vUv; in float vFog;
            uniform sampler2D uTex; uniform vec3 uHorizon;
            uniform float uFogNear; uniform float uFogFar;
            out vec4 o;
            void main(){
                vec4 t = texture(uTex, vUv);
                if (t.a < 0.35) discard;
                float f = clamp((vFog - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);
                o = vec4(mix(t.rgb, uHorizon, f), 1.0);
            }
        """.trimIndent()
        texProg = linkProgram(texVert, texFrag)
        tVP = GLES30.glGetUniformLocation(texProg, "uVP")
        tModel = GLES30.glGetUniformLocation(texProg, "uModel")
        tSampler = GLES30.glGetUniformLocation(texProg, "uTex")
        setFogUniforms(texProg)
    }

    /** The fog distance range is constant; the horizon COLOUR shifts by stage (updateHorizon). */
    private fun setFogUniforms(prog: Int) {
        GLES30.glUseProgram(prog)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(prog, "uHorizon"), curHorizon[0], curHorizon[1], curHorizon[2])
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uFogNear"), FOG_NEAR)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uFogFar"), FOG_FAR)
    }

    // Sky/fog colour eases from yellow (start) toward red-orange (finish) as a progress cue.
    private val curHorizon = floatArrayOf(1.0f, 0.88f, 0.45f)
    private fun updateHorizon() {
        val span = tileStartZ - farZ
        val prog = if (span > 1f) ((tileStartZ - charZ) / span).coerceIn(0f, 1f) else 0f
        for (i in 0..2) curHorizon[i] = HORIZON[i] + (HORIZON_END[i] - HORIZON[i]) * prog
        GLES30.glClearColor(curHorizon[0], curHorizon[1], curHorizon[2], 1f)
        for (p in intArrayOf(skinProg, flatProg, texProg)) {
            if (p == 0) continue
            GLES30.glUseProgram(p)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(p, "uHorizon"), curHorizon[0], curHorizon[1], curHorizon[2])
        }
    }

    private fun linkProgram(vs: String, fs: String): Int {
        val v = compile(GLES30.GL_VERTEX_SHADER, vs)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v); GLES30.glAttachShader(p, f); GLES30.glLinkProgram(p)
        val ok = IntArray(1); GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("link: " + GLES30.glGetProgramInfoLog(p))
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type); GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
        val ok = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("compile: " + GLES30.glGetShaderInfoLog(s) + "\n" + src)
        return s
    }

    companion object {
        private val CHAR_GREY = floatArrayOf(0.82f, 0.82f, 0.85f)  // light grey runner
        private val TILE_GREY = floatArrayOf(0.30f, 0.31f, 0.34f)  // dark grey track pieces
        private val HORIZON = floatArrayOf(1.0f, 0.88f, 0.45f)     // bright yellow horizon (start)
        private val HORIZON_END = floatArrayOf(0.86f, 0.30f, 0.13f) // red-orange horizon (finish)
        private val FOG_NEAR = 25f                                  // clear "here"
        private val FOG_FAR = 230f                                  // fully horizon-yellow far off
        private val COIN_COL = floatArrayOf(1f, 0.82f, 0.18f)
        private val SIGN_RED = floatArrayOf(0.86f, 0.18f, 0.15f)
        private val SIGN_WHITE = floatArrayOf(0.95f, 0.95f, 0.95f)
        private val SIGN_BLACK = floatArrayOf(0.05f, 0.05f, 0.06f)

        // Named object-groups within a tile that get their own colour (rest stays TILE_GREY).
        // The toll sign: outer disc = red rim, inner disc = white face, TOLL text = black.
        private val TILE_GROUP_COLORS: Map<String, Map<String, FloatArray>> = mapOf(
            "sptoll" to mapOf(
                "Circle" to SIGN_RED, "Circle.001" to SIGN_WHITE, "TOLL" to SIGN_BLACK,
            )
        )
    }
}
