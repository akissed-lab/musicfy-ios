package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.musicfy.db.entities.Album as LocalAlbum
import com.example.musicfy.db.entities.Artist as LocalArtist
import com.example.musicfy.db.entities.Playlist as LocalPlaylist
import com.example.musicfy.db.entities.SearchHistory
import com.example.musicfy.db.entities.Song as LocalSong
import com.example.musicfy.settings.SettingsKeys
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * KMP port of the upstream online search suggestions screen.
 *
 * YouTube's `searchSuggestions` and `YouTubeUrlParser` pages are not part of the KMP innertube
 * port, so suggestions and link previews stay empty; the local library is still searched and the
 * query history still comes from SQLDelight. `refreshSettings()` should be called after the
 * hide-explicit/hide-video settings change (SettingsStore has no reactive flow).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnlineSearchSuggestionViewModel(container: AppContainer) : ViewModel() {
    private val database = container.database
    private val settings = container.settings

    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    /** SettingsStore has no reactive flow; bumping this recreates the current suggestions. */
    private val settingsVersion = MutableStateFlow(0)

    fun refreshSettings() {
        settingsVersion.value++
    }

    init {
        viewModelScope.launch {
            combine(query, settingsVersion) { currentQuery, _ -> currentQuery }
                .flatMapLatest { currentQuery ->
                    val history = searchHistory(
                        prefix = currentQuery,
                        limit = if (currentQuery.isEmpty()) Int.MAX_VALUE else 3,
                    )

                    if (currentQuery.isEmpty()) {
                        history.map { entries -> SearchSuggestionViewState(history = entries) }
                    } else {
                        val hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false)
                        val hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false)

                        // TODO: YouTube.searchSuggestions() and YouTubeUrlParser.parse() are not
                        // ported; `suggestions`, `isFromLink` and linked-item previews stay empty.
                        val localItems = combine(
                            database.searchSongs(currentQuery, OfflinePreviewSize),
                            database.searchAlbums(currentQuery, OfflinePreviewSize),
                            database.searchArtists(currentQuery, OfflinePreviewSize),
                            database.searchPlaylists(currentQuery, OfflinePreviewSize),
                        ) { songs, albums, artists, playlists ->
                            val visibleSongs = if (hideVideoSongs) {
                                songs.filter { !it.song.isVideo }
                            } else {
                                songs
                            }
                            val converted = buildList<YTItem> {
                                visibleSongs.forEach { add(it.toYTItem()) }
                                albums.forEach { add(it.toYTItem()) }
                                artists.forEach { add(it.toYTItem()) }
                                playlists.forEach { add(it.toYTItem()) }
                            }
                            if (hideExplicit) converted.filterExplicit() else converted
                        }

                        combine(history, localItems) { entries, items ->
                            SearchSuggestionViewState(
                                history = entries,
                                suggestions = emptyList(),
                                items = items,
                                isFromLink = false,
                            )
                        }
                    }
                }
                .collect { _viewState.value = it }
        }
    }

    /**
     * The facade doesn't expose search history, but the generated queries do; `searchHistory`
     * already does prefix matching and newest-first ordering.
     */
    private fun searchHistory(prefix: String, limit: Int): Flow<List<SearchHistory>> =
        database.queries.searchHistory(prefix, limit.toLong())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { SearchHistory(id = it.id, query = it.query) } }
            .flowOn(Dispatchers.Default)

    private fun LocalSong.toYTItem(): YTItem = SongItem(
        id = id,
        title = title,
        artists = artists.map { Artist(name = it.name, id = it.id) },
        thumbnail = thumbnailUrl ?: "",
        explicit = song.explicit,
    )

    private fun LocalAlbum.toYTItem(): YTItem = AlbumItem(
        browseId = id,
        playlistId = album.playlistId ?: "",
        title = title,
        artists = artists.map { Artist(name = it.name, id = it.id) },
        year = album.year,
        thumbnail = thumbnailUrl ?: "",
        explicit = album.explicit,
    )

    private fun LocalArtist.toYTItem(): YTItem = ArtistItem(
        id = id,
        title = title,
        thumbnail = thumbnailUrl,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    private fun LocalPlaylist.toYTItem(): YTItem = PlaylistItem(
        id = id,
        title = title,
        author = Artist(name = "Local Playlist", id = null),
        songCountText = "$songCount songs",
        thumbnail = thumbnails.firstOrNull(),
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )
}

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
    val isFromLink: Boolean = false,
)

/** how many of each kind offline search shows */
private const val OfflinePreviewSize = 8
