package com.example.musicfy.lyrics

import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.SettingsStore
import com.music.directlyrics.Deezer

object DeezerLyricsProvider : LyricsProvider {
    override val name = "Deezer"

    override fun isEnabled(settings: SettingsStore): Boolean =
        settings.getBoolean(SettingsKeys.EnableDeezerKey, true)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Deezer.getLyrics(title, artist, duration)
}
