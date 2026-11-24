package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** How much room Musicfy takes, in the four parts the Storage page shows, and the phone around it. */
data class StorageUsage(
    val deviceTotal: Long,
    val deviceFree: Long,
    /** songs streamed and kept for next time, cover pictures, and other throwaway files */
    val cache: Long,
    /** downloaded songs and the covers saved for local files */
    val downloads: Long,
    val database: Long,
    /** the app itself and its settings, everything not counted above */
    val appData: Long,
) {
    val musicfy: Long get() = cache + downloads + database + appData
    val othersUsed: Long get() = (deviceTotal - deviceFree - musicfy).coerceAtLeast(0L)
}

/** A finished download, with what it costs on the phone. */
data class DownloadedSong(
    val song: Song,
    val bytes: Long,
    /** when the download finished, ms since 1970 */
    val downloadedAt: Long,
)

/**
 * KMP port of the state surface of upstream `StorageViewModel`. Android's `StatFs`, Media3
 * downloads and Coil caches don't exist in commonMain, so sizes are derived from the database:
 * downloaded songs contribute their known content length, and the database estimate is based on
 * its row count. TODO: replace the estimates with real platform storage queries when available.
 */
class StorageViewModel(container: AppContainer) : ViewModel() {
    private val database: MusicDatabaseFacade = container.database

    private val _usage = MutableStateFlow<StorageUsage?>(null)
    val usage: StateFlow<StorageUsage?> = _usage.asStateFlow()

    /** True while something is being deleted, so the buttons can't be tapped twice. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Every finished download, newest first; the sheet sorts it however it likes. */
    val downloads: StateFlow<List<DownloadedSong>?> = database
        .songs(MusicDatabaseFacade.SongSortType.CREATE_DATE, true)
        .map { songs ->
            songs.filter { it.song.isDownloaded }
                .map { song ->
                    DownloadedSong(
                        song = song,
                        bytes = song.format?.contentLength?.coerceAtLeast(0L) ?: 0L,
                        downloadedAt = song.song.dateDownload
                            ?.toInstant(TimeZone.UTC)
                            ?.toEpochMilliseconds() ?: 0L,
                    )
                }
                .sortedByDescending { it.downloadedAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Walks the local database again. The page calls it on open and whenever downloads change. */
    fun measure() {
        viewModelScope.launch(Dispatchers.Default) { _usage.value = readUsage() }
    }

    /** TODO: the stream cache and cover cache are not ported; nothing to clear yet. */
    fun clearCaches() = runBusy { }

    /** TODO: Media3 DownloadService is not ported; downloaded song flags stay in the database. */
    fun removeAllDownloads() = runBusy { }

    /** TODO: Media3 DownloadService is not ported. */
    fun removeDownloads(songIds: Collection<String>) = runBusy { }

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                block()
            } finally {
                _usage.value = readUsage()
                _busy.value = false
            }
        }
    }

    private suspend fun readUsage(): StorageUsage {
        val songs = database.songs(MusicDatabaseFacade.SongSortType.CREATE_DATE, true).first()
        val downloadedBytes = songs.sumOf { song ->
            if (song.song.isDownloaded) song.format?.contentLength?.coerceAtLeast(0L) ?: 0L else 0L
        }
        // TODO: there is no byte-level size API in commonMain; count rows as a rough estimate.
        val rows = songs.size +
            database.albums(MusicDatabaseFacade.AlbumSortType.CREATE_DATE, true).first().size +
            database.artists(MusicDatabaseFacade.ArtistSortType.CREATE_DATE, true).first().size +
            database.playlists(MusicDatabaseFacade.PlaylistSortType.CREATE_DATE, true).first().size +
            database.getEventCount()
        return StorageUsage(
            deviceTotal = 0L, // TODO: no StatFs equivalent in commonMain.
            deviceFree = 0L,
            cache = 0L, // TODO: no stream/image cache in the KMP port.
            downloads = downloadedBytes,
            database = rows * 512L,
            appData = 0L, // TODO: APK/data-dir sizes are platform-specific.
        )
    }
}
