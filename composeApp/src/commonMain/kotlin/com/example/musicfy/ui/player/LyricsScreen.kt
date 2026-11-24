// LyricsScreen.kt
//
// KMP port of the synced lyrics page. The playback clock comes from the
// PlaybackController state flows; word karaoke and the interlude dots read a
// position provider so a tick never recomposes the whole list.

package com.example.musicfy.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.musicfy.LocalPlayerUiState
import com.example.musicfy.constants.LyricsMotionStyle
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.lyrics.LyricsHelper
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.arrow_back
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberEnumPreference
import com.example.musicfy.settings.rememberPreference
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.utils.stableSystemBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ActiveLineViewportFraction = 0.055f
private val MinPastLineVisibleHeight = 72.dp

private class LyricsListPadding : PaddingValues {
    var heightPx = 0
    var density = 1f

    private fun listHeight() = (heightPx / density).dp

    override fun calculateTopPadding() =
        maxOf(listHeight() * ActiveLineViewportFraction, MinPastLineVisibleHeight)

    override fun calculateBottomPadding() = listHeight() * 0.55f

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
}

private const val ImmersiveDelayMs = 3_500L
private const val RecenterDurationMs = 950

internal const val LyricsRecenterDurationMs = RecenterDurationMs
internal val LyricsRecenterEasing: Easing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)
internal val LyricsSmoothRecenterEasing: Easing = CubicBezierEasing(0.42f, 0f, 0.16f, 1f)

internal fun recenterEasingFor(style: LyricsMotionStyle): Easing =
    if (style == LyricsMotionStyle.MOTION) LyricsSmoothRecenterEasing else LyricsRecenterEasing

private const val IdleRecenterDelayMs = 3_500L
private const val TopFadeFraction = 0.10f
private const val BottomFadeStart = 0.62f

private const val LyricsMotionStyleKey = SettingsKeys.LyricsMotionStyleKey
private const val LyricsWaveAnimationKey = SettingsKeys.LyricsWaveAnimationKey
private const val LyricsHighBloomKey = SettingsKeys.LyricsHighBloomKey

/**
 * The synced lyrics page.
 *
 * `morph` is accepted for call sites ported from the Android player, where a
 * card-to-page morph drove extra transforms. The KMP player owns its own
 * transitions, so it is ignored here.
 */
