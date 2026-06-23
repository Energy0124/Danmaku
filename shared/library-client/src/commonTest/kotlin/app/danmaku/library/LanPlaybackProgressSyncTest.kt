package app.danmaku.library

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LanDanmakuLoadStatus
import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class LanPlaybackProgressSyncTest {
    @Test
    fun preparesRemotePlaybackStreamsWithResumePosition() {
        val item = LibraryMediaItem(
            id = "episode-id",
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mp4",
            sizeBytes = 123,
            mediaType = "video/mp4",
            streamPath = "/media/episode-id",
            subtitles = listOf(
                LibrarySubtitleTrack(
                    id = "subtitle-id",
                    label = "English",
                    relativePath = "Example Show/Episode 01.en.srt",
                    mediaType = "application/x-subrip",
                    streamPath = "/subtitles/subtitle-id",
                ),
            ),
        )
        val client = RecordingLanLibraryClient(
            streamUrl = "http://192.168.1.20:8686/media/episode-id?token=123456",
            subtitleUrl = "http://192.168.1.20:8686/subtitles/subtitle-id?token=123456",
            progress = PlaybackProgress(
                mediaId = item.id,
                positionMs = 12_345,
                durationMs = 98_765,
                updatedAtEpochMs = 789,
            ),
        )

        val preparation = LanPlaybackPreparer(client).prepare(
            baseUrl = "http://192.168.1.20:8686",
            pairingToken = "123456",
            item = item,
        )

        assertEquals(item, preparation.item)
        assertEquals(
            LanPlaybackTarget(
                baseUrl = "http://192.168.1.20:8686",
                pairingToken = "123456",
                mediaId = item.id,
            ),
            preparation.target,
        )
        assertEquals(
            PlaybackSource.RemoteStream("http://192.168.1.20:8686/media/episode-id?token=123456"),
            preparation.source,
        )
        assertEquals(
            listOf(
                LanSubtitlePreparation(
                    track = item.subtitles.single(),
                    source = PlaybackSource.RemoteStream(
                        "http://192.168.1.20:8686/subtitles/subtitle-id?token=123456",
                    ),
                ),
            ),
            preparation.subtitles,
        )
        assertEquals(12_345, preparation.resumePositionMs)
    }

    @Test
    fun ignoresNearEndedProgressWhenPreparingRemotePlayback() {
        val item = LibraryMediaItem(
            id = "episode-id",
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mp4",
            sizeBytes = 123,
            mediaType = "video/mp4",
            streamPath = "/media/episode-id",
        )
        val client = RecordingLanLibraryClient(
            streamUrl = "http://192.168.1.20:8686/media/episode-id?token=123456",
            progress = PlaybackProgress(
                mediaId = item.id,
                positionMs = 95_000,
                durationMs = 98_765,
                updatedAtEpochMs = 789,
            ),
        )

        val preparation = LanPlaybackPreparer(client).prepare(
            baseUrl = "http://192.168.1.20:8686",
            pairingToken = "123456",
            item = item,
        )

        assertEquals(null, preparation.resumePositionMs)
    }

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
    fun fetchesCatalogWideProgressFromTheClient() {
        val progress = PlaybackProgress(
            mediaId = "episode-id",
            positionMs = 12_345,
            durationMs = 98_765,
            updatedAtEpochMs = 789,
        )
        val client = RecordingLanLibraryClient(progresses = listOf(progress))

        assertEquals(
            listOf(progress),
            LanPlaybackProgressSync(client, currentTimeMillis = { 0 }).fetchAllProgress(
                baseUrl = "http://192.168.1.20:8686",
                pairingToken = "123456",
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
        private val streamUrl: String = "http://example/media",
        private val subtitleUrl: String = "http://example/subtitle",
        private val progress: PlaybackProgress? = null,
        private val progresses: List<PlaybackProgress> = progress?.let(::listOf).orEmpty(),
    ) : LanLibraryClient {
        var savedProgress: PlaybackProgress? = null

        override fun fetchServerStatus(baseUrl: String): LanLibraryServerStatus =
            LanLibraryServerStatus()

        override fun fetchCatalog(baseUrl: String, pairingToken: String): LibraryCatalog =
            error("not used")

        override fun streamUrl(
            baseUrl: String,
            item: LibraryMediaItem,
            pairingToken: String,
        ): String = streamUrl

        override fun subtitleUrl(
            baseUrl: String,
            subtitle: LibrarySubtitleTrack,
            pairingToken: String,
        ): String = subtitleUrl

        override fun fetchProgress(
            baseUrl: String,
            mediaId: String,
            pairingToken: String,
        ): PlaybackProgress? = progress

        override fun fetchAllProgress(
            baseUrl: String,
            pairingToken: String,
        ): List<PlaybackProgress> =
            progresses

        override fun fetchDanmaku(
            baseUrl: String,
            mediaId: String,
            pairingToken: String,
            forceRefresh: Boolean,
        ): LanDanmakuTrack =
            LanDanmakuTrack(mediaId = mediaId, status = LanDanmakuLoadStatus.UNAVAILABLE)

        override fun saveProgress(
            baseUrl: String,
            pairingToken: String,
            progress: PlaybackProgress,
        ) {
            savedProgress = progress
        }
    }
}
