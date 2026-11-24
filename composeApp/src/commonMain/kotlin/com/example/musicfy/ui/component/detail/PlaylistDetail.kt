package com.example.musicfy.ui.component.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.arrow_back
import com.example.musicfy.resources.check
import com.example.musicfy.resources.expand_more
import com.example.musicfy.resources.favorite_border
import com.example.musicfy.resources.more_vert
import com.example.musicfy.resources.pause
import com.example.musicfy.resources.play
import com.example.musicfy.resources.queue_music
import com.example.musicfy.resources.search
import com.example.musicfy.resources.shuffle
import com.example.musicfy.ui.component.BlurDirection
import com.example.musicfy.ui.component.Bone
import com.example.musicfy.ui.component.GlassPillBackground
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.ProgressiveGlassBackground
import com.example.musicfy.ui.component.RoundedHighlight
import com.example.musicfy.ui.component.glassSurface
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Common port of the playlist detail chrome. Android-only image blur,
 * RenderEffect sampling and shared-element transitions are omitted; the
 * header, top bar, rows and pills all use the shared glass system.
 */

internal val PlaylistInset = 36.dp
internal val PlaylistTracking = (-0.06).em
internal val PillContent = Color(0xFF2F2F2F)
internal val PillFill = Color.White.copy(alpha = 0.64f)
internal val TrackShape = RoundedCornerShape(9.dp)

private val TrackHighlight = RoundedHighlight(cornerRadius = 100.dp, spillX = (-10).dp)

internal val PlaylistTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = PlaylistTracking,
)
internal val PlaylistCreatorStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.04).em,
)
internal val PlaylistStatsStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.02).em,
)
internal val PillLabelStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    letterSpacing = PlaylistTracking,
)
internal val TrackTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.03).em,
)
internal val TrackSubtitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 11.5.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.02).em,
)
internal val SectionTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    letterSpacing = PlaylistTracking,
)
internal val ReceiptSmallStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 8.5.sp,
    letterSpacing = 0.04.em,
)
internal val ReceiptCenterStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 0.42.em,
)

data class CreatorUi(
    val name: String,
    val avatarUrl: String? = null,
    val artistId: String? = null,
)

data class FeaturedArtistUi(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
)

fun <A> featuredArtistsOf(
    artists: List<A>,
    id: (A) -> String?,
    name: (A) -> String,
    limit: Int = 10,
): List<FeaturedArtistUi> =
    artists
        .mapNotNull { artist -> id(artist)?.let { it to name(artist) } }
        .groupBy({ it.first }, { it.second })
        .entries
        .sortedByDescending { it.value.size }
        .take(limit)
        .map { (artistId, names) -> FeaturedArtistUi(id = artistId, name = names.first()) }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun playlistCoverHeight(): Dp {
    val density = LocalDensity.current
    val heightPx = LocalWindowInfo.current.containerSize.height
    return with(density) { (heightPx * 0.6f).toDp() }
}

@Composable
fun PlaylistDetailScaffold(
    sharedElementKey: String?,
    coverUrl: String?,
    lazyListState: LazyListState,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    showCover: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    topBarOverride: (@Composable () -> Unit)? = null,
    modeBar: PlaylistModeBar? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    headerHeight: Dp? = null,
    roundCapsule: Boolean = false,
    cover: (@Composable (Modifier) -> Unit)? = null,
    coverThemeColor: Color? = null,
    content: LazyListScope.() -> Unit,
) {
    val glassState = LocalGlassState.current
    val density = LocalDensity.current
    val coverHeight = headerHeight ?: playlistCoverHeight()
    val triggerPx = with(density) { (coverHeight * 0.45f).toPx() }

    val scrollOffset by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex * 1000f + lazyListState.firstVisibleItemScrollOffset
        }
    }
    val morph by animateFloatAsState(
        targetValue = if (scrollOffset > triggerPx) 1f else 0f,
        animationSpec = tween(240),
        label = "playlistMorph",
    )
    val background by animateColorAsState(
        targetValue = coverThemeColor ?: glassState.accent,
        animationSpec = tween(300),
        label = "playlistBackground",
    )
    val backgroundState = remember { mutableStateOf(background) }
    backgroundState.value = background

    Box(modifier = modifier.fillMaxSize()) {
        if (showCover) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverHeight)
                    .glassSurface(
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                        style = GlassStyle(containerColor = background.copy(alpha = 0.45f)),
                    ),
            ) {
                if (cover != null) {
                    cover(Modifier.fillMaxSize())
                } else if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl.resize(1200, 1200),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(Res.drawable.queue_music),
                        error = painterResource(Res.drawable.queue_music),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            content = content,
        )

        if (topBarOverride != null) {
            topBarOverride()
        } else {
            PlaylistTopBar(
                glassState = glassState,
                morph = { morph },
                coverUrl = coverUrl,
                cover = cover,
                background = backgroundState,
                onBackClick = onBackClick,
                onBackLongClick = onBackLongClick,
                onMoreClick = onMoreClick,
                onMiniCoverClick = onBackClick,
                roundCapsule = roundCapsule,
                mode = modeBar,
            )
        }
        overlay()
    }
}

