package com.fictioncutshort.justacalculator.ui.components

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont

/**
 * BrowserOverlay.kt
 *
 * Fake browser UI used in two sequences:
 * 1. Google search animation (step 61-63) - fails with "No internet"
 * 2. Wikipedia page (step 80+) - shows real Wikipedia or fake fallback
 *
 * The browser appearing is part of the story - the calculator is trying
 * to access the internet to learn about its history.
 */

/**
 * Fake browser overlay that can show Google search or Wikipedia.
 *
 * @param searchText Text being "typed" in the URL bar
 * @param showError True to show "No internet connection" error
 * @param showWikipedia True to show Wikipedia page
 * @param modifier Modifier for sizing
 */
@Composable
fun BrowserOverlay(
    searchText: String,
    showError: Boolean,
    showWikipedia: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // URL/Search bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color(0xFFF0F0F0), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = searchText.ifEmpty { "Search..." },
                    fontSize = if (showWikipedia) 12.sp else 16.sp,
                    fontFamily = if (searchText.isNotEmpty()) CalculatorDisplayFont else null,
                    color = if (searchText.isEmpty()) Color.Gray else Color.Black,
                    maxLines = 1
                )
            }

            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    showWikipedia -> WikipediaContent()
                    showError -> ErrorContent()
                    else -> GoogleLogo()
                }
            }
        }
    }
}

/**
 * Google logo placeholder shown during search animation.
 */
@Composable
private fun GoogleLogo() {
    Text(
        text = "Google",
        fontSize = 48.sp,
        fontFamily = CalculatorDisplayFont,
        color = Color(0xFF4285F4)
    )
}

/**
 * "No internet connection" error message.
 */
@Composable
private fun ErrorContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "⚠",
            fontSize = 48.sp,
            color = Color.Gray
        )
        Text(
            text = "No internet connection",
            fontSize = 20.sp,
            fontFamily = CalculatorDisplayFont,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * Wikipedia page content - tries real WebView first, falls back to fake.
 */
@Composable
private fun WikipediaContent() {
    var webViewFailed by remember { mutableStateOf(false) }

    if (!webViewFailed) {
        // Try real Wikipedia WebView
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
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                webViewFailed = true
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            @Suppress("DEPRECATION")
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            webViewFailed = true
                        }
                    }

                    loadUrl("https://en.wikipedia.org/wiki/Calculator")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Fallback: Fake Wikipedia content
        FakeWikipediaContent()
    }
}

/**
 * Fake Wikipedia page that looks like the real thing.
 * Used when WebView fails to load (no internet or WebView issues).
 */
@Composable
fun FakeWikipediaContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        // Donation banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1589D1))
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = "☆ Please donate to keep Wikipedia free ☆",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Hi reader. This is the 2nd time we've interrupted your reading, but 98% of our readers don't give.",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Wikipedia header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F9FA))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Wikipedia logo placeholder
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.LightGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "W",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
            }

            Column(modifier = Modifier.padding(start = 6.dp)) {
                Text(
                    text = "WIKIPEDIA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.Black
                )
                Text(
                    text = "The Free Encyclopedia",
                    fontSize = 8.sp,
                    color = Color.Gray
                )
            }

            // Spacer and menu icon
            Box(modifier = Modifier.weight(1f))
            Text("☰", fontSize = 20.sp, color = Color.Gray)
        }

        // Main content
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = "Calculator",
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black,
                modifier = Modifier.padding(top = 12.dp)
            )

            Text(
                text = "From Wikipedia, the free encyclopedia",
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF54595D),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "A calculator is a machine that performs arithmetic operations. Modern electronic calculators range from cheap, credit card-sized models to sturdy desktop models with built-in printers.",
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 20.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // History section
            Text(
                text = "History",
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black,
                modifier = Modifier.padding(top = 20.dp, bottom = 2.dp)
            )

            // Section divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFA2A9B1))
            )

            Text(
                text = "The 17th century saw the development of mechanical calculators. In 1623, Wilhelm Schickard designed a calculating machine. In 1642, Blaise Pascal invented the Pascaline.",
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 20.sp,
                modifier = Modifier.padding(vertical = 10.dp)
            )

            Text(
                text = "Charles Xavier Thomas de Colmar designed the Arithmometer around 1820, which became the first commercially successful mechanical calculator.",
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 50.dp)
            )
        }
    }
}