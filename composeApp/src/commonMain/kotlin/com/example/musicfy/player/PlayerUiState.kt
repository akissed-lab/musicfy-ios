// PlayerUiState.kt

package com.example.musicfy.player

import androidx.compose.runtime.Stable
import com.example.musicfy.player.model.ProgressState
import com.example.musicfy.player.model.QueueState
import com.example.musicfy.player.model.TrackInfo
import com.example.musicfy.player.model.TransportState
import kotlinx.coroutines.flow.StateFlow

/** Thin projection of the controller for the ported player UI screens. */
@Stable
class PlayerUiState(controller: PlaybackController) {
    val transportState: StateFlow<TransportState> = controller.transportState
    val trackInfo: StateFlow<TrackInfo> = controller.trackInfo
    val queueState: StateFlow<QueueState> = controller.queueState
    /** Upstream name; the same atomic snapshot as [queueState]. */
    val queueSnapshot: StateFlow<QueueState> = controller.queueState
    val progressState: StateFlow<ProgressState> = controller.progressState
}
