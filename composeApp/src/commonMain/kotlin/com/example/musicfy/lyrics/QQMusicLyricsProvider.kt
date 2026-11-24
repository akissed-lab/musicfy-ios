package com.example.musicfy.lyrics

import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.SettingsStore
import com.music.directlyrics.QQMusic

object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"

    override fun isEnabled(settings: SettingsStore): Boolean =
        settings.getBoolean(SettingsKeys.EnableQQMusicKey, true)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = QQMusic.getLyrics(title, artist, duration)
}
