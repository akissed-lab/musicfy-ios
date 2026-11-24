package com.example.musicfy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.cached
import com.example.musicfy.resources.favorite
import com.example.musicfy.resources.favorite_border
import com.example.musicfy.resources.info
import com.example.musicfy.resources.library_add_check
import com.example.musicfy.resources.more_horiz
import com.example.musicfy.resources.n_song
import com.example.musicfy.resources.play
import com.example.musicfy.resources.queue_music
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Common port of the shared list/grid item kit. Android-only details
 * (LocalContext, Media3 downloads, Coil `ImageRequest.Builder`, shared
 * elements) are replaced with the closest common primitives; the public item
 * names and argument shapes stay intact wherever possible.
 */

const val ActiveBoxAlpha = 0.6f

// TODO: upstream read these from constants/MainActivity. Kept here so the
// ported components have the same sizing contract without touching those files.
val ListItemHeight = 64.dp
val ListThumbnailSize = 48.dp
val SmallGridThumbnailHeight = 104.dp
val GridThumbnailHeight = 164.dp
val ThumbnailCornerRadius = 6.dp
val GridThumbnailCornerRadius = 12.dp

/** Minimal common replacement for upstream `constants.GridItemSize`. */
enum class GridItemSize { BIG, SMALL }

/** Minimal common replacement for upstream root `LocalGridItemSize`. */
val LocalGridItemSize = compositionLocalOf { GridItemSize.BIG }
val LocalGridItemPadding = compositionLocalOf { 12.dp }

@Composable
fun currentGridThumbnailHeight(): Dp =
    if (LocalGridItemSize.current == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight

/** Common replacements for upstream `utils.joinByBullet` / `utils.makeTimeString`. */
fun joinByBullet(vararg parts: String?): String =
    parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" • ")

