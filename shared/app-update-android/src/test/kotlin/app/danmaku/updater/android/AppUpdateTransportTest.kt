package app.danmaku.updater.android

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class AppUpdateTransportTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun downloadsTheExactDeclaredPayloadAndReportsItsDigest() = runTest {
        val payload = "apk-payload".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(payload.toString(Charsets.UTF_8)))
        val destination = File.createTempFile("danmaku-update", ".apk").apply { delete() }

        try {
            val progress = mutableListOf<Long>()
            val result = HttpAppUpdateTransport().download(
                server.url("/app.apk").toString(),
                destination,
                payload.size.toLong(),
                progress::add,
            )

            assertEquals(payload.size.toLong(), result.sizeBytes)
            assertEquals(sha256(payload), result.sha256)
            assertEquals(payload.toList(), destination.readBytes().toList())
            assertEquals(payload.size.toLong(), progress.last())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun rejectsPayloadsThatExceedTheManifestSize() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("too-large"))
        val destination = File.createTempFile("danmaku-update", ".apk").apply { delete() }

        try {
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    HttpAppUpdateTransport().download(
                        server.url("/app.apk").toString(),
                        destination,
                        2,
                    ) {}
                }
            }
        } finally {
            destination.delete()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
