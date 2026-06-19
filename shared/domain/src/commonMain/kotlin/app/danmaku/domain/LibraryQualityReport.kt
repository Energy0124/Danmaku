package app.danmaku.domain

data class LibraryQualityReport(
    val issues: List<LibraryQualityIssue>,
) {
    val issueCount: Int = issues.size
    val reviewCount: Int = issues.count { it.severity == LibraryQualityIssueSeverity.REVIEW }
    val warningCount: Int = issues.count { it.severity == LibraryQualityIssueSeverity.WARNING }
}

data class LibraryQualityIssue(
    val type: LibraryQualityIssueType,
    val severity: LibraryQualityIssueSeverity,
    val seriesId: String,
    val seriesTitle: String,
    val mediaItemIds: List<String>,
    val relativePaths: List<String>,
    val message: String,
    val evidence: List<String> = emptyList(),
) {
    init {
        require(seriesId.isNotBlank()) { "seriesId must not be blank" }
        require(seriesTitle.isNotBlank()) { "seriesTitle must not be blank" }
        require(mediaItemIds.isNotEmpty()) { "mediaItemIds must not be empty" }
        require(mediaItemIds.none { it.isBlank() }) { "mediaItemIds must not contain blank IDs" }
        require(relativePaths.isNotEmpty()) { "relativePaths must not be empty" }
        require(relativePaths.none { it.isBlank() }) { "relativePaths must not contain blank paths" }
        require(message.isNotBlank()) { "message must not be blank" }
        require(evidence.none { it.isBlank() }) { "evidence must not contain blank values" }
    }
}

enum class LibraryQualityIssueSeverity {
    REVIEW,
    WARNING,
}

enum class LibraryQualityIssueType {
    FOLDER_FILE_EPISODE_MISMATCH,
    DUPLICATE_EPISODE_NUMBER,
    MISSING_EPISODE_NUMBER,
    UNPARSED_EPISODE_NUMBER,
    UNMATCHED_SERIES,
    METADATA_EPISODE_COUNT_MISMATCH,
    SPLIT_SERIES_CANDIDATE,
}

fun LibraryCatalog.libraryQualityReport(): LibraryQualityReport =
    LibraryQualityScanner.scan(this)

fun LibraryQualityIssue.stableKey(): String =
    listOf(
        type.name.qualityKeyPart(),
        seriesId.qualityKeyPart(),
        mediaItemIds.sorted().joinToString(separator = ",") { id -> id.qualityKeyPart() },
        relativePaths.sorted().joinToString(separator = ",") { path -> path.qualityKeyPart() },
    ).joinToString(separator = "|")

object LibraryQualityScanner {
    fun scan(catalog: LibraryCatalog): LibraryQualityReport {
        val itemIssues = catalog.items.flatMap(::itemIssues)
        val localSeriesIssues = catalog.items
            .groupBy { it.seriesTitle }
            .flatMap(::localSeriesIssues)
        val groupedSeriesIssues = catalog.groupedSeries().flatMap(::groupedSeriesIssues)
        return LibraryQualityReport(
            issues = (itemIssues + localSeriesIssues + groupedSeriesIssues)
                .sortedWith(
                    compareBy<LibraryQualityIssue> { it.seriesTitle.lowercase() }
                        .thenBy { it.type.name }
                        .thenBy { it.relativePaths.firstOrNull().orEmpty().lowercase() },
                ),
        )
    }

    private fun itemIssues(item: LibraryMediaItem): List<LibraryQualityIssue> {
        val folderHint = EpisodeHint.parse(item.seriesTitle)
        val fileHint = EpisodeHint.parse(item.episodeTitle)
            ?: EpisodeHint.parse(item.relativePath.substringAfterLast('/'))
        val mismatch = folderHint?.mismatchWith(fileHint)
        return buildList {
            if (mismatch != null && fileHint != null) {
                add(
                    LibraryQualityIssue(
                        type = LibraryQualityIssueType.FOLDER_FILE_EPISODE_MISMATCH,
                        severity = LibraryQualityIssueSeverity.REVIEW,
                        seriesId = item.seriesTitle.stableQualityId(),
                        seriesTitle = item.seriesTitle,
                        mediaItemIds = listOf(item.id),
                        relativePaths = listOf(item.relativePath),
                        message = "Folder and file episode hints disagree.",
                        evidence = listOf(
                            "folder=${folderHint.describe()}",
                            "file=${fileHint.describe()}",
                            mismatch,
                        ),
                    ),
                )
            }
            if (fileHint == null) {
                add(
                    LibraryQualityIssue(
                        type = LibraryQualityIssueType.UNPARSED_EPISODE_NUMBER,
                        severity = LibraryQualityIssueSeverity.REVIEW,
                        seriesId = item.seriesTitle.stableQualityId(),
                        seriesTitle = item.seriesTitle,
                        mediaItemIds = listOf(item.id),
                        relativePaths = listOf(item.relativePath),
                        message = "No episode number could be inferred from the file name.",
                        evidence = listOf("episodeTitle=${item.episodeTitle}"),
                    ),
                )
            }
        }
    }

