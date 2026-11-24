// PlaybackControllerFactory.kt

package com.example.musicfy.player

import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.models.PersistPlayerState
import com.example.musicfy.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope

/** Everything a platform controller needs; none of it is Android-specific. */
data class PlaybackDependencies(
    val settings: SettingsStore,
    val database: MusicDatabaseFacade,
    val streamResolver: StreamResolver,
    val initialState: PersistPlayerState? = null,
)

/** Implemented per platform; the iOS actual currently delegates to the in-memory fake. */
expect fun createPlaybackController(
    scope: CoroutineScope,
    dependencies: PlaybackDependencies,
): PlaybackController
