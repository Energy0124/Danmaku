package app.danmaku.desktop

import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeason
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LocalAnimeListEntry
import app.danmaku.domain.LocalAnimeListStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLibraryWatchListFilterTest {
    @Test
    fun statusFiltersMatchOnlyEntriesWithTheSelectedStatus() {
        val watchingEntry = LocalAnimeListEntry(
            localSeriesId = "series-1",
            status = LocalAnimeListStatus.WATCHING,
            updatedAtEpochMs = 10,
        )

        assertTrue(LocalWatchListFilter.WATCHING.matches(watchingEntry))
        assertFalse(LocalWatchListFilter.COMPLETED.matches(watchingEntry))
        assertFalse(LocalWatchListFilter.WATCHING.matches(null))
        assertTrue(LocalWatchListFilter.ANY.matches(null))
    }

    @Test
    fun untrackedFilterMatchesOnlySeriesWithoutLocalEntries() {
        val entry = LocalAnimeListEntry(
            localSeriesId = "series-1",
            status = LocalAnimeListStatus.PLAN_TO_WATCH,
            updatedAtEpochMs = 10,
        )

        assertTrue(LocalWatchListFilter.UNTRACKED.matches(null))
        assertFalse(LocalWatchListFilter.UNTRACKED.matches(entry))
    }

    @Test
    fun filtersEpisodesThroughTheirOwningSeries() {
        val series = listOf(
            librarySeries("series-1", mediaItem("episode-1")),
            librarySeries("series-2", mediaItem("episode-2")),
        )
        val episodes = series.flatMap { it.seasons }.flatMap { it.items }
        val entries = mapOf(
            "series-1" to LocalAnimeListEntry(
                localSeriesId = "series-1",
                status = LocalAnimeListStatus.WATCHING,
                updatedAtEpochMs = 10,
            ),
        )

        assertEquals(
            listOf("episode-1"),
            episodes
                .filterByLocalWatchList(series.seriesIdByMediaId(), entries, LocalWatchListFilter.WATCHING)
                .map(LibraryMediaItem::id),
        )
        assertEquals(
            listOf("episode-2"),
            episodes
                .filterByLocalWatchList(series.seriesIdByMediaId(), entries, LocalWatchListFilter.UNTRACKED)
                .map(LibraryMediaItem::id),
        )
    }

    private fun librarySeries(id: String, item: LibraryMediaItem): LibrarySeries =
        LibrarySeries(
            id = id,
            title = id,
            seasons = listOf(
                LibrarySeason(
                    id = "$id-season",
                    label = "Season unknown",
                    sortKey = Int.MAX_VALUE,
                    items = listOf(item),
                ),
            ),
        )

    private fun mediaItem(id: String): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = id,
            episodeTitle = id,
            relativePath = "$id.mkv",
            sizeBytes = 100,
            mediaType = "video/x-matroska",
            streamPath = "/library/$id",
            indexedAtEpochMs = 20,
        )
}
