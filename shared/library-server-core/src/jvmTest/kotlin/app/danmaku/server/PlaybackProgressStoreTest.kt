package app.danmaku.server

import app.danmaku.domain.PlaybackProgress
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackProgressStoreTest {
    @Test
    fun fileStorePersistsProgressAcrossInstances() {
        val temp = createTempDirectory("danmaku-progress-store")
        val file = temp.resolve("progress.json")
        val first = PlaybackProgress(
            mediaId = "episode-1",
            positionMs = 12_000,
            durationMs = 24_000,
            updatedAtEpochMs = 100,
        )
        val second = PlaybackProgress(
            mediaId = "episode-2",
            positionMs = 45_000,
            durationMs = null,
            updatedAtEpochMs = 200,
        )

        try {
            FilePlaybackProgressStore(file).apply {
                saveProgress(first)
                saveProgress(second)
            }

            val loaded = FilePlaybackProgressStore(file)

            assertEquals(first, loaded.loadProgress(first.mediaId))
            assertEquals(second, loaded.loadProgress(second.mediaId))
            assertEquals(listOf(second, first), loaded.loadAllProgress())
        } finally {
            temp.toFile().deleteRecursively()
        }
    }
}
