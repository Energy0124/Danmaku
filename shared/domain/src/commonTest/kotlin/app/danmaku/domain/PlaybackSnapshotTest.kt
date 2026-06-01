package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PlaybackSnapshotTest {
    @Test
    fun rejectsAnErrorMessageOutsideTheErrorState() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackSnapshot(status = PlaybackStatus.READY, errorMessage = "no decoder")
        }
    }

    @Test
    fun rejectsBlankLocalPaths() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackSource.LocalFile(" ")
        }
    }
}
