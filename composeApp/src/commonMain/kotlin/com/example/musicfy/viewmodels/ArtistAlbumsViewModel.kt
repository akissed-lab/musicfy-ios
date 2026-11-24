package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.entities.Album as AlbumModel
import com.example.musicfy.db.entities.Artist as ArtistModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Local albums preview shown on the artist page. */
class ArtistAlbumsViewModel(
    container: AppContainer,
    val artistId: String,
) : ViewModel() {
    private val database = container.database

    val artist: StateFlow<ArtistModel?> = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val albums: StateFlow<List<AlbumModel>> = database.artistAlbumsPreview(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
