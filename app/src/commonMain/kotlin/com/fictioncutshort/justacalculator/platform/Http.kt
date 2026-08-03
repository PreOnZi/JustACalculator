package com.fictioncutshort.justacalculator.platform

/** Status and body of a completed request. */
class HttpTextResponse(val status: Int, val body: String)

/**
 * Blocking GET returning the body as text, or null if the request never
 * completed. Callers already run this off the main thread.
 *
 * The status comes back rather than being folded into null because the Overpass
 * mirror loop distinguishes "this mirror rate-limited me, try the next" from
 * "the network is gone".
 */
expect fun httpGetText(
    url: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    userAgent: String,
): HttpTextResponse?

private const val UNRESERVED =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"

/**
 * Percent-encodes [value] for use in a query string.
 *
 * Written out rather than seamed: `URLEncoder` and
 * `stringByAddingPercentEncoding` disagree about spaces (`+` versus `%20`) and
 * about which punctuation is reserved, and an Overpass query is mostly
 * punctuation.
 */
fun urlEncode(value: String): String {
    val out = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val ch = byte.toInt().toChar()
        if (ch in UNRESERVED) {
            out.append(ch)
        } else {
            out.append('%').append(
                ((byte.toInt() and 0xFF).toString(16).uppercase()).padStart(2, '0')
            )
        }
    }
    return out.toString()
}
