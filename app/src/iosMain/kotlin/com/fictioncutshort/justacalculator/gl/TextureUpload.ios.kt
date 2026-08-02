package com.fictioncutshort.justacalculator.gl

import com.fictioncutshort.justacalculator.platform.Assets
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIImage

/**
 * Decodes via UIImage, then redraws into a known RGBA8888 layout with
 * CoreGraphics.
 *
 * The redraw is not optional: a CGImage's native layout varies by source file
 * (BGRA, premultiplied, 16-bit), and GL needs tightly-packed RGBA. Drawing into
 * a context we allocated normalises all of that in one step.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun decodeImageAsset(path: String): DecodedImage? {
    if (!Assets.exists(path)) return null
    val image = UIImage.imageWithContentsOfFile(Assets.uri(path).removePrefix("file://"))
        ?: return null
    val cg = image.CGImage ?: return null

    val width = CGImageGetWidth(cg).toInt()
    val height = CGImageGetHeight(cg).toInt()
    if (width <= 0 || height <= 0) return null

    val rgba = ByteArray(width * height * 4)
    rgba.usePinned { pinned ->
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = (width * 4).toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        ) ?: return@usePinned
        CGContextDrawImage(
            context,
            CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
            cg,
        )
    }
    return DecodedImage(width, height, rgba)
}
