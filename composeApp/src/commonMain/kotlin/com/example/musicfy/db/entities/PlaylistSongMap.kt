package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class PlaylistSongMap(
    val id: Int = 0,
    val playlistId: String,
    val songId: String,
    val position: Int = 0,
    val setVideoId: String? = null,
)
