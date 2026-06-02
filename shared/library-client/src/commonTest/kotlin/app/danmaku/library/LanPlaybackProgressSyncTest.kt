package app.danmaku.library

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class LanPlaybackProgressSyncTest {
    @Test
    fun fetchesMeaningfulResumePositionsFromTheClient() {
        val client = RecordingLanLibraryClient(
            progress = PlaybackProgress(
                mediaId = "episode-id",
                positionMs = 12_345,
                durationMs = 98_765,
                updatedAtEpochMs = 789,
            ),
        )

        assertEquals(
            12_345,
            LanPlaybackProgressSync(client, currentTimeMillis = { 0 }).fetchResumePositionMs(
                LanPlaybackTarget(
                    baseUrl = "http://192.168.1.20:8686",
                    pairingToken = "123456",
                    mediaId = "episode-id",
                ),
            ),
        )
    }

    @Test
    fun convertsSnapshotsIntoPairedProgressUpdates() {
        val client = RecordingLanLibraryClient()
        val target = LanPlaybackTarget(
            baseUrl = "http://192.168.1.20:8686",
            pairingToken = "123456",
            mediaId = "episode-id",
        )

        LanPlaybackProgressSync(client, currentTimeMillis = { 789 }).saveProgress(
            target = target,
            snapshot = PlaybackSnapshot(
                status = PlaybackStatus.PLAYING,
                source = PlaybackSource.RemoteStream("http://example/media"),
                position = PlaybackPosition(positionMs = 12_345, durationMs = 98_765),
            ),
        )

        assertEquals(
            PlaybackProgress(
                mediaId = "episode-id",
                positionMs = 12_345,
                durationMs = 98_765,
                updatedAtEpochMs = 789,
            ),
            client.savedProgress,
        )
    }

    private class RecordingLanLibraryClient(
        private val progress: PlaybackProgress? = null,
    ) : LanLibraryClient {
        var savedProgress: PlaybackProgress? = null

        override fun fetchCatalog(baseUrl: String, pairingToken: String): LibraryCatalog =
            error("not used")

        override fun streamUrl(
            baseUrl: String,
            item: LibraryMediaItem,
            pairingToken: String,
        ): String = error("not used")

        override fun fetchProgress(
            baseUrl: String,
            mediaId: String,
            pairingToken: String,
        ): PlaybackProgress? = progress

        override fun saveProgress(
            baseUrl: String,
            pairingToken: String,
            progress: PlaybackProgress,
        ) {
            savedProgress = progress
        }
    }
}
