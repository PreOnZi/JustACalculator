package com.fictioncutshort.justacalculator.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openExternalUrl(context: AppContext, url: String): Boolean {
    val nsUrl = NSURL.URLWithString(url) ?: return false
    val app = UIApplication.sharedApplication
    if (!app.canOpenURL(nsUrl)) {
        logWarn("ExternalLinks", "No handler for $url")
        return false
    }
    app.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    return true
}
