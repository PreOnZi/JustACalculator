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
 * The App Store "Apple ID" for this app, assigned by App Store Connect when the
 * record was created. The empty-string guard below is kept deliberately: it is
 * what makes a build with no id yet degrade to a no-op instead of opening a
 * broken itms-apps:// link.
 */
private const val APP_STORE_ID = "6804248023"

actual fun openAppStoreListing(context: AppContext) {
    if (APP_STORE_ID.isEmpty()) {
        logWarn("ExternalLinks", "No App Store id set yet; skipping store link")
        return
    }
    openExternalUrl(context, "itms-apps://apple.com/app/id$APP_STORE_ID")
}

actual fun openMapsAt(context: AppContext, lat: Double, lon: Double, label: String) {
    // maps.apple.com opens Maps directly when it is installed and falls back to
    // the web otherwise, which `maps://` does not.
    openExternalUrl(
        context,
        "https://maps.apple.com/?ll=$lat,$lon&q=${urlEncode(label)}",
    )
}

/** Apple's own name for the store, as players see it on the device. */
actual val appStoreName: String = "App Store"
