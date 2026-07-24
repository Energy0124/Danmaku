package app.danmaku.library.jvm

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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JvmLanLibraryClientTest {
    @Test
    fun browsesStreamsAndSynchronizesProgressAgainstLocalServer() {
        val mediaBytes = byteArrayOf(0, 1, 2, 3, 4, 5)
        val item = LibraryMediaItem(
            id = "episode-id",
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mp4",
            sizeBytes = mediaBytes.size.toLong(),
            mediaType = "video/mp4",
            streamPath = "/media/episode-id",
            subtitles = listOf(
                LibrarySubtitleTrack(
                    id = "subtitle-id",
                    label = "English",
                    relativePath = "Example Show/Episode 01.en.srt",
                    mediaType = "application/x-subrip",
                    streamPath = "/subtitles/subtitle-id",
                ),
            ),
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
            danmakuByMediaId = mapOf(item.id to danmakuTrack),
        ).use { server ->
            val client = JvmLanLibraryClient()

            assertEquals(LanLibraryServerStatus(), client.fetchServerStatus(server.baseUrl))
            assertEquals(catalog, client.fetchCatalog(server.baseUrl, server.pairingToken))
            assertContentEquals(
                mediaBytes,
                URI(client.streamUrl(server.baseUrl, item, server.pairingToken))
                    .toURL()
                    .openStream()
                    .use { it.readBytes() },
            )
            assertEquals(
                "${server.baseUrl}/subtitles/subtitle-id",
                client.subtitleUrl(server.baseUrl, item.subtitles.single(), server.pairingToken),
            )
            assertEquals(emptyList(), client.fetchAllProgress(server.baseUrl, server.pairingToken))
            assertNull(client.fetchProgress(server.baseUrl, item.id, server.pairingToken))

            assertEquals(
                400,
                putProgress(server.baseUrl, item.id, progress.copy(mediaId = "body-id")),
            )
            assertEquals(
                404,
                putProgress(
                    server.baseUrl,
                    "missing-id",
                    progress.copy(mediaId = "missing-id"),
                ),
            )
            assertEquals(emptyList(), client.fetchAllProgress(server.baseUrl, server.pairingToken))

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
    fun recoversAfterInterruptedCatalogRequest() {
        val catalog = LibraryCatalog(
            rootName = "Example Library",
            indexedAtEpochMs = 123,
            items = listOf(
                LibraryMediaItem(
                    id = "episode-id",
                    seriesTitle = "Example Show",
                    episodeTitle = "Episode 01",
                    relativePath = "Example Show/Episode 01.mp4",
                    sizeBytes = 6,
                    mediaType = "video/mp4",
                    streamPath = "/media/episode-id",
                ),
            ),
        )

        InterruptingCatalogServer(catalog).use { server ->
            val client = JvmLanLibraryClient()

            assertEquals(catalog, client.fetchCatalog(server.baseUrl, "123456"))
            assertEquals(2, server.acceptedRequests)
        }
    }

    @Test
    fun reportsHttpFailuresAsLanLibraryClientExceptions() {
        LanProtocolFixtureServer().use { server ->
            val client = JvmLanLibraryClient()

            val failure = assertFailsWith<LanLibraryClientException> {
                client.fetchDanmaku(server.baseUrl, "missing", "", forceRefresh = false)
            }

            assertEquals("Library server returned HTTP 404", failure.message)
        }
    }

    @Test
    fun timesOutSlowCatalogResponses() {
        SlowCatalogServer(responseDelayMillis = 1_000).use { server ->
            val client = JvmLanLibraryClient(readTimeoutMillis = 100)

            assertFailsWith<SocketTimeoutException> {
                client.fetchCatalog(server.baseUrl, "123456")
            }
        }
    }

    private fun putProgress(
        baseUrl: String,
        pathMediaId: String,
        progress: PlaybackProgress,
    ): Int {
        val body = Json.encodeToString(progress).toByteArray(StandardCharsets.UTF_8)
        val encodedMediaId = URLEncoder.encode(pathMediaId, StandardCharsets.UTF_8)
        val connection = URI("$baseUrl/api/progress/$encodedMediaId")
            .toURL()
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private class InterruptingCatalogServer(
        private val catalog: LibraryCatalog,
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        private val requestCount = AtomicInteger()

        val baseUrl: String = "http://127.0.0.1:${socket.localPort}"
        val acceptedRequests: Int
            get() = requestCount.get()

        init {
            executor.submit {
                socket.accept().use {
                    requestCount.incrementAndGet()
                    it.readRequestAndClose()
                }
                socket.accept().use {
                    requestCount.incrementAndGet()
                    it.writeCatalogResponse(catalog)
                }
            }
        }

        override fun close() {
            socket.close()
            executor.shutdownNow()
        }

        private fun Socket.readRequestAndClose() {
            getInputStream().bufferedReader().readLinesUntilBlank()
        }

        private fun Socket.writeCatalogResponse(catalog: LibraryCatalog) {
            getInputStream().bufferedReader().readLinesUntilBlank()
            val body = Json.encodeToString(catalog).toByteArray()
            getOutputStream().bufferedWriter().apply {
                write("HTTP/1.1 200 OK\r\n")
                write("Content-Type: application/json; charset=utf-8\r\n")
                write("Content-Length: ${body.size}\r\n")
                write("Connection: close\r\n")
                write("\r\n")
                flush()
            }
            getOutputStream().write(body)
            getOutputStream().flush()
        }
    }

    private class SlowCatalogServer(
        private val responseDelayMillis: Long,
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()

        val baseUrl: String = "http://127.0.0.1:${socket.localPort}"

        init {
            executor.submit {
                runCatching {
                    socket.accept().use {
                        it.readRequestAndWait(responseDelayMillis)
                    }
                }
            }
        }

        override fun close() {
            socket.close()
            executor.shutdownNow()
        }

        private fun Socket.readRequestAndWait(responseDelayMillis: Long) {
            getInputStream().bufferedReader().readLinesUntilBlank()
            Thread.sleep(responseDelayMillis)
        }
    }
}

private fun java.io.BufferedReader.readLinesUntilBlank() {
    while (true) {
        val line = readLine() ?: break
        if (line.isBlank()) break
    }
}
