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

fun interface DesktopMpvPropertyReader {
    fun readProperty(name: String): String?
}

interface CloseableDesktopMpvCommandExecutor : DesktopMpvCommandExecutor, AutoCloseable

class DesktopMpvPlaybackController(
    private val commandExecutor: DesktopMpvCommandExecutor,
    private val propertyReader: DesktopMpvPropertyReader? = null,
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

    override fun snapshot(): PlaybackSnapshot {
        val reader = propertyReader ?: return snapshot
        if (snapshot.source == null || snapshot.status == PlaybackStatus.ERROR) {
            return snapshot
        }
        return runCatching {
            val positionMs = reader.readSecondsProperty("time-pos")?.toMillis()
                ?: snapshot.position.positionMs
            val durationMs = reader.readSecondsProperty("duration")?.toMillis()
                ?: snapshot.position.durationMs
            val pause = reader.readBooleanProperty("pause")
            val eofReached = reader.readBooleanProperty("eof-reached") ?: false
            val rate = reader.readProperty("speed")?.toFloatOrNull()?.takeIf { it > 0 }
                ?: snapshot.playbackRate
            val status = when {
                eofReached -> PlaybackStatus.ENDED
                pause == true -> PlaybackStatus.PAUSED
                pause == false -> PlaybackStatus.PLAYING
                snapshot.status == PlaybackStatus.LOADING && (positionMs > 0 || durationMs != null) ->
                    PlaybackStatus.READY
                else -> snapshot.status
            }
            snapshot = snapshot.copy(
                status = status,
                position = PlaybackPosition(
                    positionMs = durationMs?.let { positionMs.coerceAtMost(it) } ?: positionMs,
                    durationMs = durationMs,
                ),
                playbackRate = rate,
                errorMessage = null,
            )
            snapshot
        }.getOrDefault(snapshot)
    }

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

private fun DesktopMpvPropertyReader.readSecondsProperty(name: String): Double? =
    readProperty(name)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

private fun DesktopMpvPropertyReader.readBooleanProperty(name: String): Boolean? =
    when (readProperty(name)?.lowercase()) {
        "yes", "true", "1" -> true
        "no", "false", "0" -> false
        else -> null
    }

private fun Double.toMillis(): Long =
    (this * 1_000.0).toLong().coerceAtLeast(0)
