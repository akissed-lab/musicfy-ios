package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import com.music.innertube.models.YTItem
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Local stand-in for upstream `com.music.innertube.pages.BrowseResult` (the browse page parser is
 * not part of the KMP innertube port). The field names match so ported UI keeps working.
 * TODO: replace with the real page model when `YouTube.browse` is ported.
 */
data class BrowseResult(
    val title: String?,
    val items: List<Item>,
) {
    data class Item(
        val title: String?,
        val items: List<YTItem>,
    )
}

/**
 * Upstream read `browseId`/`params` from SavedStateHandle and fetched the genre page from
 * YouTube. In the KMP port the route args are plain constructor parameters and the page is
 * unavailable, so both state flows settle on their empty values.
 */
class GenreViewModel(
    container: AppContainer,
    browseId: String,
    params: String? = null,
) : ViewModel() {
    private val browseId: String = browseId
    private val params: String? = params?.takeIf { it != "null" }

    val result = MutableStateFlow<BrowseResult?>(null)

    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)

    init {
        // TODO: YouTube.browse(browseId, params) and YouTube.playlist(id) are not ported.
        result.value = BrowseResult(title = null, items = emptyList())
        communityPlaylists.value = emptyList()
    }
}
