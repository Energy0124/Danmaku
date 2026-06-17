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

data class ExternalAnimeListEntry(
    val animeId: ExternalAnimeId,
    val status: ExternalAnimeListStatus? = null,
    val watchedEpisodes: Int? = null,
    val score: Int? = null,
    val updatedAtEpochMs: Long? = null,
) {
    init {
        require(watchedEpisodes == null || watchedEpisodes >= 0) { "watchedEpisodes must not be negative" }
        require(score == null || score in 0..10) { "score must be between 0 and 10" }
        require(updatedAtEpochMs == null || updatedAtEpochMs >= 0) { "updatedAtEpochMs must not be negative" }
    }
}

data class ExternalAnimeTrackingPlan(
    val updates: List<ExternalAnimeTrackingPlanUpdate>,
    val skipped: List<ExternalAnimeTrackingPlanSkip>,
    val conflicts: List<ExternalAnimeTrackingPlanConflict> = emptyList(),
    val failures: List<ExternalAnimeSyncFailure> = emptyList(),
) {
    init {
        require(updates.map { it.mapping.localSeriesId to it.mapping.animeId.provider }.distinct().size == updates.size) {
            "tracking plan updates must be unique by local series and provider"
        }
    }

    val summary: ExternalAnimeTrackingPlanSummary =
        ExternalAnimeTrackingPlanSummary(
            updateCount = updates.size,
            skippedCount = skipped.size,
            conflictCount = conflicts.size,
            failureCount = failures.size,
            providerUpdateCounts = updates
                .groupingBy { it.mapping.animeId.provider }
                .eachCount(),
            skipReasonCounts = skipped
                .groupingBy(ExternalAnimeTrackingPlanSkip::reason)
                .eachCount(),
        )
}

data class ExternalAnimeTrackingPlanUpdate(
    val series: LibrarySeries,
    val mapping: ExternalAnimeMapping,
    val update: ExternalAnimeTrackingUpdate,
) {
    val label: String
        get() = "${series.title} -> ${mapping.animeId.provider.displayName} " +
            "${update.status.displayName}, ${update.watchedEpisodes ?: 0}/${series.episodeCount} watched"
}

data class ExternalAnimeTrackingPlanSkip(
    val localSeriesId: String,
    val provider: ExternalAnimeProvider? = null,
    val reason: ExternalAnimeTrackingPlanSkipReason,
) {
    val label: String
        get() {
            val providerLabel = provider?.displayName ?: "External provider"
            return "$providerLabel: ${reason.displayName} ($localSeriesId)"
    }
}

data class ExternalAnimeTrackingPlanConflict(
    val series: LibrarySeries,
    val mapping: ExternalAnimeMapping,
    val localUpdate: ExternalAnimeTrackingUpdate,
    val externalEntry: ExternalAnimeListEntry,
    val reason: ExternalAnimeTrackingPlanConflictReason,
) {
    val label: String
        get() = "${series.title} -> ${mapping.animeId.provider.displayName}: ${reason.displayName}"
}

data class ExternalAnimeSyncFailure(
    val animeId: ExternalAnimeId,
    val message: String,
    val failedAtEpochMs: Long,
    val attemptCount: Int,
    val retryAfterEpochMs: Long,
) {
    init {
        require(message.isNotBlank()) { "message must not be blank" }
        require(failedAtEpochMs >= 0) { "failedAtEpochMs must not be negative" }
        require(attemptCount > 0) { "attemptCount must be positive" }
        require(retryAfterEpochMs >= failedAtEpochMs) { "retryAfterEpochMs must not be before failedAtEpochMs" }
    }
}

data class ExternalAnimeLocalProgressImport(
    val series: LibrarySeries,
    val mapping: ExternalAnimeMapping,
    val externalEntry: ExternalAnimeListEntry,
    val localWatchedEpisodes: Int,
    val externalWatchedEpisodes: Int,
    val progressUpdates: List<PlaybackProgress>,
) {
    init {
        require(localWatchedEpisodes >= 0) { "localWatchedEpisodes must not be negative" }
        require(externalWatchedEpisodes >= 0) { "externalWatchedEpisodes must not be negative" }
    }
}

data class ExternalAnimeTrackingPlanSummary(
    val updateCount: Int,
    val skippedCount: Int,
    val conflictCount: Int = 0,
    val failureCount: Int = 0,
    val providerUpdateCounts: Map<ExternalAnimeProvider, Int>,
    val skipReasonCounts: Map<ExternalAnimeTrackingPlanSkipReason, Int>,
) {
    init {
        require(updateCount >= 0) { "updateCount must not be negative" }
        require(skippedCount >= 0) { "skippedCount must not be negative" }
        require(conflictCount >= 0) { "conflictCount must not be negative" }
        require(failureCount >= 0) { "failureCount must not be negative" }
        require(providerUpdateCounts.values.all { it >= 0 }) {
            "provider update counts must not be negative"
        }
        require(skipReasonCounts.values.all { it >= 0 }) {
            "skip reason counts must not be negative"
        }
    }

    val label: String
        get() = "$updateCount updates ready, $conflictCount conflicts, $skippedCount skipped"
}

enum class ExternalAnimeTrackingPlanSkipReason {
    MISSING_LOCAL_SERIES,
    UNMAPPED_LOCAL_SERIES,
}

enum class ExternalAnimeTrackingPlanConflictReason {
    EXTERNAL_PROGRESS_AHEAD,
}

val ExternalAnimeProvider.displayName: String
    get() = when (this) {
        ExternalAnimeProvider.MY_ANIME_LIST -> "MyAnimeList"
        ExternalAnimeProvider.BANGUMI -> "Bangumi"
        ExternalAnimeProvider.DANDANPLAY -> "dandanplay"
    }

