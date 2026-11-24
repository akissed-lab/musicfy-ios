// StreamFacts.kt

package com.example.musicfy.player.model

import androidx.compose.runtime.Immutable
import com.example.musicfy.db.entities.FormatEntity

/** What a song is actually playing as, for the player's format badge. */
@Immutable
data class StreamFacts(
    /** "OPUS", "AAC", "FLAC"...: the decoder's format once known, the provider's before. */
    val codec: String? = null,
    /** Samples per second, as the decoder is given them. */
    val sampleRate: Int? = null,
    /** From a source module, so the song's format record doesn't describe it. */
    val fromModule: Boolean = false,
) {
    /** "OPUS · 160 kbps" (or "FLAC · 44.1 kHz" when there is no bitrate). */
    fun describe(format: FormatEntity?): String? {
        val kbps = format?.bitrate?.takeIf { it > 0 }?.let { "${(it + 500) / 1000} kbps" }
        val rate = sampleRate?.takeIf { it > 0 }?.let(::oneDecimalkHz)
        return listOfNotNull(codec, kbps ?: rate).joinToString(" · ").ifEmpty { null }
    }

    companion object {
        /** The badge's names for a decoder format / MIME type. */
        fun codecOf(mimeType: String?, codecs: String? = null): String? {
            val mime = mimeType?.lowercase().orEmpty()
            val codec = codecs?.lowercase().orEmpty()
            return when {
                mime == "audio/opus" || codec.startsWith("opus") -> "OPUS"
                mime == "audio/flac" || codec == "flac" -> "FLAC"
                mime == "audio/alac" || codec == "alac" -> "ALAC"
                mime == "audio/eac3-joc" || codec == "ec+3" -> "DOLBY ATMOS"
                mime == "audio/eac3" || codec == "ec-3" -> "E-AC-3"
                mime == "audio/ac3" || codec == "ac-3" -> "AC-3"
                mime == "audio/ac4" || codec.startsWith("ac-4") -> "AC-4"
                codec.startsWith("mp4a.40.5") || codec.startsWith("mp4a.40.29") -> "HE-AAC"
                mime == "audio/aac" || codec.startsWith("mp4a") -> "AAC"
                mime == "audio/vorbis" || codec == "vorbis" -> "VORBIS"
                mime == "audio/mpeg" || codec == "mp3" -> "MP3"
                mime == "audio/raw" || mime == "audio/wav" -> "PCM"
                // Container types, when the record has no codec name.
                mime == "audio/webm" -> "OPUS"
                mime == "audio/mp4" -> "AAC"
                else -> null
            }
        }

        /** From YouTube's record of the format it handed over. */
        fun of(format: FormatEntity): StreamFacts = StreamFacts(
            codec = codecOf(format.mimeType, format.codecs),
            sampleRate = format.sampleRate?.takeIf { it > 0 },
        )

        /** Locale-free one-decimal kHz, e.g. 44100 -> "44.1 kHz". */
        private fun oneDecimalkHz(sampleRate: Int): String {
            val tenths = (sampleRate + 50) / 100
            return "${tenths / 10}.${tenths % 10} kHz"
        }
    }
}
