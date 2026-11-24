package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class PlaylistSong(
    val map: PlaylistSongMap,
    val song: Song,
)
