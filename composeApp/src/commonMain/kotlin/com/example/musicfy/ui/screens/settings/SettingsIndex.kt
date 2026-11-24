package com.example.musicfy.ui.screens.settings

import androidx.navigation.NavHostController
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.music_note
import com.example.musicfy.resources.play
import com.example.musicfy.resources.settings_outline
import com.example.musicfy.ui.screens.Routes
import org.jetbrains.compose.resources.DrawableResource

/** One searchable row of the settings index, ported from upstream. */
internal data class SettingsEntry(
    val title: String,
    val page: String,
    val route: String,
    val keywords: String = "",
) {
    val highlightKey: String get() = title
}

/**
 * Local stand-in for upstream `SettingsHighlight` until the shared settings
 * component lands. Nothing consumes it yet; the value is kept so the
 * recommended rows can be wired without another round of edits.
 */
internal object SettingsPendingHighlight {
    var key: String? = null
}

internal val SettingsIndex = listOf(
    // Appearance
    SettingsEntry("Customize player", "Appearance", Routes.APPEARANCE_SETTINGS, "player customise cover style"),
    SettingsEntry("Customize lyrics tab", "Appearance", Routes.APPEARANCE_SETTINGS, "lyrics letter typography font"),
    SettingsEntry("Lyrics Shader", "Appearance", Routes.APPEARANCE_SETTINGS, "lyrics glow wave letters bloom shader"),
    SettingsEntry("High quality bloom", "Appearance", Routes.APPEARANCE_SETTINGS, "lyrics bloom quality extra soft pass"),
    SettingsEntry("Always enable Youtube background", "Appearance", Routes.APPEARANCE_SETTINGS, "youtube video background canvas"),
    SettingsEntry("Enable animated canvas", "Appearance", Routes.APPEARANCE_SETTINGS, "canvas animation spotify looping visual"),
    SettingsEntry("Audio format badge", "Appearance", Routes.APPEARANCE_SETTINGS, "format codec opus aac flac badge title quality"),
    SettingsEntry("Show sample rate", "Appearance", Routes.APPEARANCE_SETTINGS, "sample rate khz hz format badge quality"),
    SettingsEntry("Blur", "Appearance", Routes.APPEARANCE_SETTINGS, "blur glass performance lag strength"),
    SettingsEntry("Animation", "Appearance", Routes.APPEARANCE_SETTINGS, "element blur animator transition animation"),

    // Gesture
    SettingsEntry("Gesture", "Gesture", Routes.GESTURE_SETTINGS, "swipe slide song list left right queue library add remove"),
    SettingsEntry("Swipe right", "Gesture", Routes.GESTURE_SETTINGS, "slide song list queue library add remove action"),
    SettingsEntry("Swipe left", "Gesture", Routes.GESTURE_SETTINGS, "slide song list queue library add remove action"),

    // Audio Lab
    SettingsEntry("Audio Lab", "Audio Lab", Routes.AUDIO_LAB, "sound effects equalizer fx quality"),
    SettingsEntry("Crossfade", "Audio Lab", Routes.CROSSMIX_SETTINGS, "crossmix automix transition gapless blend"),
    SettingsEntry("Equalizer", "Audio Lab", Routes.EQUALIZER, "eq bass treble band parametric graph autoeq headphones correction preset"),
    SettingsEntry("AutoEQ", "Audio Lab", Routes.EQUALIZER, "headphones earbuds correction harman database model"),
    SettingsEntry("Audio Quality", "Audio Lab", Routes.AUDIO_QUALITY, "bitrate stream high low lossless hi-res flac monochrome"),
    SettingsEntry("Reverb", "Audio Lab", Routes.AUDIO_FX, "room hall echo space fx effect"),
    SettingsEntry("Loudness", "Audio Lab", Routes.AUDIO_FX, "boost louder compressor limiter night mode fx"),
    SettingsEntry("Audio Normalization", "Audio Lab", Routes.AUDIO_FX, "even out songs loudness volume gain balance replaygain"),
    SettingsEntry("Spatial audio", "Audio Lab", Routes.AUDIO_FX, "crossfeed binaural hrtf virtual speakers 3d headphones"),
    SettingsEntry("Dolby Atmos", "Audio Lab", Routes.AUDIO_FX, "atmos spatial surround 3d immersive"),
    SettingsEntry("Playback speed", "Audio Lab", Routes.AUDIO_FX, "tempo pitch slowed nightcore preserve pitch"),
    SettingsEntry("Exponential volume", "Audio Lab", Routes.AUDIO_FX, "volume curve logarithmic limit"),
    SettingsEntry("Modules", "Modules", Routes.MODULES, "apple music source module plugin extension stream subscription"),
    SettingsEntry("Apple Music", "Modules", SettingsRoutes.appleMusic(), "apple music itunes subscription stream aac"),
    SettingsEntry("Monochrome", "Modules", SettingsRoutes.monochrome(), "monochrome lossless flac hi-res tidal"),
    SettingsEntry("Provider order", "Audio Lab", Routes.AUDIO_QUALITY, "source order apple music monochrome youtube priority"),
    SettingsEntry("YouTube Music", "Audio Lab", Routes.AUDIO_QUALITY, "youtube turn off disable provider only monochrome apple music"),

    // Integrations
    // TODO(integrations): the Discord/scrobble destinations are registered placeholders;
    // wire the real Discord RPC / Last.fm / Libre.fm flows once their KMP ports exist.
    SettingsEntry("Discord", "Integrations", SettingsRoutes.Discord, "discord rich presence rpc status activity listening playing profile"),
    SettingsEntry("Last.fm", "Integrations", SettingsRoutes.scrobble(SettingsRoutes.LASTFM_SITE), "lastfm scrobble scrobbling history stats account sign in"),
    SettingsEntry("Libre.fm", "Integrations", SettingsRoutes.scrobble(SettingsRoutes.LIBREFM_SITE), "librefm scrobble scrobbling history stats account sign in"),
    SettingsEntry("Now Playing", "Integrations", SettingsRoutes.scrobble(SettingsRoutes.LASTFM_SITE), "scrobble now playing status profile lastfm librefm"),
    SettingsEntry("Send likes", "Integrations", SettingsRoutes.scrobble(SettingsRoutes.LASTFM_SITE), "love loved songs lastfm librefm scrobble"),

    // Playback
    SettingsEntry("Keep playing even when closed", "Playback", Routes.PLAYBACK_SETTINGS, "background close stop playback"),
    SettingsEntry("Save Player's last state", "Playback", Routes.PLAYBACK_SETTINGS, "persistent queue restore track state"),
    SettingsEntry("Skip Silence", "Playback", Routes.PLAYBACK_SETTINGS, "silence trim gap interval"),

    // General
    SettingsEntry("Import Data", "General", Routes.GENERAL_SETTINGS, "backup restore transfer library spotify"),
    SettingsEntry("Edit profile", "General", Routes.GENERAL_SETTINGS, "username avatar account"),
    SettingsEntry("Offline mode", "General", Routes.GENERAL_SETTINGS, "play downloaded cache offline"),
    SettingsEntry("Local song auto metadata", "General", Routes.GENERAL_SETTINGS, "match tags id3 covers lyrics"),

    // Storage
    SettingsEntry("Storage", "Storage", Routes.STORAGE, "space disk size cache downloads clear free junk delete"),
    SettingsEntry("Music cache limit", "Storage", Routes.STORAGE, "cache size limit space song stream"),
    SettingsEntry("Manage downloads", "Storage", Routes.STORAGE, "downloads delete remove biggest offline space"),
    SettingsEntry("Remove all downloads", "Storage", Routes.STORAGE, "delete clear downloads offline space"),

    // Experimental
    SettingsEntry("Import & sync", "Experimental", Routes.EXPERIMENTAL_SETTINGS, "import spotify apple music deezer tidal tunemymusic csv youtube sync export backup transfer"),
    SettingsEntry("Big disc cover styles", "Experimental", Routes.EXPERIMENTAL_SETTINGS, "disc vinyl cover"),
    SettingsEntry("Music haptics", "Experimental", Routes.EXPERIMENTAL_SETTINGS, "vibration haptic"),
    SettingsEntry("Cipher", "Experimental", Routes.CIPHER_SETTINGS, "player script signature refresh"),
    SettingsEntry("Playback diagnostics", "Experimental", Routes.PLAYBACK_DIAGNOSTICS, "logs potoken stream client debug"),
    SettingsEntry("Repeat initial setup", "Experimental", Routes.EXPERIMENTAL_SETTINGS, "onboarding wizard setup"),
    SettingsEntry("Show where songs play from", "Experimental", Routes.EXPERIMENTAL_SETTINGS, "source banner toast apple music monochrome youtube"),
    SettingsEntry("Force playback", "Experimental", Routes.EXPERIMENTAL_SETTINGS, "fallback skip error stream failed youtube"),
)

