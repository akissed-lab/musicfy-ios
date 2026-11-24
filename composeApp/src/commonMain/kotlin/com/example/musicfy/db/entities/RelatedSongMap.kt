package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class RelatedSongMap(
    val id: Long = 0,
    val songId: String,
    val relatedSongId: String,
)
