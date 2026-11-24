package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import com.example.musicfy.util.currentTimeMillis
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

@Immutable
data class SpeedDialItem(
    val id: String,
    val secondaryId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val type: String,
    val explicit: Boolean = false,
    val createDate: Long = currentTimeMillis(),
) {
    fun toYTItem(): YTItem = when (type) {
        "SONG" -> SongItem(
            id = id,
            title = title,
            artists = subtitle?.split(", ")?.map { Artist(name = it, id = null) } ?: emptyList(),
            thumbnail = thumbnailUrl ?: "",
            explicit = explicit,
        )
        "ALBUM" -> AlbumItem(
            browseId = id,
            playlistId = secondaryId ?: "",
            title = title,
            artists = subtitle?.split(", ")?.map { Artist(name = it, id = null) },
            thumbnail = thumbnailUrl ?: "",
            explicit = explicit,
        )
        "ARTIST" -> ArtistItem(
            id = id,
            title = title,
            thumbnail = thumbnailUrl,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
        "PLAYLIST" -> PlaylistItem(
            id = id,
            title = title,
            author = subtitle?.let { Artist(name = it, id = null) },
            songCountText = null,
            thumbnail = thumbnailUrl,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
        else -> throw IllegalArgumentException("Unknown type: $type")
    }

    companion object {
        fun fromYTItem(item: YTItem): SpeedDialItem = when (item) {
            is SongItem -> SpeedDialItem(
                id = item.id,
                title = item.title,
                subtitle = item.artists.joinToString(", ") { it.name },
                thumbnailUrl = item.thumbnail,
                type = "SONG",
                explicit = item.explicit,
            )
            is AlbumItem -> SpeedDialItem(
                id = item.browseId,
                secondaryId = item.playlistId,
                title = item.title,
                subtitle = item.artists?.joinToString(", ") { it.name },
                thumbnailUrl = item.thumbnail,
                type = "ALBUM",
                explicit = item.explicit,
            )
            is ArtistItem -> SpeedDialItem(
                id = item.id,
                title = item.title,
                thumbnailUrl = item.thumbnail,
                type = "ARTIST",
            )
            is PlaylistItem -> SpeedDialItem(
                id = item.id,
                title = item.title,
                subtitle = item.author?.name,
                thumbnailUrl = item.thumbnail,
                type = "PLAYLIST",
            )
        }
    }
}
