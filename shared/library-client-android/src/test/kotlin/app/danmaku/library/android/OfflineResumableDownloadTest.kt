package app.danmaku.library.android

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineResumableDownloadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun appendsAValidatedPartialResponse() = withServer { server ->
        val destination = File(temporaryFolder.root, "video.mkv")
        File(temporaryFolder.root, "video.mkv.part").writeText("abc")
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 3-5/6")
                .setBody("def"),
        )

        runBlocking { downloadResumableFile(server.url("/video").toString(), destination, 6) }

        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
        assertEquals("abcdef", destination.readText())
    }

    @Test
    fun restartsWhenServerIgnoresRangeRequest() = withServer { server ->
        val destination = File(temporaryFolder.root, "video.mkv")
        File(temporaryFolder.root, "video.mkv.part").writeText("abc")
        server.enqueue(MockResponse().setResponseCode(200).setBody("abcdef"))

        runBlocking { downloadResumableFile(server.url("/video").toString(), destination, 6) }

        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
        assertEquals("abcdef", destination.readText())
    }

    @Test
    fun classifiesMissingVideoAsPermanentFailure() = withServer { server ->
        val destination = File(temporaryFolder.root, "video.mkv")
        server.enqueue(MockResponse().setResponseCode(404))

        assertThrows(PermanentDownloadException::class.java) {
            runBlocking { downloadResumableFile(server.url("/video").toString(), destination, 6) }
        }
    }

    @Test
    fun throttlesProgressCallbacksInsteadOfReportingEveryBuffer() = withServer { server ->
        val destination = File(temporaryFolder.root, "video.mkv")
        val payload = "x".repeat(100_000)
        val progress = mutableListOf<Long>()
        var elapsedMs = 0L
        server.enqueue(MockResponse().setResponseCode(200).setBody(payload))

        runBlocking {
            downloadResumableFile(
                url = server.url("/video").toString(),
                destination = destination,
                expectedBytes = payload.length.toLong(),
                progressUpdateIntervalMs = 1_000,
                elapsedRealtimeMs = { elapsedMs.also { elapsedMs += 100 } },
                onProgress = progress::add,
            )
        }

        assertEquals(payload.length.toLong(), destination.length())
        assertEquals(payload.length.toLong(), progress.last())
        assertEquals(2, progress.size)
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use(block)
    }
}
