package com.example.musicfy.util

import kotlin.random.Random

/**
 * KMP replacement for `RandomStringUtils.insecure().next(length, true, false)`,
 * which generated letters only in upstream entity factories.
 */
fun randomLetters(length: Int): String {
    val alphabet = ('A'..'Z') + ('a'..'z')
    return buildString(length) {
        repeat(length) { append(alphabet[Random.nextInt(alphabet.size)]) }
    }
}
