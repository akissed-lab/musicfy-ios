package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

/** KMP port of the Room `Song` relation, with joins resolved by the DAO mappers. */
@Immutable
data class Song(
    val song: SongEntity,
    val artists: List<ArtistEntity>,
    val album: AlbumEntity? = null,
    val format: FormatEntity? = null,
) : LocalItem() {
    override val id: String get() = song.id
    override val title: String get() = song.title
    override val thumbnailUrl: String? get() = song.thumbnailUrl
    val romanizeLyrics: Boolean get() = song.romanizeLyrics
}
