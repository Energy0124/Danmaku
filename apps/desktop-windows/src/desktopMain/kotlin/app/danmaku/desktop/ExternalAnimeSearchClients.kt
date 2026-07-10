package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.rankExternalAnimeMatches

interface ExternalAnimeSearchClient {
    val provider: ExternalAnimeProvider

    fun search(query: ExternalAnimeMatchQuery, limit: Int = DEFAULT_SEARCH_LIMIT): List<ExternalAnimeInfo>
}

class ExternalAnimeSearchService(
    private val clients: List<ExternalAnimeSearchClient>,
    private val catalogStore: DesktopLibraryCatalogStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    init {
        require(clients.map(ExternalAnimeSearchClient::provider).distinct().size == clients.size) {
            "external anime search clients must be unique by provider"
        }
    }

    fun searchAndCache(
        query: ExternalAnimeMatchQuery,
        providers: Set<ExternalAnimeProvider> = clients.mapTo(mutableSetOf(), ExternalAnimeSearchClient::provider),
        limitPerProvider: Int = DEFAULT_SEARCH_LIMIT,
    ): List<ExternalAnimeMatchCandidate> {
        require(limitPerProvider in 1..50) { "limitPerProvider must be between 1 and 50" }
        val results = clients
            .filter { client -> client.provider in providers }
            .flatMap { client ->
                query.searchTitles.flatMap { searchTitle ->
                    client.search(
                        query.copy(title = searchTitle, alternateTitles = emptyList()),
                        limitPerProvider,
                    )
                }
            }
            .distinctBy(ExternalAnimeInfo::id)
        results.forEach { anime ->
            catalogStore.saveExternalAnimeMetadataCache(
                DesktopExternalAnimeMetadataCache(
                    anime = anime,
                    fetchedAtEpochMs = nowEpochMs(),
                ),
            )
        }
        return rankExternalAnimeMatches(query, results)
    }
}

const val DEFAULT_SEARCH_LIMIT = 10
