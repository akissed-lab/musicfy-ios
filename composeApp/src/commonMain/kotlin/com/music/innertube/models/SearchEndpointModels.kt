@file:OptIn(ExperimentalSerializationApi::class)

package com.music.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Search-slice endpoint models.
 *
 * Upstream keeps these in `Endpoint.kt`/`NavigationEndpoint.kt`, but the existing KMP
 * [WatchEndpoint]/[BrowseEndpoint] are deliberately minimal (lyrics only), so the
 * richer renderer-side variants live here and convert to the shared types when a
 * [com.music.innertube.models.YTItem] is built.
 */
@Serializable
data class NavigationEndpoint(
    val watchEndpoint: SearchWatchEndpoint? = null,
    val watchPlaylistEndpoint: SearchWatchEndpoint? = null,
    val browseEndpoint: SearchBrowseEndpoint? = null,
    val searchEndpoint: SearchQueryEndpoint? = null,
    val feedbackEndpoint: FeedbackEndpoint? = null,
    val continuationCommand: ContinuationCommand? = null,
) {
    val anyWatchEndpoint: SearchWatchEndpoint?
        get() = watchEndpoint ?: watchPlaylistEndpoint

    val musicVideoType: String?
        get() = anyWatchEndpoint?.musicVideoType

    val isArtistEndpoint: Boolean
        get() = browseEndpoint?.isArtistEndpoint == true

    val isAlbumEndpoint: Boolean
        get() = browseEndpoint?.isAlbumEndpoint == true

    val isPlaylistEndpoint: Boolean
        get() = browseEndpoint?.isPlaylistEndpoint == true
}

@Serializable
data class SearchWatchEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null,
    val playlistSetVideoId: String? = null,
    val params: String? = null,
    val index: Int? = null,
    val watchEndpointMusicSupportedConfigs: WatchEndpointMusicSupportedConfigs? = null,
) {
    val musicVideoType: String?
        get() = watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType

    fun toShared(): WatchEndpoint = WatchEndpoint(videoId = videoId)

    @Serializable
    data class WatchEndpointMusicSupportedConfigs(
        val watchEndpointMusicConfig: WatchEndpointMusicConfig? = null,
    )

    @Serializable
    data class WatchEndpointMusicConfig(
        val musicVideoType: String? = null,
    )
}

@Serializable
data class SearchBrowseEndpoint(
    val browseId: String? = null,
    val params: String? = null,
    val browseEndpointContextSupportedConfigs: BrowseEndpointContextSupportedConfigs? = null,
) {
    val pageType: String?
        get() = browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType

    val isArtistEndpoint: Boolean
        get() = pageType == MUSIC_PAGE_TYPE_ARTIST || pageType == MUSIC_PAGE_TYPE_LIBRARY_ARTIST

    val isAlbumEndpoint: Boolean
        get() = pageType == MUSIC_PAGE_TYPE_ALBUM || pageType == MUSIC_PAGE_TYPE_AUDIOBOOK

    val isPlaylistEndpoint: Boolean
        get() = pageType == MUSIC_PAGE_TYPE_PLAYLIST

    fun toShared(): BrowseEndpoint? = browseId?.let { BrowseEndpoint(browseId = it, params = params) }

    @Serializable
    data class BrowseEndpointContextSupportedConfigs(
        val browseEndpointContextMusicConfig: BrowseEndpointContextMusicConfig? = null,
    )

    @Serializable
    data class BrowseEndpointContextMusicConfig(
        val pageType: String? = null,
    )

    companion object {
        const val MUSIC_PAGE_TYPE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
        const val MUSIC_PAGE_TYPE_AUDIOBOOK = "MUSIC_PAGE_TYPE_AUDIOBOOK"
        const val MUSIC_PAGE_TYPE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
        const val MUSIC_PAGE_TYPE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
        const val MUSIC_PAGE_TYPE_LIBRARY_ARTIST = "MUSIC_PAGE_TYPE_LIBRARY_ARTIST"
    }
}

@Serializable
data class SearchQueryEndpoint(
    val query: String? = null,
    val params: String? = null,
)

@Serializable
data class FeedbackEndpoint(
    val feedbackToken: String? = null,
)

@Serializable
data class ContinuationCommand(
    @JsonNames("token")
    val token: String? = null,
)
