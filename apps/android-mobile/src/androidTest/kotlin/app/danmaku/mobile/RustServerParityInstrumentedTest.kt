package app.danmaku.mobile

import androidx.test.platform.app.InstrumentationRegistry
import app.danmaku.domain.PlaybackProgress
import app.danmaku.library.android.LanLibraryClient
import java.net.HttpURLConnection
import java.net.URI
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RustServerParityInstrumentedTest {
    @Test
    fun androidClientTalksToLiveRustServerHost() {
        val arguments = InstrumentationRegistry.getArguments()
        val serverBaseUrl = arguments.getString(SERVER_BASE_URL_ARGUMENT)?.trim().orEmpty()
        val pairingToken = arguments.getString(PAIRING_TOKEN_ARGUMENT)?.trim().orEmpty()
        assumeTrue(
            "Rust server parity test requires instrumentation arguments " +
                "$SERVER_BASE_URL_ARGUMENT and $PAIRING_TOKEN_ARGUMENT.",
            serverBaseUrl.isNotBlank() && pairingToken.isNotBlank(),
        )

        val client = LanLibraryClient()
        val status = client.fetchServerStatus(serverBaseUrl)
        assertEquals(1, status.apiVersion)
        assertTrue(status.mediaStreaming)
        assertTrue(status.progressSync)

        val catalog = client.fetchCatalog(serverBaseUrl, pairingToken)
        assertTrue("Expected Rust host fixture catalog to contain media.", catalog.items.isNotEmpty())
        val item = catalog.items.first()

        val mediaBytes = readMediaRange(client.streamUrl(serverBaseUrl, item, pairingToken))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), mediaBytes)

        val progress = PlaybackProgress(
            mediaId = item.id,
            positionMs = 32_123,
            durationMs = 65_432,
            updatedAtEpochMs = 2_468_135_790,
        )
        client.saveProgress(serverBaseUrl, pairingToken, progress)

        assertEquals(progress, client.fetchProgress(serverBaseUrl, item.id, pairingToken))
        assertNotNull(
            client.fetchAllProgress(serverBaseUrl, pairingToken)
                .firstOrNull { it.mediaId == item.id && it.positionMs == progress.positionMs },
        )
    }

    private fun readMediaRange(streamUrl: String): ByteArray {
        val connection = URI(streamUrl).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("Range", "bytes=1-4")
        return try {
            assertEquals(HttpURLConnection.HTTP_PARTIAL, connection.responseCode)
            assertTrue(
                "Expected byte range Content-Range header.",
                connection.getHeaderField("Content-Range")?.startsWith("bytes 1-4/") == true,
            )
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val SERVER_BASE_URL_ARGUMENT = "danmakuServerBaseUrl"
        const val PAIRING_TOKEN_ARGUMENT = "danmakuPairingToken"
    }
}
