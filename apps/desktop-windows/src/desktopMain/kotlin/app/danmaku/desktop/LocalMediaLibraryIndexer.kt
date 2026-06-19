package app.danmaku.desktop

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
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
    private val subtitleExtensions = setOf("ass", "srt", "ssa", "vtt")

    fun index(
        root: Path,
        cachedItems: Map<String, CachedLocalMediaItem> = emptyMap(),
        idNamespace: String? = null,
    ): IndexedLocalLibrary {
        require(idNamespace == null || idNamespace.isNotBlank()) {
            "idNamespace must not be blank"
        }
        require(Files.isDirectory(root)) { "library root must be a directory" }
        val normalizedRoot = root.toAbsolutePath().normalize()
        val filesById = linkedMapOf<String, Path>()
        val subtitleFilesById = linkedMapOf<String, Path>()
        val fileMetadataByRelativePath = linkedMapOf<String, CachedLocalMediaItem>()
        var reusedItemCount = 0
        val scanStartedAtEpochMs = System.currentTimeMillis()

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
                    val subtitles = sidecarSubtitles(
                        root = normalizedRoot,
                        videoPath = path,
                        idNamespace = idNamespace,
                    )
                    val seriesTitle = seriesTitle(root = normalizedRoot, path = path)
                    val episodeTitle = path.nameWithoutExtension
                    val mediaType = mediaType(path.extension)
                    val item = cachedItem?.item
                        ?.copy(
                            seriesTitle = seriesTitle,
                            episodeTitle = episodeTitle,
                            mediaType = mediaType,
                            indexedAtEpochMs = cachedItem.item.indexedAtEpochMs
                                .takeIf { it > 0 }
                                ?: lastModifiedEpochMs,
                            subtitles = subtitles.map(LibrarySubtitleFile::track),
                        )
                        ?: indexedItem(
                            root = normalizedRoot,
                            path = path,
                            relativePath = relativePath,
                            sizeBytes = sizeBytes,
                            indexedAtEpochMs = scanStartedAtEpochMs,
                            idNamespace = idNamespace,
                            subtitles = subtitles.map(LibrarySubtitleFile::track),
                        )
                    if (cachedItem != null) reusedItemCount += 1
                    filesById[item.id] = path
                    subtitles.forEach { subtitle ->
                        subtitleFilesById[subtitle.track.id] = subtitle.path
                    }
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
                indexedAtEpochMs = scanStartedAtEpochMs,
                items = items,
            ),
            filesById = filesById,
            subtitleFilesById = subtitleFilesById,
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
        indexedAtEpochMs: Long,
        idNamespace: String?,
        subtitles: List<LibrarySubtitleTrack>,
    ): LibraryMediaItem {
        val id = sha256(idNamespace?.let { "$it/$relativePath" } ?: relativePath).take(24)
        return LibraryMediaItem(
            id = id,
            seriesTitle = seriesTitle(root = root, path = path),
            episodeTitle = path.nameWithoutExtension,
            relativePath = relativePath,
            sizeBytes = sizeBytes,
            mediaType = mediaType(path.extension),
            streamPath = "/media/$id",
            indexedAtEpochMs = indexedAtEpochMs,
            subtitles = subtitles,
        )
    }

    private fun sidecarSubtitles(
        root: Path,
        videoPath: Path,
        idNamespace: String?,
    ): List<LibrarySubtitleFile> {
        val parent = videoPath.parent ?: return emptyList()
        val videoBaseName = videoPath.nameWithoutExtension
        return Files.list(parent).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .filter { it.extension.lowercase() in subtitleExtensions }
                .filter { subtitlePath ->
                    val subtitleBaseName = subtitlePath.nameWithoutExtension
                    subtitleBaseName.equals(videoBaseName, ignoreCase = true) ||
                        subtitleBaseName.startsWith("$videoBaseName.", ignoreCase = true)
                }
                .map { subtitlePath ->
                    val relativePath = root.relativeMediaPath(subtitlePath)
                    val id = sha256(
                        idNamespace?.let { "$it/subtitle/$relativePath" }
                            ?: "subtitle/$relativePath",
                    ).take(24)
                    val suffix = subtitlePath.nameWithoutExtension
                        .drop(videoBaseName.length)
                        .trimStart('.')
                    LibrarySubtitleFile(
                        track = LibrarySubtitleTrack(
                            id = id,
                            label = suffix.ifBlank { subtitlePath.extension.uppercase() },
                            relativePath = relativePath,
                            mediaType = subtitleMediaType(subtitlePath.extension),
                            streamPath = "/subtitles/$id",
                        ),
                        path = subtitlePath,
                    )
                }
                .sorted(compareBy { it.track.relativePath })
                .toList()
        }
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

    private fun subtitleMediaType(extension: String): String =
        when (extension.lowercase()) {
            "srt" -> "application/x-subrip"
            "vtt" -> "text/vtt"
            "ass" -> "text/x-ass"
            "ssa" -> "text/x-ssa"
            else -> "text/plain"
        }

    private fun seriesTitle(root: Path, path: Path): String {
        val parent = path.parent?.toAbsolutePath()?.normalize()
        return if (parent == root) {
            inferRootFileSeriesTitle(path.nameWithoutExtension)
        } else {
            path.parent?.fileName?.toString()
                ?: root.fileName?.toString()
                ?: root.toString()
        }
    }

    private fun inferRootFileSeriesTitle(fileStem: String): String {
        val candidate = stripLeadingReleaseGroup(fileStem.trim())
        val marker = listOfNotNull(
            rootFileHyphenEpisodeRegex.find(candidate),
            rootFileBracketEpisodeRegex.find(candidate),
            rootFileNamedEpisodeRegex.find(candidate),
        ).minByOrNull { match -> match.range.first }
        val title = marker?.let { match -> candidate.substring(0, match.range.first) } ?: candidate
        return title
            .trimSeriesTitleDelimiters()
            .trimSingleEnclosingBrackets()
            .ifBlank { fileStem.trim() }
    }

    private fun stripLeadingReleaseGroup(candidate: String): String {
        val first = leadingBracketTokenRegex.find(candidate) ?: return candidate
        val afterFirst = candidate.substring(first.range.last + 1).trimStart()
        if (afterFirst.isBlank()) return candidate
        if (!afterFirst.startsWith("[")) return afterFirst
        val second = leadingBracketTokenRegex.find(afterFirst) ?: return candidate
        val secondToken = second.groupValues[1]
        return if (secondToken.isRootFileEpisodeToken()) candidate else afterFirst
    }

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private val leadingBracketTokenRegex = Regex("""^\[([^]]+)]""")
private val rootFileHyphenEpisodeRegex =
    Regex("""(?i)\s+-\s+(?:s[0-9]{1,2}\s*e)?[0-9]{1,4}(?:v[0-9]+)?(?:\s|\[|$)""")
