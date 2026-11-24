package com.example.musicfy.lyrics

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind

/** iOS implementation: Android content URIs become plain file paths or `file://` URLs. */
@OptIn(ExperimentalForeignApi::class)
actual fun readEmbeddedLyrics(uri: String): String? {
    val path = uri.removePrefix("file://")
    val file = fopen(path, "rb") ?: return null
    return try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toLong()
        if (size <= 0L) return null
        rewind(file)
        memScoped {
            val buffer = allocArray<ByteVar>(size.toInt())
            val bytesRead = fread(buffer, 1u, size.toULong(), file).toInt()
            if (bytesRead <= 0) return null
            val bytes = buffer.readBytes(bytesRead)
            EmbeddedLyrics.find(object : LyricsSource {
                override val size: Long = bytes.size.toLong()

                override fun read(position: Long, length: Int): ByteArray {
                    if (position < 0 || position >= this.size || length <= 0) return ByteArray(0)
                    val end = minOf(this.size, position + length).toInt()
                    return bytes.copyOfRange(position.toInt(), end)
                }
            })
        }
    } finally {
        fclose(file)
    }
}
