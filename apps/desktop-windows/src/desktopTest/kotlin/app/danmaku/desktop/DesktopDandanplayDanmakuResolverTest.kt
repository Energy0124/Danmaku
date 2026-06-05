package app.danmaku.desktop

import app.danmaku.domain.DanmakuEvent
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopDandanplayDanmakuResolverTest {
    @Test
    fun fetchesTrackAndCachesRenderedAssSubtitle() {
        val temp = createTempDirectory("danmaku-dandanplay-resolver")
        val media = temp.resolve("Episode 01.mkv")
        media.writeBytes("hello".encodeToByteArray())
        val cache = temp.resolve("cache")
        var fetchCount = 0
        val cacheStore = InMemoryDandanplayCommentCacheStore()
        val resolver = DesktopDandanplayDanmakuResolver(
            loadConnection = { DandanplayConnection("http://127.0.0.1:9000") },
            cacheStore = cacheStore,
            fetchTrack = { connection, fingerprint ->
                fetchCount += 1
                assertEquals("http://127.0.0.1:9000/", connection.baseUri.toString())
                assertEquals("Episode 01.mkv", fingerprint.fileName)
                assertEquals("5d41402abc4b2a76b9719d911017c592", fingerprint.normalizedFileHash)
                DandanplayCommentTrack(
                    match = DandanplayMatch(
                        episodeId = 123,
                        animeId = 456,
                        animeTitle = "Example",
                        episodeTitle = "Episode 01",
                        shiftSeconds = 0.0,
                    ),
                    events = listOf(
                        DanmakuEvent(id = "one", timestampMs = 1_000, text = "hello overlay"),
                    ),
                )
            },
            cacheDirectory = cache,
            nowEpochMs = { 1234 },
        )

        val resolution = resolver.resolve("media-id", media)

        val subtitle = assertNotNull(resolution.subtitle)
        assertTrue(subtitle.isDanmakuOverlay)
        assertEquals("dandanplay: Example - Episode 01", subtitle.label)
        assertEquals(1, resolution.eventCount)
        assertEquals(DesktopDandanplayResolutionSource.NETWORK, resolution.source)
        assertNotNull(resolution.cachePath)
        assertTrue(Files.isRegularFile(resolution.cachePath))
        assertContains(resolution.cachePath.readText(), "hello overlay")
        assertEquals(1, fetchCount)

        Files.delete(resolution.cachePath)
        val cachedResolution = resolver.resolve("media-id", media)

        assertEquals(1, fetchCount)
        assertEquals(DesktopDandanplayResolutionSource.CACHE, cachedResolution.source)
        assertNotNull(cachedResolution.cachePath)
        assertContains(cachedResolution.cachePath.readText(), "hello overlay")

        temp.toFile().deleteRecursively()
    }

    @Test
    fun returnsNoSubtitleWhenNoMatchOrNoComments() {
        val temp = createTempDirectory("danmaku-dandanplay-resolver")
        val media = temp.resolve("Episode 01.mkv")
        media.writeBytes("hello".encodeToByteArray())

        val noMatch = DesktopDandanplayDanmakuResolver(
            loadConnection = { DandanplayConnection("http://127.0.0.1:9000") },
            fetchTrack = { _, _ -> null },
            cacheDirectory = temp.resolve("no-match-cache"),
        ).resolve("media-id", media)

        assertNull(noMatch.subtitle)
        assertNull(noMatch.cachePath)

        val noComments = DesktopDandanplayDanmakuResolver(
            loadConnection = { DandanplayConnection("http://127.0.0.1:9000") },
            fetchTrack = { _, _ ->
                DandanplayCommentTrack(
                    match = DandanplayMatch(
                        episodeId = 123,
                        animeId = null,
                        animeTitle = null,
                        episodeTitle = null,
                        shiftSeconds = null,
                    ),
                    events = emptyList(),
                )
            },
            cacheDirectory = temp.resolve("no-comments-cache"),
        ).resolve("media-id", media)

        assertNull(noComments.subtitle)
        assertNull(noComments.cachePath)
        assertEquals(0, noComments.eventCount)

        temp.toFile().deleteRecursively()
    }

    @Test
    fun ignoresCachedTrackWhenMediaFingerprintChanges() {
        val temp = createTempDirectory("danmaku-dandanplay-resolver")
        val media = temp.resolve("Episode 01.mkv")
        media.writeBytes("hello".encodeToByteArray())
        val cacheStore = InMemoryDandanplayCommentCacheStore()
        cacheStore.saveDandanplayCommentCache(
            DesktopDandanplayCommentCache(
                mediaId = "media-id",
                fileHash = "00000000000000000000000000000000",
                fileName = "Episode 01.mkv",
                fileSizeBytes = 5,
                episodeId = 123,
                animeId = null,
                animeTitle = null,
                episodeTitle = null,
                shiftSeconds = null,
                commentsJson = """{"events":[{"timestampMs":1,"text":"stale"}]}""",
                renderedAssPath = null,
                fetchedAtEpochMs = 1,
            ),
        )
        var fetchCount = 0
        val resolver = DesktopDandanplayDanmakuResolver(
            loadConnection = { DandanplayConnection("http://127.0.0.1:9000") },
            cacheStore = cacheStore,
            fetchTrack = { _, _ ->
                fetchCount += 1
                DandanplayCommentTrack(
                    match = DandanplayMatch(
                        episodeId = 456,
                        animeId = null,
                        animeTitle = "Fresh",
                        episodeTitle = "Episode",
                        shiftSeconds = null,
                    ),
                    events = listOf(DanmakuEvent(id = "fresh", timestampMs = 1_000, text = "fresh")),
                )
            },
            cacheDirectory = temp.resolve("cache"),
        )

        val resolution = resolver.resolve("media-id", media)

        assertEquals(1, fetchCount)
        assertEquals(DesktopDandanplayResolutionSource.NETWORK, resolution.source)
        assertContains(assertNotNull(resolution.cachePath).readText(), "fresh")

        temp.toFile().deleteRecursively()
    }

    private class InMemoryDandanplayCommentCacheStore : DandanplayCommentCacheStore {
        private val values = mutableMapOf<String, DesktopDandanplayCommentCache>()

        override fun loadDandanplayCommentCache(mediaId: String): DesktopDandanplayCommentCache? =
            values[mediaId]

        override fun saveDandanplayCommentCache(cache: DesktopDandanplayCommentCache) {
            values[cache.mediaId] = cache
        }
    }
}
