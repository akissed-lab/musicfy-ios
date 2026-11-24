// LocalSearchScreen.kt
//
// WP5d port of the upstream library-only results screen. Android-only pieces
// (Hilt, LocalPlayerAwareWindowInsets, Activity keyboard controller, SongMenu,
// animateItem, LocalContext) are replaced with commonMain primitives. The
// ported list items from ui/component/Items.kt already use the shared glass
// system; the filter row / section headers / empty state below are local glass
// chrome so this file does not depend on the parallel SearchDesign.kt.
//
// TODO(menu): wire SongMenu / the common PopupSheet once that menu is ported;
// the overflow buttons are intentionally inert for now.

package com.example.musicfy.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.TrackSource
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.filter_albums
import com.example.musicfy.resources.filter_all
import com.example.musicfy.resources.filter_artists
import com.example.musicfy.resources.filter_playlists
import com.example.musicfy.resources.filter_songs
import com.example.musicfy.resources.ic_chevron_right
import com.example.musicfy.resources.more_vert
import com.example.musicfy.resources.music_note
import com.example.musicfy.resources.no_results_found
import com.example.musicfy.resources.queue_searched_songs
import com.example.musicfy.ui.component.AlbumListItem
import com.example.musicfy.ui.component.ArtistListItem
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.ListItemHeight
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.PlaylistListItem
import com.example.musicfy.ui.component.SongListItem
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.viewmodels.LocalFilter
import com.example.musicfy.viewmodels.LocalSearchViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.viewModelFactory
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Convenience entry point used by the navigation graph: no Hilt here, the
 * container-backed view model is created from [LocalAppContainer].
 */
@Composable
fun LocalSearchScreen(
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
    pureBlack: Boolean,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above LocalSearchScreen"
    }
    val viewModel = appViewModel<LocalSearchViewModel>(
        factory = viewModelFactory { LocalSearchViewModel(container) },
    )
    LocalSearchScreen(
        query = query,
        navController = navController,
        onDismiss = onDismiss,
        isFromCache = isFromCache,
        pureBlack = pureBlack,
        viewModel = viewModel,
    )
}

/**
 * Injectable overload kept for parity with upstream's `viewModel` parameter.
 * The default Hilt argument is intentionally replaced by the overload above.
 */
@Composable
fun LocalSearchScreen(
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
    pureBlack: Boolean,
    viewModel: LocalSearchViewModel,
) {
    val player = LocalPlaybackController.current ?: return
    val transportState by player.transportState.collectAsState()
    val currentMetadata by player.currentMetadata.collectAsState()
    val searchFilter by viewModel.filter.collectAsState()
    val result by viewModel.result.collectAsState()
    val queueTitle = stringResource(Res.string.queue_searched_songs)

    LaunchedEffect(query) {
        viewModel.query.value = query
    }

    val distinctResultMap = result.map.mapValues { (_, sectionItems) ->
        sectionItems.distinctBy { it.id }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (pureBlack) GlassTokens.Base else Color.Transparent),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "local_filters") {
                LocalFilterChips(
                    selected = searchFilter,
                    onSelect = { viewModel.filter.value = it },
                )
            }

            distinctResultMap.forEach { (filter, items) ->
                if (result.filter == LocalFilter.ALL) {
                    item(key = "section_${filter.name}") {
                        LocalSectionHeader(
                            title = filterTitle(filter),
                            onClick = { viewModel.filter.value = filter },
                        )
                    }
                }

                itemsIndexed(
                    items = items,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "localResult" },
                ) { _, item ->
                    when (item) {
                        is Song -> SongListItem(
                            song = item,
                            showInLibraryIcon = true,
                            isActive = item.id == currentMetadata?.id,
                            isPlaying = transportState.isPlaying,
                            trailingContent = {
                                IconButton(onClick = { /* TODO(menu): SongMenu is not ported yet */ }) {
                                    Icon(
                                        painter = painterResource(Res.drawable.more_vert),
                                        contentDescription = null,
                                        tint = GlassTokens.MutedContent,
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                if (item.id == currentMetadata?.id) {
                                    player.togglePlayPause()
                                } else {
                                    val tracks = result.map[LocalFilter.SONG]
                                        .orEmpty()
                                        .filterIsInstance<Song>()
                                        .map { it.toPlaybackTrack() }
                                    player.playQueue(
                                        ListQueue(
                                            title = queueTitle,
                                            items = tracks,
                                            startIndex = tracks
                                                .indexOfFirst { it.mediaId == item.id }
                                                .coerceAtLeast(0),
                                        ),
                                    )
                                }
                            },
                        )

                        is Album -> AlbumListItem(
                            album = item,
                            isActive = item.id == currentMetadata?.album?.id,
                            isPlaying = transportState.isPlaying,
                            modifier = Modifier.clickable {
                                onDismiss()
                                navController.navigate("album/${item.id}")
                            },
                        )

                        is Artist -> ArtistListItem(
                            artist = item,
                            modifier = Modifier.clickable {
                                onDismiss()
                                navController.navigate("artist/${item.id}")
                            },
                        )

                        is Playlist -> PlaylistListItem(
                            playlist = item,
                            modifier = Modifier.clickable {
                                onDismiss()
                                navController.navigate("local_playlist/${item.id}")
                            },
                        )
                    }
                }
            }

            if (result.query.isNotEmpty() && result.map.isEmpty()) {
                item(key = "no_results") {
                    LocalEmptyPlaceholder()
                }
            }
        }
    }
}

