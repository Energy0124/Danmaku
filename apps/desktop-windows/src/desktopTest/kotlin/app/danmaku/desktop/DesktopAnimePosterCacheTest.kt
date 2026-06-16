package app.danmaku.desktop

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DesktopAnimePosterCacheTest {
    @Test
    fun reportsNetworkPosterFetchFailuresAsTypedFailures() {
        val temp = createTempDirectory("danmaku-poster-cache")
        val cache = DesktopAnimePosterCache(
            cacheDirectory = temp,
            connectTimeoutMillis = 200,
            readTimeoutMillis = 200,
        )

        assertFailsWith<DesktopAnimePosterCacheException> {
            cache.fetch("https://127.0.0.1:1/poster.jpg")
        }

        temp.toFile().deleteRecursively()
    }
}
