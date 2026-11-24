// PersistPlayerState.kt

package com.example.musicfy.models

import com.example.musicfy.util.currentTimeMillis

data class PersistPlayerState(
    val playWhenReady: Boolean,
    val repeatMode: Int,
    val shuffleModeEnabled: Boolean,
    val volume: Float,
    val currentPosition: Long,
    val currentMediaItemIndex: Int,
    val playbackState: Int,
    val timestamp: Long = currentTimeMillis(),
)
