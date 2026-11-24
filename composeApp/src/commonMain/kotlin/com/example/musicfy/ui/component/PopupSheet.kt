// PopupSheet.kt
//
// Common port of the single popup/bottom-sheet primitive. The Android-only
// BackHandler and player bottom-sheet dependency are replaced with minimal
// common stubs (TODO) so the public state/host API stays usable.

package com.example.musicfy.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Minimal common replacement for `androidx.activity.compose.BackHandler`. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    // TODO: platform back handling needs an expect/actual bridge; no-op in commonMain.
}

/** Minimal common replacement for the player's bottom-sheet state. */
@Stable
class PlayerBottomSheetState {
    var progress: Float by mutableFloatStateOf(0f)
}

val LocalPlayerBottomSheetState = compositionLocalOf<PlayerBottomSheetState?> { null }

/** Replacements for upstream `ui/player/menu.MenuSurface` / `MenuRowSurface`. */
val MenuSurface: Color get() = GlassTokens.Chrome
val MenuRowSurface: Color get() = GlassTokens.Chrome.copy(alpha = 0.36f)

@Stable
class PopupSheetState {
    internal val frames: SnapshotStateList<Frame> = mutableStateListOf()

    val isVisible: Boolean get() = frames.isNotEmpty()

    internal val presence: Float
        get() {
            var max = 0f
            for (i in frames.indices) max = maxOf(max, frames[i].presence)
            return max
        }

    internal class Frame(
        locked: Boolean,
        val buttonBar: (@Composable () -> Unit)?,
        val fullBleed: Boolean,
        val minHeightPx: Float,
        val onClosed: (() -> Unit)?,
        val surface: Color?,
        val content: @Composable ColumnScope.() -> Unit,
    ) {
        val hasHandle = !locked
        var locked by mutableStateOf(locked)
        var offset by mutableFloatStateOf(1f)
        val presence: Float get() = (1f - offset).coerceIn(0f, 1f)
        var heightPx by mutableFloatStateOf(0f)
        var closing by mutableStateOf(false)
    }

    fun show(
        locked: Boolean = false,
        buttonBar: (@Composable () -> Unit)? = null,
        fullBleed: Boolean = false,
        onClosed: (() -> Unit)? = null,
        surface: Color? = null,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        showWithHandle(locked, buttonBar, fullBleed, onClosed, surface, content)
    }

    fun showWithHandle(
        locked: Boolean = false,
        buttonBar: (@Composable () -> Unit)? = null,
        fullBleed: Boolean = false,
        onClosed: (() -> Unit)? = null,
        surface: Color? = null,
        content: @Composable ColumnScope.() -> Unit,
    ): PopupSheetHandle {
        var index = frames.size
        while (index > 0 && frames[index - 1].closing) index--
        val covered = frames.getOrNull(index - 1)
        val frame = Frame(locked, buttonBar, fullBleed, covered?.heightPx ?: 0f, onClosed, surface, content)
        frames.add(index, frame)
        return PopupSheetHandle(frame)
    }

    fun dismiss() {
        frames.lastOrNull { !it.closing }?.closing = true
    }

    fun dismissAll() {
        for (i in frames.indices) frames[i].closing = true
    }

    fun setLocked(locked: Boolean) {
        frames.lastOrNull { !it.closing }?.locked = locked
    }

    internal fun dismissFromOutside() {
        val top = frames.lastOrNull() ?: return
        if (!top.closing && !top.locked) top.closing = true
    }

    internal fun remove(frame: Frame) {
        if (frames.remove(frame)) frame.onClosed?.invoke()
    }
}

class PopupSheetHandle internal constructor(private val frame: PopupSheetState.Frame) {
    val isClosing: Boolean get() = frame.closing

    fun dismiss() {
        if (!frame.closing) frame.closing = true
    }

    fun setLocked(locked: Boolean) {
        frame.locked = locked
    }
}

typealias MenuState = PopupSheetState
typealias BottomSheetPageState = PopupSheetState
typealias ZoomOutOverlayState = PopupSheetState

