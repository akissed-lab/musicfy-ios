package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import com.example.musicfy.util.nowLocal
import com.example.musicfy.util.randomLetters
import kotlinx.datetime.LocalDateTime

@Immutable
data class PlaylistEntity(
    val id: String = generatePlaylistId(),
    val name: String,
    val browseId: String? = null,
    val createdAt: LocalDateTime? = nowLocal(),
    val lastUpdateTime: LocalDateTime? = nowLocal(),
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val thumbnailUrl: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    val isLocal: Boolean = false,
    val isAutoSync: Boolean = false,
    /** Free text the owner wrote about the playlist. Local only - never sent to YouTube. */
    val description: String? = null,
) {
    val shareLink: String?
        get() = if (browseId != null) {
            "https://music.youtube.com/playlist?list=$browseId"
        } else {
            null
        }

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else nowLocal(),
    )

    fun toggleLike() = localToggleLike()

    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"

        fun generatePlaylistId() = "LP" + randomLetters(8)
    }
}
