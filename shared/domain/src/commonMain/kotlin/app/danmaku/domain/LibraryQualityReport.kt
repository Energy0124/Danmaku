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
    EPISODE_VARIANT_GROUP,
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
        val catalogHasAnimeMetadata = catalog.items.any { item -> item.animeMetadata != null }
        val itemIssues = catalog.items.flatMap(::itemIssues)
        val localSeriesIssues = catalog.items
            .groupBy { it.seriesTitle }
            .flatMap(::localSeriesIssues)
        val groupedSeriesIssues = catalog.groupedSeries()
            .flatMap { series -> groupedSeriesIssues(series, catalogHasAnimeMetadata) }
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
        val folderHint = if ('/' in item.relativePath) EpisodeHint.parse(item.seriesTitle) else null
        val fileHint = episodeHintForQuality(item)
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

    private fun groupedSeriesIssues(
        series: LibrarySeries,
        catalogHasAnimeMetadata: Boolean,
    ): List<LibraryQualityIssue> {
        val items = series.seasons.flatMap(LibrarySeason::items)
        val hintsByItem = items.mapNotNull { item ->
            val hint = episodeHintForQuality(item) ?: return@mapNotNull null
            item to hint
        }
        val unparsedIssues = unparsedEpisodeIssues(series, items, hintsByItem)
        val duplicateIssues = duplicateEpisodeIssues(series, hintsByItem)
        val missingIssues = missingEpisodeIssues(series, hintsByItem)
        val unmatchedIssue = if (catalogHasAnimeMetadata) unmatchedSeriesIssue(series, items) else null
        val metadataIssue = metadataEpisodeCountIssue(series, items)
        return unparsedIssues + duplicateIssues + missingIssues + listOfNotNull(unmatchedIssue, metadataIssue)
    }

    private fun unparsedEpisodeIssues(
        series: LibrarySeries,
        items: List<LibraryMediaItem>,
        hintsByItem: List<Pair<LibraryMediaItem, EpisodeHint>>,
    ): List<LibraryQualityIssue> {
        if (hintsByItem.isEmpty()) return emptyList()
        val itemsWithHints = hintsByItem.map { (item, _) -> item.id }.toSet()
        return items
            .filterNot { item -> item.id in itemsWithHints }
            .filterNot { item ->
                EpisodeHint.isKnownSpecial(item.episodeTitle) ||
                    EpisodeHint.isKnownSpecial(item.relativePath.substringAfterLast('/'))
            }
            .map { item ->
                LibraryQualityIssue(
                    type = LibraryQualityIssueType.UNPARSED_EPISODE_NUMBER,
                    severity = LibraryQualityIssueSeverity.REVIEW,
                    seriesId = series.id,
                    seriesTitle = series.title,
                    mediaItemIds = listOf(item.id),
                    relativePaths = listOf(item.relativePath),
                    message = "No episode number could be inferred from the file name.",
                    evidence = listOf("episodeTitle=${item.episodeTitle}"),
                )
            }
    }

    private fun episodeHintForQuality(item: LibraryMediaItem): EpisodeHint? {
        val fileName = item.relativePath.substringAfterLast('/')
        if (EpisodeHint.isKnownSupplemental(item.episodeTitle) || EpisodeHint.isKnownSupplemental(fileName)) {
            return null
        }
        return EpisodeHint.parse(item.episodeTitle) ?: EpisodeHint.parse(fileName)
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
                val variantEvidence = episodeVariantEvidence(items)
                val isVariantGroup = variantEvidence != null
                LibraryQualityIssue(
                    type = if (isVariantGroup) {
                        LibraryQualityIssueType.EPISODE_VARIANT_GROUP
                    } else {
                        LibraryQualityIssueType.DUPLICATE_EPISODE_NUMBER
                    },
                    severity = LibraryQualityIssueSeverity.REVIEW,
                    seriesId = series.id,
                    seriesTitle = series.title,
                    mediaItemIds = items.map(LibraryMediaItem::id),
                    relativePaths = items.map(LibraryMediaItem::relativePath),
                    message = if (isVariantGroup) {
                        "Multiple alternate files appear to represent the same episode."
                    } else {
                        "Multiple files appear to represent the same episode."
                    },
                    evidence = listOf("episode=${key.describe()}") + variantEvidence.orEmpty(),
                )
            }

    private fun episodeVariantEvidence(items: List<LibraryMediaItem>): List<String>? {
        val variants = items.map(EpisodeVariant::from)
        val signatures = variants.map(EpisodeVariant::signature)
        if (signatures.distinct().size != signatures.size) return null
        val evidence = buildList {
            add("variants=${variants.size}")
            variants.fieldEvidence(
                label = "releaseGroups",
                values = variants.mapNotNull(EpisodeVariant::releaseGroup),
            )?.let(::add)
            variants.fieldEvidence(
                label = "resolutions",
                values = variants.mapNotNull(EpisodeVariant::resolution),
            )?.let(::add)
            variants.fieldEvidence(
                label = "subtitleTags",
                values = variants.flatMap(EpisodeVariant::subtitleTags),
            )?.let(::add)
            variants.fieldEvidence(
                label = "sources",
                values = variants.flatMap(EpisodeVariant::sourceTags),
            )?.let(::add)
            variants.fieldEvidence(
                label = "codecs",
                values = variants.flatMap(EpisodeVariant::codecTags),
            )?.let(::add)
            variants.fieldEvidence(
                label = "containers",
                values = variants.mapNotNull(EpisodeVariant::container),
            )?.let(::add)
        }
        return evidence.takeIf { it.size > 1 }
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

private data class EpisodeVariant(
    val releaseGroup: String?,
    val resolution: String?,
    val subtitleTags: List<String>,
    val sourceTags: List<String>,
    val codecTags: List<String>,
    val container: String?,
) {
    val signature: String =
        listOf(
            releaseGroup.normalizedVariantPart(),
            resolution.normalizedVariantPart(),
            subtitleTags.joinToString(separator = "+"),
            sourceTags.joinToString(separator = "+"),
            codecTags.joinToString(separator = "+"),
            container.normalizedVariantPart(),
        ).joinToString(separator = "|")

    companion object {
        fun from(item: LibraryMediaItem): EpisodeVariant {
            val fileName = item.relativePath.substringAfterLast('/')
            val text = "${item.episodeTitle} $fileName"
            return EpisodeVariant(
                releaseGroup = releaseGroup(item),
                resolution = resolution(text),
                subtitleTags = tagValues(text, subtitleTagRegex),
                sourceTags = tagValues(text, sourceTagRegex),
                codecTags = tagValues(text, codecTagRegex),
                container = fileName.substringAfterLast('.', missingDelimiterValue = "")
                    .lowercase()
                    .takeIf { extension -> extension in variantContainerExtensions },
            )
        }

        private fun releaseGroup(item: LibraryMediaItem): String? {
            val title = item.episodeTitle.trim()
            val first = leadingBracketVariantTokenRegex.find(title) ?: return null
            val firstToken = first.groupValues[1].trim()
            if (firstToken.isEpisodeToken() || firstToken.variantComparable() == item.seriesTitle.variantComparable()) {
                return null
            }
            val afterFirst = title.substring(first.range.last + 1).trimStart()
            val second = leadingBracketVariantTokenRegex.find(afterFirst)
            val secondToken = second?.groupValues?.get(1)?.trim()
            return when {
                secondToken != null && secondToken.variantComparable() == item.seriesTitle.variantComparable() ->
                    firstToken
                afterFirst.variantComparable().startsWith(item.seriesTitle.variantComparable()) ->
                    firstToken
                firstToken.looksLikeReleaseGroup() ->
                    firstToken
                else ->
                    null
            }
        }

        private fun resolution(text: String): String? =
            resolutionHeightRegex.find(text)?.groupValues?.get(1)?.let { height -> "${height.lowercase()}p" }
                ?: resolutionDimensionRegex.find(text)?.groupValues?.get(1)?.let { height -> "${height.lowercase()}p" }

        private fun tagValues(text: String, regex: Regex): List<String> =
            regex.findAll(text)
                .map { match -> match.value.normalizedVariantPart() }
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
                .toList()
    }
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
            val normalized = text.withoutKnownMediaExtension().replace('_', ' ')
            seasonEpisodeRegex.find(normalized)?.let { match ->
                return EpisodeHint(
                    season = match.groupValues[1].toIntOrNull(),
                    episode = match.groupValues[2].toIntOrNull() ?: return null,
                )
            }
            episodeRangeHint(normalized)?.let { hint ->
                return hint
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
            titleNumberBeforeDashRegex.find(normalized)?.let { match ->
                val episode = match.groupValues[1].toIntOrNull() ?: return null
                if (isPlausibleEpisodeNumber(episode)) {
                    return EpisodeHint(season = null, episode = episode)
                }
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

        fun isKnownSpecial(text: String): Boolean {
            val normalized = text.withoutKnownMediaExtension().replace('_', ' ')
            return specialEpisodeRegex.containsMatchIn(normalized)
        }

        fun isKnownSupplemental(text: String): Boolean {
            val normalized = text.withoutKnownMediaExtension().replace('_', ' ')
            return supplementalEpisodeRegex.containsMatchIn(normalized)
        }

        private fun isPlausibleEpisodeNumber(value: Int): Boolean =
            value in 1..999

        private fun episodeRangeHint(normalized: String): EpisodeHint? {
            val regexes = listOf(spacedEpisodeRangeRegex, compactHyphenEpisodeRangeRegex)
            for (regex in regexes) {
                regex.findAll(normalized)
                    .mapNotNull { match -> match.toEpisodeRangeHint() }
                    .firstOrNull()
                    ?.let { hint -> return hint }
            }
            return null
        }

        private fun MatchResult.toEpisodeRangeHint(): EpisodeHint? {
            val start = groupValues[1].toIntOrNull() ?: return null
            val end = groupValues[2].toIntOrNull() ?: return null
            return if (start in 1..end) {
                EpisodeHint(season = null, episode = start, rangeEnd = end)
            } else {
                null
            }
        }
    }
}

private val seasonEpisodeRegex = Regex("""(?i)\bs([0-9]{1,2})\s*e([0-9]{1,4})\b""")
private val spacedEpisodeRangeRegex = Regex("""(?i)(?:^|[^0-9])([0-9]{1,4})\s*[~～]\s*([0-9]{1,4})(?:\s*\+\s*(?:ova|oad|special|sp))?(?:[^0-9]|$)""")
private val compactHyphenEpisodeRangeRegex = Regex("""(?i)(?:^|[^0-9])([0-9]{1,4})-([0-9]{1,4})(?:\s*\+\s*(?:ova|oad|special|sp))?(?:[^0-9]|$)""")
private val namedEpisodeRegex = Regex("""(?i)\b(?:episode|ep)\s*([0-9]{1,4})\b""")
private val dashEpisodeRegex = Regex("""(?i)(?:^|\s)-\s*([0-9]{1,4})(?:\s|$)""")
private val titleNumberBeforeDashRegex = Regex("""(?i)(?:^|\s)([0-9]{1,3})\s+-\s+\S""")
private val bracketEpisodeRegex = Regex("""(?i)\[([0-9]{1,4})(?:v[0-9]+|\s*(?:end|fin|final))?]""")
private val trailingEpisodeRegex = Regex("""(?i)(?:^|[^0-9])([0-9]{1,4})(?:v[0-9])?(?:\s|\[[0-9a-f]{8}])*$""")
private val specialEpisodeRegex = Regex(
    """(?i)(?:^|[^a-z0-9])(?:ova|oad|sp|special|pv|op|ed|ncop|nced|menu|menus|bonus|trailer|teaser|cm|creditless|yokoku|preview|remix|hope\s+side)(?:[0-9]+(?:\.[0-9]+)?)?(?:[^a-z0-9]|$)""",
)
private val supplementalEpisodeRegex = Regex(
    """(?i)(?:^|[^a-z0-9])(?:pv|op|ed|ncop|nced|menu|menus|trailer|teaser|cm|creditless|yokoku|preview|remix)(?:[0-9]+(?:\.[0-9]+)?)?(?:[^a-z0-9]|$)""",
)
private val knownMediaExtensionRegex = Regex("""(?i)\.(?:avi|flv|m2ts|m4v|mkv|mov|mp4|mpeg|mpg|ts|webm|wmv)$""")
private val leadingBracketVariantTokenRegex = Regex("""^\[([^]]+)]""")
private val variantEpisodeTokenRegex = Regex("""(?i)^(?:s[0-9]{1,2}\s*e)?[0-9]{1,4}(?:v[0-9]+|\s*(?:end|fin|final))?$""")
private val resolutionHeightRegex = Regex("""(?i)\b([0-9]{3,4})p\b""")
private val resolutionDimensionRegex = Regex("""(?i)\b[0-9]{3,4}\s*[x×]\s*([0-9]{3,4})\b""")
private val subtitleTagRegex = Regex(
    """(?i)\b(?:big5|gb|gb-cn|gb_cn|chs|cht|cht-jp|cht_jp|jp|jpn|jpsc|jptc|eng|en|multi-audio|multi-subs?|multiple subtitle)\b""",
)
private val sourceTagRegex = Regex("""(?i)\b(?:baha|web-dl|webrip|bdrip|bd|bluray|blu-ray|hdtv|tv)\b""")
private val codecTagRegex = Regex("""(?i)\b(?:avc|hevc|x264|x265|h264|h265|aac|flac|opus|dts-hd|dts)\b""")
private val releaseGroupHintRegex = Regex("""(?i)(?:sub|subs|raw|raws|fansub|studio|house|kissaten|caso|ktxp|vcb|erai|lilith|moozzi|kamigami|hysub|jysub|jyfansub)""")
private val variantContainerExtensions = setOf("avi", "flv", "m2ts", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "ts", "webm", "wmv")

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

private fun String.withoutKnownMediaExtension(): String =
    replace(knownMediaExtensionRegex, "")

private fun List<EpisodeVariant>.fieldEvidence(
    label: String,
    values: List<String>,
): String? {
    val distinctValues = values
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
    return if (distinctValues.size > 1) {
        "$label=${distinctValues.joinToString(separator = "; ")}"
    } else {
        null
    }
}

private fun String?.normalizedVariantPart(): String =
    this?.variantComparable().orEmpty()

private fun String.variantComparable(): String =
    trim()
        .lowercase()
        .replace('_', ' ')
        .map { char ->
            when {
                char.isLetterOrDigit() -> char
                else -> ' '
            }
        }
        .joinToString(separator = "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.isEpisodeToken(): Boolean =
    variantEpisodeTokenRegex.matches(trim())

private fun String.looksLikeReleaseGroup(): Boolean =
    contains('&') ||
        contains('+') ||
        releaseGroupHintRegex.containsMatchIn(this)
