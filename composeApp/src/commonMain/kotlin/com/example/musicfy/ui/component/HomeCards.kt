package com.example.musicfy.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.cached
import com.example.musicfy.resources.expand_more
import com.example.musicfy.resources.more_vert
import com.example.musicfy.resources.queue_music
import org.jetbrains.compose.resources.painterResource

/**
 * Common port of the Home card kit. Missing Android/shared-element helpers
 * are kept as minimal common stubs with TODOs; every card and row now uses the
 * shared glass surface.
 */

val HomeContentInset = 36.dp
val HomeCardSpacing = 15.dp
val HomeCardCornerRadius = 20.dp

private val HomeTracking = (-0.06).em

// TODO: upstream used the static InterFontFamily; the common theme provides
// interFontFamily() as a @Composable, so these stable styles use the platform
// default family and callers can copy() in a composable to apply Inter.
val HomeLabelStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 19.sp,
    letterSpacing = HomeTracking,
)

val HomeTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = HomeTracking,
)

private val HomeCardTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.03).em,
)

private val HomeCardSubtitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 15.sp,
    letterSpacing = (-0.02).em,
)

private val SectionLabelColor = Color.White.copy(alpha = 0.69f)
private val HomeRowShape = RoundedCornerShape(14.dp)
val HomeRowHeight = 54.dp

@Composable
fun homeRowHeight(): Dp {
    val titleLine = 17.sp
    val subtitleLine = HomeCardSubtitleStyle.lineHeight
    val linesHeight = with(LocalDensity.current) { titleLine.toDp() + subtitleLine.toDp() }
    return maxOf(HomeRowHeight, linesHeight + 14.dp)
}

/**
 * Minimal common replacement for upstream `RoundedHighlight`. It keeps the
 * sizing parameters used by Home; the Android ripple-node drawing is a TODO.
 */
data class RoundedHighlight(
    val cornerRadius: Dp,
    val spillX: Dp = 0.dp,
    val spillY: Dp = 0.dp,
)

/** Shared-element stub: shared-element transitions are Android-only for now. */
fun Modifier.homeSharedElement(key: String?): Modifier = this

val HomeCardHighlight = RoundedHighlight(cornerRadius = HomeCardCornerRadius + 6.dp, spillX = 7.dp, spillY = 7.dp)

@Composable
fun homeCardSize(): Dp =
    if (LocalGridItemSize.current == GridItemSize.BIG) 137.dp else 104.dp

