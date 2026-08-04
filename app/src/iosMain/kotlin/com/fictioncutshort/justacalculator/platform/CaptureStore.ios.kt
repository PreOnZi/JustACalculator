package com.fictioncutshort.justacalculator.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
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
