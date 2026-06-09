package app.danmaku.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ExternalAnimeProvider {
    MY_ANIME_LIST,
    BANGUMI,
    DANDANPLAY,
}

@Serializable
data class ExternalAnimeId(
    val provider: ExternalAnimeProvider,
    val value: Long,
) {
    init {
        require(value > 0) { "external anime ID must be positive" }
    }

    val webUrl: String
        get() = when (provider) {
            ExternalAnimeProvider.MY_ANIME_LIST -> "https://myanimelist.net/anime/$value"
            ExternalAnimeProvider.BANGUMI -> "https://bangumi.tv/subject/$value"
            ExternalAnimeProvider.DANDANPLAY -> "https://www.dandanplay.com/bangumi/$value"
        }
}

@Serializable
data class ExternalAnimeTitleSet(
    val primary: String,
    val chinese: String? = null,
    val english: String? = null,
    val japanese: String? = null,
    val alternateNames: List<String> = emptyList(),
) {
    init {
        require(primary.isNotBlank()) { "primary title must not be blank" }
        require(chinese == null || chinese.isNotBlank()) { "chinese title must not be blank" }
        require(english == null || english.isNotBlank()) { "english title must not be blank" }
        require(japanese == null || japanese.isNotBlank()) { "japanese title must not be blank" }
        require(alternateNames.none { it.isBlank() }) { "alternate names must not be blank" }
    }

    val displayNames: List<String> =
        listOfNotNull(primary, chinese, english, japanese)
            .plus(alternateNames)
            .distinctBy(String::normalizedAnimeTitle)
}

@Serializable
data class ExternalAnimeInfo(
    val id: ExternalAnimeId,
    val titles: ExternalAnimeTitleSet,
    val episodeCount: Int? = null,
    val startYear: Int? = null,
    val imageUrl: String? = null,
    val summary: String? = null,
) {
    init {
        require(episodeCount == null || episodeCount > 0) { "episodeCount must be positive" }
        require(startYear == null || startYear in 1900..2200) { "startYear must be reasonable" }
        require(imageUrl == null || imageUrl.startsWith("https://")) { "imageUrl must be HTTPS" }
        require(summary == null || summary.isNotBlank()) { "summary must not be blank" }
    }
}

@Serializable
enum class ExternalAnimeMappingSource {
    AUTO,
    MANUAL,
}

@Serializable
data class ExternalAnimeMapping(
    val localSeriesId: String,
    val animeId: ExternalAnimeId,
    val source: ExternalAnimeMappingSource,
    val confidence: Double,
    val mappedAtEpochMs: Long,
) {
    init {
        require(localSeriesId.isNotBlank()) { "localSeriesId must not be blank" }
        require(confidence in 0.0..1.0) { "confidence must be between 0 and 1" }
        require(mappedAtEpochMs >= 0) { "mappedAtEpochMs must not be negative" }
    }
}

@Serializable
enum class ExternalAnimeListStatus {
    WATCHING,
    COMPLETED,
    ON_HOLD,
    DROPPED,
    PLAN_TO_WATCH,
}

@Serializable
data class ExternalAnimeTrackingUpdate(
    val animeId: ExternalAnimeId,
    val status: ExternalAnimeListStatus? = null,
    val watchedEpisodes: Int? = null,
    val score: Int? = null,
    val trackingEnabled: Boolean = true,
    val ratingEnabled: Boolean = true,
) {
    init {
        require(watchedEpisodes == null || watchedEpisodes >= 0) { "watchedEpisodes must not be negative" }
        require(score == null || score in 0..10) { "score must be between 0 and 10" }
        require(trackingEnabled || watchedEpisodes == null) {
            "watchedEpisodes requires trackingEnabled"
        }
        require(ratingEnabled || score == null) {
            "score requires ratingEnabled"
        }
    }
}

