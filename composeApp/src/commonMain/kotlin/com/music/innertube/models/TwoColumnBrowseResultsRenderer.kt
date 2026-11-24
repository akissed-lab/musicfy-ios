package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class TwoColumnBrowseResultsRenderer(
    val secondaryContents: SecondaryContents? = null,
    val tabs: List<Tabs.Tab?> = emptyList(),
) {
    @Serializable
    data class SecondaryContents(
        val sectionListRenderer: SectionListRenderer? = null,
    )
}