    private fun localSeriesIssues(entry: Map.Entry<String, List<LibraryMediaItem>>): List<LibraryQualityIssue> {
        val (seriesTitle, items) = entry
        val animeIds = items.mapNotNull { it.animeMetadata?.animeId }.distinct()
        if (animeIds.size < 2) return emptyList()
        return listOf(
            LibraryQualityIssue(
                type = LibraryQualityIssueType.SPLIT_SERIES_CANDIDATE,
                severity = LibraryQualityIssueSeverity.REVIEW,
                seriesId = seriesTitle.stableQualityId(),
                seriesTitle = seriesTitle,
                mediaItemIds = items.map(LibraryMediaItem::id),
                relativePaths = items.map(LibraryMediaItem::relativePath),
                message = "One local folder contains files matched to multiple anime.",
                evidence = animeIds.map { animeId -> "${animeId.provider.name}#${animeId.value}" },
            ),
        )
    }

    private fun groupedSeriesIssues(series: LibrarySeries): List<LibraryQualityIssue> {
        val items = series.seasons.flatMap(LibrarySeason::items)
        val hintsByItem = items.mapNotNull { item ->
            val hint = EpisodeHint.parse(item.episodeTitle)
                ?: EpisodeHint.parse(item.relativePath.substringAfterLast('/'))
                ?: return@mapNotNull null
            item to hint
        }
        val duplicateIssues = duplicateEpisodeIssues(series, hintsByItem)
        val missingIssues = missingEpisodeIssues(series, hintsByItem)
        val unmatchedIssue = unmatchedSeriesIssue(series, items)
        val metadataIssue = metadataEpisodeCountIssue(series, items)
        return duplicateIssues + missingIssues + listOfNotNull(unmatchedIssue, metadataIssue)
    }

    private fun duplicateEpisodeIssues(
        series: LibrarySeries,
        hintsByItem: List<Pair<LibraryMediaItem, EpisodeHint>>,
    ): List<LibraryQualityIssue> =
        hintsByItem
            .groupBy { (_, hint) -> EpisodeKey(hint.season, hint.episode) }
            .filterValues { matches -> matches.size > 1 }
            .map { (key, matches) ->
                val items = matches.map { (item, _) -> item }
                LibraryQualityIssue(
                    type = LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER,
                    severity = LibraryQualityIssueSeverity.REVIEW,
                    seriesId = series.id,
                    seriesTitle = series.title,
                    mediaItemIds = items.map(LibraryMediaItem::id),
                    relativePaths = items.map(LibraryMediaItem::relativePath),
                    message = "Multiple files appear to represent the same episode.",
                    evidence = listOf("episode=${key.describe()}"),
                )
            }

    private fun missingEpisodeIssues(
        series: LibrarySeries,
        hintsByItem: List<Pair<LibraryMediaItem, EpisodeHint>>,
    ): List<LibraryQualityIssue> =
        hintsByItem
            .groupBy { (_, hint) -> hint.season }
            .mapNotNull { (season, matches) ->
                val episodes = matches.map { (_, hint) -> hint.episode }.distinct().sorted()
                if (episodes.size < 2) return@mapNotNull null
                val first = episodes.first()
                val last = episodes.last()
                if (last - first > MAX_MISSING_EPISODE_SPAN) return@mapNotNull null
                val missing = (first..last).filterNot { it in episodes }
                if (missing.isEmpty()) return@mapNotNull null
                val items = matches.map { (item, _) -> item }
                LibraryQualityIssue(
                    type = LibraryQualityIssueType.MISSING_EPISODE_NUMBER,
                    severity = LibraryQualityIssueSeverity.REVIEW,
                    seriesId = series.id,
                    seriesTitle = series.title,
                    mediaItemIds = items.map(LibraryMediaItem::id),
                    relativePaths = items.map(LibraryMediaItem::relativePath),
                    message = "Episode numbers have gaps in this series.",
                    evidence = listOf(
                        "season=${season?.toString() ?: "unknown"}",
                        "missing=${missing.joinToString()}",
                    ),
                )
            }

    private fun unmatchedSeriesIssue(
        series: LibrarySeries,
        items: List<LibraryMediaItem>,
    ): LibraryQualityIssue? {
        if (items.any { it.animeMetadata != null }) return null
        return LibraryQualityIssue(
            type = LibraryQualityIssueType.UNMATCHED_SERIES,
            severity = LibraryQualityIssueSeverity.REVIEW,
            seriesId = series.id,
            seriesTitle = series.title,
            mediaItemIds = items.map(LibraryMediaItem::id),
            relativePaths = items.map(LibraryMediaItem::relativePath),
            message = "Series has no matched anime metadata.",
        )
    }

