package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalog

@Composable
internal fun TvLibraryNavigationRail(
    catalog: LibraryCatalog?,
    filteredCount: Int,
    totalCount: Int,
    favoriteCount: Int,
    hasActiveFilters: Boolean,
    searchActive: Boolean,
    favoritesActive: Boolean,
    subtitlesActive: Boolean,
    onResetFilters: () -> Unit,
    onFocusSearch: () -> Unit,
    onToggleFavorites: () -> Unit,
    onToggleSubtitles: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(TvCardColor)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.library_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        TvRailPill(stringResource(R.string.library_episode_count, filteredCount, totalCount), compact = true)
        TvRailPill(stringResource(R.string.favorites_count, favoriteCount), active = favoriteCount > 0, compact = true)
        if (hasActiveFilters) {
            TvRailPill(stringResource(R.string.library_filters_active), active = true, compact = true)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(stringResource(R.string.library_quick_actions), color = TvMutedText, fontWeight = FontWeight.SemiBold)
        TvRailNavigationItem(
            label = stringResource(R.string.library_all_episodes),
            selected = !hasActiveFilters,
            testTag = "tv-rail-all",
            compact = true,
            onClick = onResetFilters,
        )
        TvRailNavigationItem(
            label = if (searchActive) {
                stringResource(R.string.library_search_active)
            } else {
                stringResource(R.string.nav_search)
            },
            selected = searchActive,
            testTag = "tv-rail-search",
            compact = true,
            onClick = onFocusSearch,
        )
        TvRailNavigationItem(
            label = if (favoritesActive) {
                stringResource(R.string.library_favorites_only)
            } else {
                stringResource(R.string.nav_favorites)
            },
            selected = favoritesActive,
            testTag = "tv-rail-favorites",
            compact = true,
            onClick = onToggleFavorites,
        )
        TvRailNavigationItem(
            label = if (subtitlesActive) {
                stringResource(R.string.library_subtitles_only)
            } else {
                stringResource(R.string.subtitle_tracks_title)
            },
            selected = subtitlesActive,
            testTag = "tv-rail-subtitles",
            compact = true,
            onClick = onToggleSubtitles,
        )
    }
}

@Composable
internal fun TvLibraryEmptyPanel(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
            .padding(16.dp)
            .testTag("library-empty-state"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(body)
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.testTag("library-reset-filters"),
                colors = tvButtonColors(),
            ) {
                Text(actionLabel)
            }
        }
    }
}
