package app.danmaku.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.ptr.PointerByReference
import java.nio.file.Path

interface DesktopMpvNativeLibrary : Library {
    fun danmaku_mpv_create_with_options(
        libmpvPath: String,
        optionNames: Pointer?,
        optionValues: Pointer?,
        optionsLen: Long,
        outHandle: PointerByReference,
    ): Int

    fun danmaku_mpv_command(
        handle: Pointer,
        args: Pointer,
        argsLen: Long,
    ): Int

    fun danmaku_mpv_osd_overlay(
        handle: Pointer,
        id: Long,
        format: String,
        data: String,
        resX: Long,
        resY: Long,
        z: Long,
        hidden: Int,
    ): Int

    fun danmaku_mpv_get_property_string(
        handle: Pointer,
        name: String,
        outValue: Pointer,
        outValueLen: Long,
    ): Int

    fun danmaku_mpv_destroy(handle: Pointer?)
}

enum class DesktopMpvNativeStatus(
    val code: Int,
) {
    OK(0),
    NULL_POINTER(-1),
    INVALID_UTF8(-2),
    LOAD_FAILED(-3),
    CREATE_FAILED(-4),
    COMMAND_FAILED(-5),
    SET_OPTION_FAILED(-6),
    BUFFER_TOO_SMALL(-7),
    UNKNOWN(Int.MIN_VALUE),
    ;

    companion object {
        fun fromCode(code: Int): DesktopMpvNativeStatus =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

class DesktopMpvNativeException(
    val status: DesktopMpvNativeStatus,
    message: String,
) : RuntimeException(message)

data class DesktopMpvOsdOverlay(
    val id: Long,
    val format: String,
    val data: String,
    val resX: Long,
    val resY: Long,
    val z: Long = 0,
    val hidden: Boolean = false,
) {
    init {
        require(id >= 0) { "overlay id must not be negative" }
        require(format.isNotBlank()) { "overlay format must not be blank" }
        require(data.none { it == '\u0000' }) { "overlay data must not contain null bytes" }
        require(resX > 0) { "overlay resX must be positive" }
        require(resY > 0) { "overlay resY must be positive" }
    }
}

fun interface DesktopMpvOsdOverlayRenderer {
    fun render(overlay: DesktopMpvOsdOverlay)
}

class DesktopMpvNativeCommandExecutor private constructor(
    private val nativeLibrary: DesktopMpvNativeLibrary,
    private val handle: Pointer,
) : CloseableDesktopMpvCommandExecutor, DesktopMpvPropertyReader, DesktopMpvOsdOverlayRenderer {
    override fun execute(command: DesktopMpvCommand) {
        val args = StringArray(command.args.toTypedArray(), Charsets.UTF_8.name())
        val status = DesktopMpvNativeStatus.fromCode(
            nativeLibrary.danmaku_mpv_command(
                handle = handle,
                args = args,
                argsLen = command.args.size.toLong(),
            ),
        )
        checkStatus(status, "mpv command failed: ${command.args.joinToString(" ")}")
    }

    override fun readProperty(name: String): String? {
        val buffer = Memory(PROPERTY_BUFFER_BYTES)
        val status = DesktopMpvNativeStatus.fromCode(
            nativeLibrary.danmaku_mpv_get_property_string(
                handle = handle,
                name = name,
                outValue = buffer,
                outValueLen = PROPERTY_BUFFER_BYTES,
            ),
        )
        if (status == DesktopMpvNativeStatus.BUFFER_TOO_SMALL) {
            val largeBuffer = Memory(LARGE_PROPERTY_BUFFER_BYTES)
            val largeStatus = DesktopMpvNativeStatus.fromCode(
                nativeLibrary.danmaku_mpv_get_property_string(
                    handle = handle,
                    name = name,
                    outValue = largeBuffer,
                    outValueLen = LARGE_PROPERTY_BUFFER_BYTES,
                ),
            )
            checkStatus(largeStatus, "mpv property read failed: $name")
            return largeBuffer.getString(0, Charsets.UTF_8.name()).takeIf(String::isNotBlank)
        }
        checkStatus(status, "mpv property read failed: $name")
        return buffer.getString(0, Charsets.UTF_8.name()).takeIf(String::isNotBlank)
    }

    override fun render(overlay: DesktopMpvOsdOverlay) {
        val status = DesktopMpvNativeStatus.fromCode(
            nativeLibrary.danmaku_mpv_osd_overlay(
                handle = handle,
                id = overlay.id,
                format = overlay.format,
                data = overlay.data,
                resX = overlay.resX,
                resY = overlay.resY,
                z = overlay.z,
                hidden = if (overlay.hidden) 1 else 0,
            ),
        )
        checkStatus(status, "mpv osd-overlay failed")
    }

    override fun close() {
        nativeLibrary.danmaku_mpv_destroy(handle)
    }

    companion object {
        fun load(
            nativeLibraryPath: Path,
            libmpvPath: Path,
            options: Map<String, String> = emptyMap(),
        ): DesktopMpvNativeCommandExecutor {
            val nativeLibrary = Native.load(
                nativeLibraryPath.toAbsolutePath().normalize().toString(),
                DesktopMpvNativeLibrary::class.java,
            )
            return create(nativeLibrary, libmpvPath, options)
        }

        fun create(
            nativeLibrary: DesktopMpvNativeLibrary,
            libmpvPath: Path,
            options: Map<String, String> = emptyMap(),
        ): DesktopMpvNativeCommandExecutor {
            val outHandle = PointerByReference()
            val optionNames = stringArrayOrNull(options.keys)
            val optionValues = stringArrayOrNull(options.values)
            val status = DesktopMpvNativeStatus.fromCode(
                nativeLibrary.danmaku_mpv_create_with_options(
                    libmpvPath.toAbsolutePath().normalize().toString(),
                    optionNames,
                    optionValues,
                    options.size.toLong(),
                    outHandle,
                ),
            )
            checkStatus(status, "mpv context creation failed")
            val handle = outHandle.value
                ?: throw DesktopMpvNativeException(
                    DesktopMpvNativeStatus.NULL_POINTER,
                    "mpv context creation returned a null handle",
                )
            return DesktopMpvNativeCommandExecutor(nativeLibrary, handle)
        }

        private fun stringArrayOrNull(values: Collection<String>): Pointer? =
            if (values.isEmpty()) {
                null
            } else {
                StringArray(values.toTypedArray(), Charsets.UTF_8.name())
            }

        private fun checkStatus(
            status: DesktopMpvNativeStatus,
            message: String,
        ) {
            if (status != DesktopMpvNativeStatus.OK) {
                throw DesktopMpvNativeException(status, "$message ($status)")
            }
        }

        private const val PROPERTY_BUFFER_BYTES = 4_096L
        private const val LARGE_PROPERTY_BUFFER_BYTES = 65_536L
    }
}
