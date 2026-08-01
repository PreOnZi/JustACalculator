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