@Composable
private fun PlaylistTopBar(
    glassState: GlassState,
    morph: () -> Float,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    background: State<Color>,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    onMiniCoverClick: () -> Unit,
    roundCapsule: Boolean = false,
    mode: PlaylistModeBar? = null,
) {
    // Common-safe top allowance; caller-provided modifiers can add platform insets.
    val top = 16.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = top + 6.dp, start = 26.dp, end = 30.dp)
            .height(47.dp),
    ) {
        if (morph() > 0.01f) {
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 34f * morph().coerceIn(0f, 1f) },
                tint = background.value.copy(alpha = 0.4f),
                foundationColor = background.value,
                direction = BlurDirection.BottomToTop,
                steps = 3,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (mode != null) {
            PlaylistModeBarRow(
                mode = mode,
                glassState = glassState,
                coverUrl = coverUrl,
                cover = cover,
                tint = background,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PlaylistNormalBarRow(
                morph = morph,
                glassState = glassState,
                coverUrl = coverUrl,
                cover = cover,
                background = background,
                onBackClick = onBackClick,
                onBackLongClick = onBackLongClick,
                onMoreClick = onMoreClick,
                onMiniCoverClick = onMiniCoverClick,
                roundCapsule = roundCapsule,
            )
        }
    }
}

@Composable
private fun PlaylistNormalBarRow(
    morph: () -> Float,
    glassState: GlassState,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    background: State<Color>,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    onMiniCoverClick: () -> Unit,
    roundCapsule: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
        BackPill(
            morph = morph,
            glassState = glassState,
            coverUrl = coverUrl,
            cover = cover,
            tint = background,
            onBackClick = onBackClick,
            onBackLongClick = onBackLongClick,
            onMiniCoverClick = onMiniCoverClick,
            round = roundCapsule,
        )
        Spacer(Modifier.weight(1f))
        if (onMoreClick != null) {
            CircleAction(icon = Res.drawable.more_vert, contentDescription = "More", onClick = onMoreClick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackPill(
    morph: () -> Float,
    glassState: GlassState,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    tint: State<Color>,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMiniCoverClick: () -> Unit,
    round: Boolean = false,
) {
    val shape = if (round) CircleShape else RoundedCornerShape(17.dp)
    Box(
        modifier = Modifier
            .size(width = 99.dp, height = 47.dp)
            .graphicsLayer { translationX = 10.dp.toPx() * morph() },
        contentAlignment = Alignment.CenterStart,
    ) {
        GlassPillBackground(
            state = glassState,
            blurRadius = { 18f },
            tint = tint.value.copy(alpha = 0.22f),
            shape = shape,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = { if (morph() > 0.5f) onMiniCoverClick() else onBackClick() },
                    onLongClick = onBackLongClick,
                )
                .padding(start = 8.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.arrow_back),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (cover != null) {
                cover(Modifier.size(29.dp).clip(if (round) CircleShape else TrackShape))
            } else if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl.resize(240, 240),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(Res.drawable.queue_music),
                    error = painterResource(Res.drawable.queue_music),
                    modifier = Modifier.size(29.dp).clip(if (round) CircleShape else TrackShape),
                )
            }
        }
    }
}

