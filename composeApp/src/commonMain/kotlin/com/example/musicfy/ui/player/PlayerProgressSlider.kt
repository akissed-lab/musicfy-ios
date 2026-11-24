package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.makeTimeString
import com.example.musicfy.ui.player.styles.PlayerStyleKit

/**
 * Seek bar and timestamps.
 *
 * The port drops the editor's frame-accurate `PlaybackClock` and animated fill, but keeps the same
 * public hooks and the same guard against the collapsed player following every progress tick.
 */
@Composable
fun PlayerProgressSlider(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onSeekBarPlaced: ((LayoutCoordinates) -> Unit)? = null,
    onEndTimePlaced: ((LayoutCoordinates) -> Unit)? = null,
    seekBarModifier: Modifier = Modifier,
    endTimeModifier: Modifier = Modifier,
) {
    val controller = LocalPlaybackController.current ?: return
    val progress by controller.progressState.collectAsState()

    val rangeEnd = if (progress.duration > 0L) progress.duration.toFloat() else 0f
    val upper = if (rangeEnd > 0f) rangeEnd else 1f
    var sliderValue by remember { mutableFloatStateOf(progress.position.toFloat().coerceIn(0f, upper)) }
    var dragging by remember { mutableStateOf(false) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(active, dragging, progress.position, upper) {
        if (active && !dragging && pendingSeek == null) {
            sliderValue = progress.position.toFloat().coerceIn(0f, upper)
        }
    }

    val barAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.72f,
        label = "progressAlpha",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.alpha(barAlpha),
    ) {
        Text(
            text = makeTimeString(sliderValue.toLong()),
            style = PlayerStyleKit.textStyle(11),
            color = GlassTokens.MutedContent,
        )
        Spacer(Modifier.width(10.dp))
        Slider(
            value = sliderValue.coerceIn(0f, upper),
            valueRange = 0f..upper,
            onValueChange = { value ->
                sliderValue = value
                dragging = true
                pendingSeek = value.toLong()
            },
            onValueChangeFinished = {
                controller.seekTo(pendingSeek ?: sliderValue.toLong())
                dragging = false
                pendingSeek = null
            },
            modifier = seekBarModifier
                .weight(1f)
                .height(24.dp)
                .onGloballyPositioned { coordinates -> onSeekBarPlaced?.invoke(coordinates) },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = makeTimeString(progress.duration.takeIf { it > 0L }),
            style = PlayerStyleKit.textStyle(11),
            color = GlassTokens.MutedContent,
            modifier = endTimeModifier.onGloballyPositioned { coordinates -> onEndTimePlaced?.invoke(coordinates) },
        )
    }
}
