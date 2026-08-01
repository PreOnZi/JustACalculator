package com.fictioncutshort.justacalculator.platform

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder

/**
 * Installs the app-wide Coil image loader with SVG decoding enabled — the phone
 * screen's icons under `phonescreen/` are all SVGs loaded from assets.
 *
 * Coil 3 replaces Coil 2's Android-only `ImageLoaderFactory` with this
 * singleton hook, which both entry points call at startup.
 */
fun installImageLoader() {
    SingletonImageLoader.setSafe { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
}