@Composable
fun PlaylistHeader(
    title: String,
    creators: List<CreatorUi>,
    stats: String,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean? = null,
    onSaveClick: () -> Unit = {},
    sortControl: (@Composable () -> Unit)? = null,
    description: String? = null,
    onCreatorClick: ((CreatorUi) -> Unit)? = null,
    compact: Boolean = false,
    below: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.padding(start = PlaylistInset + 1.dp, end = PlaylistInset, bottom = 14.dp)) {
        Text(
            text = title,
            style = if (compact) PlaylistCompactTitleStyle else PlaylistTitleStyle,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (creators.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            CreatorRow(creators, onCreatorClick)
        }
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            PlaylistDescription(description)
        }
        Spacer(Modifier.height(10.dp))
        Text(text = stats, style = PlaylistStatsStyle, color = Color.White.copy(alpha = 0.72f))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayPill(isPlaying = isPlaying, onClick = onPlayClick)
            Spacer(Modifier.width(12.dp))
            CircleAction(icon = Res.drawable.shuffle, contentDescription = "Shuffle", onClick = onShuffleClick)
            if (isSaved != null) {
                Spacer(Modifier.width(12.dp))
                CircleAction(
                    icon = if (isSaved) Res.drawable.check else Res.drawable.favorite_border,
                    contentDescription = "Save",
                    onClick = onSaveClick,
                )
            }
            Spacer(Modifier.weight(1f))
            if (sortControl != null) sortControl()
        }
        if (below != null) {
            Spacer(Modifier.height(12.dp))
            below()
        }
    }
}

@Composable
fun playlistCompactHeaderTop(): Dp = 8.dp

@Composable
fun playlistCompactHeaderHeight(): Dp = playlistCompactHeaderTop() + 120.dp

internal val PlaylistCompactTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 36.sp,
    letterSpacing = PlaylistTracking,
)

