package com.fictioncutshort.justacalculator.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait

/**
 * NSURLSession is callback-based, so a semaphore turns it back into the
 * blocking call the seam promises. Safe because callers are already off the
 * main thread — the completion handler runs on the session's own queue, not the
 * one being blocked.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun httpGetText(
    url: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    userAgent: String,
): HttpTextResponse? {
    val nsUrl = NSURL.URLWithString(url) ?: return null

    val config = NSURLSessionConfiguration.defaultSessionConfiguration
    config.timeoutIntervalForRequest = connectTimeoutMs / 1000.0
    config.timeoutIntervalForResource = readTimeoutMs / 1000.0
    val session = NSURLSession.sessionWithConfiguration(config)

    val request = NSMutableURLRequest.requestWithURL(nsUrl)
    request.setValue(userAgent, forHTTPHeaderField = "User-Agent")

    var result: HttpTextResponse? = null
    val done = dispatch_semaphore_create(0)

    session.dataTaskWithRequest(request) { data: NSData?, response, _ ->
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status != null) {
            val body = data
                ?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding) }
                ?.toString()
                .orEmpty()
            result = HttpTextResponse(status, body)
        }
        dispatch_semaphore_signal(done)
    }.resume()

    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER)
    session.finishTasksAndInvalidate()
    return result
}
