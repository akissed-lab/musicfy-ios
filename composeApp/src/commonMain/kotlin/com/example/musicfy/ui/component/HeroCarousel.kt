package com.example.musicfy.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.example.musicfy.db.entities.LocalItem
import com.example.musicfy.db.entities.Song
import com.example.musicfy.models.ArtistGroup
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.PlaybackController
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.TrackSource
import com.example.musicfy.player.model.TransportState
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.arrow_back
import com.example.musicfy.resources.arrow_forward
import com.example.musicfy.resources.ic_untitled_pause
import com.example.musicfy.resources.ic_untitled_play
import com.example.musicfy.resources.queue_music
import com.example.musicfy.resources.shuffle
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

/** Upstream's hero share of the screen height, kept as part of the ported API. */
const val HeroHeightFraction = 0.6f

/**
 * CommonMain has no stable screen-height accessor, so the simplified carousel
 * uses a fixed glass stage instead of `LocalConfiguration.screenHeightDp`.
 */
private val HeroStageHeight = 420.dp
private const val HeroAdvanceMillis = 5500L

/** The small line above a hero title: [text], then a tiny [coverUrl] and its [subject]. */
@Immutable
data class HeroLabel(
    val text: String,
    val coverUrl: String? = null,
    val subject: String? = null,
    val roundCover: Boolean = false,
)

/** Why a recommendation is on the hero. */
enum class DiscoverReason { YouMayAlsoLike, BecauseYouLike, MoreFrom, BecauseYouPlayed, InspiredBy }

/**
 * Plain, common-safe projection of a playable item. Upstream used YTItem and
 * Android MediaMetadata directly; both are replaced by immutable local data so
 * the carousel can be driven from the database facade and [FakeCatalog].
 */
@Immutable
data class HeroTrack(
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val duration: Int = 0,
    val albumTitle: String? = null,
    val localUri: String? = null,
)

/** A daily-discover card plus the song it grew out of. */
@Immutable
data class DailyDiscoverItem(
    val recommendation: HeroTrack,
    val seedTitle: String,
    val seedThumbnailUrl: String? = null,
    val seedArtists: List<String> = emptyList(),
    val seedLiked: Boolean = false,
)

/** Common replacement for the Android `HeroCarouselItem` interface. */
sealed interface HeroCarouselItem {
    val mainText: String
    val subText: String
    val thumbnailUrl: String?
    val mediaId: String?
    val songTitle: String?
    val artistName: String?
    val albumTitle: String?

    /** Albums, artists and playlists open a page instead of playing straight away. */
    val opensPage: Boolean get() = false

    fun label(isPlaying: Boolean = false): HeroLabel
    fun onPlay(playerConnection: PlaybackController?, navController: NavHostController)
}

/** The song that was playing last, or one from Keep Listening. */
@Immutable
data class KeepListeningItem(
    val item: MediaMetadata,
    val isLastPlayed: Boolean = false,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel(
        when {
            isPlaying -> "Now playing"
            isLastPlayed -> "Continue"
            else -> "Recently played"
        },
    )
    override val mainText: String get() = item.title
    override val subText: String get() = item.artists.joinToString(", ") { it.name }
    override val thumbnailUrl: String? get() = item.thumbnailUrl
    override val mediaId: String? get() = item.id
    override val songTitle: String? get() = item.title
    override val artistName: String? get() = item.artists.joinToString { it.name }
    override val albumTitle: String? get() = item.album?.title

    override fun onPlay(playerConnection: PlaybackController?, navController: NavHostController) {
        if (isLastPlayed) playerConnection?.play() else playMetadata(item, playerConnection)
    }
}

/** A daily recommendation, labelled with the song it grew out of. */
@Immutable
data class DiscoverItem(
    val item: DailyDiscoverItem,
    val reason: DiscoverReason = DiscoverReason.YouMayAlsoLike,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel {
        if (isPlaying) return HeroLabel("Now playing")
        val seed = item
        return when (reason) {
            DiscoverReason.YouMayAlsoLike -> HeroLabel("You may also like")
            DiscoverReason.BecauseYouLike -> HeroLabel("Because you like", seed.seedThumbnailUrl, seed.seedTitle)
            DiscoverReason.MoreFrom -> HeroLabel("More from", seed.seedThumbnailUrl, sharedArtist(seed) ?: seed.seedTitle)
            DiscoverReason.BecauseYouPlayed -> HeroLabel("Because you played", seed.seedThumbnailUrl, seed.seedTitle)
            DiscoverReason.InspiredBy -> HeroLabel("Inspired by", seed.seedThumbnailUrl, seed.seedTitle)
        }
    }
    override val mainText: String get() = item.recommendation.title
    override val subText: String get() = item.recommendation.artists.joinToString(", ")
    override val thumbnailUrl: String? get() = item.recommendation.thumbnailUrl
    override val mediaId: String? get() = item.recommendation.id
    override val songTitle: String? get() = item.recommendation.title
    override val artistName: String? get() = item.recommendation.artists.joinToString()
    override val albumTitle: String? get() = item.recommendation.albumTitle

    override fun onPlay(playerConnection: PlaybackController?, navController: NavHostController) =
        playTrack(item.recommendation, playerConnection)
}

