package com.example.musicfy.models

import androidx.compose.runtime.Immutable
import com.music.innertube.models.MUSIC_VIDEO_TYPE_ATV
import kotlinx.datetime.LocalDateTime

/**
 * KMP port of upstream `models/MediaMetadata.kt`.
 *
 * Java's `Serializable` and `java.time.LocalDateTime` are replaced by a plain
 * immutable model and `kotlinx-datetime`; all field names are kept so callers
 * ported from the Android app keep the same contract.
 */
@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val musicVideoType: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val suggestedBy: String? = null,
    val localUri: String? = null,
) {
    val isVideoSong: Boolean
        get() = musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV

    data class Artist(
        val id: String?,
        val name: String,
    )

    data class Album(
        val id: String,
        val title: String,
    )
}
