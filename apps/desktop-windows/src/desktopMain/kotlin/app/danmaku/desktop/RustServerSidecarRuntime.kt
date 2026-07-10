package app.danmaku.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import kotlin.concurrent.thread

internal class RustServerSidecarRuntime private constructor(
    val baseUrl: String,
    val pairingToken: String,
    val port: Int,
    val dataDirectory: Path,
    val executablePath: Path,
    val logPath: Path,
    private val processFactory: RustServerProcessFactory,
    private val readinessPoller: RustServerReadinessPoller,
    private val restartPolicy: RustSidecarRestartBackoffPolicy,
    private val onStatusChanged: (RustServerSidecarStatus) -> Unit,
    private var process: Process,
) : AutoCloseable {
    @Volatile
    private var closed = false

    @Volatile
    private var currentPairingToken = pairingToken

    private val monitorThread = thread(
        start = true,
        isDaemon = true,
        name = "danmaku-rust-server-sidecar-monitor",
    ) {
        monitorProcess()
    }

    val remoteClientOptions: DesktopRemoteClientOptions
        get() = DesktopRemoteClientOptions(
            serverUrl = baseUrl,
            pairingToken = currentPairingToken,
            autoLoad = true,
        )

    fun localNetworkUrls(): List<String> =
        listOf(baseUrl)

    override fun close() {
        closed = true
        stopProcess(process)
        runCatching { monitorThread.join(SHUTDOWN_JOIN_TIMEOUT.toMillis()) }
    }

    private fun monitorProcess() {
        while (!closed) {
            val exitedProcess = process
            val exitCode = try {
                exitedProcess.waitFor()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            if (closed) return

            val delay = restartPolicy.recordCrashAndNextDelay(System.currentTimeMillis())
            if (delay == null) {
                onStatusChanged(
                    RustServerSidecarStatus.Failed(
                        "Rust sidecar exited with code $exitCode and restart limit was reached.",
                    ),
                )
                return
            }

            onStatusChanged(
                RustServerSidecarStatus.Restarting(
                    "Rust sidecar exited with code $exitCode; restarting in ${delay.toMillis()}ms.",
                ),
            )
            try {
                Thread.sleep(delay.toMillis())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            if (closed) return

            runCatching {
                val restarted = processFactory.start()
                process = restarted
                readinessPoller.awaitReady(baseUrl)
                currentPairingToken = readPairingToken(dataDirectory)
                onStatusChanged(RustServerSidecarStatus.Running("Rust sidecar restarted on $baseUrl."))
            }.onFailure { error ->
                onStatusChanged(
                    RustServerSidecarStatus.Restarting(
                        "Rust sidecar restart failed: ${error.message ?: error::class.simpleName}",
                    ),
                )
            }
        }
    }

    private fun stopProcess(process: Process) {
        if (!process.isAlive) return
        process.destroy()
        val exited = runCatching { process.waitFor(SHUTDOWN_GRACE.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS) }
            .getOrDefault(false)
        if (!exited && process.isAlive) {
            process.destroyForcibly()
            runCatching { process.waitFor(SHUTDOWN_JOIN_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS) }
        }
    }

    companion object {
        fun start(
            launchOptions: DesktopLaunchOptions,
            libraryRoots: List<Path>,
            environment: Map<String, String> = System.getenv(),
            repositoryRoot: Path = Path.of("").toAbsolutePath().normalize(),
            processFactoryBuilder: (RustServerSidecarLaunch) -> RustServerProcessFactory = { launch ->
                ProcessBuilderRustServerProcessFactory(launch)
            },
            readinessPoller: RustServerReadinessPoller = RustServerReadinessPoller(),
            restartPolicy: RustSidecarRestartBackoffPolicy = RustSidecarRestartBackoffPolicy(),
            onStatusChanged: (RustServerSidecarStatus) -> Unit = {},
        ): RustServerSidecarRuntime {
            val executable = RustServerBinaryResolver.resolve(
                launchOverride = launchOptions.rustSidecar.serverPath,
                environment = environment,
                repositoryRoot = repositoryRoot,
            )
            val dataDirectory = defaultDesktopAppDataDirectory(environment)
                .resolve("rust-server")
                .toAbsolutePath()
                .normalize()
            Files.createDirectories(dataDirectory)
            val port = RustSidecarPortSelector.choosePort(launchOptions.serverPort)
            val baseUrl = "http://127.0.0.1:$port"
            val logPath = RustSidecarLogFiles.prepareLogFile(dataDirectory)
            val launch = RustServerSidecarLaunch(
                executablePath = executable.path,
                dataDirectory = dataDirectory,
                port = port,
                libraryRoots = libraryRoots.distinctBy { it.toAbsolutePath().normalize().toString() },
                pairingToken = launchOptions.serverPairingToken,
                webAssetsRoot = launchOptions.webAssetsRoot,
                logPath = logPath,
            )
            val processFactory = processFactoryBuilder(launch)
            val process = processFactory.start()
            try {
                readinessPoller.awaitReady(baseUrl)
                val pairingToken = readPairingToken(dataDirectory)
                onStatusChanged(RustServerSidecarStatus.Running("Rust sidecar ready on $baseUrl."))
                return RustServerSidecarRuntime(
                    baseUrl = baseUrl,
                    pairingToken = pairingToken,
                    port = port,
                    dataDirectory = dataDirectory,
                    executablePath = executable.path,
                    logPath = logPath,
                    processFactory = processFactory,
                    readinessPoller = readinessPoller,
                    restartPolicy = restartPolicy,
                    onStatusChanged = onStatusChanged,
                    process = process,
                )
            } catch (error: Throwable) {
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                throw RustServerSidecarException(
                    reason = RustServerSidecarFailureReason.READINESS_TIMEOUT,
                    detail = "Rust sidecar did not become ready at $baseUrl: ${error.message ?: error::class.simpleName}",
                    cause = error,
                )
            }
        }

        fun readPairingToken(dataDirectory: Path): String {
            val settingsPath = dataDirectory.resolve("server-settings.json")
            val body = try {
                Files.readString(settingsPath, StandardCharsets.UTF_8)
            } catch (error: IOException) {
                throw RustServerSidecarException(
                    reason = RustServerSidecarFailureReason.SETTINGS_READ_FAILED,
                    detail = "Rust sidecar settings file is unavailable: $settingsPath",
                    cause = error,
                )
            }
            val token = runCatching {
                Json.parseToJsonElement(body)
                    .jsonObject["pairingToken"]
                    ?.jsonPrimitive
                    ?.content
                    ?.takeIf(String::isNotBlank)
            }.getOrElse { error ->
                throw RustServerSidecarException(
                    reason = RustServerSidecarFailureReason.SETTINGS_READ_FAILED,
                    detail = "Rust sidecar settings file is malformed: $settingsPath",
                    cause = error,
                )
            }
            return token ?: throw RustServerSidecarException(
                reason = RustServerSidecarFailureReason.SETTINGS_READ_FAILED,
                detail = "Rust sidecar settings file does not contain a non-blank pairingToken: $settingsPath",
            )
        }
    }
}

internal data class RustServerSidecarLaunch(
    val executablePath: Path,
    val dataDirectory: Path,
    val port: Int,
    val libraryRoots: List<Path>,
    val pairingToken: String?,
    val webAssetsRoot: Path?,
    val logPath: Path,
) {
    fun command(): List<String> =
        buildList {
            add(executablePath.toAbsolutePath().normalize().toString())
            add("--data-dir")
            add(dataDirectory.toAbsolutePath().normalize().toString())
            add("--port")
            add(port.toString())
            pairingToken?.takeIf(String::isNotBlank)?.let {
                add("--pairing-token")
                add(it)
            }
            webAssetsRoot?.let {
                add("--web-assets-dir")
                add(it.toAbsolutePath().normalize().toString())
            }
            libraryRoots.forEach { root ->
                add("--root")
                add(root.toAbsolutePath().normalize().toString())
            }
        }
}

internal fun interface RustServerProcessFactory {
    fun start(): Process
}

private class ProcessBuilderRustServerProcessFactory(
    private val launch: RustServerSidecarLaunch,
) : RustServerProcessFactory {
    override fun start(): Process =
        ProcessBuilder(launch.command())
            .directory(launch.dataDirectory.toFile())
            .redirectOutput(ProcessBuilder.Redirect.appendTo(launch.logPath.toFile()))
            .redirectError(ProcessBuilder.Redirect.appendTo(launch.logPath.toFile()))
            .start()
}

internal object RustServerBinaryResolver {
    fun resolve(
        launchOverride: Path?,
        environment: Map<String, String>,
        repositoryRoot: Path,
    ): RustServerBinaryResolution {
        val envOverride = environment[DesktopLaunchOptions.RUST_SERVER_PATH_ENV]
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
        val override = launchOverride ?: envOverride
        if (override != null) {
            return requireExecutable(
                path = override,
                source = RustServerBinaryResolutionSource.OVERRIDE,
                searched = listOf(override),
            )
        }

        val executableName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            "library-server.exe"
        } else {
            "library-server"
        }
        val candidates = buildList {
            environment["CARGO_TARGET_DIR"]
                ?.takeIf(String::isNotBlank)
                ?.let { add(Path.of(it).resolve("release").resolve(executableName)) }
            add(repositoryRoot.resolve("target").resolve("release").resolve(executableName))
            add(repositoryRoot.resolve("native").resolve("library-server").resolve("target").resolve("release").resolve(executableName))
        }
        candidates.firstOrNull(Files::isRegularFile)?.let { path ->
            return RustServerBinaryResolution(
                path = path.toAbsolutePath().normalize(),
                source = RustServerBinaryResolutionSource.CARGO_TARGET,
                searchedPaths = candidates,
            )
        }
        throw RustServerSidecarException(
            reason = RustServerSidecarFailureReason.BINARY_NOT_FOUND,
            detail = "Rust library-server binary was not found. Set ${DesktopLaunchOptions.RUST_SERVER_PATH_ENV} or --rust-server-path, or run `cargo build --release -p library-server`. Searched: ${candidates.joinToString()}",
        )
    }

    private fun requireExecutable(
        path: Path,
        source: RustServerBinaryResolutionSource,
        searched: List<Path>,
    ): RustServerBinaryResolution {
        if (!Files.isRegularFile(path)) {
            throw RustServerSidecarException(
                reason = RustServerSidecarFailureReason.BINARY_NOT_FOUND,
                detail = "Rust library-server binary does not exist: $path",
            )
        }
        return RustServerBinaryResolution(
            path = path.toAbsolutePath().normalize(),
            source = source,
            searchedPaths = searched,
        )
    }
}

internal data class RustServerBinaryResolution(
    val path: Path,
    val source: RustServerBinaryResolutionSource,
    val searchedPaths: List<Path>,
)

internal enum class RustServerBinaryResolutionSource {
    OVERRIDE,
    CARGO_TARGET,
}

internal object RustSidecarPortSelector {
    fun choosePort(explicitPort: Int?): Int {
        val requestedPort = explicitPort?.takeIf { it != 0 }
        if (requestedPort != null) {
            require(requestedPort in 1..65_535) { "Rust sidecar port must be between 1 and 65535: $requestedPort" }
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                try {
                    socket.bind(java.net.InetSocketAddress("127.0.0.1", requestedPort))
                } catch (error: IOException) {
                    throw RustServerSidecarException(
                        reason = RustServerSidecarFailureReason.PORT_UNAVAILABLE,
                        detail = "Rust sidecar port $requestedPort is unavailable.",
                        cause = error,
                    )
                }
            }
            return requestedPort
        }
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { socket ->
            return socket.localPort
        }
    }
}

