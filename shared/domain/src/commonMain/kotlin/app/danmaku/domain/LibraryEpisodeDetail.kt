package app.danmaku.domain

data class LibraryEpisodeDetail(
    val mediaItem: LibraryMediaItem,
    val series: LibrarySeries,
    val season: LibrarySeason,
    val watchStatus: LibraryWatchStatus,
    val previousItem: LibraryMediaItem?,
    val nextItem: LibraryMediaItem?,
)

fun LibraryCatalog.episodeDetail(
    mediaId: String,
    progresses: List<PlaybackProgress> = emptyList(),
): LibraryEpisodeDetail? {
    require(mediaId.isNotBlank()) { "mediaId must not be blank" }
    val watchStatusById = watchStatusByMediaId(progresses)
    val series = groupedSeries()
        .firstOrNull { summary ->
            summary.seasons.any { season ->
                season.items.any { item -> item.id == mediaId }
            }
        }
        ?: return null
    val season = series.seasons
        .first { season -> season.items.any { item -> item.id == mediaId } }
    val item = season.items.first { item -> item.id == mediaId }
    return LibraryEpisodeDetail(
        mediaItem = item,
        series = series,
        season = season,
        watchStatus = watchStatusById.getValue(mediaId),
        previousItem = previousItem(mediaId),
        nextItem = nextItem(mediaId),
    )
}
