package com.example.musicfy.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.db.entities.SpeedDialItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Library home screen with local data only. Upstream also depended on the import
 * service, the stream cache and YouTube artist images; those are stubbed with empty flows
 * until their KMP ports land.
 */
class LibraryHomeViewModel(container: AppContainer) : ViewModel() {
    private val database: MusicDatabaseFacade = container.database

    val pinnedItems = database.getAll()
        .map { pinned -> pinned.map { it.toYTItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songs = database.songs(MusicDatabaseFacade.SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists = database.artists(MusicDatabaseFacade.ArtistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums = database.albums(MusicDatabaseFacade.AlbumSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists = database.playlists(MusicDatabaseFacade.PlaylistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val likedSongs = database.likedSongs(MusicDatabaseFacade.SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // TODO: importer not ported; keep the banner's progress state at its empty default.
    val importProgress: StateFlow<ImportProgress> = MutableStateFlow(ImportProgress())

    // TODO: facade has no importedSourceCounts()/importedCovers(); no source cards yet.
    val importedSources: StateFlow<List<ImportedSourceCard>> = MutableStateFlow(emptyList())

    // TODO: facade has no importedPlaylists(); no playlist attribution yet.
    val playlistSources: StateFlow<Map<String, ImportSource>> = MutableStateFlow(emptyMap())

    /**
     * Downloads and imported files. TODO: facade has no onDeviceSongs(), downloadedSongs() or
     * localSongs(); derive the same slice from the library songs' isDownloaded/isLocal flags.
     */
    val local = LibraryShelf(
        source = database.songs(MusicDatabaseFacade.SongSortType.CREATE_DATE, true)
            .map { list -> list.filter { it.song.isDownloaded || it.song.isLocal } },
        libraryPlaylists = playlists,
        scope = viewModelScope,
    )

    /** TODO: stream cache not ported; the Cached tab is an empty shelf for now. */
    val cached = LibraryShelf(
        source = flowOf(emptyList()),
        libraryPlaylists = playlists,
        scope = viewModelScope,
    )

    fun shelf(kind: LibraryShelfKind): LibraryShelf = when (kind) {
        LibraryShelfKind.Local -> local
        LibraryShelfKind.Cached -> cached
    }

    // TODO: no cache to remove from until CachedSongs is ported.
    fun removeFromCache(songIds: Collection<String>) = Unit

    // TODO: no cache to clear until CachedSongs is ported.
    fun clearCache() = Unit

    fun unpin(id: String) {
        viewModelScope.launch(Dispatchers.Default) {
            database.delete(id)
        }
    }

    fun pin(item: YTItem) {
        viewModelScope.launch(Dispatchers.Default) {
            database.pinToSpeedDial(SpeedDialItem.fromYTItem(item))
        }
    }
}

/** What a service's card in the Library shows. */
@Immutable
data class ImportedSourceCard(
    val source: ImportSource,
    val songCount: Int,
    val covers: List<String>,
)

/** Placeholder for upstream `importer.ImportProgress` until the importer is ported. */
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
    val error: String? = null,
)

/** Which slice of the library a tab shows. */
enum class LibraryShelfKind(val key: String, val label: String) {
    Local("local", "Local"),
    Cached("cached", "Cached"),
}

/** Reduced local-only version of upstream `LibraryShelf` (no YouTube artist refresh). */
class LibraryShelf internal constructor(
    source: Flow<List<Song>>,
    libraryPlaylists: StateFlow<List<Playlist>>,
    scope: CoroutineScope,
) {
    val songs: StateFlow<List<Song>> = source
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val liked: StateFlow<List<Song>> = songs
        .map { list -> list.filter { it.song.liked } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<ShelfAlbum>> = songs
        .map(::groupShelfAlbums)
        .flowOnDefault()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<Artist>> = songs
        .map { list ->
            list.flatMap { it.artists }
                .filter { it.isYouTubeArtist || it.isLocal }
                .groupBy { it.id }
                .map { (_, entries) -> Artist(artist = entries.first(), songCount = entries.size) }
        }
        .flowOnDefault()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // TODO: facade has playlistIdsContainingSongs(), but filtering needs a per-playlist lookup
    // per shelf update; keep all library playlists until that flow is ported.
    val playlists: StateFlow<List<Playlist>> = libraryPlaylists
}

/** An album on the Local or Cached tab. `albumId` is null for a tag-only imported album. */
@Immutable
data class ShelfAlbum(
    val key: String,
    val albumId: String?,
    val title: String,
    val artists: String?,
    val thumbnailUrl: String?,
    val songs: List<Song>,
)

private fun groupShelfAlbums(songs: List<Song>): List<ShelfAlbum> =
    songs
        .groupBy { song ->
            song.album?.id
                ?: song.song.albumId
                ?: song.song.albumName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { "tag:$it" }
        }
        .mapNotNull { (key, group) ->
            if (key == null) return@mapNotNull null
            val first = group.first()
            ShelfAlbum(
                key = key,
                albumId = first.album?.id ?: first.song.albumId,
                title = first.album?.title ?: first.song.albumName?.trim() ?: return@mapNotNull null,
                artists = group.flatMap { it.artists }.map { it.name }.distinct()
                    .joinToString().ifBlank { null },
                thumbnailUrl = first.album?.thumbnailUrl
                    ?: group.firstNotNullOfOrNull { it.song.thumbnailUrl },
                songs = group,
            )
        }

/** Default dispatcher without importing platform-specific dispatchers. */
private fun <T> Flow<T>.flowOnDefault(): Flow<T> = flowOn(Dispatchers.Default)
