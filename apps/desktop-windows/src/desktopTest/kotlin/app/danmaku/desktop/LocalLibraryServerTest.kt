package app.danmaku.desktop

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.PlaybackProgress
import app.danmaku.server.LocalLibraryServer
import app.danmaku.server.PublishedLibrary
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LocalLibraryServerTest {
    @Test
    fun servesSamePcCatalogAndStreams() {
        val root = createTempDirectory("danmaku-server")
        root.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mp4")
            .writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))
        val indexed = LocalMediaLibraryIndexer.index(root)

        LocalLibraryServer(port = 0, pairingToken = "123456").use { server ->
            server.publish(indexed.toPublishedLibrary())
            server.start()

            val catalogConnection = connection("${server.baseUrl()}/api/library")
            assertEquals(200, catalogConnection.responseCode)
            val catalog = Json.decodeFromString<LibraryCatalog>(
                catalogConnection.inputStream.bufferedReader().use { it.readText() },
            )
            assertEquals("Episode 01", catalog.items.single().episodeTitle)

            val fullStreamConnection = connection(
                "${server.baseUrl()}${catalog.items.single().streamPath}",
            )
            assertEquals(200, fullStreamConnection.responseCode)
            assertContentEquals(
                byteArrayOf(0, 1, 2, 3, 4, 5),
                fullStreamConnection.inputStream.readBytes(),
            )

            val streamConnection = connection(
                "${server.baseUrl()}${catalog.items.single().streamPath}",
                range = "bytes=2-4",
            )
            assertEquals(206, streamConnection.responseCode)
            assertEquals("bytes 2-4/6", streamConnection.getHeaderField("Content-Range"))
            assertContentEquals(byteArrayOf(2, 3, 4), streamConnection.inputStream.readBytes())
            assertEquals(
                416,
                connection(
                    "${server.baseUrl()}${catalog.items.single().streamPath}",
                    range = "bytes=9-10",
                ).responseCode,
            )
        }

        root.toFile().deleteRecursively()
    }

    @Test
    fun persistsPairedPlaybackProgressForIndexedMedia() {
        val root = createTempDirectory("danmaku-progress-server")
        root.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mp4")
            .writeBytes(byteArrayOf(0, 1, 2))
        val indexed = LocalMediaLibraryIndexer.index(root)
        val mediaId = indexed.catalog.items.single().id
        val progress = PlaybackProgress(
            mediaId = mediaId,
            positionMs = 12_345,
            durationMs = 98_765,
            updatedAtEpochMs = 123,
        )

        LocalLibraryServer(port = 0, pairingToken = "123456").use { server ->
            server.publish(indexed.toPublishedLibrary())
            server.start()

            assertEquals(
                404,
                connection("${server.baseUrl()}/api/progress/$mediaId")
                    .responseCode,
            )
            assertEquals(
                204,
                connection(
                    url = "${server.baseUrl()}/api/progress/$mediaId",
                    method = "PUT",
                    body = Json.encodeToString(progress),
                ).responseCode,
            )

            val progressConnection =
                connection("${server.baseUrl()}/api/progress/$mediaId")
            assertEquals(200, progressConnection.responseCode)
            assertEquals(
                progress,
                Json.decodeFromString<PlaybackProgress>(
                    progressConnection.inputStream.bufferedReader().use { it.readText() },
                ),
            )
            assertEquals(
                404,
                connection("${server.baseUrl()}/api/progress/missing")
                    .responseCode,
            )
        }

        root.toFile().deleteRecursively()
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
                outputStream.bufferedWriter().use { writer -> writer.write(it) }
            }
        }

    private fun IndexedLocalLibrary.toPublishedLibrary(): PublishedLibrary =
        PublishedLibrary(
            catalog = catalog,
            filesById = filesById,
        )
}
