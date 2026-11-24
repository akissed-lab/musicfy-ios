// StringUtils.kt

package com.example.musicfy.ui.utils

import kotlin.math.absoluteValue

fun formatFileSize(sizeBytes: Long): String {
    val prefix = if (sizeBytes < 0) "-" else ""
    var result: Long = sizeBytes.absoluteValue
    var suffix = "B"
    if (result > 900) {
        suffix = "KB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "MB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "GB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "TB"
        result /= 1024
    }
    if (result > 900) {
        suffix = "PB"
        result /= 1024
    }
    return "$prefix$result $suffix"
}

/**
 * KMP replacement for the upstream `DecimalFormat("#,###").replace(",", ".")`.
 * Grouping is applied manually so the output stays identical on every target.
 */
fun numberFormatter(n: Int): String {
    val negative = n < 0
    val digits = (n.toLong().absoluteValue).toString()
    val grouped = buildString {
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) append('.')
            append(digit)
        }
    }
    return if (negative) "-$grouped" else grouped
}
