package app.danmaku.library.android

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineCacheRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun pendingProgressSurvivesMediaDeletionUntilUploadSucceeds() {
        val uploads = mutableListOf<PlaybackProgress>()
        val repository = repository(
            uploader = OfflineProgressUploader { _, _, progress -> uploads += progress },
        )
        val entry = repository.enqueue(SERVER_URL, listOf(mediaItem())).single()
        val progress = PlaybackProgress("episode-1", 42_000, 90_000, 200)

        repository.savePendingProgress(entry.key, progress)
        repository.delete(entry.key)

        assertTrue(repository.entries().isEmpty())
        assertEquals(listOf(progress), repository.syncPendingProgress(SERVER_URL, "token", emptyList()))
        assertEquals(listOf(progress), uploads)

        repository.syncPendingProgress(SERVER_URL, "token", emptyList())
        assertEquals(1, uploads.size)
    }

    @Test
    fun clearKeepsProgressTombstoneAndRemoteNewerProgressWins() {
        val uploads = mutableListOf<PlaybackProgress>()
        val repository = repository(
            uploader = OfflineProgressUploader { _, _, progress -> uploads += progress },
        )
        val entry = repository.enqueue(SERVER_URL, listOf(mediaItem())).single()
        val pending = PlaybackProgress("episode-1", 20_000, 90_000, 100)
        val remote = PlaybackProgress("episode-1", 30_000, 90_000, 200)
        repository.savePendingProgress(entry.key, pending)

        repository.clear()
        val merged = repository.syncPendingProgress(SERVER_URL, "token", listOf(remote))

        assertEquals(listOf(remote), merged)
        assertTrue(uploads.isEmpty())
    }

    @Test
    fun corruptIndexAndMissingReadyAssetsFailClosed() {
        val root = temporaryFolder.newFolder("cache")
        File(root, "index.json").writeText("not-json")
        val repository = repository(root = root)

        assertTrue(repository.entries().isEmpty())
        val entry = repository.enqueue(SERVER_URL, listOf(mediaItem())).single()
        repository.updateEntry(entry.key) {
            it.copy(
                state = OfflineCacheState.READY,
                videoPath = "${entry.key}/missing-video.mkv",
                danmakuPath = "${entry.key}/missing-danmaku.json",
            )
        }

        assertNull(repository.playable(entry.key))
    }

    @Test
    fun queueControlsScheduleEntriesAndClearTheChain() {
        val scheduler = RecordingScheduler()
        val repository = repository(scheduler = scheduler)
        val first = mediaItem()
        val second = first.copy(id = "episode-2", episodeTitle = "Episode 2")

        val entries = repository.enqueue(SERVER_URL, listOf(first, second))
        repository.pause(entries.first().key)
        repository.resume(entries.first().key)
        repository.clear()

        assertEquals(entries.map { it.key } + entries.first().key, scheduler.enqueued)
        assertTrue(scheduler.cancelledAll)
    }

    private fun repository(
        root: File = temporaryFolder.newFolder(),
        uploader: OfflineProgressUploader = OfflineProgressUploader { _, _, _ -> },
        scheduler: OfflineWorkScheduler = RecordingScheduler(),
    ): AndroidOfflineCacheRepository = AndroidOfflineCacheRepository(
        root = root,
        workScheduler = scheduler,
        progressUploader = uploader,
        atomicMove = OfflineAtomicMove { source, destination ->
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        },
    )

    private fun mediaItem() = LibraryMediaItem(
        id = "episode-1",
        seriesTitle = "Example",
        episodeTitle = "Episode 1",
        relativePath = "Example/01.mkv",
        sizeBytes = 6,
        mediaType = "video/x-matroska",
        streamPath = "/api/library/items/episode-1/stream",
    )

    private class RecordingScheduler : OfflineWorkScheduler {
        val enqueued = mutableListOf<String>()
        var cancelledAll = false

        override fun enqueue(key: String) {
            enqueued += key
        }

        override fun cancelAll() {
            cancelledAll = true
        }
    }

    private companion object {
        const val SERVER_URL = "http://pc:8686"
    }
}
