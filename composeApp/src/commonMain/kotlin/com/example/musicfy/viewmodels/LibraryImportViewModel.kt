package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.MusicDatabaseFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A storage scan for the Add music sheet. `uri` replaces Android's `android.net.Uri`. */
sealed interface DeviceScan {
    data object Idle : DeviceScan
    data object Scanning : DeviceScan
    data class Found(val songs: List<DeviceSong>) : DeviceScan
}

/** A song found on the device before it is imported. */
data class DeviceSong(
    val uri: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val artworkUri: String?,
)

// TODO: the importer package isn't ported, so these are local stand-ins for the progress batch
// until `LocalImportService`/`DownloadUtil` exist in commonMain.
enum class TransferState { Waiting, Running, Done, Failed }

data class TransferItem(
    val key: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val state: TransferState,
    val progress: Float = 0f,
    val songId: String? = null,
)

data class TransferBatch(val items: List<TransferItem> = emptyList()) {
    val isEmpty: Boolean get() = items.isEmpty()

    val isRunning: Boolean
        get() = items.any { it.state == TransferState.Waiting || it.state == TransferState.Running }

    val doneCount: Int get() = items.count { it.state == TransferState.Done }

    /** 0..1 for the whole batch; a failed song counts as finished so the bar still fills. */
    val fraction: Float
        get() {
            if (items.isEmpty()) return 0f
            val sum = items.sumOf { item ->
                when (item.state) {
                    TransferState.Done, TransferState.Failed -> 1.0
                    TransferState.Running -> item.progress.coerceIn(0f, 1f).toDouble()
                    TransferState.Waiting -> 0.0
                }
            }
            return (sum / items.size).toFloat()
        }

    val finishedSongIds: List<String>
        get() = items.mapNotNull { item -> item.songId.takeIf { item.state == TransferState.Done } }
}

/**
 * Backs the Library's import side. The importer services, Media3 download service and the local
 * file scanner are Android-only and are not ported, so progress stays empty and the mutating
 * actions are no-ops. [fillPlaylist] still works through the local database facade.
 */
class LibraryImportViewModel(container: AppContainer) : ViewModel() {
    private val database: MusicDatabaseFacade = container.database

    // TODO: no DownloadUtil/LocalImportService in KMP; both batches stay empty for now.
    val downloads: StateFlow<TransferBatch> = flowOf(TransferBatch())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferBatch())

    val localImports: StateFlow<TransferBatch> = flowOf(TransferBatch())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferBatch())

    private val _scan = MutableStateFlow<DeviceScan>(DeviceScan.Idle)
    val scan: StateFlow<DeviceScan> = _scan.asStateFlow()

    /** Looks for songs on the device that aren't in the library yet. */
    fun scanDevice() {
        if (_scan.value == DeviceScan.Scanning) return
        _scan.value = DeviceScan.Scanning
        // TODO: DeviceMusicScanner.scan() needs platform storage access.
        viewModelScope.launch(Dispatchers.Default) {
            _scan.value = DeviceScan.Found(emptyList())
        }
    }

    /** A folder the user just picked. TODO: LocalFolders.add/newAudioFiles are not ported. */
    suspend fun addFolder(uri: String): Boolean = false

    /** Re-scan saved folders and repair covers. TODO: LocalFolders/LocalArtwork are not ported. */
    suspend fun refreshLocal(): Boolean = false

    /** TODO: no persisted folder list until LocalFolders is ported. */
    fun hasFolders(): Boolean = false

    /** TODO: LocalImportService.importPicked() is not ported. */
    fun importPicked(uris: List<String>): Boolean = false

    /** TODO: LocalImportService.importDeviceSongs() is not ported. */
    fun importDeviceSongs(songs: List<DeviceSong>): Boolean = false

    /** TODO: LocalImportService.clearFinished() is not ported. */
    fun clearLocalImport() = Unit

    /** TODO: DownloadService.sendRemoveDownload() is not ported. */
    fun cancelAllDownloads() = Unit

    // ---- Manage imports ----

    /** TODO: ImportHistory is not ported and the facade has no song-delete operation. */
    fun deleteAllImports() = Unit

    /** TODO: ImportHistory is not ported. */
    fun undoLatestImport() = Unit

    /** TODO: ImportHistory is not ported. */
    fun deleteImportsFrom(source: ImportSource) = Unit

    // ---- Manage local ----

    /** TODO: ImportHistory is not ported. */
    fun undoLatestLocalImport() = Unit

    /** TODO: ImportHistory is not ported and the facade has no song-delete operation. */
    fun removeLocalSongs(songIds: Collection<String>) = Unit

    /**
     * Fills the playlist the "Create playlist" sheet just made with a finished batch. Wait for
     * the playlist row (the create sheet writes it in the background) before adding songs.
     */
    fun fillPlaylist(playlistId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            val playlist = withTimeoutOrNull(5_000) {
                database.playlist(playlistId).filterNotNull().first()
            } ?: return@launch
            database.addSongToPlaylist(playlist, songIds)
        }
    }
}
