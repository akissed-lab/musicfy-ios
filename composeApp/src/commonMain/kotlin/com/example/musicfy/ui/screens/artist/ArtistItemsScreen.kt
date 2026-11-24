package com.example.musicfy.ui.screens.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.ui.component.BackHandler
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.YouTubeGridItem
import com.example.musicfy.ui.component.YouTubeListItem
import com.example.musicfy.ui.screens.ScreenStateCard
import com.example.musicfy.ui.screens.ScreenTopBar
import com.example.musicfy.ui.screens.itemIsActive
import com.example.musicfy.ui.screens.openYouTubeItem
import com.example.musicfy.viewmodels.ArtistItemsViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory
import com.music.innertube.models.SongItem

/** One "see all" artist shelf, with continuation paging. */
@Composable
fun ArtistItemsScreen(
    navController: NavHostController,
    artistId: String?,
    browseId: String?,
    params: String?,
    pureBlack: Boolean = false,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above ArtistItemsScreen"
    }
    val key = "artist_items_${artistId.orEmpty()}_${browseId.orEmpty()}_${params.orEmpty()}"
    val viewModel = appViewModel<ArtistItemsViewModel>(
        key = key,
        factory = containerViewModelFactory(container) { appContainer ->
            ArtistItemsViewModel(appContainer, artistId, browseId, params)
        },
    )

    val player = LocalPlaybackController.current
    val currentMetadataState = player?.currentMetadata?.collectAsState()
    val title by viewModel.title.collectAsState()
    val itemsPage by viewModel.itemsPage.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()

    val stateStyle = remember(pureBlack) {
        GlassStyle(
            containerColor = if (pureBlack) GlassTokens.Base.copy(alpha = 0.92f) else GlassTokens.Chrome,
        )
    }

    BackHandler { navController.navigateUp() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = title.ifBlank { "More" }, onBack = { navController.navigateUp() })

        when {
            loadFailed -> ScreenStateCard(
                title = "Couldn't load these items",
                message = "Check your connection and try again.",
                onRetry = viewModel::retry,
                style = stateStyle,
            )

            itemsPage == null -> ScreenStateCard(title = "Loading…", style = stateStyle)

            itemsPage?.items.isNullOrEmpty() -> ScreenStateCard(
                title = "Nothing here yet",
                style = stateStyle,
            )

            else -> {
                val pageItems = itemsPage!!.items
                val currentTrackId = currentMetadataState?.value?.id
                val useGrid = pageItems.firstOrNull() !is SongItem
                val hasMore = itemsPage?.continuation != null

                if (useGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 132.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        gridItems(items = pageItems, key = { it.id }) { item ->
                            YouTubeGridItem(
                                item = item,
                                isActive = itemIsActive(item, currentTrackId),
                                fillMaxWidth = true,
                                modifier = Modifier.clickable {
                                    openYouTubeItem(item, navController, player, currentTrackId)
                                },
                            )
                        }
                        if (hasMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "Load more",
                                    color = GlassTokens.Pink,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.loadMore() }
                                        .padding(vertical = 16.dp),
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        items(items = pageItems, key = { it.id }) { item ->
                            YouTubeListItem(
                                item = item,
                                isActive = itemIsActive(item, currentTrackId),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        openYouTubeItem(item, navController, player, currentTrackId)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 3.dp),
                            )
                        }
                        if (hasMore) {
                            item(key = "artist_items_load_more") {
                                Text(
                                    text = "Load more",
                                    color = GlassTokens.Pink,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.loadMore() }
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
