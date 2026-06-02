package app.danmaku.library.jvm

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.server.LocalLibraryServer
import app.danmaku.server.PublishedLibrary
import java.net.URI
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
}
