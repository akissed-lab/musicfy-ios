package com.example.musicfy.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.TrackSource
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.arrow_back
import com.example.musicfy.resources.ic_untitled_pause
import com.example.musicfy.resources.play
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference
import com.example.musicfy.ui.component.BackHandler
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.navigateToTab
import com.example.musicfy.ui.screens.Screens
import com.example.musicfy.viewmodels.OnlineSearchViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.viewModelFactory
import com.music.innertube.SearchFilter
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private val SearchCategories = listOf(
    "All" to null,
    "Music" to SearchFilter.FILTER_SONG,
    "Artist" to SearchFilter.FILTER_ARTIST,
    "Video" to SearchFilter.FILTER_VIDEO,
    "Album" to SearchFilter.FILTER_ALBUM,
    "Playlist" to SearchFilter.FILTER_COMMUNITY_PLAYLIST,
    "Featured" to SearchFilter.FILTER_FEATURED_PLAYLIST,
)

/** Result page for `search/{query}`; the route argument is UTF-8 percent-encoded. */
@Composable
fun OnlineSearchResult(
    navController: NavController,
    query: String,
    pureBlack: Boolean = false,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above OnlineSearchResult"
    }
    val decodedQuery = remember(query) { decodeSearchQuery(query) }
    val viewModel = appViewModel<OnlineSearchViewModel>(
        key = "online_search_$decodedQuery",
        factory = viewModelFactory { OnlineSearchViewModel(container, decodedQuery) },
    )

    val player = LocalPlaybackController.current
    val currentMetadataState = player?.currentMetadata?.collectAsState()
    val transportState = player?.transportState?.collectAsState()
    val currentTrackId = currentMetadataState?.value?.id
    val isPlaying = transportState?.value?.isPlaying == true

    val searchFilter by viewModel.filter.collectAsState()
    val summary = viewModel.summaryPage
    val errorMessage = viewModel.errorMessage
    val isLoadingMore = viewModel.isLoadingMore
    val itemsPage by remember(searchFilter) {
        derivedStateOf { searchFilter?.value?.let { viewModel.viewStateMap[it] } }
    }

    var searchText by remember(decodedQuery) {
        mutableStateOf(TextFieldValue(decodedQuery, TextRange(decodedQuery.length)))
    }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val pauseSearchHistory by rememberPreference(SettingsKeys.PauseSearchHistoryKey, defaultValue = false)
    val profilePicStr by rememberPreference(SettingsKeys.ProfilePicUriKey, defaultValue = "")

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)
    val progressProvider = remember(collapse) { { collapse.value } }

    val selectedIndex = remember(searchFilter) {
        SearchCategories.indexOfFirst { it.second?.value == searchFilter?.value }
            .coerceAtLeast(0)
    }

    LaunchedEffect(searchFilter) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState, itemsPage) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }.collect { nearEnd ->
            if (nearEnd && itemsPage?.continuation != null) viewModel.loadMore()
        }
    }

    BackHandler { navController.navigateUp() }

    val submit: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty() && text != decodedQuery) {
            focusManager.clearFocus()
            val encoded = encodeSearchQuery(text)
            navController.navigate("search/$encoded") {
                popUpTo("search/${encodeSearchQuery(decodedQuery)}") { inclusive = true }
            }
            if (!pauseSearchHistory) {
                coroutineScope.launch(Dispatchers.Default) {
                    container.database.queries.insertSearchHistory(text)
                }
            }
        }
    }

    val onItemClick: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> {
                if (item.id == currentTrackId) {
                    player?.togglePlayPause()
                } else if (player != null) {
                    player.playQueue(
                        ListQueue(
                            title = item.title,
                            items = listOf(item.toPlaybackTrack()),
                        ),
                    )
                }
            }

            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }

    val top = remember(summary, decodedQuery) {
        summary?.let { pickTopResult(it.summaries.flatMap { section -> section.items }, decodedQuery) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { progressProvider() > 0.01f }),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = searchTopBarHeight(withTitle = true, extra = 46.dp),
                    bottom = 112.dp,
                ),
            ) {
                when {
                    errorMessage != null -> {
                        item(key = "search_error") {
                            ResultErrorCard(
                                message = errorMessage.orEmpty(),
                                onRetry = { viewModel.refreshSettings() },
                            )
                        }
                    }

                    searchFilter == null -> {
                        val page = summary
                        if (page == null) {
                            item(key = "summary_loading") { ResultLoadingCard() }
                        } else {
                            if (top != null) {
                                item(key = "top_header") {
                                    SearchSectionHeader(title = "Top Results", ruleAbove = false)
                                }
                                item(key = "top_card") {
                                    TopResultCard(
                                        item = top,
                                        isActive = currentTrackId == top.id,
                                        isPlaying = isPlaying,
                                        onClick = { onItemClick(top) },
                                    )
                                    Spacer(modifier = Modifier.height(22.dp))
                                }
                            }

                            var renderedSection = false
                            page.summaries.forEachIndexed { index, section ->
                                val rows = section.items.filter { it.id != top?.id }
                                if (rows.isNotEmpty()) {
                                    val ruleAbove = !renderedSection
                                    renderedSection = true
                                    item(key = "summary_header_${index}_${section.title}") {
                                        SearchSectionHeader(
                                            title = if (index == 0) "Exact matches" else section.title,
                                            ruleAbove = ruleAbove,
                                        )
                                    }
                                    items(
                                        items = rows,
                                        key = { "summary_${index}_${it.id}" },
                                        contentType = { "resultRow" },
                                    ) { item ->
                                        ResultRow(
                                            item = item,
                                            isActive = currentTrackId == item.id,
                                            isPlaying = isPlaying,
                                            onClick = { onItemClick(item) },
                                        )
                                    }
                                    item(key = "summary_gap_$index") {
                                        Spacer(modifier = Modifier.height(18.dp))
                                    }
                                }
                            }

                            if (top == null && page.summaries.all { it.items.isEmpty() }) {
                                item(key = "summary_empty") { NoResultsCard() }
                            }
                        }
                    }

                    else -> {
                        val page = itemsPage
                        if (page == null) {
                            item(key = "filter_loading") { ResultLoadingCard() }
                        } else if (page.items.isEmpty()) {
                            item(key = "filter_empty") { NoResultsCard() }
                        } else {
                            items(
                                items = page.items,
                                key = { it.id },
                                contentType = { "resultRow" },
                            ) { item ->
                                ResultRow(
                                    item = item,
                                    isActive = currentTrackId == item.id,
                                    isPlaying = isPlaying,
                                    onClick = { onItemClick(item) },
                                )
                            }
                        }
                    }
                }

                if (isLoadingMore) {
                    item(key = "load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SearchLoadingDots()
                        }
                    }
                }
            }
        }

        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = progressProvider,
            pureBlack = pureBlack,
            title = "Search",
            trailing = {
                SearchAvatar(
                    imageUrl = profilePicStr.ifBlank { null },
                    onClick = { navController.navigateToTab(Screens.Settings.route) },
                )
            },
            below = {
                SearchCategoryRow(
                    categories = SearchCategories.map { it.first },
                    selectedIndex = selectedIndex,
                    onSelect = { index -> viewModel.filter.value = SearchCategories[index].second },
                )
            },
        ) {
            SearchField(
                value = searchText,
                onValueChange = { searchText = it },
                onSearch = submit,
                placeholder = "Search for any tracks, albums, lyrics...",
                leading = {
                    Icon(
                        painter = painterResource(Res.drawable.arrow_back),
                        contentDescription = null,
                        tint = SearchColors.Secondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { navController.navigateUp() },
                    )
                },
            )
        }
    }
}

