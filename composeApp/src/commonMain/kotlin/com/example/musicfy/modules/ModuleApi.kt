// ModuleApi.kt
//
// iOS-safe subset of the module surface from upstream PR #407 (Mochy 8.5).
// Everything here is plain Kotlin / Compose Multiplatform: a module contributes
// data and composable content, and the host supplies all track, queue and sheet
// state. Nothing here loads code, scans archives, opens sockets or touches files.
//
// Intentionally NOT ported to iOS (see README.md in this folder):
// JavaScript/EEL engines, DexClassLoader/PathClassLoader/native library loading,
// zip/apk scanning and unpacking, private-network checks and sandboxed HTTP,
// secret storage/vault probing, and canonical-path file sandbox escapes.

package com.example.musicfy.modules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/** The only things an iOS module is allowed to contribute. */
enum class ModuleCapability {
    /** Timed lines, enhanced (word-by-word) LRC and translations. */
    Lyrics,

    /** Reordering/decorating a queue snapshot the host already owns. */
    Queue,

    /** Extra facts about a song derived from metadata the host already has. */
    SongInfo,

    /** Small sheets over the player, rendered by [com.example.musicfy.ui.screens.modules.ModuleSheetHost]. */
    Sheets,
}

/** Identity and declared capabilities of a module. Ids are validated before registration. */
@Immutable
data class ModuleDescriptor(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val author: String? = null,
    val sourceUrl: String? = null,
    val capabilities: Set<ModuleCapability> = emptySet(),
    val builtIn: Boolean = false,
) {
    init {
        require(SAFE_ID.matches(id)) { "Unsafe module id: $id" }
    }

    companion object {
        /** No separators, dots or traversal: safe as a key in memory and in a future store. */
        val SAFE_ID: Regex = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
    }
}

/** What the host knows about a song when it asks a module. No module fetches it itself. */
@Immutable
data class ModuleTrackQuery(
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val durationMs: Long? = null,
    /** LRC text the host already obtained from its own lyrics pipeline; may be null. */
    val rawLyrics: String? = null,
) {
    val displayArtist: String get() = artists.firstOrNull().orEmpty()
}

/** One word of a karaoke line. Times are milliseconds from the start of the song. */
@Immutable
data class ModuleLyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long? = null,
)

/** One lyric line, optionally with word-level timings and a translation. */
@Immutable
data class ModuleLyricLine(
    val startMs: Long,
    val endMs: Long? = null,
    val text: String,
    val words: List<ModuleLyricWord> = emptyList(),
    val translation: String? = null,
) {
    /** Index of the word being sung, or -1 before the first word / after the line. */
    fun activeWordIndex(positionMs: Long): Int {
        val end = endMs
        if (end != null && positionMs > end) return -1
        var found = -1
        for (i in words.indices) {
            if (words[i].startMs > positionMs) break
            found = i
        }
        return found
    }
}

/** Lines sorted by time. Modules never return raw untrusted markup to the UI. */
@Immutable
data class ModuleLyrics(
    val lines: List<ModuleLyricLine>,
    val sourceLabel: String? = null,
) {
    /** Index of the line being sung, or -1 before the first line. */
    fun activeIndex(positionMs: Long): Int {
        var found = -1
        for (i in lines.indices) {
            if (lines[i].startMs > positionMs) break
            found = i
        }
        return found
    }
}

/** Contributes lyrics from data in [ModuleTrackQuery]; network and files stay with the host. */
interface ModuleLyricsProvider {
    val moduleId: String
    suspend fun lyrics(query: ModuleTrackQuery): ModuleLyrics?
}

/** One row of a queue snapshot handed to a module by the host. */
@Immutable
data class ModuleQueueEntry(
    val uid: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val isCurrent: Boolean = false,
)

/** A read-only queue snapshot. A module cannot mutate the player's queue. */
@Immutable
data class ModuleQueueInput(
    val title: String,
    val entries: List<ModuleQueueEntry>,
)

/** What a module wants the custom queue page to display. */
@Immutable
data class ModuleQueueView(
    val title: String,
    val entries: List<ModuleQueueEntry>,
    val emptyMessage: String? = null,
)

/** Projects a host-owned queue snapshot into a page-friendly view. */
interface ModuleQueueProvider {
    val moduleId: String
    fun queue(input: ModuleQueueInput): ModuleQueueView
}

/** Extra song facts. Blank/null means "this module does not know". */
@Immutable
data class ModuleSongInfo(
    val album: String? = null,
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    val label: String? = null,
    val about: String? = null,
    val sourceLabel: String? = null,
) {
    val isEmpty: Boolean
        get() = album.isNullOrBlank() && releaseDate.isNullOrBlank() && genres.isEmpty() &&
            writers.isEmpty() && producers.isEmpty() && label.isNullOrBlank() && about.isNullOrBlank()
}

/** Contributes facts from metadata the host already has. */
interface ModuleSongInfoContributor {
    val moduleId: String
    suspend fun songInfo(query: ModuleTrackQuery): ModuleSongInfo
}

/** Scope passed to a sheet's content: identity plus a way to dismiss it. */
@Stable
class ModuleSheetScope(
    val moduleId: String,
    val sheetId: String,
    val close: () -> Unit,
)

/**
 * A small sheet contributed by a module. [content] is optional; the host draws a
 * short placeholder when a descriptor only advertises the sheet's existence.
 * Sizing is a fraction of screen height so it stays portable across devices.
 */
@Immutable
data class ModuleSheet(
    val id: String,
    val title: String,
    val moduleId: String? = null,
    val heightFraction: Float? = null,
    val showHandle: Boolean = true,
    val dim: Float = 0.45f,
    val content: (@Composable (ModuleSheetScope) -> Unit)? = null,
) {
    init {
        require(ModuleDescriptor.SAFE_ID.matches(id)) { "Unsafe sheet id: $id" }
        require(heightFraction == null || heightFraction in 0.2f..1f) { "heightFraction must be 0.2..1" }
    }
}

/**
 * One registered module: its identity plus the implementations it is allowed to
 * provide. Capabilities and provider ids are checked at registration time.
 */
@Stable
data class ModuleRegistration(
    val descriptor: ModuleDescriptor,
    val lyricsProvider: ModuleLyricsProvider? = null,
    val queueProvider: ModuleQueueProvider? = null,
    val songInfoContributor: ModuleSongInfoContributor? = null,
    val sheets: List<ModuleSheet> = emptyList(),
    val enabled: Boolean = true,
) {
    val moduleId: String get() = descriptor.id

    init {
        lyricsProvider?.let {
            require(it.moduleId == moduleId) { "Lyrics provider id mismatch for $moduleId" }
            require(ModuleCapability.Lyrics in descriptor.capabilities) { "Missing Lyrics capability for $moduleId" }
        }
        queueProvider?.let {
            require(it.moduleId == moduleId) { "Queue provider id mismatch for $moduleId" }
            require(ModuleCapability.Queue in descriptor.capabilities) { "Missing Queue capability for $moduleId" }
        }
        songInfoContributor?.let {
            require(it.moduleId == moduleId) { "Song info contributor id mismatch for $moduleId" }
            require(ModuleCapability.SongInfo in descriptor.capabilities) { "Missing SongInfo capability for $moduleId" }
        }
        if (sheets.isNotEmpty()) {
            require(ModuleCapability.Sheets in descriptor.capabilities) { "Missing Sheets capability for $moduleId" }
        }
        sheets.forEach { sheet ->
            require(sheet.moduleId == null || sheet.moduleId == moduleId) { "Sheet belongs to another module: ${sheet.id}" }
        }
    }
}
