package com.fictioncutshort.justacalculator.platform

/**
 * Bundled game assets — models, textures, SVGs, sprites, audio.
 *
 * Both platforms read from one source tree (`app/src/commonMain/assets`), which
 * Android packages via `sourceSets["main"].assets` and iOS carries as a folder
 * reference in the app bundle. Paths are relative and identical on both:
 * `"models/stickman.obj"`, `"filters/hat.svg"`, …
 *
 * Deliberately **synchronous**, unlike Compose Resources' suspend API: the GL
 * renderers and audio players load assets from a render/callback thread where
 * there is no coroutine scope to suspend in.
 */
expect object Assets {

    /** Whole file as bytes. Throws if [path] is missing — use [exists] to probe. */
    fun readBytes(path: String): ByteArray

    /** Whole file as UTF-8 text (OBJ/MTL/CSV data). */
    fun readText(path: String): String

    /** Immediate children of [dir] as bare file names, or empty if absent. */
    fun list(dir: String): List<String>

    /** Whether [path] resolves to a bundled asset. */
    fun exists(path: String): Boolean

    /**
     * A URI an image loader can consume — `file:///android_asset/…` on Android,
     * a bundle file URL on iOS.
     */
    fun uri(path: String): String
}

/** Lines of a text asset, the shape the OBJ/MTL parsers want. */
fun Assets.readLines(path: String): List<String> = readText(path).lineSequence().toList()
