package com.music.innertube.models

import com.music.innertube.models.SearchBrowseEndpoint.Companion.MUSIC_PAGE_TYPE_ALBUM
import com.music.innertube.models.SearchBrowseEndpoint.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.music.innertube.models.SearchBrowseEndpoint.Companion.MUSIC_PAGE_TYPE_AUDIOBOOK
import com.music.innertube.models.SearchBrowseEndpoint.Companion.MUSIC_PAGE_TYPE_PLAYLIST
import kotlinx.serialization.Serializable

@Serializable
data class MusicTwoRowItemRenderer(
    val title: Runs = Runs(),
    val subtitle: Runs? = null,
    val subtitleBadges: List<Badges>? = null,
    val menu: Menu? = null,
    val thumbnailRenderer: ThumbnailRenderer = ThumbnailRenderer(),
    val navigationEndpoint: NavigationEndpoint = NavigationEndpoint(),
    val thumbnailOverlay: MusicResponsiveListItemRenderer.Overlay? = null,
) {
    val isSong: Boolean
        get() = navigationEndpoint.watchEndpoint != null

    val isPlaylist: Boolean
        get() = navigationEndpoint.browseEndpoint?.pageType == MUSIC_PAGE_TYPE_PLAYLIST

    val isAlbum: Boolean
        get() {
            val pageType = navigationEndpoint.browseEndpoint?.pageType
            return pageType == MUSIC_PAGE_TYPE_ALBUM || pageType == MUSIC_PAGE_TYPE_AUDIOBOOK
        }

    val isArtist: Boolean
        get() = navigationEndpoint.browseEndpoint?.pageType == MUSIC_PAGE_TYPE_ARTIST

    val musicVideoType: String?
        get() = thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
            ?.musicPlayButtonRenderer?.playNavigationEndpoint?.musicVideoType
            ?: navigationEndpoint.musicVideoType
}
