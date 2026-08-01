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

/**
 * TODO: replace APP_STORE_ID once the app has a listing. Until then this is a
 * no-op rather than a broken link.
 */
private const val APP_STORE_ID = ""

actual fun openAppStoreListing(context: AppContext) {
    if (APP_STORE_ID.isEmpty()) {
        logWarn("ExternalLinks", "No App Store id set yet; skipping store link")
        return
    }
    openExternalUrl(context, "itms-apps://apple.com/app/id$APP_STORE_ID")
}
