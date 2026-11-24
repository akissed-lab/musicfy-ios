package com.example.musicfy.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Shared, platform-neutral palette for the dark "liquid glass" look.
 *
 * Ranges are deliberately narrow: the background is almost black with a slight
 * blue/violet cast, while the accent is a restrained pink-red → violet → indigo
 * ramp. Values here are safe to use from GlassKit, the player and the root; no
 * artwork sampling or platform-specific colour APIs are involved.
 */
object MusicfyPalette {

    // Accent ramp: warm pink-red through violet into cool indigo.
    val Accent: Color = Color(0xFFFF2D55)
    val AccentWarm: Color = Color(0xFFFF375F)
    val AccentViolet: Color = Color(0xFFB14BFF)
    val AccentCool: Color = Color(0xFF5E5CE6)
    val AccentRamp: List<Color> = listOf(AccentWarm, AccentViolet, AccentCool)

    // Layered near-black neutrals (no flat grey).
    val Base: Color = Color(0xFF050507)
    val BasePureBlack: Color = Color(0xFF000000)
    val SurfaceLow: Color = Color(0xFF0A0A0E)
    val Surface: Color = Color(0xFF101015)
    val SurfaceHigh: Color = Color(0xFF17171C)
    val SurfaceTinted: Color = Color(0xFF1B1420)
    val SurfaceTint: Color = Color(0xFF2A1630)
    val Glass: Color = Color(0x3317171C)
    val GlassTint: Color = Color(0x24FF2D55)
    val Hairline: Color = Color(0x33FFFFFF)
    val Scrim: Color = Color(0xB3000000)

    // Text is lifted off the near-black surfaces rather than pure-white/flat-grey.
    val TextPrimary: Color = Color(0xFFF5F5F7)
    val TextSecondary: Color = Color(0xFFC7C7CE)
    val TextTertiary: Color = Color(0xFF9A9AA5)
    val OnAccent: Color = Color(0xFFFFFFFF)

    val Error: Color = Color(0xFFFF453A)
    val OnError: Color = Color(0xFF1A0508)
}
