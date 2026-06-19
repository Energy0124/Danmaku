package app.danmaku.desktop

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
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

    @Test
    fun infersSeriesTitlesForRootLevelReleaseFilesAndRefreshesCachedTitles() {
        val root = createTempDirectory("danmaku-library")
        root.resolve("[BeanSub&FZSD&LoliHouse] Jujutsu Kaisen - 15 [WebRip 1080p].mkv")
            .writeBytes(byteArrayOf(1, 2, 3))
        root.resolve("[HYSUB]Sono Bisque Doll wa Koi wo Suru[15][BIG5_MP4][1920X1080].mp4")
            .writeBytes(byteArrayOf(4, 5, 6))
        root.resolve("[Re Zero kara Hajimeru Isekai Seikatsu S2][01][BIG5][1080P].mp4")
            .writeBytes(byteArrayOf(7, 8, 9))
        root.resolve("[Comicat&KissSub][86 - Eighty Six][04][1080P][BIG5][MP4].mp4")
            .writeBytes(byteArrayOf(10, 11, 12))

        val indexed = LocalMediaLibraryIndexer.index(root)
        val titlesByEpisode = indexed.catalog.items.associate { item -> item.episodeTitle to item.seriesTitle }

        assertEquals(
            "Jujutsu Kaisen",
            titlesByEpisode["[BeanSub&FZSD&LoliHouse] Jujutsu Kaisen - 15 [WebRip 1080p]"],
        )
        assertEquals(
            "Sono Bisque Doll wa Koi wo Suru",
            titlesByEpisode["[HYSUB]Sono Bisque Doll wa Koi wo Suru[15][BIG5_MP4][1920X1080]"],
        )
        assertEquals(
            "Re Zero kara Hajimeru Isekai Seikatsu S2",
            titlesByEpisode["[Re Zero kara Hajimeru Isekai Seikatsu S2][01][BIG5][1080P]"],
        )
        assertEquals(
            "86 - Eighty Six",
            titlesByEpisode["[Comicat&KissSub][86 - Eighty Six][04][1080P][BIG5][MP4]"],
        )

        val staleCache = indexed.fileMetadataByRelativePath.mapValues { (_, cached) ->
            cached.copy(item = cached.item.copy(seriesTitle = "Anime"))
        }
        val cached = LocalMediaLibraryIndexer.index(
            root = root,
            cachedItems = staleCache,
        )

        assertEquals(4, cached.scanStats.reusedItemCount)
        assertEquals(
            indexed.catalog.items.map { item -> LibraryMediaItemTitle.from(item) },
            cached.catalog.items.map { item -> LibraryMediaItemTitle.from(item) },
        )

        root.toFile().deleteRecursively()
    }

    @Test
    fun indexesMatchingSidecarSubtitlesAndDiscoversNewTracksForCachedVideos() {
        val root = createTempDirectory("danmaku-library")
        val series = root.resolve("Example Show").createDirectories()
        series.resolve("Episode 01.mkv").writeBytes(byteArrayOf(1, 2, 3))
        series.resolve("episode 01.en.srt").writeText("1\n00:00:00,000 --> 00:00:01,000\nHello")
        series.resolve("Episode 02.srt").writeText("not a match")

        val first = LocalMediaLibraryIndexer.index(root)
        val firstTrack = first.catalog.items.single().subtitles.single()

        assertEquals("en", firstTrack.label)
        assertEquals("application/x-subrip", firstTrack.mediaType)
        assertEquals("Example Show/episode 01.en.srt", firstTrack.relativePath)
        assertEquals(series.resolve("episode 01.en.srt"), first.subtitleFilesById[firstTrack.id])

        series.resolve("Episode 01.ass").writeText("[Script Info]")
        val cached = LocalMediaLibraryIndexer.index(
            root = root,
            cachedItems = first.fileMetadataByRelativePath,
        )

        assertEquals(1, cached.scanStats.reusedItemCount)
        assertEquals(2, cached.catalog.items.single().subtitles.size)

        root.toFile().deleteRecursively()
    }

    @Test
    fun reusesUnchangedItemsAndRefreshesChangedFiles() {
        val root = createTempDirectory("danmaku-library")
        val series = root.resolve("Example Show").createDirectories()
        val episode = series.resolve("Episode 01.mkv")
        episode.writeBytes(byteArrayOf(1, 2, 3))
        val first = LocalMediaLibraryIndexer.index(root)
        val firstIndexedAtEpochMs = first.catalog.items.single().indexedAtEpochMs
        assertTrue(firstIndexedAtEpochMs > 0)

        val unchanged = LocalMediaLibraryIndexer.index(
            root = root,
            cachedItems = first.fileMetadataByRelativePath,
        )
        assertEquals(1, unchanged.scanStats.reusedItemCount)
        assertEquals(0, unchanged.scanStats.refreshedItemCount)
        assertEquals(firstIndexedAtEpochMs, unchanged.catalog.items.single().indexedAtEpochMs)

        episode.writeText("changed content")
        val changed = LocalMediaLibraryIndexer.index(
            root = root,
            cachedItems = unchanged.fileMetadataByRelativePath,
        )
        assertEquals(0, changed.scanStats.reusedItemCount)
        assertEquals(1, changed.scanStats.refreshedItemCount)
        assertTrue(changed.catalog.items.single().indexedAtEpochMs > 0)

        root.toFile().deleteRecursively()
    }

    @Test
    fun namespacesIdsForMultipleRootIndexes() {
        val temp = createTempDirectory("danmaku-library")
        val firstRoot = temp.resolve("First").createDirectories()
        val secondRoot = temp.resolve("Second").createDirectories()
        firstRoot.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
            .writeBytes(byteArrayOf(1, 2, 3))
        secondRoot.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
            .writeBytes(byteArrayOf(4, 5, 6))

        val firstItem = LocalMediaLibraryIndexer.index(
            root = firstRoot,
            idNamespace = "first-root",
        ).catalog.items.single()
        val secondItem = LocalMediaLibraryIndexer.index(
            root = secondRoot,
            idNamespace = "second-root",
        ).catalog.items.single()

        assertEquals("Example Show/Episode 01.mkv", firstItem.relativePath)
        assertEquals("Example Show/Episode 01.mkv", secondItem.relativePath)
        assertTrue(firstItem.id != secondItem.id)
        assertTrue(firstItem.streamPath != secondItem.streamPath)

        temp.toFile().deleteRecursively()
    }

    private data class LibraryMediaItemTitle(
        val episodeTitle: String,
        val seriesTitle: String,
    ) {
        companion object {
            fun from(item: app.danmaku.domain.LibraryMediaItem): LibraryMediaItemTitle =
                LibraryMediaItemTitle(
                    episodeTitle = item.episodeTitle,
                    seriesTitle = item.seriesTitle,
                )
        }
    }
}
