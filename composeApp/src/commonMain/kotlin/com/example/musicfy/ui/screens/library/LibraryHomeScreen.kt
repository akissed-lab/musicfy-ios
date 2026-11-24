// LibraryHomeScreen.kt
//
// The Library tab's landing page: collapsing glass title + search, then
// All / Local / Cached / Imported. All has the import banner, Pinned, the four
// category cards, Recently added and Liked; the other tabs are built inline
// from the shared shelf cards until their own screens are ported.
//
// Upstream used Hilt, PlayerConnection, the import service and the menu host.
// The common port reads the DI graph from LocalAppContainer, plays through
// LocalPlaybackController and routes unfinished sheets to existing glass
// destinations with TODOs. The local DB shelves render from LibraryHomeViewModel.

package com.example.musicfy.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.LocalPlayerUiState
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.PlaybackController
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.cached
import com.example.musicfy.resources.library_music_filled
import com.example.musicfy.resources.person
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.LikedSongsThumbnail
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.component.navigateToTab
import com.example.musicfy.ui.screens.Routes
import com.example.musicfy.ui.screens.Screens
import com.example.musicfy.viewmodels.ImportedSourceCard
import com.example.musicfy.viewmodels.ImportProgress
import com.example.musicfy.viewmodels.LibraryHomeViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import org.jetbrains.compose.resources.painterResource

/**
 * KMP entry point for the Library main tab.
 *
 * The integrator only needs to call this from the `Screens.Library.route`
 * destination with the current [NavController]; the ViewModel is created from
 * [LocalAppContainer] exactly like the other ported screens.
 */
