// LyricsGlowLine.kt
//
// KMP port. The Android original used AGSL/RenderEffect for the karaoke bloom;
// none of that exists outside Android. This keeps the public surface and the
// look (a bright swept head, a soft accent halo, glass-friendly translucent
// readings) with plain Compose drawing primitives.

package com.example.musicfy.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.constants.LyricsMotionStyle
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.RubyToken
import com.example.musicfy.lyrics.WordTimestamp
import kotlin.math.abs
import kotlin.math.roundToInt

enum class LyricsLineState { ACTIVE, UPCOMING, PAST, DEFAULT }

enum class LyricsAlignment { START, CENTER, END }

enum class RubyPlacement { ABOVE, BELOW }

/** Left edge of the artwork, shared with the screen's header. */
internal val LyricsHeaderArtX = 20.dp

private val LyricsLineHorizontalPadding = 44.dp
private val LyricsTapPadH = 8.dp
private val LyricsTapPadV = 4.dp
private val LyricsTapShape = RoundedCornerShape(14.dp)
private val LyricsWaitingDrop = 3.dp

private val LyricsFontSize = 32.sp
private val LyricsLineHeight = 40.sp
private val BlurStageRadii = floatArrayOf(0f, 5f, 12f)

/** Replaces the Android RenderEffect cache with a plain strength multiplier. */
internal fun blurEffectForRadius(radius: Float): Float =
    (radius / (BlurStageRadii.maxOrNull() ?: 12f)).coerceIn(0f, 1f)

private const val UpcomingAlpha = 0.5f
private const val PastAlpha = 0.24f
private const val DefaultAlpha = 0.38f

internal fun restingAlphaOf(state: LyricsLineState): Float = when (state) {
    LyricsLineState.ACTIVE -> 1f
    LyricsLineState.UPCOMING -> UpcomingAlpha
    LyricsLineState.PAST -> PastAlpha
    LyricsLineState.DEFAULT -> DefaultAlpha
}

internal fun restingScaleOf(state: LyricsLineState): Float = when (state) {
    LyricsLineState.ACTIVE -> 1.06f
    LyricsLineState.UPCOMING -> 0.98f
    LyricsLineState.PAST -> 0.93f
    LyricsLineState.DEFAULT -> 0.95f
}

internal fun restingLineStyle(
    base: TextStyle,
    active: Boolean,
    letterMotion: Boolean,
): TextStyle = base.copy(
    fontSize = LyricsFontSize,
    lineHeight = LyricsLineHeight,
    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
)

internal class LyricsLineInsets(
    val start: Dp,
    val end: Dp,
    val top: Dp,
    val padH: Dp,
    val padV: Dp,
)

internal fun lyricsLineInsets(alignment: LyricsAlignment): LyricsLineInsets {
    val anchored = LyricsHeaderArtX - LyricsTapPadH
    val zoom = LyricsLineHorizontalPadding - LyricsTapPadH
    val (start, end) = when (alignment) {
        LyricsAlignment.START -> anchored to zoom
        LyricsAlignment.CENTER -> zoom to zoom
        LyricsAlignment.END -> zoom to anchored
    }
    return LyricsLineInsets(start, end, top = 16.dp - LyricsTapPadV, padH = LyricsTapPadH, padV = LyricsTapPadV)
}

private const val ActiveUnsungAlpha = 0.58f
private const val SubTextFadeMs = 350
private val RubyFontSize = 11.sp
private val RubyLineHeight = 13.sp
private const val RubyHeadroomSp = 14f
private const val RubyBaselineGapPx = 2f

