// SongInfoDetails.kt
//
// Portable facts shown by "song info". The upstream 8.5 about sheet gathers the same
// facts from modules, Genius, Wikipedia, Musixmatch and YouTube; this port keeps only
// the values, so an integrator can fill them from whatever source it already has and
// no fetch happens here.

package com.example.musicfy.ui.player.songinfo

import androidx.compose.runtime.Immutable
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.TrackInfo
import com.music.innertube.models.SongItem

/** One destination in the "More" part of the sheet. Opening it is left to the caller. */
@Immutable
data class SongInfoLink(
    val label: String,
    val url: String,
)

/**
 * Everything the sheet can show about a song.
 *
 * [year] is a display string, not a number: upstream can show "2014", "Mar 2014" or a
 * full localized date. Blank values are simply left out of the sheet.
 */
@Immutable
data class SongInfoDetails(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val writers: String? = null,
    val label: String? = null,
    val about: String? = null,
    val externalLinks: List<SongInfoLink> = emptyList(),
) {
    companion object
}

/** The facts the playing track carries without a lookup: identity, artist, album and a watch link. */
fun SongInfoDetails.Companion.from(track: PlaybackTrack): SongInfoDetails = from(track.metadata)

/** Player UI state carries a lightweight [TrackInfo] rather than a full [PlaybackTrack]. */
fun SongInfoDetails.Companion.from(track: TrackInfo): SongInfoDetails = SongInfoDetails(
    title = track.title.ifBlank { "Unknown" },
    artist = track.artist.ifBlank { null },
    album = track.album.ifBlank { null },
    externalLinks = if (track.mediaId.isBlank() || track.mediaId.startsWith("LOCAL_")) {
        emptyList()
    } else {
        listOf(SongInfoLink("YouTube Music", "https://music.youtube.com/watch?v=${track.mediaId}"))
    },
)

/** Same mapping for screens that only hold [MediaMetadata] (the player controller's current song). */
fun SongInfoDetails.Companion.from(metadata: MediaMetadata): SongInfoDetails = SongInfoDetails(
    title = metadata.title.ifBlank { "Unknown" },
    artist = metadata.artists.joinToString(", ") { it.name }.ifBlank { null },
    album = metadata.album?.title,
    externalLinks = metadata.youtubeWatchLink(),
)

/** Same mapping for a search result or chart entry. */
fun SongInfoDetails.Companion.from(item: SongItem): SongInfoDetails = SongInfoDetails(
    title = item.title.ifBlank { "Unknown" },
    artist = item.artists.joinToString(", ") { it.name }.ifBlank { null },
    album = item.album?.name,
    externalLinks = listOf(SongInfoLink("YouTube Music", item.shareLink)),
)

/**
 * Adds facts gathered elsewhere to the identity mapped from a track. Only non-blank
 * values replace what is already there, so a source that answered nothing never hides
 * a value from the library.
 */
fun SongInfoDetails.withFacts(
    album: String? = null,
    year: String? = null,
    genre: String? = null,
    writers: String? = null,
    label: String? = null,
    about: String? = null,
    externalLinks: List<SongInfoLink> = emptyList(),
): SongInfoDetails = copy(
    album = album.takeUnless { it.isNullOrBlank() } ?: this.album,
    year = year.takeUnless { it.isNullOrBlank() } ?: this.year,
    genre = genre.takeUnless { it.isNullOrBlank() } ?: this.genre,
    writers = writers.takeUnless { it.isNullOrBlank() } ?: this.writers,
    label = label.takeUnless { it.isNullOrBlank() } ?: this.label,
    about = about.takeUnless { it.isNullOrBlank() } ?: this.about,
    externalLinks = if (externalLinks.isEmpty()) this.externalLinks else (this.externalLinks + externalLinks).distinctBy { it.url },
)

/** Local files have no video page to link to. */
private fun MediaMetadata.youtubeWatchLink(): List<SongInfoLink> =
    if (id.isBlank() || id.startsWith("LOCAL_") || localUri != null) {
        emptyList()
    } else {
        listOf(SongInfoLink("YouTube Music", "https://music.youtube.com/watch?v=$id"))
    }
