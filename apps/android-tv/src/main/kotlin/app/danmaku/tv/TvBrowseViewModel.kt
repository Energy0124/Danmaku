package app.danmaku.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class TvBrowseViewModel(
    private val repository: TvLibraryRepository,
    private val presenter: TvBrowsePresenter,
    private val presentationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val searchText = MutableStateFlow("")
    private val filters = MutableStateFlow(TvBrowseQuery())

    // Connection and refresh flags do not affect catalog presentation. Referential checks
    // keep those frequent repository copies from reprocessing a large, unchanged catalog.
    private val presentationSessions = repository.state.distinctUntilChanged { previous, next ->
        previous.catalog === next.catalog &&
            previous.playbackProgresses === next.playbackProgresses &&
            previous.favoriteMediaIds === next.favoriteMediaIds
    }
    private val debouncedSearchText = searchText.debounce { value ->
        if (value.isEmpty()) 0L else SEARCH_DEBOUNCE_MS
    }

    val state = combine(
        presentationSessions,
        debouncedSearchText,
        filters,
    ) { session, debouncedSearch, filterState ->
        TvBrowsePresentationRequest(
            session = session,
            query = filterState.copy(searchText = debouncedSearch),
        )
    }.mapLatest { request ->
        withContext(presentationDispatcher) {
            presenter.present(
                request.session,
                request.query,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TvBrowseUiState(),
    )

    fun setSearchText(value: String) {
        searchText.value = value
    }

    fun setSort(value: TvLibrarySort) {
        filters.value = filters.value.copy(sort = value)
    }

    fun setReleaseYear(value: Int?) {
        filters.value = filters.value.copy(releaseYear = value)
    }

    fun toggleSubtitles() {
        filters.value = filters.value.copy(
            subtitleFilter = if (
                filters.value.subtitleFilter == LibrarySubtitleFilter.ANY
            ) {
                LibrarySubtitleFilter.WITH_SUBTITLES
            } else {
                LibrarySubtitleFilter.ANY
            },
        )
    }

    fun setFavoritesOnly(enabled: Boolean) {
        filters.value = filters.value.copy(
            favoriteFilter = if (enabled) {
                LibraryFavoriteFilter.FAVORITES_ONLY
            } else {
                LibraryFavoriteFilter.ANY
            },
        )
    }

    fun resetFilters() {
        searchText.value = ""
        filters.value = TvBrowseQuery()
    }

    fun setFavorite(
        item: LibraryMediaItem,
        isFavorite: Boolean,
    ) = repository.setFavorite(item, isFavorite)

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

private data class TvBrowsePresentationRequest(
    val session: TvSessionUiState,
    val query: TvBrowseQuery,
)
