package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LocalAnimeListEntry
import app.danmaku.domain.LocalAnimeListStatus

internal enum class LocalWatchListFilter {
    ANY,
    PLAN_TO_WATCH,
    WATCHING,
    COMPLETED,
    ON_HOLD,
    DROPPED,
    UNTRACKED,
}

internal fun LocalWatchListFilter.localizedLabel(strings: DesktopStrings): String =
    when (this) {
        LocalWatchListFilter.ANY -> strings.anyWatchListStatusAction
        LocalWatchListFilter.PLAN_TO_WATCH -> LocalAnimeListStatus.PLAN_TO_WATCH.localizedLabel(strings)
        LocalWatchListFilter.WATCHING -> LocalAnimeListStatus.WATCHING.localizedLabel(strings)
        LocalWatchListFilter.COMPLETED -> LocalAnimeListStatus.COMPLETED.localizedLabel(strings)
        LocalWatchListFilter.ON_HOLD -> LocalAnimeListStatus.ON_HOLD.localizedLabel(strings)
        LocalWatchListFilter.DROPPED -> LocalAnimeListStatus.DROPPED.localizedLabel(strings)
        LocalWatchListFilter.UNTRACKED -> strings.untrackedWatchListStatusAction
    }

internal fun LocalWatchListFilter.matches(entry: LocalAnimeListEntry?): Boolean =
    when (this) {
        LocalWatchListFilter.ANY -> true
        LocalWatchListFilter.PLAN_TO_WATCH -> entry?.status == LocalAnimeListStatus.PLAN_TO_WATCH
        LocalWatchListFilter.WATCHING -> entry?.status == LocalAnimeListStatus.WATCHING
        LocalWatchListFilter.COMPLETED -> entry?.status == LocalAnimeListStatus.COMPLETED
        LocalWatchListFilter.ON_HOLD -> entry?.status == LocalAnimeListStatus.ON_HOLD
        LocalWatchListFilter.DROPPED -> entry?.status == LocalAnimeListStatus.DROPPED
        LocalWatchListFilter.UNTRACKED -> entry == null
    }

internal fun List<LibrarySeries>.seriesIdByMediaId(): Map<String, String> =
    flatMap { series ->
        series.seasons.flatMap { season ->
            season.items.map { item -> item.id to series.id }
        }
    }.toMap()

internal fun List<LibraryMediaItem>.filterByLocalWatchList(
    seriesIdByMediaId: Map<String, String>,
    localAnimeListEntryBySeriesId: Map<String, LocalAnimeListEntry>,
    filter: LocalWatchListFilter,
): List<LibraryMediaItem> =
    if (filter == LocalWatchListFilter.ANY) {
        this
    } else {
        filter { item ->
            val seriesId = seriesIdByMediaId[item.id] ?: return@filter false
            filter.matches(localAnimeListEntryBySeriesId[seriesId])
        }
    }
