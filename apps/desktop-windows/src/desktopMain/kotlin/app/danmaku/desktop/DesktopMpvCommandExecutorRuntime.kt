package app.danmaku.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

enum class DesktopMpvCommandExecutorMode {
    NATIVE,
    COMMAND_LOG_ONLY,
}

class DesktopMpvCommandExecutorRuntime(
    val executor: DesktopMpvCommandExecutor,
    val mode: DesktopMpvCommandExecutorMode,
    val statusMessage: String,
    private val closeAction: () -> Unit = {},
) : AutoCloseable {
    override fun close() {
        closeAction()
    }
}

class DesktopMpvCommandExecutorRuntimeFactory(
    private val environment: Map<String, String> = System.getenv(),
    private val librarySearchPaths: List<Path> = defaultLibrarySearchPaths(),
    private val isRegularFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val isDirectory: (Path) -> Boolean = { Files.isDirectory(it) },
    private val loadNativeExecutor: (Path, Path) -> CloseableDesktopMpvCommandExecutor =
        DesktopMpvNativeCommandExecutor::load,
) {
    fun create(commandObserver: (DesktopMpvCommand) -> Unit): DesktopMpvCommandExecutorRuntime {
        val dependencies = runCatching { resolveDependencies() }
            .getOrElse { error ->
                return fallback(commandObserver, "Native mpv dependency lookup failed: ${error.message}")
            }
            ?: return fallback(
                commandObserver,
                "Native mpv dependencies are missing. Expected $BRIDGE_DLL_NAME and $LIBMPV_DLL_NAME.",
            )

        return runCatching {
            val nativeExecutor = loadNativeExecutor(
                dependencies.bridgePath,
                dependencies.libmpvPath,
            )
            DesktopMpvCommandExecutorRuntime(
                executor = observingExecutor(nativeExecutor, commandObserver),
                mode = DesktopMpvCommandExecutorMode.NATIVE,
                statusMessage = "Native libmpv executor active.",
                closeAction = nativeExecutor::close,
            )
        }.getOrElse { error ->
            fallback(
                commandObserver,
                "Native libmpv executor could not start: ${error.message ?: error::class.simpleName}",
            )
        }
    }

    private fun resolveDependencies(): DesktopMpvNativeDependencyPaths? {
        val bridgePath = resolveDependency(BRIDGE_PATH_ENV, BRIDGE_DLL_NAME) ?: return null
        val libmpvPath = resolveDependency(LIBMPV_PATH_ENV, LIBMPV_DLL_NAME) ?: return null
        return DesktopMpvNativeDependencyPaths(bridgePath, libmpvPath)
    }

    private fun resolveDependency(
        environmentVariable: String,
        fileName: String,
    ): Path? {
        environment[environmentVariable]
            ?.takeIf(String::isNotBlank)
            ?.let(::normalizedPath)
            ?.let { path -> if (isDirectory(path)) path.resolve(fileName) else path }
            ?.takeIf(isRegularFile)
            ?.let { return it }

        return librarySearchPaths
            .asSequence()
            .map { it.resolve(fileName).toAbsolutePath().normalize() }
            .firstOrNull(isRegularFile)
    }

    private fun fallback(
        commandObserver: (DesktopMpvCommand) -> Unit,
        statusMessage: String,
    ): DesktopMpvCommandExecutorRuntime =
        DesktopMpvCommandExecutorRuntime(
            executor = DesktopMpvCommandExecutor(commandObserver),
            mode = DesktopMpvCommandExecutorMode.COMMAND_LOG_ONLY,
            statusMessage = "$statusMessage Command-log-only mode is active.",
        )

    private fun observingExecutor(
        delegate: DesktopMpvCommandExecutor,
        commandObserver: (DesktopMpvCommand) -> Unit,
    ): DesktopMpvCommandExecutor =
        DesktopMpvCommandExecutor { command ->
            commandObserver(command)
            delegate.execute(command)
        }

    private fun normalizedPath(value: String): Path =
        Path.of(value).toAbsolutePath().normalize()

    companion object {
        const val BRIDGE_PATH_ENV = "DANMAKU_MPV_BRIDGE_PATH"
        const val LIBMPV_PATH_ENV = "DANMAKU_LIBMPV_PATH"
        const val BRIDGE_DLL_NAME = "player_windows_mpv.dll"
        const val LIBMPV_DLL_NAME = "libmpv-2.dll"

        private fun defaultLibrarySearchPaths(): List<Path> =
            System.getProperty("java.library.path")
                .orEmpty()
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map(Path::of)
    }
}

private data class DesktopMpvNativeDependencyPaths(
    val bridgePath: Path,
    val libmpvPath: Path,
)
