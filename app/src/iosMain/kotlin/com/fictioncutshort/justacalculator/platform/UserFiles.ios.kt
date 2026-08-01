package com.fictioncutshort.justacalculator.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual fun writeUserVisibleFile(
    context: AppContext,
    fileName: String,
    content: String,
): Boolean {
    val documents = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true,
    ).firstOrNull() as? String ?: return false

    @Suppress("CAST_NEVER_SUCCEEDS")
    return (content as NSString).writeToFile(
        path = "$documents/$fileName",
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
}

@OptIn(ExperimentalForeignApi::class)
actual fun appPackageSizeBytes(context: AppContext): Long {
    val path = NSBundle.mainBundle.bundlePath
    val manager = NSFileManager.defaultManager
    // A bundle is a directory, so its own attributes report ~0; sum the contents.
    val enumerator = manager.enumeratorAtURL(
        NSURL.fileURLWithPath(path),
        includingPropertiesForKeys = null,
        options = 0u,
        errorHandler = null,
    ) ?: return 0L

    var total = 0L
    while (true) {
        val next = enumerator.nextObject() ?: break
        val url = next as? NSURL ?: continue
        val filePath = url.path ?: continue
        val attrs = manager.attributesOfItemAtPath(filePath, null) ?: continue
        val size = attrs["NSFileSize"] as? Number ?: continue
        total += size.toLong()
    }
    return total
}
