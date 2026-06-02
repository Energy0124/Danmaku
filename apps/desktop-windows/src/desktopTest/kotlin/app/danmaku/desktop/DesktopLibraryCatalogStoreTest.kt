package app.danmaku.desktop

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
