package com.music.innertube.models

import kotlinx.serialization.Serializable

/** Upstream keeps this next to `MusicEditablePlaylistDetailHeaderRenderer`. */
@Serializable
data class MusicDetailHeaderRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val secondSubtitle: Runs? = null,
    val description: Runs? = null,
    val thumbnail: ThumbnailRenderer = ThumbnailRenderer(),
    val menu: Menu = Menu(),
)
