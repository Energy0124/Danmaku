package app.danmaku.desktop

import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackController
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus

fun interface DesktopMpvCommandExecutor {
    fun execute(command: DesktopMpvCommand)
}

interface CloseableDesktopMpvCommandExecutor : DesktopMpvCommandExecutor, AutoCloseable

class DesktopMpvPlaybackController(
    private val commandExecutor: DesktopMpvCommandExecutor,
) : PlaybackController {
    private var snapshot = PlaybackSnapshot()

    override fun load(source: PlaybackSource) {
        execute(
            command = DesktopMpvCommandPlanner.load(source),
            nextSnapshot = {
                PlaybackSnapshot(
                    status = PlaybackStatus.LOADING,
                    source = source,
                    position = PlaybackPosition(positionMs = 0, durationMs = null),
                    playbackRate = snapshot.playbackRate,
                )
            },
        )
    }

    override fun dispatch(command: PlaybackCommand) {
        execute(
            command = DesktopMpvCommandPlanner.dispatch(command),
            nextSnapshot = {
                when (command) {
                    PlaybackCommand.Play -> snapshot.copy(
                        status = snapshot.source?.let { PlaybackStatus.PLAYING }
                            ?: PlaybackStatus.IDLE,
                        errorMessage = null,
                    )
                    PlaybackCommand.Pause -> snapshot.copy(
                        status = snapshot.source?.let { PlaybackStatus.PAUSED }
                            ?: PlaybackStatus.IDLE,
                        errorMessage = null,
                    )
                    is PlaybackCommand.SeekTo -> snapshot.copy(
                        position = snapshot.position.copy(positionMs = command.positionMs),
                        errorMessage = null,
                    )
                    is PlaybackCommand.SetPlaybackRate -> snapshot.copy(
                        playbackRate = command.rate,
                        errorMessage = null,
                    )
                    is PlaybackCommand.SelectAudioTrack,
                    is PlaybackCommand.SelectSubtitleTrack,
                    -> snapshot.copy(errorMessage = null)
                }
            },
        )
    }

    override fun snapshot(): PlaybackSnapshot = snapshot

    private fun execute(
        command: DesktopMpvCommand,
        nextSnapshot: () -> PlaybackSnapshot,
    ) {
        runCatching {
            commandExecutor.execute(command)
        }.onSuccess {
            snapshot = nextSnapshot()
        }.onFailure { error ->
            snapshot = snapshot.copy(
                status = PlaybackStatus.ERROR,
                errorMessage = error.message ?: error::class.simpleName ?: "mpv command failed",
            )
        }
    }
}
