package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.recentlyWatchedItems

@Composable
internal fun TvHomePanel(
    catalog: LibraryCatalog?,
    playbackProgresses: List<PlaybackProgress>,
    posterEndpoint: LibraryPosterEndpoint?,
    onShowLibrary: () -> Unit,
    onShowPc: () -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    val nextUpItems = catalog?.nextUpItems(playbackProgresses, limit = 6).orEmpty()
    val continueWatchingItems = catalog?.continueWatchingItems(playbackProgresses, limit = 6).orEmpty()
    val recentlyWatchedItems = catalog?.recentlyWatchedItems(playbackProgresses, limit = 6).orEmpty()
    val recentlyAddedItems = catalog
        ?.items
        .orEmpty()
        .sortedWith(
            compareByDescending<LibraryMediaItem> { it.indexedAtEpochMs }
                .thenBy { it.seriesTitle }
                .thenBy { it.episodeTitle },
        )
        .take(6)
    val seriesSlices = catalog?.groupedSeries().orEmpty().take(6)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TvPanelRaisedColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_now_next_up),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (catalog == null) {
                        stringResource(R.string.home_connect_pc_library)
                    } else {
                        stringResource(R.string.home_available_episodes, catalog.items.size)
                    },
                    color = TvMutedText,
                )
            }
            Button(
                onClick = if (catalog == null) onShowPc else onShowLibrary,
                modifier = Modifier.tvFocusHalo(RoundedCornerShape(18.dp)),
                colors = tvButtonColors(),
            ) {
                Text(
                    if (catalog == null) {
                        stringResource(R.string.action_open_pc)
                    } else {
                        stringResource(R.string.action_open_library)
                    },
                )
            }
        }
        if (nextUpItems.isNotEmpty()) {
            TvNextUpRail(
                items = nextUpItems,
                posterEndpoint = posterEndpoint,
                onShowDetails = { _ -> onShowLibrary() },
                onPlay = onPlay,
            )
        }
        if (continueWatchingItems.isNotEmpty()) {
            TvProgressRail(
                title = stringResource(R.string.home_continue_watching),
                tag = "home-continue-watching",
                itemTagPrefix = "home-continue",
                items = continueWatchingItems,
                posterEndpoint = posterEndpoint,
                onShowDetails = { _ -> onShowLibrary() },
                onPlay = onPlay,
            )
        }
        if (recentlyAddedItems.isNotEmpty()) {
            TvRecentlyAddedRail(
                items = recentlyAddedItems,
                posterEndpoint = posterEndpoint,
                onShowLibrary = onShowLibrary,
                onPlay = onPlay,
            )
        }
        if (recentlyWatchedItems.isNotEmpty()) {
            TvProgressRail(
                title = stringResource(R.string.home_recently_watched),
                tag = "home-recently-watched",
                itemTagPrefix = "home-recent",
                items = recentlyWatchedItems,
                posterEndpoint = posterEndpoint,
                onShowDetails = { _ -> onShowLibrary() },
                onPlay = onPlay,
            )
        }
        if (seriesSlices.isNotEmpty()) {
            TvHomeSeriesRail(
                series = seriesSlices,
                posterEndpoint = posterEndpoint,
                onShowLibrary = onShowLibrary,
                onPlay = onPlay,
            )
        }
        TvHomeStatusPanel(
            catalog = catalog,
            onShowPc = onShowPc,
            onShowLibrary = onShowLibrary,
        )
    }
}
