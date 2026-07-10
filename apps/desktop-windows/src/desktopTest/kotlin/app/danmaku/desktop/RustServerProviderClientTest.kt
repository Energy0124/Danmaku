package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RustServerProviderClientTest {
    @Test
    fun readsAndWritesExternalListEntriesThroughRustServerRoutes() {
        val requests = CopyOnWriteArrayList<RecordedProviderRequest>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/providers/list/entry") { exchange ->
            requests += RecordedProviderRequest(
                method = exchange.requestMethod,
                query = exchange.requestURI.rawQuery.orEmpty(),
                body = exchange.requestBody.bufferedReader().use { it.readText() },
            )
            val response =
                """{"animeId":{"provider":"MY_ANIME_LIST","value":42},"status":"WATCHING","watchedEpisodes":3,"score":8}"""
                    .toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val client = RustServerProviderClient(
                provider = ExternalAnimeProvider.MY_ANIME_LIST,
                baseUrl = { "http://127.0.0.1:${server.address.port}" },
                pairingToken = { "123456" },
            )
            val animeId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 42)

            val fetched = client.fetchListEntry(animeId)
            val updated = client.updateListEntry(
                ExternalAnimeTrackingUpdate(
                    animeId = animeId,
                    status = ExternalAnimeListStatus.WATCHING,
                    watchedEpisodes = 3,
                    score = 8,
                ),
            )

            assertEquals(3, fetched?.watchedEpisodes)
            assertEquals(8, updated.score)
            assertEquals(listOf("GET", "POST"), requests.map(RecordedProviderRequest::method))
            assertTrue(requests[0].query.contains("provider=my_anime_list"))
            assertTrue(requests[0].query.contains("animeId=42"))
            assertTrue(requests.all { it.query.contains("token=123456") })
            assertTrue(requests[1].body.contains("\"watchedEpisodes\":3"))
        } finally {
            server.stop(0)
        }
    }
}

private data class RecordedProviderRequest(
    val method: String,
    val query: String,
    val body: String,
)
