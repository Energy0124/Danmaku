package app.danmaku.desktop

import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackController
import app.danmaku.domain.PlaybackPosition
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind

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
                    volumePercent = snapshot.volumePercent,
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
                    is PlaybackCommand.SetVolume -> snapshot.copy(
                        volumePercent = command.volumePercent,
                        errorMessage = null,
                    )
                    is PlaybackCommand.SelectAudioTrack -> snapshot.copy(
                        tracks = snapshot.tracks.map { track ->
                            if (track.kind == PlaybackTrackKind.AUDIO) {
                                track.copy(selected = track.id == command.trackId)
                            } else {
                                track
                            }
                        },
                        errorMessage = null,
                    )
                    is PlaybackCommand.SelectSubtitleTrack -> snapshot.copy(
                        tracks = snapshot.tracks.map { track ->
                            if (track.kind == PlaybackTrackKind.SUBTITLE) {
                                track.copy(selected = track.id == command.trackId)
                            } else {
                                track
                            }
                        },
                        errorMessage = null,
                    )
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
            val volumePercent = reader.readProperty("volume")
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() }
                ?.toInt()
                ?.coerceIn(0, 100)
                ?: snapshot.volumePercent
            val tracks = reader.readPlaybackTracks()
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
                volumePercent = volumePercent,
                tracks = tracks.ifEmpty { snapshot.tracks },
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

private fun DesktopMpvPropertyReader.readPlaybackTracks(): List<PlaybackTrack> {
    val count = readProperty("track-list/count")
        ?.toIntOrNull()
        ?.coerceIn(0, MAX_MPV_TRACK_COUNT)
        ?: return emptyList()
    return (0 until count).mapNotNull { index ->
        val kind = readTrackKind(index) ?: return@mapNotNull null
        val mpvId = readProperty("track-list/$index/id")
            ?.takeIf { it.isNotBlank() && it != "no" }
            ?: return@mapNotNull null
        val language = readProperty("track-list/$index/lang")
            ?.takeIf { it.isNotBlank() && it != "und" }
        PlaybackTrack(
            id = kind.toMpvPlaybackTrackId(mpvId),
            kind = kind,
            label = trackLabel(
                kind = kind,
                index = index,
                id = mpvId,
                title = readProperty("track-list/$index/title"),
                language = language,
            ),
            language = language,
            selected = readBooleanProperty("track-list/$index/selected") ?: false,
            supported = true,
        )
    }
}

private fun DesktopMpvPropertyReader.readTrackKind(index: Int): PlaybackTrackKind? =
    when (readProperty("track-list/$index/type")?.lowercase()) {
        "audio" -> PlaybackTrackKind.AUDIO
        "sub", "subtitle" -> PlaybackTrackKind.SUBTITLE
        else -> null
    }

private fun trackLabel(
    kind: PlaybackTrackKind,
    index: Int,
    id: String,
    title: String?,
    language: String?,
): String =
    listOfNotNull(
        title?.takeIf(String::isNotBlank),
        language?.takeIf(String::isNotBlank)?.uppercase(),
    ).distinct().joinToString(separator = " / ").ifBlank {
        "${kind.displayName()} ${index + 1} (mpv $id)"
    }

private fun PlaybackTrackKind.displayName(): String =
    when (this) {
        PlaybackTrackKind.AUDIO -> "Audio"
        PlaybackTrackKind.SUBTITLE -> "Subtitle"
    }

private fun PlaybackTrackKind.toMpvPlaybackTrackId(mpvId: String): String =
    "mpv:${name.lowercase()}:$mpvId"

private fun Double.toMillis(): Long =
    (this * 1_000.0).toLong().coerceAtLeast(0)

private const val MAX_MPV_TRACK_COUNT = 64
