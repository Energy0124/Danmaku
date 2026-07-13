package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId

internal enum class LibrarySeriesGroupKind {
    ALL,
    RECENT_MONTH,
    RELEASE_YEAR,
    FOLDER,
    UNKNOWN,
}

internal data class LibrarySeriesGroup(
    val key: String,
    val kind: LibrarySeriesGroupKind,
    val value: String?,
    val series: List<LibrarySeries>,
)

internal fun libraryRootIdForPath(
    path: Path,
    roots: List<DesktopLibraryRoot>,
): String? {
    val normalizedPath = path.toAbsolutePath().normalize()
    return roots
        .filter { normalizedPath.startsWith(it.normalizedPath) }
        .maxByOrNull { it.normalizedPath.nameCount }
        ?.id
}

internal fun List<LibraryMediaItem>.filterToLibraryRoot(
    rootId: String?,
    rootIdByMediaId: Map<String, String>,
): List<LibraryMediaItem> =
    if (rootId == null) this else filter { rootIdByMediaId[it.id] == rootId }

internal fun LibrarySeries.latestIndexedAtEpochMs(): Long =
    seasons.flatMap { it.items }.maxOfOrNull(LibraryMediaItem::indexedAtEpochMs) ?: 0L

internal fun LibrarySeries.releaseYear(): Int? =
    seasons
        .asSequence()
        .flatMap { it.items.asSequence() }
        .mapNotNull { it.animeMetadata?.startYear }
        .groupingBy { it }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        ?.key

internal fun List<LibrarySeries>.groupForLibraryPresentation(
    mode: LibrarySeriesViewMode,
    roots: List<DesktopLibraryRoot>,
    rootIdByMediaId: Map<String, String>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<LibrarySeriesGroup> = when (mode) {
    LibrarySeriesViewMode.ALL -> listOf(
        LibrarySeriesGroup(
            key = "all",
            kind = LibrarySeriesGroupKind.ALL,
            value = null,
            series = sortedBy { it.title.lowercase() },
        ),
    )
    LibrarySeriesViewMode.RECENT ->
        sortedByDescending(LibrarySeries::latestIndexedAtEpochMs)
            .groupBy { librarySeries ->
                librarySeries.latestIndexedAtEpochMs()
                    .takeIf { it > 0L }
                    ?.let { epochMs ->
                        val date = Instant.ofEpochMilli(epochMs).atZone(zoneId)
                        "%04d-%02d".format(date.year, date.monthValue)
                    }
            }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String?, List<LibrarySeries>>> { it.key != null }.thenByDescending { it.key })
            .map { (month, series) ->
                LibrarySeriesGroup(
                    key = month?.let { "recent:$it" } ?: "recent:unknown",
                    kind = if (month == null) LibrarySeriesGroupKind.UNKNOWN else LibrarySeriesGroupKind.RECENT_MONTH,
                    value = month,
                    series = series,
                )
            }
    LibrarySeriesViewMode.SEASON ->
        groupBy(LibrarySeries::releaseYear)
            .entries
            .sortedWith(compareByDescending<Map.Entry<Int?, List<LibrarySeries>>> { it.key != null }.thenByDescending { it.key })
            .map { (year, series) ->
                LibrarySeriesGroup(
                    key = year?.let { "season:$it" } ?: "season:unknown",
                    kind = if (year == null) LibrarySeriesGroupKind.UNKNOWN else LibrarySeriesGroupKind.RELEASE_YEAR,
                    value = year?.toString(),
                    series = series.sortedBy { it.title.lowercase() },
                )
            }
    LibrarySeriesViewMode.FOLDER -> {
        val rootById = roots.associateBy(DesktopLibraryRoot::id)
        val grouped = linkedMapOf<String?, MutableList<LibrarySeries>>()
        forEach { librarySeries ->
            val rootIds = librarySeries.seasons
                .flatMap { it.items }
                .mapNotNull { rootIdByMediaId[it.id] }
                .distinct()
            if (rootIds.isEmpty()) {
                grouped.getOrPut(null) { mutableListOf() }.add(librarySeries)
            } else {
                rootIds.forEach { rootId -> grouped.getOrPut(rootId) { mutableListOf() }.add(librarySeries) }
            }
        }
        grouped.entries
            .sortedWith(compareBy<Map.Entry<String?, MutableList<LibrarySeries>>> { it.key == null }.thenBy { rootById[it.key]?.displayName })
            .map { (rootId, series) ->
                LibrarySeriesGroup(
                    key = rootId?.let { "folder:$it" } ?: "folder:unknown",
                    kind = if (rootId == null) LibrarySeriesGroupKind.UNKNOWN else LibrarySeriesGroupKind.FOLDER,
                    value = rootId?.let { rootById[it]?.displayName },
                    series = series.sortedBy { it.title.lowercase() },
                )
            }
    }
}
