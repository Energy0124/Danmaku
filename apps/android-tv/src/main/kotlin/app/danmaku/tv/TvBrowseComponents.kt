package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ButtonDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary

@Composable
internal fun TvScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TvContent,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = TvSecondaryContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        action?.invoke()
    }
}

@Composable
internal fun TvSeriesCard(
    series: LibrarySeries,
    endpoint: LibraryPosterEndpoint?,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    route: TvRoute,
    focusKey: String,
    isDefault: Boolean,
    summary: LibrarySeriesWatchSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val posterItem = series.seasons.first().items.first()
    Button(
        onClick = onClick,
        modifier = modifier
            .width(184.dp)
            .height(304.dp)
            .tvRouteFocus(navigation, navigator, route, focusKey, isDefault)
            .tvFocusHalo(RoundedCornerShape(20.dp))
            .testTag("series-card:${series.id}"),
        colors = tvButtonColors(),
        scale = tvButtonScale(),
        shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TvPosterImage(
                item = posterItem,
                endpoint = endpoint,
                width = 184.dp,
                height = 232.dp,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = series.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary?.let {
                        stringResource(
                            R.string.series_progress_short,
                            it.watchedCount,
                            it.totalCount,
                        )
                    } ?: stringResource(R.string.episode_count, series.episodeCount),
                    maxLines = 1,
                    color = TvSecondaryContent,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun TvMediaRail(
    title: String,
    items: List<LibraryMediaItem>,
    endpoint: LibraryPosterEndpoint?,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    route: TvRoute,
    testTag: String,
    onOpenItem: (LibraryMediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag(testTag),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                TvEpisodePosterCard(
                    item = item,
                    endpoint = endpoint,
                    navigation = navigation,
                    navigator = navigator,
                    route = route,
                    focusKey = "$testTag:${item.id}",
                    isDefault = false,
                    onClick = { onOpenItem(item) },
                )
            }
        }
    }
}

@Composable
internal fun TvEpisodePosterCard(
    item: LibraryMediaItem,
    endpoint: LibraryPosterEndpoint?,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    route: TvRoute,
    focusKey: String,
    isDefault: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .width(208.dp)
            .height(132.dp)
            .tvRouteFocus(navigation, navigator, route, focusKey, isDefault)
            .tvFocusHalo(RoundedCornerShape(18.dp))
            .testTag("media-card:${item.id}"),
        colors = tvButtonColors(),
        scale = tvButtonScale(),
        shape = ButtonDefaults.shape(RoundedCornerShape(18.dp)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            TvPosterImage(
                item = item,
                endpoint = endpoint,
                width = 208.dp,
                height = 132.dp,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE6101722)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    text = item.displaySeriesTitle(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.episodeTitle,
                    color = TvSecondaryContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun TvEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TvSurface)
            .padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(body, color = TvSecondaryContent)
    }
}
