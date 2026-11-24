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
 * Ported from Android `ui/theme/Type.kt`. Inter weights come from
 * composeResources/font; sizes and line heights stay compatible with the
 * original scale while weights and tracking now follow role-specific rules.
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

// Display text is tightened for a poster-like feel; small text opens up for legibility.
private const val DisplayTracking = -0.04f
private const val HeadlineTracking = -0.03f
private const val TitleTracking = -0.02f
private const val BodyTracking = -0.01f
private const val CaptionTracking = 0.015f

@Composable
fun AppTypography(): Typography {
    val fontFamily = interFontFamily()
    fun style(size: Int, lineHeight: Int, weight: FontWeight, tracking: Float) = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.em,
    )

    return Typography(
        displayLarge = style(57, 64, FontWeight.Bold, DisplayTracking),
        displayMedium = style(45, 52, FontWeight.Bold, DisplayTracking),
        displaySmall = style(36, 44, FontWeight.Bold, DisplayTracking),
        headlineLarge = style(32, 40, FontWeight.Bold, HeadlineTracking),
        headlineMedium = style(28, 36, FontWeight.Bold, HeadlineTracking),
        headlineSmall = style(24, 32, FontWeight.Bold, HeadlineTracking),
        titleLarge = style(22, 28, FontWeight.SemiBold, TitleTracking),
        titleMedium = style(16, 24, FontWeight.SemiBold, TitleTracking),
        titleSmall = style(14, 20, FontWeight.SemiBold, TitleTracking),
        bodyLarge = style(16, 24, FontWeight.Medium, BodyTracking),
        bodyMedium = style(14, 20, FontWeight.Medium, BodyTracking),
        bodySmall = style(12, 16, FontWeight.Normal, CaptionTracking),
        labelLarge = style(14, 20, FontWeight.SemiBold, BodyTracking),
        labelMedium = style(12, 16, FontWeight.SemiBold, CaptionTracking),
        labelSmall = style(11, 16, FontWeight.Medium, CaptionTracking),
    )
}
