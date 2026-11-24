package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Runs(
    val runs: List<Run>? = null,
    val simpleText: String? = null,
)

@Serializable
data class Run(
    val text: String = "",
    val navigationEndpoint: NavigationEndpoint? = null,
)

/** YouTube separates metadata columns with " • " runs. */
fun List<Run>.splitBySeparator(): List<List<Run>> {
    val result = mutableListOf<List<Run>>()
    var current = mutableListOf<Run>()
    forEach { run ->
        if (run.text == " • ") {
            result += current
            current = mutableListOf()
        } else {
            current += run
        }
    }
    result += current
    return result
}

/** Suggestion renderers sometimes prepend a non-link "no text" fragment. */
fun List<List<Run>>.clean(): List<List<Run>> {
    val first = getOrNull(0)?.getOrNull(0) ?: return this
    return if (first.navigationEndpoint != null || Regex("[&,]").containsMatchIn(first.text)) {
        this
    } else {
        drop(1)
    }
}

/** Artist/album runs alternate with separators: 0, 2, 4... are the values. */
fun List<Run>.oddElements(): List<Run> = filterIndexed { index, _ -> index % 2 == 0 }

private val TIMESTAMP_TEXT_REGEX = Regex("^\\d{1,2}[:.]\\d{2}([:.]\\d{2})?$")

fun String.looksLikeTimestamp(): Boolean = TIMESTAMP_TEXT_REGEX.matches(trim())