private val rootFileBracketEpisodeRegex =
    Regex("""(?i)\[([0-9]{1,4})(?:v[0-9]+|\s*(?:end|fin|final))?]""")
private val rootFileNamedEpisodeRegex =
    Regex("""(?i)\b(?:episode|ep)\s*[0-9]{1,4}\b""")

private fun String.trimSeriesTitleDelimiters(): String =
    trim().trim('-', '_', '.', ' ')

private fun String.trimSingleEnclosingBrackets(): String =
    if (startsWith("[") && endsWith("]") && drop(1).dropLast(1).none { char -> char == '[' || char == ']' }) {
        drop(1).dropLast(1).trim()
    } else {
        this
    }

private fun String.isRootFileEpisodeToken(): Boolean =
    rootFileBracketEpisodeRegex.matches("[$this]")

data class IndexedLocalLibrary(
    val catalog: LibraryCatalog,
    val filesById: Map<String, Path>,
    val subtitleFilesById: Map<String, Path> = emptyMap(),
    val fileMetadataByRelativePath: Map<String, CachedLocalMediaItem> = emptyMap(),
    val scanStats: LocalMediaLibraryScanStats = LocalMediaLibraryScanStats(),
)

private data class LibrarySubtitleFile(
    val track: LibrarySubtitleTrack,
    val path: Path,
)

data class CachedLocalMediaItem(
    val item: LibraryMediaItem,
    val lastModifiedEpochMs: Long,
)

data class LocalMediaLibraryScanStats(
    val reusedItemCount: Int = 0,
    val refreshedItemCount: Int = 0,
)
