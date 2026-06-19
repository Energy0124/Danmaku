package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryQualityReportTest {
    @Test
    fun detectsFolderFileEpisodeMismatchesFromReleaseNames() {
        val catalog = catalogOf(
            item(
                id = "cyberpunk-01",
                seriesTitle = "[Erai-raws] Cyberpunk - Edgerunners - 01 ~ 10 [1080p][Multiple Subtitle]",
                episodeTitle = "[Erai-raws] Cyberpunk - Edgerunners - 01 [1080p][Multiple Subtitle][1565E812]",
            ),
            item(
                id = "kimetsu-01",
                seriesTitle = "[CoolComic404][Kimetsu no Yaiba - Mugen Ressha-Hen][01][1080P][WebRip][CHT_JP]",
                episodeTitle = "[CoolComic404][Kimetsu no Yaiba - Mugen Ressha-Hen][01][1080P][WebRip][CHT_JP]",
            ),
            item(
                id = "seven-seeds-folder-14-file-13",
                seriesTitle = "[DragsterPS] 7seeds S02E14 [1080p] [Multi-Audio] [Multi-Subs]",
                episodeTitle = "[DragsterPS] 7seeds S02E13 [1080p] [Multi-Audio] [Multi-Subs] [1BDF2DA6]",
            ),
        )

        val issues = catalog.libraryQualityReport().issues
            .filter { it.type == LibraryQualityIssueType.FOLDER_FILE_EPISODE_MISMATCH }

        assertEquals(1, issues.size)
        assertEquals(listOf("seven-seeds-folder-14-file-13"), issues.single().mediaItemIds)
        assertEquals(
            listOf("folder=S02E14", "file=S02E13", "episode differs"),
            issues.single().evidence,
        )
    }

    @Test
    fun detectsDuplicateAndMissingEpisodeNumbers() {
        val catalog = catalogOf(
            item(id = "one-a", seriesTitle = "Example Show", episodeTitle = "Example Show - 01"),
            item(id = "one-b", seriesTitle = "Example Show", episodeTitle = "Example Show - 01v2"),
            item(id = "three", seriesTitle = "Example Show", episodeTitle = "Example Show - 03"),
        )

        val issuesByType = catalog.libraryQualityReport().issues.groupBy(LibraryQualityIssue::type)

        val duplicate = issuesByType.getValue(LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER).single()
        assertEquals(listOf("one-a", "one-b"), duplicate.mediaItemIds)
        assertEquals(listOf("episode=1"), duplicate.evidence)

        val missing = issuesByType.getValue(LibraryQualityIssueType.MISSING_EPISODE_NUMBER).single()
        assertEquals(listOf("season=unknown", "missing=2"), missing.evidence)
    }

    @Test
    fun detectsMetadataCountMismatchAndUnmatchedSeries() {
        val catalog = catalogOf(
            (1..10).map { episode ->
                item(
                    id = "matched-$episode",
                    seriesTitle = "Cyberpunk",
                    episodeTitle = "Cyberpunk - ${episode.toString().padStart(2, '0')}",
                    animeMetadata = animeMetadata(
                        animeId = 42310,
                        displayTitle = "Cyberpunk: Edgerunners",
                        episodeCount = 12,
                    ),
                )
            } +
                item(
                    id = "unmatched-01",
                    seriesTitle = "Mystery Fansub",
                    episodeTitle = "Mystery Fansub - 01",
                ),
        )

        val issuesByType = catalog.libraryQualityReport().issues.groupBy(LibraryQualityIssue::type)

        val countMismatch = issuesByType
            .getValue(LibraryQualityIssueType.METADATA_EPISODE_COUNT_MISMATCH)
            .single()
        assertEquals("Cyberpunk: Edgerunners", countMismatch.seriesTitle)
        assertEquals(listOf("local=10", "metadata=12"), countMismatch.evidence)

        val unmatched = issuesByType.getValue(LibraryQualityIssueType.UNMATCHED_SERIES).single()
        assertEquals("Mystery Fansub", unmatched.seriesTitle)
        assertEquals(listOf("unmatched-01"), unmatched.mediaItemIds)
    }

    @Test
    fun detectsLocalFolderSplitAcrossMultipleMatchedAnime() {
        val catalog = catalogOf(
            item(
                id = "movie-one",
                seriesTitle = "Movie Collection",
                episodeTitle = "Movie Collection - 01",
                animeMetadata = animeMetadata(1, "First Movie"),
            ),
            item(
                id = "movie-two",
                seriesTitle = "Movie Collection",
                episodeTitle = "Movie Collection - 02",
                animeMetadata = animeMetadata(2, "Second Movie"),
            ),
        )

        val issue = catalog.libraryQualityReport().issues
            .single { it.type == LibraryQualityIssueType.SPLIT_SERIES_CANDIDATE }

        assertEquals("Movie Collection", issue.seriesTitle)
        assertEquals(listOf("movie-one", "movie-two"), issue.mediaItemIds)
        assertTrue(issue.evidence.contains("DANDANPLAY#1"))
        assertTrue(issue.evidence.contains("DANDANPLAY#2"))
    }

    private fun catalogOf(items: List<LibraryMediaItem>): LibraryCatalog =
        LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = items,
        )

    private fun catalogOf(vararg items: LibraryMediaItem): LibraryCatalog =
        catalogOf(items.toList())

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
        episodeCount: Int? = null,
    ): LibraryAnimeMetadata =
        LibraryAnimeMetadata(
            animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, animeId),
            displayTitle = displayTitle,
            primaryTitle = displayTitle,
            episodeCount = episodeCount,
        )
}
