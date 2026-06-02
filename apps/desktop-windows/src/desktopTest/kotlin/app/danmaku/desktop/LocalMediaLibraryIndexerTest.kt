package app.danmaku.desktop

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMediaLibraryIndexerTest {
    @Test
    fun indexesSupportedVideoFilesRecursively() {
        val root = createTempDirectory("danmaku-library")
        val series = root.resolve("Example Show").createDirectories()
        series.resolve("Episode 01.mkv").writeBytes(byteArrayOf(1, 2, 3))
        series.resolve("notes.txt").writeBytes(byteArrayOf(4))

        val indexed = LocalMediaLibraryIndexer.index(root)

        assertEquals(1, indexed.catalog.items.size)
        assertEquals("Example Show", indexed.catalog.items.single().seriesTitle)
        assertEquals("Episode 01", indexed.catalog.items.single().episodeTitle)
        assertTrue(indexed.filesById.containsKey(indexed.catalog.items.single().id))

        root.toFile().deleteRecursively()
    }
}
