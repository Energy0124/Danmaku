package app.danmaku.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import coil3.request.ImageRequest
import coil3.request.SuccessResult

class TvCacheAndPosterInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(
        "danmaku_tv_catalog_cache",
        Context.MODE_PRIVATE,
    )

    @After
    fun cleanUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun cacheIsVersionedIsolatedByServerAndRecoversFromInvalidData() = runBlocking {
        val cache = AndroidTvCatalogCache(context)
        val first = createTvQaFixture(seriesCount = 2, episodesPerSeries = 2)
        val second = createTvQaFixture(seriesCount = 3, episodesPerSeries = 1)
        val firstUrl = "http://first-${System.nanoTime()}:8686"
        val secondUrl = "http://second-${System.nanoTime()}:8686"

        cache.save(firstUrl, first.catalog, first.progresses)
        cache.save(secondUrl, second.catalog, second.progresses)

        assertEquals(4, cache.load(firstUrl)?.catalog?.items?.size)
        assertEquals(3, cache.load(secondUrl)?.catalog?.items?.size)

        val invalidUrl = "http://invalid-${System.nanoTime()}:8686"
        val invalidKey = invalidUrl.cacheKeyForTest()
        preferences.edit()
            .putInt("$invalidKey.version", 1)
            .putString("$invalidKey.catalog", "{broken")
            .putString("$invalidKey.progress", "[]")
            .commit()

        assertNull(cache.load(invalidUrl))
        assertTrue(preferences.all.keys.none { it.startsWith(invalidKey) })

        val oldVersionUrl = "http://old-${System.nanoTime()}:8686"
        val oldVersionKey = oldVersionUrl.cacheKeyForTest()
        preferences.edit()
            .putInt("$oldVersionKey.version", 999)
            .putString("$oldVersionKey.catalog", "{}")
            .commit()

        assertNull(cache.load(oldVersionUrl))
        assertTrue(preferences.all.keys.none { it.startsWith(oldVersionKey) })
    }

    @Test
    fun unchangedPosterIsServedFromBoundedImageLoaderCache() = runBlocking {
        val server = MockWebServer()
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk" +
                "YAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
        )
        server.enqueue(MockResponse().setBody(okio.Buffer().write(png)))
        server.start()
        val loader = createTvImageLoader(context)
        try {
            val url = server.url("/poster.png").toString()
            val request = {
                ImageRequest.Builder(context)
                    .data(url)
                    .size(320, 480)
                    .memoryCacheKey("qa-poster")
                    .diskCacheKey("qa-poster")
                    .build()
            }

            assertTrue(loader.execute(request()) is SuccessResult)
            assertTrue(loader.execute(request()) is SuccessResult)
            assertEquals(1, server.requestCount)
        } finally {
            loader.shutdown()
            server.shutdown()
        }
    }

    private fun String.cacheKeyForTest(): String {
        val normalized = trim().trimEnd('/').lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.take(16).joinToString(separator = "") { "%02x".format(it) }
    }
}
