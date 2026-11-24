package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Badges(
    val musicInlineBadgeRenderer: MusicInlineBadgeRenderer? = null,
) {
    @Serializable
    data class MusicInlineBadgeRenderer(
        val icon: Icon = Icon(),
    )
}

fun List<Badges>?.hasExplicitBadge(): Boolean =
    this?.any { it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE" } == true