private fun pickTopResult(items: List<YTItem>, query: String): YTItem? {
    if (items.isEmpty()) return null
    val normalised = query.trim().lowercase()

    items.filterIsInstance<ArtistItem>()
        .firstOrNull { it.title.trim().lowercase() == normalised }
        ?.let { return it }

    val songs = items.filterIsInstance<SongItem>()
    songs.firstOrNull { !it.isVideoSong }?.let { return it }

    return songs.firstOrNull() ?: items.first()
}

@Composable
private fun TopResultCard(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val kind = when (item) {
        is SongItem -> if (item.isVideoSong) "Video" else "Song"
        is AlbumItem -> "Album"
        is ArtistItem -> "Artist"
        is PlaylistItem -> "Playlist"
    }
    val detail = when (item) {
        is SongItem -> listOfNotNull(
            kind,
            item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }?.let { "by $it" },
            item.duration?.let { formatDuration(it) },
        ).joinToString("  •  ")

        is AlbumItem -> listOfNotNull(
            kind,
            item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }?.let { "by $it" },
            item.year?.toString(),
        ).joinToString("  •  ")

        is ArtistItem -> kind
        is PlaylistItem -> listOfNotNull(kind, item.author?.name?.let { "by $it" }, item.songCountText)
            .joinToString("  •  ")
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding)
            .clickable(onClick = onClick),
        style = GlassStyle(
            containerColor = SearchColors.Tile.copy(alpha = 0.92f),
            cornerRadius = 16.dp,
            elevation = 0.dp,
        ),
    ) {
        SearchArtwork(
            url = item.thumbnail,
            size = 108.dp,
            circle = item is ArtistItem,
            corner = 8.dp,
            sharedElementKey = item.id,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = SearchColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = SearchColors.Secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item !is ArtistItem) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isActive && isPlaying) Res.drawable.ic_untitled_pause else Res.drawable.play,
                        ),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> listOfNotNull(
            item.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
            item.duration?.let { formatDuration(it) },
        ).joinToString("  •  ")

        is AlbumItem -> listOfNotNull(
            "Album",
            item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() },
        ).joinToString("  •  ")

        is ArtistItem -> "Artist"
        is PlaylistItem -> listOfNotNull("Playlist", item.author?.name).joinToString("  •  ")
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = SearchHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchArtwork(
                url = item.thumbnail,
                size = 46.dp,
                circle = item is ArtistItem,
                sharedElementKey = item.id,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (isActive) Color(0xFF7FD1FF) else SearchColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = SearchColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item is SongItem && isActive) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(
                        if (isPlaying) Res.drawable.ic_untitled_pause else Res.drawable.play,
                    ),
                    contentDescription = null,
                    tint = SearchColors.Secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        SearchRule()
    }
}

