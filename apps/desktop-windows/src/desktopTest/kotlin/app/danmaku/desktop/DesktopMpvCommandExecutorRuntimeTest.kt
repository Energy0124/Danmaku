package app.danmaku.desktop

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMpvCommandExecutorRuntimeTest {
    @Test
    fun loadsNativeExecutorFromBundledLibrarySearchPath() {
        val temp = createTempDirectory("danmaku-mpv-runtime")
        val bridgePath = Files.createFile(
            temp.resolve(DesktopMpvCommandExecutorRuntimeFactory.BRIDGE_DLL_NAME),
        )
        val libmpvPath = Files.createFile(
            temp.resolve(DesktopMpvCommandExecutorRuntimeFactory.LIBMPV_DLL_NAME),
        )
        val nativeExecutor = RecordingCloseableExecutor()
        var loadedBridgePath = temp.resolve("missing")
        var loadedLibmpvPath = temp.resolve("missing")
        val commands = mutableListOf<DesktopMpvCommand>()

        val runtime = DesktopMpvCommandExecutorRuntimeFactory(
            platform = DesktopHostPlatform.WINDOWS,
            environment = emptyMap(),
            librarySearchPaths = listOf(temp),
            loadNativeExecutor = { bridge, libmpv, options ->
                loadedBridgePath = bridge
                loadedLibmpvPath = libmpv
                assertEquals(emptyMap(), options)
                nativeExecutor
            },
        ).create(commandObserver = commands::add)

        val command = DesktopMpvCommand(listOf("set", "pause", "no"))
        runtime.executor.execute(command)
        runtime.close()

        assertEquals(DesktopMpvCommandExecutorMode.NATIVE, runtime.mode)
        assertEquals(bridgePath, loadedBridgePath)
        assertEquals(libmpvPath, loadedLibmpvPath)
        assertEquals(listOf(command), commands)
        assertEquals(listOf(command), nativeExecutor.commands)
        assertTrue(nativeExecutor.closed)
        temp.toFile().deleteRecursively()
    }

    @Test
    fun resolvesDependencyDirectoriesFromEnvironment() {
        val temp = createTempDirectory("danmaku-mpv-runtime-env")
        val bridgePath = Files.createFile(
            temp.resolve(DesktopMpvCommandExecutorRuntimeFactory.BRIDGE_DLL_NAME),
        )
        val libmpvPath = Files.createFile(
            temp.resolve(DesktopMpvCommandExecutorRuntimeFactory.LIBMPV_DLL_NAME),
        )
        var loadedPaths = emptyList<java.nio.file.Path>()
        var loadedOptions = emptyMap<String, String>()

        val runtime = DesktopMpvCommandExecutorRuntimeFactory(
            platform = DesktopHostPlatform.WINDOWS,
            environment = mapOf(
                DesktopMpvCommandExecutorRuntimeFactory.BRIDGE_PATH_ENV to temp.toString(),
                DesktopMpvCommandExecutorRuntimeFactory.LIBMPV_PATH_ENV to temp.toString(),
            ),
            librarySearchPaths = emptyList(),
            loadNativeExecutor = { bridge, libmpv, options ->
                loadedPaths = listOf(bridge, libmpv)
                loadedOptions = options
                RecordingCloseableExecutor()
            },
        ).create(nativeOptions = mapOf("wid" to "1234")) {}

        assertEquals(DesktopMpvCommandExecutorMode.NATIVE, runtime.mode)
        assertEquals(listOf(bridgePath, libmpvPath), loadedPaths)
        assertEquals(mapOf("wid" to "1234"), loadedOptions)
        runtime.close()
        temp.toFile().deleteRecursively()
    }

    @Test
    fun includesRepositoryDevelopmentMpvRuntimeDirectories() {
        val temp = createTempDirectory("danmaku-mpv-runtime-repo")
        Files.createFile(temp.resolve("settings.gradle.kts"))
        Files.createDirectories(temp.resolve("native").resolve("player-windows-mpv"))
        val nestedAnchor = Files.createDirectories(
            temp.resolve("apps")
                .resolve("desktop-windows")
                .resolve("build")
                .resolve("classes"),
        )

        val paths = developmentRepositoryLibrarySearchPaths(listOf(nestedAnchor))

        assertEquals(
            listOf(
                temp.resolve("target").resolve("release"),
                temp.resolve("target").resolve("debug"),
                temp.resolve("runtime").resolve("windows").resolve("libmpv"),
            ),
            paths,
        )
        temp.toFile().deleteRecursively()
    }

    @Test
    fun includesPackagedApplicationSubdirectoryForMpvRuntimeDependencies() {
        val temp = createTempDirectory("danmaku-mpv-runtime-packaged")

        assertEquals(
            listOf(
                temp.toAbsolutePath().normalize(),
                temp.toAbsolutePath().normalize().resolve("app"),
            ),
            packagedApplicationLibrarySearchPaths(temp),
        )
        temp.toFile().deleteRecursively()
    }

    @Test
    fun fallsBackToCommandLogWhenNativeDependenciesAreMissing() {
        val commands = mutableListOf<DesktopMpvCommand>()
        var loaderCalled = false
        val runtime = DesktopMpvCommandExecutorRuntimeFactory(
            platform = DesktopHostPlatform.WINDOWS,
            environment = emptyMap(),
            librarySearchPaths = emptyList(),
            loadNativeExecutor = { _, _, _ ->
                loaderCalled = true
                RecordingCloseableExecutor()
            },
        ).create(commandObserver = commands::add)

        val command = DesktopMpvCommand(listOf("loadfile", "S:\\Anime\\Episode 01.mkv", "replace"))
        runtime.executor.execute(command)
        runtime.close()

        assertEquals(DesktopMpvCommandExecutorMode.COMMAND_LOG_ONLY, runtime.mode)
        assertContains(runtime.statusMessage, "Command-log-only mode")
        assertEquals(listOf(command), commands)
        assertFalse(loaderCalled)
    }

    @Test
    fun resolvesMacosNativeLibraryNames() {
        val temp = createTempDirectory("danmaku-mpv-runtime-macos")
        val bridgePath = Files.createFile(temp.resolve("libplayer_windows_mpv.dylib"))
        val libmpvPath = Files.createFile(temp.resolve("libmpv.2.dylib"))
        var loadedPaths = emptyList<java.nio.file.Path>()

        val runtime = DesktopMpvCommandExecutorRuntimeFactory(
            platform = DesktopHostPlatform.MACOS,
            environment = emptyMap(),
            librarySearchPaths = listOf(temp),
            loadNativeExecutor = { bridge, libmpv, _ ->
                loadedPaths = listOf(bridge, libmpv)
                RecordingCloseableExecutor()
            },
        ).create(commandObserver = {})

        assertEquals(DesktopMpvCommandExecutorMode.NATIVE, runtime.mode)
        assertEquals(listOf(bridgePath, libmpvPath), loadedPaths)
        runtime.close()
        temp.toFile().deleteRecursively()
    }

    private class RecordingCloseableExecutor : CloseableDesktopMpvCommandExecutor {
        val commands = mutableListOf<DesktopMpvCommand>()
        var closed = false

        override fun execute(command: DesktopMpvCommand) {
            commands += command
        }

        override fun close() {
            closed = true
        }
    }
}
