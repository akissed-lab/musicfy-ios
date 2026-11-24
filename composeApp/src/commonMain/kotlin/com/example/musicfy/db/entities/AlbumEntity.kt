package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import com.example.musicfy.util.nowLocal
import kotlinx.datetime.LocalDateTime

@Immutable
data class AlbumEntity(
    val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int,
    val duration: Int,
    val explicit: Boolean = false,
    val lastUpdateTime: LocalDateTime = nowLocal(),
    val bookmarkedAt: LocalDateTime? = null,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val description: String? = null,
    val isLocal: Boolean = false,
    val isUploaded: Boolean = false,
) {
    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else nowLocal(),
    )

    fun toggleUploaded() = copy(isUploaded = !isUploaded)

    fun toggleLibrary() = copy(
        inLibrary = if (inLibrary != null) null else nowLocal(),
    )

    fun toggleLike() = localToggleLike()
}
