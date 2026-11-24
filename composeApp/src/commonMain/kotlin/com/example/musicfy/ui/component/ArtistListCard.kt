package com.example.musicfy.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.musicfy.models.ArtistGroup
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.person
import com.example.musicfy.resources.queue_music
import com.music.innertube.models.YTItem
import org.jetbrains.compose.resources.painterResource

/**
 * Common port of the Android "artist list" card.
 *
 * Palette extraction, `Dispatchers.IO` and Android colour blending are gone;
 * the same deterministically tinted glass card is built from the shared glass
 * accent and a fixed local palette. The card keeps the upstream public name and
 * the [ArtistGroup] contract so Home can pass its rows through unchanged.
 */
@Composable
fun ArtistListCard(
    group: ArtistGroup,
    onClick: () -> Unit,
    onItemClick: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlassState.current
    val glow by animateFloatAsState(
        targetValue = if (group.items.isEmpty()) 0.72f else 1f,
        animationSpec = tween(800),
        label = "artistListGlow",
    )
    val palette = remember(group.artistName) {
        val palettes = listOf(
            GlassTokens.Pink to GlassTokens.Purple,
            GlassTokens.Blue to GlassTokens.Purple,
            GlassTokens.Pink to GlassTokens.Blue,
            GlassTokens.Purple to GlassTokens.Pink,
            GlassTokens.Blue to GlassTokens.Pink,
        )
        palettes[(group.artistName.hashCode() and 0x7FFFFFFF) % palettes.size]
    }
    val gradient = Brush.verticalGradient(
        listOf(
            palette.first.copy(alpha = 0.42f * glow),
            palette.second.copy(alpha = 0.24f * glow),
            GlassTokens.Base.copy(alpha = 0.78f),
        ),
    )
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .width(276.dp)
            .height(344.dp)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp))
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(gradient))

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .glassSurface(
                            shape = CircleShape,
                            style = GlassStyle(
                                containerColor = glass.accent.copy(alpha = 0.24f),
                                elevation = 0.dp,
                            ),
                        ),
                ) {
                    if (group.artistThumbnailUrl != null) {
                        AsyncImage(
                            model = group.artistThumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(Res.drawable.person),
                            error = painterResource(Res.drawable.person),
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Icon(
                            painter = painterResource(Res.drawable.person),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(26.dp).align(Alignment.Center),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Similar to",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTokens.MutedContent,
                        maxLines = 1,
                    )
                    Text(
                        text = group.artistName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        fontSize = 24.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(start = 24.dp),
            ) {
                group.items.take(3).forEach { item ->
                    ArtistCover(
                        thumbnailUrl = item.thumbnail,
                        size = 96.dp,
                        circular = false,
                        onClick = { onItemClick(item) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(start = 24.dp),
            ) {
                group.items.drop(3).take(2).forEach { item ->
                    ArtistCover(
                        thumbnailUrl = item.thumbnail,
                        size = 160.dp,
                        circular = true,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistCover(
    thumbnailUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    circular: Boolean,
    onClick: () -> Unit,
) {
    val shape = if (circular) CircleShape else RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp))
            .clickable(onClick = onClick),
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.queue_music),
                error = painterResource(Res.drawable.queue_music),
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.queue_music),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.58f),
                modifier = Modifier.size(size / 3).align(Alignment.Center),
            )
        }
    }
}
