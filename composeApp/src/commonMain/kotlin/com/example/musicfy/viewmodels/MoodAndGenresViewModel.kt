package com.example.musicfy.viewmodels

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.models.BrowseEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Local stand-in for upstream `com.music.innertube.pages.MoodAndGenres` (page parser not ported). */
data class MoodAndGenres(
    val title: String,
    val items: List<Item>,
) {
    data class Item(
        val title: String,
        val stripeColor: Long,
        val endpoint: BrowseEndpoint,
    )
}

private object MoodCoverCache {
    val covers = mutableStateMapOf<String, List<String>>()
    val requested = mutableSetOf<String>()
}

fun coverKey(browseId: String, params: String?): String = "$browseId|${params.orEmpty()}"

/**
 * Minimal functional port: YouTube's mood-and-genres browse endpoint is not available in the
 * KMP innertube port yet, so the shelves stay empty instead of crashing or using Android APIs.
 */
class MoodAndGenresViewModel : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(emptyList())

    val covers = MoodCoverCache.covers

    init {
        viewModelScope.launch {
            // TODO: replace with YouTube.moodAndGenres() once that endpoint is ported.
            // The empty list is a valid "nothing to show" state for the shelf.
            moodAndGenres.value = emptyList()
        }
    }

    fun requestCovers(browseId: String, params: String?) {
        val key = coverKey(browseId, params)
        if (!MoodCoverCache.requested.add(key)) return
        // TODO: replace with YouTube.browse(browseId, params) once the endpoint is ported.
        // Keep the key marked as requested so callers don't retry every recomposition.
    }
}
