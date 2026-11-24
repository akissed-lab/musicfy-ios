package com.example.musicfy.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.model.StreamFailure
import com.example.musicfy.util.currentTimeMillis
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** The red of a failed stream's countdown. */
val StreamFailureRed = Color(0xFFFF4D4F)

/**
 * How far a failed stream is through its timeout, 0..1, or -1 while nothing has failed.
 *
 * The Android original used `SystemClock.elapsedRealtime()`; the port reads the same wall clock the
 * KMP controller uses to stamp [StreamFailure], so the two stay in step on every platform.
 */
@Composable
fun rememberStreamFailureProgress(): State<Float> {
    val controller = LocalPlaybackController.current
        ?: return remember { object : State<Float> { override val value: Float = -1f } }
    val snapshot by controller.playerState.collectAsState()
    val failure = snapshot.streamFailure

    return produceState(
        initialValue = if (failure == null) -1f else 0f,
        failure?.startedAtMs,
        failure?.timeoutMs,
    ) {
        if (failure == null) {
            value = -1f
            return@produceState
        }
        val timeout = failure.timeoutMs.takeIf { it > 0L } ?: 1L
        while (true) {
            val elapsed = currentTimeMillis() - failure.startedAtMs
            value = (elapsed.toFloat() / timeout.toFloat()).coerceIn(0f, 1f)
            if (value >= 1f) break
            delay(16L)
        }
    }
}

/** "Error, timeout in 6s" while a stream is failing, null otherwise. */
@Composable
fun rememberStreamFailureLabel(progress: State<Float>): State<String?> {
    val controller = LocalPlaybackController.current
        ?: return remember { object : State<String?> { override val value: String? = null } }
    val snapshot by controller.playerState.collectAsState()
    val failure = snapshot.streamFailure

    return remember(failure) {
        derivedStateOf {
            val f = failure
            val p = progress.value
            if (f == null || p < 0f) {
                null
            } else {
                val timeout = f.timeoutMs.takeIf { it > 0L } ?: 1L
                val seconds = ceil((1f - p) * timeout / 1000f).toInt().coerceAtLeast(0)
                "Error, timeout in ${seconds}s"
            }
        }
    }
}

/**
 * Paints [progress] of the width in red, growing from the right edge to the left, over whatever the
 * content drew - so every bar style keeps its own shape and only its colour changes.
 */
fun Modifier.streamFailureOverlay(progress: State<Float>): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val p = progress.value
        if (p > 0f) {
            val w = size.width * p
            drawRect(
                color = StreamFailureRed,
                topLeft = Offset(size.width - w, 0f),
                size = Size(w, size.height),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
