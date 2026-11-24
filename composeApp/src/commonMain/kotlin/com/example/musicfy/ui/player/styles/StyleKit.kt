package com.example.musicfy.ui.player.styles

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.theme.interFontFamily

/**
 * Small, KMP-safe stand-in for the upstream player style kit.
 *
 * The Android original kept shader-heavy card morphs and canvas textures here. The port keeps the
 * sizing/typography contract and routes every surface through [GlassCard], which is the shared
 * glass system in commonMain.
 */
object PlayerStyleKit {
    val CardCorner: Dp = 30.dp
    val ContentPadding: Dp = 20.dp
    val ControlSize: Dp = 56.dp
    val SmallControlSize: Dp = 36.dp
    val TrackHeight: Dp = 4.dp
    val HandleWidth: Dp = 40.dp
    val HandleHeight: Dp = 4.dp

    @Composable
    fun textStyle(
        size: Int,
        fontWeight: FontWeight = FontWeight.SemiBold,
    ): TextStyle = TextStyle(
        fontFamily = interFontFamily(),
        fontWeight = fontWeight,
        fontSize = size.sp,
        lineHeight = (size + 6).sp,
    )
}

/** Standard glass panel for every ported player page. */
@Composable
fun PlayerGlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = PlayerStyleKit.CardCorner,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(
        modifier = modifier,
        style = GlassStyle(cornerRadius = corner),
        content = content,
    )
}

/** Small uppercase-ish label used by headers and section titles. */
@Composable
fun PlayerEyebrow(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        maxLines = 1,
        style = PlayerStyleKit.textStyle(11, FontWeight.Medium),
        modifier = modifier,
    )
}
