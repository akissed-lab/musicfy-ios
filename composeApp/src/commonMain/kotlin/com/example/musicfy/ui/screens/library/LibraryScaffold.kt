// LibraryScaffold.kt
//
// The page frame every Library screen shares (glass title, search pill, the list
// below) and the search that runs inside it.
//
// TODO(search-design): upstream reused SearchColors / SearchField /
// SearchGlassTopBar here. Those helpers belong to another port, so this file
// keeps a small, self-contained glass header with the same read/write surface
// (query: TextFieldValue + collapseProvider) and no dependency on that package.

package com.example.musicfy.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.PlaybackController
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.TrackSource
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.search
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.screens.Routes
import com.example.musicfy.viewmodels.ShelfAlbum
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist as InnertubeArtist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import org.jetbrains.compose.resources.painterResource

/** Total height reserved for the locally drawn glass header. */
private val LibraryTopBarHeight: Dp = 152.dp

/**
 * Shared page frame for every Library screen: a collapsing glass title/subtitle,
 * a pinned search pill, and the caller's content below.
 *
 * Every Library screen carries its own search that only covers that screen's
 * contents, which is why the field lives in the scaffold rather than in one
 * global place.
 */
@Composable
fun LibraryScaffold(
    title: String,
    subtitle: String?,
    searchPlaceholder: String,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    listState: LazyListState,
    glassState: GlassState,
    collapseProvider: () -> Float,
    pureBlack: Boolean,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = LibraryTopBarHeight,
                bottom = bottomInset + 24.dp,
            ),
            content = content,
        )

        // The bar is a sibling of the list, never recorded into the glass surface it draws.
        LibraryGlassTopBar(
            glassState = glassState,
            progress = collapseProvider(),
            pureBlack = pureBlack,
            title = title,
            subtitle = subtitle,
            searchPlaceholder = searchPlaceholder,
            query = query,
            onQueryChange = onQueryChange,
            trailing = trailing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Local replacement for upstream `rememberCollapseProgress` (SearchDesign). */
@Composable
internal fun rememberLibraryCollapseProgress(listState: LazyListState): State<Float> =
    remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / 180f).coerceIn(0f, 1f)
            }
        }
    }

@Composable
private fun LibraryGlassTopBar(
    glassState: GlassState,
    progress: Float,
    pureBlack: Boolean,
    title: String,
    subtitle: String?,
    searchPlaceholder: String,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    trailing: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val pageAccent = LocalGlassState.current.accent
    val accent = if (glassState.glassEnabled) pageAccent else GlassTokens.MutedContent
    val p = progress.coerceIn(0f, 1f)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = shape,
                style = GlassStyle(
                    containerColor = if (pureBlack) {
                        GlassTokens.Base.copy(alpha = 0.86f + 0.1f * p)
                    } else {
                        GlassTokens.Chrome.copy(alpha = 0.78f + 0.18f * p)
                    },
                    borderColor = accent.copy(alpha = 0.12f + 0.18f * p),
                    cornerRadius = 28.dp,
                    elevation = 12.dp + 8.dp * p,
                ),
            )
            .padding(horizontal = LibraryHorizontalPadding)
            .padding(top = 22.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = GlassTokens.MutedContent,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val fieldShape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .glassSurface(
                    shape = fieldShape,
                    style = GlassStyle(containerColor = GlassTokens.Chrome.copy(alpha = 0.62f), elevation = 0.dp, cornerRadius = 22.dp),
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.text.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(Res.drawable.search),
                        contentDescription = null,
                        tint = GlassTokens.MutedContent,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = searchPlaceholder,
                        color = GlassTokens.MutedContent,
                        fontSize = 14.sp,
                    )
                }
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Search within the library
// ---------------------------------------------------------------------------------------------

