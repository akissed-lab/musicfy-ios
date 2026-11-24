// PreferenceEnums.kt
//
// Common-main port of the enums declared in upstream
// `app/src/main/kotlin/com/example/musicfy/constants/PreferenceKeys.kt`, plus the
// player/lyrics enums that settings screens read through string preferences.
// No Android imports: resource ids, drawables and Build checks are deliberately
// left to the platform layer. `PreferredLyricsProvider` is intentionally NOT
// redeclared here - it already lives in this package in PreferredLyricsProvider.kt.

package com.example.musicfy.constants

import com.example.musicfy.util.currentTimeMillis

// ---------------------------------------------------------------- theme / look

enum class DensityScale(val value: Float, val label: String) {
    NATIVE(1.0f, "Native (100%)"),
    SLIGHTLY_COMPACT(0.85f, "Slightly Compact (85%)"),
    COMPACT(0.75f, "Compact (75%)"),
    VERY_COMPACT(0.65f, "Very Compact (65%)"),
    ULTRA_COMPACT(0.55f, "Ultra Compact (55%)");

    companion object {
        fun fromValue(value: Float): DensityScale = entries.find { it.value == value } ?: NATIVE
    }
}

enum class SliderStyle { DEFAULT, WAVY, SLIM }

enum class BlurQuality { LOW, HIGH }

/** Upstream also defines this in `ui.screens`; kept here for settings-only code. */
enum class DarkMode { AUTO, ON, OFF }

// ---------------------------------------------------------------- content / audio

enum class AudioQuality {
    AUTO,
    LOW,
    MEDIUM,
    HIGH,
    LOSSLESS,
    HI_RES_LOSSLESS,
    DOLBY_ATMOS;

    /** Tiers only the Monochrome backend can serve; YouTube has no equivalent. */
    val usesMonochrome: Boolean
        get() = this == LOSSLESS || this == HI_RES_LOSSLESS || this == DOLBY_ATMOS
}

enum class DownloadQuality(val title: String, val description: String) {
    LOSSLESS("Lossless", "FLAC from Monochrome, CD quality or better. Songs it doesn't have download at High"),
    HIGH("High", "The best YouTube has: ~160 kbps Opus"),
    MEDIUM("Medium", "Around 128 kbps, half the space"),
    LOW("Low", "The smallest files, ~50-70 kbps"),
}

/** Upstream: `com.music.innertube.models.IpVersion`. */
enum class IpVersion { AUTO, IPV4, IPV6 }

enum class CrossmixMode {
    AUTO_CROSSFADE,
    OFF,
    MANUAL_CROSSFADE,
    CROSSMIX,
}

// the crossmix effect layered over a pause, play or skip fade
enum class TransportEffect(val label: String) {
    OFF("None"),
    ECHO("Echo"),
    REWIND("Rewind"),
    TAPE_STOP("Tape stop"),
    FAR_AWAY("Fade to far"),
    REVERB("Reverb wash"),
    FILTER("Filter sweep"),
}

// ---------------------------------------------------------------- library / sorting

enum class LibraryViewType {
    LIST,
    GRID,
    ;

    fun toggle(): LibraryViewType = when (this) {
        LIST -> GRID
        GRID -> LIST
    }
}

enum class SongFilter { LIBRARY, LIKED, DOWNLOADED, UPLOADED }
enum class ArtistFilter { LIBRARY, LIKED }
enum class AlbumFilter { LIBRARY, LIKED, UPLOADED }

enum class SongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }
enum class PlaylistSongSortType { CUSTOM, CREATE_DATE, NAME, ARTIST, PLAY_TIME }
enum class LikedSongSortType { CREATE_DATE, IMPORT_ORDER, NAME, ARTIST, PLAY_TIME }
enum class AutoPlaylistSongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }
enum class ArtistSortType { CREATE_DATE, NAME, SONG_COUNT, PLAY_TIME }
enum class ArtistSongSortType { CREATE_DATE, NAME, PLAY_TIME }
enum class AlbumSortType { CREATE_DATE, NAME, ARTIST, YEAR, SONG_COUNT, LENGTH, PLAY_TIME }
enum class PlaylistSortType { CREATE_DATE, NAME, SONG_COUNT, LAST_UPDATED }
enum class MixSortType { CREATE_DATE, NAME, LAST_UPDATED }

enum class GridItemSize { BIG, SMALL }

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    /**
     * Upstream uses java.time.LocalDateTime in UTC; kotlinx-datetime has no
     * calendar-month arithmetic on Instant, so a month is 30 days and a year
     * is 365 days here. Good enough for the "My top" time window.
     */
    fun toTimeMillis(): Long {
        val now = currentTimeMillis()
        return when (this) {
            DAY -> now - 1L * 86_400_000L
            WEEK -> now - 7L * 86_400_000L
            MONTH -> now - 30L * 86_400_000L
            YEAR -> now - 365L * 86_400_000L
            ALL_TIME -> 0L
        }
    }
}

enum class QuickPicks { QUICK_PICKS, LAST_LISTEN }

enum class SearchSource {
    LOCAL,
    ONLINE,
    ;

    fun toggle(): SearchSource = when (this) {
        LOCAL -> ONLINE
        ONLINE -> LOCAL
    }
}

