package com.example.musicfy.viewmodels

import com.music.innertube.pages.ArtistPage
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tiny in-memory replacement for upstream's process-wide artist page cache.
 *
 * A StateFlow backs the map so reads and writes from coroutines on different dispatchers don't
 * need locking (commonMain has no `synchronized`). Only the most recently visited pages are kept.
 */
internal object ArtistPageCache {
    private const val MaxEntries = 12
    private val pages = MutableStateFlow<Map<String, ArtistPage>>(emptyMap())

    fun page(artistId: String): ArtistPage? = pages.value[artistId]

    fun putPage(artistId: String, page: ArtistPage) {
        pages.value = (pages.value - artistId + (artistId to page))
            .toList()
            .takeLast(MaxEntries)
            .toMap()
    }
}
