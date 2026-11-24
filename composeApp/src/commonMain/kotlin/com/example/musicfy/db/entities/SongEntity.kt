package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import com.example.musicfy.util.nowLocal
import kotlinx.datetime.LocalDateTime

/**
 * KMP port of upstream `db/entities/SongEntity.kt`.
 *
 * Room annotations and the fire-and-forget YouTube sync calls are removed:
 * syncing is handled by the ported sync layer, while these methods keep the
 * exact local copy semantics used by the UI.
 */
@Immutable
data class SongEntity(
    val id: String,
    val title: String,
    val duration: Int = -1,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null,
    val dateModified: LocalDateTime? = null,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0,
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = null,
    val isLocal: Boolean = false,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val lyricsOffset: Int = 0,
    val romanizeLyrics: Boolean = true,
    val isDownloaded: Boolean = false,
    val isUploaded: Boolean = false,
    val isVideo: Boolean = false,
    val localUri: String? = null,
) {
    fun localToggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) nowLocal() else null,
    )

    fun toggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) nowLocal() else null,
        inLibrary = if (!liked) inLibrary ?: nowLocal() else inLibrary,
    )

    fun toggleLibrary(syncToYouTube: Boolean = true) = copy(
        liked = if (inLibrary == null) liked else false,
        inLibrary = if (inLibrary == null) nowLocal() else null,
        likedDate = if (inLibrary == null) likedDate else null,
    )

    fun toggleUploaded() = copy(isUploaded = !isUploaded)
}
