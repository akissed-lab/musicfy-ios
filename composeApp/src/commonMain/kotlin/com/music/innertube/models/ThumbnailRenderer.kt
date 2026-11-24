@file:OptIn(ExperimentalSerializationApi::class)

package com.music.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class ThumbnailRenderer(
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null,
    @JsonNames("croppedSquareThumbnailRenderer")
    val croppedSquareThumbnailRenderer: MusicThumbnailRenderer? = null,
    val musicAnimatedThumbnailRenderer: MusicAnimatedThumbnailRenderer? = null,
) {
    /** Largest thumbnail URL, with the same fallbacks upstream uses. */
    fun getThumbnailUrl(): String? =
        (musicThumbnailRenderer ?: croppedSquareThumbnailRenderer)?.getThumbnailUrl()
            ?: musicAnimatedThumbnailRenderer?.backupRenderer?.getThumbnailUrl()

    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails? = null,
        val thumbnailCrop: String? = null,
        val thumbnailScale: String? = null,
    ) {
        fun getThumbnailUrl(): String? = thumbnail?.thumbnails?.lastOrNull()?.url
    }

    @Serializable
    data class MusicAnimatedThumbnailRenderer(
        val animatedThumbnail: Thumbnails? = null,
        val backupRenderer: MusicThumbnailRenderer? = null,
    )
}

@Serializable
data class Thumbnails(
    val thumbnails: List<Thumbnail> = emptyList(),
)

@Serializable
data class Thumbnail(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null,
)
