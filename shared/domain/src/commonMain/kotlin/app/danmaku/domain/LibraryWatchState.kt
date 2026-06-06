package app.danmaku.domain

data class LibraryWatchStatus(
    val mediaItem: LibraryMediaItem,
    val state: LibraryWatchState,
    val progress: PlaybackProgress? = null,
)

enum class LibraryWatchState {
    NEW,
    IN_PROGRESS,
    WATCHED,
}

fun LibraryCatalog.watchStatusByMediaId(
    progresses: List<PlaybackProgress>,
    minimumStartedPositionMs: Long = 10_000,
    watchedRemainingMs: Long = 30_000,
): Map<String, LibraryWatchStatus> {
    require(minimumStartedPositionMs >= 0) {
        "minimumStartedPositionMs must not be negative"
    }
    require(watchedRemainingMs >= 0) { "watchedRemainingMs must not be negative" }
    val progressByMediaId = progresses.latestByMediaId()
    return items.associate { item ->
        item.id to item.watchStatus(
            progress = progressByMediaId[item.id],
            minimumStartedPositionMs = minimumStartedPositionMs,
            watchedRemainingMs = watchedRemainingMs,
        )
    }
}

fun LibraryMediaItem.watchStatus(
    progress: PlaybackProgress?,
    minimumStartedPositionMs: Long = 10_000,
    watchedRemainingMs: Long = 30_000,
): LibraryWatchStatus {
    require(minimumStartedPositionMs >= 0) {
        "minimumStartedPositionMs must not be negative"
    }
    require(watchedRemainingMs >= 0) { "watchedRemainingMs must not be negative" }
    val state = when {
        progress == null -> LibraryWatchState.NEW
        progress.isWatched(watchedRemainingMs) -> LibraryWatchState.WATCHED
        progress.resumePositionMs(
            minimumPositionMs = minimumStartedPositionMs,
            minimumRemainingMs = watchedRemainingMs,
        ) != null -> LibraryWatchState.IN_PROGRESS
        else -> LibraryWatchState.NEW
    }
    return LibraryWatchStatus(mediaItem = this, state = state, progress = progress)
}

private fun PlaybackProgress.isWatched(watchedRemainingMs: Long): Boolean =
    durationMs?.let { duration ->
        positionMs > 0 && duration - positionMs <= watchedRemainingMs
    } == true
