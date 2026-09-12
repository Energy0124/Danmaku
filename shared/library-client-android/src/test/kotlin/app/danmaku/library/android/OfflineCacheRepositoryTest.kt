package app.danmaku.library.android

import androidx.work.ListenableWorker.Result
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
            uploader = OfflineProgressUploader { _, progress -> uploads += progress },
        )
        val entry = repository.enqueue(SERVER_URL, listOf(mediaItem())).single()
        val progress = PlaybackProgress("episode-1", 42_000, 90_000, 200)

        repository.savePendingProgress(entry.key, progress)
        repository.delete(entry.key)

        assertTrue(repository.entries().isEmpty())
        assertEquals(listOf(progress), repository.syncPendingProgress(SERVER_URL, emptyList()))
        assertEquals(listOf(progress), uploads)

        repository.syncPendingProgress(SERVER_URL, emptyList())
        assertEquals(1, uploads.size)
    }

    @Test
    fun clearKeepsProgressTombstoneAndRemoteNewerProgressWins() {
        val uploads = mutableListOf<PlaybackProgress>()
        val repository = repository(
            uploader = OfflineProgressUploader { _, progress -> uploads += progress },
        )
        val entry = repository.enqueue(SERVER_URL, listOf(mediaItem())).single()
        val pending = PlaybackProgress("episode-1", 20_000, 90_000, 100)
        val remote = PlaybackProgress("episode-1", 30_000, 90_000, 200)
        repository.savePendingProgress(entry.key, pending)

        repository.clear()
        val merged = repository.syncPendingProgress(SERVER_URL, listOf(remote))

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

    @Test
    fun downloadSpaceUsesRemainingBytesAndPreservesReserve() {
        val mib = 1024L * 1024L
        requireDownloadSpace(6_400 * mib, 369 * mib)
        requireDownloadSpace(300 * mib, 44 * mib)
        try {
            requireDownloadSpace(300 * mib, 45 * mib)
            throw AssertionError("The download must preserve the 256 MiB reserve")
        } catch (_: PermanentDownloadException) {
            // A resumed transfer needs room only for its remaining bytes.
        }
    }

    @Test
    fun failedItemDoesNotFailTheWorkChain() = runBlocking {
        val repository = repository()
        val entries = repository.enqueue(
            SERVER_URL,
            listOf(mediaItem(), mediaItem().copy(id = "episode-2")),
        )

        val result = repository.runDownloadAttempt(entries[0].key, 0) {
            throw PermanentDownloadException("Video is no longer available on the PC")
        }
        assertEquals(Result.success(), result)
        assertEquals(OfflineCacheState.FAILED, repository.entry(entries[0].key)?.state)
        var nextStarted = false
        assertEquals(Result.success(), repository.runDownloadAttempt(entries[1].key, 0) {
            nextStarted = true
        })
        assertTrue(nextStarted)
    }

    @Test
    fun startupFailureIsVisibleAndRetriesAreBounded() = runBlocking {
        val repository = repository()
        val entry = repository.enqueue(SERVER_URL, listOf(mediaItem())).single()
        val start: suspend (OfflineCacheEntry) -> Unit = {
            throw IllegalStateException("Foreground startup failed")
        }

        assertEquals(Result.retry(), repository.runDownloadAttempt(entry.key, 0, start))
        assertEquals(OfflineCacheState.RETRYING, repository.entry(entry.key)?.state)
        assertEquals("Foreground startup failed", repository.entry(entry.key)?.errorMessage)
        assertEquals(Result.success(), repository.runDownloadAttempt(entry.key, 4, start))
        assertEquals(OfflineCacheState.FAILED, repository.entry(entry.key)?.state)
    }

    @Test
    fun interruptedDownloadCanStartAgainWithoutLosingProgress() = runBlocking {
        val repository = repository()
        val entry = repository.enqueue(SERVER_URL, listOf(mediaItem())).single()
        try {
            repository.runDownloadAttempt(entry.key, 0) {
                repository.updateEntry(entry.key) { it.copy(downloadedBytes = 3) }
                throw CancellationException("Worker stopped")
            }
            throw AssertionError("Worker cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(OfflineCacheState.DOWNLOADING, repository.entry(entry.key)?.state)
        }
        var restarted = false
        assertEquals(Result.success(), repository.runDownloadAttempt(entry.key, 1) {
            restarted = true
            assertEquals(3L, it.downloadedBytes)
        })
        assertTrue(restarted)
    }

    @Test
    fun failureAfterPauseDoesNotRequeueThePausedItem() = runBlocking {
        val repository = repository()
        val entry = repository.enqueue(SERVER_URL, listOf(mediaItem())).single()
        assertEquals(Result.success(), repository.runDownloadAttempt(entry.key, 0) {
            repository.pause(entry.key)
            throw IllegalStateException("Connection closed")
        })
        assertEquals(OfflineCacheState.PAUSED, repository.entry(entry.key)?.state)
        assertEquals(Result.success(), repository.runDownloadAttempt(entry.key, 1) {
            throw AssertionError("Paused download must not start")
        })
    }

    private fun repository(
        root: File = temporaryFolder.newFolder(),
        uploader: OfflineProgressUploader = OfflineProgressUploader { _, _ -> },
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

        override fun refreshPendingConstraints() = Unit

        override fun cancelAll() {
            cancelledAll = true
        }
    }

    private companion object {
        const val SERVER_URL = "http://pc:8686"
    }
}
