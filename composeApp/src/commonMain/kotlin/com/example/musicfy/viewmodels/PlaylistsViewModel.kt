@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.constants.PlaylistSortType
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.toEnum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * KMP port of upstream `PlaylistsViewModel`. SettingsStore is synchronous, so the two sort
 * preferences are snapshotted into flows; call [refreshSettings] after a settings screen writes
 * them. Syncing needs the unported `SyncUtils`, so [sync] is a no-op for now.
 */
class PlaylistsViewModel(container: AppContainer) : ViewModel() {
    private val database: MusicDatabaseFacade = container.database
    private val settings = container.settings

    private val sortType = MutableStateFlow(
        settings.getString(SettingsKeys.AddToPlaylistSortTypeKey, PlaylistSortType.CREATE_DATE.name)
            .toEnum(PlaylistSortType.CREATE_DATE),
    )
    private val descending = MutableStateFlow(
        settings.getBoolean(SettingsKeys.AddToPlaylistSortDescendingKey, true),
    )

    fun refreshSettings() {
        sortType.value = settings.getString(
            SettingsKeys.AddToPlaylistSortTypeKey,
            PlaylistSortType.CREATE_DATE.name,
        ).toEnum(PlaylistSortType.CREATE_DATE)
        descending.value = settings.getBoolean(SettingsKeys.AddToPlaylistSortDescendingKey, true)
    }

    val allPlaylists = combine(sortType, descending) { type, desc -> type to desc }
        .flatMapLatest { (type, desc) -> database.playlists(type.toFacadeSort(), desc) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** TODO: `SyncUtils.syncSavedPlaylists()` (YouTube account sync) is not ported. */
    suspend fun sync() = Unit

    private fun PlaylistSortType.toFacadeSort(): MusicDatabaseFacade.PlaylistSortType = when (this) {
        PlaylistSortType.CREATE_DATE -> MusicDatabaseFacade.PlaylistSortType.CREATE_DATE
        PlaylistSortType.NAME -> MusicDatabaseFacade.PlaylistSortType.NAME
        PlaylistSortType.SONG_COUNT -> MusicDatabaseFacade.PlaylistSortType.SONG_COUNT
        PlaylistSortType.LAST_UPDATED -> MusicDatabaseFacade.PlaylistSortType.LAST_UPDATED
    }
}
