package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * The orientation the app should currently allow. `null` means "no restriction".
 *
 * iOS asks the root view controller for supported orientations rather than
 * letting a view demand one, so this is a flag the Swift side reads from
 * `application(_:supportedInterfaceOrientationsFor:)`.
 *
 * **Not wired up yet**: iosApp does not read this, so the city will still
 * rotate on iOS. Doing it properly needs the Swift AppDelegate to consult this
 * and call `setNeedsUpdateOfSupportedInterfaceOrientations()`.
 */
object IosOrientationLock {
    /** True while a screen wants orientation frozen. */
    var locked: Boolean = false
        private set

    internal fun acquire() { locked = true }
    internal fun release() { locked = false }
}

@Composable
actual fun LockOrientationWhileVisible() {
    DisposableEffect(Unit) {
        IosOrientationLock.acquire()
        onDispose { IosOrientationLock.release() }
    }
}
