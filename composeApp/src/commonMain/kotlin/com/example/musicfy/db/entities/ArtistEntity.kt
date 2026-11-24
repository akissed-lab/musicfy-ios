package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import com.example.musicfy.util.nowLocal
import com.example.musicfy.util.randomLetters
import kotlinx.datetime.LocalDateTime

@Immutable
data class ArtistEntity(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val lastUpdateTime: LocalDateTime = nowLocal(),
    val bookmarkedAt: LocalDateTime? = null,
    val isLocal: Boolean = false,
) {
    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith("FEmusic_library_privately_owned_artist")

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else nowLocal(),
    )

    fun toggleLike() = localToggleLike()

    companion object {
        fun generateArtistId() = "LA" + randomLetters(8)
    }
}