/** A pick from one of Home's related rows, worded by what it grew out of. */
@Immutable
data class RelatedPickItem(
    val recommendation: HeroTrack,
    val reason: HeroLabel,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = if (isPlaying) HeroLabel("Now playing") else reason
    override val mainText: String get() = recommendation.title
    override val subText: String get() = recommendation.artists.joinToString(", ")
    override val thumbnailUrl: String? get() = recommendation.thumbnailUrl
    override val mediaId: String? get() = recommendation.id
    override val songTitle: String? get() = recommendation.title
    override val artistName: String? get() = recommendation.artists.joinToString()
    override val albumTitle: String? get() = recommendation.albumTitle

    override fun onPlay(playerConnection: PlaybackController?, navController: NavHostController) =
        playTrack(recommendation, playerConnection)
}

/** One of the user's own songs brought back with a reason of its own. */
@Immutable
data class ListeningPickItem(
    val item: MediaMetadata,
    val reason: String,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel(if (isPlaying) "Now playing" else reason)
    override val mainText: String get() = item.title
    override val subText: String get() = item.artists.joinToString(", ") { it.name }
    override val thumbnailUrl: String? get() = item.thumbnailUrl
    override val mediaId: String? get() = item.id
    override val songTitle: String? get() = item.title
    override val artistName: String? get() = item.artists.joinToString { it.name }
    override val albumTitle: String? get() = item.album?.title

    override fun onPlay(playerConnection: PlaybackController?, navController: NavHostController) =
        playMetadata(item, playerConnection)
}

/** Simplified "Random plays" card. The reel is replaced by a glass pill and a random pick. */
data object GachaItem : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel("Gacha here!")
    override val mainText: String = "Random plays"
    override val subText: String = ""
    override val thumbnailUrl: String? = null
    override val mediaId: String? = null
    override val songTitle: String? = null
    override val artistName: String? = null
    override val albumTitle: String? = null

    override fun onPlay(playerConnection: PlaybackController?, navController: NavHostController) = Unit
}

