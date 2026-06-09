package app.danmaku.domain

data class LibraryCatalogQuery(
    val searchText: String = "",
    val sort: LibraryCatalogSort = LibraryCatalogSort.TITLE,
    val subtitleFilter: LibrarySubtitleFilter = LibrarySubtitleFilter.ANY,
    val favoriteFilter: LibraryFavoriteFilter = LibraryFavoriteFilter.ANY,
    val favoriteMediaIds: Set<String> = emptySet(),
) {
    init {
        require(searchText.none { it == '\u0000' }) {
            "searchText must not contain null bytes"
        }
        require(favoriteMediaIds.none { it.isBlank() }) {
            "favoriteMediaIds must not contain blank ids"
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

enum class LibraryFavoriteFilter {
    ANY,
    FAVORITES_ONLY,
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
        .filter { item ->
            when (query.favoriteFilter) {
                LibraryFavoriteFilter.ANY -> true
                LibraryFavoriteFilter.FAVORITES_ONLY -> item.id in query.favoriteMediaIds
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
    val searchableText = listOfNotNull(
        seriesTitle,
        episodeTitle,
        relativePath,
        animeMetadata?.displayTitle,
        animeMetadata?.primaryTitle,
        animeMetadata?.chineseTitle,
        animeMetadata?.englishTitle,
        animeMetadata?.japaneseTitle,
    )
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
