package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class Playlist(
    val playlist: PlaylistEntity,
    val songCount: Int,
    val songThumbnails: List<String?>,
) : LocalItem() {
    override val id: String get() = playlist.id
    override val title: String get() = playlist.name
    override val thumbnailUrl: String? get() = null

    val thumbnails: List<String>
        get() = if (playlist.thumbnailUrl != null) {
            listOf(playlist.thumbnailUrl!!)
        } else {
            songThumbnails.filterNotNull()
        }
}
