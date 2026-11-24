package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.entities.AlbumWithSongs
import com.example.musicfy.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * KMP port of the album detail VM. Route args are constructor parameters; the database flow is
 * shown immediately while YouTube supplies metadata, related releases and the automix id.
 */
class AlbumViewModel(
    container: AppContainer,
    val albumId: String,
) : ViewModel() {
    private val database = container.database

    private val _playlistId = MutableStateFlow("")
    val playlistId: StateFlow<String> = _playlistId.asStateFlow()

    val albumWithSongs: StateFlow<AlbumWithSongs?> = database.albumWithSongs(albumId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())
    val otherVersions: StateFlow<List<AlbumItem>> = _otherVersions.asStateFlow()

    private val _releasesForYou = MutableStateFlow<List<AlbumItem>>(emptyList())
    val releasesForYou: StateFlow<List<AlbumItem>> = _releasesForYou.asStateFlow()

    private val _description = MutableStateFlow<String?>(null)
    val description: StateFlow<String?> = _description.asStateFlow()

    private val _descriptionRuns = MutableStateFlow<List<Run>?>(null)
    val descriptionRuns: StateFlow<List<Run>?> = _descriptionRuns.asStateFlow()

    /** The main artist's other albums and singles, newest first, without this one. */
    private val _moreByArtist = MutableStateFlow<List<AlbumItem>>(emptyList())
    val moreByArtist: StateFlow<List<AlbumItem>> = _moreByArtist.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private var moreByArtistFor: String? = null

    init {
        load()
    }

    fun retry() {
        _loadFailed.value = false
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val album = database.album(albumId).first()
            album?.description?.let { _description.value = it }
            album?.album?.playlistId
                ?.takeIf { it.isNotEmpty() }
                ?.let { if (_playlistId.value.isEmpty()) _playlistId.value = it }
            album?.artists?.firstOrNull()?.id?.let(::loadMoreByArtist)

            YouTube.album(albumId)
                .onSuccess { page ->
                    _loadFailed.value = false
                    _playlistId.value = page.album.playlistId
                    if (_moreByArtist.value.isEmpty()) {
                        page.album.artists?.firstOrNull()?.id?.let(::loadMoreByArtist)
                    }
                    _otherVersions.value = page.otherVersions
                    _releasesForYou.value = page.releasesForYou
                    page.description?.let { _description.value = it }
                    _descriptionRuns.value = page.descriptionRuns
                }
                .onFailure { throwable ->
                    reportException(throwable)
                    val cached = database.albumWithSongs(albumId).first()
                    if (album == null || cached?.songs.isNullOrEmpty()) {
                        _loadFailed.value = true
                    }
                }
        }
    }

    private fun loadMoreByArtist(artistId: String) {
        if (moreByArtistFor == artistId) return
        moreByArtistFor = artistId
        viewModelScope.launch(Dispatchers.Default) {
            val page = ArtistPageCache.page(artistId)
                ?: YouTube.artist(artistId).getOrNull()?.also { ArtistPageCache.putPage(artistId, it) }
                ?: return@launch
            _moreByArtist.value = page.sections
                .filter { section -> section.items.firstOrNull() is AlbumItem }
                .flatMap { section -> section.items.filterIsInstance<AlbumItem>() }
                .filter { item -> item.browseId != albumId }
                .distinctBy { it.browseId }
                .sortedByDescending { it.year ?: 0 }
                .take(16)
        }
    }
}
