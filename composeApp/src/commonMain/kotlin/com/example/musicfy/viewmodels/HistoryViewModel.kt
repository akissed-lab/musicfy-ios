@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.constants.HistorySource
import com.example.musicfy.db.entities.EventWithSong
import com.example.musicfy.settings.SettingsKeys
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.musicfy.util.currentTimeMillis
import kotlinx.datetime.*

/** Local stand-in for upstream `com.music.innertube.pages.HistoryPage`. */
data class HistoryPage(val sections: List<HistorySection>? = null) {
    data class HistorySection(val title: String, val songs: List<SongItem>)
}

/** KMP port of upstream `HistoryViewModel`, backed by the local listen-event table. */
class HistoryViewModel(container: AppContainer) : ViewModel() {
    val database = container.database
    private val settings = container.settings

    var historySource = MutableStateFlow(HistorySource.LOCAL)

    private val today: LocalDate = Instant.fromEpochMilliseconds(currentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val todayStart = today.toUtcDayStart()
    private val thisMondayStart = todayStart - today.dayOfWeek.ordinal * 86_400_000L
    private val lastMondayStart = thisMondayStart - 7L * 86_400_000L

    // TODO: YouTube.musicHistory() (remote history page) is not ported.
    val historyPage = MutableStateFlow<HistoryPage?>(null)

    private val hideVideoSongs = MutableStateFlow(
        settings.getBoolean(SettingsKeys.HideVideoSongsKey, false),
    )

    /** Re-read the filter after the settings screen writes it. */
    fun refreshSettings() {
        hideVideoSongs.value = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false)
    }

    val events: StateFlow<Map<DateAgo, List<EventWithSong>>> = hideVideoSongs
        .flatMapLatest { hide ->
            database.events().map { all ->
                all.filter { !hide || !it.song.song.isVideo }
                    .groupBy { event ->
                        val timestamp = event.event.timestamp
                        val dayStart = timestamp.toUtcDayStart()
                        val daysAgo = (todayStart - dayStart) / 86_400_000L
                        when {
                            daysAgo == 0L -> DateAgo.Today
                            daysAgo == 1L -> DateAgo.Yesterday
                            dayStart >= thisMondayStart -> DateAgo.ThisWeek
                            dayStart >= lastMondayStart -> DateAgo.LastWeek
                            else -> DateAgo.Other(LocalDate(timestamp.year, timestamp.monthNumber, 1))
                        }
                    }
                    .entries
                    .sortedWith(
                        compareBy<Map.Entry<DateAgo, List<EventWithSong>>> { entry ->
                            when (val key = entry.key) {
                                DateAgo.Today -> 0L
                                DateAgo.Yesterday -> 1L
                                DateAgo.ThisWeek -> 2L
                                DateAgo.LastWeek -> 3L
                                is DateAgo.Other -> 4L + daysBetween(key.date, today)
                            }
                        },
                    )
                    .associate { entry -> entry.key to entry.value.distinctBy { it.song.id } }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        fetchRemoteHistory()
    }

    /** TODO: no YouTube history page in the KMP innertube port; local events remain the source. */
    fun fetchRemoteHistory() {
        viewModelScope.launch(Dispatchers.Default) {
            historyPage.value = null
        }
    }

    /** Same UTC-day-start convention as the facade's timestamp mapping. */
    private fun LocalDate.toUtcDayStart(): Long =
        LocalDateTime(year, monthNumber, dayOfMonth, 0, 0)
            .toInstant(TimeZone.UTC).toEpochMilliseconds()

    private fun LocalDateTime.toUtcDayStart(): Long =
        LocalDateTime(year, monthNumber, dayOfMonth, 0, 0)
            .toInstant(TimeZone.UTC).toEpochMilliseconds()

    private fun daysBetween(from: LocalDate, to: LocalDate): Int =
        ((to.toUtcDayStart() - from.toUtcDayStart()) / 86_400_000L).toInt()
}

sealed class DateAgo {
    data object Today : DateAgo()
    data object Yesterday : DateAgo()
    data object ThisWeek : DateAgo()
    data object LastWeek : DateAgo()
    data class Other(val date: LocalDate) : DateAgo()
}
