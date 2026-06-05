package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlaybackCommandTest {
    @Test
    fun rejectsNegativeSeekPosition() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackCommand.SeekTo(-1)
        }
    }

    @Test
    fun rejectsNonPositivePlaybackRates() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackCommand.SetPlaybackRate(0f)
        }
    }

    @Test
    fun rejectsOutOfRangeVolumes() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackCommand.SetVolume(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            PlaybackCommand.SetVolume(101)
        }
    }

    @Test
    fun rejectsBlankTrackSelections() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackCommand.SelectAudioTrack(" ")
        }
        assertFailsWith<IllegalArgumentException> {
            PlaybackCommand.SelectSubtitleTrack(" ")
        }
    }

    @Test
    fun rejectsProgressWithoutAMediaId() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackProgress(
                mediaId = " ",
                positionMs = 1,
                durationMs = 2,
                updatedAtEpochMs = 3,
            )
        }
    }

    @Test
    fun rejectsNegativeProgressPosition() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackProgress(
                mediaId = "episode",
                positionMs = -1,
                durationMs = null,
                updatedAtEpochMs = 3,
            )
        }
    }

    @Test
    fun resumesMeaningfulInProgressPlayback() {
        assertEquals(
            45_000,
            PlaybackProgress(
                mediaId = "episode",
                positionMs = 45_000,
                durationMs = 120_000,
                updatedAtEpochMs = 3,
            ).resumePositionMs(),
        )
    }

    @Test
    fun coercesSeekTargetsToKnownDuration() {
        val position = PlaybackPosition(positionMs = 45_000, durationMs = 120_000)

        assertEquals(0, position.coerceSeekTarget(-1))
        assertEquals(60_000, position.coerceSeekTarget(60_000))
        assertEquals(120_000, position.coerceSeekTarget(130_000))
    }

    @Test
    fun seeksByDeltaWithinKnownDuration() {
        val position = PlaybackPosition(positionMs = 45_000, durationMs = 120_000)

        assertEquals(35_000, position.seekTargetBy(-10_000))
        assertEquals(0, position.seekTargetBy(-60_000))
        assertEquals(75_000, position.seekTargetBy(30_000))
        assertEquals(120_000, position.seekTargetBy(200_000))
    }

    @Test
    fun seeksByDeltaWithUnknownDuration() {
        val position = PlaybackPosition(positionMs = 45_000, durationMs = null)

        assertEquals(0, position.seekTargetBy(-60_000))
        assertEquals(75_000, position.seekTargetBy(30_000))
    }

    @Test
    fun restartsNearBeginningAndNearEndPlayback() {
        assertNull(
            PlaybackProgress(
                mediaId = "episode",
                positionMs = 9_999,
                durationMs = 120_000,
                updatedAtEpochMs = 3,
            ).resumePositionMs(),
        )
        assertNull(
            PlaybackProgress(
                mediaId = "episode",
                positionMs = 91_000,
                durationMs = 120_000,
                updatedAtEpochMs = 3,
            ).resumePositionMs(),
        )
    }

    @Test
    fun snapshotsWithoutSourcesDoNotCreateProgress() {
        assertNull(PlaybackSnapshot().toPlaybackProgress("episode", updatedAtEpochMs = 3))
    }
}
