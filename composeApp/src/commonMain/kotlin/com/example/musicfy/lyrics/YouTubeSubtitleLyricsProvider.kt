package com.example.musicfy.lyrics

import com.example.musicfy.settings.SettingsStore
import com.music.innertube.YouTube

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    // Must match the registry key, not the display name: it is stored on LyricsEntity.provider.
    override val name = "YouTubeSubtitle"

    override fun isEnabled(settings: SettingsStore): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouTube.transcript(id)
}
