package app.danmaku.desktop

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class DesktopLaunchOptions(
    val smokePlayback: DesktopSmokePlaybackOptions? = null,
    val initialLanguage: DesktopUiLanguage? = null,
    val initialTab: DesktopShellTab? = null,
    val serverPort: Int? = null,
) {
    companion object {
        const val SMOKE_PLAYBACK_MEDIA_ENV = "DANMAKU_SMOKE_PLAYBACK_MEDIA"
        const val SMOKE_PLAYBACK_SECONDS_ENV = "DANMAKU_SMOKE_PLAYBACK_SECONDS"

        fun parse(args: Array<String>): DesktopLaunchOptions =
            parse(args.asList(), System.getenv())

        fun parse(
            args: List<String>,
            environment: Map<String, String> = emptyMap(),
        ): DesktopLaunchOptions {
            var smokeMediaPath = environment[SMOKE_PLAYBACK_MEDIA_ENV]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
            var smokePlaybackDuration = environment[SMOKE_PLAYBACK_SECONDS_ENV]
                ?.toSmokeDuration()
                ?: DEFAULT_SMOKE_PLAYBACK_DURATION
            var smokeAutoExit = true
            var initialLanguage: DesktopUiLanguage? = null
            var initialTab: DesktopShellTab? = null
            var serverPort: Int? = null

            var index = 0
            while (index < args.size) {
                val arg = args[index]
                when {
                    arg == "--ui-language" || arg == "--language" -> {
                        initialLanguage = args.valueAfter(arg, index)?.toDesktopUiLanguage()
                        index += 1
                    }
                    arg.startsWith("--ui-language=") -> {
                        initialLanguage = arg.substringAfter("=").toDesktopUiLanguage()
                    }
                    arg.startsWith("--language=") -> {
                        initialLanguage = arg.substringAfter("=").toDesktopUiLanguage()
                    }
                    arg == "--initial-tab" || arg == "--tab" -> {
                        initialTab = args.valueAfter(arg, index)?.toDesktopShellTab()
                        index += 1
                    }
                    arg.startsWith("--initial-tab=") -> {
                        initialTab = arg.substringAfter("=").toDesktopShellTab()
                    }
                    arg.startsWith("--tab=") -> {
                        initialTab = arg.substringAfter("=").toDesktopShellTab()
                    }
                    arg == "--server-port" -> {
                        serverPort = args.valueAfter(arg, index)?.toServerPort()
                        index += 1
                    }
                    arg.startsWith("--server-port=") -> {
                        serverPort = arg.substringAfter("=").toServerPort()
                    }
                    arg == "--smoke-playback-media" || arg == "--smoke-video" -> {
                        smokeMediaPath = args.valueAfter(arg, index)?.let(Path::of)
                        index += 1
                    }
                    arg.startsWith("--smoke-playback-media=") -> {
                        smokeMediaPath = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                    }
                    arg.startsWith("--smoke-video=") -> {
                        smokeMediaPath = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                    }
                    arg == "--smoke-playback-media-env" || arg == "--smoke-video-env" -> {
                        smokeMediaPath = environment[SMOKE_PLAYBACK_MEDIA_ENV]
                            ?.takeIf(String::isNotBlank)
                            ?.let(Path::of)
                            ?: error("$arg requires $SMOKE_PLAYBACK_MEDIA_ENV to be set")
                    }
                    arg == "--smoke-playback-seconds" -> {
                        smokePlaybackDuration = args.valueAfter(arg, index)
                            ?.toSmokeDuration()
                            ?: smokePlaybackDuration
                        index += 1
                    }
                    arg.startsWith("--smoke-playback-seconds=") -> {
                        smokePlaybackDuration = arg.substringAfter("=")
                            .toSmokeDuration()
                            ?: smokePlaybackDuration
                    }
                    arg == "--smoke-keep-open" -> {
                        smokeAutoExit = false
                    }
                    arg == "--smoke-exit" -> {
                        smokeAutoExit = true
                    }
                }
                index += 1
            }

            return DesktopLaunchOptions(
                smokePlayback = smokeMediaPath?.let { mediaPath ->
                    DesktopSmokePlaybackOptions(
                        mediaPath = mediaPath,
                        playbackDuration = smokePlaybackDuration,
                        autoExit = smokeAutoExit,
                    )
                },
                initialLanguage = initialLanguage,
                initialTab = initialTab,
                serverPort = serverPort,
            )
        }

        private fun List<String>.valueAfter(option: String, index: Int): String? =
            getOrNull(index + 1)?.takeUnless { it.startsWith("--") }?.takeIf(String::isNotBlank)
                ?: error("$option requires a value")

        private fun String.toSmokeDuration(): Duration? =
            toLongOrNull()
                ?.coerceIn(MIN_SMOKE_PLAYBACK_SECONDS, MAX_SMOKE_PLAYBACK_SECONDS)
                ?.seconds

        private fun String.toDesktopUiLanguage(): DesktopUiLanguage {
            val value = trim()
            return DesktopUiLanguage.entries.firstOrNull {
                it.storageValue.equals(value, ignoreCase = true) ||
                    it.name.equals(value.replace('-', '_'), ignoreCase = true)
            } ?: error("Unsupported desktop UI language: $this")
        }

        private fun String.toDesktopShellTab(): DesktopShellTab {
            val value = trim()
            val normalized = value.replace("-", "_").replace(" ", "_")
            return DesktopShellTab.entries.firstOrNull {
                it.name.equals(normalized, ignoreCase = true) ||
                    it.title.equals(value, ignoreCase = true) ||
                    (it == DesktopShellTab.MEDIA_LIBRARY && normalized.equals("library", ignoreCase = true)) ||
                    (it == DesktopShellTab.PROFILE && normalized.equals("settings", ignoreCase = true))
            } ?: error("Unsupported desktop shell tab: $this")
        }

        private fun String.toServerPort(): Int =
            toIntOrNull()
                ?.takeIf { it in 0..65_535 }
                ?: error("Server port must be between 0 and 65535: $this")
    }
}

internal data class DesktopSmokePlaybackOptions(
    val mediaPath: Path,
    val playbackDuration: Duration = DEFAULT_SMOKE_PLAYBACK_DURATION,
    val autoExit: Boolean = false,
)

private const val MIN_SMOKE_PLAYBACK_SECONDS = 1L
private const val MAX_SMOKE_PLAYBACK_SECONDS = 60L

val DEFAULT_SMOKE_PLAYBACK_DURATION: Duration = 6.seconds
