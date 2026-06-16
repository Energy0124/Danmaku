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
            .width(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(TvCardColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.library_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            catalog?.rootName ?: stringResource(R.string.pc_no_connected),
            color = TvMutedText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TvRailPill(
            if (catalog == null) {
                stringResource(R.string.pc_offline)
            } else {
                stringResource(R.string.pc_ready)
            },
            active = catalog != null,
        )
        TvRailPill(stringResource(R.string.library_episode_count, filteredCount, totalCount))
        TvRailPill(stringResource(R.string.favorites_count, favoriteCount), active = favoriteCount > 0)
        if (hasActiveFilters) {
            TvRailPill(stringResource(R.string.library_filters_active), active = true)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.library_quick_actions), color = TvMutedText, fontWeight = FontWeight.SemiBold)
        TvRailNavigationItem(
            label = stringResource(R.string.library_all_episodes),
            selected = !hasActiveFilters,
            testTag = "tv-rail-all",
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
            ) {
                Text(actionLabel)
            }
        }
    }
}