@Composable
fun HomeSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onPlayAllClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(start = HomeContentInset - 12.dp, end = HomeContentInset - 12.dp, top = 4.dp, bottom = 4.dp),
    ) {
        if (thumbnail != null) {
            thumbnail()
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = HomeLabelStyle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (label != null) {
                Text(text = label, style = MaterialTheme.typography.bodySmall, color = SectionLabelColor, maxLines = 1)
            }
        }
        Icon(
            painter = painterResource(Res.drawable.expand_more),
            contentDescription = null,
            tint = SectionLabelColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun HomeCoverCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    sharedElementKey: String? = null,
    badges: @Composable RowScopeBadges.() -> Unit = {},
    cover: (@Composable (Dp) -> Unit)? = null,
) = HomeCoverCard(
    title = title,
    subtitle = subtitle,
    thumbnailUrl = thumbnailUrl,
    modifier = modifier,
    circular = circular,
    isActive = isActive,
    isPlaying = { isPlaying },
    sharedElementKey = sharedElementKey,
    badges = badges,
    cover = cover,
)

// Type alias keeps the upstream RowScope badge signature readable in commonMain.
typealias RowScopeBadges = androidx.compose.foundation.layout.RowScope

@Composable
fun HomeCoverCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    isActive: Boolean = false,
    isPlaying: () -> Boolean,
    sharedElementKey: String? = null,
    badges: @Composable RowScopeBadges.() -> Unit = {},
    cover: (@Composable (Dp) -> Unit)? = null,
) {
    val size = homeCardSize()
    val shape = if (circular) CircleShape else RoundedCornerShape(HomeCardCornerRadius)
    Column(modifier = modifier.width(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .homeSharedElement(sharedElementKey)
                .clip(shape)
                .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp)),
        ) {
            if (cover != null) {
                cover(size)
            } else {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(Res.drawable.queue_music),
                    error = painterResource(Res.drawable.queue_music),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PlayingIndicatorBox(isActive = isActive, isPlaying = isPlaying)
        }
        Spacer(Modifier.height(8.dp))
        Text(text = title, style = HomeCardTitleStyle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(text = subtitle, style = HomeCardSubtitleStyle, color = SectionLabelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row { badges() }
    }
}

const val HomeVideoCardAspect = 314f / 247f

@Composable
fun HomeVideoCard(
    title: String,
    subtitle: String?,
    videoId: String,
    fallbackThumbnailUrl: String?,
    width: Dp,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) = HomeVideoCard(title, subtitle, videoId, fallbackThumbnailUrl, width, isActive, { isPlaying }, modifier)

@Composable
fun HomeVideoCard(
    title: String,
    subtitle: String?,
    videoId: String,
    fallbackThumbnailUrl: String?,
    width: Dp,
    isActive: Boolean,
    isPlaying: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(HomeCardCornerRadius)
    val scrim = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.62f),
        0.24f to Color.Black.copy(alpha = 0.34f),
        0.52f to Color.Transparent,
    )
    Box(
        modifier = modifier
            .width(width)
            .height(width / HomeVideoCardAspect)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp)),
    ) {
        AsyncImage(
            model = if (fallbackThumbnailUrl != null) fallbackThumbnailUrl else "https://i.ytimg.com/vi/$videoId/hq720.jpg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(Res.drawable.queue_music),
            error = painterResource(Res.drawable.queue_music),
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(scrim))
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = HomeLabelStyle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = SectionLabelColor, maxLines = 1)
            }
        }
        PlayingIndicatorBox(isActive = isActive, isPlaying = isPlaying, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTrackRow(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    coverShape: Shape = HomeRowShape,
    coverModifier: Modifier = Modifier,
    songId: String? = null,
) = HomeTrackRow(
    thumbnailUrl, title, subtitle, isActive, { isPlaying }, onClick, onLongClick, onMoreClick,
    modifier, rank, coverShape, coverModifier, songId,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTrackRow(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    isActive: Boolean,
    isPlaying: () -> Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    coverShape: Shape = HomeRowShape,
    coverModifier: Modifier = Modifier,
    songId: String? = null,
) {
    val accent = LocalGlassState.current.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(homeRowHeight())
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .glassSurface(
                shape = RoundedCornerShape(22.dp),
                style = GlassStyle(
                    containerColor = if (isActive) accent.copy(alpha = 0.2f) else GlassTokens.Chrome.copy(alpha = 0.35f),
                    cornerRadius = 22.dp,
                    elevation = 0.dp,
                ),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (rank != null) {
            Text(text = rank.toString(), style = HomeTitleStyle, color = SectionLabelColor, modifier = Modifier.padding(end = 8.dp))
        }
        Box(modifier = coverModifier.size(46.dp).clip(coverShape).glassSurface(shape = coverShape, style = GlassStyle(elevation = 0.dp))) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.queue_music),
                error = painterResource(Res.drawable.queue_music),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = HomeCardTitleStyle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(text = subtitle, style = HomeCardSubtitleStyle, color = SectionLabelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        OnDeviceMarks(songId = songId)
        PlayingIndicatorBox(isActive = isActive, isPlaying = isPlaying)
        if (onMoreClick != null) {
            Icon(
                painter = painterResource(Res.drawable.more_vert),
                contentDescription = null,
                tint = SectionLabelColor,
                modifier = Modifier.clickable { onMoreClick() }.size(20.dp),
            )
        }
    }
}

@Composable
fun HomeSectionBones(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(6) {
            Row(modifier = Modifier.padding(vertical = 5.dp)) {
                Bone(width = 46.dp, height = 46.dp, shape = HomeRowShape)
                Spacer(Modifier.width(12.dp))
                Column {
                    Bone(width = 160.dp, height = 12.dp)
                    Spacer(Modifier.height(6.dp))
                    Bone(width = 110.dp, height = 10.dp)
                }
            }
        }
    }
}

/** Minimal common replacement for the Android-only shared playing indicator. */
@Composable
fun PlayingIndicatorBox(
    isActive: Boolean,
    isPlaying: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isActive || !isPlaying()) return
    val accent = LocalGlassState.current.accent
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .padding(8.dp)
            .glassSurface(
                shape = RoundedCornerShape(10.dp),
                style = GlassStyle(containerColor = Color.Black.copy(alpha = 0.45f), cornerRadius = 10.dp, elevation = 0.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .width(3.dp)
                    .height((10 + index * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
        }
    }
}

/** Minimal common replacement for upstream `OnDeviceMarks`. */
@Composable
fun OnDeviceMarks(songId: String?, modifier: Modifier = Modifier) {
    if (songId == null) return
    Icon(
        painter = painterResource(Res.drawable.cached),
        contentDescription = null,
        tint = GlassTokens.Blue,
        modifier = modifier.size(16.dp).padding(end = 4.dp),
    )
}
