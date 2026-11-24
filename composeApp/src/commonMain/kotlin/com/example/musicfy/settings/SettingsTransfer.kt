// SettingsTransfer.kt
//
// Common-main equivalents of upstream TransferArchive's
// preferencesToJson / jsonIntoPreferences / sharedPreferencesToJson / mapStrings.
// The store is represented as Map<String, SettingEntry> so the exact same
// `mochy-transfer` settings JSON can be read and written without Android's
// JSONObject or Jetpack DataStore.

package com.example.musicfy.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

object SettingsTransfer {
    const val Format = "mochy-transfer"
    const val FormatVersion = 1
    const val SettingsEntry = "settings/settings.json"
    const val PrefsPrefix = "settings/prefs/"

    /** Sign-ins and tokens stay behind; a fresh install signs in again. */
    val LoginKeys: Set<String> = setOf(
        SettingsKeys.InnerTubeCookieKey,
        SettingsKeys.VisitorDataKey,
        SettingsKeys.DataSyncIdKey,
        SettingsKeys.AccountNameKey,
        SettingsKeys.AccountEmailKey,
        SettingsKeys.AccountChannelHandleKey,
        SettingsKeys.DiscordAccessTokenKey,
        SettingsKeys.DiscordRefreshTokenKey,
        SettingsKeys.DiscordTokenExpiresAtKey,
        SettingsKeys.DiscordLoginStateKey,
        SettingsKeys.DiscordLoginVerifierKey,
        SettingsKeys.DiscordProfileKey,
        SettingsKeys.EnableDiscordRPCKey,
        SettingsKeys.LastFMSessionKey,
        SettingsKeys.LastFMUsernameKey,
        SettingsKeys.EnableLastFMScrobblingKey,
        SettingsKeys.LibreFmSessionKey,
        SettingsKeys.LibreFmUsernameKey,
        SettingsKeys.EnableLibreFmScrobblingKey,
        SettingsKeys.MonochromePlaybackApiTokenKey,
        SettingsKeys.TurnstileBypassTokenKey,
    )

    /** Typed settings JSON, so ints stay ints and sets stay arrays. */
    fun preferencesToJson(
        entries: Map<String, SettingEntry>,
        skip: Set<String> = emptySet(),
    ): String = buildJsonObject {
        for ((name, entry) in entries) {
            if (name in skip) continue
            val encoded = SettingsSnapshot.encode(entry) ?: continue
            put(name, encoded.toJsonObject())
        }
    }.toString()

    /** Same shape as [preferencesToJson], for SharedPreferences-like maps. */
    fun sharedPreferencesToJson(values: Map<String, Any?>): String = buildJsonObject {
        for ((name, value) in values) {
            val encoded = encodePlainValue(value ?: continue) ?: continue
            put(name, encoded.toJsonObject())
        }
    }.toString()

    /** Parses typed settings JSON, skipping login keys from older archives. */
    fun jsonIntoPreferences(
        json: String,
        skipLoginKeys: Boolean = true,
    ): Map<String, SettingEntry> {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return emptyMap()
        return buildMap {
            for ((name, element) in root) {
                if (skipLoginKeys && name in LoginKeys) continue
                val obj = element as? JsonObject ?: continue
                val type = obj["t"] as? JsonPrimitive ?: continue
                val valueElement = obj["v"] ?: continue
                val value = decodeValue(type.content, valueElement) ?: continue
                val entry = SettingsSnapshot.decode(type.content, value) ?: continue
                put(name, entry)
            }
        }
    }

    /** Runs every text value through [change]; used to repoint saved file paths. */
    fun mapStrings(json: String, change: (String) -> String): String {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return json
        return buildJsonObject {
            for ((name, element) in root) {
                val obj = element as? JsonObject
                val type = (obj?.get("t") as? JsonPrimitive)?.content
                val updated: JsonObject? = when (type) {
                    SettingsSnapshot.TYPE_STRING -> {
                        val primitive = obj?.get("v") as? JsonPrimitive
                        if (primitive == null) null else buildJsonObject {
                            put("t", JsonPrimitive(SettingsSnapshot.TYPE_STRING))
                            put("v", JsonPrimitive(change(primitive.content)))
                        }
                    }
                    SettingsSnapshot.TYPE_STRING_SET -> {
                        val array = obj?.get("v") as? JsonArray
                        if (array == null) null else buildJsonObject {
                            put("t", JsonPrimitive(SettingsSnapshot.TYPE_STRING_SET))
                            put("v", JsonArray(array.mapNotNull { (it as? JsonPrimitive)?.content }.map { JsonPrimitive(change(it)) }))
                        }
                    }
                    else -> null
                }
                put(name, updated ?: element)
            }
        }.toString()
    }

