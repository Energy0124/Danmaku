package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.PlaybackProgress

@Composable
internal fun LibraryItems(
    catalog: LibraryCatalog?,
    posterEndpoint: LibraryPosterEndpoint? = null,
    playbackProgresses: List<PlaybackProgress> = emptyList(),
    favoriteMediaIds: Set<String> = emptySet(),
    initialFavoriteFilter: LibraryFavoriteFilter = LibraryFavoriteFilter.ANY,
    focusSearchOnStart: Boolean = false,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit = { _, _ -> },
    onPlay: (LibraryMediaItem) -> Unit,
) {
    val controls = rememberTvLibraryControlsState(catalog, initialFavoriteFilter)
    val libraryViewState = buildTvLibraryViewState(
        catalog = catalog,
        playbackProgresses = playbackProgresses,
        favoriteMediaIds = favoriteMediaIds,
        searchText = controls.searchText,
        sort = controls.sort,
        subtitleFilter = controls.subtitleFilter,
        favoriteFilter = controls.favoriteFilter,
        selectedSeriesId = controls.selectedSeriesId,
        selectedEpisodeId = controls.selectedEpisodeId,
    )
    val nextUpFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(libraryViewState.nextUpItems.firstOrNull()?.mediaItem?.id, focusSearchOnStart) {
        if (!focusSearchOnStart && libraryViewState.nextUpItems.isNotEmpty()) {
            nextUpFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(focusSearchOnStart, catalog?.indexedAtEpochMs) {
        if (focusSearchOnStart) {
            searchFocusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(TvPanelColor)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TvLibraryNavigationRail(
            catalog = catalog,
            filteredCount = libraryViewState.filteredItems.size,
            totalCount = libraryViewState.totalItems.size,
            favoriteCount = favoriteMediaIds.size,
            hasActiveFilters = libraryViewState.hasActiveFilters,
            searchActive = controls.searchText.isNotBlank(),
            favoritesActive = controls.favoriteFilter == LibraryFavoriteFilter.FAVORITES_ONLY,
            subtitlesActive = controls.subtitleFilter == LibrarySubtitleFilter.WITH_SUBTITLES,
            onResetFilters = controls::resetFilters,
            onFocusSearch = { searchFocusRequester.requestFocus() },
            onToggleFavorites = controls::toggleFavorites,
            onToggleSubtitles = controls::toggleSubtitles,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvLibraryHeader(
                catalog = catalog,
                filteredCount = libraryViewState.filteredItems.size,
                totalCount = libraryViewState.totalItems.size,
            )
            TvLibrarySearchField(
                searchText = controls.searchText,
                searchFocusRequester = searchFocusRequester,
                onSearchTextChange = { controls.searchText = it },
            )
            TvLibraryFilterBar(
                sort = controls.sort,
                favoriteFilter = controls.favoriteFilter,
                subtitleFilter = controls.subtitleFilter,
                onSelectSort = { controls.sort = it },
                onToggleFavorites = controls::toggleFavorites,
                onToggleSubtitles = controls::toggleSubtitles,
            )
            TvLibraryContentSections(
                catalog = catalog,
                libraryViewState = libraryViewState,
                controls = controls,
                posterEndpoint = posterEndpoint,
                favoriteMediaIds = favoriteMediaIds,
                nextUpFocusRequester = nextUpFocusRequester,
                onSetFavorite = onSetFavorite,
                onPlay = onPlay,
            )
        }
    }
}
