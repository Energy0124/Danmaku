package app.danmaku.library.android

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.server.LocalLibraryServer
import app.danmaku.server.PublishedLibrary
import java.net.URI
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanLibraryClientIntegrationTest {
    @Test
    fun browsesStreamsAndSynchronizesProgressAgainstLocalServer() {
        val mediaBytes = byteArrayOf(0, 1, 2, 3, 4, 5)
        val mediaFile = createTempFile("danmaku-android-client", ".mp4")
        val subtitleFile = createTempFile("danmaku-android-client", ".srt")
        mediaFile.writeBytes(mediaBytes)
        subtitleFile.writeBytes("Hello".toByteArray())
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

        try {
            LocalLibraryServer(port = 0, pairingToken = "token with spaces").use { server ->
                server.publish(
                    PublishedLibrary(
                        catalog = catalog,
                        filesById = mapOf(item.id to mediaFile),
                        subtitleFilesById = mapOf(subtitle.id to subtitleFile),
                    ),
                )
                server.start()
                val client = LanLibraryClient()

                assertEquals(LanLibraryServerStatus(), client.fetchServerStatus(server.baseUrl()))
                assertEquals(catalog, client.fetchCatalog(server.baseUrl(), server.pairingToken))
                assertArrayEquals(
                    mediaBytes,
                    URI(client.streamUrl(server.baseUrl(), item, server.pairingToken))
                        .toURL()
                        .openStream()
                        .use { it.readBytes() },
                )
                assertArrayEquals(
                    "Hello".toByteArray(),
                    URI(client.subtitleUrl(server.baseUrl(), subtitle, server.pairingToken))
                        .toURL()
                        .openStream()
                        .use { it.readBytes() },
                )
                assertEquals(emptyList<PlaybackProgress>(), client.fetchAllProgress(server.baseUrl(), server.pairingToken))
                assertNull(client.fetchProgress(server.baseUrl(), item.id, server.pairingToken))

                client.saveProgress(server.baseUrl(), server.pairingToken, progress)

                assertEquals(
                    progress,
                    client.fetchProgress(server.baseUrl(), item.id, server.pairingToken),
                )
                assertEquals(
                    listOf(progress),
                    client.fetchAllProgress(server.baseUrl(), server.pairingToken),
                )
            }
        } finally {
            mediaFile.deleteIfExists()
            subtitleFile.deleteIfExists()
        }
    }
}
