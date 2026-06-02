package app.danmaku.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

class LocalLibrarySelectionStore(
    private val selectionFile: Path,
) {
    fun load(): Path? =
        runCatching {
            Files.readString(selectionFile)
                .trim()
                .takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?.toAbsolutePath()
                ?.normalize()
                ?.takeIf(Files::isDirectory)
        }.getOrNull()

    fun save(root: Path) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        require(Files.isDirectory(normalizedRoot)) {
            "Library root must be an existing directory"
        }
        selectionFile.parent?.let(Files::createDirectories)
        Files.writeString(
            selectionFile,
            normalizedRoot.toString(),
            CREATE,
            TRUNCATE_EXISTING,
            WRITE,
        )
    }

    companion object {
        fun default(): LocalLibrarySelectionStore {
            val appDataRoot = System.getenv("LOCALAPPDATA")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: Path.of(System.getProperty("user.home"), ".danmaku")
            return LocalLibrarySelectionStore(
                appDataRoot.resolve("Danmaku").resolve("library-root.txt"),
            )
        }
    }
}