fun makeTimeString(durationMs: Long?): String {
    val totalSeconds = ((durationMs ?: 0L) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

object ItemIcons {
    @Composable
    fun Favorite() {
        Icon(
            painter = painterResource(Res.drawable.favorite),
            contentDescription = null,
            tint = GlassTokens.Pink,
            modifier = Modifier.size(18.dp),
        )
    }

    @Composable
    fun Explicit() {
        Icon(
            painter = painterResource(Res.drawable.info),
            contentDescription = "Explicit",
            tint = GlassTokens.MutedContent,
            modifier = Modifier.size(14.dp),
        )
    }

    @Composable
    fun Library() {
        Icon(
            painter = painterResource(Res.drawable.library_add_check),
            contentDescription = null,
            tint = GlassTokens.Blue,
            modifier = Modifier.size(18.dp),
        )
    }

    @Composable
    fun Download(state: Int?) {
        Icon(
            painter = painterResource(Res.drawable.cached),
            contentDescription = null,
            tint = if (state == null) GlassTokens.MutedContent else GlassTokens.Blue,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    isAvailable: Boolean = true,
    shape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    drawHighlight: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
) {
    val accent = LocalGlassState.current.accent
    val container = when {
        backgroundColor != Color.Unspecified -> backgroundColor
        isActive -> accent.copy(alpha = 0.24f)
        isSelected == true && drawHighlight -> accent.copy(alpha = 0.16f)
        else -> GlassTokens.Chrome.copy(alpha = 0.42f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(contentPadding)
            .height(ListItemHeight)
            .glassSurface(
                shape = shape,
                style = GlassStyle(
                    containerColor = container,
                    cornerRadius = ThumbnailCornerRadius,
                    elevation = 0.dp,
                ),
            ),
    ) {
        Box(
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            thumbnailContent()
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isAvailable) Color.White else GlassTokens.MutedContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Row { subtitle() }
            }
        }
        Row(horizontalArrangement = Arrangement.End) { trailingContent() }
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: AnnotatedString?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    shape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    drawHighlight: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
) {
    // Explicitly typed so this overload cannot be confused with the String? or
    // composable-subtitle overloads while the lambda is being analysed.
    val subtitleContent: (@Composable RowScope.() -> Unit)? = subtitle?.let { subtitleText ->
        {
            badges()
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.MutedContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    ListItem(
        modifier = modifier,
        title = title,
        subtitle = subtitleContent,
        thumbnailContent = thumbnailContent,
        trailingContent = trailingContent,
        isSelected = isSelected,
        isActive = isActive,
        shape = shape,
        drawHighlight = drawHighlight,
        backgroundColor = backgroundColor,
        contentPadding = contentPadding,
    )
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isSelected: Boolean? = false,
    isActive: Boolean = false,
    shape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    drawHighlight: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
) {
    // Explicitly typed so this overload cannot be confused with the AnnotatedString? or
    // composable-subtitle overloads while the lambda is being analysed.
    val subtitleContent: (@Composable RowScope.() -> Unit)? =
        subtitle?.takeIf { it.isNotEmpty() }?.let { subtitleText ->
            {
                badges()
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.MutedContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    ListItem(
        modifier = modifier,
        title = title,
        subtitle = subtitleContent,
        thumbnailContent = thumbnailContent,
        trailingContent = trailingContent,
        isSelected = isSelected,
        isActive = isActive,
        shape = shape,
        drawHighlight = drawHighlight,
        backgroundColor = backgroundColor,
        contentPadding = contentPadding,
    )
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) {
    val gridHeight = currentGridThumbnailHeight()
    val padding = LocalGridItemPadding.current
    Column(
        modifier = if (fillMaxWidth) {
            modifier.padding(padding).fillMaxWidth()
        } else {
            modifier.padding(padding).width(gridHeight * thumbnailRatio)
        },
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(gridHeight)
            }.aspectRatio(thumbnailRatio),
        ) {
            thumbnailContent()
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { title() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            badges()
            subtitle()
        }
    }
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) = GridItem(
    modifier = modifier,
    title = {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (fillMaxWidth) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    },
    subtitle = {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = GlassTokens.MutedContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
    badges = badges,
    thumbnailContent = thumbnailContent,
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
)

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    downloadState: Int? = null,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) ItemIcons.Favorite()
        if (song.song.explicit) ItemIcons.Explicit()
        if (showInLibraryIcon && song.song.inLibrary != null) ItemIcons.Library()
        if (showDownloadIcon && downloadState != null) ItemIcons.Download(downloadState)
    },
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    modifier = modifier,
    title = song.title,
    subtitle = joinByBullet(song.artists.joinToString { it.name }, song.album?.title, makeTimeString(song.song.duration * 1000L)),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = song.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = shape,
            albumIndex = albumIndex,
            modifier = Modifier.size(ListThumbnailSize),
        )
    },
    trailingContent = trailingContent,
    isSelected = isSelected,
    isActive = isActive,
    shape = shape,
)

@Composable
fun SongGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    downloadState: Int? = null,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) ItemIcons.Favorite()
        if (showInLibraryIcon && song.song.inLibrary != null) ItemIcons.Library()
        if (showDownloadIcon && downloadState != null) ItemIcons.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    modifier = modifier,
    title = song.title,
    subtitle = song.artists.joinToString { it.name },
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = song.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            modifier = Modifier.fillMaxSize(),
        )
    },
    fillMaxWidth = fillMaxWidth,
)

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) ItemIcons.Favorite()
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    modifier = modifier,
    title = artist.artist.name,
    subtitle = pluralStringResource(Res.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(Res.drawable.queue_music),
            error = painterResource(Res.drawable.queue_music),
            modifier = Modifier.size(ListThumbnailSize).clip(CircleShape),
        )
    },
    trailingContent = trailingContent,
    backgroundColor = backgroundColor,
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) ItemIcons.Favorite()
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    modifier = modifier,
    title = artist.artist.name,
    subtitle = pluralStringResource(Res.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(Res.drawable.queue_music),
            error = painterResource(Res.drawable.queue_music),
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
    },
    fillMaxWidth = fillMaxWidth,
)

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    downloadState: Int? = null,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && album.album.bookmarkedAt != null) ItemIcons.Favorite()
        if (album.album.explicit) ItemIcons.Explicit()
        if (downloadState != null) ItemIcons.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    modifier = modifier,
    title = album.album.title,
    subtitle = joinByBullet(
        album.artists.joinToString { it.name },
        pluralStringResource(Res.plurals.n_song, album.album.songCount, album.album.songCount),
        album.album.year?.toString(),
    ),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = Modifier.size(ListThumbnailSize),
        )
    },
    trailingContent = trailingContent,
    isActive = isActive,
    backgroundColor = backgroundColor,
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    downloadState: Int? = null,
    badges: @Composable RowScope.() -> Unit = {
        if (album.album.bookmarkedAt != null) ItemIcons.Favorite()
        if (downloadState != null) ItemIcons.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    sharedElementKey: String? = null,
) = GridItem(
    modifier = modifier,
    title = album.album.title,
    subtitle = album.artists.joinToString { it.name },
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            modifier = Modifier.fillMaxSize(),
        )
    },
    fillMaxWidth = fillMaxWidth,
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    downloadState: Int? = null,
    badges: @Composable RowScope.() -> Unit = {
        if (downloadState != null) ItemIcons.Download(downloadState)
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
    shape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    backgroundColor: Color = Color.Unspecified,
) {
    val count = if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
        playlist.playlist.remoteSongCount!!
    } else {
        playlist.songCount
    }
    ListItem(
        modifier = modifier,
        title = playlist.playlist.name,
        subtitle = if (autoPlaylist) null else pluralStringResource(Res.plurals.n_song, count, count),
        badges = badges,
        thumbnailContent = {
            PlaylistThumbnail(
                thumbnails = playlist.thumbnails,
                size = ListThumbnailSize,
                placeHolder = {
                    Icon(
                        painter = painterResource(Res.drawable.queue_music),
                        contentDescription = null,
                        tint = GlassTokens.MutedContent,
                    )
                },
                shape = shape,
            )
        },
        trailingContent = trailingContent,
        shape = shape,
        backgroundColor = backgroundColor,
    )
}

