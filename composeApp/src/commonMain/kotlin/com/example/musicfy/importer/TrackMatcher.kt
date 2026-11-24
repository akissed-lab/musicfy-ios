package com.example.musicfy.importer

import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.SettingsStore
import com.music.innertube.SearchFilter
import com.music.innertube.YouTube
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Finds the YouTube Music song for a track from another service, scored on title, artist, length
 * and album. A song beats a music video unless none is close. No pinned-match store is used;
 * unmatched tracks are reported in progress instead.
 */
class TrackMatcher(
    private val hideExplicit: Boolean = false,
    private val hideVideoSongs: Boolean = false,
) {
    private val cache = HashMap<String, SongItem?>()
    private val cacheLock = Mutex()

    suspend fun match(track: ImportedTrack): SongItem? {
        track.ytItem?.let { return it }
        track.videoId?.let { id ->
            return SongItem(
                id = id,
                title = track.title,
                artists = splitArtists(track.artist).map { Artist(name = it, id = null) },
                album = null,
                duration = track.durationMs?.let { (it / 1000).toInt() }?.takeIf { it > 0 },
                thumbnail = track.thumbnailUrl ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            )
        }

        val key = "${normalizeTitle(track.title)}|${normalizeText(track.artist)}|${track.durationMs?.div(5000)}"
        val (searched, cached) = cacheLock.withLock { cache.containsKey(key) to cache[key] }
        return if (searched) {
            cached
        } else {
            findBest(track).also { result -> cacheLock.withLock { cache[key] = result } }
        }
    }

    companion object {
        fun from(settings: SettingsStore): TrackMatcher = TrackMatcher(
            hideExplicit = settings.getBoolean(SettingsKeys.HideExplicitKey, false),
            hideVideoSongs = settings.getBoolean(SettingsKeys.HideVideoSongsKey, false),
        )

        private const val SONG_ACCEPT = 0.62
        private const val VIDEO_ACCEPT = 0.7
        private const val SONG_FALLBACK_ACCEPT = 0.5
        private const val MIN_TITLE = 0.45

        /** Within this many seconds of the source's length, a result is the same recording. */
        const val CLOSE_SECONDS = 3.0
        private const val VERSION_PENALTY = 0.3

        private val VERSION_WORDS = listOf(
            "remix", "live", "instrumental", "inst", "acoustic", "karaoke", "cover", "sped up", "slowed",
            "nightcore", "reverb", "8d", "stage", "dance practice", "practice", "performance", "fancam",
            "concert", "rehearsal", "reaction", "1 hour", "hour", "loop", "extended",
            "japanese", "korean", "english", "chinese",
        )
        private val LIVE_ALBUM_WORDS = listOf("live", "tour", "concert", "unplugged", "session", "sessions")

        private val NOISE_IN_BRACKETS = Regex(
            """[(\[][^)\]]*\b(feat|ft|with|remaster|remastered|version|edit|mono|stereo|deluxe|explicit|clean|bonus|from|official|audio|video|lyrics?|visuali[sz]er|hd|hq|4k)\b[^)\]]*[)\]]""",
            RegexOption.IGNORE_CASE,
        )
        private val DASH_SUFFIX = Regex(
            """\s[-–—]\s.*(remaster|version|edit|mono|stereo|mix\b|from |live at|bonus).*$""",
            RegexOption.IGNORE_CASE,
        )
        private val FEAT_TAIL = Regex("""\s(feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
        private val ARTIST_SPLIT = Regex(
            """\s*(,|&|;|/|\sx\s|\sand\s|\sfeat\.?\s|\sft\.?\s|\sfeaturing\s|\swith\s)\s*""",
            RegexOption.IGNORE_CASE,
        )
        private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
        private val COMBINING_MARKS = Regex("""[\u0300-\u036f]+""")

        /**
         * CommonMain has no java.text.Normalizer. Fold the Latin marks services actually send
         * before stripping anything left over as combining marks.
         */
        private val DIACRITIC_FOLD = mapOf(
            'à' to "a", 'á' to "a", 'â' to "a", 'ã' to "a", 'ä' to "a", 'å' to "a", 'æ' to "ae",
            'ç' to "c", 'ć' to "c", 'č' to "c", 'ď' to "d", 'đ' to "d", 'ð' to "d",
            'è' to "e", 'é' to "e", 'ê' to "e", 'ë' to "e", 'ę' to "e", 'ě' to "e",
            'ì' to "i", 'í' to "i", 'î' to "i", 'ï' to "i", 'ł' to "l", 'ñ' to "n", 'ń' to "n",
            'ò' to "o", 'ó' to "o", 'ô' to "o", 'õ' to "o", 'ö' to "o", 'ø' to "o",
            'ř' to "r", 'ś' to "s", 'š' to "s", 'ş' to "s", 'ß' to "ss",
            'ù' to "u", 'ú' to "u", 'û' to "u", 'ü' to "u", 'ů' to "u", 'ý' to "y", 'ÿ' to "y",
            'ž' to "z", 'ż' to "z", 'ź' to "z", 'þ' to "th",
        )

        fun splitArtists(artist: String): List<String> =
            artist.replace('\u00A0', ' ').split(ARTIST_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

        private fun cleanTitleForSearch(title: String): String =
            title.replace(NOISE_IN_BRACKETS, " ").replace(DASH_SUFFIX, "").replace(FEAT_TAIL, "")
                .replace(Regex("""\s+"""), " ").trim()
                .ifEmpty { title }

        fun normalizeTitle(title: String): String = normalizeText(cleanTitleForSearch(title))

        fun normalizeText(text: String): String {
            val lower = text.lowercase()
            val folded = buildString(lower.length) {
                lower.forEach { ch ->
                    val replacement = DIACRITIC_FOLD[ch]
                    if (replacement != null) append(replacement) else append(ch)
                }
            }
            return folded.replace(COMBINING_MARKS, "").replace(NON_WORD, " ").trim()
        }

        /** Best of token overlap and character edit distance, so reordered words and scripts without spaces score fairly. */
        fun similarity(a: String, b: String): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            if (a == b) return 1.0
            val tokensA = a.split(' ').toSet()
            val tokensB = b.split(' ').toSet()
            val intersection = tokensA.intersect(tokensB).size
            val jaccard = intersection.toDouble() / (tokensA.size + tokensB.size - intersection)
            val compactA = a.replace(" ", "")
            val compactB = b.replace(" ", "")
            val edit = 1.0 - levenshtein(compactA, compactB).toDouble() / maxOf(compactA.length, compactB.length)
            val containment = if (compactA.length >= 3 && compactB.length >= 3 &&
                (compactA.contains(compactB) || compactB.contains(compactA))
            ) 0.8 else 0.0
            return maxOf(jaccard, edit, containment)
        }

        private fun levenshtein(a: String, b: String): Int {
            if (a.length > 200 || b.length > 200) return maxOf(a.length, b.length) - minOf(a.length, b.length)
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return prev[b.length]
        }
    }

    private suspend fun findBest(track: ImportedTrack): SongItem? {
        val query = searchQuery(track)

        val songs = search(query, SearchFilter.FILTER_SONG).map { it to score(track, it) }
        pick(track, songs, SONG_ACCEPT)?.let { return it }

        // Some tracks only exist as uploads/videos on YouTube.
        val videos = search(query, SearchFilter.FILTER_VIDEO).map { it to score(track, it) }
        pick(track, videos, VIDEO_ACCEPT)?.let { return it }

        return pick(track, songs, SONG_FALLBACK_ACCEPT)
    }

    /** Best result at or above accept, looking first at ones within a few seconds of the source length. */
    private fun pick(track: ImportedTrack, scored: List<Pair<SongItem, Double>>, accept: Double): SongItem? {
        val sourceSeconds = track.durationMs?.takeIf { it > 0 }?.div(1000.0)
        if (sourceSeconds != null) {
            scored.filter { (song, _) -> song.duration?.let { abs(it - sourceSeconds) <= CLOSE_SECONDS } == true }
                .maxByOrNull { it.second }
                ?.takeIf { it.second >= accept }
                ?.let { return it.first }
        }
        return scored.maxByOrNull { it.second }?.takeIf { it.second >= accept }?.first
    }

    private fun searchQuery(track: ImportedTrack): String {
        val firstArtist = splitArtists(track.artist).firstOrNull().orEmpty()
        return "${cleanTitleForSearch(track.title)} $firstArtist".trim()
    }

    /** Up to ten songs then ten videos, with local preference filters applied before scoring. */
    private suspend fun search(query: String, filter: SearchFilter): List<SongItem> {
        repeat(2) { attempt ->
            try {
                val result = YouTube.search(query, filter).getOrThrow()
                return result.items.filterIsInstance<SongItem>()
                    .filter { !hideExplicit || !it.explicit }
                    .filter { !hideVideoSongs || !it.isVideoSong }
                    .take(10)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt == 0) delay(800)
            }
        }
        return emptyList()
    }

    private fun score(track: ImportedTrack, candidate: SongItem): Double {
        val targetTitle = normalizeTitle(track.title)
        val candidateTitle = normalizeTitle(candidate.title)
        var titleScore = similarity(targetTitle, candidateTitle)

        val targetArtists = splitArtists(track.artist).map(::normalizeText).filter { it.isNotEmpty() }
        val candidateArtists = candidate.artists.map { normalizeText(it.name) }.filter { it.isNotEmpty() }
        var artistScore = if (targetArtists.isEmpty()) 0.5 else targetArtists.maxOf { target ->
            candidateArtists.maxOfOrNull { similarity(target, it) } ?: 0.0
        }

        // Videos are often titled "artist - title (official video)".
        if (artistScore < 0.6 && targetArtists.any { it.isNotEmpty() && candidateTitle.contains(it) }) {
            artistScore = 0.8
            titleScore = maxOf(titleScore, similarity(targetTitle, targetArtists.fold(candidateTitle) { acc, a -> acc.replace(a, " ") }.trim()))
        }

        val durationScore = durationScore(track.durationMs, candidate.duration)

        // A remix/live take the source didn't ask for is a different song. Whole words on the raw
        // titles, since cleaning drops live markers and "live" is inside "alive".
        val rawTarget = " ${normalizeText(track.title)} "
        val rawCandidate = " ${normalizeText(candidate.title)} "
        val versionPenalty = VERSION_WORDS.count { word ->
            rawCandidate.contains(" $word ") != rawTarget.contains(" $word ")
        } * VERSION_PENALTY

        if (titleScore < MIN_TITLE) return 0.0
        return titleScore * 0.5 + artistScore * 0.25 + durationScore * 0.25 +
            albumScore(track.album, candidate.album?.name) + uploadScore(candidate) - versionPenalty
    }

    /** A small lift for the same album, a penalty for a live album the source isn't on. */
    private fun albumScore(sourceAlbum: String?, candidateAlbum: String?): Double {
        if (candidateAlbum.isNullOrBlank()) return 0.0
        val candidate = " ${normalizeText(candidateAlbum)} "
        val source = " ${normalizeText(sourceAlbum.orEmpty())} "
        val liveAlbum = LIVE_ALBUM_WORDS.any { candidate.contains(" $it ") && !source.contains(" $it ") }
        if (liveAlbum) return -VERSION_PENALTY
        if (sourceAlbum.isNullOrBlank()) return 0.0
        return if (similarity(source.trim(), candidate.trim()) >= 0.8) 0.05 else 0.0
    }

    /** The artist's own audio (a "topic" track) is the album version; a fan upload often isn't. */
    private fun uploadScore(candidate: SongItem): Double = when (candidate.musicVideoType) {
        "MUSIC_VIDEO_TYPE_ATV" -> 0.05
        "MUSIC_VIDEO_TYPE_UGC", "MUSIC_VIDEO_TYPE_UGC_WITHOUT_ADS" -> -0.08
        null -> if (candidate.album != null) 0.05 else 0.0
        else -> 0.0
    }

    private fun durationScore(sourceMs: Long?, candidateSeconds: Int?): Double {
        if (sourceMs == null || sourceMs <= 0 || candidateSeconds == null || candidateSeconds <= 0) return 0.5
        val diff = abs(sourceMs / 1000.0 - candidateSeconds)
        return when {
            diff <= CLOSE_SECONDS -> 1.0
            diff <= 8 -> 0.6
            diff <= 20 -> 0.1
            diff <= 60 -> -0.3
            else -> -1.0
        }
    }
}
