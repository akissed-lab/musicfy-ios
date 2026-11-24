package com.music.innertube.pages

import com.music.innertube.models.Album
import com.music.innertube.models.Artist
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.hasExplicitBadge
import com.music.innertube.models.looksLikeTimestamp
import com.music.innertube.models.oddElements
import com.music.innertube.models.splitBySeparator
import com.music.innertube.utils.parseTime

data class PlaylistPage(
    val playlist: PlaylistItem,
    val songs: List<SongItem>,
    val songsContinuation: String?,
    val continuation: String?,
    val related: List<YTItem>? = null,
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
                }?.filter { !it.name.looksLikeTimestamp() }.orEmpty(),
                album = renderer.flexColumns.getOrNull(2)
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.let { run ->
                        val browseId = run.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null
                        Album(name = run.text, id = browseId)
                    },
                duration = renderer.fixedColumns?.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.text?.parseTime(),
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit = renderer.badges.hasExplicitBadge(),
                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.toShared(),
                setVideoId = renderer.playlistItemData?.playlistSetVideoId ?: return null,
                libraryAddToken = libraryTokens.addToken,
                libraryRemoveToken = libraryTokens.removeToken,
            )
        }
    }
}
