// LibraryDesign.kt
//
// KMP port of the Android Library design kit. The original leaned on
// SearchColors / SearchField / shared-element helpers from the Android-only
// Search screen; this port builds every card, row and divider from the shared
// glass primitives instead. The public composable names and argument shapes
// are kept so screens ported from upstream read the same way.

package com.example.musicfy.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.music_note
import com.example.musicfy.resources.play
import com.example.musicfy.resources.shuffle
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.HomeTrackRow
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.glassSurface
import org.jetbrains.compose.resources.painterResource

/** Title + subtitle block height, for the collapsing header on Library screens. */
val LibraryTitleBlockHeight: Dp = 78.dp

/** Horizontal inset of Library content. Mirrors the shared search inset without importing SearchDesign. */
val LibraryHorizontalPadding: Dp = 24.dp

// ---------------------------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------------------------

@Composable
fun LibraryRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LibraryHorizontalPadding)
            .height(1.dp)
            .background(GlassTokens.Stroke.copy(alpha = 0.35f)),
    )
}

@Composable
fun LibrarySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = LibraryHorizontalPadding),
    )
}

/**
 * Artwork whose size comes entirely from the caller's modifier, for grid cells whose width isn't
 * known ahead of time. Always a rounded square: the Library deliberately has no circular artwork
 * anywhere, including artists, so rows and grids line up on one edge.
 */
@Composable
fun LibraryGridArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    round: Boolean = false,
) {
    val shape = if (round) CircleShape else RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.music_note),
                error = painterResource(Res.drawable.music_note),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.music_note),
                contentDescription = null,
                tint = GlassTokens.MutedContent,
                modifier = Modifier.fillMaxSize(0.35f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pinned
// ---------------------------------------------------------------------------------------------

/**
 * A pinned item: artwork only. With a 3-wide grid and a 9-item cap the art alone identifies what is
 * pinned. Shared-element transitions are Android-only; [sharedElementKey] is kept for source
 * compatibility and is a no-op in the common port.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPinnedTile(
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") sharedElementKey: String? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.music_note),
                error = painterResource(Res.drawable.music_note),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.music_note),
                contentDescription = null,
                tint = GlassTokens.MutedContent,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Category + wide cards
// ---------------------------------------------------------------------------------------------

/** Two real covers, fanned and tucked into the bottom-right corner of a card. */
@Composable
private fun LibraryCoverStack(
    covers: List<String?>,
    modifier: Modifier = Modifier,
    tile: Dp = 46.dp,
    round: Boolean = false,
    front: (@Composable (Modifier) -> Unit)? = null,
) {
    val shown = covers.filterNotNull().take(2)
    if (shown.isEmpty() && front == null) return
    val back = if (front != null) shown.firstOrNull() else shown.getOrNull(1)
    val corner = if (round) tile / 2 else tile * 0.22f

    Box(modifier = modifier.size(width = tile * 1.55f, height = tile)) {
        if (back != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(tile)
                    .graphicsLayer {
                        alpha = 0.55f
                        rotationZ = if (round) 0f else -9f
                    },
            ) {
                LibraryGridArtwork(url = back, corner = corner, round = round, modifier = Modifier.fillMaxSize())
            }
        }
        val frontModifier = Modifier
            .align(Alignment.CenterEnd)
            .size(tile)
            .graphicsLayer { rotationZ = if (round) 0f else 7f }
        if (front != null) {
            front(frontModifier.clip(if (round) CircleShape else RoundedCornerShape(corner)))
        } else {
            Box(modifier = frontModifier) {
                LibraryGridArtwork(url = shown[0], corner = corner, round = round, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** A card's label, a step above its count. */
@Composable
private fun LibraryCardLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The big count on every Library card, tinted with the current glass accent. */
@Composable
private fun LibraryCount(count: Int) {
    Text(
        text = count.toString(),
        color = LocalGlassState.current.accent.copy(alpha = 0.85f),
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
}

/** Centred placeholder for a screen or section with nothing in it. */
@Composable
fun LibraryEmptyState(modifier: Modifier = Modifier, text: String = "Nothing to show here") {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LibraryHorizontalPadding, vertical = 56.dp)
            .glassSurface(shape = RoundedCornerShape(20.dp), style = GlassStyle(elevation = 6.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = GlassTokens.MutedContent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * One of the half-width cards: Songs / Playlist / Albums / Artist, and one per service music was
 * imported from, which carries that service's [icon] before its name.
 */
@Composable
fun LibraryCategoryCard(
    title: String,
    count: Int,
    covers: List<String?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    roundCovers: Boolean = false,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(86.dp)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 8.dp))
            .clickable(onClick = onClick),
    ) {
        LibraryCoverStack(
            covers = covers,
            round = roundCovers,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 12.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 12.dp, end = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                LibraryCardLabel(title)
            }
            LibraryCount(count)
        }
    }
}

/** The full-width cards: "Recently added" and "Liked". */
@Composable
fun LibraryWideCard(
    title: String,
    count: Int,
    covers: List<String?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") sharedElementKey: String? = null,
    art: (@Composable (Modifier) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 8.dp))
            .clickable(onClick = onClick),
    ) {
        LibraryCoverStack(
            covers = covers,
            tile = 60.dp,
            front = art,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 14.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp, end = 110.dp),
        ) {
            LibraryCardLabel(title)
            LibraryCount(count)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// List rows
// ---------------------------------------------------------------------------------------------

/** A row in the A-Z lists, drawn with the shared glass track row. */
@Composable
fun LibraryListRow(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    round: Boolean = false,
    @Suppress("UNUSED_PARAMETER") sharedElementKey: String? = null,
    songId: String? = null,
) {
    val coverShape: Shape = if (round) CircleShape else RoundedCornerShape(14.dp)
    // TODO: shared-element transitions are Android-only; sharedElementKey is a no-op for now.
    HomeTrackRow(
        thumbnailUrl = thumbnailUrl,
        title = title,
        subtitle = subtitle,
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        onMoreClick = null,
        coverShape = coverShape,
        songId = songId,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LibraryHorizontalPadding, vertical = 3.dp),
    )
}

/** The header above each section of an A-Z list. */
@Composable
fun LibraryLetterHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = Color.White,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        modifier = modifier.padding(horizontal = LibraryHorizontalPadding, vertical = 10.dp),
    )
}

/** Play and shuffle above the Albums and Recently-added grids. */
@Composable
fun LibraryPlayBar(
    onPlay: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalGlassState.current.accent
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = LibraryHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .glassSurface(shape = CircleShape)
                .clickable(onClick = onPlay),
        ) {
            Icon(
                painter = painterResource(Res.drawable.play),
                contentDescription = "Play",
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .glassSurface(shape = CircleShape, style = GlassStyle(elevation = 0.dp))
                .clickable(onClick = onMore),
        ) {
            Icon(
                painter = painterResource(Res.drawable.shuffle),
                contentDescription = "Shuffle",
                tint = GlassTokens.MutedContent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
