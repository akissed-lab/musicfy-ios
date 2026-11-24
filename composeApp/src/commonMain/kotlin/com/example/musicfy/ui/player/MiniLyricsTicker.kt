package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.lyrics
import com.example.musicfy.resources.lyrics_not_found
import com.example.musicfy.ui.player.models.CardMorphState
import com.example.musicfy.ui.player.styles.PlayerStyleKit
import org.jetbrains.compose.resources.stringResource

/**
 * One current lyric line (and, in the tall card, the next one).
 *
 * The upstream ticker swept syllables with a frame clock; the common port follows the controller's
 * progress and lyric offset flows, which is enough for the card that sits above the seek bar.
 */
@Composable
fun MiniLyricsTicker(
    lines: List<LyricsEntry>,
    loaded: Boolean,
    morph: CardMorphState,
    modifier: Modifier = Modifier,
    growthProvider: () -> Float = { 0f },
) {
    val controller = LocalPlaybackController.current ?: return
    val progress by controller.progressState.collectAsState()
    val offsetMs by controller.lyricsOffsetMs.collectAsState()

    val position = progress.position + offsetMs
    val index = remember(lines, position) { LyricsUtils.findCurrentLineIndex(lines, position) }
    val current = lines.getOrNull(index)
    val upcoming = lines.getOrNull(index + 1)
    val growth = growthProvider()

    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0.45f,
        label = "miniLyricsAlpha",
    )

    LaunchedEffect(morph, index >= 0) {
        morph.setProgress(if (index >= 0) 1f else 0f)
    }

    Column(
        modifier = modifier
            .alpha(alpha)
            .graphicsLayer { translationY = -growth * 0.18f },
    ) {
        Text(
            text = when {
                !loaded -> stringResource(Res.string.lyrics)
                current != null -> current.text
                else -> stringResource(Res.string.lyrics_not_found)
            },
            style = PlayerStyleKit.textStyle(18),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (growth > 0.5f && upcoming != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = upcoming.text,
                style = PlayerStyleKit.textStyle(14),
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
