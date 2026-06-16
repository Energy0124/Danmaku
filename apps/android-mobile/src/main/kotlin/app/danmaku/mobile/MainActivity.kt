package app.danmaku.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryEpisodeDetail
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.coerceSeekTarget
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.seekTargetBy
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanLibraryConnectionProfile
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.LanLibraryClient
import app.danmaku.library.android.LanLibraryDiscoveryClient
import app.danmaku.player.android.Media3PlaybackController
import app.danmaku.player.android.Media3PlaybackServiceConnection
import kotlinx.coroutines.delay

internal val AppBackground = Color(0xFF101214)
internal val PlayerBlack = Color(0xFF050607)
internal val PanelColor = Color(0xFF191D21)
internal val PanelAltColor = Color(0xFF20262B)
internal val SubtleText = Color(0xFFB7C0C9)
internal val AccentBlue = Color(0xFF7DD3FC)
internal val AccentAmber = Color(0xFFFBBF24)
internal val DangerRed = Color(0xFFFCA5A5)

internal data class LibraryPosterEndpoint(
    val baseUrl: String,
    val pairingToken: String,
) {
    fun posterUrl(item: LibraryMediaItem): String? {
        val path = item.posterPath ?: return null
        return "${baseUrl.trim().trimEnd('/')}$path?token=${pairingToken.encodedQueryValue()}"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AccentBlue,
                    secondary = AccentAmber,
                    background = AppBackground,
                    surface = PanelColor,
                    surfaceVariant = PanelAltColor,
                    onPrimary = PlayerBlack,
                    onSecondary = PlayerBlack,
                    onBackground = Color(0xFFF7F7F8),
                    onSurface = Color(0xFFF7F7F8),
                    onSurfaceVariant = SubtleText,
                    outline = Color(0xFF3A4149),
                ),
            ) {
                MobilePlayerScreen()
            }
        }
    }
}

@Composable
private fun MobilePlayerScreen() {
    val context = LocalContext.current
    val playbackConnection = remember {
        Media3PlaybackServiceConnection(context.applicationContext)
    }
    val libraryClient = remember { LanLibraryClient() }
    val libraryConnectionSession = remember(libraryClient) { LanLibraryConnectionSession(libraryClient) }
    val progressSync = remember(libraryClient) {
        LanPlaybackProgressSync(libraryClient, System::currentTimeMillis)
    }
    val playbackPreparer = remember(libraryClient) { LanPlaybackPreparer(libraryClient) }
    val connectionStore = remember(context) {
        AndroidLanLibraryConnectionStore(context.applicationContext)
    }
    val favoriteStore = remember(context) {
        AndroidLibraryFavoriteStore(context.applicationContext)
    }
    val discoveryClient = remember { LanLibraryDiscoveryClient() }
    val scope = rememberCoroutineScope()
    val appState = remember(connectionStore, favoriteStore) {
        MobilePlayerState(
            initialSavedConnections = connectionStore.loadProfiles(),
            initialFavoriteMediaIds = favoriteStore.loadFavoriteMediaIds(),
        )
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        appState.controller?.let {
            appState.nowPlaying = null
            it.load(PlaybackSource.LocalFile(uri.toString()))
            appState.snapshot = it.snapshot()
        }
    }
    val actionHandler = remember(
        appState,
        scope,
        libraryConnectionSession,
        progressSync,
        playbackPreparer,
        connectionStore,
        favoriteStore,
        discoveryClient,
    ) {
        MobilePlayerActionHandler(
            state = appState,
            scope = scope,
            libraryConnectionSession = libraryConnectionSession,
            progressSync = progressSync,
            playbackPreparer = playbackPreparer,
            connectionStore = connectionStore,
            favoriteStore = favoriteStore,
            discoveryClient = discoveryClient,
            openVideoPicker = { openDocument.launch(arrayOf("video/*")) },
        )
    }

    DisposableEffect(playbackConnection) {
        playbackConnection.connect(
            executor = ContextCompat.getMainExecutor(context),
            onConnected = {
                appState.controller = it
                appState.snapshot = it.snapshot()
                appState.playbackError = null
            },
            onFailure = {
                appState.playbackError = it.message
            },
        )
        onDispose {
            appState.controller = null
            playbackConnection.close()
        }
    }

    LaunchedEffect(appState.controller) {
        val activeController = appState.controller ?: return@LaunchedEffect
        while (true) {
            appState.snapshot = activeController.snapshot()
            delay(250)
        }
    }

    MobileAppScaffold(
        state = appState.toUiState(),
        actions = actionHandler.toAppActions(),
    )
}

