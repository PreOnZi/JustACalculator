package com.fictioncutshort.justacalculator.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Rasterises the SVG at [assetPath] to [targetWidth] pixels wide, keeping its
 * aspect ratio, or returns null if it cannot be read or parsed.
 *
 * The vanity room's stickers are vectors so they stay sharp when anchored to a
 * face at any distance; they are rasterised once at load rather than re-drawn
 * per frame.
 */
expect fun renderSvgAsset(assetPath: String, targetWidth: Int): ImageBitmap?
