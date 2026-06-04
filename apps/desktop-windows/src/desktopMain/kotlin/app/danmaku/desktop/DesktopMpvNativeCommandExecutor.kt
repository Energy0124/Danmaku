package app.danmaku.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.ptr.PointerByReference
import java.nio.file.Path

interface DesktopMpvNativeLibrary : Library {
    fun danmaku_mpv_create(
        libmpvPath: String,
        outHandle: PointerByReference,
    ): Int

    fun danmaku_mpv_command(
        handle: Pointer,
        args: Pointer,
        argsLen: Long,
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

class DesktopMpvNativeCommandExecutor private constructor(
    private val nativeLibrary: DesktopMpvNativeLibrary,
    private val handle: Pointer,
) : CloseableDesktopMpvCommandExecutor {
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

    override fun close() {
        nativeLibrary.danmaku_mpv_destroy(handle)
    }

    companion object {
        fun load(
            nativeLibraryPath: Path,
            libmpvPath: Path,
        ): DesktopMpvNativeCommandExecutor {
            val nativeLibrary = Native.load(
                nativeLibraryPath.toAbsolutePath().normalize().toString(),
                DesktopMpvNativeLibrary::class.java,
            )
            return create(nativeLibrary, libmpvPath)
        }

        fun create(
            nativeLibrary: DesktopMpvNativeLibrary,
            libmpvPath: Path,
        ): DesktopMpvNativeCommandExecutor {
            val outHandle = PointerByReference()
            val status = DesktopMpvNativeStatus.fromCode(
                nativeLibrary.danmaku_mpv_create(
                    libmpvPath.toAbsolutePath().normalize().toString(),
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

        private fun checkStatus(
            status: DesktopMpvNativeStatus,
            message: String,
        ) {
            if (status != DesktopMpvNativeStatus.OK) {
                throw DesktopMpvNativeException(status, "$message ($status)")
            }
        }
    }
}
