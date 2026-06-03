package app.danmaku.desktop

import app.danmaku.domain.PlaybackProgress
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
}
