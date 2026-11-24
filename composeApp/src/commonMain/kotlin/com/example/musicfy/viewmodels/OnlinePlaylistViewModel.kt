package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.entities.Playlist as PlaylistModel
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterVideoSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Online playlist detail backed by YouTube, with its local bookmark exposed alongside it. */
class OnlinePlaylistViewModel(
    container: AppContainer,
    val playlistId: String,
) : ViewModel() {
    private val settings = container.settings

    private val _playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlist: StateFlow<PlaylistItem?> = _playlist.asStateFlow()

    private val _playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val playlistSongs: StateFlow<List<SongItem>> = _playlistSongs.asStateFlow()

    private val _relatedItems = MutableStateFlow<List<YTItem>>(emptyList())
    val relatedItems: StateFlow<List<YTItem>> = _relatedItems.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val dbPlaylist: StateFlow<PlaylistModel?> = container.database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private var continuation: String? = null

    init {
        fetchInitialPlaylistData()
    }

    fun retry() {
        fetchInitialPlaylistData()
    }

    private fun fetchInitialPlaylistData() {
        viewModelScope.launch(Dispatchers.Default) {
            _isLoading.value = true
            _error.value = null
            continuation = null
            YouTube.playlist(playlistId)
                .onSuccess { page ->
                    _playlist.value = page.playlist
                    _playlistSongs.value = applySongFilters(page.songs)
                    _relatedItems.value = page.related.orEmpty()
                    continuation = page.songsContinuation
                    _isLoading.value = false
                }
                .onFailure { throwable ->
                    reportException(throwable)
                    _error.value = throwable.message?.takeIf { it.isNotBlank() } ?: "Failed to load playlist"
                    _isLoading.value = false
                }
        }
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return
        val token = continuation ?: return
        _isLoadingMore.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val page = YouTube.playlistContinuation(token).getOrThrow()
                _playlistSongs.value = applySongFilters(_playlistSongs.value + page.songs)
                continuation = page.continuation
            } catch (throwable: Throwable) {
                reportException(throwable)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private fun applySongFilters(songs: List<SongItem>): List<SongItem> {
        val hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false)
        return songs.distinctBy { it.id }.filterVideoSongs(hideVideoSongs)
    }
}