@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    downloadState: Int? = null,
    badges: @Composable RowScope.() -> Unit = {
        if (downloadState != null) ItemIcons.Download(downloadState)
    },
    fillMaxWidth: Boolean = false,
    sharedElementKey: String? = null,
) {
    val count = if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
        playlist.playlist.remoteSongCount!!
    } else {
        playlist.songCount
    }
    GridItem(
        modifier = modifier,
        title = playlist.playlist.name,
        subtitle = if (autoPlaylist) "" else pluralStringResource(Res.plurals.n_song, count, count),
        badges = badges,
        thumbnailContent = {
            PlaylistThumbnail(
                thumbnails = playlist.thumbnails,
                size = maxOf(gridHeightForThumbnail(), 1.dp),
                placeHolder = {
                    Icon(
                        painter = painterResource(Res.drawable.queue_music),
                        contentDescription = null,
                        tint = GlassTokens.MutedContent,
                    )
                },
                shape = RoundedCornerShape(GridThumbnailCornerRadius),
                modifier = Modifier.fillMaxSize(),
            )
        },
        fillMaxWidth = fillMaxWidth,
    )
}

@Composable
private fun gridHeightForThumbnail(): Dp = currentGridThumbnailHeight()

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val subtitle = buildAnnotatedString {
        append(mediaMetadata.artists.joinToString { it.name })
        append(" • ")
        append(makeTimeString(mediaMetadata.duration * 1000L))
        if (mediaMetadata.suggestedBy != null) {
            append(" • ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(mediaMetadata.suggestedBy!!) }
        }
    }
    ListItem(
        modifier = modifier,
        title = mediaMetadata.title,
        subtitle = subtitle,
        badges = { if (mediaMetadata.explicit) ItemIcons.Explicit() },
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = shape,
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        trailingContent = trailingContent,
        isSelected = isSelected,
        isActive = isActive,
        shape = shape,
    )
}

@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isLiked: Boolean? = null,
    downloadState: Int? = null,
    badges: @Composable RowScope.() -> Unit = {
        if (isLiked == true) ItemIcons.Favorite()
        if (item.explicit) ItemIcons.Explicit()
        if (item is SongItem && downloadState != null) ItemIcons.Download(downloadState)
    },
    shape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    drawHighlight: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
) {
    val subtitle = when (item) {
        is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
        is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
        is ArtistItem -> null
        is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
    }
    ListItem(
        modifier = modifier,
        title = item.title,
        subtitle = subtitle,
        badges = badges,
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = item.thumbnail,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = if (item is ArtistItem) CircleShape else shape,
                albumIndex = albumIndex,
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        trailingContent = trailingContent,
        isSelected = isSelected,
        isActive = isActive,
        shape = shape,
        drawHighlight = drawHighlight,
        backgroundColor = backgroundColor,
    )
}

@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    isLiked: Boolean? = null,
    downloadState: Int? = null,
    badges: @Composable RowScope.() -> Unit = {
        if (isLiked == true) ItemIcons.Favorite()
        if (item.explicit) ItemIcons.Explicit()
        if (item is SongItem && downloadState != null) ItemIcons.Download(downloadState)
    },
    thumbnailRatio: Float = if (item is SongItem) 16f / 9f else 1f,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    sharedElementKey: String? = null,
) = GridItem(
    modifier = modifier,
    title = item.title,
    subtitle = when (item) {
        is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
        is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
        is ArtistItem -> ""
        is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
    },
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = item.thumbnail,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(GridThumbnailCornerRadius),
            modifier = Modifier.fillMaxSize(),
            thumbnailRatio = thumbnailRatio,
        )
    },
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
)

