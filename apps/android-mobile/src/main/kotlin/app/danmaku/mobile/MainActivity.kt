package app.danmaku.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.coerceSeekTarget
import app.danmaku.domain.filteredItems
import app.danmaku.domain.seekTargetBy
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
    val progressSync = remember(libraryClient) {
        LanPlaybackProgressSync(libraryClient, System::currentTimeMillis)
    }
    val playbackPreparer = remember(libraryClient) { LanPlaybackPreparer(libraryClient) }
    val discoveryClient = remember { LanLibraryDiscoveryClient() }
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<Media3PlaybackController?>(null) }
    var snapshot by remember { mutableStateOf(PlaybackSnapshot()) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8686") }
    var pairingToken by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf<LibraryCatalog?>(null) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        controller?.let {
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Danmaku", style = MaterialTheme.typography.headlineMedium)
            Text("Android library streaming")
            AndroidView(
                factory = { PlayerView(it) },
                update = { it.player = controller?.player },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
            Text("Player state: ${snapshot.status}")
            playbackError?.let { Text("Playback connection error: $it") }
            PlayerControls(
                snapshot = snapshot,
                onOpen = { openDocument.launch(arrayOf("video/*")) },
                onPlay = { controller?.dispatch(PlaybackCommand.Play) },
                onPause = { controller?.dispatch(PlaybackCommand.Pause) },
                onSeekTo = { controller?.dispatch(PlaybackCommand.SeekTo(it)) },
                onSetVolume = { controller?.dispatch(PlaybackCommand.SetVolume(it)) },
            )
            TrackControls(
                snapshot = snapshot,
                onSelectAudio = {
                    controller?.dispatch(PlaybackCommand.SelectAudioTrack(it))
                },
                onSelectSubtitle = {
                    controller?.dispatch(PlaybackCommand.SelectSubtitleTrack(it))
                },
            )
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Windows server URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pairingToken,
                onValueChange = { pairingToken = it },
                label = { Text("Pairing code") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                ) {
                    Text("Discover PC")
                }
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    libraryClient.fetchCatalog(serverUrl, pairingToken)
                                }
                            }.onSuccess {
                                catalog = it
                                libraryError = null
                            }.onFailure {
                                libraryError = it.message
                            }
                        }
                    },
                ) {
                    Text("Refresh PC library")
                }
            }
            libraryError?.let { Text("Library error: $it") }
            LibraryItems(
                catalog = catalog,
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
private fun PlayerControls(
    snapshot: PlaybackSnapshot,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpen) {
                Text("Open video")
            }
            Button(onClick = onPlay, enabled = snapshot.source != null) {
                Text("Play")
            }
            Button(onClick = onPause, enabled = snapshot.source != null) {
                Text("Pause")
            }
        }
        PlaybackSeekControls(snapshot = snapshot, onSeekTo = onSeekTo)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Volume ${snapshot.volumePercent}%")
            Button(
                onClick = { onSetVolume((snapshot.volumePercent - 10).coerceAtLeast(0)) },
                enabled = snapshot.source != null && snapshot.volumePercent > 0,
            ) {
                Text("-")
            }
            Button(
                onClick = { onSetVolume((snapshot.volumePercent + 10).coerceAtMost(100)) },
                enabled = snapshot.source != null && snapshot.volumePercent < 100,
            ) {
                Text("+")
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

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${currentPositionMs.formatPlaybackTime()} / ${durationMs?.formatPlaybackTime() ?: "--:--"}")
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
private fun LibraryItems(
    catalog: LibraryCatalog?,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
    var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Search library") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(
                    onClick = { sort = LibraryCatalogSort.TITLE },
                    enabled = sort != LibraryCatalogSort.TITLE,
                ) {
                    Text("Title")
                }
            }
            item {
                Button(
                    onClick = { sort = LibraryCatalogSort.PATH },
                    enabled = sort != LibraryCatalogSort.PATH,
                ) {
                    Text("Path")
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
                    Text(if (subtitleFilter == LibrarySubtitleFilter.ANY) "With subtitles" else "All")
                }
            }
        }
        Text("Showing ${filteredItems.size} / ${totalItems.size} episodes")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filteredItems, key = LibraryMediaItem::id) { item ->
                Button(
                    onClick = { onPlay(item) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${item.seriesTitle} - ${item.episodeTitle}")
                }
            }
        }
    }
}
