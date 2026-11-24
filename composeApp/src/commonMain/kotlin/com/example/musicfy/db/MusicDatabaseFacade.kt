package com.example.musicfy.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import com.example.musicfy.db.entities.Album as AlbumModel
import com.example.musicfy.db.entities.AlbumArtistMap
import com.example.musicfy.db.entities.AlbumEntity
import com.example.musicfy.db.entities.AlbumWithSongs
import com.example.musicfy.db.entities.Artist as ArtistModel
import com.example.musicfy.db.entities.ArtistEntity
import com.example.musicfy.db.entities.Event as EventEntity
import com.example.musicfy.db.entities.EventWithSong
import com.example.musicfy.db.entities.FormatEntity
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.db.entities.Playlist as PlaylistModel
import com.example.musicfy.db.entities.PlaylistEntity
import com.example.musicfy.db.entities.PlaylistSong
import com.example.musicfy.db.entities.PlaylistSongMap
import com.example.musicfy.db.entities.SetVideoIdEntity
import com.example.musicfy.db.entities.Song as SongModel
import com.example.musicfy.db.entities.SongAlbumMap
import com.example.musicfy.db.entities.SongArtistMap
import com.example.musicfy.db.entities.SongEntity
import com.example.musicfy.db.entities.SpeedDialItem
import com.example.musicfy.util.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * KMP facade over the generated SQLDelight [MusicDatabase] / [MusicQueries].
 *
 * Deliberately not named `MusicDatabase`: SQLDelight generates that name in this package.
 * Generated flat rows [Song], [Album], [Artist], [Playlist], etc. are mapped into the
 * relation models in `db.entities`, mirroring Room's `@Relation` assembly.
 */
