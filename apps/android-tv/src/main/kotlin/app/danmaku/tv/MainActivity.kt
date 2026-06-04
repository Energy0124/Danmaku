package app.danmaku.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
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

@Composable
private fun LibraryItems(
    catalog: LibraryCatalog?,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(catalog?.items.orEmpty(), key = LibraryMediaItem::id) { item ->
            Button(
                onClick = { onPlay(item) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${item.seriesTitle} - ${item.episodeTitle}")
            }
        }
    }
}