@Composable
fun LibraryHomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val container = LocalAppContainer.current
    if (container == null) {
        // Previews and tests without the app graph get an honest glass placeholder.
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp)
                .glassSurface(shape = RoundedCornerShape(24.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Library is unavailable until the app container is provided.",
                color = GlassTokens.MutedContent,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val viewModel: LibraryHomeViewModel = appViewModel(
        factory = containerViewModelFactory(container) { appContainer -> LibraryHomeViewModel(appContainer) },
    )
    LibraryHomeContent(
        viewModel = viewModel,
        navController = navController,
        modifier = modifier,
        pureBlack = pureBlack,
    )
}

@Composable
private fun LibraryHomeContent(
    viewModel: LibraryHomeViewModel,
    navController: NavController,
    modifier: Modifier,
    pureBlack: Boolean,
) {
    val player = LocalPlaybackController.current
    val playerUi = LocalPlayerUiState.current

    val pinnedItems by viewModel.pinnedItems.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val importedSources by viewModel.importedSources.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    var tab by remember { mutableStateOf(LibraryTab.All) }
    // Only the open tab's shelf is collected; null while it is first read.
    val shelfData = tab.shelf?.let { collectShelfTabData(viewModel, it) }

    // Collect the player snapshot in composition; LazyListScope builders are not composable.
    val trackInfo = playerUi?.trackInfo?.collectAsState()?.value
    val transportState = playerUi?.transportState?.collectAsState()?.value
    val currentTrackId = trackInfo?.mediaId
    val isPlaying = transportState?.isPlaying == true

    val (profilePicStr) = rememberPreference(SettingsKeys.ProfilePicUriKey, defaultValue = "")

    var query by remember { mutableStateOf(TextFieldValue()) }
    val results = remember(query.text, songs, artists, albums, playlists, tab, shelfData) {
        val q = query.text.trim()
        when {
            q.isBlank() -> emptyList()
            tab.shelf != null -> shelfData?.let { searchShelf(q, it.songs, it.artists, it.albums, it.playlists) }.orEmpty()
            else -> searchLibrary(q, songs, artists, albums, playlists)
        }
    }

    val listState = rememberLazyListState()
    val collapse = rememberLibraryCollapseProgress(listState)
    // Use the page's shared glass state rather than creating a second one.
    val glassState = LocalGlassState.current
    // TODO: LocalPlayerAwareWindowInsets has no KMP equivalent yet.
    val bottomInset = 0.dp

    // TODO: the import/manage sheets are Android-only; route to their ported pages instead.
    val openImportSheet = { navController.navigate(Routes.IMPORT_SYNC) }
    val openImportProgress = { navController.navigate(Routes.IMPORT_PROGRESS) }

    Box(modifier = modifier.fillMaxSize()) {
        LibraryScaffold(
            title = "Library",
            subtitle = null,
            searchPlaceholder = when (tab) {
                LibraryTab.Local -> "Search your local music"
                LibraryTab.Cached -> "Search your cached music"
                else -> "Search anything on your library"
            },
            query = query,
            onQueryChange = { query = it },
            listState = listState,
            glassState = glassState,
            collapseProvider = { collapse.value },
            pureBlack = pureBlack,
            trailing = {
                LibraryProfileAvatar(
                    imageUrl = profilePicStr.ifBlank { null },
                    onClick = { navController.navigateToTab(Screens.Settings.route) },
                )
            },
            bottomInset = bottomInset,
        ) {
            item(key = "tabs") {
                LibraryTabChips(
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (query.text.isNotBlank()) {
                librarySearchResults(
                    results = results,
                    query = query.text,
                    onOpen = { item ->
                        if (shelfData != null) {
                            openShelfResult(item, shelfData, navController, player)
                        } else {
                            openLibraryItem(item, navController, player, songs)
                        }
                    },
                )
                return@LibraryScaffold
            }

            when (tab) {
                LibraryTab.All -> {
                    libraryProgressCards(importProgress, openImportProgress)
                    libraryAllTab(
                        pinnedItems = pinnedItems,
                        songs = songs,
                        artists = artists.size,
                        artistCovers = artists.take(4).map { it.artist.thumbnailUrl },
                        albums = albums.size,
                        albumCovers = albums.take(4).map { it.album.thumbnailUrl },
                        playlists = playlists.size,
                        playlistCovers = playlists.take(2).flatMap { it.thumbnails },
                        likedSongs = likedSongs,
                        importBanner = {
                            LibraryImportBannerCard(
                                progress = importProgress,
                                onOpenSheet = openImportSheet,
                                onOpenProgress = openImportProgress,
                                modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
                            )
                        },
                        onOpen = { item -> openLibraryItem(item, navController, player, songs) },
                        onUnpin = viewModel::unpin,
                        navController = navController,
                    )
                }

                LibraryTab.Local -> {
                    val local = shelfData ?: return@LibraryScaffold
                    libraryProgressCards(importProgress, openImportProgress)
                    libraryLocalTab(data = local, navController = navController)
                }

                LibraryTab.Cached -> {
                    val cached = shelfData ?: return@LibraryScaffold
                    libraryCachedTab(
                        data = cached,
                        navController = navController,
                        player = player,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                    )
                }

                LibraryTab.Imported -> libraryImportedTab(
                    sources = importedSources,
                    onOpenImportSheet = openImportSheet,
                    onOpenImportProgress = openImportProgress,
                )
            }
        }
    }
}

/** The download and local import progress; only shows while a job runs or has just finished. */
private fun LazyListScope.libraryProgressCards(
    progress: ImportProgress,
    onOpen: () -> Unit,
) {
    if (!progress.isRunning && !progress.isDone) return
    item(key = "progress_cards") {
        val accent = LocalGlassState.current.accent
        Column(
            modifier = Modifier
                .padding(horizontal = LibraryHorizontalPadding)
                .glassSurface(shape = RoundedCornerShape(18.dp), style = GlassStyle(elevation = 8.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = if (progress.error != null) "Import failed" else "Importing music",
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = progress.currentLabel.ifBlank {
                    "${progress.processedTracks} of ${progress.totalTracks} tracks processed"
                },
                color = GlassTokens.MutedContent,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            val fraction = if (progress.totalTracks > 0) {
                progress.processedTracks.toFloat() / progress.totalTracks
            } else {
                0f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .glassSurface(
                        shape = RoundedCornerShape(2.dp),
                        style = GlassStyle(containerColor = GlassTokens.Chrome, elevation = 0.dp, cornerRadius = 2.dp),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(accent, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/** The import entry point; doubles as the import's progress bar while one runs. */
@Composable
private fun LibraryImportBannerCard(
    progress: ImportProgress,
    onOpenSheet: () -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalGlassState.current.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(20.dp), style = GlassStyle(elevation = 10.dp))
            .clickable(onClick = if (progress.isRunning) onOpenProgress else onOpenSheet)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .glassSurface(
                        shape = RoundedCornerShape(14.dp),
                        style = GlassStyle(containerColor = accent.copy(alpha = 0.24f), elevation = 0.dp, cornerRadius = 14.dp),
                    ),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.library_music_filled),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (progress.isRunning) "Import in progress" else "Import your music",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (progress.isRunning) {
                        "${progress.processedTracks}/${progress.totalTracks} · tap for details"
                    } else {
                        "Bring songs and playlists from another service or your device"
                    },
                    color = GlassTokens.MutedContent,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun LazyListScope.libraryAllTab(
    pinnedItems: List<YTItem>,
    songs: List<com.example.musicfy.db.entities.Song>,
    artists: Int,
    artistCovers: List<String?>,
    albums: Int,
    albumCovers: List<String?>,
    playlists: Int,
    playlistCovers: List<String?>,
    likedSongs: List<com.example.musicfy.db.entities.Song>,
    importBanner: @Composable () -> Unit,
    onOpen: (YTItem) -> Unit,
    onUnpin: (String) -> Unit,
    navController: NavController,
) {
    item(key = "import_banner") {
        importBanner()
        Spacer(modifier = Modifier.height(16.dp))
    }

    // A completely empty library gets one honest message instead of four zero-count cards.
    if (songs.isEmpty() && albums == 0 && artists == 0 && playlists == 0 && pinnedItems.isEmpty()) {
        item(key = "empty") {
            LibraryEmptyState(text = "Nothing here yet. Import your music above to fill your library.")
        }
        return
    }

    // Pinned disappears entirely when nothing is pinned rather than showing an empty 3x3.
    if (pinnedItems.isNotEmpty()) {
        item(key = "pinned_header") {
            LibrarySectionTitle("Pinned")
            Spacer(modifier = Modifier.height(12.dp))
            LibraryRule()
            Spacer(modifier = Modifier.height(16.dp))
        }
        item(key = "pinned_grid") {
            // Chunked rows rather than a nested lazy grid: a scrolling grid inside a
            // scrolling column has no bounded height, and nine items never needs one.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = LibraryHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pinnedItems.take(9).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { pinned ->
                            LibraryPinnedTile(
                                thumbnailUrl = pinned.thumbnail,
                                onClick = { onOpen(pinned) },
                                onLongClick = { onUnpin(pinned.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keeps the last row's tiles the same size as a full row's.
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            LibraryRule()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    item(key = "categories") {
        LibraryCardGrid(
            listOf(
                LibraryCardEntry("Songs", songs.size, songs.take(4).map { it.song.thumbnailUrl }, {
                    navController.navigate(Routes.LIBRARY_SONGS)
                }),
                LibraryCardEntry("Playlist", playlists, playlistCovers, {
                    navController.navigate(Routes.LIBRARY_PLAYLISTS)
                }),
                LibraryCardEntry("Albums", albums, albumCovers, {
                    navController.navigate(Routes.LIBRARY_ALBUMS)
                }),
                LibraryCardEntry("Artist", artists, artistCovers, {
                    navController.navigate(Routes.LIBRARY_ARTISTS)
                }, roundCovers = true),
            ),
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    if (songs.isNotEmpty()) {
        item(key = "recently_added") {
            LibraryWideCard(
                title = "Recently added",
                count = songs.size,
                covers = songs.take(4).map { it.song.thumbnailUrl },
                onClick = { navController.navigate(Routes.LIBRARY_ADDED) },
                modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (likedSongs.isNotEmpty()) {
        item(key = "liked_songs") {
            LibraryWideCard(
                title = "Liked",
                count = likedSongs.size,
                covers = likedSongs.take(4).map { it.song.thumbnailUrl },
                onClick = { navController.navigate(Routes.AUTO_PLAYLIST.replace("{playlist}", "liked")) },
                sharedElementKey = "playlist-liked",
                art = { coverModifier -> LikedSongsThumbnail(size = 60.dp, modifier = coverModifier) },
                modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
            )
        }
    }
}

/** Local files/downloads shelf. Import and manage sheets are still Android-only. */
private fun LazyListScope.libraryLocalTab(
    data: ShelfTabData,
    navController: NavController,
) {
    item(key = "local_header") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LibraryHorizontalPadding)
                .glassSurface(shape = RoundedCornerShape(20.dp), style = GlassStyle(elevation = 8.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.library_music_filled),
                    contentDescription = null,
                    tint = LocalGlassState.current.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Column {
                    Text(
                        text = "Music on this device",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Downloads and files from your phone",
                        color = GlassTokens.MutedContent,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }

    if (data.songs.isEmpty()) {
        item(key = "local_empty") {
            LibraryEmptyState(text = "No local music yet. Download a song or import files from your phone.")
        }
    }
    libraryShelfCards(data = data, navController = navController, spaceAbove = 6.dp)

    // TODO: wire to LocalImport sheet (rememberLocalImportActions / ManageLocalSheet).
    item(key = "local_manage") {
        Spacer(modifier = Modifier.height(14.dp))
        LibraryManagePill(text = "Manage local music", onClick = {})
    }
}

/** Stream-cache shelf: empty until the KMP cache port lands. */
private fun LazyListScope.libraryCachedTab(
    data: ShelfTabData,
    navController: NavController,
    player: PlaybackController?,
    currentTrackId: String?,
    isPlaying: Boolean,
) {
    item(key = "cached_header") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LibraryHorizontalPadding)
                .glassSurface(shape = RoundedCornerShape(20.dp), style = GlassStyle(elevation = 8.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.cached),
                    contentDescription = null,
                    tint = LocalGlassState.current.accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Column {
                    Text(
                        text = "Cached music",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Songs available without a connection",
                        color = GlassTokens.MutedContent,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }

    // TODO: cache service is not ported, so this shelf is empty for now.
    if (data.songs.isEmpty()) {
        item(key = "cached_empty") {
            LibraryEmptyState(text = "Nothing cached yet. Download a song to play it offline.")
        }
    } else {
        items(data.songs, key = { it.id }) { song ->
            LibraryListRow(
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                thumbnailUrl = song.thumbnailUrl,
                onClick = { playShelf(data, data.songs.indexOf(song), player) },
                onLongClick = { playShelf(data, data.songs.indexOf(song), player) },
                isActive = currentTrackId == song.id,
                isPlaying = isPlaying,
                songId = song.id,
            )
        }
    }
    libraryShelfCards(data = data, navController = navController, spaceAbove = 10.dp)

    // TODO: wire to the ManageCached sheet (clearCache / removeFromCache).
    item(key = "cached_manage") {
        Spacer(modifier = Modifier.height(14.dp))
        LibraryManagePill(text = "Manage cache", onClick = {})
    }
}

/** Import sources shelf: the ported ViewModel exposes no attributed songs yet. */
private fun LazyListScope.libraryImportedTab(
    sources: List<ImportedSourceCard>,
    onOpenImportSheet: () -> Unit,
    onOpenImportProgress: () -> Unit,
) {
    item(key = "imported_banner") {
        LibraryImportBannerCard(
            progress = ImportProgress(),
            onOpenSheet = onOpenImportSheet,
            onOpenProgress = onOpenImportProgress,
            modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    // TODO: importedServices() lives with the importer, which is not ported; use the VM's cards
    // when it lands. Today the list is always empty.
    if (sources.isEmpty()) {
        item(key = "imported_empty") {
            LibraryEmptyState(text = "Nothing imported yet. Bring in music from another service above.")
        }
    } else {
        items(sources, key = { it.source.key }) { source ->
            LibraryCardGrid(
                listOf(
                    LibraryCardEntry(
                        title = source.source.label,
                        count = source.songCount,
                        covers = source.covers,
                        onClick = onOpenImportSheet,
                    ),
                ),
            )
        }
    }

    // TODO: wire to the ManageImports sheet (sources / resyncAll).
    item(key = "imported_manage") {
        Spacer(modifier = Modifier.height(14.dp))
        LibraryManagePill(text = "Manage imports", onClick = {})
    }
}

/** A song found on the Local or Cached tab plays among that tab's own songs. */
private fun openShelfResult(
    item: YTItem,
    shelf: ShelfTabData,
    navController: NavController,
    player: PlaybackController?,
) {
    val index = if (item is SongItem) shelf.songs.indexOfFirst { it.id == item.id } else -1
    if (index < 0) {
        openLibraryItem(item, navController, player, shelf.songs)
        return
    }
    playShelf(shelf, index, player)
}

/** The shelf's songs as one queue, in the order the tab shows them, starting at [startIndex]. */
private fun playShelf(
    shelf: ShelfTabData,
    startIndex: Int,
    player: PlaybackController?,
) {
    player?.playQueue(
        ListQueue(
            title = shelf.kind.label,
            items = shelf.songs.map { it.toPlaybackTrack() },
            startIndex = startIndex.coerceAtLeast(0),
        ),
    )
}

/** Small glass avatar that opens Settings, mirroring upstream's `SearchAvatar`. */
@Composable
private fun LibraryProfileAvatar(
    imageUrl: String?,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp, cornerRadius = 18.dp))
            .clickable(onClick = onClick),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(shape),
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.person),
                contentDescription = "Open settings",
                tint = GlassTokens.MutedContent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
