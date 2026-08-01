package com.fictioncutshort.justacalculator.platform

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
 */
expect fun hasPermission(context: AppContext, permission: AppPermission): Boolean
