package app.danmaku.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary

@Composable
internal fun TvSeriesPickerRail(
    series: List<LibrarySeries>,
    canClearSelection: Boolean,
    posterEndpoint: LibraryPosterEndpoint?,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    onShowAllSeries: () -> Unit,
    onToggleSeries: (LibrarySeries) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "all-series") {
            Button(
                onClick = onShowAllSeries,
                enabled = canClearSelection,
                modifier = Modifier
                    .tvFocusHalo(RoundedCornerShape(18.dp))
                    .testTag("series:all"),
            ) {
                Text(stringResource(R.string.library_all_series))
            }
        }
        items(series, key = { it.id }) { summary ->
            Button(
                onClick = { onToggleSeries(summary) },
                modifier = Modifier
                    .tvFocusHalo(RoundedCornerShape(22.dp))
                    .testTag("series:${summary.title}"),
            ) {
                Column {
                    TvPosterTile(
                        item = summary.latestIndexedItem,
                        title = summary.title,
                        posterEndpoint = posterEndpoint,
                        label = seriesWatchSummaryById[summary.id].shortProgressLabel(),
                        modifier = Modifier
                            .width(132.dp)
                            .aspectRatio(0.75f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        summary.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(summary.episodeLabel())
                    Text(seriesWatchSummaryById[summary.id].shortProgressLabel())
                }
            }
        }
    }
}
