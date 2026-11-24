package com.example.musicfy.lyrics

import com.example.musicfy.settings.SettingsStore
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint

object YouTubeLyricsProvider : LyricsProvider {
    override val name = "YouTubeMusic"

    override fun isEnabled(settings: SettingsStore): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val next = YouTube.next(WatchEndpoint(videoId = id)).getOrThrow()
        YouTube.lyrics(
            next.lyricsEndpoint ?: throw IllegalStateException("Lyrics endpoint not found"),
        ).getOrThrow() ?: throw IllegalStateException("Lyrics unavailable")
    }
}
