package com.example.musicfy.ui.component.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.check
import com.example.musicfy.resources.close
import com.example.musicfy.resources.done
import com.example.musicfy.resources.more_horiz
import com.example.musicfy.resources.queue_music
import com.example.musicfy.resources.select
import com.example.musicfy.ui.component.GlassPillBackground
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.glassSurface
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Common port of the playlist selection/edit-order bar. Missing Android-only
 * helpers (`pressBounce`, `resize`) are replaced with minimal common stubs.
 */

sealed interface PlaylistModeBar {
    class Select(
        val count: Int,
        val allSelected: Boolean,
        val onToggleAll: () -> Unit,
        val onMore: () -> Unit,
        val onClose: () -> Unit,
    ) : PlaylistModeBar

    class EditOrder(
        val onMore: (() -> Unit)?,
        val onDone: () -> Unit,
    ) : PlaylistModeBar
}

private val ModeButtonFill = Color.White.copy(alpha = 0.24f)
private val ModePillShape = RoundedCornerShape(17.dp)
private val ModeTrackShape = RoundedCornerShape(9.dp)

/** Minimal replacement for upstream `pressBounce`. */
fun Modifier.pressBounce(enabled: Boolean = true): Modifier = this

/** Minimal replacement for upstream `String.resize`. */
fun String.resize(width: Int, height: Int): String = this

@Composable
internal fun PlaylistModeBarRow(
    mode: PlaylistModeBar,
    glassState: GlassState,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    tint: State<Color>,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        when (mode) {
            is PlaylistModeBar.Select -> {
                Text(
                    text = stringResource(Res.string.select),
                    color = Color.White,
                    modifier = Modifier.padding(start = 10.dp),
                )
                Spacer(Modifier.weight(1f))
                SelectAllPill(allSelected = mode.allSelected, onClick = mode.onToggleAll)
                Spacer(Modifier.width(8.dp))
                ModeCircleButton(
                    icon = Res.drawable.more_horiz,
                    contentDescription = "Actions for the picked songs",
                    enabled = mode.count > 0,
                    onClick = mode.onMore,
                )
                Spacer(Modifier.width(8.dp))
                ModeCircleButton(
                    icon = Res.drawable.close,
                    contentDescription = "Stop selecting",
                    onClick = mode.onClose,
                )
            }

            is PlaylistModeBar.EditOrder -> {
                if (mode.onMore != null) {
                    ModeCircleButton(
                        icon = Res.drawable.more_horiz,
                        contentDescription = "More actions",
                        onClick = mode.onMore!!,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                EditOrderPill(
                    glassState = glassState,
                    coverUrl = coverUrl,
                    cover = cover,
                    tint = tint,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ModeCircleButton(
                    icon = Res.drawable.done,
                    contentDescription = "Done",
                    onClick = mode.onDone,
                )
            }
        }
    }
}

@Composable
private fun SelectAllPill(allSelected: Boolean, onClick: () -> Unit) {
    val accent = LocalGlassState.current.accent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(30.dp)
            .glassSurface(
                shape = ModePillShape,
                style = GlassStyle(
                    containerColor = accent.copy(alpha = if (allSelected) 0.34f else 0.16f),
                    cornerRadius = 17.dp,
                    elevation = 0.dp,
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Text(text = stringResource(Res.string.select), color = Color.White)
    }
}

@Composable
private fun ModeCircleButton(
    icon: org.jetbrains.compose.resources.DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "modeButtonAlpha")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce()
            .graphicsLayer { this.alpha = alpha }
            .size(30.dp)
            .clip(CircleShape)
            .glassSurface(
                shape = CircleShape,
                style = GlassStyle(containerColor = ModeButtonFill, elevation = 0.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EditOrderPill(
    glassState: GlassState,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    tint: State<Color>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(47.dp)) {
        GlassPillBackground(
            state = glassState,
            blurRadius = { 18f },
            tint = tint.value.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 5.dp, end = 14.dp),
        ) {
            val miniModifier = Modifier.size(37.dp).clip(ModeTrackShape).glassSurface(
                shape = ModeTrackShape,
                style = GlassStyle(elevation = 0.dp),
            )
            when {
                cover != null -> cover(miniModifier)
                !coverUrl.isNullOrEmpty() -> AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(Res.drawable.queue_music),
                    error = painterResource(Res.drawable.queue_music),
                    modifier = miniModifier,
                )
            }
            Text(
                text = "Edit order",
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.padding(start = 9.dp),
            )
        }
    }
}

@Composable
fun PlaylistSelectTick(checked: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalGlassState.current.accent
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .glassSurface(
                shape = CircleShape,
                style = GlassStyle(
                    containerColor = if (checked) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                    cornerRadius = 22.dp,
                    elevation = 0.dp,
                ),
            ),
    ) {
        if (checked) {
            Icon(
                painter = painterResource(Res.drawable.check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

internal fun Modifier.selectedRowBand(progress: () -> Float): Modifier = drawBehind {
    val p = progress().coerceIn(0f, 1f)
    if (p > 0f) {
        drawRect(color = GlassTokens.Purple.copy(alpha = 0.18f * p))
    }
}
