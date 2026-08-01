package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable

/**
 * App foreground/background transitions.
 *
 * The story leans on these: step 112 only fires its follow-up question if the
 * player *actually left the app* (ON_STOP), not merely pulled down the
 * notification shade — so the distinction between pausing and stopping is
 * load-bearing, not incidental.
 */
enum class AppLifecycleEvent {
    /** Became visible. Android ON_START; iOS willEnterForeground. */
    STARTED,

    /** Became interactive. Android ON_RESUME; iOS didBecomeActive. */
    RESUMED,

    /** Lost focus but may still be visible. Android ON_PAUSE; iOS willResignActive. */
    PAUSED,

    /** Genuinely backgrounded — the signal step 112 waits for. */
    STOPPED,
}

/** Invokes [onEvent] for the lifetime of the composition. */
@Composable
expect fun OnAppLifecycleEvent(onEvent: (AppLifecycleEvent) -> Unit)

/**
 * Returns a function that asks the user for [permission], reporting the outcome
 * to [onResult]. Safe to call when already granted — the platform short-circuits.
 */
@Composable
expect fun rememberPermissionRequest(
    permission: AppPermission,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit
