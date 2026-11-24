package com.example.musicfy.settings

/**
 * iOS replacement for Jetpack DataStore Preferences ("settings").
 *
 * Upstream had 321 keys (322 declarations including a duplicate) and a
 * PreferencesCache layer. All key names are preserved in [SettingsKeys] so
 * exported settings stay compatible with the `mochy-transfer` JSON format.
 */
expect class SettingsStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getFloat(key: String, default: Float): Float
    fun getString(key: String, default: String): String

    fun setBoolean(key: String, value: Boolean)
    fun setInt(key: String, value: Int)
    fun setLong(key: String, value: Long)
    fun setFloat(key: String, value: Float)
    fun setString(key: String, value: String)

    fun remove(key: String)
    fun clear()

    /** Typed snapshot used by TransferExporter (`settings/settings.json`). */
    fun snapshot(): Map<String, SettingEntry>
}

/** `t` codes match upstream TransferArchive: b/i/l/f/d/s/ss/bytes. */
data class SettingEntry(val t: String, val v: Any?)
