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
}
