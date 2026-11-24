// InMemoryPlaybackController.kt

package com.example.musicfy.player.fake

import androidx.compose.runtime.Stable
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.PlaybackConstants
import com.example.musicfy.player.PlaybackController
import com.example.musicfy.player.PlaybackDependencies
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.PlayerState
import com.example.musicfy.player.model.ProgressState
import com.example.musicfy.player.model.QueueItemData
import com.example.musicfy.player.model.QueueState
import com.example.musicfy.player.model.SleepTimerState
import com.example.musicfy.player.model.StreamFacts
import com.example.musicfy.player.model.TrackInfo
import com.example.musicfy.player.model.TransportState
import com.example.musicfy.player.queue.PlaybackQueue
import com.example.musicfy.util.nowLocal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.musicfy.util.currentTimeMillis
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * A completely in-memory [PlaybackController] used by previews and early UI work.
 *
 * It reproduces the shape of upstream `PlayerConnection` (flows, uid-addressed queue operations,
 * 66 ms progress polling, Media3-compatible state/repeat constants) but plays no audio: there is
 * no AVFoundation, no network and no DRM here. The position is advanced by a monotonic-clock
 * ticker so the progress UI behaves exactly as it will with a real engine.
 */
@Stable
@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryPlaybackController(
    private val scope: CoroutineScope,
    private val dependencies: PlaybackDependencies? = null,
) : PlaybackController {
    private data class QueueEntry(val uid: Int, val track: PlaybackTrack)

    /** Child scope: [dispose] stops the ticker and any queue work started by this controller. */
    private val controllerScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
    )

    private val initialState = dependencies?.initialState
    private val random = Random(1_337L)

    private var nextUid = 1
    /** Current play order; uid ops re-resolve against it at call time, like upstream. */
    private var entries = mutableListOf<QueueEntry>()
    /** Insertion order, used to undo shuffle. */
    private var originalOrder = mutableListOf<Int>()
    private var currentUid: Int? = null
    private var positionMs = 0L
    private var isPlaying = false
    private var playbackState = PlaybackConstants.STATE_IDLE
    private var shuffle = initialState?.shuffleModeEnabled ?: false
    private var repeatMode = (initialState?.repeatMode ?: PlaybackConstants.REPEAT_MODE_OFF)
        .coerceIn(PlaybackConstants.REPEAT_MODE_OFF, PlaybackConstants.REPEAT_MODE_ONE)
    private var queueTitle = ""
    private var volumeValue = (initialState?.volume ?: 1f).coerceIn(0f, 1f)
    private var muted = false
    private var speed = 1f
    private var sleep = SleepTimerState()
    private var playbackError: com.example.musicfy.player.model.PlaybackError? = null
    private var streamFailureState: com.example.musicfy.player.model.StreamFailure? = null
    private var waitingForNetwork = false
    private var crossmixing = false
    private var infiniteQueue = true

    // -----------------------------------------------------------------------------------------
    // Flows
    // -----------------------------------------------------------------------------------------

    private val _playerState = MutableStateFlow(
        PlayerState(
            transport = TransportState(repeatMode = repeatMode, playbackState = playbackState),
            volume = volumeValue,
        )
    )
    private val _currentMetadata = MutableStateFlow<MediaMetadata?>(null)
    private val _lyricsOffsetMs = MutableStateFlow(0)
    private val _allStreamFacts = MutableStateFlow(FakeCatalog.initialStreamFacts)
    private val _playbackSpeed = MutableStateFlow(1f)
    private val _studioRequests = MutableStateFlow(0L)

    override val playerState: StateFlow<PlayerState> get() = _playerState

    override val transportState: StateFlow<TransportState> = _playerState
        .map { it.transport }.distinctUntilChanged()
        .stateIn(controllerScope, SharingStarted.Eagerly, _playerState.value.transport)

    override val trackInfo: StateFlow<TrackInfo> = _playerState
        .map { it.track }.distinctUntilChanged()
        .stateIn(controllerScope, SharingStarted.Eagerly, _playerState.value.track)

    override val queueState: StateFlow<QueueState> = _playerState
        .map { it.queue }.distinctUntilChanged()
        .stateIn(controllerScope, SharingStarted.Eagerly, _playerState.value.queue)

    override val progressState: StateFlow<ProgressState> = _playerState
        .map { it.progress }.distinctUntilChanged()
        .stateIn(controllerScope, SharingStarted.Eagerly, _playerState.value.progress)

    override val currentMetadata: StateFlow<MediaMetadata?> get() = _currentMetadata

    override val currentLyrics: StateFlow<LyricsEntity?> =
        _currentMetadata.map { it?.id }.distinctUntilChanged()
            .stateIn<String?>(controllerScope, SharingStarted.Eagerly, null)
            .flatMapLatest { id ->
                if (id == null) flowOf<LyricsEntity?>(null)
                else dependencies?.database?.lyrics(id) ?: flowOf<LyricsEntity?>(null)
            }
            .stateIn<LyricsEntity?>(controllerScope, SharingStarted.Eagerly, null)

    override val lyricsOffsetMs: StateFlow<Int> get() = _lyricsOffsetMs
    override val volume: StateFlow<Float> = _playerState
        .map { it.volume }.distinctUntilChanged()
        .stateIn(controllerScope, SharingStarted.Eagerly, _playerState.value.volume)

    override val isMuted: StateFlow<Boolean> = _playerState
        .map { it.isMuted }.distinctUntilChanged()
        .stateIn(controllerScope, SharingStarted.Eagerly, _playerState.value.isMuted)

    override val sleepTimerState: StateFlow<SleepTimerState> = _playerState
        .map { it.sleepTimer }.distinctUntilChanged()
        .stateIn(controllerScope, SharingStarted.Eagerly, _playerState.value.sleepTimer)

    override val allStreamFacts: StateFlow<Map<String, StreamFacts>> get() = _allStreamFacts

    /** Extra flow for previews of the ported player studio. */
    val studioRequests: StateFlow<Long> get() = _studioRequests

    /** Extra flow for the ported playback-speed sheet. */
    val playbackSpeed: StateFlow<Float> get() = _playbackSpeed

    init {
        // Seed with offline content so every UI flow has something meaningful from frame one.
        loadTracks(FakeCatalog.tracks, 0, initialState?.currentPosition ?: 0L, FakeCatalog.title)
        startTicker()
    }

    // -----------------------------------------------------------------------------------------
    // State publication
    // -----------------------------------------------------------------------------------------

    private fun currentIndex(): Int = entries.indexOfFirst { it.uid == currentUid }

    private fun indexOfUid(uid: Int): Int = entries.indexOfFirst { it.uid == uid }

    private fun durationOf(track: PlaybackTrack?): Long =
        track?.metadata?.duration?.toLong()?.times(1_000L)?.coerceAtLeast(0L) ?: 0L

    private fun publish() {
        val index = currentIndex()
        val track = entries.getOrNull(index)?.track
        val metadata = track?.metadata
        val duration = durationOf(track)
        val position = positionMs.coerceAtLeast(0L)
        val hasQueue = entries.isNotEmpty()

        _currentMetadata.value = metadata
        _playerState.value = PlayerState(
            transport = TransportState(
                isPlaying = isPlaying && hasQueue && playbackState != PlaybackConstants.STATE_ENDED,
                shuffleModeEnabled = shuffle,
                repeatMode = repeatMode,
                canSkipNext = hasQueue && (index < entries.lastIndex || repeatMode != PlaybackConstants.REPEAT_MODE_OFF),
                canSkipPrevious = hasQueue && (index > 0 || repeatMode != PlaybackConstants.REPEAT_MODE_OFF || position > 3_000L),
                playbackState = playbackState,
            ),
            track = TrackInfo(
                mediaId = metadata?.id.orEmpty(),
                title = metadata?.title.orEmpty(),
                artist = metadata?.artists?.joinToString { it.name }.orEmpty(),
                album = metadata?.album?.title.orEmpty(),
                thumbnailUrl = metadata?.thumbnailUrl,
                liked = metadata?.liked == true,
            ),
            queue = QueueState(
                items = entries.map { entry ->
                    QueueItemData(
                        uid = entry.uid,
                        mediaId = entry.track.mediaId,
                        title = entry.track.metadata.title,
                        artist = entry.track.metadata.artists.joinToString { it.name },
                        artworkUri = entry.track.metadata.thumbnailUrl,
                        duration = durationOf(entry.track),
                        isAutoplay = entry.track.isAutoplay,
                    )
                },
                currentIndex = index,
                title = queueTitle,
            ),
            progress = ProgressState(
                position = position,
                duration = duration,
                percentage = if (duration > 0L) position.toFloat() / duration else 0f,
            ),
            volume = volumeValue,
            isMuted = muted,
            sleepTimer = sleep,
            error = playbackError,
            streamFailure = streamFailureState,
            waitingForNetwork = waitingForNetwork,
            isCrossmixing = crossmixing,
            infiniteQueueEnabled = infiniteQueue,
        )
    }

    private fun loadTracks(
        tracks: List<PlaybackTrack>,
        startIndex: Int,
        position: Long,
        title: String?,
    ) {
        entries = tracks.map { QueueEntry(nextUid++, it) }.toMutableList()
        originalOrder = entries.map { it.uid }.toMutableList()
        currentUid = entries.getOrNull(startIndex.coerceAtLeast(0))?.uid
        positionMs = position.coerceAtLeast(0L)
        queueTitle = title.orEmpty()
        playbackState = if (entries.isEmpty()) PlaybackConstants.STATE_IDLE else PlaybackConstants.STATE_READY
        if (shuffle) applyShuffle()
        publish()
    }

    private fun applyShuffle() {
        val index = currentIndex()
        val current = entries.getOrNull(index)
        val rest = entries.filterIndexed { i, _ -> i != index }.toMutableList()
        rest.shuffle(random)
        entries.clear()
        if (current != null) entries.add(current)
        entries.addAll(rest)
    }

    private fun restoreOriginalOrder() {
        val byUid = entries.associateBy { it.uid }
        val restored = originalOrder.mapNotNull(byUid::get).toMutableList()
        restored.addAll(byUid.values.filter { it.uid !in originalOrder })
        entries = restored
    }

    // -----------------------------------------------------------------------------------------
    // Ticker and track changes
    // -----------------------------------------------------------------------------------------

    private var tickerJob: Job? = null

    private fun startTicker() {
        val clock = TimeSource.Monotonic
        var previous = clock.markNow()
        tickerJob = controllerScope.launch {
            while (isActive) {
                val now = clock.markNow()
                val elapsed = (now - previous).inWholeMilliseconds.coerceAtLeast(0L)
                previous = now
                advanceTick(elapsed)
                delay(66L)
            }
        }
    }

    private fun advanceTick(deltaMs: Long) {
        var changed = false

        if (sleep.triggerAtMs >= 0L) {
            val remaining = sleep.triggerAtMs - currentTimeMillis()
            if (remaining <= 0L) {
                isPlaying = false
                sleep = SleepTimerState()
                changed = true
            } else if (remaining != sleep.remainingMs) {
                sleep = sleep.copy(remainingMs = remaining)
                changed = true
            }
        }

        if (isPlaying && playbackState != PlaybackConstants.STATE_ENDED && entries.isNotEmpty()) {
            val step = (deltaMs * speed).toLong().coerceAtLeast(0L)
            val duration = durationOf(entries.getOrNull(currentIndex())?.track)
            if (duration > 0L && positionMs + step >= duration) {
                handleTrackEnd()
            } else {
                positionMs += step
            }
            changed = true
        }

        if (changed) publish()
    }

    private fun handleTrackEnd() {
        if (sleep.pauseAfterCurrentSong) {
            isPlaying = false
            sleep = SleepTimerState()
            return
        }
        if (repeatMode == PlaybackConstants.REPEAT_MODE_ONE) {
            positionMs = 0L
            return
        }
        val next = currentIndex() + 1
        when {
            next < entries.size -> {
                currentUid = entries[next].uid
                positionMs = 0L
                playbackState = PlaybackConstants.STATE_READY
            }
            repeatMode == PlaybackConstants.REPEAT_MODE_ALL && entries.isNotEmpty() -> {
                currentUid = entries.first().uid
                positionMs = 0L
                playbackState = PlaybackConstants.STATE_READY
            }
            else -> {
                isPlaying = false
                playbackState = PlaybackConstants.STATE_ENDED
                positionMs = durationOf(entries.getOrNull(currentIndex())?.track)
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Transport
    // -----------------------------------------------------------------------------------------

    override fun play() {
        if (entries.isEmpty()) {
            loadTracks(FakeCatalog.tracks, 0, 0L, FakeCatalog.title)
        }
        isPlaying = true
        playbackState = PlaybackConstants.STATE_READY
        publish()
    }

    override fun pause() {
        isPlaying = false
        publish()
    }

    override fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    override fun seekTo(position: Long) {
        positionMs = position.coerceAtLeast(0L)
        if (playbackState == PlaybackConstants.STATE_IDLE && entries.isNotEmpty()) {
            playbackState = PlaybackConstants.STATE_READY
        }
        publish()
    }

    override fun seekTo(position: Long, mediaItemIndex: Int) {
        val entry = entries.getOrNull(mediaItemIndex) ?: return
        currentUid = entry.uid
        positionMs = position.coerceAtLeast(0L)
        playbackState = PlaybackConstants.STATE_READY
        publish()
    }

    override fun seekToNext() {
        if (entries.isEmpty()) return
        if (repeatMode == PlaybackConstants.REPEAT_MODE_ONE) {
            positionMs = 0L
            publish()
            return
        }
        val next = currentIndex() + 1
        val target = if (next < entries.size) next
        else if (repeatMode == PlaybackConstants.REPEAT_MODE_ALL) 0
        else return
        currentUid = entries[target].uid
        positionMs = 0L
        playbackState = PlaybackConstants.STATE_READY
        publish()
    }

    override fun seekToPrevious() {
        if (entries.isEmpty()) return
        if (positionMs > 3_000L || currentIndex() <= 0 && repeatMode == PlaybackConstants.REPEAT_MODE_OFF) {
            positionMs = 0L
            publish()
            return
        }
        val target = if (currentIndex() > 0) currentIndex() - 1
        else if (repeatMode == PlaybackConstants.REPEAT_MODE_ALL) entries.lastIndex
        else 0
        currentUid = entries[target].uid
        positionMs = 0L
        playbackState = PlaybackConstants.STATE_READY
        publish()
    }

    // -----------------------------------------------------------------------------------------
    // Queue / sources
    // -----------------------------------------------------------------------------------------

    override fun playQueue(queue: PlaybackQueue, playWhenReady: Boolean) {
        controllerScope.launch {
            val status = try {
                queue.getInitialStatus()
            } catch (_: Exception) {
                PlaybackQueue.Status(title = null, tracks = emptyList(), mediaItemIndex = -1)
            }
            loadTracks(status.tracks, status.mediaItemIndex, status.position, status.title)
            isPlaying = playWhenReady && entries.isNotEmpty()

            // Materialise a bounded runway for paginated queues (never loop forever).
            var pages = 0
            while (queue.hasNextPage() && pages < 8) {
                val page = try {
                    queue.nextPage()
                } catch (_: Exception) {
                    break
                }
                if (page.isEmpty()) break
                page.forEach { track ->
                    val entry = QueueEntry(nextUid++, track)
                    entries.add(entry)
                    originalOrder.add(entry.uid)
                }
                pages++
            }
            publish()
        }
    }

    override fun playNext(items: List<PlaybackTrack>) {
        if (items.isEmpty()) return
        val insertAt = if (currentIndex() >= 0) currentIndex() + 1 else 0
        val added = items.map { QueueEntry(nextUid++, it.copy(isAutoplay = false)) }
        entries.addAll(insertAt, added)

        val originAt = currentUid?.let { originalOrder.indexOf(it) + 1 } ?: originalOrder.size
        originalOrder.addAll(originAt.coerceIn(0, originalOrder.size), added.map { it.uid })
        publish()
    }

    override fun addToQueue(items: List<PlaybackTrack>) {
        if (items.isEmpty()) return
        items.forEach { track ->
            val entry = QueueEntry(nextUid++, track)
            entries.add(entry)
            originalOrder.add(entry.uid)
        }
        publish()
    }

    override fun startRadioSeamlessly() {
        addToQueue(FakeCatalog.radioTracks)
    }

    override fun setInfiniteQueueEnabled(enabled: Boolean) {
        infiniteQueue = enabled
        publish()
    }

    override fun playQueueItem(uid: Int) {
        val index = indexOfUid(uid)
        if (index < 0) return
        currentUid = entries[index].uid
        positionMs = 0L
        isPlaying = true
        playbackState = PlaybackConstants.STATE_READY
        publish()
    }

    override fun moveQueueItem(uid: Int, afterUid: Int) {
        val from = indexOfUid(uid)
        val after = indexOfUid(afterUid)
        if (from < 0 || after < 0 || from == after) return

        val moved = entries.removeAt(from)
        val insertAt = entries.indexOfFirst { it.uid == afterUid }
        if (insertAt < 0) {
            entries.add(moved)
            return
        }
        entries.add(insertAt + 1, moved)

        originalOrder.remove(uid)
        val originAt = originalOrder.indexOf(afterUid)
        originalOrder.add(if (originAt < 0) originalOrder.size else originAt + 1, uid)
        publish()
    }

    override fun playQueueItemNext(uid: Int) {
        val from = indexOfUid(uid)
        if (from < 0 || from == currentIndex()) return
        val original = entries[from]
        val moved = original.copy(track = original.track.copy(isAutoplay = false))
        entries.removeAt(from)
        val insertAt = currentIndex() + 1
        entries.add(insertAt.coerceIn(0, entries.size), moved)

        originalOrder.remove(uid)
        val originAt = currentUid?.let { originalOrder.indexOf(it) } ?: -1
        originalOrder.add(if (originAt < 0) originalOrder.size else originAt + 1, uid)
        publish()
    }

    override fun removeQueueItems(uids: Collection<Int>) {
        if (uids.isEmpty()) return
        val wanted = uids.toHashSet()
        val playing = currentUid
        // The playing song is never removed, matching upstream's queue page behavior.
        entries.removeAll { it.uid != playing && it.uid in wanted }
        originalOrder.removeAll { it != playing && it in wanted }
        publish()
    }

    // -----------------------------------------------------------------------------------------
    // Modes / audio
    // -----------------------------------------------------------------------------------------

    override fun setShuffleModeEnabled(enabled: Boolean) {
        if (shuffle == enabled) return
        shuffle = enabled
        if (enabled) applyShuffle() else restoreOriginalOrder()
        publish()
    }

    override fun setRepeatMode(mode: Int) {
        repeatMode = mode.coerceIn(PlaybackConstants.REPEAT_MODE_OFF, PlaybackConstants.REPEAT_MODE_ONE)
        publish()
    }

    override fun toggleRepeatMode() {
        repeatMode = when (repeatMode) {
            PlaybackConstants.REPEAT_MODE_OFF -> PlaybackConstants.REPEAT_MODE_ALL
            PlaybackConstants.REPEAT_MODE_ALL -> PlaybackConstants.REPEAT_MODE_ONE
            else -> PlaybackConstants.REPEAT_MODE_OFF
        }
        publish()
    }

    override fun setVolume(volume: Float) {
        volumeValue = volume.coerceIn(0f, 1f)
        publish()
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        publish()
    }

    override fun toggleMute() {
        muted = !muted
        publish()
    }

    override fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3f)
        this.speed = clamped
        _playbackSpeed.value = clamped
    }

    // -----------------------------------------------------------------------------------------
    // Song actions / lyrics / ancillary UI
    // -----------------------------------------------------------------------------------------

    private fun editCurrentMetadata(transform: (MediaMetadata) -> MediaMetadata) {
        val index = currentIndex()
        if (index < 0) return
        val entry = entries[index]
        entries[index] = entry.copy(track = entry.track.copy(metadata = transform(entry.track.metadata)))
        publish()
    }

    override fun toggleLike() {
        val metadata = _currentMetadata.value ?: return
        val liked = !metadata.liked
        editCurrentMetadata {
            it.copy(liked = liked, likedDate = if (liked) nowLocal() else null)
        }
        val facade = dependencies?.database
        val id = metadata.id
        if (facade != null) {
            controllerScope.launch {
                try {
                    facade.getSongById(id)?.let { song ->
                        facade.updateLike(
                            song.song.copy(liked = liked, likedDate = if (liked) nowLocal() else null)
                        )
                    }
                } catch (_: Exception) {
                    // Persistence is best-effort for the fake controller.
                }
            }
        }
    }

    override fun toggleLibrary() {
        val metadata = _currentMetadata.value ?: return
        val inLibrary = if (metadata.inLibrary == null) nowLocal() else null
        editCurrentMetadata { it.copy(inLibrary = inLibrary) }
        val facade = dependencies?.database
        val id = metadata.id
        if (facade != null) {
            controllerScope.launch {
                try {
                    facade.getSongById(id)?.let { facade.updateLibrary(it.song.copy(inLibrary = inLibrary)) }
                } catch (_: Exception) {
                    // Persistence is best-effort for the fake controller.
                }
            }
        }
    }

    override fun setLyricsOffsetMs(offsetMs: Int) {
        _lyricsOffsetMs.value = offsetMs
        val id = _currentMetadata.value?.id ?: return
        val facade = dependencies?.database ?: return
        controllerScope.launch {
            try {
                facade.setLyricsOffset(id, offsetMs)
            } catch (_: Exception) {
                // Persistence is best-effort for the fake controller.
            }
        }
    }

    override fun startSleepTimer(minutes: Int) {
        val durationMs = minutes.toLong() * 60_000L
        sleep = if (minutes < 0) {
            SleepTimerState(pauseAfterCurrentSong = true)
        } else {
            SleepTimerState(
                triggerAtMs = currentTimeMillis() + durationMs,
                remainingMs = durationMs,
            )
        }
        publish()
    }

    override fun clearSleepTimer() {
        sleep = SleepTimerState()
        publish()
    }

    override fun requestPlayerStudio() {
        _studioRequests.value++
    }

    override fun streamFacts(mediaId: String): Flow<StreamFacts?> =
        _allStreamFacts.map { it[mediaId] }.distinctUntilChanged()

    /**
     * Preview hooks: let a Compose preview drive error / buffering states without a real engine.
     */
    fun setError(error: com.example.musicfy.player.model.PlaybackError?) {
        playbackError = error
        publish()
    }

    fun reportStreamFailure(timeoutMs: Long) {
        streamFailureState = com.example.musicfy.player.model.StreamFailure(
            startedAtMs = currentTimeMillis(),
            timeoutMs = timeoutMs,
        )
        publish()
    }

    fun clearStreamFailure() {
        streamFailureState = null
        publish()
    }

    fun setWaitingForNetwork(waiting: Boolean) {
        waitingForNetwork = waiting
        publish()
    }

    fun setCrossmixing(crossmixing: Boolean) {
        this.crossmixing = crossmixing
        publish()
    }

    override fun dispose() {
        tickerJob?.cancel()
        tickerJob = null
        controllerScope.cancel()
    }
}
