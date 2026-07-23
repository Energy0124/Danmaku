package app.danmaku.library.android

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LanDanmakuComment
import app.danmaku.domain.LanDanmakuLoadStatus
import app.danmaku.domain.LanDanmakuSource
import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.library.LanLibraryClientException
import app.danmaku.library.testing.LanProtocolFixtureServer
import java.net.URI
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanLibraryClientIntegrationTest {
    @Test
    fun browsesStreamsAndSynchronizesProgressAgainstLocalServer() {
        val mediaBytes = byteArrayOf(0, 1, 2, 3, 4, 5)
        val subtitleBytes = "Hello".toByteArray()
        val subtitle = LibrarySubtitleTrack(
            id = "subtitle-id",
            label = "English",
            relativePath = "Example Show/Episode 01.en.srt",
            mediaType = "application/x-subrip",
            streamPath = "/subtitles/subtitle-id",
        )
        val item = LibraryMediaItem(
            id = "episode-id",
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mp4",
            sizeBytes = mediaBytes.size.toLong(),
            mediaType = "video/mp4",
            streamPath = "/media/episode-id",
            subtitles = listOf(subtitle),
        )
        val catalog = LibraryCatalog(
            rootName = "Example Library",
            indexedAtEpochMs = 123,
            items = listOf(item),
        )
        val progress = PlaybackProgress(
            mediaId = item.id,
            positionMs = 12_345,
            durationMs = 98_765,
            updatedAtEpochMs = 456,
        )
        val danmakuTrack = LanDanmakuTrack(
            mediaId = item.id,
            status = LanDanmakuLoadStatus.READY,
            source = LanDanmakuSource.CACHE,
            comments = listOf(LanDanmakuComment("comment-1", 1_000, "Hello")),
            matchTitle = "Example Show",
        )

        LanProtocolFixtureServer(
            catalog = catalog,
            mediaByPath = mapOf(item.streamPath to mediaBytes),
            subtitlesByPath = mapOf(subtitle.streamPath to subtitleBytes),
            danmakuByMediaId = mapOf(item.id to danmakuTrack),
        ).use { server ->
            val client = LanLibraryClient()

            assertEquals(LanLibraryServerStatus(), client.fetchServerStatus(server.baseUrl))
            assertEquals(catalog, client.fetchCatalog(server.baseUrl, server.pairingToken))
            assertArrayEquals(
                mediaBytes,
                URI(client.streamUrl(server.baseUrl, item, server.pairingToken))
                    .toURL()
                    .openStream()
                    .use { it.readBytes() },
            )
            assertArrayEquals(
                subtitleBytes,
                URI(client.subtitleUrl(server.baseUrl, subtitle, server.pairingToken))
                    .toURL()
                    .openStream()
                    .use { it.readBytes() },
            )
            assertEquals(
                emptyList<PlaybackProgress>(),
                client.fetchAllProgress(server.baseUrl, server.pairingToken),
            )
            assertNull(client.fetchProgress(server.baseUrl, item.id, server.pairingToken))

            client.saveProgress(server.baseUrl, server.pairingToken, progress)

            assertEquals(
                progress,
                client.fetchProgress(server.baseUrl, item.id, server.pairingToken),
            )
            assertEquals(
                listOf(progress),
                client.fetchAllProgress(server.baseUrl, server.pairingToken),
            )
            assertEquals(
                danmakuTrack,
                client.fetchDanmaku(
                    server.baseUrl,
                    item.id,
                    server.pairingToken,
                    forceRefresh = true,
                ),
            )
            assertEquals(1, server.danmakuForceRefreshRequests)
        }
    }

    @Test
    fun reportsHttpFailuresAsLanLibraryClientExceptions() {
        LanProtocolFixtureServer().use { server ->
            val client = LanLibraryClient()

            val failure = runCatching {
                client.fetchDanmaku(server.baseUrl, "missing", "", forceRefresh = false)
            }.exceptionOrNull()

            assertTrue(failure is LanLibraryClientException)
            assertEquals("Library server returned HTTP 404", failure?.message)
        }
    }
}
