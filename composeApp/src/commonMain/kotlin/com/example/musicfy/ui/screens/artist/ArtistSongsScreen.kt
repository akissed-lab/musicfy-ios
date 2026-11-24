package com.example.musicfy.ui.screens.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.ui.component.BackHandler
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.HomeTrackRow
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.screens.ScreenStateCard
import com.example.musicfy.ui.screens.ScreenTopBar
import com.example.musicfy.ui.screens.formatDuration
import com.example.musicfy.ui.screens.toLibraryTrack
import com.example.musicfy.viewmodels.ArtistSongsViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory

/** Local songs saved for this artist. */
@Composable
fun ArtistSongsScreen(
    navController: NavHostController,
    artistId: String,
    pureBlack: Boolean = false,
) {
    val container = requireNotNull(LocalAppContainer.current) {
        "LocalAppContainer must be provided above ArtistSongsScreen"
    }
    val viewModel = appViewModel<ArtistSongsViewModel>(
        key = "artist_songs_$artistId",
        factory = containerViewModelFactory(container) { appContainer ->
            ArtistSongsViewModel(appContainer, artistId)
        },
    )

    val player = LocalPlaybackController.current
    val currentMetadataState = player?.currentMetadata?.collectAsState()
    val isPlaying = player?.transportState?.collectAsState()?.value?.isPlaying == true
    val artist by viewModel.artist.collectAsState()
    val songs by viewModel.songs.collectAsState()

    val title = artist?.artist?.name ?: "Songs"
    val stateStyle = remember(pureBlack) {
        GlassStyle(
            containerColor = if (pureBlack) GlassTokens.Base.copy(alpha = 0.92f) else GlassTokens.Chrome,
        )
    }

    BackHandler { navController.navigateUp() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title = title, onBack = { navController.navigateUp() })

        if (songs.isEmpty()) {
            ScreenStateCard(
                title = "No saved songs yet",
                message = "Songs you add from this artist will show up here.",
                style = stateStyle,
            )
            return@Column
        }

        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .clip(CircleShape)
                .glassSurface(shape = CircleShape, style = GlassStyle(elevation = 0.dp))
                .clickable {
                    player?.playQueue(
                        ListQueue(
                            title = title,
                            items = songs.map { it.toLibraryTrack() },
                        ),
                    )
                }
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                text = if (isPlaying) "Playing · Pause queue" else "Play all",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
                HomeTrackRow(
                    thumbnailUrl = song.thumbnailUrl,
                    songId = song.id,
                    title = song.title,
                    subtitle = formatDuration(song.song.duration),
                    isActive = song.id == currentMetadataState?.value?.id,
                    isPlaying = isPlaying,
                    onClick = {
                        if (song.id == currentMetadataState?.value?.id) {
                            player?.togglePlayPause()
                        } else {
                            player?.playQueue(
                                ListQueue(
                                    title = title,
                                    items = songs.map { it.toLibraryTrack() },
                                    startIndex = index,
                                ),
                            )
                        }
                    },
                    onLongClick = {},
                    onMoreClick = null,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                )
            }
            item {
                Spacer(Modifier.height(56.dp))
            }
        }
    }
}