@Composable
fun LyricsScreen(
    onClose: () -> Unit,
    screenHeight: Dp,
    contentBottomInset: Dp,
    onImmersiveChange: (Boolean) -> Unit = {},
    isSheetDragging: Boolean = false,
    isMorphing: Boolean = false,
    enterProgressProvider: () -> Float = { 1f },
    morph: Any? = null,
    onSwitchSource: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val controller = LocalPlaybackController.current ?: return
    val uiState = LocalPlayerUiState.current

    val trackInfo by controller.trackInfo.collectAsState()
    val lyricsEntity by controller.currentLyrics.collectAsState()
    val progress by (uiState?.progressState ?: controller.progressState).collectAsState()
    val lyricsOffsetMs by controller.lyricsOffsetMs.collectAsState()
    val positionProvider = remember(controller, lyricsOffsetMs) {
        { controller.progressState.value.position + controller.lyricsOffsetMs.value }
    }
    val position = progress.position + lyricsOffsetMs

    val parsedLyrics = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) {
            emptyList<LyricsEntry>() to false
        } else {
            val timed = runCatching { LyricsUtils.parseLyrics(raw) }.getOrDefault(emptyList())
            when {
                timed.isEmpty() -> {
                    val plain = runCatching { LyricsUtils.parsePlainLyrics(raw) }.getOrDefault(emptyList())
                    plain to true
                }
                LyricsUtils.isUnsynced(timed) -> timed to true
                else -> timed to false
            }
        }
    }
    val lines = parsedLyrics.first
    val unsynced = parsedLyrics.second

    LyricsReadings(lines, trackInfo.mediaId.takeIf { it.isNotBlank() }, null, lyricsEntity)

    val currentIndex by remember(lines, unsynced) {
        derivedStateOf {
            if (unsynced) -1 else LyricsUtils.findCurrentLineIndex(lines, position)
        }
    }
    val anchorIndex by remember(lines, unsynced) {
        derivedStateOf {
            if (unsynced) return@derivedStateOf -1
            var i = LyricsUtils.findCurrentLineIndex(lines, position)
            if (i <= 0 || i >= lines.size) return@derivedStateOf i
            while (i > 0) {
                val previous = i - 1
                val endMs = lines[previous].words?.lastOrNull()
                    ?.let { (it.endTime * 1000).toLong() }
                    ?: return@derivedStateOf i
                if (endMs <= position) return@derivedStateOf i
                i = previous
            }
            i
        }
    }

    val rows = remember(lines) { buildLyricRows(lines) }
    val rowOfLine = remember(rows, lines) {
        IntArray(lines.size).also { map ->
            rows.forEachIndexed { rowIndex, row ->
                if (row is LyricRow.Line) map[row.index] = rowIndex
            }
        }
    }

    val (motionStyle) = rememberEnumPreference(LyricsMotionStyleKey, LyricsMotionStyle.MOTION)
    val cascade = motionStyle == LyricsMotionStyle.MOTION
    val (lyricsWaveAnimation) = rememberPreference(LyricsWaveAnimationKey, defaultValue = true)
    val (lyricsHighBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)

    val initialRow = remember(lines, rowOfLine) {
        rowOfLine.getOrNull(anchorIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))) ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)
    var followPlayback by remember { mutableStateOf(true) }
    var isAutoScrolling by remember { mutableStateOf(false) }

    val userScrolling by remember {
        derivedStateOf { listState.isScrollInProgress && !isAutoScrolling }
    }
    val suppressEffects = userScrolling || isSheetDragging || isMorphing
    val sheetDragging by rememberUpdatedState(isSheetDragging)

    LaunchedEffect(userScrolling) {
        if (userScrolling) followPlayback = false
    }
    LaunchedEffect(userScrolling, followPlayback) {
        if (!followPlayback && !userScrolling) {
            delay(IdleRecenterDelayMs)
            followPlayback = true
        }
    }

    var immersive by remember { mutableStateOf(false) }
    LaunchedEffect(userScrolling, immersive) {
        if (!userScrolling && !immersive) {
            delay(ImmersiveDelayMs)
            immersive = true
        }
    }
    LaunchedEffect(listState) {
        var previous = -1L
        snapshotFlow {
            listState.firstVisibleItemIndex.toLong() * 1_000_000L +
                listState.firstVisibleItemScrollOffset
        }.collect { now ->
            val last = previous
            previous = now
            if (last < 0L || isAutoScrolling) return@collect
            val delta = now - last
            if (delta > 6L) immersive = true else if (delta < -6L) immersive = false
        }
    }
    DisposableEffect(Unit) { onDispose { onImmersiveChange(false) } }
    LaunchedEffect(immersive) { onImmersiveChange(immersive) }

    val immersion by animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "lyricsImmersion",
    )

    LaunchedEffect(followPlayback, lines, motionStyle, rowOfLine) {
        if (!followPlayback) return@LaunchedEffect
        snapshotFlow { anchorIndex }.collectLatest { target ->
            if (target !in lines.indices) return@collectLatest
            val targetRow = rowOfLine.getOrNull(target) ?: return@collectLatest
            isAutoScrolling = true
            try {
                if (sheetDragging) {
                    listState.scrollToItem(targetRow, 0)
                } else if (listState.layoutInfo.visibleItemsInfo.any { it.index == targetRow }) {
                    listState.animateScrollToItem(targetRow, 0)
                } else {
                    listState.scrollToItem(targetRow, 0)
                }
            } finally {
                isAutoScrolling = false
            }
        }
    }

    val activeInterlude by remember(lines, rows) {
        derivedStateOf {
            rows.firstOrNull {
                it is LyricRow.Interlude && position >= it.startMs &&
                    position + LyricsUtils.LineLeadInMs < it.endMs
            }?.let { (it as LyricRow.Interlude).afterIndex } ?: NoInterlude
        }
    }

    val useAgentAlignment = remember(lines) {
        lines.filter { !it.isBackground }
            .map { it.agent ?: "v1" }
            .distinct()
            .size > 1
    }

    val translationStatus by LyricsTranslationStatusHolder.status.collectAsState()

    val stagger = remember(rows) { LyricsStagger(rows.size + 1) }
    LaunchedEffect(stagger) { stagger.run() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.stableSystemBars)
                .padding(horizontal = 20.dp)
                .glassSurface(
                    shape = RoundedCornerShape(18.dp),
                    style = GlassStyle(
                        containerColor = Color.White.copy(alpha = 0.06f),
                        borderColor = Color.White.copy(alpha = 0.10f),
                        elevation = 0.dp,
                    ),
                )
                .clickable {
                    if (immersive) immersive = false else onClose()
                }
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.arrow_back),
                contentDescription = "Close lyrics",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackInfo.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = trackInfo.artist,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val listPadding = remember { LyricsListPadding() }
        Box(
            modifier = Modifier
                .weight(1f)
                .layout { measurable, constraints ->
                    val inset = (contentBottomInset * (1f - immersion)).coerceAtLeast(0.dp).roundToPx()
                    val minHeight = if (constraints.hasBoundedHeight) {
                        (constraints.minHeight - inset).coerceAtLeast(0)
                    } else {
                        constraints.minHeight
                    }
                    val inner = constraints.copy(
                        minHeight = minHeight,
                        maxHeight = if (constraints.hasBoundedHeight) {
                            (constraints.maxHeight - inset).coerceAtLeast(minHeight)
                        } else {
                            constraints.maxHeight
                        },
                    )
                    listPadding.heightPx = inner.maxHeight
                    listPadding.density = this.density
                    val placeable = measurable.measure(inner)
                    layout(
                        placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth),
                        (placeable.height + inset).coerceIn(constraints.minHeight, constraints.maxHeight),
                    ) { placeable.place(0, 0) }
                },
        ) {
            if (lines.isEmpty()) {
                if (lyricsEntity == null) {
                    LyricsLoadingDots(modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(
                        text = "No lyrics found",
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = listPadding,
                    userScrollEnabled = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithCache {
                            val fade = Brush.verticalGradient(
                                0f to Color.Transparent,
                                TopFadeFraction to Color.Black,
                                BottomFadeStart to Color.Black,
                                1f to Color.Transparent,
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush = fade, blendMode = BlendMode.DstIn)
                            }
                        },
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.key },
                        contentType = { _, row -> if (row is LyricRow.Interlude) "interlude" else "lyric" },
                    ) { listIndex, row ->
                        when (row) {
                            is LyricRow.Interlude -> LyricsInterludeDots(
                                modifier = Modifier.graphicsLayer {
                                    translationY = stagger.lagOf(listIndex)
                                    applyPageRowEnter(enterProgressProvider(), listIndex)
                                },
                                startMs = row.startMs,
                                endMs = row.endMs,
                                positionProvider = positionProvider,
                                accentColor = Color(0xFFFA4C7E),
                                visible = activeInterlude == row.afterIndex,
                                collapseEasing = recenterEasingFor(motionStyle),
                                alignment = lines.getOrNull(row.afterIndex + 1)
                                    ?.let { alignmentFor(it, useAgentAlignment) }
                                    ?: LyricsAlignment.CENTER,
                            )

                            is LyricRow.Line -> {
                                val index = row.index
                                val entry = row.entry
                                val look by remember(index, lines, unsynced) {
                                    derivedStateOf {
                                        lineLook(index, currentIndex, anchorIndex, unsynced, activeInterlude != NoInterlude)
                                    }
                                }
                                val state = look.state
                                val blurStage = if (suppressEffects) 0 else look.blurStage
                                val romanized by entry.romanizedTextFlow.collectAsState()
                                val ruby by entry.rubyFlow.collectAsState()
                                val translated by entry.translatedTextFlow.collectAsState()
                                val rowIndex = rowOfLine.getOrElse(index) { listIndex }
                                val lineAlignment = alignmentFor(entry, useAgentAlignment)
                                val isTranslating = translationStatus is
                                    com.example.musicfy.lyrics.LyricsTranslationHelper.TranslationStatus.Translating

                                LyricsGlowLine(
                                    modifier = Modifier.graphicsLayer {
                                        translationY = stagger.lagOf(rowIndex)
                                        applyPageRowEnter(enterProgressProvider(), rowIndex)
                                    },
                                    entry = entry,
                                    state = state,
                                    blurStage = blurStage,
                                    positionProvider = positionProvider,
                                    accentColor = Color(0xFF7B5CFF),
                                    onClick = {
                                        if (!unsynced) {
                                            controller.seekTo(entry.time)
                                            followPlayback = true
                                        }
                                    },
                                    subLine = romanized,
                                    waveEnabled = lyricsWaveAnimation && !suppressEffects,
                                    highBloom = lyricsHighBloom,
                                    suppressEffects = suppressEffects,
                                    alignment = lineAlignment,
                                    ruby = ruby,
                                    rubyPlacement = if (isTranslating || !translated.isNullOrBlank()) {
                                        RubyPlacement.ABOVE
                                    } else {
                                        RubyPlacement.BELOW
                                    },
                                    translation = translated,
                                    translationLoading = isTranslating && translated.isNullOrBlank(),
                                    motionStyle = motionStyle,
                                )
                            }
                        }
                    }

                    val provider = lyricsEntity?.provider
                    if (provider != null && provider != "Unknown" &&
                        provider != LyricsHelper.NoConnectionProvider
                    ) {
                        item(key = "source", contentType = "source") {
                            LyricsSourceFooter(
                                provider = provider,
                                onSwitchSource = onSwitchSource,
                                modifier = Modifier
                                    .graphicsLayer {
                                        translationY = stagger.lagOf(rows.size)
                                        applyPageRowEnter(enterProgressProvider(), rows.size)
                                    }
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 36.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Page-entry offset for a row: rows below the card's line rise into place. */
internal fun GraphicsLayerScope.applyPageRowEnter(progress: Float, rowsFromCard: Int) {
    val e = progress.coerceIn(0f, 1f)
    val delay = rowsFromCard.coerceIn(0, 12) * 0.035f
    val shown = ((e - delay) / 0.55f).coerceIn(0f, 1f)
    alpha *= shown
    translationY += (1f - shown) * 14f
}

private const val NoInterlude = Int.MIN_VALUE

private data class LineLook(val state: LyricsLineState, val blurStage: Int)

private fun lineLook(
    index: Int,
    current: Int,
    anchor: Int,
    unsynced: Boolean,
    interlude: Boolean,
): LineLook {
    if (unsynced) return LineLook(LyricsLineState.ACTIVE, 0)
    val distance = when {
        current < 0 -> index - current
        index < anchor -> index - anchor
        index > current -> index - current
        else -> 0
    }
    val state = when {
        current >= 0 && index in anchor..current ->
            if (interlude) LyricsLineState.PAST else LyricsLineState.ACTIVE
        index == current + 1 -> LyricsLineState.UPCOMING
        index < current -> LyricsLineState.PAST
        else -> LyricsLineState.DEFAULT
    }
    val blurStage = when (abs(distance)) {
        0 -> 0
        1 -> 1
        else -> 2
    }
    return LineLook(state, blurStage)
}

private sealed interface LyricRow {
    val key: Long

    data class Line(val index: Int, val entry: LyricsEntry) : LyricRow {
        override val key get() = index.toLong()
    }

    data class Interlude(val afterIndex: Int, val startMs: Long, val endMs: Long) : LyricRow {
        override val key get() = -1_000_000L - afterIndex
    }
}

private fun buildLyricRows(lines: List<LyricsEntry>): List<LyricRow> {
    if (lines.isEmpty()) return emptyList()
    val rows = ArrayList<LyricRow>(lines.size + 4)
    if (lines.first().time >= MinInterludeGapMs) {
        rows.add(LyricRow.Interlude(afterIndex = -1, startMs = 0L, endMs = lines.first().time))
    }
    for (index in lines.indices) {
        rows.add(LyricRow.Line(index, lines[index]))
        val next = lines.getOrNull(index + 1) ?: continue
        val gapStart = LyricsUtils.lineEndMs(lines, index)
        if (next.time - gapStart >= MinInterludeGapMs) {
            rows.add(LyricRow.Interlude(afterIndex = index, startMs = gapStart, endMs = next.time))
        }
    }
    return rows
}

private fun alignmentFor(entry: LyricsEntry, useAgentAlignment: Boolean): LyricsAlignment = when {
    !useAgentAlignment -> LyricsAlignment.START
    entry.isBackground -> LyricsAlignment.CENTER
    entry.agent == "v2" -> LyricsAlignment.END
    entry.agent == "v1000" -> LyricsAlignment.CENTER
    else -> LyricsAlignment.START
}

@Composable
internal fun LyricsLoadingDots(
    modifier: Modifier = Modifier,
    alignStart: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "lyricsLoading")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (alignStart) Arrangement.spacedBy(5.dp) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 900,
                        delayMillis = index * 150,
                        easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "lyricsLoadingDot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        alpha = 0.3f + 0.7f * phase
                        val s = 0.75f + 0.25f * phase
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** Tiny read-through so the screen can observe translation progress. */
private object LyricsTranslationStatusHolder {
    val status = com.example.musicfy.lyrics.LyricsTranslationHelper.status
}
