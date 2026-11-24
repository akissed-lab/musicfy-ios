package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

/** KMP model of the `playlist_song_map_preview` view (first three songs). */
@Immutable
data class PlaylistSongMapPreview(
    val playlistId: String,
    val songId: String,
    val idInPlaylist: Int = 0,
)
