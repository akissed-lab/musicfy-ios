package com.example.musicfy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.example.musicfy.db.DatabaseDriverFactory
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.PlaybackController
import com.example.musicfy.player.PlaybackDependencies
import com.example.musicfy.player.PlayerUiState
import com.example.musicfy.player.YouTubeStreamResolver
import com.example.musicfy.player.createPlaybackController
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

private data class StartupGraph(
    val scope: CoroutineScope,
    val container: AppContainer,
    val player: PlaybackController,
    val playerUiState: PlayerUiState,
)

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

    // Native setup (SQLite driver, AVAudioSession, HTTP engines) happens during the first
    // composition. Keep it inside runCatching so a startup failure shows a readable error
    // instead of terminating the process before the first frame.
    val startup = remember {
        runCatching {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val container = AppContainer(
                database = MusicDatabaseFacade(DatabaseDriverFactory().createDriver()),
                settings = SettingsStore(),
            )
            val player = createPlaybackController(
                scope = scope,
                dependencies = PlaybackDependencies(
                    settings = container.settings,
                    database = container.database,
                    streamResolver = YouTubeStreamResolver(),
                ),
            )
            StartupGraph(scope, container, player, PlayerUiState(player))
        }
    }

    val graph = startup.getOrNull()
    if (graph == null) {
        val details = startup.exceptionOrNull()?.stackTraceToString()?.take(4000).orEmpty()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Musicfy could not start")
            Text(details)
        }
        return
    }

    val (playerScope, container, player, playerUiState) = graph
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
