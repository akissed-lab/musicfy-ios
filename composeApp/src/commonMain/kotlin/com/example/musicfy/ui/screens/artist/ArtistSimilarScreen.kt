package com.example.musicfy.ui.screens.artist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.musicfy.models.ArtistGroup
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.ui.component.ArtistListCard
import com.example.musicfy.ui.component.BackHandler
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.screens.ScreenStateCard
import com.example.musicfy.ui.screens.ScreenTopBar
import com.example.musicfy.ui.screens.openYouTubeItem
import com.example.musicfy.viewmodels.ArtistSimilarViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory
import com.music.innertube.models.YTItem

/** "Similar to this artist": artists, their songs/albums, and related playlists. */
@Composable
fun ArtistSimilarScreen(
    navController: NavHostController,
    artistId: String,
    pureBlack: Boolean = false,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above ArtistSimilarScreen"
    }
    val viewModel = appViewModel<ArtistSimilarViewModel>(
        key = "artist_similar_$artistId",
        factory = containerViewModelFactory(container) { appContainer ->
            ArtistSimilarViewModel(appContainer, artistId)
        },
    )

    val player = LocalPlaybackController.current
    val currentMetadataState = player?.currentMetadata?.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val deepLoading by viewModel.deepLoading.collectAsState()

    val stateStyle = remember(pureBlack) {
        GlassStyle(
            containerColor = if (pureBlack) GlassTokens.Base.copy(alpha = 0.92f) else GlassTokens.Chrome,
        )
    }

    fun buildGroups(source: ArtistSimilarViewModel.SimilarGroups): List<ArtistGroup> = buildList {
        source.artists.forEachIndexed { index, artist ->
            val items = mutableListOf<YTItem>()
            source.songs.getOrNull(index)?.let { items.add(it) }
            source.albums.getOrNull(index)?.let { items.add(it) }
            add(
                ArtistGroup(
                    artistName = artist.title,
                    artistId = artist.id,
                    artistThumbnailUrl = artist.thumbnail,
                    items = items,
                ),
            )
        }
        if (source.playlists.isNotEmpty()) {
            add(
                ArtistGroup(
                    artistName = "Playlists",
                    artistId = null,
                    artistThumbnailUrl = source.artists.firstOrNull()?.thumbnail,
                    items = source.playlists,
                ),
            )
        }
    }

    BackHandler { navController.navigateUp() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = "Similar artists", onBack = { navController.navigateUp() })

        if (groups == null) {
            ScreenStateCard(title = "Loading similar artists…", style = stateStyle)
            return@Column
        }

        val builtGroups = remember(groups) { groups?.let { source -> buildGroups(source) }.orEmpty() }
        if (builtGroups.isEmpty()) {
            ScreenStateCard(
                title = "Nothing similar found",
                message = "Try again in a moment.",
                style = stateStyle,
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(items = builtGroups, key = { it.artistId ?: it.artistName }) { group ->
                ArtistListCard(
                    group = group,
                    onClick = {
                        group.artistId?.let { navController.navigate("artist/$it") }
                    },
                    onItemClick = { item ->
                        openYouTubeItem(item, navController, player, currentMetadataState?.value?.id)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (deepLoading) {
                item(key = "similar_loading") {
                    ScreenStateCard(title = "Finding more songs…", style = stateStyle)
                }
            }
            item(key = "similar_bottom_spacer") {
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}
