package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.db.entities.Artist as ArtistModel
import com.example.musicfy.db.entities.Song as SongModel
import com.example.musicfy.settings.SettingsKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Local artist songs shelf, sorted and filtered by the current library settings. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistSongsViewModel(
    container: AppContainer,
    val artistId: String,
) : ViewModel() {
    private val database = container.database
    private val settings = container.settings

    val artist: StateFlow<ArtistModel?> = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val settingsVersion = MutableStateFlow(0)

    val songs: StateFlow<List<SongModel>> = settingsVersion
        .flatMapLatest {
            val sortType = runCatching {
                MusicDatabaseFacade.ArtistSongSortType.valueOf(
                    settings.getString(SettingsKeys.ArtistSongSortTypeKey, "CREATE_DATE"),
                )
            }.getOrDefault(MusicDatabaseFacade.ArtistSongSortType.CREATE_DATE)
            val descending = settings.getBoolean(SettingsKeys.ArtistSongSortDescendingKey, true)
            val hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false)
            val hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false)

            database.artistSongs(artistId, sortType, descending).map { rows ->
                rows.filter { (hideExplicit.not() || it.song.explicit.not()) &&
                    (hideVideoSongs.not() || it.song.isVideo.not()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Re-read sort/visibility preferences after they change elsewhere in the app. */
    fun refreshSettings() {
        settingsVersion.value++
    }
}
