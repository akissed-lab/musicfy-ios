package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.pages.BrowseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Genre / mood page. Route args are constructor parameters; the page and its community playlist
 * previews come from YouTube rather than the old local BrowseResult stand-in.
 */
class GenreViewModel(
    container: AppContainer,
    browseId: String,
    params: String? = null,
) : ViewModel() {
    private val browseId: String = browseId
    private val params: String? = params?.takeIf { it != "null" }

    private val _result = MutableStateFlow<BrowseResult?>(null)
    val result: StateFlow<BrowseResult?> = _result.asStateFlow()

    private val _communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val communityPlaylists: StateFlow<List<CommunityPlaylistItem>?> = _communityPlaylists.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            YouTube.browse(browseId, params)
                .onSuccess { page ->
                    _result.value = page
                    loadCommunity(page)
                }
                .onFailure { throwable ->
                    reportException(throwable)
                    _result.value = BrowseResult(title = null, items = emptyList())
                    _communityPlaylists.value = emptyList()
                }
        }
    }

    private suspend fun loadCommunity(page: BrowseResult) {
        val candidates = page.items
            .asSequence()
            .flatMap { section -> section.items.asSequence() }
            .filterIsInstance<PlaylistItem>()
            .distinctBy { it.id }
            .take(3)
            .toList()

        if (candidates.isEmpty()) {
            _communityPlaylists.value = emptyList()
            return
        }

        val resolved = coroutineScope {
            candidates.map { playlist ->
                async(Dispatchers.Default) {
                    YouTube.playlist(playlist.id).getOrNull()?.let { detail ->
                        val songs = detail.songs.take(6)
                        if (songs.isNotEmpty()) {
                            CommunityPlaylistItem(
                                playlist.copy(
                                    songCountText = detail.playlist.songCountText
                                        ?: playlist.songCountText,
                                ),
                                songs,
                            )
                        } else {
                            null
                        }
                    }
                }
            }.awaitAll()
        }.filterNotNull()
        _communityPlaylists.value = resolved
    }
}
