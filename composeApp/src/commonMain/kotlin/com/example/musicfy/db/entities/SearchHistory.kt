package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class SearchHistory(
    val id: Long = 0,
    val query: String,
)
