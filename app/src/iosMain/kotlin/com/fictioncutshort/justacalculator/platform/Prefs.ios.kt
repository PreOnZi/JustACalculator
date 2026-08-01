package com.fictioncutshort.justacalculator.platform

import platform.Foundation.NSUserDefaults

/**
 * iOS has no Context to thread around, so [AppContext] is a marker singleton.
 * Call sites keep passing it purely so the shared code reads identically on both
 * platforms.
 */
actual abstract class AppContext

/** The single instance the iOS app threads through the shared code. */
object IosAppContext : AppContext()

actual fun AppContext.openPrefs(name: String): Prefs = UserDefaultsPrefs(name)

/**
 * Each Android preference file ("calc_city", "building6", …) maps to its own
 * NSUserDefaults suite, which keeps the same namespacing and lets a suite be
 * wiped wholesale the way `Editor.clear()` expects.
 */
private class UserDefaultsPrefs(private val suiteName: String) : Prefs {
    private val defaults: NSUserDefaults =
        NSUserDefaults(suiteName = suiteName) ?: NSUserDefaults.standardUserDefaults

    override fun getString(key: String, defValue: String?): String? =
        if (defaults.objectForKey(key) == null) defValue else defaults.stringForKey(key)

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValue: Set<String>?): Set<String>? {
        val stored = defaults.arrayForKey(key) ?: return defValue
        return (stored as List<*>).filterIsInstance<String>().toSet()
    }

    // NSUserDefaults returns 0/false for absent keys rather than a supplied
    // default, so every getter checks for presence first — otherwise a default
    // of `true` would silently read back as `false` on a fresh install.
    override fun getInt(key: String, defValue: Int): Int =
        if (defaults.objectForKey(key) == null) defValue else defaults.integerForKey(key).toInt()

    override fun getLong(key: String, defValue: Long): Long =
        if (defaults.objectForKey(key) == null) defValue else defaults.integerForKey(key)

    override fun getFloat(key: String, defValue: Float): Float =
        if (defaults.objectForKey(key) == null) defValue else defaults.floatForKey(key)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) defValue else defaults.boolForKey(key)

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun getAll(): Map<String, Any?> =
        defaults.dictionaryRepresentation().entries.associate { (k, v) -> k.toString() to v }

    override fun edit(): Prefs.Editor = UserDefaultsEditor(defaults)
}

/**
 * Android's Editor batches writes until commit()/apply(); this mirrors that by
 * queuing mutations and only touching NSUserDefaults when the batch is flushed.
 * `clear()` is recorded as a flag so it applies before the queued puts, exactly
 * as SharedPreferences does.
 */
private class UserDefaultsEditor(private val defaults: NSUserDefaults) : Prefs.Editor {
    private val pending = mutableListOf<() -> Unit>()
    private var clearRequested = false

    override fun putString(key: String, value: String?) = apply {
        pending += { if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key) }
    }

    override fun putStringSet(key: String, value: Set<String>?) = apply {
        pending += { if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value.toList(), key) }
    }

    override fun putInt(key: String, value: Int) = apply {
        pending += { defaults.setInteger(value.toLong(), key) }
    }

    override fun putLong(key: String, value: Long) = apply {
        pending += { defaults.setInteger(value, key) }
    }

    override fun putFloat(key: String, value: Float) = apply {
        pending += { defaults.setFloat(value, key) }
    }

    override fun putBoolean(key: String, value: Boolean) = apply {
        pending += { defaults.setBool(value, key) }
    }

    override fun remove(key: String) = apply {
        pending += { defaults.removeObjectForKey(key) }
    }

    override fun clear() = apply { clearRequested = true }

    override fun commit(): Boolean {
        flush()
        return defaults.synchronize()
    }

    override fun apply() {
        flush()
    }

    private fun flush() {
        if (clearRequested) {
            defaults.dictionaryRepresentation().keys.forEach {
                defaults.removeObjectForKey(it.toString())
            }
            clearRequested = false
        }
        pending.forEach { it() }
        pending.clear()
    }
}

// iOS has a single process-wide context already, so there is nothing to widen to.
actual fun AppContext.applicationScope(): AppContext = this
