package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class SongArtistMap(
    val songId: String,
    val artistId: String,
    val position: Int,
)
