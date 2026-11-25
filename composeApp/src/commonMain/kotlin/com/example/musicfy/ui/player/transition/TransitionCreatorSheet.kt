// TransitionCreatorSheet.kt
//
// KMP port of the portable part of upstream "NEW transition creator" (MixIt +
// Crossmix): the picker, the volume curves and the live picture. Actual audio
// mixing stays in the platform player; this sheet only produces a TransitionSpec.

package com.example.musicfy.ui.player.transition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.player.transition.TransitionController
import com.example.musicfy.player.transition.TransitionCurve
import com.example.musicfy.player.transition.TransitionKind
import com.example.musicfy.player.transition.TransitionProgress
import com.example.musicfy.player.transition.TransitionSpec
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.utils.stableSystemBars
import kotlin.math.roundToInt

/**
 * Bottom sheet for creating a transition. No player connection is required:
 * [onApply] receives the validated [TransitionSpec] so the integrating screen
 * can persist it or feed it to a future player adapter. [onDismiss] is called
 * right after a successful apply; the caller owns the route/nav state.
 */
@Composable
fun TransitionCreatorSheet(
    onDismiss: () -> Unit,
    onApply: (TransitionSpec) -> Unit,
    modifier: Modifier = Modifier,
    initialSpec: TransitionSpec = TransitionSpec.Default,
    controller: TransitionController = remember { TransitionController(initialSpec) },
) {
    val spec = controller.spec

    PreviewClock(controller)

    TransitionSheetScaffold(
        onDismiss = onDismiss,
        title = "Transition creator",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Choose how the next song takes over. The preview is only a picture " +
                    "and a volume curve; no audio is started.",
                color = GlassTokens.MutedContent,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            Spacer(Modifier.height(12.dp))

            TransitionPreviewCard(controller = controller)

            Spacer(Modifier.height(16.dp))

            SectionLabel("Transition")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransitionKind.entries.forEach { kind ->
                    ChoiceChip(
                        label = kind.shortLabel,
                        selected = spec.kind == kind,
                        onClick = { controller.setKind(kind) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = spec.kind.description,
                color = GlassTokens.MutedContent,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )

            Spacer(Modifier.height(16.dp))

            SectionLabel("Timing")
            Spacer(Modifier.height(6.dp))
            SettingSlider(
                label = "Duration",
                valueText = "${(spec.durationMs / 100f).roundToInt() / 10f} s",
                value = spec.durationMs.toFloat(),
                valueRange = TransitionSpec.MinDurationMs.toFloat()..TransitionSpec.MaxDurationMs.toFloat(),
                onValueChange = { next ->
                    val stepped = ((next / 500f).roundToInt() * 500)
                        .coerceIn(TransitionSpec.MinDurationMs, TransitionSpec.MaxDurationMs)
                    controller.setDurationMs(stepped)
                },
            )
            Spacer(Modifier.height(10.dp))
            SettingSlider(
                label = "Mix amount",
                valueText = "${(spec.mixAmount * 100f).roundToInt()}%",
                value = spec.mixAmount.coerceIn(0f, 1f),
                valueRange = 0f..1f,
                onValueChange = { controller.setMixAmount(it) },
            )

            Spacer(Modifier.height(16.dp))

            SectionLabel("Volume curve")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransitionCurve.entries.forEach { curve ->
                    ChoiceChip(
                        label = curve.label,
                        selected = spec.curve == curve,
                        onClick = { controller.setCurve(curve) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = spec.curve.description,
                color = GlassTokens.MutedContent,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )

            Spacer(Modifier.height(16.dp))

            AutoCrossfadeRow(
                checked = spec.autoCrossfade,
                onCheckedChange = { controller.setAutoCrossfade(it) },
            )

            Spacer(Modifier.height(18.dp))

            SavePill(
                label = "Save transition",
                onClick = {
                    onApply(spec.validated())
                    onDismiss()
                },
            )
        }
    }
}

/** Advance the preview playhead from Compose frames; nothing is sent to a player. */
@Composable
private fun PreviewClock(controller: TransitionController) {
    val playing = controller.previewPlaying
    val duration = controller.spec.effectiveDurationMs

    LaunchedEffect(playing, duration) {
        if (!playing || duration <= 0) return@LaunchedEffect
        val totalMs = duration.toLong()
        var lastNanos = 0L
        while (controller.previewPlaying) {
            val next = withFrameNanos { now ->
                val dt = if (lastNanos == 0L) {
                    0.016f
                } else {
                    ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.064f)
                }
                lastNanos = now
                val advanced = controller.previewElapsedMs + (dt * 1_000f).toLong()
                if (advanced >= totalMs) 0L else advanced
            }
            controller.previewElapsedMs = next
        }
    }
}

@Composable
private fun TransitionSheetScaffold(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.stableSystemBars)
                .glassSurface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    style = GlassStyle(
                        containerColor = Color(0xF2101015),
                        borderColor = Color.White.copy(alpha = 0.14f),
                        elevation = 24.dp,
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .glassSurface(
                            shape = CircleShape,
                            style = GlassStyle(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                borderColor = GlassTokens.Stroke,
                                elevation = 0.dp,
                            ),
                        )
                        .clickable(onClick = onDismiss),
                ) {
                    Text(text = "×", color = GlassTokens.MutedContent, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TransitionPreviewCard(
    controller: TransitionController,
    modifier: Modifier = Modifier,
) {
    val spec = controller.spec
    val progress = controller.progress

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(20.dp),
                style = GlassStyle(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    borderColor = Color.White.copy(alpha = 0.10f),
                    elevation = 0.dp,
                ),
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Live preview",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(progress.fraction * 100f).roundToInt()}%",
                color = GlassTokens.MutedContent,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.32f)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
            ) {
                PreviewTrack(
                    label = "OUT",
                    color = GlassTokens.Pink,
                    volume = progress.outgoingVolume,
                    pan = progress.outgoingPan,
                    modifier = Modifier.weight(1f),
                )
                PreviewTrack(
                    label = "IN",
                    color = GlassTokens.Purple,
                    volume = progress.incomingVolume,
                    pan = progress.incomingPan,
                    modifier = Modifier.weight(1f),
                )
            }
            TransitionVolumeGraph(
                spec = spec,
                progress = progress,
                modifier = Modifier.matchParentSize(),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            val active = controller.previewPlaying
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (active) GlassTokens.Pink else Color.White.copy(alpha = 0.16f))
                    .clickable {
                        if (active) controller.pausePreview() else controller.playPreview()
                    },
            ) {
                Text(
                    text = if (active) "■" else "▶",
                    color = if (active) Color.White else GlassTokens.MutedContent,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Slider(
                value = progress.fraction,
                valueRange = 0f..1f,
                onValueChange = { next ->
                    controller.pausePreview()
                    controller.setPreviewFraction(next)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
            )
        }
    }
}

@Composable
private fun PreviewTrack(
    label: String,
    color: Color,
    volume: Float,
    pan: Float,
    modifier: Modifier = Modifier,
) {
    val shown = (0.18f + 0.82f * volume.coerceIn(0f, 1f))
    Column(
        modifier = modifier
            .offset(x = (pan * 36f).dp)
            .height(66.dp)
            .glassSurface(
                shape = RoundedCornerShape(14.dp),
                style = GlassStyle(
                    containerColor = color.copy(alpha = 0.10f),
                    borderColor = color.copy(alpha = 0.28f),
                    elevation = 0.dp,
                ),
            )
            .padding(10.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (index in 0 until 10) {
                val barHeight = 8 + ((index * 7 + label.length * 3) % 5) * 4
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = shown * (0.45f + (index % 3) * 0.18f))),
                )
            }
        }
    }
}