internal class RustServerReadinessPoller(
    // The server scans configured roots before binding HTTP (matching the
    // JVM headless host), and a real multi-thousand-item library takes tens
    // of seconds on first boot, so readiness must be patient.
    private val timeout: Duration = Duration.ofSeconds(180),
    private val interval: Duration = Duration.ofMillis(250),
    private val connectionFactory: (URI) -> HttpURLConnection = { uri ->
        uri.toURL().openConnection() as HttpURLConnection
    },
) {
    fun awaitReady(baseUrl: String) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastFailure: Throwable? = null
        while (System.nanoTime() < deadline) {
            runCatching {
                val connection = connectionFactory(URI("$baseUrl/api/server/status"))
                connection.connectTimeout = interval.toMillis().coerceAtLeast(1).toInt()
                connection.readTimeout = interval.toMillis().coerceAtLeast(1).toInt()
                connection.requestMethod = "GET"
                try {
                    if (connection.responseCode == 200) {
                        connection.inputStream.close()
                        return
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { error ->
                lastFailure = error
            }
            Thread.sleep(interval.toMillis())
        }
        throw RustServerSidecarException(
            reason = RustServerSidecarFailureReason.READINESS_TIMEOUT,
            detail = "Timed out waiting for Rust sidecar readiness at $baseUrl.",
            cause = lastFailure,
        )
    }
}

internal class RustSidecarRestartBackoffPolicy(
    private val maxRestarts: Int = 3,
    private val windowMillis: Long = Duration.ofMinutes(5).toMillis(),
    private val baseDelay: Duration = Duration.ofMillis(250),
) {
    private val crashTimes = ArrayDeque<Long>()

    @Synchronized
    fun recordCrashAndNextDelay(nowEpochMs: Long): Duration? {
        while (crashTimes.isNotEmpty() && nowEpochMs - crashTimes.first() > windowMillis) {
            crashTimes.removeFirst()
        }
        if (crashTimes.size >= maxRestarts) {
            return null
        }
        crashTimes.addLast(nowEpochMs)
        return baseDelay.multipliedBy(1L shl (crashTimes.size - 1))
    }
}

internal object RustSidecarLogFiles {
    fun prepareLogFile(dataDirectory: Path): Path {
        Files.createDirectories(dataDirectory)
        val logPath = dataDirectory.resolve("sidecar.log")
        for (index in MAX_LOG_ROTATIONS downTo 1) {
            val from = if (index == 1) logPath else dataDirectory.resolve("sidecar.log.${index - 1}")
            val to = dataDirectory.resolve("sidecar.log.$index")
            if (Files.exists(from)) {
                Files.deleteIfExists(to)
                Files.move(from, to)
            }
        }
        Files.writeString(
            logPath,
            "Danmaku Rust sidecar log${System.lineSeparator()}",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        return logPath
    }

    private const val MAX_LOG_ROTATIONS = 3
}

internal sealed interface RustServerSidecarStatus {
    val message: String

    data class Running(override val message: String) : RustServerSidecarStatus
    data class Restarting(override val message: String) : RustServerSidecarStatus
    data class Failed(override val message: String) : RustServerSidecarStatus
}

internal class RustServerSidecarException(
    val reason: RustServerSidecarFailureReason,
    val detail: String,
    cause: Throwable? = null,
) : RuntimeException(detail, cause)

internal enum class RustServerSidecarFailureReason {
    BINARY_NOT_FOUND,
    PORT_UNAVAILABLE,
    READINESS_TIMEOUT,
    SETTINGS_READ_FAILED,
}

private fun defaultDesktopAppDataDirectory(environment: Map<String, String>): Path {
    environment["LOCALAPPDATA"]
        ?.takeIf(String::isNotBlank)
        ?.let { return Path.of(it).resolve("Danmaku") }
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Danmaku")
    }
    return Path.of(System.getProperty("user.home"), ".danmaku")
}

private val SHUTDOWN_GRACE: Duration = Duration.ofSeconds(2)
private val SHUTDOWN_JOIN_TIMEOUT: Duration = Duration.ofSeconds(5)
