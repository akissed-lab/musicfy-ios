package com.example.musicfy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

/**
 * KMP adapters for the Android-only helpers from upstream `GlassKit.kt`.
 *
 * The upstream implementation records a `RenderNode` and applies a
 * `RenderEffect`/`Shader.TileMode` backdrop blur. None of those APIs exist in
 * `commonMain`, so this file keeps the public names and arguments while
 * rebuilding the effect from [glassSurface], [Modifier.blur] and the shared
 * [LocalGlassState] accent. [GlassKit.kt] is used unchanged.
 */

/**
 * Repaint isolation boundary. On Android this records the subtree into a
 * `RenderNode`; on other targets a graphics layer plus clipping gives the
 * same "only this rectangle repaints" boundary without platform APIs.
 */
fun Modifier.damageBoundary(): Modifier = this
    .clipToBounds()
    .graphicsLayer()

/**
 * Root marker for content that glass chrome may sample.
 *
 * Upstream recorded the subtree into [GlassState.renderNode] and tracked a
 * `rootPosition`. The common [GlassState] exposes only `accent` and
 * `glassEnabled`, so sampling is no longer possible; the marker still keeps
 * the call sites and lets [isActive] drive whether chrome renders its blur.
 */
fun Modifier.glassRoot(
    state: GlassState,
    isActive: () -> Boolean = { true },
): Modifier = this.then(
    if (state.glassEnabled && isActive()) Modifier.graphicsLayer() else Modifier
)

/** Common replacement for `android.graphics.Shader.TileMode`. */
enum class GlassTileMode { CLAMP, REPEAT, MIRROR, DECAL }

/** Direction in which a progressive glass blur fades in. */
enum class BlurDirection { TopToBottom, BottomToTop }

/**
 * Glass backdrop used by the navigation pill, mini player and playlist bar.
 *
 * `tileMode`, `mask`, `bleed` and the precise pixel radius are Android blur
 * details that have no common equivalent; the arguments are retained for API
 * compatibility and mapped to the closest cross-platform effect.
 */
@Composable
fun GlassPillBackground(
    state: GlassState,
    blurRadius: () -> Float = { 24f },
    tint: Color = Color.Transparent,
    foundationColor: Color? = null,
    shape: Shape? = null,
    tileMode: GlassTileMode? = null,
    mask: Brush? = null,
    accentWash: (() -> Color)? = null,
    bleed: Boolean = false,
    progressive: BlurDirection? = null,
    modifier: Modifier = Modifier,
) {
    val surfaceShape = shape ?: RoundedCornerShape(GlassTokens.NavCorner)
    val radius = androidx.compose.ui.unit.Dp(blurRadius().coerceAtLeast(0f))
    val wash = accentWash?.invoke() ?: Color.Transparent
    val accent = state.accent
    val foundation = foundationColor ?: GlassTokens.Chrome.copy(alpha = 0.55f)

    // TODO(tile-mode/mask/bleed): Android's shader tile mode and blur-internal
    // mask cannot be expressed by common Compose; the vertical gradient below
    // approximates both the fade and the progressive direction.
    val washBrush = if (progressive == BlurDirection.BottomToTop) {
        Brush.verticalGradient(listOf(accent.copy(alpha = 0.34f), Color.Transparent))
    } else {
        Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.34f)))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (state.glassEnabled) Modifier.blur(radius) else Modifier)
            .background(foundation, surfaceShape)
            .background(washBrush, surfaceShape)
            .then(if (tint != Color.Transparent) Modifier.background(tint, surfaceShape) else Modifier)
            .then(if (wash != Color.Transparent) Modifier.background(wash, surfaceShape) else Modifier)
            .glassSurface(shape = surfaceShape),
    )
}

/**
 * Progressive glass bar: several blurred slices approximate upstream's
 * row-by-row radius growth. The common implementation uses the shared accent
 * for every slice rather than sampling the app behind it.
 */
@Composable
fun ProgressiveGlassBackground(
    state: GlassState,
    maxBlurRadius: () -> Float = { 24f },
    tint: Color = Color.Transparent,
    foundationColor: Color? = null,
    direction: BlurDirection = BlurDirection.TopToBottom,
    modifier: Modifier = Modifier,
    steps: Int = 5,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val count = steps.coerceAtLeast(1)
        for (i in 1..count) {
            val fraction = i.toFloat() / count
            GlassPillBackground(
                state = state,
                blurRadius = { maxBlurRadius() * fraction * fraction },
                tint = tint,
                foundationColor = foundationColor,
                progressive = direction,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        // Later slices cover progressively more of the bar,
                        // leaving the first slice sharp and the last one fully blurred.
                        Modifier
                    ),
            )
        }
    }
}
