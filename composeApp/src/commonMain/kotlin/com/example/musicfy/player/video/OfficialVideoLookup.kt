package com.example.musicfy.player.video

import com.example.musicfy.player.ResolvedStream
import com.music.innertube.YouTube
import com.music.innertube.YouTubePlayer
import com.music.innertube.models.MUSIC_VIDEO_TYPE_OMV
import com.music.innertube.models.SongItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * KMP port of upstream's official-video lookup, using our Innertube search and player slices.
 *
 * Search misses and stream-resolution failures are never cached as a "no video" answer: the next
 * request retries them. The id cache stores only successful searches, and the url cache stores only
 * successfully resolved streams whose links have not expired.
 */
object OfficialVideoLookup {
    /** The mini player and the full video mode both ask for the playing song; one lookup is enough. */
    private val lookupLock = Mutex()

    suspend fun findOfficialMusicVideoId(title: String, artist: String): String? {
        if (title.isBlank() || artist.isBlank()) return null
        val key = "$title|$artist"
        OfficialVideoIdCache.get(key)?.let { return it }

        val results = YouTube.search(
            query = "$title $artist",
            filter = YouTube.SearchFilter.FILTER_VIDEO,
        ).getOrNull()?.items ?: return null

        val normalizedTitle = normalizeSongTitle(title)
        val normalizedArtists = normalizedArtistNames(artist)

        val match = results
            .filterIsInstance<SongItem>()
            .filter { it.musicVideoType == MUSIC_VIDEO_TYPE_OMV }
            .firstOrNull { candidate ->
                val candidateTitle = normalizeSongTitle(candidate.title)
                val candidateArtists = candidate.artists.flatMap { credited -> normalizedArtistNames(credited.name) }
                titlesOverlap(normalizedTitle, candidateTitle) &&
                    normalizedArtists.any { credited ->
                        candidateArtists.any { candidate -> namesOverlap(credited, candidate) }
                    }
            } ?: return null

        OfficialVideoIdCache.put(key, match.id)
        return match.id
    }

    suspend fun findOfficialMusicVideo(title: String, artist: String): OfficialMusicVideo? {
        val videoId = findOfficialMusicVideoId(title, artist) ?: return null
        val streams = resolveStreams(videoId) ?: return null
        return OfficialMusicVideo(videoId = videoId, streams = streams)
    }

    /** A track that is itself a video plays its own stream, which lines up with the audio exactly. */
    suspend fun ownMusicVideo(mediaId: String): OfficialMusicVideo? {
        if (mediaId.isBlank()) return null
        val streams = resolveStreams(mediaId) ?: return null
        return OfficialMusicVideo(videoId = mediaId, streams = streams)
    }

    /** Find the video for a track, reusing a fresh cached resolution when one exists. */
    suspend fun lookUpVideo(
        mediaId: String,
        title: String,
        artist: String,
        isVideoSong: Boolean = false,
        videoIdOverride: String? = null,
    ): OfficialMusicVideo? = lookupLock.withLock {
        if (mediaId.isBlank()) return@withLock null
        val picked = videoIdOverride?.takeIf { it.isNotBlank() }
        val key = videoCacheKey(mediaId, isVideoSong, picked)
        if (OfficialVideoUrlCache.contains(key)) return@withLock OfficialVideoUrlCache.get(key)

        if (picked == null && !isVideoSong && (title.isBlank() || artist.isBlank())) return@withLock null
        val resolved = when {
            picked != null -> ownMusicVideo(picked)
            isVideoSong -> ownMusicVideo(mediaId)
            else -> findOfficialMusicVideo(title, artist)
        }
        // A miss is never stored: it can be a transient network or player failure.
        resolved?.let { OfficialVideoUrlCache.put(key, it) }
        resolved
    }

    /** For the first frame of a video background: cached only, no network. */
    suspend fun cachedVideo(
        mediaId: String,
        isVideoSong: Boolean = false,
        videoIdOverride: String? = null,
    ): OfficialMusicVideo? {
        if (mediaId.isBlank()) return null
        val key = videoCacheKey(mediaId, isVideoSong, videoIdOverride)
        return if (OfficialVideoUrlCache.contains(key)) OfficialVideoUrlCache.get(key) else null
    }

