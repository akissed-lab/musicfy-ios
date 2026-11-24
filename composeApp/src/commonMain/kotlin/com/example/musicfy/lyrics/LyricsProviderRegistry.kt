package com.example.musicfy.lyrics

import com.example.musicfy.constants.PreferredLyricsProvider

/**
 * Ported from Android `lyrics/LyricsProviderRegistry.kt`.
 *
 * The complete provider name list and default order are preserved so saved
 * settings (`lyricsProviderOrder`) stay compatible with the Android app.
 * Providers are registered as their ports land; unported ones are skipped
 * by [getOrderedProviders] instead of crashing.
 */
object LyricsProviderRegistry {
    private val providerMap = mapOf<String, LyricsProvider>(
        "BetterLyrics" to BetterLyricsProvider,
        "YouLyPlus" to YouLyPlusLyricsProvider,
        "Paxsenix" to PaxSenixLyricsProvider,
        "SimpMusic" to SimpMusicLyricsProvider,
        "LrcLib" to LrcLibLyricsProvider,
        "Deezer" to DeezerLyricsProvider,
        "NetEase" to NetEaseLyricsProvider,
        "QQMusic" to QQMusicLyricsProvider,
        "Kugou" to KuGouLyricsProvider,
        "YouTubeSubtitle" to YouTubeSubtitleLyricsProvider,
        "YouTubeMusic" to YouTubeLyricsProvider,
        "YouTubeCaptions" to YouTubeCaptionsLyricsProvider,
    )

    /** All upstream source names, including ones not yet ported. */
    val providerNames = listOf(
        "YouLyPlus", "Paxsenix", "BetterLyrics", "SimpMusic", "LrcLib", "Kugou",
        "NetEase", "QQMusic", "Deezer", "YouTubeSubtitle", "YouTubeMusic", "YouTubeCaptions",
    )

    val portedProviderNames get() = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    /** A saved order, with any source added since it was saved at the end, in default order. */
    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return getDefaultProviderOrder()
        val saved = orderString.split(",").map { it.trim() }.filter { it in providerNames }.distinct()
        return saved + getDefaultProviderOrder().filter { it !in saved }
    }

    fun serializeProviderOrder(providers: List<String>): String =
        providers.filter { it in providerNames }.joinToString(",")

    // NetEase, QQ Music and KuGou near the end: they are Chinese services, and LyricsHelper only
    // believes them first-hand for Chinese songs. YouTube's auto captions last of all - they are
    // speech recognition.
    fun getDefaultProviderOrder(): List<String> = listOf(
        "BetterLyrics", "YouLyPlus", "Paxsenix", "SimpMusic", "LrcLib", "Deezer",
        "YouTubeSubtitle", "YouTubeMusic", "NetEase", "QQMusic", "Kugou", "YouTubeCaptions",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        deserializeProviderOrder(orderString).mapNotNull { getProviderByName(it) }

    fun getProviderNameForEnum(enum: PreferredLyricsProvider): String = when (enum) {
        PreferredLyricsProvider.LRCLIB -> "LrcLib"
        PreferredLyricsProvider.KUGOU -> "Kugou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
        PreferredLyricsProvider.YOULYPLUS -> "YouLyPlus"
        PreferredLyricsProvider.PAXSENIX -> "Paxsenix"
    }

    fun getDisplayName(name: String): String = when (name) {
        "YouLyPlus" -> "YouLyPlus"
        "Paxsenix" -> "PaxSenix"
        "BetterLyrics" -> "Apple Music"
        "SimpMusic" -> "SimpMusic"
        "LrcLib" -> "LrcLib"
        "Kugou" -> "KuGou"
        "NetEase" -> "NetEase"
        "QQMusic" -> "QQ Music"
        "Deezer" -> "Deezer"
        "YouTubeSubtitle" -> "YouTube Subtitle"
        "YouTubeMusic" -> "YouTube Music"
        "YouTubeCaptions" -> "YouTube Auto Captions"
        else -> name
    }

    /**
     * The open-source project behind a source, for the lyrics page's "Star on GitHub" button.
     */
    fun getProject(name: String): LyricsProject? = when (name) {
        "YouLyPlus" -> LyricsProject("LyricsPlus", "https://github.com/ibratabian17/LyricsPlus")
        "BetterLyrics" -> LyricsProject("Better Lyrics", "https://github.com/better-lyrics/better-lyrics")
        "LrcLib" -> LyricsProject("LRCLIB", "https://github.com/tranxuanthang/lrclib")
        "SimpMusic" -> LyricsProject("SimpMusic", "https://github.com/maxrave-dev/SimpMusic")
        else -> null
    }
}

data class LyricsProject(val name: String, val repoUrl: String)
