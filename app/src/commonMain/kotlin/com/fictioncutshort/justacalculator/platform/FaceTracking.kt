package com.fictioncutshort.justacalculator.platform

import androidx.compose.ui.graphics.ImageBitmap

/** A point in the frame's pixel space. */
data class FacePoint(val x: Float, val y: Float)

/**
 * One detected face, in the pixel space of the frame it came from.
 *
 * Only what the vanity room actually anchors stickers to: the box, the two
 * eyes, and how far the head is tilted. Everything richer that ML Kit and
 * Vision each offer is deliberately left out — the two disagree about most of
 * it, and none of it is used.
 *
 * [leftEye] and [rightEye] follow ML Kit's convention: the **subject's** left
 * and right, so in a mirrored selfie the left eye appears on the right of the
 * frame. Vision numbers them from the image's point of view, so the iOS actual
 * swaps them; getting this backwards mirrors every eye-anchored sticker without
 * otherwise looking wrong.
 */
data class DetectedFace(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val leftEye: FacePoint?,
    val rightEye: FacePoint?,
    /** Roll about the view axis, degrees, positive counter-clockwise. */
    val rollDegrees: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = width * height
}

/**
 * A camera frame together with the faces found in it.
 *
 * [image] is already upright and, for the front camera, mirrored — so it
 * matches what the player sees in the preview, and face coordinates are in that
 * same space. Handing over a corrected frame is what lets the caller forget
 * about sensor rotation entirely.
 */
class FaceFrame(
    val faces: List<DetectedFace>,
    val image: ImageBitmap,
) {
    val width: Int get() = image.width
    val height: Int get() = image.height
}
