// LyricsHelper.kt
//
// KMP port of upstream LyricsHelper: provider order, candidate grading, cache,
// offline handling and the source picker's candidate list. Android Context,
// DataStore and ConnectivityManager are replaced by SettingsStore and
// NetworkConnectivityObserver; LruCache is a small common implementation.

package com.example.musicfy.lyrics

import com.example.musicfy.constants.PreferredLyricsProvider
import com.example.musicfy.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.SettingsStore
import com.example.musicfy.utils.OfflineMode
import com.example.musicfy.utils.NetworkConnectivityObserver
import com.example.musicfy.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LyricsHelper(
    private val settings: SettingsStore,
    private val networkConnectivity: NetworkConnectivityObserver = NetworkConnectivityObserver(),
) {
    private val cache = SimpleLruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private val bestCache = SimpleLruCache<String, LyricsWithProvider>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val orderString = settings.getString(SettingsKeys.LyricsProviderOrderKey, "")
        if (orderString.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(orderString)
        }
        val preferred = settings
            .getString(SettingsKeys.PreferredLyricsProviderKey, "")
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferred)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        return (listOf(preferredName) + defaultOrder.filter { it != preferredName })
            .mapNotNull(LyricsProviderRegistry::getProviderByName)
    }

    /**
     * Picks the best lyrics out of the enabled providers, in preference order.
     *
     * Grading compares original script, sync quality, voice tags and finally
     * provider order. Every enabled provider is queried because the script
     * comparison needs the full candidate set.
     */
    suspend fun getLyrics(
        mediaMetadata: MediaMetadata,
        forceRefresh: Boolean = false,
    ): LyricsWithProvider {
        if (forceRefresh) {
            bestCache.remove(mediaMetadata.id)
        } else {
            bestCache.get(mediaMetadata.id)?.let { return it }
        }

        // An imported file's own tags, before anything goes online.
        mediaMetadata.localUri?.let { uri ->
            withContext(Dispatchers.Default) { readEmbeddedLyrics(uri) }?.let { lyrics ->
                return LyricsWithProvider(lyrics, EmbeddedLyrics.Provider)
                    .also { bestCache.put(mediaMetadata.id, it) }
            }
        }

        if (!isNetworkAvailable()) {
            // Not "not found": nothing was asked, so callers must not save this.
            return LyricsWithProvider(LYRICS_NOT_FOUND, NoConnectionProvider)
        }

        val providers = resolveLyricsProviders()
        val (lastResort, regular) = providers.withIndex()
            .partition { it.value.name == YouTubeCaptionsLyricsProvider.name }
        var candidates = fetchIndexed(mediaMetadata, regular)
        var best = withContext(Dispatchers.Default) {
            pickBest(mediaMetadata, candidates, providers.size)
        }
        if (best == null && lastResort.isNotEmpty()) {
            candidates = candidates + fetchIndexed(mediaMetadata, lastResort)
            best = withContext(Dispatchers.Default) {
                pickBest(mediaMetadata, candidates, providers.size)
            }
        }
        if (best == null) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        val result = LyricsWithProvider(best.lyrics, best.providerName)
        bestCache.put(mediaMetadata.id, result)
        return result
    }

    /** One provider's answer, graded, for the source picker. */
    data class ProviderCandidate(
        val providerName: String,
        val lyrics: String,
        val order: Int,
        val sync: LyricsUtils.SyncKind,
        val script: LyricsUtils.Script,
        val language: String? = null,
    ) {
        val hasDuet: Boolean get() = LyricsUtils.hasDistinctVoices(lyrics)
    }

    /** Asks every enabled provider at once. */
    suspend fun fetchCandidates(
        mediaMetadata: MediaMetadata,
        providers: List<LyricsProvider>? = null,
    ): List<ProviderCandidate> =
        fetchIndexed(mediaMetadata, (providers ?: resolveLyricsProviders()).withIndex().toList())

    private suspend fun fetchIndexed(
        mediaMetadata: MediaMetadata,
        providers: List<IndexedValue<LyricsProvider>>,
    ): List<ProviderCandidate> = coroutineScope {
        providers.map { (order, provider) ->
            async(Dispatchers.Default) { fetchCandidate(mediaMetadata, provider, order) }
        }.awaitAll().filterNotNull()
    }

    /** The enabled providers, in the user's preferred order. */
    suspend fun orderedProviders(): List<LyricsProvider> = resolveLyricsProviders()

    fun isProviderEnabled(provider: LyricsProvider): Boolean = provider.isEnabled(settings)

    /** Null when the provider is switched off, has nothing, fails or times out. */
    suspend fun fetchCandidate(
        mediaMetadata: MediaMetadata,
        provider: LyricsProvider,
        order: Int,
    ): ProviderCandidate? {
        if (!provider.isEnabled(settings) || OfflineMode.isOn) return null
        val lyrics = try {
            withTimeoutOrNull(ProviderTimeoutMs) {
                provider.getLyrics(
                    mediaMetadata.id,
                    mediaMetadata.title,
                    mediaMetadata.artists.joinToString { it.name },
                    mediaMetadata.duration,
                    mediaMetadata.album?.title,
                ).onFailure { reportException(it) }.getOrNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            null
        }
        if (lyrics.isNullOrBlank() || lyrics == LYRICS_NOT_FOUND) return null
        return ProviderCandidate(
            providerName = provider.name,
            lyrics = lyrics,
            order = order,
            sync = LyricsUtils.syncKind(lyrics),
            script = LyricsUtils.lyricsScript(lyrics),
            language = LyricsUtils.latinLanguage(lyrics),
        ).takeIf { it.sync != LyricsUtils.SyncKind.NONE }
    }

    /** Remembers a source the user picked by hand, so the next lookup returns it. */
    fun rememberChoice(mediaId: String, choice: LyricsWithProvider) {
        bestCache.put(mediaId, choice)
    }

    private suspend fun isNetworkAvailable(): Boolean =
        !OfflineMode.isOn && try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

    /**
     * Picks the lyrics most likely to be the song as sung. Language comes from
     * evidence about the song first, then from what most sources agree on.
     */
    private fun pickBest(
        mediaMetadata: MediaMetadata,
        candidates: List<ProviderCandidate>,
        providerCount: Int,
    ): ProviderCandidate? {
        val metadataScript = LyricsUtils.dominantScript(
            listOfNotNull(
                mediaMetadata.title,
                mediaMetadata.artists.joinToString(" ") { it.name },
                mediaMetadata.album?.title,
            ).joinToString(" "),
        )
        val referenceScript = (
            metadataScript.takeIf(::isNative)
                ?: candidates
                    .filter { it.providerName in LanguageReferenceProviders }
                    .map { it.script }
                    .firstOrNull(::isNative)
            )?.let { script ->
            if (script == LyricsUtils.Script.HAN && candidates.any { it.script == LyricsUtils.Script.KANA }) {
                LyricsUtils.Script.KANA
            } else {
                script
            }
        }

        val chineseSong = isChineseSong(referenceScript, candidates)
        val songLanguage = songLanguage(referenceScript, candidates)
        val pool = eligible(candidates, chineseSong, songLanguage)
        if (pool.isEmpty()) return null

        val languages = languageKeys(pool)
        val majority = if (referenceScript == null) majorityLanguage(languages, minVotes = 2) else null

        val trustedNative: Set<LyricsUtils.Script> = if (referenceScript != null || majority != null) {
            emptySet()
        } else {
            pool.map { it.script }
                .filter(::isNative)
                .groupingBy { it }
                .eachCount()
                .filter { (script, count) ->
                    count >= 2 || script == LyricsUtils.Script.KANA || script == LyricsUtils.Script.HANGUL
                }
                .keys
        }

        return pool.withIndex().maxByOrNull { (index, candidate) ->
            val scriptScore = when {
                referenceScript != null -> when {
                    candidate.script == referenceScript -> 3
                    sameLanguageFamily(candidate.script, referenceScript) -> 2
                    candidate.script == LyricsUtils.Script.UNKNOWN -> 1
                    else -> 0
                }
                majority != null -> when {
                    languages[index] == majority -> 3
                    candidate.script == LyricsUtils.Script.UNKNOWN -> 1
                    else -> 0
                }
                trustedNative.isNotEmpty() -> when {
                    candidate.script in trustedNative -> 2
                    candidate.script == LyricsUtils.Script.UNKNOWN -> 1
                    else -> 0
                }
                isNative(candidate.script) -> 0
                else -> 1
            }

            val syncScore = if (
                candidate.sync == LyricsUtils.SyncKind.WORD &&
                LyricsUtils.isSyllableSplit(candidate.lyrics)
            ) {
                LyricsUtils.SyncKind.LINE.ordinal
            } else {
                candidate.sync.ordinal
            }

            val duetScore = when {
                candidate.hasDuet -> 2
                LyricsUtils.hasVoiceTags(candidate.lyrics) -> 1
                else -> 0
            }

            val orderScore = if (chineseSong && candidate.providerName in ChineseSources) {
                providerCount * 2 - candidate.order
            } else {
                providerCount - candidate.order
            }

            scriptScore * ScriptWeight +
                syncScore * SyncWeight +
                duetScore * DuetWeight +
                orderScore
        }?.value
    }

    private fun languageKeys(candidates: List<ProviderCandidate>): List<String> {
        val keys = candidates.map { candidate ->
            if (candidate.script == LyricsUtils.Script.LATIN) {
                candidate.language?.let { LatinPrefix + it } ?: LyricsUtils.Script.LATIN.name
            } else {
                candidate.script.name
            }
        }
        val commonLatin = keys.filter { it.startsWith(LatinPrefix) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        return keys.map { key ->
            if (key == LyricsUtils.Script.LATIN.name && commonLatin != null) commonLatin else key
        }
    }

    private fun majorityLanguage(keys: List<String>, minVotes: Int): String? {
        val ranked = keys.filter { it != LyricsUtils.Script.UNKNOWN.name }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
        val top = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)?.value ?: 0
        return top.key.takeIf { top.value >= minVotes && top.value > runnerUp }
    }

    private fun isChineseSong(
        referenceScript: LyricsUtils.Script?,
        candidates: List<ProviderCandidate>,
    ): Boolean = if (referenceScript != null) {
        referenceScript == LyricsUtils.Script.HAN
    } else {
        val others = candidates.filter { it.providerName !in ChineseSources }
        candidates.none { it.script == LyricsUtils.Script.KANA } &&
            majorityLanguage(languageKeys(others), minVotes = 1) == LyricsUtils.Script.HAN.name
    }

    private fun eligible(
        candidates: List<ProviderCandidate>,
        chineseSong: Boolean,
        songLanguage: String?,
    ): List<ProviderCandidate> {
        val (chinese, rest) = candidates.partition { it.providerName in ChineseSources }
        val (captions, main) = rest.partition { it.providerName == YouTubeCaptionsLyricsProvider.name }
        val wordTimedOriginals = chinese.filter { candidate ->
            candidate.sync == LyricsUtils.SyncKind.WORD &&
                candidate.script != LyricsUtils.Script.HAN &&
                songLanguage != null &&
                languageKey(candidate, songLanguage) == songLanguage
        }
        return (if (chineseSong) main + chinese else main + wordTimedOriginals)
            .ifEmpty { chinese.filter { it.script != LyricsUtils.Script.HAN } }
            .ifEmpty { captions }
    }

    private fun songLanguage(
        referenceScript: LyricsUtils.Script?,
        candidates: List<ProviderCandidate>,
    ): String? {
        if (referenceScript != null) return referenceScript.name
        val others = candidates.filter {
            it.providerName !in ChineseSources && it.providerName != YouTubeCaptionsLyricsProvider.name
        }
        return majorityLanguage(languageKeys(others), minVotes = 1)
    }

    private fun languageKey(candidate: ProviderCandidate, songLanguage: String): String = when {
        candidate.script == LyricsUtils.Script.LATIN ->
            candidate.language?.let { LatinPrefix + it }
                ?: songLanguage.takeIf {
                    it.startsWith(LatinPrefix) || it == LyricsUtils.Script.LATIN.name
                }
                ?: LyricsUtils.Script.LATIN.name
        candidate.script == LyricsUtils.Script.KANA && songLanguage == LyricsUtils.Script.HAN.name -> songLanguage
        else -> candidate.script.name
    }

    private fun isNative(script: LyricsUtils.Script): Boolean =
        script != LyricsUtils.Script.LATIN && script != LyricsUtils.Script.UNKNOWN

    /** Kanji-only Japanese titles read as Han; Japanese lyrics read as kana. Same song. */
    private fun sameLanguageFamily(a: LyricsUtils.Script, b: LyricsUtils.Script): Boolean {
        val cjk = setOf(LyricsUtils.Script.HAN, LyricsUtils.Script.KANA)
        return a in cjk && b in cjk
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()
        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach(callback)
            return
        }
        if (!isNetworkAvailable()) return

        val allResult = mutableListOf<LyricsResult>()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            resolveLyricsProviders().forEach { provider ->
                if (provider.isEnabled(settings)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            cache.put(cacheKey, allResult)
        }
        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    /** Small commonMain LRU: access order is maintained explicitly (no JVM-only LinkedHashMap flag). */
    private class SimpleLruCache<K, V>(private val maxSize: Int) {
        private val map = LinkedHashMap<K, V>()

        fun get(key: K): V? {
            val value = map.remove(key) ?: return null
            map[key] = value
            return value
        }

        fun put(key: K, value: V) {
            if (map.containsKey(key)) map.remove(key)
            map[key] = value
            while (map.size > maxSize) {
                val eldest = map.keys.first()
                map.remove(eldest)
            }
        }

        fun remove(key: K) {
            map.remove(key)
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3

        /** A lookup that couldn't go online; callers must not save its "not found". */
        const val NoConnectionProvider = "NoConnection"

        private const val ProviderTimeoutMs = 35_000L
        private val LanguageReferenceProviders = setOf("YouTubeMusic", "YouTubeSubtitle")
        private val ChineseSources = setOf(
            KuGouLyricsProvider.name,
            NetEaseLyricsProvider.name,
            QQMusicLyricsProvider.name,
        )
        private const val LatinPrefix = "latin:"
        private const val DuetWeight = 100
        private const val SyncWeight = 1_000
        private const val ScriptWeight = 10_000
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)

private inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T =
    if (this == null) {
        defaultValue
    } else {
        try {
            enumValueOf<T>(this)
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }
