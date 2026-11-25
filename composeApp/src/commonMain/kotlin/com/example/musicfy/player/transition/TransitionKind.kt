package com.example.musicfy.player.transition

/**
 * Portable transition kinds shared by the transition creator and any future
 * playback wiring. This is a KMP model only: no platform player, decoder or
 * AVFoundation call is involved.
 */
enum class TransitionKind(
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    NONE(
        label = "No transition",
        shortLabel = "None",
        description = "Hard cut: the next song starts straight away.",
    ),
    CROSSFADE(
        label = "Crossfade",
        shortLabel = "Fade",
        description = "The two songs overlap while one volume fades down and the other fades up.",
    ),
    BLEND(
        label = "Blend",
        shortLabel = "Blend",
        description = "A smoother hand-over that keeps the loudness steady through the middle.",
    ),
    SLIDE(
        label = "Slide",
        shortLabel = "Slide",
        description = "The hand-over slides the outgoing track away and the incoming track in.",
    ),
    CAROUSEL(
        label = "Carousel",
        shortLabel = "Wheel",
        description = "The hand-over moves like the next item on a carousel.",
    );

    /** Only an overlapping kind has a meaningful duration and volume curve. */
    val usesOverlap: Boolean
        get() = this != NONE

    companion object {
        fun fromName(name: String?): TransitionKind =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CROSSFADE
    }
}
