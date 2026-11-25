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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * KMP port of upstream `PlaylistsViewModel`. SettingsStore is synchronous, so the two sort
 * preferences are snapshotted into flows; call [refreshSettings] after a settings screen writes
 * them. Syncing needs the unported `SyncUtils`, so [sync] is a no-op for now.
 *
 * Sort changes go through [selectSortType] / [setSortDescending] so they are both reflected in
 * [allPlaylists] and persisted. As in upstream PR #368, selecting CREATE_DATE resets the
 * direction to descending; [toggleSortDirection] is the only implicit direction flip.
 */
class PlaylistsViewModel(container: AppContainer) : ViewModel() {
    private val database: MusicDatabaseFacade = container.database
    private val settings = container.settings

    private val _sortType = MutableStateFlow(
        settings.getString(SettingsKeys.AddToPlaylistSortTypeKey, PlaylistSortType.CREATE_DATE.name)
            .toEnum(PlaylistSortType.CREATE_DATE),
    )
    private val _descending = MutableStateFlow(
        settings.getBoolean(SettingsKeys.AddToPlaylistSortDescendingKey, true),
    )

    val selectedSortType: StateFlow<PlaylistSortType> = _sortType.asStateFlow()
    val sortDescending: StateFlow<Boolean> = _descending.asStateFlow()

    fun refreshSettings() {
        _sortType.value = settings.getString(
            SettingsKeys.AddToPlaylistSortTypeKey,
            PlaylistSortType.CREATE_DATE.name,
        ).toEnum(PlaylistSortType.CREATE_DATE)
        _descending.value = settings.getBoolean(SettingsKeys.AddToPlaylistSortDescendingKey, true)
    }

    /**
     * Port of upstream's `selectSortType`: date sorting starts newest-first, but a later
     * [setSortDescending] call can still switch it to ascending.
     */
    fun selectSortType(type: PlaylistSortType) {
        _sortType.value = type
        settings.setString(SettingsKeys.AddToPlaylistSortTypeKey, type.name)
        if (type == PlaylistSortType.CREATE_DATE) setSortDescending(true)
    }

    /** Explicit direction control; selection of a sort type never toggles this by itself. */
    fun toggleSortDirection() {
        setSortDescending(!_descending.value)
    }

    fun setSortDescending(descending: Boolean) {
        _descending.value = descending
        settings.setBoolean(SettingsKeys.AddToPlaylistSortDescendingKey, descending)
    }

    val allPlaylists = combine(_sortType, _descending) { type, desc -> type to desc }
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
