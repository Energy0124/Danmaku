package app.danmaku.desktop

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

object LocalMediaLibraryIndexer {
    private val videoExtensions = setOf(
        "avi",
        "flv",
        "m2ts",
        "m4v",
        "mkv",
        "mov",
        "mp4",
        "mpeg",
        "mpg",
        "ts",
        "webm",
        "wmv",
    )

    fun index(root: Path): IndexedLocalLibrary {
        require(Files.isDirectory(root)) { "library root must be a directory" }
        val normalizedRoot = root.toAbsolutePath().normalize()
        val filesById = linkedMapOf<String, Path>()

        val items = Files.walk(normalizedRoot).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .filter { it.extension.lowercase() in videoExtensions }
                .map { path -> indexedItem(normalizedRoot, path, filesById) }
                .sorted(compareBy(LibraryMediaItem::seriesTitle, LibraryMediaItem::relativePath))
                .toList()
        }

        return IndexedLocalLibrary(
            catalog = LibraryCatalog(
                rootName = normalizedRoot.fileName?.toString() ?: normalizedRoot.toString(),
                indexedAtEpochMs = System.currentTimeMillis(),
                items = items,
            ),
            filesById = filesById,
        )
    }

    private fun indexedItem(
        root: Path,
        path: Path,
        filesById: MutableMap<String, Path>,
    ): LibraryMediaItem {
        val relativePath = root.relativize(path).toString().replace('\\', '/')
        val id = sha256(relativePath).take(24)
        filesById[id] = path
        return LibraryMediaItem(
            id = id,
            seriesTitle = path.parent?.fileName?.toString() ?: root.fileName.toString(),
            episodeTitle = path.nameWithoutExtension,
            relativePath = relativePath,
            sizeBytes = Files.size(path),
            mediaType = mediaType(path.extension),
            streamPath = "/media/$id",
        )
    }

    private fun mediaType(extension: String): String =
        when (extension.lowercase()) {
            "m4v", "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "ts", "m2ts" -> "video/mp2t"
            else -> "application/octet-stream"
        }

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

data class IndexedLocalLibrary(
    val catalog: LibraryCatalog,
    val filesById: Map<String, Path>,
)
