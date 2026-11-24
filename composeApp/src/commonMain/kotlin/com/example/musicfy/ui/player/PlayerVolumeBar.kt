package com.example.musicfy.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.music_note
import com.example.musicfy.resources.offline
import com.example.musicfy.resources.volume
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.player.models.PlayerAction
import com.example.musicfy.ui.utils.stableSystemBars
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The bar's own height, from the volume line to the bottom of its buttons. */
val PlayerVolumeBarHeight: Dp = 76.dp

private val VolumeBarBottomGap = 10.dp
private val VolumeRowInset = 6.dp
private val VolumeActions = listOf(PlayerAction.LYRICS, PlayerAction.QUEUE)

/** Room to leave under the controls for this bar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun playerVolumeBarFootprint(): Dp =
    WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding() +
        VolumeBarBottomGap +
        PlayerVolumeBarHeight

/**
 * Bottom-bar alternative to the card deck: a volume line and the lyrics/queue buttons.
 *
 * The Android original listened to `AudioManager` volume keys through a `BroadcastReceiver`. Here
 * the controller's own [PlaybackController.volume] state is the single source of truth, which also
 * makes the bar work on desktop/iOS.
 */
@Composable
fun PlayerVolumeBar(
    isActive: (PlayerAction) -> Boolean,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    progressProvider: () -> Float = { 1f },
    preview: Boolean = false,
) {
    val controller = LocalPlaybackController.current ?: return
    val visible by remember { derivedStateOf { progressProvider() > 0.98f } }
    if (!visible) return

    val volume by controller.volume.collectAsState()
    val muted by controller.isMuted.collectAsState()
    val shown = if (muted || preview && volume <= 0f) 0f else volume.coerceIn(0f, 1f)

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerVolumeBarHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = VolumeRowInset),
        ) {
            Icon(
                painter = painterResource(if (shown <= 0f) Res.drawable.offline else Res.drawable.music_note),
                contentDescription = stringResource(Res.string.volume),
                tint = if (shown <= 0f) GlassTokens.MutedContent else Color.White,
                modifier = Modifier.height(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Slider(
                value = shown,
                valueRange = 0f..1f,
                onValueChange = { next ->
                    if (muted) controller.setMuted(false)
                    controller.setVolume(next.coerceIn(0f, 1f))
                },
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VolumeRowInset),
        ) {
            for (action in VolumeActions) {
                val on = isActive(action)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f),
                ) {
                    PressScaleActionButton(
                        icon = if (action == PlayerAction.LYRICS) Res.drawable.music_note else Res.drawable.offline,
                        tint = if (on) Color.White else Color.White.copy(alpha = 0.85f),
                        containerColor = if (on) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
                        onClick = { onAction(action) },
                        hasShadow = false,
                    )
                }
            }
        }
    }
}
