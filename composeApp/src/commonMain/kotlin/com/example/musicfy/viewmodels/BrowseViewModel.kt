package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Simple browse destination: one flat list assembled from all sections of a browse page. */
class BrowseViewModel(
    container: AppContainer,
    val browseId: String?,
) : ViewModel() {

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    private val _items = MutableStateFlow<List<YTItem>?>(null)
    val items: StateFlow<List<YTItem>?> = _items.asStateFlow()

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
            YouTube.browse(browseId.orEmpty(), null)
                .onSuccess { result ->
                    _title.value = result.title
                    _items.value = result.items.flatMap { section -> section.items }
                    _loadFailed.value = false
                }
                .onFailure { throwable ->
                    reportException(throwable)
                    _loadFailed.value = true
                }
        }
    }
}