internal fun androidx.compose.foundation.lazy.LazyListScope.librarySearchResults(
    results: List<YTItem>,
    query: String,
    onOpen: (YTItem) -> Unit,
) {
    if (results.isEmpty()) {
        item(key = "no_results") {
            Text(
                text = "Nothing in your library matches \"$query\"",
                color = GlassTokens.MutedContent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = LibraryHorizontalPadding, vertical = 24.dp)
                    .glassSurface(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        return
    }
    items(results, key = { it.id }) { result ->
        val subtitle = when (result) {
            is SongItem -> result.artists.joinToString { it.name }
            is AlbumItem -> "Album"
            is ArtistItem -> "Artist"
            is PlaylistItem -> "Playlist"
        }
        LibraryListRow(
            title = result.title,
            subtitle = subtitle,
            thumbnailUrl = result.thumbnail,
            onClick = { onOpen(result) },
            onLongClick = { onOpen(result) },
            round = result is ArtistItem,
            songId = (result as? SongItem)?.id,
            // TODO: shared-element transitions are Android-only; the key is kept for parity.
            sharedElementKey = result.id,
        )
    }
}

/**
 * Opens what a search result or pinned tile stands for. [librarySongs] is where
 * a song result is looked up first: a file imported from the device has no
 * YouTube id to start a radio from, so it plays from its file instead.
 *
 * Upstream took a `PlayerConnection`; the common port takes the shared
 * [PlaybackController] exposed by `LocalPlaybackController`.
 */
internal fun openLibraryItem(
    item: YTItem,
    navController: NavController,
    player: PlaybackController? = null,
    librarySongs: List<Song> = emptyList(),
) {
    when (item) {
        is SongItem -> {
            val file = librarySongs.firstOrNull { it.id == item.id }?.takeIf { it.song.isLocal }
            val track = if (file != null) {
                file.toPlaybackTrack()
            } else {
                // TODO: no YouTubeQueue/radio port yet; start with this track only.
                item.toRemotePlaybackTrack()
            }
            player?.playQueue(ListQueue(title = item.title, items = listOf(track)))
        }
        is AlbumItem -> navController.navigate(
            Routes.ALBUM.replace("{albumId}", item.browseId),
        )
        is ArtistItem -> navController.navigate(
            Routes.ARTIST.replace("{artistId}", item.id),
        )
        is PlaylistItem -> navController.navigate(
            if (item.id.startsWith("LP")) {
                Routes.LOCAL_PLAYLIST.replace("{playlistId}", item.id)
            } else {
                Routes.ONLINE_PLAYLIST.replace("{playlistId}", item.id)
            },
        )
    }
}

/** Convert a database song into a controller-ready queue item. */
internal fun Song.toPlaybackTrack(): PlaybackTrack {
    val uri = song.localUri
    return PlaybackTrack(
        mediaId = id,
        metadata = MediaMetadata(
            id = id,
            title = title,
            artists = artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
            duration = song.duration,
            thumbnailUrl = thumbnailUrl,
            album = album?.let { MediaMetadata.Album(id = it.id, title = it.title) },
            explicit = song.explicit,
            liked = song.liked,
            localUri = uri,
        ),
        source = if (uri != null) TrackSource.Local(uri) else TrackSource.Remote(id),
    )
}

private fun SongItem.toRemotePlaybackTrack(): PlaybackTrack = PlaybackTrack(
    mediaId = id,
    metadata = MediaMetadata(
        id = id,
        title = title,
        artists = artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
        duration = duration ?: 0,
        thumbnailUrl = thumbnail,
        explicit = explicit,
    ),
    source = TrackSource.Remote(id),
)

internal fun searchLibrary(
    query: String,
    songs: List<Song>,
    artists: List<Artist>,
    albums: List<Album>,
    playlists: List<Playlist>,
): List<YTItem> {
    val q = query.lowercase()
    val results = ArrayList<YTItem>()
    songs.filter { it.song.title.lowercase().contains(q) }.take(8).forEach { songEntry ->
        results += SongItem(
            id = songEntry.id,
            title = songEntry.title,
            artists = songEntry.artists.map { artist -> InnertubeArtist(name = artist.name, id = artist.id) },
            thumbnail = songEntry.song.thumbnailUrl ?: "",
            explicit = songEntry.song.explicit,
        )
    }
    artists.filter { it.artist.name.lowercase().contains(q) }.take(6).forEach { artistEntry ->
        results += ArtistItem(
            id = artistEntry.id,
            title = artistEntry.title,
            thumbnail = artistEntry.artist.thumbnailUrl,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
    }
    albums.filter { it.album.title.lowercase().contains(q) }.take(6).forEach { albumEntry ->
        results += AlbumItem(
            browseId = albumEntry.id,
            playlistId = albumEntry.album.playlistId ?: "",
            title = albumEntry.title,
            artists = albumEntry.artists.map { artist -> InnertubeArtist(name = artist.name, id = artist.id) },
            thumbnail = albumEntry.album.thumbnailUrl ?: "",
            explicit = albumEntry.album.explicit,
        )
    }
    playlists.filter { it.playlist.name.lowercase().contains(q) }.take(6).forEach { playlistEntry ->
        results += PlaylistItem(
            id = playlistEntry.id,
            title = playlistEntry.title,
            author = null,
            songCountText = null,
            thumbnail = playlistEntry.thumbnails.firstOrNull(),
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
    }
    return results
}

/**
 * The library search, over one shelf's things only (Local or Cached). An album
 * whose only trace is a file's tag has no page to open, so it isn't a result of
 * its own - its songs are.
 */
internal fun searchShelf(
    query: String,
    songs: List<Song>,
    artists: List<Artist>,
    albums: List<ShelfAlbum>,
    playlists: List<Playlist>,
): List<YTItem> {
    val q = query.lowercase()
    val albumHits = albums
        .filter { it.albumId != null && it.title.lowercase().contains(q) }
        .take(6)
        .map { album ->
            AlbumItem(
                browseId = album.albumId!!,
                playlistId = "",
                title = album.title,
                artists = null,
                thumbnail = album.thumbnailUrl ?: "",
            )
        }
    // The same order as the Library's search: songs, artists, albums, playlists.
    return searchLibrary(query, songs, artists, emptyList(), emptyList()) +
        albumHits +
        searchLibrary(query, emptyList(), emptyList(), emptyList(), playlists)
}

/** Convenience overload used by the All tab's search (which has real albums). */
internal fun searchShelfAlbums(
    query: String,
    albums: List<Album>,
): List<YTItem> = searchLibrary(query, emptyList(), emptyList(), albums, emptyList())
