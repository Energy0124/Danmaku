package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import java.nio.file.Files
import java.nio.file.Path

internal enum class DesktopUserActionFailureKind {
    GENERIC,
    INDEXED_MEDIA_MAPPING_MISSING,
    INDEXED_MEDIA_FILE_MISSING,
    DANDANPLAY_NO_MATCH,
}

internal class DesktopUserActionException(
    message: String,
    val kind: DesktopUserActionFailureKind = DesktopUserActionFailureKind.GENERIC,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal fun IndexedLocalLibrary.requireMediaPath(item: LibraryMediaItem): Path {
    val path = filesById[item.id]
        ?: throw DesktopUserActionException(
            message = "Indexed media file is missing for ${item.id}",
            kind = DesktopUserActionFailureKind.INDEXED_MEDIA_MAPPING_MISSING,
        )
    if (!Files.isRegularFile(path)) {
        throw DesktopUserActionException(
            message = "Indexed media file no longer exists: ${path.toAbsolutePath().normalize()}",
            kind = DesktopUserActionFailureKind.INDEXED_MEDIA_FILE_MISSING,
        )
    }
    return path
}
