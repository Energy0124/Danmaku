package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeExternalLink
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.LibraryAnimeMetadata
import app.danmaku.domain.LibrarySeries

internal class DesktopExternalAnimeMappingSuggester(
    private val searchService: ExternalAnimeSearchService,
) {
    fun suggestForSeries(
        series: LibrarySeries,
        existingMappings: List<ExternalAnimeMapping>,
        providers: Set<ExternalAnimeProvider>,
        limitPerProvider: Int = DEFAULT_SUGGESTION_SEARCH_LIMIT,
    ): List<ExternalAnimeMappingSuggestion> {
        val mappedProviders = existingMappings.mapTo(mutableSetOf()) { mapping -> mapping.animeId.provider }
        val searchableProviders = providers - mappedProviders
        if (searchableProviders.isEmpty()) {
            return emptyList()
        }
        return searchService.searchAndCache(
            query = series.externalAnimeMatchQuery(),
            providers = searchableProviders,
            limitPerProvider = limitPerProvider,
        )
            .groupBy { candidate -> candidate.anime.id.provider }
            .mapNotNull { (provider, candidates) ->
                val sortedCandidates = candidates.sortedWith(
                    compareByDescending<ExternalAnimeMatchCandidate> { candidate -> candidate.confidence }
                        .thenBy { candidate -> candidate.anime.titles.primary.lowercase() }
                        .thenBy { candidate -> candidate.anime.id.value },
                )
                val top = sortedCandidates.firstOrNull() ?: return@mapNotNull null
                val runnerUpConfidence = sortedCandidates.drop(1).maxOfOrNull { it.confidence } ?: 0.0
                val margin = top.confidence - runnerUpConfidence
                val disposition = top.suggestionDisposition(margin) ?: return@mapNotNull null
                ExternalAnimeMappingSuggestion(
                    series = series,
                    provider = provider,
                    candidate = top,
                    margin = margin,
                    disposition = disposition,
                )
            }
            .sortedWith(
                compareBy<ExternalAnimeMappingSuggestion> { suggestion -> suggestion.provider.name }
                    .thenByDescending { suggestion -> suggestion.candidate.confidence },
            )
    }
}

internal data class ExternalAnimeMappingSuggestion(
    val series: LibrarySeries,
    val provider: ExternalAnimeProvider,
    val candidate: ExternalAnimeMatchCandidate,
    val margin: Double,
    val disposition: ExternalAnimeMappingSuggestionDisposition,
)

internal enum class ExternalAnimeMappingSuggestionDisposition {
    AUTO_LINK,
    REVIEW,
}

internal fun LibrarySeries.externalAnimeMatchQuery(searchTitle: String = title): ExternalAnimeMatchQuery {
    val signals = externalAnimeSearchSignals()
    return ExternalAnimeMatchQuery(
        title = searchTitle.trim().ifBlank { title },
        episodeCount = episodeCount.takeIf { it > 0 },
        startYear = signals.startYear,
        alternateTitles = signals.alternateTitles
            .filterNot { alternateTitle -> alternateTitle.equals(searchTitle, ignoreCase = true) },
        externalLinks = signals.externalLinks,
    )
}

private data class ExternalAnimeSearchSignals(
    val alternateTitles: List<String>,
    val externalLinks: List<ExternalAnimeExternalLink>,
    val startYear: Int?,
)

private fun LibrarySeries.externalAnimeSearchSignals(): ExternalAnimeSearchSignals {
    val metadata = seasons
        .flatMap { season -> season.items }
        .mapNotNull { item -> item.animeMetadata }
    return ExternalAnimeSearchSignals(
        alternateTitles = metadata
            .flatMap(LibraryAnimeMetadata::searchableTitles)
            .distinctBy(String::normalizedDesktopAnimeTitle)
            .take(MAX_METADATA_SEARCH_TITLES),
        externalLinks = metadata
            .flatMap(LibraryAnimeMetadata::externalLinks)
            .filter { link ->
                link.animeId.provider == ExternalAnimeProvider.MY_ANIME_LIST ||
                    link.animeId.provider == ExternalAnimeProvider.BANGUMI
            }
            .distinctBy(ExternalAnimeExternalLink::animeId)
            .take(MAX_METADATA_EXTERNAL_LINKS),
        startYear = metadata
            .mapNotNull(LibraryAnimeMetadata::startYear)
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
            ?.key,
    )
}

private fun ExternalAnimeMatchCandidate.suggestionDisposition(
    margin: Double,
): ExternalAnimeMappingSuggestionDisposition? {
    if (confidence < REVIEW_CONFIDENCE_THRESHOLD) {
        return null
    }
    val trustedExternalLink = evidence.any { item -> item.startsWith(TRUSTED_EXTERNAL_LINK_EVIDENCE_PREFIX) }
    val hasContradictingEvidence = evidence.any { item -> item.endsWith("differs") }
    val safeHighConfidence = confidence >= AUTO_LINK_CONFIDENCE_THRESHOLD &&
        margin >= AUTO_LINK_MARGIN_THRESHOLD
    val safeTrustedLink = confidence >= AUTO_LINK_CONFIDENCE_THRESHOLD &&
        trustedExternalLink &&
        !hasContradictingEvidence
    return if (safeHighConfidence || safeTrustedLink) {
        ExternalAnimeMappingSuggestionDisposition.AUTO_LINK
    } else {
        ExternalAnimeMappingSuggestionDisposition.REVIEW
    }
}

private fun LibraryAnimeMetadata.searchableTitles(): List<String> =
    listOf(displayTitle, primaryTitle)
        .plus(listOfNotNull(chineseTitle, englishTitle, japaneseTitle))
        .plus(alternateNames)
        .map(String::trim)
        .filter(String::isNotBlank)

private fun String.normalizedDesktopAnimeTitle(): String =
    lowercase().filter { it.isLetterOrDigit() }

private const val DEFAULT_SUGGESTION_SEARCH_LIMIT = 8
private const val MAX_METADATA_SEARCH_TITLES = 12
private const val MAX_METADATA_EXTERNAL_LINKS = 8
private const val AUTO_LINK_CONFIDENCE_THRESHOLD = 0.93
private const val AUTO_LINK_MARGIN_THRESHOLD = 0.12
private const val REVIEW_CONFIDENCE_THRESHOLD = 0.60
private const val TRUSTED_EXTERNAL_LINK_EVIDENCE_PREFIX = "trusted external link matches"
