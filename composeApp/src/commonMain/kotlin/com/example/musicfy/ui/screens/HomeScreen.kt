package com.example.musicfy.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.AlbumEntity
import com.example.musicfy.db.entities.Artist as LocalArtist
import com.example.musicfy.db.entities.ArtistEntity
import com.example.musicfy.db.entities.LocalItem
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.db.entities.Song
import com.example.musicfy.db.entities.SongEntity
import com.example.musicfy.db.entities.SpeedDialItem
import com.example.musicfy.models.ArtistGroup
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.PlaybackController
import com.example.musicfy.player.fake.FakeCatalog
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.TrackSource
import com.example.musicfy.player.model.TransportState
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.all_time_hits
import com.example.musicfy.resources.artist_list
import com.example.musicfy.resources.dont_forget_these_songs
import com.example.musicfy.resources.from_the_community
import com.example.musicfy.resources.home
import com.example.musicfy.resources.home_because_you_listen_to
import com.example.musicfy.resources.home_history
import com.example.musicfy.resources.home_more_like
import com.example.musicfy.resources.home_most_played
import com.example.musicfy.resources.home_new_from_your_artists
import com.example.musicfy.resources.home_try_something_new
import com.example.musicfy.resources.live_shows
import com.example.musicfy.resources.music_videos_for_you
import com.example.musicfy.resources.n_song
import com.example.musicfy.resources.person
import com.example.musicfy.resources.queue_music
import com.example.musicfy.resources.recently_played
import com.example.musicfy.resources.speed_dial
import com.example.musicfy.ui.component.ArtistListCard
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.BlurEffectCache
import com.example.musicfy.ui.component.Bone
import com.example.musicfy.ui.component.BonesHost
import com.example.musicfy.ui.component.DailyDiscoverItem
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.HeroCarousel
import com.example.musicfy.ui.component.HeroCarouselBones
import com.example.musicfy.ui.component.HeroCarouselItem
import com.example.musicfy.ui.component.HeroTrack
import com.example.musicfy.ui.component.HomeCardCornerRadius
import com.example.musicfy.ui.component.HomeCardSpacing
import com.example.musicfy.ui.component.HomeContentInset
import com.example.musicfy.ui.component.HomeCoverCard
import com.example.musicfy.ui.component.HomeLabelStyle
import com.example.musicfy.ui.component.HomeSectionBones
import com.example.musicfy.ui.component.HomeSectionTitle
import com.example.musicfy.ui.component.HomeTitleStyle
import com.example.musicfy.ui.component.HomeTrackRow
import com.example.musicfy.ui.component.HomeVideoCard
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.PlaylistPickItem
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.RevealSeenState
import com.example.musicfy.ui.component.RevealWhenLoaded
import com.example.musicfy.ui.component.StarterItem
import com.example.musicfy.ui.component.StarterSongItem
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.component.homeRowHeight
import com.example.musicfy.ui.component.revealOnAppear
import com.example.musicfy.ui.component.rememberGlassState
import com.example.musicfy.ui.component.rememberRevealSeenState
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist as YtArtist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Stable identifiers used for reveal keys and section_detail routes. */
sealed class HomeSection(val id: String) {
    data object RecentlyPlayed : HomeSection("recently_played")
    data object MostPlayed : HomeSection("most_played")
    data object History : HomeSection("history")
    data object DontForgetTheseSongs : HomeSection("dont_forget_these_songs")
    data object AccountPlaylists : HomeSection("account_playlists")
    data object SpeedDial : HomeSection("speed_dial")
    data object FromTheCommunity : HomeSection("from_the_community")
    data object ArtistList : HomeSection("artist_list")
    data object AllTimeHits : HomeSection("all_time_hits")
    data object LiveShows : HomeSection("live_shows")
    data object MusicVideos : HomeSection("music_videos")

    data class Category(val categoryId: String) : HomeSection("category_$categoryId")

    data class HomePageSection(val index: Int, val title: String) : HomeSection("home_page_section_${index}_$title")
}

/** Plain UI projection shared by the local database and the offline demo catalog. */
@Immutable
data class HomeTrack(
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val duration: Int = 0,
    val albumTitle: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val localUri: String? = null,
)

@Immutable
data class CommunityPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val songCount: Int,
    val songCountText: String? = null,
)

/** Local replacement for upstream's ViewModel-owned CommunityPlaylistItem. */
@Immutable
data class CommunityPlaylistItem(
    val playlist: CommunityPlaylist,
    val songs: List<SongItem>,
)

@Immutable
data class HomeCategory(
    val id: String,
    val title: String,
    val label: String?,
    val thumbnailUrl: String?,
    val items: List<YTItem>,
    val kind: Kind,
    val browseId: String? = null,
) {
    enum class Kind { BecauseYouLike, MoreLikeArtist, YourGenre, NewGenre, NewFromArtists }
}

@Immutable
data class HomePageSectionData(
    val title: String,
    val label: String?,
    val thumbnailUrl: String?,
    val items: List<YTItem>,
    val browseId: String?,
)

@Immutable
data class HomeVideo(
    val item: SongItem,
    val title: String,
    val subtitle: String?,
)

private val HomeMarkWidth = 31.dp
private val HomeMarkHeight = 34.dp
private val HomeHeroHeight = 420.dp

