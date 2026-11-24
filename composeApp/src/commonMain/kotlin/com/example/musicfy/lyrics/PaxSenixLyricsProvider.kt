package com.example.musicfy.lyrics

import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.SettingsStore
import com.music.paxsenix.Paxsenix

object PaxSenixLyricsProvider : LyricsProvider {
    override val name = "Paxsenix"

    override fun isEnabled(settings: SettingsStore): Boolean =
        settings.getBoolean(SettingsKeys.EnablePaxsenixKey, true)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Paxsenix.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        Paxsenix.getAllLyrics(title, artist, duration, album, callback)
    }
}
