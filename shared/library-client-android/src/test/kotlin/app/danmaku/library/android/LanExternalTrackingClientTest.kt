package app.danmaku.library.android

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

class LanExternalTrackingClientTest {
    @Test
    fun readsAccountsAndTrackingWithoutClientAuthentication() {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(ACCOUNTS_JSON))
            server.enqueue(jsonResponse(TRACKING_JSON))
            val client = LanExternalTrackingClient()
            val baseUrl = server.url("/").toString()

            val accounts = client.fetchAccounts(baseUrl)
            val tracking = client.fetchTracking(baseUrl)

            assertEquals(ProviderAccountState.CONNECTED, accounts.myAnimeList.state)
            assertEquals(1, tracking.plan.summary.updateCount)
            repeat(2) { assertEquals(null, server.takeRequest().getHeader("Authorization")) }
        }
    }

    @Test
    fun syncPostsTheExactPreviewedUpdates() {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse(OPERATION_JSON))
            val update = ExternalAnimeTrackingUpdate(
                animeId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 42),
                status = ExternalAnimeListStatus.WATCHING,
                watchedEpisodes = 3,
            )

            val response = LanExternalTrackingClient().sync(
                server.url("/").toString(),
                listOf(update),
            )

            assertEquals(1, response.successCount)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/providers/tracking/sync", request.path)
            assertEquals(null, request.getHeader("Authorization"))
            val body = Json.parseToJsonElement(request.body.readUtf8())
            val expected = buildJsonObject {
                put("expectedUpdates", buildJsonArray {
                    add(buildJsonObject {
                        put("animeId", buildJsonObject {
                            put("provider", "MY_ANIME_LIST")
                            put("value", 42)
                        })
                        put("status", "WATCHING")
                        put("watchedEpisodes", 3)
                    })
                })
            }
            assertEquals(expected, body)
        }
    }

    @Test
    fun preservesHttpStatusForConflictAndServerFailures() {
        listOf(401, 404, 409, 502).forEach { status ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(status).setBody("failure-$status"))
                val failure = runCatching {
                    LanExternalTrackingClient().fetchTracking(server.url("/").toString())
                }.exceptionOrNull() as LanExternalTrackingException

                assertEquals(status, failure.statusCode)
                assertEquals("failure-$status", failure.message)
            }
        }
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val ACCOUNTS_JSON = """
            {"myAnimeList":{"state":"CONNECTED","userId":"1","displayName":"MAL user"},"bangumi":{"state":"DISCONNECTED"},"bangumiTokenUrl":"https://next.bgm.tv/demo/access-token"}
        """
        const val TRACKING_JSON = """
            {"generatedAtEpochMs":1,"series":[],"mappings":[],"listEntries":[],"plan":{"summary":{"updateCount":1,"skippedCount":0,"conflictCount":0,"failureCount":0,"myAnimeListUpdateCount":1,"bangumiUpdateCount":0},"updates":[],"skipped":[],"conflicts":[],"mappingConflicts":[],"failures":[]}}
        """
        const val OPERATION_JSON = """
            {"document":$TRACKING_JSON,"successCount":1,"conflictCount":0,"missingCount":0,"errors":[]}
        """
    }
}
