package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.Menu
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.Run
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.hasExplicitBadge
import com.music.innertube.models.oddElements
import com.music.innertube.models.splitBySeparator
import com.music.innertube.utils.parseTime

data class SearchResult(
    val items: List<YTItem>,
    val continuation: String? = null,
)

object SearchPage {
    fun toYTItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        val secondaryLine = runsAt(renderer, 1)?.splitBySeparator()
        return when {
            renderer.isSong -> {
                val line = secondaryLine ?: return null
                val libraryTokens = PageHelper.extractLibraryTokensFromMenuItems(
                    renderer.menu?.menuRenderer?.items,
                )
                SongItem(
                    id = renderer.playlistItemData?.videoId ?: return null,
                    title = runsAt(renderer, 0)?.firstOrNull()?.text ?: return null,
                    artists = line.firstOrNull()?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                    album = line.getOrNull(1)?.firstOrNull()
                        ?.takeIf { it.navigationEndpoint?.browseEndpoint != null }
                        ?.let {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                            )
                        },
                    duration = line.lastOrNull()?.firstOrNull()?.text?.parseTime(),
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

            renderer.isAlbum -> {
                val line = secondaryLine ?: return null
                AlbumItem(
                    browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    playlistId = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                        ?.musicPlayButtonRenderer?.playNavigationEndpoint
                        ?.anyWatchEndpoint?.playlistId ?: return null,
                    title = runsAt(renderer, 0)?.firstOrNull()?.text ?: return null,
                    artists = line.getOrNull(1)?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                    year = line.getOrNull(2)?.firstOrNull()?.text?.toIntOrNull(),
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    explicit = renderer.badges.hasExplicitBadge(),
                )
            }

            renderer.isPlaylist -> {
                val line = secondaryLine ?: return null
                PlaylistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId
                        ?.removePrefix("VL") ?: return null,
                    title = runsAt(renderer, 0)?.firstOrNull()?.text ?: return null,
                    author = line.firstOrNull()?.firstOrNull()?.let {
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
            }

            else -> null
        }
    }
}

internal fun runsAt(renderer: MusicResponsiveListItemRenderer, index: Int): List<Run>? =
    renderer.flexColumns.getOrNull(index)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs

internal fun watchPlaylistEndpoint(menu: Menu?, iconType: String) =
    menu?.menuRenderer?.items
        ?.firstNotNullOfOrNull { item ->
            item.menuNavigationItemRenderer
                ?.takeIf { it.icon?.iconType == iconType }
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint
                ?.toShared()
        }
