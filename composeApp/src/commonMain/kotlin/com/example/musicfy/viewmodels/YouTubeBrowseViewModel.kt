package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.BrowseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Full browse page (genre / mood / YouTube section) with the same visibility filters as search. */
class YouTubeBrowseViewModel(
    container: AppContainer,
    val browseId: String?,
    val params: String?,
) : ViewModel() {
    private val settings = container.settings
    private val normalizedParams = params?.takeIf { it.isNotEmpty() && it != "null" }

    private val _result = MutableStateFlow<BrowseResult?>(null)
    val result: StateFlow<BrowseResult?> = _result.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _loadFailed.value = false
        load()
    }

    private fun load() {
        viewModelScope.launch {
            YouTube.browse(browseId.orEmpty(), normalizedParams)
                .onSuccess { page ->
                    _result.value = page.filtered(
                        hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false),
                        hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false),
                        hideYoutubeShorts = settings.getBoolean(SettingsKeys.HideYoutubeShortsKey, false),
                    )
                    _loadFailed.value = false
                }
                .onFailure { throwable ->
                    reportException(throwable)
                    _loadFailed.value = true
                }
        }
    }

    private fun BrowseResult.filtered(
        hideExplicit: Boolean,
        hideVideoSongs: Boolean,
        hideYoutubeShorts: Boolean,
    ): BrowseResult = copy(
        items = items.mapNotNull { section ->
            val filtered = section.items
                .filterExplicit(hideExplicit)
                .filterVideoSongs(hideVideoSongs)
                .filterYoutubeShorts(hideYoutubeShorts)
            if (filtered.isEmpty()) null else section.copy(items = filtered)
        },
    )
}
