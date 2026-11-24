// PlaybackConstants.kt

package com.example.musicfy.player

/**
 * KMP stand-ins for the Media3 `Player` constants used by the Android app.
 *
 * The numbers are deliberately identical so persisted [com.example.musicfy.models.PersistPlayerState]
 * values and ported UI comparisons keep working.
 */
object PlaybackConstants {
    const val REPEAT_MODE_OFF = 0
    const val REPEAT_MODE_ALL = 1
    const val REPEAT_MODE_ONE = 2

    const val STATE_IDLE = 1
    const val STATE_BUFFERING = 2
    const val STATE_READY = 3
    const val STATE_ENDED = 4

    /** `startSleepTimer(-1)` pauses after the current song. */
    const val SLEEP_TIMER_END_OF_SONG = -1
}
