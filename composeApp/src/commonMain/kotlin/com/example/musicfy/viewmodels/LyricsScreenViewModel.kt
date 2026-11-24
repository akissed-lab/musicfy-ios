package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.db.MusicDatabaseFacade
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.lyrics.LyricsHelper
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Closest KMP port of upstream LyricsScreenViewModel (Hilt replaced by [AppContainer]). */
class LyricsScreenViewModel(container: AppContainer) : ViewModel() {
    private val lyricsHelper: LyricsHelper = container.lyricsHelper

    /** Exposed so the lyrics screen can hand it to LyricsTranslationHelper. */
    val database: MusicDatabaseFacade = container.database

    private val requestedIds = mutableSetOf<String>()

    fun ensureLyricsLoaded(mediaMetadata: MediaMetadata) {
        val id = mediaMetadata.id
        if (!requestedIds.add(id)) return

        viewModelScope.launch(Dispatchers.Default) {
            val existing = database.lyrics(id).first()
            // YouLyPlus and Paxsenix used to join syllables with spaces ("wa nt yo u"). Their
            // converters are fixed, but lyrics cached before that keep the split text forever, so
            // a cached copy from either that still reads as syllable-split is fetched again once.
            val staleSyllableSplit = existing != null &&
                existing.provider in SyllableFixedProviders &&
                LyricsUtils.isSyllableSplit(existing.lyrics)
            if (existing != null && !staleSyllableSplit) return@launch

            val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
            // Nothing was asked: not a "not found" to keep.
            if (lyricsWithProvider.provider == LyricsHelper.NoConnectionProvider) return@launch
            database.query {
                upsert(
                    LyricsEntity(
                        id = id,
                        lyrics = lyricsWithProvider.lyrics,
                        provider = lyricsWithProvider.provider,
                    ),
                )
            }
        }
    }
}

private val SyllableFixedProviders = setOf("YouLyPlus", "Paxsenix")
