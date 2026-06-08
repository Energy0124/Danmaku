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
    val propertyReader: DesktopMpvPropertyReader? = null,
    val osdOverlayRenderer: DesktopMpvOsdOverlayRenderer? = null,
    val mode: DesktopMpvCommandExecutorMode,
    val statusMessage: String,
    private val closeAction: () -> Unit = {},
) : AutoCloseable {
    override fun close() {
        closeAction()
    }
}

class DesktopMpvCommandExecutorRuntimeFactory(
    private val platform: DesktopHostPlatform = DesktopHostPlatform.current(),
    private val environment: Map<String, String> = System.getenv(),
    private val librarySearchPaths: List<Path> = defaultLibrarySearchPaths(platform),
    private val isRegularFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    private val isDirectory: (Path) -> Boolean = { Files.isDirectory(it) },
    private val loadNativeExecutor: (Path, Path, Map<String, String>) -> CloseableDesktopMpvCommandExecutor =
        DesktopMpvNativeCommandExecutor::load,
) {
    fun create(
        nativeOptions: Map<String, String> = emptyMap(),
        commandObserver: (DesktopMpvCommand) -> Unit,
    ): DesktopMpvCommandExecutorRuntime {
        val dependencies = runCatching { resolveDependencies() }
            .getOrElse { error ->
                return fallback(commandObserver, "Native mpv dependency lookup failed: ${error.message}")
            }
            ?: return fallback(
                commandObserver,
                "Native mpv dependencies are missing for ${platform.displayName}. " +
                    "Expected ${platform.bridgeLibraryNames.joinToString(" or ")} and " +
                    platform.libmpvLibraryNames.joinToString(" or ") + ".",
            )

        return runCatching {
            val nativeExecutor = loadNativeExecutor(
                dependencies.bridgePath,
                dependencies.libmpvPath,
                nativeOptions,
            )
            DesktopMpvCommandExecutorRuntime(
                executor = observingExecutor(nativeExecutor, commandObserver),
                propertyReader = nativeExecutor as? DesktopMpvPropertyReader,
                osdOverlayRenderer = nativeExecutor as? DesktopMpvOsdOverlayRenderer,
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
        val bridgePath = resolveDependency(BRIDGE_PATH_ENV, platform.bridgeLibraryNames) ?: return null
        val libmpvPath = resolveDependency(LIBMPV_PATH_ENV, platform.libmpvLibraryNames) ?: return null
        return DesktopMpvNativeDependencyPaths(bridgePath, libmpvPath)
    }

    private fun resolveDependency(
        environmentVariable: String,
        fileNames: List<String>,
    ): Path? {
        environment[environmentVariable]
            ?.takeIf(String::isNotBlank)
            ?.let(::normalizedPath)
            ?.let { path ->
                if (isDirectory(path)) {
                    fileNames
                        .asSequence()
                        .map(path::resolve)
                        .firstOrNull(isRegularFile)
                } else {
                    path
                }
            }
            ?.takeIf(isRegularFile)
            ?.let { return it }

        return librarySearchPaths
            .asSequence()
            .flatMap { searchPath ->
                fileNames
                    .asSequence()
                    .map { fileName -> searchPath.resolve(fileName).toAbsolutePath().normalize() }
            }
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
        private const val COMPOSE_RESOURCES_DIR_PROPERTY = "compose.application.resources.dir"

        private fun defaultLibrarySearchPaths(platform: DesktopHostPlatform): List<Path> =
            buildList {
                addAll(
                    System.getProperty("java.library.path")
                        .orEmpty()
                        .split(File.pathSeparator)
                        .filter(String::isNotBlank)
                        .map(Path::of),
                )
                System.getProperty(COMPOSE_RESOURCES_DIR_PROPERTY)
                    ?.takeIf(String::isNotBlank)
                    ?.let(Path::of)
                    ?.parent
                    ?.let(::add)
                appCodeSourcePath()?.let { codeSource ->
                    add(if (Files.isRegularFile(codeSource)) codeSource.parent else codeSource)
                }
                addAll(platform.defaultMpvLibrarySearchPaths())
            }
                .map { it.toAbsolutePath().normalize() }
                .distinct()

        private fun appCodeSourcePath(): Path? =
            runCatching {
                DesktopMpvCommandExecutorRuntimeFactory::class.java
                    .protectionDomain
                    .codeSource
                    ?.location
                    ?.toURI()
                    ?.let(Path::of)
            }.getOrNull()
    }
}

private fun DesktopHostPlatform.defaultMpvLibrarySearchPaths(): List<Path> =
    when (this) {
        DesktopHostPlatform.MACOS -> listOf(
            Path.of("/opt/homebrew/lib"),
            Path.of("/usr/local/lib"),
        )
        DesktopHostPlatform.LINUX -> listOf(
            Path.of("/usr/local/lib"),
            Path.of("/usr/lib"),
        )
        else -> emptyList()
    }

private data class DesktopMpvNativeDependencyPaths(
    val bridgePath: Path,
    val libmpvPath: Path,
)
