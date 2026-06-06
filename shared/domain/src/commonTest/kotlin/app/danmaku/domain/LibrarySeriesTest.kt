package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySeriesTest {
    @Test
    fun groupsCatalogItemsIntoSeriesOrderedByEpisodeCountThenTitle() {
        val catalog = catalogOf(
            item(id = "b1", seriesTitle = "Beta Show", episodeTitle = "Episode 01"),
            item(id = "a1", seriesTitle = "Alpha Show", episodeTitle = "Episode 01"),
            item(id = "a2", seriesTitle = "Alpha Show", episodeTitle = "Episode 02"),
        )

        val series = catalog.groupedSeries()

        assertEquals(listOf("Alpha Show", "Beta Show"), series.map { it.title })
        assertEquals(listOf(2, 1), series.map { it.episodeCount })
    }

    @Test
    fun infersSeasonsFromEpisodeTitlesAndRelativePaths() {
        val catalog = catalogOf(
            item(
                id = "s2e2",
                seriesTitle = "Example Show",
                episodeTitle = "S02E02",
                relativePath = "Example Show/Season 2/Episode 02.mkv",
            ),
            item(
                id = "s1e2",
                seriesTitle = "Example Show",
                episodeTitle = "S01E02",
                relativePath = "Example Show/Season 1/Episode 02.mkv",
            ),
            item(
                id = "s1e1",
                seriesTitle = "Example Show",
                episodeTitle = "S01E01",
                relativePath = "Example Show/Season 1/Episode 01.mkv",
            ),
        )

        val seasons = catalog.groupedSeries().single().seasons

        assertEquals(listOf("Season 1", "Season 2"), seasons.map { it.label })
        assertEquals(listOf("s1e1", "s1e2"), seasons[0].items.map { it.id })
        assertEquals(listOf("s2e2"), seasons[1].items.map { it.id })
    }

    @Test
    fun keepsUnmatchedItemsInUnknownSeason() {
        val catalog = catalogOf(
            item(
                id = "ova",
                seriesTitle = "Example Show",
                episodeTitle = "OVA",
                relativePath = "Example Show/OVA.mkv",
            ),
        )

        val season = catalog.groupedSeries().single().seasons.single()

        assertEquals("Season unknown", season.label)
        assertEquals(Int.MAX_VALUE, season.sortKey)
        assertEquals(listOf("ova"), season.items.map { it.id })
    }

    @Test
    fun exposesSubtitleAndSizeTotals() {
        val catalog = catalogOf(
            item(
                id = "one",
                seriesTitle = "Example Show",
                episodeTitle = "Episode 01",
                sizeBytes = 100,
                subtitleCount = 2,
            ),
            item(
                id = "two",
                seriesTitle = "Example Show",
                episodeTitle = "Episode 02",
                sizeBytes = 200,
                subtitleCount = 1,
            ),
        )

        val series = catalog.groupedSeries().single()

        assertEquals("example-show", series.id)
        assertEquals(2, series.episodeCount)
        assertEquals(3, series.subtitleTrackCount)
        assertEquals(300, series.totalSizeBytes)
    }

    private fun catalogOf(vararg items: LibraryMediaItem): LibraryCatalog =
        LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = items.toList(),
        )

    private fun item(
        id: String,
        seriesTitle: String,
        episodeTitle: String,
        relativePath: String = "$seriesTitle/$episodeTitle.mkv",
        sizeBytes: Long = 123,
        subtitleCount: Int = 0,
    ): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = seriesTitle,
            episodeTitle = episodeTitle,
            relativePath = relativePath,
            sizeBytes = sizeBytes,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
            subtitles = (1..subtitleCount).map { index ->
                LibrarySubtitleTrack(
                    id = "$id-subtitle-$index",
                    label = "Subtitle $index",
                    relativePath = "$seriesTitle/$episodeTitle.$index.ass",
                    mediaType = "text/x-ssa",
                    streamPath = "/subtitles/$id-$index",
                )
            },
        )
}
