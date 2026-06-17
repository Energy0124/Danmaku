package app.danmaku.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
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
                var cardHasFocus by remember(item.mediaItem.id) { mutableStateOf(false) }
                val cardScale by animateFloatAsState(
                    targetValue = if (cardHasFocus) 1.025f else 1f,
                    label = "$itemTagPrefix-card-focus-scale",
                )
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .scale(cardScale)
                        .clip(RoundedCornerShape(20.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onShowDetails(item.mediaItem) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { cardHasFocus = it.isFocused }
                            .tvFocusHalo(RoundedCornerShape(20.dp))
                            .testTag("$itemTagPrefix-details:${item.mediaItem.id}"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TvPosterTile(
                                item = item.mediaItem,
                                title = item.mediaItem.seriesTitle,
                                posterEndpoint = posterEndpoint,
                                label = stringResource(R.string.action_resume),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                            )
                            Text(
                                item.mediaItem.seriesTitle,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.mediaItem.episodeTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(item.progress.progressLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Button(
                        onClick = { onPlay(item.mediaItem) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { cardHasFocus = it.isFocused }
                            .tvFocusHalo(RoundedCornerShape(16.dp))
                            .testTag("$itemTagPrefix:${item.mediaItem.id}"),
                    ) {
                        Text(stringResource(R.string.action_play))
                    }
                }
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
                var cardHasFocus by remember(item.mediaItem.id) { mutableStateOf(false) }
                val cardScale by animateFloatAsState(
                    targetValue = if (cardHasFocus) 1.025f else 1f,
                    label = "next-up-card-focus-scale",
                )
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .scale(cardScale)
                        .clip(RoundedCornerShape(20.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onShowDetails(item.mediaItem) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { cardHasFocus = it.isFocused }
                            .then(
                                if (item.mediaItem.id == firstItemId && initialFocusRequester != null) {
                                    Modifier.focusRequester(initialFocusRequester)
                                } else {
                                    Modifier
                                },
                            )
                            .tvFocusHalo(RoundedCornerShape(20.dp))
                            .testTag("next-up-details:${item.mediaItem.id}"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TvPosterTile(
                                item = item.mediaItem,
                                title = item.mediaItem.seriesTitle,
                                posterEndpoint = posterEndpoint,
                                label = item.nextUpActionLabel(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                            )
                            Text(
                                item.mediaItem.seriesTitle,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.mediaItem.episodeTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(item.nextUpLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Button(
                        onClick = { onPlay(item.mediaItem) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { cardHasFocus = it.isFocused }
                            .tvFocusHalo(RoundedCornerShape(16.dp))
                            .testTag("next-up:${item.mediaItem.id}"),
                    ) {
                        Text(item.nextUpActionLabel())
                    }
                }
            }
        }
    }
}