data class ExternalAnimeTrackingPlan(
    val updates: List<ExternalAnimeTrackingPlanUpdate>,
    val skipped: List<ExternalAnimeTrackingPlanSkip>,
) {
    init {
        require(updates.map { it.mapping.localSeriesId to it.mapping.animeId.provider }.distinct().size == updates.size) {
            "tracking plan updates must be unique by local series and provider"
        }
    }
}

data class ExternalAnimeTrackingPlanUpdate(
    val series: LibrarySeries,
    val mapping: ExternalAnimeMapping,
    val update: ExternalAnimeTrackingUpdate,
)

data class ExternalAnimeTrackingPlanSkip(
    val localSeriesId: String,
    val provider: ExternalAnimeProvider? = null,
    val reason: ExternalAnimeTrackingPlanSkipReason,
)

enum class ExternalAnimeTrackingPlanSkipReason {
    MISSING_LOCAL_SERIES,
    UNMAPPED_LOCAL_SERIES,
}

fun LibraryCatalog.externalAnimeTrackingPlan(
    mappings: List<ExternalAnimeMapping>,
    progresses: List<PlaybackProgress>,
    providers: Set<ExternalAnimeProvider> = setOf(
        ExternalAnimeProvider.MY_ANIME_LIST,
        ExternalAnimeProvider.BANGUMI,
    ),
    minimumStartedPositionMs: Long = 10_000,
    watchedRemainingMs: Long = 30_000,
): ExternalAnimeTrackingPlan {
    require(providers.isNotEmpty()) { "providers must not be empty" }
    val seriesById = groupedSeries().associateBy(LibrarySeries::id)
    val watchStatusByMediaId = watchStatusByMediaId(
        progresses = progresses,
        minimumStartedPositionMs = minimumStartedPositionMs,
        watchedRemainingMs = watchedRemainingMs,
    )
    val mappingsBySeriesId = mappings
        .filter { mapping -> mapping.animeId.provider in providers }
        .groupBy(ExternalAnimeMapping::localSeriesId)
    val updates = mappingsBySeriesId
        .flatMap { (seriesId, seriesMappings) ->
            val series = seriesById[seriesId] ?: return@flatMap emptyList()
            seriesMappings.map { mapping ->
                ExternalAnimeTrackingPlanUpdate(
                    series = series,
                    mapping = mapping,
                    update = series.externalAnimeTrackingUpdate(mapping, watchStatusByMediaId),
                )
            }
        }
        .sortedWith(
            compareBy<ExternalAnimeTrackingPlanUpdate> { it.series.title.lowercase() }
                .thenBy { it.mapping.animeId.provider.name }
                .thenBy { it.mapping.animeId.value },
        )
    val missingSeriesSkips = mappingsBySeriesId
        .filterKeys { seriesId -> seriesId !in seriesById }
        .flatMap { (seriesId, seriesMappings) ->
            seriesMappings.map { mapping ->
                ExternalAnimeTrackingPlanSkip(
                    localSeriesId = seriesId,
                    provider = mapping.animeId.provider,
                    reason = ExternalAnimeTrackingPlanSkipReason.MISSING_LOCAL_SERIES,
                )
            }
        }
    val unmappedSeriesSkips = seriesById.keys
        .filter { seriesId -> mappingsBySeriesId[seriesId].isNullOrEmpty() }
        .flatMap { seriesId ->
            providers.map { provider ->
                ExternalAnimeTrackingPlanSkip(
                    localSeriesId = seriesId,
                    provider = provider,
                    reason = ExternalAnimeTrackingPlanSkipReason.UNMAPPED_LOCAL_SERIES,
                )
            }
        }
    return ExternalAnimeTrackingPlan(
        updates = updates,
        skipped = (missingSeriesSkips + unmappedSeriesSkips).sortedWith(
            compareBy<ExternalAnimeTrackingPlanSkip> { it.localSeriesId }
                .thenBy { it.provider?.name.orEmpty() }
                .thenBy { it.reason.name },
        ),
    )
}