/** One of the user's own playlists, put forward like any other recommendation. */
class PlaylistPickItem(
    val id: String,
    val name: String,
    override val thumbnailUrl: String?,
    private val play: () -> Unit,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel("From your playlists")
    override val mainText: String get() = name
    override val subText: String = ""
    override val mediaId: String? = null
    override val songTitle: String? = null
    override val artistName: String? = null
    override val albumTitle: String? = null

    override fun onPlay(playerConnection: PlaybackController?, navController: NavHostController) = play()

    override fun equals(other: Any?): Boolean = other is PlaylistPickItem && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/** A brand-new install's hero card: imported playlists or onboarding artists. */
@Immutable
data class StarterItem(
    val kind: Kind,
    val id: String,
    val title: String,
    val detail: String,
    override val thumbnailUrl: String?,
) : HeroCarouselItem {
    enum class Kind { Playlist, Artist }

    override fun label(isPlaying: Boolean): HeroLabel =
        HeroLabel(if (kind == Kind.Playlist) "From your import" else "Recommended from")
    override val mainText: String get() = title
    override val subText: String get() = detail
    override val mediaId: String? = null
    override val songTitle: String? = null
    override val artistName: String? = null
    override val albumTitle: String? = null
    override val opensPage: Boolean = true

    override fun onPlay(playerConnection: PlaybackController?, navController: NavHostController) {
        navController.navigate(if (kind == Kind.Playlist) "local_playlist/$id" else "artist/$id")
    }
}

/** A new install's hero card: a song by one of the artists picked in onboarding. */
@Immutable
data class StarterSongItem(
    val song: HeroTrack,
    val pickedArtist: String,
    val pickedArtistThumbnail: String?,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel =
        if (isPlaying) HeroLabel("Now playing")
        else HeroLabel("Recommended from", pickedArtistThumbnail, pickedArtist, roundCover = true)
    override val mainText: String get() = song.title
    override val subText: String get() = song.artists.joinToString(", ")
    override val thumbnailUrl: String? get() = song.thumbnailUrl
    override val mediaId: String? get() = song.id
    override val songTitle: String? get() = song.title
    override val artistName: String? get() = song.artists.joinToString()
    override val albumTitle: String? get() = song.albumTitle

    override fun onPlay(playerConnection: PlaybackController?, navController: NavHostController) =
        playTrack(song, playerConnection)
}

/** Convert a local hero track into a controller-ready queue item. */
fun HeroTrack.toPlaybackTrack(): PlaybackTrack {
    val uri = localUri
    return PlaybackTrack(
        mediaId = id,
        metadata = MediaMetadata(
            id = id,
            title = title,
            artists = artists.map { MediaMetadata.Artist(id = null, name = it) },
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            album = albumTitle?.let { MediaMetadata.Album(id = "${id}_album", title = it) },
            localUri = uri,
        ),
        source = if (uri != null) TrackSource.Local(uri) else TrackSource.Remote(id),
    )
}

/** Convert an Innertube item into the hero's common track projection. */
fun YTItem.toHeroTrack(): HeroTrack = HeroTrack(
    id = id,
    title = title,
    artists = when (this) {
        is SongItem -> artists.map { it.name }
        is AlbumItem -> artists.orEmpty().map { it.name }
        is PlaylistItem -> listOfNotNull(author?.name)
        is ArtistItem -> listOf(title)
        else -> emptyList()
    },
    thumbnailUrl = thumbnail,
    duration = if (this is SongItem) duration ?: 0 else 0,
    albumTitle = when (this) {
        is SongItem -> album?.name
        is AlbumItem -> title
        else -> null
    },
)

private fun MediaMetadata.toPlaybackTrack(): PlaybackTrack {
    val uri = localUri
    return PlaybackTrack(
        mediaId = id,
        metadata = this,
        source = if (uri != null) TrackSource.Local(uri) else TrackSource.Remote(id),
    )
}

private fun playMetadata(metadata: MediaMetadata, controller: PlaybackController?) {
    controller?.playQueue(ListQueue(title = metadata.title, items = listOf(metadata.toPlaybackTrack())))
}

private fun playTrack(track: HeroTrack, controller: PlaybackController?) {
    controller?.playQueue(ListQueue(title = track.title, items = listOf(track.toPlaybackTrack())))
}

private fun Song.toHeroMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
    duration = song.duration,
    thumbnailUrl = thumbnailUrl,
    album = album?.let { MediaMetadata.Album(id = it.id, title = it.title) },
    explicit = song.explicit,
    liked = song.liked,
    localUri = song.localUri,
)

private fun sharedArtist(item: DailyDiscoverItem): String? =
    item.recommendation.artists.firstOrNull { name ->
        item.seedArtists.any { it.equals(name, ignoreCase = true) }
    }

/** Wording for each recommendation, never repeating a phrase more than twice in a row. */
private fun discoverReasons(items: List<DailyDiscoverItem>): List<DiscoverReason> {
    val picked = ArrayList<DiscoverReason>(items.size)
    items.forEachIndexed { index, item ->
        val options = buildList {
            if (index == 0) add(DiscoverReason.YouMayAlsoLike)
            if (sharedArtist(item) != null) add(DiscoverReason.MoreFrom)
            if (item.seedLiked) add(DiscoverReason.BecauseYouLike)
            add(DiscoverReason.BecauseYouPlayed)
            add(DiscoverReason.InspiredBy)
            add(DiscoverReason.YouMayAlsoLike)
        }
        picked += options.firstOrNull { option ->
            option != picked.lastOrNull() && picked.count { it == option } < 2
        } ?: DiscoverReason.YouMayAlsoLike
    }
    return picked
}

