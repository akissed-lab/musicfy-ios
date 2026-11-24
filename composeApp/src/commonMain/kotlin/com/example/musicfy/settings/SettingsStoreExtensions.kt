// SettingsStoreExtensions.kt
//
// Graceful fallbacks for operations the current typed SettingsStore contract
// does not expose. Nothing here changes SettingsStore or its iOS actual.
// TODO: when SettingsStore grows contains/removeAll/getDouble/getStringSet/
// getBytes and a reactive data flow, replace these extensions with delegates
// to the real implementations.

package com.example.musicfy.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Whether a value is currently stored under [key]. */
fun SettingsStore.contains(key: String): Boolean {
    // TODO: DataStore has a real contains(); snapshot() only knows declared keys.
    return key in SettingsKeys.AllKeys && snapshot().containsKey(key)
}

/** Removes every listed key. */
fun SettingsStore.removeAll(keys: Iterable<String>) {
    // TODO: replace with a batched DataStore edit when available.
    keys.forEach { remove(it) }
}

/** The snapshot entry for a key, or null. */
fun SettingsStore.entry(key: String): SettingEntry? = snapshot()[key]

fun SettingsStore.getDouble(key: String, default: Double): Double {
    // TODO: doublePreferencesKey support; NSUserDefaults stores doubles as NSNumber.
    val stored = entry(key)
    if (stored != null && stored.t in setOf(
            SettingsSnapshot.TYPE_DOUBLE,
            SettingsSnapshot.TYPE_FLOAT,
            SettingsSnapshot.TYPE_INT,
            SettingsSnapshot.TYPE_LONG,
        )
    ) {
        return SettingsSnapshot.asDouble(stored, default)
    }
    return getFloat(key, default.toFloat()).toDouble()
}

fun SettingsStore.setStringSet(key: String, value: Set<String>) {
    // TODO: stringSetPreferencesKey support; today we store a comma-separated string.
    setString(key, value.joinToString(","))
}

fun SettingsStore.getStringSet(key: String, default: Set<String> = emptySet()): Set<String> {
    // TODO: stringSetPreferencesKey support.
    val stored = entry(key)
    if (stored != null && stored.t == SettingsSnapshot.TYPE_STRING_SET) {
        return SettingsSnapshot.asStringSet(stored, default)
    }
    val text = getString(key, "")
    return if (text.isEmpty()) default else text.split(',').filter { it.isNotEmpty() }.toSet()
}

fun SettingsStore.setBytes(key: String, value: ByteArray) {
    // TODO: byteArrayPreferencesKey support; today we store Base64 text.
    setString(key, SettingsSnapshot.encodeBase64(value))
}

fun SettingsStore.getBytes(key: String, default: ByteArray = ByteArray(0)): ByteArray {
    // TODO: byteArrayPreferencesKey support.
    val stored = entry(key)
    if (stored != null && stored.t == SettingsSnapshot.TYPE_BYTES) {
        return SettingsSnapshot.asBytes(stored, default)
    }
    val text = getString(key, "")
    return if (text.isEmpty()) default else SettingsSnapshot.decodeBase64(text) ?: default
}

/**
 * A one-shot snapshot stream. NO cross-screen reactivity yet: downstream
 * composables must re-read the store when a screen resumes.
 * TODO: expose a real DataStore Flow through SettingsStore.
 */
fun SettingsStore.snapshotFlow(): Flow<Map<String, SettingEntry>> = flowOf(snapshot())