class MusicDatabaseFacade(
    val database: MusicDatabase,
    val driver: SqlDriver,
) {
    val queries: MusicQueries = database.musicQueries

    constructor(driver: SqlDriver) : this(MusicDatabase(driver), driver)

    /** Stand-ins for the upstream `constants.SortType` enums until that package is ported. */
    enum class SongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }

    enum class ArtistSortType { CREATE_DATE, NAME, SONG_COUNT, PLAY_TIME }

    enum class ArtistSongSortType { CREATE_DATE, NAME, PLAY_TIME }

    enum class AlbumSortType { CREATE_DATE, NAME, ARTIST, YEAR, SONG_COUNT, LENGTH, PLAY_TIME }

    enum class PlaylistSortType { CREATE_DATE, NAME, SONG_COUNT, LAST_UPDATED }

    companion object {
        const val MaxPinned = 9
    }

    // -----------------------------------------------------------------------------------------
    // Room facade plumbing
    // -----------------------------------------------------------------------------------------

    /**
     * TODO: Room posted queries on a query executor. SQLDelight executes synchronously; callers
     * that need background work should collect the returned Flow on their own dispatcher.
     */
    fun query(block: MusicDatabaseFacade.() -> Unit) {
        block(this)
    }

    fun transaction(block: MusicDatabaseFacade.() -> Unit) {
        queries.transaction { block(this@MusicDatabaseFacade) }
    }

    suspend fun withTransaction(block: suspend MusicDatabaseFacade.() -> Unit) {
        withContext(Dispatchers.Default) {
            queries.transaction {
                runBlocking { block(this@MusicDatabaseFacade) }
            }
        }
    }

    fun close() = driver.close()

    // -----------------------------------------------------------------------------------------
    // Songs
    // -----------------------------------------------------------------------------------------

    fun song(songId: String?): Flow<SongModel?> =
        if (songId == null) flowOf(null) else queries.songById(songId).asFlowOfSong()

    suspend fun getSongById(songId: String): SongModel? =
        withContext(Dispatchers.Default) { songByIdSync(songId) }

    fun getSongByIdBlocking(songId: String): SongModel? = songByIdSync(songId)

    suspend fun getSongsByIds(songIds: List<String>): List<SongModel> =
        withContext(Dispatchers.Default) { songsByIdsSync(songIds) }

    fun songsByIds(songIds: List<String>): Flow<List<SongModel>> {
        if (songIds.isEmpty()) return flowOf(emptyList())
        return queries.songsByIds(songIds)
            .asFlowOfSongs()
            .map { rows ->
                val byId = rows.associateBy { it.id }
                songIds.mapNotNull { byId[it] }
            }
            .flowOn(Dispatchers.Default)
    }

    fun songs(
        sortType: SongSortType,
        descending: Boolean,
    ): Flow<List<SongModel>> = sortedSongsFlow(
        query = when (sortType) {
            SongSortType.CREATE_DATE -> queries.songsByCreateDateAsc()
            SongSortType.NAME -> queries.songsByNameAsc()
            SongSortType.ARTIST -> queries.songsByRowIdAsc()
            SongSortType.PLAY_TIME -> queries.songsByPlayTimeAsc()
        },
        sortType = sortType,
        descending = descending,
        libraryOnly = true,
    )

    fun likedSongs(
        sortType: SongSortType,
        descending: Boolean,
    ): Flow<List<SongModel>> = sortedSongsFlow(
        query = when (sortType) {
            SongSortType.CREATE_DATE -> queries.likedSongsByCreateDateAsc()
            SongSortType.NAME -> queries.likedSongsByNameAsc()
            SongSortType.ARTIST -> queries.likedSongsByRowIdAsc()
            SongSortType.PLAY_TIME -> queries.likedSongsByPlayTimeAsc()
        },
        sortType = sortType,
        descending = descending,
        libraryOnly = false,
    )

    fun mostPlayedSongs(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = null,
    ): Flow<List<SongModel>> =
        queries.mostPlayedSongsInRange(
            fromTimeStamp,
            toTimeStamp ?: currentTimeMillis(),
            limit.toLong(),
            offset.toLong(),
        ).asFlowOfSongs()

    fun recentlyPlayedSongs(limit: Int = 15): Flow<List<SongModel>> =
        queries.recentlyPlayedSongs(limit.toLong()).asFlowOfSongs()

    fun songArtistMap(songId: String): List<SongArtistMap> =
        queries.songArtistMap(songId).executeAsList().map {
            SongArtistMap(songId = it.songId, artistId = it.artistId, position = it.position.toInt())
        }

    // -----------------------------------------------------------------------------------------
    // Albums
    // -----------------------------------------------------------------------------------------

    fun album(id: String): Flow<AlbumModel?> =
        queries.albumById(id).asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { row -> row?.let { assembleAlbums(listOf(it)).first() } }
            .flowOn(Dispatchers.Default)

    suspend fun getAlbumById(id: String): AlbumModel? =
        withContext(Dispatchers.Default) {
            queries.albumById(id).executeAsOneOrNull()?.let { assembleAlbums(listOf(it)).first() }
        }

    fun albums(
        sortType: AlbumSortType,
        descending: Boolean,
    ): Flow<List<AlbumModel>> {
        val flow = queries.albumListQuery(sortType)
            .asFlowOfAlbums()
            .map { rows ->
                val libraryAlbumIds = queries.libraryAlbumIds().executeAsList().toSet()
                rows.filter { it.id in libraryAlbumIds }
            }
        return finishAlbumList(flow, sortType, descending)
    }

    fun albumsLiked(
        sortType: AlbumSortType,
        descending: Boolean,
    ): Flow<List<AlbumModel>> {
        val flow = queries.albumListQuery(sortType)
            .asFlowOfAlbums()
            .map { rows -> rows.filter { it.album.bookmarkedAt != null } }
        return finishAlbumList(flow, sortType, descending)
    }

    fun albumsUploaded(
        sortType: AlbumSortType,
        descending: Boolean,
    ): Flow<List<AlbumModel>> {
        val flow = queries.albumListQuery(sortType)
            .asFlowOfAlbums()
            .map { rows -> rows.filter { it.album.isUploaded } }
        return finishAlbumList(flow, sortType, descending)
    }

    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?> =
        queries.albumById(albumId).asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { row ->
                row?.let {
                    val album = assembleAlbums(listOf(it)).first()
                    AlbumWithSongs(
                        album = album.album,
                        artists = album.artists,
                        songs = albumSongsSync(albumId),
                    )
                }
            }
            .flowOn(Dispatchers.Default)

    fun albumSongs(albumId: String): Flow<List<SongModel>> =
        queries.albumSongs(albumId).asFlowOfSongs()

    fun albumArtists(albumId: String): List<ArtistEntity> =
        queries.albumArtists(albumId).executeAsList().map(::mapArtist)

    // -----------------------------------------------------------------------------------------
    // Artists
    // -----------------------------------------------------------------------------------------

    fun artist(id: String): Flow<ArtistModel?> =
        queries.artistById(id).asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { row -> row?.let { assembleArtists(listOf(it)).first() } }
            .flowOn(Dispatchers.Default)

    suspend fun getArtistById(id: String): ArtistEntity? =
        withContext(Dispatchers.Default) {
            queries.artistById(id).executeAsOneOrNull()?.let(::mapArtist)
        }

    fun artistByName(name: String): ArtistEntity? =
        queries.artistByName(name).executeAsOneOrNull()?.let(::mapArtist)

    fun artists(
        sortType: ArtistSortType,
        descending: Boolean,
    ): Flow<List<ArtistModel>> = artistsFlow(queries.artistListQuery(sortType), descending) { artist ->
        artist.songCount > 0 && (artist.artist.isYouTubeArtist || artist.artist.isLocal)
    }

    fun artistsBookmarked(
        sortType: ArtistSortType,
        descending: Boolean,
    ): Flow<List<ArtistModel>> = artistsFlow(queries.artistListQuery(sortType), descending) { artist ->
        artist.artist.bookmarkedAt != null &&
            (artist.artist.isYouTubeArtist || artist.artist.isLocal)
    }

    fun artistSongs(
        artistId: String,
        sortType: ArtistSongSortType,
        descending: Boolean,
        fromTimeStamp: Long? = null,
        toTimeStamp: Long? = null,
        limit: Int = -1,
    ): Flow<List<SongModel>> {
        val base = when {
            sortType == ArtistSongSortType.PLAY_TIME && fromTimeStamp != null && toTimeStamp != null ->
                queries.mostPlayedSongsByArtist(artistId, fromTimeStamp, toTimeStamp).asFlowOfSongs()

            sortType == ArtistSongSortType.NAME -> queries.artistSongsByNameAsc(artistId).asFlowOfSongs()
            sortType == ArtistSongSortType.PLAY_TIME -> queries.artistSongsByPlayTimeAsc(artistId).asFlowOfSongs()
            else -> queries.artistSongsByCreateDateAsc(artistId).asFlowOfSongs()
        }
        return base
            .map { rows -> rows.filter { it.song.inLibrary != null } }
            .map { rows -> if (limit > 0) rows.take(limit) else rows }
            .map { rows -> sortArtistSongs(rows, sortType) }
            .map { rows -> if (descending) rows.reversed() else rows }
            .flowOn(Dispatchers.Default)
    }

    fun artistSongsPreview(
        artistId: String,
        previewSize: Int = 3,
    ): Flow<List<SongModel>> =
        queries.artistSongsPreview(artistId, previewSize.toLong())
            .asFlowOfSongs()
            .map { rows -> rows.filter { it.song.inLibrary != null } }

    /** Newest local albums for an artist, used by the artist page preview shelf. */
    fun artistAlbumsPreview(
        artistId: String,
        previewSize: Int = 6,
    ): Flow<List<AlbumModel>> =
        queries.artistAlbumsPreview(artistId, previewSize.toLong())
            .asFlowOfAlbums()

    fun mostPlayedArtists(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = null,
    ): Flow<List<ArtistModel>> =
        queries.mostPlayedArtists(
            fromTimeStamp,
            toTimeStamp ?: currentTimeMillis(),
            limit.toLong(),
            offset.toLong(),
        ) { id, name, thumbnailUrl, channelId, lastUpdateTime, bookmarkedAt, isLocal, songCount, timeListened ->
            ArtistModel(
                artist = ArtistEntity(
                    id = id,
                    name = name,
                    thumbnailUrl = thumbnailUrl,
                    channelId = channelId,
                    lastUpdateTime = epochToLocal(lastUpdateTime),
                    bookmarkedAt = epochToLocal(bookmarkedAt),
                    isLocal = isLocal == 1L,
                ),
                songCount = songCount.toInt(),
                timeListened = timeListened.toNullableInt(),
            )
        }.asFlow().mapToList(Dispatchers.Default)

    fun songArtists(songId: String): List<ArtistEntity> =
        queries.songArtists(songId).executeAsList().map(::mapArtist)

    fun songAlbum(songId: String): AlbumEntity? =
        queries.songAlbum(songId).executeAsOneOrNull()?.let(::mapAlbum)

    // -----------------------------------------------------------------------------------------
    // Playlists
    // -----------------------------------------------------------------------------------------

    fun playlist(playlistId: String): Flow<PlaylistModel?> =
        queries.playlistById(playlistId).asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { row -> row?.let { assemblePlaylists(listOf(it)).first() } }
            .flowOn(Dispatchers.Default)

    fun playlistByBrowseId(browseId: String): Flow<PlaylistModel?> =
        queries.playlistByBrowseId(browseId).asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { row -> row?.let { assemblePlaylists(listOf(it)).first() } }
            .flowOn(Dispatchers.Default)

    fun playlists(
        sortType: PlaylistSortType,
        descending: Boolean,
    ): Flow<List<PlaylistModel>> = playlistsFlow(queries.playlistListQuery(sortType), descending) {
        it.playlist.bookmarkedAt != null
    }

    fun editablePlaylists(
        sortType: PlaylistSortType,
        descending: Boolean,
    ): Flow<List<PlaylistModel>> = playlistsFlow(queries.playlistListQuery(sortType), descending) {
        it.playlist.isEditable && it.playlist.bookmarkedAt != null
    }

    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>> =
        queries.playlistSongMaps(playlistId).asFlow().mapToList(Dispatchers.Default)
            .map { maps ->
                val songs = songsByIdsSync(maps.map { it.songId }).associateBy { it.id }
                maps.mapNotNull { map ->
                    songs[map.songId]?.let { song ->
                        PlaylistSong(
                            map = PlaylistSongMap(
                                id = map.id.toInt(),
                                playlistId = map.playlistId,
                                songId = map.songId,
                                position = map.position.toInt(),
                                setVideoId = map.setVideoId,
                            ),
                            song = song,
                        )
                    }
                }
            }
            .flowOn(Dispatchers.Default)

    fun playlistSongIds(playlistId: String): Flow<List<String>> =
        queries.playlistSongIds(playlistId).asFlow().mapToList(Dispatchers.Default).flowOn(Dispatchers.Default)

    suspend fun playlistIdsContainingSongs(songIds: List<String>): List<String> =
        withContext(Dispatchers.Default) {
            if (songIds.isEmpty()) emptyList() else queries.playlistIdsContainingSongs(songIds).executeAsList()
        }

    suspend fun playlistDuplicates(
        playlistId: String,
        songIds: List<String>,
    ): List<String> = withContext(Dispatchers.Default) {
        if (songIds.isEmpty()) {
            emptyList()
        } else {
            queries.playlistDuplicates(playlistId, songIds).executeAsList()
        }
    }

    suspend fun checkInPlaylist(
        playlistId: String,
        songId: String,
    ): Int = withContext(Dispatchers.Default) {
        queries.checkInPlaylist(playlistId, songId).executeAsOne().toInt()
    }

    fun addSongToPlaylist(
        playlist: PlaylistModel,
        songIds: List<String>,
    ) {
        transaction {
            var position = playlist.songCount
            songIds.forEach { id ->
                queries.insertPlaylistSong(playlist.id, id, position.toLong(), null)
                position++
            }
        }
    }

    fun move(
        playlistId: String,
        fromPosition: Int,
        toPosition: Int,
    ) {
        queries.movePlaylistSong(
            fromPosition.toLong(),
            toPosition.toLong(),
            playlistId,
            minOf(fromPosition, toPosition).toLong(),
            maxOf(fromPosition, toPosition).toLong(),
        )
    }

    fun clearPlaylist(playlistId: String) {
        queries.clearPlaylist(playlistId)
    }

    fun removeAddedSongs(
        playlistId: String,
        songIds: List<String>,
        fromPosition: Int,
    ) {
        if (songIds.isEmpty()) return
        queries.removeAddedSongs(playlistId, songIds, fromPosition.toLong())
    }

    fun bookmarkPlaylist(
        playlistId: String,
        bookmarkedAt: LocalDateTime?,
    ) {
        queries.bookmarkPlaylist(localToEpoch(bookmarkedAt), playlistId)
    }

    // -----------------------------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------------------------

    fun searchSongs(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<SongModel>> = queries.searchSongs(query, previewSize.toLong())
        .asFlowOfSongs()
        .map { rows -> rows.filter { it.song.inLibrary != null } }

    fun searchArtists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<ArtistModel>> = queries.searchArtists(query, previewSize.toLong())
        .asFlowOfArtists()
        .map { rows -> rows.filter { it.songCount > 0 } }

    fun searchAlbums(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<AlbumModel>> = queries.searchAlbums(query, previewSize.toLong())
        .asFlowOfAlbums()
        .map { rows ->
            val libraryAlbumIds = queries.libraryAlbumIds().executeAsList().toSet()
            rows.filter { it.id in libraryAlbumIds }
        }

    fun searchPlaylists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<PlaylistModel>> = queries.searchPlaylists(query, previewSize.toLong()).asFlowOfPlaylists()

    // -----------------------------------------------------------------------------------------
    // Format, lyrics and set-video-id
    // -----------------------------------------------------------------------------------------

    fun format(id: String?): Flow<FormatEntity?> =
        if (id == null) {
            flowOf(null)
        } else {
            queries.formatById(id).asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.let(::mapFormat) }
        }

    fun lyrics(id: String?): Flow<LyricsEntity?> =
        if (id == null) {
            flowOf(null)
        } else {
            queries.lyricsById(id).asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.let(::mapLyrics) }
        }

    suspend fun getSetVideoId(videoId: String): SetVideoIdEntity? =
        withContext(Dispatchers.Default) {
            queries.setVideoIdByVideoId(videoId).executeAsOneOrNull()?.let(::mapSetVideoId)
        }

    fun setLyricsOffset(
        songId: String,
        offset: Int,
    ) {
        queries.updateLyricsOffset(offset.toLong(), songId)
    }

    fun upsert(lyrics: LyricsEntity) {
        queries.insertLyrics(
            lyrics.id,
            lyrics.lyrics,
            lyrics.provider,
            lyrics.translatedLyrics,
            lyrics.translationLanguage,
            lyrics.translationMode,
        )
    }

    fun upsert(format: FormatEntity) {
        queries.upsertFormat(
            format.id,
            format.itag.toLong(),
            format.mimeType,
            format.codecs,
            format.bitrate.toLong(),
            format.sampleRate?.toLong(),
            format.contentLength,
            format.loudnessDb,
            format.perceptualLoudnessDb,
            format.playbackUrl,
        )
    }

    fun insert(setVideoId: SetVideoIdEntity) {
        queries.insertSetVideoId(setVideoId.videoId, setVideoId.setVideoId)
    }

    // -----------------------------------------------------------------------------------------
    // Events and stats
    // -----------------------------------------------------------------------------------------

    fun events(): Flow<List<EventWithSong>> = queries.events().asFlowOfEvents()

    fun recentEvents(limit: Int): Flow<List<EventWithSong>> =
        queries.recentEventsLimited(limit.toLong()).asFlowOfEvents()

    fun firstEvent(): Flow<EventWithSong?> =
        queries.firstEvent().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { row -> row?.let { assembleEvents(listOf(it)).firstOrNull() } }
            .flowOn(Dispatchers.Default)

    fun eventCount(): Flow<Int> =
        queries.eventCount().asFlow().mapToOne(Dispatchers.Default).map { it.toInt() }

    suspend fun getEventCount(): Int =
        withContext(Dispatchers.Default) { queries.eventCount().executeAsOne().toInt() }

    suspend fun firstEventTimestamp(): Long? =
        withContext(Dispatchers.Default) { queries.firstEventTimestamp().executeAsOneOrNull()?.MIN }

    suspend fun recapEvents(
        fromTimeStamp: Long,
        toTimeStamp: Long,
    ): List<RecapEventRow> = withContext(Dispatchers.Default) {
        queries.recapEventsRange(fromTimeStamp, toTimeStamp) { songId, timestamp, playTime ->
            RecapEventRow(songId, timestamp, playTime)
        }.executeAsList()
    }

    fun incrementTotalPlayTime(
        songId: String,
        playTime: Long,
    ) {
        queries.incrementTotalPlayTime(playTime, songId)
    }

    fun clearListenHistory() {
        queries.clearListenHistory()
    }

    // -----------------------------------------------------------------------------------------
    // Speed dial (SpeedDialDao parity + the central 9-item cap)
    // -----------------------------------------------------------------------------------------

    fun getAll(): Flow<List<SpeedDialItem>> =
        queries.speedDialItemsOrdered().asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map(::mapSpeedDial) }
            .flowOn(Dispatchers.Default)

    suspend fun oldest(): SpeedDialItem? =
        withContext(Dispatchers.Default) {
            queries.oldestSpeedDialItem().executeAsOneOrNull()?.let(::mapSpeedDial)
        }

    suspend fun count(): Int =
        withContext(Dispatchers.Default) { queries.countSpeedDialItems().executeAsOne().toInt() }

    suspend fun insert(item: SpeedDialItem) =
        withContext(Dispatchers.Default) { insertSpeedDialRow(item) }

    suspend fun delete(id: String) =
        withContext(Dispatchers.Default) { queries.deleteSpeedDialItem(id) }

    fun isPinned(id: String): Flow<Boolean> =
        queries.isSpeedDialPinned(id).asFlow().mapToOne(Dispatchers.Default).map { it == true }

    /**
     * The only pin entry point. The 3x3 grid has room for nine items; evict the oldest first.
     */
    suspend fun pinToSpeedDial(item: SpeedDialItem) {
        withContext(Dispatchers.Default) {
            // Keep the whole check/evict/insert sequence in one transaction; calling the
            // suspend count()/oldest()/insert() helpers here would hop dispatchers mid-transaction.
            queries.transaction {
                if (queries.countSpeedDialItems().executeAsOne() >= MaxPinned.toLong()) {
                    queries.oldestSpeedDialItem().executeAsOneOrNull()?.let { queries.deleteSpeedDialItem(it.id) }
                }
                insertSpeedDialRow(item)
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Entity writes (insert/update parity with Room)
    // -----------------------------------------------------------------------------------------

    // TODO: Room's @Delete overloads and the import/recognition, playCount, related_song_map and
    // playlist_event tables have no facade operations in this stage. The signatures above cover
    // the insert/update paths used by the current UI; deletes can be added once a call site needs
    // them (and should go through queries.transaction, like addSongToPlaylist).

    fun insert(song: SongEntity) {
        queries.insertSong(
            song.id,
            song.title,
            song.duration.toLong(),
            song.thumbnailUrl,
            song.albumId,
            song.albumName,
            song.explicit.asDbLong(),
            song.year?.toLong(),
            localToEpoch(song.date),
            localToEpoch(song.dateModified),
            song.liked.asDbLong(),
            localToEpoch(song.likedDate),
            song.totalPlayTime,
            localToEpoch(song.inLibrary),
            localToEpoch(song.dateDownload),
            song.isLocal.asDbLong(),
            song.libraryAddToken,
            song.libraryRemoveToken,
            song.lyricsOffset.toLong(),
            song.romanizeLyrics.asDbLong(),
            song.isDownloaded.asDbLong(),
            song.isUploaded.asDbLong(),
            song.isVideo.asDbLong(),
            song.localUri,
        )
    }

    fun upsert(song: SongEntity) {
        queries.upsertSong(
            song.id,
            song.title,
            song.duration.toLong(),
            song.thumbnailUrl,
            song.albumId,
            song.albumName,
            song.explicit.asDbLong(),
            song.year?.toLong(),
            localToEpoch(song.date),
            localToEpoch(song.dateModified),
            song.liked.asDbLong(),
            localToEpoch(song.likedDate),
            song.totalPlayTime,
            localToEpoch(song.inLibrary),
            localToEpoch(song.dateDownload),
            song.isLocal.asDbLong(),
            song.libraryAddToken,
            song.libraryRemoveToken,
            song.lyricsOffset.toLong(),
            song.romanizeLyrics.asDbLong(),
            song.isDownloaded.asDbLong(),
            song.isUploaded.asDbLong(),
            song.isVideo.asDbLong(),
            song.localUri,
        )
    }

    fun insert(artist: ArtistEntity) {
        queries.insertArtistEntity(
            artist.id,
            artist.name,
            artist.thumbnailUrl,
            artist.channelId,
            localToEpoch(artist.lastUpdateTime)!!,
            localToEpoch(artist.bookmarkedAt),
            artist.isLocal.asDbLong(),
        )
    }

    fun insert(album: AlbumEntity) {
        queries.insertAlbumEntity(
            album.id,
            album.playlistId,
            album.title,
            album.year?.toLong(),
            album.thumbnailUrl,
            album.themeColor?.toLong(),
            album.songCount.toLong(),
            album.duration.toLong(),
            album.explicit.asDbLong(),
            localToEpoch(album.lastUpdateTime)!!,
            localToEpoch(album.bookmarkedAt),
            localToEpoch(album.likedDate),
            localToEpoch(album.inLibrary),
            album.description,
            album.isLocal.asDbLong(),
            album.isUploaded.asDbLong(),
        )
    }

    fun insert(playlist: PlaylistEntity) {
        queries.insertPlaylistEntity(
            playlist.id,
            playlist.name,
            playlist.browseId,
            localToEpoch(playlist.createdAt),
            localToEpoch(playlist.lastUpdateTime),
            playlist.isEditable.asDbLong(),
            localToEpoch(playlist.bookmarkedAt),
            playlist.remoteSongCount?.toLong(),
            playlist.playEndpointParams,
            playlist.thumbnailUrl,
            playlist.shuffleEndpointParams,
            playlist.radioEndpointParams,
            playlist.isLocal.asDbLong(),
            playlist.isAutoSync.asDbLong(),
            playlist.description,
        )
    }

    fun insert(map: SongArtistMap) {
        queries.insertSongArtistMap(map.songId, map.artistId, map.position.toLong())
    }

    fun insert(map: SongAlbumMap) {
        queries.insertSongAlbumMap(map.songId, map.albumId, map.index.toLong())
    }

    fun insert(map: AlbumArtistMap) {
        queries.insertAlbumArtistMap(map.albumId, map.artistId, map.order.toLong())
    }

    fun insert(map: PlaylistSongMap) {
        queries.insertPlaylistSong(map.playlistId, map.songId, map.position.toLong(), map.setVideoId)
    }

    fun updateLike(song: SongEntity) {
        queries.updateLike(song.liked.asDbLong(), localToEpoch(song.likedDate), song.id)
    }

    fun updateLibrary(song: SongEntity) {
        queries.updateLibrary(
            localToEpoch(song.inLibrary),
            song.liked.asDbLong(),
            localToEpoch(song.likedDate),
            song.id,
        )
    }

    fun updateDownloaded(song: SongEntity) {
        queries.updateDownloaded(song.isDownloaded.asDbLong(), localToEpoch(song.dateDownload), song.id)
    }

    // -----------------------------------------------------------------------------------------
    // Public-link importer writes (no new tables; imported_* tables are declared in Music.sq)
    // -----------------------------------------------------------------------------------------

    /** Marks a matched YouTube song as being in the library. */
    fun markSongInLibrary(songId: String, atMs: Long) {
        queries.markSongInLibrary(atMs, atMs, songId)
    }

    /** Records which service brought a song in, for the "Imported from ..." shelves. */
    fun tagImportedSong(songId: String, sourceKey: String, importedAtMs: Long) {
        queries.insertImportedSongTag(songId, sourceKey, importedAtMs)
    }

    /** Finds a local, editable copy by the name the source gave the playlist. */
    fun findPlaylistByName(name: String): PlaylistModel? =
        queries.playlistByName(name).executeAsOneOrNull()?.let { assemblePlaylists(listOf(it)).firstOrNull() }

    fun importedPlaylistId(sourceKey: String, remoteId: String): String? =
        queries.importedPlaylistByRemoteId(sourceKey, remoteId).executeAsOneOrNull()?.playlistId

    fun importedPlaylistSource(playlistId: String): String? =
        queries.importedPlaylistSourceByPlaylistId(playlistId).executeAsOneOrNull()

    fun recordImportedPlaylist(
        playlistId: String,
        sourceKey: String,
        sessionId: Long?,
        importedAtMs: Long,
        remoteId: String?,
        remoteUrl: String?,
        lastSyncedAtMs: Long?,
    ) {
        queries.insertImportedPlaylistRecord(
            playlistId,
            sourceKey,
            sessionId,
            importedAtMs,
            remoteId,
            remoteUrl,
            lastSyncedAtMs,
        )
    }

    fun importedPlaylistTrackKeys(playlistId: String): List<String> =
        queries.importedPlaylistTrackKeys(playlistId).executeAsList()

    fun recordImportedPlaylistTrack(playlistId: String, trackKey: String) {
        queries.insertImportedPlaylistTrack(playlistId, trackKey)
    }

    fun updatePlaylistThumbnail(playlistId: String, thumbnailUrl: String) {
        queries.updatePlaylistThumbnail(thumbnailUrl, playlistId)
    }

    // -----------------------------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------------------------

    private fun insertSpeedDialRow(item: SpeedDialItem) {
        queries.insertSpeedDialItem(
            item.id,
            item.secondaryId,
            item.title,
            item.subtitle,
            item.thumbnailUrl,
            item.type,
            item.explicit.asDbLong(),
            item.createDate,
        )
    }

    private fun MusicQueries.albumListQuery(sortType: AlbumSortType): Query<Album> =
        when (sortType) {
            AlbumSortType.CREATE_DATE, AlbumSortType.ARTIST -> queries.albumsByCreateDateAsc()
            AlbumSortType.NAME -> queries.albumsByNameAsc()
            AlbumSortType.YEAR -> queries.albumsByYearAsc()
            AlbumSortType.SONG_COUNT -> queries.albumsBySongCountAsc()
            AlbumSortType.LENGTH -> queries.albumsByLengthAsc()
            AlbumSortType.PLAY_TIME -> queries.albumsByPlayTimeAsc()
        }

    private fun finishAlbumList(
        flow: Flow<List<AlbumModel>>,
        sortType: AlbumSortType,
        descending: Boolean,
    ): Flow<List<AlbumModel>> = flow
        .map { rows -> if (sortType == AlbumSortType.ARTIST) sortAlbumsByArtist(rows) else rows }
        .map { rows -> if (descending) rows.reversed() else rows }
        .flowOn(Dispatchers.Default)

    private fun MusicQueries.artistListQuery(sortType: ArtistSortType): Query<Artist> =
        when (sortType) {
            ArtistSortType.CREATE_DATE -> queries.artistsByCreateDateAsc()
            ArtistSortType.NAME -> queries.artistsByNameAsc()
            ArtistSortType.SONG_COUNT -> queries.artistsBySongCountAsc()
            ArtistSortType.PLAY_TIME -> queries.artistsByPlayTimeAsc()
        }

    private fun artistsFlow(
        query: Query<Artist>,
        descending: Boolean,
        filter: (ArtistModel) -> Boolean,
    ): Flow<List<ArtistModel>> = query.asFlowOfArtists()
        .map { rows -> rows.filter(filter) }
        .map { rows -> if (descending) rows.reversed() else rows }
        .flowOn(Dispatchers.Default)

    private fun MusicQueries.playlistListQuery(sortType: PlaylistSortType): Query<Playlist> =
        when (sortType) {
            PlaylistSortType.CREATE_DATE -> queries.playlistsByCreateDateAsc()
            PlaylistSortType.NAME -> queries.playlistsByNameAsc()
            PlaylistSortType.SONG_COUNT -> queries.playlistsBySongCountAsc()
            PlaylistSortType.LAST_UPDATED -> queries.playlistsByUpdatedDateAsc()
        }

    private fun playlistsFlow(
        query: Query<Playlist>,
        descending: Boolean,
        filter: (PlaylistModel) -> Boolean,
    ): Flow<List<PlaylistModel>> = query.asFlowOfPlaylists()
        .map { rows -> rows.filter(filter) }
        .map { rows -> if (descending) rows.reversed() else rows }
        .flowOn(Dispatchers.Default)

    private fun sortedSongsFlow(
        query: Query<Song>,
        sortType: SongSortType,
        descending: Boolean,
        libraryOnly: Boolean,
    ): Flow<List<SongModel>> {
        var flow = query.asFlowOfSongs()
        if (libraryOnly) {
            flow = flow.map { rows -> rows.filter { it.song.inLibrary != null } }
        }
        return flow
            .map { rows -> sortSongs(rows, sortType) }
            .map { rows -> if (descending) rows.reversed() else rows }
            .flowOn(Dispatchers.Default)
    }

    /**
     * TODO: upstream uses a locale-aware Collator and groups ARTIST results by album title before
     * sorting album artists. commonMain has no Collator yet, so this uses case-folded comparison.
     */
    private fun sortSongs(
        rows: List<SongModel>,
        sortType: SongSortType,
    ): List<SongModel> = when (sortType) {
        SongSortType.CREATE_DATE, SongSortType.PLAY_TIME -> rows
        SongSortType.NAME -> rows.sortedBy { it.song.title.lowercase() }
        SongSortType.ARTIST -> rows.sortedBy { it.artists.joinToString("") { artist -> artist.name }.lowercase() }
    }

    private fun sortAlbumsByArtist(rows: List<AlbumModel>): List<AlbumModel> =
        rows.sortedBy { it.artists.joinToString("") { artist -> artist.name }.lowercase() }

    private fun sortArtistSongs(
        rows: List<SongModel>,
        sortType: ArtistSongSortType,
    ): List<SongModel> = when (sortType) {
        ArtistSongSortType.CREATE_DATE, ArtistSongSortType.PLAY_TIME -> rows
        ArtistSongSortType.NAME -> rows.sortedBy { it.song.title.lowercase() }
    }

    private fun Query<Song>.asFlowOfSongs(): Flow<List<SongModel>> =
        asFlow().mapToList(Dispatchers.Default)
            .map { rows -> assembleSongs(rows) }
            .flowOn(Dispatchers.Default)

    private fun Query<Song>.asFlowOfSong(): Flow<SongModel?> =
        asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { row -> row?.let { assembleSongs(listOf(it)).first() } }
            .flowOn(Dispatchers.Default)

    private fun Query<Album>.asFlowOfAlbums(): Flow<List<AlbumModel>> =
        asFlow().mapToList(Dispatchers.Default)
            .map { rows -> assembleAlbums(rows) }
            .flowOn(Dispatchers.Default)

    private fun Query<Artist>.asFlowOfArtists(): Flow<List<ArtistModel>> =
        asFlow().mapToList(Dispatchers.Default)
            .map { rows -> assembleArtists(rows) }
            .flowOn(Dispatchers.Default)

    private fun Query<Playlist>.asFlowOfPlaylists(): Flow<List<PlaylistModel>> =
        asFlow().mapToList(Dispatchers.Default)
            .map { rows -> assemblePlaylists(rows) }
            .flowOn(Dispatchers.Default)

    private fun Query<Event>.asFlowOfEvents(): Flow<List<EventWithSong>> =
        asFlow().mapToList(Dispatchers.Default)
            .map { rows -> assembleEvents(rows) }
            .flowOn(Dispatchers.Default)

    private fun songByIdSync(songId: String): SongModel? =
        queries.songById(songId).executeAsOneOrNull()?.let { assembleSongs(listOf(it)).first() }

    private fun songsByIdsSync(songIds: List<String>): List<SongModel> {
        if (songIds.isEmpty()) return emptyList()
        val byId = assembleSongs(queries.songsByIds(songIds).executeAsList()).associateBy { it.id }
        return songIds.mapNotNull { byId[it] }
    }

    private fun albumSongsSync(albumId: String): List<SongModel> =
        assembleSongs(queries.albumSongs(albumId).executeAsList())

    private fun assembleSongs(rows: List<Song>): List<SongModel> {
        if (rows.isEmpty()) return emptyList()
        val songEntities = rows.map(::mapSong)
        val songIds = songEntities.map { it.id }

        val artistsBySong = queries.songArtistsForSongIds(songIds) {
                songId, id, name, thumbnailUrl, channelId, lastUpdateTime, bookmarkedAt, isLocal ->
            songId to ArtistEntity(
                id = id,
                name = name,
                thumbnailUrl = thumbnailUrl,
                channelId = channelId,
                lastUpdateTime = epochToLocal(lastUpdateTime),
                bookmarkedAt = epochToLocal(bookmarkedAt),
                isLocal = isLocal == 1L,
            )
        }.executeAsList().groupBy({ it.first }, { it.second })

        val albumsBySong = queries.songAlbumsForSongIds(songIds) {
                songId, id, playlistId, title, year, thumbnailUrl, themeColor, songCount,
                duration, explicit, lastUpdateTime, bookmarkedAt, likedDate, inLibrary,
                description, isLocal, isUploaded ->
            songId to AlbumEntity(
                id = id,
                playlistId = playlistId,
                title = title,
                year = year?.toInt(),
                thumbnailUrl = thumbnailUrl,
                themeColor = themeColor?.toInt(),
                songCount = songCount.toInt(),
                duration = duration.toInt(),
                explicit = explicit == 1L,
                lastUpdateTime = epochToLocal(lastUpdateTime),
                bookmarkedAt = epochToLocal(bookmarkedAt),
                likedDate = epochToLocal(likedDate),
                inLibrary = epochToLocal(inLibrary),
                description = description,
                isLocal = isLocal == 1L,
                isUploaded = isUploaded == 1L,
            )
        }.executeAsList().groupBy({ it.first }, { it.second })
            .mapValues { (_, albums) -> albums.firstOrNull() }

        val formatsById = queries.formatsByIds(songIds).executeAsList()
            .associateBy({ it.id }, ::mapFormat)

        return songEntities.map { song ->
            SongModel(
                song = song,
                artists = artistsBySong[song.id].orEmpty(),
                album = albumsBySong[song.id],
                format = formatsById[song.id],
            )
        }
    }

    private fun assembleAlbums(rows: List<Album>): List<AlbumModel> {
        if (rows.isEmpty()) return emptyList()
        val albumEntities = rows.map(::mapAlbum)
        val artistsByAlbum = queries.albumArtistsForAlbumIds(albumEntities.map { it.id }) {
                albumId, id, name, thumbnailUrl, channelId, lastUpdateTime, bookmarkedAt, isLocal ->
            albumId to ArtistEntity(
                id = id,
                name = name,
                thumbnailUrl = thumbnailUrl,
                channelId = channelId,
                lastUpdateTime = epochToLocal(lastUpdateTime),
                bookmarkedAt = epochToLocal(bookmarkedAt),
                isLocal = isLocal == 1L,
            )
        }.executeAsList().groupBy({ it.first }, { it.second })

        return albumEntities.map { album ->
            AlbumModel(album = album, artists = artistsByAlbum[album.id].orEmpty())
        }
    }

    private fun assembleArtists(rows: List<Artist>): List<ArtistModel> {
        if (rows.isEmpty()) return emptyList()
        val counts = queries.artistLibrarySongCounts { artistId, songCount ->
            artistId to songCount.toInt()
        }.executeAsList().toMap()
        return rows.map { artist ->
            ArtistModel(artist = mapArtist(artist), songCount = counts[artist.id] ?: 0)
        }
    }

    private fun assemblePlaylists(rows: List<Playlist>): List<PlaylistModel> {
        if (rows.isEmpty()) return emptyList()
        val playlistEntities = rows.map(::mapPlaylist)
        val ids = playlistEntities.map { it.id }
        val counts = queries.playlistSongCounts(ids) { playlistId, songCount ->
            playlistId to songCount.toInt()
        }.executeAsList().toMap()
        val thumbnails = queries.playlistThumbsForIds(ids) { playlistId, thumbnailUrl ->
            playlistId to thumbnailUrl
        }.executeAsList().groupBy({ it.first }, { it.second })

        return playlistEntities.map { playlist ->
            PlaylistModel(
                playlist = playlist,
                songCount = counts[playlist.id] ?: 0,
                songThumbnails = thumbnails[playlist.id].orEmpty(),
            )
        }
    }

    private fun assembleEvents(rows: List<Event>): List<EventWithSong> {
        if (rows.isEmpty()) return emptyList()
        val events = rows.map(::mapEvent)
        val songs = songsByIdsSync(events.map { it.songId }.distinct()).associateBy { it.id }
        return events.mapNotNull { event ->
            songs[event.songId]?.let { EventWithSong(event = event, song = it) }
        }
    }

    private fun mapSong(row: Song): SongEntity = SongEntity(
        id = row.id,
        title = row.title,
        duration = row.duration.toInt(),
        thumbnailUrl = row.thumbnailUrl,
        albumId = row.albumId,
        albumName = row.albumName,
        explicit = row.explicit == 1L,
        year = row.year?.toInt(),
        date = epochToLocal(row.date),
        dateModified = epochToLocal(row.dateModified),
        liked = row.liked == 1L,
        likedDate = epochToLocal(row.likedDate),
        totalPlayTime = row.totalPlayTime,
        inLibrary = epochToLocal(row.inLibrary),
        dateDownload = epochToLocal(row.dateDownload),
        isLocal = row.isLocal == 1L,
        libraryAddToken = row.libraryAddToken,
        libraryRemoveToken = row.libraryRemoveToken,
        lyricsOffset = row.lyricsOffset.toInt(),
        romanizeLyrics = row.romanizeLyrics == 1L,
        isDownloaded = row.isDownloaded == 1L,
        isUploaded = row.isUploaded == 1L,
        isVideo = row.isVideo == 1L,
        localUri = row.localUri,
    )

    private fun mapAlbum(row: Album): AlbumEntity = AlbumEntity(
        id = row.id,
        playlistId = row.playlistId,
        title = row.title,
        year = row.year?.toInt(),
        thumbnailUrl = row.thumbnailUrl,
        themeColor = row.themeColor?.toInt(),
        songCount = row.songCount.toInt(),
        duration = row.duration.toInt(),
        explicit = row.explicit == 1L,
        lastUpdateTime = epochToLocal(row.lastUpdateTime),
        bookmarkedAt = epochToLocal(row.bookmarkedAt),
        likedDate = epochToLocal(row.likedDate),
        inLibrary = epochToLocal(row.inLibrary),
        description = row.description,
        isLocal = row.isLocal == 1L,
        isUploaded = row.isUploaded == 1L,
    )

    private fun mapArtist(row: Artist): ArtistEntity = ArtistEntity(
        id = row.id,
        name = row.name,
        thumbnailUrl = row.thumbnailUrl,
        channelId = row.channelId,
        lastUpdateTime = epochToLocal(row.lastUpdateTime),
        bookmarkedAt = epochToLocal(row.bookmarkedAt),
        isLocal = row.isLocal == 1L,
    )

    private fun mapPlaylist(row: Playlist): PlaylistEntity = PlaylistEntity(
        id = row.id,
        name = row.name,
        browseId = row.browseId,
        createdAt = epochToLocal(row.createdAt),
        lastUpdateTime = epochToLocal(row.lastUpdateTime),
        isEditable = row.isEditable == 1L,
        bookmarkedAt = epochToLocal(row.bookmarkedAt),
        remoteSongCount = row.remoteSongCount?.toInt(),
        playEndpointParams = row.playEndpointParams,
        thumbnailUrl = row.thumbnailUrl,
        shuffleEndpointParams = row.shuffleEndpointParams,
        radioEndpointParams = row.radioEndpointParams,
        isLocal = row.isLocal == 1L,
        isAutoSync = row.isAutoSync == 1L,
        description = row.description,
    )

    private fun mapFormat(row: Format): FormatEntity = FormatEntity(
        id = row.id,
        itag = row.itag.toInt(),
        mimeType = row.mimeType,
        codecs = row.codecs,
        bitrate = row.bitrate.toInt(),
        sampleRate = row.sampleRate?.toInt(),
        contentLength = row.contentLength,
        loudnessDb = row.loudnessDb,
        perceptualLoudnessDb = row.perceptualLoudnessDb,
        playbackUrl = row.playbackUrl,
    )

    private fun mapLyrics(row: Lyrics): LyricsEntity = LyricsEntity(
        id = row.id,
        lyrics = row.lyrics,
        provider = row.provider,
        translatedLyrics = row.translatedLyrics,
        translationLanguage = row.translationLanguage,
        translationMode = row.translationMode,
    )

    private fun mapEvent(row: Event): EventEntity = EventEntity(
        id = row.id,
        songId = row.songId,
        timestamp = epochToLocal(row.timestamp),
        playTime = row.playTime,
    )

    private fun mapSetVideoId(row: Set_video_id): SetVideoIdEntity = SetVideoIdEntity(
        videoId = row.videoId,
        setVideoId = row.setVideoId,
    )

    private fun mapSpeedDial(row: Speed_dial_item): SpeedDialItem = SpeedDialItem(
        id = row.id,
        secondaryId = row.secondaryId,
        title = row.title,
        subtitle = row.subtitle,
        thumbnailUrl = row.thumbnailUrl,
        type = row.type,
        explicit = row.explicit == 1L,
        createDate = row.createDate,
    )

    private fun epochToLocal(epochMilli: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(epochMilli).toLocalDateTime(TimeZone.UTC)

    private fun epochToLocal(epochMilli: Long?): LocalDateTime? =
        epochMilli?.let { epochToLocal(it) }

    private fun localToEpoch(value: LocalDateTime?): Long? =
        value?.toInstant(TimeZone.UTC)?.toEpochMilliseconds()

    /** SQLDelight may infer SUM(...) as Long, so keep this usable for both Long and Long?. */
    private fun Long?.toNullableInt(): Int? = this?.toInt()

    private fun Boolean.asDbLong(): Long = if (this) 1L else 0L
}
