package app.danmaku.desktop

import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSource
import app.danmaku.server.InMemoryPlaybackProgressStore
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DesktopLocalPlaybackPreparerTest {
    @Test
    fun preparesDirectLocalFilePlaybackWithResumePosition() {
        val temp = createTempDirectory("danmaku-local-playback")
        val episode = temp.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
        episode.writeBytes(byteArrayOf(1, 2, 3))
        val library = LocalMediaLibraryIndexer.index(temp)
        val item = library.catalog.items.single()
        val progressStore = InMemoryPlaybackProgressStore().apply {
            saveProgress(
                PlaybackProgress(
                    mediaId = item.id,
                    positionMs = 12_345,
                    durationMs = 98_765,
                    updatedAtEpochMs = 123,
                ),
            )
        }

        val preparation = DesktopLocalPlaybackPreparer(progressStore).prepare(library, item)

        assertEquals(item, preparation.item)
        assertEquals(
            PlaybackSource.LocalFile(episode.toAbsolutePath().normalize().toString()),
            preparation.source,
        )
        assertEquals(12_345, preparation.resumePositionMs)

        temp.toFile().deleteRecursively()
    }

    @Test
    fun skipsNearEndedLocalResumeProgress() {
        val temp = createTempDirectory("danmaku-local-playback")
        val episode = temp.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
        episode.writeBytes(byteArrayOf(1, 2, 3))
        val library = LocalMediaLibraryIndexer.index(temp)
        val item = library.catalog.items.single()
        val progressStore = InMemoryPlaybackProgressStore().apply {
            saveProgress(
                PlaybackProgress(
                    mediaId = item.id,
                    positionMs = 95_000,
                    durationMs = 98_765,
                    updatedAtEpochMs = 123,
                ),
            )
        }

        val preparation = DesktopLocalPlaybackPreparer(progressStore).prepare(library, item)

        assertNull(preparation.resumePositionMs)

        temp.toFile().deleteRecursively()
    }

    @Test
    fun rejectsDeletedIndexedFilesBeforePreparingPlayback() {
        val temp = createTempDirectory("danmaku-local-playback")
        val episode = temp.resolve("Example Show").createDirectories()
            .resolve("Episode 01.mkv")
        episode.writeBytes(byteArrayOf(1, 2, 3))
        val library = LocalMediaLibraryIndexer.index(temp)
        val item = library.catalog.items.single()
        episode.deleteExisting()

        assertFailsWith<IllegalArgumentException> {
            DesktopLocalPlaybackPreparer(InMemoryPlaybackProgressStore()).prepare(library, item)
        }

        temp.toFile().deleteRecursively()
    }
}
