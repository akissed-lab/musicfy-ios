// AvFoundationPlaybackController.ios.kt

package com.example.musicfy.player

import androidx.compose.runtime.Stable
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.models.PersistPlayerState
import com.example.musicfy.player.fake.FakeCatalog
import com.example.musicfy.player.model.PlaybackError
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.PlayerState
import com.example.musicfy.player.model.ProgressState
import com.example.musicfy.player.model.QueueItemData
import com.example.musicfy.player.model.QueueState
import com.example.musicfy.player.model.SleepTimerState
import com.example.musicfy.player.model.StreamFailure
import com.example.musicfy.player.model.StreamFacts
import com.example.musicfy.player.model.TrackInfo
import com.example.musicfy.player.model.TrackSource
import com.example.musicfy.player.model.TransportState
import com.example.musicfy.player.queue.PlaybackQueue
import com.example.musicfy.util.currentTimeMillis
import com.example.musicfy.util.nowLocal
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
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
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Real iOS playback engine: a single [AVPlayer] whose current item is replaced from the shared
 * uid-addressed queue.
 *
 * Why not [AVQueuePlayer]: remote items need asynchronous stream resolution, while shuffle,
 * repeat-one and uid queue edits need the current item replaced out of band. Owning the queue in
 * Kotlin and feeding one item at a time keeps the exact [PlaybackController] contract; gapless
 * prefetch can be layered on later without changing the interface.
 *
 * Position/duration come from CMTime and advance from an AVPlayer periodic-style 66 ms poll, so
 * the ported progress UI keeps the same cadence as the in-memory preview controller.
 */