internal data class RecommendedSetting(
    val title: String,
    val breadcrumb: String,
    val icon: DrawableResource,
    val highlightKey: String? = null,
    val onClick: (NavHostController) -> Unit,
)

/*
 * Icons use drawable accessors that are present in the currently generated
 * resource accessor files. TODO: switch to Res.drawable.contrast / equalizer /
 * linear_scale once the resource generator has picked up the expanded drawable
 * set (the XML files are on disk but the generated accessors are stale).
 */
internal val RecommendedOptions = listOf(
    RecommendedSetting(
        title = "Configure Blur settings",
        breadcrumb = "settings>appearance",
        icon = Res.drawable.settings_outline,
        highlightKey = SettingsRoutes.HIGHLIGHT_BLUR,
        onClick = { nav -> nav.navigate(Routes.APPEARANCE_SETTINGS) },
    ),
    RecommendedSetting(
        title = "Configure Crossfade settings",
        breadcrumb = "settings>audio lab",
        icon = Res.drawable.play,
        onClick = { nav -> nav.navigate(Routes.CROSSMIX_SETTINGS) },
    ),
    RecommendedSetting(
        title = "Configure Equalizer settings",
        breadcrumb = "settings>audio lab",
        icon = Res.drawable.music_note,
        onClick = { nav -> nav.navigate(Routes.EQUALIZER) },
    ),
)
