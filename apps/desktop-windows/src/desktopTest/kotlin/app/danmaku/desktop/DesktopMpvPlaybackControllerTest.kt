package app.danmaku.desktop

import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopMpvPlaybackControllerTest {
    @Test
    fun loadsLocalFilesAndTracksPlaybackCommands() {
        val executor = RecordingDesktopMpvCommandExecutor()
        val controller = DesktopMpvPlaybackController(executor)
        val source = PlaybackSource.LocalFile("S:\\Anime\\Example Show\\Episode 01.mkv")

        controller.load(source)
        controller.dispatch(PlaybackCommand.Play)
        controller.dispatch(PlaybackCommand.SeekTo(12_345))
        controller.dispatch(PlaybackCommand.SetPlaybackRate(1.25f))
        controller.dispatch(PlaybackCommand.Pause)

        assertEquals(
            listOf(
                DesktopMpvCommand(
                    listOf(
                        "loadfile",
                        "S:\\Anime\\Example Show\\Episode 01.mkv",
                        "replace",
                    ),
                ),
                DesktopMpvCommand(listOf("set", "pause", "no")),
                DesktopMpvCommand(listOf("seek", "12.345", "absolute")),
                DesktopMpvCommand(listOf("set", "speed", "1.25")),
                DesktopMpvCommand(listOf("set", "pause", "yes")),
            ),
            executor.commands,
        )
        assertEquals(PlaybackStatus.PAUSED, controller.snapshot().status)
        assertEquals(source, controller.snapshot().source)
        assertEquals(
            PlaybackPosition(positionMs = 12_345, durationMs = null),
            controller.snapshot().position,
        )
        assertEquals(1.25f, controller.snapshot().playbackRate)
    }

    @Test
    fun loadsRemoteStreams() {
        val executor = RecordingDesktopMpvCommandExecutor()
        val controller = DesktopMpvPlaybackController(executor)
        val source = PlaybackSource.RemoteStream(
            "http://127.0.0.1:8686/media/episode-id?token=123456",
        )

        controller.load(source)

        assertEquals(
            listOf(
                DesktopMpvCommand(
                    listOf(
                        "loadfile",
                        "http://127.0.0.1:8686/media/episode-id?token=123456",
                        "replace",
                    ),
                ),
            ),
            executor.commands,
        )
        assertEquals(PlaybackStatus.LOADING, controller.snapshot().status)
        assertEquals(source, controller.snapshot().source)
    }

    @Test
    fun reportsExecutorFailuresAsPlaybackErrors() {
        val executor = RecordingDesktopMpvCommandExecutor(
            failure = IllegalStateException("libmpv command failed"),
        )
        val controller = DesktopMpvPlaybackController(executor)

        controller.load(PlaybackSource.RemoteStream("http://127.0.0.1/media"))

        assertEquals(PlaybackStatus.ERROR, controller.snapshot().status)
        assertEquals("libmpv command failed", controller.snapshot().errorMessage)
    }

    private class RecordingDesktopMpvCommandExecutor(
        private val failure: Throwable? = null,
    ) : DesktopMpvCommandExecutor {
        val commands = mutableListOf<DesktopMpvCommand>()

        override fun execute(command: DesktopMpvCommand) {
            commands += command
            failure?.let { throw it }
        }
    }
}
