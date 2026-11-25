package com.example.musicfy.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.db.entities.Artist
import com.example.musicfy.db.entities.Playlist
import com.example.musicfy.db.entities.Song
import com.example.musicfy.util.nowLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** One "Total listens" card: how many different songs, and for how long, over [label]'s span. */
@Immutable
data class ListenTotal(val label: String, val songs: Int, val playTimeMs: Long) {
    companion object {
        /** Shown until the first count arrives, so the numbers roll up from zero instead of popping in. */
        val Placeholder = listOf(
            ListenTotal("ALL TIME", 0, 0L),
            ListenTotal("THIS MONTH", 0, 0L),
            ListenTotal("THIS WEEK", 0, 0L),
        )
    }
}

/** Local-only profile: the facade replaces upstream's Room DAOs. */
@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModel(container: AppContainer) : ViewModel() {
    private val database: MusicDatabaseFacade = container.database

    val recentSongs: StateFlow<List<Song>?> = database.recentlyPlayedSongs(limit = 20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Bookmarked playlists, the most recently changed first.
     * The facade has no playlistsByUpdatedDateAsc(); LAST_UPDATED/descending is its equivalent.
     */
    val playlists: StateFlow<List<Playlist>?> = database
        .playlists(MusicDatabaseFacade.PlaylistSortType.LAST_UPDATED, descending = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // TODO: facade has no likedSongsCount(); count the same liked-songs flow instead.
    val likedCount: StateFlow<Int> = database
        .likedSongs(MusicDatabaseFacade.SongSortType.CREATE_DATE, descending = false)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val topArtists: StateFlow<List<Artist>?> = database
        .mostPlayedArtists(fromTimeStamp = 0L, limit = 12)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The total play time changes with every logged play, which is exactly when these go stale.
    // TODO: facade has no totalListeningTimeMs(); events() is the equivalent change signal.
    val listens: StateFlow<List<ListenTotal>?> = database.events()
        .distinctUntilChanged()
        .mapLatest { countListens() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Keeps every list loaded while the caller stays collecting, so the profile opens populated. */
    suspend fun keepWarm() {
        merge(recentSongs, playlists, likedCount, topArtists, listens).collect { }
    }

    private suspend fun countListens(): List<ListenTotal> {
        val today = nowLocal().date
        val todayStart = today.toStoredMillis()
        val monthStart = LocalDate(today.year, today.monthNumber, 1).toStoredMillis()
        val weekStart = todayStart - today.dayOfWeek.ordinal * 86_400_000L
        // One read of the whole history; the month and the week are slices of it.
        val events = database.recapEvents(0L, todayStart + 86_400_000L)

        fun total(label: String, from: Long): ListenTotal {
            val songs = HashSet<String>()
            var ms = 0L
            for (e in events) {
                if (e.timestamp < from) continue
                songs += e.songId
                ms += e.playTime.coerceAtLeast(0L)
            }
            return ListenTotal(label, songs.size, ms)
        }

        return listOf(
            total("ALL TIME", 0L),
            total("THIS MONTH", monthStart),
            total("THIS WEEK", weekStart),
        )
    }
}

/** Upstream `java.time.LocalDate.toStoredMillis()`, on kotlinx-datetime with UTC day starts. */
private fun LocalDate.toStoredMillis(): Long =
    LocalDateTime(year, monthNumber, dayOfMonth, 0, 0)
        .toInstant(TimeZone.UTC).toEpochMilliseconds()