@Composable
internal fun PlaylistDescription(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTypeStyles.description,
        color = Color.White.copy(alpha = 0.72f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
internal fun PlayPill(isPlaying: Boolean, onClick: () -> Unit) {
    val accent = LocalGlassState.current.accent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .glassSurface(
                shape = CircleShape,
                style = GlassStyle(containerColor = accent.copy(alpha = 0.42f), cornerRadius = 48.dp, elevation = 0.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(if (isPlaying) Res.drawable.pause else Res.drawable.play),
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun CircleAction(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    spinOnClick: Boolean = false,
) {
    val spin by animateFloatAsState(
        targetValue = if (spinOnClick) 1f else 0f,
        animationSpec = tween(520),
        label = "circleActionSpin",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce()
            .graphicsLayer { rotationZ = spin * 360f }
            .size(33.dp)
            .clip(CircleShape)
            .glassSurface(
                shape = CircleShape,
                style = GlassStyle(containerColor = PillFill, cornerRadius = 33.dp, elevation = 0.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = PillContent,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CreatorRow(creators: List<CreatorUi>, onClick: ((CreatorUi) -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        creators.take(3).forEachIndexed { index, creator ->
            if (index > 0) Spacer(Modifier.width(8.dp))
            CreatorAvatar(creator = creator, size = 22.dp, modifier = Modifier.clickable { onClick?.invoke(creator) })
        }
        if (creators.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = creators.joinToString { it.name },
                style = PlaylistCreatorStyle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CreatorAvatar(creator: CreatorUi, size: Dp, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .glassSurface(shape = CircleShape, style = GlassStyle(elevation = 0.dp)),
    ) {
        if (creator.avatarUrl != null) {
            AsyncImage(
                model = creator.avatarUrl!!.resize(size.value.toInt(), size.value.toInt()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(text = creator.name.take(1), color = Color.White, style = PlaylistStatsStyle)
        }
    }
}

@Composable
fun PlaylistSearchControl(onClick: () -> Unit) {
    CircleAction(icon = Res.drawable.search, contentDescription = "Search in playlist", onClick = onClick)
}

@Composable
fun <T : Enum<T>> PlaylistSortControl(
    current: T,
    descending: Boolean,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onToggleDirection: () -> Unit,
    showDirection: (T) -> Boolean = { true },
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = label(current).lowercase(),
                style = PlaylistStatsStyle.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.72f),
            )
            Icon(
                painter = painterResource(Res.drawable.expand_more),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (showDirection(current) && !descending) 180f else 0f },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelect(option)
                        if (option == current) onToggleDirection()
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistTrackRow(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    coverShape: Shape = TrackShape,
    coverModifier: Modifier = Modifier,
    songId: String? = null,
    selected: Boolean = false,
) {
    val band by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "selectedBand")
    val accent = LocalGlassState.current.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .selectedRowBand { band }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .glassSurface(
                shape = RoundedCornerShape(12.dp),
                style = GlassStyle(
                    containerColor = if (isActive) accent.copy(alpha = 0.22f) else GlassTokens.Chrome.copy(alpha = 0.32f),
                    cornerRadius = 12.dp,
                    elevation = 0.dp,
                ),
            )
            .padding(start = PlaylistInset, end = 24.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Box(
            modifier = coverModifier
                .size(46.dp)
                .clip(coverShape)
                .glassSurface(shape = coverShape, style = GlassStyle(elevation = 0.dp)),
        ) {
            AsyncImage(
                model = thumbnailUrl?.resize(240, 240),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.queue_music),
                error = painterResource(Res.drawable.queue_music),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = TrackTitleStyle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitle,
                style = TrackSubtitleStyle,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) trailing()
        Icon(
            painter = painterResource(Res.drawable.more_vert),
            contentDescription = "Track actions",
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.clickable { onMenuClick() }.size(20.dp),
        )
    }
}

@Composable
fun PlaylistTrackBones(rows: Int = 7) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PlaylistInset)) {
        repeat(rows) {
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                Bone(width = 46.dp, height = 46.dp, shape = TrackShape)
                Spacer(Modifier.width(12.dp))
                Column {
                    Bone(width = 170.dp, height = 12.dp)
                    Spacer(Modifier.height(6.dp))
                    Bone(width = 110.dp, height = 10.dp)
                }
            }
        }
    }
}

@Composable
fun PlaylistEndOfLine(
    songCount: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlaylistInset)
            .padding(top = 18.dp, bottom = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.16f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(25.dp)) {
            Text(
                text = "$songCount ${if (songCount == 1) "SONG" else "SONGS"}",
                style = ReceiptSmallStyle,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "END OF LINE",
                style = ReceiptCenterStyle,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            val minutes = (totalSeconds + 30) / 60
            Text(
                text = "$minutes MIN",
                style = ReceiptSmallStyle,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun PlaylistSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = PlaylistInset - 4.dp, end = PlaylistInset)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(text = title, style = SectionTitleStyle, color = Color.White, maxLines = 1)
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(Res.drawable.expand_more),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun FeaturedArtistsRow(
    artists: List<FeaturedArtistUi>,
    onArtistClick: (FeaturedArtistUi) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Featured Artist",
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = PlaylistInset)) {
        Text(text = title, style = SectionTitleStyle, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            artists.take(6).forEach { artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onArtistClick(artist) },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .glassSurface(shape = CircleShape, style = GlassStyle(elevation = 0.dp)),
                    ) {
                        val thumbnail by rememberArtistThumbnail(artist.id, artist.name)
                        val model = artist.thumbnailUrl ?: thumbnail
                        if (model != null) {
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(text = artist.name.take(1), color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(text = artist.name, style = PlaylistStatsStyle, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun rememberArtistThumbnail(artistId: String?, artistName: String): State<String?> {
    // TODO: upstream resolved this from DB then YouTube; common port starts with the name only.
    return remember(artistId, artistName) { mutableStateOf<String?>(null) }
}

private object MaterialTypeStyles {
    val description: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 17.sp)
}