@Composable
private fun ResultLoadingCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        SearchLoadingDots()
    }
}

@Composable
private fun NoResultsCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = SearchHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            style = GlassStyle(elevation = 0.dp),
        ) {
            Text(
                text = "No results",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = GlassTokens.MutedContent,
            )
        }
    }
}

@Composable
private fun ResultErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding, vertical = 12.dp),
        style = GlassStyle(elevation = 0.dp),
    ) {
        Text(
            text = "Search failed",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = SearchColors.Primary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = GlassTokens.MutedContent,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Retry",
            style = MaterialTheme.typography.labelLarge,
            color = GlassTokens.Purple,
            modifier = Modifier
                .clickable(onClick = onRetry)
                .padding(vertical = 4.dp),
        )
    }
}

private fun SongItem.toPlaybackTrack(): PlaybackTrack = PlaybackTrack(
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

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}

/** CommonMain replacement for `URLDecoder.decode(value, "UTF-8")`. */
private fun decodeSearchQuery(value: String): String {
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        when (val char = value[index]) {
            '+' -> {
                bytes.add(' '.code.toByte())
                index++
            }

            '%' -> {
                val high = value.getOrNull(index + 1)?.let(::hexValue)
                val low = value.getOrNull(index + 2)?.let(::hexValue)
                if (high != null && low != null) {
                    bytes.add(((high shl 4) or low).toByte())
                    index += 3
                } else {
                    bytes.add(char.code.toByte())
                    index++
                }
            }

            else -> {
                char.toString().encodeToByteArray().forEach { bytes.add(it) }
                index++
            }
        }
    }
    return ByteArray(bytes.size) { bytes[it] }.decodeToString()
}

private fun hexValue(char: Char): Int? = when (char) {
    in '0'..'9' -> char - '0'
    in 'a'..'f' -> char - 'a' + 10
    in 'A'..'F' -> char - 'A' + 10
    else -> null
}
