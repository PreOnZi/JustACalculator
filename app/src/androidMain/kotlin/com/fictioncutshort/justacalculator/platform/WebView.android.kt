package com.fictioncutshort.justacalculator.platform

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun PlatformWebView(
    url: String,
    modifier: Modifier,
    onLoadError: () -> Unit,
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                @Suppress("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true

                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        // Sub-resource failures are ignored; only a dead main
                        // frame should trigger the fallback page.
                        if (request?.isForMainFrame == true) onLoadError()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?,
                    ) {
                        @Suppress("DEPRECATION")
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        onLoadError()
                    }
                }

                loadUrl(url)
            }
        },
        modifier = modifier,
    )
}
