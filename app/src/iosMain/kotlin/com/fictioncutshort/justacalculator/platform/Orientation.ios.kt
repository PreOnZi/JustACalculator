package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Whether a screen currently wants orientation frozen.
 *
 * iOS asks the app delegate for supported orientations rather than letting a
 * view demand one, so this is a flag `AppDelegate` reads from
 * `application(_:supportedInterfaceOrientationsFor:)`.
 *
 * Deliberately only a boolean. Which orientation to freeze *at* is decided on
 * the Swift side, because knowing it means asking the window scene, and the
 * answer has to be sampled at the instant of locking — matching Android, which
 * pins the current orientation rather than requesting a named one.
 */
object IosOrientationLock {
    /** True while a screen wants orientation frozen. */
    var locked: Boolean = false
        private set

    /**
     * Installed by the Swift app delegate at launch.
     *
     * iOS caches the supported-orientation answer and will not re-ask on its
     * own, so flipping [locked] is not enough — the delegate has to be told to
     * invalidate it. Without this the lock takes effect only at the next
     * unrelated rotation event, which looks exactly like it not working.
     */
    var onChange: (() -> Unit)? = null

    internal fun acquire() {
        locked = true
        onChange?.invoke()
    }

    internal fun release() {
        locked = false
        onChange?.invoke()
    }
}

@Composable
actual fun LockOrientationWhileVisible() {
    DisposableEffect(Unit) {
        IosOrientationLock.acquire()
        onDispose { IosOrientationLock.release() }
    }
}
