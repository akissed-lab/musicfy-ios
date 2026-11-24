package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.ArtistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Similar artists come from the main page; their songs/albums load a moment later. */
class ArtistSimilarViewModel(
    container: AppContainer,
    val artistId: String,
) : ViewModel() {
    /** The world around one artist, grouped by item kind rather than by who it came from. */
    data class SimilarGroups(
        val artists: List<ArtistItem> = emptyList(),
        val playlists: List<PlaylistItem> = emptyList(),
        val songs: List<SongItem> = emptyList(),
        val albums: List<AlbumItem> = emptyList(),
    )

    private val _groups = MutableStateFlow<SimilarGroups?>(null)
    val groups: StateFlow<SimilarGroups?> = _groups.asStateFlow()

    private val _deepLoading = MutableStateFlow(true)
    val deepLoading: StateFlow<Boolean> = _deepLoading.asStateFlow()

    init {
        viewModelScope.launch {
            val page = ArtistPageCache.page(artistId)
                ?: YouTube.artist(artistId)
                    .onFailure(::reportException)
                    .getOrNull()
                    ?.also { ArtistPageCache.putPage(artistId, it) }
            if (page == null) {
                _groups.value = _groups.value ?: SimilarGroups()
                _deepLoading.value = false
                return@launch
            }

            val artists = page.sections
                .filter { section -> section.items.firstOrNull() is ArtistItem }
                .flatMap { section -> section.items.filterIsInstance<ArtistItem>() }
                .distinctBy { it.id }
            val playlists = page.sections
                .filter { section -> section.items.firstOrNull() is PlaylistItem }
                .flatMap { section -> section.items.filterIsInstance<PlaylistItem>() }
                .distinctBy { it.id }
            _groups.value = SimilarGroups(artists = artists, playlists = playlists)

            val pages = withContext(Dispatchers.Default) {
                coroutineScope {
                    artists.take(5).map { artist ->
                        async {
                            ArtistPageCache.page(artist.id)
                                ?: withTimeoutOrNull(8_000) { YouTube.artist(artist.id).getOrNull() }
                                    ?.also { ArtistPageCache.putPage(artist.id, it) }
                        }
                    }.awaitAll()
                }
            }.filterNotNull()

            val songs = interleave(
                pages.map { similar ->
                    similar.sections
                        .firstOrNull { section -> (section.items.firstOrNull() as? SongItem)?.album != null }
                        ?.items?.filterIsInstance<SongItem>()
                        ?.take(3)
                        .orEmpty()
                },
                limit = 15,
            ).distinctBy { it.id }
            val albums = pages.mapNotNull { similar: ArtistPage ->
                similar.sections
                    .filter { section -> section.items.firstOrNull() is AlbumItem }
                    .flatMap { section -> section.items.filterIsInstance<AlbumItem>() }
                    .maxByOrNull { it.year ?: 0 }
            }.distinctBy { it.id }

            _groups.value = SimilarGroups(
                artists = artists,
                playlists = playlists,
                songs = songs,
                albums = albums,
            )
            _deepLoading.value = false
        }
    }

    private fun interleave(lists: List<List<SongItem>>, limit: Int): List<SongItem> {
        val result = mutableListOf<SongItem>()
        var index = 0
        while (result.size < limit && lists.any { index < it.size }) {
            lists.forEach { list ->
                if (result.size < limit && index < list.size) result += list[index]
            }
            index++
        }
        return result
    }
}
