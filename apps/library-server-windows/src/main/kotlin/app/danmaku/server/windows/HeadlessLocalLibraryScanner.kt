package app.danmaku.server.windows

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.server.PublishedLibrary
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

internal object HeadlessLocalLibraryScanner {
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

    fun scan(roots: List<Path>): HeadlessLibraryScanResult {
        if (roots.isEmpty()) {
            return HeadlessLibraryScanResult(PublishedLibrary.EMPTY, scannedRootCount = 0)
        }

        val normalizedRoots = roots
            .map { root -> root.toAbsolutePath().normalize() }
            .distinct()
            .sortedBy(Path::toString)
        normalizedRoots.forEach { root ->
            require(Files.isDirectory(root)) { "library root must be a directory: $root" }
        }

        val filesById = linkedMapOf<String, Path>()
        val subtitleFilesById = linkedMapOf<String, Path>()
        val scanStartedAtEpochMs = System.currentTimeMillis()
        val items = normalizedRoots
            .flatMap { root ->
                scanRoot(
                    root = root,
                    indexedAtEpochMs = scanStartedAtEpochMs,
                    filesById = filesById,
                    subtitleFilesById = subtitleFilesById,
                )
            }
            .sortedWith(compareBy(LibraryMediaItem::seriesTitle, LibraryMediaItem::relativePath))

        return HeadlessLibraryScanResult(
            publishedLibrary = PublishedLibrary(
                catalog = LibraryCatalog(
                    rootName = rootName(normalizedRoots),
                    indexedAtEpochMs = scanStartedAtEpochMs,
                    items = items,
                ),
                filesById = filesById,
                subtitleFilesById = subtitleFilesById,
            ),
            scannedRootCount = normalizedRoots.size,
        )
    }

    private fun scanRoot(
        root: Path,
        indexedAtEpochMs: Long,
        filesById: MutableMap<String, Path>,
        subtitleFilesById: MutableMap<String, Path>,
    ): List<LibraryMediaItem> {
        val idNamespace = root.toString()
        return Files.walk(root).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .filter { path -> path.extension.lowercase() in videoExtensions }
                .map { path ->
                    val relativePath = root.relativeMediaPath(path)
                    val subtitles = sidecarSubtitles(
                        root = root,
                        videoPath = path,
                        idNamespace = idNamespace,
                    )
                    val id = sha256("$idNamespace/$relativePath").take(24)
                    val item = LibraryMediaItem(
                        id = id,
                        seriesTitle = seriesTitle(root, path),
                        episodeTitle = path.nameWithoutExtension,
                        relativePath = relativePath,
                        sizeBytes = Files.size(path),
                        mediaType = mediaType(path.extension),
                        streamPath = "/media/$id",
                        indexedAtEpochMs = indexedAtEpochMs,
                        subtitles = subtitles.map(HeadlessSubtitleFile::track),
                    )
                    filesById[item.id] = path
                    subtitles.forEach { subtitle ->
                        subtitleFilesById[subtitle.track.id] = subtitle.path
                    }
                    item
                }
                .toList()
        }
    }

    private fun sidecarSubtitles(
        root: Path,
        videoPath: Path,
        idNamespace: String,
    ): List<HeadlessSubtitleFile> {
        val parent = videoPath.parent ?: return emptyList()
        val videoBaseName = videoPath.nameWithoutExtension
        return Files.list(parent).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .filter { subtitlePath -> subtitlePath.extension.lowercase() in subtitleExtensions }
                .filter { subtitlePath ->
                    val subtitleBaseName = subtitlePath.nameWithoutExtension
                    subtitleBaseName.equals(videoBaseName, ignoreCase = true) ||
                        subtitleBaseName.startsWith("$videoBaseName.", ignoreCase = true)
                }
                .map { subtitlePath ->
                    val relativePath = root.relativeMediaPath(subtitlePath)
                    val id = sha256("$idNamespace/subtitle/$relativePath").take(24)
                    val suffix = subtitlePath.nameWithoutExtension
                        .drop(videoBaseName.length)
                        .trimStart('.')
                    HeadlessSubtitleFile(
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
                .sorted(compareBy<HeadlessSubtitleFile> { subtitle -> subtitle.track.relativePath })
                .toList()
        }
    }

    private fun rootName(roots: List<Path>): String =
        if (roots.size == 1) {
            roots.single().fileName?.toString() ?: roots.single().toString()
        } else {
            "Headless Library"
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

internal data class HeadlessLibraryScanResult(
    val publishedLibrary: PublishedLibrary,
    val scannedRootCount: Int,
)

private data class HeadlessSubtitleFile(
    val track: LibrarySubtitleTrack,
    val path: Path,
)

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
