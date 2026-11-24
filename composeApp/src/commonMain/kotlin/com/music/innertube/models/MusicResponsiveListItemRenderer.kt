@file:OptIn(ExperimentalSerializationApi::class)

package com.music.innertube.models

import com.music.innertube.models.SearchBrowseEndpoint.Companion.MUSIC_PAGE_TYPE_ALBUM
import com.music.innertube.models.SearchBrowseEndpoint.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.music.innertube.models.SearchBrowseEndpoint.Companion.MUSIC_PAGE_TYPE_AUDIOBOOK
import com.music.innertube.models.SearchBrowseEndpoint.Companion.MUSIC_PAGE_TYPE_LIBRARY_ARTIST
import com.music.innertube.models.SearchBrowseEndpoint.Companion.MUSIC_PAGE_TYPE_PLAYLIST
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class MusicResponsiveListItemRenderer(
    val badges: List<Badges>? = null,
    val fixedColumns: List<FlexColumn>? = null,
    val flexColumns: List<FlexColumn> = emptyList(),
    val thumbnail: ThumbnailRenderer? = null,
    val menu: Menu? = null,
    val playlistItemData: PlaylistItemData? = null,
    val overlay: Overlay? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
) {
    val isSong: Boolean
        get() = navigationEndpoint == null ||
            navigationEndpoint.watchEndpoint != null ||
            navigationEndpoint.watchPlaylistEndpoint != null

    val isPlaylist: Boolean
        get() = navigationEndpoint?.browseEndpoint?.pageType == MUSIC_PAGE_TYPE_PLAYLIST

    val isAlbum: Boolean
        get() {
            val pageType = navigationEndpoint?.browseEndpoint?.pageType
            return pageType == MUSIC_PAGE_TYPE_ALBUM || pageType == MUSIC_PAGE_TYPE_AUDIOBOOK
        }

    val isArtist: Boolean
        get() {
            val pageType = navigationEndpoint?.browseEndpoint?.pageType
            return pageType == MUSIC_PAGE_TYPE_ARTIST || pageType == MUSIC_PAGE_TYPE_LIBRARY_ARTIST
        }

    val musicVideoType: String?
        get() = overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
            ?.playNavigationEndpoint?.musicVideoType
            ?: navigationEndpoint?.musicVideoType

    @Serializable
    data class FlexColumn(
        @JsonNames("musicResponsiveListItemFixedColumnRenderer")
        val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer = MusicResponsiveListItemFlexColumnRenderer(),
    ) {
        @Serializable
        data class MusicResponsiveListItemFlexColumnRenderer(
            val text: Runs? = null,
        )
    }

    @Serializable
    data class PlaylistItemData(
        val playlistSetVideoId: String? = null,
        val videoId: String? = null,
    )

    @Serializable
    data class Overlay(
        val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer? = null,
    ) {
        @Serializable
        data class MusicItemThumbnailOverlayRenderer(
            val content: Content? = null,
        ) {
            @Serializable
            data class Content(
                val musicPlayButtonRenderer: MusicPlayButtonRenderer? = null,
            ) {
                @Serializable
                data class MusicPlayButtonRenderer(
                    val playNavigationEndpoint: NavigationEndpoint? = null,
                )
            }
        }
    }
}
