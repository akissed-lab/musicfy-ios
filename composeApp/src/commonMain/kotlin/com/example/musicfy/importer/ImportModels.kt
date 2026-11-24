package com.example.musicfy.importer

import com.example.musicfy.viewmodels.ImportSource
import com.music.innertube.models.SongItem

/** One track as the source service lists it, before it is matched to a YouTube song. */
data class ImportedTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long? = null,
    /** Exact YouTube id when the source already knows it, so no search is needed. */
    val videoId: String? = null,
    val thumbnailUrl: String? = null,
    /** Full YouTube item when the source is YouTube itself. */
    val ytItem: SongItem? = null,
    /** When the track was added on the source, so imports keep that order. */
    val addedAtMs: Long? = null,
    /** The track's own id on the service ("spotify:track:..."). */
    val sourceId: String? = null,
)

/** What a public link (or a later re-read of it) produced. */
data class ParsedImport(
    val likedSongs: List<ImportedTrack> = emptyList(),
    val playlists: Map<String, List<ImportedTrack>> = emptyMap(),
    /** Songs that go into the library without a playlist. */
    val librarySongs: List<ImportedTrack> = emptyList(),
    /** Things the user should know before confirming, e.g. a source that only shares part of a list. */
    val warnings: List<String> = emptyList(),
    val provider: ImportSource? = null,
    /** Each playlist's own cover on the source, by its name in [playlists]. */
    val playlistCovers: Map<String, String> = emptyMap(),
    /** Where each playlist lives on the source, by its name in [playlists]. */
    val playlistOrigins: Map<String, PlaylistOrigin> = emptyMap(),
) {
    val totalSongs: Int
        get() = likedSongs.size + librarySongs.size + playlists.values.sumOf { it.size }

    val totalPlaylists: Int get() = playlists.size
}

/** Where a playlist lives on its service; either field is enough to find it again. */
data class PlaylistOrigin(
    val remoteId: String? = null,
    val remoteUrl: String? = null,
)

data class ImportOptions(
    /**
     * Kept for parity with upstream options. This KMP importer only writes the local library;
     * it never signs in to YouTube or creates playlists there.
     */
    val mirrorToYouTube: Boolean = false,
)

/** A source track that could not be matched, with its playlist so it can be found by hand. */
data class UnmatchedTrack(
    val track: ImportedTrack,
    val playlistName: String?,
)

data class ImportProgress(
    val isRunning: Boolean = false,
    val totalTracks: Int = 0,
    val processedTracks: Int = 0,
    val matchedTracks: Int = 0,
    val currentLabel: String = "",
    val isDone: Boolean = false,
    val cancelled: Boolean = false,
    val source: ImportSource? = null,
    val playlistsCreated: Int = 0,
    val likedAdded: Int = 0,
    val unmatched: List<UnmatchedTrack> = emptyList(),
    /** Names of playlists a remote mirror could not handle; unused by the local-only port. */
    val youtubeFailures: List<String> = emptyList(),
    val error: String? = null,
)
