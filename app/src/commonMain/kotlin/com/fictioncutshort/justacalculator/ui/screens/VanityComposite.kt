package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.fictioncutshort.justacalculator.platform.DetectedFace
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Everything the vanity room draws.
 *
 * The Android original had two copies of each routine — one against `DrawScope`
 * for the live view and one against `android.graphics.Canvas` for the saved
 * photo. Compose's `CanvasDrawScope` can render into an off-screen
 * `ImageBitmap`, so there is one copy here and the photo is by construction
 * what the player was looking at.
 */

/** One sticker to draw this frame, in the frame's own (upright, mirrored) space. */
internal data class Placement(
    val image: ImageBitmap,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val roll: Float,
)

/** Draws [image] scaled to cover a [w] x [h] area, cropping the overflow. */
internal fun DrawScope.drawCover(image: ImageBitmap, w: Float, h: Float) {
    val scale = max(w / image.width, h / image.height)
    val dw = image.width * scale
    val dh = image.height * scale
    drawImage(
        image = image,
        dstOffset = IntOffset(((w - dw) / 2f).roundToInt(), ((h - dh) / 2f).roundToInt()),
        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/** Draws [image] scaled to fit inside [w] x [h], letterboxed and centred. */
private fun DrawScope.drawFit(image: ImageBitmap, w: Float, h: Float) {
    val scale = min(w / image.width, h / image.height)
    val dw = image.width * scale
    val dh = image.height * scale
    drawImage(
        image = image,
        dstOffset = IntOffset(((w - dw) / 2f).roundToInt(), ((h - dh) / 2f).roundToInt()),
        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/** Where the character art and its face cut-out land in a [canvasW] x [canvasH] area. */
internal class EllipseFit(
    val artLeft: Float, val artTop: Float, val artWidth: Float, val artHeight: Float,
    val cx: Float, val cy: Float, val rx: Float, val ry: Float,
)

internal fun ellipseFit(
    canvasW: Float,
    canvasH: Float,
    bodyImage: ImageBitmap,
): EllipseFit {
    val fit = min(canvasW / bodyImage.width, canvasH / bodyImage.height)
    val dw = bodyImage.width * fit
    val dh = bodyImage.height * fit
    val ox = (canvasW - dw) / 2f
    val oy = (canvasH - dh) / 2f
    return EllipseFit(
        artLeft = ox, artTop = oy, artWidth = dw, artHeight = dh,
        cx = ox + BODY_ELLIPSE_CX * dw,
        cy = oy + BODY_ELLIPSE_CY * dh,
        rx = BODY_ELLIPSE_RX * dw,
        ry = BODY_ELLIPSE_RY * dh,
    )
}

/**
 * Character art with the live camera poured into its face cut-out.
 *
 * [face] and [frame] are both in the frame's own space, which is already
 * mirrored — so no flip happens here. When no face is detected the crop falls
 * back to a plausible head position rather than showing nothing, so the player
 * can line themselves up.
 */
internal fun DrawScope.drawBodyComposite(
    bodyImage: ImageBitmap,
    background: ImageBitmap?,
    frame: ImageBitmap?,
    face: DetectedFace?,
    look: LookFilter,
) {
    val w = size.width
    val h = size.height
    val g = ellipseFit(w, h, bodyImage)

    // Backdrop behind the character — the supplied background, or a flat fill.
    // Either way it hides the raw preview leaking around the figure.
    if (background != null) drawCover(background, w, h) else drawRect(Color(0xFF101018))

    if (frame != null && frame.width > 0 && frame.height > 0) {
        val oval = Path().apply {
            addOval(Rect(g.cx - g.rx, g.cy - g.ry, g.cx + g.rx, g.cy + g.ry))
        }
        clipPath(oval) {
            drawRect(Color.Black)

            val faceCx: Float
            val faceCy: Float
            val faceH: Float
            if (face != null) {
                faceCx = face.centerX; faceCy = face.centerY; faceH = face.height
            } else {
                faceCx = frame.width / 2f
                faceCy = frame.height * 0.45f
                faceH = frame.height * 0.6f
            }
            // Scale so the detected head fills most of the cut-out's height.
            val scale = (2f * g.ry * 0.95f) / faceH.coerceAtLeast(1f)

            withTransform({
                translate(g.cx - faceCx * scale, g.cy - faceCy * scale)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawImage(
                    image = frame,
                    colorFilter = look.makeMatrix()?.let { ColorFilter.colorMatrix(it) },
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }

    // Character art on top — its transparent ellipse keeps the face visible.
    drawFit(bodyImage, w, h)
}

/** Anchored stickers, mapped from frame space into this canvas. */
internal fun DrawScope.drawPlacements(
    placements: List<Placement>,
    srcW: Int,
    srcH: Int,
    fillCanvas: Boolean,
) {
    if (srcW <= 0 || srcH <= 0) return
    // FILL_CENTER when drawn over the preview, 1:1 when rendering the photo.
    val scale = if (fillCanvas) max(size.width / srcW, size.height / srcH) else 1f
    val dx = (size.width - srcW * scale) / 2f
    val dy = (size.height - srcH * scale) / 2f

    for (p in placements) {
        val cx = p.cx * scale + dx
        val cy = p.cy * scale + dy
        val w = p.w * scale
        val h = p.h * scale
        rotate(degrees = p.roll, pivot = Offset(cx, cy)) {
            drawImage(
                image = p.image,
                dstOffset = IntOffset((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt()),
                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

/** Flat scrim + vignette for the Look grade. */
internal fun DrawScope.drawLookScrim(look: LookFilter) {
    look.overlay?.let { drawRect(it) }
    if (look.vignette) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                center = center,
                radius = size.minDimension * 0.75f,
            )
        )
    }
}

/**
 * Anchors the selected stickers to [f].
 *
 * Coordinates are the frame's own, which the camera seam already delivers
 * upright and mirrored — so unlike the Android original nothing is flipped
 * here.
 */
internal fun buildPlacements(
    f: DetectedFace,
    selected: Map<Slot, String>,
    stickersBySlot: Map<Slot, List<StickerAsset>>,
): List<Placement> {
    val boxCx = f.centerX
    val faceW = f.width
    val faceH = f.height
    val le = f.leftEye
    val re = f.rightEye

    // In-plane roll from the eye line; fall back to the reported head tilt.
    var roll = if (le != null && re != null) {
        (kotlin.math.atan2((re.y - le.y).toDouble(), (re.x - le.x).toDouble()) * 180.0 / kotlin.math.PI).toFloat()
    } else {
        -f.rollDegrees
    }
    // The eye vector can point "backwards" (about 180 degrees), which would
    // render every overlay upside-down — fold roll into [-90, 90].
    if (roll > 90f) roll -= 180f else if (roll < -90f) roll += 180f

    val out = mutableListOf<Placement>()
    fun assetFor(slot: Slot): StickerAsset? =
        selected[slot]?.let { name -> stickersBySlot[slot]?.firstOrNull { it.name == name } }

    // EYES — span eye-to-eye, centred on the eye midpoint.
    assetFor(Slot.EYES)?.let { a ->
        val (cx, cy, w) = if (le != null && re != null) {
            val eyeDist = kotlin.math.hypot((re.x - le.x).toDouble(), (re.y - le.y).toDouble()).toFloat()
            Triple((le.x + re.x) / 2f, (le.y + re.y) / 2f, eyeDist * 2.4f)
        } else {
            Triple(boxCx, f.top + faceH * 0.38f, faceW * 1.05f)
        }
        out.add(Placement(a.image, cx, cy, w, w * a.aspect, roll))
    }

    // HEAD — rest the item ON the head by anchoring its BOTTOM edge to the
    // hairline. (Offsetting by image height floated tall hats way too high.)
    assetFor(Slot.HEAD)?.let { a ->
        val w = faceW * 1.15f
        val h = w * a.aspect
        val bottomY = f.top + faceH * 0.12f
        out.add(Placement(a.image, boxCx, bottomY - h / 2f, w, h, roll))
    }

    // FACE — cover the whole face, sized to the detected face height.
    assetFor(Slot.FACE)?.let { a ->
        val h = faceH * 1.18f
        val w = h / a.aspect
        out.add(Placement(a.image, boxCx, f.centerY, w, h, roll))
    }

    return out
}

/** Renders what the player sees into an off-screen image, for saving. */
internal fun renderStickerCapture(
    frame: ImageBitmap,
    placements: List<Placement>,
    look: LookFilter,
): ImageBitmap = renderOffscreen(frame.width, frame.height) {
    // Base photo with the Look colour grade baked in.
    drawImage(
        image = frame,
        colorFilter = look.makeMatrix()?.let { ColorFilter.colorMatrix(it) },
        filterQuality = FilterQuality.High,
    )
    drawPlacements(placements, frame.width, frame.height, fillCanvas = false)
    drawLookScrim(look)
}

/** Renders the body composite at the character art's own resolution. */
internal fun renderBodyCapture(
    frame: ImageBitmap,
    face: DetectedFace?,
    bodyImage: ImageBitmap,
    background: ImageBitmap?,
    look: LookFilter,
): ImageBitmap = renderOffscreen(bodyImage.width, bodyImage.height) {
    drawBodyComposite(bodyImage, background, frame, face, look)
}

private fun renderOffscreen(
    width: Int,
    height: Int,
    block: DrawScope.() -> Unit,
): ImageBitmap {
    val bitmap = ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        density = androidx.compose.ui.unit.Density(1f),
        layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
        canvas = androidx.compose.ui.graphics.Canvas(bitmap),
        size = Size(width.toFloat(), height.toFloat()),
        block = block,
    )
    return bitmap
}
