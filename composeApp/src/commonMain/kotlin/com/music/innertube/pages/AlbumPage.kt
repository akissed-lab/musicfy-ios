package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.MusicResponsiveHeaderRenderer
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.Run
import com.music.innertube.models.SongItem
import com.music.innertube.models.getItems
import com.music.innertube.models.hasExplicitBadge
import com.music.innertube.models.oddElements
import com.music.innertube.models.response.BrowseResponse
import com.music.innertube.models.splitBySeparator
import com.music.innertube.utils.parseTime

data class AlbumPage(
    val album: AlbumItem,
    val songs: List<SongItem>,
    val otherVersions: List<AlbumItem>,
    val releasesForYou: List<AlbumItem> = emptyList(),
    val description: String? = null,
    val descriptionRuns: List<Run>? = null,
) {
    companion object {
        fun getPlaylistId(response: BrowseResponse): String? =
            response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')
                ?: response.header?.musicDetailHeaderRenderer?.menu?.menuRenderer?.topLevelButtons
                    ?.firstOrNull()?.buttonRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.playlistId

        fun getTitle(response: BrowseResponse): String? =
            (getHeader(response)?.title ?: response.header?.musicDetailHeaderRenderer?.title)
                ?.runs?.firstOrNull()?.text

        fun getYear(response: BrowseResponse): Int? =
            (getHeader(response)?.subtitle ?: response.header?.musicDetailHeaderRenderer?.subtitle)
                ?.runs?.lastOrNull()?.text?.toIntOrNull()

        fun getThumbnail(response: BrowseResponse): String? =
            response.background?.getThumbnailUrl()
                ?: response.header?.musicDetailHeaderRenderer?.thumbnail?.getThumbnailUrl()

        fun getArtists(response: BrowseResponse): List<Artist> =
            getHeader(response)?.straplineTextOne?.runs?.oddElements()?.map {
                Artist(
                    name = it.text,
                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                )
            } ?: response.header?.musicDetailHeaderRenderer?.subtitle?.runs?.splitBySeparator()
                ?.getOrNull(1)?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                } ?: emptyList()

        private fun getHeader(response: BrowseResponse): MusicResponsiveHeaderRenderer? {
            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs
                ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs
            return tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.firstOrNull()?.musicResponsiveHeaderRenderer
        }

        fun getSongs(response: BrowseResponse, album: AlbumItem): List<SongItem> {
            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs
                ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs
            val shelfRenderer = tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.firstOrNull()?.musicShelfRenderer
                ?: response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
                    ?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer
            return shelfRenderer?.contents?.getItems()?.mapNotNull { getSong(it, album) }.orEmpty()
        }

        fun getSong(renderer: MusicResponsiveListItemRenderer, album: AlbumItem? = null): SongItem? {
            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(
                renderer.menu?.menuRenderer?.items,
            )
            return SongItem(
                id = renderer.playlistItemData?.videoId ?: return null,
                title = PageHelper.extractRuns(renderer.flexColumns, "MUSIC_VIDEO")
                    .firstOrNull()?.text ?: return null,
                artists = PageHelper.extractRuns(renderer.flexColumns, "MUSIC_PAGE_TYPE_ARTIST")
                    .map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    }.ifEmpty { album?.artists ?: emptyList() },
                album = album?.let { Album(it.title, it.browseId) }
                    ?: renderer.flexColumns.getOrNull(2)
                        ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                        ?.let { run ->
                            val browseId = run.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null
                            Album(name = run.text, id = browseId)
                        },
                duration = renderer.fixedColumns?.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.text?.parseTime(),
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: album?.thumbnail ?: return null,
                explicit = renderer.badges.hasExplicitBadge(),
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
            )
        }
    }
}
