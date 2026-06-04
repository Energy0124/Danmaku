package app.danmaku.library.jvm

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.server.LocalLibraryServer
import app.danmaku.server.PublishedLibrary
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JvmLanLibraryClientTest {
    @Test
    fun browsesStreamsAndSynchronizesProgressAgainstLocalServer() {
        val mediaBytes = byteArrayOf(0, 1, 2, 3, 4, 5)
        val mediaFile = createTempFile("danmaku-jvm-client", ".mp4")
        mediaFile.writeBytes(mediaBytes)
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

        try {
            LocalLibraryServer(port = 0, pairingToken = "token with spaces").use { server ->
                server.publish(
                    PublishedLibrary(
                        catalog = catalog,
                        filesById = mapOf(item.id to mediaFile),
                    ),
                )
                server.start()
                val client = JvmLanLibraryClient()

                assertEquals(catalog, client.fetchCatalog(server.baseUrl(), server.pairingToken))
                assertContentEquals(
                    mediaBytes,
                    URI(client.streamUrl(server.baseUrl(), item, server.pairingToken))
                        .toURL()
                        .openStream()
                        .use { it.readBytes() },
                )
                assertEquals(
                    "${server.baseUrl()}/subtitles/subtitle-id?token=token+with+spaces",
                    client.subtitleUrl(server.baseUrl(), item.subtitles.single(), server.pairingToken),
                )
                assertNull(client.fetchProgress(server.baseUrl(), item.id, server.pairingToken))

                client.saveProgress(server.baseUrl(), server.pairingToken, progress)

                assertEquals(
                    progress,
                    client.fetchProgress(server.baseUrl(), item.id, server.pairingToken),
                )
            }
        } finally {
            mediaFile.deleteIfExists()
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
    fun timesOutSlowCatalogResponses() {
        SlowCatalogServer(responseDelayMillis = 1_000).use { server ->
            val client = JvmLanLibraryClient(readTimeoutMillis = 100)

            assertFailsWith<SocketTimeoutException> {
                client.fetchCatalog(server.baseUrl, "123456")
            }
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