private fun extraHeroPicks(
    similar: List<ArtistGroup>?,
    onRepeat: List<Song>?,
    forgotten: List<Song>?,
): List<HeroCarouselItem> = buildList {
    similar.orEmpty().take(4).forEach { group ->
        val pick = group.items.firstOrNull { !it.thumbnail.isNullOrEmpty() } ?: return@forEach
        add(
            RelatedPickItem(
                recommendation = pick.toHeroTrack(),
                reason = HeroLabel(
                    "Because you listen to",
                    group.artistThumbnailUrl,
                    group.artistName,
                    roundCover = true,
                ),
            ),
        )
    }
    onRepeat?.firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.let {
        add(ListeningPickItem(it.toHeroMetadata(), "On repeat"))
    }
    forgotten?.firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.let {
        add(ListeningPickItem(it.toHeroMetadata(), "Back in rotation"))
    }
}

private fun <T> interleave(a: List<T>, b: List<T>): List<T> = buildList(a.size + b.size) {
    for (i in 0 until maxOf(a.size, b.size)) {
        a.getOrNull(i)?.let(::add)
        b.getOrNull(i)?.let(::add)
    }
}

/**
 * The real Home hero, simplified for commonMain: a glass stage, a four-second
 * discover reel, and one glass play pill. Shader blurs, gacha reels, Canvas
 * videos and Android media APIs are deliberately absent; the public name and
 * the item contracts are preserved.
 */
@Composable
fun HeroCarousel(
    keepListening: List<LocalItem>? = null,
    lastPlayedSong: MediaMetadata? = null,
    dailyDiscover: List<DailyDiscoverItem>? = null,
    playerConnection: PlaybackController? = null,
    navController: NavHostController,
    heroScrollProgressProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    hold: Boolean = false,
    gachaPool: List<MediaMetadata> = emptyList(),
    starterItems: List<HeroCarouselItem> = emptyList(),
    playlistPicks: List<PlaylistPickItem> = emptyList(),
    similarRecommendations: List<ArtistGroup>? = null,
    onRepeat: List<Song>? = null,
    forgottenFavorites: List<Song>? = null,
) {
    val showGacha = gachaPool.size >= 5
    val extras = remember(
        similarRecommendations?.map { it.artistId to it.items.firstOrNull()?.id },
        onRepeat?.firstOrNull()?.id,
        forgottenFavorites?.firstOrNull()?.id,
    ) { extraHeroPicks(similarRecommendations, onRepeat, forgottenFavorites) }

    val carouselItems = remember(
        keepListening,
        lastPlayedSong,
        dailyDiscover,
        showGacha,
        starterItems,
        playlistPicks,
        extras,
    ) {
        buildList {
            lastPlayedSong?.let { add(KeepListeningItem(it, isLastPlayed = true)) }
            if (showGacha) add(GachaItem)

            val discover = dailyDiscover?.take(4).orEmpty()
            val reasons = discoverReasons(discover)
            val discoverCards = discover.mapIndexed { index, item -> DiscoverItem(item, reasons[index]) }
            val shown = HashSet<String>()
            lastPlayedSong?.id?.let(shown::add)
            interleave(discoverCards, extras).forEach { card ->
                val id = card.mediaId
                if (id == null || shown.add(id)) add(card)
            }

            addAll(playlistPicks.take(2))

            val regular = count { it !is GachaItem }
            if (regular < 5) {
                keepListening.orEmpty()
                    .filterIsInstance<Song>()
                    .filter { song -> none { it.mediaId == song.id } }
                    .take(5 - regular)
                    .forEach { song -> add(KeepListeningItem(song.toHeroMetadata())) }
            }

            if (none { it !is GachaItem }) addAll(starterItems.take(5))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeroStageHeight)
            .glassSurface(
                shape = RoundedCornerShape(0.dp),
                style = GlassStyle(
                    containerColor = Color.Transparent,
                    borderColor = Color.Transparent,
                    highlightColor = Color.Transparent,
                    elevation = 0.dp,
                ),
            ),
    ) {
        RevealWhenLoaded(
            data = if (hold) null else carouselItems.takeIf { it.isNotEmpty() || !isLoading },
            modifier = Modifier.fillMaxSize(),
            bones = { HeroBones() },
        ) { items ->
            if (items.isEmpty()) {
                QuietHero(modifier = Modifier.fillMaxSize())
            } else {
                HeroPager(
                    items = items,
                    playerConnection = playerConnection,
                    navController = navController,
                    heroScrollProgressProvider = heroScrollProgressProvider,
                    gachaPool = gachaPool,
                )
            }
        }
    }
}

