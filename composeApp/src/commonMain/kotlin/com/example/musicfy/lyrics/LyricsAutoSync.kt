// LyricsAutoSync.kt
//
// KMP port: the service-side Media3 DataSource/StreamPcmDecoder is injected as
// [decodePcm], implemented by the iOS playback bridge later. The analyzer,
// alignment, status machine and persistence semantics are unchanged.

package com.example.musicfy.lyrics

import com.example.musicfy.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

class LyricsAutoSync(
    /** Supplies interleaved 16-bit PCM chunks from any song the app can read. */
    private val decodePcm: suspend (
        uri: String,
        cacheKey: String,
        maxSongUs: Long,
        onChunk: (pcm: ShortArray, channels: Int, sampleRate: Int, presentationUs: Long) -> Unit,
    ) -> Unit,
    private val saveOffset: (songId: String, offsetMs: Int) -> Unit,
    /** Told about a finished sync nobody was watching, so it can say so another way. */
    private val onUnwatchedResult: (State) -> Unit,
) {
    sealed interface State {
        val songId: String

        /** Decoding the song; [progress] 0..1. */
        data class Listening(override val songId: String, val progress: Float) : State

        /** Lining the lyrics up with what was heard. */
        data class Matching(override val songId: String) : State

        data class Synced(
            override val songId: String,
            val offsetMs: Int,
            val previousOffsetMs: Int,
            val sure: Boolean,
            val driftMs: Int?,
        ) : State

        data class Failed(override val songId: String, val message: String) : State
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    // listening must never compete with decoding the song that's playing
    // One sync job at a time; cancel() stops the previous one before a new job starts.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val heard = LinkedHashMap<String, VocalActivity>()

    /** Syncs [lines] for [songId]; [durationMs] drives progress, [previousOffsetMs] is kept for Undo. */
    fun start(
        songId: String,
        uri: String,
        cacheKey: String,
        durationMs: Long,
        lines: List<LyricsEntry>,
        previousOffsetMs: Int,
    ) {
        job?.cancel()
        _state.value = State.Listening(songId, 0f)
        job = scope.launch {
            val result = try {
                withTimeoutOrNull(TimeoutMs) {
                    sync(songId, uri, cacheKey, durationMs, lines, previousOffsetMs)
                } ?: State.Failed(songId, "Listening took too long. Check your connection and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                State.Failed(songId, "Couldn't listen to this song. Check your connection and try again.")
            }
            _state.value = result
            if (_state.subscriptionCount.value == 0) onUnwatchedResult(result)
        }
    }

    private suspend fun sync(
        songId: String,
        uri: String,
        cacheKey: String,
        durationMs: Long,
        lines: List<LyricsEntry>,
        previousOffsetMs: Int,
    ): State {
        val vocals = heard[cacheKey]
            ?: listen(songId, uri, cacheKey, durationMs)
            ?: return State.Failed(songId, "Too little of the song came through to hear the singing.")
        heard[cacheKey] = vocals
        while (heard.size > 3) heard.remove(heard.keys.first())

        _state.value = State.Matching(songId)
        val alignment = LyricsAligner().align(lines, vocals, MaxOffsetMs)
        if (alignment == null || alignment.confidence < MinConfidence) {
            return State.Failed(
                songId,
                "Couldn't match the singing to these lyrics. They may be for another version, or you can set it by hand.",
            )
        }
        saveOffset(songId, alignment.offsetMs)
        return State.Synced(
            songId = songId,
            offsetMs = alignment.offsetMs,
            previousOffsetMs = previousOffsetMs,
            sure = alignment.confidence >= SureConfidence,
            driftMs = alignment.driftMs,
        )
    }

    private suspend fun listen(
        songId: String,
        uri: String,
        cacheKey: String,
        durationMs: Long,
    ): VocalActivity? {
        val analyzer = VocalActivityAnalyzer()
        var shown = 0f
        decodePcm(uri, cacheKey, MaxSongUs) { pcm, channels, sampleRate, presentationUs ->
            analyzer.feed(pcm, channels, sampleRate, presentationUs)
            if (durationMs > 0) {
                val progress = (presentationUs / 1_000f / durationMs).coerceIn(0f, 1f)
                if (abs(progress - shown) >= 0.01f) {
                    shown = progress
                    _state.value = State.Listening(songId, progress)
                }
            }
        }
        // Give the UI a moment to draw the completed progress before matching.
        delay(50)
        return analyzer.finish()
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val MaxOffsetMs = 10_000
        const val MaxSongUs = 15 * 60_000_000L
        const val TimeoutMs = 120_000L
        const val MinConfidence = 0.3f
        const val SureConfidence = 0.55f
    }
}
