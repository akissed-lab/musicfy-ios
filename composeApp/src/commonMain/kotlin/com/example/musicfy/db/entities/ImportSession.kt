package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDateTime

/** One run of an import: everything brought in from one service in one go. */
@Immutable
data class ImportSession(
    val id: Long = 0,
    val source: String,
    val startedAt: LocalDateTime,
) {
    companion object {
        /** The source of a session that imported audio files from the phone. */
        const val DEVICE_SOURCE = "device"
    }
}

/** What one session did to one song, so Undo only reverses its own changes. */
@Immutable
data class ImportSessionSong(
    val sessionId: Long,
    val songId: String,
    val addedToLibrary: Boolean,
    val liked: Boolean,
)

/** A playlist that came from a service, so Library can count per-service imports. */
@Immutable
data class ImportedPlaylist(
    val playlistId: String,
    val source: String,
    val sessionId: Long?,
    val importedAt: LocalDateTime,
    val remoteId: String? = null,
    val remoteUrl: String? = null,
    val lastSyncedAt: LocalDateTime? = null,
)

/** A track key the service's playlist already had at the last sync. */
@Immutable
data class ImportedPlaylistTrack(
    val playlistId: String,
    val trackKey: String,
)
