package app.danmaku.server

import app.danmaku.domain.LibraryCatalog
import java.nio.file.Path

data class PublishedLibrary(
    val catalog: LibraryCatalog,
    val filesById: Map<String, Path>,
    val subtitleFilesById: Map<String, Path> = emptyMap(),
) {
    companion object {
        val EMPTY = PublishedLibrary(
            catalog = LibraryCatalog(
                rootName = "No folder selected",
                indexedAtEpochMs = 0,
                items = emptyList(),
            ),
            filesById = emptyMap(),
            subtitleFilesById = emptyMap(),
        )
    }
}
