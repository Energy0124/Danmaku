package app.danmaku.desktop

import kotlinx.serialization.json.Json
import app.danmaku.domain.LibraryCatalog
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
    fun servesCatalogAndByteRanges() {
        val root = createTempDirectory("danmaku-server")
        root.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mp4")
            .writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))
        val indexed = LocalMediaLibraryIndexer.index(root)

        LocalLibraryServer(port = 0, pairingToken = "123456").use { server ->
            server.publish(indexed)
            server.start()

            val unauthorizedConnection = connection("${server.baseUrl()}/api/library")
            assertEquals(401, unauthorizedConnection.responseCode)

            val catalogConnection = connection("${server.baseUrl()}/api/library?token=123456")
            assertEquals(200, catalogConnection.responseCode)
            val catalog = Json.decodeFromString<LibraryCatalog>(
                catalogConnection.inputStream.bufferedReader().use { it.readText() },
            )
            assertEquals("Episode 01", catalog.items.single().episodeTitle)

            val streamConnection = connection(
                "${server.baseUrl()}${catalog.items.single().streamPath}?token=123456",
                range = "bytes=2-4",
            )
            assertEquals(206, streamConnection.responseCode)
            assertEquals("bytes 2-4/6", streamConnection.getHeaderField("Content-Range"))
            assertContentEquals(byteArrayOf(2, 3, 4), streamConnection.inputStream.readBytes())
        }

        root.toFile().deleteRecursively()
    }

    private fun connection(
        url: String,
        range: String? = null,
    ): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            range?.let { setRequestProperty("Range", it) }
        }
}
