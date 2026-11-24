// LocalPlaybackController.kt

package com.example.musicfy.player

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Provided by the app root (WP0). Null until then so previews and tests that don't need playback
 * keep working.
 */
val LocalPlaybackController = staticCompositionLocalOf<PlaybackController?> { null }
