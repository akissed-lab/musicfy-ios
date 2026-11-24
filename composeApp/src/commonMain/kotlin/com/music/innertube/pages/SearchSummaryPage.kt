package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.MusicCardShelfRenderer
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.Run
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.clean
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.models.hasExplicitBadge
import com.music.innertube.models.looksLikeTimestamp
import com.music.innertube.models.oddElements
import com.music.innertube.models.splitBySeparator
import com.music.innertube.utils.parseTime

data class SearchSummary(
    val title: String,
    val items: List<YTItem>,
)

data class SearchSummaryPage(
    val summaries: List<SearchSummary>,
) {
    fun filterExplicit(enabled: Boolean): SearchSummaryPage =
        if (!enabled) {
            this
        } else {
            SearchSummaryPage(
                summaries.mapNotNull { summary ->
                    SearchSummary(
                        title = summary.title,
                        items = summary.items.filterExplicit().ifEmpty { return@mapNotNull null },
                    )
                },
            )
        }

    fun filterVideoSongs(disableVideos: Boolean): SearchSummaryPage =
        if (!disableVideos) {
            this
        } else {
            SearchSummaryPage(
                summaries.mapNotNull { summary ->
                    SearchSummary(
                        title = summary.title,
                        items = summary.items.filterVideoSongs(true).ifEmpty { return@mapNotNull null },
                    )
                },
            )
        }

    fun filterYoutubeShorts(enabled: Boolean = false): SearchSummaryPage =
        if (!enabled) {
            this
        } else {
            SearchSummaryPage(
                summaries.mapNotNull { summary ->
                    SearchSummary(
                        title = summary.title,
                        items = summary.items.filterYoutubeShorts(true).ifEmpty { return@mapNotNull null },
                    )
                },
            )
        }

    companion object {
        fun fromMusicCardShelfRenderer(renderer: MusicCardShelfRenderer): YTItem? {
            val subtitle = renderer.subtitle?.runs?.splitBySeparator()
            val onTap = renderer.onTap
            return when {
                onTap.watchEndpoint != null -> SongItem(
                    id = onTap.watchEndpoint.videoId ?: return null,
                    title = renderer.title?.runs?.firstOrNull()?.text ?: return null,
                    artists = subtitle?.getOrNull(1)?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                    album = subtitle.getOrNull(2)?.firstOrNull()
                        ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
                        ?.let {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                            )
                        },
                    duration = subtitle.lastOrNull()?.firstOrNull()?.text?.parseTime(),
                    musicVideoType = onTap.musicVideoType,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    explicit = renderer.subtitleBadges.hasExplicitBadge(),
                )

                onTap.isArtistEndpoint -> ArtistItem(
                    id = onTap.browseEndpoint?.browseId ?: return null,
                    title = renderer.title?.runs?.firstOrNull()?.text ?: return null,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    shuffleEndpoint = buttonWatchPlaylistEndpoint(renderer, "MUSIC_SHUFFLE") ?: return null,
                    radioEndpoint = buttonWatchPlaylistEndpoint(renderer, "MIX") ?: return null,
                )

                onTap.isAlbumEndpoint -> AlbumItem(
                    browseId = onTap.browseEndpoint?.browseId ?: return null,
                    playlistId = renderer.buttons.firstNotNullOfOrNull { button ->
                        button.buttonRenderer?.command?.anyWatchEndpoint?.playlistId
                    } ?: return null,
                    title = renderer.title?.runs?.firstOrNull()?.text ?: return null,
                    artists = subtitle?.getOrNull(1)?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                    year = null,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    explicit = renderer.subtitleBadges.hasExplicitBadge(),
                )

                onTap.isPlaylistEndpoint -> PlaylistItem(
                    id = onTap.browseEndpoint?.browseId?.removePrefix("VL") ?: return null,
                    title = renderer.header?.musicCardShelfHeaderBasicRenderer?.title?.runs
                        ?.joinToString(separator = "") { it.text } ?: return null,
                    author = subtitle?.firstOrNull()?.joinToString(separator = "") { it.text }
                        ?.let { Artist(id = null, name = it) } ?: return null,
                    songCountText = null,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    playEndpoint = buttonWatchPlaylistEndpoint(renderer, "PLAY_ARROW") ?: return null,
                    shuffleEndpoint = buttonWatchPlaylistEndpoint(renderer, "MUSIC_SHUFFLE") ?: return null,
                    radioEndpoint = null,
                )

                else -> null
            }
        }

        // A top-result card's songs can read "song • 3:02" with no artist, so take the
        // segment that links to an artist, never a timestamp, and let the card supply its own artist.
        private fun songArtists(segments: List<List<Run>>, fallback: Artist?): List<Artist> {
            val linked = segments.firstOrNull { segment ->
                segment.any { it.navigationEndpoint?.browseEndpoint?.isArtistEndpoint == true }
            }
            if (linked == null && fallback != null) return listOf(fallback)
            val segment = linked ?: segments.firstOrNull() ?: return emptyList()
            return segment.oddElements()
                .filterNot { it.text.looksLikeTimestamp() }
                .map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                }
        }

        fun fromMusicResponsiveListItemRenderer(
            renderer: MusicResponsiveListItemRenderer,
            fallbackArtist: Artist? = null,
        ): YTItem? {
            val secondaryLine = runsAt(renderer, 1)?.splitBySeparator() ?: return null
            val thirdLine = runsAt(renderer, 2)?.splitBySeparator().orEmpty()
            val listRun = (secondaryLine + thirdLine).clean()
            return when {
                renderer.isSong -> {
                    val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(
                        renderer.menu?.menuRenderer?.items,
                    )
                    SongItem(
                        id = renderer.playlistItemData?.videoId ?: return null,
                        title = runsAt(renderer, 0)?.firstOrNull()?.text ?: return null,
                        artists = songArtists(listRun, fallbackArtist),
                        album = listRun.getOrNull(1)?.firstOrNull()
                            ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
                            ?.let {
                                Album(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                                )
                            },
                        duration = secondaryLine.lastOrNull()?.firstOrNull()?.text?.parseTime(),
                        musicVideoType = renderer.musicVideoType,
                        thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                        explicit = renderer.badges.hasExplicitBadge(),
                        libraryAddToken = libraryTokens.addToken,
                        libraryRemoveToken = libraryTokens.removeToken,
                    )
                }

                renderer.isArtist -> ArtistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    title = runsAt(renderer, 0)?.firstOrNull()?.text ?: return null,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    shuffleEndpoint = watchPlaylistEndpoint(renderer.menu, "MUSIC_SHUFFLE") ?: return null,
                    radioEndpoint = watchPlaylistEndpoint(renderer.menu, "MIX") ?: return null,
                )

                renderer.isAlbum -> AlbumItem(
                    browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    playlistId = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                        ?.musicPlayButtonRenderer?.playNavigationEndpoint
                        ?.watchPlaylistEndpoint?.playlistId ?: return null,
                    title = runsAt(renderer, 0)?.firstOrNull()?.text ?: return null,
                    artists = secondaryLine.getOrNull(1)?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                    year = secondaryLine.getOrNull(2)?.firstOrNull()?.text?.toIntOrNull(),
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    explicit = renderer.badges.hasExplicitBadge(),
                )

                renderer.isPlaylist -> PlaylistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId
                        ?.removePrefix("VL") ?: return null,
                    title = runsAt(renderer, 0)?.firstOrNull()?.text ?: return null,
                    author = secondaryLine.getOrNull(1)?.firstOrNull()?.let {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                    songCountText = runsAt(renderer, 1)?.lastOrNull()?.text ?: return null,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    playEndpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                        ?.musicPlayButtonRenderer?.playNavigationEndpoint
                        ?.watchPlaylistEndpoint?.toShared() ?: return null,
                    shuffleEndpoint = watchPlaylistEndpoint(renderer.menu, "MUSIC_SHUFFLE") ?: return null,
                    radioEndpoint = watchPlaylistEndpoint(renderer.menu, "MIX") ?: return null,
                )

                else -> null
            }
        }

        private fun buttonWatchPlaylistEndpoint(
            renderer: MusicCardShelfRenderer,
            iconType: String,
        ) = renderer.buttons.firstNotNullOfOrNull { button ->
            button.buttonRenderer
                ?.takeIf { it.icon?.iconType == iconType }
                ?.command
                ?.watchPlaylistEndpoint
                ?.toShared()
        }
    }
}
