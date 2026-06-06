package app.danmaku.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryEpisodeDetail
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryNextUpReason
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.filteredItems
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.seekTargetBy
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId
import app.danmaku.library.android.LanLibraryDiscoveryClient
import app.danmaku.library.android.LanLibraryClient
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.player.android.Media3PlaybackController
import app.danmaku.player.android.Media3PlaybackServiceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TvPlayerScreen()
            }
        }
    }
}

@Composable
private fun TvPlayerScreen() {
    val context = LocalContext.current
    val playbackConnection = remember {
        Media3PlaybackServiceConnection(context.applicationContext)
    }
    val libraryClient = remember { LanLibraryClient() }
    val progressSync = remember(libraryClient) {
        LanPlaybackProgressSync(libraryClient, System::currentTimeMillis)
    }
    val playbackPreparer = remember(libraryClient) { LanPlaybackPreparer(libraryClient) }
    val discoveryClient = remember { LanLibraryDiscoveryClient() }
    val refreshPcFocusRequester = remember { FocusRequester() }
    val discoverPcFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<Media3PlaybackController?>(null) }
    var snapshot by remember { mutableStateOf(PlaybackSnapshot()) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8686") }
    var pairingToken by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf<LibraryCatalog?>(null) }
    var playbackProgresses by remember { mutableStateOf<List<PlaybackProgress>>(emptyList()) }
    var libraryError by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(Unit) {
        discoverPcFocusRequester.requestFocus()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Danmaku TV", style = MaterialTheme.typography.headlineLarge)
            Text("Android TV PC-library streaming")
            AndroidView(
                factory = { PlayerView(it) },
                update = { it.player = controller?.player },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )
            Text("Player state: ${snapshot.status}")
            TvSeekControls(
                snapshot = snapshot,
                onSeekTo = { controller?.dispatch(PlaybackCommand.SeekTo(it)) },
            )
            playbackError?.let { Text("Playback connection error: $it") }
            TrackControls(
                snapshot = snapshot,
                onSelectAudio = {
                    controller?.dispatch(PlaybackCommand.SelectAudioTrack(it))
                },
                onSelectSubtitle = {
                    controller?.dispatch(PlaybackCommand.SelectSubtitleTrack(it))
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { controller?.dispatch(PlaybackCommand.Play) },
                    enabled = snapshot.source != null,
                ) {
                    Text("Play")
                }
                Button(
                    onClick = { controller?.dispatch(PlaybackCommand.Pause) },
                    enabled = snapshot.source != null,
                ) {
                    Text("Pause")
                }
                Button(
                    onClick = {
                        controller?.dispatch(
                            PlaybackCommand.SetVolume((snapshot.volumePercent - 10).coerceAtLeast(0)),
                        )
                    },
                    enabled = snapshot.source != null && snapshot.volumePercent > 0,
                ) {
                    Text("Vol -")
                }
                Button(
                    onClick = {
                        controller?.dispatch(
                            PlaybackCommand.SetVolume((snapshot.volumePercent + 10).coerceAtMost(100)),
                        )
                    },
                    enabled = snapshot.source != null && snapshot.volumePercent < 100,
                ) {
                    Text("Vol + ${snapshot.volumePercent}%")
                }
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val fetchedCatalog = libraryClient.fetchCatalog(serverUrl, pairingToken)
                                    val fetchedProgresses = runCatching {
                                        progressSync.fetchAllProgress(serverUrl, pairingToken)
                                    }.getOrElse { emptyList() }
                                    fetchedCatalog to fetchedProgresses
                                }
                            }.onSuccess {
                                catalog = it.first
                                playbackProgresses = it.second
                                libraryError = null
                            }.onFailure {
                                libraryError = it.message
                            }
                        }
                    },
                    modifier = Modifier
                        .focusRequester(refreshPcFocusRequester)
                        .focusProperties {
                            right = discoverPcFocusRequester
                        },
                ) {
                    Text("Refresh PC library")
                }
                Button(
                    onClick = {
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
                    },
                    modifier = Modifier
                        .focusRequester(discoverPcFocusRequester)
                        .focusProperties {
                            left = refreshPcFocusRequester
                        },
                ) {
                    Text("Discover PC")
                }
            }
            BasicTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(8.dp)
                    .focusable(),
            )
            BasicTextField(
                value = pairingToken,
                onValueChange = { pairingToken = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(8.dp)
                    .focusable(),
            )
            libraryError?.let { Text("Library error: $it") }
            LibraryItems(
                catalog = catalog,
                playbackProgresses = playbackProgresses,
                onPlay = { item ->
                    val activeController = controller ?: return@LibraryItems
                    val target = LanPlaybackTarget(serverUrl, pairingToken, item.id)
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
                },
            )
        }
    }
}