/** Two crossing volume lines plus a playhead, drawn from the same helper the player would use. */
@Composable
private fun TransitionVolumeGraph(
    spec: TransitionSpec,
    progress: TransitionProgress,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pad = 8.dp.toPx()
        val plotWidth = size.width - pad * 2f
        val plotHeight = size.height - pad * 2f
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        val left = pad
        val right = left + plotWidth
        val top = pad
        val bottom = top + plotHeight

        fun xAt(t: Float) = left + plotWidth * t.coerceIn(0f, 1f)
        fun yAt(v: Float) = top + plotHeight * (1f - v.coerceIn(0f, 1f))

        val grid = Color.White.copy(alpha = 0.08f)
        val hairline = 1.dp.toPx()
        drawLine(grid, Offset(left, top), Offset(right, top), hairline)
        drawLine(grid, Offset(left, yAt(0.5f)), Offset(right, yAt(0.5f)), hairline)
        drawLine(grid, Offset(left, bottom), Offset(right, bottom), hairline)

        if (spec.kind == TransitionKind.NONE) {
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(left, top),
                end = Offset(left, bottom),
                strokeWidth = 1.5.dp.toPx(),
            )
            return@Canvas
        }

        val samples = 32
        val stroke = 2.dp.toPx()
        for (index in 0 until samples) {
            val t0 = index.toFloat() / samples.toFloat()
            val t1 = (index + 1).toFloat() / samples.toFloat()
            val first = TransitionProgress.compute(spec, (t0 * spec.effectiveDurationMs).toLong())
            val second = TransitionProgress.compute(spec, (t1 * spec.effectiveDurationMs).toLong())

            drawLine(
                color = GlassTokens.Pink,
                start = Offset(xAt(t0), yAt(first.outgoingVolume)),
                end = Offset(xAt(t1), yAt(second.outgoingVolume)),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = GlassTokens.Purple,
                start = Offset(xAt(t0), yAt(first.incomingVolume)),
                end = Offset(xAt(t1), yAt(second.incomingVolume)),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        val playheadX = xAt(progress.fraction)
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = Offset(playheadX, top),
            end = Offset(playheadX, bottom),
            strokeWidth = hairline,
        )
        val dotRadius = 3.dp.toPx()
        drawCircle(Color.White, dotRadius, Offset(playheadX, yAt(progress.outgoingVolume)))
        drawCircle(Color.White, dotRadius, Offset(playheadX, yAt(progress.incomingVolume)))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = GlassTokens.MutedContent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun RowScope.ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .glassSurface(
                shape = RoundedCornerShape(14.dp),
                style = GlassStyle(
                    containerColor = if (selected) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                    borderColor = if (selected) GlassTokens.Pink.copy(alpha = 0.55f) else GlassTokens.Stroke,
                    elevation = 0.dp,
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else GlassTokens.MutedContent,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                color = GlassTokens.Pink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            valueRange = valueRange,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
    }
}

@Composable
private fun AutoCrossfadeRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                style = GlassStyle(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    borderColor = Color.White.copy(alpha = 0.10f),
                    elevation = 0.dp,
                ),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Auto crossfade",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Use this transition for songs without their own setting.",
                color = GlassTokens.MutedContent,
                fontSize = 11.sp,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SavePill(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50.dp))
            .glassSurface(
                shape = RoundedCornerShape(50.dp),
                style = GlassStyle(
                    containerColor = Color.White.copy(alpha = 0.20f),
                    borderColor = Color.White.copy(alpha = 0.18f),
                    elevation = 0.dp,
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
