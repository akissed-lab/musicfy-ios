package com.example.musicfy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.musicfy.ui.screens.modules.registerExampleModuleSheets
import com.example.musicfy.ui.theme.MusicfyTheme
import com.example.musicfy.viewmodels.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

private data class StartupGraph(
    val scope: CoroutineScope,
    val container: AppContainer,
    val player: PlaybackController,
    val playerUiState: PlayerUiState,
)

private sealed interface StartupState {
    data object Loading : StartupState
    data class Ready(val graph: StartupGraph) : StartupState
    data class Failed(val error: Throwable) : StartupState
}

private val BootBackground = Color(0xFF08080C)

/**
 * Root of the iOS application. Every screen is Kotlin/Compose shared code;
 * iosApp contains only the UIViewController bootstrap.
 *
 * Native setup is split in two phases: SQLite/NSUserDefaults are opened off the
 * main thread, while AVFoundation objects are created on the main thread after
 * the first Compose frame. Both phases are wrapped in runCatching and always
 * render a readable high-contrast screen on failure, so startup problems can
 * no longer look like an empty black screen.
 */
@Composable
fun MusicfyApp() {
    // Same loader as upstream Coil 3, with the Darwin Ktor engine from iosMain.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).build()
    }

    // Registers the Compose-only example sheets once; no external module code is loaded.
    remember { registerExampleModuleSheets() }

    var startupState by remember { mutableStateOf<StartupState>(StartupState.Loading) }
    var retryToken by remember { mutableStateOf(0) }

    LaunchedEffect(retryToken) {
        startupState = StartupState.Loading
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val containerResult = withContext(Dispatchers.Default) {
            runCatching {
                AppContainer(
                    database = MusicDatabaseFacade(DatabaseDriverFactory().createDriver()),
                    settings = SettingsStore(),
                )
            }
        }

        startupState = containerResult.fold(
            onSuccess = { container ->
                // AVPlayer/AVAudioSession are main-thread UIKit-adjacent objects.
                // This code resumes on the Compose main dispatcher after withContext.
                runCatching {
                    val player = createPlaybackController(
                        scope = scope,
                        dependencies = PlaybackDependencies(
                            settings = container.settings,
                            database = container.database,
                            streamResolver = YouTubeStreamResolver(),
                        ),
                    )
                    StartupGraph(scope, container, player, PlayerUiState(player))
                }.fold(
                    onSuccess = { graph -> StartupState.Ready(graph) },
                    onFailure = { error ->
                        scope.cancel()
                        StartupState.Failed(error)
                    },
                )
            },
            onFailure = { error ->
                scope.cancel()
                StartupState.Failed(error)
            },
        )
    }

    when (val state = startupState) {
        StartupState.Loading -> BootScreen()
        is StartupState.Failed -> StartupErrorScreen(
            error = state.error,
            onRetry = { retryToken++ },
        )
        is StartupState.Ready -> {
            val (playerScope, container, player, playerUiState) = state.graph
            DisposableEffect(player, playerScope) {
                onDispose {
                    player.dispose()
                    playerScope.cancel()
                }
            }

            // The store must be provided above MusicfyTheme so theme-level preferences
            // (for example the custom font mode) can read the same SettingsStore instance.
            ProvideSettingsStore(container.settings) {
                MusicfyTheme {
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
    }
}

@Composable
private fun BootScreen() {
    Column(Modifier.fillMaxSize().background(BootBackground).padding(24.dp)) {
        Text(
            text = "Musicfy 8.5",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text("Starting…", color = Color(0xFFB9B9C6), fontSize = 14.sp)
    }
}

@Composable
private fun StartupErrorScreen(error: Throwable, onRetry: () -> Unit) {
    val details = runCatching { error.stackTraceToString() }
        .getOrDefault(error.toString())
        .take(4000)
    val summary = error.message?.takeIf { it.isNotBlank() }
        ?: error::class.simpleName
        ?: "Unknown startup error"

    Column(
        Modifier
            .fillMaxSize()
            .background(BootBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "Musicfy could not start",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(summary, color = Color(0xFFFF9A8A), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Text(details, color = Color(0xFFC9C9D4), fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA4C7E)),
        ) {
            Text("Retry", color = Color.White)
        }
    }
}
