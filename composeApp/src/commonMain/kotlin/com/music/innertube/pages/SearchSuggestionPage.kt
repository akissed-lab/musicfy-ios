package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.hasExplicitBadge
import com.music.innertube.models.oddElements
import com.music.innertube.models.splitBySeparator
import com.music.innertube.models.clean

object SearchSuggestionPage {
    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? {
        return when {
            renderer.isSong -> {
                val secondaryLine = runsAt(renderer, 1)?.splitBySeparator()?.clean()
                SongItem(
                    id = renderer.playlistItemData?.videoId ?: return null,
                    title = runsAt(renderer, 0)?.firstOrNull()?.text ?: return null,
                    artists = secondaryLine?.firstOrNull()?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                    album = runsAt(renderer, 2)?.firstOrNull()?.let {
                        val browseId = it.navigationEndpoint?.browseEndpoint?.browseId ?: return null
                        Album(name = it.text, id = browseId)
                    },
                    duration = null,
                    musicVideoType = renderer.musicVideoType,
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    explicit = renderer.badges.hasExplicitBadge(),
                )
            }

            renderer.isArtist -> ArtistItem(
                id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                title = runsAt(renderer, 0)?.firstOrNull()?.text ?: return null,
                thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                shuffleEndpoint = watchPlaylistEndpoint(renderer.menu, "MUSIC_SHUFFLE"),
                radioEndpoint = watchPlaylistEndpoint(renderer.menu, "MIX"),
            )

            renderer.isAlbum -> {
                val secondaryLine = runsAt(renderer, 1)?.splitBySeparator() ?: return null
                AlbumItem(
                    browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    playlistId = renderer.menu?.menuRenderer?.items
                        ?.firstNotNullOfOrNull { item ->
                            item.menuNavigationItemRenderer
                                ?.takeIf { it.icon?.iconType == "MUSIC_SHUFFLE" }
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint
                                ?.playlistId
                        } ?: return null,
                    title = runsAt(renderer, 0)?.firstOrNull()?.text ?: return null,
                    artists = secondaryLine.getOrNull(1)?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                    year = secondaryLine.lastOrNull()?.firstOrNull()?.text?.toIntOrNull(),
                    thumbnail = renderer.thumbnail?.getThumbnailUrl() ?: return null,
                    explicit = renderer.badges.hasExplicitBadge(),
                )
            }

            else -> null
        }
    }
}
