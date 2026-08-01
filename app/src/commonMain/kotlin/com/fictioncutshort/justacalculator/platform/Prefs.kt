package com.fictioncutshort.justacalculator.platform

/**
 * Key-value persistence, deliberately shaped exactly like Android's
 * `SharedPreferences` (same method names, same defaulting, same fluent
 * `edit()…commit()` chain).
 *
 * That shape is not an accident: the game persists through ~750 call sites
 * written against SharedPreferences, and mirroring the interface lets every one
 * of them move to `commonMain` untouched. The Android actual is a thin delegate
 * over the real thing, so saved games from the shipped Play Store build keep
 * loading; iOS is backed by `NSUserDefaults`.
 */
interface Prefs {
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValue: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun getAll(): Map<String, Any?>
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, value: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor

        /** Writes synchronously. Returns true on success, as on Android. */
        fun commit(): Boolean

        /** Writes asynchronously; fire-and-forget. */
        fun apply()
    }
}

/**
 * The per-platform application handle that prefs (and later audio, haptics,
 * notifications…) hang off.
 *
 * On Android this is a typealias for `android.content.Context`, so all the
 * existing `context` plumbing keeps compiling. On iOS it is a singleton, since
 * UIKit has no equivalent object to thread through.
 */
// `abstract` so the Android actual can be a typealias to android.content.Context,
// which is itself abstract. Shared code never constructs one — it only passes it on.
expect abstract class AppContext

/** Opens the named preference file — was `getSharedPreferences(name, MODE_PRIVATE)`. */
expect fun AppContext.openPrefs(name: String): Prefs

/**
 * The process-lifetime context to hold on to, so long-lived singletons never
 * retain an Activity. Was `context.applicationContext`; a no-op on iOS.
 */
expect fun AppContext.applicationScope(): AppContext
