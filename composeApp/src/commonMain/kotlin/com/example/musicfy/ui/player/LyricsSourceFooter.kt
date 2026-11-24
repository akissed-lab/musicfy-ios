// LyricsSourceFooter.kt

package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.musicfy.lyrics.EmbeddedLyrics
import com.example.musicfy.lyrics.LyricsHelper
import com.example.musicfy.lyrics.LyricsProviderRegistry
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.theme.interFontFamily

private val LabelColor = Color.White.copy(alpha = 0.6f)
private val ValueColor = Color.White.copy(alpha = 0.42f)
private val StarColor = Color(0xFFFFD60A)

private const val UserEditedProvider = "Your edit"

/**
 * Credit for the lyrics, plus an optional switch-source button. Every surface
 * here is glass; nothing paints an opaque Material card.
 */
@Composable
internal fun LyricsSourceFooter(
    provider: String,
    onSwitchSource: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val fontFamily = interFontFamily()
    val labelStyle = TextStyle(fontFamily = fontFamily, fontSize = 17.sp, letterSpacing = (-0.01).em)
    val buttonTextStyle = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val project = remember(provider) { LyricsProviderRegistry.getProject(provider) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabeledText(
                label = "Source",
                value = sourceName(provider),
                style = labelStyle,
            )
            if (onSwitchSource != null) {
                Spacer(modifier = Modifier.width(10.dp))
                FooterButton(
                    onClick = onSwitchSource,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = "Switch", style = buttonTextStyle.copy(color = LabelColor))
                }
            }
        }
        if (project != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledText(label = project.name, value = null, style = labelStyle)
                Spacer(modifier = Modifier.width(10.dp))
                FooterButton(
                    onClick = { uriHandler.openUri(project.repoUrl) },
                    shape = RoundedCornerShape(50.dp),
                ) {
                    Text(
                        text = "Star on GitHub",
                        style = buttonTextStyle.copy(color = Color.White.copy(alpha = 0.85f)),
                    )
                    Text(text = "★", color = StarColor, fontSize = 15.sp)
                }
            }
        }
    }
}

private fun sourceName(provider: String): String = when (provider) {
    EmbeddedLyrics.Provider -> "Song file"
    UserEditedProvider -> "Your edit"
    LyricsHelper.NoConnectionProvider -> "No connection"
    else -> LyricsProviderRegistry.getDisplayName(provider)
}

@Composable
private fun LabeledText(label: String, value: String?, style: TextStyle) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = LabelColor)) { append(label) }
            if (value != null) {
                withStyle(SpanStyle(color = ValueColor)) { append(" $value") }
            }
        },
        style = style.copy(color = ValueColor),
        maxLines = 1,
    )
}

/** A translucent pill that shrinks a little while held. */
@Composable
private fun FooterButton(
    onClick: () -> Unit,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "footerButtonScale",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassSurface(
                shape = shape,
                style = GlassStyle(
                    containerColor = Color.White.copy(alpha = 0.10f),
                    borderColor = Color.White.copy(alpha = 0.16f),
                    elevation = 0.dp,
                ),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        content()
    }
}