    private fun metadataEpisodeCountIssue(
        series: LibrarySeries,
        items: List<LibraryMediaItem>,
    ): LibraryQualityIssue? {
        val expectedCounts = items.mapNotNull { it.animeMetadata?.episodeCount }.distinct()
        if (expectedCounts.size != 1) return null
        val expectedCount = expectedCounts.single()
        if (expectedCount == items.size) return null
        return LibraryQualityIssue(
            type = LibraryQualityIssueType.METADATA_EPISODE_COUNT_MISMATCH,
            severity = LibraryQualityIssueSeverity.WARNING,
            seriesId = series.id,
            seriesTitle = series.title,
            mediaItemIds = items.map(LibraryMediaItem::id),
            relativePaths = items.map(LibraryMediaItem::relativePath),
            message = "Local file count differs from matched anime episode count.",
            evidence = listOf(
                "local=${items.size}",
                "metadata=$expectedCount",
            ),
        )
    }

    private const val MAX_MISSING_EPISODE_SPAN = 200
}

private data class EpisodeKey(
    val season: Int?,
    val episode: Int,
) {
    fun describe(): String =
        season?.let { "S${it.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}" }
            ?: episode.toString()
}

private data class EpisodeHint(
    val season: Int?,
    val episode: Int,
    val rangeEnd: Int? = null,
) {
    fun describe(): String =
        buildString {
            if (season != null) append("S${season.toString().padStart(2, '0')}")
            append("E${episode.toString().padStart(2, '0')}")
            if (rangeEnd != null) append("-${rangeEnd.toString().padStart(2, '0')}")
        }

    fun mismatchWith(other: EpisodeHint?): String? {
        if (other == null) return null
        if (season != null && other.season != null && season != other.season) {
            return "season differs"
        }
        if (rangeEnd != null) {
            return if (other.episode in episode..rangeEnd) null else "file episode is outside folder range"
        }
        return if (episode == other.episode) null else "episode differs"
    }

    companion object {
        fun parse(text: String): EpisodeHint? {
            val normalized = text.replace('_', ' ')
            seasonEpisodeRegex.find(normalized)?.let { match ->
                return EpisodeHint(
                    season = match.groupValues[1].toIntOrNull(),
                    episode = match.groupValues[2].toIntOrNull() ?: return null,
                )
            }
            episodeRangeRegex.find(normalized)?.let { match ->
                val start = match.groupValues[1].toIntOrNull() ?: return null
                val end = match.groupValues[2].toIntOrNull() ?: return null
                if (start in 1..end) return EpisodeHint(season = null, episode = start, rangeEnd = end)
            }
            namedEpisodeRegex.find(normalized)?.let { match ->
                return EpisodeHint(
                    season = null,
                    episode = match.groupValues[1].toIntOrNull() ?: return null,
                )
            }
            dashEpisodeRegex.find(normalized)?.let { match ->
                return EpisodeHint(
                    season = null,
                    episode = match.groupValues[1].toIntOrNull() ?: return null,
                )
            }
            bracketEpisodeRegex.findAll(normalized)
                .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
                .firstOrNull(::isPlausibleEpisodeNumber)
                ?.let { episode -> return EpisodeHint(season = null, episode = episode) }
            trailingEpisodeRegex.find(normalized)?.let { match ->
                val episode = match.groupValues[1].toIntOrNull() ?: return null
                if (isPlausibleEpisodeNumber(episode)) {
                    return EpisodeHint(season = null, episode = episode)
                }
            }
            return null
        }

        private fun isPlausibleEpisodeNumber(value: Int): Boolean =
            value in 1..999
    }
}

private val seasonEpisodeRegex = Regex("""(?i)\bs([0-9]{1,2})\s*e([0-9]{1,4})\b""")
private val episodeRangeRegex = Regex("""(?i)(?:^|[^0-9])([0-9]{1,4})\s*[~～]\s*([0-9]{1,4})(?:[^0-9]|$)""")
private val namedEpisodeRegex = Regex("""(?i)\b(?:episode|ep)\s*([0-9]{1,4})\b""")
private val dashEpisodeRegex = Regex("""(?i)(?:^|\s)-\s*([0-9]{1,4})(?:\s|$)""")
private val bracketEpisodeRegex = Regex("""\[([0-9]{1,4})]""")
private val trailingEpisodeRegex = Regex("""(?i)(?:^|[^0-9])([0-9]{1,4})(?:v[0-9])?(?:\s|\[[0-9a-f]{8}])*$""")

private fun String.stableQualityId(): String {
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

private fun String.qualityKeyPart(): String =
    replace("\\", "\\\\")
        .replace(",", "\\,")
        .replace("|", "\\|")
