package app.danmaku.tv

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.LibraryAnimeMetadata
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBrowsePresenterTest {
    private val presenter = TvBrowsePresenter()

    @Test
    fun sixThousandItemCatalogHasNoSeriesTruncation() {
        val catalog = LibraryCatalog(
            rootName = "Stress library",
            indexedAtEpochMs = 1,
            items = (0 until 6_000).map { index ->
                mediaItem(
                    id = "item-$index",
                    series = "Series ${index / 20}",
                    episode = index % 20,
                    indexedAt = index.toLong(),
                )
            },
        )

        val result = presenter.present(
            session = TvSessionUiState(catalog = catalog),
            query = TvBrowseQuery(),
        )

        assertEquals(300, result.librarySeries.size)
        assertEquals(6_000, result.filteredItems.size)
        assertEquals(300, result.seriesById.size)
        assertEquals(6_000, result.seriesIdByMediaId.size)
    }

    @Test
    fun homeRailsAreDistinctAndHeroPrefersContinueWatching() {
        val items = (0 until 8).map { mediaItem("item-$it", "Series $it", it, it.toLong()) }
        val progress = PlaybackProgress(
            mediaId = "item-3",
            positionMs = 60_000,
            durationMs = 1_200_000,
            updatedAtEpochMs = 5_000,
        )
        val result = presenter.present(
            session = TvSessionUiState(
                catalog = LibraryCatalog("Library", 1, items),
                playbackProgresses = listOf(progress),
            ),
            query = TvBrowseQuery(),
        )

        assertEquals("item-3", result.heroItem?.id)
        val railIds = buildList {
            addAll(result.continueWatching.map { it.mediaItem.id })
            addAll(result.nextUp.map { it.mediaItem.id })
            addAll(result.recentlyWatched.map { it.mediaItem.id })
            addAll(result.recentlyAdded.map { it.id })
        }
        assertEquals(railIds.distinct(), railIds)
        assertFalse("item-3" in railIds)
    }

    @Test
    fun searchFavoritesAndSubtitleFiltersStayIndependentFromLibrarySearch() {
        val favorite = mediaItem("favorite", "Blue Archive", 1, 2, hasSubtitle = true)
        val other = mediaItem("other", "Red Garden", 2, 1)
        val session = TvSessionUiState(
            catalog = LibraryCatalog("Library", 1, listOf(favorite, other)),
            favoriteMediaIds = setOf(favorite.id),
        )

        val result = presenter.present(
            session,
            TvBrowseQuery(
                searchText = "Blue",
                favoriteFilter = LibraryFavoriteFilter.FAVORITES_ONLY,
            ),
        )

        assertEquals(listOf(favorite.id), result.filteredItems.map { it.id })
        assertEquals(2, result.librarySeries.size)
        assertEquals(1, result.favoriteSeries.size)
        assertTrue(result.favoriteSeries.single().title.contains("Blue"))
    }

    @Test
    fun librarySupportsReleaseYearAndWatchDateOrdering() {
        val older = mediaItem("older", "Older", 1, 100, year = 2022, animeId = 1)
        val current = mediaItem("current", "Current", 1, 200, year = 2025, animeId = 2)
        val unwatched = mediaItem("unwatched", "Unwatched", 1, 300, year = 2024, animeId = 3)
        val session = TvSessionUiState(
            catalog = LibraryCatalog("Library", 1, listOf(older, current, unwatched)),
            playbackProgresses = listOf(
                PlaybackProgress("older", 10, 100, 900),
                PlaybackProgress("current", 10, 100, 1_000),
            ),
        )

        val watchedOrder = presenter.present(
            session,
            TvBrowseQuery(sort = TvLibrarySort.LAST_WATCHED),
        )
        assertEquals(
            listOf("Current", "Older", "Unwatched"),
            watchedOrder.librarySeries.map { it.title },
        )
        assertEquals(listOf(2025, 2024, 2022), watchedOrder.availableReleaseYears)

        val season = presenter.present(
            session,
            TvBrowseQuery(
                sort = TvLibrarySort.RELEASE_YEAR,
                releaseYear = 2024,
            ),
        )
        assertEquals(listOf("Unwatched"), season.librarySeries.map { it.title })
    }

    @Test
    fun folderBrowserSupportsMultipleRootsAndNestedFiles() {
        val catalog = LibraryCatalog(
            rootName = "Merged",
            indexedAtEpochMs = 1,
            items = listOf(
                mediaItem(
                    "a",
                    "Alpha",
                    1,
                    1,
                    rootLabel = "M:\\Anime",
                    relativePath = "Alpha/Season 1/Episode 1.mkv",
                ),
                mediaItem(
                    "b",
                    "Beta",
                    1,
                    1,
                    rootLabel = "D:\\Downloads",
                    relativePath = "Beta/Episode 1.mkv",
                ),
            ),
        )

        val roots = catalog.folderListing(emptyList())
        assertEquals(listOf("M:\\Anime", "D:\\Downloads"), roots.folders.map { it.name })
        assertTrue(roots.files.isEmpty())

        val alpha = catalog.folderListing(listOf("M:\\Anime", "Alpha"))
        assertEquals(listOf("Season 1"), alpha.folders.map { it.name })
        assertEquals(1, alpha.folders.single().itemCount)

        val season = catalog.folderListing(listOf("M:\\Anime", "Alpha", "Season 1"))
        assertEquals(listOf("a"), season.files.map { it.id })
    }

    private fun mediaItem(
        id: String,
        series: String,
        episode: Int,
        indexedAt: Long,
        hasSubtitle: Boolean = false,
        rootLabel: String? = null,
        relativePath: String = "$series/Episode $episode.mkv",
        year: Int? = null,
        animeId: Long? = null,
    ): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = series,
            episodeTitle = "Episode $episode",
            relativePath = relativePath,
            rootLabel = rootLabel,
            sizeBytes = 1,
            mediaType = "video/x-matroska",
            streamPath = "/media/$id",
            indexedAtEpochMs = indexedAt,
            subtitles = if (hasSubtitle) {
                listOf(
                    app.danmaku.domain.LibrarySubtitleTrack(
                        id = "$id-sub",
                        label = "English",
                        relativePath = "$series/Episode $episode.ass",
                        mediaType = "text/x-ass",
                        streamPath = "/subtitles/$id-sub",
                    ),
                )
            } else {
                emptyList()
            },
            animeMetadata = animeId?.let {
                LibraryAnimeMetadata(
                    animeId = ExternalAnimeId(ExternalAnimeProvider.DANDANPLAY, it),
                    displayTitle = series,
                    primaryTitle = series,
                    startYear = year,
                )
            },
        )
}
