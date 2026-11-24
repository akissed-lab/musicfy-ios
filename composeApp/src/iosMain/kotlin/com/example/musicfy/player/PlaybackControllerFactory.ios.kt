// PlaybackControllerFactory.ios.kt

package com.example.musicfy.player

import com.example.musicfy.player.fake.InMemoryPlaybackController
import kotlinx.coroutines.CoroutineScope

/**
 * Stage 1/2 actual: the whole app can be ported against [PlaybackController] before any native
 * audio work is done.
 *
 * TODO(WP4 Stage 3): build `AvFoundationPlaybackController` on `AVQueuePlayer`, wire
 * `AudioSessionAndRemoteCommands`, `IosStreamResolver` (local/direct URLs only at first), and
 * return it here. Keep this in-memory delegation as the preview/fallback implementation.
 */
actual fun createPlaybackController(
    scope: CoroutineScope,
    dependencies: PlaybackDependencies,
): PlaybackController = InMemoryPlaybackController(scope, dependencies)
