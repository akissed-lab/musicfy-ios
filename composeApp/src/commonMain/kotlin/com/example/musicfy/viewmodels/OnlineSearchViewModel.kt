package com.example.musicfy.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.models.ItemsPage
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.utils.reportException
import com.music.innertube.SearchFilter
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * KMP port of upstream's online search results ViewModel.
 *
 * Hilt's `SavedStateHandle` is replaced by the explicit [query] constructor argument; the result
 * screen owns URL decoding. `SettingsStore` has no reactive flow, so [refreshSettings] clears the
 * cached pages and re-runs the current request with the new visibility settings.
 */
class OnlineSearchViewModel(
    container: AppContainer,
    val query: String,
) : ViewModel() {
    private val settings = container.settings

    val filter = MutableStateFlow<SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
        private set
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    /** Surfaced by the result UI so a failed request isn't rendered as perpetual loading. */
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set

    private val settingsVersion = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(filter, settingsVersion) { activeFilter, _ -> activeFilter }
                .collect { activeFilter ->
                    errorMessage = null
                    val visibility = currentVisibility()

                    if (activeFilter == null) {
                        if (summaryPage == null) {
                            YouTube.searchSummary(query)
                                .onSuccess { page ->
                                    summaryPage = SearchSummaryPage(
                                        summaries = page.summaries.map { section ->
                                            section.copy(
                                                items = section.items
                                                    .distinctBy { it.id }
                                                    .filtered(visibility),
                                            )
                                        },
                                    )
                                }
                                .onFailure { throwable -> recordFailure(throwable) }
                        }
                    } else {
                        val key = activeFilter.value
                        if (viewStateMap[key] == null) {
                            YouTube.search(query, activeFilter)
                                .onSuccess { result ->
                                    viewStateMap[key] = ItemsPage(
                                        items = result.items
                                            .distinctBy { it.id }
                                            .filtered(visibility),
                                        continuation = result.continuation,
                                    )
                                }
                                .onFailure { throwable -> recordFailure(throwable) }
                        }
                    }
                }
        }
    }

    /** Re-requests the visible page with current hide-explicit/hide-video/hide-shorts settings. */
    fun refreshSettings() {
        summaryPage = null
        viewStateMap.clear()
        errorMessage = null
        isLoadingMore = false
        settingsVersion.value++
    }

    fun loadMore() {
        val activeFilter = filter.value ?: return
        val key = activeFilter.value
        val current = viewStateMap[key] ?: return
        val continuation = current.continuation ?: return
        if (isLoadingMore) return

        viewModelScope.launch {
            isLoadingMore = true
            errorMessage = null
            try {
                val result = YouTube.searchContinuation(continuation).getOrThrow()
                val newItems = result.items.filtered(currentVisibility())
                viewStateMap[key] = ItemsPage(
                    items = (current.items + newItems).distinctBy { it.id },
                    continuation = result.continuation,
                )
            } catch (throwable: Throwable) {
                reportException(throwable)
                errorMessage = throwable.message?.takeIf { it.isNotBlank() } ?: "Search failed"
            } finally {
                isLoadingMore = false
            }
        }
    }

    private fun currentVisibility(): SearchVisibility =
        SearchVisibility(
            hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false),
            hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false),
            hideYoutubeShorts = settings.getBoolean(SettingsKeys.HideYoutubeShortsKey, false),
        )

    private fun List<YTItem>.filtered(visibility: SearchVisibility): List<YTItem> =
        filterExplicit(visibility.hideExplicit)
            .filterVideoSongs(visibility.hideVideoSongs)
            .filterYoutubeShorts(visibility.hideYoutubeShorts)

    private fun recordFailure(throwable: Throwable) {
        reportException(throwable)
        errorMessage = throwable.message?.takeIf { it.isNotBlank() } ?: "Search failed"
    }

    private data class SearchVisibility(
        val hideExplicit: Boolean,
        val hideVideoSongs: Boolean,
        val hideYoutubeShorts: Boolean,
    )
}
