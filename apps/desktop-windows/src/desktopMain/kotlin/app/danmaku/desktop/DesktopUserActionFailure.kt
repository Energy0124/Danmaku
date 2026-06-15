package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import java.nio.file.Files
import java.nio.file.Path

internal class DesktopUserActionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal fun IndexedLocalLibrary.requireMediaPath(item: LibraryMediaItem): Path {
    val path = filesById[item.id]
        ?: throw DesktopUserActionException("Indexed media file is missing for ${item.id}")
    if (!Files.isRegularFile(path)) {
        throw DesktopUserActionException(
            "Indexed media file no longer exists: ${path.toAbsolutePath().normalize()}",
        )
    }
    return path
}