@Composable
private fun TvSeekControls(
    snapshot: PlaybackSnapshot,
    onSeekTo: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Position ${snapshot.position.positionMs.formatPlaybackTime()} / " +
                (snapshot.position.durationMs?.formatPlaybackTime() ?: "--:--"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvSeekButton("-30s", snapshot, onSeekTo, -30_000)
            TvSeekButton("-10s", snapshot, onSeekTo, -10_000)
            TvSeekButton("+10s", snapshot, onSeekTo, 10_000)
            TvSeekButton("+30s", snapshot, onSeekTo, 30_000)
        }
    }
}

@Composable
private fun TvSeekButton(
    label: String,
    snapshot: PlaybackSnapshot,
    onSeekTo: (Long) -> Unit,
    deltaMs: Long,
) {
    Button(
        onClick = { onSeekTo(snapshot.position.seekTargetBy(deltaMs)) },
        enabled = snapshot.source != null,
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
    if (audioTracks.isNotEmpty()) {
        Text("Audio tracks")
        TrackButtons(audioTracks, onSelectAudio)
    }
    if (subtitleTracks.isNotEmpty()) {
        Text("Subtitle tracks")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "subtitle-off") {
                Button(
                    onClick = { onSelectSubtitle(null) },
                    enabled = subtitleTracks.any(PlaybackTrack::selected),
                ) {
                    Text("Off")
                }
            }
            items(subtitleTracks, key = PlaybackTrack::id) { track ->
                Button(
                    onClick = { onSelectSubtitle(track.id) },
                    enabled = track.supported && !track.selected,
                ) {
                    Text(track.buttonLabel())
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tracks, key = PlaybackTrack::id) { track ->
            Button(
                onClick = { onSelect(track.id) },
                enabled = track.supported && !track.selected,
            ) {
                Text(track.buttonLabel())
            }
        }
    }
}

private fun PlaybackTrack.buttonLabel(): String =
    if (selected) "$label (selected)" else label

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

@Composable
internal fun LibraryItems(
    catalog: LibraryCatalog?,
    playbackProgresses: List<PlaybackProgress> = emptyList(),
    onPlay: (LibraryMediaItem) -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
    var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
    var selectedSeriesId by remember(catalog) { mutableStateOf<String?>(null) }
    var selectedEpisodeId by remember(catalog) { mutableStateOf<String?>(null) }
    val totalItems = catalog?.items.orEmpty()
    val filteredItems = catalog
        ?.filteredItems(
            LibraryCatalogQuery(
                searchText = searchText,
                sort = sort,
                subtitleFilter = subtitleFilter,
            ),
        )
        .orEmpty()
    val series = catalog?.groupedSeries().orEmpty().take(10)
    val nextUpItems = catalog?.nextUpItems(playbackProgresses, limit = 6).orEmpty()
    val continueWatchingItems = catalog?.continueWatchingItems(playbackProgresses, limit = 6).orEmpty()
    val recentlyWatchedItems = catalog?.recentlyWatchedItems(playbackProgresses, limit = 6).orEmpty()
    val watchStatusById = catalog?.watchStatusByMediaId(playbackProgresses).orEmpty()
    val seriesWatchSummaryById = catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty()
    val selectedSeries = series.firstOrNull { it.id == selectedSeriesId }
    val selectedDetailId = selectedEpisodeId
        ?.takeIf { id -> filteredItems.any { it.id == id } }
        ?: filteredItems.firstOrNull()?.id
    val selectedEpisodeDetail = selectedDetailId
        ?.let { id -> catalog?.episodeDetail(id, playbackProgresses) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "PC Library",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    catalog?.rootName ?: "Connect to a Windows library server",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("${filteredItems.size} / ${totalItems.size} episodes")
        }
        BasicTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(10.dp)
                .focusable(),
            decorationBox = { innerTextField ->
                if (searchText.isBlank()) {
                    Text("Search library")
                }
                innerTextField()
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Button(
                    onClick = { sort = LibraryCatalogSort.TITLE },
                    enabled = sort != LibraryCatalogSort.TITLE,
                ) {
                    Text("Sort title")
                }
            }
            item {
                Button(
                    onClick = { sort = LibraryCatalogSort.PATH },
                    enabled = sort != LibraryCatalogSort.PATH,
                ) {
                    Text("Sort path")
                }
            }
            item {
                Button(
                    onClick = {
                        subtitleFilter = if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                            LibrarySubtitleFilter.WITH_SUBTITLES
                        } else {
                            LibrarySubtitleFilter.ANY
                        }
                    },
                ) {
                    Text(if (subtitleFilter == LibrarySubtitleFilter.ANY) "Subtitles" else "All")
                }
            }
        }
        if (nextUpItems.isNotEmpty()) {
            TvNextUpRail(
                items = nextUpItems,
                onShowDetails = { selectedEpisodeId = it.id },
                onPlay = onPlay,
            )
        }
        if (continueWatchingItems.isNotEmpty()) {
            TvProgressRail(
                title = "Continue Watching",
                tag = "library-continue-watching",
                itemTagPrefix = "continue-watching",
                items = continueWatchingItems,
                onShowDetails = { selectedEpisodeId = it.id },
                onPlay = onPlay,
            )
        }
        if (recentlyWatchedItems.isNotEmpty()) {
            TvProgressRail(
                title = "Recently Watched",
                tag = "library-recently-watched",
                itemTagPrefix = "recently-watched",
                items = recentlyWatchedItems,
                onShowDetails = { selectedEpisodeId = it.id },
                onPlay = onPlay,
            )
        }
        if (series.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "all-series") {
                    Button(
                        onClick = {
                            selectedSeriesId = null
                            searchText = ""
                        },
                        enabled = searchText.isNotBlank() || selectedSeriesId != null,
                        modifier = Modifier.testTag("series:all"),
                    ) {
                        Text("All series")
                    }
                }
                items(series, key = { it.id }) { summary ->
                    Button(
                        onClick = {
                            val alreadySelected = selectedSeriesId == summary.id
                            selectedSeriesId = if (alreadySelected) null else summary.id
                            searchText = if (alreadySelected) {
                                ""
                            } else {
                                summary.title
                            }
                        },
                        modifier = Modifier.testTag("series:${summary.title}"),
                    ) {
                        Column {
                            Text(
                                summary.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(summary.episodeLabel())
                            Text(seriesWatchSummaryById[summary.id].shortProgressLabel())
                        }
                    }
                }
            }
        }
        selectedSeries?.let { summary ->
            TvSeriesDetail(
                series = summary,
                watchSummary = seriesWatchSummaryById[summary.id],
                onPlay = onPlay,
            )
        }
        selectedEpisodeDetail?.let { detail ->
            TvEpisodeDetail(
                detail = detail,
                onPlay = onPlay,
                onSelectEpisode = { selectedEpisodeId = it.id },
            )
        }
        LazyColumn(
            modifier = Modifier.height(320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filteredItems, key = LibraryMediaItem::id) { item ->
                TvEpisodeButton(
                    item = item,
                    watchStatus = watchStatusById[item.id],
                    onShowDetails = { selectedEpisodeId = item.id },
                    onPlay = { onPlay(item) },
                )
            }
        }
    }
}