/**
 * One lyric row. Word timing is simplified to a karaoke fill; the bloom is a
 * radial accent wash instead of a shader, so it works on every Compose target.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsGlowLine(
    entry: LyricsEntry,
    state: LyricsLineState,
    blurStage: Int,
    positionProvider: () -> Long,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subLine: String? = null,
    waveEnabled: Boolean = true,
    highBloom: Boolean = true,
    suppressEffects: Boolean = false,
    alignment: LyricsAlignment = LyricsAlignment.START,
    ruby: List<RubyToken>? = null,
    rubyPlacement: RubyPlacement = RubyPlacement.BELOW,
    translation: String? = null,
    translationLoading: Boolean = false,
    motionStyle: LyricsMotionStyle = LyricsMotionStyle.MOTION,
    onLongClick: (() -> Unit)? = null,
) {
    val wordMotion = motionStyle == LyricsMotionStyle.MOTION
    val targetAlpha = restingAlphaOf(state)
    val targetScale = restingScaleOf(state)
    val animSpec = tween<Float>(durationMillis = 600)

    val alpha by animateFloatAsState(targetAlpha, animSpec, label = "lyricsLineAlpha")
    val scale by animateFloatAsState(targetScale, animSpec, label = "lyricsLineScale")
    val drop by animateFloatAsState(
        targetValue = if (state == LyricsLineState.ACTIVE || entry.words.isNullOrEmpty()) 0f else 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessVeryLow,
        ),
        label = "lyricsLineDrop",
    )
    val blurStrength by animateFloatAsState(
        targetValue = if (suppressEffects) 0f else blurEffectForRadius(
            BlurStageRadii[blurStage.coerceIn(0, BlurStageRadii.lastIndex)],
        ),
        animationSpec = tween(350),
        label = "lyricsLineBlur",
    )

    val lineColor by animateColorAsState(
        targetValue = if (state == LyricsLineState.ACTIVE) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(600),
        label = "lyricsLineColor",
    )
    val textColor = remember(lineColor) { ColorProducer { lineColor } }
    val unsungColor = remember(lineColor) { ColorProducer { lineColor.copy(alpha = ActiveUnsungAlpha) } }
    val readingColor = remember(lineColor) { ColorProducer { lineColor.copy(alpha = 0.45f) } }
    val subLineColor = remember(lineColor) { ColorProducer { lineColor.copy(alpha = 0.6f) } }
    val translationColor = remember(lineColor) { ColorProducer { lineColor.copy(alpha = 0.55f) } }

    val textAlign = when (alignment) {
        LyricsAlignment.START -> TextAlign.Start
        LyricsAlignment.CENTER -> TextAlign.Center
        LyricsAlignment.END -> TextAlign.End
    }
    val columnAlign = when (alignment) {
        LyricsAlignment.START -> Alignment.Start
        LyricsAlignment.CENTER -> Alignment.CenterHorizontally
        LyricsAlignment.END -> Alignment.End
    }
    val insets = lyricsLineInsets(alignment)
    val parts = remember(entry.text, entry.words, entry.syllables) {
        splitBackingVocal(entry.text, entry.syllables ?: entry.words)
    }
    val leadText = parts.lead
    val words = parts.leadWords
    val hasRuby = !ruby.isNullOrEmpty()
    val leadStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = LyricsFontSize,
        lineHeight = if (hasRuby) (LyricsLineHeight.value + RubyHeadroomSp).sp else LyricsLineHeight,
        lineHeightStyle = if (hasRuby) rubyLineHeightStyle(rubyPlacement) else null,
        textAlign = textAlign,
    )
    val waveAmplitude by animateFloatAsState(
        targetValue = if (state == LyricsLineState.ACTIVE) 1f else 0f,
        animationSpec = animSpec,
        label = "lyricsWaveAmplitude",
    )
    val karaokeActive = !words.isNullOrEmpty() && leadText.isNotEmpty() &&
        (state == LyricsLineState.ACTIVE || waveAmplitude > 0.01f)

    Column(
        horizontalAlignment = columnAlign,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = insets.start,
                end = insets.end,
                top = insets.top,
                bottom = insets.top,
            )
            .drawBehind {
                if (state == LyricsLineState.ACTIVE && !suppressEffects) {
                    val wash = if (highBloom) 0.26f else 0.12f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = wash * waveAmplitude),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.35f, size.height / 2f),
                            radius = size.width.coerceAtLeast(1f),
                        ),
                        center = Offset(size.width * 0.35f, size.height / 2f),
                        radius = size.width.coerceAtLeast(1f),
                    )
                }
            }
            .graphicsLayer {
                this.alpha = alpha * (1f - blurStrength * 0.55f)
                scaleX = scale
                scaleY = scale
                translationY = drop * LyricsWaitingDrop.toPx()
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    pivotFractionX = when (alignment) {
                        LyricsAlignment.START -> 0f
                        LyricsAlignment.CENTER -> 0.5f
                        LyricsAlignment.END -> 1f
                    },
                    pivotFractionY = 0.5f,
                )
            },
    ) {
        // The highlight sits behind the text and hugs the words, not the row.
        Box {
            Spacer(
                modifier = Modifier
                    .matchParentSize()
                    .clip(LyricsTapShape)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            )
            Column(horizontalAlignment = columnAlign) {
                if (karaokeActive) {
                    KaraokeSweepText(
                        text = leadText,
                        words = words,
                        positionProvider = positionProvider,
                        waveAmplitudeProvider = { if (waveEnabled) waveAmplitude else 0f },
                        riseAmplitudeProvider = { waveAmplitude },
                        baseColor = unsungColor,
                        highlightColor = Color.White,
                        highBloom = highBloom,
                        textAlign = textAlign,
                        ruby = ruby,
                        rubyPlacement = rubyPlacement,
                        rubyColor = readingColor,
                        motionStyle = motionStyle,
                    )
                } else if (leadText.isNotEmpty()) {
                    val plainLayout = remember(leadText) { arrayOfNulls<TextLayoutResult>(1) }
                    BasicText(
                        text = leadText,
                        style = leadStyle.copy(
                            fontWeight = if (state == LyricsLineState.ACTIVE) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        color = textColor,
                        overflow = TextOverflow.Clip,
                        onTextLayout = { plainLayout[0] = it },
                        modifier = rememberRubyOverlay(
                            layoutHolder = plainLayout,
                            ruby = ruby,
                            textLength = leadText.length,
                            placement = rubyPlacement,
                            dimColor = readingColor,
                            litColor = readingColor,
                            headProvider = { 0f },
                        ),
                    )
                }

                FadingSubText(text = subLine, color = subLineColor, textAlign = textAlign)

                parts.backing?.let { backing ->
                    BasicText(
                        text = backing,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 21.sp,
                            lineHeight = 26.sp,
                            textAlign = textAlign,
                        ),
                        color = textColor,
                        overflow = TextOverflow.Clip,
                    )
                }

                if (translationLoading && translation.isNullOrBlank()) {
                    TranslationLoadingDots(modifier = Modifier.padding(top = 7.dp))
                } else {
                    FadingSubText(text = translation, color = translationColor, textAlign = textAlign)
                }
            }
        }
    }
}

@Composable
private fun FadingSubText(text: String?, color: ColorProducer, textAlign: TextAlign) {
    val shown = remember { arrayOfNulls<String>(1) }
    if (!text.isNullOrBlank()) shown[0] = text
    androidx.compose.animation.AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = androidx.compose.animation.fadeIn(tween(SubTextFadeMs)) +
            androidx.compose.animation.expandVertically(tween(SubTextFadeMs)),
        exit = androidx.compose.animation.fadeOut(tween(SubTextFadeMs)) +
            androidx.compose.animation.shrinkVertically(tween(SubTextFadeMs)),
    ) {
        BasicText(
            text = shown[0].orEmpty(),
            style = MaterialTheme.typography.bodyLarge.copy(textAlign = textAlign),
            color = color,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun TranslationLoadingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "translating")
    Row(modifier = modifier) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 620,
                        delayMillis = index * 140,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "translatingDot$index",
            )
            Box(
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(5.dp)
                    .graphicsLayer { alpha = 0.25f + 0.75f * phase }
                    .drawBehind { drawCircle(Color.White) },
            )
        }
    }
}

private fun rubyLineHeightStyle(placement: RubyPlacement) =
    androidx.compose.ui.text.style.LineHeightStyle(
        alignment = when (placement) {
            RubyPlacement.ABOVE -> androidx.compose.ui.text.style.LineHeightStyle.Alignment.Bottom
            RubyPlacement.BELOW -> androidx.compose.ui.text.style.LineHeightStyle.Alignment.Top
        },
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
    )

/** Draws per-word readings against the text layout, without changing that layout. */
@Composable
private fun rememberRubyOverlay(
    layoutHolder: Array<TextLayoutResult?>,
    ruby: List<RubyToken>?,
    textLength: Int,
    placement: RubyPlacement,
    dimColor: ColorProducer,
    litColor: ColorProducer,
    headProvider: () -> Float,
): Modifier {
    if (ruby.isNullOrEmpty()) return Modifier
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = RubyFontSize, lineHeight = RubyLineHeight)
    val measuredTokens = remember(ruby, style, measurer) { arrayOfNulls<TextLayoutResult>(ruby.size) }
    val headState = rememberUpdatedState(headProvider)
    return Modifier.drawWithContent {
        drawContent()
        val layout = layoutHolder[0] ?: return@drawWithContent
        val head = headState.value()
        for (tokenIndex in ruby.indices) {
            val token = ruby[tokenIndex]
            val start = token.start.coerceIn(0, textLength)
            val end = token.end.coerceIn(start, textLength)
            if (end <= start) continue
            val line = layout.getLineForOffset(start)
            val left = layout.getHorizontalPosition(start, true)
            val right = if (layout.getLineForOffset(end - 1) != line) {
                layout.getLineRight(line)
            } else {
                layout.getHorizontalPosition(end, true)
            }
            if (right <= left) continue
            val measured = measuredTokens[tokenIndex] ?: measurer.measure(
                text = AnnotatedString(token.text),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            ).also { measuredTokens[tokenIndex] = it }
            val x = ((left + right) / 2f - measured.size.width / 2f).coerceIn(
                0f,
                (size.width - measured.size.width).coerceAtLeast(0f),
            )
            val y = when (placement) {
                RubyPlacement.ABOVE -> layout.getLineTop(line).coerceAtMost(
                    layout.getLineBaseline(line) - measured.size.height - RubyBaselineGapPx,
                )
                RubyPlacement.BELOW -> (layout.getLineBaseline(line) + RubyBaselineGapPx)
                    .coerceAtMost(layout.getLineBottom(line) - measured.size.height)
            }
            val lit = when {
                head <= start -> 0f
                head >= end -> 1f
                else -> (head - start) / (end - start).toFloat()
            }
            drawText(
                textLayoutResult = measured,
                color = lerpColor(dimColor(), litColor(), lit),
                topLeft = Offset(x, y),
            )
        }
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    if (f <= 0f) return from
    if (f >= 1f) return to
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = from.alpha + (to.alpha - from.alpha) * f,
    )
}

