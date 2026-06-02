package app.danmaku.desktop

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalLibrarySelectionStoreTest {
    @Test
    fun savesAndLoadsExistingLibraryRoot() {
        val temp = createTempDirectory("danmaku-library-selection")
        val root = temp.resolve("Anime").createDirectories()
        val store = LocalLibrarySelectionStore(temp.resolve("settings/library-root.txt"))

        store.save(root)

        assertEquals(root.toAbsolutePath().normalize(), store.load())
        temp.toFile().deleteRecursively()
    }

    @Test
    fun ignoresMissingLibraryRoot() {
        val temp = createTempDirectory("danmaku-library-selection")
        val selectionFile = temp.resolve("library-root.txt")
        selectionFile.writeText(temp.resolve("Missing").toString())

        assertNull(LocalLibrarySelectionStore(selectionFile).load())
        temp.toFile().deleteRecursively()
    }
}
