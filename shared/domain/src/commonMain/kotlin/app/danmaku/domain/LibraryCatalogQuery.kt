package app.danmaku.domain

data class LibraryCatalogQuery(
    val searchText: String = "",
    val sort: LibraryCatalogSort = LibraryCatalogSort.TITLE,
    val subtitleFilter: LibrarySubtitleFilter = LibrarySubtitleFilter.ANY,
) {
    init {
        require(searchText.none { it == '\u0000' }) {
            "searchText must not contain null bytes"
        }
    }
}

enum class LibraryCatalogSort {
    TITLE,
    PATH,
}

enum class LibrarySubtitleFilter {
    ANY,
    WITH_SUBTITLES,
}

fun LibraryCatalog.filteredItems(query: LibraryCatalogQuery): List<LibraryMediaItem> =
    items
        .asSequence()
        .filter { item -> item.matchesSearch(query.searchText) }
        .filter { item ->
            when (query.subtitleFilter) {
                LibrarySubtitleFilter.ANY -> true
                LibrarySubtitleFilter.WITH_SUBTITLES -> item.subtitles.isNotEmpty()
            }
        }
        .sortedWith(query.sort.comparator())
        .toList()

private fun LibraryMediaItem.matchesSearch(searchText: String): Boolean {
    val terms = searchText
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (terms.isEmpty()) {
        return true
    }
    val searchableText = listOf(seriesTitle, episodeTitle, relativePath)
        .joinToString(separator = " ")
        .lowercase()
    return terms.all { term -> searchableText.contains(term.lowercase()) }
}

private fun LibraryCatalogSort.comparator(): Comparator<LibraryMediaItem> =
    when (this) {
        LibraryCatalogSort.TITLE -> compareBy(
            { it.seriesTitle.lowercase() },
            { it.episodeTitle.lowercase() },
            { it.relativePath.lowercase() },
        )
        LibraryCatalogSort.PATH -> compareBy(
            { it.relativePath.lowercase() },
            { it.seriesTitle.lowercase() },
            { it.episodeTitle.lowercase() },
        )
    }