@Composable
private fun HeroPager(
    items: List<HeroCarouselItem>,
    playerConnection: PlaybackController?,
    navController: NavHostController,
    heroScrollProgressProvider: () -> Float,
    gachaPool: List<MediaMetadata>,
) {
    val scope = rememberCoroutineScope()
    val pageState = remember { mutableStateOf(0) }
    val swipe = remember { Animatable(0f) }
    val fallbackTransport = remember { mutableStateOf(TransportState()) }
    val transport by playerConnection?.transportState?.collectAsState() ?: fallbackTransport
    val fallbackMetadata = remember { mutableStateOf<MediaMetadata?>(null) }
    val current by playerConnection?.currentMetadata?.collectAsState() ?: fallbackMetadata

    fun move(delta: Int) {
        if (items.size <= 1) return
        val target = (pageState.value + delta + items.size) % items.size
        scope.launch {
            swipe.animateTo(if (delta > 0) -1f else 1f, tween(180))
            pageState.value = target
            swipe.snapTo(0f)
        }
    }

    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(HeroAdvanceMillis)
            if (heroScrollProgressProvider() > 0.08f) continue
            pageState.value = (pageState.value + 1) % items.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = swipe.value * 24f
                alpha = 1f - abs(swipe.value) * 0.12f
            },
    ) {
        AnimatedContent(
            targetState = pageState.value,
            transitionSpec = {
                (slideInHorizontally(tween(420)) { it / 8 } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(320)) { -it / 8 } + fadeOut(tween(200)))
                    .using(SizeTransform(clip = false))
            },
            label = "heroPage",
        ) { index ->
            val item = items[index.coerceIn(0, items.lastIndex)]
            val itemIsPlaying = current?.id == item.mediaId && transport.isPlaying
            HeroPage(
                item = item,
                isThisPlaying = itemIsPlaying,
                onPrimaryClick = {
                    if (item is GachaItem) {
                        gachaPool.randomOrNull()?.let { playMetadata(it, playerConnection) }
                    } else if (itemIsPlaying) {
                        playerConnection?.pause()
                    } else {
                        item.onPlay(playerConnection, navController)
                    }
                },
                onPrevious = { move(-1) },
                onNext = { move(1) },
            )
        }
    }
}

@Composable
private fun HeroPage(
    item: HeroCarouselItem,
    isThisPlaying: Boolean,
    onPrimaryClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .glassSurface(
                shape = RoundedCornerShape(0.dp),
                style = GlassStyle(
                    containerColor = Color.Transparent,
                    borderColor = Color.Transparent,
                    highlightColor = Color.Transparent,
                    elevation = 0.dp,
                ),
            ),
    ) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(Res.drawable.queue_music),
            error = painterResource(Res.drawable.queue_music),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        GlassTokens.Base.copy(alpha = 0.78f),
                        Color.Transparent,
                        GlassTokens.Base.copy(alpha = 0.42f),
                        GlassTokens.Base.copy(alpha = 0.92f),
                    ),
                ),
            ),
        )

        HeroText(
            label = item.label(isThisPlaying),
            title = if (item.subText.isEmpty()) item.mainText else "${item.mainText} - ${item.subText}",
            pill = when {
                item is GachaItem -> HeroPillKind.Roll
                item.opensPage -> HeroPillKind.View
                isThisPlaying -> HeroPillKind.Pause
                else -> HeroPillKind.Play
            },
            onPillClick = onPrimaryClick,
            modifier = Modifier.align(Alignment.BottomStart),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = HomeContentInset, bottom = 76.dp),
        ) {
            HeroArrow(direction = -1, onClick = onPrevious)
            HeroArrow(direction = 1, onClick = onNext)
        }
    }
}

@Composable
private fun HeroArrow(direction: Int, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .glassSurface(
                shape = shape,
                style = GlassStyle(
                    containerColor = GlassTokens.Chrome.copy(alpha = 0.7f),
                    elevation = 0.dp,
                ),
            )
            .pointerInput(direction) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (direction < 0) Res.drawable.arrow_back else Res.drawable.arrow_forward),
            contentDescription = if (direction < 0) "Previous" else "Next",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun HeroText(
    label: HeroLabel,
    title: String,
    pill: HeroPillKind,
    onPillClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = HomeContentInset, end = HomeContentInset, bottom = 64.dp),
    ) {
        HeroLabelLine(label)
        Text(
            text = title,
            style = HomeTitleStyle,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(11.dp))
        HeroPill(kind = pill, onClick = onPillClick)
    }
}

