package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class SongAlbumMap(
    val songId: String,
    val albumId: String,
    val index: Int,
)
