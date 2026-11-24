package com.music.innertube.utils

/** Common/KMP port of upstream `parseTime` (no java.* dependencies). */
private val DURATION_REGEX = Regex("^(?:(\\d+)[:.])?(\\d+)[:.](\\d{2})$")

/** Accepts `m:ss`, `h:mm:ss` and locale variants using `.` as separator. */
fun String.parseTime(): Int? {
    val match = DURATION_REGEX.matchEntire(trim()) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: 0
    val minutes = match.groupValues[2].toIntOrNull() ?: return null
    val seconds = match.groupValues[3].toIntOrNull() ?: return null
    return hours * 3600 + minutes * 60 + seconds
}
