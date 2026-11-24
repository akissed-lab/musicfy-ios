package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class EventWithSong(
    val event: Event,
    val song: Song,
)