/** Lays out like the content but gives the layer extra room on every side. */
internal fun Modifier.layerBleed(bleed: Dp): Modifier = layout { measurable, constraints ->
    val px = bleed.roundToPx()
    val extra = px * 2
    val expanded = constraints.copy(
        minWidth = constraints.minWidth + extra,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        minHeight = constraints.minHeight + extra,
        maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + extra else constraints.maxHeight,
    )
    val placeable = measurable.measure(expanded)
    layout(
        (placeable.width - extra).coerceIn(constraints.minWidth, constraints.maxWidth),
        (placeable.height - extra).coerceIn(constraints.minHeight, constraints.maxHeight),
    ) {
        placeable.place(-px, -px)
    }
}

/** The sweep's horizontal position, written from draw for callers that follow it. */
internal class SweepProbe {
    var headX = 0f
    var textWidth = 0f
}

@Composable
internal fun KaraokeSweepText(
    text: String,
    words: List<WordTimestamp>,
    positionProvider: () -> Long,
    waveAmplitudeProvider: () -> Float,
    baseColor: ColorProducer,
    highlightColor: Color,
    subLine: String? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = LyricsFontSize,
    lineHeight: androidx.compose.ui.unit.TextUnit = LyricsLineHeight,
    highBloom: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    ruby: List<RubyToken>? = null,
    rubyPlacement: RubyPlacement = RubyPlacement.BELOW,
    rubyColor: ColorProducer = ColorProducer { Color.White.copy(alpha = 0.4f) },
    motionStyle: LyricsMotionStyle = LyricsMotionStyle.MOTION,
    riseAmplitudeProvider: () -> Float = waveAmplitudeProvider,
    singleLine: Boolean = false,
    motionScale: Float = 1f,
    probe: SweepProbe? = null,
) {
    val head = rememberKaraokeHead(text, words, positionProvider, motionStyle == LyricsMotionStyle.MOTION)
    val filled = (head.offset.floatValue * text.length).roundToInt()
    val style = MaterialTheme.typography.headlineSmall.copy(
        fontSize = fontSize,
        lineHeight = if (!ruby.isNullOrEmpty()) (lineHeight.value + RubyHeadroomSp).sp else lineHeight,
        lineHeightStyle = if (!ruby.isNullOrEmpty()) rubyLineHeightStyle(rubyPlacement) else null,
        textAlign = textAlign,
        fontWeight = FontWeight.Bold,
    )
    val layoutHolder = remember(text) { arrayOfNulls<TextLayoutResult>(1) }
    val wave = rememberUpdatedState(waveAmplitudeProvider)
    Column {
        BasicText(
            text = karaokeAnnotated(text, filled, highlightColor, baseColor()),
            style = style,
            overflow = TextOverflow.Clip,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            onTextLayout = { layoutHolder[0] = it },
            modifier = Modifier.drawWithContent {
                drawContent()
                probe?.let { p ->
                    p.textWidth = size.width
                    p.headX = size.width * head.offset.floatValue
                }
            },
        )
        FadingSubText(text = subLine, color = rubyColor, textAlign = textAlign)
        if (!ruby.isNullOrEmpty()) {
            BasicText(
                text = ruby.joinToString(" ") { it.text },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = RubyFontSize, textAlign = textAlign),
                color = rubyColor,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            )
        }
    }
    // Kept live: the legacy wave used it for the shader; the common draw uses
    // it for the accent halo's strength, so a disabled wave really is disabled.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                head.glow.floatValue = wave.value() * if (highBloom) 1f else 0.5f
                head.motionTick.intValue++
            }
        }
    }
}

