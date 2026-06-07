package app.danmaku.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ExternalAnimeProvider {
    MY_ANIME_LIST,
    BANGUMI,
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
