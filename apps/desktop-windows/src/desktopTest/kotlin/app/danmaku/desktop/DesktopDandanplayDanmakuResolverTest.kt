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
        val resolver = DesktopDandanplayDanmakuResolver(
            loadConnection = { DandanplayConnection("http://127.0.0.1:9000") },
            fetchTrack = { connection, fingerprint ->
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
        )

        val resolution = resolver.resolve("media-id", media)

        val subtitle = assertNotNull(resolution.subtitle)
        assertTrue(subtitle.isDanmakuOverlay)
        assertEquals("dandanplay: Example - Episode 01", subtitle.label)
        assertEquals(1, resolution.eventCount)
        assertNotNull(resolution.cachePath)
        assertTrue(Files.isRegularFile(resolution.cachePath))
        assertContains(resolution.cachePath.readText(), "hello overlay")

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
}
