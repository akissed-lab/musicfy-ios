// LyricsTranslationHelper.kt
//
// KMP port of the upstream translation orchestrator. The keyless Google path
// works out of the box; the AI path keeps the same provider switch and status
// machine but receives its transport as a lambda until the OpenRouter/DeepL/
// Mistral services are ported. Database access is likewise injected as two
// suspend functions so the helper does not depend on a platform DAO.

package com.example.musicfy.lyrics

import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object LyricsTranslationHelper {
    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _translationOn = MutableStateFlow(false)
    val translationOn: StateFlow<Boolean> = _translationOn.asStateFlow()

    private fun triggerFlow() = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _manualTrigger = triggerFlow()
    val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

    private val _clearTranslationsTrigger = triggerFlow()
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

    private val _translationSaved = triggerFlow()
    val translationSaved: SharedFlow<Unit> = _translationSaved.asSharedFlow()

    private var translationJob: Job? = null
    private var isCompositionActive = true
    private var lastSongId: String? = null

    private val translationCache = mutableMapOf<String, List<String>>()

    private fun cacheKey(lyricsText: String, mode: String, language: String): String =
        "${lyricsText.hashCode()}_${mode}_$language"

    /** Parses a partially streamed JSON array of strings, up to [expectedCount]. */
    internal fun tryParsePartialTranslation(content: String, expectedCount: Int): List<String> {
        val start = content.indexOf('[')
        if (start < 0) return emptyList()
        val result = ArrayList<String>()
        val current = StringBuilder()
        var pos = start + 1
        var inString = false
        var escaping = false
        while (pos < content.length && result.size < expectedCount) {
            val ch = content[pos]
            when {
                escaping -> {
                    current.append(ch)
                    escaping = false
                }
                ch == '\\' && inString -> {
                    current.append(ch)
                    escaping = true
                }
                ch == '"' -> {
                    if (inString) {
                        result += current.toString()
                        current.clear()
                        inString = false
                    } else {
                        inString = true
                    }
                }
                inString -> current.append(ch)
                ch == ']' -> break
            }
            pos++
        }
        return result
    }

    fun getCachedTranslations(
        lyrics: List<LyricsEntry>,
        mode: String,
        language: String,
    ): List<String>? {
        val text = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        return translationCache[cacheKey(text, mode, language)]
    }

    fun applyCachedTranslations(
        lyrics: List<LyricsEntry>,
        mode: String,
        language: String,
    ): Boolean {
        val cached = getCachedTranslations(lyrics, mode, language) ?: return false
        val indexed = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }
        if (cached.size < indexed.size) return false
        indexed.forEachIndexed { i, (index, _) ->
            lyrics[index].translatedTextFlow.value = cached[i]
        }
        return true
    }

    /**
     * Just the target when the source was detected, so existing stored
     * translations still match; "source>target" when the user picked a source.
     */
    fun languageTag(sourceLanguage: String, targetLanguage: String): String =
        if (sourceLanguage.isBlank() || sourceLanguage == "auto") {
            targetLanguage
        } else {
            "$sourceLanguage>$targetLanguage"
        }

    fun triggerManualTranslation() {
        _translationOn.value = true
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearTranslations() {
        _translationOn.value = false
        _hasActiveTranslations.value = false
        translationJob?.cancel()
        translationJob = null
        _status.value = TranslationStatus.Idle
        _clearTranslationsTrigger.tryEmit(Unit)
    }

    /** Translation carries to the next song only in session-wide mode. */
    fun onSongChanged(songId: String?, sessionWide: Boolean) {
        if (songId == null) return
        val previous = lastSongId
        if (previous != null && previous != songId && !sessionWide) {
            _translationOn.value = false
            _hasActiveTranslations.value = false
        }
        lastSongId = songId
    }

    fun hasTranslations(entity: LyricsEntity?): Boolean = !entity?.translatedLyrics.isNullOrBlank()

    fun clearTranslations(entity: LyricsEntity): LyricsEntity = entity.copy(
        translatedLyrics = "",
        translationLanguage = "",
        translationMode = "",
    )

    fun resetStatus() {
        _status.value = TranslationStatus.Idle
    }

    fun clearCache() {
        translationCache.clear()
    }

    fun setCompositionActive(active: Boolean) {
        isCompositionActive = active
    }

    fun cancelTranslation() {
        isCompositionActive = false
        translationJob?.cancel()
        translationJob = null
    }

    /** @return true when stored translations matching [targetLanguage] and [mode] were applied. */
    fun loadTranslationsFromDatabase(
        lyrics: List<LyricsEntry>,
        entity: LyricsEntity?,
        targetLanguage: String,
        mode: String,
    ): Boolean {
        lyrics.forEach { it.translatedTextFlow.value = null }
        val stored = entity?.translatedLyrics
        if (stored.isNullOrBlank() || entity.translationLanguage != targetLanguage || entity.translationMode != mode) {
            _hasActiveTranslations.value = false
            return false
        }
        val translatedLines = stored.lines()
        val indexed = lyrics.mapIndexedNotNull { index, entry ->
            if (entry.text.isNotBlank()) index to entry else null
        }
        indexed.forEachIndexed { i, (index, _) ->
            lyrics[index].translatedTextFlow.value = translatedLines.getOrNull(i)
        }
        val text = indexed.joinToString("\n") { it.second.text }
        translationCache[cacheKey(text, mode, targetLanguage)] = translatedLines
        _hasActiveTranslations.value = true
        return true
    }

    private suspend fun persist(
        songId: String,
        lines: List<String>,
        language: String,
        mode: String,
        load: suspend (String) -> LyricsEntity?,
        save: suspend (LyricsEntity) -> Unit,
    ) {
        if (songId.isBlank()) return
        try {
            val current = load(songId) ?: return
            save(
                current.copy(
                    translatedLyrics = lines.joinToString("\n"),
                    translationLanguage = language,
                    translationMode = mode,
                ),
            )
            _translationSaved.tryEmit(Unit)
        } catch (e: Exception) {
            if (e !is CancellationException) reportException(e)
        }
    }

    /** Keyless translation: the default when no AI credentials are configured. */
    fun translateWithGoogle(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        mode: String,
        sourceLanguage: String = "auto",
        scope: CoroutineScope,
        songId: String = "",
        loadEntity: suspend (String) -> LyricsEntity? = { null },
        saveEntity: suspend (LyricsEntity) -> Unit = {},
    ) {
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating
        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob = scope.launchTranslation {
            val indexed = lyrics.mapIndexedNotNull { index, entry ->
                if (entry.text.isNotBlank()) index to entry else null
            }
            if (indexed.isEmpty()) {
                _status.value = TranslationStatus.Idle
                return@launchTranslation
            }
            val translated = GoogleTranslateEngine.translate(
                lines = indexed.map { it.second.text },
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage.ifBlank { "auto" },
            )
            if (!isCompositionActive) return@launchTranslation
            if (translated == null) {
                _status.value = TranslationStatus.Error("Translation failed")
                return@launchTranslation
            }

            indexed.forEachIndexed { i, (index, entry) ->
                val value = translated.getOrNull(i)
                    ?.takeUnless { it.trim().equals(entry.text.trim(), ignoreCase = true) }
                lyrics[index].translatedTextFlow.value = value
            }
            _hasActiveTranslations.value = true
            _status.value = TranslationStatus.Success

            val text = indexed.joinToString("\n") { it.second.text }
            val tag = languageTag(sourceLanguage, targetLanguage)
            translationCache[cacheKey(text, mode, tag)] = translated
            persist(songId, translated, tag, mode, loadEntity, saveEntity)
        }
    }

    /**
     * AI translation. [translate] owns the provider-specific HTTP call; the
     * helper keeps the cache, status, partial-line application and persistence
     * identical to upstream.
     */
    fun translateWithAI(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        mode: String,
        scope: CoroutineScope,
        translate: suspend (fullText: String, languageName: String) -> Result<List<String>>,
        songId: String = "",
        loadEntity: suspend (String) -> LyricsEntity? = { null },
        saveEntity: suspend (LyricsEntity) -> Unit = {},
    ) {
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating
        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob = scope.launchTranslation {
            val indexed = lyrics.mapIndexedNotNull { index, entry ->
                if (entry.text.isNotBlank()) index to entry else null
            }
            if (indexed.isEmpty()) {
                _status.value = TranslationStatus.Error("No lyrics to translate")
                return@launchTranslation
            }
            val fullText = indexed.joinToString("\n") { it.second.text }
            val key = cacheKey(fullText, mode, targetLanguage)
            val cached = translationCache[key]
            if (cached != null && cached.size >= indexed.size) {
                indexed.forEachIndexed { i, (index, _) ->
                    lyrics[index].translatedTextFlow.value = cached.getOrNull(i)
                }
                _hasActiveTranslations.value = true
                _status.value = TranslationStatus.Success
                persist(songId, cached, targetLanguage, mode, loadEntity, saveEntity)
                delay(3_000)
                if (_status.value is TranslationStatus.Success && isCompositionActive) {
                    _status.value = TranslationStatus.Idle
                }
                return@launchTranslation
            }
            if (targetLanguage.isBlank()) {
                _status.value = TranslationStatus.Error("Translation language required")
                return@launchTranslation
            }

            val languageName = LanguageCodeToName[targetLanguage] ?: targetLanguage
            val result = runCatching { translate(fullText, languageName).getOrThrow() }
            if (!isCompositionActive) return@launchTranslation
            result.onSuccess { lines ->
                translationCache[key] = lines
                indexed.forEachIndexed { i, (index, _) ->
                    lyrics[index].translatedTextFlow.value = lines.getOrNull(i)
                }
                _hasActiveTranslations.value = true
                _status.value = TranslationStatus.Success
                persist(songId, lines, targetLanguage, mode, loadEntity, saveEntity)
                delay(3_000)
                if (_status.value is TranslationStatus.Success && isCompositionActive) {
                    _status.value = TranslationStatus.Idle
                }
            }.onFailure { error ->
                if (isCompositionActive) {
                    _status.value = TranslationStatus.Error(error.message ?: "Translation failed")
                }
            }
        }
    }

    private fun CoroutineScope.launchTranslation(block: suspend CoroutineScope.() -> Unit): Job {
        val job = launch(Dispatchers.Default, block = block)
        job.invokeOnCompletion { cause ->
            if (
                cause is CancellationException &&
                translationJob === job &&
                _status.value is TranslationStatus.Translating
            ) {
                _status.value = TranslationStatus.Idle
            }
        }
        return job
    }

    sealed class TranslationStatus {
        data object Idle : TranslationStatus()
        data object Translating : TranslationStatus()
        data object Success : TranslationStatus()
        data class Error(val message: String) : TranslationStatus()
    }

    private val LanguageCodeToName = mapOf(
        "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
        "it" to "Italian", "pt" to "Portuguese", "nl" to "Dutch", "ru" to "Russian",
        "uk" to "Ukrainian", "pl" to "Polish", "tr" to "Turkish", "ar" to "Arabic",
        "he" to "Hebrew", "hi" to "Hindi", "id" to "Indonesian", "vi" to "Vietnamese",
        "th" to "Thai", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
        "zh-CN" to "Simplified Chinese", "zh-TW" to "Traditional Chinese",
        "fil" to "Filipino", "tl" to "Tagalog", "ms" to "Malay", "sv" to "Swedish",
        "no" to "Norwegian", "da" to "Danish", "fi" to "Finnish", "cs" to "Czech",
        "el" to "Greek", "ro" to "Romanian", "hu" to "Hungarian",
    )
}
