package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibraryCatalogQueryTest {
    @Test
    fun filtersByMultiTermSearchAcrossSeriesEpisodeAndPath() {
        val catalog = LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = listOf(
                item(id = "one", seriesTitle = "Example Show", episodeTitle = "Episode 01"),
                item(id = "two", seriesTitle = "Other Show", episodeTitle = "Example OVA"),
                item(id = "three", seriesTitle = "Example Show", episodeTitle = "Episode 02"),
            ),
        )

        assertEquals(
            listOf("three"),
            catalog.filteredItems(LibraryCatalogQuery(searchText = "example 02")).map { it.id },
        )
    }

    @Test
    fun filtersToItemsWithSubtitles() {
        val catalog = LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = listOf(
                item(id = "one", seriesTitle = "No Subs", episodeTitle = "Episode 01"),
                item(
                    id = "two",
                    seriesTitle = "Subbed",
                    episodeTitle = "Episode 01",
                    subtitles = listOf(
                        LibrarySubtitleTrack(
                            id = "subtitle-id",
                            label = "English",
                            relativePath = "Subbed/Episode 01.en.ass",
                            mediaType = "text/x-ssa",
                            streamPath = "/subtitles/subtitle-id",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("two"),
            catalog
                .filteredItems(LibraryCatalogQuery(subtitleFilter = LibrarySubtitleFilter.WITH_SUBTITLES))
                .map { it.id },
        )
    }

    @Test
    fun sortsByTitleThenEpisodeByDefault() {
        val catalog = LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = listOf(
                item(id = "two", seriesTitle = "B Show", episodeTitle = "Episode 02"),
                item(id = "one", seriesTitle = "A Show", episodeTitle = "Episode 01"),
                item(id = "three", seriesTitle = "B Show", episodeTitle = "Episode 01"),
            ),
        )

        assertEquals(
            listOf("one", "three", "two"),
            catalog.filteredItems(LibraryCatalogQuery()).map { it.id },
        )
    }

    @Test
    fun sortsByPathWhenRequested() {
        val catalog = LibraryCatalog(
            rootName = "Anime",
            indexedAtEpochMs = 123,
            items = listOf(
                item(id = "two", seriesTitle = "B", episodeTitle = "B", relativePath = "Z/B.mkv"),
                item(id = "one", seriesTitle = "A", episodeTitle = "A", relativePath = "A/A.mkv"),
            ),
        )

        assertEquals(
            listOf("one", "two"),
            catalog.filteredItems(LibraryCatalogQuery(sort = LibraryCatalogSort.PATH)).map { it.id },
        )
    }

    @Test
    fun rejectsNullBytesInSearchText() {
        assertFailsWith<IllegalArgumentException> {
            LibraryCatalogQuery(searchText = "bad\u0000query")
        }
    }

    private fun item(
        id: String,
        seriesTitle: String,
        episodeTitle: String,
        relativePath: String = "$seriesTitle/$episodeTitle.mkv",
        subtitles: List<LibrarySubtitleTrack> = emptyList(),
    ): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = seriesTitle,
            episodeTitle = episodeTitle,
            relativePath = relativePath,
            sizeBytes = 123,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
            subtitles = subtitles,
        )
}
