package app.danmaku.mobile

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryEpisodeDetail
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryNextUpReason
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.coerceSeekTarget
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.filteredItems
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
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.LanLibraryClient
import app.danmaku.library.android.LanLibraryDiscoveryClient
import app.danmaku.player.android.Media3PlaybackController
import app.danmaku.player.android.Media3PlaybackServiceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

private val AppBackground = Color(0xFF101214)
private val PlayerBlack = Color(0xFF050607)
private val PanelColor = Color(0xFF191D21)
private val PanelAltColor = Color(0xFF20262B)
private val SubtleText = Color(0xFFB7C0C9)
private val AccentBlue = Color(0xFF7DD3FC)
private val AccentAmber = Color(0xFFFBBF24)
private val DangerRed = Color(0xFFFCA5A5)

internal data class LibraryPosterEndpoint(
    val baseUrl: String,
    val pairingToken: String,
) {
    fun posterUrl(item: LibraryMediaItem): String? {
        val path = item.posterPath ?: return null
        return "${baseUrl.trim().trimEnd('/')}$path?token=${pairingToken.encodedQueryValue()}"
    }
}

private enum class PosterImageLoadState {
    IDLE,
    LOADING,
    LOADED,
    FAILED,
}

private enum class MobileTab(
    val label: String,
    val icon: ImageVector,
) {
    Watch("Watch", Icons.Filled.PlayArrow),
    Library("Library", Icons.Filled.VideoLibrary),
    Connect("Connect", Icons.Filled.Settings),
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
    var controller by remember { mutableStateOf<Media3PlaybackController?>(null) }
    var snapshot by remember { mutableStateOf(PlaybackSnapshot()) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8686") }
    var pairingToken by remember { mutableStateOf("") }
    var savedConnections by remember { mutableStateOf(connectionStore.loadProfiles()) }
    var catalog by remember { mutableStateOf<LibraryCatalog?>(null) }
    var playbackProgresses by remember { mutableStateOf<List<PlaybackProgress>>(emptyList()) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var librarySearchText by remember { mutableStateOf("") }
    var librarySort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
    var librarySubtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
    var libraryFavoriteFilter by remember { mutableStateOf(LibraryFavoriteFilter.ANY) }
    var favoriteMediaIds by remember { mutableStateOf(favoriteStore.loadFavoriteMediaIds()) }
    var nowPlaying by remember { mutableStateOf<LibraryMediaItem?>(null) }
    var selectedTab by remember { mutableStateOf(MobileTab.Watch) }
    val totalItems = catalog?.items.orEmpty()
    val filteredItems = catalog
        ?.filteredItems(
            LibraryCatalogQuery(
                searchText = librarySearchText,
                sort = librarySort,
                subtitleFilter = librarySubtitleFilter,
                favoriteFilter = libraryFavoriteFilter,
                favoriteMediaIds = favoriteMediaIds,
            ),
        )
        .orEmpty()
    val posterEndpoint = catalog?.let {
        LibraryPosterEndpoint(serverUrl, pairingToken)
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        controller?.let {
            nowPlaying = null
            it.load(PlaybackSource.LocalFile(uri.toString()))
            snapshot = it.snapshot()
        }
    }

    DisposableEffect(playbackConnection) {
        playbackConnection.connect(
            executor = ContextCompat.getMainExecutor(context),
            onConnected = {
                controller = it
                snapshot = it.snapshot()
                playbackError = null
            },
            onFailure = {
                playbackError = it.message
            },
        )
        onDispose {
            controller = null
            playbackConnection.close()
        }
    }

    LaunchedEffect(controller) {
        val activeController = controller ?: return@LaunchedEffect
        while (true) {
            snapshot = activeController.snapshot()
            delay(250)
        }
    }

    val discoverPc: () -> Unit = {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    discoveryClient.discover().firstOrNull()
                        ?: error("No Windows library server discovered")
                }
            }.onSuccess {
                serverUrl = it.baseUrl
                libraryError = null
            }.onFailure {
                libraryError = it.message
            }
        }
    }
    fun connectToLibrary(
        requestedServerUrl: String,
        requestedPairingToken: String,
        fallbackDisplayName: String? = null,
    ) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryConnectionSession.fetchCatalogWithProgress(
                        baseUrl = requestedServerUrl,
                        pairingToken = requestedPairingToken,
                    )
                }
            }.onSuccess {
                serverUrl = requestedServerUrl.trim().trimEnd('/')
                pairingToken = requestedPairingToken
                catalog = it.catalog
                playbackProgresses = it.playbackProgresses
                connectionStore.saveCurrentConnection(
                    baseUrl = requestedServerUrl,
                    pairingToken = requestedPairingToken,
                    displayName = it.catalog.rootName.ifBlank { fallbackDisplayName },
                )
                savedConnections = connectionStore.loadProfiles()
                libraryError = null
                selectedTab = MobileTab.Library
            }.onFailure {
                libraryError = it.message
            }
        }
    }
    val refreshLibrary: () -> Unit = {
        connectToLibrary(serverUrl, pairingToken)
    }
    val setFavorite: (LibraryMediaItem, Boolean) -> Unit = { item, isFavorite ->
        runCatching {
            favoriteStore.setFavoriteMediaId(item.id, isFavorite)
        }.onSuccess {
            favoriteMediaIds = it
            libraryError = null
        }.onFailure {
            libraryError = it.message
        }
    }
    val playEpisode: (LibraryMediaItem) -> Unit = { item ->
        val activeController = controller
        if (activeController == null) {
            playbackError = "Player service is not connected yet."
        } else {
            val target = LanPlaybackTarget(serverUrl, pairingToken, item.id)
            nowPlaying = item
            selectedTab = MobileTab.Watch
            scope.launch {
                val resumePosition = runCatching {
                    withContext(Dispatchers.IO) {
                        progressSync.fetchResumePositionMs(target)
                    }
                }.onFailure {
                    libraryError = "Resume lookup failed: ${it.message}"
                }.getOrNull()
                val preparation = playbackPreparer.prepare(
                    baseUrl = target.baseUrl,
                    pairingToken = target.pairingToken,
                    item = item,
                    resumePositionMs = resumePosition,
                )
                activeController.load(preparation)
                preparation.resumePositionMs?.let {
                    activeController.dispatch(PlaybackCommand.SeekTo(it))
                }
                activeController.dispatch(PlaybackCommand.Play)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppBackground,
        bottomBar = {
            MobileBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { innerPadding ->
        when (selectedTab) {
            MobileTab.Watch -> WatchPage(
                contentPadding = innerPadding,
                controller = controller,
                snapshot = snapshot,
                nowPlaying = nowPlaying,
                playbackError = playbackError,
                onOpen = { openDocument.launch(arrayOf("video/*")) },
                onPlayPause = {
                    if (snapshot.status == PlaybackStatus.PLAYING) {
                        controller?.dispatch(PlaybackCommand.Pause)
                    } else {
                        controller?.dispatch(PlaybackCommand.Play)
                    }
                },
                onSeekTo = { controller?.dispatch(PlaybackCommand.SeekTo(it)) },
                onSetVolume = { controller?.dispatch(PlaybackCommand.SetVolume(it)) },
                onSelectAudio = { controller?.dispatch(PlaybackCommand.SelectAudioTrack(it)) },
                onSelectSubtitle = { controller?.dispatch(PlaybackCommand.SelectSubtitleTrack(it)) },
                onBrowseLibrary = { selectedTab = MobileTab.Library },
            )
            MobileTab.Library -> LibraryPage(
                contentPadding = innerPadding,
                catalog = catalog,
                posterEndpoint = posterEndpoint,
                playbackProgresses = playbackProgresses,
                filteredItems = filteredItems,
                totalCount = totalItems.size,
                snapshot = snapshot,
                nowPlaying = nowPlaying,
                searchText = librarySearchText,
                onSearchTextChange = { librarySearchText = it },
                sort = librarySort,
                onSortChange = { librarySort = it },
                subtitleFilter = librarySubtitleFilter,
                onSubtitleFilterChange = { librarySubtitleFilter = it },
                favoriteMediaIds = favoriteMediaIds,
                favoriteFilter = libraryFavoriteFilter,
                onFavoriteFilterChange = { libraryFavoriteFilter = it },
                onSetFavorite = setFavorite,
                onPlay = playEpisode,
                onPlayPause = {
                    if (snapshot.status == PlaybackStatus.PLAYING) {
                        controller?.dispatch(PlaybackCommand.Pause)
                    } else {
                        controller?.dispatch(PlaybackCommand.Play)
                    }
                },
                onOpenPlayer = { selectedTab = MobileTab.Watch },
                onConnect = { selectedTab = MobileTab.Connect },
            )
            MobileTab.Connect -> ConnectPage(
                contentPadding = innerPadding,
                catalog = catalog,
                snapshot = snapshot,
                nowPlaying = nowPlaying,
                serverUrl = serverUrl,
                pairingToken = pairingToken,
                savedConnections = savedConnections,
                libraryError = libraryError,
                onServerUrlChange = { serverUrl = it },
                onPairingTokenChange = { pairingToken = it },
                onSelectConnection = {
                    connectToLibrary(
                        requestedServerUrl = it.baseUrl,
                        requestedPairingToken = it.pairingToken,
                        fallbackDisplayName = it.displayName,
                    )
                },
                onEditConnection = {
                    serverUrl = it.baseUrl
                    pairingToken = it.pairingToken
                },
                onForgetConnection = {
                    connectionStore.forgetProfile(it.id)
                    savedConnections = connectionStore.loadProfiles()
                },
                onSaveConnection = {
                    runCatching {
                        connectionStore.saveCurrentConnection(
                            baseUrl = serverUrl,
                            pairingToken = pairingToken,
                            displayName = catalog?.rootName,
                        )
                    }.onSuccess {
                        savedConnections = connectionStore.loadProfiles()
                        libraryError = null
                    }.onFailure {
                        libraryError = it.message
                    }
                },
                onDiscover = discoverPc,
                onRefresh = refreshLibrary,
                onPlayPause = {
                    if (snapshot.status == PlaybackStatus.PLAYING) {
                        controller?.dispatch(PlaybackCommand.Pause)
                    } else {
                        controller?.dispatch(PlaybackCommand.Play)
                    }
                },
                onOpenPlayer = { selectedTab = MobileTab.Watch },
            )
        }
    }
}

@Composable
private fun MobileBottomBar(
    selectedTab: MobileTab,
    onTabSelected: (MobileTab) -> Unit,
) {
    NavigationBar(
        containerColor = Color(0xFF15191D),
        tonalElevation = 0.dp,
    ) {
        MobileTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = { Text(tab.label) },
            )
        }
    }
}

@Composable
private fun PageColumn(
    contentPadding: PaddingValues,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(contentPadding)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
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
                        Text("Library playback", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Pick episodes from your PC catalog without leaving the player.",
                            color = SubtleText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = onBrowseLibrary) {
                        Text("Browse")
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
                title = "Library",
                subtitle = if (catalog == null) {
                    "Connect a Windows library to browse episodes"
                } else {
                    "${filteredItems.size} of $totalCount episodes"
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
                        title = "Continue Watching",
                        subtitle = "Resume episodes with enough saved progress.",
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
                        title = "Recently Watched",
                        subtitle = "Recent activity from your paired Windows library.",
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
                if (catalog == null) "No library connected" else "Select an episode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (catalog == null) {
                    "Connect to the Windows library server to keep episode details visible here."
                } else {
                    "Episode artwork, status, subtitles, and navigation stay pinned in this pane."
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
                            "Matched anime: ${metadata.displayTitle}",
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
                AssistChip(onClick = {}, label = { Text("${detail.mediaItem.subtitles.size} subtitles") })
                AssistChip(onClick = {}, label = { Text(detail.mediaItem.mediaType) })
                detail.mediaItem.posterPath?.let {
                    AssistChip(onClick = {}, label = { Text("Poster ready") })
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
                    Text("Play")
                }
                OutlinedButton(
                    onClick = { onSetFavorite(!isFavorite) },
                    modifier = Modifier.testTag("episode-detail-favorite:${detail.mediaItem.id}"),
                ) {
                    Text(if (isFavorite) "Unfavorite" else "Favorite")
                }
                OutlinedButton(
                    onClick = { detail.previousItem?.let(onSelectEpisode) },
                    enabled = detail.previousItem != null,
                ) {
                    Text("Previous")
                }
                OutlinedButton(
                    onClick = { detail.nextItem?.let(onSelectEpisode) },
                    enabled = detail.nextItem != null,
                ) {
                    Text("Next")
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
                    "Next Up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Resume or continue from your Windows library progress.",
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
                    Text("Details")
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
                contentDescription = "Poster for $title",
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
                    Text("Details")
                }
                Button(
                    onClick = onPlay,
                    modifier = Modifier.testTag(playTag),
                ) {
                    Text("Play")
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
                        "Series",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${series.size} most common titles",
                        color = SubtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(
                    onClick = onClearSearch,
                    enabled = searchText.isNotBlank(),
                ) {
                    Text("Clear")
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
                AssistChip(onClick = {}, label = { Text("${series.subtitleTrackCount} subtitles") })
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
private fun ConnectPage(
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
                title = "Connect",
                subtitle = if (catalog == null) {
                    "Pair this phone with the Windows library server"
                } else {
                    "Connected to ${catalog.rootName}"
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
                title = "Pair once, watch anywhere",
                body = "Open the Windows app on the same network, use Discover PC, then enter the pairing code shown on desktop.",
            )
        }
    }
}

@Composable
private fun PageHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E2930)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                color = SubtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    snapshot: PlaybackSnapshot,
    nowPlaying: LibraryMediaItem?,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenPlayer)
            .testTag("mini-player"),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF17212A),
        border = BorderStroke(1.dp, Color(0xFF304454)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (snapshot.status == PlaybackStatus.PLAYING) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    tint = PlayerBlack,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    nowPlaying?.seriesTitle ?: snapshot.sourceLabel(nowPlaying),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    nowPlaying?.episodeTitle
                        ?: "${snapshot.position.positionMs.formatPlaybackTime()} · ${snapshot.status.displayLabel()}",
                    color = SubtleText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (snapshot.status == PlaybackStatus.PLAYING) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (snapshot.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                )
            }
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
                        "Ready to play",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Open a video or choose an episode from your PC library.",
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
                    nowPlaying?.seriesTitle ?: "No episode selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    nowPlaying?.episodeTitle ?: "Local files and LAN streams appear here while playing.",
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
                        if (snapshot.status == PlaybackStatus.PLAYING) "Pause" else "Play",
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
                    Text("Open video")
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
                "Volume ${snapshot.volumePercent}%",
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
            )
            TrackControls(
                snapshot = snapshot,
                onSelectAudio = onSelectAudio,
                onSelectSubtitle = onSelectSubtitle,
            )
            playbackError?.let {
                ErrorText("Playback connection error: $it")
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
                        "Episodes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        catalog?.rootName ?: "Connect a Windows library to browse episodes",
                        color = SubtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("$filteredCount/$totalCount") },
                )
            }
            Text(
                "${favoriteMediaIds.size} favorites",
                color = SubtleText,
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = {}, label = { Text("$filteredCount of $totalCount episodes") })
                if (favoriteFilter == LibraryFavoriteFilter.FAVORITES_ONLY) {
                    AssistChip(onClick = {}, label = { Text("Favorites only") })
                }
                if (subtitleFilter == LibrarySubtitleFilter.WITH_SUBTITLES) {
                    AssistChip(onClick = {}, label = { Text("Subtitles only") })
                }
                if (sort == LibraryCatalogSort.PATH) {
                    AssistChip(onClick = {}, label = { Text("Path sort") })
                }
            }

            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                label = { Text("Search episodes") },
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
                    label = { Text("Favorites") },
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
                    label = { Text("Title") },
                )
                FilterChip(
                    selected = sort == LibraryCatalogSort.PATH,
                    onClick = { onSortChange(LibraryCatalogSort.PATH) },
                    label = { Text("Path") },
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
                    label = { Text("Subtitles") },
                )
            }

            if (catalog == null) {
                OutlinedButton(onClick = onConnect) {
                    Text("Connect library")
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
                        "Windows PC",
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
                StatusPill(if (catalog == null) "Offline" else "Ready")
            }

            if (savedConnections.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Saved PCs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Connect to a remembered Windows library or edit the manual pairing details.",
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
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pairingToken,
                    onValueChange = onPairingTokenChange,
                    label = { Text("Pairing code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onDiscover) {
                    Text("Discover PC")
                }
                if (showManualFields) {
                    OutlinedButton(onClick = onRefresh) {
                        Text("Connect current")
                    }
                    OutlinedButton(onClick = onSaveConnection) {
                        Text("Save current")
                    }
                } else {
                    OutlinedButton(onClick = { showManualFields = true }) {
                        Text("Manual setup")
                    }
                }
            }

            libraryError?.let {
                ErrorText("Library error: $it")
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
                    StatusPill("Selected")
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
                    Text(if (isSelected) "Reconnect" else "Connect")
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("saved-connection-edit:${connection.id}"),
                ) {
                    Text("Edit")
                }
                TextButton(
                    onClick = onForget,
                    modifier = Modifier.testTag("saved-connection-forget:${connection.id}"),
                ) {
                    Text("Forget")
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
                Text("Audio", color = SubtleText, style = MaterialTheme.typography.labelMedium)
                TrackButtons(audioTracks, onSelectAudio)
            }
            if (subtitleTracks.isNotEmpty()) {
                Text("Subtitles", color = SubtleText, style = MaterialTheme.typography.labelMedium)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "subtitle-off") {
                        FilterChip(
                            selected = subtitleTracks.none(PlaybackTrack::selected),
                            onClick = { onSelectSubtitle(null) },
                            enabled = subtitleTracks.any(PlaybackTrack::selected),
                            label = { Text("Off") },
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
        add("${item.subtitles.size} subtitle tracks")
        item.animeMetadata?.let { add("Matched: ${it.displayTitle}") }
        if (item.posterPath != null) {
            add("Poster ready")
        }
        if (isFavorite) {
            add("Favorite")
        }
    }.joinToString(" · ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onPlay)
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
                Text(if (isFavorite) "Unfavorite" else "Favorite")
            }
            TextButton(
                onClick = onShowDetails,
                modifier = Modifier.testTag("episode-details:${item.id}"),
            ) {
                Text("Details")
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
            Text("Connect to your Windows library", fontWeight = FontWeight.SemiBold)
            Text(
                "Use discovery when the desktop app is open, then refresh the catalog.",
                color = SubtleText,
            )
            OutlinedButton(onClick = onConnect) {
                Text("Open Connect")
            }
        }
    }
}

@Composable
private fun EmptyResultsState(onResetFilters: () -> Unit) {
    EmptyPanel(
        title = "No matching episodes",
        body = "Try a different search, sort order, subtitle filter, or favorites filter.",
        actionLabel = "Reset filters",
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

private fun PlaybackSnapshot.sourceLabel(nowPlaying: LibraryMediaItem?): String =
    when {
        nowPlaying != null -> "${nowPlaying.seriesTitle} · ${nowPlaying.episodeTitle}"
        source is PlaybackSource.LocalFile -> "Local video"
        source is PlaybackSource.RemoteStream -> "LAN stream"
        else -> "Select a video to start watching"
    }

private fun PlaybackStatus.displayLabel(): String =
    name.lowercase().replaceFirstChar { it.uppercaseChar() }

private fun PlaybackTrack.buttonLabel(): String =
    if (selected) "$label (selected)" else label

private fun String.serverDisplayName(): String =
    removePrefix("http://")
        .removePrefix("https://")
        .ifBlank { "No server selected" }

private data class PosterImageState(
    val bitmap: ImageBitmap?,
    val state: PosterImageLoadState,
)

@Composable
private fun rememberPosterImage(url: String?): PosterImageState {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var state by remember(url) { mutableStateOf(PosterImageLoadState.IDLE) }

    LaunchedEffect(url) {
        bitmap = null
        if (url == null) {
            state = PosterImageLoadState.IDLE
            return@LaunchedEffect
        }
        state = PosterImageLoadState.LOADING
        val loaded = withContext(Dispatchers.IO) {
            loadPosterImage(url)
        }
        bitmap = loaded
        state = if (loaded == null) PosterImageLoadState.FAILED else PosterImageLoadState.LOADED
    }

    return PosterImageState(bitmap, state)
}

private fun loadPosterImage(url: String): ImageBitmap? {
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 3_000
        readTimeout = 5_000
        requestMethod = "GET"
    }
    return try {
        if (connection.responseCode !in 200..299) {
            null
        } else {
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun String.encodedQueryValue(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())

private fun String.initials(): String {
    val words = trim()
        .split(' ', '.', '_', '-', '[', ']')
        .filter(String::isNotBlank)
    return words
        .take(2)
        .joinToString(separator = "") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
}

private fun LibraryMediaItem.formatSize(): String {
    return sizeBytes.formatSize()
}

private fun Long.formatSize(): String {
    val mib = toDouble() / (1024.0 * 1024.0)
    val gib = mib / 1024.0
    return if (gib >= 1.0) {
        "${gib.formatOneDecimal()} GB"
    } else {
        "${mib.formatOneDecimal()} MB"
    }
}

private fun LibrarySeries.displayLabel(watchSummary: LibrarySeriesWatchSummary?): String {
    val episodeLabel = if (episodeCount == 1) "1 ep" else "$episodeCount eps"
    val progressLabel = watchSummary.progressLabel()
    return if (subtitleTrackCount > 0) {
        "$title · $episodeLabel · $progressLabel · $subtitleTrackCount sub"
    } else {
        "$title · $episodeLabel · $progressLabel"
    }
}

private fun LibrarySeriesWatchSummary?.progressLabel(): String =
    if (this == null) {
        "0 watched"
    } else {
        "${watchedCount} watched · ${inProgressCount} watching · ${newCount} new"
    }

private fun LibraryNextUpItem.nextUpLabel(): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> "Resume at ${progress?.positionMs?.formatPlaybackTime() ?: "saved position"}"
        LibraryNextUpReason.NEXT_EPISODE -> "Next episode"
        LibraryNextUpReason.START -> "Start watching"
    }

private fun LibraryNextUpItem.nextUpActionLabel(): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> "Resume"
        LibraryNextUpReason.NEXT_EPISODE,
        LibraryNextUpReason.START -> "Play"
    }

private fun LibraryWatchStatus?.statusLabel(): String =
    when (this?.state) {
        LibraryWatchState.WATCHED -> "Watched"
        LibraryWatchState.IN_PROGRESS -> {
            val progress = progress
            "In progress" + if (progress == null) {
                ""
            } else {
                " · ${progress.positionMs.formatPlaybackTime()} / " +
                    (progress.durationMs?.formatPlaybackTime() ?: "--:--")
            }
        }
        LibraryWatchState.NEW,
        null -> "New"
    }

private fun PlaybackProgress.progressLabel(): String =
    "${positionMs.formatPlaybackTime()} / ${durationMs?.formatPlaybackTime() ?: "--:--"}"

private fun Double.formatOneDecimal(): String {
    val scaled = (this * 10).toLong()
    return "${scaled / 10}.${scaled % 10}"
}

private fun Long.formatPlaybackTime(): String {
    val totalSeconds = this.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
