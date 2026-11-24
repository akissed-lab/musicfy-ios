package com.example.musicfy.importer

import com.example.musicfy.db.entities.AlbumEntity
import com.example.musicfy.db.entities.ArtistEntity
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.db.entities.PlaylistSongMap
import com.example.musicfy.db.entities.SongAlbumMap
import com.example.musicfy.db.entities.SongArtistMap
import com.example.musicfy.db.entities.SongEntity
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.SettingsStore
import com.example.musicfy.util.currentTimeMillis
import com.example.musicfy.viewmodels.AppContainer
import com.example.musicfy.viewmodels.ImportSource
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.coroutineContext

/**
 * Local-only counterpart of upstream `MusicImportService`. Matching maps source metadata to
 * YouTube song ids; it does not download audio from Spotify or Apple Music and never signs in.
 */
class MusicImportService(container: AppContainer) {
    private val database = container.database
    private val settings: SettingsStore = container.settings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runningJob: Job? = null
    private var source: ImportSource = ImportSource.OTHER

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    val isRunning: Boolean get() = runningJob?.isActive == true

    /** Starts one import in the background; false if one is already running. */
    fun startImport(parsed: ParsedImport, options: ImportOptions = ImportOptions()): Boolean {
        if (isRunning) return false
        _progress.value = ImportProgress(
            isRunning = true,
            totalTracks = parsed.totalSongs,
            source = parsed.provider,
        )
        runningJob = scope.launch {
            try {
                runImport(parsed, options)
                _progress.update { it.copy(isRunning = false, isDone = true, currentLabel = "") }
            } catch (e: CancellationException) {
                _progress.update {
                    it.copy(isRunning = false, isDone = true, cancelled = true, currentLabel = "")
                }
                throw e
            } catch (e: Exception) {
                _progress.update {
                    it.copy(
                        isRunning = false,
                        isDone = true,
                        currentLabel = "",
                        error = e.message ?: "Import failed",
                    )
                }
            }
        }
        return true
    }

    /** Stops after the current song; everything already filed stays in the library. */
    fun cancel() {
        runningJob?.cancel()
    }

    /** Forgets a finished summary so the next import starts clean. */
    fun clearFinished() {
        if (!isRunning) _progress.value = ImportProgress()
    }

    private suspend fun runImport(parsed: ParsedImport, options: ImportOptions) {
        source = parsed.provider ?: ImportSource.OTHER
        val matcher = TrackMatcher.from(settings)
        // No account is available in this build, so mirroring is intentionally never attempted.
        @Suppress("UNUSED_VARIABLE")
        val mirrorRequested = options.mirrorToYouTube

        if (parsed.likedSongs.isNotEmpty()) importLooseTracks(matcher, parsed.likedSongs, "Liked songs")
        if (parsed.librarySongs.isNotEmpty()) importLooseTracks(matcher, parsed.librarySongs, "Library songs")

        for ((name, tracks) in parsed.playlists) {
            coroutineContext.ensureActive()
            importPlaylist(
                matcher = matcher,
                name = name,
                tracks = tracks,
                cover = parsed.playlistCovers[name],
                origin = parsed.playlistOrigins[name],
            )
        }
    }

    /** Source likes/library songs go straight into the local library, with no playlist. */
    private suspend fun importLooseTracks(matcher: TrackMatcher, tracks: List<ImportedTrack>, label: String) {
        val matched = matchAll(matcher, tracks, playlistName = null, label = label)
            .map { it.second }
            .distinctBy { it.id }
        if (matched.isEmpty()) return
        insertSongs(matched)
        fileIntoLibrary(matched.map { it.id })
    }

