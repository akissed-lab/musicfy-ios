package com.example.musicfy

import androidx.compose.runtime.compositionLocalOf
import com.example.musicfy.player.PlayerUiState
import com.example.musicfy.viewmodels.AppContainer

/**
 * Composition locals provided once by [MusicfyApp].
 *
 * Hilt is not available in KMP, so screen-level ViewModels and glass player UI
 * read the explicit DI graph from here instead of a generated injector.
 */
val LocalAppContainer = compositionLocalOf<AppContainer?> { null }

/** Immutable projection of the shared [com.example.musicfy.player.PlaybackController]. */
val LocalPlayerUiState = compositionLocalOf<PlayerUiState?> { null }
