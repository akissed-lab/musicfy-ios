package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.model.StreamFacts
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.expand_more
import com.example.musicfy.resources.favorite
import com.example.musicfy.resources.favorite_border
import com.example.musicfy.resources.lib
import com.example.musicfy.resources.lib_fill
import com.example.musicfy.resources.more_horiz
import com.example.musicfy.resources.music_note
import com.example.musicfy.resources.queue_music
import com.example.musicfy.resources.settings
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.player.models.LikeBurstState
import com.example.musicfy.ui.player.models.PlayerAction
import com.example.musicfy.ui.player.models.PlayerLikeSlot
import com.example.musicfy.ui.player.styles.PlayerStyleKit
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The title/artist row above the seek bar.
 *
 * Editor-only layout hooks are kept as optional parameters, but the Android-only artwork palette
 * and animated-vector icons are replaced with the shared glass accent and ordinary drawables.
 *
 * [onSongInfoClick] is the hook for the portable `SongInfoSheet` in `ui.player.songinfo`:
 * pass a state setter from the player and host the sheet above it. The row itself stays
 * a dumb, self-contained title/artist row.
 */
@Composable
fun SongInfoRow(
    modifier: Modifier = Modifier,
    isExpandMenuOpen: Boolean = false,
    hideChevron: Boolean = false,
    onToggleExpandMenu: () -> Unit = {},
    onTitlePositioned: (Rect) -> Unit = {},
    onChevronPositioned: (Rect) -> Unit = {},
    likeBurst: LikeBurstState? = null,
    sideAction: PlayerAction = PlayerAction.LIKE,
    sideActive: Boolean = false,
    onSideAction: (PlayerAction) -> Unit = {},
    onSidePlaced: ((LayoutCoordinates) -> Unit)? = null,
    onChevronPlaced: ((LayoutCoordinates) -> Unit)? = null,
    sideModifier: Modifier = Modifier,
    chevronModifier: Modifier = Modifier,
    onSongInfoClick: (() -> Unit)? = null,
    infoAlpha: () -> Float = { 1f },
    badgeAlpha: () -> Float = { 1f },
    sideLayer: (GraphicsLayerScope.() -> Unit)? = null,
    onSidePositioned: (Rect) -> Unit = {},
) {
    val controller = LocalPlaybackController.current ?: return
    val track by controller.trackInfo.collectAsState()

    val settings = LocalAppContainer.current?.settings
    val showBadge = settings?.getBoolean(SettingsKeys.ShowFormatBadgeKey, true) ?: true
    val showSampleRate = settings?.getBoolean(SettingsKeys.ShowFormatSampleRateKey, false) ?: false

    val factsFlow = remember(controller, track.mediaId) { controller.streamFacts(track.mediaId) }
    val facts: StreamFacts? by factsFlow.collectAsState(initial = null)
    val badgeText = streamBadgeText(facts, showSampleRate)

    val titleRect = { coordinates: LayoutCoordinates ->
        onTitlePositioned(
            Rect(0f, 0f, coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = infoAlpha() }
                .clickable(enabled = onSongInfoClick != null) { onSongInfoClick?.invoke() }
                .onGloballyPositioned(titleRect),
        ) {
            Text(
                text = track.title.ifBlank { "Unknown" },
                style = PlayerStyleKit.textStyle(22),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = track.artist.ifBlank { "Unknown artist" },
                style = PlayerStyleKit.textStyle(13),
                color = GlassTokens.MutedContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showBadge && badgeText != null) {
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.graphicsLayer { alpha = badgeAlpha() }) {
                    StreamFormatBadge(text = badgeText)
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        val sideLayerModifier = sideLayer?.let { Modifier.graphicsLayer(it) } ?: Modifier
        Box(
            contentAlignment = Alignment.Center,
            modifier = sideModifier
                .then(sideLayerModifier)
                .onGloballyPositioned { coordinates ->
                    onSidePlaced?.invoke(coordinates)
                    onSidePositioned(
                        Rect(0f, 0f, coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                    )
                },
        ) {
            PressScaleActionButton(
                icon = actionIcon(sideAction, track.liked, sideActive),
                tint = if (sideActive || sideAction == PlayerAction.LIKE && track.liked) {
                    GlassTokens.Pink
                } else {
                    Color.White
                },
                containerColor = Color.White.copy(alpha = 0.15f),
                onClick = {
                    when (sideAction) {
                        PlayerAction.LIKE -> {
                            controller.toggleLike()
                            if (!track.liked) likeBurst?.burst(PlayerLikeSlot.PLAYER)
                        }
                        PlayerAction.LIBRARY -> controller.toggleLibrary()
                        PlayerAction.REPEAT -> controller.toggleRepeatMode()
                        PlayerAction.SHUFFLE -> controller.setShuffleModeEnabled(!sideActive)
                        PlayerAction.SLEEP_TIMER -> controller.startSleepTimer(10)
                        PlayerAction.SPEED -> controller.setPlaybackSpeed(if (sideActive) 1f else 1.5f)
                        PlayerAction.LYRICS,
                        PlayerAction.QUEUE,
                        PlayerAction.STUDIO,
                        PlayerAction.MORE,
                        -> Unit
                    }
                    onSideAction(sideAction)
                },
            )
        }

        if (!hideChevron) {
            Spacer(Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = chevronModifier
                    .size(34.dp)
                    .graphicsLayer { rotationZ = if (isExpandMenuOpen) 180f else 0f }
                    .onGloballyPositioned { coordinates ->
                        onChevronPlaced?.invoke(coordinates)
                        onChevronPositioned(
                            Rect(0f, 0f, coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                        )
                    },
            ) {
                Icon(
                    painter = painterResource(Res.drawable.expand_more),
                    contentDescription = "Expand player menu",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp),
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { onToggleExpandMenu() },
                )
            }
        }
    }
}

/** Shared with the queue page so its action buttons match the player's exactly. */
@Composable
fun PressScaleActionButton(
    icon: DrawableResource,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boldIcon: Boolean = false,
    badgeText: String? = null,
    hasShadow: Boolean = false,
    size: Dp = 34.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "pressScaleActionButton",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .glassSurface(
                    shape = CircleShape,
                    style = GlassStyle(
                        containerColor = containerColor,
                        borderColor = Color.Transparent,
                        elevation = if (hasShadow) 8.dp else 0.dp,
                    ),
                ),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(if (boldIcon) size * 0.62f else size * 0.52f),
            )
            if (badgeText != null) {
                Text(
                    text = badgeText,
                    color = Color.Black,
                    fontSize = 7.sp,
                    lineHeight = 7.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun actionIcon(action: PlayerAction, liked: Boolean, active: Boolean): DrawableResource =
    when (action) {
        PlayerAction.LIKE -> if (liked) Res.drawable.favorite else Res.drawable.favorite_border
        PlayerAction.LIBRARY -> if (active) Res.drawable.lib_fill else Res.drawable.lib
        PlayerAction.REPEAT,
        PlayerAction.SHUFFLE,
        PlayerAction.SLEEP_TIMER,
        PlayerAction.SPEED,
        -> Res.drawable.settings
        PlayerAction.LYRICS -> Res.drawable.music_note
        PlayerAction.QUEUE -> Res.drawable.queue_music
        PlayerAction.STUDIO -> Res.drawable.settings
        PlayerAction.MORE -> Res.drawable.more_horiz
    }
