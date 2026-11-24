package com.example.musicfy.lyrics

import com.example.musicfy.settings.SettingsStore

/**
 * Ported from Android `lyrics/LyricsProvider.kt`.
 *
 * Android passed `Context` to read DataStore; the KMP version reads the
 * same `SettingsKeys` from [SettingsStore] (NSUserDefaults on iOS).
 */
interface LyricsProvider {
    val name: String

    fun isEnabled(settings: SettingsStore): Boolean

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String>

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, duration, album).onSuccess(callback)
    }
}
