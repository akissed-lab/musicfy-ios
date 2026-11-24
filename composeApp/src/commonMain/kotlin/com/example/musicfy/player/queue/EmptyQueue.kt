// EmptyQueue.kt

package com.example.musicfy.player.queue

import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.model.PlaybackTrack

/** The no-op queue used before anything has been selected. */
object EmptyQueue : PlaybackQueue {
    override val title: String? = null
    override val preloadTrack: MediaMetadata? = null

    override suspend fun getInitialStatus(): PlaybackQueue.Status =
        PlaybackQueue.Status(title = null, tracks = emptyList(), mediaItemIndex = -1)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<PlaybackTrack> = emptyList()
}
