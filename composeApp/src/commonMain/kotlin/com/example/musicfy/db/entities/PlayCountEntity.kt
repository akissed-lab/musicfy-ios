package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class PlayCountEntity(
    val song: String,
    val year: Int = -1,
    val month: Int = -1,
    val count: Int = -1,
)
