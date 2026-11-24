package com.example.musicfy.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.HomeCoverCard
import com.example.musicfy.ui.component.detail.CreatorUi
import com.example.musicfy.ui.component.detail.FeaturedArtistsRow
import com.example.musicfy.ui.component.detail.PlaylistDetailScaffold
import com.example.musicfy.ui.component.detail.PlaylistEndOfLine
import com.example.musicfy.ui.component.detail.PlaylistHeader
import com.example.musicfy.ui.component.detail.PlaylistModeBar
import com.example.musicfy.ui.component.detail.PlaylistSectionTitle
import com.example.musicfy.ui.component.detail.PlaylistSelectTick
import com.example.musicfy.ui.component.detail.PlaylistTrackBones
import com.example.musicfy.ui.component.detail.PlaylistTrackRow
import com.example.musicfy.ui.component.detail.featuredArtistsOf
import com.example.musicfy.ui.utils.backToMain
import com.example.musicfy.viewmodels.AlbumViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory
import com.music.innertube.models.AlbumItem

/** KMP port of the album detail page: local tracks first, YouTube metadata as it arrives. */
@Composable
fun AlbumScreen(
    navController: NavHostController,
    albumId: String,
    pureBlack: Boolean = false,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above AlbumScreen"
    }
    val viewModel = appViewModel<AlbumViewModel>(
        key = "album_$albumId",
        factory = containerViewModelFactory(container) { appContainer ->
            AlbumViewModel(appContainer, albumId)
        },
    )

    val player = LocalPlaybackController.current
    val currentMetadataState = player?.currentMetadata?.collectAsState()
    val isPlaying = player?.transportState?.collectAsState()?.value?.isPlaying == true

    val playlistId by viewModel.playlistId.collectAsState()
    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val otherVersions by viewModel.otherVersions.collectAsState()
    val releasesForYou by viewModel.releasesForYou.collectAsState()
    val moreByArtist by viewModel.moreByArtist.collectAsState()
    val description by viewModel.description.collectAsState()
    val descriptionRuns by viewModel.descriptionRuns.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()

    val hideExplicit by rememberPreference(SettingsKeys.HideExplicitKey, false)
    val hideVideoSongs by rememberPreference(SettingsKeys.HideVideoSongsKey, false)
    val album = albumWithSongs
    val allSongs = album?.songs.orEmpty()
    val songs = remember(allSongs, hideExplicit, hideVideoSongs) {
        allSongs.filter { song ->
            (hideExplicit.not() || song.song.explicit.not()) &&
                (hideVideoSongs.not() || song.song.isVideo.not())
        }
    }
    val totalSeconds = remember(songs) { songs.sumOf { it.song.duration } }
    val featuredArtists = remember(songs) {
        featuredArtistsOf(songs.flatMap { it.artists }, id = { it.id }, name = { it.name })
    }

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

    fun play(startIndex: Int = 0, shuffled: Boolean = false) {
        val current = album ?: return
        if (current.songs.isEmpty()) return
        val ordered = if (shuffled) current.songs.shuffled() else current.songs
        val first = (if (shuffled) 0 else startIndex).coerceIn(0, ordered.lastIndex)
        player?.playQueue(
            ListQueue(
                title = current.album.title,
                items = ordered.map { it.toLibraryTrack() },
                startIndex = first,
            ),
        )
        if (isPlaying && currentMetadataState?.value?.album?.id == current.album.id) {
            player?.togglePlayPause()
        }
    }

    fun playSong(index: Int) {
        if (album == null) return
        val originalIndex = album.songs.indexOfFirst { it.id == songs.getOrNull(index)?.id }
        play(startIndex = originalIndex.coerceAtLeast(0))
    }

    PlaylistDetailScaffold(
        sharedElementKey = "album-${playlistId.ifBlank { albumId }}",
        coverUrl = album?.album?.thumbnailUrl,
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
                                title = album?.album?.title.orEmpty(),
                                items = picked.map { it.toLibraryTrack() },
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
        if (album == null || album.songs.isEmpty()) {
            item(key = "album_loading_space") {
                Spacer(Modifier.height(260.dp))
            }
            item(key = "album_loading") {
                if (loadFailed) {
                    ScreenStateCard(
                        title = "Couldn't load this album",
                        message = "Check your connection and try again.",
                        onRetry = viewModel::retry,
                        style = stateStyle,
                    )
                } else {
                    PlaylistTrackBones()
                }
            }
            return@PlaylistDetailScaffold
        }

        item(key = "album_header") {
            val isThisAlbum = currentMetadataState?.value?.album?.id == album.album.id
            PlaylistHeader(
                title = album.album.title,
                creators = album.artists.map { artist ->
                    CreatorUi(name = artist.name, avatarUrl = artist.thumbnailUrl, artistId = artist.id)
                },
                stats = detailStats(songs.size, totalSeconds, album.album.year?.toString()),
                description = description?.takeIf { it.isNotBlank() }
                    ?: descriptionRuns?.joinToString("") { it.text }?.takeIf { it.isNotBlank() },
                isPlaying = isThisAlbum && isPlaying,
                onPlayClick = {
                    when {
                        isThisAlbum && isPlaying -> player?.togglePlayPause()
                        isThisAlbum -> player?.play()
                        else -> play()
                    }
                },
                onShuffleClick = { play(shuffled = true) },
                onCreatorClick = { creator ->
                    creator.artistId?.let { navController.navigate("artist/$it") }
                },
            )
        }

        itemsIndexed(
            items = songs,
            key = { _, song -> song.id },
        ) { index, song ->
            PlaylistTrackRow(
                thumbnailUrl = song.song.thumbnailUrl,
                songId = song.id,
                title = song.song.title,
                subtitle = listOfNotNull(
                    song.artists.joinToString { it.name }.ifBlank { null },
                    formatDuration(song.song.duration),
                ).joinToString(" · "),
                isActive = song.id == currentMetadataState?.value?.id,
                isPlaying = isPlaying,
                onClick = {
                    if (inSelectMode) {
                        if (song.id in selection) selection.remove(song.id) else selection.add(song.id)
                    } else if (song.id == currentMetadataState?.value?.id) {
                        player?.togglePlayPause()
                    } else {
                        playSong(index)
                    }
                },
                onLongClick = {
                    inSelectMode = true
                    if (song.id !in selection) selection.add(song.id)
                },
                onMenuClick = { playSong(index) },
                selected = inSelectMode && song.id in selection,
                trailing = if (inSelectMode) {
                    { PlaylistSelectTick(checked = song.id in selection) }
                } else {
                    null
                },
            )
        }

        item(key = "album_end_of_line") {
            PlaylistEndOfLine(songCount = songs.size, totalSeconds = totalSeconds)
        }

        if (featuredArtists.isNotEmpty()) {
            item(key = "album_featured_artists") {
                FeaturedArtistsRow(
                    artists = featuredArtists,
                    onArtistClick = { navController.navigate("artist/${it.id}") },
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }

        albumShelf(
            key = "more_by_artist",
            title = album.artists.firstOrNull()?.name?.let { "More by $it" } ?: "More by this artist",
            albums = moreByArtist,
            onTitleClick = album.artists.firstOrNull()?.id?.let { id -> { navController.navigate("artist/$id") } },
            onClick = { navController.navigate("album/${it.id}") },
        )
        albumShelf(
            key = "other_versions",
            title = "Other versions",
            albums = otherVersions,
            onClick = { navController.navigate("album/${it.id}") },
        )
        albumShelf(
            key = "releases_for_you",
            title = "Releases for you",
            albums = releasesForYou,
            onClick = { navController.navigate("album/${it.id}") },
        )

        item(key = "album_bottom_spacer") {
            Spacer(Modifier.height(56.dp))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.albumShelf(
    key: String,
    title: String,
    albums: List<AlbumItem>,
    onClick: (AlbumItem) -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
    val distinct = albums.distinctBy { it.id }
    if (distinct.isEmpty()) return
    item(key = "${key}_title") {
        PlaylistSectionTitle(
            title = title,
            onClick = onTitleClick,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
    }
    item(key = "${key}_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = distinct, key = { it.id }) { item ->
                HomeCoverCard(
                    title = item.title,
                    subtitle = itemSubtitle(item),
                    thumbnailUrl = item.thumbnail,
                    sharedElementKey = "album-${item.id}",
                    modifier = Modifier.clickable { onClick(item) },
                )
            }
        }
    }
}