val ExternalAnimeListStatus?.displayName: String
    get() = when (this) {
        ExternalAnimeListStatus.WATCHING -> "watching"
        ExternalAnimeListStatus.COMPLETED -> "completed"
        ExternalAnimeListStatus.ON_HOLD -> "on hold"
        ExternalAnimeListStatus.DROPPED -> "dropped"
        ExternalAnimeListStatus.PLAN_TO_WATCH -> "plan to watch"
        null -> "unchanged"
    }

val ExternalAnimeTrackingPlanSkipReason.displayName: String
    get() = when (this) {
        ExternalAnimeTrackingPlanSkipReason.MISSING_LOCAL_SERIES -> "mapped series is no longer in the library"
        ExternalAnimeTrackingPlanSkipReason.UNMAPPED_LOCAL_SERIES -> "series is not linked"
    }

val ExternalAnimeTrackingPlanConflictReason.displayName: String
    get() = when (this) {
        ExternalAnimeTrackingPlanConflictReason.EXTERNAL_PROGRESS_AHEAD -> "external progress is ahead of local progress"
    }

fun LibraryCatalog.externalAnimeTrackingPlan(
    mappings: List<ExternalAnimeMapping>,
    progresses: List<PlaybackProgress>,
    externalEntries: List<ExternalAnimeListEntry> = emptyList(),
    failures: List<ExternalAnimeSyncFailure> = emptyList(),
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
    val externalEntryByAnimeId = externalEntries.associateBy(ExternalAnimeListEntry::animeId)
    val updateCandidates = mappingsBySeriesId
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
    val conflicts = updateCandidates
        .mapNotNull { update ->
            val externalEntry = externalEntryByAnimeId[update.mapping.animeId] ?: return@mapNotNull null
            val externalWatched = externalEntry.watchedEpisodes ?: return@mapNotNull null
            val localWatched = update.update.watchedEpisodes ?: return@mapNotNull null
            if (externalWatched > localWatched) {
                ExternalAnimeTrackingPlanConflict(
                    series = update.series,
                    mapping = update.mapping,
                    localUpdate = update.update,
                    externalEntry = externalEntry,
                    reason = ExternalAnimeTrackingPlanConflictReason.EXTERNAL_PROGRESS_AHEAD,
                )
            } else {
                null
            }
        }
        .sortedWith(
            compareBy<ExternalAnimeTrackingPlanConflict> { it.series.title.lowercase() }
                .thenBy { it.mapping.animeId.provider.name }
                .thenBy { it.mapping.animeId.value },
        )
    val conflictAnimeIds = conflicts.mapTo(mutableSetOf()) { it.mapping.animeId }
    val updates = updateCandidates
        .filterNot { it.mapping.animeId in conflictAnimeIds }
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
        conflicts = conflicts,
        failures = failures.filter { failure -> failure.animeId.provider in providers },
    )
}

fun externalAnimeSyncRetryAfterEpochMs(
    failedAtEpochMs: Long,
    attemptCount: Int,
    baseDelayMs: Long = 30_000,
    maxDelayMs: Long = 30 * 60_000,
): Long {
    require(failedAtEpochMs >= 0) { "failedAtEpochMs must not be negative" }
    require(attemptCount > 0) { "attemptCount must be positive" }
    require(baseDelayMs > 0) { "baseDelayMs must be positive" }
    require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be at least baseDelayMs" }
    val multiplier = 1L shl (attemptCount - 1).coerceAtMost(30)
    val delay = (baseDelayMs * multiplier).coerceAtMost(maxDelayMs)
    return failedAtEpochMs + delay
}

fun ExternalAnimeTrackingPlanConflict.toLocalProgressImport(
    progresses: List<PlaybackProgress>,
    updatedAtEpochMs: Long,
    importedWatchedDurationMs: Long = 60_000,
    watchedRemainingMs: Long = 30_000,
): ExternalAnimeLocalProgressImport? {
    require(updatedAtEpochMs >= 0) { "updatedAtEpochMs must not be negative" }
    require(importedWatchedDurationMs > 0) { "importedWatchedDurationMs must be positive" }
    require(watchedRemainingMs >= 0) { "watchedRemainingMs must not be negative" }
    val externalWatchedEpisodes = externalEntry.watchedEpisodes
        ?.coerceIn(0, series.episodeCount)
        ?: return null
    val localWatchedEpisodes = localUpdate.watchedEpisodes ?: 0
    if (externalWatchedEpisodes <= localWatchedEpisodes) {
        return null
    }
    val progressByMediaId = progresses.latestByMediaId()
    val updates = series.seasons
        .flatMap(LibrarySeason::items)
        .take(externalWatchedEpisodes)
        .filter { item ->
            item.watchStatus(
                progress = progressByMediaId[item.id],
                watchedRemainingMs = watchedRemainingMs,
            ).state != LibraryWatchState.WATCHED
        }
        .map { item ->
            val existingProgress = progressByMediaId[item.id]
            val durationMs = existingProgress
                ?.durationMs
                ?.takeIf { it > 0 }
                ?: importedWatchedDurationMs
            PlaybackProgress(
                mediaId = item.id,
                positionMs = durationMs,
                durationMs = durationMs,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        }
    if (updates.isEmpty()) {
        return null
    }
    return ExternalAnimeLocalProgressImport(
        series = series,
        mapping = mapping,
        externalEntry = externalEntry,
        localWatchedEpisodes = localWatchedEpisodes,
        externalWatchedEpisodes = externalWatchedEpisodes,
        progressUpdates = updates,
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
