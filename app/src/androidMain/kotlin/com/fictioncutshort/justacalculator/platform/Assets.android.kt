package com.fictioncutshort.justacalculator.platform

import java.io.FileNotFoundException

/**
 * Reads straight from the APK's AssetManager, so the packaging and lookup
 * behaviour is exactly what the Android build has always done.
 */
actual object Assets {

    private val assetManager get() = AppInit.context.assets

    actual fun readBytes(path: String): ByteArray =
        assetManager.open(path).use { it.readBytes() }

    actual fun readText(path: String): String =
        assetManager.open(path).use { it.readBytes().decodeToString() }

    actual fun list(dir: String): List<String> =
        try {
            assetManager.list(dir)?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

    actual fun exists(path: String): Boolean =
        try {
            assetManager.open(path).close()
            true
        } catch (_: FileNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }

    actual fun uri(path: String): String = "file:///android_asset/$path"
}