@Composable
private fun HeroLabelLine(label: HeroLabel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label.text, style = HomeLabelStyle, color = GlassTokens.MutedContent, maxLines = 1)
        if (label.coverUrl != null) {
            Spacer(Modifier.width(6.dp))
            val coverShape = if (label.roundCover) CircleShape else RoundedCornerShape(5.dp)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(coverShape)
                    .glassSurface(shape = coverShape, style = GlassStyle(elevation = 0.dp)),
            ) {
                AsyncImage(
                    model = label.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(Res.drawable.queue_music),
                    error = painterResource(Res.drawable.queue_music),
                    modifier = Modifier.fillMaxSize().clip(coverShape),
                )
            }
        }
        if (label.subject != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = label.subject,
                style = HomeLabelStyle,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private enum class HeroPillKind { Play, Pause, View, Roll }

@Composable
private fun HeroPill(kind: HeroPillKind, onClick: () -> Unit) {
    val pressed = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed.value) 0.94f else 1f,
        animationSpec = tween(120),
        label = "heroPillScale",
    )
    val shape = RoundedCornerShape(50)
    val glass = LocalGlassState.current
    val tint = glass.accent.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(33.dp)
            .clip(shape)
            .glassSurface(
                shape = shape,
                style = GlassStyle(
                    containerColor = GlassTokens.Chrome.copy(alpha = 0.72f),
                    borderColor = Color.White.copy(alpha = 0.32f),
                    elevation = 0.dp,
                ),
            )
            .pointerInput(kind) {
                detectTapGestures(
                    onPress = {
                        pressed.value = true
                        awaitRelease()
                        pressed.value = false
                    },
                    onTap = { onClick() },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        GlassPillBackground(
            state = glass,
            blurRadius = { BlurEffectCache.get(10f) ?: 0f },
            tint = tint.copy(alpha = 0.18f),
            foundationColor = Color.Transparent,
            shape = shape,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            when (kind) {
                HeroPillKind.Play, HeroPillKind.Pause -> {
                    Icon(
                        painter = painterResource(
                            if (kind == HeroPillKind.Pause) Res.drawable.ic_untitled_pause else Res.drawable.ic_untitled_play,
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                }
                HeroPillKind.Roll -> {
                    Icon(
                        painter = painterResource(Res.drawable.shuffle),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                HeroPillKind.View -> Unit
            }
            Text(
                text = when (kind) {
                    HeroPillKind.Play -> "Play"
                    HeroPillKind.Pause -> "Pause"
                    HeroPillKind.View -> "View"
                    HeroPillKind.Roll -> "roll!!"
                },
                style = HomeLabelStyle,
                color = Color.White,
            )
            if (kind == HeroPillKind.View) {
                Spacer(Modifier.width(5.dp))
                Icon(
                    painter = painterResource(Res.drawable.arrow_forward),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** The hero's loading look, sized and placed like [HeroCarousel]. */
@Composable
fun HeroCarouselBones(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HeroStageHeight)
            .glassSurface(
                shape = RoundedCornerShape(0.dp),
                style = GlassStyle(
                    containerColor = Color.Transparent,
                    borderColor = Color.Transparent,
                    highlightColor = Color.Transparent,
                    elevation = 0.dp,
                ),
            ),
    ) {
        HeroBones()
    }
}

/** Nothing to put forward: a quiet glass card instead of an empty hole. */
@Composable
private fun QuietHero(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.glassSurface(
            shape = RoundedCornerShape(0.dp),
            style = GlassStyle(
                containerColor = GlassTokens.Chrome.copy(alpha = 0.55f),
                borderColor = Color.Transparent,
                elevation = 0.dp,
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = HomeContentInset, end = HomeContentInset, bottom = 64.dp),
        ) {
            Text(text = "Nothing here yet", style = HomeLabelStyle, color = GlassTokens.MutedContent, maxLines = 1)
            Text(
                text = "Play something and it shows up here",
                style = HomeTitleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What the hero looks like while the first load is still out. */
@Composable
private fun HeroBones() {
    BonesHost(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.verticalGradient(
                        listOf(BoneColor.copy(alpha = 0.12f), Color.Transparent),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = HomeContentInset, bottom = 64.dp),
            ) {
                Bone(width = 72.dp, height = 14.dp)
                Spacer(Modifier.height(8.dp))
                Bone(width = 220.dp, height = 22.dp)
                Spacer(Modifier.height(13.dp))
                Bone(width = 96.dp, height = 33.dp)
            }
        }
    }
}
