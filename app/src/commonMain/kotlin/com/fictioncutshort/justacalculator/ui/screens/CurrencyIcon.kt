package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.fictioncutshort.justacalculator.logic.Currency

/**
 * CurrencyIcon.kt
 *
 * Renders a currency's 3D .obj model to a still, transparent-background bitmap
 * for use as a flat HUD icon.
 *
 * The offscreen render and its caching live behind [rememberModelIcon]; this is
 * just the currency-shaped entry point. Returns null on platforms where the
 * offscreen renderer is not ported, and the HUD draws its counters without an
 * icon rather than failing.
 */
private const val ICON_PX = 128

@Composable
fun rememberCurrencyIcon(c: Currency): ImageBitmap? =
    rememberModelBitmap(objPath = c.obj, mtlPath = c.mtl, sizePx = ICON_PX)
