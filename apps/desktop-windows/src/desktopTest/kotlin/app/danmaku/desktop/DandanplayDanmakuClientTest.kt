package app.danmaku.desktop

import app.danmaku.domain.DanmakuMode
import app.danmaku.domain.DanmakuSize
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DandanplayDanmakuClientTest {
    @Test
    fun matchesMediaAndFetchesSignedDanmakuComments() {
        DandanplayFixture().use { fixture ->
            val clock = Clock.fixed(Instant.ofEpochSecond(1_735_660_800), ZoneOffset.UTC)
            val connection = DandanplayConnection(
                baseUrl = fixture.baseUrl,
                appId = "test-app",
                appSecret = "test-secret",
            )
            val client = DandanplayDanmakuClient(connection = connection, clock = clock)

            val track = client.fetchBestMatchComments(
                DandanplayMediaFingerprint(
                    fileName = "Example S01E01.mkv",
                    fileHash = "658d05841b9476ccc7420b3f0bb21c3b",
                    fileSizeBytes = 123_456,
                    videoDurationSeconds = 1_420,
                ),
            )

            assertNotNull(track)
            assertEquals(123450001, track.match.episodeId)
            assertEquals("Example Anime - Episode 01", track.match.displayTitle)
            assertEquals(2, track.events.size)
            assertEquals("hello", track.events[0].text)
            assertEquals(1_500, track.events[0].timestampMs)
            assertEquals(DanmakuMode.TOP, track.events[1].style.mode)
            assertEquals(DanmakuSize.SMALL, track.events[1].style.size)
            assertEquals(0xFFFF0000u, track.events[1].style.colorArgb)
            assertFalse(connection.toString().contains("test-secret"))

            val matchRequest = fixture.requests.first { it.path == "/api/v2/match" }
            assertEquals("POST", matchRequest.method)
            assertTrue(matchRequest.body.contains("\"fileName\":\"Example S01E01.mkv\""))
            assertTrue(matchRequest.body.contains("\"fileHash\":\"658d05841b9476ccc7420b3f0bb21c3b\""))
            assertTrue(matchRequest.body.contains("\"fileSize\":123456"))
            assertTrue(matchRequest.body.contains("\"videoDuration\":1420"))
            assertTrue(matchRequest.body.contains("\"matchMode\":\"hashAndFileName\""))
            assertSignedHeaders(matchRequest, "/api/v2/match")

            val commentRequest = fixture.requests.first { it.path == "/api/v2/comment/123450001" }
            assertEquals("GET", commentRequest.method)
            assertEquals("withRelated=true", commentRequest.query)
            assertSignedHeaders(commentRequest, "/api/v2/comment/123450001")
        }
    }

    @Test
    fun supportsCredentialModeAndCompatibleServersWithoutCredentials() {
        DandanplayFixture().use { fixture ->
            val credentialClient = DandanplayDanmakuClient(
                DandanplayConnection(
                    baseUrl = fixture.baseUrl,
                    appId = "app",
                    appSecret = "secret",
                    authenticationMode = DandanplayAuthenticationMode.CREDENTIAL,
                ),
            )

            credentialClient.fetchComments(123450001)
            val credentialRequest = fixture.requests.last()
            assertEquals("app", credentialRequest.headers["X-AppId"])
            assertEquals("secret", credentialRequest.headers["X-AppSecret"])
            assertNull(credentialRequest.headers["X-Signature"])

            val compatibleClient = DandanplayDanmakuClient(DandanplayConnection(fixture.baseUrl))

            compatibleClient.fetchComments(123450001)
            val compatibleRequest = fixture.requests.last()
            assertNull(compatibleRequest.headers["X-AppId"])
            assertNull(compatibleRequest.headers["X-AppSecret"])
            assertNull(compatibleRequest.headers["X-Signature"])
        }
    }

    @Test
    fun computesDandanplayFingerprintFromFirstSixteenMegabytes() {
        val file = Files.createTempFile("danmaku-dandanplay", ".mkv")
        try {
            Files.writeString(file, "hello")

            val fingerprint = DandanplayMediaFingerprint.fromPath(file)

            assertEquals(file.fileName.toString(), fingerprint.fileName)
            assertEquals("5d41402abc4b2a76b9719d911017c592", fingerprint.normalizedFileHash)
            assertEquals(5, fingerprint.fileSizeBytes)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun rejectsUnsafeConfigurationAndInvalidApiResponses() {
        assertFailsWith<IllegalArgumentException> {
            DandanplayConnection("file:///tmp/dandanplay")
        }
        assertFailsWith<IllegalArgumentException> {
            DandanplayConnection("http://user:password@example.test")
        }
        assertFailsWith<IllegalArgumentException> {
            DandanplayConnection("http://example.test?secret=x")
        }
        assertFailsWith<IllegalArgumentException> {
            DandanplayConnection("http://example.test", appId = "app")
        }
        assertFailsWith<IllegalArgumentException> {
            DandanplayMediaFingerprint(
                fileName = "bad.mkv",
                fileHash = "not-md5",
                fileSizeBytes = 1,
            )
        }

        DandanplayFixture(commentResponse = """{"success":false,"message":"denied","comments":[]}""").use { fixture ->
            val client = DandanplayDanmakuClient(DandanplayConnection(fixture.baseUrl))

            assertFailsWith<IllegalStateException> {
                client.fetchComments(123450001)
            }
        }
    }

    private fun assertSignedHeaders(request: CapturedRequest, path: String) {
        assertEquals("test-app", request.headers["X-AppId"])
        assertEquals("1735660800", request.headers["X-Timestamp"])
        assertNull(request.headers["X-AppSecret"])
        assertEquals(
            expectedSignature("test-app", 1_735_660_800, path, "test-secret"),
            request.headers["X-Signature"],
        )
    }

    private fun expectedSignature(
        appId: String,
        timestamp: Long,
        path: String,
        appSecret: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$appId$timestamp${path.lowercase()}$appSecret".toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    private class DandanplayFixture(
        private val matchResponse: String = """
            {
              "success": true,
              "isMatched": true,
              "matches": [
                {
                  "episodeId": 123450001,
                  "animeId": 12345,
                  "animeTitle": "Example Anime",
                  "episodeTitle": "Episode 01",
                  "shift": 0
                }
              ]
            }
        """.trimIndent(),
        private val commentResponse: String = """
            {
              "success": true,
              "count": 3,
              "comments": [
                {"cid": "c-1", "p": "1.5,1,25,16777215,0,0,user,row-1", "m": "hello"},
                {"cid": "c-2", "p": "2.0,5,18,16711680,0,0,user,row-2", "m": "top"},
                {"cid": "bad", "p": "-1,1,25,16777215", "m": "skip"}
              ]
            }
        """.trimIndent(),
    ) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = CopyOnWriteArrayList<CapturedRequest>()
        val baseUrl: String
            get() = "http://127.0.0.1:${server.address.port}"

        init {
            server.createContext("/") { exchange ->
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                requests += CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    query = exchange.requestURI.query,
                    headers = mapOf(
                        "X-AppId" to exchange.requestHeaders.getFirst("X-AppId"),
                        "X-AppSecret" to exchange.requestHeaders.getFirst("X-AppSecret"),
                        "X-Timestamp" to exchange.requestHeaders.getFirst("X-Timestamp"),
                        "X-Signature" to exchange.requestHeaders.getFirst("X-Signature"),
                    ),
                    body = body,
                )
                val response = when (exchange.requestURI.path) {
                    "/api/v2/match" -> matchResponse
                    "/api/v2/comment/123450001" -> commentResponse
                    else -> """{"success":false,"message":"not found"}"""
                }
                respond(exchange, response)
            }
            server.start()
        }

        override fun close() {
            server.stop(0)
        }

        private fun respond(exchange: HttpExchange, response: String) {
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val query: String?,
        val headers: Map<String, String?>,
        val body: String,
    )
}
