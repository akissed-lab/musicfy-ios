package com.example.musicfy.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.ui.component.BackHandler
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.HomeCoverCard
import com.example.musicfy.ui.component.YouTubeListItem
import com.example.musicfy.ui.screens.ScreenStateCard
import com.example.musicfy.ui.screens.ScreenTopBar
import com.example.musicfy.ui.screens.itemIsActive
import com.example.musicfy.ui.screens.itemSubtitle
import com.example.musicfy.ui.screens.openYouTubeItem
import com.example.musicfy.viewmodels.CommunityPlaylistItem
import com.example.musicfy.viewmodels.GenreViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

/** Genre / mood page with YouTube sections and community playlist previews. */
@Composable
fun GenreScreen(
    navController: NavHostController,
    browseId: String,
    params: String?,
    pureBlack: Boolean = false,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above GenreScreen"
    }
    val viewModel = appViewModel<GenreViewModel>(
        key = "genre_${browseId}_${params.orEmpty()}",
        factory = containerViewModelFactory(container) { appContainer ->
            GenreViewModel(appContainer, browseId, params)
        },
    )

    val player = LocalPlaybackController.current
    val currentMetadataState = player?.currentMetadata?.collectAsState()
    val result by viewModel.result.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()

    val title = result?.title?.takeIf { it.isNotBlank() } ?: "Genre"
    val stateStyle = remember(pureBlack) {
        GlassStyle(
            containerColor = if (pureBlack) GlassTokens.Base.copy(alpha = 0.92f) else GlassTokens.Chrome,
        )
    }

    BackHandler { navController.navigateUp() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = title, onBack = { navController.navigateUp() })

        when {
            result == null -> ScreenStateCard(title = "Loading…", style = stateStyle)

            result?.items.isNullOrEmpty() -> ScreenStateCard(
                title = "Nothing here yet",
                onRetry = viewModel::retry,
                style = stateStyle,
            )

            else -> GenreContent(
                sections = result?.items.orEmpty(),
                communityPlaylists = communityPlaylists.orEmpty(),
                navController = navController,
                player = player,
                currentTrackId = currentMetadataState?.value?.id,
                stateStyle = stateStyle,
            )
        }
    }
}

@Composable
private fun ColumnScope.GenreContent(
    sections: List<com.music.innertube.pages.BrowseResult.Item>,
    communityPlaylists: List<CommunityPlaylistItem>,
    navController: NavHostController,
    player: com.example.musicfy.player.PlaybackController?,
    currentTrackId: String?,
    stateStyle: GlassStyle,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
        sections.forEach { section ->
            val sectionItems = section.items.distinctBy { it.id }
            if (sectionItems.isEmpty()) return@forEach

            item(key = "genre_title_${section.title}") {
                Text(
                    text = section.title ?: "More",
                    color = GlassTokens.Pink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }

            if (sectionItems.firstOrNull() is SongItem) {
                items(items = sectionItems, key = { "genre_song_${it.id}" }) { item: YTItem ->
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
            } else {
                item(key = "genre_row_${section.title}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(items = sectionItems, key = { it.id }) { item ->
                            HomeCoverCard(
                                title = item.title,
                                subtitle = itemSubtitle(item),
                                thumbnailUrl = item.thumbnail,
                                modifier = Modifier.clickable {
                                    openYouTubeItem(item, navController, player, currentTrackId)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (communityPlaylists.isNotEmpty()) {
            item(key = "genre_community_title") {
                Text(
                    text = "Community playlists",
                    color = GlassTokens.Pink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            items(items = communityPlaylists, key = { it.playlist.id }) { community ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    style = stateStyle,
                ) {
                    Text(text = community.playlist.title, color = GlassTokens.Pink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(text = itemSubtitle(community.playlist).orEmpty(), color = GlassTokens.MutedContent)
                    Spacer(Modifier.height(8.dp))
                    community.songs.take(6).forEach { song ->
                        Text(text = song.title, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}
