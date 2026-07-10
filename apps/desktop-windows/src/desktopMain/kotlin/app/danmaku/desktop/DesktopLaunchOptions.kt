package app.danmaku.desktop

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class DesktopLaunchOptions(
    val smokePlayback: DesktopSmokePlaybackOptions? = null,
    val initialLanguage: DesktopUiLanguage? = null,
    val initialTab: DesktopShellTab? = null,
    val serverPort: Int? = null,
    val serverPairingToken: String? = null,
    val webAssetsRoot: Path? = null,
    val qaLibraryRoot: Path? = null,
    val remoteClient: DesktopRemoteClientOptions? = null,
    val rustSidecar: DesktopRustSidecarOptions = DesktopRustSidecarOptions(),
    val qaSidecarAutoplayFirst: Boolean = false,
    val qaScreenshot: DesktopQaScreenshotOptions? = null,
) {
    companion object {
        const val SMOKE_PLAYBACK_MEDIA_ENV = "DANMAKU_SMOKE_PLAYBACK_MEDIA"
        const val SMOKE_PLAYBACK_SECONDS_ENV = "DANMAKU_SMOKE_PLAYBACK_SECONDS"
        const val WEB_UI_DIST_ENV = "DANMAKU_WEB_UI_DIST"
        const val SERVER_PAIRING_TOKEN_ENV = "DANMAKU_SERVER_PAIRING_TOKEN"
        const val QA_LIBRARY_ROOT_ENV = "DANMAKU_QA_LIBRARY_ROOT"
        const val REMOTE_SERVER_URL_ENV = "DANMAKU_REMOTE_SERVER_URL"
        const val REMOTE_PAIRING_TOKEN_ENV = "DANMAKU_REMOTE_PAIRING_TOKEN"
        const val RUST_SIDECAR_ENV = "DANMAKU_RUST_SIDECAR"
        const val RUST_SERVER_PATH_ENV = "DANMAKU_RUST_SERVER_PATH"

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
            var serverPairingToken = environment[SERVER_PAIRING_TOKEN_ENV]
                ?.takeIf(String::isNotBlank)
            var webAssetsRoot = environment[WEB_UI_DIST_ENV]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
            var qaLibraryRoot = environment[QA_LIBRARY_ROOT_ENV]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
            var remoteClientRequested = false
            var remoteServerUrl = environment[REMOTE_SERVER_URL_ENV]
                ?.takeIf(String::isNotBlank)
                ?.also { remoteClientRequested = true }
            var remotePairingToken = environment[REMOTE_PAIRING_TOKEN_ENV]
                ?.takeIf(String::isNotBlank)
                ?.also { remoteClientRequested = true }
            var remoteAutoLoad = true
            var rustSidecarEnabled = environment[RUST_SIDECAR_ENV].toBooleanLaunchFlagOrNull() ?: true
            var rustServerPath = environment[RUST_SERVER_PATH_ENV]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
            var qaSidecarAutoplayFirst = false
            var qaScreenshotDirectory: Path? = null
            var qaScreenshotName: String? = null
            var qaScreenshotDelay = DEFAULT_QA_SCREENSHOT_DELAY
            var qaScreenshotAutoExit = true

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
                    arg == "--server-pairing-token" -> {
                        serverPairingToken = args.valueAfter(arg, index)
                        index += 1
                    }
                    arg.startsWith("--server-pairing-token=") -> {
                        serverPairingToken = arg.substringAfter("=").takeIf(String::isNotBlank)
                            ?: error("--server-pairing-token requires a value")
                    }
                    arg == "--web-assets-dir" || arg == "--web-ui-dist" -> {
                        webAssetsRoot = args.valueAfter(arg, index)?.let(Path::of)
                        index += 1
                    }
                    arg.startsWith("--web-assets-dir=") -> {
                        webAssetsRoot = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                    }
                    arg.startsWith("--web-ui-dist=") -> {
                        webAssetsRoot = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                    }
                    arg == "--qa-library-root" -> {
                        qaLibraryRoot = args.valueAfter(arg, index)?.let(Path::of)
                        index += 1
                    }
                    arg.startsWith("--qa-library-root=") -> {
                        qaLibraryRoot = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                    }
                    arg == "--remote-client" -> {
                        remoteClientRequested = true
                    }
                    arg == "--remote-server-url" || arg == "--remote-url" || arg == "--paired-server-url" -> {
                        remoteServerUrl = args.valueAfter(arg, index)
                        remoteClientRequested = true
                        index += 1
                    }
                    arg.startsWith("--remote-server-url=") -> {
                        remoteServerUrl = arg.substringAfter("=").takeIf(String::isNotBlank)
                        remoteClientRequested = true
                    }
                    arg.startsWith("--remote-url=") -> {
                        remoteServerUrl = arg.substringAfter("=").takeIf(String::isNotBlank)
                        remoteClientRequested = true
                    }
                    arg.startsWith("--paired-server-url=") -> {
                        remoteServerUrl = arg.substringAfter("=").takeIf(String::isNotBlank)
                        remoteClientRequested = true
                    }
                    arg == "--remote-pairing-token" || arg == "--remote-token" || arg == "--paired-token" -> {
                        remotePairingToken = args.valueAfter(arg, index)
                        remoteClientRequested = true
                        index += 1
                    }
                    arg.startsWith("--remote-pairing-token=") -> {
                        remotePairingToken = arg.substringAfter("=").takeIf(String::isNotBlank)
                        remoteClientRequested = true
                    }
                    arg.startsWith("--remote-token=") -> {
                        remotePairingToken = arg.substringAfter("=").takeIf(String::isNotBlank)
                        remoteClientRequested = true
                    }
                    arg.startsWith("--paired-token=") -> {
                        remotePairingToken = arg.substringAfter("=").takeIf(String::isNotBlank)
                        remoteClientRequested = true
                    }
                    arg == "--remote-auto-load" -> {
                        remoteAutoLoad = true
                    }
                    arg == "--remote-no-auto-load" -> {
                        remoteAutoLoad = false
                    }
                    arg == "--rust-sidecar" -> {
                        rustSidecarEnabled = true
                    }
                    arg == "--no-rust-sidecar" -> {
                        rustSidecarEnabled = false
                    }
                    arg == "--rust-server-path" -> {
                        rustServerPath = args.valueAfter(arg, index)?.let(Path::of)
                        index += 1
                    }
                    arg.startsWith("--rust-server-path=") -> {
                        rustServerPath = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                            ?: error("--rust-server-path requires a value")
                    }
                    arg == "--qa-sidecar-autoplay-first" -> {
                        qaSidecarAutoplayFirst = true
                    }
                    arg == "--qa-screenshot-dir" -> {
                        qaScreenshotDirectory = args.valueAfter(arg, index)?.let(Path::of)
                        index += 1
                    }
                    arg.startsWith("--qa-screenshot-dir=") -> {
                        qaScreenshotDirectory = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                    }
                    arg == "--qa-screenshot-name" -> {
                        qaScreenshotName = args.valueAfter(arg, index)
                        index += 1
                    }
                    arg.startsWith("--qa-screenshot-name=") -> {
                        qaScreenshotName = arg.substringAfter("=").takeIf(String::isNotBlank)
                    }
                    arg == "--qa-screenshot-delay-seconds" -> {
                        qaScreenshotDelay = args.valueAfter(arg, index)
                            ?.toQaScreenshotDelay()
                            ?: qaScreenshotDelay
                        index += 1
                    }
                    arg.startsWith("--qa-screenshot-delay-seconds=") -> {
                        qaScreenshotDelay = arg.substringAfter("=")
                            .toQaScreenshotDelay()
                            ?: qaScreenshotDelay
                    }
                    arg == "--qa-screenshot-keep-open" -> {
                        qaScreenshotAutoExit = false
                    }
                    arg == "--qa-screenshot-exit" -> {
                        qaScreenshotAutoExit = true
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

            val hasRemoteClient = remoteServerUrl != null
            if (remoteClientRequested && !hasRemoteClient) {
                error("Remote client options require --remote-server-url or $REMOTE_SERVER_URL_ENV")
            }
            if (!rustSidecarEnabled && !hasRemoteClient) {
                error("--no-rust-sidecar is only valid with --remote-server-url")
            }
            if (qaSidecarAutoplayFirst && hasRemoteClient) {
                error("--qa-sidecar-autoplay-first cannot be used with --remote-server-url")
            }
            val startRustSidecar = !hasRemoteClient

            return DesktopLaunchOptions(
                smokePlayback = smokeMediaPath?.let { mediaPath ->
                    DesktopSmokePlaybackOptions(
                        mediaPath = mediaPath,
                        playbackDuration = smokePlaybackDuration,
                        autoExit = smokeAutoExit,
                    )
                },
                initialLanguage = initialLanguage,
                initialTab = initialTab ?: DesktopShellTab.MEDIA_LIBRARY,
                serverPort = serverPort,
                serverPairingToken = serverPairingToken,
                webAssetsRoot = webAssetsRoot,
                qaLibraryRoot = qaLibraryRoot,
                remoteClient = remoteServerUrl?.let { serverUrl ->
                    DesktopRemoteClientOptions(
                        serverUrl = serverUrl,
                        pairingToken = remotePairingToken.orEmpty(),
                        autoLoad = remoteAutoLoad,
                    )
                },
                rustSidecar = DesktopRustSidecarOptions(
                    enabled = startRustSidecar,
                    serverPath = rustServerPath,
                ),
                qaSidecarAutoplayFirst = qaSidecarAutoplayFirst,
                qaScreenshot = qaScreenshotDirectory?.let { outputDirectory ->
                    DesktopQaScreenshotOptions(
                        outputDirectory = outputDirectory,
                        fileName = qaScreenshotName,
                        delay = qaScreenshotDelay,
                        autoExit = qaScreenshotAutoExit,
                    )
                },
            )
        }

        private fun List<String>.valueAfter(option: String, index: Int): String? =
            getOrNull(index + 1)?.takeUnless { it.startsWith("--") }?.takeIf(String::isNotBlank)
                ?: error("$option requires a value")

        private fun String.toSmokeDuration(): Duration? =
            toLongOrNull()
                ?.coerceIn(MIN_SMOKE_PLAYBACK_SECONDS, MAX_SMOKE_PLAYBACK_SECONDS)
                ?.seconds

        private fun String.toQaScreenshotDelay(): Duration? =
            toLongOrNull()
                ?.coerceIn(MIN_QA_SCREENSHOT_DELAY_SECONDS, MAX_QA_SCREENSHOT_DELAY_SECONDS)
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

        private fun String?.toBooleanLaunchFlagOrNull(): Boolean? =
            when (this?.trim()?.lowercase()) {
                "1", "true", "yes", "y", "on" -> true
                "0", "false", "no", "n", "off" -> false
                null, "" -> null
                else -> error("$RUST_SIDECAR_ENV must be a boolean value")
            }
    }
}

