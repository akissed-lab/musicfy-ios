// PlayerModels.kt

package com.example.musicfy.player.model

import androidx.compose.runtime.Immutable
import com.example.musicfy.player.PlaybackConstants

/** Port of upstream `ui/player/models/PlayerModels.kt`, without Android Uri/Media3 types. */
@Immutable
data class TransportState(
    val isPlaying: Boolean = false,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = PlaybackConstants.REPEAT_MODE_OFF,
    val canSkipNext: Boolean = true,
    val canSkipPrevious: Boolean = true,
    val playbackState: Int = PlaybackConstants.STATE_IDLE,
)

@Immutable
data class TrackInfo(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val thumbnailUrl: String? = null,
    val liked: Boolean = false,
)

@Immutable
data class QueueItemData(
    /** Session-stable id minted by the controller; re-resolve at call time. */
    val uid: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    /** String instead of android.net.Uri. */
    val artworkUri: String? = null,
    val duration: Long = 0L,
    /** Lined up by the infinite queue rather than by the user. */
    val isAutoplay: Boolean = false,
)

@Immutable
data class QueueState(
    val items: List<QueueItemData> = emptyList(),
    val currentIndex: Int = -1,
    val title: String = "",
)

@Immutable
data class ProgressState(
    val position: Long = 0L,
    val duration: Long = 0L,
    val percentage: Float = 0f,
)

@Immutable
data class SleepTimerState(
    val triggerAtMs: Long = -1L,
    val remainingMs: Long = -1L,
    val pauseAfterCurrentSong: Boolean = false,
) {
    val isActive: Boolean
        get() = triggerAtMs != -1L || pauseAfterCurrentSong
}

@Immutable
data class PlaybackError(
    val message: String,
    val cause: String? = null,
    val isNetwork: Boolean = false,
    val canRetry: Boolean = true,
)

@Immutable
data class StreamFailure(val startedAtMs: Long, val timeoutMs: Long)

/** One aggregate snapshot for callers that want the whole player at once. */
@Immutable
data class PlayerState(
    val transport: TransportState = TransportState(),
    val track: TrackInfo = TrackInfo(),
    val queue: QueueState = QueueState(),
    val progress: ProgressState = ProgressState(),
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val error: PlaybackError? = null,
    val streamFailure: StreamFailure? = null,
    val waitingForNetwork: Boolean = false,
    val isCrossmixing: Boolean = false,
    val infiniteQueueEnabled: Boolean = true,
)
