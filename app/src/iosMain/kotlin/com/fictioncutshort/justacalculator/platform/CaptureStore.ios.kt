package com.fictioncutshort.justacalculator.platform

import kotlinx.cinterop.ExperimentalForeignApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile

/**
 * The app's own copy of every photo the story takes.
 *
 * The photo library gets a copy too, but the app cannot read that one back
 * without full library access — a much larger permission than the add-only one
 * the capture needs. Keeping a copy here means the fake phone's photo grid and
 * the vanity room's selfie playback both work with no read permission at all.
 */
@OptIn(ExperimentalForeignApi::class)
internal object CaptureStore {

    private const val FOLDER = "captures"

    private val directory: String? by lazy {
        val docs = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true,
        ).firstOrNull() as? String ?: return@lazy null
        val path = "$docs/$FOLDER"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path, withIntermediateDirectories = true, attributes = null, error = null,
        )
        path
    }

    /** Writes [data] as [name] and returns its path, or null if it could not. */
    fun save(name: String, data: NSData): String? {
        val dir = directory ?: return null
        val path = "$dir/$name"
        return if (data.writeToFile(path, atomically = true)) path else null
    }

    /** Deletes all but the newest [keep] captures matching [suffix]. */
    fun prune(suffix: String, keep: Int) {
        val stale = paths(Int.MAX_VALUE, suffix).drop(keep)
        val manager = NSFileManager.defaultManager
        for (path in stale) manager.removeItemAtPath(path, null)
    }

    /** Stored capture paths, newest first, at most [max]. */
    fun paths(max: Int, suffix: String? = null): List<String> {
        val dir = directory ?: return emptyList()
        val manager = NSFileManager.defaultManager
        val names = manager.contentsOfDirectoryAtPath(dir, null)
            ?.filterIsInstance<String>()
            ?: return emptyList()

        return names
            .filter { suffix == null || it.contains(suffix) }
            // Names carry the capture time, so lexical order is time order and
            // no per-file attribute lookup is needed.
            .sortedDescending()
            .take(max)
            .map { "$dir/$it" }
    }
}

actual fun capturedImagePaths(max: Int): List<String> = CaptureStore.paths(max)

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}

/** Marks a capture as coming from the vanity room, so Building 3 can find it. */
internal const val VANITY_PREFIX = "vanity_"

actual fun saveCaptureLocally(name: String, image: ImageBitmap, keepNewest: Int): String? {
    val jpeg = Image.makeFromBitmap(image.asSkiaBitmap())
        .encodeToData(EncodedImageFormat.JPEG, 90)
        ?.bytes
        ?: return null
    val path = CaptureStore.save(name, jpeg.toNSData())
    CaptureStore.prune(VANITY_PREFIX, keepNewest)
    return path
}