@Composable
fun LocalSongsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier,
) = GridItem(
    modifier = modifier,
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            modifier = Modifier.fillMaxSize(),
            showCenterPlay = true,
        )
    },
    fillMaxWidth = fillMaxWidth,
)

@Composable
fun LocalArtistsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier,
) = GridItem(
    modifier = modifier,
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = CircleShape,
            modifier = Modifier.fillMaxSize(),
        )
    },
    fillMaxWidth = fillMaxWidth,
)

@Composable
fun LocalAlbumsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier,
) = GridItem(
    modifier = modifier,
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(GridThumbnailCornerRadius),
            modifier = Modifier.fillMaxSize(),
            playButtonVisible = true,
        )
    },
    fillMaxWidth = fillMaxWidth,
)

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    thumbnailRatio: Float = 1f,
) = ItemThumbnail(
    thumbnailUrl = thumbnailUrl,
    isActive = isActive,
    isPlaying = { isPlaying },
    shape = shape,
    modifier = modifier,
    albumIndex = albumIndex,
    isSelected = isSelected,
    thumbnailRatio = thumbnailRatio,
)

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: () -> Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    thumbnailRatio: Float = 1f,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(thumbnailRatio)
            .clip(shape)
            .glassSurface(
                shape = shape,
                style = GlassStyle(
                    containerColor = GlassTokens.Chrome.copy(alpha = 0.35f),
                    elevation = 0.dp,
                ),
            ),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(Res.drawable.queue_music),
            error = painterResource(Res.drawable.queue_music),
            modifier = Modifier.fillMaxSize(),
        )
        if (isActive) {
            Box(
                modifier = Modifier.fillMaxSize().background(LocalGlassState.current.accent.copy(alpha = ActiveBoxAlpha)),
            )
        }
        this.OverlayPlayButton(visible = isActive && isPlaying())
        if (isSelected) {
            Icon(
                painter = painterResource(Res.drawable.favorite_border),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp),
            )
        }
    }
}

@Composable
fun LocalThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    showCenterPlay: Boolean = false,
    playButtonVisible: Boolean = false,
    thumbnailRatio: Float = 1f,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(thumbnailRatio)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp)),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(Res.drawable.queue_music),
            error = painterResource(Res.drawable.queue_music),
            modifier = Modifier.fillMaxSize(),
        )
        this.OverlayPlayButton(visible = isActive && showCenterPlay)
        if (playButtonVisible) {
            this.AlbumPlayButton(visible = isActive, onClick = {})
        }
    }
}

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape,
    cacheKey: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp)),
    ) {
        val first = thumbnails.firstOrNull()
        if (first == null) {
            placeHolder()
        } else {
            AsyncImage(
                model = first,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.queue_music),
                error = painterResource(Res.drawable.queue_music),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun BoxScope.OverlayPlayButton(visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .glassSurface(
                    shape = CircleShape,
                    style = GlassStyle(containerColor = Color.Black.copy(alpha = ActiveBoxAlpha), elevation = 0.dp),
                ),
        ) {
            Icon(
                painter = painterResource(Res.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.OverlayEditButton(
    visible: Boolean,
    onClick: () -> Unit,
    alignment: Alignment = Alignment.Center,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(alignment)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .glassSurface(
                    shape = CircleShape,
                    style = GlassStyle(containerColor = Color.Black.copy(alpha = ActiveBoxAlpha), elevation = 0.dp),
                )
                .combinedClickable(onClick = onClick),
        ) {
            Icon(
                painter = painterResource(Res.drawable.more_horiz),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun BoxScope.AlbumPlayButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .glassSurface(
                    shape = CircleShape,
                    style = GlassStyle(containerColor = Color.Black.copy(alpha = ActiveBoxAlpha), elevation = 0.dp),
                )
                .clickable(onClick = onClick),
        ) {
            Icon(
                painter = painterResource(Res.drawable.play),
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
fun LikedSongsThumbnail(
    size: Dp,
    shape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp)),
    ) {
        Icon(
            painter = painterResource(Res.drawable.favorite),
            contentDescription = null,
            tint = GlassTokens.Pink,
            modifier = Modifier.size(size / 2),
        )
    }
}
