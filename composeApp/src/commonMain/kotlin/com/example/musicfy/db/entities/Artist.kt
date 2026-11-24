package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class Artist(
    val artist: ArtistEntity,
    val songCount: Int,
    val timeListened: Int? = 0,
) : LocalItem() {
    override val id: String get() = artist.id
    override val title: String get() = artist.name
    override val thumbnailUrl: String? get() = artist.thumbnailUrl
}
