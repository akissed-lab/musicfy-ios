package com.example.musicfy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.example.musicfy.db.DatabaseDriverFactory
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.PlaybackDependencies
import com.example.musicfy.player.PlayerUiState
import com.example.musicfy.player.ResolvedStream
import com.example.musicfy.player.StreamResolver
import com.example.musicfy.player.createPlaybackController
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.settings.ProvideSettingsStore
import com.example.musicfy.settings.SettingsStore
import com.example.musicfy.ui.component.AppNavHost
import com.example.musicfy.ui.component.GlassRoot
import com.example.musicfy.ui.theme.MusicfyTheme
import com.example.musicfy.viewmodels.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Root of the iOS application. Every screen is Kotlin/Compose shared code;
 * iosApp contains only the UIViewController bootstrap.
 *
 * The whole app lives inside [GlassRoot] so every ported screen inherits the
 * Apple Music-style liquid glass backdrop, chrome and card language.
 */
@Composable
fun MusicfyApp() {
    // Same loader as upstream Coil 3, with the Darwin Ktor engine from iosMain.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).build()
    }

    val container = remember {
        AppContainer(
            database = MusicDatabaseFacade(DatabaseDriverFactory().createDriver()),
            settings = SettingsStore(),
        )
    }
    val playerScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val player = remember {
        createPlaybackController(
            scope = playerScope,
            dependencies = PlaybackDependencies(
                settings = container.settings,
                database = container.database,
                // Stream resolution lives behind WP5/server; local/direct sources work today.
                streamResolver = object : StreamResolver {
                    override suspend fun resolve(track: PlaybackTrack): ResolvedStream =
                        error("Stream resolver is not available for ${track.mediaId} yet")
                },
            ),
        )
    }
    val playerUiState = remember(player) { PlayerUiState(player) }
    DisposableEffect(player, playerScope) {
        onDispose {
            player.dispose()
            playerScope.cancel()
        }
    }

    MusicfyTheme {
        ProvideSettingsStore(container.settings) {
            CompositionLocalProvider(
                LocalPlaybackController provides player,
                LocalAppContainer provides container,
                LocalPlayerUiState provides playerUiState,
            ) {
                GlassRoot {
                    AppNavHost()
                }
            }
        }
    }
}
