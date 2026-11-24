package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

/** KMP model of the `sorted_song_album_map` view (ORDER BY `index`). */
@Immutable
data class SortedSongAlbumMap(
    val songId: String,
    val albumId: String,
    val index: Int,
)
