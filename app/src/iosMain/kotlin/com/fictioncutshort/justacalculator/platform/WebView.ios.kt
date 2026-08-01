package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformWebView(
    url: String,
    modifier: Modifier,
    onLoadError: () -> Unit,
) {
    // rememberUpdatedState so the delegate, which outlives a recomposition,
    // always calls the current callback.
    val currentOnError by rememberUpdatedState(onLoadError)
    val delegate = remember { NavigationDelegate { currentOnError() } }

    UIKitView(
        factory = {
            WKWebView(frame = CGRectZero.readValue(), configuration = WKWebViewConfiguration()).apply {
                navigationDelegate = delegate
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest(it)) }
            }
        },
        modifier = modifier,
    )
}

/**
 * WKWebView reports main-frame failures through two callbacks: one before the
 * response arrives (provisional) and one after. Both mean "the page did not
 * load", which is what the browser overlay's fallback keys off.
 */
private class NavigationDelegate(
    private val onError: () -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError,
    ) = onError()

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) = onError()
}
