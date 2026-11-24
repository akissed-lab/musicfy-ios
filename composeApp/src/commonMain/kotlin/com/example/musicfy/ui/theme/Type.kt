package com.example.musicfy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.inter_bold
import com.example.musicfy.resources.inter_extralight
import com.example.musicfy.resources.inter_light
import com.example.musicfy.resources.inter_medium
import com.example.musicfy.resources.inter_regular
import com.example.musicfy.resources.inter_semibold
import com.example.musicfy.resources.inter_thin
import org.jetbrains.compose.resources.Font

/**
 * Ported from Android `ui/theme/Type.kt`. Inter weights now come from
 * composeResources/font; all sizes, weights and letter spacing are unchanged.
 */
@Composable
fun interFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_thin, FontWeight.Thin),
    Font(Res.font.inter_extralight, FontWeight.ExtraLight),
    Font(Res.font.inter_light, FontWeight.Light),
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

private val GlobalFontWeight = FontWeight.SemiBold
private val GlobalLetterSpacing = (-0.05).em

@Composable
fun AppTypography(): Typography {
    val fontFamily = interFontFamily()
    fun style(size: Int, lineHeight: Int) = TextStyle(
        fontFamily = fontFamily,
        fontWeight = GlobalFontWeight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = GlobalLetterSpacing,
    )

    return Typography(
        displayLarge = style(57, 64),
        displayMedium = style(45, 52),
        displaySmall = style(36, 44),
        headlineLarge = style(32, 40),
        headlineMedium = style(28, 36),
        headlineSmall = style(24, 32),
        titleLarge = style(22, 28),
        titleMedium = style(16, 24),
        titleSmall = style(14, 20),
        bodyLarge = style(16, 24),
        bodyMedium = style(14, 20),
        bodySmall = style(12, 16),
        labelLarge = style(14, 20),
        labelMedium = style(12, 16),
        labelSmall = style(11, 16),
    )
}