@Composable
private fun rememberKaraokeHead(
    text: String,
    words: List<WordTimestamp>,
    positionProvider: () -> Long,
    wordMotion: Boolean = false,
): KaraokeHead {
    val head = remember(text, words) {
        val (starts, ends) = visualWords(text)
        KaraokeHead(starts, ends, motionCells(text, starts, ends))
    }
    LaunchedEffect(text, words, positionProvider, wordMotion) {
        while (true) {
            withFrameMillis {
                val filled = karaokeFilledChars(text, words, positionProvider())
                head.offset.floatValue = if (text.isEmpty()) 0f else filled / text.length.toFloat()
            }
        }
    }
    return head
}

@Stable
internal class KaraokeHead(
    val wordStarts: IntArray = IntArray(0),
    val wordEnds: IntArray = IntArray(0),
    val cells: MotionCells = MotionCells.Empty,
) {
    val offset = mutableFloatStateOf(0f)
    val glow = mutableFloatStateOf(0f)
    val wordCount: Int get() = wordStarts.size
    val cellCount: Int get() = cells.starts.size
    val rise = FloatArray(cells.starts.size)
    val riseVelocity = FloatArray(cells.starts.size)
    val swell = FloatArray(cells.starts.size)
    val swellVelocity = FloatArray(cells.starts.size)
    val motionTick = mutableIntStateOf(0)
}

