package app.danmaku.desktop

import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopMpvCommandPlannerTest {
    @Test
    fun plansLocalFileLoads() {
        assertEquals(
            DesktopMpvCommand(
                listOf(
                    "loadfile",
                    "S:\\Anime\\Example Show\\Episode 01.mkv",
                    "replace",
                ),
            ),
            DesktopMpvCommandPlanner.load(
                PlaybackSource.LocalFile("S:\\Anime\\Example Show\\Episode 01.mkv"),
            ),
        )
    }

    @Test
    fun plansRemoteStreamLoads() {
        assertEquals(
            DesktopMpvCommand(
                listOf(
                    "loadfile",
                    "http://127.0.0.1:8686/media/episode-id?token=123456",
                    "replace",
                ),
            ),
            DesktopMpvCommandPlanner.load(
                PlaybackSource.RemoteStream(
                    "http://127.0.0.1:8686/media/episode-id?token=123456",
                ),
            ),
        )
    }

    @Test
    fun plansPauseStateCommands() {
        assertEquals(
            DesktopMpvCommand(listOf("set", "pause", "no")),
            DesktopMpvCommandPlanner.dispatch(PlaybackCommand.Play),
        )
        assertEquals(
            DesktopMpvCommand(listOf("set", "pause", "yes")),
            DesktopMpvCommandPlanner.dispatch(PlaybackCommand.Pause),
        )
    }

    @Test
    fun plansSeekAndPlaybackRateCommands() {
        assertEquals(
            DesktopMpvCommand(listOf("seek", "12.345", "absolute")),
            DesktopMpvCommandPlanner.dispatch(PlaybackCommand.SeekTo(12_345)),
        )
        assertEquals(
            DesktopMpvCommand(listOf("set", "speed", "1.25")),
            DesktopMpvCommandPlanner.dispatch(PlaybackCommand.SetPlaybackRate(1.25f)),
        )
        assertEquals(
            DesktopMpvCommand(listOf("set", "volume", "75")),
            DesktopMpvCommandPlanner.dispatch(PlaybackCommand.SetVolume(75)),
        )
    }

    @Test
    fun plansAudioAndSubtitleTrackSelectionCommands() {
        assertEquals(
            DesktopMpvCommand(listOf("set", "aid", "2")),
            DesktopMpvCommandPlanner.dispatch(PlaybackCommand.SelectAudioTrack("2")),
        )
        assertEquals(
            DesktopMpvCommand(listOf("set", "sid", "3")),
            DesktopMpvCommandPlanner.dispatch(PlaybackCommand.SelectSubtitleTrack("3")),
        )
        assertEquals(
            DesktopMpvCommand(listOf("set", "sid", "no")),
            DesktopMpvCommandPlanner.dispatch(PlaybackCommand.SelectSubtitleTrack(null)),
        )
    }

    @Test
    fun stripsNamespacedWindowsTrackIdsForMpvSelectionCommands() {
        assertEquals(
            DesktopMpvCommand(listOf("set", "aid", "2")),
            DesktopMpvCommandPlanner.dispatch(PlaybackCommand.SelectAudioTrack("mpv:audio:2")),
        )
        assertEquals(
            DesktopMpvCommand(listOf("set", "sid", "3")),
            DesktopMpvCommandPlanner.dispatch(PlaybackCommand.SelectSubtitleTrack("mpv:subtitle:3")),
        )
    }

    @Test
    fun plansFullscreenAndAspectCommands() {
        assertEquals(
            DesktopMpvCommand(listOf("set", "fullscreen", "yes")),
            DesktopMpvCommandPlanner.setFullscreen(true),
        )
        assertEquals(
            DesktopMpvCommand(listOf("set", "fullscreen", "no")),
            DesktopMpvCommandPlanner.setFullscreen(false),
        )
        assertEquals(
            DesktopMpvCommand(listOf("set", "video-aspect-override", "no")),
            DesktopMpvCommandPlanner.setVideoAspectMode(DesktopVideoAspectMode.DEFAULT),
        )
        assertEquals(
            DesktopMpvCommand(listOf("set", "video-aspect-override", "16:9")),
            DesktopMpvCommandPlanner.setVideoAspectMode(DesktopVideoAspectMode.WIDE_16_9),
        )
    }

    @Test
    fun rejectsCommandsWithNullBytesBeforeCrossingTheNativeBoundary() {
        assertFailsWith<IllegalArgumentException> {
            DesktopMpvCommand(listOf("loadfile", "bad\u0000path", "replace"))
        }
    }
}
