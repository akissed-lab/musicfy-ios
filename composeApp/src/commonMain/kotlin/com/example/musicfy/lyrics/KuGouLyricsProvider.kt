package com.example.musicfy.lyrics

import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.SettingsStore
import com.music.kugou.KuGou

object KuGouLyricsProvider : LyricsProvider {
    override val name = "Kugou"

    override fun isEnabled(settings: SettingsStore): Boolean =
        settings.getBoolean(SettingsKeys.EnableKugouKey, true)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = KuGou.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        KuGou.getAllPossibleLyricsOptions(title, artist, duration, album, callback)
    }
}
