package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class AlbumWithSongs(
    val album: AlbumEntity,
    val artists: List<ArtistEntity>,
    val songs: List<Song>,
)
