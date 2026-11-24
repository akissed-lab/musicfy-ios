package com.example.musicfy.db.entities

import com.example.musicfy.util.nowLocal
import kotlinx.datetime.LocalDateTime

data class RecognitionHistory(
    val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverArtUrl: String? = null,
    val coverArtHqUrl: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val label: String? = null,
    val shazamUrl: String? = null,
    val appleMusicUrl: String? = null,
    val spotifyUrl: String? = null,
    val isrc: String? = null,
    val youtubeVideoId: String? = null,
    val recognizedAt: LocalDateTime = nowLocal(),
    val liked: Boolean = false,
)
