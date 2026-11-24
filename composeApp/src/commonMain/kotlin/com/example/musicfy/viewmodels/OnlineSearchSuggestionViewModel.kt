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
import com.example.musicfy.utils.OfflineMode
import com.example.musicfy.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.utils.YouTubeUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * KMP port of the upstream online search suggestions screen.
 *
 * Online queries use [YouTube.searchSuggestions]; URLs are recognised by [YouTubeUrlParser] and
 * shown as a single preview row before the query is committed. Offline mode keeps searching the
 * local library. `refreshSettings()` should be called after the hide-explicit/hide-video settings
 * change (SettingsStore has no reactive flow).
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

                    when {
                        currentQuery.isEmpty() -> {
                            history.map { entries -> SearchSuggestionViewState(history = entries) }
                        }

                        OfflineMode.isOn -> {
                            val hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false)
                            val hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false)
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
                                buildList<YTItem> {
                                    visibleSongs.forEach { add(it.toYTItem()) }
                                    albums.forEach { add(it.toYTItem()) }
                                    artists.forEach { add(it.toYTItem()) }
                                    playlists.forEach { add(it.toYTItem()) }
                                }.let { items ->
                                    if (hideExplicit) items.filterExplicit() else items
                                }
                            }

                            combine(history, localItems) { entries, items ->
                                SearchSuggestionViewState(history = entries, items = items)
                            }
                        }

                        else -> {
                            val parsedUrl = YouTubeUrlParser.parse(currentQuery)
                            val parsedItem = parsedUrl?.let { parsedUrlItem(it, currentQuery) }

                            // flatMapLatest's transform is not suspend, so the network request has
                            // to live inside a Flow builder. The request is cancelled by
                            // flatMapLatest as soon as a newer query arrives.
                            val onlineResult = flow {
                                val result = if (parsedUrl == null) {
                                    YouTube.searchSuggestions(currentQuery)
                                        .onFailure { reportException(it) }
                                        .getOrNull()
                                } else {
                                    null
                                }
                                emit(result)
                            }

                            val hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false)
                            val hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false)

                            combine(history, onlineResult) { entries, result ->
                                SearchSuggestionViewState(
                                    history = entries,
                                    suggestions = result?.queries
                                        ?.filter { suggestion -> entries.none { it.query == suggestion } }
                                        .orEmpty(),
                                    items = listOfNotNull(parsedItem) + result?.recommendedItems
                                        .orEmpty()
                                        .distinctBy { it.id }
                                        .filter { it.id != parsedItem?.id }
                                        .filterExplicit(hideExplicit)
                                        .filterVideoSongs(hideVideoSongs),
                                    isFromLink = parsedUrl != null,
                                )
                            }
                        }
                    }
                }
                .collect { _viewState.value = it }
        }
    }

    /**
     * The KMP innertube port has no `YouTube.queue`/`YouTube.artist` metadata calls yet, so a link
     * preview is built from the parsed id. Clicking a song still plays that remote id.
     */
    private fun parsedUrlItem(
        parsedUrl: YouTubeUrlParser.ParsedUrl,
        rawQuery: String,
    ): YTItem? {
        val title = rawQuery.trim()
        return when (parsedUrl) {
            is YouTubeUrlParser.ParsedUrl.Video -> SongItem(
                id = parsedUrl.id,
                title = title,
                artists = emptyList(),
                thumbnail = "",
                endpoint = YouTubeUrlParser.createWatchEndpoint(rawQuery),
            )

            is YouTubeUrlParser.ParsedUrl.Artist -> ArtistItem(
                id = parsedUrl.id,
                title = title,
                thumbnail = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
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