    private suspend fun importPlaylist(
        matcher: TrackMatcher,
        name: String,
        tracks: List<ImportedTrack>,
        cover: String?,
        origin: PlaylistOrigin?,
    ) {
        val existing = findExistingCopy(name, origin)
        if (existing != null) {
            if (existing.playlist.thumbnailUrl == null && cover != null) {
                database.updatePlaylistThumbnail(existing.id, cover)
            }
            val fresh = newTracks(existing.id, tracks)
            countAsDone(tracks.size - fresh.size)
            if (fresh.isNotEmpty()) topUp(matcher, existing.id, name, fresh)
            rememberTracks(existing.id, tracks, origin)
            return
        }

        val matched = matchAll(matcher, tracks, playlistName = name, label = name).map { it.second }
        val songIds = matched.map { it.id }.distinct()
        // Don't leave an empty local playlist behind when nothing could be matched.
        if (songIds.isEmpty() && tracks.isNotEmpty()) return

        val nowMs = currentTimeMillis()
        val nowLocal = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(TimeZone.UTC)
        val playlist = PlaylistEntity(
            name = name,
            bookmarkedAt = nowLocal,
            isEditable = true,
            thumbnailUrl = cover,
        )
        database.transaction {
            insert(playlist)
            recordImportedPlaylist(
                playlistId = playlist.id,
                sourceKey = source.key,
                sessionId = null,
                importedAtMs = nowMs,
                remoteId = origin?.remoteId,
                remoteUrl = origin?.remoteUrl,
                lastSyncedAtMs = nowMs,
            )
            matched.distinctBy { it.id }.forEach { insert(songEntity(it, nowLocal)) }
            insertSongMetadata(matched, nowLocal)
            songIds.forEachIndexed { index, songId ->
                insert(PlaylistSongMap(playlistId = playlist.id, songId = songId, position = index))
            }
        }
        rememberTracks(playlist.id, tracks, origin)
        fileIntoLibrary(songIds)
        _progress.update { it.copy(playlistsCreated = it.playlistsCreated + 1) }
    }

    /** A previous import of this source's playlist, found by its remote id or (fallback) name. */
    private suspend fun findExistingCopy(name: String, origin: PlaylistOrigin?): Playlist? {
        origin?.remoteId?.let { remoteId ->
            database.importedPlaylistId(source.key, remoteId)?.let { id ->
                database.playlist(id).first()?.let { return it }
            }
        }
        val candidate = database.findPlaylistByName(name) ?: return null
        val tied = database.importedPlaylistSource(candidate.id)
        if (tied != null && tied != source.key) return null
        return candidate
    }

    private fun newTracks(playlistId: String, tracks: List<ImportedTrack>): List<ImportedTrack> {
        val known = database.importedPlaylistTrackKeys(playlistId).toHashSet()
        val seen = HashSet<String>()
        return tracks.filter { track ->
            val key = track.trackKey()
            key !in known && seen.add(key)
        }
    }

    /** Adds newly found tracks after the last position, skipping ones already in the playlist. */
    private suspend fun topUp(
        matcher: TrackMatcher,
        playlistId: String,
        name: String,
        tracks: List<ImportedTrack>,
    ): List<String> {
        val matched = matchAll(matcher, tracks, playlistName = name, label = name).map { it.second }
        val songIds = matched.map { it.id }.distinct()
        if (songIds.isEmpty()) return emptyList()

        insertSongs(matched)
        val start = (database.playlistSongs(playlistId).first().maxOfOrNull { it.map.position } ?: -1) + 1
        val already = database.playlistDuplicates(playlistId, songIds).toSet()
        val added = songIds.filterNot { it in already }
        database.transaction {
            added.forEachIndexed { index, songId ->
                insert(PlaylistSongMap(playlistId = playlistId, songId = songId, position = start + index))
            }
        }
        fileIntoLibrary(songIds)
        return added
    }

    /** Saves where the playlist lives and every source track it had this time. */
    private fun rememberTracks(playlistId: String, tracks: List<ImportedTrack>, origin: PlaylistOrigin?) {
        val nowMs = currentTimeMillis()
        database.transaction {
            recordImportedPlaylist(
                playlistId = playlistId,
                sourceKey = source.key,
                sessionId = null,
                importedAtMs = nowMs,
                remoteId = origin?.remoteId,
                remoteUrl = origin?.remoteUrl,
                lastSyncedAtMs = nowMs,
            )
            tracks.map { it.trackKey() }.distinct().forEach { recordImportedPlaylistTrack(playlistId, it) }
        }
    }

    /** Inserts matched YouTube song rows; existing ids are left alone. */
    private fun insertSongs(items: List<SongItem>) {
        if (items.isEmpty()) return
        val now = Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(TimeZone.UTC)
        database.transaction {
            items.distinctBy { it.id }.forEach { insert(songEntity(it, now)) }
            insertSongMetadata(items, now)
        }
    }

