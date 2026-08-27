package app.danmaku.library.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OfflineCacheKeyTest {
    @Test
    fun keyIsStableAcrossEquivalentServerUrlsAndIsolatesMedia() {
        val first = AndroidOfflineCacheRepository.cacheKey("HTTP://PC:8686/", "episode-1")

        assertEquals(first, AndroidOfflineCacheRepository.cacheKey("http://pc:8686", "episode-1"))
        assertNotEquals(first, AndroidOfflineCacheRepository.cacheKey("http://pc:8686", "episode-2"))
        assertNotEquals(first, AndroidOfflineCacheRepository.cacheKey("http://other:8686", "episode-1"))
    }

    @Test
    fun keyDoesNotExposeServerOrMediaIdentity() {
        val key = AndroidOfflineCacheRepository.cacheKey("http://private-pc:8686", "secret episode")

        assertFalse(key.contains("private", ignoreCase = true))
        assertFalse(key.contains("secret", ignoreCase = true))
        assertEquals(40, key.length)
    }
}
