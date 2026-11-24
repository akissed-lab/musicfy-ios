// LyricsOffsetSheet.kt
//
// KMP port of the lyrics timing offset sheet. Auto offset is deliberately omitted:
// it depended on Media3 PCM decoding and a service-side analyser. Manual offset
// still writes through PlaybackController, which persists it with the current song.

package com.example.musicfy.ui.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.glassSurface
import kotlin.math.roundToInt

/** The slider and typed value both stop here: ten seconds either way, shown in ms. */
private const val MaxOffsetMs = 10_000

/** Slider snapping, so a drag lands on clean numbers instead of 4137. */
private const val SliderStepMs = 10

/** How far the − and + labels move the offset. */
private const val NudgeMs = 50

/** Shifts the current song's lyrics earlier (+) or later (−), in milliseconds. */
@Composable
fun LyricsOffsetSheet(onDismiss: () -> Unit) {
    val controller = LocalPlaybackController.current ?: return
    val focusManager = LocalFocusManager.current
    val controllerOffset by controller.lyricsOffsetMs.collectAsState()

    var offset by remember { mutableIntStateOf(controllerOffset) }
    var draft by remember { mutableStateOf(controllerOffset.toString()) }

    LaunchedEffect(controllerOffset) {
        if (controllerOffset != offset) {
            offset = controllerOffset
            draft = controllerOffset.toString()
        }
    }

    fun applyOffset(value: Int, updateDraft: Boolean = true) {
        val clamped = value.coerceIn(-MaxOffsetMs, MaxOffsetMs)
        offset = clamped
        if (updateDraft) draft = clamped.toString()
        controller.setLyricsOffsetMs(clamped)
    }

    fun setOffset(value: Int) {
        focusManager.clearFocus()
        applyOffset(value)
    }

    LyricsSheetScaffold(onDismiss = onDismiss, title = "Lyrics offset") {
        Column {
            Text(
                text = "Move lyrics earlier or later until they sit with the singing.",
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OffsetValueBox(
                text = draft,
                onTextChange = { raw ->
                    val cleaned = raw
                        .filterIndexed { index, char -> char.isDigit() || (index == 0 && char == '-') }
                        .take(6)
                    draft = cleaned
                    cleaned.toIntOrNull()?.let { parsed ->
                        val clamped = parsed.coerceIn(-MaxOffsetMs, MaxOffsetMs)
                        offset = clamped
                        controller.setLyricsOffsetMs(clamped)
                        if (clamped != parsed) draft = clamped.toString()
                    }
                },
                onFocusChange = { focused ->
                    if (!focused) draft = offset.toString()
                },
            )

            Spacer(modifier = Modifier.height(30.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                NudgeLabel(symbol = "−") { setOffset(offset - NudgeMs) }
                NudgeLabel(symbol = "+") { setOffset(offset + NudgeMs) }
            }

            Spacer(modifier = Modifier.height(6.dp))
            OffsetTrack(offset = offset, onOffsetChange = { setOffset(it) })

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Auto offset needs the audio analyser, which is not part of the KMP player yet. " +
                    "The offset is saved as soon as you move it.",
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))
            SheetPill(label = "Done", onClick = onDismiss)
        }
    }
}

/** The grey value box. Tap it to type an exact number. */
@Composable
private fun OffsetValueBox(
    text: String,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .glassSurface(
                shape = RoundedCornerShape(22.dp),
                style = GlassStyle(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    borderColor = Color.White.copy(alpha = 0.14f),
                    elevation = 0.dp,
                ),
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier
                .width((text.length.coerceAtLeast(1) * 27 + 4).dp)
                .onFocusChanged { onFocusChange(it.isFocused) },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "ms",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun NudgeLabel(symbol: String, onClick: () -> Unit) {
    Text(
        text = symbol,
        color = Color.White,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

/** Flat bar: white up to the current value, with the middle of the bar as zero. */
@Composable
private fun OffsetTrack(offset: Int, onOffsetChange: (Int) -> Unit) {
    var widthPx by remember { mutableIntStateOf(0) }
    val fraction = ((offset + MaxOffsetMs) / (2f * MaxOffsetMs)).coerceIn(0f, 1f)

    fun valueAt(x: Float): Int {
        if (widthPx <= 0) return offset
        val raw = (x / widthPx).coerceIn(0f, 1f) * 2f * MaxOffsetMs - MaxOffsetMs
        return (raw / SliderStepMs).roundToInt() * SliderStepMs
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) { detectTapGestures { onOffsetChange(valueAt(it.x)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onOffsetChange(valueAt(change.position.x))
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(50.dp))
                .glassSurface(
                    shape = RoundedCornerShape(50.dp),
                    style = GlassStyle(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        borderColor = Color.Transparent,
                        elevation = 0.dp,
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White),
            )
        }
    }
}
