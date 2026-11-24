package com.example.musicfy.lyrics

import com.example.musicfy.betterlyrics.BetterLyrics
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.SettingsStore

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(settings: SettingsStore): Boolean =
        settings.getBoolean(SettingsKeys.EnableBetterLyricsKey, true)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(title, artist, duration, album, callback)
    }
}
