package com.fictioncutshort.justacalculator.platform

import android.content.Intent
import android.net.Uri

actual fun openExternalUrl(context: AppContext, url: String): Boolean = try {
    // ACTION_VIEW handles http/https and mailto alike; ACTION_SENDTO was only
    // ever needed to keep non-mail apps out of the chooser, which ACTION_VIEW
    // on a mailto: URI already does.
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
    true
} catch (e: Exception) {
    logWarn("ExternalLinks", "Could not open $url: ${e.message}")
    false
}

actual fun openAppStoreListing(context: AppContext) {
    val pkg = context.packageName
    // market:// opens the Play app directly; the https form is the fallback for
    // devices without it.
    if (!openExternalUrl(context, "market://details?id=$pkg")) {
        openExternalUrl(context, "https://play.google.com/store/apps/details?id=$pkg")
    }
}

actual fun openMapsAt(context: AppContext, lat: Double, lon: Double, label: String) {
    val encoded = urlEncode(label)
    val uri = android.net.Uri.parse("geo:$lat,$lon?q=$lat,$lon($encoded)")
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

/** Google's own shorthand for the store, as players see it on the device. */
actual val appStoreName: String = "Play Store"
