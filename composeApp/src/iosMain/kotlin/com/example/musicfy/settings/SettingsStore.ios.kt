package com.example.musicfy.settings

import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/** NSUserDefaults-backed equivalent of Android DataStore "settings". */
actual class SettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        defaults.objectForKey(key)?.let { (it as? NSNumber)?.boolValue } ?: default

    actual fun getInt(key: String, default: Int): Int =
        defaults.objectForKey(key)?.let { (it as? NSNumber)?.intValue } ?: default

    actual fun getLong(key: String, default: Long): Long =
        defaults.objectForKey(key)?.let { (it as? NSNumber)?.longLongValue } ?: default

    actual fun getFloat(key: String, default: Float): Float =
        defaults.objectForKey(key)?.let { (it as? NSNumber)?.floatValue } ?: default

    actual fun getString(key: String, default: String): String =
        defaults.stringForKey(key) ?: default

    actual fun setBoolean(key: String, value: Boolean) = defaults.setBool(value, key)
    actual fun setInt(key: String, value: Int) = defaults.setInteger(value.toLong(), key)
    actual fun setLong(key: String, value: Long) = defaults.setInteger(value, key)
    actual fun setFloat(key: String, value: Float) = defaults.setFloat(value, key)
    actual fun setString(key: String, value: String) = defaults.setObject(value, key)

    actual fun remove(key: String) = defaults.removeObjectForKey(key)

    actual fun clear() = SettingsKeys.AllKeys.forEach(::remove)

    /** Typed snapshot for TransferExporter (`settings/settings.json`). */
    actual fun snapshot(): Map<String, SettingEntry> = buildMap {
        SettingsKeys.AllKeys.forEach { key ->
            val value = defaults.objectForKey(key) ?: return@forEach
            val type = SettingsKeys.KeyTypes[key] ?: "s"
            val entry = when (type) {
                "b" -> SettingEntry("b", (value as? NSNumber)?.boolValue)
                "i" -> SettingEntry("i", (value as? NSNumber)?.intValue)
                "l" -> SettingEntry("l", (value as? NSNumber)?.longLongValue)
                "f" -> SettingEntry("f", (value as? NSNumber)?.floatValue)
                else -> SettingEntry("s", value as? String)
            }
            if (entry.v != null) put(key, entry)
        }
    }
}
