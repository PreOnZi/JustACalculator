package com.fictioncutshort.justacalculator.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.NSDateFormatter
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.NSTextAlignmentCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}

/**
 * iOS names photo-library assets itself, so [fileName] is unused here — the
 * caption baked into the image is what identifies a mosaic on this platform.
 *
 * Encoding goes through Skia because that is what already backs [ImageBitmap]
 * on this target; going via CoreGraphics would mean getting the byte order and
 * premultiplication right by hand for no gain.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalForeignApi::class)
actual fun saveImageToGallery(fileName: String, image: ImageBitmap): Boolean = try {
    val png = Image.makeFromBitmap(image.asSkiaBitmap())
        .encodeToData(EncodedImageFormat.PNG)
        ?.bytes
    val uiImage = png?.toNSData()?.let { UIImage(data = it) }
    if (uiImage == null) {
        false
    } else {
        // Fire-and-forget: the completion selector is optional, and a refused
        // library permission surfaces as the system's own alert.
        UIImageWriteToSavedPhotosAlbum(uiImage, null, null, null)
        true
    }
} catch (_: Exception) {
    false
}

/**
 * iOS has no Toast, so this is the smallest thing that behaves like one: a
 * rounded label floated over the key window that fades out on its own.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun showToast(message: String) {
    dispatch_async(dispatch_get_main_queue()) {
        val window = UIApplication.sharedApplication.windows
            .filterIsInstance<UIWindow>()
            .firstOrNull { it.isKeyWindow() }
            ?: return@dispatch_async

        val bounds = window.bounds
        val label = UILabel()
        label.text = message
        label.textColor = UIColor.whiteColor
        label.backgroundColor = UIColor.blackColor.colorWithAlphaComponent(0.75)
        label.font = UIFont.systemFontOfSize(15.0)
        label.textAlignment = NSTextAlignmentCenter
        label.layer.cornerRadius = 14.0
        label.clipsToBounds = true
        label.alpha = 0.0

        val width = 260.0
        val height = 40.0
        label.setFrame(
            CGRectMake(
                x = (bounds.useContents { size.width } - width) / 2.0,
                y = bounds.useContents { size.height } - 140.0,
                width = width,
                height = height,
            )
        )
        window.addSubview(label)

        UIView.animateWithDuration(0.2, animations = { label.alpha = 1.0 }) { _ ->
            UIView.animateWithDuration(
                duration = 0.3,
                delay = 1.8,
                options = 0u,
                animations = { label.alpha = 0.0 },
                completion = { _ -> label.removeFromSuperview() },
            )
        }
    }
}

private val dateTimeFormatter by lazy {
    NSDateFormatter().apply { dateFormat = "yyyy-MM-dd HH:mm" }
}

actual fun formatDateTime(millis: Long): String =
    dateTimeFormatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(millis / 1000.0))
