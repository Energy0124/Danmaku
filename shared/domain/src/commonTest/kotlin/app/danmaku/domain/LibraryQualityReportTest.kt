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
    fun classifiesReleaseLanguageAndQualityAlternatesAsEpisodeVariants() {
        val catalog = catalogOf(
            item(
                id = "eighty-six-comicat-11",
                seriesTitle = "86 - Eighty Six",
                episodeTitle = "[Comicat&KissSub][86 - Eighty Six][11][1080P][BIG5][MP4]",
                relativePath = "[Comicat&KissSub][86 - Eighty Six][11][1080P][BIG5][MP4].mp4",
            ),
            item(
                id = "eighty-six-lilith-11",
                seriesTitle = "86 - Eighty Six",
                episodeTitle = "[Lilith-Raws] 86 - Eighty Six - 11 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]",
                relativePath = "[Lilith-Raws] 86 - Eighty Six - 11 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4].mp4",
            ),
            item(
                id = "appare-jpsc-01",
                seriesTitle = "Appare-Ranman!",
                episodeTitle = "[Nekomoe kissaten][Appare-Ranman!][01][1080p][JPSC]",
                relativePath = "[Nekomoe kissaten][Appare-Ranman!][01][1080p][JPSC].mp4",
            ),
            item(
                id = "appare-jptc-01",
                seriesTitle = "Appare-Ranman!",
                episodeTitle = "[Nekomoe kissaten][Appare-Ranman!][01][1080p][JPTC]",
                relativePath = "[Nekomoe kissaten][Appare-Ranman!][01][1080p][JPTC].mp4",
            ),
            item(
                id = "higurashi-720-13",
                seriesTitle = "Higurashi_Gou",
                episodeTitle = "[KTXP][Higurashi_Gou][13][GB_CN][HEVC_opus][720p]",
                relativePath = "[KTXP][Higurashi_Gou][13][GB_CN][HEVC_opus][720p].mkv",
            ),
            item(
                id = "higurashi-1080-13",
                seriesTitle = "Higurashi_Gou",
                episodeTitle = "[KTXP][Higurashi_Gou][13][GB_CN][HEVC_opus][1080p]",
                relativePath = "[KTXP][Higurashi_Gou][13][GB_CN][HEVC_opus][1080p].mkv",
            ),
        )

        val issues = catalog.libraryQualityReport().issues
        val variants = issues.filter { issue -> issue.type == LibraryQualityIssueType.EPISODE_VARIANT_GROUP }

        assertEquals(3, variants.size)
        assertTrue(issues.none { issue -> issue.type == LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER })
        assertTrue(
            variants.single { issue -> issue.seriesTitle == "86 - Eighty Six" }
                .evidence.any { evidence -> evidence.startsWith("releaseGroups=") },
        )
        assertTrue(
            variants.single { issue -> issue.seriesTitle == "Appare-Ranman!" }
                .evidence.any { evidence -> evidence == "subtitleTags=jpsc; jptc" },
        )
        assertTrue(
            variants.single { issue -> issue.seriesTitle == "Higurashi_Gou" }
                .evidence.any { evidence -> evidence == "resolutions=1080p; 720p" },
        )
    }

    @Test
    fun keepsUnmatchedSameSignatureSideArcsAsDuplicateEpisodeReview() {
        val catalog = catalogOf(
            item(
                id = "danganronpa-despair-01",
                seriesTitle = "[SumiSora&CASO][DanganRonpa3][BIG5][720p]",
                episodeTitle = "[SumiSora&CASO][DanganRonpa3][Despair_Side][01][BIG5][720p]",
            ),
            item(
                id = "danganronpa-future-01",
                seriesTitle = "[SumiSora&CASO][DanganRonpa3][BIG5][720p]",
                episodeTitle = "[SumiSora&CASO][DanganRonpa3][Future_Side][01][BIG5][720p]",
            ),
        )

        val issues = catalog.libraryQualityReport().issues

        assertEquals(
            listOf(LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER),
            issues.map(LibraryQualityIssue::type),
        )
    }

    @Test
    fun suggestsSplittingSameLocalTitleWhenMetadataPointsToDifferentAnime() {
        val catalog = catalogOf(
            item(
                id = "danganronpa-despair-01",
                seriesTitle = "[SumiSora&CASO][DanganRonpa3][BIG5][720p]",
                episodeTitle = "[SumiSora&CASO][DanganRonpa3][Despair_Side][01][BIG5][720p]",
                animeMetadata = animeMetadata(3001, "Danganronpa 3: Despair Arc"),
            ),
            item(
                id = "danganronpa-future-01",
                seriesTitle = "[SumiSora&CASO][DanganRonpa3][BIG5][720p]",
                episodeTitle = "[SumiSora&CASO][DanganRonpa3][Future_Side][01][BIG5][720p]",
                animeMetadata = animeMetadata(3002, "Danganronpa 3: Future Arc"),
            ),
        )

        val issues = catalog.libraryQualityReport().issues

        val split = issues.single { issue -> issue.type == LibraryQualityIssueType.SPLIT_SERIES_CANDIDATE }
        assertEquals("[SumiSora&CASO][DanganRonpa3][BIG5][720p]", split.seriesTitle)
        assertTrue(split.evidence.contains("DANDANPLAY#3001=Danganronpa 3: Despair Arc"))
        assertTrue(split.evidence.contains("DANDANPLAY#3002=Danganronpa 3: Future Arc"))
        assertTrue(issues.none { issue -> issue.type == LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER })
    }

    @Test
    fun suggestsMergingDifferentLocalTitlesWhenMetadataPointsToSameAnime() {
        val catalog = catalogOf(
            item(
                id = "eighty-six-comicat-04",
                seriesTitle = "[Comicat&KissSub][86 - Eighty Six]",
                episodeTitle = "[Comicat&KissSub][86 - Eighty Six][04][1080P][BIG5][MP4]",
                animeMetadata = animeMetadata(41457, "86 EIGHTY-SIX"),
            ),
            item(
                id = "eighty-six-lilith-01",
                seriesTitle = "86 - Eighty Six",
                episodeTitle = "[Lilith-Raws] 86 - Eighty Six - 01 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]",
                animeMetadata = animeMetadata(41457, "86 EIGHTY-SIX"),
            ),
        )

        val issue = catalog.libraryQualityReport().issues
            .single { issue -> issue.type == LibraryQualityIssueType.MERGE_SERIES_CANDIDATE }

        assertEquals("86 EIGHTY-SIX", issue.seriesTitle)
        assertEquals(listOf("eighty-six-comicat-04", "eighty-six-lilith-01"), issue.mediaItemIds)
        assertEquals(
            listOf(
                "anime=DANDANPLAY#41457",
                "localTitles=86 - Eighty Six; [Comicat&KissSub][86 - Eighty Six]",
            ),
            issue.evidence,
        )
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
    fun suppressesUnmatchedSeriesWhenNoMetadataHasBeenImportedYet() {
        val catalog = catalogOf(
            item(id = "mystery-01", seriesTitle = "Mystery Fansub", episodeTitle = "Mystery Fansub - 01"),
            item(id = "other-01", seriesTitle = "Other Fansub", episodeTitle = "Other Fansub - 01"),
        )

        val issues = catalog.libraryQualityReport().issues

        assertTrue(issues.none { it.type == LibraryQualityIssueType.UNMATCHED_SERIES })
    }

    @Test
    fun handlesVersionedEpisodesSpecialsAndFileExtensionsFromAnimeReleases() {
        val catalog = catalogOf(
            item(
                id = "bisque-01v2",
                seriesTitle = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[01~12][BIG5_MP4][1920X1080]",
                episodeTitle = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[01v2][BIG5_MP4][1920X1080]",
                relativePath = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[01~12][BIG5_MP4][1920X1080]/[HYSUB]Sono Bisque Doll wa Koi wo Suru[01v2][BIG5_MP4][1920X1080].mp4",
            ),
            item(
                id = "bisque-02",
                seriesTitle = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[01~12][BIG5_MP4][1920X1080]",
                episodeTitle = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[02][BIG5_MP4][1920X1080]",
                relativePath = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[01~12][BIG5_MP4][1920X1080]/[HYSUB]Sono Bisque Doll wa Koi wo Suru[02][BIG5_MP4][1920X1080].mp4",
            ),
            item(
                id = "bisque-03",
                seriesTitle = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[01~12][BIG5_MP4][1920X1080]",
                episodeTitle = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[03][BIG5_MP4][1920X1080]",
                relativePath = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[01~12][BIG5_MP4][1920X1080]/[HYSUB]Sono Bisque Doll wa Koi wo Suru[03][BIG5_MP4][1920X1080].mp4",
            ),
            item(
                id = "bisque-04",
                seriesTitle = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[01~12][BIG5_MP4][1920X1080]",
                episodeTitle = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[04][BIG5_MP4][1920X1080]",
                relativePath = "[HYSUB]Sono Bisque Doll wa Koi wo Suru[01~12][BIG5_MP4][1920X1080]/[HYSUB]Sono Bisque Doll wa Koi wo Suru[04][BIG5_MP4][1920X1080].mp4",
            ),
            item(
                id = "konosuba-04",
                seriesTitle = "[JYFanSub][Kono_Subarashii_Sekai_ni_Shukufuku_o_2][01-10+OVA][GB][1080p]",
                episodeTitle = "[JYFanSub][Kono_Subarashii_Sekai_ni_Shukufuku_o_2][04][GB][1080p]",
                relativePath = "[JYFanSub][Kono_Subarashii_Sekai_ni_Shukufuku_o_2][01-10+OVA][GB][1080p]/[JYFanSub][Kono_Subarashii_Sekai_ni_Shukufuku_o_2][04][GB][1080p].mp4",
            ),
            item(
                id = "konosuba-ova",
                seriesTitle = "[JYFanSub][Kono_Subarashii_Sekai_ni_Shukufuku_o_2][01-10+OVA][GB][1080p]",
                episodeTitle = "[JYFanSub][Kono_Subarashii_Sekai_ni_Shukufuku_o_2][OVA][GB][1080p]",
                relativePath = "[JYFanSub][Kono_Subarashii_Sekai_ni_Shukufuku_o_2][01-10+OVA][GB][1080p]/[JYFanSub][Kono_Subarashii_Sekai_ni_Shukufuku_o_2][OVA][GB][1080p].mp4",
            ),
        )

        val issues = catalog.libraryQualityReport().issues

        assertTrue(issues.none { it.type == LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER })
        assertTrue(issues.none { issue ->
            issue.type == LibraryQualityIssueType.UNPARSED_EPISODE_NUMBER &&
                issue.mediaItemIds == listOf("konosuba-ova")
        })
    }

    @Test
    fun ignoresSupplementalPreviewClipsInEpisodeContinuityChecks() {
        val catalog = catalogOf(
            item(
                id = "tantei-01",
                seriesTitle = "[Nekomoe kissaten][Tantei wa Mou, Shindeiru.][01-12][1080p][JPTC]",
                episodeTitle = "[Nekomoe kissaten][Tantei wa Mou, Shindeiru.][01][1080p][JPTC]",
            ),
            item(
                id = "tantei-yokoku-01",
                seriesTitle = "[Nekomoe kissaten][Tantei wa Mou, Shindeiru.][01-12][1080p][JPTC]",
                episodeTitle = "[Nekomoe kissaten][Tantei wa Mou, Shindeiru. Yokoku][01][1080p][JPTC]",
            ),
            item(
                id = "deadman-cm1",
                seriesTitle = "[YYDM-11FANS][Deadmen Wonderland][1-12+SP][720P]",
                episodeTitle = "[YYDM-11FANS][Deadmen Wonderland][CM1][BDrip][720P]",
            ),
            item(
                id = "deadman-menu",
                seriesTitle = "[YYDM-11FANS][Deadmen Wonderland][1-12+SP][720P]",
                episodeTitle = "[YYDM-11FANS][Deadmen Wonderland][Menu1.1][BDrip][720P]",
            ),
            item(
                id = "fate-remix",
                seriesTitle = "[VCB-Studio] Fate Zero [Ma10p_1080p]",
                episodeTitle = "[VCB-Studio] Fate Zero [Remix01][Ma10p_1080p][x265_flac]",
            ),
        )

        val issues = catalog.libraryQualityReport().issues

        assertTrue(issues.none { it.type == LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER })
        assertTrue(issues.none { it.type == LibraryQualityIssueType.UNPARSED_EPISODE_NUMBER })
    }

    @Test
    fun handlesHyphenRangesAndMovieSequenceTitles() {
        val catalog = catalogOf(
            item(
                id = "nihon-02",
                seriesTitle = "[Erai-raws] Nihon Chinbotsu 2020 - 01 ~ 10 [1080p][Multiple Subtitle]",
                episodeTitle = "[Erai-raws] Nihon Chinbotsu 2020 - 02 [1080p][Multiple Subtitle]",
            ),
            item(
                id = "absolute-09",
                seriesTitle = "[JYFanSub][Absolute Demonic Front Babylonia][09-10][BIG5][1080P][HEVC][V2]",
                episodeTitle = "[JYFanSub][Absolute Demonic Front Babylonia][09][BIG5][1080P][HEVC][V2]",
            ),
            item(
                id = "absolute-10",
                seriesTitle = "[JYFanSub][Absolute Demonic Front Babylonia][09-10][BIG5][1080P][HEVC][V2]",
                episodeTitle = "[JYFanSub][Absolute Demonic Front Babylonia][10][BIG5][1080P][HEVC][V2]",
            ),
            item(
                id = "kyoukai-01",
                seriesTitle = "[Kamigami] Kara no Kyoukai Movie 01-08 [BD x264 1920x1080 DTS-HD(5.1ch,2.0ch) Sub(Chs,Jap)]",
                episodeTitle = "[Kamigami] Kara no Kyoukai 1 - Fukan Fuukei [BD 1920x1080 DTS-HD(5.1ch,2.0ch)]",
            ),
            item(
                id = "kyoukai-02",
                seriesTitle = "[Kamigami] Kara no Kyoukai Movie 01-08 [BD x264 1920x1080 DTS-HD(5.1ch,2.0ch) Sub(Chs,Jap)]",
                episodeTitle = "[Kamigami] Kara no Kyoukai 2 - Satsujin Kousatsu Zen [BD 1920x1080 DTS-HD(5.1ch,2.0ch)]",
            ),
            item(
                id = "haikyuu-s2-02",
                seriesTitle = "[Kamigami] Haikyuu!! S2 [BD 1080p x265 Ma10p AAC]",
                episodeTitle = "[Kamigami] Haikyuu!! S2 - 02 [BD 1080p x265 Ma10p AAC]",
            ),
            item(
                id = "haikyuu-s2-03",
                seriesTitle = "[Kamigami] Haikyuu!! S2 [BD 1080p x265 Ma10p AAC]",
                episodeTitle = "[Kamigami] Haikyuu!! S2 - 03 [BD 1080p x265 Ma10p AAC]",
            ),
            item(
                id = "gurazeni-24",
                seriesTitle = "[UHA-WINGS][Gurazeni][01-24 END][x264 1080p][CHT]",
                episodeTitle = "[UHA-WINGS][Gurazeni][24 END][x264 1080p][CHT]",
            ),
        )

        val issues = catalog.libraryQualityReport().issues

        assertTrue(issues.none { it.type == LibraryQualityIssueType.FOLDER_FILE_EPISODE_MISMATCH })
        assertTrue(issues.none { it.type == LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER })
        assertTrue(issues.none { it.type == LibraryQualityIssueType.UNPARSED_EPISODE_NUMBER })
    }

    @Test
    fun doesNotTreatRootLevelInferredSeriesTitleAsFolderEpisodeHint() {
        val catalog = catalogOf(
            item(
                id = "eighty-six-01",
                seriesTitle = "86 - Eighty Six",
                episodeTitle = "[Lilith-Raws] 86 - Eighty Six - 01 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]",
                relativePath = "[Lilith-Raws] 86 - Eighty Six - 01 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4].mp4",
            ),
            item(
                id = "eighty-six-02",
                seriesTitle = "86 - Eighty Six",
                episodeTitle = "[Lilith-Raws] 86 - Eighty Six - 02 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4]",
                relativePath = "[Lilith-Raws] 86 - Eighty Six - 02 [Baha][WEB-DL][1080p][AVC AAC][CHT][MP4].mp4",
            ),
        )

        val issues = catalog.libraryQualityReport().issues

        assertTrue(issues.none { it.type == LibraryQualityIssueType.FOLDER_FILE_EPISODE_MISMATCH })
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
        assertTrue(issue.evidence.contains("DANDANPLAY#1=First Movie"))
        assertTrue(issue.evidence.contains("DANDANPLAY#2=Second Movie"))
    }

    @Test
    fun issueStableKeyIgnoresItemAndPathOrdering() {
        val first = LibraryQualityIssue(
            type = LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER,
            severity = LibraryQualityIssueSeverity.REVIEW,
            seriesId = "example|show",
            seriesTitle = "Example Show",
            mediaItemIds = listOf("episode,b", "episode\\a"),
            relativePaths = listOf("Example Show/02.mkv", "Example Show/01,alt.mkv"),
            message = "Multiple files appear to represent the same episode.",
        )
        val second = first.copy(
            mediaItemIds = listOf("episode\\a", "episode,b"),
            relativePaths = listOf("Example Show/01,alt.mkv", "Example Show/02.mkv"),
            message = "Scanner wording changed.",
            evidence = listOf("episode=1"),
        )

        assertEquals(first.stableKey(), second.stableKey())
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
        relativePath: String = "$seriesTitle/$episodeTitle.mkv",
        animeMetadata: LibraryAnimeMetadata? = null,
    ): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = seriesTitle,
            episodeTitle = episodeTitle,
            relativePath = relativePath,
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
