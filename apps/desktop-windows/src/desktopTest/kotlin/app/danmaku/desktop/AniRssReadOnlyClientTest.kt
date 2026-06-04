package app.danmaku.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniRssReadOnlyClientTest {
    @Test
    fun normalizesReadOnlySnapshotWithoutSensitiveProviderUrls() {
        AniRssFixture().use { fixture ->
            val connection = AniRssConnection(fixture.baseUrl, "test-secret")
            val client = AniRssReadOnlyClient(connection)

            assertEquals(AniRssHealth(available = true), client.ping())
            val snapshot = client.fetchSnapshot()

            assertEquals("3.1.46", snapshot.about.version)
            assertEquals(1, snapshot.subscriptions.size)
            assertEquals("Example Show", snapshot.subscriptions.single().title)
            assertEquals("C:/Anime/Example Show/Season 1", snapshot.subscriptions.single().outputFolder)
            assertEquals("Episode 01.mkv", snapshot.subscriptions.single().completedEpisodes.single().fileName)
            assertEquals(42.5, snapshot.downloads.single().progressPercent)
            assertEquals("downloading", snapshot.downloads.single().state)
            assertFalse(snapshot.toString().contains("magnet:"))
            assertFalse(snapshot.toString().contains("torrent.example"))
            assertFalse(snapshot.toString().contains("rss.example"))
            assertFalse(connection.toString().contains("test-secret"))

            assertEquals(listOf(null, "test-secret", "test-secret", "test-secret", "test-secret", "test-secret"), fixture.apiKeys)
            assertTrue(fixture.requestBodies.any { it.contains("\"url\":\"https://rss.example/show\"") })
        }
    }

    @Test
    fun toleratesMissingOptionalFieldsAndDuplicateWeekEntries() {
        AniRssFixture(
            listAniData = """
                {
                  "weekList": [
                    {"items": [{"id": "same", "title": "Sparse Show"}]},
                    {"items": [{"id": "same", "title": "Sparse Show"}]}
                  ]
                }
            """.trimIndent(),
            downloadPathData = "{}",
            playListData = "[{}]",
            torrentsData = "[{}]",
        ).use { fixture ->
            val client = AniRssReadOnlyClient(AniRssConnection(fixture.baseUrl, "secret"))

            val snapshot = client.fetchSnapshot()

            assertEquals(1, snapshot.subscriptions.size)
            assertNull(snapshot.subscriptions.single().outputFolder)
            assertEquals("Untitled episode", snapshot.subscriptions.single().completedEpisodes.single().title)
            assertEquals("Unnamed download", snapshot.downloads.single().name)
        }
    }

    @Test
    fun rejectsUnsafeConnectionUrlsAndOversizedResponses() {
        assertFailsWith<IllegalArgumentException> {
            AniRssConnection("file:///tmp/ani-rss", "secret")
        }
        assertFailsWith<IllegalArgumentException> {
            AniRssConnection("http://user:password@example.test", "secret")
        }
        assertFailsWith<IllegalArgumentException> {
            AniRssConnection("http://example.test?api-key=secret", "secret")
        }

        AniRssFixture(aboutData = "\"${"x".repeat(128)}\"").use { fixture ->
            val client = AniRssReadOnlyClient(
                connection = AniRssConnection(fixture.baseUrl, "secret"),
                maxResponseBytes = 64,
            )

            assertFailsWith<IllegalStateException> {
                client.fetchAbout()
            }
        }
    }

    private class AniRssFixture(
        private val listAniData: String = """
            {
              "weekList": [
                {
                  "weekLabel": "Thursday",
                  "items": [
                    {
                      "id": "show-1",
                      "url": "https://rss.example/show",
                      "title": "Example Show",
                      "enable": true,
                      "currentEpisodeNumber": 3,
                      "totalEpisodeNumber": 12
                    }
                  ]
                }
              ]
            }
        """.trimIndent(),
        private val downloadPathData: String = """
            {"change": false, "downloadPath": "C:/Anime/Example Show/Season 1"}
        """.trimIndent(),
        private val playListData: String = """
            [
              {
                "title": "S01E01",
                "filename": "C:/Anime/Example Show/Season 1/Episode 01.mkv",
                "name": "Episode 01.mkv",
                "episode": 1.0,
                "lastModify": 1234,
                "formatSize": "1.2 GB",
                "extName": "mkv"
              }
            ]
        """.trimIndent(),
        private val torrentsData: String = """
            [
              {
                "id": "download-1",
                "name": "Example Show - 03",
                "state": "downloading",
                "progress": 42.5,
                "completed": 425,
                "size": 1000,
                "downloadDir": "C:/Downloads",
                "magnet": "magnet:?xt=urn:btih:secret",
                "torrent": "https://torrent.example/file.torrent"
              }
            ]
        """.trimIndent(),
        private val aboutData: String = """
            {"version": "3.1.46", "latest": "3.1.46", "update": false, "autoUpdate": true}
        """.trimIndent(),
    ) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val apiKeys = CopyOnWriteArrayList<String?>()
        val requestBodies = CopyOnWriteArrayList<String>()
        val baseUrl: String
            get() = "http://127.0.0.1:${server.address.port}"

        init {
            server.createContext("/api/ping") { exchange ->
                respond(exchange, "null")
            }
            server.createContext("/api/about") { exchange ->
                respond(exchange, aboutData)
            }
            server.createContext("/api/listAni") { exchange ->
                respond(exchange, listAniData)
            }
            server.createContext("/api/downloadPath") { exchange ->
                respond(exchange, downloadPathData)
            }
            server.createContext("/api/playList") { exchange ->
                respond(exchange, playListData)
            }
            server.createContext("/api/torrentsInfos") { exchange ->
                respond(exchange, torrentsData)
            }
            server.start()
        }

        override fun close() {
            server.stop(0)
        }

        private fun respond(exchange: HttpExchange, data: String) {
            apiKeys += exchange.requestHeaders.getFirst("api-key")
            requestBodies += exchange.requestBody.bufferedReader().use { it.readText() }
            val body = """{"code":200,"message":"success","data":$data}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }
}
