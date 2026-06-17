package app.danmaku.domain

import kotlinx.serialization.Serializable

@Serializable
enum class LocalAnimeListStatus {
    PLAN_TO_WATCH,
    WATCHING,
    COMPLETED,
    ON_HOLD,
    DROPPED,
}

@Serializable
data class LocalAnimeListEntry(
    val localSeriesId: String,
    val status: LocalAnimeListStatus,
    val score: Int? = null,
    val notes: String? = null,
    val updatedAtEpochMs: Long,
) {
    init {
        require(localSeriesId.isNotBlank()) { "localSeriesId must not be blank" }
        require(score == null || score in 0..10) { "score must be between 0 and 10" }
        require(notes == null || notes.isNotBlank()) { "notes must not be blank" }
        require(updatedAtEpochMs >= 0) { "updatedAtEpochMs must not be negative" }
    }
}
