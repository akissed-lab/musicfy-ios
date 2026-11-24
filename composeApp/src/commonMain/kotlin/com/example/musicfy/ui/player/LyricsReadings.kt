// LyricsReadings.kt

package com.example.musicfy.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.lyrics.LyricsEntry
import com.example.musicfy.lyrics.LyricsTranslationHelper
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference

/**
 * Fills each [LyricsEntry] with the readings shown under (or above) its text.
 * Romanisation tables are not part of the common port yet, so those flows are
 * cleared here instead of being filled with guessed text; the translation
 * flows are driven by the ported [LyricsTranslationHelper].
 */
@Composable
fun LyricsReadings(
    lines: List<LyricsEntry>,
    songId: String?,
    database: MusicDatabaseFacade? = null,
    lyricsEntity: LyricsEntity? = null,
) {
    // `database` is accepted for parity with upstream. Persistence of machine
    // translations is owned by LyricsTranslationHelper's load/save callbacks;
    // the keyless Google engine is used until an AI key port lands.
    val (targetLanguage) = rememberPreference(SettingsKeys.TranslateLanguageKey, defaultValue = "en")
    val (sourceLanguage) = rememberPreference(SettingsKeys.TranslateSourceLanguageKey, defaultValue = "auto")
    val (mode) = rememberPreference(SettingsKeys.TranslateModeKey, defaultValue = "line")
    val (sessionWide) = rememberPreference(SettingsKeys.TranslateLyricsKey, defaultValue = false)

    val translationOn by LyricsTranslationHelper.translationOn.collectAsState()
    val entity by rememberUpdatedState(lyricsEntity)

    DisposableEffect(Unit) {
        LyricsTranslationHelper.setCompositionActive(true)
        onDispose { LyricsTranslationHelper.setCompositionActive(false) }
    }

    LaunchedEffect(songId, sessionWide) {
        LyricsTranslationHelper.onSongChanged(songId, sessionWide)
    }

    LaunchedEffect(lines) {
        if (lines.isEmpty()) return@LaunchedEffect
        // No transliteration engine in commonMain yet: keep the slot empty
        // rather than showing a romanisation that cannot be verified.
        lines.forEach {
            it.romanizedTextFlow.value = null
            it.rubyFlow.value = null
        }
    }

    LaunchedEffect(
        lines,
        translationOn,
        targetLanguage,
        sourceLanguage,
        mode,
        entity,
    ) {
        if (lines.isEmpty()) return@LaunchedEffect
        if (!translationOn) {
            lines.forEach { it.translatedTextFlow.value = null }
            return@LaunchedEffect
        }
        val target = targetLanguage.ifBlank { "en" }
        val tag = LyricsTranslationHelper.languageTag(sourceLanguage, target)
        if (LyricsTranslationHelper.loadTranslationsFromDatabase(
                lyrics = lines,
                entity = entity,
                targetLanguage = tag,
                mode = mode,
            )
        ) {
            return@LaunchedEffect
        }
        LyricsTranslationHelper.translateWithGoogle(
            lyrics = lines,
            targetLanguage = target,
            mode = mode,
            sourceLanguage = sourceLanguage,
            scope = this,
            songId = songId.orEmpty(),
        )
    }

    LaunchedEffect(lines) {
        LyricsTranslationHelper.clearTranslationsTrigger.collect {
            lines.forEach { it.translatedTextFlow.value = null }
        }
    }
}

internal fun hasAiKeyFor(provider: String, openRouterKey: String, deeplKey: String): Boolean =
    if (provider == "DeepL") deeplKey.isNotBlank() else openRouterKey.isNotBlank()
