package app.danmaku.desktop

import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrackKind
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
        controller.dispatch(PlaybackCommand.SetVolume(70))
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
                DesktopMpvCommand(listOf("set", "volume", "70")),
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
        assertEquals(70, controller.snapshot().volumePercent)
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
    fun pollsPlaybackPropertiesFromNativeReader() {
        val executor = RecordingDesktopMpvCommandExecutor()
        val properties = mutableMapOf(
            "time-pos" to "45.5",
            "duration" to "120.0",
            "pause" to "no",
            "speed" to "1.25",
            "volume" to "42",
            "eof-reached" to "no",
        )
        val controller = DesktopMpvPlaybackController(
            commandExecutor = executor,
            propertyReader = DesktopMpvPropertyReader { properties[it] },
        )
        val source = PlaybackSource.LocalFile("S:\\Anime\\Example Show\\Episode 01.mkv")

        controller.load(source)
        val snapshot = controller.snapshot()

        assertEquals(PlaybackStatus.PLAYING, snapshot.status)
        assertEquals(PlaybackPosition(positionMs = 45_500, durationMs = 120_000), snapshot.position)
        assertEquals(1.25f, snapshot.playbackRate)
        assertEquals(42, snapshot.volumePercent)
    }

    @Test
    fun pollsRuntimeAudioAndSubtitleTracksFromNativeReader() {
        val executor = RecordingDesktopMpvCommandExecutor()
        val properties = mutableMapOf(
            "time-pos" to "0",
            "duration" to "120.0",
            "pause" to "yes",
            "speed" to "1.0",
            "eof-reached" to "no",
            "track-list/count" to "3",
            "track-list/0/type" to "video",
            "track-list/0/id" to "1",
            "track-list/1/type" to "audio",
            "track-list/1/id" to "2",
            "track-list/1/title" to "Japanese Stereo",
            "track-list/1/lang" to "jpn",
            "track-list/1/selected" to "yes",
            "track-list/2/type" to "sub",
            "track-list/2/id" to "2",
            "track-list/2/title" to "English Signs",
            "track-list/2/lang" to "eng",
            "track-list/2/selected" to "no",
        )
        val controller = DesktopMpvPlaybackController(
            commandExecutor = executor,
            propertyReader = DesktopMpvPropertyReader { properties[it] },
        )

        controller.load(PlaybackSource.LocalFile("S:\\Anime\\Example Show\\Episode 01.mkv"))
        val snapshot = controller.snapshot()

        assertEquals(2, snapshot.tracks.size)
        assertEquals("mpv:audio:2", snapshot.tracks[0].id)
        assertEquals(PlaybackTrackKind.AUDIO, snapshot.tracks[0].kind)
        assertEquals("Japanese Stereo / JPN", snapshot.tracks[0].label)
        assertEquals("jpn", snapshot.tracks[0].language)
        assertEquals(true, snapshot.tracks[0].selected)
        assertEquals("mpv:subtitle:2", snapshot.tracks[1].id)
        assertEquals(PlaybackTrackKind.SUBTITLE, snapshot.tracks[1].kind)
        assertEquals("English Signs / ENG", snapshot.tracks[1].label)
        assertEquals(false, snapshot.tracks[1].selected)
    }

    @Test
    fun updatesRuntimeTrackSelectionSnapshotAfterCommands() {
        val executor = RecordingDesktopMpvCommandExecutor()
        val properties = mutableMapOf(
            "time-pos" to "0",
            "duration" to "120.0",
            "pause" to "yes",
            "speed" to "1.0",
            "eof-reached" to "no",
            "track-list/count" to "2",
            "track-list/0/type" to "audio",
            "track-list/0/id" to "2",
            "track-list/0/selected" to "no",
            "track-list/1/type" to "sub",
            "track-list/1/id" to "3",
            "track-list/1/selected" to "yes",
        )
        val controller = DesktopMpvPlaybackController(
            commandExecutor = executor,
            propertyReader = DesktopMpvPropertyReader { properties[it] },
        )

        controller.load(PlaybackSource.LocalFile("S:\\Anime\\Example Show\\Episode 01.mkv"))
        controller.snapshot()
        controller.dispatch(PlaybackCommand.SelectAudioTrack("mpv:audio:2"))
        controller.dispatch(PlaybackCommand.SelectSubtitleTrack(null))
        properties["track-list/0/selected"] = "yes"
        properties["track-list/1/selected"] = "no"

        assertEquals(
            listOf(
                DesktopMpvCommand(listOf("loadfile", "S:\\Anime\\Example Show\\Episode 01.mkv", "replace")),
                DesktopMpvCommand(listOf("set", "aid", "2")),
                DesktopMpvCommand(listOf("set", "sid", "no")),
            ),
            executor.commands,
        )
        val snapshot = controller.snapshot()
        assertEquals(true, snapshot.tracks.single { it.kind == PlaybackTrackKind.AUDIO }.selected)
        assertEquals(false, snapshot.tracks.single { it.kind == PlaybackTrackKind.SUBTITLE }.selected)
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
