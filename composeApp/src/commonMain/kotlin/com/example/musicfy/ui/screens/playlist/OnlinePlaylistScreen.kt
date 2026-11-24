package com.example.musicfy.ui.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.HomeCoverCard
import com.example.musicfy.ui.component.YouTubeListItem
import com.example.musicfy.ui.component.detail.CreatorUi
import com.example.musicfy.ui.component.detail.PlaylistDetailScaffold
import com.example.musicfy.ui.component.detail.PlaylistEndOfLine
import com.example.musicfy.ui.component.detail.PlaylistHeader
import com.example.musicfy.ui.component.detail.PlaylistModeBar
import com.example.musicfy.ui.component.detail.PlaylistSectionTitle
import com.example.musicfy.ui.component.detail.PlaylistSelectTick
import com.example.musicfy.ui.component.detail.PlaylistTrackBones
import com.example.musicfy.ui.component.detail.PlaylistTrackRow
import com.example.musicfy.ui.screens.ScreenStateCard
import com.example.musicfy.ui.screens.detailStats
import com.example.musicfy.ui.screens.formatDuration
import com.example.musicfy.ui.screens.itemSubtitle
import com.example.musicfy.ui.screens.openYouTubeItem
import com.example.musicfy.ui.screens.playSongList
import com.example.musicfy.ui.screens.toRemoteTrack
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.viewmodels.OnlinePlaylistViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

