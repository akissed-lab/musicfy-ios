// PlaybackQueue.kt

package com.example.musicfy.player.queue

import androidx.compose.runtime.Immutable
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.model.PlaybackTrack

/** Lazy/paginated queue contract shared by the controller and future WP5 queue sources. */
interface PlaybackQueue {
    val title: String?
    val preloadTrack: MediaMetadata?

    suspend fun getInitialStatus(): Status
    fun hasNextPage(): Boolean
    suspend fun nextPage(): List<PlaybackTrack>

    @Immutable
    data class Status(
        val title: String?,
        val tracks: List<PlaybackTrack>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true): Status =
            if (enabled) copy(tracks = tracks.filterNot { it.metadata.explicit }) else this

        fun filterVideoSongs(disableVideos: Boolean = false): Status =
            if (disableVideos) copy(tracks = tracks.filterNot { it.metadata.isVideoSong }) else this
    }
}
