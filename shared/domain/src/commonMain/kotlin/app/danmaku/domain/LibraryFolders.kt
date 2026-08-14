package app.danmaku.domain

data class LibraryFolderEntry(
    val name: String,
    val itemCount: Int,
)

data class LibraryFolderListing(
    val folders: List<LibraryFolderEntry> = emptyList(),
    val files: List<LibraryMediaItem> = emptyList(),
)

fun LibraryCatalog.folderListing(path: List<String>): LibraryFolderListing {
    val roots = rootLabels()
    if (roots.size < 2) {
        return folderListingOf(items, path)
    }
    if (path.isEmpty()) {
        return LibraryFolderListing(
            folders = roots.map { (name, count) -> LibraryFolderEntry(name, count) },
        )
    }
    val root = path.first()
    return folderListingOf(
        items = items.filter { it.rootLabel.equals(root, ignoreCase = true) },
        path = path.drop(1),
    )
}

fun LibraryCatalog.folderHeading(path: List<String>): String {
    if (path.isEmpty()) return rootName
    return if (rootLabels().size >= 2) {
        path.joinToString("\\")
    } else {
        "$rootName\\${path.joinToString("\\")}"
    }
}

fun LibraryMediaItem.fileName(): String =
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
): LibraryFolderListing {
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
    return LibraryFolderListing(
        folders = folderCounts
            .map { (name, count) -> LibraryFolderEntry(name, count) }
            .sortedBy { it.name.lowercase() },
        files = files.sortedBy { it.fileName().lowercase() },
    )
}
