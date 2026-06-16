package app.danmaku.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem

@Composable
internal fun NextUpPanel(
    items: List<LibraryNextUpItem>,
    posterEndpoint: LibraryPosterEndpoint?,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-next-up"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF17212A),
        border = BorderStroke(1.dp, Color(0xFF304454)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.library_next_up_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.library_next_up_subtitle),
                    color = SubtleText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.mediaItem.id }) { item ->
                    NextUpChip(
                        item = item,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { onShowDetails(item.mediaItem) },
                        onPlay = { onPlay(item.mediaItem) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NextUpChip(
    item: LibraryNextUpItem,
    posterEndpoint: LibraryPosterEndpoint?,
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
                progressLabel = item.nextUpActionLabel(),
                posterEndpoint = posterEndpoint,
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
                item.nextUpLabel(),
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
                    modifier = Modifier.testTag("next-up-details:${item.mediaItem.id}"),
                ) {
                    Text(stringResource(R.string.action_details))
                }
                Button(
                    onClick = onPlay,
                    modifier = Modifier.testTag("next-up:${item.mediaItem.id}"),
                ) {
                    Text(item.nextUpActionLabel())
                }
            }
        }
    }
}
