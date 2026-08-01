package com.fictioncutshort.justacalculator.platform

import android.content.Context
import android.content.SharedPreferences

actual typealias AppContext = Context

actual fun AppContext.openPrefs(name: String): Prefs =
    AndroidPrefs(getSharedPreferences(name, Context.MODE_PRIVATE))

/**
 * Straight delegation to the platform SharedPreferences — no key mangling, no
 * format change, so existing installs keep their save state.
 */
private class AndroidPrefs(private val delegate: SharedPreferences) : Prefs {
    override fun getString(key: String, defValue: String?): String? =
        delegate.getString(key, defValue)

    override fun getStringSet(key: String, defValue: Set<String>?): Set<String>? =
        delegate.getStringSet(key, defValue)

    override fun getInt(key: String, defValue: Int): Int = delegate.getInt(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = delegate.getLong(key, defValue)
    override fun getFloat(key: String, defValue: Float): Float = delegate.getFloat(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        delegate.getBoolean(key, defValue)

    override fun contains(key: String): Boolean = delegate.contains(key)
    override fun getAll(): Map<String, Any?> = delegate.all
    override fun edit(): Prefs.Editor = AndroidEditor(delegate.edit())
}

private class AndroidEditor(private val delegate: SharedPreferences.Editor) : Prefs.Editor {
    override fun putString(key: String, value: String?) = apply { delegate.putString(key, value) }
    override fun putStringSet(key: String, value: Set<String>?) =
        apply { delegate.putStringSet(key, value) }

    override fun putInt(key: String, value: Int) = apply { delegate.putInt(key, value) }
    override fun putLong(key: String, value: Long) = apply { delegate.putLong(key, value) }
    override fun putFloat(key: String, value: Float) = apply { delegate.putFloat(key, value) }
    override fun putBoolean(key: String, value: Boolean) = apply { delegate.putBoolean(key, value) }
    override fun remove(key: String) = apply { delegate.remove(key) }
    override fun clear() = apply { delegate.clear() }
    override fun commit(): Boolean = delegate.commit()
    override fun apply() = delegate.apply()
}

actual fun AppContext.applicationScope(): AppContext = applicationContext