    /** Applies a parsed archive to the store; unsupported types degrade to the closest key type. */
    fun jsonIntoSettingsStore(
        json: String,
        store: SettingsStore,
        skipLoginKeys: Boolean = true,
    ) = applyEntries(jsonIntoPreferences(json, skipLoginKeys), store)

    fun applyEntries(entries: Map<String, SettingEntry>, store: SettingsStore) {
        for ((name, entry) in entries) {
            when (entry.t) {
                SettingsSnapshot.TYPE_BOOLEAN -> store.setBoolean(name, SettingsSnapshot.asBoolean(entry))
                SettingsSnapshot.TYPE_INT -> store.setInt(name, SettingsSnapshot.asInt(entry))
                SettingsSnapshot.TYPE_LONG -> store.setLong(name, SettingsSnapshot.asLong(entry))
                SettingsSnapshot.TYPE_FLOAT -> store.setFloat(name, SettingsSnapshot.asFloat(entry))
                SettingsSnapshot.TYPE_DOUBLE -> {
                    // TODO: native double key support in SettingsStore.
                    store.setFloat(name, SettingsSnapshot.asDouble(entry).toFloat())
                }
                SettingsSnapshot.TYPE_STRING -> store.setString(name, SettingsSnapshot.asString(entry))
                SettingsSnapshot.TYPE_STRING_SET -> store.setStringSet(name, SettingsSnapshot.asStringSet(entry))
                SettingsSnapshot.TYPE_BYTES -> store.setBytes(name, SettingsSnapshot.asBytes(entry))
            }
        }
    }

    private fun encodePlainValue(value: Any): EncodedSetting? = when (value) {
        is Boolean -> SettingsSnapshot.encode(SettingEntry(SettingsSnapshot.TYPE_BOOLEAN, value))
        is Int -> SettingsSnapshot.encode(SettingEntry(SettingsSnapshot.TYPE_INT, value))
        is Long -> SettingsSnapshot.encode(SettingEntry(SettingsSnapshot.TYPE_LONG, value))
        is Float -> SettingsSnapshot.encode(SettingEntry(SettingsSnapshot.TYPE_FLOAT, value))
        is Double -> SettingsSnapshot.encode(SettingEntry(SettingsSnapshot.TYPE_DOUBLE, value))
        is String -> SettingsSnapshot.encode(SettingEntry(SettingsSnapshot.TYPE_STRING, value))
        is Set<*> -> SettingsSnapshot.encode(SettingEntry(SettingsSnapshot.TYPE_STRING_SET, value))
        is ByteArray -> SettingsSnapshot.encode(SettingEntry(SettingsSnapshot.TYPE_BYTES, value))
        else -> null
    }

    private fun decodeValue(type: String, element: JsonElement): Any? {
        val primitive = element as? JsonPrimitive
        return when (type) {
            SettingsSnapshot.TYPE_BOOLEAN -> primitive?.content?.toBooleanStrictOrNull()
            SettingsSnapshot.TYPE_INT -> primitive?.content?.toIntOrNull()
            SettingsSnapshot.TYPE_LONG -> primitive?.content?.toLongOrNull()
            SettingsSnapshot.TYPE_FLOAT -> primitive?.content?.toDoubleOrNull()?.toFloat()
            SettingsSnapshot.TYPE_DOUBLE -> primitive?.content?.toDoubleOrNull()
            SettingsSnapshot.TYPE_STRING -> primitive?.contentOrNull
            SettingsSnapshot.TYPE_STRING_SET -> (element as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.toSet()
            SettingsSnapshot.TYPE_BYTES -> primitive?.contentOrNull
            else -> null
        }
    }

    private fun EncodedSetting.toJsonObject(): JsonObject = buildJsonObject {
        put("t", JsonPrimitive(t))
        put("v", when (v) {
            is Boolean -> JsonPrimitive(v)
            is Int -> JsonPrimitive(v.toString())
            is Long -> JsonPrimitive(v.toString())
            is Double -> JsonPrimitive(v.toString())
            is String -> JsonPrimitive(v)
            is List<*> -> JsonArray(v.mapNotNull { it as? String }.map { JsonPrimitive(it) })
            else -> JsonPrimitive(v.toString())
        })
    }
}
