package app.danmaku.provider.external

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalAnimeTrackingClientsTest {
    @Test
    fun myAnimeListTrackingClientWritesListStatusWithBearerToken() {
        var requestBody = ""
        val client = MyAnimeListTrackingClient(
            connection = MyAnimeListTrackingConnection(
                accessToken = "mal-access-token",
                baseUri = "https://example.test/".toUri(),
            ),
            httpPatch = ExternalAnimeHttpPatch { url, headers, body ->
                assertEquals("https://example.test/anime/52991/my_list_status", url.toString())
                assertEquals("Bearer mal-access-token", headers["Authorization"])
                assertEquals("application/x-www-form-urlencoded", headers["Content-Type"])
                requestBody = body
                """
                {
                  "status": "watching",
                  "score": 8,
                  "num_episodes_watched": 3
                }
                """.trimIndent()
            },
        )

        val entry = client.updateListEntry(
            ExternalAnimeTrackingUpdate(
                animeId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991),
                status = ExternalAnimeListStatus.WATCHING,
                watchedEpisodes = 3,
                score = 8,
            ),
        )

        assertTrue(requestBody.contains("status=watching"))
        assertTrue(requestBody.contains("num_watched_episodes=3"))
        assertTrue(requestBody.contains("score=8"))
        assertEquals(ExternalAnimeListStatus.WATCHING, entry.status)
        assertEquals(3, entry.watchedEpisodes)
        assertEquals(8, entry.score)
    }

    @Test
    fun myAnimeListTrackingClientReadsCurrentListStatusWithBearerToken() {
        val client = MyAnimeListTrackingClient(
            connection = MyAnimeListTrackingConnection(
                accessToken = "mal-access-token",
                baseUri = "https://example.test/".toUri(),
            ),
            httpGet = ExternalAnimeHttpGet { url, headers ->
                assertEquals("https://example.test/anime/52991?fields=my_list_status", url.toString())
                assertEquals("Bearer mal-access-token", headers["Authorization"])
                """
                {
                  "id": 52991,
                  "my_list_status": {
                    "status": "watching",
                    "score": 8,
                    "num_episodes_watched": 4,
                    "updated_at": "2024-01-02T03:04:05+00:00"
                  }
                }
                """.trimIndent()
            },
        )

        val entry = client.fetchListEntry(ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991))

        requireNotNull(entry)
        assertEquals(ExternalAnimeListStatus.WATCHING, entry.status)
        assertEquals(4, entry.watchedEpisodes)
        assertEquals(8, entry.score)
        assertEquals(1_704_164_645_000, entry.updatedAtEpochMs)
    }

    @Test
    fun bangumiTrackingClientWritesCollectionStatusWithBearerToken() {
        var requestBody = ""
        val client = BangumiTrackingClient(
            connection = BangumiTrackingConnection(
                accessToken = "bangumi-access-token",
                baseUri = "https://example.test/".toUri(),
                userAgent = "DanmakuTest/1.0",
            ),
            httpPatch = ExternalAnimeHttpPatch { url, headers, body ->
                assertEquals("https://example.test/v0/users/-/collections/400602", url.toString())
                assertEquals("Bearer bangumi-access-token", headers["Authorization"])
                assertEquals("DanmakuTest/1.0", headers["User-Agent"])
                assertEquals("application/json", headers["Content-Type"])
                requestBody = body
                """
                {
                  "type": 2,
                  "rate": 9,
                  "ep_status": 28
                }
                """.trimIndent()
            },
        )

        val entry = client.updateListEntry(
            ExternalAnimeTrackingUpdate(
                animeId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
                status = ExternalAnimeListStatus.COMPLETED,
                watchedEpisodes = 28,
                score = 9,
            ),
        )

        assertTrue(requestBody.contains("\"type\":2"))
        assertTrue(requestBody.contains("\"ep_status\":28"))
        assertTrue(requestBody.contains("\"rate\":9"))
        assertEquals(ExternalAnimeListStatus.COMPLETED, entry.status)
        assertEquals(28, entry.watchedEpisodes)
        assertEquals(9, entry.score)
    }

    @Test
    fun bangumiTrackingClientReadsCurrentCollectionStatusWithBearerToken() {
        val client = BangumiTrackingClient(
            connection = BangumiTrackingConnection(
                accessToken = "bangumi-access-token",
                baseUri = "https://example.test/".toUri(),
                userAgent = "DanmakuTest/1.0",
            ),
            httpGet = ExternalAnimeHttpGet { url, headers ->
                assertEquals("https://example.test/v0/users/-/collections/400602", url.toString())
                assertEquals("Bearer bangumi-access-token", headers["Authorization"])
                assertEquals("DanmakuTest/1.0", headers["User-Agent"])
                """
                {
                  "type": 3,
                  "rate": 9,
                  "ep_status": 12
                }
                """.trimIndent()
            },
        )

        val entry = client.fetchListEntry(ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602))

        requireNotNull(entry)
        assertEquals(ExternalAnimeListStatus.WATCHING, entry.status)
        assertEquals(12, entry.watchedEpisodes)
        assertEquals(9, entry.score)
    }
}

private fun String.toUri() = java.net.URI(this)
