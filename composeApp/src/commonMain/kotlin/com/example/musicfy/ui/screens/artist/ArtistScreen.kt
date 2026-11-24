package com.example.musicfy.ui.screens.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.queue_music
import com.example.musicfy.ui.component.BackHandler
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.HomeCoverCard
import com.example.musicfy.ui.component.HomeSectionTitle
import com.example.musicfy.ui.component.HomeTrackRow
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.screens.ScreenStateCard
import com.example.musicfy.ui.screens.formatDuration
import com.example.musicfy.ui.screens.itemSubtitle
import com.example.musicfy.ui.screens.openYouTubeItem
import com.example.musicfy.ui.screens.playSongList
import com.example.musicfy.ui.screens.toLibraryTrack
import com.example.musicfy.viewmodels.ArtistViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** KMP port of the artist page: banner, YouTube shelves, saved previews and subscribe. */
@Composable
fun ArtistScreen(
    navController: NavHostController,
    artistId: String,
    pureBlack: Boolean = false,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above ArtistScreen"
    }
    val viewModel = appViewModel<ArtistViewModel>(
        key = "artist_$artistId",
        factory = containerViewModelFactory(container) { appContainer ->
            ArtistViewModel(appContainer, artistId)
        },
    )

    val player = LocalPlaybackController.current
    val currentMetadataState = player?.currentMetadata?.collectAsState()
    val isPlaying = player?.transportState?.collectAsState()?.value?.isPlaying == true

    val page by viewModel.page.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val subscribed by viewModel.subscribed.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val glassState = remember { GlassState() }
    val listState = rememberLazyListState()

    val name = page?.artist?.title.orEmpty()
    val photo = page?.artist?.thumbnail
    val stats = listOfNotNull(
        page?.subscriberCountText,
        page?.monthlyListenerCount,
    ).joinToString(" · ")

    val stateStyle = remember(pureBlack) {
        GlassStyle(
            containerColor = if (pureBlack) GlassTokens.Base.copy(alpha = 0.92f) else GlassTokens.Chrome,
        )
    }

    fun playTopSongs() {
        coroutineScope.launch {
            val top = if (page != null) viewModel.topSongs() else emptyList()
            when {
                top.isNotEmpty() -> playSongList(top, player, name)
                librarySongs.isNotEmpty() -> player?.playQueue(
                    ListQueue(
                        title = name,
                        items = librarySongs.map { it.toLibraryTrack() },
                    ),
                )
            }
        }
    }

    fun openMore(browseId: String, params: String?) {
        navController.navigate("artist/$artistId/items?browseId=$browseId?params=${params.orEmpty()}")
    }

    BackHandler { navController.navigateUp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .glassRoot(glassState),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "artist_hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .glassSurface(
                            shape = RoundedCornerShape(28.dp),
                            style = GlassStyle(
                                containerColor = GlassTokens.Chrome,
                                cornerRadius = 28.dp,
                            ),
                        ),
                ) {
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(Res.drawable.queue_music),
                            error = painterResource(Res.drawable.queue_music),
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.Top,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(20.dp),
                    ) {
                        Text(
                            text = name,
                            color = Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
                                .copy(fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (stats.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(text = stats, color = GlassTokens.MutedContent, maxLines = 1)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .glassSurface(shape = CircleShape, style = GlassStyle(elevation = 0.dp))
                                    .clickable { if (isPlaying) player?.togglePlayPause() else playTopSongs() }
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = if (isPlaying) "Pause" else "Play",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .glassSurface(shape = CircleShape, style = GlassStyle(elevation = 0.dp))
                                    .clickable { playTopSongs() }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(text = "Shuffle", color = Color.White)
                            }
                            if (page?.artist?.channelId != null) {
                                Spacer(Modifier.width(10.dp))
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .glassSurface(shape = CircleShape, style = GlassStyle(elevation = 0.dp))
                                        .clickable { viewModel.toggleSubscribe() }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = if (subscribed == true) "Following" else "Follow",
                                        color = if (subscribed == true) GlassTokens.Pink else Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (page == null) {
                item(key = "artist_loading") {
                    if (loadFailed) {
                        ScreenStateCard(
                            title = "Couldn't load this artist",
                            message = "Check your connection and try again.",
                            onRetry = viewModel::retry,
                            style = stateStyle,
                        )
                    } else {
                        ScreenStateCard(title = "Loading artist…", style = stateStyle)
                    }
                }
            }

            page?.sections?.forEach { section ->
                val songs = section.items.filterIsInstance<SongItem>().distinctBy { it.id }
                val covers = section.items.filterNot { it is SongItem }.distinctBy { it.id }
                if (songs.isEmpty() && covers.isEmpty()) return@forEach

                item(key = "section_${section.title}") {
                    HomeSectionTitle(
                        title = section.title,
                        onClick = section.moreEndpoint?.let { endpoint ->
                            { openMore(endpoint.browseId, endpoint.params) }
                        },
                    )
                }

                if (songs.isNotEmpty()) {
                    items(items = songs.take(5), key = { "song_${it.id}" }) { song ->
                        HomeTrackRow(
                            thumbnailUrl = song.thumbnail,
                            songId = song.id,
                            title = song.title,
                            subtitle = song.album?.name ?: song.artists.joinToString { it.name },
                            isActive = song.id == currentMetadataState?.value?.id,
                            isPlaying = isPlaying,
                            onClick = {
                                if (song.id == currentMetadataState?.value?.id) {
                                    player?.togglePlayPause()
                                } else {
                                    playSongList(listOf(song), player, song.title)
                                }
                            },
                            onLongClick = {},
                            onMoreClick = null,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        )
                    }
                }

                if (covers.isNotEmpty()) {
                    item(key = "covers_${section.title}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(items = covers, key = { it.id }) { item: YTItem ->
                                HomeCoverCard(
                                    title = item.title,
                                    subtitle = itemSubtitle(item),
                                    thumbnailUrl = item.thumbnail,
                                    circular = item is com.music.innertube.models.ArtistItem,
                                    modifier = Modifier.clickable {
                                        openYouTubeItem(item, navController, player, currentMetadataState?.value?.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (librarySongs.isNotEmpty()) {
                item(key = "library_songs_title") {
                    HomeSectionTitle(
                        title = "Saved songs",
                        onClick = { navController.navigate("artist/$artistId/songs") },
                    )
                }
                items(items = librarySongs.take(3), key = { "library_song_${it.id}" }) { song ->
                    HomeTrackRow(
                        thumbnailUrl = song.thumbnailUrl,
                        songId = song.id,
                        title = song.title,
                        subtitle = formatDuration(song.song.duration),
                        isActive = song.id == currentMetadataState?.value?.id,
                        isPlaying = isPlaying,
                        onClick = {
                            if (song.id == currentMetadataState?.value?.id) {
                                player?.togglePlayPause()
                            } else {
                                player?.playQueue(
                                    ListQueue(
                                        title = name,
                                        items = librarySongs.map { it.toLibraryTrack() },
                                        startIndex = librarySongs.indexOf(song).coerceAtLeast(0),
                                    ),
                                )
                            }
                        },
                        onLongClick = {},
                        onMoreClick = null,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
            }

            if (libraryAlbums.isNotEmpty()) {
                item(key = "library_albums_title") {
                    HomeSectionTitle(
                        title = "Saved albums",
                        onClick = { navController.navigate("artist/$artistId/albums") },
                    )
                }
                item(key = "library_albums_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(items = libraryAlbums.take(8), key = { it.id }) { album ->
                            HomeCoverCard(
                                title = album.album.title,
                                subtitle = album.album.year?.toString(),
                                thumbnailUrl = album.album.thumbnailUrl,
                                modifier = Modifier.clickable { navController.navigate("album/${album.id}") },
                            )
                        }
                    }
                }
            }

            if (!page?.description.isNullOrBlank()) {
                item(key = "artist_about") {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        style = stateStyle,
                    ) {
                        Text(
                            text = page?.description.orEmpty(),
                            color = Color.White,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            item(key = "artist_bottom_spacer") {
                Spacer(Modifier.height(56.dp))
            }
        }
    }
}
