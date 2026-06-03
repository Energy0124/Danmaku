package app.danmaku.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopMpvNativeCommandExecutorTest {
    @Test
    fun createsNativeHandleForwardsCommandsAndDestroysIt() {
        val nativeLibrary = RecordingDesktopMpvNativeLibrary()
        val executor = DesktopMpvNativeCommandExecutor.create(
            nativeLibrary = nativeLibrary,
            libmpvPath = Path.of("S:/runtime/windows/libmpv/libmpv-2.dll"),
        )

        executor.execute(
            DesktopMpvCommand(
                listOf(
                    "loadfile",
                    "S:\\Anime\\Example Show\\Episode 01.mkv",
                    "replace",
                ),
            ),
        )
        executor.close()

        assertTrue(nativeLibrary.createdLibmpvPath.endsWith("runtime\\windows\\libmpv\\libmpv-2.dll"))
        assertEquals(
            listOf(
                listOf(
                    "loadfile",
                    "S:\\Anime\\Example Show\\Episode 01.mkv",
                    "replace",
                ),
            ),
            nativeLibrary.commands,
        )
        assertEquals(nativeLibrary.handle, nativeLibrary.destroyedHandle)
    }

    @Test
    fun reportsNativeCreationFailures() {
        val nativeLibrary = RecordingDesktopMpvNativeLibrary(
            createStatus = DesktopMpvNativeStatus.LOAD_FAILED,
        )

        val error = assertFailsWith<DesktopMpvNativeException> {
            DesktopMpvNativeCommandExecutor.create(
                nativeLibrary = nativeLibrary,
                libmpvPath = Path.of("S:/runtime/windows/libmpv/libmpv-2.dll"),
            )
        }

        assertEquals(DesktopMpvNativeStatus.LOAD_FAILED, error.status)
    }

    @Test
    fun rejectsNullHandlesAfterSuccessfulNativeCreation() {
        val nativeLibrary = RecordingDesktopMpvNativeLibrary(handle = null)

        val error = assertFailsWith<DesktopMpvNativeException> {
            DesktopMpvNativeCommandExecutor.create(
                nativeLibrary = nativeLibrary,
                libmpvPath = Path.of("S:/runtime/windows/libmpv/libmpv-2.dll"),
            )
        }

        assertEquals(DesktopMpvNativeStatus.NULL_POINTER, error.status)
    }

    @Test
    fun reportsNativeCommandFailures() {
        val nativeLibrary = RecordingDesktopMpvNativeLibrary(
            commandStatus = DesktopMpvNativeStatus.COMMAND_FAILED,
        )
        val executor = DesktopMpvNativeCommandExecutor.create(
            nativeLibrary = nativeLibrary,
            libmpvPath = Path.of("S:/runtime/windows/libmpv/libmpv-2.dll"),
        )

        val error = assertFailsWith<DesktopMpvNativeException> {
            executor.execute(DesktopMpvCommand(listOf("set", "pause", "no")))
        }

        assertEquals(DesktopMpvNativeStatus.COMMAND_FAILED, error.status)
    }

    private class RecordingDesktopMpvNativeLibrary(
        val handle: Pointer? = Pointer.createConstant(0x1234),
        private val createStatus: DesktopMpvNativeStatus = DesktopMpvNativeStatus.OK,
        private val commandStatus: DesktopMpvNativeStatus = DesktopMpvNativeStatus.OK,
    ) : DesktopMpvNativeLibrary {
        var createdLibmpvPath: String = ""
        var destroyedHandle: Pointer? = null
        val commands = mutableListOf<List<String>>()

        override fun danmaku_mpv_create(
            libmpvPath: String,
            outHandle: PointerByReference,
        ): Int {
            createdLibmpvPath = libmpvPath
            outHandle.value = handle
            return createStatus.code
        }

        override fun danmaku_mpv_command(
            handle: Pointer,
            args: Pointer,
            argsLen: Long,
        ): Int {
            commands += (0 until argsLen).map { index ->
                args
                    .getPointer(index * Native.POINTER_SIZE.toLong())
                    .getString(0, Charsets.UTF_8.name())
            }
            return commandStatus.code
        }

        override fun danmaku_mpv_destroy(handle: Pointer?) {
            destroyedHandle = handle
        }
    }
}
