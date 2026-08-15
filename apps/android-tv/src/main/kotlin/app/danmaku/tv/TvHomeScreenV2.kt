package app.danmaku.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ButtonDefaults
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryMediaItem

@Composable
internal fun TvHomeScreen(
    navigation: TvNavigationState,
    navigator: TvNavigator,
    session: TvSessionUiState,
    browse: TvBrowseUiState,
    onOpenSeries: (String) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
    onOpenPc: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen-home"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            TvScreenHeader(
                title = stringResource(R.string.nav_home),
                subtitle = session.catalog?.rootName
                    ?: stringResource(R.string.home_connect_pc_library),
            )
        }
        val hero = browse.heroItem
        if (hero == null) {
            item {
                TvHomeConnectHero(
                    navigation = navigation,
                    navigator = navigator,
                    onOpenPc = onOpenPc,
                )
            }
        } else {
            item {
                TvHomeHero(
                    item = hero,
                    isResume = browse.heroIsResume,
                    endpoint = session.posterEndpoint,
                    navigation = navigation,
                    navigator = navigator,
                    onPlay = { onPlay(hero) },
                )
            }
        }
        item {
            TvMediaRail(
                title = stringResource(R.string.home_continue_watching),
                items = browse.continueWatching.map { it.mediaItem },
                endpoint = session.posterEndpoint,
                navigation = navigation,
                navigator = navigator,
                route = TvRoute.Home,
                testTag = "home-continue",
                onOpenItem = { item ->
                    browse.seriesIdByMediaId[item.id]?.let(onOpenSeries)
                },
            )
        }
        item {
            TvMediaRail(
                title = stringResource(R.string.next_up_title),
                items = browse.nextUp.map { it.mediaItem },
                endpoint = session.posterEndpoint,
                navigation = navigation,
                navigator = navigator,
                route = TvRoute.Home,
                testTag = "home-next-up",
                onOpenItem = { item ->
                    browse.seriesIdByMediaId[item.id]?.let(onOpenSeries)
                },
            )
        }
        item {
            TvMediaRail(
                title = stringResource(R.string.home_recently_added),
                items = browse.recentlyAdded,
                endpoint = session.posterEndpoint,
                navigation = navigation,
                navigator = navigator,
                route = TvRoute.Home,
                testTag = "home-recently-added",
                onOpenItem = { item ->
                    browse.seriesIdByMediaId[item.id]?.let(onOpenSeries)
                },
            )
        }
        if (browse.seriesById.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.home_series),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(
                            browse.seriesById.values.take(12).toList(),
                            key = { _, series -> series.id },
                        ) { index, series ->
                            TvSeriesCard(
                                series = series,
                                endpoint = session.posterEndpoint,
                                navigation = navigation,
                                navigator = navigator,
                                route = TvRoute.Home,
                                focusKey = "home-series:${series.id}",
                                isDefault = false,
                                summary = browse.seriesWatchSummaryById[series.id],
                                onClick = { onOpenSeries(series.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHomeHero(
    item: LibraryMediaItem,
    isResume: Boolean,
    endpoint: LibraryPosterEndpoint?,
    navigation: TvNavigationState,
    navigator: TvNavigator,
    onPlay: () -> Unit,
) {
    Button(
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .tvRouteFocus(
                navigation,
                navigator,
                TvRoute.Home,
                "home-hero",
                isDefault = true,
            )
            .tvFocusHalo(RoundedCornerShape(28.dp))
            .testTag("home-hero"),
        colors = tvButtonColors(selected = true),
        shape = ButtonDefaults.shape(RoundedCornerShape(28.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            TvPosterImage(
                item = item,
                endpoint = endpoint,
                width = 920.dp,
                height = 220.dp,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xF2070B12), Color(0x99070B12), Color.Transparent),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(560.dp)
                    .padding(34.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(
                        if (isResume) R.string.home_hero_resume else R.string.next_up_title,
                    ),
                    color = TvAccent,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.displaySeriesTitle(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.episodeTitle,
                    color = TvSecondaryContent,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (isResume) R.string.action_resume else R.string.action_play,
                    ),
                    color = TvContent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TvHomeConnectHero(
    navigation: TvNavigationState,
    navigator: TvNavigator,
    onOpenPc: () -> Unit,
) {
    Button(
        onClick = onOpenPc,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .tvRouteFocus(
                navigation,
                navigator,
                TvRoute.Home,
                "home-hero",
                isDefault = true,
            )
            .tvFocusHalo(RoundedCornerShape(28.dp))
            .testTag("home-hero"),
        colors = tvButtonColors(),
        shape = ButtonDefaults.shape(RoundedCornerShape(28.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.library_no_pc_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.library_no_pc_body),
                    color = TvSecondaryContent,
                )
            }
            Text(
                text = stringResource(R.string.action_open_pc),
                color = TvAccent,
                modifier = Modifier.padding(start = 24.dp),
            )
        }
    }
}
