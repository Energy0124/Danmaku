package app.danmaku.desktop

import app.danmaku.domain.PlaybackProgress
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val databasePath = temp.resolve("catalog.db")

        DesktopLibraryCatalogStore(databasePath).use { store ->
            val indexed = LocalMediaLibraryIndexer.index(root)
            store.replace(root, indexed)

            val loaded = store.load(root)
            assertEquals(indexed.catalog.items, loaded?.catalog?.items)
            assertEquals(1, loaded?.fileMetadataByRelativePath?.size)

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
            assertNull(store.loadProgress("missing-id"))
        }

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            assertEquals(progress, store.loadProgress(progress.mediaId))
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
}
