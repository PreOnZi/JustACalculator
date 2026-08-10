package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * The runtime permissions the story asks for. The phone-detour beat branches on
 * whether the player granted them, so this has to answer the same way on both
 * platforms even though the underlying APIs differ completely.
 */
enum class AppPermission {
    MICROPHONE,
    LOCATION,
    CONTACTS,
    CAMERA,
}

/**
 * Whether [permission] is currently granted. Never prompts — this is a query.
 *
 * On iOS "not yet asked" counts as not granted, matching Android's behaviour
 * before the first request.
 *
 * Not a value to cache for long. Both platforms offer a one-time grant ("Allow
 * Once" / "Only this time") that lapses when the app is backgrounded, so an
 * answer of `true` is only reliably true right now — see [rememberPermissionState].
 */
expect fun hasPermission(context: AppContext, permission: AppPermission): Boolean

/**
 * Raised while a system permission dialog is on screen.
 *
 * The story advances on timers. Without this it kept talking underneath the
 * dialog, so a player reading a permission prompt came back to a conversation
 * that had moved on without them.
 *
 * A count rather than a flag because more than one request can be outstanding,
 * and the deadline is a safety valve: if a platform ever fails to answer, the
 * story resumes rather than hanging forever on a prompt nobody can see.
 */
object PermissionGate {

    private const val MAX_WAIT_MS = 60_000L

    private var outstanding = 0
    private var deadline = 0L

    val isPending: Boolean
        get() = outstanding > 0 && nowMillis() < deadline

    fun begin() {
        outstanding++
        deadline = nowMillis() + MAX_WAIT_MS
    }

    fun end() {
        if (outstanding > 0) outstanding--
    }
}

/**
 * Asks for [permission], holding the story still until the player answers.
 *
 * Wraps the platform request so every call site gets the pause without having
 * to remember it. Both platforms always report a result — including when the
 * permission was already granted — so the gate cannot be left raised.
 */
@Composable
fun rememberPermissionRequest(
    permission: AppPermission,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val request = rememberPlatformPermissionRequest(permission) { granted ->
        PermissionGate.end()
        onResult(granted)
    }
    return {
        PermissionGate.begin()
        request()
    }
}

/**
 * Live grant state for [permission], re-read whenever the app comes back.
 *
 * A one-time grant dies when the app is backgrounded, so a value read once at
 * composition goes stale exactly when the player returns from doing something
 * else — which is precisely when the next beat needs the camera or the mic.
 * Re-checking on resume makes that lapse visible, and callers keyed on this
 * state ask again instead of silently failing.
 */
@Composable
fun rememberPermissionState(permission: AppPermission): MutableState<Boolean> {
    val context = AppInit.context
    val granted = remember(permission) { mutableStateOf(hasPermission(context, permission)) }
    OnAppLifecycleEvent { event ->
        if (event == AppLifecycleEvent.RESUMED) {
            granted.value = hasPermission(context, permission)
        }
    }
    return granted
}
