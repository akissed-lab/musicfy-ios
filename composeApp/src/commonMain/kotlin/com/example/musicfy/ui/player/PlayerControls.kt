package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.PlaybackConstants
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.next
import com.example.musicfy.resources.now_playing
import com.example.musicfy.resources.pause
import com.example.musicfy.resources.play
import com.example.musicfy.resources.previous
import com.example.musicfy.resources.repeat
import com.example.musicfy.resources.shuffle
import com.example.musicfy.resources.skip_next
import com.example.musicfy.resources.skip_previous
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.player.styles.PlayerStyleKit
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Song info, progress and the transport row in the player card. */
@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    onTitlePositioned: (Rect) -> Unit = {},
    onSongInfoClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SongInfoRow(
            onTitlePositioned = onTitlePositioned,
            onSongInfoClick = onSongInfoClick,
        )
        Spacer(Modifier.height(12.dp))
        PlayerProgressSlider()
        Spacer(Modifier.height(20.dp))
        PlayerTransportRow()
    }
}

/** Shuffle / previous / play-pause / next / repeat. */
@Composable
fun PlayerTransportRow(modifier: Modifier = Modifier) {
    val controller = LocalPlaybackController.current ?: return
    val transport by controller.transportState.collectAsState()
    val accent = LocalGlassState.current.accent

    val playScale by animateFloatAsState(
        targetValue = if (transport.isPlaying) 1.06f else 1f,
        label = "playButtonScale",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = { controller.setShuffleModeEnabled(!transport.shuffleModeEnabled) },
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = painterResource(Res.drawable.shuffle),
                contentDescription = stringResource(Res.string.shuffle),
                tint = if (transport.shuffleModeEnabled) accent else GlassTokens.MutedContent,
                modifier = Modifier.size(22.dp),
            )
        }

        IconButton(
            onClick = { controller.seekToPrevious() },
            enabled = transport.canSkipPrevious,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = painterResource(Res.drawable.skip_previous),
                contentDescription = stringResource(Res.string.previous),
                tint = transportTint(transport.canSkipPrevious),
                modifier = Modifier.size(30.dp),
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1.4f)
                .graphicsLayer { scaleX = playScale; scaleY = playScale },
        ) {
            IconButton(
                onClick = { controller.togglePlayPause() },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    painter = painterResource(if (transport.isPlaying) Res.drawable.pause else Res.drawable.play),
                    contentDescription = stringResource(Res.string.now_playing),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        IconButton(
            onClick = { controller.seekToNext() },
            enabled = transport.canSkipNext,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = painterResource(Res.drawable.skip_next),
                contentDescription = stringResource(Res.string.next),
                tint = transportTint(transport.canSkipNext),
                modifier = Modifier.size(30.dp),
            )
        }

        IconButton(
            onClick = { controller.toggleRepeatMode() },
            modifier = Modifier.weight(1f),
        ) {
            val repeatLabel = stringResource(Res.string.repeat) +
                if (transport.repeatMode == PlaybackConstants.REPEAT_MODE_ONE) "1" else ""
            Text(
                text = repeatLabel,
                style = PlayerStyleKit.textStyle(10, FontWeight.Bold),
                color = if (transport.repeatMode == PlaybackConstants.REPEAT_MODE_OFF) {
                    GlassTokens.MutedContent
                } else {
                    accent
                },
            )
        }
    }
}

/** Small helper so disabled skips keep a readable glass colour. */
private fun transportTint(enabled: Boolean): Color =
    if (enabled) Color.White else GlassTokens.MutedContent
