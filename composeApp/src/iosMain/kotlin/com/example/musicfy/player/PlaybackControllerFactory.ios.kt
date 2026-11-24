// PlaybackControllerFactory.ios.kt

package com.example.musicfy.player

import kotlinx.coroutines.CoroutineScope

/** Stage 3 iOS actual: the UI talks to a real AVFoundation-backed controller. */
actual fun createPlaybackController(
    scope: CoroutineScope,
    dependencies: PlaybackDependencies,
): PlaybackController = AvFoundationPlaybackController(scope, dependencies)
