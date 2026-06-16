package app.danmaku.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary

@Composable
internal fun RecentlyAddedRail(
    items: List<LibraryMediaItem>,
    posterEndpoint: LibraryPosterEndpoint?,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-recently-added"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF15191D),
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.home_recently_added),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.home_recently_added_subtitle),
                    color = SubtleText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = LibraryMediaItem::id) { item ->
                    Surface(
                        modifier = Modifier
                            .width(240.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        color = PanelColor,
                        border = BorderStroke(1.dp, Color(0xFF2B3239)),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LibraryPosterTile(
                                item = item,
                                title = item.seriesTitle,
                                selected = false,
                                posterEndpoint = posterEndpoint,
                                progressLabel = item.mediaType,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp),
                            )
                            Text(
                                item.seriesTitle,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.episodeTitle,
                                color = SubtleText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.formatSize(),
                                color = AccentBlue,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onShowDetails(item) },
                                    modifier = Modifier.testTag("recently-added-details:${item.id}"),
                                ) {
                                    Text(stringResource(R.string.action_details))
                                }
                                Button(
                                    onClick = { onPlay(item) },
                                    modifier = Modifier.testTag("recently-added-play:${item.id}"),
                                ) {
                                    Text(stringResource(R.string.action_play))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProgressRail(
    title: String,
    subtitle: String,
    tag: String,
    itemTagPrefix: String,
    items: List<LibraryPlaybackProgressItem>,
    posterEndpoint: LibraryPosterEndpoint?,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF15191D),
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    color = SubtleText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.mediaItem.id }) { item ->
                    ProgressChip(
                        item = item,
                        posterEndpoint = posterEndpoint,
                        playTag = "$itemTagPrefix:${item.mediaItem.id}",
                        detailTag = "$itemTagPrefix-details:${item.mediaItem.id}",
                        onShowDetails = { onShowDetails(item.mediaItem) },
                        onPlay = { onPlay(item.mediaItem) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressChip(
    item: LibraryPlaybackProgressItem,
    posterEndpoint: LibraryPosterEndpoint?,
    playTag: String,
    detailTag: String,
    onShowDetails: () -> Unit,
    onPlay: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LibraryPosterTile(
                item = item.mediaItem,
                title = item.mediaItem.seriesTitle,
                selected = false,
                posterEndpoint = posterEndpoint,
                progressLabel = item.progress.progressLabel(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )
            Text(
                item.mediaItem.seriesTitle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.mediaItem.episodeTitle,
                color = SubtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.progress.progressLabel(),
                color = AccentBlue,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onShowDetails,
                    modifier = Modifier.testTag(detailTag),
                ) {
                    Text(stringResource(R.string.action_details))
                }
                Button(
                    onClick = onPlay,
                    modifier = Modifier.testTag(playTag),
                ) {
                    Text(stringResource(R.string.action_play))
                }
            }
        }
    }
}

@Composable
internal fun SeriesRail(
    series: List<LibrarySeries>,
    watchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    posterEndpoint: LibraryPosterEndpoint?,
    searchText: String,
    onSelectSeries: (String) -> Unit,
    onClearSearch: () -> Unit,
) {
    if (series.isEmpty()) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-series-rail"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF15191D),
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.library_series_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.library_series_count, series.size),
                        color = SubtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(
                    onClick = onClearSearch,
                    enabled = searchText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(series, key = LibrarySeries::id) { summary ->
                    val selected = searchText.trim().equals(summary.title, ignoreCase = true)
                    SeriesPosterCard(
                        series = summary,
                        watchSummary = watchSummaryById[summary.id],
                        posterEndpoint = posterEndpoint,
                        selected = selected,
                        onClick = {
                            if (selected) {
                                onClearSearch()
                            } else {
                                onSelectSeries(summary.title)
                            }
                        },
                        modifier = Modifier.testTag("series:${summary.title}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesPosterCard(
    series: LibrarySeries,
    watchSummary: LibrarySeriesWatchSummary?,
    posterEndpoint: LibraryPosterEndpoint?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFF263847) else PanelColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) AccentBlue else Color(0xFF2B3239),
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryPosterTile(
                item = series.latestIndexedItem,
                title = series.title,
                selected = selected,
                posterEndpoint = posterEndpoint,
                progressLabel = "${watchSummary?.watchedCount ?: 0}/${series.episodeCount}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.70f),
            )
            Text(
                series.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                watchSummary.progressLabel(),
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SeriesDetailPanel(
    series: LibrarySeries,
    watchSummary: LibrarySeriesWatchSummary?,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("series-detail:${series.title}"),
        shape = RoundedCornerShape(20.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    series.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${series.episodeCount} episodes across ${series.seasons.size} seasons",
                    color = SubtleText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.subtitle_count, series.subtitleTrackCount)) },
                )
                AssistChip(onClick = {}, label = { Text(watchSummary.progressLabel()) })
                AssistChip(onClick = {}, label = { Text(series.totalSizeBytes.formatSize()) })
                AssistChip(onClick = {}, label = { Text(series.latestIndexedItem.episodeTitle) })
            }
            series.seasons.take(3).forEach { season ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        season.label,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (season.items.size == 1) "1 episode" else "${season.items.size} episodes",
                        color = SubtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