val LocalMenuState = compositionLocalOf { PopupSheetState() }
val LocalBottomSheetPageState = compositionLocalOf { PopupSheetState() }
val LocalZoomOutOverlayState = compositionLocalOf { PopupSheetState() }

private val PopupSpring = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessLow,
    visibilityThreshold = 0.0005f,
)

private const val BackdropRecede = 0.08f
private const val RecedeScale = 0.92f
private val PeekHeight = 24.dp
private const val DragCloseThreshold = 0.35f

@Composable
fun PopupSheetHost(
    states: List<PopupSheetState>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val active = states.firstOrNull { it.isVisible }
    val playerSheet = LocalPlayerBottomSheetState.current

    BackHandler(enabled = active != null) { active?.dismiss() }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = active?.presence ?: 0f
                    val scale = 1f - BackdropRecede * p
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape((28f * p).dp)
                    clip = p > 0f
                    val moving = p > 0f && p < 1f
                    val overPlayer = (playerSheet?.progress ?: 0f) > 0.001f
                    compositingStrategy = if (moving && !overPlayer) {
                        androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    } else {
                        androidx.compose.ui.graphics.CompositingStrategy.Auto
                    }
                },
        ) {
            content()
        }

        if (active != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.5f * active.presence }
                    .background(Color.Black),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = active::dismissFromOutside,
                    ),
            )
            key(active) {
                PopupSheetStack(state = active)
            }
        }
    }
}

@Composable
private fun PopupSheetStack(state: PopupSheetState) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val parentHeightPx = with(density) { maxHeight.toPx() }
        val peekPx = with(density) { PeekHeight.toPx() }

        state.frames.forEachIndexed { index, frame ->
            key(frame) {
                val depth = (state.frames.size - 1 - index).coerceAtLeast(0)
                val target = if (frame.closing) 1f else 0f

                LaunchedEffect(frame, frame.closing) {
                    Animatable(frame.offset).animateTo(target, PopupSpring) {
                        frame.offset = value
                    }
                    if (frame.closing) state.remove(frame)
                }

                val recede = if (depth > 0) RecedeScale else 1f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = frame.presence
                            scaleX = recede + (1f - recede) * p
                            scaleY = recede + (1f - recede) * p
                            translationY = frame.offset * parentHeightPx - depth * peekPx * p
                            alpha = if (depth == 0) 1f else 0.82f
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    SheetBody(frame = frame, parentHeightPx = parentHeightPx, state = state)
                }
            }
        }
    }
}

private fun sheetSurfaceShape(): Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@Composable
private fun SheetBody(
    frame: PopupSheetState.Frame,
    parentHeightPx: Float,
    state: PopupSheetState,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxHeight = with(density) { (parentHeightPx * 0.86f).toDp() }
    val shape = sheetSurfaceShape()
    val draggableState = rememberDraggableState { delta ->
        if (parentHeightPx > 0f) {
            frame.offset = (frame.offset + delta / parentHeightPx).coerceIn(0f, 1f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .glassSurface(
                shape = shape,
                style = GlassStyle(
                    containerColor = frame.surface ?: MenuSurface,
                    cornerRadius = 28.dp,
                    blurRadius = 24.dp,
                ),
            )
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (frame.offset > DragCloseThreshold || velocity > 1200f) {
                        frame.closing = true
                    } else {
                        scope.launch {
                            Animatable(frame.offset).animateTo(0f, PopupSpring) {
                                frame.offset = value
                            }
                        }
                    }
                },
            )
            .clip(shape)
            .padding(bottom = 8.dp),
    ) {
        if (frame.hasHandle) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f)),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            content = frame.content,
        )
        if (frame.buttonBar != null) {
            Spacer(Modifier.height(8.dp))
            frame.buttonBar?.invoke()
        }
    }
}

/** Sheet row background; upstream read this from `MenuRowSurface`. */
val PopupSheetRowSurface: Color get() = MenuRowSurface
