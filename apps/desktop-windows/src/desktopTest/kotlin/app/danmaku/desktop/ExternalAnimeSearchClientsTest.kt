package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalAnimeSearchClientsTest {
    @Test
    fun myAnimeListClientNormalizesSearchResultsAndSendsClientId() {
        val client = MyAnimeListAnimeSearchClient(
            connection = MyAnimeListSearchConnection(
                clientId = "test-client-id",
                baseUri = "https://example.test/".toUri(),
            ),
            httpGet = ExternalAnimeHttpGet { url, headers ->
                assertTrue(url.toString().startsWith("https://example.test/anime?q=Frieren"))
                assertEquals("test-client-id", headers["X-MAL-CLIENT-ID"])
                """
                {
                  "data": [
                    {
                      "node": {
                        "id": 52991,
                        "title": "Sousou no Frieren",
                        "alternative_titles": {
                          "en": "Frieren: Beyond Journey's End",
                          "ja": "葬送のフリーレン",
                          "synonyms": ["Frieren"]
                        },
                        "num_episodes": 28,
                        "start_date": "2023-09-29",
                        "main_picture": {
                          "large": "https://cdn.myanimelist.net/images/anime/1015/138006l.jpg"
                        },
                        "synopsis": "After the party defeats the Demon King."
                      }
                    }
                  ]
                }
                """.trimIndent()
            },
        )

        val results = client.search(ExternalAnimeMatchQuery("Frieren"), limit = 5)

        assertEquals(1, results.size)
        val anime = results.single()
        assertEquals(ExternalAnimeProvider.MY_ANIME_LIST, anime.id.provider)
        assertEquals(52991, anime.id.value)
        assertEquals("Sousou no Frieren", anime.titles.primary)
        assertEquals("Frieren: Beyond Journey's End", anime.titles.english)
        assertEquals(28, anime.episodeCount)
        assertEquals(2023, anime.startYear)
    }

    @Test
    fun bangumiClientNormalizesSearchResultsAndFiltersAnimeSubjects() {
        var requestBody = ""
        val client = BangumiAnimeSearchClient(
            baseUri = "https://example.test/".toUri(),
            userAgent = "DanmakuTest/1.0",
            httpPost = ExternalAnimeHttpPost { url, headers, body ->
                assertEquals("https://example.test/v0/search/subjects?limit=5&offset=0", url.toString())
                assertEquals("DanmakuTest/1.0", headers["User-Agent"])
                requestBody = body
                """
                {
                  "data": [
                    {
                      "id": 400602,
                      "name": "葬送のフリーレン",
                      "name_cn": "葬送的芙莉莲",
                      "summary": "勇者一行打倒魔王之后。",
                      "date": "2023-09-29",
                      "eps": 28,
                      "images": {
                        "common": "https://lain.bgm.tv/pic/cover/c/ef/50/400602.jpg"
                      }
                    }
                  ]
                }
                """.trimIndent()
            },
        )

        val results = client.search(ExternalAnimeMatchQuery("芙莉莲"), limit = 5)

        assertTrue(requestBody.contains("\"type\":[2]"))
        assertEquals(1, results.size)
        val anime = results.single()
        assertEquals(ExternalAnimeProvider.BANGUMI, anime.id.provider)
        assertEquals(400602, anime.id.value)
        assertEquals("葬送のフリーレン", anime.titles.primary)
        assertEquals("葬送的芙莉莲", anime.titles.chinese)
        assertEquals(28, anime.episodeCount)
        assertEquals(2023, anime.startYear)
    }

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
}

private fun String.toUri() = java.net.URI(this)
