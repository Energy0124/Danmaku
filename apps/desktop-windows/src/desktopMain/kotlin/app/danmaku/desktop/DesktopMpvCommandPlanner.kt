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

object DesktopMpvCommandPlanner {
    fun load(source: PlaybackSource): DesktopMpvCommand =
        DesktopMpvCommand(
            listOf(
                "loadfile",
                when (source) {
                    is PlaybackSource.LocalFile -> source.path
                    is PlaybackSource.RemoteStream -> source.url
                },
                "replace",
            ),
        )

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
        }
}

private fun Long.toMpvSeconds(): String =
    (this / 1_000.0).toStableMpvFloat()

private fun Double.toStableMpvFloat(): String =
    String.format(Locale.US, "%.3f", this).trimTrailingZeroFraction()

private fun Float.toStableMpvFloat(): String =
    toDouble().toStableMpvFloat()

private fun String.trimTrailingZeroFraction(): String =
    trimEnd('0').trimEnd('.')
