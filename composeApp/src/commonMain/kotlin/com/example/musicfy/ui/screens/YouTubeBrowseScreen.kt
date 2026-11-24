package com.example.musicfy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.musicfy.ui.component.YouTubeListItem
import com.example.musicfy.viewmodels.YouTubeBrowseViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory
import com.music.innertube.models.SongItem

/** Full YouTube browse page: grouped sections rendered as song rows or cover grids. */
@Composable
fun YouTubeBrowseScreen(
    navController: NavHostController,
    browseId: String?,
    params: String?,
    pureBlack: Boolean = false,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above YouTubeBrowseScreen"
    }
    val viewModel = appViewModel<YouTubeBrowseViewModel>(
        key = "youtube_browse_${browseId.orEmpty()}_${params.orEmpty()}",
        factory = containerViewModelFactory(container) { appContainer ->
            YouTubeBrowseViewModel(appContainer, browseId, params)
        },
    )

    val player = LocalPlaybackController.current
    val currentMetadataState = player?.currentMetadata?.collectAsState()
    val result by viewModel.result.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()

    val title = result?.title?.takeIf { it.isNotBlank() } ?: "Browse"
    val stateStyle = remember(pureBlack) {
        GlassStyle(
            containerColor = if (pureBlack) GlassTokens.Base.copy(alpha = 0.92f) else GlassTokens.Chrome,
        )
    }

    BackHandler { navController.navigateUp() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = title, onBack = { navController.navigateUp() })

        when {
            loadFailed -> ScreenStateCard(
                title = "Couldn't load this page",
                message = "Check your connection and try again.",
                onRetry = viewModel::retry,
                style = stateStyle,
            )

            result == null -> ScreenStateCard(title = "Loading…", style = stateStyle)

            result?.items.isNullOrEmpty() -> ScreenStateCard(title = "Nothing here yet", style = stateStyle)

            else -> {
                val allItems = result?.items.orEmpty().flatMap { it.items }.distinctBy { it.id }
                val currentTrackId = currentMetadataState?.value?.id
                if (allItems.any { it is SongItem }) {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        items(items = allItems, key = { it.id }) { item ->
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
                    }
                } else {
                    ScreenItemGrid(
                        entries = allItems,
                        currentTrackId = currentTrackId,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onClick = { item ->
                            openYouTubeItem(item, navController, player, currentTrackId)
                        },
                    )
                }
            }
        }
    }
}
