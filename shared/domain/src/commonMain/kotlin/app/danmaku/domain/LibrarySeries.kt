package app.danmaku.domain

data class LibrarySeries(
    val id: String,
    val title: String,
    val seasons: List<LibrarySeason>,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(seasons.isNotEmpty()) { "seasons must not be empty" }
    }

    val episodeCount: Int = seasons.sumOf { it.items.size }
    val subtitleTrackCount: Int = seasons.sumOf { season ->
        season.items.sumOf { it.subtitles.size }
    }
    val totalSizeBytes: Long = seasons.sumOf { season ->
        season.items.sumOf { it.sizeBytes }
    }
    val latestIndexedItem: LibraryMediaItem = seasons
        .flatMap(LibrarySeason::items)
        .maxBy { it.relativePath.lowercase() }
}

data class LibrarySeason(
    val id: String,
    val label: String,
    val sortKey: Int,
    val items: List<LibraryMediaItem>,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
        require(items.isNotEmpty()) { "items must not be empty" }
    }
}

fun LibraryCatalog.groupedSeries(): List<LibrarySeries> =
    items
        .groupBy { it.seriesTitle.trim() }
        .map { (seriesTitle, seriesItems) ->
            LibrarySeries(
                id = seriesTitle.stableLibraryId(),
                title = seriesTitle,
                seasons = seriesItems
                    .groupBy(LibraryMediaItem::seasonIdentity)
                    .map { (seasonIdentity, seasonItems) ->
                        LibrarySeason(
                            id = "${seriesTitle.stableLibraryId()}-${seasonIdentity.id}",
                            label = seasonIdentity.label,
                            sortKey = seasonIdentity.sortKey,
                            items = seasonItems.sortedWith(libraryItemTitleComparator()),
                        )
                    }
                    .sortedWith(
                        compareBy<LibrarySeason> { it.sortKey }
                            .thenBy { it.label.lowercase() },
                    ),
            )
        }
        .sortedWith(
            compareByDescending<LibrarySeries> { it.episodeCount }
                .thenBy { it.title.lowercase() },
        )

private data class LibrarySeasonIdentity(
    val id: String,
    val label: String,
    val sortKey: Int,
)

private fun LibraryMediaItem.seasonIdentity(): LibrarySeasonIdentity {
    val searchableText = "$relativePath $episodeTitle"
    val seasonNumber = seasonRegexes
        .firstNotNullOfOrNull { regex ->
            regex.find(searchableText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    return if (seasonNumber == null) {
        LibrarySeasonIdentity(
            id = "season-unknown",
            label = "Season unknown",
            sortKey = Int.MAX_VALUE,
        )
    } else {
        LibrarySeasonIdentity(
            id = "season-${seasonNumber.toString().padStart(2, '0')}",
            label = "Season $seasonNumber",
            sortKey = seasonNumber,
        )
    }
}

private val seasonRegexes = listOf(
    Regex("""(?i)\bseason\s*([0-9]{1,2})\b"""),
    Regex("""(?i)\bs([0-9]{1,2})\b"""),
)

private fun libraryItemTitleComparator(): Comparator<LibraryMediaItem> =
    compareBy(
        { it.episodeSortKey() },
        { it.episodeTitle.lowercase() },
        { it.relativePath.lowercase() },
    )

private fun LibraryMediaItem.episodeSortKey(): Int =
    episodeRegexes
        .firstNotNullOfOrNull { regex ->
            regex.find("$episodeTitle $relativePath")?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        ?: Int.MAX_VALUE

private val episodeRegexes = listOf(
    Regex("""(?i)\bepisode\s*([0-9]{1,4})\b"""),
    Regex("""(?i)\bep\s*([0-9]{1,4})\b"""),
    Regex("""(?i)\be([0-9]{1,4})\b"""),
)

private fun String.stableLibraryId(): String {
    val normalized = trim()
        .lowercase()
        .map { char ->
            when {
                char.isLetterOrDigit() -> char
                else -> '-'
            }
        }
        .joinToString(separator = "")
        .replace(Regex("-+"), "-")
        .trim('-')
    return normalized.ifBlank { "series" }
}