internal class MotionCells(
    val starts: IntArray,
    val ends: IntArray,
    val word: IntArray,
    val tiled: Boolean,
) {
    companion object {
        val Empty = MotionCells(IntArray(0), IntArray(0), IntArray(0), tiled = false)
    }
}

internal fun motionCells(text: String, wordStarts: IntArray, wordEnds: IntArray): MotionCells {
    val starts = ArrayList<Int>(text.length)
    val ends = ArrayList<Int>(text.length)
    val owner = ArrayList<Int>(text.length)
    for (w in wordStarts.indices) {
        val ws = wordStarts[w]
        val we = wordEnds[w]
        if (ws >= we) continue
        if (!splitsIntoLetters(text, ws, we)) {
            starts += ws
            ends += we
            owner += w
        } else {
            var s = ws
            while (s < we) {
                val e = (s + 1).coerceAtMost(we)
                starts += s
                ends += e
                owner += w
                s = e
            }
        }
    }
    return MotionCells(starts.toIntArray(), ends.toIntArray(), owner.toIntArray(), tiled = true)
}

/** Joined scripts move as whole words: pulling their letters apart tears glyphs. */
internal fun splitsIntoLetters(text: String, start: Int, end: Int): Boolean {
    var i = start.coerceAtLeast(0)
    while (i < end.coerceAtMost(text.length)) {
        val c = text[i]
        if (c.code in 0x0600..0x06FF || c.code in 0x0900..0x097F || c.code in 0x0E00..0x0E7F) return false
        i++
    }
    return true
}

