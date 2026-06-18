package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import app.danmaku.domain.LibraryEpisodeDetail
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary

@Composable
internal fun TvEpisodeDetail(
    detail: LibraryEpisodeDetail,
    posterEndpoint: LibraryPosterEndpoint?,
    isFavorite: Boolean,
    onSetFavorite: (Boolean) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
    onSelectEpisode: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
            .padding(14.dp)
            .testTag("episode-detail:${detail.mediaItem.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvPosterTile(
                item = detail.mediaItem,
                title = detail.series.title,
                posterEndpoint = posterEndpoint,
                label = detail.watchStatus.statusLabel().substringBefore(" / "),
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(0.72f),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    detail.mediaItem.episodeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${detail.series.title} / ${detail.season.label} / ${detail.watchStatus.statusLabel()}")
                detail.mediaItem.animeMetadata?.let { metadata ->
                    Text(
                        stringResource(R.string.matched_anime, metadata.displayTitle),
                        color = TvAccentBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                detail.mediaItem.metadataStatusLabel(
                    loadingLabel = stringResource(R.string.metadata_loading),
                    failedLabel = stringResource(R.string.metadata_failed),
                )?.let { label ->
                    Text(
                        label,
                        color = TvAccentBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(detail.mediaItem.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (detail.mediaItem.posterPath == null) {
                    stringResource(R.string.subtitle_count, detail.mediaItem.subtitles.size)
                } else {
                    stringResource(R.string.subtitle_count_with_poster, detail.mediaItem.subtitles.size)
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onPlay(detail.mediaItem) },
                modifier = Modifier.testTag("episode-detail-play:${detail.mediaItem.id}"),
            ) {
                Text(stringResource(R.string.action_play))
            }
            Button(
                onClick = { onSetFavorite(!isFavorite) },
                modifier = Modifier.testTag("episode-detail-favorite:${detail.mediaItem.id}"),
            ) {
                Text(
                    if (isFavorite) {
                        stringResource(R.string.action_unfavorite)
                    } else {
                        stringResource(R.string.action_favorite)
                    },
                )
            }
            Button(
                onClick = { detail.previousItem?.let(onSelectEpisode) },
                enabled = detail.previousItem != null,
            ) {
                Text(stringResource(R.string.action_previous))
            }
            Button(
                onClick = { detail.nextItem?.let(onSelectEpisode) },
                enabled = detail.nextItem != null,
            ) {
                Text(stringResource(R.string.action_next))
            }
        }
    }
}

@Composable
internal fun TvSeriesDetail(
    series: LibrarySeries,
    watchSummary: LibrarySeriesWatchSummary?,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
            .padding(14.dp)
            .testTag("series-detail:${series.title}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    series.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.series_across_seasons, series.episodeLabel(), series.seasons.size))
                Text(watchSummary.progressLabel())
            }
            Text(stringResource(R.string.subtitle_track_count, series.subtitleTrackCount))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(series.seasons, key = { it.id }) { season ->
                Column(
                    modifier = Modifier
                        .width(340.dp)
                        .testTag("series-season:${season.label}"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.season_item_count, season.label, season.items.size),
                        fontWeight = FontWeight.SemiBold,
                    )
                    season.items.take(3).forEach { item ->
                        Button(
                            onClick = { onPlay(item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusHalo(RoundedCornerShape(16.dp))
                                .testTag("series-detail-episode:${item.id}"),
                        ) {
                            Text(item.episodeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (season.items.size > 3) {
                        Text(stringResource(R.string.more_episode_count, season.items.size - 3))
                    }
                }
            }
        }
    }
}