// ---------------------------------------------------------------- player

enum class PlayerButtonsStyle { DEFAULT, PRIMARY, TERTIARY }
enum class PlayerBackgroundStyle { COVER_GRADIENT, SOLID, DARK_GRADIENT, APPLE_MUSIC }

enum class PlayerCoverStyle {
    EDGE_TO_EDGE,
    SQUARED,
    DISC_SMALL_FULL,
    DISC_SMALL_LABEL,
    DISC_BIG_FULL,
    DISC_BIG_LABEL,
    DISC_ALBUM,
    /** the Turntable player's own: a big record and its arm */
    TURNTABLE,
}

/**
 * Upstream picks EDGE_TO_EDGE only on Android API >= 31 (S). iOS has the
 * equivalent blur behind the artwork, so the common default is edge-to-edge.
 * An explicit choice in Player customise still wins on every platform.
 */
val DefaultPlayerCoverStyle: PlayerCoverStyle get() = PlayerCoverStyle.EDGE_TO_EDGE

/**
 * Whole-player presets. [DEFAULT] can be edited, [TURNTABLE] is the flagship
 * around a record player and [POSTER] has its visualizer; the other two are
 * complete designs of their own.
 */
enum class PlayerStyle(val displayName: String, val selectorName: String) {
    DEFAULT("Flagship", "Default"),
    TURNTABLE("Turntable", "Turntable"),
    POSTER("Poster", "Poster"),
    TIMELINE("Timeline", "Timeline"),
    LYRICS("Lyrics", "Lyrics stage");

    val editable: Boolean get() = this == DEFAULT || this == TURNTABLE || this == POSTER

    /** drawn by the flagship's own player (cover, controls, card) */
    val flagshipFamily: Boolean get() = this == DEFAULT || this == TURNTABLE

    companion object {
        /** the styles these replaced, so a stored choice lands on its nearest successor */
        fun fromStored(name: String?): PlayerStyle? = when (name) {
            "SIMPLE", "SIMPLE_COMPACT" -> TIMELINE
            "LYRICS_ABSTRACT" -> LYRICS
            else -> null
        }

        /** current names as well as legacy ones, falling back to [DEFAULT] */
        fun fromStoredOrDefault(name: String?): PlayerStyle =
            fromStored(name) ?: entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

enum class PlayerEndTimestamp { END_TIME, TIME_LEFT }

enum class PlayerBottomBarStyle(val displayName: String) {
    STACK("Stack"),
    VOLUME("Volume"),
}

enum class DiscLabelContent { CUSTOM, MUSIC_NAME, ARTIST_NAME, ALBUM_NAME, MUSIC_AND_ARTIST, NONE }
enum class PlayerTextContent { MUSIC_NAME, ALBUM_NAME, ARTIST_NAME, COMBINED }

/**
 * Everything the player's round buttons can do. Upstream carries a drawable
 * resource id per entry; only the stable enum names and labels are needed in
 * common code (the name is what is saved in `playerMenuSlots`).
 */
enum class PlayerMenuAction(val label: String) {
    LIKE("Like"),
    OTHER_MENU("Other menu"),
    SLEEP_TIMER("Sleep timer"),
    SEE_VIDEO("See video"),
    REPEAT("Repeat"),
    SHUFFLE("Shuffle"),
    LYRICS("Lyrics"),
    QUEUE("Queue"),
    SPEED("Playback speed"),
    OUTPUT("Device output"),
    KEEP_AWAKE("Keep screen on"),
    TRANSLATE_LYRICS("Translate lyrics"),
    DOWNLOAD("Download"),
    ADD_TO_PLAYLIST("Add to playlist");

    companion object {
        val DefaultSlots = listOf(OTHER_MENU, SLEEP_TIMER, SEE_VIDEO, REPEAT)
        val DefaultSide = LIKE
        const val SideSlot = 4

        fun parseSlots(stored: String): List<PlayerMenuAction> {
            val parsed = stored.split(',').mapNotNull { name -> entries.firstOrNull { it.name == name } }
            return if (parsed.size == DefaultSlots.size) parsed else DefaultSlots
        }

        fun parseSide(stored: String): PlayerMenuAction =
            entries.firstOrNull { it.name == stored } ?: DefaultSide
    }
}

/** Upstream also defines this in `ui.screens`; kept here for settings-only code. */
enum class LyricsPosition { LEFT, CENTER, RIGHT }

/** what sliding a song can do, chosen per side in Settings > Gesture */
enum class SongSwipeAction(val title: String) {
    Library("Add or remove from library"),
    Queue("Add to queue"),
}

/** Out of the box, sliding right saves the song and sliding left queues it. */
val DefaultSwipeRight = SongSwipeAction.Library
val DefaultSwipeLeft = SongSwipeAction.Queue

enum class LyricsMotionStyle(val displayName: String, val description: String) {
    MOTION("Style 1", "Words rise as they're sung, long notes swell, lines cascade"),
    LEGACY("Style 2", "The original wave across the sung letters"),
}

enum class LyricsAnimationStyle {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE,
    APPLE_V2,
    MUSICFY_1,
    LYRICS_V2,
    METRO_LYRICS,
}
