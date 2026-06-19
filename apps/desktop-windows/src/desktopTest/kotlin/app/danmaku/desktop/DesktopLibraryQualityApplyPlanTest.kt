package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.LibraryAnimeMetadata
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryQualityIssueType
import app.danmaku.domain.libraryQualityReport
import app.danmaku.domain.stableKey
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopLibraryQualityApplyPlanTest {
    @Test
    fun plansItemAndSeriesMappingsForSplitCandidate() {
        val catalog = catalogOf(
            item(
                id = "despair-01",
                seriesTitle = "Danganronpa 3",
                episodeTitle = "Danganronpa 3 Despair Side - 01",
                animeMetadata = animeMetadata(3001, "Danganronpa 3: Despair Arc"),
            ),
            item(
                id = "future-01",
                seriesTitle = "Danganronpa 3",
                episodeTitle = "Danganronpa 3 Future Side - 01",
                animeMetadata = animeMetadata(3002, "Danganronpa 3: Future Arc"),
            ),
        )
        val issue = catalog.libraryQualityReport().issues
            .single { issue -> issue.type == LibraryQualityIssueType.SPLIT_SERIES_CANDIDATE }

        val plan = issue.libraryQualityMappingApplyPlan(catalog, mappedAtEpochMs = 999)!!

        assertEquals(
            listOf("despair-01" to 3001L, "future-01" to 3002L),
            plan.itemMappings.map { mapping -> mapping.localMediaId to mapping.animeId.value },
        )
        assertEquals(
            listOf("anime-dandanplay-3001" to 3001L, "anime-dandanplay-3002" to 3002L),
            plan.seriesMappings.map { mapping -> mapping.localSeriesId to mapping.animeId.value },
        )
        assertEquals(ExternalAnimeMappingSource.MANUAL, plan.itemMappings.single { it.localMediaId == "despair-01" }.source)
        assertEquals(1.0, plan.itemMappings.single { it.localMediaId == "despair-01" }.confidence)
        assertEquals(999, plan.itemMappings.single { it.localMediaId == "despair-01" }.mappedAtEpochMs)
    }

    @Test
    fun plansLocalTitleAndItemMappingsForMergeCandidate() {
        val catalog = catalogOf(
            item(
                id = "eighty-six-comicat-04",
                seriesTitle = "[Comicat&KissSub][86 - Eighty Six]",
                episodeTitle = "[Comicat&KissSub][86 - Eighty Six][04]",
                animeMetadata = animeMetadata(41457, "86 EIGHTY-SIX"),
            ),
            item(
                id = "eighty-six-lilith-01",
                seriesTitle = "86 - Eighty Six",
                episodeTitle = "86 - Eighty Six - 01",
                animeMetadata = animeMetadata(41457, "86 EIGHTY-SIX"),
            ),
        )
        val issue = catalog.libraryQualityReport().issues
            .single { issue -> issue.type == LibraryQualityIssueType.MERGE_SERIES_CANDIDATE }

        val plan = issue.libraryQualityMappingApplyPlan(catalog, mappedAtEpochMs = 1234)!!

        assertEquals(
            listOf("eighty-six-comicat-04" to 41457L, "eighty-six-lilith-01" to 41457L),
            plan.itemMappings.map { mapping -> mapping.localMediaId to mapping.animeId.value },
        )
        assertEquals(
            listOf(
                "86-eighty-six",
                "anime-dandanplay-41457",
                "comicat-kisssub-86-eighty-six",
            ),
            plan.seriesMappings.map { mapping -> mapping.localSeriesId },
        )
        assertEquals(listOf(41457L, 41457L, 41457L), plan.seriesMappings.map { mapping -> mapping.animeId.value })
        assertEquals(ExternalAnimeMappingSource.MANUAL, plan.seriesMappings.first().source)
        assertEquals(1234, plan.seriesMappings.first().mappedAtEpochMs)
    }

    @Test
    fun appliesPlanToCatalogStoreAndResolvesIssue() {
        val catalog = catalogOf(
            item(
                id = "despair-01",
                seriesTitle = "Danganronpa 3",
                episodeTitle = "Danganronpa 3 Despair Side - 01",
                animeMetadata = animeMetadata(3001, "Danganronpa 3: Despair Arc"),
            ),
            item(
                id = "future-01",
                seriesTitle = "Danganronpa 3",
                episodeTitle = "Danganronpa 3 Future Side - 01",
                animeMetadata = animeMetadata(3002, "Danganronpa 3: Future Arc"),
            ),
        )
        val issue = catalog.libraryQualityReport().issues
            .single { issue -> issue.type == LibraryQualityIssueType.SPLIT_SERIES_CANDIDATE }
        val plan = issue.libraryQualityMappingApplyPlan(catalog, mappedAtEpochMs = 777)!!
        val temp = createTempDirectory("danmaku-library-quality-apply-plan")

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val decisions = store.applyLibraryQualityMappingPlan(issue, plan, appliedAtEpochMs = 777)

            assertEquals(plan.itemMappings[0], store.loadExternalAnimeItemMappings("despair-01").single())
            assertEquals(plan.itemMappings[1], store.loadExternalAnimeItemMappings("future-01").single())
            assertEquals(
                plan.seriesMappings[0],
                store.loadExternalAnimeMappings("anime-dandanplay-3001").single(),
            )
            assertEquals(
                plan.seriesMappings[1],
                store.loadExternalAnimeMappings("anime-dandanplay-3002").single(),
            )
            assertEquals(issue.stableKey(), decisions.single().issueKey)
            assertEquals(DesktopLibraryQualityIssueDecisionState.RESOLVED, decisions.single().state)
            assertEquals(777, decisions.single().updatedAtEpochMs)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun doesNotPlanMappingsForNonMetadataQualityIssues() {
        val catalog = catalogOf(
            item(
                id = "episode-01",
                seriesTitle = "Example Show",
                episodeTitle = "Example Show - 01",
            ),
            item(
                id = "episode-01-copy",
                seriesTitle = "Example Show",
                episodeTitle = "Example Show - 01v2",
            ),
        )
        val issue = catalog.libraryQualityReport().issues
            .single { issue -> issue.type == LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER }

        assertNull(issue.libraryQualityMappingApplyPlan(catalog, mappedAtEpochMs = 1234))
    }

    private fun catalogOf(vararg items: LibraryMediaItem): LibraryCatalog =
        LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = items.toList(),
        )

    private fun item(
        id: String,
        seriesTitle: String,
        episodeTitle: String,
        animeMetadata: LibraryAnimeMetadata? = null,
    ): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = seriesTitle,
            episodeTitle = episodeTitle,
            relativePath = "$seriesTitle/$episodeTitle.mkv",
            sizeBytes = 123,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
            animeMetadata = animeMetadata,
        )

    private fun animeMetadata(
        animeId: Long,
        displayTitle: String,
    ): LibraryAnimeMetadata =
        LibraryAnimeMetadata(
            animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, animeId),
            displayTitle = displayTitle,
            primaryTitle = displayTitle,
        )
}