/** A Home row fades and slides up the first time it scrolls into view. */
private fun LazyListScope.homeItem(
    key: String,
    revealSeen: RevealSeenState,
    contentType: String,
    content: @Composable () -> Unit,
) {
    item(key = key, contentType = contentType) {
        Box(modifier = Modifier.revealOnAppear(key, revealSeen, cascade = true)) {
            content()
        }
    }
}

@Composable
private fun rememberCardRowState(): LazyListState = rememberLazyListState()

@Composable
private fun rememberTrackGridState(): LazyGridState = rememberLazyGridState()

/** Horizontally scrolling cards, lined up with the section title. */
@Composable
private fun <T> HomeCardRow(
    rowKey: String,
    items: List<T>,
    revealSeen: RevealSeenState,
    key: (T) -> Any,
    content: @Composable (T) -> Unit,
) {
    LazyRow(
        state = rememberCardRowState(),
        contentPadding = PaddingValues(horizontal = HomeContentInset),
        horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
    ) {
        items(count = items.size, key = { index -> key(items[index]) }) { index ->
            val item = items[index]
            Box(
                modifier = Modifier.revealOnAppear(
                    key = "$rowKey/${key(item)}",
                    seenState = revealSeen,
                    delayMillis = minOf(index, 4) * 60,
                ),
            ) {
                content(item)
            }
        }
    }
}

/** Columns of at most three song rows, horizontally paged. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> HomeTrackGrid(
    items: List<T>,
    state: LazyGridState,
    rowSpacing: Dp,
    columnWidth: Dp,
    key: (Int, T) -> Any,
    content: @Composable (index: Int, item: T) -> Unit,
) {
    val rows = minOf(3, items.size).coerceAtLeast(1)
    LazyHorizontalGrid(
        state = state,
        rows = GridCells.Fixed(rows),
        contentPadding = PaddingValues(horizontal = HomeContentInset),
        horizontalArrangement = Arrangement.spacedBy(39.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .height(homeRowHeight() * rows + rowSpacing * (rows - 1)),
    ) {
        items(count = items.size, key = { index -> key(index, items[index]) }) { index ->
            Box(modifier = Modifier.width(columnWidth)) {
                content(index, items[index])
            }
        }
    }
}

/** Deterministic dark-gradient community playlist card, with glass instead of Palette. */
@Composable
fun CommunityPlaylistCard(
    item: CommunityPlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlassState.current
    val palettes = listOf(
        listOf(0xFF2B1B38, 0xFF151521),
        listOf(0xFF1B2838, 0xFF0F1521),
        listOf(0xFF381B24, 0xFF1F0F15),
        listOf(0xFF1B382D, 0xFF0F1F17),
        listOf(0xFF2D2B55, 0xFF151521),
    )
    val palette = palettes[(item.playlist.id.hashCode() and 0x7FFFFFFF) % palettes.size]
    val shape = RoundedCornerShape(HomeCardCornerRadius)

    Box(
        modifier = modifier
            .width(276.dp)
            .height(344.dp)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color(palette[0]).copy(alpha = 0.92f),
                        Color(palette[1]).copy(alpha = 0.86f),
                        GlassTokens.Base.copy(alpha = 0.94f),
                    ),
                ),
            ),
        )

        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.songs.take(3).forEach { song ->
                        CommunityCover(
                            thumbnailUrl = song.thumbnail,
                            accent = glass.accent,
                            size = 84.dp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.songs.drop(3).take(2).forEach { song ->
                        CommunityCover(
                            thumbnailUrl = song.thumbnail,
                            accent = glass.accent,
                            size = 84.dp,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Transparent,
                        GlassTokens.Base.copy(alpha = 0.72f),
                        GlassTokens.Base.copy(alpha = 0.92f),
                    ),
                ),
            ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        ) {
            Text(
                text = item.playlist.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.playlist.songCountText ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.MutedContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CommunityCover(thumbnailUrl: String?, accent: Color, size: Dp) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .glassSurface(
                shape = shape,
                style = GlassStyle(
                    containerColor = accent.copy(alpha = 0.16f),
                    elevation = 0.dp,
                ),
            ),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(Res.drawable.queue_music),
            error = painterResource(Res.drawable.queue_music),
            modifier = Modifier.fillMaxSize().clip(shape),
        )
    }
}

@Composable
fun AllTimeHitsCard(
    item: YTItem,
    modifier: Modifier = Modifier,
) {
    val artistName = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> item.artists?.joinToString(", ") { it.name } ?: ""
        is ArtistItem -> item.title
        is PlaylistItem -> item.author?.name ?: ""
        else -> ""
    }
    val shape = RoundedCornerShape(HomeCardCornerRadius)

    Box(
        modifier = modifier
            .width(160.dp)
            .height(160.dp)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp)),
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(Res.drawable.queue_music),
            error = painterResource(Res.drawable.queue_music),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, GlassTokens.Base.copy(alpha = 0.72f)),
                ),
            ),
        )
        Text(
            text = artistName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        )
    }
}

