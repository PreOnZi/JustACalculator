package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.ui.graphics.ImageBitmap

// ─────────────────────────────────────────────────────────────────────────────
// Building 7 — "Vanity" room.
//
// A front-camera mirror with a Snapchat-style filter engine. There are TWO
// carousels:
//   • LOOK   — colour & lighting filters (grade the whole feed).
//   • STICKER — vector overlays loaded from app/src/main/assets/filters/*.svg,
//               anchored to detected face landmarks by FILENAME:
//                 crown / hat            → HEAD (above the forehead)
//                 glasses / glasses01    → EYES (over the eyes)
//                 face / face1 / mask    → FACE (cover the whole face)
//                 full                   → BODY — a character whose face is a
//                                          transparent ellipse; the live camera
//                                          is piped into that ellipse.
//
// SVGs are rasterised once through the `renderSvgAsset` seam so the rest of the
// engine works in plain bitmaps.
//
// NOTE: capture is explicit and disclosed — the only image saved is the one the
// user deliberately shoots with the shutter.
// ─────────────────────────────────────────────────────────────────────────────

// Face slot a sticker is anchored to.
internal enum class Slot(val label: String) { HEAD("Head"), EYES("Eyes"), FACE("Face") }

internal fun slotForName(stem: String): Slot? = when {
    stem == "crown" || stem == "hat"               -> Slot.HEAD
    stem.startsWith("glasses")                     -> Slot.EYES
    stem == "face" || stem == "face1" || stem == "mask" -> Slot.FACE
    else                                           -> null
}

// Static "style points" awarded for wearing each item (see styleScore).
internal fun pointsForSticker(stem: String): Int = when {
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
internal const val BODY_POINTS = 30

// The "full" character: face hole is an ellipse, expressed as fractions of the
// rasterised character bitmap (derived from full.svg's 794×1123 viewBox).
internal const val BODY_ASSET = "full"
internal const val BODY_ELLIPSE_CX = 0.500f
internal const val BODY_ELLIPSE_CY = 0.207f
internal const val BODY_ELLIPSE_RX = 0.167f
internal const val BODY_ELLIPSE_RY = 0.151f

internal data class StickerAsset(
    val name: String, val slot: Slot, val points: Int, val image: ImageBitmap,
) {
    val aspect: Float get() = image.height.toFloat() / image.width.toFloat()
}
