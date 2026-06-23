package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries

@Composable
internal fun TvHomeSeriesRail(
    series: List<LibrarySeries>,
    posterEndpoint: LibraryPosterEndpoint?,
    onShowLibrary: () -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
            .padding(14.dp)
            .testTag("home-library-slices"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_library_slices),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.episode_count, series.sumOf { it.episodeCount }))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(series, key = LibrarySeries::id) { summary ->
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onShowLibrary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusHalo(RoundedCornerShape(20.dp))
                            .testTag("home-series:${summary.id}"),
                        colors = tvButtonColors(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TvPosterTile(
                                item = summary.latestIndexedItem,
                                title = summary.title,
                                posterEndpoint = posterEndpoint,
                                label = stringResource(R.string.episode_count, summary.episodeCount),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f),
                            )
                            Text(
                                summary.title,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Button(
                        onClick = { onPlay(summary.latestIndexedItem) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusHalo(RoundedCornerShape(16.dp)),
                        colors = tvButtonColors(),
                    ) {
                        Text(stringResource(R.string.action_play))
                    }
                }
            }
        }
    }
}
