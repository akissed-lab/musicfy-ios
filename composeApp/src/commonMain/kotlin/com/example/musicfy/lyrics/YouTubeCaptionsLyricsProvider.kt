package com.example.musicfy.lyrics

import com.example.musicfy.settings.SettingsStore
import com.music.innertube.YouTube

/** Speech recognition gets sung words wrong, so LyricsHelper only uses this source as a last resort. */
object YouTubeCaptionsLyricsProvider : LyricsProvider {
    override val name = "YouTubeCaptions"

    override fun isEnabled(settings: SettingsStore): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouTube.captions(id)
}
