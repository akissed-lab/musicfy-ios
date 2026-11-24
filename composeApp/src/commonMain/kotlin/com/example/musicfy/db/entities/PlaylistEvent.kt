package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDateTime

@Immutable
data class PlaylistEvent(
    val id: Long = 0,
    val playlistId: String,
    val timestamp: LocalDateTime,
)