    suspend fun hasFoundVideo(
        mediaId: String,
        isVideoSong: Boolean = false,
        videoIdOverride: String? = null,
    ): Boolean = cachedVideo(mediaId, isVideoSong, videoIdOverride) != null

    /** A picked video, a video track itself, and a song's official video all need separate keys. */
    fun videoCacheKey(
        mediaId: String,
        isVideoSong: Boolean,
        videoIdOverride: String? = null,
    ): String = when {
        !videoIdOverride.isNullOrBlank() -> "pick:$videoIdOverride"
        isVideoSong -> "self:$mediaId"
        else -> mediaId
    }

    // Our commonMain player slice resolves a direct stream; it is the KMP stand-in for upstream's
    // YTPlayerUtils video-stream resolver until a video-track path exists in this port.
    private suspend fun resolveStreams(videoId: String): ResolvedStream? =
        YouTubePlayer.resolve(videoId, YouTube.visitorData).getOrNull()
}

// Upstream-shaped entry points, so call sites can be ported without learning a new facade.
suspend fun findOfficialMusicVideoId(title: String, artist: String): String? =
    OfficialVideoLookup.findOfficialMusicVideoId(title, artist)

suspend fun findOfficialMusicVideo(title: String, artist: String): OfficialMusicVideo? =
    OfficialVideoLookup.findOfficialMusicVideo(title, artist)

suspend fun ownMusicVideo(mediaId: String): OfficialMusicVideo? =
    OfficialVideoLookup.ownMusicVideo(mediaId)

suspend fun lookUpVideo(
    mediaId: String,
    title: String,
    artist: String,
    isVideoSong: Boolean = false,
    videoIdOverride: String? = null,
): OfficialMusicVideo? = OfficialVideoLookup.lookUpVideo(
    mediaId = mediaId,
    title = title,
    artist = artist,
    isVideoSong = isVideoSong,
    videoIdOverride = videoIdOverride,
)

private val Whitespace = Regex("\\s+")

private val BracketedTag = Regex("\\s*\\[[^]]*]")

private val FeaturingTag = Regex(
    "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
    RegexOption.IGNORE_CASE,
)

private val VideoTag = Regex(
    "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
    RegexOption.IGNORE_CASE,
)

private val TrailingVideoTag = Regex(
    "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
    RegexOption.IGNORE_CASE,
)

/** Mirrors upstream `normalizeCanvasSongTitle`, used on both sides of the comparison. */
internal fun normalizeSongTitle(raw: String): String =
    raw.replace(BracketedTag, "")
        .replace(FeaturingTag, "")
        .replace(VideoTag, "")
        .replace(TrailingVideoTag, "")
        .replace(Whitespace, " ")
        .trim()
        .trim('-')
        .replace(Whitespace, " ")
        .trim()

private val ArtistNameSeparators = Regex(
    """\s*(?:,|&|×|\bx\b|\bfeat\.?|\bft\.?|\bfeaturing\b|\bwith\b)\s*""",
    RegexOption.IGNORE_CASE,
)

/** Mirrors upstream `normalizeCanvasArtistName`. */
internal fun normalizeArtistName(raw: String): String =
    raw.split(ArtistNameSeparators, limit = 2).firstOrNull().orEmpty()
        .replace(Whitespace, " ")
        .trim()

/**
 * Keep every credited artist: YouTube can list collaborators in a different order, so a single
 * normalized "first artist" is not enough to decide whether a candidate is the same song.
 */
private fun normalizedArtistNames(raw: String): List<String> =
    raw.split(ArtistNameSeparators)
        .map(::normalizeArtistName)
        .filter { it.isNotBlank() }

private fun titlesOverlap(a: String, b: String): Boolean =
    a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)

private fun namesOverlap(a: String, b: String): Boolean =
    a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)
