package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExternalAnimeTrackingTest {
    @Test
    fun buildsProviderLinks() {
        assertEquals(
            "https://myanimelist.net/anime/1",
            ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 1).webUrl,
        )
        assertEquals(
            "https://bangumi.tv/subject/2",
            ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 2).webUrl,
        )
    }

    @Test
    fun exposesMultilingualAndAlternateNamesWithoutDuplicates() {
        val titles = ExternalAnimeTitleSet(
            primary = "葬送のフリーレン",
            chinese = "葬送的芙莉莲",
            english = "Frieren: Beyond Journey's End",
            japanese = "葬送のフリーレン",
            alternateNames = listOf("Frieren", "葬送のフリーレン"),
        )

        assertEquals(
            listOf("葬送のフリーレン", "葬送的芙莉莲", "Frieren: Beyond Journey's End", "Frieren"),
            titles.displayNames,
        )
    }

    @Test
    fun ranksCandidatesByAlternateTitleEpisodeCountAndYear() {
        val query = ExternalAnimeMatchQuery(
            title = "Frieren Beyond Journeys End",
            episodeCount = 28,
            startYear = 2023,
        )
        val candidates = listOf(
            animeInfo(
                id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 999),
                primary = "Wrong Show",
                episodeCount = 28,
                startYear = 2023,
            ),
            animeInfo(
                id = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991),
                primary = "Sousou no Frieren",
                english = "Frieren: Beyond Journey's End",
                alternateNames = listOf("Frieren Beyond Journey's End"),
                episodeCount = 28,
                startYear = 2023,
            ),
            animeInfo(
                id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
                primary = "葬送のフリーレン",
                chinese = "葬送的芙莉莲",
                english = "Frieren",
                episodeCount = 28,
                startYear = 2023,
            ),
        )

        val ranked = rankExternalAnimeMatches(query, candidates)

        assertEquals(3, ranked.size)
        assertEquals(52991, ranked.first().anime.id.value)
        assertEquals("Frieren: Beyond Journey's End", ranked.first().matchedTitle)
        assertEquals(1.0, ranked.first().confidence)
    }

    @Test
    fun validatesOptionalTrackingAndRatingUpdates() {
        val animeId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 1)

        ExternalAnimeTrackingUpdate(
            animeId = animeId,
            status = ExternalAnimeListStatus.WATCHING,
            watchedEpisodes = 3,
            score = 8,
        )

        ExternalAnimeTrackingUpdate(
            animeId = animeId,
            status = ExternalAnimeListStatus.WATCHING,
            trackingEnabled = false,
            ratingEnabled = false,
        )

        assertFailsWith<IllegalArgumentException> {
            ExternalAnimeTrackingUpdate(animeId = animeId, watchedEpisodes = 1, trackingEnabled = false)
        }
        assertFailsWith<IllegalArgumentException> {
            ExternalAnimeTrackingUpdate(animeId = animeId, score = 11)
        }
    }

    @Test
    fun validatesManualAndAutomaticMappings() {
        val mapping = ExternalAnimeMapping(
            localSeriesId = "frieren",
            animeId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
            source = ExternalAnimeMappingSource.MANUAL,
            confidence = 1.0,
            mappedAtEpochMs = 123,
        )

        assertEquals(ExternalAnimeMappingSource.MANUAL, mapping.source)
        assertFailsWith<IllegalArgumentException> {
            mapping.copy(confidence = 1.1)
        }
    }

    @Test
    fun derivesCompletedExternalTrackingUpdateFromWatchedSeries() {
        val series = librarySeries()
        val mapping = externalMapping(series.id)
        val watchStatusById = series.watchStatusById(
            "episode-1" to LibraryWatchState.WATCHED,
            "episode-2" to LibraryWatchState.WATCHED,
        )

        val update = series.externalAnimeTrackingUpdate(mapping, watchStatusById)

        assertEquals(mapping.animeId, update.animeId)
        assertEquals(ExternalAnimeListStatus.COMPLETED, update.status)
        assertEquals(2, update.watchedEpisodes)
    }

    @Test
    fun derivesWatchingExternalTrackingUpdateWithoutCountingInProgressEpisodeAsWatched() {
        val series = librarySeries()
        val mapping = externalMapping(series.id)
        val watchStatusById = series.watchStatusById(
            "episode-1" to LibraryWatchState.WATCHED,
            "episode-2" to LibraryWatchState.IN_PROGRESS,
        )

        val update = series.externalAnimeTrackingUpdate(mapping, watchStatusById)

        assertEquals(ExternalAnimeListStatus.WATCHING, update.status)
        assertEquals(1, update.watchedEpisodes)
    }

    @Test
    fun derivesPlanToWatchExternalTrackingUpdateForNewSeries() {
        val series = librarySeries()
        val mapping = externalMapping(series.id)
        val watchStatusById = series.watchStatusById(
            "episode-1" to LibraryWatchState.NEW,
            "episode-2" to LibraryWatchState.NEW,
        )

        val update = series.externalAnimeTrackingUpdate(mapping, watchStatusById)

        assertEquals(ExternalAnimeListStatus.PLAN_TO_WATCH, update.status)
        assertEquals(0, update.watchedEpisodes)
    }

    @Test
    fun rejectsTrackingUpdateForMismatchedMappingOrMissingWatchStatus() {
        val series = librarySeries()

        assertFailsWith<IllegalArgumentException> {
            series.externalAnimeTrackingUpdate(
                externalMapping("other-series"),
                series.watchStatusById(
                    "episode-1" to LibraryWatchState.NEW,
                    "episode-2" to LibraryWatchState.NEW,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            series.externalAnimeTrackingUpdate(externalMapping(series.id), emptyMap())
        }
    }

    @Test
    fun buildsExternalTrackingPlanForMappedSeriesAndSkippedLocalSeries() {
        val catalog = libraryCatalog(
            librarySeries(),
            librarySeries(id = "apothecary", title = "Apothecary"),
        )
        val malMapping = externalMapping("frieren", ExternalAnimeProvider.MY_ANIME_LIST, 52991)
        val bangumiMapping = externalMapping("frieren", ExternalAnimeProvider.BANGUMI, 400602)
        val staleMapping = externalMapping("deleted-series", ExternalAnimeProvider.MY_ANIME_LIST, 999)
        val progresses = listOf(
            playbackProgress("episode-1", positionMs = 1_470_000, durationMs = 1_500_000),
            playbackProgress("episode-2", positionMs = 20_000, durationMs = 1_500_000),
        )

        val plan = catalog.externalAnimeTrackingPlan(
            mappings = listOf(staleMapping, bangumiMapping, malMapping),
            progresses = progresses,
            providers = setOf(ExternalAnimeProvider.MY_ANIME_LIST, ExternalAnimeProvider.BANGUMI),
        )

        assertEquals(
            listOf(ExternalAnimeProvider.BANGUMI, ExternalAnimeProvider.MY_ANIME_LIST),
            plan.updates.map { it.mapping.animeId.provider },
        )
        assertEquals(listOf(1, 1), plan.updates.map { it.update.watchedEpisodes })
        assertEquals(
            listOf(
                ExternalAnimeTrackingPlanSkip(
                    localSeriesId = "apothecary",
                    provider = ExternalAnimeProvider.BANGUMI,
                    reason = ExternalAnimeTrackingPlanSkipReason.UNMAPPED_LOCAL_SERIES,
                ),
                ExternalAnimeTrackingPlanSkip(
                    localSeriesId = "apothecary",
                    provider = ExternalAnimeProvider.MY_ANIME_LIST,
                    reason = ExternalAnimeTrackingPlanSkipReason.UNMAPPED_LOCAL_SERIES,
                ),
                ExternalAnimeTrackingPlanSkip(
                    localSeriesId = "deleted-series",
                    provider = ExternalAnimeProvider.MY_ANIME_LIST,
                    reason = ExternalAnimeTrackingPlanSkipReason.MISSING_LOCAL_SERIES,
                ),
            ),
            plan.skipped,
        )
    }

    @Test
    fun filtersExternalTrackingPlanByProvider() {
        val catalog = libraryCatalog(librarySeries())

        val plan = catalog.externalAnimeTrackingPlan(
            mappings = listOf(
                externalMapping("frieren", ExternalAnimeProvider.MY_ANIME_LIST, 52991),
                externalMapping("frieren", ExternalAnimeProvider.BANGUMI, 400602),
            ),
            progresses = emptyList(),
            providers = setOf(ExternalAnimeProvider.BANGUMI),
        )

        assertEquals(listOf(ExternalAnimeProvider.BANGUMI), plan.updates.map { it.mapping.animeId.provider })
        assertEquals(emptyList(), plan.skipped)
    }

    @Test
    fun rejectsExternalTrackingPlanWithoutProviders() {
        assertFailsWith<IllegalArgumentException> {
            libraryCatalog(librarySeries()).externalAnimeTrackingPlan(
                mappings = emptyList(),
                progresses = emptyList(),
                providers = emptySet(),
            )
        }
    }

    private fun animeInfo(
        id: ExternalAnimeId,
        primary: String,
        chinese: String? = null,
        english: String? = null,
        japanese: String? = null,
        alternateNames: List<String> = emptyList(),
        episodeCount: Int? = null,
        startYear: Int? = null,
    ): ExternalAnimeInfo =
        ExternalAnimeInfo(
            id = id,
            titles = ExternalAnimeTitleSet(
                primary = primary,
                chinese = chinese,
                english = english,
                japanese = japanese,
                alternateNames = alternateNames,
            ),
            episodeCount = episodeCount,
            startYear = startYear,
        )

    private fun libraryCatalog(vararg series: LibrarySeries): LibraryCatalog =
        LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = series
                .flatMap { it.seasons }
                .flatMap { it.items },
        )

    private fun librarySeries(
        id: String = "frieren",
        title: String = "Frieren",
    ): LibrarySeries =
        LibrarySeries(
            id = id,
            title = title,
            seasons = listOf(
                LibrarySeason(
                    id = "$id-season-unknown",
                    label = "Season unknown",
                    sortKey = Int.MAX_VALUE,
                    items = listOf(
                        libraryMediaItem("$id-episode-1".shortEpisodeId(), title, "Episode 01"),
                        libraryMediaItem("$id-episode-2".shortEpisodeId(), title, "Episode 02"),
                    ),
                ),
            ),
        )

    private fun externalMapping(
        localSeriesId: String,
        provider: ExternalAnimeProvider = ExternalAnimeProvider.MY_ANIME_LIST,
        animeId: Long = 52991,
    ): ExternalAnimeMapping =
        ExternalAnimeMapping(
            localSeriesId = localSeriesId,
            animeId = ExternalAnimeId(provider, animeId),
            source = ExternalAnimeMappingSource.MANUAL,
            confidence = 1.0,
            mappedAtEpochMs = 123,
        )

    private fun LibrarySeries.watchStatusById(
        vararg states: Pair<String, LibraryWatchState>,
    ): Map<String, LibraryWatchStatus> {
        val stateById = states.toMap()
        return seasons
            .flatMap(LibrarySeason::items)
            .associate { item ->
                item.id to LibraryWatchStatus(
                    mediaItem = item,
                    state = stateById.getValue(item.id),
                    progress = null,
                )
            }
    }

    private fun libraryMediaItem(
        id: String,
        seriesTitle: String = "Frieren",
        episodeTitle: String,
    ): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = seriesTitle,
            episodeTitle = episodeTitle,
            relativePath = "$episodeTitle.mkv",
            sizeBytes = 1_000L,
            mediaType = "video/x-matroska",
            streamPath = "/library/items/$id/stream",
        )

    private fun playbackProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
    ): PlaybackProgress =
        PlaybackProgress(
            mediaId = mediaId,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtEpochMs = 123,
        )

    private fun String.shortEpisodeId(): String =
        removePrefix("frieren-")
}
