package com.fictioncutshort.justacalculator.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.audiofx.PresetReverb
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.Surface
import com.fictioncutshort.justacalculator.R
import com.fictioncutshort.justacalculator.ui.components.RequestGamePermissionsOnEntry
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.res.Configuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// DOOR 4 — "Surveillance & Time" exhibition (maze)
//
// A large black maze of chambers navigated first-person, like Calculator City.
// 6 exhibition pieces (4 videos + 2 images) are scattered through the maze; they
// reveal ONE AT A TIME. The player must find the piece that just appeared, walk up
// to its [X] marker, and read the description panel before the next one reveals.
// After all 6 are read, a door opens in the north wall, revealing a tunnel whose
// walls show the device's live cameras. Everything is black; walls/screens are
// outlined in thin blue. A clock ticks for images; videos play their own audio.
// ─────────────────────────────────────────────────────────────────────────────

private const val ROOM_R    = 2100f   // half-extent of the square maze (X and Z)
private const val ROOM_H     = 900f    // wall height
private const val THIRD      = 700f    // chamber grid line (room split into 3×3)
private const val WT_H       = 20f     // wall half-thickness
private const val DIV_DOOR_HW = 200f   // half-width of doorways carved in dividers
private const val EYE_Y      = 360f    // first-person eye height
private const val MOVE_SPD   = 6.5f
private const val YAW_SPD     = 3.0f
private const val WALL_PAD    = 60f    // collision margin

// Two distinct radii drive the new interaction:
//   APPROACH_RADIUS — player is near the current piece; nudge timer starts.
//   READ_RADIUS_X   — player is right at the [X] glyph; description auto-opens.
// The pieces sit on walls; the [X] is below them, offset toward one corner, so
// "reach the X" forces a deliberate walk to a specific corner of the chamber.
private const val APPROACH_RADIUS = 520f
private const val READ_RADIUS_X   = 240f
private const val NUDGE_DELAY_MS  = 5_000L  // approach without opening before nudge shows
private const val NEXT_REVEAL_MS  = 5_000L  // after the player leaves a piece, before next reveal

// Clock loudness ramps up as the player nears the current revealed-but-unread piece.
// CLOCK_MIN is intentionally very low so the swell from distant→near is dramatic.
// For VIDEO targets the clock is heavily ducked so the video's own audio leads.
private const val CLOCK_MIN  = 0.03f
private const val CLOCK_MAX  = 1.0f
private const val CLOCK_NEAR = 220f    // at/inside this distance → full volume
private const val CLOCK_FAR  = 1700f   // beyond this → quiet
private const val CLOCK_VIDEO_DUCK = 0.18f  // clock max when current target is a video

// Video volume (per-piece, distance-modulated). MIN is just above CLOCK_MIN so
// the video is barely audible from anywhere in the maze, then the volume ramps
// up steeply through VIDEO_CURVE so the player perceives a clear "I'm getting
// closer" cue as they approach (linear was too flat — the change between far
// and near was imperceptible mid-walk). When the panel is open the video
// auto-ducks so the user can read; "Mute and leave" then hard-mutes it.
private const val VIDEO_MIN      = 0.04f
private const val VIDEO_MAX      = 1.0f
private const val VIDEO_NEAR     = 200f
private const val VIDEO_FAR      = 2400f    // start ramping earlier — most of the maze
private const val VIDEO_CURVE    = 2.6f     // pow exponent: >1 → most growth near user
private const val VIDEO_DUCK_OPEN = 0.20f   // factor applied while panel is open

private const val SCREEN_COUNT = 6

// Tunnel door on the north wall, centred on the (0,1) chamber
private const val DOOR_X  = 0f
private const val DOOR_HW = 200f
private const val TUNNEL_LEN = 2200f

// Maximum panel half-extents (world units). The actual panel size per piece is
// derived from the media's aspect ratio so there is NO leftover background
// around square or vertical media.
private const val PANEL_MAX_HALF_W = 380f
private const val PANEL_MAX_HALF_H = 270f

// ── Piece placement (one ScreenSpec per news item, in news order) ─────────────
// axis 0 = wall at x=const (screen faces ±x) ; axis 1 = wall at z=const (faces ±z)
private class ScreenSpec(val order: Int, val axis: Int, val c: Float, val lat: Float, val nrm: Float)

// 6 spots picked from the original 10 to spread the player through the maze.
// news1 starts in the far NW (forces exploration); news6 lands on the centre
// chamber's south wall right by the spawn, so the player ends adjacent to the
// (now-open) north tunnel door.
private val DOOR4_SCREENS = listOf(
    ScreenSpec(0, axis = 0, c = -2100f, lat = -1400f, nrm = 1f),    // news1 — (0,0) west outer (NW corner)
    ScreenSpec(1, axis = 0, c = 700f,   lat = -1400f, nrm = -1f),   // news2 — (0,1) east divider
    ScreenSpec(2, axis = 0, c = -2100f, lat = 0f,     nrm = 1f),    // news3 — (1,0) west outer (mid-west)
    ScreenSpec(3, axis = 0, c = 2100f,  lat = 0f,     nrm = -1f),   // news4 — (1,2) east outer (mid-east)
    ScreenSpec(4, axis = 0, c = 2100f,  lat = 1400f,  nrm = -1f),   // news5 — (2,2) east outer (NE-ish corner)
    ScreenSpec(5, axis = 1, c = 700f,   lat = 0f,     nrm = -1f),   // news6 — centre chamber south wall (spawn)
)

// One news item per ScreenSpec, indexed by `order`.
private data class NewsItem(
    val asset: String,
    val isVideo: Boolean,
    val title: String,
    val subtitle: String?,
    val year: String,
    val source: String,
    val url: String,
)

private val DOOR4_NEWS = listOf(
    NewsItem(
        asset = "articles/news1.mp4", isVideo = true,
        title = "Public facial recognition works", subtitle = null,
        year = "2026", source = "Met Police London",
        url = "https://x.com/metpoliceuk/status/2057818316085891260",
    ),
    NewsItem(
        asset = "articles/news2.png", isVideo = false,
        title = "Phone tracking golore", subtitle = null,
        year = "2025", source = "Lighthouse Reports",
        url = "https://www.lighthousereports.com/methodology/surveillance-secrets-explainer/",
    ),
    NewsItem(
        asset = "articles/news3.mp4", isVideo = true,
        title = "Public facial recognition does not work",
        subtitle = "The Angela Lipps Case",
        year = "2026", source = "WBIR Channel 10",
        url = "https://www.youtube.com/watch?v=XI6DvJOXHtw",
    ),
    NewsItem(
        asset = "articles/news4.mp4", isVideo = true,
        title = "Can you hear me?", subtitle = null,
        year = "2023", source = "abc 2 WBAY",
        url = "https://www.wbay.com/video/2023/03/02/green-bay-mayor-city-clerk-had-access-audio-surveillance/",
    ),
    NewsItem(
        asset = "articles/news5.png", isVideo = false,
        title = "Where you goin'?", subtitle = null,
        year = "2025", source = "Liberty Investigates",
        url = "https://libertyinvestigates.org.uk/articles/police-ai-anpr-track-drivers/",
    ),
    NewsItem(
        asset = "articles/news6.mp4", isVideo = true,
        title = "Who you textin'?", subtitle = null,
        year = "2026", source = "WION",
        url = "https://www.youtube.com/watch?v=us69h0Wda8M",
    ),
)

private fun screenCenterXZ(s: ScreenSpec): Pair<Float, Float> =
    if (s.axis == 1) Pair(s.lat, s.c) else Pair(s.c, s.lat)

// Half-extents of the panel for a given aspect ratio (w/h), capped by the panel
// max half-extents so big landscape pieces don't dominate the room.
private fun panelHalfExtents(aspect: Float): Pair<Float, Float> {
    var halfH = PANEL_MAX_HALF_H
    var halfW = halfH * aspect
    if (halfW > PANEL_MAX_HALF_W) {
        halfW = PANEL_MAX_HALF_W
        halfH = halfW / aspect
    }
    return Pair(halfW, halfH)
}

// World XZ of the [X] marker (the lateral corner under each panel).
private fun screenXMarkXZ(s: ScreenSpec, halfW: Float): Pair<Float, Float> {
    val xW = 90f
    val cLat = s.lat + s.nrm * (halfW - xW * 0.5f)
    return if (s.axis == 1) Pair(cLat, s.c) else Pair(s.c, cLat)
}