@Composable
private fun TvEpisodeDetail(
    detail: LibraryEpisodeDetail,
    onPlay: (LibraryMediaItem) -> Unit,
    onSelectEpisode: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray)
            .padding(14.dp)
            .testTag("episode-detail:${detail.mediaItem.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    detail.mediaItem.episodeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${detail.series.title} / ${detail.season.label} / ${detail.watchStatus.statusLabel()}")
                Text(detail.mediaItem.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${detail.mediaItem.subtitles.size} subtitles")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onPlay(detail.mediaItem) }) {
                Text("Play")
            }
            Button(
                onClick = { detail.previousItem?.let(onSelectEpisode) },
                enabled = detail.previousItem != null,
            ) {
                Text("Previous")
            }
            Button(
                onClick = { detail.nextItem?.let(onSelectEpisode) },
                enabled = detail.nextItem != null,
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
private fun TvProgressRail(
    title: String,
    tag: String,
    itemTagPrefix: String,
    items: List<LibraryPlaybackProgressItem>,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray)
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
            Text("${items.size} episodes")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.mediaItem.id }) { item ->
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .background(Color.Black.copy(alpha = 0.18f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onShowDetails(item.mediaItem) },
                            modifier = Modifier.testTag("$itemTagPrefix-details:${item.mediaItem.id}"),
                        ) {
                            Text("Details")
                        }
                        Button(
                            onClick = { onPlay(item.mediaItem) },
                            modifier = Modifier.testTag("$itemTagPrefix:${item.mediaItem.id}"),
                        ) {
                            Text("Play")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvNextUpRail(
    items: List<LibraryNextUpItem>,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray)
            .padding(14.dp)
            .testTag("library-next-up"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Next Up",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text("${items.size} picks")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.mediaItem.id }) { item ->
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .background(Color.Black.copy(alpha = 0.18f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onShowDetails(item.mediaItem) },
                            modifier = Modifier.testTag("next-up-details:${item.mediaItem.id}"),
                        ) {
                            Text("Details")
                        }
                        Button(
                            onClick = { onPlay(item.mediaItem) },
                            modifier = Modifier.testTag("next-up:${item.mediaItem.id}"),
                        ) {
                            Text(item.nextUpActionLabel())
                        }
                    }
                }
            }
        }
    }
}

