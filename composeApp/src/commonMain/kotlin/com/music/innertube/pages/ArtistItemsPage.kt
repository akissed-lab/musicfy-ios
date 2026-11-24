package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.MusicTwoRowItemRenderer
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.hasExplicitBadge
import com.music.innertube.models.looksLikeTimestamp
import com.music.innertube.models.oddElements
import com.music.innertube.models.splitBySeparator
import com.music.innertube.utils.parseTime

data class ArtistItemsPage(
    val title: String,
    val items: List<YTItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val secondaryLineRuns = renderer.flexColumns.getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator()

            val artists = secondaryLineRuns?.firstOrNull()?.oddElements()?.map {
                Artist(
                    name = it.text,
                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                )
            }?.filter { !it.name.looksLikeTimestamp() }

            val album = renderer.flexColumns.lastOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                ?.let { run ->
                    val browseId = run.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null
                    Album(name = run.text, id = browseId)
                }

            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(
                renderer.menu?.menuRenderer?.items,
            )

            return SongItem(
                id = renderer.playlistItemData?.videoId ?: return null,
                title = renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                    ?: return null,
                artists = artists ?: return null,
                album = album,
                duration = renderer.fixedColumns?.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.text?.parseTime(),
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit = renderer.badges.hasExplicitBadge(),
                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.toShared(),
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
            )
        }

        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
            val menuItems = renderer.menu?.menuRenderer?.items
            return when {
                renderer.isAlbum -> AlbumItem(
                    browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                    playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                        ?.musicPlayButtonRenderer?.playNavigationEndpoint
                        ?.anyWatchEndpoint?.playlistId ?: return null,
                    title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                    artists = null,
                    year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                    thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: return null,
                    explicit = renderer.subtitleBadges.hasExplicitBadge(),
                )

                renderer.isSong -> SongItem(
                    id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                    title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                    artists = renderer.subtitle?.runs?.splitBySeparator()?.firstOrNull()?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                    musicVideoType = renderer.musicVideoType,
                    thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: return null,
                    endpoint = renderer.navigationEndpoint.watchEndpoint?.toShared(),
                )

                renderer.isPlaylist -> PlaylistItem(
                    id = renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix("VL")
                        ?: return null,
                    title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                    author = renderer.subtitle?.runs?.firstOrNull()?.let {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    },
                    songCountText = renderer.subtitle?.runs?.getOrNull(4)?.text,
                    thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: return null,
                    playEndpoint = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                        ?.musicPlayButtonRenderer?.playNavigationEndpoint
                        ?.watchPlaylistEndpoint?.toShared() ?: return null,
                    shuffleEndpoint = menuItems?.firstNotNullOfOrNull {
                        it.menuNavigationItemRenderer?.takeIf { item -> item.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.navigationEndpoint?.watchPlaylistEndpoint?.toShared()
                    } ?: return null,
                    radioEndpoint = menuItems?.firstNotNullOfOrNull {
                        it.menuNavigationItemRenderer?.takeIf { item -> item.icon?.iconType == "MIX" }
                            ?.navigationEndpoint?.watchPlaylistEndpoint?.toShared()
                    } ?: return null,
                )

                else -> null
            }
        }
    }
}
