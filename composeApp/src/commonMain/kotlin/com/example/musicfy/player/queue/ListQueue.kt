// ListQueue.kt

package com.example.musicfy.player.queue

import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.model.PlaybackTrack

/** A fully materialised queue; the common case for local/playlist playback. */
class ListQueue(
    override val title: String? = null,
    val items: List<PlaybackTrack>,
    val startIndex: Int = 0,
    val position: Long = 0L,
) : PlaybackQueue {
    override val preloadTrack: MediaMetadata? = null

    override suspend fun getInitialStatus(): PlaybackQueue.Status = PlaybackQueue.Status(
        title = title,
        tracks = items,
        mediaItemIndex = startIndex.coerceAtLeast(0).coerceAtMost(maxOf(0, items.lastIndex)),
        position = position,
    )

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<PlaybackTrack> = emptyList()
}
