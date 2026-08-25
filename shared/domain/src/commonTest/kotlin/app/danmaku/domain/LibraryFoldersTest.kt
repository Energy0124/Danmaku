package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryFoldersTest {
    @Test
    fun listsMultipleRootsAndNestedFolders() {
        val catalog = LibraryCatalog(
            rootName = "Merged",
            indexedAtEpochMs = 1,
            items = listOf(
                item("a", "M:\\Anime", "Alpha/Season 1/Episode 1.mkv"),
                item("b", "M:\\Anime", "Alpha/Season 1/Episode 2.mkv"),
                item("c", "D:\\Downloads", "Beta/Episode 1.mkv"),
            ),
        )

        val roots = catalog.folderListing(emptyList())
        assertEquals(listOf("M:\\Anime", "D:\\Downloads"), roots.folders.map { it.name })
        assertEquals(listOf(2, 1), roots.folders.map { it.itemCount })
        assertTrue(roots.files.isEmpty())

        val alpha = catalog.folderListing(listOf("M:\\Anime", "Alpha"))
        assertEquals(listOf(LibraryFolderEntry("Season 1", 2)), alpha.folders)
        assertEquals("M:\\Anime\\Alpha", catalog.folderHeading(listOf("M:\\Anime", "Alpha")))

        val season = catalog.folderListing(listOf("M:\\Anime", "Alpha", "Season 1"))
        assertEquals(listOf("a", "b"), season.files.map { it.id })
        assertEquals("Episode 1.mkv", season.files.first().fileName())
    }

    @Test
    fun singleRootStartsAtItsRelativeFolders() {
        val catalog = LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 1,
            items = listOf(item("a", "M:\\Anime", "Alpha/Episode 1.mkv")),
        )

        assertEquals(listOf("Alpha"), catalog.folderListing(emptyList()).folders.map { it.name })
        assertEquals("Anime\\Alpha", catalog.folderHeading(listOf("Alpha")))
    }

    @Test
    fun recursivelySelectsOnlyItemsBelowTheRequestedMultiRootFolder() {
        val catalog = LibraryCatalog(
            rootName = "Merged",
            indexedAtEpochMs = 1,
            items = listOf(
                item("one", "M:\\Anime", "Alpha/Season 1/01.mkv"),
                item("two", "M:\\Anime", "Alpha/Season 2/02.mkv"),
                item("three", "M:\\Anime", "Beta/01.mkv"),
                item("four", "N:\\Anime", "Alpha/03.mkv"),
            ),
        )

        assertEquals(
            listOf("one", "two"),
            catalog.itemsInFolder(listOf("M:\\Anime", "Alpha")).map { it.id },
        )
    }

    private fun item(id: String, rootLabel: String, relativePath: String) =
        LibraryMediaItem(
            id = id,
            seriesTitle = "Series $id",
            episodeTitle = "Episode $id",
            relativePath = relativePath,
            rootLabel = rootLabel,
            sizeBytes = 1,
            mediaType = "video/mp4",
            streamPath = "/media/$id",
        )
}
