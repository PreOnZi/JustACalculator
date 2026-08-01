package com.fictioncutshort.justacalculator.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.posix.memcpy

/**
 * Reads from an `assets/` folder reference inside the app bundle. The Xcode
 * project points that reference at `app/src/commonMain/assets`, the same tree
 * the APK packages, so relative paths match Android exactly.
 */
@OptIn(ExperimentalForeignApi::class)
actual object Assets {

    private val root: String by lazy {
        // A folder reference keeps the directory structure, so assets land under
        // <bundle>/assets/… rather than being flattened into the bundle root.
        NSBundle.mainBundle.resourcePath?.let { "$it/assets" } ?: "assets"
    }

    private fun resolve(path: String): String = "$root/${path.trimStart('/')}"

    actual fun readBytes(path: String): ByteArray {
        val full = resolve(path)
        val data: NSData = NSData.dataWithContentsOfFile(full)
            ?: throw IllegalArgumentException("Asset not found: $path")
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        val out = ByteArray(length)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return out
    }

    actual fun readText(path: String): String =
        NSString.stringWithContentsOfFile(resolve(path), encoding = NSUTF8StringEncoding, error = null)
            ?: throw IllegalArgumentException("Asset not found: $path")

    actual fun list(dir: String): List<String> {
        val contents = NSFileManager.defaultManager.contentsOfDirectoryAtPath(resolve(dir), null)
            ?: return emptyList()
        return contents.mapNotNull { it as? String }
    }

    actual fun exists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(resolve(path))

    actual fun uri(path: String): String = "file://${resolve(path)}"
}
