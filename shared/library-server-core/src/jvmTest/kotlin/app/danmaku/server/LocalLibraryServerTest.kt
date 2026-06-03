package app.danmaku.server

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LocalLibraryServerTest {
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

            assertEquals(404, connection(progressUrl).responseCode)

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
            LocalLibraryServer(port = 0, pairingToken = "123456").use { server ->
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
    ): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            range?.let { setRequestProperty("Range", it) }
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
