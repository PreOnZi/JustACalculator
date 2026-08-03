package com.fictioncutshort.justacalculator.platform

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

actual fun httpGetText(
    url: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    userAgent: String,
): HttpTextResponse? = try {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        requestMethod = "GET"
        setRequestProperty("User-Agent", userAgent)
    }
    val status = conn.responseCode
    if (status !in 200..299) {
        // Reading inputStream on a 4xx/5xx throws, so don't.
        conn.disconnect()
        HttpTextResponse(status, "")
    } else {
        val body = conn.inputStream.use { BufferedReader(InputStreamReader(it)).readText() }
        HttpTextResponse(status, body)
    }
} catch (_: Exception) {
    null
}
