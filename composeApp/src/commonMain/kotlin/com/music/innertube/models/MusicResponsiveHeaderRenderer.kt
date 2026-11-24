package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicResponsiveHeaderRenderer(
    val thumbnail: ThumbnailRenderer? = null,
    val buttons: List<Button> = emptyList(),
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val secondSubtitle: Runs? = null,
    val straplineTextOne: Runs? = null,
    val description: Runs? = null,
)
