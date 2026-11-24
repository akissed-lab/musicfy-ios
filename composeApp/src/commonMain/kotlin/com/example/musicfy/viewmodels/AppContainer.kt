package com.example.musicfy.viewmodels

import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.lyrics.LyricsHelper
import com.example.musicfy.network.createPlatformHttpClient
import com.example.musicfy.settings.SettingsStore
import com.example.musicfy.utils.NetworkConnectivityObserver
import io.ktor.client.HttpClient

/**
 * KMP replacement for the upstream Hilt graph: one explicit holder constructed at the
 * composition root and passed into every plain [androidx.lifecycle.ViewModel] constructor.
 */
class AppContainer(
    val database: MusicDatabaseFacade,
    val settings: SettingsStore,
    val connectivity: NetworkConnectivityObserver = NetworkConnectivityObserver(),
    val lyricsHelper: LyricsHelper = LyricsHelper(settings, connectivity),
    platformHttpClient: HttpClient? = null,
) {
    /** Created only when a view model actually needs raw HTTP. */
    val httpClient: HttpClient = platformHttpClient ?: createPlatformHttpClient()
}
