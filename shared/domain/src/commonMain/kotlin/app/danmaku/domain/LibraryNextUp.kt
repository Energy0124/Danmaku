package app.danmaku.domain

data class LibraryNextUpItem(
    val mediaItem: LibraryMediaItem,
    val reason: LibraryNextUpReason,
    val progress: PlaybackProgress? = null,
    val sourceProgress: PlaybackProgress? = progress,
)

enum class LibraryNextUpReason {
    RESUME,
    NEXT_EPISODE,
    START,
}

fun LibraryCatalog.nextUpItems(
    progresses: List<PlaybackProgress>,
    limit: Int = 8,
    minimumResumePositionMs: Long = 10_000,
    minimumRemainingMs: Long = 30_000,
): List<LibraryNextUpItem> {
    require(limit >= 0) { "limit must not be negative" }
    require(minimumResumePositionMs >= 0) { "minimumResumePositionMs must not be negative" }
    require(minimumRemainingMs >= 0) { "minimumRemainingMs must not be negative" }
    if (limit == 0 || items.isEmpty()) {
        return emptyList()
    }

    val newestProgressByMediaId = progresses
        .groupBy(PlaybackProgress::mediaId)
        .mapValues { (_, itemProgresses) -> itemProgresses.maxBy(PlaybackProgress::updatedAtEpochMs) }
    val itemsById = items.associateBy(LibraryMediaItem::id)
    val candidates = mutableListOf<LibraryNextUpItem>()

    progresses
        .asSequence()
        .filter { it.mediaId in itemsById }
        .sortedByDescending(PlaybackProgress::updatedAtEpochMs)
        .forEach { progress ->
            val item = itemsById.getValue(progress.mediaId)
            val resumePosition = progress.resumePositionMs(
                minimumPositionMs = minimumResumePositionMs,
                minimumRemainingMs = minimumRemainingMs,
            )
            if (resumePosition != null) {
                candidates += LibraryNextUpItem(
                    mediaItem = item,
                    reason = LibraryNextUpReason.RESUME,
                    progress = progress,
                )
            } else if (progress.isNearEnd(minimumResumePositionMs, minimumRemainingMs)) {
                nextItem(progress.mediaId)
                    ?.takeIf { nextItem -> newestProgressByMediaId[nextItem.id] == null }
                    ?.let { nextItem ->
                        candidates += LibraryNextUpItem(
                            mediaItem = nextItem,
                            reason = LibraryNextUpReason.NEXT_EPISODE,
                            sourceProgress = progress,
                        )
                    }
            }
        }

    if (candidates.isEmpty()) {
        candidates += LibraryNextUpItem(
            mediaItem = items.first(),
            reason = LibraryNextUpReason.START,
        )
    }

    return candidates
        .distinctBy { it.mediaItem.id }
        .take(limit)
}

private fun PlaybackProgress.isNearEnd(
    minimumResumePositionMs: Long,
    minimumRemainingMs: Long,
): Boolean =
    durationMs != null &&
        positionMs >= minimumResumePositionMs &&
        durationMs - positionMs < minimumRemainingMs