    /** Keeps artist/album names in the library: the UI reads them through the join tables. */
    private fun insertSongMetadata(items: List<SongItem>, now: LocalDateTime) {
        items.flatMap { song -> song.artists }
            .distinctBy { artist -> artist.id ?: "LA_${TrackMatcher.normalizeText(artist.name)}" }
            .forEach { artist ->
                database.insert(
                    ArtistEntity(
                        id = artist.id ?: localArtistId(artist.name),
                        name = artist.name,
                        lastUpdateTime = now,
                    ),
                )
            }
        items.distinctBy { it.id }.forEach { song ->
            song.album?.let { album ->
                database.insert(
                    AlbumEntity(
                        id = album.id,
                        title = album.name,
                        thumbnailUrl = song.thumbnail,
                        songCount = 1,
                        duration = song.duration ?: 0,
                        explicit = song.explicit,
                        lastUpdateTime = now,
                        inLibrary = now,
                    ),
                )
                database.insert(SongAlbumMap(songId = song.id, albumId = album.id, index = 0))
            }
            song.artists.forEachIndexed { index, artist ->
                database.insert(
                    SongArtistMap(
                        songId = song.id,
                        artistId = artist.id ?: localArtistId(artist.name),
                        position = index,
                    ),
                )
            }
        }
    }

    private fun localArtistId(name: String): String =
        "LA_" + TrackMatcher.normalizeText(name).ifEmpty { "unknown" }

    /** Files songs into the library (the library only lists inLibrary songs) and tags the source. */
    private fun fileIntoLibrary(songIds: List<String>) {
        if (songIds.isEmpty()) return
        val nowMs = currentTimeMillis()
        database.transaction {
            songIds.distinct().forEach { id ->
                val song = getSongByIdBlocking(id) ?: return@forEach
                if (song.song.inLibrary == null) markSongInLibrary(id, nowMs)
                tagImportedSong(id, source.key, nowMs)
            }
        }
    }

    /** Matches in parallel (a few searches at a time) and keeps the source order. */
    private suspend fun matchAll(
        matcher: TrackMatcher,
        tracks: List<ImportedTrack>,
        playlistName: String?,
        label: String,
    ): List<Pair<ImportedTrack, SongItem>> = coroutineScope {
        val semaphore = Semaphore(MATCH_PARALLELISM)
        tracks.map { track ->
            async {
                semaphore.withPermit {
                    val item = try {
                        matcher.match(track)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    _progress.update { progress ->
                        progress.copy(
                            processedTracks = progress.processedTracks + 1,
                            matchedTracks = progress.matchedTracks + if (item != null) 1 else 0,
                            currentLabel = "$label — ${track.title}",
                            unmatched = if (item == null) {
                                progress.unmatched + UnmatchedTrack(track, playlistName)
                            } else {
                                progress.unmatched
                            },
                        )
                    }
                    item?.let { track to it }
                }
            }
        }.awaitAll().filterNotNull()
    }

    /** Tracks already seen on an earlier import count as done so progress still reaches the end. */
    private fun countAsDone(tracks: Int) {
        if (tracks <= 0) return
        _progress.update {
            it.copy(processedTracks = it.processedTracks + tracks, matchedTracks = it.matchedTracks + tracks)
        }
    }

    private companion object {
        const val MATCH_PARALLELISM = 4
    }
}

/** What re-sync would know a service's song by: exact for YouTube, else cleaned title and artist. */
internal fun ImportedTrack.trackKey(): String {
    (videoId ?: ytItem?.id)?.let { return "yt:$it" }
    sourceId?.let { return "src:$it" }
    val firstArtist = TrackMatcher.splitArtists(artist).firstOrNull().orEmpty()
    return "${TrackMatcher.normalizeTitle(title)}|${TrackMatcher.normalizeText(firstArtist)}"
}

private fun songEntity(item: SongItem, now: LocalDateTime): SongEntity = SongEntity(
    id = item.id,
    title = item.title,
    duration = item.duration ?: -1,
    thumbnailUrl = item.thumbnail,
    albumId = item.album?.id,
    albumName = item.album?.name,
    explicit = item.explicit,
    date = now,
    dateModified = now,
    inLibrary = now,
)
