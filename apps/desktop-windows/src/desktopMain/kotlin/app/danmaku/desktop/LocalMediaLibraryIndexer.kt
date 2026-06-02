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

    fun index(
        root: Path,
        cachedItems: Map<String, CachedLocalMediaItem> = emptyMap(),
    ): IndexedLocalLibrary {
        require(Files.isDirectory(root)) { "library root must be a directory" }
        val normalizedRoot = root.toAbsolutePath().normalize()
        val filesById = linkedMapOf<String, Path>()
        val fileMetadataByRelativePath = linkedMapOf<String, CachedLocalMediaItem>()
        var reusedItemCount = 0

        val items = Files.walk(normalizedRoot).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .filter { it.extension.lowercase() in videoExtensions }
                .map { path ->
                    val relativePath = normalizedRoot.relativeMediaPath(path)
                    val sizeBytes = Files.size(path)
                    val lastModifiedEpochMs = Files.getLastModifiedTime(path).toMillis()
                    val cachedItem = cachedItems[relativePath]
                        ?.takeIf {
                            it.item.sizeBytes == sizeBytes &&
                                it.lastModifiedEpochMs == lastModifiedEpochMs
                        }
                    val item = cachedItem?.item
                        ?: indexedItem(
                            root = normalizedRoot,
                            path = path,
                            relativePath = relativePath,
                            sizeBytes = sizeBytes,
                        )
                    if (cachedItem != null) reusedItemCount += 1
                    filesById[item.id] = path
                    fileMetadataByRelativePath[relativePath] = CachedLocalMediaItem(
                        item = item,
                        lastModifiedEpochMs = lastModifiedEpochMs,
                    )
                    item
                }
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
            fileMetadataByRelativePath = fileMetadataByRelativePath,
            scanStats = LocalMediaLibraryScanStats(
                reusedItemCount = reusedItemCount,
                refreshedItemCount = items.size - reusedItemCount,
            ),
        )
    }

    private fun indexedItem(
        root: Path,
        path: Path,
        relativePath: String,
        sizeBytes: Long,
    ): LibraryMediaItem {
        val id = sha256(relativePath).take(24)
        return LibraryMediaItem(
            id = id,
            seriesTitle = path.parent?.fileName?.toString() ?: root.fileName.toString(),
            episodeTitle = path.nameWithoutExtension,
            relativePath = relativePath,
            sizeBytes = sizeBytes,
            mediaType = mediaType(path.extension),
            streamPath = "/media/$id",
        )
    }

    private fun Path.relativeMediaPath(path: Path): String =
        relativize(path).toString().replace('\\', '/')

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
    val fileMetadataByRelativePath: Map<String, CachedLocalMediaItem> = emptyMap(),
    val scanStats: LocalMediaLibraryScanStats = LocalMediaLibraryScanStats(),
)

data class CachedLocalMediaItem(
    val item: LibraryMediaItem,
    val lastModifiedEpochMs: Long,
)

data class LocalMediaLibraryScanStats(
    val reusedItemCount: Int = 0,
    val refreshedItemCount: Int = 0,
)
