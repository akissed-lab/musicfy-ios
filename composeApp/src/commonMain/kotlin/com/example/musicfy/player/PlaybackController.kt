// PlaybackController.kt

package com.example.musicfy.player

import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.model.PlayerState
import com.example.musicfy.player.model.ProgressState
import com.example.musicfy.player.model.QueueState
import com.example.musicfy.player.model.SleepTimerState
import com.example.musicfy.player.model.StreamFacts
import com.example.musicfy.player.model.TrackInfo
import com.example.musicfy.player.model.TransportState
import com.example.musicfy.player.queue.PlaybackQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The only playback API UI/ViewModels should depend on.
 *
 * Commands are intentionally non-suspend: upstream call sites such as
 * `playerConnection.togglePlayPause()` / `playQueue(...)` port unchanged, while the controller
 * owns a CoroutineScope for suspend queue and stream work.
 */
interface PlaybackController {
    // Aggregate + the exact flows PlayerUiState/ui screens consume.
    val playerState: StateFlow<PlayerState>
    val transportState: StateFlow<TransportState>
    val trackInfo: StateFlow<TrackInfo>
    val queueState: StateFlow<QueueState>
    val progressState: StateFlow<ProgressState>

    val currentMetadata: StateFlow<MediaMetadata?>
    val currentLyrics: StateFlow<LyricsEntity?>
    val lyricsOffsetMs: StateFlow<Int>
    val volume: StateFlow<Float>
    val isMuted: StateFlow<Boolean>
    val sleepTimerState: StateFlow<SleepTimerState>
    val allStreamFacts: StateFlow<Map<String, StreamFacts>>

    // Transport.
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(position: Long)
    fun seekTo(position: Long, mediaItemIndex: Int)
    fun seekToNext()
    fun seekToPrevious()

    // Queue / sources.
    fun playQueue(queue: PlaybackQueue, playWhenReady: Boolean = true)
    fun playNext(items: List<com.example.musicfy.player.model.PlaybackTrack>)
    fun addToQueue(items: List<com.example.musicfy.player.model.PlaybackTrack>)
    fun startRadioSeamlessly()
    fun setInfiniteQueueEnabled(enabled: Boolean)

    // Queue page operations; uid is QueueItemData.uid.
    fun playQueueItem(uid: Int)
    fun moveQueueItem(uid: Int, afterUid: Int)
    fun playQueueItemNext(uid: Int)
    fun removeQueueItems(uids: Collection<Int>)

    // Modes / audio.
    fun setShuffleModeEnabled(enabled: Boolean)
    fun setRepeatMode(mode: Int)
    fun toggleRepeatMode()
    fun setVolume(volume: Float)
    fun setMuted(muted: Boolean)
    fun toggleMute()
    fun setPlaybackSpeed(speed: Float)

    // Song actions / lyrics / ancillary UI.
    fun toggleLike()
    fun toggleLibrary()
    fun setLyricsOffsetMs(offsetMs: Int)
    fun startSleepTimer(minutes: Int)
    fun clearSleepTimer()
    fun requestPlayerStudio()
    fun streamFacts(mediaId: String): Flow<StreamFacts?>

    fun dispose()
}
