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
import app.danmaku.domain.groupedSeries
import java.nio.file.Files
import java.nio.file.Path
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

    @Test
    fun keepsSuggestionsFromHealthyProvidersWhenOneProviderFails() {
        val temp = createTempDirectory("danmaku-external-suggester-failure")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val suggester = DesktopExternalAnimeMappingSuggester(
                ExternalAnimeSearchService(
                    clients = listOf(
                        FailingSearchClient(ExternalAnimeProvider.MY_ANIME_LIST),
                        RecordingSearchClient(
                            provider = ExternalAnimeProvider.BANGUMI,
                            results = listOf(
                                animeInfo(
                                    id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 10),
                                    primary = "Short Anime",
                                    episodeCount = 12,
                                ),
                            ),
                        ),
                    ),
                    catalogStore = store,
                    nowEpochMs = { 123 },
                ),
            )
            val failures = mutableListOf<ExternalAnimeProvider>()

            val suggestions = suggester.suggestForSeries(
                series = librarySeries(title = "Short Anime"),
                existingMappings = emptyList(),
                providers = setOf(ExternalAnimeProvider.MY_ANIME_LIST, ExternalAnimeProvider.BANGUMI),
                onProviderFailure = { provider, _ -> failures += provider },
            )

            assertEquals(listOf(ExternalAnimeProvider.MY_ANIME_LIST), failures)
            assertEquals(1, suggestions.size)
            assertEquals(ExternalAnimeProvider.BANGUMI, suggestions.single().provider)
        }
    }

    @Test
    fun skipsTooLongBulkSuggestionSearchTitles() {
        val temp = createTempDirectory("danmaku-external-suggester-long-title")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val client = RecordingSearchClient(
                provider = ExternalAnimeProvider.MY_ANIME_LIST,
                results = listOf(
                    animeInfo(
                        id = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 20),
                        primary = "Short Anime",
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
            val longTitle = "A".repeat(80)

            suggester.suggestForSeries(
                series = librarySeries(
                    title = longTitle,
                    metadata = LibraryAnimeMetadata(
                        animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 12345),
                        displayTitle = "Short Anime",
                        primaryTitle = "Short Anime",
                    ),
                ),
                existingMappings = emptyList(),
                providers = setOf(ExternalAnimeProvider.MY_ANIME_LIST),
            )

            assertTrue(client.queries.isNotEmpty())
            assertTrue(client.queries.none { query -> query.title == longTitle })
            assertEquals("Short Anime", client.queries.first().title)
        }
    }

    @Test
    fun mapsIndexedReleaseFilenameExamplesToExternalSuggestions() {
        val temp = createTempDirectory("danmaku-external-suggester-file-examples")
        val libraryRoot = temp.resolve("anime")
        createSampleVideo(
            libraryRoot,
            "[Erai-raws] Cyberpunk - Edgerunners - 01 ~ 10 [1080p][Multiple Subtitle]",
            "[Erai-raws] Cyberpunk - Edgerunners - 01 [1080p][Multiple Subtitle].mkv",
        )
        createSampleVideo(
            libraryRoot,
            "[CoolComic404][Kimetsu no Yaiba - Mugen Ressha-Hen][01][1080P][WebRip][CHT_JP][HEVC-10bit AAC][MKV]",
            "[CoolComic404][Kimetsu no Yaiba - Mugen Ressha-Hen][01][1080P][WebRip][CHT_JP][HEVC-10bit AAC].mkv",
        )
        createSampleVideo(
            libraryRoot,
            "[DragsterPS] 7seeds S02E14 [1080p] [Multi-Audio] [Multi-Subs]",
            "[DragsterPS] 7seeds S02E14 [1080p] [Multi-Audio] [Multi-Subs].mkv",
        )

        val indexedSeries = LocalMediaLibraryIndexer.index(libraryRoot).catalog.groupedSeries()
        assertEquals(3, indexedSeries.size)
        val cyberpunk = indexedSeries.single { series -> series.title.contains("Cyberpunk") }
        assertEquals(
            "[Erai-raws] Cyberpunk - Edgerunners - 01 ~ 10 [1080p][Multiple Subtitle]",
            cyberpunk.title,
        )
        assertEquals(
            "[Erai-raws] Cyberpunk - Edgerunners - 01 [1080p][Multiple Subtitle]",
            cyberpunk.seasons.single().items.single().episodeTitle,
        )

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val suggester = DesktopExternalAnimeMappingSuggester(
                ExternalAnimeSearchService(
                    clients = listOf(
                        RecordingSearchClient(
                            provider = ExternalAnimeProvider.MY_ANIME_LIST,
                            results = listOf(
                                animeInfo(
                                    id = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 42310),
                                    primary = "Cyberpunk: Edgerunners",
                                    episodeCount = 10,
                                ),
                                animeInfo(
                                    id = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 28735),
                                    primary = "7SEEDS 2nd Season",
                                    episodeCount = 24,
                                ),
                            ),
                        ),
                        RecordingSearchClient(
                            provider = ExternalAnimeProvider.BANGUMI,
                            results = listOf(
                                animeInfo(
                                    id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 302766),
                                    primary = "鬼滅の刃 無限列車編",
                                    episodeCount = 7,
                                ),
                            ),
                        ),
                    ),
                    catalogStore = store,
                    nowEpochMs = { 123 },
                ),
            )

            val suggestionsByTitle = listOf(
                cyberpunk.withMetadata(
                    LibraryAnimeMetadata(
                        animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 15447),
                        displayTitle = "Cyberpunk: Edgerunners",
                        primaryTitle = "Cyberpunk: Edgerunners",
                        japaneseTitle = "サイバーパンク エッジランナーズ",
                        episodeCount = 10,
                    ),
                ),
                indexedSeries.single { series -> series.title.contains("Kimetsu") }
                    .withMetadata(
                        LibraryAnimeMetadata(
                            animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 14542),
                            displayTitle = "鬼滅の刃 無限列車編",
                            primaryTitle = "鬼滅の刃 無限列車編",
                            englishTitle = "Demon Slayer: Kimetsu no Yaiba Mugen Train Arc",
                            alternateNames = listOf("Kimetsu no Yaiba: Mugen Ressha-hen"),
                            episodeCount = 7,
                            externalLinks = listOf(
                                ExternalAnimeExternalLink(
                                    ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 302766),
                                ),
                            ),
                        ),
                    ),
                indexedSeries.single { series -> series.title.contains("7seeds", ignoreCase = true) }
                    .withMetadata(
                        LibraryAnimeMetadata(
                            animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 12295),
                            displayTitle = "7SEEDS 2nd Season",
                            primaryTitle = "7SEEDS 2nd Season",
                            alternateNames = listOf("7seeds S02"),
                            episodeCount = 12,
                        ),
                    ),
            ).associate { series ->
                series.title to suggester.suggestForSeries(
                    series = series,
                    existingMappings = emptyList(),
                    providers = setOf(ExternalAnimeProvider.MY_ANIME_LIST, ExternalAnimeProvider.BANGUMI),
                )
            }

            val cyberpunkSuggestion = suggestionsByTitle.getValue("Cyberpunk: Edgerunners").single()
            assertEquals(ExternalAnimeProvider.MY_ANIME_LIST, cyberpunkSuggestion.provider)
            assertEquals(42310, cyberpunkSuggestion.candidate.anime.id.value)
            assertEquals(ExternalAnimeMappingSuggestionDisposition.AUTO_LINK, cyberpunkSuggestion.disposition)

            val kimetsuSuggestion = suggestionsByTitle.getValue("鬼滅の刃 無限列車編").single()
            assertEquals(ExternalAnimeProvider.BANGUMI, kimetsuSuggestion.provider)
            assertEquals(302766, kimetsuSuggestion.candidate.anime.id.value)
            assertEquals(ExternalAnimeMappingSuggestionDisposition.AUTO_LINK, kimetsuSuggestion.disposition)
            assertTrue(kimetsuSuggestion.candidate.evidence.any { evidence -> evidence.contains("trusted external link") })

            val sevenSeedsSuggestion = suggestionsByTitle.getValue("7SEEDS 2nd Season").single()
            assertEquals(ExternalAnimeProvider.MY_ANIME_LIST, sevenSeedsSuggestion.provider)
            assertEquals(28735, sevenSeedsSuggestion.candidate.anime.id.value)
            assertEquals(ExternalAnimeMappingSuggestionDisposition.REVIEW, sevenSeedsSuggestion.disposition)
            assertTrue(sevenSeedsSuggestion.candidate.evidence.any { evidence -> evidence == "episode count differs" })
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

private class FailingSearchClient(
    override val provider: ExternalAnimeProvider,
) : ExternalAnimeSearchClient {
    override fun search(query: ExternalAnimeMatchQuery, limit: Int): List<ExternalAnimeInfo> {
        throw ExternalAnimeProviderException("provider failed")
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

private fun createSampleVideo(
    root: Path,
    folder: String,
    fileName: String,
) {
    val directory = root.resolve(folder)
    Files.createDirectories(directory)
    Files.write(directory.resolve(fileName), byteArrayOf(1))
}

private fun LibrarySeries.withMetadata(metadata: LibraryAnimeMetadata): LibrarySeries =
    copy(
        title = metadata.displayTitle,
        seasons = seasons.map { season ->
            season.copy(
                items = season.items.map { item -> item.copy(animeMetadata = metadata) },
            )
        },
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