fun LibrarySeries.externalAnimeTrackingUpdate(
    mapping: ExternalAnimeMapping,
    watchStatusByMediaId: Map<String, LibraryWatchStatus>,
    trackingEnabled: Boolean = true,
    ratingEnabled: Boolean = true,
): ExternalAnimeTrackingUpdate {
    require(mapping.localSeriesId == id) {
        "mapping localSeriesId must match series id"
    }
    val items = seasons.flatMap(LibrarySeason::items)
    val missingStatusItem = items.firstOrNull { it.id !in watchStatusByMediaId }
    require(missingStatusItem == null) {
        "watch status is missing for ${missingStatusItem?.id}"
    }
    val states = items.map { item -> watchStatusByMediaId.getValue(item.id).state }
    val watchedEpisodes = states.count { it == LibraryWatchState.WATCHED }
    val status = when {
        watchedEpisodes == states.size -> ExternalAnimeListStatus.COMPLETED
        states.any { it == LibraryWatchState.WATCHED || it == LibraryWatchState.IN_PROGRESS } ->
            ExternalAnimeListStatus.WATCHING
        else -> ExternalAnimeListStatus.PLAN_TO_WATCH
    }
    return ExternalAnimeTrackingUpdate(
        animeId = mapping.animeId,
        status = status,
        watchedEpisodes = watchedEpisodes,
        trackingEnabled = trackingEnabled,
        ratingEnabled = ratingEnabled,
    )
}

@Serializable
data class ExternalAnimeMatchQuery(
    val title: String,
    val episodeCount: Int? = null,
    val startYear: Int? = null,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(episodeCount == null || episodeCount > 0) { "episodeCount must be positive" }
        require(startYear == null || startYear in 1900..2200) { "startYear must be reasonable" }
    }
}

data class ExternalAnimeMatchCandidate(
    val anime: ExternalAnimeInfo,
    val confidence: Double,
    val matchedTitle: String?,
)

fun rankExternalAnimeMatches(
    query: ExternalAnimeMatchQuery,
    candidates: List<ExternalAnimeInfo>,
): List<ExternalAnimeMatchCandidate> =
    candidates
        .map { candidate -> candidate.toMatchCandidate(query) }
        .filter { it.confidence > 0.0 }
        .sortedWith(
            compareByDescending<ExternalAnimeMatchCandidate> { it.confidence }
                .thenBy { it.anime.titles.primary.lowercase() }
                .thenBy { it.anime.id.provider.name }
                .thenBy { it.anime.id.value },
        )

private fun ExternalAnimeInfo.toMatchCandidate(query: ExternalAnimeMatchQuery): ExternalAnimeMatchCandidate {
    val normalizedQueryTitle = query.title.normalizedAnimeTitle()
    val titleScore = titles.displayNames
        .map { title -> title to title.matchTitleScore(normalizedQueryTitle) }
        .maxBy { it.second }
    val episodeScore = when {
        query.episodeCount == null || episodeCount == null -> 0.0
        query.episodeCount == episodeCount -> 0.15
        else -> -0.08
    }
    val yearScore = when {
        query.startYear == null || startYear == null -> 0.0
        query.startYear == startYear -> 0.05
        else -> -0.03
    }
    return ExternalAnimeMatchCandidate(
        anime = this,
        confidence = (titleScore.second + episodeScore + yearScore).coerceIn(0.0, 1.0),
        matchedTitle = titleScore.first.takeIf { titleScore.second > 0.0 },
    )
}

private fun String.matchTitleScore(normalizedQueryTitle: String): Double {
    val normalizedCandidateTitle = normalizedAnimeTitle()
    return when {
        normalizedCandidateTitle == normalizedQueryTitle -> 0.8
        normalizedCandidateTitle.contains(normalizedQueryTitle) -> 0.55
        normalizedQueryTitle.contains(normalizedCandidateTitle) -> 0.5
        else -> 0.0
    }
}

private fun String.normalizedAnimeTitle(): String =
    trim()
        .lowercase()
        .filter { it.isLetterOrDigit() }
