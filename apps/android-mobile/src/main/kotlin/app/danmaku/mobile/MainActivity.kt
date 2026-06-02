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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.library.android.LanLibraryDiscoveryClient
import app.danmaku.library.android.LanLibraryClient
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
                        activeController.load(
                            PlaybackSource.RemoteStream(
                                libraryClient.streamUrl(
                                    target.baseUrl,
                                    item,
                                    target.pairingToken,
                                ),
                            ),
                        )
                        resumePosition?.let {
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
) {
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
}

@Composable
private fun LibraryItems(
    catalog: LibraryCatalog?,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
