package app.danmaku.domain

data class LibraryPlaybackProgressItem(
    val mediaItem: LibraryMediaItem,
    val progress: PlaybackProgress,
)

fun LibraryCatalog.continueWatchingItems(
    progresses: List<PlaybackProgress>,
    limit: Int = 8,
    minimumResumePositionMs: Long = 10_000,
    minimumRemainingMs: Long = 30_000,
): List<LibraryPlaybackProgressItem> {
    require(limit >= 0) { "limit must not be negative" }
    require(minimumResumePositionMs >= 0) { "minimumResumePositionMs must not be negative" }
    require(minimumRemainingMs >= 0) { "minimumRemainingMs must not be negative" }
    if (limit == 0) {
        return emptyList()
    }
    val progressByMediaId = progresses.latestByMediaId()
    return items
        .mapNotNull { item ->
            val progress = progressByMediaId[item.id]
                ?.takeIf {
                    it.resumePositionMs(
                        minimumPositionMs = minimumResumePositionMs,
                        minimumRemainingMs = minimumRemainingMs,
                    ) != null
                }
                ?: return@mapNotNull null
            LibraryPlaybackProgressItem(item, progress)
        }
        .sortedByDescending { it.progress.updatedAtEpochMs }
        .take(limit)
}

fun LibraryCatalog.recentlyWatchedItems(
    progresses: List<PlaybackProgress>,
    limit: Int = 8,
): List<LibraryPlaybackProgressItem> {
    require(limit >= 0) { "limit must not be negative" }
    if (limit == 0) {
        return emptyList()
    }
    val itemsById = items.associateBy(LibraryMediaItem::id)
    return progresses
        .latestByMediaId()
        .values
        .mapNotNull { progress ->
            val item = itemsById[progress.mediaId] ?: return@mapNotNull null
            LibraryPlaybackProgressItem(item, progress)
        }
        .sortedByDescending { it.progress.updatedAtEpochMs }
        .take(limit)
}

internal fun List<PlaybackProgress>.latestByMediaId(): Map<String, PlaybackProgress> =
    groupBy(PlaybackProgress::mediaId).mapValues { (_, progresses) ->
        progresses.maxBy { it.updatedAtEpochMs }
    }