/** KMP port of the YouTube playlist detail page with continuation paging. */
@Composable
fun OnlinePlaylistScreen(
    navController: NavHostController,
    playlistId: String,
    pureBlack: Boolean = false,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above OnlinePlaylistScreen"
    }
    val viewModel = appViewModel<OnlinePlaylistViewModel>(
        key = "online_playlist_$playlistId",
        factory = containerViewModelFactory(container) { appContainer ->
            OnlinePlaylistViewModel(appContainer, playlistId)
        },
    )

    val player = LocalPlaybackController.current
    val currentMetadataState = player?.currentMetadata?.collectAsState()
    val isPlaying = player?.transportState?.collectAsState()?.value?.isPlaying == true

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val relatedItems by viewModel.relatedItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val dbPlaylist by viewModel.dbPlaylist.collectAsState()

    val totalSeconds = remember(songs) { songs.sumOf { it.duration ?: 0 } }
    val lazyListState = rememberLazyListState()
    var inSelectMode by remember { mutableStateOf(false) }
    val selection = remember { mutableStateListOf<String>() }
    fun exitSelection() {
        inSelectMode = false
        selection.clear()
    }

    val stateStyle = remember(pureBlack) {
        GlassStyle(
            containerColor = if (pureBlack) GlassTokens.Base.copy(alpha = 0.92f) else GlassTokens.Chrome,
        )
    }

    LaunchedEffect(lazyListState, songs.size, isLoadingMore) {
        snapshotFlow {
            val info = lazyListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }.collect { nearEnd ->
            if (nearEnd && !isLoadingMore && songs.isNotEmpty()) viewModel.loadMoreSongs()
        }
    }

    fun play(startIndex: Int = 0, shuffled: Boolean = false) {
        playSongList(songs, player, playlist?.title.orEmpty(), startIndex, shuffled)
    }

    PlaylistDetailScaffold(
        sharedElementKey = "online_playlist-$playlistId",
        coverUrl = playlist?.thumbnail,
        lazyListState = lazyListState,
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        contentPadding = PaddingValues(bottom = 96.dp),
        modeBar = if (inSelectMode) {
            PlaylistModeBar.Select(
                count = selection.size,
                allSelected = selection.size == songs.size && songs.isNotEmpty(),
                onToggleAll = {
                    val selectAll = selection.size != songs.size
                    selection.clear()
                    if (selectAll) selection.addAll(songs.map { it.id })
                },
                onMore = {
                    val picked = songs.filter { it.id in selection }
                    if (picked.isNotEmpty()) {
                        player?.playQueue(
                            ListQueue(
                                title = playlist?.title.orEmpty(),
                                items = picked.map { it.toRemoteTrack() },
                            ),
                        )
                    }
                    exitSelection()
                },
                onClose = ::exitSelection,
            )
        } else {
            null
        },
    ) {
        if (isLoading && playlist == null) {
            item(key = "playlist_loading_space") {
                Spacer(Modifier.height(260.dp))
            }
            item(key = "playlist_loading") {
                PlaylistTrackBones()
            }
            return@PlaylistDetailScaffold
        }

        val currentPlaylist = playlist ?: run {
            item(key = "playlist_error") {
                ScreenStateCard(
                    title = error ?: "Couldn't load this playlist",
                    message = "Check your connection and try again.",
                    onRetry = viewModel::retry,
                    style = stateStyle,
                )
            }
            return@PlaylistDetailScaffold
        }

        item(key = "playlist_header") {
            val isThisPlaylist = currentPlaylist.id == currentMetadataState?.value?.album?.id
            PlaylistHeader(
                title = currentPlaylist.title,
                creators = listOfNotNull(
                    currentPlaylist.author?.name?.let { name ->
                        CreatorUi(name = name, artistId = currentPlaylist.author?.id)
                    },
                ),
                stats = detailStats(songs.size, totalSeconds, currentPlaylist.songCountText),
                isPlaying = isThisPlaylist && isPlaying,
                onPlayClick = {
                    when {
                        isThisPlaylist && isPlaying -> player?.togglePlayPause()
                        isThisPlaylist -> player?.play()
                        else -> play()
                    }
                },
                onShuffleClick = { play(shuffled = true) },
                isSaved = dbPlaylist != null,
                onSaveClick = { /* bookkeeping is owned by the library screen */ },
            )
        }

        itemsIndexed(
            items = songs,
            key = { _, song -> song.id },
        ) { index, song ->
            PlaylistTrackRow(
                thumbnailUrl = song.thumbnail,
                songId = song.id,
                title = song.title,
                subtitle = listOfNotNull(
                    song.artists.joinToString { it.name }.ifBlank { null },
                    song.duration?.let(::formatDuration),
                ).joinToString(" · "),
                isActive = song.id == currentMetadataState?.value?.id,
                isPlaying = isPlaying,
                onClick = {
                    if (inSelectMode) {
                        if (song.id in selection) selection.remove(song.id) else selection.add(song.id)
                    } else if (song.id == currentMetadataState?.value?.id) {
                        player?.togglePlayPause()
                    } else {
                        play(startIndex = index)
                    }
                },
                onLongClick = {
                    inSelectMode = true
                    if (song.id !in selection) selection.add(song.id)
                },
                onMenuClick = { play(startIndex = index) },
                selected = inSelectMode && song.id in selection,
                trailing = if (inSelectMode) {
                    { PlaylistSelectTick(checked = song.id in selection) }
                } else {
                    null
                },
            )
        }

        if (isLoadingMore) {
            item(key = "playlist_loading_more") {
                androidx.compose.material3.Text(
                    text = "Loading more songs…",
                    color = GlassTokens.MutedContent,
                    modifier = Modifier.fillMaxWidth().paddingHorizontal(),
                )
            }
        }

        item(key = "playlist_end_of_line") {
            PlaylistEndOfLine(songCount = songs.size, totalSeconds = totalSeconds)
        }

        val relatedPlaylists = relatedItems.filterIsInstance<PlaylistItem>().distinctBy { it.id }
        if (relatedPlaylists.isNotEmpty()) {
            item(key = "related_playlists_title") {
                PlaylistSectionTitle(title = "More playlists")
            }
            item(key = "related_playlists_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items = relatedPlaylists, key = { it.id }) { item ->
                        HomeCoverCard(
                            title = item.title,
                            subtitle = itemSubtitle(item),
                            thumbnailUrl = item.thumbnail,
                            modifier = Modifier.clickable {
                                openYouTubeItem(item, navController, player, currentMetadataState?.value?.id)
                            },
                        )
                    }
                }
            }
        }

        val relatedAlbums = relatedItems.filterIsInstance<AlbumItem>().distinctBy { it.id }
        if (relatedAlbums.isNotEmpty()) {
            item(key = "related_albums_title") {
                PlaylistSectionTitle(title = "Albums")
            }
            item(key = "related_albums_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items = relatedAlbums, key = { it.id }) { item ->
                        HomeCoverCard(
                            title = item.title,
                            subtitle = itemSubtitle(item),
                            thumbnailUrl = item.thumbnail,
                            modifier = Modifier.clickable {
                                openYouTubeItem(item, navController, player, currentMetadataState?.value?.id)
                            },
                        )
                    }
                }
            }
        }

        val relatedSongs = relatedItems.filter { it is SongItem || it !is PlaylistItem && it !is AlbumItem }
        if (relatedSongs.isNotEmpty()) {
            item(key = "related_songs_title") {
                PlaylistSectionTitle(title = "You might also like")
            }
            items(items = relatedSongs, key = { it.id }) { item: YTItem ->
                YouTubeListItem(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            openYouTubeItem(item, navController, player, currentMetadataState?.value?.id)
                        }
                        .paddingHorizontal(),
                )
            }
        }

        item(key = "playlist_bottom_spacer") {
            Spacer(Modifier.height(56.dp))
        }
    }
}

private fun Modifier.paddingHorizontal(): Modifier =
    this.padding(horizontal = 20.dp, vertical = 4.dp)
