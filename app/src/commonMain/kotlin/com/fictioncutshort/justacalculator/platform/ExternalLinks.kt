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

/**
 * Opens the platform's maps app at ([lat], [lon]), with [label] as the pin's
 * name.
 *
 * Separate from [openExternalUrl] because the two platforms take different URL
 * schemes for this — Android's `geo:` means nothing to iOS — and the story
 * hands the player a real place to walk to, so silently doing nothing is worse
 * here than for a link.
 */
expect fun openMapsAt(context: AppContext, lat: Double, lon: Double, label: String)
