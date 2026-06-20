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

data class LibrarySeriesWatchSummary(
    val seriesId: String,
    val totalCount: Int,
    val watchedCount: Int,
    val inProgressCount: Int,
    val newCount: Int,
) {
    init {
        require(seriesId.isNotBlank()) { "seriesId must not be blank" }
        require(totalCount >= 0) { "totalCount must not be negative" }
        require(watchedCount >= 0) { "watchedCount must not be negative" }
        require(inProgressCount >= 0) { "inProgressCount must not be negative" }
        require(newCount >= 0) { "newCount must not be negative" }
        require(watchedCount + inProgressCount + newCount == totalCount) {
            "watch-state counts must add up to totalCount"
        }
    }
}

fun LibraryCatalog.groupedSeries(): List<LibrarySeries> =
    items
        .map { item -> LibrarySeriesIdentity.fromItem(item) to item }
        .groupBy(
            keySelector = { (seriesIdentity, _) -> seriesIdentity.id },
            valueTransform = { it },
        )
        .map { (seriesId, identifiedItems) ->
            val seriesItems = identifiedItems.map { (_, item) -> item }
            LibrarySeries(
                id = seriesId,
                title = identifiedItems.preferredSeriesTitle(),
                seasons = seriesItems
                    .groupBy(LibraryMediaItem::seasonIdentity)
                    .map { (seasonIdentity, seasonItems) ->
                        LibrarySeason(
                            id = "$seriesId-${seasonIdentity.id}",
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
                .thenBy { it.title.lowercase() }
                .thenBy { it.id },
        )

private fun List<Pair<LibrarySeriesIdentity, LibraryMediaItem>>.preferredSeriesTitle(): String =
    groupingBy { (seriesIdentity, _) -> seriesIdentity.title }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key.length }
                .thenBy { it.key },
        )
        .firstOrNull()
        ?.key
        ?: "Series"

fun LibraryCatalog.seriesWatchSummaryById(
    progresses: List<PlaybackProgress>,
    minimumStartedPositionMs: Long = 10_000,
    watchedRemainingMs: Long = 30_000,
): Map<String, LibrarySeriesWatchSummary> {
    val watchStatusById = watchStatusByMediaId(
        progresses = progresses,
        minimumStartedPositionMs = minimumStartedPositionMs,
        watchedRemainingMs = watchedRemainingMs,
    )
    return groupedSeries().associate { series ->
        val states = series.seasons
            .flatMap(LibrarySeason::items)
            .map { item -> watchStatusById.getValue(item.id).state }
        series.id to LibrarySeriesWatchSummary(
            seriesId = series.id,
            totalCount = states.size,
            watchedCount = states.count { it == LibraryWatchState.WATCHED },
            inProgressCount = states.count { it == LibraryWatchState.IN_PROGRESS },
            newCount = states.count { it == LibraryWatchState.NEW },
        )
    }
}

private data class LibrarySeasonIdentity(
    val id: String,
    val label: String,
    val sortKey: Int,
)

private data class LibrarySeriesIdentity(
    val id: String,
    val title: String,
) {
    companion object {
        fun fromItem(item: LibraryMediaItem): LibrarySeriesIdentity {
            val metadata = item.animeMetadata
            if (metadata != null) {
                return LibrarySeriesIdentity(
                    id = "anime-${metadata.animeId.stableLibraryId()}",
                    title = metadata.displayTitle,
                )
            }
            return local(item.seriesTitle)
        }

        private fun local(seriesTitle: String): LibrarySeriesIdentity {
            val normalizedTitle = seriesTitle.trim().ifBlank { "Series" }
            return LibrarySeriesIdentity(
                id = normalizedTitle.stableLibraryId(),
                title = normalizedTitle,
            )
        }
    }
}

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

private fun ExternalAnimeId.stableLibraryId(): String =
    "${provider.name.lowercase()}-$value"
