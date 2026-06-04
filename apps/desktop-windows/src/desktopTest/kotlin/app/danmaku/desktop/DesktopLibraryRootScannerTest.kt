package app.danmaku.desktop

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopLibraryRootScannerTest {
    @Test
    fun importsAndIncrementallyRescansAniRssOutputFolders() {
        val temp = createTempDirectory("danmaku-root-scanner")
        val aniRssRoot = temp.resolve("ani-rss").createDirectories()
        val episode = aniRssRoot.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
        episode.writeBytes(byteArrayOf(1, 2, 3))
        var now = 100L

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val registry = DesktopLibraryRootRegistry(store) { now }
            val scanner = DesktopLibraryRootScanner(store, registry)

            val first = scanner.importAniRssOutputRoot(
                path = aniRssRoot,
                displayName = "ani-rss completed",
            )
            assertEquals(DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER, first.root.provenance)
            assertEquals(1, first.indexedLibrary?.scanStats?.refreshedItemCount)
            assertEquals(1, first.publishedLibrary.catalog.items.size)

            now = 200
            val unchanged = scanner.scan(first.root)
            assertEquals(1, unchanged.indexedLibrary?.scanStats?.reusedItemCount)
            assertEquals(0, unchanged.indexedLibrary?.scanStats?.refreshedItemCount)

            episode.writeText("changed content")
            now = 300
            val changed = scanner.scan(unchanged.root)
            assertEquals(0, changed.indexedLibrary?.scanStats?.reusedItemCount)
            assertEquals(1, changed.indexedLibrary?.scanStats?.refreshedItemCount)
            assertEquals(300, changed.root.lastScannedAtEpochMs)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun marksMissingRootsAndExcludesTheirFilesFromThePublishedLibrary() {
        val temp = createTempDirectory("danmaku-root-scanner")
        val rootPath = temp.resolve("Anime").createDirectories()
        val episode = rootPath.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
        episode.writeBytes(byteArrayOf(1, 2, 3))
        var now = 100L

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val registry = DesktopLibraryRootRegistry(store) { now }
            val scanner = DesktopLibraryRootScanner(store, registry)
            val root = registry.addUserSelectedRoot(rootPath)
            val available = scanner.scan(root)
            assertEquals(1, available.publishedLibrary.catalog.items.size)

            episode.deleteExisting()
            rootPath.resolve("Example Show").deleteExisting()
            rootPath.deleteExisting()
            now = 200

            val missing = scanner.scan(available.root)
            assertEquals(DesktopLibraryRootState.MISSING, missing.root.state)
            assertEquals(DesktopLibraryRootScanner.MISSING_ROOT_ERROR, missing.root.lastError)
            assertNull(missing.indexedLibrary)
            assertEquals(emptyList(), missing.publishedLibrary.catalog.items)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun scansAllRegisteredRootsIntoOnePublishedLibrary() {
        val temp = createTempDirectory("danmaku-root-scanner")
        val firstPath = temp.resolve("First").createDirectories()
        val secondPath = temp.resolve("Second").createDirectories()
        firstPath.resolve("Show One").createDirectories()
            .resolve("Episode 01.mkv")
            .writeBytes(byteArrayOf(1))
        secondPath.resolve("Show Two").createDirectories()
            .resolve("Episode 01.mkv")
            .writeBytes(byteArrayOf(2))

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val registry = DesktopLibraryRootRegistry(store) { 100 }
            val scanner = DesktopLibraryRootScanner(store, registry)
            registry.addUserSelectedRoot(firstPath)
            registry.addAniRssOutputRoot(secondPath)

            val batch = scanner.scanAll()

            assertEquals(2, batch.results.size)
            assertEquals(2, batch.publishedLibrary.catalog.items.size)
            assertEquals(2, batch.publishedLibrary.filesById.size)
        }

        temp.toFile().deleteRecursively()
    }

    @Test
    fun scansOnlyAniRssRootsForCompletionNotifications() {
        val temp = createTempDirectory("danmaku-root-scanner")
        val userPath = temp.resolve("User").createDirectories()
        val aniRssPath = temp.resolve("AniRss").createDirectories()
        userPath.resolve("User Show").createDirectories()
            .resolve("Episode 01.mkv")
            .writeBytes(byteArrayOf(1))
        aniRssPath.resolve("AniRss Show").createDirectories()
            .resolve("Episode 01.mkv")
            .writeBytes(byteArrayOf(2))

        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { store ->
            val registry = DesktopLibraryRootRegistry(store) { 100 }
            val scanner = DesktopLibraryRootScanner(store, registry)
            registry.addUserSelectedRoot(userPath)
            registry.addAniRssOutputRoot(aniRssPath)

            val batch = scanner.scanAniRssRoots()

            assertEquals(1, batch.results.size)
            assertEquals(
                DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER,
                batch.results.single().root.provenance,
            )
            assertEquals(1, batch.publishedLibrary.catalog.items.size)
            assertEquals("AniRss Show", batch.publishedLibrary.catalog.items.single().seriesTitle)
        }

        temp.toFile().deleteRecursively()
    }
}
