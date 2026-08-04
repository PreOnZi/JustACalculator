package com.fictioncutshort.justacalculator.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.useContents
import platform.CoreVideo.CVPixelBufferRef
import platform.Vision.VNDetectFaceLandmarksRequest
import platform.Vision.VNFaceLandmarkRegion2D
import platform.Vision.VNFaceObservation
import platform.Vision.VNImageRequestHandler
import kotlin.math.PI

/**
 * Vision counterpart to ML Kit face detection.
 *
 * Three conventions differ, and each one would mis-place stickers without
 * otherwise looking broken:
 *
 * 1. **Vision normalises coordinates** to 0..1 of the image; ML Kit reports
 *    pixels. Everything here is scaled back to pixels.
 * 2. **Vision's origin is bottom-left**, ML Kit's is top-left, so the vertical
 *    axis is flipped.
 * 3. **Landmark points are relative to the face's own bounding box**, not the
 *    image, so they are mapped through the box before being flipped.
 *
 * Vision also names eyes from the image's point of view where ML Kit names them
 * from the subject's, so the two are swapped on the way out — see [DetectedFace].
 */
@OptIn(ExperimentalForeignApi::class)
internal fun detectFaces(
    pixelBuffer: CVPixelBufferRef,
    width: Int,
    height: Int,
): List<DetectedFace> {
    val request = VNDetectFaceLandmarksRequest(completionHandler = null)
    val handler = VNImageRequestHandler(cVPixelBuffer = pixelBuffer, options = emptyMap<Any?, Any?>())

    val ok = handler.performRequests(listOf(request), null)
    if (!ok) return emptyList()

    val observations = request.results?.filterIsInstance<VNFaceObservation>() ?: return emptyList()
    val w = width.toFloat()
    val h = height.toFloat()

    return observations.map { face ->
        // One read of the normalised box; every conversion below works from it.
        val n = face.boundingBox.useContents {
            floatArrayOf(
                origin.x.toFloat(), origin.y.toFloat(),
                size.width.toFloat(), size.height.toFloat(),
            )
        }
        val boxLeftN = n[0]; val boxBottomN = n[1]; val boxWN = n[2]; val boxHN = n[3]

        val box = run {
            // origin is the bottom-left corner in normalised space
            val left = boxLeftN * w
            val boxWidth = boxWN * w
            val boxHeight = boxHN * h
            // Flip: Vision's bottom edge is the top edge once y points down.
            val top = (1f - (boxBottomN + boxHN)) * h
            floatArrayOf(left, top, left + boxWidth, top + boxHeight)
        }

        /** Maps a landmark's box-relative point into flipped image pixels. */
        fun centreOf(region: VNFaceLandmarkRegion2D?): FacePoint? {
            val region = region ?: return null
            val count = region.pointCount.toInt()
            if (count <= 0) return null
            val points = region.normalizedPoints ?: return null

            var sx = 0.0
            var sy = 0.0
            for (i in 0 until count) {
                sx += points[i].x
                sy += points[i].y
            }
            val nx = boxLeftN + (sx / count).toFloat() * boxWN
            val ny = boxBottomN + (sy / count).toFloat() * boxHN
            return FacePoint(nx * w, (1f - ny) * h)
        }

        val landmarks = face.landmarks
        // Swapped on purpose: Vision's "left" is the viewer's left.
        val subjectLeftEye = centreOf(landmarks?.rightEye)
        val subjectRightEye = centreOf(landmarks?.leftEye)

        DetectedFace(
            left = box[0],
            top = box[1],
            right = box[2],
            bottom = box[3],
            leftEye = subjectLeftEye,
            rightEye = subjectRightEye,
            // Vision reports roll in radians, and positive is clockwise where
            // ML Kit's headEulerAngleZ is counter-clockwise.
            rollDegrees = -((face.roll?.doubleValue ?: 0.0) * 180.0 / PI).toFloat(),
        )
    }
}
