package com.example.musicfy.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

/**
 * Reusable description of the layered ambient gradient behind glass surfaces.
 * Brushes are sized in pixels so they scale with the host; callers that draw
 * their own background can use [rememberAmbientBrushes] instead of the box below.
 */
data class AmbientBrushes(
    val base: Brush,
    val warm: Brush,
    val violet: Brush,
    val cool: Brush,
    val sheen: Brush,
)

/**
 * Builds the ambient brushes for a given pixel size. Common Compose APIs only;
 * safe to call from the root or GlassKit.
 */
@Composable
fun rememberAmbientBrushes(
    widthPx: Float,
    heightPx: Float,
    pureBlack: Boolean = false,
    intensity: Float = 1f,
): AmbientBrushes = remember(widthPx, heightPx, pureBlack, intensity) {
    val strength = intensity.coerceIn(0f, 1f)
    val glow = if (pureBlack) 0.45f else 1f
    val width = if (widthPx.isFinite() && widthPx > 0f) widthPx else 1f
    val height = if (heightPx.isFinite() && heightPx > 0f) heightPx else 1f
    val span = maxOf(width, height)

    AmbientBrushes(
        base = Brush.linearGradient(
            colors = listOf(
                if (pureBlack) MusicfyPalette.BasePureBlack else MusicfyPalette.Base,
                if (pureBlack) MusicfyPalette.BasePureBlack else MusicfyPalette.SurfaceLow,
            ),
            start = Offset.Zero,
            end = Offset(width, height),
        ),
        warm = Brush.radialGradient(
            colors = listOf(
                MusicfyPalette.AccentWarm.copy(alpha = 0.34f * strength * glow),
                Color.Transparent,
            ),
            center = Offset(width * 0.12f, -height * 0.08f),
            radius = span * 0.85f,
        ),
        violet = Brush.radialGradient(
            colors = listOf(
                MusicfyPalette.AccentViolet.copy(alpha = 0.26f * strength * glow),
                Color.Transparent,
            ),
            center = Offset(width * 0.92f, height * 0.24f),
            radius = span * 0.78f,
        ),
        cool = Brush.radialGradient(
            colors = listOf(
                MusicfyPalette.AccentCool.copy(alpha = 0.30f * strength * glow),
                Color.Transparent,
            ),
            center = Offset(width * 0.46f, height * 1.08f),
            radius = span * 0.95f,
        ),
        sheen = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.055f * strength),
                Color.Transparent,
            ),
            start = Offset.Zero,
            end = Offset(0f, height * 0.6f),
        ),
    )
}

/**
 * Drop-in ambient background for MusicfyTheme/root or GlassKit.
 *
 * ```kotlin
 * AmbientBackdrop(Modifier.fillMaxSize()) {
 *     // screen content
 * }
 * ```
 *
 * With no [content] it can be used as a background-only layer.
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val rawWidth = with(density) { maxWidth.toPx() }
        val rawHeight = with(density) { maxHeight.toPx() }
        val brushes = rememberAmbientBrushes(rawWidth, rawHeight, pureBlack, intensity)

        Box(Modifier.fillMaxSize().background(brushes.base))
        Box(Modifier.fillMaxSize().background(brushes.warm))
        Box(Modifier.fillMaxSize().background(brushes.violet))
        Box(Modifier.fillMaxSize().background(brushes.cool))
        Box(Modifier.fillMaxSize().background(brushes.sheen))
        content()
    }
}
