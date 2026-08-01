package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An embedded web page — Android's `WebView`, iOS's `WKWebView`.
 *
 * [onLoadError] fires when the *main frame* fails to load; the browser overlay
 * uses it to swap in its hand-written fake Wikipedia page, which is a story
 * beat rather than an error screen, so it must keep firing on both platforms.
 */
@Composable
expect fun PlatformWebView(
    url: String,
    modifier: Modifier = Modifier,
    onLoadError: () -> Unit = {},
)