@Composable
fun Door4Room(modifier: Modifier = Modifier, onComplete: () -> Unit = {}) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val renderer = remember { Door4Renderer(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // This building's surveillance tunnel needs CAMERA — request ONLY that,
    // not the rest of the game's permissions (mic / location / contacts /
    // notifications), which belong to other beats. The result is mirrored
    // into hasCameraPermission for the DisposableEffect that wires the live
    // feeds.
    RequestGamePermissionsOnEntry(
        permissions = listOf(Manifest.permission.CAMERA),
    ) { results ->
        results[Manifest.permission.CAMERA]?.let { hasCameraPermission = it }
    }

    // Camera / player state (camera IS the player).
    var pX     by remember { mutableStateOf(0f) }
    var pZ     by remember { mutableStateOf(0f) }
    var camYaw by remember { mutableStateOf(180f) }
    var joyX   by remember { mutableStateOf(0f) }
    var joyY   by remember { mutableStateOf(0f) }

    // Progression:
    //   litCount    — pieces currently REVEALED (visible + video playing).
    //   foundCount  — pieces whose description has been read AND closed.
    //   The next piece reveals NEXT_REVEAL_MS after the previous panel CLOSES.
    // Which exhibits the player has already been shown. rememberSaveable survives a
    // rotation but not the process, so these also go to disk: the room is a slow
    // read, and making someone re-read every panel because their phone dropped the
    // app is the kind of thing that makes people stop playing.
    var foundCount     by rememberSaveable { mutableIntStateOf(com.fictioncutshort.justacalculator.logic.BuildingProgress.getInt(context, 4, "found", 0)) }
    var litCount       by rememberSaveable { mutableIntStateOf(com.fictioncutshort.justacalculator.logic.BuildingProgress.getInt(context, 4, "lit", 1)) }
    // Narration: vo016 as building 4's exhibition is entered (fresh entry only);
    // vo017 once the player has seen the last exhibition piece.
    LaunchedEffect(Unit) {
        com.fictioncutshort.justacalculator.logic.VoiceoverManager.init(context)
        if (foundCount == 0) {
            com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo016, cctv = false)
        }
    }
    LaunchedEffect(foundCount) {
        if (foundCount >= SCREEN_COUNT) {
            com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo017, cctv = false)
        }
    }
    LaunchedEffect(foundCount) { com.fictioncutshort.justacalculator.logic.BuildingProgress.putInt(context, 4, "found", foundCount) }
    LaunchedEffect(litCount) { com.fictioncutshort.justacalculator.logic.BuildingProgress.putInt(context, 4, "lit", litCount) }
    var shownDescIndex by remember { mutableStateOf<Int?>(null) }
    var tunnelReady    by remember { mutableStateOf(false) }
    var showNudge      by remember { mutableStateOf(false) }
    // Per-piece mute flag (videos): set true once the user taps "Mute and leave".
    val muted = remember { mutableStateListOf<Boolean>().apply { repeat(SCREEN_COUNT) { add(false) } } }
    // MediaPlayers and reverb effects, one per video piece. Built lazily once
    // the renderer's SurfaceTextures exist (see DisposableEffect below).
    val mediaPlayers = remember { arrayOfNulls<MediaPlayer>(SCREEN_COUNT) }
    val mediaReverbs = remember { arrayOfNulls<PresetReverb>(SCREEN_COUNT) }
    val mediaPrepared = remember { BooleanArray(SCREEN_COUNT) }

    // ── Ambient clock loop ─────────────────────────────────────────────────────
    // SoundPool decodes clock.mp3 to PCM in memory and loops seamlessly.
    val clockSp = remember { mutableStateOf<SoundPool?>(null) }
    val clockStream = remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val sp = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        clockSp.value = sp
        try {
            val soundId = sp.load(context, R.raw.clock, 1)
            sp.setOnLoadCompleteListener { _, id, status ->
                if (status == 0 && id == soundId) {
                    val s = sp.play(soundId, CLOCK_MIN, CLOCK_MIN, 1, -1, 1f)
                    clockStream.intValue = s
                }
            }
        } catch (_: Exception) {}
        onDispose {
            try { sp.release() } catch (_: Exception) {}
            clockSp.value = null
            clockStream.intValue = 0
        }
    }

    // Keep renderer's lit count in sync; (re-)start any prepared video whose
    // piece is now revealed.
    LaunchedEffect(litCount) {
        renderer.litCount = litCount
        for (i in 0 until litCount) {
            val mp = mediaPlayers[i] ?: continue
            if (!mediaPrepared[i]) continue
            try { if (!mp.isPlaying) mp.start() } catch (_: Throwable) {}
        }
    }
    // Mirror foundCount to the renderer so the [X] marker hides for read pieces.
    LaunchedEffect(foundCount) { renderer.foundCount = foundCount }

    // Thump when the very first piece is revealed at entry.
    LaunchedEffect(Unit) {
        if (foundCount == 0) {
            val (sx, sz) = screenCenterXZ(DOOR4_SCREENS[0])
            val (l, r) = thumpPan(sx, sz, pX, pZ, camYaw)
            playOneShotRaw(context, "thump", l, r)
        }
    }

    // After the player closes a panel (foundCount increments), reveal the next
    // piece NEXT_REVEAL_MS later (+ thump + start its video if applicable).
    LaunchedEffect(foundCount) {
        if (foundCount in 1 until SCREEN_COUNT && litCount == foundCount) {
            delay(NEXT_REVEAL_MS)
            if (litCount == foundCount) {
                litCount = foundCount + 1
                val nextIdx = foundCount
                val (sx, sz) = screenCenterXZ(DOOR4_SCREENS[nextIdx])
                val (l, r) = thumpPan(sx, sz, pX, pZ, camYaw)
                playOneShotRaw(context, "thump", l, r)
                val mp = mediaPlayers[nextIdx]
                if (mp != null && mediaPrepared[nextIdx]) {
                    try { if (!mp.isPlaying) mp.start() } catch (_: Throwable) {}
                }
            }
        }
    }

    // Open the tunnel door once all pieces are read.
    LaunchedEffect(foundCount) {
        if (foundCount >= SCREEN_COUNT && renderer.doorOpen < 1f) {
            val steps = 90
            repeat(steps) { delay(16); renderer.doorOpen = (it + 1f) / steps }
            renderer.doorOpen = 1f
            tunnelReady = true
        }
    }

    // ── Video MediaPlayers — wait for renderer's SurfaceTextures, then attach ──
    DisposableEffect(Unit) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        val job = scope.launch {
            // Wait until the GL thread has created all video SurfaceTextures.
            while (true) {
                val ready = (0 until SCREEN_COUNT).all { i ->
                    !DOOR4_NEWS[i].isVideo || renderer.mediaSurfaceTexture[i] != null
                }
                if (ready) break
                delay(60)
            }
            for (i in 0 until SCREEN_COUNT) {
                val item = DOOR4_NEWS[i]
                if (!item.isVideo) continue
                val st = renderer.mediaSurfaceTexture[i] ?: continue
                renderer.mediaPixelSize[i]?.let { (w, h) -> st.setDefaultBufferSize(w, h) }
                val mp = MediaPlayer()
                try {
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    val afd = context.assets.openFd(item.asset)
                    try { mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
                    finally { afd.close() }
                    mp.setSurface(Surface(st))
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    mp.setOnPreparedListener {
                        mediaPrepared[i] = true
                        if (i < renderer.litCount) try { mp.start() } catch (_: Throwable) {}
                    }
                    mp.prepareAsync()
                } catch (_: Throwable) {
                    try { mp.release() } catch (_: Throwable) {}
                    continue
                }
                mediaPlayers[i] = mp
                // Best-effort echo. Some devices lack PresetReverb; failing is fine.
                try {
                    val rv = PresetReverb(1, 0).apply {
                        preset = PresetReverb.PRESET_LARGEHALL
                        enabled = true
                    }
                    mp.attachAuxEffect(rv.id)
                    mp.setAuxEffectSendLevel(0.7f)
                    mediaReverbs[i] = rv
                } catch (_: Throwable) {}
            }
        }
        onDispose {
            job.cancel(); scope.cancel()
            for (i in mediaPlayers.indices) {
                try { mediaPlayers[i]?.stop() } catch (_: Throwable) {}
                try { mediaPlayers[i]?.release() } catch (_: Throwable) {}
                mediaPlayers[i] = null
            }
            for (i in mediaReverbs.indices) {
                try { mediaReverbs[i]?.release() } catch (_: Throwable) {}
                mediaReverbs[i] = null
            }
        }
    }

    var levelDone by remember { mutableStateOf(false) }
    var approachEnterMs by remember { mutableStateOf(0L) }

    // ── Movement / camera loop + proximity → reveal / nudge / audio ───────────
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            camYaw += joyX * YAW_SPD
            val cr = Math.toRadians(camYaw.toDouble())
            val fwd = -joyY
            val dx = (sin(cr) * fwd * MOVE_SPD).toFloat()
            val dz = (-cos(cr) * fwd * MOVE_SPD).toFloat()
            val nx = pX + dx
            val nz = pZ + dz
            val doorIsOpen = renderer.doorOpen > 0.5f
            if (!door4Blocked(nx, nz, doorIsOpen)) { pX = nx; pZ = nz }
            else {
                if (!door4Blocked(nx, pZ, doorIsOpen)) pX = nx
                if (!door4Blocked(pX, nz, doorIsOpen)) pZ = nz
            }

            renderer.camX = pX; renderer.camZ = pZ; renderer.camYaw = camYaw
            renderer.lookAtX = pX + sin(cr).toFloat()
            renderer.lookAtZ = pZ - cos(cr).toFloat()

            if (!levelDone && doorIsOpen && pZ < -ROOM_R - TUNNEL_LEN + 140f) {
                levelDone = true
                // News pieces read become cookies.
                com.fictioncutshort.justacalculator.logic.CurrencyStore.award(
                    context, com.fictioncutshort.justacalculator.logic.Currency.COOKIES, foundCount, "b4")
                onComplete()
            }

            val target = foundCount  // next piece to be found
            val targetValid = target < SCREEN_COUNT && target < litCount
            val targetIsVideo = targetValid && DOOR4_NEWS[target].isVideo

            // Distance to the target piece's center & to its [X] marker.
            var dCenter = Float.MAX_VALUE
            var dXMark = Float.MAX_VALUE
            if (targetValid) {
                val s = DOOR4_SCREENS[target]
                val (sx, sz) = screenCenterXZ(s)
                dCenter = sqrt((pX - sx) * (pX - sx) + (pZ - sz) * (pZ - sz))
                val (halfW, _) = panelHalfExtents(renderer.mediaAspect[target])
                val (mx, mz) = screenXMarkXZ(s, halfW)
                dXMark = sqrt((pX - mx) * (pX - mx) + (pZ - mz) * (pZ - mz))
            }

            // Auto-open description when player reaches the [X].
            if (targetValid && shownDescIndex == null && dXMark < READ_RADIUS_X) {
                shownDescIndex = target
                showNudge = false
                approachEnterMs = 0L
            }

            // Nudge: 5s in approach zone without opening the panel.
            if (targetValid && shownDescIndex == null) {
                if (dCenter < APPROACH_RADIUS) {
                    if (approachEnterMs == 0L) approachEnterMs = System.currentTimeMillis()
                    else if (System.currentTimeMillis() - approachEnterMs > NUDGE_DELAY_MS) showNudge = true
                } else {
                    approachEnterMs = 0L
                    showNudge = false
                }
            } else {
                showNudge = false
                approachEnterMs = 0L
            }

            // ── Clock volume ──
            // Open panel → silent. Video target → ducked max. Image target → normal.
            var clockVol = CLOCK_MIN
            if (shownDescIndex != null) {
                clockVol = 0f
            } else if (targetValid) {
                val close = ((CLOCK_FAR - dCenter) / (CLOCK_FAR - CLOCK_NEAR)).coerceIn(0f, 1f)
                val maxVol = if (targetIsVideo) CLOCK_VIDEO_DUCK else CLOCK_MAX
                clockVol = CLOCK_MIN + (maxVol - CLOCK_MIN) * close
            }
            val sp = clockSp.value; val sid = clockStream.intValue
            if (sp != null && sid != 0) {
                try { sp.setVolume(sid, clockVol, clockVol) } catch (_: Throwable) {}
            }

            // ── Video volumes (per-piece, distance-modulated) ──
            for (i in 0 until SCREEN_COUNT) {
                if (!DOOR4_NEWS[i].isVideo) continue
                if (i >= litCount) continue
                val mp = mediaPlayers[i] ?: continue
                if (!mediaPrepared[i]) continue
                if (muted[i]) {
                    try { mp.setVolume(0f, 0f) } catch (_: Throwable) {}
                    continue
                }
                val (sx, sz) = screenCenterXZ(DOOR4_SCREENS[i])
                val d = sqrt((pX - sx) * (pX - sx) + (pZ - sz) * (pZ - sz))
                val close = ((VIDEO_FAR - d) / (VIDEO_FAR - VIDEO_NEAR)).coerceIn(0f, 1f)
                // VIDEO_CURVE squashes the low end so most volume gain happens
                // in the last third of the approach — perceptually the player
                // feels the piece "growing louder" only once they're committing.
                val shaped = close.pow(VIDEO_CURVE)
                var vol = VIDEO_MIN + (VIDEO_MAX - VIDEO_MIN) * shaped
                if (shownDescIndex == i) vol *= VIDEO_DUCK_OPEN
                try { mp.setVolume(vol, vol) } catch (_: Throwable) {}
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setEGLConfigChooser(8, 8, 8, 0, 24, 0)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        val joySize = (configuration.screenWidthDp * 0.30f).dp.coerceIn(110.dp, 160.dp)
        CityJoystick(
            joyDp = joySize,
            modifier = if (isLandscape) Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
                       else Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            onJoy = { x, y -> joyX = x; joyY = y }
        )

        val prompt = when {
            foundCount >= 3 -> null
            foundCount >= 2 -> "are you sure?"
            else -> "Are you alone?"
        }
        prompt?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isLandscape) 60.dp else 110.dp)
            ) {
                Text(
                    text,
                    color = Color(0xFF3A6FFF), fontSize = 30.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Nudge — appears after the player lingers near the piece without reaching the [X].
        if (showNudge && shownDescIndex == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isLandscape) 110.dp else 170.dp)
            ) {
                Text(
                    "Try getting closer to the [X]",
                    color = Color(0xFF8FA8DC),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // News item description panel
        shownDescIndex?.let { idx ->
            Door4InfoPanel(
                item = DOOR4_NEWS[idx],
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = if (isLandscape) 16.dp else 150.dp),
                onLeave = {
                    if (DOOR4_NEWS[idx].isVideo) {
                        muted[idx] = true
                        try { mediaPlayers[idx]?.setVolume(0f, 0f) } catch (_: Throwable) {}
                    }
                    shownDescIndex = null
                    if (idx >= foundCount) foundCount = idx + 1
                },
                onOpenLink = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DOOR4_NEWS[idx].url))
                    try { context.startActivity(intent) } catch (_: Throwable) {}
                }
            )
        }
    }

    // ── Live camera feeds for the tunnel walls (after the door opens) ──────────
    DisposableEffect(tunnelReady, hasCameraPermission) {
        if (!tunnelReady || !hasCameraPermission) return@DisposableEffect onDispose { }
        val rearST = renderer.rearSurfaceTexture
        val frontST = renderer.frontSurfaceTexture
        if (rearST == null || frontST == null) return@DisposableEffect onDispose { }

        val mainExecutor = ContextCompat.getMainExecutor(context)
        var providerRef: ProcessCameraProvider? = null
        var alternateJob: kotlinx.coroutines.Job? = null
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)

        fun previewFor(st: SurfaceTexture): Preview =
            Preview.Builder().build().also { p ->
                p.setSurfaceProvider { request ->
                    st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                    val surface = Surface(st)
                    request.provideSurface(surface, mainExecutor) { surface.release() }
                }
            }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                providerRef = provider
                provider.unbindAll()

                val concurrentSupported = try {
                    provider.availableConcurrentCameraInfos.isNotEmpty()
                } catch (_: Throwable) { false }

                var boundConcurrent = false
                if (concurrentSupported) {
                    try {
                        val rearCfg = SingleCameraConfig(
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            UseCaseGroup.Builder().addUseCase(previewFor(rearST)).build(),
                            lifecycleOwner
                        )
                        val frontCfg = SingleCameraConfig(
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            UseCaseGroup.Builder().addUseCase(previewFor(frontST)).build(),
                            lifecycleOwner
                        )
                        provider.bindToLifecycle(listOf(rearCfg, frontCfg))
                        renderer.rearLive = true
                        renderer.frontLive = true
                        boundConcurrent = true
                    } catch (_: Throwable) {
                        provider.unbindAll()
                        boundConcurrent = false
                    }
                }

                if (!boundConcurrent) {
                    alternateJob = scope.launch {
                        var rear = true
                        while (true) {
                            try {
                                provider.unbindAll()
                                if (rear) {
                                    provider.bindToLifecycle(
                                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, previewFor(rearST))
                                    renderer.rearLive = true; renderer.frontLive = false
                                } else {
                                    provider.bindToLifecycle(
                                        lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, previewFor(frontST))
                                    renderer.rearLive = false; renderer.frontLive = true
                                }
                            } catch (_: Throwable) { }
                            rear = !rear
                            delay(5000)
                        }
                    }
                }
            } catch (_: Throwable) { }
        }, mainExecutor)

        onDispose {
            alternateJob?.cancel()
            scope.cancel()
            try { providerRef?.unbindAll() } catch (_: Throwable) { }
            renderer.rearLive = false
            renderer.frontLive = false
        }
    }
}

