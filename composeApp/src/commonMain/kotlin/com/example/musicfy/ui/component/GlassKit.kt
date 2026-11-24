package com.example.musicfy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * KMP replacement for upstream `ui/component/GlassKit.kt`.
 *
 * The Android original records a `RenderNode` backdrop and samples it on the
 * CPU via `RenderEffect`, which has no equivalent outside Android. This port
 * keeps the *same role* in the app - one shared glass system behind the nav
 * pill, mini player and every screen - but implements it with Compose
 * Multiplatform primitives: a blurred aurora backdrop, translucent layered
 * surfaces, hairline highlights and soft elevation. Every screen must go
 * through [GlassRoot] / [glassSurface] so the look stays consistent.
 */
object GlassTokens {
    val Base: Color = Color(0xFF050508)
    val Chrome: Color = Color(0x99131317)
    val Stroke: Color = Color(0x33FFFFFF)
    val Highlight: Color = Color(0x14FFFFFF)
    val Shadow: Color = Color(0x66000000)
    val MutedContent: Color = Color(0x8CFFFFFF)

    val Pink: Color = Color(0xFFFA4C7E)
    val Purple: Color = Color(0xFF7B5CFF)
    val Blue: Color = Color(0xFF2E9BFF)

    val Blur: Dp = 28.dp
    val Corner: Dp = 28.dp
    val NavCorner: Dp = 26.dp
    val ContentPadding: Dp = 20.dp
}

@Immutable
data class GlassStyle(
    val containerColor: Color = GlassTokens.Chrome,
    val borderColor: Color = GlassTokens.Stroke,
    val highlightColor: Color = GlassTokens.Highlight,
    val shadowColor: Color = GlassTokens.Shadow,
    val blurRadius: Dp = GlassTokens.Blur,
    val cornerRadius: Dp = GlassTokens.Corner,
    val elevation: Dp = 18.dp,
)

/** Shared accent of the current page, used to tint chrome the way Apple Music does. */
@Stable
class GlassState {
    var accent: Color by mutableStateOf(GlassTokens.Purple)
    var glassEnabled: Boolean by mutableStateOf(true)
}

val LocalGlassState = staticCompositionLocalOf<GlassState> { GlassState() }

@Composable
fun rememberGlassState(): GlassState = remember { GlassState() }

/**
 * Root of the liquid-glass design. Wrap the whole app in this once; screens
 * should never draw their own opaque full-screen background.
 */
@Composable
fun GlassRoot(
    modifier: Modifier = Modifier,
    state: GlassState = rememberGlassState(),
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalGlassState provides state) {
        Box(modifier = modifier.fillMaxSize().background(GlassTokens.Base).clipToBounds()) {
            GlassAurora()
            content()
        }
    }
}

/** Soft, out-of-focus colour washes that give the glass something to refract. */
@Composable
private fun GlassAurora() {
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-90).dp, y = (-130).dp)
                .size(360.dp)
                .clip(RoundedCornerShape(180.dp))
                .background(
                    Brush.radialGradient(
                        listOf(GlassTokens.Pink.copy(alpha = 0.55f), Color.Transparent),
                    ),
                )
                .blur(72.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 120.dp, y = (-60).dp)
                .size(320.dp)
                .clip(RoundedCornerShape(160.dp))
                .background(
                    Brush.radialGradient(
                        listOf(GlassTokens.Purple.copy(alpha = 0.45f), Color.Transparent),
                    ),
                )
                .blur(80.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 140.dp)
                .size(420.dp)
                .clip(RoundedCornerShape(210.dp))
                .background(
                    Brush.radialGradient(
                        listOf(GlassTokens.Blue.copy(alpha = 0.35f), Color.Transparent),
                    ),
                )
                .blur(96.dp),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, GlassTokens.Base.copy(alpha = 0.72f)),
                ),
            ),
        )
    }
}

/**
 * The reusable glass surface. A vertical highlight at the top edge plus a
 * hairline border reads as frosted glass even where true backdrop blur is not
 * available on the platform.
 */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(GlassTokens.Corner),
    style: GlassStyle = GlassStyle(),
): Modifier = this
    .shadow(style.elevation, shape, ambientColor = style.shadowColor, spotColor = style.shadowColor)
    .background(
        brush = Brush.verticalGradient(
            listOf(
                style.highlightColor,
                Color.Transparent,
                style.containerColor.copy(alpha = 0.92f),
            ),
        ),
        shape = shape,
    )
    .border(width = 1.dp, color = style.borderColor, shape = shape)

/** Standard content card. Use this instead of Material `Card` in ported screens. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .glassSurface(shape = RoundedCornerShape(style.cornerRadius), style = style)
            .padding(GlassTokens.ContentPadding),
        content = content,
    )
}

/** Floating navigation pill used by the four main tabs and the player sub-tabs. */
@Composable
fun <T> GlassNavigationBar(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .glassSurface(shape = RoundedCornerShape(GlassTokens.NavCorner))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val itemModifier = Modifier
                .clickable { onSelect(item) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .then(
                    if (isSelected) {
                        Modifier.glassSurface(
                            shape = RoundedCornerShape(18.dp),
                            style = GlassStyle(
                                containerColor = Color(0x33FFFFFF),
                                borderColor = Color(0x22FFFFFF),
                                elevation = 0.dp,
                            ),
                        )
                    } else {
                        Modifier
                    },
                )
            Box(modifier = itemModifier) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else GlassTokens.MutedContent,
                )
            }
        }
    }
}
