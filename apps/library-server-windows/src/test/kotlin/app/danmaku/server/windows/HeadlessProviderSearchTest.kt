package app.danmaku.server.windows

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTitleSet
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadlessProviderSearchTest {
    @Test
    fun exposesAuthenticatedProviderSearchEndpoint() {
        val dataDirectory = createTempDirectory("danmaku-headless-provider-search")
        val capturedQueries = mutableListOf<ExternalAnimeMatchQuery>()
        val searchService = object : HeadlessExternalAnimeSearchService {
            override fun search(
                query: ExternalAnimeMatchQuery,
                providers: Set<ExternalAnimeProvider>,
                limitPerProvider: Int,
            ): List<ExternalAnimeMatchCandidate> {
                capturedQueries += query
                assertEquals(setOf(ExternalAnimeProvider.BANGUMI), providers)
                assertEquals(3, limitPerProvider)
                return listOf(
                    ExternalAnimeMatchCandidate(
                        anime = animeInfo(
                            provider = ExternalAnimeProvider.BANGUMI,
                            id = 362_810,
                            primaryTitle = "Frieren",
                            englishTitle = "Frieren: Beyond Journey's End",
                            episodeCount = 28,
                            startYear = 2023,
                        ),
                        confidence = 0.94,
                        matchedTitle = "Frieren: Beyond Journey's End",
                        evidence = listOf("title-match"),
                    ),
                )
            }
        }

        try {
            HeadlessLibraryServer(
                options = HeadlessServerOptions(
                    dataDirectory = dataDirectory,
                    port = 0,
                    pairingToken = "123456",
                ),
                discoveryAnnouncerFactory = { NoOpCloseable },
                externalAnimeSearchServiceFactory = { searchService },
            ).use { server ->
                server.start()
                val baseUrl = server.runtimeStatus.baseUrls.first()

                assertEquals(401, connection("$baseUrl/api/providers/search?title=Frieren").responseCode)
                assertEquals(400, connection("$baseUrl/api/providers/search?token=123456").responseCode)
                assertEquals(
                    400,
                    connection("$baseUrl/api/providers/search?token=123456&title=Frieren&providers=unknown")
                        .responseCode,
                )

                val matches = Json.decodeFromString<List<ExternalAnimeMatchCandidate>>(
                    connection(
                        "$baseUrl/api/providers/search?token=123456&title=Frieren" +
                            "&providers=bangumi&limit=3&episodeCount=28&startYear=2023",
                    ).inputStream.bufferedReader().use { it.readText() },
                )

                assertEquals(
                    ExternalAnimeMatchQuery(
                        title = "Frieren",
                        episodeCount = 28,
                        startYear = 2023,
                    ),
                    capturedQueries.single(),
                )
                val match = matches.single()
                assertEquals(ExternalAnimeProvider.BANGUMI, match.anime.id.provider)
                assertEquals(362_810L, match.anime.id.value)
                assertEquals("Frieren: Beyond Journey's End", match.matchedTitle)
                assertContentEquals(listOf("title-match"), match.evidence)
            }
        } finally {
            dataDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun searchServiceKeepsHealthyProviderResultsWhenAnotherProviderFails() {
        val failingClient = object : HeadlessExternalAnimeSearchClient {
            override val provider = ExternalAnimeProvider.MY_ANIME_LIST

            override fun search(query: ExternalAnimeMatchQuery, limit: Int): List<ExternalAnimeInfo> =
                error("provider unavailable")
        }
        val healthyClient = object : HeadlessExternalAnimeSearchClient {
            override val provider = ExternalAnimeProvider.BANGUMI

            override fun search(query: ExternalAnimeMatchQuery, limit: Int): List<ExternalAnimeInfo> =
                listOf(
                    animeInfo(
                        provider = ExternalAnimeProvider.BANGUMI,
                        id = 362_810,
                        primaryTitle = "Frieren",
                        episodeCount = 28,
                        startYear = 2023,
                    ),
                )
        }

        val matches = DefaultHeadlessExternalAnimeSearchService(listOf(failingClient, healthyClient)).search(
            query = ExternalAnimeMatchQuery(
                title = "Frieren",
                episodeCount = 28,
                startYear = 2023,
            ),
            providers = emptySet(),
            limitPerProvider = 5,
        )

        assertEquals(ExternalAnimeProvider.BANGUMI, matches.single().anime.id.provider)
        assertTrue(matches.single().confidence > 0.0)
    }

    private fun connection(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
        }

    private fun animeInfo(
        provider: ExternalAnimeProvider,
        id: Long,
        primaryTitle: String,
        englishTitle: String? = null,
        episodeCount: Int? = null,
        startYear: Int? = null,
    ): ExternalAnimeInfo =
        ExternalAnimeInfo(
            id = ExternalAnimeId(provider, id),
            titles = ExternalAnimeTitleSet(
                primary = primaryTitle,
                english = englishTitle,
            ),
            episodeCount = episodeCount,
            startYear = startYear,
        )

    private data object NoOpCloseable : AutoCloseable {
        override fun close() = Unit
    }
}
