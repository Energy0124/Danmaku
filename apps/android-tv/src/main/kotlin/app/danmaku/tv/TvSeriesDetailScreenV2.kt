package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryMediaItem

@Composable
internal fun TvSeriesDetailScreen(
    route: TvRoute.SeriesDetail,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    browse: TvBrowseUiState,
    onPlay: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
) {
    val series = browse.seriesById[route.seriesKey]
    if (series == null) {
        TvEmptyState(
            title = stringResource(R.string.series_not_found_title),
            body = stringResource(R.string.series_not_found_body),
        )
        return
    }
    val episodes = series.seasons.flatMap { it.items }
    var selectedId by rememberSaveable(route.seriesKey) {
        mutableStateOf(episodes.firstOrNull()?.id)
    }
    LaunchedEffect(episodes) {
        if (episodes.none { it.id == selectedId }) selectedId = episodes.firstOrNull()?.id
    }
    val selected = episodes.firstOrNull { it.id == selectedId } ?: episodes.first()
    val isFavorite = selected.id in session.favoriteMediaIds
    val playFocusRequester = remember(route) { FocusRequester() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen-series-detail"),
    ) {
        val compactHeight = maxHeight < 600.dp
        val posterWidth = if (compactHeight) 112.dp else 160.dp
        val posterHeight = if (compactHeight) 144.dp else 210.dp
        val detailWidth = if (compactHeight) 360.dp else 430.dp
        val detailPadding = if (compactHeight) 14.dp else 20.dp
        val detailSpacing = if (compactHeight) 6.dp else 10.dp
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(if (compactHeight) 20.dp else 28.dp),
        ) {
        Column(
            modifier = Modifier
                .width(detailWidth)
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(TvSurface)
                .padding(detailPadding),
            verticalArrangement = Arrangement.spacedBy(detailSpacing),
        ) {
            TvPosterImage(
                item = selected,
                endpoint = session.posterEndpoint,
                width = posterWidth,
                height = posterHeight,
                modifier = Modifier.clip(RoundedCornerShape(22.dp)),
            )
            Text(
                text = series.title,
                style = if (compactHeight) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = selected.episodeTitle,
                color = TvSecondaryContent,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.series_episode_position,
                    episodes.indexOf(selected) + 1,
                    episodes.size,
                ),
                color = TvSecondaryContent,
            )
            Button(
                onClick = { onPlay(selected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .tvRouteFocus(
                        navigation,
                        navigator,
                        route,
                        "series-primary-action",
                        isDefault = true,
                        focusRequesterOverride = playFocusRequester,
                    )
                    .tvFocusHalo(RoundedCornerShape(18.dp))
                    .testTag("series-play"),
                colors = tvButtonColors(selected = true),
                scale = tvButtonScale(),
            ) {
                Text(
                    if (browse.watchStatusById[selected.id]?.progress == null) {
                        stringResource(R.string.action_play)
                    } else {
                        stringResource(R.string.action_resume)
                    },
                )
            }
            Button(
                onClick = { onSetFavorite(selected, !isFavorite) },
                modifier = Modifier
                    .fillMaxWidth()
                    .tvRouteFocus(
                        navigation,
                        navigator,
                        route,
                        "series-favorite",
                    )
                    .tvFocusHalo(RoundedCornerShape(18.dp)),
                colors = tvButtonColors(isFavorite),
                scale = tvButtonScale(),
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 10.dp else 16.dp),
        ) {
            TvScreenHeader(
                title = stringResource(R.string.episodes_title),
                subtitle = stringResource(R.string.episode_count, episodes.size),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("episode-list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(episodes, key = { _, item -> item.id }) { index, item ->
                    val selectedRow = item.id == selected.id
                    Button(
                        onClick = { selectedId = item.id },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compactHeight) 68.dp else 76.dp)
                            .tvRouteFocus(
                                navigation,
                                navigator,
                                route,
                                "episode:${item.id}",
                                leftFocusRequester = playFocusRequester,
                            )
                            .tvFocusHalo(RoundedCornerShape(16.dp))
                            .testTag("episode-row:${item.id}"),
                        colors = tvButtonColors(selectedRow),
                        scale = tvButtonScale(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = item.episodeTitle,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.relativePath,
                                color = TvSecondaryContent,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
