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

    @Test
    fun rejectsDuplicateTrackIds() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackSnapshot(
                tracks = listOf(
                    PlaybackTrack("audio-1", PlaybackTrackKind.AUDIO, "English"),
                    PlaybackTrack("audio-1", PlaybackTrackKind.SUBTITLE, "English"),
                ),
            )
        }
    }
}
