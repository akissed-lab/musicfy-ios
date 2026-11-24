package com.example.musicfy.db.entities

/** Room entity ported to a plain KMP model; SQLDelight persists the same columns. */
data class LyricsEntity(
    val id: String,
    val lyrics: String,
    val provider: String = "Unknown",
    val translatedLyrics: String = "",
    val translationLanguage: String = "",
    val translationMode: String = "",
) {
    companion object {
        const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"
    }
}
