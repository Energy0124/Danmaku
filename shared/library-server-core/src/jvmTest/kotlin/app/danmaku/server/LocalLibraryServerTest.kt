package app.danmaku.server

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalLibraryServerTest {
    @Test
    fun exposesUnauthenticatedServerStatusForCompatibilityChecks() {
        LocalLibraryServer(port = 0, pairingToken = "123456").use { server ->
            server.start()

            val response = connection("${server.baseUrl()}/api/server/status")

            assertEquals(200, response.responseCode)
            assertEquals("application/json; charset=utf-8", response.getHeaderField("Content-Type"))
            assertEquals(
                LanLibraryServerStatus(),
                Json.decodeFromString<LanLibraryServerStatus>(
                    response.inputStream.bufferedReader().use { it.readText() },
                ),
            )
        }
    }

    @Test
    fun streamsOnlyPublishedSubtitleTracks() {
        val subtitleFile = createTempFile("danmaku-subtitle", ".srt")
        val subtitleBody = "1\n00:00:00,000 --> 00:00:01,000\nHello\n"
        subtitleFile.writeBytes(subtitleBody.toByteArray())
        val track = LibrarySubtitleTrack(
            id = "subtitle-id",
            label = "English",
            relativePath = "Example Show/Episode 01.en.srt",
            mediaType = "application/x-subrip",
            streamPath = "/subtitles/subtitle-id",
        )

        try {
            LocalLibraryServer(port = 0, pairingToken = "123456").use { server ->
                server.publish(
                    PublishedLibrary(
                        catalog = LibraryCatalog("Example", 123, emptyList()),
                        filesById = emptyMap(),
                        subtitleFilesById = mapOf(track.id to subtitleFile),
                    ),
                )
                server.start()
                val url = "${server.baseUrl()}${track.streamPath}"

                assertEquals(401, connection(url).responseCode)
                assertEquals(404, connection("${server.baseUrl()}/subtitles/missing?token=123456").responseCode)
                val headResponse = connection("$url?token=123456").apply {
                    requestMethod = "HEAD"
                }
                assertEquals(200, headResponse.responseCode)
                assertEquals(subtitleBody.toByteArray().size.toString(), headResponse.getHeaderField("Content-Length"))
                val response = connection("$url?token=123456")
                assertEquals(200, response.responseCode)
                assertEquals("application/x-subrip", response.getHeaderField("Content-Type"))
                assertEquals(subtitleBody, response.inputStream.bufferedReader().use { it.readText() })
            }
        } finally {
            subtitleFile.deleteIfExists()
        }
    }

    @Test
    fun streamsOnlyPublishedPosterImages() {
        val posterFile = createTempFile("danmaku-poster", ".jpg")
        val posterBytes = byteArrayOf(0x01, 0x23, 0x45, 0x67)
        posterFile.writeBytes(posterBytes)
        val item = LibraryMediaItem(
            id = "episode-id",
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mp4",
            sizeBytes = 1024,
            mediaType = "video/mp4",
            streamPath = "/media/episode-id",
            posterPath = "/posters/episode-id",
        )

        try {
            LocalLibraryServer(port = 0, pairingToken = "123456").use { server ->
                server.publish(
                    PublishedLibrary(
                        catalog = LibraryCatalog("Example", 123, listOf(item)),
                        filesById = emptyMap(),
                        posterFilesById = mapOf(item.id to posterFile),
                    ),
                )
                server.start()
                val url = "${server.baseUrl()}${item.posterPath}"

                assertEquals(401, connection(url).responseCode)
                assertEquals(404, connection("${server.baseUrl()}/posters/missing?token=123456").responseCode)
                val headResponse = connection("$url?token=123456").apply {
                    requestMethod = "HEAD"
                }
                assertEquals(200, headResponse.responseCode)
                assertEquals(posterBytes.size.toString(), headResponse.getHeaderField("Content-Length"))
                val response = connection("$url?token=123456")
                assertEquals(200, response.responseCode)
                assertContentEquals(posterBytes, response.inputStream.readBytes())
            }
        } finally {
            posterFile.deleteIfExists()
        }
    }

    @Test
    fun acceptsOnlyAuthenticatedPostHooks() {
        val accepted = AtomicInteger()
        val hook = AuthenticatedPostHook(
            path = "/api/hooks/test",
            token = "0123456789abcdef",
            onAccepted = accepted::incrementAndGet,
        )

        LocalLibraryServer(
            port = 0,
            pairingToken = "123456",
            authenticatedPostHooks = listOf(hook),
        ).use { server ->
            server.start()
            val url = "${server.baseUrl()}${hook.path}"

            assertEquals(405, connection(url).responseCode)
            assertEquals(401, connection(url, method = "POST").responseCode)
            assertEquals(
                401,
                connection(
                    url = url,
                    method = "POST",
                    headers = mapOf(LocalLibraryServer.WEBHOOK_TOKEN_HEADER to "wrong-token-value"),
                ).responseCode,
            )
            assertEquals(
                202,
                connection(
                    url = url,
                    method = "POST",
                    headers = mapOf(LocalLibraryServer.WEBHOOK_TOKEN_HEADER to "0123456789abcdef"),
                ).responseCode,
            )
            assertEquals(1, accepted.get())
            assertEquals(
                "AuthenticatedPostHook(path=/api/hooks/test, token=<redacted>)",
                hook.toString(),
            )
        }
    }

    @Test
    fun rejectsUnauthorizedMediaRequestsAndInvalidRanges() {
        withServer(byteArrayOf(0, 1, 2, 3, 4, 5)) { server, item ->
            val mediaUrl = "${server.baseUrl()}${item.streamPath}"

            assertEquals(401, connection(mediaUrl).responseCode)
            listOf(
                "items=0-1",
                "bytes=1-2,4-5",
                "bytes=1-nope",
                "bytes=-0",
                "bytes=4-2",
                "bytes=9-10",
            ).forEach { range ->
                val response = connection("$mediaUrl?token=123456", range)
                assertEquals(416, response.responseCode, "range: $range")
                assertEquals("bytes */6", response.getHeaderField("Content-Range"))
            }
        }
    }

    @Test
    fun emitsDiagnosticEventsForPublishedLibrariesAndMediaRequests() {
        val events = Collections.synchronizedList(mutableListOf<LocalLibraryServerEvent>())

        withServer(
            mediaBytes = byteArrayOf(0, 1, 2, 3, 4, 5),
            eventSink = events::add,
        ) { server, item ->
            val mediaUrl = "${server.baseUrl()}${item.streamPath}?token=123456"
            val response = connection(mediaUrl, "bytes=1-3")

            assertEquals(206, response.responseCode)
            assertContentEquals(byteArrayOf(1, 2, 3), response.inputStream.use { it.readBytes() })
        }

        assertTrue(
            events.any { event ->
                event.category == "library" &&
                    event.method == "PUBLISH" &&
                    event.path == "/api/library" &&
                    event.status == 200 &&
                    event.detail == "items=1"
            },
        )
        assertTrue(
            events.any { event ->
                event.category == "media" &&
                    event.method == "GET" &&
                    event.path == "/media/episode-id" &&
                    event.status == 206 &&
                    "range=bytes=1-3" in event.detail &&
                    "bytes=3" in event.detail
            },
        )
    }

    @Test
    fun streamsLargeFilesAndConcurrentRanges() {
        val mediaBytes = ByteArray(LARGE_FIXTURE_SIZE) { index -> (index % 251).toByte() }

        withServer(mediaBytes) { server, item ->
            val mediaUrl = "${server.baseUrl()}${item.streamPath}?token=123456"
            val ranges = listOf(
                0L..65_535L,
                32_768L..98_303L,
                1_048_576L..1_114_111L,
                (LARGE_FIXTURE_SIZE - 65_536).toLong() until LARGE_FIXTURE_SIZE.toLong(),
            )
            val executor = Executors.newFixedThreadPool(ranges.size)

            try {
                val results = executor.invokeAll(
                    ranges.map { range ->
                        Callable {
                            val response = connection(mediaUrl, "bytes=${range.first}-${range.last}")
                            assertEquals(206, response.responseCode)
                            assertEquals(
                                "bytes ${range.first}-${range.last}/$LARGE_FIXTURE_SIZE",
                                response.getHeaderField("Content-Range"),
                            )
                            response.inputStream.use { it.readBytes() }
                        }
                    },
                )

                ranges.zip(results).forEach { (range, result) ->
                    assertContentEquals(
                        mediaBytes.copyOfRange(range.first.toInt(), range.last.toInt() + 1),
                        result.get(),
                    )
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun supportsOpenEndedAndSuffixRanges() {
        withServer(byteArrayOf(0, 1, 2, 3, 4, 5)) { server, item ->
            val mediaUrl = "${server.baseUrl()}${item.streamPath}?token=123456"

            assertContentEquals(
                byteArrayOf(2, 3, 4, 5),
                connection(mediaUrl, "bytes=2-").inputStream.use { it.readBytes() },
            )
            assertContentEquals(
                byteArrayOf(4, 5),
                connection(mediaUrl, "bytes=-2").inputStream.use { it.readBytes() },
            )
        }
    }

    @Test
    fun storesSequentialProgressUpdates() {
        withServer(byteArrayOf(0, 1, 2, 3, 4, 5)) { server, item ->
            val progressUrl = "${server.baseUrl()}/api/progress/${item.id}?token=123456"
            val progressListUrl = "${server.baseUrl()}/api/progress?token=123456"
            val paused = PlaybackProgress(
                mediaId = item.id,
                positionMs = 12_345,
                durationMs = 98_765,
                updatedAtEpochMs = 100,
            )
            val seeked = paused.copy(
                positionMs = 45_000,
                updatedAtEpochMs = 200,
            )
            val completed = paused.copy(
                positionMs = 98_765,
                updatedAtEpochMs = 300,
            )

            assertEquals(401, connection("${server.baseUrl()}/api/progress").responseCode)
            assertEquals(404, connection(progressUrl).responseCode)
            assertEquals(
                emptyList(),
                Json.decodeFromString<List<PlaybackProgress>>(
                    connection(progressListUrl).inputStream.bufferedReader().use { it.readText() },
                ),
            )

            listOf(paused, seeked, completed).forEach { progress ->
                assertEquals(
                    204,
                    connection(
                        url = progressUrl,
                        method = "PUT",
                        body = Json.encodeToString(progress),
                    ).responseCode,
                )
                assertEquals(
                    progress,
                    Json.decodeFromString<PlaybackProgress>(
                        connection(progressUrl).inputStream.bufferedReader().use { it.readText() },
                    ),
                )
            }
            assertEquals(
                listOf(completed),
                Json.decodeFromString<List<PlaybackProgress>>(
                    connection(progressListUrl).inputStream.bufferedReader().use { it.readText() },
                ),
            )

            assertEquals(
                400,
                connection(
                    url = progressUrl,
                    method = "PUT",
                    body = Json.encodeToString(completed.copy(mediaId = "other")),
                ).responseCode,
            )
        }
    }

    private fun withServer(
        mediaBytes: ByteArray,
        eventSink: (LocalLibraryServerEvent) -> Unit = {},
        block: (LocalLibraryServer, LibraryMediaItem) -> Unit,
    ) {
        val mediaFile = createTempFile("danmaku-server-core", ".mp4")
        mediaFile.writeBytes(mediaBytes)
        val item = LibraryMediaItem(
            id = "episode-id",
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mp4",
            sizeBytes = mediaBytes.size.toLong(),
            mediaType = "video/mp4",
            streamPath = "/media/episode-id",
        )

        try {
            LocalLibraryServer(
                port = 0,
                pairingToken = "123456",
                eventSink = eventSink,
            ).use { server ->
                server.publish(
                    PublishedLibrary(
                        catalog = LibraryCatalog(
                            rootName = "Example Library",
                            indexedAtEpochMs = 123,
                            items = listOf(item),
                        ),
                        filesById = mapOf(item.id to mediaFile),
                    ),
                )
                server.start()
                block(server, item)
            }
        } finally {
            mediaFile.deleteIfExists()
        }
    }

    private fun connection(
        url: String,
        range: String? = null,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            range?.let { setRequestProperty("Range", it) }
            headers.forEach(::setRequestProperty)
            body?.let {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.bufferedWriter().use { writer -> writer.write(it) }
            }
        }

    private companion object {
        const val LARGE_FIXTURE_SIZE = 2 * 1024 * 1024
    }
}
