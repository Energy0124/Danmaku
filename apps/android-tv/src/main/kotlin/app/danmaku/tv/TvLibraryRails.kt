package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryPlaybackProgressItem

@Composable
internal fun TvProgressRail(
    title: String,
    tag: String,
    itemTagPrefix: String,
    items: List<LibraryPlaybackProgressItem>,
    posterEndpoint: LibraryPosterEndpoint?,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
            .padding(14.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.episode_count, items.size))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.mediaItem.id }) { item ->
                TvProgressRailCard(
                    item = item,
                    itemTagPrefix = itemTagPrefix,
                    posterEndpoint = posterEndpoint,
                    onShowDetails = onShowDetails,
                    onPlay = onPlay,
                )
            }
        }
    }
}

@Composable
internal fun TvNextUpRail(
    items: List<LibraryNextUpItem>,
    posterEndpoint: LibraryPosterEndpoint?,
    initialFocusRequester: FocusRequester? = null,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
            .padding(14.dp)
            .testTag("library-next-up"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.next_up_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.picks_count, items.size))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val firstItemId = items.firstOrNull()?.mediaItem?.id
            items(items, key = { it.mediaItem.id }) { item ->
                TvNextUpRailCard(
                    item = item,
                    isInitialFocusItem = item.mediaItem.id == firstItemId,
                    initialFocusRequester = initialFocusRequester,
                    posterEndpoint = posterEndpoint,
                    onShowDetails = onShowDetails,
                    onPlay = onPlay,
                )
            }
        }
    }
}
