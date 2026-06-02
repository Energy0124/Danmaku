package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
