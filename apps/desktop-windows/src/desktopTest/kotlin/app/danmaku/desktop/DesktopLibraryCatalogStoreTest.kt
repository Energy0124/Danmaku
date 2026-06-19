package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.ExternalAnimeTitleSet
import app.danmaku.domain.LocalAnimeListEntry
import app.danmaku.domain.LocalAnimeListStatus
import app.danmaku.domain.PlaybackProgress
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.sql.DriverManager

class DesktopLibraryCatalogStoreTest {
    @Test
    fun persistsCatalogAndDropsDeletedFilesWhenLoading() {
        val temp = createTempDirectory("danmaku-library-database")
        val root = temp.resolve("Anime").createDirectories()
        val episode = root.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
        episode.writeBytes(byteArrayOf(1, 2, 3))
        val subtitle = episode.parent.resolve("Episode 01.en.srt")
        subtitle.writeText("Hello")
        val databasePath = temp.resolve("catalog.db")

        DesktopLibraryCatalogStore(databasePath).use { store ->
            val indexed = LocalMediaLibraryIndexer.index(root)
            store.replace(root, indexed)

            val loaded = store.load(root)
            assertEquals(indexed.catalog.items, loaded?.catalog?.items)
            assertEquals(1, loaded?.fileMetadataByRelativePath?.size)
            assertEquals(
                subtitle,
                loaded?.subtitleFilesById?.get(indexed.catalog.items.single().subtitles.single().id),
            )

            subtitle.deleteExisting()
            assertEquals(emptyList(), store.load(root)?.catalog?.items?.single()?.subtitles)

            episode.deleteExisting()
            assertEquals(emptyList(), store.load(root)?.catalog?.items)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun ignoresCatalogForDifferentRoot() {
        val temp = createTempDirectory("danmaku-library-database")
        val root = temp.resolve("Anime").createDirectories()
        val otherRoot = temp.resolve("Other").createDirectories()

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            store.replace(root, LocalMediaLibraryIndexer.index(root))
            assertNull(store.load(otherRoot))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun deletingLibraryRootAlsoDeletesIndexedItems() {
        val temp = createTempDirectory("danmaku-library-root-delete")
        val root = temp.resolve("Anime").createDirectories()
        root.resolve("Episode 01.mkv").writeBytes(byteArrayOf(1, 2, 3))
        val databasePath = temp.resolve("catalog.db")

        DesktopLibraryCatalogStore(databasePath).use { store ->
            val libraryRoot = DesktopLibraryRoot(
                id = "root-id",
                path = root,
                displayName = "Anime",
                provenance = DesktopLibraryRootProvenance.USER_SELECTED,
                state = DesktopLibraryRootState.AVAILABLE,
                addedAtEpochMs = 100,
            )
            store.replace(libraryRoot, LocalMediaLibraryIndexer.index(root, idNamespace = libraryRoot.id))
            assertEquals(1, store.loadRegisteredLibrary().catalog.items.size)

            store.deleteLibraryRoot(libraryRoot.id)

            assertNull(store.loadLibraryRoot(libraryRoot.id))
            assertEquals(emptyList(), store.loadRegisteredLibrary().catalog.items)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsPlaybackProgress() {
        val temp = createTempDirectory("danmaku-library-progress")
        val progress = PlaybackProgress(
            mediaId = "episode-id",
            positionMs = 12_345,
            durationMs = 98_765,
            updatedAtEpochMs = 123,
        )

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            store.saveProgress(progress)
            assertEquals(progress, store.loadProgress(progress.mediaId))
            assertEquals(listOf(progress), store.loadPlaybackProgress())
            assertNull(store.loadProgress("missing-id"))
        }

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            assertEquals(progress, store.loadProgress(progress.mediaId))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun loadsPlaybackProgressNewestFirst() {
        val temp = createTempDirectory("danmaku-library-progress-list")
        val older = PlaybackProgress(
            mediaId = "older",
            positionMs = 12_345,
            durationMs = 98_765,
            updatedAtEpochMs = 123,
        )
        val newer = PlaybackProgress(
            mediaId = "newer",
            positionMs = 23_456,
            durationMs = 98_765,
            updatedAtEpochMs = 456,
        )

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            store.saveProgress(older)
            store.saveProgress(newer)

            assertEquals(listOf(newer, older), store.loadPlaybackProgress())
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsSettings() {
        val temp = createTempDirectory("danmaku-library-settings")
        val databasePath = temp.resolve("catalog.db")
        val setting = DesktopAppSetting(
            key = "library.last_scan_mode",
            value = "incremental",
            updatedAtEpochMs = 123,
        )
        val updatedSetting = setting.copy(
            value = "full",
            updatedAtEpochMs = 456,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveSetting(setting)
            assertEquals(setting, store.loadSetting(setting.key))
            assertEquals(listOf(setting), store.loadSettings())

            store.saveSetting(updatedSetting)
            assertEquals(updatedSetting, store.loadSetting(setting.key))
        }

        DesktopLibraryCatalogStore(databasePath).use { store ->
            assertEquals(updatedSetting, store.loadSetting(setting.key))
            store.deleteSetting(setting.key)
            assertNull(store.loadSetting(setting.key))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsFavoriteMediaIdsAsSettings() {
        val temp = createTempDirectory("danmaku-library-favorites")
        val databasePath = temp.resolve("catalog.db")

        DesktopLibraryCatalogStore(databasePath).use { store ->
            assertEquals(emptySet(), store.loadFavoriteMediaIds())

            store.saveFavoriteMediaIds(setOf("episode-z", "episode-a"))
            assertEquals(setOf("episode-z", "episode-a"), store.loadFavoriteMediaIds())
        }

        DesktopLibraryCatalogStore(databasePath).use { store ->
            assertEquals(setOf("episode-a", "episode-z"), store.loadFavoriteMediaIds())
            store.saveFavoriteMediaIds(emptySet())
            assertEquals(emptySet(), store.loadFavoriteMediaIds())
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun rejectsBlankFavoriteMediaIds() {
        val temp = createTempDirectory("danmaku-library-favorites-invalid")

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            assertFailsWith<IllegalArgumentException> {
                store.saveFavoriteMediaIds(setOf("episode", " "))
            }
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsLibraryQualityIssueDecisions() {
        val temp = createTempDirectory("danmaku-library-quality-decisions")
        val databasePath = temp.resolve("catalog.db")
        val older = DesktopLibraryQualityIssueDecision(
            issueKey = "DUPLICATE_EPISODE_NUMBER|example-show|episode-a,episode-b|Example Show/01.mkv",
            state = DesktopLibraryQualityIssueDecisionState.IGNORED,
            updatedAtEpochMs = 100,
        )
        val newer = DesktopLibraryQualityIssueDecision(
            issueKey = "UNMATCHED_SERIES|mystery-show|episode-1|Mystery Show/01.mkv",
            state = DesktopLibraryQualityIssueDecisionState.RESOLVED,
            updatedAtEpochMs = 200,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveLibraryQualityIssueDecision(older)
            store.saveLibraryQualityIssueDecision(newer)

            assertEquals(older, store.loadLibraryQualityIssueDecision(older.issueKey))
            assertEquals(listOf(newer, older), store.loadLibraryQualityIssueDecisions())
        }

        DesktopLibraryCatalogStore(databasePath).use { store ->
            assertEquals(newer, store.loadLibraryQualityIssueDecision(newer.issueKey))
            store.deleteLibraryQualityIssueDecision(newer.issueKey)
            assertNull(store.loadLibraryQualityIssueDecision(newer.issueKey))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun rejectsBlankLibraryQualityIssueDecisionKeys() {
        val temp = createTempDirectory("danmaku-library-quality-decisions-invalid")

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            assertFailsWith<IllegalArgumentException> {
                store.saveLibraryQualityIssueDecision(
                    DesktopLibraryQualityIssueDecision(
                        issueKey = " ",
                        state = DesktopLibraryQualityIssueDecisionState.IGNORED,
                        updatedAtEpochMs = 100,
                    ),
                )
            }
            assertFailsWith<IllegalArgumentException> {
                store.loadLibraryQualityIssueDecision(" ")
            }
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsLibraryRootsWithProvenanceAndMissingState() {
        val temp = createTempDirectory("danmaku-library-roots")
        val databasePath = temp.resolve("catalog.db")
        val animeRoot = temp.resolve("Anime").createDirectories()
        val aniRssRoot = temp.resolve("ani-rss").createDirectories()
        val userSelectedRoot = DesktopLibraryRoot(
            id = "root-user",
            path = animeRoot,
            displayName = "Anime",
            provenance = DesktopLibraryRootProvenance.USER_SELECTED,
            state = DesktopLibraryRootState.AVAILABLE,
            addedAtEpochMs = 100,
            lastScannedAtEpochMs = 150,
        )
        val aniRssOutputRoot = DesktopLibraryRoot(
            id = "root-ani-rss",
            path = aniRssRoot,
            displayName = "ani-rss output",
            provenance = DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER,
            state = DesktopLibraryRootState.AVAILABLE,
            addedAtEpochMs = 200,
        )
        val missingAniRssRoot = aniRssOutputRoot.copy(
            state = DesktopLibraryRootState.MISSING,
            lastScannedAtEpochMs = 250,
            lastError = "Folder is no longer available",
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveLibraryRoot(userSelectedRoot)
            store.saveLibraryRoot(aniRssOutputRoot)

            assertEquals(userSelectedRoot, store.loadLibraryRoot(userSelectedRoot.id))
            assertEquals(listOf(userSelectedRoot, aniRssOutputRoot), store.loadLibraryRoots())

            store.saveLibraryRoot(missingAniRssRoot)
            store.deleteLibraryRoot(userSelectedRoot.id)
        }

        DesktopLibraryCatalogStore(databasePath).use { store ->
            assertNull(store.loadLibraryRoot(userSelectedRoot.id))
            assertEquals(missingAniRssRoot, store.loadLibraryRoot(aniRssOutputRoot.id))
            assertEquals(listOf(missingAniRssRoot), store.loadLibraryRoots())
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsAndMergesCatalogsForRegisteredLibraryRoots() {
        val temp = createTempDirectory("danmaku-library-root-catalogs")
        val firstRootPath = temp.resolve("First").createDirectories()
        val secondRootPath = temp.resolve("Second").createDirectories()
        firstRootPath.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
            .writeBytes(byteArrayOf(1, 2, 3))
        secondRootPath.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
            .writeBytes(byteArrayOf(4, 5, 6))
        val firstRoot = DesktopLibraryRoot(
            id = "first-root",
            path = firstRootPath,
            displayName = "First",
            provenance = DesktopLibraryRootProvenance.USER_SELECTED,
            state = DesktopLibraryRootState.AVAILABLE,
            addedAtEpochMs = 100,
            lastScannedAtEpochMs = 150,
        )
        val secondRoot = DesktopLibraryRoot(
            id = "second-root",
            path = secondRootPath,
            displayName = "Second",
            provenance = DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER,
            state = DesktopLibraryRootState.AVAILABLE,
            addedAtEpochMs = 200,
            lastScannedAtEpochMs = 250,
        )

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            store.replace(
                firstRoot,
                LocalMediaLibraryIndexer.index(firstRootPath, idNamespace = firstRoot.id),
            )
            store.replace(
                secondRoot,
                LocalMediaLibraryIndexer.index(secondRootPath, idNamespace = secondRoot.id),
            )

            val firstLoaded = store.load(firstRoot)
            val combined = store.loadRegisteredLibrary()

            assertEquals("First", firstLoaded?.catalog?.rootName)
            assertEquals(1, firstLoaded?.catalog?.items?.size)
            assertEquals(2, combined.catalog.items.size)
            assertEquals(2, combined.filesById.size)
            assertEquals(
                listOf(
                    "First/Example Show/Episode 01.mkv",
                    "Second/Example Show/Episode 01.mkv",
                ),
                combined.catalog.items.map { it.relativePath },
            )
            assertTrue(combined.catalog.items[0].id != combined.catalog.items[1].id)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun skipsMissingRegisteredRootFilesWhenLoadingCombinedCatalog() {
        val temp = createTempDirectory("danmaku-library-root-catalogs")
        val rootPath = temp.resolve("Anime").createDirectories()
        val episode = rootPath.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
        episode.writeBytes(byteArrayOf(1, 2, 3))
        val root = DesktopLibraryRoot(
            id = "root",
            path = rootPath,
            displayName = "Anime",
            provenance = DesktopLibraryRootProvenance.USER_SELECTED,
            state = DesktopLibraryRootState.AVAILABLE,
            addedAtEpochMs = 100,
            lastScannedAtEpochMs = 150,
        )

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            store.replace(root, LocalMediaLibraryIndexer.index(rootPath, idNamespace = root.id))
            episode.deleteExisting()

            assertEquals(emptyList(), store.load(root)?.catalog?.items)
            assertEquals(emptyList(), store.loadRegisteredLibrary().catalog.items)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsDownloadQueueItems() {
        val temp = createTempDirectory("danmaku-download-queue")
        val databasePath = temp.resolve("catalog.db")
        val outputPath = temp.resolve("Anime").resolve("Example Show - 01.mkv")
        val queued = DesktopDownloadQueueItem(
            id = "download-1",
            sourceUri = "ani-rss://feed/example-show/episode-1",
            outputPath = outputPath.toString(),
            state = "queued",
            positionBytes = 0,
            totalBytes = 1_024,
            createdAtEpochMs = 100,
            updatedAtEpochMs = 100,
        )
        val active = queued.copy(
            state = "downloading",
            positionBytes = 512,
            updatedAtEpochMs = 200,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveDownload(queued)
            assertEquals(queued, store.loadDownload(queued.id))
            assertEquals(listOf(queued), store.loadDownloads())

            store.saveDownload(active)
            assertEquals(active, store.loadDownload(queued.id))
        }

        DesktopLibraryCatalogStore(databasePath).use { store ->
            assertEquals(active, store.loadDownload(queued.id))
            store.deleteDownload(queued.id)
            assertNull(store.loadDownload(queued.id))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsDandanplayCommentCache() {
        val temp = createTempDirectory("danmaku-dandanplay-cache")
        val databasePath = temp.resolve("catalog.db")
        val cache = DesktopDandanplayCommentCache(
            mediaId = "episode-id",
            fileHash = "5d41402abc4b2a76b9719d911017c592",
            fileName = "Episode 01.mkv",
            fileSizeBytes = 123,
            episodeId = 456,
            animeId = 789,
            animeTitle = "Example Anime",
            episodeTitle = "Episode 01",
            shiftSeconds = 0.5,
            commentsJson = """{"events":[{"timestampMs":1000,"text":"hello"}]}""",
            renderedAssPath = temp.resolve("cache.ass").toString(),
            fetchedAtEpochMs = 1234,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveDandanplayCommentCache(cache)
            assertEquals(cache, store.loadDandanplayCommentCache(cache.mediaId))
            store.saveDandanplayCommentCache(
                cache.copy(
                    mediaId = "newer-episode-id",
                    fetchedAtEpochMs = 9999,
                ),
            )
            assertEquals(
                listOf(
                    cache.copy(
                        mediaId = "newer-episode-id",
                        fetchedAtEpochMs = 9999,
                    ),
                    cache,
                ),
                store.loadDandanplayCommentCaches(),
            )
            store.deleteDandanplayCommentCachesOlderThan(2000)
            assertNull(store.loadDandanplayCommentCache(cache.mediaId))
            assertEquals(
                cache.copy(
                    mediaId = "newer-episode-id",
                    fetchedAtEpochMs = 9999,
                ),
                store.loadDandanplayCommentCache("newer-episode-id"),
            )
        }

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.deleteDandanplayCommentCache("newer-episode-id")
            assertNull(store.loadDandanplayCommentCache("newer-episode-id"))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun addsProgressStorageToAnExistingCatalogDatabase() {
        val temp = createTempDirectory("danmaku-existing-library-database")
        val databasePath = temp.resolve("catalog.db")
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use {
            it.createStatement().use { statement ->
                statement.execute("PRAGMA user_version = 1")
            }
        }
        val progress = PlaybackProgress(
            mediaId = "episode-id",
            positionMs = 12_345,
            durationMs = null,
            updatedAtEpochMs = 123,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveProgress(progress)
            assertEquals(progress, store.loadProgress(progress.mediaId))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun addsSettingsAndDownloadStorageToAnExistingCatalogDatabase() {
        val temp = createTempDirectory("danmaku-existing-storage-database")
        val databasePath = temp.resolve("catalog.db")
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use {
            it.createStatement().use { statement ->
                statement.execute("PRAGMA user_version = 1")
            }
        }
        val setting = DesktopAppSetting(
            key = "server.port",
            value = "8080",
            updatedAtEpochMs = 123,
        )
        val download = DesktopDownloadQueueItem(
            id = "download-1",
            sourceUri = "ani-rss://feed/example-show/episode-1",
            outputPath = temp.resolve("Example Show - 01.mkv").toString(),
            state = "queued",
            positionBytes = 0,
            totalBytes = null,
            createdAtEpochMs = 100,
            updatedAtEpochMs = 100,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveSetting(setting)
            store.saveDownload(download)

            assertEquals(setting, store.loadSetting(setting.key))
            assertEquals(download, store.loadDownload(download.id))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun addsLibraryRootStorageToAnExistingCatalogDatabase() {
        val temp = createTempDirectory("danmaku-existing-root-database")
        val databasePath = temp.resolve("catalog.db")
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use {
            it.createStatement().use { statement ->
                statement.execute("PRAGMA user_version = 1")
            }
        }
        val root = DesktopLibraryRoot(
            id = "root-user",
            path = temp.resolve("Anime"),
            displayName = "Anime",
            provenance = DesktopLibraryRootProvenance.USER_SELECTED,
            state = DesktopLibraryRootState.MISSING,
            addedAtEpochMs = 100,
            lastError = "Folder missing at startup",
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveLibraryRoot(root)
            assertEquals(root, store.loadLibraryRoot(root.id))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun addsDandanplayCommentCacheStorageToAnExistingCatalogDatabase() {
        val temp = createTempDirectory("danmaku-existing-dandanplay-cache-database")
        val databasePath = temp.resolve("catalog.db")
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use {
            it.createStatement().use { statement ->
                statement.execute("PRAGMA user_version = 1")
            }
        }
        val cache = DesktopDandanplayCommentCache(
            mediaId = "episode-id",
            fileHash = "5d41402abc4b2a76b9719d911017c592",
            fileName = "Episode 01.mkv",
            fileSizeBytes = 123,
            episodeId = 456,
            animeId = null,
            animeTitle = null,
            episodeTitle = null,
            shiftSeconds = null,
            commentsJson = """{"events":[]}""",
            renderedAssPath = null,
            fetchedAtEpochMs = 1234,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveDandanplayCommentCache(cache)
            assertEquals(cache, store.loadDandanplayCommentCache(cache.mediaId))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsExternalAnimeMetadataAndMappings() {
        val temp = createTempDirectory("danmaku-external-anime-storage")
        val databasePath = temp.resolve("catalog.db")
        val bangumiId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602)
        val malId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991)
        val cache = DesktopExternalAnimeMetadataCache(
            anime = ExternalAnimeInfo(
                id = bangumiId,
                titles = ExternalAnimeTitleSet(
                    primary = "葬送のフリーレン",
                    chinese = "葬送的芙莉莲",
                    english = "Frieren: Beyond Journey's End",
                    japanese = "葬送のフリーレン",
                    alternateNames = listOf("Frieren"),
                ),
                episodeCount = 28,
                startYear = 2023,
                imageUrl = "https://example.test/frieren.jpg",
                summary = "Example summary",
            ),
            fetchedAtEpochMs = 1234,
        )
        val bangumiMapping = ExternalAnimeMapping(
            localSeriesId = "frieren",
            animeId = bangumiId,
            source = ExternalAnimeMappingSource.MANUAL,
            confidence = 1.0,
            mappedAtEpochMs = 2000,
        )
        val malMapping = bangumiMapping.copy(
            animeId = malId,
            source = ExternalAnimeMappingSource.AUTO,
            confidence = 0.92,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveExternalAnimeMetadataCache(cache)
            assertEquals(cache, store.loadExternalAnimeMetadataCache(bangumiId))

            store.saveExternalAnimeMapping(bangumiMapping)
            store.saveExternalAnimeMapping(malMapping)
            assertEquals(
                listOf(bangumiMapping, malMapping),
                store.loadExternalAnimeMappings("frieren"),
            )

            store.saveExternalAnimeMapping(malMapping.copy(confidence = 1.0))
            assertEquals(
                listOf(bangumiMapping, malMapping.copy(confidence = 1.0)),
                store.loadExternalAnimeMappings("frieren"),
            )

            store.deleteExternalAnimeMapping("frieren", ExternalAnimeProvider.MY_ANIME_LIST)
            assertEquals(listOf(bangumiMapping), store.loadExternalAnimeMappings("frieren"))
            store.deleteExternalAnimeMetadataCache(bangumiId)
            assertNull(store.loadExternalAnimeMetadataCache(bangumiId))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsExternalAnimeListEntriesAndSyncFailures() {
        val temp = createTempDirectory("danmaku-external-anime-readback")
        val databasePath = temp.resolve("catalog.db")
        val malId = ExternalAnimeId(ExternalAnimeProvider.MY_ANIME_LIST, 52991)
        val bangumiId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602)
        val malEntry = ExternalAnimeListEntry(
            animeId = malId,
            status = ExternalAnimeListStatus.WATCHING,
            watchedEpisodes = 4,
            score = 8,
            updatedAtEpochMs = 1_704_164_645_000,
        )
        val bangumiEntry = ExternalAnimeListEntry(
            animeId = bangumiId,
            status = ExternalAnimeListStatus.COMPLETED,
            watchedEpisodes = 28,
            score = 9,
        )
        val failure = ExternalAnimeSyncFailure(
            animeId = malId,
            message = "HTTP 429",
            failedAtEpochMs = 1_000,
            attemptCount = 2,
            retryAfterEpochMs = 61_000,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveExternalAnimeListEntries(listOf(bangumiEntry, malEntry))
            store.replaceExternalAnimeSyncFailures(listOf(failure))

            assertEquals(malEntry, store.loadExternalAnimeListEntry(malId))
            assertEquals(listOf(bangumiEntry, malEntry), store.loadExternalAnimeListEntries())
            assertEquals(listOf(failure), store.loadExternalAnimeSyncFailures())

            store.saveExternalAnimeListEntry(malEntry.copy(watchedEpisodes = 5))
            store.deleteExternalAnimeListEntry(bangumiId)
            store.replaceExternalAnimeSyncFailures(emptyList())
        }

        DesktopLibraryCatalogStore(databasePath).use { store ->
            assertEquals(
                listOf(malEntry.copy(watchedEpisodes = 5)),
                store.loadExternalAnimeListEntries(),
            )
            assertEquals(emptyList(), store.loadExternalAnimeSyncFailures())
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun persistsLocalAnimeListEntries() {
        val temp = createTempDirectory("danmaku-local-anime-list")
        val databasePath = temp.resolve("catalog.db")
        val frieren = LocalAnimeListEntry(
            localSeriesId = "frieren",
            status = LocalAnimeListStatus.WATCHING,
            score = 9,
            notes = "Watch weekly",
            updatedAtEpochMs = 1_704_164_645_000,
        )
        val apothecary = LocalAnimeListEntry(
            localSeriesId = "apothecary-diaries",
            status = LocalAnimeListStatus.PLAN_TO_WATCH,
            updatedAtEpochMs = 1_704_164_646_000,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveLocalAnimeListEntry(apothecary)
            store.saveLocalAnimeListEntry(frieren)

            assertEquals(frieren, store.loadLocalAnimeListEntry("frieren"))
            assertEquals(listOf(apothecary, frieren), store.loadLocalAnimeListEntries())

            store.saveLocalAnimeListEntry(frieren.copy(status = LocalAnimeListStatus.COMPLETED, score = 10))
            store.deleteLocalAnimeListEntry("apothecary-diaries")
        }

        DesktopLibraryCatalogStore(databasePath).use { store ->
            assertEquals(
                listOf(frieren.copy(status = LocalAnimeListStatus.COMPLETED, score = 10)),
                store.loadLocalAnimeListEntries(),
            )
            assertNull(store.loadLocalAnimeListEntry("apothecary-diaries"))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun replacingExternalAnimeItemMappingOnlyUpdatesSelectedEpisode() {
        val temp = createTempDirectory("danmaku-external-anime-item-scope")
        val databasePath = temp.resolve("catalog.db")
        val originalEpisodeMapping = DesktopExternalAnimeItemMapping(
            localMediaId = "episode-1",
            animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 19451),
            source = ExternalAnimeMappingSource.AUTO,
            confidence = 0.78,
            mappedAtEpochMs = 1000,
        )
        val siblingEpisodeMapping = DesktopExternalAnimeItemMapping(
            localMediaId = "episode-2",
            animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 19451),
            source = ExternalAnimeMappingSource.AUTO,
            confidence = 0.82,
            mappedAtEpochMs = 1000,
        )
        val correctedEpisodeMapping = originalEpisodeMapping.copy(
            animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, 18844),
            source = ExternalAnimeMappingSource.MANUAL,
            confidence = 1.0,
            mappedAtEpochMs = 2000,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveExternalAnimeItemMapping(originalEpisodeMapping)
            store.saveExternalAnimeItemMapping(siblingEpisodeMapping)
            store.saveExternalAnimeItemMapping(correctedEpisodeMapping)

            assertEquals(
                listOf(correctedEpisodeMapping),
                store.loadExternalAnimeItemMappings("episode-1"),
            )
            assertEquals(
                listOf(siblingEpisodeMapping),
                store.loadExternalAnimeItemMappings("episode-2"),
            )
            assertEquals(emptyList(), store.loadExternalAnimeMappings("example-show"))
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun addsExternalAnimeStorageToAnExistingCatalogDatabase() {
        val temp = createTempDirectory("danmaku-existing-external-anime-storage")
        val databasePath = temp.resolve("catalog.db")
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use {
            it.createStatement().use { statement ->
                statement.execute("PRAGMA user_version = 1")
            }
        }
        val mapping = ExternalAnimeMapping(
            localSeriesId = "frieren",
            animeId = ExternalAnimeId(ExternalAnimeProvider.BANGUMI, 400602),
            source = ExternalAnimeMappingSource.AUTO,
            confidence = 0.9,
            mappedAtEpochMs = 123,
        )

        DesktopLibraryCatalogStore(databasePath).use { store ->
            store.saveExternalAnimeMapping(mapping)
            assertEquals(listOf(mapping), store.loadExternalAnimeMappings("frieren"))
            val entry = ExternalAnimeListEntry(
                animeId = mapping.animeId,
                status = ExternalAnimeListStatus.WATCHING,
                watchedEpisodes = 3,
            )
            val failure = ExternalAnimeSyncFailure(
                animeId = mapping.animeId,
                message = "HTTP 500",
                failedAtEpochMs = 1_000,
                attemptCount = 1,
                retryAfterEpochMs = 31_000,
            )
            store.saveExternalAnimeListEntry(entry)
            store.replaceExternalAnimeSyncFailures(listOf(failure))
            assertEquals(listOf(entry), store.loadExternalAnimeListEntries())
            assertEquals(listOf(failure), store.loadExternalAnimeSyncFailures())
            val localEntry = LocalAnimeListEntry(
                localSeriesId = "frieren",
                status = LocalAnimeListStatus.WATCHING,
                score = 8,
                updatedAtEpochMs = 123,
            )
            store.saveLocalAnimeListEntry(localEntry)
            assertEquals(localEntry, store.loadLocalAnimeListEntry("frieren"))
            val qualityDecision = DesktopLibraryQualityIssueDecision(
                issueKey = "UNMATCHED_SERIES|frieren|frieren-01|Frieren/01.mkv",
                state = DesktopLibraryQualityIssueDecisionState.RESOLVED,
                updatedAtEpochMs = 456,
            )
            store.saveLibraryQualityIssueDecision(qualityDecision)
            assertEquals(qualityDecision, store.loadLibraryQualityIssueDecision(qualityDecision.issueKey))
        }

        temp.toFile().deleteRecursively()
    }
}
