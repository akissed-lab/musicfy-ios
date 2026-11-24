// FakeCatalog.kt

package com.example.musicfy.player.fake

import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.StreamFacts
import com.example.musicfy.player.model.TrackSource
import com.example.musicfy.player.queue.ListQueue

/**
 * Deterministic, offline content for Compose previews and for the in-memory controller while the
 * AVFoundation engine is not ported yet. No network, no DRM.
 */
object FakeCatalog {
    const val title: String = "Musicfy demo queue"

    private fun track(
        id: String,
        name: String,
        artist: String,
        album: String,
        durationSeconds: Int,
        liked: Boolean = false,
    ): PlaybackTrack = PlaybackTrack(
        mediaId = id,
        metadata = MediaMetadata(
            id = id,
            title = name,
            artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
            duration = durationSeconds,
            thumbnailUrl = null,
            album = MediaMetadata.Album(id = "fake-album", title = album),
            liked = liked,
        ),
        source = TrackSource.Remote(videoId = id),
    )

    val tracks: List<PlaybackTrack> = listOf(
        track("fake-1", "Midnight Signal", "Neon Coast", "Night Drive", 214, liked = true),
        track("fake-2", "Paper Lanterns", "Quiet Harbor", "Night Drive", 187),
        track("fake-3", "Glass Horizon", "Aurora Lane", "Slow Motion", 263),
        track("fake-4", "Static Bloom", "Neon Coast", "Slow Motion", 198),
        track("fake-5", "Afterimage", "Quiet Harbor", "Night Drive", 231),
    )

    /** Appended by [com.example.musicfy.player.fake.InMemoryPlaybackController.startRadioSeamlessly]. */
    val radioTracks: List<PlaybackTrack> = listOf(
        track("fake-radio-1", "Radio: First Light", "Various Artists", "Discover", 176),
        track("fake-radio-2", "Radio: Blue Hour", "Various Artists", "Discover", 205),
        track("fake-radio-3", "Radio: Last Train", "Various Artists", "Discover", 224),
    ).map { it.copy(isAutoplay = true) }

    private val facts: Map<String, StreamFacts> = tracks.associate {
        it.mediaId to StreamFacts(
            codec = if (it.mediaId == "fake-3") "FLAC" else "OPUS",
            sampleRate = if (it.mediaId == "fake-3") 44_100 else 48_000,
        )
    } + radioTracks.associate { it.mediaId to StreamFacts(codec = "OPUS", sampleRate = 48_000) }

    val initialStreamFacts: Map<String, StreamFacts> get() = facts

    fun streamFacts(mediaId: String): StreamFacts? = facts[mediaId]

    fun sampleQueue(title: String? = FakeCatalog.title): ListQueue = ListQueue(
        title = title,
        items = tracks,
    )
}
