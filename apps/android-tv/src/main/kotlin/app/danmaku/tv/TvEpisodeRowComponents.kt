package app.danmaku.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryWatchStatus

@Composable
internal fun TvEpisodeButton(
    item: LibraryMediaItem,
    posterEndpoint: LibraryPosterEndpoint?,
    watchStatus: LibraryWatchStatus?,
    isFavorite: Boolean,
    onSetFavorite: (Boolean) -> Unit,
    onShowDetails: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onShowDetails,
            modifier = Modifier
                .weight(1f)
                .tvFocusHalo(RoundedCornerShape(18.dp))
                .testTag("episode:${item.id}"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvPosterTile(
                    item = item,
                    title = item.seriesTitle,
                    posterEndpoint = posterEndpoint,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.seriesTitle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.episodeTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.tvMetadataLabel(watchStatus, isFavorite),
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Text(stringResource(R.string.subtitle_short_count, item.subtitles.size))
            }
        }
        Button(
            onClick = onPlay,
            modifier = Modifier
                .tvFocusHalo(RoundedCornerShape(18.dp))
                .testTag("episode-play:${item.id}"),
        ) {
            Text(stringResource(R.string.action_play))
        }
        Button(
            onClick = { onSetFavorite(!isFavorite) },
            modifier = Modifier
                .tvFocusHalo(RoundedCornerShape(18.dp))
                .testTag("episode-favorite:${item.id}"),
        ) {
            Text(
                if (isFavorite) {
                    stringResource(R.string.action_unfavorite)
                } else {
                    stringResource(R.string.action_favorite)
                },
            )
        }
    }
}