/** Convert a local library song into the controller's playable model. */
private fun Song.toPlaybackTrack(): PlaybackTrack = PlaybackTrack(
    mediaId = id,
    metadata = MediaMetadata(
        id = id,
        title = title,
        artists = artists.map { artist -> MediaMetadata.Artist(id = artist.id, name = artist.name) },
        duration = song.duration,
        thumbnailUrl = thumbnailUrl,
        explicit = song.explicit,
        liked = song.liked,
        inLibrary = song.inLibrary,
        localUri = song.localUri,
    ),
    source = song.localUri?.let { TrackSource.Local(it) } ?: TrackSource.Remote(id),
)

@Composable
private fun filterTitle(filter: LocalFilter): String = stringResource(
    when (filter) {
        LocalFilter.SONG -> Res.string.filter_songs
        LocalFilter.ALBUM -> Res.string.filter_albums
        LocalFilter.ARTIST -> Res.string.filter_artists
        LocalFilter.PLAYLIST -> Res.string.filter_playlists
        LocalFilter.ALL -> Res.string.filter_all
    },
)

// -------------------------------------------------------------------------------------------------
// Local glass chrome. TODO(search-design): replace with the shared SearchDesign chips/header once
// that component is available; these helpers are private so they cannot collide with it.
// -------------------------------------------------------------------------------------------------

@Composable
private fun LocalFilterChips(
    selected: LocalFilter,
    onSelect: (LocalFilter) -> Unit,
) {
    val accent = LocalGlassState.current.accent
    val chips = listOf(
        LocalFilter.ALL to Res.string.filter_all,
        LocalFilter.SONG to Res.string.filter_songs,
        LocalFilter.ALBUM to Res.string.filter_albums,
        LocalFilter.ARTIST to Res.string.filter_artists,
        LocalFilter.PLAYLIST to Res.string.filter_playlists,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .glassSurface(
                shape = RoundedCornerShape(20.dp),
                style = GlassStyle(elevation = 0.dp),
            )
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { (filter, label) ->
            val active = filter == selected
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Color.White else GlassTokens.MutedContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { onSelect(filter) }
                    .glassSurface(
                        shape = CircleShape,
                        style = GlassStyle(
                            containerColor = if (active) {
                                accent.copy(alpha = 0.28f)
                            } else {
                                Color.White.copy(alpha = 0.06f)
                            },
                            borderColor = if (active) {
                                accent.copy(alpha = 0.55f)
                            } else {
                                Color.White.copy(alpha = 0.12f)
                            },
                            elevation = 0.dp,
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LocalSectionHeader(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ListItemHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            tint = GlassTokens.MutedContent,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LocalEmptyPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 28.dp)
            .glassSurface(
                shape = RoundedCornerShape(22.dp),
                style = GlassStyle(elevation = 0.dp),
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(Res.drawable.music_note),
            contentDescription = null,
            tint = GlassTokens.MutedContent,
            modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.no_results_found),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
    }
}
