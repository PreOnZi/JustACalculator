package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import com.fictioncutshort.justacalculator.platform.AppPermission
import com.fictioncutshort.justacalculator.platform.Assets
import com.fictioncutshort.justacalculator.platform.FaceFrame
import com.fictioncutshort.justacalculator.platform.PlatformCameraSurface
import com.fictioncutshort.justacalculator.platform.Sounds
import com.fictioncutshort.justacalculator.platform.createSoundEffectPool
import com.fictioncutshort.justacalculator.platform.currentAppContext
import com.fictioncutshort.justacalculator.platform.hasPermission
import com.fictioncutshort.justacalculator.platform.logWarn
import com.fictioncutshort.justacalculator.platform.nowMillis
import com.fictioncutshort.justacalculator.platform.rememberPermissionRequest
import com.fictioncutshort.justacalculator.platform.rememberPermissionState
import com.fictioncutshort.justacalculator.platform.renderSvgAsset
import com.fictioncutshort.justacalculator.platform.saveCaptureLocally
import com.fictioncutshort.justacalculator.platform.saveImageToGallery
import com.fictioncutshort.justacalculator.gl.loadImageBitmapAsset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun Building7VanityRoom(modifier: Modifier = Modifier, onComplete: () -> Unit = {}) {
    val context = currentAppContext()

    // Re-read on resume: "Allow Once" lapses the moment the app is
    // backgrounded, and the player who granted it that way comes back to a
    // camera that is silently dead. Asking again is cheap when it is still
    // granted — the platform answers immediately without a dialog.
    val cameraGranted = rememberPermissionState(AppPermission.CAMERA)
    var hasCameraPermission by cameraGranted
    val requestCamera = rememberPermissionRequest(AppPermission.CAMERA) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(hasCameraPermission) { if (!hasCameraPermission) requestCamera() }

    // Load + rasterise SVG filters from assets/filters/ once, keyed by filename.
    val loaded = remember {
        val stickers = mutableListOf<StickerAsset>()
        var body: ImageBitmap? = null
        var background: ImageBitmap? = null
        try {
            for (f in Assets.list("filters")) {
                val stem = f.substringBeforeLast('.').lowercase()
                if (stem == "background") {
                    background = loadImageBitmapAsset("filters/$f")
                    continue
                }
                if (!f.endsWith(".svg", ignoreCase = true)) continue
                if (stem == BODY_ASSET) {
                    body = renderSvgAsset("filters/$f", 1024)
                    continue
                }
                val slot = slotForName(stem) ?: continue
                val targetW = if (slot == Slot.FACE) 640 else 512
                val bmp = renderSvgAsset("filters/$f", targetW) ?: continue
                stickers.add(StickerAsset(f, slot, pointsForSticker(stem), bmp))
            }
        } catch (e: Exception) {
            logWarn("Building7", "Failed to load filter assets: ${e.message}")
        }
        stickers.sortBy { it.slot.ordinal }
        Triple(stickers.toList(), body, background)
    }
    val stickers = loaded.first
    val stickersBySlot = remember(stickers) { stickers.groupBy { it.slot } }
    val bodyImage = loaded.second
    val backgroundImage = loaded.third

    // Per-slot active sticker (filename, or absent = none). Slots layer.
    // Restored from the saved run: the stickers on each slot, and the look. Coming
    // back to the mirror should show you the face you left with, not a reset one -
    // the whole point of the room is the look you build.
    val selected = remember {
        mutableStateMapOf<Slot, String>().apply {
            for (entry in com.fictioncutshort.justacalculator.logic.BuildingProgress.getSet(context, 7, "stickers")) {
                val i = entry.indexOf('=')
                if (i <= 0) continue
                val slot = Slot.entries.firstOrNull { it.name == entry.substring(0, i) } ?: continue
                put(slot, entry.substring(i + 1))
            }
        }
    }
    // Active "Look" colour/lighting grade.
    var lookIndex by remember { mutableIntStateOf(com.fictioncutshort.justacalculator.logic.BuildingProgress.getInt(context, 7, "look", 0)) }
    LaunchedEffect(lookIndex) { com.fictioncutshort.justacalculator.logic.BuildingProgress.putInt(context, 7, "look", lookIndex) }
    LaunchedEffect(selected.size, selected.entries.joinToString()) {
        com.fictioncutshort.justacalculator.logic.BuildingProgress.putSet(context, 7, "stickers", selected.entries.map { "${it.key.name}=${it.value}" }.toSet())
    }
    val look = LOOKS[lookIndex]
    // BODY mode (camera piped into the character ellipse). Mutually takes over.
    val bodyMode = remember { mutableStateOf(false) }

    // Live "style points" for the current outfit: the Look grade + every worn
    // item (or the body suit, which replaces the stickers).
    val styleScore by remember(stickers) {
        derivedStateOf {
            var s = LOOKS[lookIndex].points
            if (bodyMode.value) s += BODY_POINTS
            else for (name in selected.values) {
                stickers.firstOrNull { it.name == name }?.let { s += it.points }
            }
            s
        }
    }

    // Latest detections + the frame that produced them (kept for capture/body draw).
    // ALL faces in view get decorated; the largest is the "primary" used by the
    // single-subject body mode.
    // The seam delivers frames already upright and mirrored, with face
    // coordinates in that same space — so there is no rotation to track and
    // nothing to flip.
    var latestFrame by remember { mutableStateOf<FaceFrame?>(null) }
    val faces = latestFrame?.faces.orEmpty()
    val primaryFace = faces.firstOrNull()
    val srcW = latestFrame?.width ?: 0
    val srcH = latestFrame?.height ?: 0
    var savedFlash by remember { mutableStateOf(false) }

    // Shutter feedback: a white screen flash + the camera.mp3 SFX on each shot.
    val flashAlpha = remember { Animatable(0f) }
    var shutterTick by remember { mutableIntStateOf(0) }
    val soundPool = remember { createSoundEffectPool(maxStreams = 2) }
    val shutterSound = remember { soundPool.load(Sounds.path("camera").orEmpty()) }
    LaunchedEffect(shutterTick) {
        if (shutterTick > 0) {
            flashAlpha.snapTo(0.9f)
            flashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 450))
        }
    }

    // Exit is gated: the player can't leave until they've captured 3 shots, and
    // every shot must use a DISTINCT set of worn assets (no duplicate selfies).
    val requiredCaptures = 3
    val capturedOutfits = remember { mutableStateListOf<String>() }
    val captureCount = capturedOutfits.size
    // Narration inside the vanity room: vo010 as the UI lands, vo011 right after.
    // vo009 was started when the player agreed to enter (out in the city) — wait for
    // it to finish before starting vo010 so they don't stomp on each other.
    LaunchedEffect(Unit) {
        com.fictioncutshort.justacalculator.logic.VoiceoverManager.init(context)
        while (com.fictioncutshort.justacalculator.logic.VoiceoverManager.isPlaying()) {
            kotlinx.coroutines.delay(150)
        }
        com.fictioncutshort.justacalculator.logic.VoiceoverManager.playSequence(
            listOf("vo010", "vo011"), cctv = false
        )
    }
    // vo012 — after the first "look" the player saves (their first capture).
    LaunchedEffect(captureCount) {
        if (captureCount == 1) {
            com.fictioncutshort.justacalculator.logic.VoiceoverManager.play("vo012", cctv = false)
        }
    }
    var lockedFlash by remember { mutableStateOf(false) }
    var dupeFlash by remember { mutableStateOf(false) }
    val canLeave = captureCount >= requiredCaptures

    // Signature of the currently worn assets (the Look grade is NOT part of it —
    // a new selfie needs different items, not just a different colour filter).
    fun outfitSignature(): String =
        if (bodyMode.value) "body"
        else selected.entries.sortedBy { it.key.ordinal }
            .joinToString(",") { "${it.key}=${it.value}" }
            .ifEmpty { "bare" }

    DisposableEffect(Unit) {
        onDispose {
            latestFrame = null
            soundPool.release()
        }
    }

    LaunchedEffect(savedFlash) { if (savedFlash) { delay(1200); savedFlash = false } }
    LaunchedEffect(lockedFlash) { if (lockedFlash) { delay(1800); lockedFlash = false } }
    LaunchedEffect(dupeFlash) { if (dupeFlash) { delay(1800); dupeFlash = false } }

    // Active anchored stickers for this frame (skipped entirely in body mode).
    val placements: List<Placement> = remember(latestFrame, selected.toMap(), bodyMode.value) {
        if (bodyMode.value || faces.isEmpty()) return@remember emptyList()
        // Props for EVERY face in view, not just the nearest.
        faces.flatMap { buildPlacements(it, selected, stickersBySlot) }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        if (hasCameraPermission) {
            val graded = look.makeMatrix()
            PlatformCameraSurface(
                modifier = Modifier.fillMaxSize(),
                useFrontCamera = true,
                onFaceFrame = { latestFrame = it },
            )

            // With a grade active, the analysed frame is redrawn over the live
            // preview with the colour matrix applied. Neither platform can
            // filter its own preview surface portably — Android's RenderEffect
            // needs a TextureView and API 31, and iOS cannot filter a capture
            // preview layer at all — so the graded feed runs at the analysis
            // rate rather than the preview's. "None" leaves the native preview
            // untouched, which is the common case.
            if (graded != null && !bodyMode.value) {
                latestFrame?.image?.let { frame ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCover(frame, size.width, size.height)
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val sc = max(size.width / frame.width, size.height / frame.height)
                        val dw = frame.width * sc
                        val dh = frame.height * sc
                        drawImage(
                            image = frame,
                            dstOffset = IntOffset(
                                ((size.width - dw) / 2f).roundToInt(),
                                ((size.height - dh) / 2f).roundToInt(),
                            ),
                            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                            colorFilter = ColorFilter.colorMatrix(graded),
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }

            if (bodyMode.value && bodyImage != null) {
                // BODY — character art with the live camera poured into its face.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawBodyComposite(bodyImage, backgroundImage, latestFrame?.image, primaryFace, look)
                }
            } else {
                // Live sticker overlay, then the Look scrim/vignette on top.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPlacements(placements, srcW, srcH, fillCanvas = true)
                    drawLookScrim(look)
                }
            }

            if (primaryFace == null) {
                Text(
                    if (bodyMode.value) "Line your face up with the cut-out"
                    else "Point the front camera at your face",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        } else {
            Text(
                "Camera permission is needed for the mirror.",
                color = Color.White, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }

        // Close / done — locked until the required number of captures is reached.
        Text(if (canLeave) "✕" else "🔒", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                .clickable {
                    if (canLeave) {
                        // The look you leave with becomes your star currency.
                        com.fictioncutshort.justacalculator.logic.CurrencyStore.award(
                            context, com.fictioncutshort.justacalculator.logic.Currency.STARS, styleScore, "b7")
                        onComplete()
                    } else lockedFlash = true
                }
                .padding(horizontal = 14.dp, vertical = 6.dp))

        // Capture progress (top-right)
        Text("$captureCount / $requiredCaptures",
            color = if (canLeave) Color(0xFF8BE9A0) else Color.White, fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp))

        val banner = when {
            dupeFlash -> "You already shot this look — change an item first"
            lockedFlash -> "Take $requiredCaptures different looks before you can leave"
            savedFlash && canLeave -> "Saved ✓ — you can leave now"
            savedFlash -> "Saved ✓ (${requiredCaptures - captureCount} more to leave)"
            else -> null
        }
        if (banner != null) {
            Text(banner, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)
                    .background(
                        if (lockedFlash || dupeFlash) Color(0xCCB03A2E) else Color(0xCC1E7D34),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // Bottom controls: the two carousels + shutter (kept compact).
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.38f))
                .padding(bottom = 12.dp, top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Style score for the current outfit (Look + worn items).
            Row(
                modifier = Modifier.padding(bottom = 6.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val starIcon = rememberCurrencyIcon(com.fictioncutshort.justacalculator.logic.Currency.STARS)
                if (starIcon != null) {
                    Image(
                        bitmap = starIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text("$styleScore  STYLE", color = Color(0xFFFFD45A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // ── Carousel 1: Look (colour & lighting) ──
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LOOKS.forEachIndexed { i, lf ->
                    val isOn = i == lookIndex
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(36.dp)
                                .background(lf.swatch, CircleShape)
                                .border(
                                    width = if (isOn) 3.dp else 1.dp,
                                    color = if (isOn) Color(0xFF00B3C0) else Color.White.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                                .clickable { lookIndex = i }
                        )
                        Text(lf.name, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Carousel 2: Stickers (overlays + body) ──
            if (stickers.isEmpty() && bodyImage == null) {
                Text("Add .svg files to assets/filters/ to create stickers.",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Body chip first (it takes over the screen).
                    if (bodyImage != null) {
                        StickerChip(
                            image = bodyImage, label = "Body", isOn = bodyMode.value,
                            onClick = { bodyMode.value = !bodyMode.value }
                        )
                    }
                    for (a in stickers) {
                        val isOn = !bodyMode.value && selected[a.slot] == a.name
                        StickerChip(
                            image = a.image, label = a.slot.label, isOn = isOn,
                            dimmed = bodyMode.value,
                            onClick = {
                                bodyMode.value = false
                                if (selected[a.slot] == a.name) selected.remove(a.slot)
                                else selected[a.slot] = a.name
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Shutter
            Box(
                modifier = Modifier.size(68.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .clickable {
                        val sig = outfitSignature()
                        if (capturedOutfits.contains(sig)) {
                            dupeFlash = true
                        } else {
                            val frame = latestFrame?.image
                            val ok = when {
                                frame == null -> false
                                bodyMode.value && bodyImage != null -> saveVanityCapture(
                                    renderBodyCapture(frame, primaryFace, bodyImage, backgroundImage, look)
                                )
                                else -> saveVanityCapture(
                                    renderStickerCapture(frame, placements, look)
                                )
                            }
                            savedFlash = ok
                            if (ok) {
                                capturedOutfits.add(sig)
                                shutterTick++
                                soundPool.play(shutterSound, 1f)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(54.dp).background(Color.White, CircleShape))
            }
        }

        // Shutter flash — a quick white wash over the whole screen on capture.
        if (flashAlpha.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha.value)))
        }
    }
}

@Composable
private fun StickerChip(
    image: ImageBitmap, label: String, isOn: Boolean,
    dimmed: Boolean = false, onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = image, contentDescription = label,
            contentScale = ContentScale.Fit,
            alpha = if (dimmed) 0.45f else 1f,
            modifier = Modifier.size(44.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                .border(
                    width = if (isOn) 2.5.dp else 1.dp,
                    color = if (isOn) Color(0xFF00B3C0) else Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(9.dp)
                )
                .clickable { onClick() }
                .padding(5.dp)
        )
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
    }
}

// ── Sticker placement (all in MIRRORED upright-image coordinates) ────────────
/**
 * Saves one shot: a copy the player keeps in their gallery, and a private one
 * Building 3 replays later.
 */
private fun saveVanityCapture(image: ImageBitmap): Boolean {
    val name = "vanity_${nowMillis()}.jpg"
    val saved = saveImageToGallery(name, image)
    saveCaptureLocally(name, image)
    return saved
}
