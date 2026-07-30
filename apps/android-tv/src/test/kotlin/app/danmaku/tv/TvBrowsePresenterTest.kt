package app.danmaku.tv

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

    private fun mediaItem(
        id: String,
        series: String,
        episode: Int,
        indexedAt: Long,
        hasSubtitle: Boolean = false,
    ): LibraryMediaItem =
        LibraryMediaItem(
            id = id,
            seriesTitle = series,
            episodeTitle = "Episode $episode",
            relativePath = "$series/Episode $episode.mkv",
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
        )
}
