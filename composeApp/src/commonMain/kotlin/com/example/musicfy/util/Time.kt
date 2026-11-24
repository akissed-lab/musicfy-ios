package com.example.musicfy.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** KMP replacement for `System.currentTimeMillis()`. */
expect fun currentTimeMillis(): Long

/** KMP replacement for `java.time.LocalDateTime.now()`. */
fun nowLocal(): LocalDateTime =
    Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault())
