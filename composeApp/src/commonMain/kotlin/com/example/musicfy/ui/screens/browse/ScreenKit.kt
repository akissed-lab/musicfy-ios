package com.example.musicfy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.musicfy.db.entities.Song
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.PlaybackController
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.TrackSource
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.arrow_back
import com.example.musicfy.resources.queue_music
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.YouTubeGridItem
import com.example.musicfy.ui.component.YouTubeListItem
import com.example.musicfy.ui.component.glassSurface
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import org.jetbrains.compose.resources.painterResource

/**
 * Shared pieces for the ported online detail screens. Kept in one file so the
 * playlist, album, artist and browse pages all resolve items, loading state and
 * navigation the same way. Android-only menus, blur and swipe boxes are absent.
 */

internal fun SongItem.toRemoteTrack(): PlaybackTrack = PlaybackTrack(
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

internal fun Song.toLibraryTrack(): PlaybackTrack {
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

/** Shared item click behaviour for every online page. */
internal fun openYouTubeItem(
    item: YTItem,
    navController: NavController,
    player: PlaybackController? = null,
    currentTrackId: String? = null,
) {
    when (item) {
        is SongItem -> {
            if (item.id == currentTrackId) {
                player?.togglePlayPause()
            } else {
                player?.playQueue(ListQueue(title = item.title, items = listOf(item.toRemoteTrack())))
            }
        }

        is AlbumItem -> navController.navigate("album/${item.id}")
        is ArtistItem -> navController.navigate("artist/${item.id}")
        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
    }
}

internal fun playSongList(
    songs: List<SongItem>,
    player: PlaybackController?,
    title: String,
    startIndex: Int = 0,
    shuffled: Boolean = false,
) {
    if (songs.isEmpty() || player == null) return
    val ordered = if (shuffled) songs.shuffled() else songs
    val first = (if (shuffled) 0 else startIndex).coerceIn(0, ordered.lastIndex)
    player.playQueue(
        ListQueue(
            title = title,
            items = ordered.map { it.toRemoteTrack() },
            startIndex = first,
        ),
    )
}

internal fun itemSubtitle(item: YTItem): String? = when (item) {
    is SongItem -> item.artists.joinToString { it.name }.ifBlank { null }
    is AlbumItem -> listOfNotNull(
        item.artists?.joinToString { it.name }?.ifBlank { null },
        item.year?.toString(),
    ).joinToString(" · ").ifBlank { null }
    is ArtistItem -> null
    is PlaylistItem -> listOfNotNull(item.author?.name, item.songCountText).joinToString(" · ").ifBlank { null }
}

internal fun itemIsActive(item: YTItem, currentTrackId: String?, currentAlbumId: String? = null): Boolean =
    when (item) {
        is SongItem -> item.id == currentTrackId
        is AlbumItem -> item.id == currentAlbumId
        else -> false
    }

internal fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}

internal fun detailStats(songCount: Int, totalSeconds: Int, extra: String? = null): String =
    listOfNotNull(
        "$songCount ${if (songCount == 1) "song" else "songs"}",
        if (totalSeconds > 0) "${(totalSeconds + 30) / 60} min" else null,
        extra?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

/** Shared back pill; used by pages without a playlist scaffold. */
@Composable
internal fun ScreenBackPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .glassSurface(shape = CircleShape, style = GlassStyle(elevation = 0.dp))
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(Res.drawable.arrow_back),
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Frosted title bar for browse/genre/similar-style pages. */
@Composable
internal fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .glassSurface(
                shape = RoundedCornerShape(20.dp),
                style = GlassStyle(elevation = 0.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        ScreenBackPill(onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Readable loading / error / empty state, replacing Android shimmer and Lottie. */
@Composable
internal fun ScreenStateCard(
    title: String,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    style: GlassStyle = GlassStyle(),
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        style = style,
    ) {
        Text(text = title, color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                color = GlassTokens.MutedContent,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Retry",
                color = GlassTokens.Pink,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun ScreenSectionTitle(title: String, onClick: (() -> Unit)? = null) {
    Text(
        text = title,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
internal fun ScreenItemRow(
    item: YTItem,
    currentTrackId: String?,
    currentAlbumId: String? = null,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
) {
    YouTubeListItem(
        item = item,
        isActive = itemIsActive(item, currentTrackId, currentAlbumId),
        isPlaying = isPlaying,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 3.dp),
    )
}

@Composable
internal fun ScreenItemGrid(
    entries: List<YTItem>,
    currentTrackId: String?,
    currentAlbumId: String? = null,
    isPlaying: Boolean = false,
    onClick: (YTItem) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(entries, key = { it.id }) { item ->
            Box(modifier = Modifier.clickable { onClick(item) }) {
                YouTubeGridItem(
                    item = item,
                    isActive = itemIsActive(item, currentTrackId, currentAlbumId),
                    isPlaying = isPlaying,
                    fillMaxWidth = true,
                )
            }
        }
    }
}

@Composable
internal fun ScreenArtwork(url: String?, circular: Boolean = false) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(if (circular) CircleShape else RoundedCornerShape(6.dp))
            .background(GlassTokens.Chrome),
    ) {
        coil3.compose.AsyncImage(
            model = url,
            contentDescription = null,
            placeholder = painterResource(Res.drawable.queue_music),
            error = painterResource(Res.drawable.queue_music),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