@Stable
@OptIn(ExperimentalForeignApi::class, ExperimentalCoroutinesApi::class)
class AvFoundationPlaybackController(
    private val scope: CoroutineScope,
    private val dependencies: PlaybackDependencies,
) : PlaybackController {
    private data class QueueEntry(val uid: Int, val track: PlaybackTrack)

    /** Child scope: [dispose] stops the ticker, resolution and queue work started here. */
    private val controllerScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])
    )

    private val initialState: PersistPlayerState? = dependencies.initialState
    private val player = AVPlayer()
    private val audioSession = AVAudioSession.sharedInstance()
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
    private var playerError: PlaybackError? = null
    private var streamFailureState: StreamFailure? = null
    private var waitingForNetwork = false
    private var crossmixing = false
    private var infiniteQueue = true

    /** Guards late stream-resolution results against a newer track selection. */
    private var loadGeneration = 0L
    private var resolveJob: Job? = null
    private var tickerJob: Job? = null
    private var disposed = false
    private var resumeAfterInterruption = false
    private val notificationTokens = mutableListOf<NSObjectProtocol>()

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
                else dependencies.database.lyrics(id)
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

    /** Extra hook for the ported player studio, matching the preview controller. */
    val studioRequests: StateFlow<Long> get() = _studioRequests

    /** Extra hook for the ported playback-speed sheet. */
    val playbackSpeed: StateFlow<Float> get() = _playbackSpeed

    /**
     * Convenience projection of [PlayerState.error]. The shared [PlaybackController] interface has
     * no dedicated error flow; stream failures are published into the aggregate state.
     */
    val error: StateFlow<PlaybackError?> = _playerState
        .map { it.error }.distinctUntilChanged()
        .stateIn<PlaybackError?>(controllerScope, SharingStarted.Eagerly, null)

    init {
        // Configure the category only. Activating the session is deferred to the first play
        // command so a launch-time AVAudioSession failure cannot kill the app before the UI.
        configureAudioSession()
        applyAudioSettingsToPlayer()
        observeAudioSessionNotifications()
        startTicker()
        publish()
    }

    // -----------------------------------------------------------------------------------------
    // AVFoundation plumbing
    // -----------------------------------------------------------------------------------------

    private fun configureAudioSession() {
        runCatching {
            val ok = audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)
            if (!ok) {
                playerError = PlaybackError(
                    message = "Audio session could not be configured",
                    canRetry = false,
                )
            }
        }.onFailure {
            playerError = PlaybackError(
                message = "Audio session configuration threw: ${it.message.orEmpty()}",
                canRetry = false,
            )
        }
    }

    private fun activateAudioSession(): Boolean = runCatching {
        audioSession.setActive(true, error = null)
        true
    }.getOrElse {
        playerError = PlaybackError(
            message = "Audio session activation threw: ${it.message.orEmpty()}",
            canRetry = true,
        )
        false
    }

    private fun deactivateAudioSession() {
        runCatching { audioSession.setActive(false, error = null) }
    }

    private fun applyAudioSettingsToPlayer() {
        player.volume = volumeValue
        player.muted = muted
        // End-of-track is handled by handleTrackEnd() so repeat/shuffle stay in shared Kotlin.
        player.actionAtItemEnd = AVPlayerActionAtItemEndNone
    }

    private fun playNative() {
        if (disposed) return
        if (activateAudioSession()) player.rate = speed
    }

    private fun pauseNative() {
        if (!disposed) player.rate = 0f
    }

    private fun observeAudioSessionNotifications() {
        runCatching {
            val center = NSNotificationCenter.defaultCenter
            notificationTokens += center.addObserverForName(
                AVAudioSessionInterruptionNotification,
                null,
                null,
            ) { note -> handleAudioInterruption(note) }
            // Route changes (headphones, AirPlay) need no queue work; just make sure the session is
            // still active. Full route/remote-command handling belongs to a later stage.
            notificationTokens += center.addObserverForName(
                AVAudioSessionRouteChangeNotification,
                null,
                null,
            ) { activateAudioSession() }
        }.onFailure {
            playerError = PlaybackError(
                message = "Audio session observer failed: ${it.message.orEmpty()}",
                canRetry = false,
            )
        }
    }

    private fun handleAudioInterruption(note: NSNotification?) {
        val info = note?.userInfo()
        val rawType = (info?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
        when (rawType) {
            AVAudioSessionInterruptionTypeBegan -> {
                resumeAfterInterruption = isPlaying
                if (isPlaying) {
                    isPlaying = false
                    pauseNative()
                    publish()
                }
            }

            AVAudioSessionInterruptionTypeEnded -> {
                val options = (info?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)
                    ?.unsignedLongValue ?: 0uL
                val shouldResume = (options and AVAudioSessionInterruptionOptionShouldResume) != 0uL
                activateAudioSession()
                if (resumeAfterInterruption && shouldResume) {
                    resumeAfterInterruption = false
                    isPlaying = true
                    playNative()
                    publish()
                }
            }
        }
    }

    private fun nativePositionMs(): Long? = try {
        val seconds = CMTimeGetSeconds(player.currentTime())
        if (seconds.isFinite() && seconds >= 0.0) (seconds * 1_000.0).toLong() else null
    } catch (_: Throwable) {
        null
    }

    private fun nativeDurationMs(): Long? = try {
        val item = player.currentItem ?: return null
        val seconds = CMTimeGetSeconds(item.duration)
        if (seconds.isFinite() && seconds > 0.0) (seconds * 1_000.0).toLong() else null
    } catch (_: Throwable) {
        null
    }

    private fun inspectNativeFailure() {
        val item = try { player.currentItem } catch (_: Throwable) { null } ?: return
        if (item.status != AVPlayerItemStatusFailed) return
        val detail = item.error?.localizedDescription
        val message = detail ?: "AVFoundation could not play “${currentEntry()?.track?.metadata?.title.orEmpty()}”"
        if (playerError?.message == message) return
        playerError = PlaybackError(
            message = message,
            cause = detail,
            isNetwork = true,
            canRetry = true,
        )
        isPlaying = false
        playbackState = PlaybackConstants.STATE_IDLE
        pauseNative()
        publish()
    }

    private fun makeUrl(value: String): NSURL? = try {
        when {
            value.startsWith("/") -> NSURL(fileURLWithPath = value, isDirectory = false)
            value.contains("://") -> NSURL(string = value)
            else -> NSURL(fileURLWithPath = value, isDirectory = false)
        }
    } catch (_: Throwable) {
        null
    }

    private fun isCurrent(generation: Long, uid: Int?): Boolean =
        !disposed && loadGeneration == generation && currentUid == uid

    private fun currentEntry(): QueueEntry? {
        val uid = currentUid ?: return null
        return entries.firstOrNull { it.uid == uid }
    }

    /** Replace the native item for the current uid. Remote sources resolve in a child coroutine. */
    private fun loadCurrentItem(startPosition: Long) {
        if (disposed) return
        resolveJob?.cancel()
        val generation = ++loadGeneration
        val uid = currentUid
        val entry = currentEntry()

        // Clear the old item immediately: a late end-of-track tick must not fire while a
        // remote stream is being resolved, and stale audio must not keep playing.
        player.replaceCurrentItemWithPlayerItem(null)
        positionMs = startPosition.coerceAtLeast(0L)
        playerError = null
        streamFailureState = null
        waitingForNetwork = false

        if (entry == null || uid == null) {
            isPlaying = false
            playbackState = PlaybackConstants.STATE_IDLE
            publish()
            return
        }

        playbackState = PlaybackConstants.STATE_BUFFERING
        publish()

        when (val source = entry.track.source) {
            is TrackSource.Local -> {
                // A local file is already playable; no resolver and no headers.
                startItem(source.uri, positionMs, generation, uid)
            }

            is TrackSource.Direct -> {
                // TODO: custom headers and DRM hints need an AVAssetResourceLoaderDelegate.
                startItem(source.url, positionMs, generation, uid)
            }

            is TrackSource.Remote -> {
                waitingForNetwork = true
                publish()
                resolveJob = controllerScope.launch {
                    try {
                        val resolved = dependencies.streamResolver.resolve(entry.track)
                        if (!isCurrent(generation, uid)) return@launch
                        waitingForNetwork = false
                        // TODO: resolved.headers / resolved.drm need resource-loader support.
                        startItem(resolved.url, positionMs, generation, uid)
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        if (!isCurrent(generation, uid)) return@launch
                        waitingForNetwork = false
                        val detail = t.message ?: "stream resolution failed"
                        playerError = PlaybackError(
                            message = "Could not load “${entry.track.metadata.title}”: $detail",
                            cause = detail,
                            isNetwork = true,
                            canRetry = true,
                        )
                        isPlaying = false
                        playbackState = PlaybackConstants.STATE_IDLE
                        publish()
                    }
                }
            }
        }
    }

    private fun startItem(url: String, startPosition: Long, generation: Long, uid: Int) {
        if (!isCurrent(generation, uid)) return
        if (url.isBlank()) {
            reportItemError("No playable URL for “${currentEntry()?.track?.metadata?.title.orEmpty()}”")
            return
        }
        val nsUrl = makeUrl(url)
        if (nsUrl == null) {
            reportItemError("Invalid media URL: $url")
            return
        }
        val item = try {
            AVPlayerItem(nsUrl)
        } catch (t: Throwable) {
            reportItemError(t.message ?: "Could not create an AVPlayerItem")
            return
        }
        if (!isCurrent(generation, uid)) return

        player.replaceCurrentItemWithPlayerItem(item)
        applyAudioSettingsToPlayer()
        if (startPosition > 0L) {
            player.seekToTime(CMTimeMake(startPosition, 1_000))
        }
        playbackState = PlaybackConstants.STATE_READY
        waitingForNetwork = false
        if (isPlaying) playNative() else pauseNative()
        publish()
    }

    private fun reportItemError(message: String) {
        playerError = PlaybackError(message = message, canRetry = true)
        isPlaying = false
        playbackState = PlaybackConstants.STATE_IDLE
        publish()
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
        val duration = nativeDurationMs()?.takeIf { it > 0L } ?: durationOf(track)
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
            error = playerError,
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
                pauseNative()
                sleep = SleepTimerState()
                changed = true
            } else if (remaining != sleep.remainingMs) {
                sleep = sleep.copy(remainingMs = remaining)
                changed = true
            }
        }

        if (entries.isNotEmpty()) inspectNativeFailure()

        if (isPlaying && playbackState != PlaybackConstants.STATE_BUFFERING &&
            playbackState != PlaybackConstants.STATE_ENDED && entries.isNotEmpty()
        ) {
            val hasNativeItem = try { player.currentItem != null } catch (_: Throwable) { false }
            val duration = nativeDurationMs()?.takeIf { it > 0L }
                ?: durationOf(entries.getOrNull(currentIndex())?.track)
            val position = if (hasNativeItem) {
                nativePositionMs() ?: positionMs
            } else {
                positionMs + (deltaMs * speed).toLong().coerceAtLeast(0L)
            }
            if (duration > 0L && position >= duration) {
                handleTrackEnd()
                changed = true
            } else if (position != positionMs) {
                positionMs = position
                changed = true
            }
        }

        if (changed) publish()
    }

    private fun handleTrackEnd() {
        if (sleep.pauseAfterCurrentSong) {
            isPlaying = false
            pauseNative()
            sleep = SleepTimerState()
            return
        }
        if (repeatMode == PlaybackConstants.REPEAT_MODE_ONE) {
            positionMs = 0L
            try {
                player.seekToTime(CMTimeMake(0L, 1_000))
            } catch (_: Throwable) {
                // The next publish() will still reset the UI position.
            }
            return
        }
        val next = currentIndex() + 1
        when {
            next < entries.size -> {
                currentUid = entries[next].uid
                positionMs = 0L
                playbackState = PlaybackConstants.STATE_READY
                loadCurrentItem(0L)
            }

            repeatMode == PlaybackConstants.REPEAT_MODE_ALL && entries.isNotEmpty() -> {
                currentUid = entries.first().uid
                positionMs = 0L
                playbackState = PlaybackConstants.STATE_READY
                loadCurrentItem(0L)
            }

            else -> {
                isPlaying = false
                playbackState = PlaybackConstants.STATE_ENDED
                positionMs = durationOf(entries.getOrNull(currentIndex())?.track)
                pauseNative()
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Transport
    // -----------------------------------------------------------------------------------------

    override fun play() {
        if (disposed) return
        if (entries.isEmpty()) {
            // Unlike the preview controller, the native engine does not fabricate a demo queue.
            isPlaying = false
            playbackState = PlaybackConstants.STATE_IDLE
            publish()
            return
        }
        isPlaying = true
        val hasNativeItem = try { player.currentItem != null } catch (_: Throwable) { false }
        if (!hasNativeItem && playbackState != PlaybackConstants.STATE_BUFFERING) {
            loadCurrentItem(positionMs)
        } else {
            if (playbackState == PlaybackConstants.STATE_ENDED) positionMs = 0L
            playbackState = PlaybackConstants.STATE_READY
            playNative()
            publish()
        }
    }

    override fun pause() {
        isPlaying = false
        pauseNative()
        publish()
    }

    override fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    override fun seekTo(position: Long) {
        positionMs = position.coerceAtLeast(0L)
        try {
            if (player.currentItem != null) {
                player.seekToTime(CMTimeMake(positionMs, 1_000))
            } else if (entries.isNotEmpty()) {
                loadCurrentItem(positionMs)
            }
        } catch (_: Throwable) {
            if (entries.isNotEmpty()) loadCurrentItem(positionMs)
        }
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
        loadCurrentItem(positionMs)
    }

    override fun seekToNext() {
        if (entries.isEmpty()) return
        if (repeatMode == PlaybackConstants.REPEAT_MODE_ONE) {
            positionMs = 0L
            try {
                player.seekToTime(CMTimeMake(0L, 1_000))
            } catch (_: Throwable) {
                // Covered by the next publish().
            }
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
        loadCurrentItem(0L)
    }

    override fun seekToPrevious() {
        if (entries.isEmpty()) return
        if (positionMs > 3_000L || (currentIndex() <= 0 && repeatMode == PlaybackConstants.REPEAT_MODE_OFF)) {
            positionMs = 0L
            try {
                player.seekToTime(CMTimeMake(0L, 1_000))
            } catch (_: Throwable) {
                // Covered by the next publish().
            }
            publish()
            return
        }
        val target = if (currentIndex() > 0) currentIndex() - 1
        else if (repeatMode == PlaybackConstants.REPEAT_MODE_ALL) entries.lastIndex
        else 0
        currentUid = entries[target].uid
        positionMs = 0L
        playbackState = PlaybackConstants.STATE_READY
        loadCurrentItem(0L)
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
            loadCurrentItem(positionMs)
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
        // No radio continuation engine in this stage. Unlike the in-memory preview, do not append
        // FakeCatalog's unresolved remote ids to a real native queue; they have no playable URL.
        publish()
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
        loadCurrentItem(0L)
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
        player.volume = volumeValue
        publish()
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        player.muted = muted
        publish()
    }

    override fun toggleMute() {
        setMuted(!muted)
    }

    override fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3f)
        this.speed = clamped
        _playbackSpeed.value = clamped
        if (isPlaying) playNative()
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
        val facade = dependencies.database
        val id = metadata.id
        controllerScope.launch {
            try {
                facade.getSongById(id)?.let { song ->
                    facade.updateLike(
                        song.song.copy(liked = liked, likedDate = if (liked) nowLocal() else null)
                    )
                }
            } catch (_: Exception) {
                // Persistence is best-effort, as in the preview controller.
            }
        }
    }

    override fun toggleLibrary() {
        val metadata = _currentMetadata.value ?: return
        val inLibrary = if (metadata.inLibrary == null) nowLocal() else null
        editCurrentMetadata { it.copy(inLibrary = inLibrary) }
        val facade = dependencies.database
        val id = metadata.id
        controllerScope.launch {
            try {
                facade.getSongById(id)?.let { facade.updateLibrary(it.song.copy(inLibrary = inLibrary)) }
            } catch (_: Exception) {
                // Persistence is best-effort, as in the preview controller.
            }
        }
    }

    override fun setLyricsOffsetMs(offsetMs: Int) {
        _lyricsOffsetMs.value = offsetMs
        val id = _currentMetadata.value?.id ?: return
        val facade = dependencies.database
        controllerScope.launch {
            try {
                facade.setLyricsOffset(id, offsetMs)
            } catch (_: Exception) {
                // Persistence is best-effort, as in the preview controller.
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

    /** Preview/debug hooks matching the in-memory controller's extra surface. */
    fun setError(value: PlaybackError?) {
        playerError = value
        publish()
    }

    fun reportStreamFailure(timeoutMs: Long) {
        streamFailureState = StreamFailure(startedAtMs = currentTimeMillis(), timeoutMs = timeoutMs)
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

    fun setCrossmixing(enabled: Boolean) {
        crossmixing = enabled
        publish()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        resolveJob?.cancel()
        resolveJob = null
        tickerJob?.cancel()
        tickerJob = null
        try {
            notificationTokens.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        } catch (_: Throwable) {
            // Best effort; the process is tearing the player down.
        }
        notificationTokens.clear()
        try {
            player.rate = 0f
            player.replaceCurrentItemWithPlayerItem(null)
        } catch (_: Throwable) {
            // Best effort.
        }
        deactivateAudioSession()
        controllerScope.cancel()
    }
}
