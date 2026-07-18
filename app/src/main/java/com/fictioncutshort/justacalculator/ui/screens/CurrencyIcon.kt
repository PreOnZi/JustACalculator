package com.fictioncutshort.justacalculator.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.fictioncutshort.justacalculator.logic.Currency
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CurrencyIcon.kt
 *
 * Renders a currency's 3D .obj model to a still, transparent-background bitmap
 * for use as a flat HUD icon. Thin wrapper over the general-purpose
 * [ModelBitmapRenderer] (which owns the offscreen EGL + shading), caching one
 * icon per currency (icons never change).
 */
object CurrencyIconRenderer {

    private const val ICON_PX = 128
    private val cache = ConcurrentHashMap<Currency, Bitmap>()

    /** Cached bitmap for a currency's icon; renders on first request. */
    fun get(context: Context, c: Currency): Bitmap? =
        cache[c] ?: ModelBitmapRenderer.get(context, c.obj, c.mtl, ICON_PX)
            ?.also { cache[c] = it }
}

/** Compose helper: renders (off the main thread) and caches a currency's icon. */
@Composable
fun rememberCurrencyIcon(c: Currency): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = null, c) {
        value = withContext(Dispatchers.Default) {
            CurrencyIconRenderer.get(context, c)?.asImageBitmap()
        }
    }
    return state.value
}
