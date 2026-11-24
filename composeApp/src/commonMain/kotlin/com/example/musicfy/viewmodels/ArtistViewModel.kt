package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.entities.Album as AlbumModel
import com.example.musicfy.db.entities.Song as SongModel
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ArtistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Artist detail VM. Apple Music editorial / Android canvas extras are not part of the KMP port. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistViewModel(
    container: AppContainer,
    val artistId: String,
) : ViewModel() {
    private val database = container.database
    private val settings = container.settings

    private val _page = MutableStateFlow(ArtistPageCache.page(artistId))
    val page: StateFlow<ArtistPage?> = _page.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _subscribed = MutableStateFlow<Boolean?>(null)
    val subscribed: StateFlow<Boolean?> = _subscribed.asStateFlow()

    /** SettingsStore has no observable flow; bumping this re-reads the visibility preferences. */
    private val settingsVersion = MutableStateFlow(0)

    val librarySongs: StateFlow<List<SongModel>> = settingsVersion
        .flatMapLatest {
            val hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false)
            val hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false)
            database.artistSongsPreview(artistId).map { songs ->
                songs.filter { (hideExplicit.not() || it.song.explicit.not()) &&
                    (hideVideoSongs.not() || it.song.isVideo.not()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val libraryAlbums: StateFlow<List<AlbumModel>> = settingsVersion
        .flatMapLatest {
            val hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false)
            database.artistAlbumsPreview(artistId).map { albums ->
                if (hideExplicit) albums.filter { it.album.explicit.not() } else albums
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        load()
    }

    fun retry() {
        _loadFailed.value = false
        load()
    }

    /** Re-read hide-explicit / hide-video settings after they change on the settings screen. */
    fun refreshSettings() {
        settingsVersion.value++
    }

    private fun load() {
        viewModelScope.launch {
            val hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false)
            val hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false)
            val hideYoutubeShorts = settings.getBoolean(SettingsKeys.HideYoutubeShortsKey, false)
            YouTube.artist(artistId)
                .onSuccess { fetched ->
                    val filtered = fetched.copy(
                        sections = fetched.sections
                            .map { section ->
                                section.copy(
                                    items = section.items
                                        .filterExplicit(hideExplicit)
                                        .filterVideoSongs(hideVideoSongs)
                                        .filterYoutubeShorts(hideYoutubeShorts),
                                )
                            }
                            .filter { section -> section.items.isNotEmpty() },
                    )
                    ArtistPageCache.putPage(artistId, filtered)
                    _page.value = filtered
                    _subscribed.value = fetched.subscribed
                    _loadFailed.value = false
                }
                .onFailure { throwable ->
                    if (_page.value == null) _loadFailed.value = true
                    reportException(throwable)
                }
        }
    }

    /** Optimistically follows the channel, rolling back if YouTube refuses the request. */
    fun toggleSubscribe() {
        val channelId = _page.value?.artist?.channelId ?: return
        val target = _subscribed.value != true
        _subscribed.value = target
        viewModelScope.launch(Dispatchers.Default) {
            try {
                YouTube.subscribeChannel(channelId, target)
            } catch (throwable: Throwable) {
                _subscribed.value = !target
                reportException(throwable)
            }
        }
    }

    /** Every top song: the shelf's own page when one exists, otherwise the songs shown on the page. */
    suspend fun topSongs(): List<SongItem> {
        val section = _page.value?.sections?.firstOrNull { candidate ->
            (candidate.items.firstOrNull() as? SongItem)?.album != null
        } ?: return emptyList()
        val shown = section.items.filterIsInstance<SongItem>()
        val endpoint = section.moreEndpoint ?: return shown
        val all = withTimeoutOrNull(8_000) {
            YouTube.artistItems(endpoint).getOrNull()
        }?.items?.filterIsInstance<SongItem>()
        return all?.takeIf { it.isNotEmpty() } ?: shown
    }
}
