package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class SetVideoIdEntity(
    val videoId: String = "",
    val setVideoId: String? = null,
)
