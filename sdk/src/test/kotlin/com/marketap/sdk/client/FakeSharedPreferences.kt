package com.marketap.sdk.client

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk

internal class FakeSharedPreferences : SharedPreferences {
    val values = mutableMapOf<String, Any>()

    fun storage() = SharedPreferenceInternalStorage(mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns this@FakeSharedPreferences
    })

    override fun getAll(): MutableMap<String, *> = synchronized(values) { values.toMutableMap() }
    override fun getString(key: String, defValue: String?): String? = synchronized(values) { values[key] as? String ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(values) { (values[key] as? Set<String>)?.toMutableSet() ?: defValues }

    override fun getInt(key: String, defValue: Int): Int = defValue
    override fun getLong(key: String, defValue: Long): Long = defValue
    override fun getFloat(key: String, defValue: Float): Float = defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    override fun contains(key: String): Boolean = synchronized(values) { values.containsKey(key) }
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    private inner class Editor : SharedPreferences.Editor {
        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            synchronized(values) { if (value == null) values.remove(key) else values[key] = value }
            return this
        }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values?.toSet())
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = put(key, null)
        override fun clear(): SharedPreferences.Editor {
            synchronized(values) { values.clear() }
            return this
        }

        override fun commit(): Boolean = true
        override fun apply() {}
    }
}
