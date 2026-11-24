package com.example.musicfy.ui.screens.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.ui.component.BackHandler
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.HomeCoverCard
import com.example.musicfy.ui.screens.ScreenStateCard
import com.example.musicfy.ui.screens.ScreenTopBar
import com.example.musicfy.viewmodels.ArtistAlbumsViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory

/** Local albums saved for this artist. */
@Composable
fun ArtistAlbumsScreen(
    navController: NavHostController,
    artistId: String,
    pureBlack: Boolean = false,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above ArtistAlbumsScreen"
    }
    val viewModel = appViewModel<ArtistAlbumsViewModel>(
        key = "artist_albums_$artistId",
        factory = containerViewModelFactory(container) { appContainer ->
            ArtistAlbumsViewModel(appContainer, artistId)
        },
    )

    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val title = artist?.artist?.name ?: "Albums"
    val stateStyle = remember(pureBlack) {
        GlassStyle(
            containerColor = if (pureBlack) GlassTokens.Base.copy(alpha = 0.92f) else GlassTokens.Chrome,
        )
    }

    BackHandler { navController.navigateUp() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = title, onBack = { navController.navigateUp() })

        if (albums.isEmpty()) {
            ScreenStateCard(
                title = "No saved albums yet",
                message = "Albums you add from this artist will show up here.",
                style = stateStyle,
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 132.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(items = albums, key = { it.id }) { album ->
                HomeCoverCard(
                    title = album.album.title,
                    subtitle = album.album.year?.toString(),
                    thumbnailUrl = album.album.thumbnailUrl,
                    modifier = Modifier.clickable { navController.navigate("album/${album.id}") },
                )
            }
        }
    }
}
