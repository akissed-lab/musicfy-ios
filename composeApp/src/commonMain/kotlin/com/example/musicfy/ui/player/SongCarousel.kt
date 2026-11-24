package com.example.musicfy.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import com.example.musicfy.player.PlaybackController
import com.example.musicfy.player.model.QueueState
import com.example.musicfy.player.model.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One song in the row, [step] its place relative to the playing song. */
@Immutable
data class CarouselSong(
    val uid: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val step: Int,
)

/** The player's row, for everything inside it; null outside the player. */
val LocalSongCarousel = staticCompositionLocalOf<SongCarousel?> { null }

/** Whether a background copy is allowed to fade. Kept for parity with the upstream selector. */
val LocalCrossfadeShown = compositionLocalOf<() -> Boolean> { { true } }

/**
 * A small, deterministic row of previous/current/next songs.
 *
 * The Android original ran a per-frame critically damped spring with drag/flick handling. The KMP
 * port keeps the same vocabulary (`step`, `position`, `offsetOf`, slots) and uses one coroutine-
 * driven settle, so covers, titles and backgrounds still read their place from a single object.
 */
@Stable
class SongCarousel internal constructor(private val scope: CoroutineScope) {
    /** The playing song's step. */
    var step: Int by mutableIntStateOf(0)
        private set

    /** Where the row is, in steps. Read while drawing or placing only. */
    var position: Float by mutableFloatStateOf(0f)
        private set

    /** The songs kept alive for the row, normally previous/current/next. */
    var slots: List<CarouselSong> by mutableStateOf(emptyList())
        internal set

    private var initialized = false

    internal fun update(queue: QueueState, track: TrackInfo) {
        val items = queue.items
        val targetStep = if (items.isEmpty()) 0 else queue.currentIndex.coerceIn(0, items.lastIndex)

        val nextSlots = if (items.isEmpty()) {
            listOf(
                CarouselSong(
                    uid = track.mediaId.hashCode(),
                    mediaId = track.mediaId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUrl = track.thumbnailUrl,
                    step = 0,
                ),
            )
        } else {
            val from = (targetStep - 1).coerceAtLeast(0)
            val to = (targetStep + 1).coerceAtMost(items.lastIndex)
            (from..to).map { index ->
                val item = items[index]
                CarouselSong(
                    uid = item.uid,
                    mediaId = item.mediaId,
                    title = item.title,
                    artist = item.artist,
                    album = "",
                    artworkUrl = item.artworkUri,
                    step = index - targetStep,
                )
            }
        }

        if (!initialized) {
            initialized = true
            slots = nextSlots
            step = targetStep
            position = targetStep.toFloat()
            return
        }

        slots = nextSlots
        if (targetStep != step) glideTo(targetStep)
    }

    /** Signed distance of [slot] from the row's current position, in songs. */
    fun offsetOf(slot: CarouselSong): Float = slot.step - position

    /** How much of a crossfade slot should show. */
    fun crossfadeAlpha(slot: CarouselSong): Float = (1f - abs(offsetOf(slot))).coerceIn(0f, 1f)

    internal fun glideTo(target: Int) {
        val from = position
        step = target
        scope.launch {
            Animatable(from).animateTo(
                targetValue = target.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ) { position = value }
        }
    }
}

/** Remembers one row and keeps it in step with the controller's queue/track flows. */
@Composable
fun rememberSongCarousel(controller: PlaybackController): SongCarousel {
    val scope = rememberCoroutineScope()
    val carousel = remember(scope) { SongCarousel(scope) }
    val queue by controller.queueState.collectAsState()
    val track by controller.trackInfo.collectAsState()

    LaunchedEffect(carousel, queue, track) {
        carousel.update(queue, track)
    }
    return carousel
}

/** The live slots for the title row. */
@Composable
fun rememberTextSlots(carousel: SongCarousel): List<CarouselSong> = carousel.slots

/** Places a title/artist copy at its row position. */
fun Modifier.carouselText(carousel: SongCarousel, slot: CarouselSong): Modifier = graphicsLayer {
    val offset = carousel.offsetOf(slot)
    translationX = offset * size.width
    alpha = (1f - abs(offset) * 0.65f).coerceIn(0f, 1f)
}

/** Covers, one per slot, sliding under the finger as one row. */
@Composable
fun CarouselPictures(
    carousel: SongCarousel?,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
    parallax: Float = 0.3f,
    picture: @Composable BoxScope.(url: String?, modifier: Modifier) -> Unit,
) {
    Box(modifier = modifier.clipToBounds()) {
        val slots = carousel?.slots.orEmpty()
        if (carousel == null || slots.isEmpty()) {
            picture(fallbackUrl, Modifier.fillMaxSize())
            return@Box
        }
        for (slot in slots) {
            key(slot.uid) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val offset = carousel.offsetOf(slot)
                            alpha = if (abs(offset) >= 1f) 0f else 1f
                            translationX = offset * size.width
                            clip = true
                        },
                ) {
                    picture(
                        slot.artworkUrl,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = -carousel.offsetOf(slot) * size.width * parallax },
                    )
                }
            }
        }
    }
}

/** Backgrounds that crossfade along the row, one per song. */
@Composable
fun CarouselCrossfade(
    carousel: SongCarousel?,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.(url: String?) -> Unit,
) {
    val shown = LocalCrossfadeShown.current
    Box(modifier = modifier) {
        val slots = carousel?.slots.orEmpty()
        if (carousel == null || slots.isEmpty()) {
            background(fallbackUrl)
            return@Box
        }
        for (slot in slots) {
            key(slot.uid) {
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (shown()) carousel.crossfadeAlpha(slot) else 0f
                        },
                ) {
                    background(slot.artworkUrl)
                }
            }
        }
    }
}
