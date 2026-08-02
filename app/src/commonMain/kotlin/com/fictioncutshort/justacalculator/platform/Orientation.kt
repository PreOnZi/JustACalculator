package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable

/**
 * Freezes device orientation for as long as this composable is in the
 * composition, restoring the previous setting on dispose.
 *
 * The city locks orientation while it is on screen: it locks the *current*
 * orientation rather than requesting a specific one, so the window manager has
 * nothing to reconfigure and does not relaunch the activity mid-scene.
 *
 * iOS has no per-view equivalent — orientation is driven by the view
 * controller's supportedInterfaceOrientations — so the iOS actual pins the
 * value the app delegate reads. See the actual for the caveat.
 */
@Composable
expect fun LockOrientationWhileVisible()
