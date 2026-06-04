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
            environment = emptyMap(),
            librarySearchPaths = listOf(temp),
            loadNativeExecutor = { bridge, libmpv ->
                loadedBridgePath = bridge
                loadedLibmpvPath = libmpv
                nativeExecutor
            },
        ).create(commands::add)

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

        val runtime = DesktopMpvCommandExecutorRuntimeFactory(
            environment = mapOf(
                DesktopMpvCommandExecutorRuntimeFactory.BRIDGE_PATH_ENV to temp.toString(),
                DesktopMpvCommandExecutorRuntimeFactory.LIBMPV_PATH_ENV to temp.toString(),
            ),
            librarySearchPaths = emptyList(),
            loadNativeExecutor = { bridge, libmpv ->
                loadedPaths = listOf(bridge, libmpv)
                RecordingCloseableExecutor()
            },
        ).create {}

        assertEquals(DesktopMpvCommandExecutorMode.NATIVE, runtime.mode)
        assertEquals(listOf(bridgePath, libmpvPath), loadedPaths)
        runtime.close()
        temp.toFile().deleteRecursively()
    }

    @Test
    fun fallsBackToCommandLogWhenNativeDependenciesAreMissing() {
        val commands = mutableListOf<DesktopMpvCommand>()
        var loaderCalled = false
        val runtime = DesktopMpvCommandExecutorRuntimeFactory(
            environment = emptyMap(),
            librarySearchPaths = emptyList(),
            loadNativeExecutor = { _, _ ->
                loaderCalled = true
                RecordingCloseableExecutor()
            },
        ).create(commands::add)

        val command = DesktopMpvCommand(listOf("loadfile", "S:\\Anime\\Episode 01.mkv", "replace"))
        runtime.executor.execute(command)
        runtime.close()

        assertEquals(DesktopMpvCommandExecutorMode.COMMAND_LOG_ONLY, runtime.mode)
        assertContains(runtime.statusMessage, "Command-log-only mode")
        assertEquals(listOf(command), commands)
        assertFalse(loaderCalled)
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
