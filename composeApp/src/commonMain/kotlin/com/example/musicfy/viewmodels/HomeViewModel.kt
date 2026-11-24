@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.constants.QuickPicks
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.db.entities.AlbumEntity
import com.example.musicfy.db.entities.ArtistEntity
import com.example.musicfy.db.entities.Album as LocalAlbum
import com.example.musicfy.db.entities.Artist as LocalArtist
import com.example.musicfy.db.entities.LocalItem
import com.example.musicfy.db.entities.Playlist as LocalPlaylist
import com.example.musicfy.db.entities.PlaylistEntity as LocalPlaylistEntity
import com.example.musicfy.db.entities.Song as LocalSong
import com.example.musicfy.models.ArtistGroup
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.models.SimilarRecommendation
import com.example.musicfy.player.PlaybackController
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.toEnum
import com.example.musicfy.util.currentTimeMillis
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist as YTArtist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * KMP port of the Home screen state surface. Local rows are loaded from [database]; the YouTube
 * feed, HomeFeedCache, HomeRecommender, Innertube pages and account sync are not part of the
 * commonMain port, so those shelves stay empty (each has a TODO at the point it would be filled).
 */
class HomeViewModel(container: AppContainer) : ViewModel() {
    val database: MusicDatabaseFacade = container.database
    private val settings = container.settings

    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private fun quickPicksSetting(): QuickPicks =
        settings.getString(SettingsKeys.QuickPicksKey, QuickPicks.QUICK_PICKS.name)
            .toEnum(QuickPicks.QUICK_PICKS)

    private val quickPicksEnum = MutableStateFlow(quickPicksSetting())

    /** Re-read settings that affect local rows after a settings screen writes them. */
    fun refreshSettings() {
        quickPicksEnum.value = quickPicksSetting()
    }

    val quickPicks = MutableStateFlow<List<LocalItem>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<LocalSong>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val localPlaylists = MutableStateFlow<List<LocalPlaylist>?>(null)

    /** Null while the hero is waiting, empty when there is nothing to ask or the ask failed. */
    val starterSongs = MutableStateFlow<List<StarterSong>?>(null)

