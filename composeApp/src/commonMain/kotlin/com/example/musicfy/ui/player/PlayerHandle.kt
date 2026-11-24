package com.example.musicfy.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicfy.ui.player.models.PlayerSheetState
import com.example.musicfy.ui.player.styles.PlayerStyleKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HandleActiveAlpha = 0.60f
private const val HandleIdleAlpha = 0.15f
private const val HandleIdleDelayMs = 2_400L

/** The open player's top handle: bright while touched, faint once left alone. */
@Stable
class PlayerHandleState internal constructor(private val scope: CoroutineScope) {
    internal val alpha = Animatable(HandleActiveAlpha)
    /** Snapshot mirror of [alpha] for composition; this Compose version has no Animatable.asState(). */
    internal var alphaValue by mutableFloatStateOf(HandleActiveAlpha)
        private set
    private var idleJob: Job? = null

    /** The player was touched (true), or the last finger left it (false). */
    fun onTouchActivity(touching: Boolean) {
        if (touching) {
            idleJob?.cancel()
            idleJob = null
            scope.launch { alpha.animateTo(HandleActiveAlpha, tween(140)) { alphaValue = value } }
        } else {
            restartIdleWait()
        }
    }

    /** Bright with a fresh idle wait: the player has just opened. */
    internal fun wake() {
        scope.launch { alpha.animateTo(HandleActiveAlpha, tween(180)) { alphaValue = value } }
        restartIdleWait()
    }

    private fun restartIdleWait() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(HandleIdleDelayMs)
            alpha.animateTo(HandleIdleAlpha, tween(420)) { alphaValue = value }
        }
    }
}

@Composable
fun rememberPlayerHandleState(): PlayerHandleState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerHandleState(scope) }
}

/**
 * A bar that says the player is a sheet to pull down. A tap closes the page or the sheet; pressed
 * (and while the sheet is closing) it bends into a chevron pointing down.
 */
@Composable
fun PlayerHandle(
    state: PlayerHandleState,
    sheet: PlayerSheetState,
    statusBarTop: Dp = 0.dp,
    enabled: () -> Boolean = { true },
    modifier: Modifier = Modifier,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val alpha = state.alphaValue
    var pressed by remember { mutableStateOf(false) }
    val bend by animateFloatAsState(
        targetValue = if (pressed || !sheet.isExpandedOrExpanding) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "handleBend",
    )

    LaunchedEffect(sheet) {
        if (sheet.expanded) state.wake()
    }
    DisposableEffect(state) {
        onDispose { state.onTouchActivity(false) }
    }

    val color = Color.White.copy(alpha = alpha * if (currentEnabled()) 1f else 0.35f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(top = statusBarTop)
            .size(width = PlayerStyleKit.HandleWidth, height = 20.dp)
            .clickable(enabled = currentEnabled()) {
                pressed = false
                if (sheet.page != com.example.musicfy.ui.player.models.PlayerPage.PLAYER) {
                    sheet.closePage()
                } else {
                    sheet.toggle()
                }
            },
    ) {
        Canvas(modifier = Modifier.size(width = PlayerStyleKit.HandleWidth, height = 12.dp)) {
            val stroke = 3.dp.toPx()
            val midY = size.height / 2f
            val midX = size.width / 2f
            drawLine(
                color = color,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            if (bend > 0f) {
                val tipY = midY + bend * size.height * 0.28f
                val bent = color.copy(alpha = color.alpha * bend)
                drawLine(bent, Offset(0f, midY), Offset(midX, tipY), stroke, StrokeCap.Round)
                drawLine(bent, Offset(size.width, midY), Offset(midX, tipY), stroke, StrokeCap.Round)
            }
        }
    }
}
