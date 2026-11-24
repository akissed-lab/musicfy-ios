package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.models.ItemsPage
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One "see all" artist shelf (songs, albums, singles) with continuation paging. */
class ArtistItemsViewModel(
    container: AppContainer,
    val artistId: String?,
    val browseId: String?,
    val params: String?,
) : ViewModel() {
    private val settings = container.settings
    private val normalizedParams = params?.takeIf { it.isNotEmpty() && it != "null" }

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _itemsPage = MutableStateFlow<ItemsPage?>(null)
    val itemsPage: StateFlow<ItemsPage?> = _itemsPage.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private var loadingMore = false

    init {
        load()
    }

    fun retry() {
        _loadFailed.value = false
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val id = browseId?.takeIf { it.isNotEmpty() && it != "null" }
            if (id == null) {
                _loadFailed.value = true
                return@launch
            }
            YouTube.artistItems(BrowseEndpoint(browseId = id, params = normalizedParams))
                .onSuccess { page ->
                    _title.value = page.title
                    _itemsPage.value = ItemsPage(
                        items = page.items.filtered(currentVisibility()),
                        continuation = page.continuation,
                    )
                }
                .onFailure { throwable ->
                    _loadFailed.value = true
                    reportException(throwable)
                }
        }
    }

    fun loadMore() {
        if (loadingMore) return
        val current = _itemsPage.value ?: return
        val continuation = current.continuation ?: return
        loadingMore = true
        viewModelScope.launch {
            try {
                val page = YouTube.artistItemsContinuation(continuation).getOrThrow()
                val visibility = currentVisibility()
                _itemsPage.update {
                    ItemsPage(
                        items = (current.items + page.items)
                            .distinctBy { it.id }
                            .filtered(visibility),
                        continuation = page.continuation,
                    )
                }
            } catch (throwable: Throwable) {
                reportException(throwable)
            } finally {
                loadingMore = false
            }
        }
    }

    private fun currentVisibility(): Visibility = Visibility(
        hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false),
        hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false),
    )

    private fun List<YTItem>.filtered(visibility: Visibility): List<YTItem> =
        distinctBy { it.id }
            .filterExplicit(visibility.hideExplicit)
            .filterVideoSongs(visibility.hideVideoSongs)

    private data class Visibility(
        val hideExplicit: Boolean,
        val hideVideoSongs: Boolean,
    )
}