    /** Library artists (onboarding's picks on a new install), for the hero before any history. */
    val libraryArtists: StateFlow<List<LocalArtist>> = database
        .artists(MusicDatabaseFacade.ArtistSortType.CREATE_DATE, true)
        .map { artists -> artists.filter { it.artist.isYouTubeArtist }.take(5) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val recentlyPlayed = MutableStateFlow<List<LocalItem>?>(null)
    val mostPlayedSongsForHome = MutableStateFlow<List<LocalSong>?>(null)
    val recentHistorySongs = MutableStateFlow<List<LocalSong>?>(null)
    val artistListItems = MutableStateFlow<List<ArtistGroup>?>(null)
    val allTimeHits = MutableStateFlow<List<YTItem>?>(null)
    val liveShows = MutableStateFlow<List<HomeVideo>?>(null)
    val musicVideos = MutableStateFlow<List<HomeVideo>?>(null)

    /** "Because you like", "More like", genre and new-release rows, in Home's order. */
    val homeCategories = MutableStateFlow<List<HomeCategory>?>(null)

    // TODO: ArtistVideos.feedShelfKind() depends on unported Innertube HomePage shelves.
    val feedVideoKinds: StateFlow<Map<Int, FeedVideoKind>> = flowOf(emptyMap<Int, FeedVideoKind>())
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // TODO: merging YouTube's own video shelves into these rows needs the unported feed parser;
    // until then each row is exactly the locally gathered list.
    val liveRow: StateFlow<List<HomeVideo>?> = liveShows
    val musicVideoRow: StateFlow<List<HomeVideo>?> = musicVideos

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val lastPlayedSong: StateFlow<MediaMetadata?> = database.recentEvents(1)
        .map { events -> events.firstOrNull()?.song?.toMediaMetadata() }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val speedDialItems: StateFlow<List<YTItem>> = combine(
        database.getAll(),
        keepListening,
        quickPicks,
    ) { pinned, keepListeningItems, quick ->
        val filled = pinned.map { it.toYTItem() }.toMutableList()
        val targetSize = 27

        fun fillFrom(items: List<LocalItem>?) {
            if (items == null || filled.size >= targetSize) return
            val needed = targetSize - filled.size
            val available = items.mapNotNull { localToYT(it) }
                .filter { candidate -> filled.none { it.id == candidate.id } }
            filled.addAll(available.take(needed))
        }

        fillFrom(keepListeningItems)
        fillFrom(quick)
        filled.take(targetSize)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            delay(1_000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            fun add(item: LocalItem) {
                val converted = localToYT(item) ?: return
                if (converted is SongItem) userSongs.add(converted) else otherSources.add(converted)
            }

            quickPicks.value.orEmpty().forEach(::add)
            keepListening.value.orEmpty().forEach(::add)
            otherSources.addAll(allYtItems.value)

            val pool = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs
            } else {
                otherSources
            }
            return pool.distinctBy { it.id }.shuffled().firstOrNull()
                ?: userSongs.firstOrNull()
                ?: otherSources.firstOrNull()
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    fun togglePin(item: YTItem) {
        viewModelScope.launch(Dispatchers.Default) {
            val speedDialItem = com.example.musicfy.db.entities.SpeedDialItem.fromYTItem(item)
            if (database.isPinned(speedDialItem.id).first()) {
                database.delete(speedDialItem.id)
            } else {
                database.pinToSpeedDial(speedDialItem)
            }
        }
    }

    /** TODO: `YouTube.home(continuation)` and the feed cache are not ported; keep the feed as is. */
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        _isLoadingMore.value = false
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || (chip == selectedChip.value && previousHomePage.value != null)) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }
        if (selectedChip.value == null) previousHomePage.value = homePage.value
        selectedChip.value = chip
        // TODO: fetching a chip's YouTube home page needs the unported browse/home parser.
    }

    private var lastLoadAt = 0L
    private val loadMutex = Mutex()
    private val listeningRefresh = Mutex()

    /** Two skips or four completed songs rebuild listening rows; after 30 min the whole page. */
    fun onListeningBreak() {
        val sinceLoad = currentTimeMillis() - lastLoadAt
        if (lastLoadAt == 0L || sinceLoad < 60_000L || loadMutex.isLocked || listeningRefresh.isLocked) return
        val stale = sinceLoad > 30 * 60_000L
        // TODO: ListeningSignals (skips/plays) is not ported; only the stale case can refresh.
        if (!stale) return
        viewModelScope.launch(Dispatchers.Default) { refresh() }
    }

    private var listening: Job? = null
    private var listeningTo: PlaybackController? = null

    /** Follows the player's metadata/play state; skip detection needs ListeningSignals (TODO). */
    fun attachPlayer(connection: PlaybackController) {
        if (listeningTo === connection) return
        listeningTo = connection
        listening?.cancel()
        listening = viewModelScope.launch {
            var current: MediaMetadata? = null
            var playedMs = 0L
            var playingSince = -1L
            combine(
                connection.currentMetadata,
                connection.transportState.map { it.isPlaying },
            ) { metadata, playing -> metadata to playing }
                .collect { (metadata, playing) ->
                    val now = currentTimeMillis()
                    if (playingSince >= 0) {
                        playedMs += now - playingSince
                        playingSince = -1L
                    }
                    if (metadata?.id != current?.id) {
                        current = metadata
                        playedMs = 0L
                    }
                    if (playing && metadata != null) playingSince = now
                }
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                isRefreshing.value = true
                load()
            } finally {
                isRefreshing.value = false
            }
        }
        // TODO: SyncUtils.tryAutoSync() needs the account sync layer.
    }

    init {
        // TODO: HomeFeedCache has no KMP port; cached shelves start empty and are filled locally.
        viewModelScope.launch(Dispatchers.Default) { load() }
    }

    private suspend fun load() {
        if (!loadMutex.tryLock()) return
        try {
            loadOnce()
        } finally {
            loadMutex.unlock()
        }
    }

    private suspend fun loadOnce() {
        isLoading.value = true
        selectedChip.value = null
        previousHomePage.value = null
        try {
            loadLocalDataPhase()
        } finally {
            isLoading.value = false
        }
        // TODO: network phase (YouTube.home/explore, charts, community, account) is unavailable.
        lastLoadAt = currentTimeMillis()
    }

    private suspend fun loadLocalDataPhase() {
        val hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false)
        val now = currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 86_400_000L
        val twoWeeksAgo = now - 14L * 86_400_000L

        val topSongs = database.mostPlayedSongs(thirtyDaysAgo, limit = 20).first()
            .visibleSongs(hideVideoSongs)
            .distinctBy { it.id }
            .take(20)

        quickPicks.value = when (quickPicksEnum.value) {
            QuickPicks.QUICK_PICKS -> topSongs
            QuickPicks.LAST_LISTEN -> {
                // TODO: related-song tables are not in the facade; fall back to the last song.
                database.recentEvents(1).first().firstOrNull()?.song?.let { listOf(it) }
                    ?: topSongs
            }
        }

        loadRecentlyPlayed()
        mostPlayedSongsForHome.value = topSongs
        recentHistorySongs.value = database.recentEvents(15).first().map { it.song }

        val heard = recentHistorySongs.value.orEmpty().map { it.id }.toSet()
        forgottenFavorites.value = database
            .likedSongs(MusicDatabaseFacade.SongSortType.CREATE_DATE, true)
            .first()
            .filterNot { it.id in heard }
            .distinctBy { it.id }
            .take(20)

        val keepSongs = database.mostPlayedSongs(twoWeeksAgo, limit = 15, offset = 5).first()
            .visibleSongs(hideVideoSongs)
            .shuffled()
            .take(10)
        val keepAlbums = database.albums(MusicDatabaseFacade.AlbumSortType.PLAY_TIME, true)
            .first()
            .filter { it.album.thumbnailUrl != null }
            .shuffled()
            .take(5)
        val keepArtists = database.mostPlayedArtists(twoWeeksAgo)
            .first()
            .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
            .shuffled()
            .take(5)
        val playlists = database.playlists(MusicDatabaseFacade.PlaylistSortType.LAST_UPDATED, true).first()
        val matchingPlaylistIds = if (keepSongs.isEmpty()) {
            emptySet()
        } else {
            database.playlistIdsContainingSongs(keepSongs.map { it.id }).toSet()
        }
        val keepPlaylists = playlists.filter { it.id in matchingPlaylistIds }.shuffled().take(3)

        val keep = mutableListOf<LocalItem>()
        keep.addAll(keepSongs)
        keep.addAll(keepAlbums)
        keep.addAll(keepArtists)
        keep.addAll(keepPlaylists)
        keepListening.value = keep.shuffled()

        val lastPlayedLikedSongs = settings.getLong(SettingsKeys.LastPlayedLikedSongsTimeKey, 0L)
        if (currentTimeMillis() - lastPlayedLikedSongs < 7L * 86_400_000L) {
            val likedCount = database
                .likedSongs(MusicDatabaseFacade.SongSortType.CREATE_DATE, true)
                .first().size
            val liked = LocalPlaylist(
                playlist = LocalPlaylistEntity(id = "liked", name = "Liked"),
                songCount = likedCount,
                songThumbnails = emptyList(),
            )
            val withLiked = keepListening.value.orEmpty().toMutableList()
            withLiked.removeAll { it.id == "liked" }
            withLiked.add(0, liked)
            keepListening.value = withLiked
        }

        localPlaylists.value = playlists.distinctBy { it.id }
        allLocalItems.value = (quickPicks.value.orEmpty() +
            forgottenFavorites.value.orEmpty() +
            keepListening.value.orEmpty())
            .filter { it is LocalSong || it is LocalAlbum || it is LocalPlaylist }

        // TODO: YouTube-based rows. Empty means "nothing to ask" rather than a failed request;
        // the state flows keep their type so ported UI renders the same empty sections.
        dailyDiscover.value = emptyList()
        similarRecommendations.value = emptyList()
        artistListItems.value = emptyList()
        homeCategories.value = emptyList()
        liveShows.value = emptyList()
        musicVideos.value = emptyList()
        allYtItems.value = emptyList()
        starterSongs.value = emptyList()
        communityPlaylists.value = emptyList()
        allTimeHits.value = emptyList()
        homePage.value = null
        explorePage.value = null
        accountPlaylists.value = null
    }

    private suspend fun loadRecentlyPlayed() {
        val songs = database.recentlyPlayedSongs(8).first()
        // TODO: facade has no recentlyPlayedAlbums()/recentlyPlayedPlaylists(); use the
        // latest-updated library slices instead.
        val albums = database.albums(MusicDatabaseFacade.AlbumSortType.CREATE_DATE, true).first().take(6)
        val playlists = database.playlists(MusicDatabaseFacade.PlaylistSortType.LAST_UPDATED, true)
            .first().take(6)

        val merged = mutableListOf<LocalItem>()
        for (i in 0 until maxOf(songs.size, albums.size, playlists.size)) {
            songs.getOrNull(i)?.let { merged.add(it) }
            albums.getOrNull(i)?.let { merged.add(it) }
            playlists.getOrNull(i)?.let { merged.add(it) }
        }

        val lastPlayedLikedSongs = settings.getLong(SettingsKeys.LastPlayedLikedSongsTimeKey, 0L)
        if (currentTimeMillis() - lastPlayedLikedSongs < 7L * 86_400_000L) {
            val likedCount = database
                .likedSongs(MusicDatabaseFacade.SongSortType.CREATE_DATE, true)
                .first().size
            merged.removeAll { it.id == "liked" }
            merged.add(
                0,
                LocalPlaylist(
                    playlist = LocalPlaylistEntity(id = "liked", name = "Liked"),
                    songCount = likedCount,
                    songThumbnails = emptyList(),
                ),
            )
        }
        recentlyPlayed.value = merged.take(15)
    }

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private fun List<LocalSong>.visibleSongs(hideVideoSongs: Boolean): List<LocalSong> =
        if (hideVideoSongs) filter { !it.song.isVideo } else this

    private fun localToYT(item: LocalItem): YTItem? = when (item) {
        is LocalSong -> SongItem(
            id = item.id,
            title = item.title,
            artists = item.artists.map { YTArtist(name = it.name, id = it.id) },
            thumbnail = item.thumbnailUrl ?: "",
            explicit = item.song.explicit,
        )

        is LocalAlbum -> AlbumItem(
            browseId = item.id,
            playlistId = item.album.playlistId ?: "",
            title = item.title,
            artists = item.artists.map { YTArtist(name = it.name, id = it.id) },
            year = item.album.year,
            thumbnail = item.thumbnailUrl ?: "",
            explicit = item.album.explicit,
        )

        is LocalArtist -> ArtistItem(
            id = item.id,
            title = item.title,
            thumbnail = item.thumbnailUrl,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

        is LocalPlaylist -> PlaylistItem(
            id = item.id,
            title = item.title,
            author = YTArtist(name = "Local Playlist", id = null),
            songCountText = "${item.songCount} songs",
            thumbnail = item.thumbnails.firstOrNull(),
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
    }

    private fun LocalSong.toMediaMetadata(): MediaMetadata = MediaMetadata(
        id = this.song.id,
        title = this.song.title,
        artists = this.artists.map { artist: ArtistEntity ->
            MediaMetadata.Artist(id = artist.id, name = artist.name)
        },
        duration = this.song.duration,
        thumbnailUrl = this.song.thumbnailUrl,
        album = this.album?.let { localAlbum: AlbumEntity ->
            MediaMetadata.Album(id = localAlbum.id, title = localAlbum.title)
        },
        explicit = this.song.explicit,
        liked = this.song.liked,
        likedDate = this.song.likedDate,
        inLibrary = this.song.inLibrary,
        localUri = this.song.localUri,
    )
}

data class DailyDiscoverItem(
    val seed: LocalSong,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?,
)

@Serializable
data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>,
)

