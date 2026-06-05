package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackTarget
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlaybackSessionTest {
    @Test
    fun loadsLocalPlaybackRequestsAndSeeksToResumePosition() {
        val executor = RecordingDesktopMpvCommandExecutor()
        val controller = DesktopMpvPlaybackController(executor)
        val loadedRequests = mutableListOf<DesktopPlaybackRequest>()
        val request = DesktopPlaybackRequest(
            label = "Example Show - Episode 01",
            source = PlaybackSource.LocalFile("S:\\Anime\\Example Show\\Episode 01.mkv"),
            resumePositionMs = 12_345,
        )

        val snapshot = DesktopPlaybackSession(controller, loadedRequests::add).load(request)

        assertEquals(
            listOf(
                DesktopMpvCommand(
                    listOf(
                        "loadfile",
                        "S:\\Anime\\Example Show\\Episode 01.mkv",
                        "replace",
                    ),
                ),
                DesktopMpvCommand(listOf("seek", "12.345", "absolute")),
            ),
            executor.commands,
        )
        assertEquals(PlaybackStatus.LOADING, snapshot.status)
        assertEquals(request.source, snapshot.source)
        assertEquals(12_345, snapshot.position.positionMs)
        assertEquals(listOf(request), loadedRequests)
    }

    @Test
    fun loadsLanPlaybackRequestsWithoutResumeSeekWhenProgressIsNotMeaningful() {
        val executor = RecordingDesktopMpvCommandExecutor()
        val controller = DesktopMpvPlaybackController(executor)
        val item = LibraryMediaItem(
            id = "episode-id",
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mkv",
            sizeBytes = 123,
            mediaType = "video/mp4",
            streamPath = "/media/episode-id",
        )
        val preparation = LanPlaybackPreparation(
            item = item,
            target = LanPlaybackTarget(
                baseUrl = "http://127.0.0.1:8686",
                pairingToken = "123456",
                mediaId = item.id,
            ),
            source = PlaybackSource.RemoteStream(
                "http://127.0.0.1:8686/media/episode-id?token=123456",
            ),
            resumePositionMs = null,
        )

        val request = preparation.toDesktopPlaybackRequest()
        val snapshot = DesktopPlaybackSession(controller).load(request)

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
        assertEquals(PlaybackStatus.LOADING, snapshot.status)
        assertEquals(preparation.source, snapshot.source)
        assertEquals(item.id, request.progressMediaId)
        assertEquals(preparation.target, request.progressTarget)
    }

    @Test
    fun mapsLocalPreparationsToPlaybackRequests() {
        val item = LibraryMediaItem(
            id = "episode-id",
            seriesTitle = "Example Show",
            episodeTitle = "Episode 01",
            relativePath = "Example Show/Episode 01.mkv",
            sizeBytes = 123,
            mediaType = "video/mp4",
            streamPath = "/media/episode-id",
        )
        val source = PlaybackSource.LocalFile("S:\\Anime\\Example Show\\Episode 01.mkv")

        assertEquals(
            DesktopPlaybackRequest(
                label = "Example Show - Episode 01",
                source = source,
                resumePositionMs = 12_345,
                progressMediaId = "episode-id",
            ),
            DesktopLocalPlaybackPreparation(
                item = item,
                source = source,
                resumePositionMs = 12_345,
            ).toPlaybackRequest(),
        )
    }

    @Test
    fun mapsArbitraryLocalFilesToPlaybackRequests() {
        assertEquals(
            DesktopPlaybackRequest(
                label = "Episode 01.mkv",
                source = PlaybackSource.LocalFile("S:\\Anime\\Example Show\\Episode 01.mkv"),
                resumePositionMs = null,
            ),
            Path.of("S:/Anime/Example Show/../Example Show/Episode 01.mkv")
                .toDirectLocalPlaybackRequest(),
        )
    }

    private class RecordingDesktopMpvCommandExecutor : DesktopMpvCommandExecutor {
        val commands = mutableListOf<DesktopMpvCommand>()

        override fun execute(command: DesktopMpvCommand) {
            commands += command
        }
    }
}
