package app.danmaku.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryEpisodeDetail
import app.danmaku.domain.LibraryMediaItem

@Composable
internal fun TabletDetailPlaceholder(catalog: LibraryCatalog?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-detail-placeholder"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF15191D),
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (catalog == null) {
                    stringResource(R.string.library_no_connected_title)
                } else {
                    stringResource(R.string.library_select_episode_title)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (catalog == null) {
                    stringResource(R.string.library_no_connected_body)
                } else {
                    stringResource(R.string.library_select_episode_body)
                },
                color = SubtleText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun EpisodeDetailPanel(
    detail: LibraryEpisodeDetail,
    posterEndpoint: LibraryPosterEndpoint?,
    isFavorite: Boolean,
    onSetFavorite: (Boolean) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
    onSelectEpisode: (LibraryMediaItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("episode-detail:${detail.mediaItem.id}"),
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
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryPosterTile(
                    item = detail.mediaItem,
                    title = detail.series.title,
                    selected = false,
                    progressLabel = detail.watchStatus.statusLabel().substringBefore(" · "),
                    posterEndpoint = posterEndpoint,
                    modifier = Modifier
                        .width(86.dp)
                        .aspectRatio(0.70f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        detail.mediaItem.episodeTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${detail.series.title} · ${detail.season.label} · ${detail.watchStatus.statusLabel()}",
                        color = SubtleText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    detail.mediaItem.animeMetadata?.let { metadata ->
                        Text(
                            stringResource(R.string.matched_anime, metadata.displayTitle),
                            color = AccentBlue,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = {}, label = { Text(detail.mediaItem.formatSize()) })
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.subtitle_count, detail.mediaItem.subtitles.size)) },
                )
                AssistChip(onClick = {}, label = { Text(detail.mediaItem.mediaType) })
                detail.mediaItem.posterPath?.let {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.poster_ready)) })
                }
                detail.mediaItem.metadataStatusLabel(
                    loadingLabel = stringResource(R.string.metadata_loading),
                    failedLabel = stringResource(R.string.metadata_failed),
                )?.let { label ->
                    AssistChip(onClick = {}, label = { Text(label) })
                }
            }
            Text(
                detail.mediaItem.relativePath,
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onPlay(detail.mediaItem) }) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_play))
                }
                OutlinedButton(
                    onClick = { onSetFavorite(!isFavorite) },
                    modifier = Modifier.testTag("episode-detail-favorite:${detail.mediaItem.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isFavorite) {
                            stringResource(R.string.action_unfavorite)
                        } else {
                            stringResource(R.string.action_favorite)
                        },
                    )
                }
                OutlinedButton(
                    onClick = { detail.previousItem?.let(onSelectEpisode) },
                    enabled = detail.previousItem != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_previous))
                }
                OutlinedButton(
                    onClick = { detail.nextItem?.let(onSelectEpisode) },
                    enabled = detail.nextItem != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_next))
                }
            }
        }
    }
}
