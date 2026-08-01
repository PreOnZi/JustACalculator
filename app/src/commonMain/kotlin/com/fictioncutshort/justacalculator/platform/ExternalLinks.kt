package com.fictioncutshort.justacalculator.platform

/**
 * Hands a URL to whatever app owns it — the mail client for `mailto:`, the
 * browser for `https:`, the store for a listing.
 *
 * Returns false when nothing can handle it, which the story treats as a
 * non-event rather than an error: the beats that offer a link stay playable on
 * a device with no mail client configured.
 */
expect fun openExternalUrl(context: AppContext, url: String): Boolean

/**
 * Opens this app's store listing — Play on Android (falling back to the web if
 * the Play app is absent), the App Store on iOS.
 */
expect fun openAppStoreListing(context: AppContext)
