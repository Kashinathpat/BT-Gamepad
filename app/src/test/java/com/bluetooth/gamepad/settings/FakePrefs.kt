package com.bluetooth.gamepad.settings

import android.content.SharedPreferences

class FakePrefs(initial: Map<String, Any> = emptyMap()) : SharedPreferences {
    val data = LinkedHashMap<String, Any>(initial)
    var applyCount = 0
        private set

    override fun getAll(): MutableMap<String, *> = HashMap(data)
    override fun getString(key: String?, defValue: String?): String? = data[key] as String? ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as MutableSet<String>? ?: defValues
    }
    override fun getInt(key: String?, defValue: Int): Int = data[key] as Int? ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = data[key] as Long? ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = data[key] as Float? ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as Boolean? ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?) = put(key, null)
        override fun clear(): SharedPreferences.Editor { clear = true; return this }

        override fun commit(): Boolean { flush(); return true }
        override fun apply() { flush() }

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        private fun flush() {
            applyCount++
            if (clear) data.clear()
            pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
        }
    }
}
