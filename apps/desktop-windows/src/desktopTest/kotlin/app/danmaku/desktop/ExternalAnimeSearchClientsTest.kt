package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeProvider
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
}

private fun String.toUri() = java.net.URI(this)
