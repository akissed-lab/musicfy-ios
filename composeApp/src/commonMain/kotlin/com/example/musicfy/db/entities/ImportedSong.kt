package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDateTime

/**
 * A song that came into the app by an import, and from where. A song imported
 * from two services has two rows.
 *
 * Deliberately no foreign key to the song: songs are re-inserted and replaced
 * in places, and a cascade would quietly drop the record.
 */
@Immutable
data class ImportedSong(
    val songId: String,
    val source: String,
    val importedAt: LocalDateTime,
)

/** How many songs each service brought in. */
@Immutable
data class ImportedSourceCount(
    val source: String,
    val songCount: Int,
)
