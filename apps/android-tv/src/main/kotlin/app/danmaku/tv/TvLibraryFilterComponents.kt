package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibrarySubtitleFilter

@Composable
internal fun TvLibraryHeader(
    catalog: LibraryCatalog?,
    filteredCount: Int,
    totalCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.library_pc_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                catalog?.rootName ?: stringResource(R.string.library_connect_server),
                color = TvMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TvRailPill(
            stringResource(R.string.library_episode_count, filteredCount, totalCount),
            active = totalCount > 0,
            modifier = Modifier.width(170.dp),
        )
    }
}

@Composable
internal fun TvLibrarySearchField(
    searchText: String,
    searchFocusRequester: FocusRequester,
    onSearchTextChange: (String) -> Unit,
) {
    BasicTextField(
        value = searchText,
        onValueChange = onSearchTextChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TvCardColor)
            .focusRequester(searchFocusRequester)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .focusable()
            .testTag("library-search-field"),
        decorationBox = { innerTextField ->
            if (searchText.isBlank()) {
                Text(stringResource(R.string.library_search_hint), color = TvMutedText)
            }
            innerTextField()
        },
    )
}

@Composable
internal fun TvLibraryFilterBar(
    sort: LibraryCatalogSort,
    favoriteFilter: LibraryFavoriteFilter,
    subtitleFilter: LibrarySubtitleFilter,
    onSelectSort: (LibraryCatalogSort) -> Unit,
    onToggleFavorites: () -> Unit,
    onToggleSubtitles: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(
                onClick = onToggleFavorites,
                modifier = Modifier.testTag("library-favorites-filter"),
            ) {
                Text(
                    if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                        stringResource(R.string.nav_favorites)
                    } else {
                        stringResource(R.string.library_all_episodes)
                    },
                )
            }
        }
        item {
            Button(
                onClick = { onSelectSort(LibraryCatalogSort.TITLE) },
                enabled = sort != LibraryCatalogSort.TITLE,
            ) {
                Text(stringResource(R.string.library_sort_title))
            }
        }
        item {
            Button(
                onClick = { onSelectSort(LibraryCatalogSort.PATH) },
                enabled = sort != LibraryCatalogSort.PATH,
            ) {
                Text(stringResource(R.string.library_sort_path))
            }
        }
        item {
            Button(onClick = onToggleSubtitles) {
                Text(
                    if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                        stringResource(R.string.subtitle_tracks_title)
                    } else {
                        stringResource(R.string.library_all)
                    },
                )
            }
        }
    }
}
