package com.example.musicfy.player.video

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_ENTRIES = 64

// Common LinkedHashMap has no access-order constructor, so reads reinsert the entry to make it newest.
private fun <K, V> MutableMap<K, V>.touch(key: K): V? =
    remove(key)?.also { this[key] = it }

private fun <K, V> MutableMap<K, V>.evictOldestIfOver(maxSize: Int) {
    while (size > maxSize) {
        val oldest = keys.firstOrNull() ?: return
        remove(oldest)
    }
}

/**
 * Successful video-id lookups only.
 *
 * A miss is deliberately not stored: search failures and "no official video yet" are often
 * temporary, so the next request has to be able to search again.
 */
object OfficialVideoIdCache {
    private val lock = Mutex()
    private val map = LinkedHashMap<String, String>(MAX_ENTRIES, 0.75f)

    suspend fun contains(key: String): Boolean = lock.withLock { map.containsKey(key) }

    suspend fun get(key: String): String? = lock.withLock { map.touch(key) }

    suspend fun put(key: String, videoId: String) {
        if (key.isBlank() || videoId.isBlank()) return
        lock.withLock {
            map[key] = videoId
            map.evictOldestIfOver(MAX_ENTRIES)
        }
    }

    suspend fun remove(key: String) {
        lock.withLock { map.remove(key) }
    }
}

/**
 * Successfully resolved video streams only.
 *
 * [contains] is the gate callers should use: an entry with an expired stream is treated as a miss
 * even though [get] still returns it, so a fresh resolution can replace it.
 */
object OfficialVideoUrlCache {
    private val lock = Mutex()
    private val map = LinkedHashMap<String, OfficialMusicVideo>(MAX_ENTRIES, 0.75f)

    suspend fun contains(mediaId: String): Boolean = lock.withLock {
        val video = map.touch(mediaId) ?: return@withLock false
        video.streams.isExpired.not()
    }

    suspend fun get(mediaId: String): OfficialMusicVideo? = lock.withLock { map.touch(mediaId) }

    suspend fun put(mediaId: String, video: OfficialMusicVideo) {
        if (mediaId.isBlank()) return
        lock.withLock {
            map[mediaId] = video
            map.evictOldestIfOver(MAX_ENTRIES)
        }
    }

    suspend fun remove(mediaId: String) {
        lock.withLock { map.remove(mediaId) }
    }
}
