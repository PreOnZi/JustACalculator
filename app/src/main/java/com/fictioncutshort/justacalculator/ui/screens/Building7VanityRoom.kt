package com.fictioncutshort.justacalculator.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.caverock.androidsvg.SVG
import com.fictioncutshort.justacalculator.R
import com.fictioncutshort.justacalculator.ui.components.RequestGamePermissionsOnEntry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Building 7 — "Vanity" room.
//
// A front-camera mirror with a Snapchat-style filter engine. There are TWO
// carousels:
//   • LOOK   — colour & lighting filters (grade the whole feed).
//   • STICKER — vector overlays loaded from app/src/main/assets/filters/*.svg,
//               anchored to ML Kit face landmarks by FILENAME:
//                 crown / hat            → HEAD (above the forehead)
//                 glasses / glasses01    → EYES (over the eyes)
//                 face / face1 / mask    → FACE (cover the whole face)
//                 full                   → BODY — a character whose face is a
//                                          transparent ellipse; the live camera
//                                          is piped into that ellipse.
//
// SVGs are rasterised once with AndroidSVG (already on the classpath via
// coil-svg) so the rest of the engine works in plain bitmaps.
//
// NOTE: capture is explicit and disclosed — the only image saved is the one the
// user deliberately shoots with the shutter.
// ─────────────────────────────────────────────────────────────────────────────

// Face slot a sticker is anchored to.
private enum class Slot(val label: String) { HEAD("Head"), EYES("Eyes"), FACE("Face") }

private fun slotForName(stem: String): Slot? = when {
    stem == "crown" || stem == "hat"               -> Slot.HEAD
    stem.startsWith("glasses")                     -> Slot.EYES
    stem == "face" || stem == "face1" || stem == "mask" -> Slot.FACE
    else                                           -> null
}

// Static "style points" awarded for wearing each item (see styleScore).
private fun pointsForSticker(stem: String): Int = when {
    stem == "crown"     -> 25
    stem == "hat"       -> 12
    stem == "mask"      -> 20
    stem == "face"      -> 14
    stem == "face1"     -> 18
    stem == "glasses"   -> 10
    stem == "glasses01" -> 14
    stem.startsWith("glasses") -> 12
    else                -> 8
}
private const val BODY_POINTS = 30

// The "full" character: face hole is an ellipse, expressed as fractions of the
// rasterised character bitmap (derived from full.svg's 794×1123 viewBox).
private const val BODY_ASSET = "full"
private const val BODY_ELLIPSE_CX = 0.500f
private const val BODY_ELLIPSE_CY = 0.207f
private const val BODY_ELLIPSE_RX = 0.167f
private const val BODY_ELLIPSE_RY = 0.151f

private data class StickerAsset(
    val name: String, val slot: Slot, val points: Int, val image: ImageBitmap,
) {
    val aspect: Float get() = image.height.toFloat() / image.width.toFloat()
}

// One sticker to draw this frame, in MIRRORED upright-image coordinates.
private data class Placement(
    val image: ImageBitmap,
    val cx: Float, val cy: Float, val w: Float, val h: Float, val roll: Float,
)

// ── Colour / lighting "Look" filters ─────────────────────────────────────────
// makeMatrix() returns a fresh ColorMatrix (or null for no grade); overlay is a
// flat scrim drawn on top; vignette darkens the edges. swatch tints the chip.
private class LookFilter(
    val name: String,
    val swatch: Color,
    val points: Int,
    val makeMatrix: () -> ColorMatrix?,
    val overlay: Color? = null,
    val vignette: Boolean = false,
)

private fun contrastMatrix(c: Float): ColorMatrix {
    val t = (1f - c) / 2f * 255f
    return ColorMatrix(floatArrayOf(
        c, 0f, 0f, 0f, t,
        0f, c, 0f, 0f, t,
        0f, 0f, c, 0f, t,
        0f, 0f, 0f, 1f, 0f,
    ))
}

