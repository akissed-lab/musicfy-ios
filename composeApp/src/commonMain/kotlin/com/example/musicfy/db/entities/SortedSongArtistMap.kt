package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

/** KMP model of the `sorted_song_artist_map` view (ORDER BY position). */
@Immutable
data class SortedSongArtistMap(
    val songId: String,
    val artistId: String,
    val position: Int,
)
