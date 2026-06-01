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
}

