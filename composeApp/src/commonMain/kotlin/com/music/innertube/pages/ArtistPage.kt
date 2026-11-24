package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.MusicCarouselShelfRenderer
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.MusicShelfRenderer
import com.music.innertube.models.MusicTwoRowItemRenderer
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.Run
import com.music.innertube.models.SectionListRenderer
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.getItems
import com.music.innertube.models.hasExplicitBadge
import com.music.innertube.models.oddElements
import com.music.innertube.models.splitBySeparator

data class ArtistSection(
    val title: String,
    val items: List<YTItem>,
    val moreEndpoint: BrowseEndpoint?,
)

data class ArtistPage(
    val artist: ArtistItem,
    val sections: List<ArtistSection>,
    val description: String?,
    val subscriberCountText: String?,
    val monthlyListenerCount: String? = null,
    val descriptionRuns: List<Run>? = null,
    /** whether the signed-in account follows this channel; null when signed out or not said */
    val subscribed: Boolean? = null,
) {
    companion object {
        fun fromSectionListRendererContent(content: SectionListRenderer.Content): ArtistSection? =
            when {
                content.musicShelfRenderer != null -> fromMusicShelfRenderer(content.musicShelfRenderer)
                content.musicCarouselShelfRenderer != null ->
                    fromMusicCarouselShelfRenderer(content.musicCarouselShelfRenderer)
                else -> null
            }

        private fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): ArtistSection? =
            ArtistSection(
                title = renderer.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = renderer.contents?.getItems()?.mapNotNull { fromMusicResponsiveListItemRenderer(it) }
                    ?.ifEmpty { null } ?: return null,
                moreEndpoint = renderer.title?.runs?.firstOrNull()?.navigationEndpoint
                    ?.browseEndpoint?.toShared(),
            )

        private fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): ArtistSection? {
            val basicHeader = renderer.header?.musicCarouselShelfBasicHeaderRenderer ?: return null
            return ArtistSection(
                title = basicHeader.title.runs?.firstOrNull()?.text ?: return null,
                items = renderer.contents.mapNotNull { content ->
                    content.musicTwoRowItemRenderer?.let { fromMusicTwoRowItemRenderer(it) }
                        ?: content.musicResponsiveListItemRenderer?.let { fromMusicResponsiveListItemRenderer(it) }
                }.ifEmpty { null } ?: return null,
                moreEndpoint = basicHeader.moreContentButton?.buttonRenderer?.navigationEndpoint
                    ?.browseEndpoint?.toShared(),
            )
        }

        private fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val secondaryLineRuns = renderer.flexColumns.getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator()

            val artists = secondaryLineRuns?.firstOrNull()?.oddElements()?.map {
                Artist(
                    name = it.text,
                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                )
            }

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
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.text ?: return null,
                artists = artists ?: return null,
                album = album,
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit = renderer.badges.hasExplicitBadge(),
                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.toShared(),
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
            )
        }

        private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
            val menuItems = renderer.menu?.menuRenderer?.items
            fun shuffleEndpoint() = menuItems?.firstNotNullOfOrNull {
                it.menuNavigationItemRenderer?.takeIf { item -> item.icon?.iconType == "MUSIC_SHUFFLE" }
                    ?.navigationEndpoint?.watchPlaylistEndpoint?.toShared()
            }
            fun radioEndpoint() = menuItems?.firstNotNullOfOrNull {
                it.menuNavigationItemRenderer?.takeIf { item -> item.icon?.iconType == "MIX" }
                    ?.navigationEndpoint?.watchPlaylistEndpoint?.toShared()
            }

            return when {
                renderer.isSong -> {
                    val subtitleRuns = renderer.subtitle?.runs?.oddElements() ?: return null
                    SongItem(
                        id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = subtitleRuns.filter {
                            it.navigationEndpoint?.browseEndpoint != null
                        }.map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId,
                            )
                        }.ifEmpty {
                            subtitleRuns.firstOrNull()?.let { listOf(Artist(name = it.text, id = null)) }
                                ?: emptyList()
                        },
                        musicVideoType = renderer.musicVideoType,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                            ?: return null,
                        explicit = renderer.subtitleBadges.hasExplicitBadge(),
                    )
                }

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

                renderer.isPlaylist -> PlaylistItem(
                    id = renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix("VL")
                        ?: return null,
                    title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                    author = Artist(
                        name = renderer.subtitle?.runs?.firstOrNull()?.text ?: return null,
                        id = null,
                    ),
                    songCountText = null,
                    thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: return null,
                    playEndpoint = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                        ?.musicPlayButtonRenderer?.playNavigationEndpoint
                        ?.watchPlaylistEndpoint?.toShared() ?: return null,
                    shuffleEndpoint = shuffleEndpoint() ?: return null,
                    radioEndpoint = radioEndpoint() ?: return null,
                )

                renderer.isArtist -> ArtistItem(
                    id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                    title = renderer.title.runs?.lastOrNull()?.text ?: return null,
                    thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl()
                        ?: return null,
                    channelId = menuItems?.firstNotNullOfOrNull {
                        it.toggleMenuServiceItemRenderer?.takeIf { item ->
                            item.defaultIcon?.iconType == "SUBSCRIBE"
                        }?.defaultServiceEndpoint?.subscribeEndpoint?.channelIds?.firstOrNull()
                    },
                    shuffleEndpoint = shuffleEndpoint() ?: return null,
                    radioEndpoint = radioEndpoint() ?: return null,
                )

                else -> null
            }
        }
    }
}
