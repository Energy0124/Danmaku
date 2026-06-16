package app.danmaku.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.LanLibraryClient
import app.danmaku.library.android.LanLibraryDiscoveryClient
import app.danmaku.library.android.LanLibraryDiscoveryException
import app.danmaku.player.android.Media3PlaybackController
import app.danmaku.player.android.Media3PlaybackServiceConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val TvAppBackground = Color(0xFF090B0E)
internal val TvPanelColor = Color(0xFF15191D)
internal val TvPanelRaisedColor = Color(0xFF20262B)
internal val TvCardColor = Color(0xFF111820)
internal val TvAccentBlue = Color(0xFF7DD3FC)
internal val TvMutedText = Color(0xFFB7C0C9)

internal data class LibraryPosterEndpoint(
    val baseUrl: String,
    val pairingToken: String,
) {
    fun posterUrl(item: LibraryMediaItem): String? {
        val path = item.posterPath ?: return null
        return "${baseUrl.trim().trimEnd('/')}$path?token=${pairingToken.encodedQueryValue()}"
    }
}

@Composable
internal fun Modifier.tvFocusHalo(
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.035f else 1f,
        label = "tv-focus-halo-scale",
    )
    return this
        .scale(scale)
        .border(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) TvAccentBlue else Color.Transparent,
            shape = shape,
        )
        .onFocusChanged { isFocused = it.isFocused }
}

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
    val refreshPcFocusRequester = remember { FocusRequester() }
    val discoverPcFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<Media3PlaybackController?>(null) }
    var snapshot by remember { mutableStateOf(PlaybackSnapshot()) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8686") }
    var pairingToken by remember { mutableStateOf("") }
    var savedConnections by remember { mutableStateOf(connectionStore.loadProfiles()) }
    var favoriteMediaIds by remember { mutableStateOf(favoriteStore.loadFavoriteMediaIds()) }
    var catalog by remember { mutableStateOf<LibraryCatalog?>(null) }
    var playbackProgresses by remember { mutableStateOf<List<PlaybackProgress>>(emptyList()) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var selectedDestination by remember { mutableStateOf(TvDestination.Pc) }
    val posterEndpoint = catalog?.let {
        LibraryPosterEndpoint(serverUrl, pairingToken)
    }
    fun refreshLibrary() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryConnectionSession.fetchCatalogWithProgress(
                        baseUrl = serverUrl,
                        pairingToken = pairingToken,
                    )
                }
            }.onSuccess {
                catalog = it.catalog
                playbackProgresses = it.playbackProgresses
                connectionStore.saveCurrentConnection(
                    baseUrl = serverUrl,
                    pairingToken = pairingToken,
                    displayName = it.catalog.rootName,
                )
                savedConnections = connectionStore.loadProfiles()
                libraryError = null
                selectedDestination = TvDestination.Library
            }.onFailure {
                libraryError = it.message
            }
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

    LaunchedEffect(Unit) {
        discoverPcFocusRequester.requestFocus()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(TvAppBackground)
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TvAppNavigationRail(
                selectedDestination = selectedDestination,
                catalog = catalog,
                favoriteCount = favoriteMediaIds.size,
                onSelectDestination = { selectedDestination = it },
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    TvDestinationHeader(
                        selectedDestination = selectedDestination,
                        catalog = catalog,
                        snapshot = snapshot,
                    )
                }
                if (selectedDestination != TvDestination.Pc) {
                    item {
                        TvPlayerPanel(
                            controller = controller,
                            snapshot = snapshot,
                            playbackError = playbackError,
                            onSeekTo = { controller?.dispatch(PlaybackCommand.SeekTo(it)) },
                            onSelectAudio = {
                                controller?.dispatch(PlaybackCommand.SelectAudioTrack(it))
                            },
                            onSelectSubtitle = {
                                controller?.dispatch(PlaybackCommand.SelectSubtitleTrack(it))
                            },
                            onPlay = { controller?.dispatch(PlaybackCommand.Play) },
                            onPause = { controller?.dispatch(PlaybackCommand.Pause) },
                            onVolumeDown = {
                                controller?.dispatch(
                                    PlaybackCommand.SetVolume((snapshot.volumePercent - 10).coerceAtLeast(0)),
                                )
                            },
                            onVolumeUp = {
                                controller?.dispatch(
                                    PlaybackCommand.SetVolume((snapshot.volumePercent + 10).coerceAtMost(100)),
                                )
                            },
                        )
                    }
                }

                when (selectedDestination) {
                    TvDestination.Pc -> {
                        item {
                            TvPcConnectionPanel(
                                serverUrl = serverUrl,
                                onServerUrlChange = { serverUrl = it },
                                pairingToken = pairingToken,
                                onPairingTokenChange = { pairingToken = it },
                                savedConnections = savedConnections,
                                selectedBaseUrl = serverUrl.trim().trimEnd('/'),
                                libraryError = libraryError,
                                refreshPcFocusRequester = refreshPcFocusRequester,
                                discoverPcFocusRequester = discoverPcFocusRequester,
                                onRefresh = ::refreshLibrary,
                                onDiscover = {
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                discoveryClient.discover().firstOrNull()
                                                    ?: throw LanLibraryDiscoveryException("No Windows library server discovered")
                                            }
                                        }.onSuccess {
                                            serverUrl = it.baseUrl
                                            libraryError = null
                                        }.onFailure {
                                            libraryError = it.message
                                        }
                                    }
                                },
                                onSave = {
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
                                onSelectConnection = {
                                    serverUrl = it.baseUrl
                                    pairingToken = it.pairingToken
                                },
                                onForgetConnection = {
                                    connectionStore.forgetProfile(it.id)
                                    savedConnections = connectionStore.loadProfiles()
                                },
                            )
                        }
                    }
                    TvDestination.Home -> {
                        item {
                            TvHomePanel(
                                catalog = catalog,
                                playbackProgresses = playbackProgresses,
                                posterEndpoint = posterEndpoint,
                                onShowLibrary = { selectedDestination = TvDestination.Library },
                                onShowPc = { selectedDestination = TvDestination.Pc },
                                onPlay = { item ->
                                    val activeController = controller ?: return@TvHomePanel
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
                    TvDestination.Library,
                    TvDestination.Search,
                    TvDestination.Favorites,
                    -> {
                        item {
                            LibraryItems(
                                catalog = catalog,
                                posterEndpoint = posterEndpoint,
                                playbackProgresses = playbackProgresses,
                                favoriteMediaIds = favoriteMediaIds,
                                initialFavoriteFilter = if (selectedDestination == TvDestination.Favorites) {
                                    LibraryFavoriteFilter.FAVORITES_ONLY
                                } else {
                                    LibraryFavoriteFilter.ANY
                                },
                                focusSearchOnStart = selectedDestination == TvDestination.Search,
                                onSetFavorite = { item, isFavorite ->
                                    runCatching {
                                        favoriteStore.setFavoriteMediaId(item.id, isFavorite)
                                    }.onSuccess {
                                        favoriteMediaIds = it
                                        libraryError = null
                                    }.onFailure {
                                        libraryError = it.message
                                    }
                                },
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
            }
        }
    }
}
