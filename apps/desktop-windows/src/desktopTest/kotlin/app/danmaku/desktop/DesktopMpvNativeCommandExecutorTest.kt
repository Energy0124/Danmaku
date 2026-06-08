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
        val libmpvPath = Path.of("S:/runtime/windows/libmpv/libmpv-2.dll")
        val executor = DesktopMpvNativeCommandExecutor.create(
            nativeLibrary = nativeLibrary,
            libmpvPath = libmpvPath,
            options = mapOf(
                "wid" to "4294967295",
                "hwdec" to "auto-safe",
            ),
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

        assertEquals(libmpvPath.toAbsolutePath().normalize().toString(), nativeLibrary.createdLibmpvPath)
        assertEquals(
            mapOf(
                "wid" to "4294967295",
                "hwdec" to "auto-safe",
            ),
            nativeLibrary.createdOptions,
        )
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
    fun readsNativeProperties() {
        val nativeLibrary = RecordingDesktopMpvNativeLibrary(
            properties = mapOf("time-pos" to "12.345"),
        )
        val executor = DesktopMpvNativeCommandExecutor.create(
            nativeLibrary = nativeLibrary,
            libmpvPath = Path.of("S:/runtime/windows/libmpv/libmpv-2.dll"),
        )

        assertEquals("12.345", executor.readProperty("time-pos"))
        assertEquals(null, executor.readProperty("duration"))
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
    fun reportsNativeOptionFailures() {
        val nativeLibrary = RecordingDesktopMpvNativeLibrary(
            createStatus = DesktopMpvNativeStatus.SET_OPTION_FAILED,
        )

        val error = assertFailsWith<DesktopMpvNativeException> {
            DesktopMpvNativeCommandExecutor.create(
                nativeLibrary = nativeLibrary,
                libmpvPath = Path.of("S:/runtime/windows/libmpv/libmpv-2.dll"),
                options = mapOf("wid" to "1234"),
            )
        }

        assertEquals(DesktopMpvNativeStatus.SET_OPTION_FAILED, error.status)
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

    @Test
    fun rendersOsdOverlaysThroughNativeBridge() {
        val nativeLibrary = RecordingDesktopMpvNativeLibrary()
        val executor = DesktopMpvNativeCommandExecutor.create(
            nativeLibrary = nativeLibrary,
            libmpvPath = Path.of("S:/runtime/windows/libmpv/libmpv-2.dll"),
        )

        executor.render(
            DesktopMpvOsdOverlay(
                id = 20,
                format = "ass-events",
                data = "{\\an2}controls",
                resX = 1920,
                resY = 1080,
                z = 100,
            ),
        )

        assertEquals(
            listOf(
                RecordingOsdOverlay(
                    id = 20,
                    format = "ass-events",
                    data = "{\\an2}controls",
                    resX = 1920,
                    resY = 1080,
                    z = 100,
                    hidden = 0,
                ),
            ),
            nativeLibrary.osdOverlays,
        )
    }

    private class RecordingDesktopMpvNativeLibrary(
        val handle: Pointer? = Pointer.createConstant(0x1234),
        private val createStatus: DesktopMpvNativeStatus = DesktopMpvNativeStatus.OK,
        private val commandStatus: DesktopMpvNativeStatus = DesktopMpvNativeStatus.OK,
        private val properties: Map<String, String> = emptyMap(),
    ) : DesktopMpvNativeLibrary {
        var createdLibmpvPath: String = ""
        var createdOptions: Map<String, String> = emptyMap()
        var destroyedHandle: Pointer? = null
        val commands = mutableListOf<List<String>>()
        val osdOverlays = mutableListOf<RecordingOsdOverlay>()

        override fun danmaku_mpv_create_with_options(
            libmpvPath: String,
            optionNames: Pointer?,
            optionValues: Pointer?,
            optionsLen: Long,
            outHandle: PointerByReference,
        ): Int {
            createdLibmpvPath = libmpvPath
            createdOptions = readStrings(optionNames, optionsLen)
                .zip(readStrings(optionValues, optionsLen))
                .toMap()
            outHandle.value = handle
            return createStatus.code
        }

        override fun danmaku_mpv_command(
            handle: Pointer,
            args: Pointer,
            argsLen: Long,
        ): Int {
            commands += readStrings(args, argsLen)
            return commandStatus.code
        }

        override fun danmaku_mpv_osd_overlay(
            handle: Pointer,
            id: Long,
            format: String,
            data: String,
            resX: Long,
            resY: Long,
            z: Long,
            hidden: Int,
        ): Int {
            osdOverlays += RecordingOsdOverlay(
                id = id,
                format = format,
                data = data,
                resX = resX,
                resY = resY,
                z = z,
                hidden = hidden,
            )
            return commandStatus.code
        }

        override fun danmaku_mpv_get_property_string(
            handle: Pointer,
            name: String,
            outValue: Pointer,
            outValueLen: Long,
        ): Int {
            val value = properties[name].orEmpty().toByteArray(Charsets.UTF_8)
            if (value.size + 1 > outValueLen) return DesktopMpvNativeStatus.BUFFER_TOO_SMALL.code
            outValue.write(0, value, 0, value.size)
            outValue.setByte(value.size.toLong(), 0)
            return DesktopMpvNativeStatus.OK.code
        }

        override fun danmaku_mpv_destroy(handle: Pointer?) {
            destroyedHandle = handle
        }

        private fun readStrings(
            values: Pointer?,
            valuesLen: Long,
        ): List<String> =
            (0 until valuesLen).map { index ->
                requireNotNull(values)
                    .getPointer(index * Native.POINTER_SIZE.toLong())
                    .getString(0, Charsets.UTF_8.name())
            }
    }

    private data class RecordingOsdOverlay(
        val id: Long,
        val format: String,
        val data: String,
        val resX: Long,
        val resY: Long,
        val z: Long,
        val hidden: Int,
    )
}
