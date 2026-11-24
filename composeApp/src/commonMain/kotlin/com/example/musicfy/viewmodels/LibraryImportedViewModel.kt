package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.entities.Song
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * KMP port of upstream `importer.ImportSource` (that package has not been ported yet, so the
 * enum lives here until the importer lands; update both places together if it does).
 */
enum class ImportSource(val key: String, val label: String) {
    SPOTIFY("spotify", "Spotify"),
    APPLE_MUSIC("apple_music", "Apple Music"),
    TIDAL("tidal", "Tidal"),
    YOUTUBE_MUSIC("youtube_music", "YouTube Music"),
    DEEZER("deezer", "Deezer"),
    OTHER("other", "other sources");

    val isListed: Boolean get() = this != OTHER

    companion object {
        fun fromKey(key: String?): ImportSource? = entries.firstOrNull { it.key == key }
    }
}

/** Backs "Imported from ...": the songs one service brought in, whatever playlist they went into. */
class LibraryImportedViewModel(
    container: AppContainer,
    sourceKey: String?,
) : ViewModel() {
    /** Null for a route with a source this build doesn't know - the screen then has nothing to show. */
    val source: ImportSource? = ImportSource.fromKey(sourceKey)

    // TODO: facade has no importedSongsByNameAsc(); no songs are attributed to a source yet.
    val songs: StateFlow<List<Song>> = flowOf(emptyList<Song>())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
