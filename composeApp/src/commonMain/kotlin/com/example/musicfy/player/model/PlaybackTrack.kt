// PlaybackTrack.kt

package com.example.musicfy.player.model

import androidx.compose.runtime.Immutable
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.DrmHint

/** A playable item: domain metadata plus the information a native engine needs to open it. */
@Immutable
data class PlaybackTrack(
    val mediaId: String,
    val metadata: MediaMetadata,
    val source: TrackSource = TrackSource.Remote(mediaId),
    val isAutoplay: Boolean = false,
)

sealed interface TrackSource {
    /** A file the app can read directly; no stream resolution or DRM. */
    @Immutable
    data class Local(val uri: String) : TrackSource

    /** An already resolved HTTP(HLS/DASH) URL. */
    @Immutable
    data class Direct(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val drm: DrmHint? = null,
    ) : TrackSource

    /** A provider video id that must be resolved by a [com.example.musicfy.player.StreamResolver]. */
    @Immutable
    data class Remote(
        val videoId: String,
        val headers: Map<String, String> = emptyMap(),
        val drm: DrmHint? = null,
    ) : TrackSource
}