private val LOOKS: List<LookFilter> = listOf(
    LookFilter("None", Color(0xFF3A3A3A), 0, { null }),
    LookFilter("Warm", Color(0xFFE8A24C), 5, {
        ColorMatrix(floatArrayOf(
            1.12f, 0f, 0f, 0f, 12f,
            0f, 1.00f, 0f, 0f, 4f,
            0f, 0f, 0.85f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }),
    LookFilter("Cool", Color(0xFF4C8FE8), 5, {
        ColorMatrix(floatArrayOf(
            0.88f, 0f, 0f, 0f, 0f,
            0f, 1.00f, 0f, 0f, 0f,
            0f, 0f, 1.15f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }),
    LookFilter("Mono", Color(0xFF8A8A8A), 8, { ColorMatrix().apply { setSaturation(0f) } }),
    LookFilter("Sepia", Color(0xFF9A7B4F), 8, {
        ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }),
    LookFilter("Vivid", Color(0xFFE83C8F), 12, { ColorMatrix().apply { setSaturation(1.6f) } }),
    LookFilter("Noir", Color(0xFF101010), 15, {
        ColorMatrix().apply { setSaturation(0f); postConcat(contrastMatrix(1.4f)) }
    }, overlay = Color(0x14000000), vignette = true),
    LookFilter("Dream", Color(0xFFF6A8C8), 12, {
        ColorMatrix(floatArrayOf(
            1.0f, 0f, 0f, 0f, 16f,
            0f, 1.0f, 0f, 0f, 12f,
            0f, 0f, 1.0f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }, overlay = Color(0x1FFF6FA8)),
    LookFilter("Sunset", Color(0xFFFF7A3D), 15, {
        ColorMatrix(floatArrayOf(
            1.15f, 0f, 0f, 0f, 10f,
            0f, 0.95f, 0f, 0f, 0f,
            0f, 0f, 0.80f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }, overlay = Color(0x26FF7A3D), vignette = true),
)

@Composable
fun Building7VanityRoom(modifier: Modifier = Modifier, onComplete: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // COMPATIBLE = TextureView, so the colour-grade RenderEffect is actually
    // visible on the live feed (RenderEffect does nothing on a SurfaceView).
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    RequestGamePermissionsOnEntry(permissions = listOf(Manifest.permission.CAMERA)) { results ->
        results[Manifest.permission.CAMERA]?.let { hasCameraPermission = it }
    }

    // Load + rasterise SVG filters from assets/filters/ once, keyed by filename.
    val loaded = remember {
        val stickers = mutableListOf<StickerAsset>()
        var body: ImageBitmap? = null
        var background: ImageBitmap? = null
        try {
            val files = context.assets.list("filters") ?: emptyArray()
            for (f in files) {
                val stem = f.substringBeforeLast('.').lowercase()
                if (stem == "background") {
                    background = try {
                        context.assets.open("filters/$f").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                    } catch (e: Exception) { null }
                    continue
                }
                if (!f.endsWith(".svg", ignoreCase = true)) continue
                if (stem == BODY_ASSET) {
                    body = renderSvgFromAssets(context, "filters/$f", 1024)
                    continue
                }
                val slot = slotForName(stem) ?: continue
                val targetW = if (slot == Slot.FACE) 640 else 512
                val bmp = renderSvgFromAssets(context, "filters/$f", targetW) ?: continue
                stickers.add(StickerAsset(f, slot, pointsForSticker(stem), bmp))
            }
        } catch (e: Exception) {
            Log.e("Building7", "Failed to load filter assets", e)
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

    // Latest detection + the frame that produced it (kept for capture/body draw).
    var face by remember { mutableStateOf<Face?>(null) }
    var srcW by remember { mutableIntStateOf(0) }
    var srcH by remember { mutableIntStateOf(0) }
    val latestFrame = remember { mutableStateOf<Bitmap?>(null) }
    var latestRotation by remember { mutableIntStateOf(0) }
    // Upright + mirrored copy of the latest frame, only maintained in body mode.
    var bodyFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var savedFlash by remember { mutableStateOf(false) }

    // Shutter feedback: a white screen flash + the camera.mp3 SFX on each shot.
    val flashAlpha = remember { Animatable(0f) }
    var shutterTick by remember { mutableIntStateOf(0) }
    val soundPool = remember {
        SoundPool.Builder().setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    val shutterSound = remember { soundPool.load(context, R.raw.camera, 1) }
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
            listOf(R.raw.vo010, R.raw.vo011), cctv = false
        )
    }
    // vo012 — after the first "look" the player saves (their first capture).
    LaunchedEffect(captureCount) {
        if (captureCount == 1) {
            com.fictioncutshort.justacalculator.logic.VoiceoverManager.play(R.raw.vo012, cctv = false)
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

    val detector: FaceDetector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()
        )
    }

    // Bind the front camera + analysis once permission is granted.
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    val rot = proxy.imageInfo.rotationDegrees
                    val raw = try { proxy.toBitmap() } catch (e: Exception) { null }
                    if (raw == null) { proxy.close(); return@setAnalyzer }
                    val input = InputImage.fromBitmap(raw, rot)
                    // Upright dimensions ML Kit reports coordinates in.
                    val uw = if (rot == 90 || rot == 270) raw.height else raw.width
                    val uh = if (rot == 90 || rot == 270) raw.width else raw.height
                    detector.process(input)
                        .addOnSuccessListener { faces ->
                            face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                            srcW = uw; srcH = uh
                            latestFrame.value?.let { if (it != raw) it.recycle() }
                            latestFrame.value = raw
                            latestRotation = rot
                            // In body mode, keep an upright+mirrored copy to pour
                            // into the ellipse; otherwise drop any stale one.
                            if (bodyMode.value) {
                                bodyFrame?.asAndroidBitmap()?.recycle()
                                bodyFrame = makeUprightMirrored(raw, rot).asImageBitmap()
                            } else if (bodyFrame != null) {
                                bodyFrame?.asAndroidBitmap()?.recycle()
                                bodyFrame = null
                            }
                        }
                        .addOnFailureListener { Log.e("Building7", "detect failed", it) }
                        .addOnCompleteListener { proxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                Log.e("Building7", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Apply the colour grade to the live preview itself (API 31+); the saved
    // photo gets the same matrix at capture time. Body mode grades the ellipse.
    LaunchedEffect(lookIndex, bodyMode.value, hasCameraPermission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val m = if (bodyMode.value) null else look.makeMatrix()
            previewView.setRenderEffect(
                m?.let { RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(it)) }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            detector.close()
            latestFrame.value?.recycle()
            latestFrame.value = null
            bodyFrame?.asAndroidBitmap()?.recycle()
            bodyFrame = null
            soundPool.release()
        }
    }

    LaunchedEffect(savedFlash) { if (savedFlash) { delay(1200); savedFlash = false } }
    LaunchedEffect(lockedFlash) { if (lockedFlash) { delay(1800); lockedFlash = false } }
    LaunchedEffect(dupeFlash) { if (dupeFlash) { delay(1800); dupeFlash = false } }

    // Active anchored stickers for this frame (skipped entirely in body mode).
    val placements: List<Placement> = remember(face, srcW, srcH, selected.toMap(), bodyMode.value) {
        if (bodyMode.value) return@remember emptyList()
        val f = face ?: return@remember emptyList()
        if (srcW == 0 || srcH == 0) return@remember emptyList()
        buildPlacements(f, srcW, srcH, selected, stickersBySlot)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        if (hasCameraPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            if (bodyMode.value && bodyImage != null) {
                // BODY — character art with the live camera poured into its face.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawBodyComposite(this, bodyImage, backgroundImage, bodyFrame, face, srcW, srcH, look)
                }
            } else {
                // Live sticker overlay — map mirrored-image placements to view
                // space (FILL_CENTER), then the Look scrim/vignette on top.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (srcW == 0 || srcH == 0) return@Canvas
                    val sc = max(size.width / srcW, size.height / srcH)
                    val dx = (size.width - srcW * sc) / 2f
                    val dy = (size.height - srcH * sc) / 2f
                    for (p in placements) {
                        val cx = p.cx * sc + dx
                        val cy = p.cy * sc + dy
                        val w = p.w * sc
                        val h = p.h * sc
                        rotate(degrees = p.roll, pivot = Offset(cx, cy)) {
                            drawImage(
                                image = p.image,
                                dstOffset = IntOffset((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt()),
                                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                                filterQuality = FilterQuality.High,
                            )
                        }
                    }
                    drawLookScrim(look)
                }
            }

            if (face == null) {
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
                            val frame = latestFrame.value
                            val ok = if (bodyMode.value && bodyImage != null) {
                                if (frame != null && srcW > 0)
                                    captureBodyAndSave(context, frame, latestRotation, face, bodyImage, backgroundImage, look)
                                else false
                            } else {
                                if (frame != null && srcW > 0)
                                    captureAndSave(context, frame, latestRotation, placements, look)
                                else false
                            }
                            savedFlash = ok
                            if (ok) {
                                capturedOutfits.add(sig)
                                shutterTick++
                                soundPool.play(shutterSound, 1f, 1f, 1, 0, 1f)
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
private fun buildPlacements(
    f: Face, srcW: Int, srcH: Int,
    selected: Map<Slot, String>,
    stickersBySlot: Map<Slot, List<StickerAsset>>,
): List<Placement> {
    fun m(p: PointF?) = p?.let { Offset(srcW - it.x, it.y) }      // mirror X
    val box = f.boundingBox
    val boxL = srcW - box.right.toFloat(); val boxR = srcW - box.left.toFloat()
    val boxCx = (boxL + boxR) / 2f
    val faceW = box.width().toFloat(); val faceH = box.height().toFloat()

    val le = m(f.getLandmark(FaceLandmark.LEFT_EYE)?.position)
    val re = m(f.getLandmark(FaceLandmark.RIGHT_EYE)?.position)

    // In-plane roll from the eye line (mirrored space); fall back to head Euler Z.
    var roll = if (le != null && re != null)
        Math.toDegrees(atan2((re.y - le.y).toDouble(), (re.x - le.x).toDouble())).toFloat()
    else -f.headEulerAngleZ
    // The left↔right-eye vector can point "backwards" (≈180°) once mirrored,
    // which would render every overlay upside-down — fold roll into [-90,90].
    if (roll > 90f) roll -= 180f else if (roll < -90f) roll += 180f

    val out = mutableListOf<Placement>()
    fun assetFor(slot: Slot): StickerAsset? =
        selected[slot]?.let { name -> stickersBySlot[slot]?.firstOrNull { it.name == name } }

    // EYES — span eye-to-eye, centred on the eye midpoint.
    assetFor(Slot.EYES)?.let { a ->
        val (cx, cy, w) = if (le != null && re != null) {
            val eyeDist = hypot((re.x - le.x).toDouble(), (re.y - le.y).toDouble()).toFloat()
            Triple((le.x + re.x) / 2f, (le.y + re.y) / 2f, eyeDist * 2.4f)
        } else {
            Triple(boxCx, box.top + faceH * 0.38f, faceW * 1.05f)
        }
        out.add(Placement(a.image, cx, cy, w, w * a.aspect, roll))
    }

    // HEAD — rest the item ON the head by anchoring its BOTTOM edge to the
    // hairline. (Offsetting by image height floated tall hats way too high.)
    assetFor(Slot.HEAD)?.let { a ->
        val w = faceW * 1.15f
        val h = w * a.aspect
        val bottomY = box.top + faceH * 0.12f
        out.add(Placement(a.image, boxCx, bottomY - h / 2f, w, h, roll))
    }

    // FACE — cover the whole face, sized to the detected face height.
    assetFor(Slot.FACE)?.let { a ->
        val h = faceH * 1.18f
        val w = h / a.aspect
        out.add(Placement(a.image, boxCx, box.exactCenterY(), w, h, roll))
    }

    return out
}

// ── BODY composite: character art with the live camera poured into the ellipse ─
// Shared geometry for live (DrawScope) and capture (android Canvas).
private fun ellipseFit(
    canvasW: Float, canvasH: Float, bodyImage: ImageBitmap,
): FloatArray {
    val bw = bodyImage.width.toFloat(); val bh = bodyImage.height.toFloat()
    val fit = min(canvasW / bw, canvasH / bh)
    val dw = bw * fit; val dh = bh * fit
    val ox = (canvasW - dw) / 2f; val oy = (canvasH - dh) / 2f
    val ex = ox + BODY_ELLIPSE_CX * dw; val ey = oy + BODY_ELLIPSE_CY * dh
    val erx = BODY_ELLIPSE_RX * dw; val ery = BODY_ELLIPSE_RY * dh
    return floatArrayOf(ox, oy, dw, dh, ex, ey, erx, ery)
}

private fun drawCover(nc: android.graphics.Canvas, img: ImageBitmap, w: Float, h: Float) {
    val bw = img.width.toFloat(); val bh = img.height.toFloat()
    val s = max(w / bw, h / bh)
    val dw = bw * s; val dh = bh * s
    val ox = (w - dw) / 2f; val oy = (h - dh) / 2f
    nc.drawBitmap(
        img.asAndroidBitmap(), null, RectF(ox, oy, ox + dw, oy + dh),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
}

private fun drawBodyComposite(
    scope: DrawScope, bodyImage: ImageBitmap, background: ImageBitmap?, frame: ImageBitmap?,
    face: Face?, srcW: Int, srcH: Int, look: LookFilter,
) {
    val w = scope.size.width; val h = scope.size.height
    val g = ellipseFit(w, h, bodyImage)
    val (ox, oy, dw, dh) = g
    val ex = g[4]; val ey = g[5]; val erx = g[6]; val ery = g[7]

    scope.drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        // Backdrop behind the character — the supplied background.jpg, or a flat
        // fill. Either way it hides the raw preview leaking around the figure.
        if (background != null) drawCover(nc, background, w, h)
        else nc.drawColor(android.graphics.Color.rgb(16, 16, 24))

        if (frame != null && srcW > 0 && srcH > 0) {
            val fb = frame.asAndroidBitmap()
            nc.save()
            val path = android.graphics.Path().apply {
                addOval(RectF(ex - erx, ey - ery, ex + erx, ey + ery), android.graphics.Path.Direction.CW)
            }
            nc.clipPath(path)
            nc.drawColor(android.graphics.Color.BLACK)
            // Fit the detected face (in mirrored upright coords) into the ellipse.
            val faceCx: Float; val faceCy: Float; val faceH: Float
            if (face != null) {
                val b = face.boundingBox
                faceCx = srcW - b.exactCenterX(); faceCy = b.exactCenterY(); faceH = b.height().toFloat()
            } else {
                faceCx = srcW / 2f; faceCy = srcH * 0.45f; faceH = srcH * 0.6f
            }
            val s = (2f * ery * 0.95f) / faceH.coerceAtLeast(1f)
            val mtx = Matrix().apply {
                postScale(s, s)
                postTranslate(ex - faceCx * s, ey - faceCy * s)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            look.makeMatrix()?.let { paint.colorFilter = ColorMatrixColorFilter(it) }
            nc.drawBitmap(fb, mtx, paint)
            nc.restore()
        }

        // Character art on top — its transparent ellipse keeps the face visible.
        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        nc.drawBitmap(
            bodyImage.asAndroidBitmap(),
            null,
            RectF(ox, oy, ox + dw, oy + dh),
            artPaint,
        )
    }
}

// Flat scrim + vignette for the Look grade (live overlay).
private fun DrawScope.drawLookScrim(look: LookFilter) {
    look.overlay?.let { drawRect(it) }
    if (look.vignette) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                center = center, radius = size.minDimension * 0.75f
            )
        )
    }
}

private fun drawLookScrimNative(c: android.graphics.Canvas, w: Int, h: Int, look: LookFilter) {
    look.overlay?.let { c.drawColor(it.toArgb()) }
    if (look.vignette) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w / 2f, h / 2f, max(w, h) * 0.75f,
                intArrayOf(0x00000000, 0x8C000000.toInt()), floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }
}

// ── SVG → Bitmap (AndroidSVG, bundled via coil-svg) ──────────────────────────
private fun renderSvgFromAssets(
    context: android.content.Context, path: String, targetW: Int,
): ImageBitmap? = try {
    val svg = context.assets.open(path).use { SVG.getFromInputStream(it) }
    val vb = svg.documentViewBox
    val aspect = if (vb != null && vb.width() > 0f) vb.height() / vb.width() else 1f
    val w = targetW
    val h = (targetW * aspect).roundToInt().coerceAtLeast(1)
    svg.setDocumentWidth(w.toFloat())
    svg.setDocumentHeight(h.toFloat())
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    svg.renderToCanvas(android.graphics.Canvas(bmp))
    bmp.asImageBitmap()
} catch (e: Exception) {
    Log.e("Building7", "SVG render failed for $path", e)
    null
}

private fun makeUprightMirrored(raw: Bitmap, rotation: Int): Bitmap {
    val m = Matrix().apply { postRotate(rotation.toFloat()); postScale(-1f, 1f) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
}

// ── Capture: composite the live frame + overlays and save to the gallery ─────
private fun captureAndSave(
    context: android.content.Context, raw: Bitmap, rotation: Int,
    placements: List<Placement>, look: LookFilter,
): Boolean {
    return try {
        // Rotate raw buffer upright, then mirror (front camera) to match preview.
        val rot = Matrix().apply { postRotate(rotation.toFloat()) }
        val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, rot, true)
        val mir = Matrix().apply { preScale(-1f, 1f) }
        val mirrored = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, mir, true)
        val out = Bitmap.createBitmap(mirrored.width, mirrored.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)

        // Base photo with the Look colour grade baked in.
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        look.makeMatrix()?.let { basePaint.colorFilter = ColorMatrixColorFilter(it) }
        canvas.drawBitmap(mirrored, 0f, 0f, basePaint)
        if (upright != mirrored) upright.recycle()
        mirrored.recycle()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        for (p in placements) {
            val ab = p.image.asAndroidBitmap()
            val mtx = Matrix().apply {
                postScale(p.w / ab.width, p.h / ab.height)
                postTranslate(p.cx - p.w / 2f, p.cy - p.h / 2f)
                postRotate(p.roll, p.cx, p.cy)
            }
            canvas.drawBitmap(ab, mtx, paint)
        }
        drawLookScrimNative(canvas, out.width, out.height, look)

        val ok = saveBitmap(context, out)
        out.recycle()
        ok
    } catch (e: Exception) {
        Log.e("Building7", "capture/save failed", e)
        false
    }
}

// Capture for BODY mode — render the character composite at art resolution.
private fun captureBodyAndSave(
    context: android.content.Context, raw: Bitmap, rotation: Int,
    face: Face?, bodyImage: ImageBitmap, background: ImageBitmap?, look: LookFilter,
): Boolean {
    return try {
        val frame = makeUprightMirrored(raw, rotation)
        val srcW = frame.width; val srcH = frame.height
        val outW = bodyImage.width; val outH = bodyImage.height
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val nc = android.graphics.Canvas(out)
        if (background != null) drawCover(nc, background, outW.toFloat(), outH.toFloat())
        else nc.drawColor(android.graphics.Color.rgb(16, 16, 24))

        val g = ellipseFit(outW.toFloat(), outH.toFloat(), bodyImage)
        val (ox, oy, dw, dh) = g
        val ex = g[4]; val ey = g[5]; val erx = g[6]; val ery = g[7]

        nc.save()
        val path = android.graphics.Path().apply {
            addOval(RectF(ex - erx, ey - ery, ex + erx, ey + ery), android.graphics.Path.Direction.CW)
        }
        nc.clipPath(path)
        nc.drawColor(android.graphics.Color.BLACK)
        val faceCx: Float; val faceCy: Float; val faceH: Float
        if (face != null) {
            val b = face.boundingBox
            faceCx = srcW - b.exactCenterX(); faceCy = b.exactCenterY(); faceH = b.height().toFloat()
        } else {
            faceCx = srcW / 2f; faceCy = srcH * 0.45f; faceH = srcH * 0.6f
        }
        val s = (2f * ery * 0.95f) / faceH.coerceAtLeast(1f)
        val mtx = Matrix().apply {
            postScale(s, s)
            postTranslate(ex - faceCx * s, ey - faceCy * s)
        }
        val camPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        look.makeMatrix()?.let { camPaint.colorFilter = ColorMatrixColorFilter(it) }
        nc.drawBitmap(frame, mtx, camPaint)
        nc.restore()
        frame.recycle()

        nc.drawBitmap(
            bodyImage.asAndroidBitmap(), null, RectF(ox, oy, ox + dw, oy + dh),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )

        val ok = saveBitmap(context, out)
        out.recycle()
        ok
    } catch (e: Exception) {
        Log.e("Building7", "body capture/save failed", e)
        false
    }
}

private fun saveBitmap(context: android.content.Context, out: Bitmap): Boolean {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "vanity_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/JustACalculator")
        }
    }
    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
    ) ?: return false
    context.contentResolver.openOutputStream(uri)?.use {
        out.compress(Bitmap.CompressFormat.JPEG, 92, it)
    }
    // Durable private copy for Building 3 (see saveToPrivateStore).
    saveToPrivateStore(context, out)
    return true
}

// ── Durable private store (shared with Building 3) ───────────────────────────
private const val VANITY_DIR = "vanity_captures"
private const val VANITY_KEEP = 8

private fun saveToPrivateStore(context: android.content.Context, bmp: Bitmap) {
    try {
        val dir = java.io.File(context.filesDir, VANITY_DIR).apply { mkdirs() }
        java.io.File(dir, "cap_${System.currentTimeMillis()}.jpg").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(VANITY_KEEP)
            ?.forEach { it.delete() }
    } catch (e: Exception) {
        Log.e("Building7", "private store save failed", e)
    }
}

/**
 * Most-recent vanity captures (newest first) for reuse in Building 3. Reads the
 * private store written by the shutter button. Returns paths so the caller can
 * decode lazily; empty if the user never captured anything.
 */
fun loadVanityCapturePaths(context: android.content.Context, max: Int = 2): List<String> {
    val dir = java.io.File(context.filesDir, VANITY_DIR)
    if (!dir.exists()) return emptyList()
    return dir.listFiles { f -> f.extension.equals("jpg", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        ?.take(max)
        ?.map { it.absolutePath }
        ?: emptyList()
}
