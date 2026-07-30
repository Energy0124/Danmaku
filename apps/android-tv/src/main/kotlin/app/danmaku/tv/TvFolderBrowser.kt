package app.danmaku.tv

import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem

internal data class TvFolderEntry(
    val name: String,
    val itemCount: Int,
)

internal data class TvFolderListing(
    val folders: List<TvFolderEntry> = emptyList(),
    val files: List<LibraryMediaItem> = emptyList(),
)

internal fun LibraryCatalog.folderListing(path: List<String>): TvFolderListing {
    val roots = rootLabels()
    if (roots.size < 2) {
        return folderListingOf(items, path)
    }
    if (path.isEmpty()) {
        return TvFolderListing(
            folders = roots.map { (name, count) -> TvFolderEntry(name, count) },
        )
    }
    val root = path.first()
    return folderListingOf(
        items = items.filter { it.rootLabel.equals(root, ignoreCase = true) },
        path = path.drop(1),
    )
}

internal fun LibraryCatalog.folderHeading(path: List<String>): String {
    if (path.isEmpty()) return rootName
    return if (rootLabels().size >= 2) {
        path.joinToString("\\")
    } else {
        "$rootName\\${path.joinToString("\\")}"
    }
}

internal fun LibraryMediaItem.fileName(): String =
    relativePath
        .split('/', '\\')
        .lastOrNull { it.isNotEmpty() }
        ?: relativePath

private fun LibraryCatalog.rootLabels(): List<Pair<String, Int>> {
    val labels = mutableListOf<Pair<String, Int>>()
    items.forEach { item ->
        val label = item.rootLabel ?: return@forEach
        val index = labels.indexOfFirst { (existing) -> existing.equals(label, ignoreCase = true) }
        if (index >= 0) {
            labels[index] = labels[index].copy(second = labels[index].second + 1)
        } else {
            labels += label to 1
        }
    }
    return labels
}

private fun folderListingOf(
    items: List<LibraryMediaItem>,
    path: List<String>,
): TvFolderListing {
    val folderCounts = linkedMapOf<String, Int>()
    val files = mutableListOf<LibraryMediaItem>()
    items.forEach { item ->
        val segments = item.relativePath
            .split('/', '\\')
            .filter(String::isNotEmpty)
        if (
            segments.size <= path.size ||
            path.indices.any { !path[it].equals(segments[it], ignoreCase = true) }
        ) {
            return@forEach
        }
        if (segments.size == path.size + 1) {
            files += item
        } else {
            val folderName = segments[path.size]
            val existing = folderCounts.keys.firstOrNull {
                it.equals(folderName, ignoreCase = true)
            }
            val key = existing ?: folderName
            folderCounts[key] = (folderCounts[key] ?: 0) + 1
        }
    }
    return TvFolderListing(
        folders = folderCounts
            .map { (name, count) -> TvFolderEntry(name, count) }
            .sortedBy { it.name.lowercase() },
        files = files.sortedBy { it.fileName().lowercase() },
    )
}
