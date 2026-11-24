package com.example.musicfy.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.musicfy.resources.arrow_forward
import com.example.musicfy.resources.close
import com.example.musicfy.resources.history
import com.example.musicfy.resources.search
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.viewmodels.OnlineSearchSuggestionViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.viewModelFactory
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import org.jetbrains.compose.resources.painterResource

/** Entry point used by callers that do not own a view model instance. */
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above OnlineSearchScreen"
    }
    val viewModel = appViewModel<OnlineSearchSuggestionViewModel>(
        factory = viewModelFactory { OnlineSearchSuggestionViewModel(container) },
    )
    OnlineSearchScreen(
        query = query,
        onQueryChange = onQueryChange,
        navController = navController,
        onSearch = onSearch,
        onDismiss = onDismiss,
        pureBlack = pureBlack,
        viewModel = viewModel,
    )
}

/** Injectable overload kept for parity with upstream's Hilt-provided parameter. */
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
    viewModel: OnlineSearchSuggestionViewModel,
) {
    val state by viewModel.viewState.collectAsState()
    val player = LocalPlaybackController.current

    LaunchedEffect(query) {
        viewModel.query.value = query
    }

    val openItem: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> player?.playQueue(
                ListQueue(
                    title = item.title,
                    items = listOf(item.toOnlinePlaybackTrack()),
                ),
            )

            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack))
            .padding(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.search),
                    contentDescription = null,
                    tint = GlassTokens.MutedContent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Online search",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = SearchColors.Primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(Res.drawable.close),
                    contentDescription = "Close",
                    tint = GlassTokens.MutedContent,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onDismiss),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (query.isBlank()) {
                    if (state.history.isNotEmpty()) {
                        item(key = "history_header") {
                            SearchSectionHeader(title = "Search history", ruleAbove = false)
                        }
                        items(
                            items = state.history,
                            key = { "history_${it.id}" },
                        ) { entry ->
                            SearchSimpleRow(
                                text = entry.query,
                                leadingIcon = Res.drawable.history,
                                onClick = { onSearch(entry.query) },
                                trailing = {
                                    Icon(
                                        painter = painterResource(Res.drawable.arrow_forward),
                                        contentDescription = null,
                                        tint = GlassTokens.MutedContent,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                onQueryChange(
                                                    TextFieldValue(
                                                        entry.query,
                                                        TextRange(entry.query.length),
                                                    ),
                                                )
                                            },
                                    )
                                },
                            )
                        }
                    }
                } else {
                    if (state.isFromLink) {
                        item(key = "link_header") {
                            SearchSectionHeader(title = "From link", ruleAbove = false)
                        }
                    }

                    if (state.suggestions.isNotEmpty()) {
                        item(key = "suggestions_header") {
                            SearchSectionHeader(title = "Suggestions", ruleAbove = false)
                        }
                        items(
                            items = state.suggestions,
                            key = { "suggestion_$it" },
                        ) { suggestion ->
                            SearchSimpleRow(
                                text = suggestion,
                                pill = true,
                                onClick = { onSearch(suggestion) },
                                trailing = {
                                    Icon(
                                        painter = painterResource(Res.drawable.arrow_forward),
                                        contentDescription = null,
                                        tint = GlassTokens.MutedContent,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                onQueryChange(
                                                    TextFieldValue(
                                                        suggestion,
                                                        TextRange(suggestion.length),
                                                    ),
                                                )
                                            },
                                    )
                                },
                            )
                        }
                    }

                    if (state.items.isNotEmpty()) {
                        item(key = "top_result_header") {
                            SearchSectionHeader(title = "Top result", ruleAbove = false)
                        }
                        items(
                            items = state.items,
                            key = { "top_item_${it.id}" },
                        ) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SearchHorizontalPadding, vertical = 4.dp)
                                    .clickable { openItem(item) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SearchArtwork(
                                    url = item.thumbnail,
                                    size = 44.dp,
                                    circle = item is ArtistItem,
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                        color = SearchColors.Primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = when (item) {
                                            is SongItem -> "Song"
                                            is AlbumItem -> "Album"
                                            is ArtistItem -> "Artist"
                                            is PlaylistItem -> "Playlist"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = SearchColors.Secondary,
                                    )
                                }
                            }
                        }
                    }

                    if (state.suggestions.isEmpty() && state.items.isEmpty()) {
                        item(key = "suggestions_loading") {
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SearchHorizontalPadding),
                                style = GlassStyle(elevation = 0.dp),
                            ) {
                                SearchLoadingDots()
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Search anyway",
                style = MaterialTheme.typography.labelLarge,
                color = SearchColors.Primary,
                modifier = Modifier
                    .glassSurface(
                        shape = RoundedCornerShape(16.dp),
                        style = GlassStyle(elevation = 0.dp),
                    )
                    .clickable { onSearch(query) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .align(Alignment.CenterHorizontally),
            )
        }
    }
}

private fun SongItem.toOnlinePlaybackTrack(): PlaybackTrack = PlaybackTrack(
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
