package app.danmaku.desktop

import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSource
import java.util.Locale

data class DesktopMpvCommand(
    val args: List<String>,
) {
    init {
        require(args.isNotEmpty()) { "args must not be empty" }
        args.forEach { argument ->
            require(argument.isNotBlank()) { "command arguments must not be blank" }
            require(argument.none { it == '\u0000' }) {
                "command arguments must not contain null bytes"
            }
        }
    }
}

enum class DesktopVideoAspectMode(
    val label: String,
    internal val mpvValue: String,
) {
    DEFAULT("Default", "no"),
    WIDE_16_9("16:9", "16:9"),
    STANDARD_4_3("4:3", "4:3"),
    SQUARE_1_1("1:1", "1:1"),
}

object DesktopMpvCommandPlanner {
    fun load(
        source: PlaybackSource,
        startPositionMs: Long? = null,
    ): DesktopMpvCommand {
        require(startPositionMs == null || startPositionMs >= 0) {
            "startPositionMs must not be negative"
        }
        val startOption = startPositionMs
            ?.takeIf { it > 0 }
            ?.let { "start=${it.toMpvSeconds()}" }
        val perFileOptions = startOption?.let { listOf("-1", it) }.orEmpty()
        return DesktopMpvCommand(
            listOf(
                "loadfile",
                when (source) {
                    is PlaybackSource.LocalFile -> source.path
                    is PlaybackSource.RemoteStream -> source.url
                },
                "replace",
            ) + perFileOptions,
        )
    }

    fun dispatch(command: PlaybackCommand): DesktopMpvCommand =
        when (command) {
            PlaybackCommand.Play -> DesktopMpvCommand(listOf("set", "pause", "no"))
            PlaybackCommand.Pause -> DesktopMpvCommand(listOf("set", "pause", "yes"))
            is PlaybackCommand.SeekTo -> DesktopMpvCommand(
                listOf(
                    "seek",
                    command.positionMs.toMpvSeconds(),
                    "absolute",
                ),
            )
            is PlaybackCommand.SetPlaybackRate -> DesktopMpvCommand(
                listOf(
                    "set",
                    "speed",
                    command.rate.toStableMpvFloat(),
                ),
            )
            is PlaybackCommand.SetVolume -> DesktopMpvCommand(
                listOf(
                    "set",
                    "volume",
                    command.volumePercent.toString(),
                ),
            )
            is PlaybackCommand.SelectAudioTrack -> DesktopMpvCommand(
                listOf("set", "aid", command.trackId.toMpvTrackId()),
            )
            is PlaybackCommand.SelectSubtitleTrack -> DesktopMpvCommand(
                listOf("set", "sid", command.trackId?.toMpvTrackId() ?: "no"),
            )
        }

    fun setFullscreen(enabled: Boolean): DesktopMpvCommand =
        DesktopMpvCommand(listOf("set", "fullscreen", enabled.toMpvBoolean()))

    fun setVideoAspectMode(mode: DesktopVideoAspectMode): DesktopMpvCommand =
        DesktopMpvCommand(listOf("set", "video-aspect-override", mode.mpvValue))
}

private fun Long.toMpvSeconds(): String =
    (this / 1_000.0).toStableMpvFloat()

private fun Double.toStableMpvFloat(): String =
    String.format(Locale.US, "%.3f", this).trimTrailingZeroFraction()

private fun Float.toStableMpvFloat(): String =
    toDouble().toStableMpvFloat()

private fun String.trimTrailingZeroFraction(): String =
    trimEnd('0').trimEnd('.')

private fun String.toMpvTrackId(): String =
    substringAfterLast(":", this)

private fun Boolean.toMpvBoolean(): String =
    if (this) "yes" else "no"
