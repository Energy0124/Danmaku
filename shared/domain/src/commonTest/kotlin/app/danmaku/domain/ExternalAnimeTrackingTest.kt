package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        assertEquals(1.0, ranked.first().confidence, 0.0001)
        assertTrue(ranked.first().evidence.any { it.contains("title") })
        assertTrue(ranked.first().evidence.any { it == "episode count matches" })
        assertTrue(ranked.first().evidence.any { it == "start year matches" })
    }

    @Test
    fun ranksCandidatesAcrossAlternateQueryTitles() {
        val query = ExternalAnimeMatchQuery(
            title = "葬送的芙莉莲",
            alternateTitles = listOf("Sousou no Frieren", "Frieren"),
            episodeCount = 28,
        )
        val candidates = listOf(
            animeInfo(
                id = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991),
                primary = "Sousou no Frieren",
                english = "Frieren: Beyond Journey's End",
                episodeCount = 28,
            ),
            animeInfo(
                id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 1),
                primary = "Different Anime",
                episodeCount = 28,
            ),
        )

        val ranked = rankExternalAnimeMatches(query, candidates)

        assertEquals(52991, ranked.first().anime.id.value)
        assertEquals("Sousou no Frieren", ranked.first().matchedTitle)
        assertEquals(0.95, ranked.first().confidence, 0.0001)
        assertTrue(ranked.first().evidence.any { it.contains("Sousou no Frieren") })
    }

    @Test
    fun validatesExternalAnimeLinks() {
        val link = ExternalAnimeExternalLink(
            animeId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
        )

        assertEquals("https://bangumi.tv/subject/400602", link.url)
        assertFailsWith<IllegalArgumentException> {
            ExternalAnimeExternalLink(
                animeId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
                url = "http://bangumi.tv/subject/400602",
            )
        }
    }

    @Test
    fun trustedExternalLinksRankCandidatesEvenWhenTitleDoesNotMatch() {
        val query = ExternalAnimeMatchQuery(
            title = "bad local folder name",
            externalLinks = listOf(
                ExternalAnimeExternalLink(
                    animeId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
                ),
            ),
        )
        val candidates = listOf(
            animeInfo(
                id = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
                primary = "葬送のフリーレン",
            ),
            animeInfo(
                id = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991),
                primary = "bad local folder name",
            ),
        )

        val ranked = rankExternalAnimeMatches(query, candidates)

        assertEquals(ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602), ranked.first().anime.id)
        assertEquals(1.0, ranked.first().confidence, 0.0001)
        assertTrue(ranked.first().evidence.any { it.contains("trusted external link") })
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
        assertEquals("2 updates ready, 0 conflicts, 3 skipped", plan.summary.label)
        assertEquals(1, plan.summary.providerUpdateCounts.getValue(ExternalAnimeProvider.BANGUMI))
        assertEquals(1, plan.summary.providerUpdateCounts.getValue(ExternalAnimeProvider.MY_ANIME_LIST))
        assertEquals(1, plan.summary.skipReasonCounts.getValue(ExternalAnimeTrackingPlanSkipReason.MISSING_LOCAL_SERIES))
        assertEquals(2, plan.summary.skipReasonCounts.getValue(ExternalAnimeTrackingPlanSkipReason.UNMAPPED_LOCAL_SERIES))
        assertEquals(
            "Frieren -> Bangumi watching, 1/2 watched",
            plan.updates.first().label,
        )
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
        assertEquals(
            "Bangumi: series is not linked (apothecary)",
            plan.skipped.first().label,
        )
    }

    @Test
    fun externalTrackingPlanConflictsWhenExternalProgressIsAhead() {
        val catalog = libraryCatalog(librarySeries())
        val mapping = externalMapping("frieren", ExternalAnimeProvider.MY_ANIME_LIST, 52991)
        val progresses = listOf(
            playbackProgress("episode-1", positionMs = 20_000, durationMs = 1_500_000),
            playbackProgress("episode-2", positionMs = 20_000, durationMs = 1_500_000),
        )

        val plan = catalog.externalAnimeTrackingPlan(
            mappings = listOf(mapping),
            progresses = progresses,
            externalEntries = listOf(
                ExternalAnimeListEntry(
                    animeId = mapping.animeId,
                    status = ExternalAnimeListStatus.COMPLETED,
                    watchedEpisodes = 2,
                    updatedAtEpochMs = 456,
                ),
            ),
        )

        assertEquals(emptyList(), plan.updates)
        assertEquals(1, plan.conflicts.size)
        val conflict = plan.conflicts.single()
        assertEquals(ExternalAnimeTrackingPlanConflictReason.EXTERNAL_PROGRESS_AHEAD, conflict.reason)
        assertEquals(0, conflict.localUpdate.watchedEpisodes)
        assertEquals(2, conflict.externalEntry.watchedEpisodes)
        assertEquals("Frieren -> MyAnimeList: external progress is ahead of local progress", conflict.label)
        assertEquals("0 updates ready, 1 conflicts, 0 skipped", plan.summary.label)
    }

    @Test
    fun convertsExternalProgressConflictIntoLocalWatchedProgressImport() {
        val catalog = libraryCatalog(librarySeries())
        val mapping = externalMapping("frieren", ExternalAnimeProvider.MY_ANIME_LIST, 52991)
        val progresses = listOf(
            playbackProgress("episode-1", positionMs = 20_000, durationMs = 1_500_000),
            playbackProgress("episode-2", positionMs = 20_000, durationMs = 1_500_000),
        )
        val plan = catalog.externalAnimeTrackingPlan(
            mappings = listOf(mapping),
            progresses = progresses,
            externalEntries = listOf(
                ExternalAnimeListEntry(
                    animeId = mapping.animeId,
                    status = ExternalAnimeListStatus.COMPLETED,
                    watchedEpisodes = 2,
                ),
            ),
        )

        val import = plan.conflicts.single().toLocalProgressImport(
            progresses = progresses,
            updatedAtEpochMs = 999,
        )

        requireNotNull(import)
        assertEquals(0, import.localWatchedEpisodes)
        assertEquals(2, import.externalWatchedEpisodes)
        assertEquals(
            listOf(
                PlaybackProgress("episode-1", 1_500_000, 1_500_000, 999),
                PlaybackProgress("episode-2", 1_500_000, 1_500_000, 999),
            ),
            import.progressUpdates,
        )
        val statuses = catalog.watchStatusByMediaId(progresses + import.progressUpdates)
        assertEquals(LibraryWatchState.WATCHED, statuses.getValue("episode-1").state)
        assertEquals(LibraryWatchState.WATCHED, statuses.getValue("episode-2").state)
    }

    @Test
    fun localProgressImportSkipsAlreadyWatchedEpisodesAndUsesSyntheticDurationWhenMissing() {
        val catalog = libraryCatalog(librarySeries())
        val mapping = externalMapping("frieren", ExternalAnimeProvider.BANGUMI, 400602)
        val progresses = listOf(
            playbackProgress("episode-1", positionMs = 1_500_000, durationMs = 1_500_000),
            PlaybackProgress("episode-2", positionMs = 20_000, durationMs = null, updatedAtEpochMs = 123),
        )
        val plan = catalog.externalAnimeTrackingPlan(
            mappings = listOf(mapping),
            progresses = progresses,
            externalEntries = listOf(
                ExternalAnimeListEntry(
                    animeId = mapping.animeId,
                    status = ExternalAnimeListStatus.COMPLETED,
                    watchedEpisodes = 2,
                ),
            ),
        )

        val import = plan.conflicts.single().toLocalProgressImport(
            progresses = progresses,
            updatedAtEpochMs = 999,
        )

        requireNotNull(import)
        assertEquals(
            listOf(PlaybackProgress("episode-2", 60_000, 60_000, 999)),
            import.progressUpdates,
        )
    }

    @Test
    fun externalTrackingPlanKeepsFailuresForSelectedProviders() {
        val catalog = libraryCatalog(librarySeries())
        val malFailure = ExternalAnimeSyncFailure(
            animeId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991),
            message = "HTTP 429",
            failedAtEpochMs = 1_000,
            attemptCount = 2,
            retryAfterEpochMs = 61_000,
        )
        val dandanplayFailure = malFailure.copy(
            animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 123),
        )

        val plan = catalog.externalAnimeTrackingPlan(
            mappings = emptyList(),
            progresses = emptyList(),
            failures = listOf(malFailure, dandanplayFailure),
        )

        assertEquals(listOf(malFailure), plan.failures)
        assertEquals(1, plan.summary.failureCount)
    }

    @Test
    fun computesExternalAnimeSyncRetryBackoff() {
        assertEquals(
            31_000,
            externalAnimeSyncRetryAfterEpochMs(
                failedAtEpochMs = 1_000,
                attemptCount = 1,
                baseDelayMs = 30_000,
                maxDelayMs = 120_000,
            ),
        )
        assertEquals(
            121_000,
            externalAnimeSyncRetryAfterEpochMs(
                failedAtEpochMs = 1_000,
                attemptCount = 4,
                baseDelayMs = 30_000,
                maxDelayMs = 120_000,
            ),
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

    @Test
    fun exposesProviderAndTrackingStatusDisplayNames() {
        assertEquals("MyAnimeList", ExternalAnimeProvider.MY_ANIME_LIST.displayName)
        assertEquals("Bangumi", ExternalAnimeProvider.BANGUMI.displayName)
        assertEquals("dandanplay", ExternalAnimeProvider.DANDANPLAY.displayName)
        assertEquals("completed", ExternalAnimeListStatus.COMPLETED.displayName)
        assertEquals("unchanged", null.displayName)
        assertEquals(
            "mapped series is no longer in the library",
            ExternalAnimeTrackingPlanSkipReason.MISSING_LOCAL_SERIES.displayName,
        )
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