@Composable
internal fun HomePage(
    contentPadding: PaddingValues,
    catalog: LibraryCatalog?,
    posterEndpoint: LibraryPosterEndpoint? = null,
    playbackProgresses: List<PlaybackProgress>,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    onPlay: (LibraryMediaItem) -> Unit,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenLibrary: () -> Unit,
    onShowLibraryItem: (LibraryMediaItem) -> Unit,
    onConnect: () -> Unit,
) {
    val nextUpItems = catalog?.nextUpItems(playbackProgresses, limit = 5).orEmpty()
    val continueWatchingItems = catalog?.continueWatchingItems(playbackProgresses, limit = 5).orEmpty()
    val recentlyWatchedItems = catalog?.recentlyWatchedItems(playbackProgresses, limit = 5).orEmpty()
    val recentlyAddedItems = catalog
        ?.items
        .orEmpty()
        .sortedWith(
            compareByDescending<LibraryMediaItem> { it.indexedAtEpochMs }
                .thenBy { it.seriesTitle }
                .thenBy { it.episodeTitle },
        )
        .take(5)

    PageColumn(contentPadding) {
        item(key = "home-page-header") {
            PageHeader(
                icon = Icons.Filled.Home,
                title = stringResource(R.string.nav_home),
                subtitle = catalog?.let {
                    stringResource(R.string.home_available_episodes, it.items.size)
                } ?: stringResource(R.string.home_connect_library_subtitle),
            )
        }
        if (snapshot.source != null) {
            item(key = "home-mini-player") {
                MiniPlayerBar(
                    snapshot = snapshot,
                    nowPlaying = nowPlaying,
                    onPlayPause = onPlayPause,
                    onOpenPlayer = onOpenPlayer,
                )
            }
        }
        if (catalog == null) {
            item(key = "home-empty-library") {
                EmptyPanel(
                    title = stringResource(R.string.library_empty_connect_title),
                    body = stringResource(R.string.home_library_status_empty),
                    actionLabel = stringResource(R.string.action_connect_library),
                    onAction = onConnect,
                )
            }
        } else {
            if (nextUpItems.isNotEmpty()) {
                item(key = "home-next-up") {
                    NextUpPanel(
                        items = nextUpItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { onOpenLibrary() },
                        onPlay = onPlay,
                    )
                }
            }
            if (continueWatchingItems.isNotEmpty()) {
                item(key = "home-continue-watching") {
                    ProgressRail(
                        title = stringResource(R.string.home_continue_watching),
                        subtitle = stringResource(R.string.library_continue_watching_subtitle),
                        tag = "home-continue-watching",
                        itemTagPrefix = "home-continue",
                        items = continueWatchingItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { onOpenLibrary() },
                        onPlay = onPlay,
                    )
                }
            }
            if (recentlyAddedItems.isNotEmpty()) {
                item(key = "home-recently-added") {
                    RecentlyAddedRail(
                        items = recentlyAddedItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = onShowLibraryItem,
                        onPlay = onPlay,
                    )
                }
            }
            if (recentlyWatchedItems.isNotEmpty()) {
                item(key = "home-recently-watched") {
                    ProgressRail(
                        title = stringResource(R.string.home_recently_watched),
                        subtitle = stringResource(R.string.library_recently_watched_subtitle),
                        tag = "home-recently-watched",
                        itemTagPrefix = "home-recent",
                        items = recentlyWatchedItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { onOpenLibrary() },
                        onPlay = onPlay,
                    )
                }
            }
            item(key = "home-library-status") {
                EmptyPanel(
                    title = stringResource(R.string.home_library_status_title),
                    body = stringResource(R.string.home_library_status_connected, catalog.rootName),
                    actionLabel = stringResource(R.string.action_open_library),
                    onAction = onOpenLibrary,
                )
            }
        }
    }
}