private fun LibraryNextUpItem.nextUpActionLabel(): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> "Resume"
        LibraryNextUpReason.NEXT_EPISODE,
        LibraryNextUpReason.START -> "Play"
    }

private fun LibraryNextUpItem.nextUpLabel(): String =
    when (reason) {
        LibraryNextUpReason.RESUME ->
            "Resume at ${progress?.positionMs?.formatPlaybackTime() ?: "saved position"}"
        LibraryNextUpReason.NEXT_EPISODE -> "Next episode"
        LibraryNextUpReason.START -> "Start watching"
    }

@Composable
private fun TvSeriesDetail(
    series: LibrarySeries,
    watchSummary: LibrarySeriesWatchSummary?,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray)
            .padding(14.dp)
            .testTag("series-detail:${series.title}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    series.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${series.episodeLabel()} across ${series.seasons.size} seasons")
                Text(watchSummary.progressLabel())
            }
            Text("${series.subtitleTrackCount} subtitle tracks")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(series.seasons, key = { it.id }) { season ->
                Column(
                    modifier = Modifier
                        .width(340.dp)
                        .testTag("series-season:${season.label}"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${season.label} (${season.items.size})", fontWeight = FontWeight.SemiBold)
                    season.items.take(3).forEach { item ->
                        Button(
                            onClick = { onPlay(item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("series-detail-episode:${item.id}"),
                        ) {
                            Text(item.episodeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (season.items.size > 3) {
                        Text("${season.items.size - 3} more episodes")
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEpisodeButton(
    item: LibraryMediaItem,
    watchStatus: LibraryWatchStatus?,
    onShowDetails: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier
                .weight(1f)
                .testTag("episode:${item.id}"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                        watchStatus.statusLabel(),
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Text("${item.subtitles.size} subs")
            }
        }
        Button(
            onClick = onShowDetails,
            modifier = Modifier.testTag("episode-details:${item.id}"),
        ) {
            Text("Details")
        }
    }
}

private fun LibrarySeries.episodeLabel(): String =
    if (episodeCount == 1) "1 episode" else "$episodeCount episodes"

private fun LibrarySeriesWatchSummary?.shortProgressLabel(): String =
    if (this == null) {
        "0 watched"
    } else {
        "$watchedCount watched, $inProgressCount watching"
    }

private fun LibrarySeriesWatchSummary?.progressLabel(): String =
    if (this == null) {
        "0 watched, 0 watching"
    } else {
        "$watchedCount watched, $inProgressCount watching, $newCount new"
    }

private fun LibraryWatchStatus?.statusLabel(): String =
    when (this?.state) {
        LibraryWatchState.WATCHED -> "Watched"
        LibraryWatchState.IN_PROGRESS -> {
            val progress = progress
            "In progress" + if (progress == null) {
                ""
            } else {
                " ${progress.positionMs.formatPlaybackTime()} / " +
                    (progress.durationMs?.formatPlaybackTime() ?: "--:--")
            }
        }
        LibraryWatchState.NEW,
        null -> "New"
    }

private fun PlaybackProgress.progressLabel(): String =
    "${positionMs.formatPlaybackTime()} / ${durationMs?.formatPlaybackTime() ?: "--:--"}"
