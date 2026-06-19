package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeExternalLink
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTitleSet
import app.danmaku.domain.LibraryAnimeMetadata
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeason
import app.danmaku.domain.LibrarySeries
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopExternalAnimeMappingSuggesterTest {
    @Test
    fun autoLinksTrustedExternalLinkAndSkipsExistingManualMappings() {
        val temp = createTempDirectory("danmaku-external-suggester")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val client = RecordingSearchClient(
                provider = ExternalAnimeProvider.BANGUMI,
                results = listOf(
                    animeInfo(
                        id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
                        primary = "葬送のフリーレン",
                    ),
                ),
            )
            val suggester = DesktopExternalAnimeMappingSuggester(
                ExternalAnimeSearchService(
                    clients = listOf(client),
                    catalogStore = store,
                    nowEpochMs = { 123 },
                ),
            )
            val series = librarySeries(
                title = "messy local folder",
                metadata = LibraryAnimeMetadata(
                    animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 12345),
                    displayTitle = "葬送的芙莉莲",
                    primaryTitle = "葬送のフリーレン",
                    englishTitle = "Frieren: Beyond Journey's End",
                    alternateNames = listOf("Sousou no Frieren"),
                    episodeCount = 28,
                    externalLinks = listOf(
                        ExternalAnimeExternalLink(ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602)),
                    ),
                ),
            )

            val suggestions = suggester.suggestForSeries(
                series = series,
                existingMappings = emptyList(),
                providers = setOf(ExternalAnimeProvider.BANGUMI),
            )

            assertEquals(1, suggestions.size)
            assertEquals(ExternalAnimeMappingSuggestionDisposition.AUTO_LINK, suggestions.single().disposition)
            assertEquals(400602, suggestions.single().candidate.anime.id.value)
            assertTrue(suggestions.single().candidate.evidence.any { it.contains("trusted external link") })
            assertTrue(client.queries.any { it.title == "Sousou no Frieren" && it.episodeCount == 28 })

            val protectedSuggestions = suggester.suggestForSeries(
                series = series,
                existingMappings = listOf(
                    ExternalAnimeMapping(
                        localSeriesId = series.id,
                        animeId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
                        source = ExternalAnimeMappingSource.MANUAL,
                        confidence = 1.0,
                        mappedAtEpochMs = 456,
                    ),
                ),
                providers = setOf(ExternalAnimeProvider.BANGUMI),
            )

            assertEquals(emptyList(), protectedSuggestions)
        }
    }

    @Test
    fun keepsAmbiguousHighConfidenceCandidatesForReview() {
        val temp = createTempDirectory("danmaku-external-suggester-ambiguous")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val suggester = DesktopExternalAnimeMappingSuggester(
                ExternalAnimeSearchService(
                    clients = listOf(
                        RecordingSearchClient(
                            provider = ExternalAnimeProvider.MY_ANIME_LIST,
                            results = listOf(
                                animeInfo(
                                    id = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 1),
                                    primary = "Frieren",
                                    episodeCount = 28,
                                ),
                                animeInfo(
                                    id = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 2),
                                    primary = "Frieren",
                                    episodeCount = 28,
                                ),
                            ),
                        ),
                    ),
                    catalogStore = store,
                    nowEpochMs = { 123 },
                ),
            )

            val suggestions = suggester.suggestForSeries(
                series = librarySeries(title = "Frieren"),
                existingMappings = emptyList(),
                providers = setOf(ExternalAnimeProvider.MY_ANIME_LIST),
            )

            assertEquals(1, suggestions.size)
            assertEquals(ExternalAnimeMappingSuggestionDisposition.REVIEW, suggestions.single().disposition)
            assertEquals(0.0, suggestions.single().margin, 0.0001)
        }
    }
}

private class RecordingSearchClient(
    override val provider: ExternalAnimeProvider,
    private val results: List<ExternalAnimeInfo>,
) : ExternalAnimeSearchClient {
    val queries: MutableList<ExternalAnimeMatchQuery> = mutableListOf()

    override fun search(query: ExternalAnimeMatchQuery, limit: Int): List<ExternalAnimeInfo> {
        queries += query
        return results.take(limit)
    }
}

private fun animeInfo(
    id: ExternalAnimeId,
    primary: String,
    episodeCount: Int? = null,
): ExternalAnimeInfo =
    ExternalAnimeInfo(
        id = id,
        titles = ExternalAnimeTitleSet(primary = primary),
        episodeCount = episodeCount,
    )

private fun librarySeries(
    title: String,
    metadata: LibraryAnimeMetadata? = null,
): LibrarySeries =
    LibrarySeries(
        id = "series-${title.lowercase().filter(Char::isLetterOrDigit)}",
        title = title,
        seasons = listOf(
            LibrarySeason(
                id = "season-1",
                label = "Season 1",
                sortKey = 1,
                items = listOf(
                    LibraryMediaItem(
                        id = "episode-1",
                        seriesTitle = title,
                        episodeTitle = "$title 01",
                        relativePath = "$title/01.mkv",
                        sizeBytes = 1,
                        mediaType = "video/x-matroska",
                        streamPath = "/media/episode-1",
                        animeMetadata = metadata,
                    ),
                ),
            ),
        ),
    )
