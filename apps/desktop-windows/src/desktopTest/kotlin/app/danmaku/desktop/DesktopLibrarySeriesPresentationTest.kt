package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.LibraryAnimeMetadata
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.groupedSeries
import java.nio.file.Path
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLibrarySeriesPresentationTest {
    @Test
    fun groupsRecentSeriesByNewestIndexedMonth() {
        val series = LibraryCatalog(
            rootName = "Test",
            indexedAtEpochMs = 0,
            items = listOf(
                item("one", "One", "2026-07-13T10:00:00Z"),
                item("two", "Two", "2026-06-03T10:00:00Z"),
            ),
        ).groupedSeries()

        val groups = series.groupForLibraryPresentation(
            mode = LibrarySeriesViewMode.RECENT,
            roots = emptyList(),
            rootIdByMediaId = emptyMap(),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(listOf("2026-07", "2026-06"), groups.map { it.value })
    }

    @Test
    fun groupsReleaseSeasonByMetadataYear() {
        val series = LibraryCatalog(
            rootName = "Test",
            indexedAtEpochMs = 0,
            items = listOf(
                item("one", "One", "2026-07-13T10:00:00Z", startYear = 2026),
                item("two", "Two", "2025-06-03T10:00:00Z", startYear = 2025),
            ),
        ).groupedSeries()

        val groups = series.groupForLibraryPresentation(
            mode = LibrarySeriesViewMode.SEASON,
            roots = emptyList(),
            rootIdByMediaId = emptyMap(),
        )

        assertEquals(listOf("2026", "2025"), groups.map { it.value })
    }

    @Test
    fun resolvesTheMostSpecificRegisteredFolder() {
        val broad = root("broad", "S:/Anime")
        val specific = root("specific", "S:/Anime/Seasonal")

        assertEquals(
            specific.id,
            libraryRootIdForPath(Path.of("S:/Anime/Seasonal/Show/01.mkv"), listOf(broad, specific)),
        )
    }

    private fun item(
        id: String,
        seriesTitle: String,
        indexedAt: String,
        startYear: Int? = null,
    ): LibraryMediaItem = LibraryMediaItem(
        id = id,
        seriesTitle = seriesTitle,
        episodeTitle = "Episode 1",
        relativePath = "$seriesTitle/01.mkv",
        sizeBytes = 1,
        mediaType = "video/mp4",
        streamPath = "/stream/$id",
        indexedAtEpochMs = java.time.Instant.parse(indexedAt).toEpochMilli(),
        animeMetadata = startYear?.let {
            LibraryAnimeMetadata(
                animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, it.toLong()),
                displayTitle = seriesTitle,
                primaryTitle = seriesTitle,
                startYear = it,
            )
        },
    )

    private fun root(id: String, path: String): DesktopLibraryRoot = DesktopLibraryRoot(
        id = id,
        path = Path.of(path),
        displayName = id,
        provenance = DesktopLibraryRootProvenance.USER_SELECTED,
        state = DesktopLibraryRootState.AVAILABLE,
        addedAtEpochMs = 1,
    )
}
