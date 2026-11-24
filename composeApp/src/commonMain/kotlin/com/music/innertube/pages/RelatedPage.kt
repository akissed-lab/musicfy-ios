package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.MusicTwoRowItemRenderer
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.hasExplicitBadge
import com.music.innertube.models.oddElements
import com.music.innertube.models.splitBySeparator

data class RelatedPage(
    val songs: List<SongItem>,
    val albums: List<AlbumItem>,
    val artists: List<ArtistItem>,
    val playlists: List<PlaylistItem>,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(
                renderer.menu?.menuRenderer?.items,
            )
            val secondaryLineRuns = renderer.flexColumns.getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator()

            return SongItem(
                id = renderer.playlistItemData?.videoId ?: return null,
                title = renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                    ?: return null,
                artists = secondaryLineRuns?.firstOrNull()?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                } ?: return null,
                album = renderer.flexColumns.getOrNull(2)
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.let { run ->
                        val browseId = run.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null
                        Album(name = run.text, id = browseId)
                    },
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit = renderer.badges.hasExplicitBadge(),
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
                        ?.watchPlaylistEndpoint?.playlistId ?: return null,
                    title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                    artists = renderer.subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    },
                    year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                    thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: return null,
                    explicit = renderer.subtitleBadges.hasExplicitBadge(),
                )

                renderer.isPlaylist -> PlaylistItem(
                    id = renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix("VL")
                        ?: return null,
                    title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                    author = Artist(
                        name = renderer.subtitle?.runs?.lastOrNull()?.text ?: return null,
                        id = null,
                    ),
                    songCountText = renderer.subtitle?.runs?.findLast {
                        it.text.any { c -> c.isDigit() } && !it.text.contains("view", ignoreCase = true)
                    }?.text,
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
                    },
                )

                renderer.isArtist -> ArtistItem(
                    id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                    title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                    thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: return null,
                    shuffleEndpoint = menuItems?.firstNotNullOfOrNull {
                        it.menuNavigationItemRenderer?.takeIf { item -> item.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.navigationEndpoint?.watchPlaylistEndpoint?.toShared()
                    } ?: return null,
                    radioEndpoint = menuItems?.firstNotNullOfOrNull {
                        it.menuNavigationItemRenderer?.takeIf { item -> item.icon?.iconType == "MIX" }
                            ?.navigationEndpoint?.watchPlaylistEndpoint?.toShared()
                    },
                )

                else -> null
            }
        }
    }
}