/** The brand mark folds into the "Home" wordmark as the hero scrolls away. */
@Composable
private fun HomeTopBar(
    progress: Float,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlassState.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = HomeContentInset, end = 30.dp, top = 14.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            Icon(
                painter = painterResource(Res.drawable.home),
                contentDescription = "Musicfy",
                tint = Color.White,
                modifier = Modifier
                    .size(HomeMarkWidth, HomeMarkHeight)
                    .blur(BlurEffectCache.blurDp(progress * 6f))
                    .graphicsLayer { alpha = 1f - progress },
            )
            Text(
                text = "Home",
                style = HomeTitleStyle,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = progress },
            )
        }

        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(50))
                .glassSurface(
                    shape = RoundedCornerShape(50),
                    style = GlassStyle(
                        containerColor = GlassTokens.Chrome.copy(alpha = 0.68f),
                        elevation = 0.dp,
                    ),
                )
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .glassSurface(
                        shape = CircleShape,
                        style = GlassStyle(
                            containerColor = glass.accent.copy(alpha = 0.24f),
                            elevation = 0.dp,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.person),
                    contentDescription = "Profile",
                    tint = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Home before the data is ready: the same top bar, hero and bones as the real thing. */
@Composable
private fun HomeShell() {
    Box(modifier = Modifier.fillMaxSize()) {
        HeroCarouselBones(modifier = Modifier.fillMaxWidth())
        BonesHost(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = HomeHeroHeight),
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = HomeContentInset)
                        .glassSurface(
                            shape = RoundedCornerShape(HomeCardCornerRadius),
                            style = GlassStyle(elevation = 0.dp),
                        )
                        .padding(16.dp),
                ) {
                    HomeSectionBones()
                }
                Spacer(modifier = Modifier.height(34.dp))
            }
        }
        HomeTopBar(progress = 0f, onSettings = {}, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
fun HomeScreen(navController: androidx.navigation.NavHostController, modifier: Modifier = Modifier) {
    val database = LocalAppContainer.current?.database
    val playerConnection = LocalPlaybackController.current

    val fallbackTransport = remember { mutableStateOf(TransportState()) }
    val transport by playerConnection?.transportState?.collectAsState() ?: fallbackTransport
    val fallbackMetadata = remember { mutableStateOf<MediaMetadata?>(null) }
    val currentMetadata by playerConnection?.currentMetadata?.collectAsState() ?: fallbackMetadata
    val isPlayingNow = remember(transport) { { transport.isPlaying } }

    val fakeSongs = remember { FakeCatalog.tracks.map { it.metadata.toFakeSong() } }
    val fakePlaylist = remember(fakeSongs) { fakePlaylist(fakeSongs) }

    val recentFlow = remember(database, fakeSongs) { database?.recentlyPlayedSongs(15) ?: flowOf(fakeSongs) }
    val mostPlayedFlow = remember(database, fakeSongs) {
        database?.mostPlayedSongs(0L, limit = 6) ?: flowOf(fakeSongs.take(6))
    }
    val historyFlow = remember(database, fakeSongs) { database?.recentlyPlayedSongs(20) ?: flowOf(fakeSongs) }
    val likedFlow = remember(database, fakeSongs) {
        database?.likedSongs(MusicDatabaseFacade.SongSortType.CREATE_DATE, true)
            ?: flowOf(fakeSongs.filter { it.song.liked })
    }
    val playlistsFlow = remember(database, fakePlaylist) {
        database?.playlists(MusicDatabaseFacade.PlaylistSortType.CREATE_DATE, true)
            ?: flowOf(listOf(fakePlaylist))
    }
    val speedDialFlow = remember(database, fakeSongs) {
        database?.getAll() ?: flowOf(fakeSpeedDial(fakeSongs))
    }

    val recentSongs by recentFlow.collectAsState(emptyList())
    val mostPlayedSongs by mostPlayedFlow.collectAsState(emptyList())
    val historySongs by historyFlow.collectAsState(emptyList())
    val likedSongs by likedFlow.collectAsState(emptyList())
    val localPlaylists by playlistsFlow.collectAsState(emptyList())
    val speedDialItems by speedDialFlow.collectAsState(emptyList())

    val recentTracks = remember(recentSongs) { recentSongs.map(Song::toHomeTrack) }
    val mostTracks = remember(mostPlayedSongs) { mostPlayedSongs.map(Song::toHomeTrack) }
    val historyTracks = remember(historySongs) { historySongs.map(Song::toHomeTrack) }
    val forgottenFavorites = remember(likedSongs) { likedSongs.takeLast(6) }
    val forgottenTracks = remember(forgottenFavorites) { forgottenFavorites.map(Song::toHomeTrack) }
    val speedDialTracks = remember(speedDialItems) { speedDialItems.map(SpeedDialItem::toHomeTrack) }
    val localPlaylistTracks = remember(localPlaylists) { localPlaylists.map(Playlist::toHomeTrack) }

    val communityItems = remember(localPlaylists, fakeSongs) {
        localPlaylists.map { it.toCommunityItem(fakeSongs) }
            .ifEmpty { listOf(fakePlaylist.toCommunityItem(fakeSongs)) }
    }
    val artistGroups = remember(fakeSongs) { buildArtistGroups(fakeSongs) }
    val allTimeHits = remember(mostPlayedSongs, fakeSongs) {
        (mostPlayedSongs.ifEmpty { fakeSongs }).take(8).map(Song::toSongItem)
    }
    val liveVideos = remember(fakeSongs) { fakeSongs.take(4).map(Song::toHomeVideo) }
    val musicVideos = remember(fakeSongs) { fakeSongs.drop(2).take(4).map(Song::toHomeVideo) }
    val categories = remember(fakeSongs) { demoCategories(fakeSongs) }
    val homePageSections = remember(fakeSongs) { demoHomePage(fakeSongs) }
    val accountPlaylists = remember(fakeSongs) { demoAccountPlaylists(fakeSongs) }

    val gachaPool = remember { FakeCatalog.tracks.map { it.metadata } }
    val dailyDiscover = remember(mostPlayedSongs, fakeSongs) {
        val source = mostPlayedSongs.ifEmpty { fakeSongs }
        source.take(3).map { song ->
            DailyDiscoverItem(
                recommendation = song.toHeroTrack(),
                seedTitle = song.title,
                seedThumbnailUrl = song.thumbnailUrl,
                seedArtists = song.artists.map { it.name },
                seedLiked = song.song.liked,
            )
        }
    }
    val playlistPicks = remember(localPlaylists) {
        localPlaylists.take(2).map { playlist ->
            PlaylistPickItem(
                id = playlist.id,
                name = playlist.title,
                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                play = {
                    playTracks(
                        tracks = fakeSongs.map(Song::toHomeTrack),
                        title = playlist.title,
                        controller = playerConnection,
                    )
                },
            )
        }
    }
    val starterItems = remember(fakeSongs) {
        val first = fakeSongs.firstOrNull()
        buildList<HeroCarouselItem> {
            add(
                StarterItem(
                    kind = StarterItem.Kind.Playlist,
                    id = "demo-queue",
                    title = FakeCatalog.title,
                    detail = "Your offline queue",
                    thumbnailUrl = null,
                ),
            )
            if (first != null) {
                add(
                    StarterSongItem(
                        song = first.toHeroTrack(),
                        pickedArtist = first.artists.firstOrNull()?.name ?: "Musicfy",
                        pickedArtistThumbnail = first.thumbnailUrl,
                    ),
                )
            }
        }
    }

    val revealSeen = rememberRevealSeenState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val heroHeightPx = remember(density) { with(density) { HomeHeroHeight.toPx() } }
    val firstItemScrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                heroHeightPx
            }
        }
    }
    val heroScrollProgress by remember {
        derivedStateOf { (firstItemScrollOffset / heroHeightPx).coerceIn(0f, 1f) }
    }
    val heroScrollProgressProvider = remember { { heroScrollProgress } }

    val isReady = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isReady.value = true }

    val homeSections by remember(
        recentSongs,
        mostPlayedSongs,
        historySongs,
        speedDialItems,
        communityItems,
        artistGroups,
        localPlaylists,
        accountPlaylists,
        allTimeHits,
        liveVideos,
        musicVideos,
        forgottenFavorites,
        categories,
        homePageSections,
    ) {
        derivedStateOf {
            buildList {
                if (recentSongs.isNotEmpty()) add(HomeSection.RecentlyPlayed)
                if (mostPlayedSongs.isNotEmpty()) add(HomeSection.MostPlayed)
                if (speedDialItems.isNotEmpty()) add(HomeSection.SpeedDial)
                if (historySongs.isNotEmpty()) add(HomeSection.History)
                if (communityItems.isNotEmpty()) add(HomeSection.FromTheCommunity)
                categories.forEachIndexed { index, category -> add(HomeSection.Category(category.id.takeIf { it.isNotBlank() } ?: "category_$index")) }
                if (artistGroups.isNotEmpty()) add(HomeSection.ArtistList)
                if (localPlaylists.isNotEmpty() || accountPlaylists.isNotEmpty()) add(HomeSection.AccountPlaylists)
                if (forgottenFavorites.isNotEmpty()) add(HomeSection.DontForgetTheseSongs)
                if (liveVideos.isNotEmpty()) add(HomeSection.LiveShows)
                if (musicVideos.isNotEmpty()) add(HomeSection.MusicVideos)
                homePageSections.firstOrNull()?.let { add(HomeSection.HomePageSection(0, it.title)) }
                if (allTimeHits.isNotEmpty()) add(HomeSection.AllTimeHits)
                homePageSections.drop(1).forEachIndexed { index, section ->
                    add(HomeSection.HomePageSection(index + 1, section.title))
                }
            }
        }
    }

    val playHomeTrack: (HomeTrack) -> Unit = { track ->
        if (track.id == currentMetadata?.id) {
            playerConnection?.togglePlayPause()
        } else {
            playTracks(listOf(track), track.title, playerConnection)
        }
    }
    val playSong: (Song) -> Unit = { playHomeTrack(it.toHomeTrack()) }
    val playYtItem: (YTItem) -> Unit = { playHomeTrack(it.toHomeTrack()) }
    val playAll: (List<Song>, String) -> Unit = { songs, title ->
        playTracks(songs.map(Song::toHomeTrack), title, playerConnection)
    }
    val playYtItems: (List<YTItem>, String) -> Unit = { items, title ->
        playTracks(items.map(YTItem::toHomeTrack), title, playerConnection)
    }

    val localItemCard: @Composable (LocalItem) -> Unit = { item ->
        val subtitle = when (item) {
            is Song -> item.artists.joinToString(", ") { it.name }
            is Album -> item.artists.joinToString(", ") { it.name }
            is Playlist -> pluralStringResource(Res.plurals.n_song, item.songCount, item.songCount)
            is LocalArtist -> null
        }
        val thumbnail = if (item is Playlist) {
            item.thumbnails.firstOrNull() ?: item.thumbnailUrl
        } else {
            item.thumbnailUrl
        }
        HomeCoverCard(
            title = item.title,
            subtitle = subtitle,
            thumbnailUrl = thumbnail,
            circular = item is LocalArtist,
            isActive = when (item) {
                is Song -> item.id == currentMetadata?.id
                is Album -> item.id == currentMetadata?.album?.id
                else -> false
            },
            isPlaying = isPlayingNow,
            modifier = Modifier.clickable {
                when (item) {
                    is Song -> playSong(item)
                    is Album -> navController.navigate("album/${item.id}")
                    is LocalArtist -> navController.navigate("artist/${item.id}")
                    is Playlist -> navController.navigate("local_playlist/${item.id}")
                }
            },
        )
    }

    val ytItemCard: @Composable (YTItem) -> Unit = { item ->
        HomeCoverCard(
            title = item.title,
            subtitle = when (item) {
                is SongItem -> item.artists.joinToString(", ") { it.name }
                is AlbumItem -> item.artists?.joinToString(", ") { it.name }
                is ArtistItem -> null
                is PlaylistItem -> listOfNotNull(item.author?.name, item.songCountText).joinToString(" • ")
                else -> null
            },
            thumbnailUrl = item.thumbnail,
            circular = item is ArtistItem,
            isActive = item.id == currentMetadata?.id || item.id == currentMetadata?.album?.id,
            isPlaying = isPlayingNow,
            modifier = Modifier.clickable {
                when (item) {
                    is SongItem -> playYtItem(item)
                    is AlbumItem -> navController.navigate("album/${item.id}")
                    is ArtistItem -> navController.navigate("artist/${item.id}")
                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                    else -> Unit
                }
            },
        )
    }

    val communityCard: @Composable (CommunityPlaylistItem) -> Unit = { item ->
        CommunityPlaylistCard(
            item = item,
            onClick = { navController.navigate("online_playlist/${item.playlist.id}") },
        )
    }
    val artistGroupCard: @Composable (ArtistGroup) -> Unit = { group ->
        ArtistListCard(
            group = group,
            onClick = {
                group.artistId?.let { navController.navigate("artist/$it/similar") }
            },
            onItemClick = playYtItem,
        )
    }
    val hitsCard: @Composable (YTItem) -> Unit = { item ->
        AllTimeHitsCard(
            item = item,
            modifier = Modifier.clickable {
                when (item) {
                    is SongItem -> playYtItem(item)
                    is AlbumItem -> navController.navigate("album/${item.id}")
                    is ArtistItem -> navController.navigate("artist/${item.id}")
                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                    else -> Unit
                }
            },
        )
    }

    val rankedTrackRow: @Composable (Int, Song) -> Unit = { index, song ->
        HomeTrackRow(
            thumbnailUrl = song.thumbnailUrl,
            title = song.title,
            subtitle = song.artists.joinToString(", ") { it.name },
            isActive = song.id == currentMetadata?.id,
            isPlaying = isPlayingNow,
            onClick = { playSong(song) },
            onLongClick = { playSong(song) },
            onMoreClick = null,
            rank = index + 1,
        )
    }
    val trackRow: @Composable (Int, Song) -> Unit = { _, song ->
        HomeTrackRow(
            thumbnailUrl = song.thumbnailUrl,
            title = song.title,
            subtitle = song.artists.joinToString(", ") { it.name },
            isActive = song.id == currentMetadata?.id,
            isPlaying = isPlayingNow,
            onClick = { playSong(song) },
            onLongClick = { playSong(song) },
            onMoreClick = null,
        )
    }

    RevealWhenLoaded(
        data = if (isReady.value) homeSections else null,
        modifier = modifier.fillMaxSize(),
        bones = { HomeShell() },
    ) { sections ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            val trackColumnWidth = if (maxWidth * 0.475f >= 320.dp) maxWidth * 0.475f else maxWidth * 0.76f
            val videoCardWidth = minOf(maxWidth * 0.78f, 340.dp)

            val videoRowCard: @Composable (HomeVideo) -> Unit = { video ->
                HomeVideoCard(
                    title = video.title,
                    subtitle = video.subtitle,
                    videoId = video.item.id,
                    fallbackThumbnailUrl = video.item.thumbnail,
                    width = videoCardWidth,
                    isActive = video.item.id == currentMetadata?.id,
                    isPlaying = isPlayingNow,
                    modifier = Modifier.clickable { playYtItem(video.item) },
                )
            }

            val homeGlassState = rememberGlassState()
            val topBarProgress by animateFloatAsState(
                targetValue = if (heroScrollProgress > 0.5f) 1f else 0f,
                animationSpec = tween(durationMillis = 600),
                label = "homeTopBarProgress",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassRoot(homeGlassState, isActive = { heroScrollProgress > 0f }),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 40.dp),
                ) {
                    item(key = "hero", contentType = "hero") {
                        HeroCarousel(
                            keepListening = recentSongs,
                            lastPlayedSong = currentMetadata ?: gachaPool.firstOrNull(),
                            dailyDiscover = dailyDiscover,
                            playerConnection = playerConnection,
                            navController = navController,
                            heroScrollProgressProvider = heroScrollProgressProvider,
                            gachaPool = gachaPool,
                            starterItems = starterItems,
                            playlistPicks = playlistPicks,
                            similarRecommendations = artistGroups,
                            onRepeat = mostPlayedSongs,
                            forgottenFavorites = forgottenFavorites,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    sections.forEach { section ->
                        when (section) {
                            HomeSection.SpeedDial -> {
                                homeItem("speed_dial_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(Res.string.speed_dial),
                                        onClick = { navController.navigate("section_detail/speed_dial") },
                                    )
                                }
                                homeItem("speed_dial_list", revealSeen, "cardRow") {
                                    HomeCardRow("speed_dial", speedDialTracks, revealSeen, key = { it.id }, content = ytCardFromTrack(ytItemCard))
                                }
                            }

                            HomeSection.RecentlyPlayed -> {
                                homeItem("recently_played_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(Res.string.recently_played),
                                        onClick = { navController.navigate("section_detail/recently_played") },
                                    )
                                }
                                homeItem("recently_played_list", revealSeen, "cardRow") {
                                    HomeCardRow("recently_played", recentSongs, revealSeen, key = { it.id }, content = localItemCard)
                                }
                            }

                            HomeSection.MostPlayed -> {
                                homeItem("most_played_title", revealSeen, "title") {
                                    val mostPlayedTitle = stringResource(Res.string.home_most_played)
                                    HomeSectionTitle(
                                        title = mostPlayedTitle,
                                        onClick = { navController.navigate("section_detail/most_played") },
                                        onPlayAllClick = { playAll(mostPlayedSongs, mostPlayedTitle) },
                                    )
                                }
                                homeItem("most_played_list", revealSeen, "trackGrid") {
                                    HomeTrackGrid(
                                        items = mostPlayedSongs,
                                        state = rememberTrackGridState(),
                                        rowSpacing = 6.dp,
                                        columnWidth = trackColumnWidth,
                                        key = { _, song -> song.id },
                                        content = rankedTrackRow,
                                    )
                                }
                            }

                            HomeSection.History -> {
                                homeItem("history_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(Res.string.home_history),
                                        onClick = { navController.navigate(Routes.HISTORY) },
                                    )
                                }
                                homeItem("history_list", revealSeen, "trackGrid") {
                                    HomeTrackGrid(
                                        items = historySongs,
                                        state = rememberTrackGridState(),
                                        rowSpacing = 6.dp,
                                        columnWidth = trackColumnWidth,
                                        key = { index, song -> "history_${index}_${song.id}" },
                                        content = trackRow,
                                    )
                                }
                            }

                            HomeSection.FromTheCommunity -> {
                                homeItem("community_playlists_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(Res.string.from_the_community),
                                        onClick = { navController.navigate("section_detail/from_the_community") },
                                    )
                                }
                                homeItem("community_playlists_content", revealSeen, "cardRow") {
                                    HomeCardRow("community_playlists", communityItems, revealSeen, key = { it.playlist.id }, content = communityCard)
                                }
                            }

                            HomeSection.ArtistList -> {
                                homeItem("artist_list_title", revealSeen, "title") {
                                    HomeSectionTitle(title = stringResource(Res.string.artist_list))
                                }
                                homeItem("artist_list_content", revealSeen, "cardRow") {
                                    HomeCardRow("artist_list", artistGroups, revealSeen, key = { "${it.artistId}_${it.artistName}" }, content = artistGroupCard)
                                }
                            }

                            HomeSection.AllTimeHits -> {
                                homeItem("all_time_hits_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(Res.string.all_time_hits),
                                        onClick = { navController.navigate("section_detail/all_time_hits") },
                                    )
                                }
                                homeItem("all_time_hits_list", revealSeen, "cardRow") {
                                    HomeCardRow("all_time_hits", allTimeHits, revealSeen, key = { it.id }, content = hitsCard)
                                }
                            }

                            HomeSection.LiveShows, HomeSection.MusicVideos -> {
                                val isLive = section == HomeSection.LiveShows
                                val videos = if (isLive) liveVideos else musicVideos
                                homeItem("${section.id}_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(if (isLive) Res.string.live_shows else Res.string.music_videos_for_you),
                                        onClick = { navController.navigate("section_detail/${section.id}") },
                                    )
                                }
                                homeItem("${section.id}_list", revealSeen, "cardRow") {
                                    HomeCardRow(section.id, videos, revealSeen, key = { it.item.id }, content = videoRowCard)
                                }
                            }

                            is HomeSection.Category -> {
                                val category = categories.firstOrNull { it.id == section.categoryId } ?: return@forEach
                                homeItem("${section.id}_title", revealSeen, "title") {
                                    val title = when (category.kind) {
                                        HomeCategory.Kind.NewFromArtists -> stringResource(Res.string.home_new_from_your_artists)
                                        else -> category.title
                                    }
                                    HomeSectionTitle(
                                        title = title,
                                        label = when (category.kind) {
                                            HomeCategory.Kind.BecauseYouLike -> stringResource(Res.string.home_because_you_listen_to)
                                            HomeCategory.Kind.MoreLikeArtist -> stringResource(Res.string.home_more_like)
                                            HomeCategory.Kind.YourGenre -> stringResource(Res.string.home_because_you_listen_to)
                                            HomeCategory.Kind.NewGenre -> stringResource(Res.string.home_try_something_new)
                                            HomeCategory.Kind.NewFromArtists -> null
                                        },
                                        onClick = category.browseId?.let { { navController.navigate("genre/$it") } },
                                        onPlayAllClick = { playYtItems(category.items, category.title) },
                                    )
                                }
                                homeItem("${section.id}_list", revealSeen, "cardRow") {
                                    HomeCardRow(section.id, category.items, revealSeen, key = { it.id }, content = ytItemCard)
                                }
                            }

                            HomeSection.AccountPlaylists -> {
                                homeItem("account_playlists_title", revealSeen, "title") {
                                    HomeSectionTitle(label = "Your Playlists", title = "Your Library")
                                }
                                homeItem("account_playlists_list", revealSeen, "cardRow") {
                                    LazyRow(
                                        state = rememberCardRowState(),
                                        contentPadding = PaddingValues(horizontal = HomeContentInset),
                                        horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
                                    ) {
                                        items(count = localPlaylists.size, key = { localPlaylists[it].id }) { index ->
                                            localItemCard(localPlaylists[index])
                                        }
                                        items(count = accountPlaylists.size, key = { accountPlaylists[it].id }) { index ->
                                            ytItemCard(accountPlaylists[index])
                                        }
                                    }
                                }
                            }

                            HomeSection.DontForgetTheseSongs -> {
                                homeItem("forgotten_favorites_title", revealSeen, "title") {
                                    val forgottenTitle = stringResource(Res.string.dont_forget_these_songs)
                                    HomeSectionTitle(
                                        title = forgottenTitle,
                                        onClick = { navController.navigate("section_detail/forgotten_favorites") },
                                        onPlayAllClick = { playAll(forgottenFavorites, forgottenTitle) },
                                    )
                                }
                                homeItem("forgotten_favorites_list", revealSeen, "trackGrid") {
                                    HomeTrackGrid(
                                        items = forgottenFavorites,
                                        state = rememberTrackGridState(),
                                        rowSpacing = 6.dp,
                                        columnWidth = trackColumnWidth,
                                        key = { _, song -> song.id },
                                        content = trackRow,
                                    )
                                }
                            }

                            is HomeSection.HomePageSection -> {
                                val sectionData = homePageSections.getOrNull(section.index) ?: return@forEach
                                homeItem("home_section_title_${section.id}", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = sectionData.title,
                                        label = sectionData.label,
                                        onClick = sectionData.browseId?.let { { navController.navigate("browse/$it") } },
                                        onPlayAllClick = { playYtItems(sectionData.items, sectionData.title) },
                                    )
                                }
                                homeItem("home_section_list_${section.id}", revealSeen, "cardRow") {
                                    HomeCardRow("home_section_${section.id}", sectionData.items, revealSeen, key = { it.id }, content = ytItemCard)
                                }
                            }
                        }

                        item(key = "section_spacer_${section.id}", contentType = "spacer") {
                            Spacer(modifier = Modifier.height(34.dp))
                        }
                    }

                    item(key = "bottom_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }

                if (heroScrollProgress > 0.01f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = heroScrollProgress },
                    ) {
                        ProgressiveGlassBackground(
                            state = homeGlassState,
                            maxBlurRadius = { 42f * heroScrollProgress },
                            foundationColor = GlassTokens.Base.copy(alpha = 0.72f),
                            direction = BlurDirection.BottomToTop,
                            steps = 3,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                HomeTopBar(
                    progress = topBarProgress,
                    onSettings = { navController.navigate("settings") },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

private fun playTracks(tracks: List<HomeTrack>, title: String, controller: PlaybackController?) {
    if (controller == null || tracks.isEmpty()) return
    controller.playQueue(ListQueue(title = title, items = tracks.map(HomeTrack::toPlaybackTrack)))
}

/** Adapts a HomeTrack card lambda to the shared YTItem card used by several shelves. */
@Composable
private fun ytCardFromTrack(
    content: @Composable (YTItem) -> Unit,
): @Composable (HomeTrack) -> Unit = { track -> content(track.toSongItem()) }

private fun MediaMetadata.toFakeSong(): Song = Song(
    song = SongEntity(
        id = id,
        title = title,
        duration = duration,
        thumbnailUrl = thumbnailUrl,
        explicit = explicit,
        liked = liked,
        localUri = localUri,
    ),
    artists = artists.map { artist ->
        ArtistEntity(id = artist.id ?: "unknown-${artist.name}", name = artist.name)
    },
    album = album?.let { album ->
        AlbumEntity(id = album.id, title = album.title, songCount = 0, duration = 0)
    },
)

private fun fakePlaylist(songs: List<Song>): Playlist = Playlist(
    playlist = PlaylistEntity(name = "Demo Mix", browseId = "demo-mix"),
    songCount = songs.size,
    songThumbnails = songs.map { it.thumbnailUrl },
)

private fun fakeSpeedDial(songs: List<Song>): List<SpeedDialItem> = songs.take(5).map { song ->
    SpeedDialItem(
        id = song.id,
        title = song.title,
        subtitle = song.artists.joinToString(", ") { it.name },
        thumbnailUrl = song.thumbnailUrl,
        type = "SONG",
    )
}

private fun demoCategories(songs: List<Song>): List<HomeCategory> =
    songs.groupBy { it.artists.firstOrNull()?.name ?: "Various Artists" }
        .entries
        .take(4)
        .mapIndexed { index, entry ->
            HomeCategory(
                id = "category_$index",
                title = entry.key,
                label = "Because you listen to",
                thumbnailUrl = entry.value.firstOrNull()?.thumbnailUrl,
                items = entry.value.map(Song::toSongItem),
                kind = HomeCategory.Kind.YourGenre,
                browseId = "genre_$index",
            )
        }

private fun demoHomePage(songs: List<Song>): List<HomePageSectionData> = listOf(
    HomePageSectionData(
        title = "Made for you",
        label = "Because you listen to",
        thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
        items = songs.take(5).map(Song::toSongItem),
        browseId = "made_for_you",
    ),
    HomePageSectionData(
        title = "New in music",
        label = "Try something new",
        thumbnailUrl = songs.getOrNull(1)?.thumbnailUrl,
        items = songs.drop(2).take(5).map(Song::toSongItem),
        browseId = "new_releases",
    ),
)

private fun demoAccountPlaylists(songs: List<Song>): List<PlaylistItem> = listOf(
    PlaylistItem(
        id = "demo-discover",
        title = "Discover Weekly",
        author = YtArtist(name = "Musicfy", id = null),
        songCountText = "30 songs",
        thumbnail = songs.firstOrNull()?.thumbnailUrl,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    ),
)

private fun buildArtistGroups(songs: List<Song>): List<ArtistGroup> =
    songs.groupBy { it.artists.firstOrNull()?.name ?: "Various Artists" }
        .entries
        .take(4)
        .mapIndexed { index, entry ->
            ArtistGroup(
                artistName = entry.key,
                artistId = "artist_$index",
                artistThumbnailUrl = entry.value.firstOrNull()?.thumbnailUrl,
                items = entry.value.map(Song::toSongItem),
            )
        }

private fun Song.toHomeTrack(): HomeTrack = HomeTrack(
    id = id,
    title = title,
    artists = artists.map { it.name },
    thumbnailUrl = thumbnailUrl,
    duration = song.duration,
    albumTitle = album?.title,
    explicit = song.explicit,
    liked = song.liked,
    localUri = song.localUri,
)

private fun Song.toHeroTrack(): HeroTrack = toHomeTrack().toHeroTrack()

private fun Song.toSongItem(): SongItem = toHomeTrack().toSongItem()

private fun Song.toHomeVideo(): HomeVideo {
    val item = toSongItem()
    return HomeVideo(
        item = item,
        title = item.title,
        subtitle = artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
    )
}

private fun Playlist.toHomeTrack(): HomeTrack = HomeTrack(
    id = id,
    title = title,
    thumbnailUrl = thumbnails.firstOrNull(),
)

private fun Playlist.toCommunityItem(songs: List<Song>): CommunityPlaylistItem = CommunityPlaylistItem(
    playlist = CommunityPlaylist(
        id = id,
        title = title,
        thumbnailUrl = thumbnails.firstOrNull(),
        songCount = songCount,
        songCountText = pluralSongCount(songCount),
    ),
    songs = songs.take(5).map(Song::toSongItem),
)

private fun pluralSongCount(count: Int): String = "$count songs"

private fun SpeedDialItem.toHomeTrack(): HomeTrack = HomeTrack(
    id = id,
    title = title,
    artists = subtitle?.split(", ")?.filter { it.isNotBlank() }.orEmpty(),
    thumbnailUrl = thumbnailUrl,
    explicit = explicit,
)

private fun HomeTrack.toHeroTrack(): HeroTrack = HeroTrack(
    id = id,
    title = title,
    artists = artists,
    thumbnailUrl = thumbnailUrl,
    duration = duration,
    albumTitle = albumTitle,
    localUri = localUri,
)

private fun HomeTrack.toSongItem(): SongItem = SongItem(
    id = id,
    title = title,
    artists = artists.map { YtArtist(name = it, id = null) },
    thumbnail = thumbnailUrl ?: "",
    duration = duration.takeIf { it > 0 },
    explicit = explicit,
)

private fun HomeTrack.toPlaybackTrack(): PlaybackTrack {
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
            explicit = explicit,
            liked = liked,
            localUri = uri,
        ),
        source = if (uri != null) TrackSource.Local(uri) else TrackSource.Remote(id),
    )
}

private fun YTItem.toHomeTrack(): HomeTrack = when (this) {
    is SongItem -> HomeTrack(
        id = id,
        title = title,
        artists = artists.map { it.name },
        thumbnailUrl = thumbnail,
        duration = duration ?: 0,
        albumTitle = album?.name,
        explicit = explicit,
    )
    is AlbumItem -> HomeTrack(
        id = id,
        title = title,
        artists = artists.orEmpty().map { it.name },
        thumbnailUrl = thumbnail,
        albumTitle = title,
        explicit = explicit,
    )
    is ArtistItem -> HomeTrack(
        id = id,
        title = title,
        artists = listOf(title),
        thumbnailUrl = thumbnail,
        explicit = explicit,
    )
    is PlaylistItem -> HomeTrack(
        id = id,
        title = title,
        artists = listOfNotNull(author?.name),
        thumbnailUrl = thumbnail,
    )
    else -> HomeTrack(id = id, title = title, thumbnailUrl = thumbnail, explicit = explicit)
}
