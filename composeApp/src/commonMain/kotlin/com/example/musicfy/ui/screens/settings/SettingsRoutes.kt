package com.example.musicfy.ui.screens.settings

import com.example.musicfy.ui.screens.Routes

/**
 * Route helpers that are local to the settings screen.
 *
 * Everything the settings index can reach is already registered in
 * [Routes]/`NavigationBuilder`; these helpers only fill the two gaps the
 * upstream screen had (parameterised scrobble/module links) and give names to
 * the values that upstream kept in `ScrobbleSite`/`SettingsHighlight`.
 */
object SettingsRoutes {
    const val LASTFM_SITE = "LASTFM"
    const val LIBREFM_SITE = "LIBREFM"

    const val APPLE_MUSIC_MODULE_ID = "applemusic"
    const val MONOCHROME_MODULE_ID = "monochrome"

    /** Highlight key upstream stores in `SettingsHighlight.pending`. */
    const val HIGHLIGHT_BLUR = "Blur"

    val Integrations: String = Routes.INTEGRATIONS
    val Discord: String = Routes.DISCORD

    fun scrobble(site: String): String = Routes.SCROBBLE.replace("{site}", site)

    fun module(id: String): String = Routes.MODULE.replace("{id}", id)

    fun appleMusic(): String = module(APPLE_MUSIC_MODULE_ID)

    fun monochrome(): String = module(MONOCHROME_MODULE_ID)
}
