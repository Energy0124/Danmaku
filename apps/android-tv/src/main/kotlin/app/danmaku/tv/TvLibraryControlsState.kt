package app.danmaku.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySubtitleFilter

internal class TvLibraryControlsState(
    initialFavoriteFilter: LibraryFavoriteFilter,
) {
    var searchText by mutableStateOf("")
    var sort by mutableStateOf(LibraryCatalogSort.TITLE)
    var subtitleFilter by mutableStateOf(LibrarySubtitleFilter.ANY)
    var favoriteFilter by mutableStateOf(initialFavoriteFilter)
    var selectedSeriesId by mutableStateOf<String?>(null)
    var selectedEpisodeId by mutableStateOf<String?>(null)

    fun resetFilters() {
        searchText = ""
        sort = LibraryCatalogSort.TITLE
        subtitleFilter = LibrarySubtitleFilter.ANY
        favoriteFilter = LibraryFavoriteFilter.ANY
        clearSelection()
    }

    fun toggleFavorites() {
        favoriteFilter = if (favoriteFilter == LibraryFavoriteFilter.ANY) {
            LibraryFavoriteFilter.FAVORITES_ONLY
        } else {
            LibraryFavoriteFilter.ANY
        }
    }

    fun toggleSubtitles() {
        subtitleFilter = if (subtitleFilter == LibrarySubtitleFilter.ANY) {
            LibrarySubtitleFilter.WITH_SUBTITLES
        } else {
            LibrarySubtitleFilter.ANY
        }
    }

    fun showAllSeries() {
        selectedSeriesId = null
        searchText = ""
    }

    fun toggleSeries(series: LibrarySeries) {
        val alreadySelected = selectedSeriesId == series.id
        selectedSeriesId = if (alreadySelected) null else series.id
        searchText = if (alreadySelected) "" else series.title
    }

    fun selectEpisode(item: LibraryMediaItem) {
        selectedEpisodeId = item.id
    }

    private fun clearSelection() {
        selectedSeriesId = null
        selectedEpisodeId = null
    }
}

@Composable
internal fun rememberTvLibraryControlsState(
    catalog: LibraryCatalog?,
    initialFavoriteFilter: LibraryFavoriteFilter,
): TvLibraryControlsState =
    remember(catalog, initialFavoriteFilter) {
        TvLibraryControlsState(initialFavoriteFilter)
    }