internal fun visualWords(text: String): Pair<IntArray, IntArray> {
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()
    var index = 0
    while (index < text.length) {
        while (index < text.length && text[index].isWhitespace()) index++
        val start = index
        while (index < text.length && !text[index].isWhitespace()) index++
        if (index > start) {
            starts += start
            ends += index
        }
    }
    return starts.toIntArray() to ends.toIntArray()
}

internal fun karaokeFilledChars(
    text: String,
    words: List<WordTimestamp>,
    positionMs: Long,
): Int {
    var searchFrom = 0
    val ranges = words.map { word ->
        val trimmed = word.text.trim()
        val start = if (trimmed.isEmpty()) -1 else text.indexOf(trimmed, searchFrom)
        if (start >= 0) {
            searchFrom = start + trimmed.length
            start to (start + trimmed.length)
        } else {
            null
        }
    }
    val positionSec = positionMs / 1000.0
    var filled = 0
    words.forEachIndexed { index, word ->
        val range = ranges.getOrNull(index) ?: return@forEachIndexed
        when {
            positionSec >= word.endTime -> filled = range.second
            positionSec >= word.startTime -> {
                val span = word.endTime - word.startTime
                if (span > 0.0) {
                    val within = ((positionSec - word.startTime) / span).coerceIn(0.0, 1.0)
                    filled = range.first + ((range.second - range.first) * within).roundToInt()
                }
            }
        }
    }
    return filled.coerceIn(0, text.length)
}

internal fun karaokeAnnotated(
    text: String,
    filledChars: Int,
    highlightColor: Color,
    baseColor: Color,
): AnnotatedString = buildAnnotatedString {
    val cut = filledChars.coerceIn(0, text.length)
    if (cut > 0) withStyle(SpanStyle(color = highlightColor)) { append(text.substring(0, cut)) }
    if (cut < text.length) withStyle(SpanStyle(color = baseColor)) { append(text.substring(cut)) }
}

internal class LyricLineParts(
    val lead: String,
    val backing: String?,
    val leadWords: List<WordTimestamp>?,
    val backingWords: List<WordTimestamp>?,
)

private val WhitespaceRun = Regex("\\s{2,}")

/** Splits a parenthesised or bracketed backing vocal out of the lead line. */
internal fun splitBackingVocal(
    text: String,
    words: List<WordTimestamp>?,
): LyricLineParts {
    val untouched = LyricLineParts(text, null, words, null)
    if (text.indexOf('(') < 0 && text.indexOf('[') < 0) return untouched
    val lead = StringBuilder(text.length)
    val backing = StringBuilder()
    val inBracket = BooleanArray(text.length)
    var depth = 0
    text.forEachIndexed { i, ch ->
        when {
            ch == '(' || ch == '[' -> {
                depth++
                inBracket[i] = true
            }
            (ch == ')' || ch == ']') && depth > 0 -> {
                depth--
                inBracket[i] = true
                if (depth == 0) backing.append(' ')
            }
            depth > 0 -> {
                inBracket[i] = true
                backing.append(ch)
            }
            else -> lead.append(ch)
        }
    }
    if (depth != 0) return untouched
    val leadText = lead.toString().replace(WhitespaceRun, " ").trim()
    val backingText = backing.toString().replace(WhitespaceRun, " ").trim()
    if (backingText.isEmpty()) return untouched

    if (words.isNullOrEmpty()) return LyricLineParts(leadText, backingText, null, null)
    val leadWords = ArrayList<WordTimestamp>()
    val backingWords = ArrayList<WordTimestamp>()
    var searchFrom = 0
    for (word in words) {
        val trimmed = word.text.trim()
        if (trimmed.isEmpty()) continue
        val start = text.indexOf(trimmed, searchFrom)
        if (start < 0) continue
        searchFrom = start + trimmed.length
        val bare = word.copy(
            text = word.text.filterNot { it == '(' || it == ')' || it == '[' || it == ']' },
        )
        if (bare.text.isBlank()) continue
        if (inBracket.getOrElse(start) { false }) backingWords += bare else leadWords += bare
    }
    return LyricLineParts(
        leadText,
        backingText,
        leadWords.ifEmpty { null },
        backingWords.ifEmpty { null },
    )
}
