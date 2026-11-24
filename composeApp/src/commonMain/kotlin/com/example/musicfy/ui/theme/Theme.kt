package com.example.musicfy.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Default seed used by the theme picker; now the signature pink-red accent. */
val DefaultThemeColor: Color = MusicfyPalette.Accent

/** musicfy renders dark-only; the light palette was never finished upstream. */
const val ForceDarkTheme = true

/**
 * Ported from Android `ui/theme/Theme.kt`.
 *
 * Dynamic Material You colours and platform-only artwork colour extraction are
 * intentionally omitted: they were disabled upstream ("Dynamic (Material You)
 * colour is deliberately not used").
 *
 * The scheme uses near-black layered neutrals, tinted containers and a pink →
 * violet → indigo accent ramp. [AmbientBackdrop] can be placed behind content
 * when an additional ambient glow is wanted.
 */
@Composable
fun MusicfyTheme(
    darkTheme: Boolean = ForceDarkTheme,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = MusicfyPalette.AccentWarm,
            onPrimary = MusicfyPalette.OnAccent,
            primaryContainer = Color(0xFF3A1226),
            onPrimaryContainer = Color(0xFFFFD6DE),
            secondary = MusicfyPalette.AccentViolet,
            onSecondary = MusicfyPalette.OnAccent,
            secondaryContainer = Color(0xFF2A1640),
            onSecondaryContainer = Color(0xFFEADDFF),
            tertiary = MusicfyPalette.AccentCool,
            onTertiary = MusicfyPalette.OnAccent,
            tertiaryContainer = Color(0xFF1B1C46),
            onTertiaryContainer = Color(0xFFDDE1FF),
            background = MusicfyPalette.Base,
            onBackground = MusicfyPalette.TextPrimary,
            surface = MusicfyPalette.SurfaceLow,
            onSurface = MusicfyPalette.TextPrimary,
            surfaceVariant = MusicfyPalette.SurfaceTinted,
            onSurfaceVariant = MusicfyPalette.TextSecondary,
            surfaceTint = MusicfyPalette.SurfaceTint,
            inverseSurface = Color(0xFFE6E6EA),
            inverseOnSurface = Color(0xFF101015),
            inversePrimary = MusicfyPalette.Accent,
            error = MusicfyPalette.Error,
            onError = MusicfyPalette.OnError,
            errorContainer = Color(0xFF3B0F14),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = Color(0xFF42424D),
            outlineVariant = Color(0xFF2A2A33),
            scrim = MusicfyPalette.Scrim,
            surfaceDim = MusicfyPalette.SurfaceLow,
            surfaceBright = MusicfyPalette.SurfaceHigh,
            surfaceContainerLowest = MusicfyPalette.Base,
            surfaceContainerLow = Color(0xFF0D0D11),
            surfaceContainer = MusicfyPalette.Surface,
            surfaceContainerHigh = MusicfyPalette.SurfaceHigh,
            surfaceContainerHighest = Color(0xFF1E1E24),
        )
    } else {
        lightColorScheme(
            primary = MusicfyPalette.Accent,
            onPrimary = MusicfyPalette.OnAccent,
            primaryContainer = Color(0xFFFFD6DE),
            onPrimaryContainer = Color(0xFF3A1226),
            secondary = MusicfyPalette.AccentViolet,
            onSecondary = MusicfyPalette.OnAccent,
            secondaryContainer = Color(0xFFEADDFF),
            onSecondaryContainer = Color(0xFF2A1640),
            tertiary = MusicfyPalette.AccentCool,
            onTertiary = MusicfyPalette.OnAccent,
            tertiaryContainer = Color(0xFFDDE1FF),
            onTertiaryContainer = Color(0xFF1B1C46),
            background = Color(0xFFF7F7FA),
            onBackground = Color(0xFF111116),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111116),
            surfaceVariant = Color(0xFFEDEDF3),
            onSurfaceVariant = Color(0xFF41414B),
            surfaceTint = MusicfyPalette.Accent,
            inverseSurface = Color(0xFF1E1E24),
            inverseOnSurface = Color(0xFFF5F5F7),
            inversePrimary = MusicfyPalette.AccentWarm,
            error = MusicfyPalette.Error,
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF3B0F14),
            outline = Color(0xFF77777F),
            outlineVariant = Color(0xFFC7C7CE),
            scrim = Color(0x99000000),
            surfaceDim = Color(0xFFE4E4EA),
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7F7FA),
            surfaceContainer = Color(0xFFF1F1F6),
            surfaceContainerHigh = Color(0xFFECECF2),
            surfaceContainerHighest = Color(0xFFE6E6ED),
        )
    }

    val typography = AppTypography()
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) baseColorScheme.pureBlack(true) else baseColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        ProvideTextStyle(value = typography.bodyLarge, content = content)
    }
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        background = MusicfyPalette.BasePureBlack,
        surface = MusicfyPalette.BasePureBlack,
        surfaceDim = MusicfyPalette.BasePureBlack,
        surfaceContainerLowest = MusicfyPalette.BasePureBlack,
        surfaceContainerLow = MusicfyPalette.BasePureBlack,
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