/** a song by one of the artists picked in onboarding, put forward on a new install's hero */
data class StarterSong(
    val song: SongItem,
    val artistName: String,
    val artistThumbnail: String?,
)

/** one titled row of recommendations on Home, songs and playlists side by side */
@Serializable
data class HomeCategory(
    val id: String,
    val kind: Kind,
    val title: String,
    val items: List<YTItem>,
    val thumbnailUrl: String? = null,
    /** where the title's chevron goes: the artist, or the genre's page */
    val endpoint: BrowseEndpoint? = null,
) {
    @Serializable
    enum class Kind {
        BecauseYouLike,
        MoreLikeArtist,
        YourGenre,
        NewGenre,
        NewFromArtists,
    }
}

/** the kinds of video row YouTube's own Home feed sends */
enum class FeedVideoKind { Live, MusicVideos, Other }

/** a video on one of Home's banner rows, with its title tidied up for the card */
@Serializable
data class HomeVideo(
    val item: SongItem,
    val title: String,
    val subtitle: String,
)

/** TODO: local stand-ins until the Innertube `HomePage` parser is ported. */
data class HomePage(
    val sections: List<Section> = emptyList(),
    val chips: List<Chip>? = null,
) {
    data class Section(val title: String, val items: List<YTItem>)

    data class Chip(val title: String?, val endpoint: BrowseEndpoint?)
}

/** TODO: local stand-in until the Innertube `ExplorePage` parser is ported. */
data class ExplorePage(val newReleaseAlbums: List<AlbumItem> = emptyList())
