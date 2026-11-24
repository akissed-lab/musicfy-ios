package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class AlbumArtistMap(
    val albumId: String,
    val artistId: String,
    val order: Int,
)