// ── Info panel ─────────────────────────────────────────────────────────────────
// Compact, translucent placard with title / subtitle / year / source, plus a
// "View in full" link button and a bottom Mute-and-leave / Leave button which
// is the only way to close the panel.
@Composable
private fun Door4InfoPanel(
    item: NewsItem,
    modifier: Modifier = Modifier,
    onLeave: () -> Unit,
    onOpenLink: () -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .background(Color(0xEE0A1430), shape = RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x993A6FFF), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            item.title,
            color = Color(0xFFE6EEFF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        item.subtitle?.let { sub ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                sub,
                color = Color(0xFF9FC0FF),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        InfoRow("Year",   item.year)
        InfoRow("Source", item.source)
        Spacer(modifier = Modifier.height(12.dp))
        // Link button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x333A6FFF), shape = RoundedCornerShape(3.dp))
                .border(1.dp, Color(0x803A6FFF), shape = RoundedCornerShape(3.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onOpenLink() }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "View in full",
                color = Color(0xFFB8CCFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Mute-and-leave / Leave button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x55102040), shape = RoundedCornerShape(3.dp))
                .border(1.dp, Color(0x666C86C8), shape = RoundedCornerShape(3.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onLeave() }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (item.isVideo) "Mute and leave" else "Leave",
                color = Color(0xFF9FC0FF),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Sound Effect(reveal) by Daniel Roberts from Pixabay",
            color = Color(0xFF6C86C8),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "Sound Effect(clock) by DRAGON-STUDIO from Pixabay",
            color = Color(0xFF6C86C8),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            color = Color(0xFF6C86C8),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(64.dp),
        )
        Text(
            value,
            color = Color(0xFFB8CCFF),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ── Collision ────────────────────────────────────────────────────────────────
private fun door4Blocked(x: Float, z: Float, doorOpen: Boolean): Boolean {
    val pad = WALL_PAD
    // North wall / door / tunnel
    if (z < -ROOM_R + pad) {
        val inDoorX = abs(x - DOOR_X) < DOOR_HW
        if (doorOpen && inDoorX) return z < -ROOM_R - TUNNEL_LEN + pad
        return true
    }
    if (x < -ROOM_R + pad || x > ROOM_R - pad || z > ROOM_R - pad) return true
    for (s in DOOR4_SLABS) {
        if (abs(x - s[0]) < s[2] + pad && abs(z - s[1]) < s[3] + pad) return true
    }
    return false
}

// Interior maze walls as slabs [xc, zc, hx, hz] (full height). Built from the 3×3
// chamber connectivity below (a spanning tree → proper maze with dead-ends).
private val DOOR4_SLABS: List<FloatArray> = buildMazeSlabs()

private fun buildMazeSlabs(): List<FloatArray> {
    val out = ArrayList<FloatArray>()
    val xb = floatArrayOf(-ROOM_R, -THIRD, THIRD, ROOM_R)
    val zb = floatArrayOf(-ROOM_R, -THIRD, THIRD, ROOM_R)

    // Emit a wall segment along one axis, carving a centred doorway if open.
    fun seg(axis: Int, c: Float, from: Float, to: Float, open: Boolean) {
        val a = min(from, to); val b = max(from, to)
        val pieces = if (!open) listOf(floatArrayOf(a, b))
        else {
            val mid = (a + b) / 2f
            listOf(floatArrayOf(a, mid - DIV_DOOR_HW), floatArrayOf(mid + DIV_DOOR_HW, b))
        }
        for (p in pieces) {
            if (p[1] - p[0] < 1f) continue
            val center = (p[0] + p[1]) / 2f
            val half = (p[1] - p[0]) / 2f
            if (axis == 0) out.add(floatArrayOf(c, center, WT_H, half))   // x=c wall (varies in z)
            else           out.add(floatArrayOf(center, c, half, WT_H))   // z=c wall (varies in x)
        }
    }

    // Vertical dividers (x = const), per row.  open? from connectivity table.
    // x=-700 between col0,col1
    seg(0, xb[1], zb[0], zb[1], open = true)    // (0,0)-(0,1)
    seg(0, xb[1], zb[1], zb[2], open = true)    // (1,0)-(1,1)
    seg(0, xb[1], zb[2], zb[3], open = false)   // (2,0)-(2,1)
    // x=700 between col1,col2
    seg(0, xb[2], zb[0], zb[1], open = false)   // (0,1)-(0,2)
    seg(0, xb[2], zb[1], zb[2], open = true)    // (1,1)-(1,2)
    seg(0, xb[2], zb[2], zb[3], open = true)    // (2,1)-(2,2)
    // Horizontal dividers (z = const), per col.
    // z=-700 between row0,row1
    seg(1, zb[1], xb[0], xb[1], open = false)   // (0,0)-(1,0)
    seg(1, zb[1], xb[1], xb[2], open = true)    // (0,1)-(1,1)
    seg(1, zb[1], xb[2], xb[3], open = true)    // (0,2)-(1,2)
    // z=700 between row1,row2
    seg(1, zb[2], xb[0], xb[1], open = true)    // (1,0)-(2,0)
    seg(1, zb[2], xb[1], xb[2], open = false)   // (1,1)-(2,1)
    seg(1, zb[2], xb[2], xb[3], open = true)    // (1,2)-(2,2)
    return out
}

// Fire-and-forget one-shot (releases itself when finished). leftVol/rightVol allow
// crude stereo panning so a sound can come from a direction in the space.
private fun playOneShotRaw(context: Context, name: String, leftVol: Float = 1f, rightVol: Float = 1f) {
    try {
        val resId = context.resources.getIdentifier(name, "raw", context.packageName)
        if (resId == 0) return
        MediaPlayer.create(context, resId)?.apply {
            setVolume(leftVol, rightVol)
            setOnCompletionListener { it.release() }
            start()
        }
    } catch (_: Exception) { }
}

// Stereo gains so a world point at (sx,sz) is heard from its direction relative to
// the player at (pX,pZ) facing yawDeg. Returns (leftVol, rightVol).
private fun thumpPan(sx: Float, sz: Float, pX: Float, pZ: Float, yawDeg: Float): Pair<Float, Float> {
    val dx = sx - pX; val dz = sz - pZ
    val len = sqrt(dx * dx + dz * dz)
    if (len < 1f) return Pair(1f, 1f)
    val yaw = Math.toRadians(yawDeg.toDouble())
    val rx = cos(yaw).toFloat(); val rz = sin(yaw).toFloat()   // player's "right" vector
    val pan = ((dx / len) * rx + (dz / len) * rz).coerceIn(-1f, 1f)
    val left = 1f - max(0f, pan) * 0.8f
    val right = 1f - max(0f, -pan) * 0.8f
    return Pair(left, right)
}

// ═══════════════════════════════════════════════════════════════════════════════
//  RENDERER
// ═══════════════════════════════════════════════════════════════════════════════

private class Door4Renderer(private val context: Context) : GLSurfaceView.Renderer {

    @Volatile var camX = 0f
    @Volatile var camZ = 0f
    @Volatile var camYaw = 0f
    @Volatile var lookAtX = 0f
    @Volatile var lookAtZ = -1f
    @Volatile var litCount = 1          // panels with order < litCount are revealed
    @Volatile var foundCount = 0        // panels with order < foundCount have been read
    @Volatile var doorOpen = 0f

    @Volatile var rearLive = false
    @Volatile var frontLive = false
    @Volatile var rearSurfaceTexture: SurfaceTexture? = null
    @Volatile var frontSurfaceTexture: SurfaceTexture? = null

    // Per-piece media. Populated on the GL thread in onSurfaceCreated:
    //   videos → OES texture + SurfaceTexture (composable attaches a MediaPlayer).
    //   images → GL_TEXTURE_2D, uploaded immediately from the asset.
    // mediaAspect drives panel geometry so each panel matches its media's
    // proportions exactly (no leftover background outside square/vertical media).
    @Volatile var mediaSurfaceTexture: Array<SurfaceTexture?> = arrayOfNulls(SCREEN_COUNT)
    @Volatile var mediaPixelSize: Array<Pair<Int, Int>?> = arrayOfNulls(SCREEN_COUNT)
    val mediaAspect = FloatArray(SCREEN_COUNT) { 16f / 9f }
    private val mediaTex = IntArray(SCREEN_COUNT)
    private val mediaIsVideo = BooleanArray(SCREEN_COUNT)
    private val mediaDirty = BooleanArray(SCREEN_COUNT)
    private val mediaHasContent = BooleanArray(SCREEN_COUNT)
    private val mediaTexMtx = Array(SCREEN_COUNT) { FloatArray(16).also { Matrix.setIdentityM(it, 0) } }

    // Vertical flip in texture space — (u, v) → (u, 1-v). Pre-composed with the
    // SurfaceTexture's transform matrix to compensate for the bitmap-convention V
    // baked into the panel texFill (top vertices at v=0, bottom at v=1) — without
    // this flip the OES path renders videos upside-down. The horizontal mirror is
    // handled in the texFill UVs themselves so the same buffer reads correctly
    // for both image (progT2) and video (progTex) paths.
    private val TEX_FLIP_V = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, -1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 1f, 0f, 1f
    )
    private val tmpTexMtx = FloatArray(16)

    private var prog = 0; private var aPos = 0; private var uMVP = 0; private var uCol = 0
    private var sw = 1; private var sh = 1

    private val VS = """
        uniform mat4 uMVP;
        attribute vec4 aPosition;
        void main(){ gl_Position = uMVP * aPosition; }""".trimIndent()
    private val FS = """
        precision mediump float;
        uniform vec4 uColor;
        void main(){ gl_FragColor = uColor; }""".trimIndent()

    // Textured (external-OES) program for the live camera walls
    private var progTex = 0; private var aPosT = 0; private var aTexT = 0
    private var uMVPT = 0; private var uTexMatrix = 0; private var uSampler = 0
    private val VST = """
        uniform mat4 uMVP;
        uniform mat4 uTexMatrix;
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTex;
        void main(){
            gl_Position = uMVP * aPosition;
            vTex = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
        }""".trimIndent()
    private val FST = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        uniform samplerExternalOES uTex;
        varying vec2 vTex;
        void main(){ gl_FragColor = texture2D(uTex, vTex); }""".trimIndent()

    // 2D textured program — used for image-piece panels and tunnel-wall fallback text
    private var progT2 = 0; private var aPosT2 = 0; private var aTexT2 = 0
    private var uMVPT2 = 0; private var uSamplerT2 = 0
    private val VST2 = """
        uniform mat4 uMVP;
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTex;
        void main(){
            gl_Position = uMVP * aPosition;
            vTex = aTexCoord;
        }""".trimIndent()
    private val FST2 = """
        precision mediump float;
        uniform sampler2D uTex;
        varying vec2 vTex;
        void main(){ gl_FragColor = texture2D(uTex, vTex); }""".trimIndent()
    private var texXMark = 0
    private var xMarkAspect = 2f   // bmW / bmH for the "[X]" bitmap; set in onSurfaceCreated
    private var texLeftWall = 0    // "Your perception."     fallback text on the left tunnel wall
    private var texRightWall = 0   // "Who perceives you?"   fallback text on the right tunnel wall
    private var wallLeftText: FloatBuffer? = null
    private var wallRightText: FloatBuffer? = null

    private var texRear = 0; private var texFront = 0
    @Volatile private var rearDirty = false; @Volatile private var rearHasContent = false
    @Volatile private var frontDirty = false; @Volatile private var frontHasContent = false
    private val rearTexMtx = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val frontTexMtx = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private var wallLeft: FloatBuffer? = null
    private var wallRight: FloatBuffer? = null

    // Blue wireframe (GL_LINES)
    private var roomLines: FloatBuffer? = null; private var roomLineCount = 0
    // Opaque dark surfaces
    private var wallFill: FloatBuffer? = null; private var wallFillCount = 0
    private var doorLeaf: FloatBuffer? = null; private var doorLeafCount = 0
    private var tunnelFill: FloatBuffer? = null; private var tunnelFillCount = 0
    private var tunnelLines: FloatBuffer? = null; private var tunnelLineCount = 0

    private class PanelGeo(
        val texFill: FloatBuffer,   // interleaved x,y,z,u,v (6 verts)
        val border: FloatBuffer,
        val xMark: FloatBuffer,     // [X] marker textured quad — interleaved x,y,z,u,v (6 verts)
        val glow: FloatBuffer, val order: Int,
        val r: Float, val g: Float, val b: Float
    )
    private val panels = mutableListOf<PanelGeo>()

    // A bright, distinct colour per activated screen (indexed by order)
    private val SCREEN_COLORS = arrayOf(
        floatArrayOf(1.0f, 0.18f, 0.18f),  // red
        floatArrayOf(1.0f, 0.55f, 0.10f),  // orange
        floatArrayOf(1.0f, 0.90f, 0.15f),  // yellow
        floatArrayOf(0.25f, 1.0f, 0.35f),  // green
        floatArrayOf(0.10f, 0.90f, 1.0f),  // cyan
        floatArrayOf(0.35f, 0.50f, 1.0f),  // blue
        floatArrayOf(0.70f, 0.30f, 1.0f),  // purple
        floatArrayOf(1.0f, 0.25f, 0.85f),  // magenta
        floatArrayOf(1.0f, 0.55f, 0.70f),  // pink
        floatArrayOf(0.70f, 1.0f, 0.20f),  // lime
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Floor & ceiling (the void behind the geometry): dark grey rather than pure
        // black, so the room is navigable instead of a black-on-black guessing game.
        GLES20.glClearColor(0.09f, 0.09f, 0.10f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        prog = buildProg(VS, FS)
        aPos = GLES20.glGetAttribLocation(prog, "aPosition")
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP")
        uCol = GLES20.glGetUniformLocation(prog, "uColor")

        progTex = buildProg(VST, FST)
        aPosT = GLES20.glGetAttribLocation(progTex, "aPosition")
        aTexT = GLES20.glGetAttribLocation(progTex, "aTexCoord")
        uMVPT = GLES20.glGetUniformLocation(progTex, "uMVP")
        uTexMatrix = GLES20.glGetUniformLocation(progTex, "uTexMatrix")
        uSampler = GLES20.glGetUniformLocation(progTex, "uTex")

        val ids = IntArray(2)
        GLES20.glGenTextures(2, ids, 0)
        texRear = ids[0]; texFront = ids[1]
        for (t in ids) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        rearSurfaceTexture = SurfaceTexture(texRear).also { it.setOnFrameAvailableListener { rearDirty = true } }
        frontSurfaceTexture = SurfaceTexture(texFront).also { it.setOnFrameAvailableListener { frontDirty = true } }

        // 2D textured program
        progT2 = buildProg(VST2, FST2)
        aPosT2 = GLES20.glGetAttribLocation(progT2, "aPosition")
        aTexT2 = GLES20.glGetAttribLocation(progT2, "aTexCoord")
        uMVPT2 = GLES20.glGetUniformLocation(progT2, "uMVP")
        uSamplerT2 = GLES20.glGetUniformLocation(progT2, "uTex")

        // ── Per-piece media textures (one per news item) ──────────────────────
        val mediaIds = IntArray(SCREEN_COUNT)
        GLES20.glGenTextures(SCREEN_COUNT, mediaIds, 0)
        for (i in 0 until SCREEN_COUNT) {
            mediaTex[i] = mediaIds[i]
            val item = DOOR4_NEWS[i]
            mediaIsVideo[i] = item.isVideo
            if (item.isVideo) {
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mediaIds[i])
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                // Resolve video dimensions (accounting for rotation metadata) so
                // the panel geometry below can be sized to the true aspect.
                var w = 1280; var h = 720
                try {
                    val mmr = MediaMetadataRetriever()
                    val afd = context.assets.openFd(item.asset)
                    try { mmr.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
                    finally { afd.close() }
                    w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: w
                    h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: h
                    val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (rot == 90 || rot == 270) { val tmp = w; w = h; h = tmp }
                    try { mmr.release() } catch (_: Throwable) {}
                } catch (_: Throwable) {}
                mediaPixelSize[i] = Pair(w, h)
                mediaAspect[i] = if (h > 0) w.toFloat() / h.toFloat() else 16f / 9f
                val idx = i
                val st = SurfaceTexture(mediaIds[i])
                st.setDefaultBufferSize(w, h)
                st.setOnFrameAvailableListener { mediaDirty[idx] = true }
                mediaSurfaceTexture[i] = st
            } else {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mediaIds[i])
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                try {
                    val opts = BitmapFactory.Options().apply { inScaled = false }
                    val bm = BitmapFactory.decodeStream(context.assets.open(item.asset), null, opts)
                    if (bm != null) {
                        mediaAspect[i] = if (bm.height > 0) bm.width.toFloat() / bm.height.toFloat() else 1f
                        mediaPixelSize[i] = Pair(bm.width, bm.height)
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bm, 0)
                        bm.recycle()
                        mediaHasContent[i] = true
                    }
                } catch (_: Throwable) {}
            }
        }

        // "[open]" marker — rendered once as monospace text on a transparent
        // bitmap and uploaded as a 2D texture. The bitmap aspect ratio is fed
        // back to buildScreens so the world quad's proportions match the
        // rendered glyph.
        try {
            val paint = Paint().apply {
                color = 0xFFFFFFFF.toInt()
                isAntiAlias = true
                typeface = Typeface.MONOSPACE
                textSize = 144f
            }
            val text = "[open]"
            val tw = paint.measureText(text)
            val fm = paint.fontMetrics
            val th = fm.descent - fm.ascent
            val pad = 8f
            val bmW = kotlin.math.ceil(tw + pad * 2f).toInt()
            val bmH = kotlin.math.ceil(th + pad * 2f).toInt()
            val xBm = Bitmap.createBitmap(bmW, bmH, Bitmap.Config.ARGB_8888)
            Canvas(xBm).drawText(text, pad, pad - fm.ascent, paint)
            val ids2 = IntArray(1)
            GLES20.glGenTextures(1, ids2, 0)
            texXMark = ids2[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texXMark)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, xBm, 0)
            xMarkAspect = bmW.toFloat() / bmH.toFloat()
            xBm.recycle()
        } catch (_: Throwable) {}

        // Fallback text for the tunnel walls (shown if the camera feed is
        // unavailable — e.g. the player denied the camera permission).
        texLeftWall = makeCenteredTextTexture("Your perception.", 1024, 512)
        texRightWall = makeCenteredTextTexture("Who perceives you?", 1024, 512)

        buildScene()
    }

    // Render a single line of monospace text centered in a transparent bitmap
    // and upload it as a GL_TEXTURE_2D. Returns the texture id (0 on failure).
    private fun makeCenteredTextTexture(text: String, bmW: Int, bmH: Int): Int {
        return try {
            val paint = Paint().apply {
                color = 0xFFFFFFFF.toInt()
                isAntiAlias = true
                typeface = Typeface.MONOSPACE
                textSize = bmH * 0.42f
            }
            // Shrink text size until it fits the bitmap width with a margin.
            while (paint.measureText(text) > bmW * 0.92f && paint.textSize > 8f) {
                paint.textSize = paint.textSize * 0.94f
            }
            val bm = Bitmap.createBitmap(bmW, bmH, Bitmap.Config.ARGB_8888)
            val fm = paint.fontMetrics
            val tw = paint.measureText(text)
            val baseline = (bmH - (fm.descent - fm.ascent)) / 2f - fm.ascent
            Canvas(bm).drawText(text, (bmW - tw) / 2f, baseline, paint)
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val texId = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bm, 0)
            bm.recycle()
            texId
        } catch (_: Throwable) { 0 }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h); sw = w; sh = h
    }

    private var lastFrameMs = 0L
    override fun onDrawFrame(gl: GL10?) {
        // Frame-rate cap (~33 fps) to cut sustained GPU/CPU load (heat + battery).
        val nowMs = android.os.SystemClock.uptimeMillis()
        val since = nowMs - lastFrameMs
        if (lastFrameMs != 0L && since in 0 until 30L) {
            try { Thread.sleep(30L - since) } catch (_: InterruptedException) {}
        }
        lastFrameMs = android.os.SystemClock.uptimeMillis()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(prog)

        val proj = FloatArray(16); val view = FloatArray(16); val mvp = FloatArray(16)
        Matrix.perspectiveM(proj, 0, 82f, sw.toFloat() / sh.toFloat(), 1f, 12000f)
        Matrix.setLookAtM(view, 0, camX, EYE_Y, camZ, lookAtX, EYE_Y, lookAtZ, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)

        // Opaque dark surfaces first
        // Walls a touch lighter than the floor/ceiling void so they read as surfaces.
        wallFill?.let { drawBuf(it, GLES20.GL_TRIANGLES, wallFillCount, 0.17f, 0.17f, 0.19f, 1f) }
        if (doorOpen < 0.5f) {
            doorLeaf?.let { drawBuf(it, GLES20.GL_TRIANGLES, doorLeafCount, 0.03f, 0.03f, 0.05f, 1f) }
        }

        // Blue wireframe
        roomLines?.let { drawBuf(it, GLES20.GL_LINES, roomLineCount, 0.16f, 0.34f, 0.85f, 1f) }

        // Screens — lit ones show the placeholder image, unlit ones a faint border.
        // Borders first (colored program already active).
        for (p in panels) {
            val lit = p.order < litCount
            val br = if (lit) 1f else 0.10f
            val bg = if (lit) 1f else 0.20f
            val bb = if (lit) 1f else 0.45f
            drawBuf(p.border, GLES20.GL_LINES, 8, br, bg, bb, 1f)
        }
        // Textured fills for revealed panels. Images use the 2D sampler; videos
        // use the OES sampler (camera-feed program is reused). Then the "[X]"
        // marker for the CURRENT target (foundCount).
        var anyLitImg = false
        var anyLitVid = false
        for (p in panels) {
            if (p.order >= litCount) continue
            if (mediaIsVideo[p.order]) anyLitVid = true else anyLitImg = true
        }

        if (anyLitImg) {
            GLES20.glUseProgram(progT2)
            GLES20.glUniformMatrix4fv(uMVPT2, 1, false, mvp, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glUniform1i(uSamplerT2, 0)
            for (p in panels) {
                if (p.order >= litCount || mediaIsVideo[p.order]) continue
                if (!mediaHasContent[p.order]) continue
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mediaTex[p.order])
                p.texFill.position(0)
                GLES20.glVertexAttribPointer(aPosT2, 3, GLES20.GL_FLOAT, false, 20, p.texFill)
                GLES20.glEnableVertexAttribArray(aPosT2)
                p.texFill.position(3)
                GLES20.glVertexAttribPointer(aTexT2, 2, GLES20.GL_FLOAT, false, 20, p.texFill)
                GLES20.glEnableVertexAttribArray(aTexT2)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            }
        }

        if (anyLitVid) {
            // Drain any newly-arrived video frames onto the OES textures.
            for (i in 0 until SCREEN_COUNT) {
                if (!mediaIsVideo[i] || i >= litCount) continue
                // Always drain the newest frame each render pass. Gating on the
                // non-volatile mediaDirty flag (set from the frame-available callback
                // thread) could be missed by the GL thread, freezing the video on its
                // first frame while the audio kept playing.
                mediaSurfaceTexture[i]?.let {
                    try { it.updateTexImage(); it.getTransformMatrix(mediaTexMtx[i]) }
                    catch (_: Throwable) {}
                }
                mediaHasContent[i] = true
                mediaDirty[i] = false
            }
            GLES20.glUseProgram(progTex)
            GLES20.glUniformMatrix4fv(uMVPT, 1, false, mvp, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glUniform1i(uSampler, 0)
            for (p in panels) {
                if (p.order >= litCount || !mediaIsVideo[p.order]) continue
                if (!mediaHasContent[p.order]) continue
                Matrix.multiplyMM(tmpTexMtx, 0, mediaTexMtx[p.order], 0, TEX_FLIP_V, 0)
                GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, tmpTexMtx, 0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mediaTex[p.order])
                p.texFill.position(0)
                GLES20.glVertexAttribPointer(aPosT, 3, GLES20.GL_FLOAT, false, 20, p.texFill)
                GLES20.glEnableVertexAttribArray(aPosT)
                p.texFill.position(3)
                GLES20.glVertexAttribPointer(aTexT, 2, GLES20.GL_FLOAT, false, 20, p.texFill)
                GLES20.glEnableVertexAttribArray(aTexT)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            }
        }

        // "[X]" markers — show only for the current target (revealed but not
        // yet read). Premultiplied-alpha blend over the wall.
        if (foundCount < SCREEN_COUNT && foundCount < litCount) {
            GLES20.glUseProgram(progT2)
            GLES20.glUniformMatrix4fv(uMVPT2, 1, false, mvp, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glUniform1i(uSamplerT2, 0)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texXMark)
            for (p in panels) {
                if (p.order != foundCount) continue
                p.xMark.position(0)
                GLES20.glVertexAttribPointer(aPosT2, 3, GLES20.GL_FLOAT, false, 20, p.xMark)
                GLES20.glEnableVertexAttribArray(aPosT2)
                p.xMark.position(3)
                GLES20.glVertexAttribPointer(aTexT2, 2, GLES20.GL_FLOAT, false, 20, p.xMark)
                GLES20.glEnableVertexAttribArray(aTexT2)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            }
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        // Restore colored program for the glow pass below
        GLES20.glUseProgram(prog)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)

        // Coloured "screen is on" glow — a frame AROUND the screen (screen cut out),
        // sitting slightly in front of the wall so it doesn't z-fight while walking.
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDepthMask(false)
        for (p in panels) {
            if (p.order < litCount) drawBuf(p.glow, GLES20.GL_TRIANGLES, 24, p.r, p.g, p.b, 0.22f)
        }
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)

        // Tunnel (revealed as doorOpen rises)
        if (doorOpen > 0.001f) {
            tunnelFill?.let { drawBuf(it, GLES20.GL_TRIANGLES, tunnelFillCount, 0.03f, 0.03f, 0.05f, 1f) }
            tunnelLines?.let { drawBuf(it, GLES20.GL_LINES, tunnelLineCount, 0.16f, 0.34f, 0.85f, 1f) }

            if (rearDirty) {
                rearSurfaceTexture?.let { it.updateTexImage(); it.getTransformMatrix(rearTexMtx) }
                rearHasContent = true; rearDirty = false
            }
            if (frontDirty) {
                frontSurfaceTexture?.let { it.updateTexImage(); it.getTransformMatrix(frontTexMtx) }
                frontHasContent = true; frontDirty = false
            }
            val leftLive = rearLive && rearHasContent
            val rightLive = frontLive && frontHasContent
            if (!leftLive)  wallLeft?.let  { drawWallSolid(it, 0.05f, 0.09f, 0.09f) }
            if (!rightLive) wallRight?.let { drawWallSolid(it, 0.05f, 0.09f, 0.09f) }
            if (leftLive || rightLive) {
                GLES20.glUseProgram(progTex)
                GLES20.glUniformMatrix4fv(uMVPT, 1, false, mvp, 0)
                if (leftLive)  wallLeft?.let  { drawWallTex(it, texRear, rearTexMtx) }
                if (rightLive) wallRight?.let { drawWallTex(it, texFront, frontTexMtx) }
            }
            // Fallback text on whichever wall isn't showing a live camera feed.
            if (!leftLive || !rightLive) {
                GLES20.glUseProgram(progT2)
                GLES20.glUniformMatrix4fv(uMVPT2, 1, false, mvp, 0)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glUniform1i(uSamplerT2, 0)
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                if (!leftLive && texLeftWall != 0)  wallLeftText?.let  { drawWallTex2D(it, texLeftWall) }
                if (!rightLive && texRightWall != 0) wallRightText?.let { drawWallTex2D(it, texRightWall) }
                GLES20.glDisable(GLES20.GL_BLEND)
            }
        }
    }

    private fun drawBuf(buf: FloatBuffer, mode: Int, count: Int, r: Float, g: Float, b: Float, a: Float) {
        GLES20.glUniform4f(uCol, r, g, b, a)
        buf.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, buf)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glDrawArrays(mode, 0, count)
    }

    private fun drawWallSolid(buf: FloatBuffer, r: Float, g: Float, b: Float) {
        GLES20.glUniform4f(uCol, r, g, b, 1f)
        buf.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 20, buf)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
    }

    private fun drawWallTex(buf: FloatBuffer, tex: Int, texMtx: FloatArray) {
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMtx, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glUniform1i(uSampler, 0)
        buf.position(0)
        GLES20.glVertexAttribPointer(aPosT, 3, GLES20.GL_FLOAT, false, 20, buf)
        GLES20.glEnableVertexAttribArray(aPosT)
        buf.position(3)
        GLES20.glVertexAttribPointer(aTexT, 2, GLES20.GL_FLOAT, false, 20, buf)
        GLES20.glEnableVertexAttribArray(aTexT)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
    }

    // 2D-textured wall quad — same interleaved x,y,z,u,v layout as drawWallTex
    // but sampled from a sampler2D (the progT2 program is expected to be active).
    private fun drawWallTex2D(buf: FloatBuffer, tex: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        buf.position(0)
        GLES20.glVertexAttribPointer(aPosT2, 3, GLES20.GL_FLOAT, false, 20, buf)
        GLES20.glEnableVertexAttribArray(aPosT2)
        buf.position(3)
        GLES20.glVertexAttribPointer(aTexT2, 2, GLES20.GL_FLOAT, false, 20, buf)
        GLES20.glEnableVertexAttribArray(aTexT2)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
    }

    // ── Scene ──────────────────────────────────────────────────────────────────
    private fun buildScene() {
        buildWallFills()
        buildWireframe()
        buildScreens()
        buildTunnel()
    }

    private fun box(v: ArrayList<Float>, xc: Float, zc: Float, hx: Float, hz: Float, h: Float) {
        fun q(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
            v.addAll(listOf(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]))
            v.addAll(listOf(a[0], a[1], a[2], c[0], c[1], c[2], d[0], d[1], d[2]))
        }
        fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
        val x0 = xc - hx; val x1 = xc + hx; val z0 = zc - hz; val z1 = zc + hz
        q(p(x0, 0f, z0), p(x1, 0f, z0), p(x1, h, z0), p(x0, h, z0))  // -z
        q(p(x0, 0f, z1), p(x1, 0f, z1), p(x1, h, z1), p(x0, h, z1))  // +z
        q(p(x0, 0f, z0), p(x0, 0f, z1), p(x0, h, z1), p(x0, h, z0))  // -x
        q(p(x1, 0f, z0), p(x1, 0f, z1), p(x1, h, z1), p(x1, h, z0))  // +x
        q(p(x0, h, z0), p(x1, h, z0), p(x1, h, z1), p(x0, h, z1))    // top
    }

    private fun buildWallFills() {
        val v = ArrayList<Float>()
        fun q(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
            v.addAll(listOf(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]))
            v.addAll(listOf(a[0], a[1], a[2], c[0], c[1], c[2], d[0], d[1], d[2]))
        }
        fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
        val R = ROOM_R; val H = ROOM_H; val dL = DOOR_X - DOOR_HW; val dR = DOOR_X + DOOR_HW
        // Outer walls (north split around the door)
        q(p(-R, 0f, -R), p(dL, 0f, -R), p(dL, H, -R), p(-R, H, -R))
        q(p(dR, 0f, -R), p(R, 0f, -R), p(R, H, -R), p(dR, H, -R))
        q(p(-R, 0f, R), p(R, 0f, R), p(R, H, R), p(-R, H, R))
        q(p(-R, 0f, -R), p(-R, 0f, R), p(-R, H, R), p(-R, H, -R))
        q(p(R, 0f, -R), p(R, 0f, R), p(R, H, R), p(R, H, -R))
        q(p(-R, 0f, -R), p(R, 0f, -R), p(R, 0f, R), p(-R, 0f, R))   // floor
        q(p(-R, H, -R), p(R, H, -R), p(R, H, R), p(-R, H, R))       // ceiling
        // Interior maze slabs
        for (s in DOOR4_SLABS) box(v, s[0], s[1], s[2], s[3], H)

        wallFillCount = v.size / 3
        wallFill = v.toFloatArray().toFB2()

        val leaf = ArrayList<Float>()
        leaf.addAll(listOf(dL, 0f, -R, dR, 0f, -R, dR, H, -R))
        leaf.addAll(listOf(dL, 0f, -R, dR, H, -R, dL, H, -R))
        doorLeafCount = leaf.size / 3
        doorLeaf = leaf.toFloatArray().toFB2()
    }

    private fun buildWireframe() {
        val v = ArrayList<Float>()
        fun edge(a: FloatArray, b: FloatArray) {
            v.addAll(listOf(a[0], a[1], a[2], b[0], b[1], b[2]))
        }
        fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
        val R = ROOM_R; val H = ROOM_H
        // Outer box edges
        val cs = listOf(p(-R, 0f, -R), p(R, 0f, -R), p(R, 0f, R), p(-R, 0f, R))
        for (i in cs.indices) {
            val a = cs[i]; val b = cs[(i + 1) % cs.size]
            edge(a, b); edge(p(a[0], H, a[2]), p(b[0], H, b[2])); edge(a, p(a[0], H, a[2]))
        }
        // Slab box edges (vertical free-end edges + top rectangle)
        for (s in DOOR4_SLABS) {
            val x0 = s[0] - s[2]; val x1 = s[0] + s[2]; val z0 = s[1] - s[3]; val z1 = s[1] + s[3]
            val corners = listOf(p(x0, 0f, z0), p(x1, 0f, z0), p(x1, 0f, z1), p(x0, 0f, z1))
            for (i in corners.indices) {
                val a = corners[i]; val b = corners[(i + 1) % corners.size]
                edge(a, b)
                edge(p(a[0], H, a[2]), p(b[0], H, b[2]))
                edge(a, p(a[0], H, a[2]))
            }
        }
        roomLineCount = v.size / 3
        roomLines = v.toFloatArray().toFB2()
    }

    private fun buildScreens() {
        panels.clear()
        val midY = ROOM_H * 0.5f
        // Push past the wall slab thickness (WT_H) so screens render in front of the
        // wall's near face instead of being hidden inside the slab body.
        val eps = WT_H + 6f; val gEps = 9f
        for (s in DOOR4_SCREENS) {
            // Panel half-extents follow the media's aspect ratio so the frame
            // hugs the media exactly — no background left over on square or
            // vertical pieces.
            val (halfW, halfH) = panelHalfExtents(mediaAspect[s.order])
            // Panel corners (BL,BR,TR,TL), pushed in front of the wall toward viewer
            val bl: FloatArray; val br: FloatArray; val tr: FloatArray; val tl: FloatArray
            if (s.axis == 1) {
                val zc = s.c + s.nrm * eps
                bl = floatArrayOf(s.lat - halfW, midY - halfH, zc)
                br = floatArrayOf(s.lat + halfW, midY - halfH, zc)
                tr = floatArrayOf(s.lat + halfW, midY + halfH, zc)
                tl = floatArrayOf(s.lat - halfW, midY + halfH, zc)
            } else {
                val xc = s.c + s.nrm * eps
                bl = floatArrayOf(xc, midY - halfH, s.lat - halfW)
                br = floatArrayOf(xc, midY - halfH, s.lat + halfW)
                tr = floatArrayOf(xc, midY + halfH, s.lat + halfW)
                tl = floatArrayOf(xc, midY + halfH, s.lat - halfW)
            }
            // For a wall with outward normal +X (nrm=+1, axis=0), the player views
            // from +X looking −X, so their right vector resolves to −Z = −lat.
            // That means world-BL (smaller lat) lands on the player's bottom-right,
            // so the bitmap's right edge (u=1) must map to world-BL to read upright.
            // Mirror logic for nrm=−1 walls. (Without this, all pieces show
            // horizontally mirrored.) Videos share the same UVs and have their
            // remaining V-flip corrected via TEX_FLIP_V in the OES draw call.
            val uL = if (s.nrm < 0) 0f else 1f
            val uR = if (s.nrm < 0) 1f else 0f
            val texFill = floatArrayOf(
                bl[0], bl[1], bl[2], uL, 1f,
                br[0], br[1], br[2], uR, 1f,
                tr[0], tr[1], tr[2], uR, 0f,
                bl[0], bl[1], bl[2], uL, 1f,
                tr[0], tr[1], tr[2], uR, 0f,
                tl[0], tl[1], tl[2], uL, 0f
            ).toFB2()
            val border = floatArrayOf(
                bl[0], bl[1], bl[2], br[0], br[1], br[2],
                br[0], br[1], br[2], tr[0], tr[1], tr[2],
                tr[0], tr[1], tr[2], tl[0], tl[1], tl[2],
                tl[0], tl[1], tl[2], bl[0], bl[1], bl[2]
            ).toFB2()

            // "[open]" marker — small textured quad anchored under the visually-
            // right corner of the screen (which depends on which side the player
            // views from, i.e. on nrm). We size by a fixed world-height so the
            // glyph stays legible no matter how wide the rendered text bitmap
            // becomes; width then follows the bitmap's aspect ratio.
            val xH = 46f
            val xW = xH * xMarkAspect
            val xGap = 60f
            val xMark = run {
                val xTopY = midY - halfH - xGap
                val xBotY = xTopY - xH
                // Lateral centre: nrm=+1 puts the marker on the +lat side, nrm=-1
                // on the -lat side, so it sits under the corner the viewer sees
                // as bottom-right.
                val cLat = s.lat + s.nrm * (halfW - xW * 0.5f)
                val nearLat = cLat - xW * 0.5f
                val farLat = cLat + xW * 0.5f
                if (s.axis == 1) {
                    val zc = bl[2]
                    floatArrayOf(
                        nearLat, xBotY, zc, uL, 1f,
                        farLat,  xBotY, zc, uR, 1f,
                        farLat,  xTopY, zc, uR, 0f,
                        nearLat, xBotY, zc, uL, 1f,
                        farLat,  xTopY, zc, uR, 0f,
                        nearLat, xTopY, zc, uL, 0f
                    ).toFB2()
                } else {
                    val xc = bl[0]
                    floatArrayOf(
                        xc, xBotY, nearLat, uL, 1f,
                        xc, xBotY, farLat,  uR, 1f,
                        xc, xTopY, farLat,  uR, 0f,
                        xc, xBotY, nearLat, uL, 1f,
                        xc, xTopY, farLat,  uR, 0f,
                        xc, xTopY, nearLat, uL, 0f
                    ).toFB2()
                }
            }

            // Glow frame: ring between the screen rect (inner, hole) and a grown rect
            // (outer), all pushed a bit further in front so the screen shows through.
            val cx = (bl[0] + br[0] + tr[0] + tl[0]) / 4f
            val cy = (bl[1] + br[1] + tr[1] + tl[1]) / 4f
            val cz = (bl[2] + br[2] + tr[2] + tl[2]) / 4f
            val off = s.nrm * gEps
            fun front(p: FloatArray) =
                if (s.axis == 1) floatArrayOf(p[0], p[1], p[2] + off) else floatArrayOf(p[0] + off, p[1], p[2])
            fun grow(p: FloatArray): FloatArray {
                val k = 1.16f
                val gx = cx + (p[0] - cx) * k; val gy = cy + (p[1] - cy) * k; val gz = cz + (p[2] - cz) * k
                return front(floatArrayOf(gx, gy, gz))
            }
            val iBL = front(bl); val iBR = front(br); val iTR = front(tr); val iTL = front(tl)
            val oBL = grow(bl); val oBR = grow(br); val oTR = grow(tr); val oTL = grow(tl)
            fun band(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, sink: ArrayList<Float>) {
                sink.addAll(listOf(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]))
                sink.addAll(listOf(a[0], a[1], a[2], c[0], c[1], c[2], d[0], d[1], d[2]))
            }
            val gv = ArrayList<Float>()
            band(oBL, oBR, iBR, iBL, gv)  // bottom
            band(iBR, oBR, oTR, iTR, gv)  // right
            band(iTL, iTR, oTR, oTL, gv)  // top
            band(oBL, iBL, iTL, oTL, gv)  // left
            val glow = gv.toFloatArray().toFB2()

            val col = SCREEN_COLORS[s.order % SCREEN_COLORS.size]
            panels.add(PanelGeo(texFill, border, xMark, glow, s.order, col[0], col[1], col[2]))
        }
    }

    private fun buildTunnel() {
        val lines = ArrayList<Float>()
        fun edge(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
            lines.addAll(listOf(x0, y0, z0, x1, y1, z1))
        }
        val zNear = -ROOM_R; val zFar = -ROOM_R - TUNNEL_LEN
        val xL = DOOR_X - DOOR_HW; val xR = DOOR_X + DOOR_HW; val H = ROOM_H
        edge(xL, 0f, zNear, xL, H, zNear); edge(xR, 0f, zNear, xR, H, zNear); edge(xL, H, zNear, xR, H, zNear)
        edge(xL, 0f, zNear, xL, 0f, zFar); edge(xL, H, zNear, xL, H, zFar)
        edge(xR, 0f, zNear, xR, 0f, zFar); edge(xR, H, zNear, xR, H, zFar)
        edge(xL, 0f, zFar, xR, 0f, zFar); edge(xL, H, zFar, xR, H, zFar)
        edge(xL, 0f, zFar, xL, H, zFar);  edge(xR, 0f, zFar, xR, H, zFar)
        tunnelLineCount = lines.size / 3
        tunnelLines = lines.toFloatArray().toFB2()

        wallLeft = floatArrayOf(
            xL, 0f, zNear, 0f, 0f,  xL, 0f, zFar, 1f, 0f,  xL, H, zFar, 1f, 1f,
            xL, 0f, zNear, 0f, 0f,  xL, H, zFar, 1f, 1f,   xL, H, zNear, 0f, 1f
        ).toFB2()
        wallRight = floatArrayOf(
            xR, 0f, zNear, 0f, 0f,  xR, 0f, zFar, 1f, 0f,  xR, H, zFar, 1f, 1f,
            xR, 0f, zNear, 0f, 0f,  xR, H, zFar, 1f, 1f,   xR, H, zNear, 0f, 1f
        ).toFB2()

        // Fallback-text geometry — same quad as the camera walls, but with UVs
        // that read upright (bitmap top = ceiling) and run left-to-right from
        // the player's perspective as they walk down the tunnel. The right wall
        // gets its u flipped because the player's "right" there is +z (zNear),
        // not +z's image counterpart.
        wallLeftText = floatArrayOf(
            xL, 0f, zNear, 0f, 1f,  xL, 0f, zFar, 1f, 1f,  xL, H, zFar, 1f, 0f,
            xL, 0f, zNear, 0f, 1f,  xL, H, zFar, 1f, 0f,   xL, H, zNear, 0f, 0f
        ).toFB2()
        wallRightText = floatArrayOf(
            xR, 0f, zNear, 1f, 1f,  xR, 0f, zFar, 0f, 1f,  xR, H, zFar, 0f, 0f,
            xR, 0f, zNear, 1f, 1f,  xR, H, zFar, 0f, 0f,   xR, H, zNear, 1f, 0f
        ).toFB2()

        val tf = ArrayList<Float>()
        fun q(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
            tf.addAll(listOf(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]))
            tf.addAll(listOf(a[0], a[1], a[2], c[0], c[1], c[2], d[0], d[1], d[2]))
        }
        fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
        q(p(xL, 0f, zNear), p(xR, 0f, zNear), p(xR, 0f, zFar), p(xL, 0f, zFar))
        q(p(xL, H, zNear), p(xR, H, zNear), p(xR, H, zFar), p(xL, H, zFar))
        q(p(xL, 0f, zFar), p(xR, 0f, zFar), p(xR, H, zFar), p(xL, H, zFar))
        tunnelFillCount = tf.size / 3
        tunnelFill = tf.toFloatArray().toFB2()
    }

    private fun buildProg(vs: String, fs: String): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, comp(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, comp(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }
    private fun comp(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        return s
    }
}

private fun FloatArray.toFB2(): FloatBuffer =
    ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().also { it.put(this); it.position(0) }