@Composable
internal fun WatchPage(
    contentPadding: PaddingValues,
    controller: Media3PlaybackController?,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    playbackError: String?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onBrowseLibrary: () -> Unit,
) {
    PageColumn(contentPadding) {
        item(key = "player") {
            PlayerHome(
                controller = controller,
                snapshot = snapshot,
                nowPlaying = nowPlaying,
                playbackError = playbackError,
                onOpen = onOpen,
                onPlayPause = onPlayPause,
                onSeekTo = onSeekTo,
                onSetVolume = onSetVolume,
                onSelectAudio = onSelectAudio,
                onSelectSubtitle = onSelectSubtitle,
            )
        }
        item(key = "watch-actions") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("watch-library-actions"),
                shape = RoundedCornerShape(18.dp),
                color = PanelColor,
                border = BorderStroke(1.dp, Color(0xFF2B3239)),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.watch_library_playback_title),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.watch_library_playback_body),
                            color = SubtleText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = onBrowseLibrary) {
                        Text(stringResource(R.string.action_browse))
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryPage(
    contentPadding: PaddingValues,
    catalog: LibraryCatalog?,
    posterEndpoint: LibraryPosterEndpoint? = null,
    playbackProgresses: List<PlaybackProgress>,
    filteredItems: List<LibraryMediaItem>,
    totalCount: Int,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    sort: LibraryCatalogSort,
    onSortChange: (LibraryCatalogSort) -> Unit,
    subtitleFilter: LibrarySubtitleFilter,
    onSubtitleFilterChange: (LibrarySubtitleFilter) -> Unit,
    favoriteMediaIds: Set<String> = emptySet(),
    favoriteFilter: LibraryFavoriteFilter = LibraryFavoriteFilter.ANY,
    onFavoriteFilterChange: (LibraryFavoriteFilter) -> Unit = {},
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit = { _, _ -> },
    onPlay: (LibraryMediaItem) -> Unit,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    onConnect: () -> Unit,
) {
    var selectedEpisodeId by remember(catalog) { mutableStateOf<String?>(null) }
    val series = catalog?.groupedSeries().orEmpty()
    val nextUpItems = catalog?.nextUpItems(playbackProgresses, limit = 5).orEmpty()
    val continueWatchingItems = catalog?.continueWatchingItems(playbackProgresses, limit = 5).orEmpty()
    val recentlyWatchedItems = catalog?.recentlyWatchedItems(playbackProgresses, limit = 5).orEmpty()
    val watchStatusById = catalog?.watchStatusByMediaId(playbackProgresses).orEmpty()
    val seriesWatchSummaryById = catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty()
    val selectedSeries = searchText
        .takeIf(String::isNotBlank)
        ?.let { selectedTitle ->
            series.firstOrNull { it.title.equals(selectedTitle.trim(), ignoreCase = true) }
        }
    val selectedDetailId = selectedEpisodeId
        ?.takeIf { id -> filteredItems.any { it.id == id } }
        ?: nowPlaying?.id?.takeIf { id -> filteredItems.any { it.id == id } }
        ?: filteredItems.firstOrNull()?.id
    val selectedEpisodeDetail = selectedDetailId
        ?.let { id -> catalog?.episodeDetail(id, playbackProgresses) }

    fun LazyListScope.libraryFeedItems(showInlineEpisodeDetail: Boolean) {
        item(key = "library-page-header") {
            PageHeader(
                icon = Icons.Filled.VideoLibrary,
                title = stringResource(R.string.library_title),
                subtitle = if (catalog == null) {
                    stringResource(R.string.library_connect_browse_subtitle)
                } else {
                    stringResource(R.string.library_episode_count, filteredItems.size, totalCount)
                },
            )
        }
        if (snapshot.source != null) {
            item(key = "mini-player") {
                MiniPlayerBar(
                    snapshot = snapshot,
                    nowPlaying = nowPlaying,
                    onPlayPause = onPlayPause,
                    onOpenPlayer = onOpenPlayer,
                )
            }
        }
        item(key = "library-header") {
            LibraryHeader(
                catalog = catalog,
                filteredCount = filteredItems.size,
                totalCount = totalCount,
                searchText = searchText,
                onSearchTextChange = onSearchTextChange,
                sort = sort,
                onSortChange = onSortChange,
                subtitleFilter = subtitleFilter,
                onSubtitleFilterChange = onSubtitleFilterChange,
                favoriteMediaIds = favoriteMediaIds,
                favoriteFilter = favoriteFilter,
                onFavoriteFilterChange = onFavoriteFilterChange,
                onConnect = onConnect,
            )
        }
        catalog?.takeIf { it.items.isNotEmpty() }?.let {
            if (nextUpItems.isNotEmpty()) {
                item(key = "library-next-up") {
                    NextUpPanel(
                        items = nextUpItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { selectedEpisodeId = it.id },
                        onPlay = onPlay,
                    )
                }
            }
            if (continueWatchingItems.isNotEmpty()) {
                item(key = "library-continue-watching") {
                    ProgressRail(
                        title = stringResource(R.string.home_continue_watching),
                        subtitle = stringResource(R.string.library_continue_watching_subtitle),
                        tag = "library-continue-watching",
                        itemTagPrefix = "continue-watching",
                        items = continueWatchingItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { selectedEpisodeId = it.id },
                        onPlay = onPlay,
                    )
                }
            }
            if (recentlyWatchedItems.isNotEmpty()) {
                item(key = "library-recently-watched") {
                    ProgressRail(
                        title = stringResource(R.string.home_recently_watched),
                        subtitle = stringResource(R.string.library_recently_watched_subtitle),
                        tag = "library-recently-watched",
                        itemTagPrefix = "recently-watched",
                        items = recentlyWatchedItems,
                        posterEndpoint = posterEndpoint,
                        onShowDetails = { selectedEpisodeId = it.id },
                        onPlay = onPlay,
                    )
                }
            }
            item(key = "library-series-rail") {
                    SeriesRail(
                        series = series.take(12),
                        watchSummaryById = seriesWatchSummaryById,
                        posterEndpoint = posterEndpoint,
                        searchText = searchText,
                    onSelectSeries = onSearchTextChange,
                    onClearSearch = { onSearchTextChange("") },
                )
            }
            selectedSeries?.let { series ->
                item(key = "library-series-detail-${series.id}") {
                    SeriesDetailPanel(
                        series = series,
                        watchSummary = seriesWatchSummaryById[series.id],
                    )
                }
            }
        }
        if (showInlineEpisodeDetail) {
            selectedEpisodeDetail?.let { detail ->
                item(key = "episode-detail-${detail.mediaItem.id}") {
                    EpisodeDetailPanel(
                        detail = detail,
                        posterEndpoint = posterEndpoint,
                        isFavorite = detail.mediaItem.id in favoriteMediaIds,
                        onSetFavorite = { onSetFavorite(detail.mediaItem, it) },
                        onPlay = onPlay,
                        onSelectEpisode = { selectedEpisodeId = it.id },
                    )
                }
            }
        }
        if (catalog == null) {
            item(key = "library-empty") {
                EmptyLibraryState(onConnect = onConnect)
            }
        } else if (filteredItems.isEmpty()) {
            item(key = "library-no-results") {
                EmptyResultsState(
                    onResetFilters = {
                        onSearchTextChange("")
                        onSortChange(LibraryCatalogSort.TITLE)
                        onSubtitleFilterChange(LibrarySubtitleFilter.ANY)
                        onFavoriteFilterChange(LibraryFavoriteFilter.ANY)
                    },
                )
            }
        } else {
            items(filteredItems, key = LibraryMediaItem::id) { item ->
                EpisodeRow(
                    item = item,
                    posterEndpoint = posterEndpoint,
                    selected = nowPlaying?.id == item.id,
                    watchStatus = watchStatusById[item.id],
                    isFavorite = item.id in favoriteMediaIds,
                    onSetFavorite = { onSetFavorite(item, it) },
                    onShowDetails = { selectedEpisodeId = item.id },
                    onPlay = { onPlay(item) },
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(contentPadding)
            .safeDrawingPadding(),
    ) {
        val useTwoPane = maxWidth >= 840.dp
        if (useTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("library-master-pane"),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    libraryFeedItems(showInlineEpisodeDetail = false)
                }
                Column(
                    modifier = Modifier
                        .width(380.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag("library-detail-pane"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    selectedEpisodeDetail?.let { detail ->
                        EpisodeDetailPanel(
                            detail = detail,
                            posterEndpoint = posterEndpoint,
                            isFavorite = detail.mediaItem.id in favoriteMediaIds,
                            onSetFavorite = { onSetFavorite(detail.mediaItem, it) },
                            onPlay = onPlay,
                            onSelectEpisode = { selectedEpisodeId = it.id },
                        )
                    } ?: TabletDetailPlaceholder(catalog = catalog)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                libraryFeedItems(showInlineEpisodeDetail = true)
            }
        }
    }
}

@Composable
private fun TabletDetailPlaceholder(catalog: LibraryCatalog?) {
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
private fun EpisodeDetailPanel(
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

@Composable
private fun NextUpPanel(
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

@Composable
private fun LibraryPosterTile(
    item: LibraryMediaItem,
    title: String,
    selected: Boolean,
    posterEndpoint: LibraryPosterEndpoint?,
    progressLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val posterUrl = posterEndpoint?.posterUrl(item)
    val posterImage = rememberPosterImage(posterUrl)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentBlue else Color(0xFF26313A)),
    ) {
        if (posterImage.bitmap != null) {
            Image(
                bitmap = posterImage.bitmap,
                contentDescription = stringResource(R.string.poster_content_description, title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                title.initials(),
                color = if (selected) PlayerBlack else Color(0xFFE5E7EB),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (posterImage.state == PosterImageLoadState.LOADING) {
            PosterPill(
                label = "Loading",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
        progressLabel?.let {
            PosterPill(
                label = it,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun PosterPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.62f),
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentlyAddedRail(
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
private fun ProgressRail(
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
private fun SeriesRail(
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
private fun SeriesDetailPanel(
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

@Composable
internal fun ConnectPage(
    contentPadding: PaddingValues,
    catalog: LibraryCatalog?,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    serverUrl: String,
    pairingToken: String,
    savedConnections: List<LanLibraryConnectionProfile>,
    libraryError: String?,
    onServerUrlChange: (String) -> Unit,
    onPairingTokenChange: (String) -> Unit,
    onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
    onEditConnection: (LanLibraryConnectionProfile) -> Unit,
    onForgetConnection: (LanLibraryConnectionProfile) -> Unit,
    onSaveConnection: () -> Unit,
    onDiscover: () -> Unit,
    onRefresh: () -> Unit,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    PageColumn(contentPadding) {
        item(key = "connect-page-header") {
            PageHeader(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.nav_connect),
                subtitle = if (catalog == null) {
                    stringResource(R.string.connect_page_pair_subtitle)
                } else {
                    stringResource(R.string.connect_page_connected_to, catalog.rootName)
                },
            )
        }
        if (snapshot.source != null) {
            item(key = "mini-player") {
                MiniPlayerBar(
                    snapshot = snapshot,
                    nowPlaying = nowPlaying,
                    onPlayPause = onPlayPause,
                    onOpenPlayer = onOpenPlayer,
                )
            }
        }
        item(key = "connect") {
            ConnectionPanel(
                catalog = catalog,
                serverUrl = serverUrl,
                pairingToken = pairingToken,
                savedConnections = savedConnections,
                libraryError = libraryError,
                onServerUrlChange = onServerUrlChange,
                onPairingTokenChange = onPairingTokenChange,
                onSelectConnection = onSelectConnection,
                onEditConnection = onEditConnection,
                onForgetConnection = onForgetConnection,
                onSaveConnection = onSaveConnection,
                onDiscover = onDiscover,
                onRefresh = onRefresh,
            )
        }
        item(key = "connect-help") {
            EmptyPanel(
                title = stringResource(R.string.connect_help_title),
                body = stringResource(R.string.connect_help_body),
            )
        }
    }
}

@Composable
private fun PlayerHome(
    controller: Media3PlaybackController?,
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    playbackError: String?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("watch-player-home"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Danmaku",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    snapshot.sourceLabel(nowPlaying),
                    color = SubtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(snapshot.status.displayLabel())
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("watch-video-surface")
                .clip(RoundedCornerShape(22.dp))
                .background(PlayerBlack)
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = false
                    }
                },
                update = { it.player = controller?.player },
                modifier = Modifier.fillMaxSize(),
            )
            if (snapshot.source == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        stringResource(R.string.player_ready_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.player_ready_body),
                        color = SubtleText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        NowPlayingPanel(
            snapshot = snapshot,
            nowPlaying = nowPlaying,
            playbackError = playbackError,
            onOpen = onOpen,
            onPlayPause = onPlayPause,
            onSeekTo = onSeekTo,
            onSetVolume = onSetVolume,
            onSelectAudio = onSelectAudio,
            onSelectSubtitle = onSelectSubtitle,
        )
    }
}

@Composable
private fun NowPlayingPanel(
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    playbackError: String?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("now-playing-panel"),
        shape = RoundedCornerShape(20.dp),
        color = PanelColor,
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    nowPlaying?.seriesTitle ?: stringResource(R.string.now_playing_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    nowPlaying?.episodeTitle ?: stringResource(R.string.now_playing_empty_body),
                    color = SubtleText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            PlaybackSeekControls(snapshot = snapshot, onSeekTo = onSeekTo)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onPlayPause,
                    enabled = snapshot.source != null,
                    modifier = Modifier.testTag("watch-play-pause"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = PlayerBlack,
                    ),
                ) {
                    Icon(
                        imageVector = if (snapshot.status == PlaybackStatus.PLAYING) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (snapshot.status == PlaybackStatus.PLAYING) {
                            stringResource(R.string.action_pause)
                        } else {
                            stringResource(R.string.action_play)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.testTag("watch-open-video"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_open_video))
                }
                OutlinedButton(
                    onClick = { onSetVolume((snapshot.volumePercent - 10).coerceAtLeast(0)) },
                    enabled = snapshot.source != null && snapshot.volumePercent > 0,
                    modifier = Modifier.testTag("watch-volume-down"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("-10")
                }
                OutlinedButton(
                    onClick = { onSetVolume((snapshot.volumePercent + 10).coerceAtMost(100)) },
                    enabled = snapshot.source != null && snapshot.volumePercent < 100,
                    modifier = Modifier.testTag("watch-volume-up"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("+10")
                }
            }

            Text(
                stringResource(R.string.volume_percent, snapshot.volumePercent),
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
            )
            TrackControls(
                snapshot = snapshot,
                onSelectAudio = onSelectAudio,
                onSelectSubtitle = onSelectSubtitle,
            )
            playbackError?.let {
                ErrorText(stringResource(R.string.playback_error_prefix, it))
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    catalog: LibraryCatalog?,
    filteredCount: Int,
    totalCount: Int,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    sort: LibraryCatalogSort,
    onSortChange: (LibraryCatalogSort) -> Unit,
    subtitleFilter: LibrarySubtitleFilter,
    onSubtitleFilterChange: (LibrarySubtitleFilter) -> Unit,
    favoriteMediaIds: Set<String>,
    favoriteFilter: LibraryFavoriteFilter,
    onFavoriteFilterChange: (LibraryFavoriteFilter) -> Unit,
    onConnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PanelAltColor,
        border = BorderStroke(1.dp, Color(0xFF343D45)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.library_episodes_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        catalog?.rootName ?: stringResource(R.string.library_connect_browse_subtitle),
                        color = SubtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.library_favorites_count, favoriteMediaIds.size)) },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.library_episode_count, filteredCount, totalCount)) },
                )
                if (favoriteFilter == LibraryFavoriteFilter.FAVORITES_ONLY) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.library_favorites_only)) })
                }
                if (subtitleFilter == LibrarySubtitleFilter.WITH_SUBTITLES) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.library_subtitles_only)) })
                }
                if (sort == LibraryCatalogSort.PATH) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.library_path_sort)) })
                }
            }

            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                label = { Text(stringResource(R.string.library_search_episodes)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = favoriteFilter == LibraryFavoriteFilter.FAVORITES_ONLY,
                    onClick = {
                        onFavoriteFilterChange(
                            if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                                LibraryFavoriteFilter.FAVORITES_ONLY
                            } else {
                                LibraryFavoriteFilter.ANY
                            },
                        )
                    },
                    modifier = Modifier.testTag("library-favorites-filter"),
                    label = { Text(stringResource(R.string.library_filter_favorites)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                FilterChip(
                    selected = sort == LibraryCatalogSort.TITLE,
                    onClick = { onSortChange(LibraryCatalogSort.TITLE) },
                    label = { Text(stringResource(R.string.library_filter_title)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.SortByAlpha,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                FilterChip(
                    selected = sort == LibraryCatalogSort.PATH,
                    onClick = { onSortChange(LibraryCatalogSort.PATH) },
                    label = { Text(stringResource(R.string.library_filter_path)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                FilterChip(
                    selected = subtitleFilter == LibrarySubtitleFilter.WITH_SUBTITLES,
                    onClick = {
                        onSubtitleFilterChange(
                            if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                                LibrarySubtitleFilter.WITH_SUBTITLES
                            } else {
                                LibrarySubtitleFilter.ANY
                            },
                        )
                    },
                    label = { Text(stringResource(R.string.library_filter_subtitles)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Subtitles,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            if (catalog == null) {
                OutlinedButton(onClick = onConnect) {
                    Text(stringResource(R.string.action_connect_library))
                }
            }
        }
    }
}

@Composable
internal fun ConnectionPanel(
    catalog: LibraryCatalog?,
    serverUrl: String,
    pairingToken: String,
    savedConnections: List<LanLibraryConnectionProfile>,
    libraryError: String?,
    onServerUrlChange: (String) -> Unit,
    onPairingTokenChange: (String) -> Unit,
    onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
    onEditConnection: (LanLibraryConnectionProfile) -> Unit,
    onForgetConnection: (LanLibraryConnectionProfile) -> Unit,
    onSaveConnection: () -> Unit,
    onDiscover: () -> Unit,
    onRefresh: () -> Unit,
) {
    var showManualFields by remember(savedConnections.isEmpty()) {
        mutableStateOf(savedConnections.isEmpty())
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PanelAltColor,
        border = BorderStroke(1.dp, Color(0xFF343D45)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.connect_windows_pc),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        catalog?.rootName ?: serverUrl.serverDisplayName(),
                        color = SubtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(
                    if (catalog == null) {
                        stringResource(R.string.status_offline)
                    } else {
                        stringResource(R.string.status_ready)
                    },
                )
            }

            if (savedConnections.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.connect_saved_pcs_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.connect_saved_pcs_body),
                        color = SubtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedConnections.forEach { connection ->
                        SavedConnectionRow(
                            connection = connection,
                            isSelected = connection.normalizedBaseUrl == serverUrl.trim().trimEnd('/'),
                            onSelect = { onSelectConnection(connection) },
                            onEdit = {
                                onEditConnection(connection)
                                showManualFields = true
                            },
                            onForget = { onForgetConnection(connection) },
                        )
                    }
                }
            }

            if (showManualFields) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text(stringResource(R.string.connect_server_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pairingToken,
                    onValueChange = onPairingTokenChange,
                    label = { Text(stringResource(R.string.connect_pairing_code_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onDiscover) {
                    Text(stringResource(R.string.action_discover_pc))
                }
                if (showManualFields) {
                    OutlinedButton(onClick = onRefresh) {
                        Text(stringResource(R.string.action_connect_current))
                    }
                    OutlinedButton(onClick = onSaveConnection) {
                        Text(stringResource(R.string.action_save_current))
                    }
                } else {
                    OutlinedButton(onClick = { showManualFields = true }) {
                        Text(stringResource(R.string.action_manual_setup))
                    }
                }
            }

            libraryError?.let {
                ErrorText(stringResource(R.string.library_error_prefix, it))
            }
        }
    }
}

@Composable
private fun SavedConnectionRow(
    connection: LanLibraryConnectionProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onForget: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0xFF273747) else PanelColor,
        border = BorderStroke(1.dp, if (isSelected) AccentBlue else Color(0xFF343D45)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        connection.displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        connection.normalizedBaseUrl,
                        color = SubtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isSelected) {
                    StatusPill(stringResource(R.string.status_selected))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSelect,
                    modifier = Modifier.testTag("saved-connection:${connection.id}"),
                ) {
                    Text(
                        if (isSelected) {
                            stringResource(R.string.action_reconnect)
                        } else {
                            stringResource(R.string.action_connect)
                        },
                    )
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("saved-connection-edit:${connection.id}"),
                ) {
                    Text(stringResource(R.string.action_edit))
                }
                TextButton(
                    onClick = onForget,
                    modifier = Modifier.testTag("saved-connection-forget:${connection.id}"),
                ) {
                    Text(stringResource(R.string.action_forget))
                }
            }
        }
    }
}

@Composable
private fun PlaybackSeekControls(
    snapshot: PlaybackSnapshot,
    onSeekTo: (Long) -> Unit,
) {
    val durationMs = snapshot.position.durationMs?.takeIf { it > 0 }
    val currentPositionMs = snapshot.position.coerceSeekTarget(snapshot.position.positionMs)
    var sliderPositionMs by remember(snapshot.source, durationMs) {
        mutableStateOf(currentPositionMs.toFloat())
    }
    var isDragging by remember(snapshot.source, durationMs) { mutableStateOf(false) }

    LaunchedEffect(currentPositionMs, durationMs, isDragging) {
        if (!isDragging) {
            sliderPositionMs = currentPositionMs.toFloat()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Slider(
            value = durationMs
                ?.let { sliderPositionMs.coerceIn(0f, it.toFloat()) }
                ?: 0f,
            onValueChange = {
                isDragging = true
                sliderPositionMs = it
            },
            onValueChangeFinished = {
                durationMs?.let {
                    onSeekTo(snapshot.position.coerceSeekTarget(sliderPositionMs.toLong()))
                }
                isDragging = false
            },
            valueRange = 0f..(durationMs ?: 1L).toFloat(),
            enabled = snapshot.source != null && durationMs != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                currentPositionMs.formatPlaybackTime(),
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                durationMs?.formatPlaybackTime() ?: "--:--",
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SeekButton("-30s", snapshot, onSeekTo, -30_000)
            SeekButton("-10s", snapshot, onSeekTo, -10_000)
            SeekButton("+10s", snapshot, onSeekTo, 10_000)
            SeekButton("+30s", snapshot, onSeekTo, 30_000)
        }
    }
}

@Composable
private fun SeekButton(
    label: String,
    snapshot: PlaybackSnapshot,
    onSeekTo: (Long) -> Unit,
    deltaMs: Long,
) {
    TextButton(
        onClick = { onSeekTo(snapshot.position.seekTargetBy(deltaMs)) },
        enabled = snapshot.source != null,
        modifier = Modifier.testTag("watch-seek:$label"),
    ) {
        Text(label)
    }
}

@Composable
private fun TrackControls(
    snapshot: PlaybackSnapshot,
    onSelectAudio: (String) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
) {
    val audioTracks = snapshot.tracks.filter { it.kind == PlaybackTrackKind.AUDIO }
    val subtitleTracks = snapshot.tracks.filter { it.kind == PlaybackTrackKind.SUBTITLE }
    if (audioTracks.isNotEmpty() || subtitleTracks.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (audioTracks.isNotEmpty()) {
                Text(
                    stringResource(R.string.audio_tracks_title),
                    color = SubtleText,
                    style = MaterialTheme.typography.labelMedium,
                )
                TrackButtons(audioTracks, onSelectAudio)
            }
            if (subtitleTracks.isNotEmpty()) {
                Text(
                    stringResource(R.string.subtitle_tracks_title),
                    color = SubtleText,
                    style = MaterialTheme.typography.labelMedium,
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "subtitle-off") {
                        FilterChip(
                            selected = subtitleTracks.none(PlaybackTrack::selected),
                            onClick = { onSelectSubtitle(null) },
                            enabled = subtitleTracks.any(PlaybackTrack::selected),
                            label = { Text(stringResource(R.string.subtitle_off)) },
                        )
                    }
                    items(subtitleTracks, key = PlaybackTrack::id) { track ->
                        FilterChip(
                            selected = track.selected,
                            onClick = { onSelectSubtitle(track.id) },
                            enabled = track.supported && !track.selected,
                            label = { Text(track.buttonLabel()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackButtons(
    tracks: List<PlaybackTrack>,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tracks, key = PlaybackTrack::id) { track ->
            FilterChip(
                selected = track.selected,
                onClick = { onSelect(track.id) },
                enabled = track.supported && !track.selected,
                label = { Text(track.buttonLabel()) },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    item: LibraryMediaItem,
    posterEndpoint: LibraryPosterEndpoint?,
    selected: Boolean,
    watchStatus: LibraryWatchStatus?,
    isFavorite: Boolean,
    onSetFavorite: (Boolean) -> Unit,
    onShowDetails: () -> Unit,
    onPlay: () -> Unit,
) {
    val metadata = buildList {
        add(watchStatus.statusLabel())
        add(item.formatSize())
        add(stringResource(R.string.subtitle_track_count, item.subtitles.size))
        item.animeMetadata?.let { add(stringResource(R.string.matched_anime_short, it.displayTitle)) }
        if (item.posterPath != null) {
            add(stringResource(R.string.poster_ready))
        }
        item.metadataStatusLabel(
            loadingLabel = stringResource(R.string.metadata_loading),
            failedLabel = stringResource(R.string.metadata_failed),
        )?.let(::add)
        if (isFavorite) {
            add(stringResource(R.string.action_favorite))
        }
    }.joinToString(" · ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onShowDetails)
            .testTag("episode:${item.id}"),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFF263847) else PanelColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) AccentBlue else Color(0xFF2B3239),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryPosterTile(
                item = item,
                title = item.seriesTitle,
                selected = selected,
                posterEndpoint = posterEndpoint,
                progressLabel = null,
                modifier = Modifier
                    .size(52.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
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
                    metadata,
                    color = Color(0xFF8F9AA5),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = { onSetFavorite(!isFavorite) },
                modifier = Modifier.testTag("episode-favorite:${item.id}"),
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
            TextButton(
                onClick = onPlay,
                modifier = Modifier.testTag("episode-play:${item.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_play))
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    onConnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF15191D),
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.library_empty_connect_title),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.library_empty_connect_body),
                color = SubtleText,
            )
            OutlinedButton(onClick = onConnect) {
                Text(stringResource(R.string.action_open_connect))
            }
        }
    }
}

@Composable
private fun EmptyResultsState(onResetFilters: () -> Unit) {
    EmptyPanel(
        title = stringResource(R.string.library_no_results_title),
        body = stringResource(R.string.library_no_results_body),
        actionLabel = stringResource(R.string.action_reset_filters),
        onAction = onResetFilters,
    )
}

@Composable
private fun EmptyPanel(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF15191D),
        border = BorderStroke(1.dp, Color(0xFF2B3239)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = SubtleText)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Surface(
        shape = CircleShape,
        color = Color(0xFF1E2930),
        border = BorderStroke(1.dp, Color(0xFF35424D)),
    ) {
        Text(
            label,
            modifier = Modifier
                .widthIn(min = 72.dp)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = AccentAmber,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        color = DangerRed,
        style = MaterialTheme.typography.bodySmall,
    )
}