internal data class DesktopRustSidecarOptions(
    val enabled: Boolean = true,
    val serverPath: Path? = null,
)

internal data class DesktopRemoteClientOptions(
    val serverUrl: String,
    val pairingToken: String = "",
    val autoLoad: Boolean = false,
) {
    init {
        require(serverUrl.isNotBlank()) { "serverUrl must not be blank" }
    }

    val normalizedServerUrl: String =
        serverUrl.trim().trimEnd('/')
}

internal data class DesktopSmokePlaybackOptions(
    val mediaPath: Path,
    val playbackDuration: Duration = DEFAULT_SMOKE_PLAYBACK_DURATION,
    val autoExit: Boolean = false,
)

internal data class DesktopQaScreenshotOptions(
    val outputDirectory: Path,
    val fileName: String? = null,
    val delay: Duration = DEFAULT_QA_SCREENSHOT_DELAY,
    val autoExit: Boolean = true,
)

private const val MIN_SMOKE_PLAYBACK_SECONDS = 1L
private const val MAX_SMOKE_PLAYBACK_SECONDS = 60L
private const val MIN_QA_SCREENSHOT_DELAY_SECONDS = 0L
private const val MAX_QA_SCREENSHOT_DELAY_SECONDS = 60L

val DEFAULT_SMOKE_PLAYBACK_DURATION: Duration = 6.seconds
val DEFAULT_QA_SCREENSHOT_DELAY: Duration = 4.seconds
