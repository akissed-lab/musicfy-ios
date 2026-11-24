package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.db.entities.Album
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.LocalItem
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class LocalSearchViewModel(container: AppContainer) : ViewModel() {
    private val database: MusicDatabaseFacade = container.database
    private val settings: SettingsStore = container.settings

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(LocalFilter.ALL)

    // TODO: SettingsStore is a synchronous key/value store (no DataStore reactive flow).
    // Snapshot the value here; call refreshSettings() after the setting screen writes it.
    private val hideVideoSongs = MutableStateFlow(
        settings.getBoolean(SettingsKeys.HideVideoSongsKey, false),
    )

    fun refreshSettings() {
        hideVideoSongs.value = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false)
    }

    val result = combine(query, filter, hideVideoSongs) { q, f, hide ->
        Triple(q, f, hide)
    }.flatMapLatest { (q, f, hideVideoSongs) ->
        if (q.isEmpty()) {
            flowOf(LocalSearchResult("", f, emptyMap()))
        } else {
            val results: Flow<List<LocalItem>> = when (f) {
                LocalFilter.ALL -> combine(
                    database.searchSongs(q, PREVIEW_SIZE),
                    database.searchAlbums(q, PREVIEW_SIZE),
                    database.searchArtists(q, PREVIEW_SIZE),
                    database.searchPlaylists(q, PREVIEW_SIZE),
                ) { songs, albums, artists, playlists ->
                    val filteredSongs: List<LocalItem> =
                        if (hideVideoSongs) songs.filter { !it.song.isVideo } else songs
                    filteredSongs + albums + artists + playlists
                }

                LocalFilter.SONG -> database.searchSongs(q).map { songs ->
                    if (hideVideoSongs) songs.filter { !it.song.isVideo } else songs
                }

                LocalFilter.ALBUM -> database.searchAlbums(q)
                LocalFilter.ARTIST -> database.searchArtists(q)
                LocalFilter.PLAYLIST -> database.searchPlaylists(q)
            }
            results.map { list ->
                LocalSearchResult(
                    query = q,
                    filter = f,
                    map = list.groupBy {
                        when (it) {
                            is Song -> LocalFilter.SONG
                            is Album -> LocalFilter.ALBUM
                            is Artist -> LocalFilter.ARTIST
                            is Playlist -> LocalFilter.PLAYLIST
                        }
                    },
                )
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        LocalSearchResult("", filter.value, emptyMap()),
    )

    companion object {
        const val PREVIEW_SIZE = 3
    }
}

enum class LocalFilter {
    ALL,
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST,
}

data class LocalSearchResult(
    val query: String,
    val filter: LocalFilter,
    val map: Map<LocalFilter, List<LocalItem>>,
)
