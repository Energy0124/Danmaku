package app.danmaku.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.PlaybackCommand
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.LanLibraryClient
import app.danmaku.library.android.LanLibraryDiscoveryClient
import app.danmaku.player.android.Media3PlaybackServiceConnection
import kotlinx.coroutines.delay

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
    val libraryDiscovery = remember(discoveryClient) {
        TvLibraryDiscovery { discoveryClient.discover() }
    }
    val refreshPcFocusRequester = remember { FocusRequester() }
    val discoverPcFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val state = remember(connectionStore, favoriteStore) {
        TvPlayerState(
            initialSavedConnections = connectionStore.loadProfiles(),
            initialFavoriteMediaIds = favoriteStore.loadFavoriteMediaIds(),
        )
    }
    val actionHandler = remember(
        state,
        scope,
        libraryConnectionSession,
        progressSync,
        playbackPreparer,
        connectionStore,
        favoriteStore,
        libraryDiscovery,
    ) {
        TvPlayerActionHandler(
            state = state,
            scope = scope,
            libraryConnectionSession = libraryConnectionSession,
            progressSync = progressSync,
            playbackPreparer = playbackPreparer,
            connectionStore = connectionStore,
            favoriteStore = favoriteStore,
            libraryDiscovery = libraryDiscovery,
        )
    }

    DisposableEffect(playbackConnection) {
        playbackConnection.connect(
            executor = ContextCompat.getMainExecutor(context),
            onConnected = {
                state.controller = it
                state.snapshot = it.snapshot()
                state.playbackError = null
            },
            onFailure = {
                state.playbackError = it.message
            },
        )
        onDispose {
            state.controller = null
            playbackConnection.close()
        }
    }

    LaunchedEffect(state.controller) {
        val activeController = state.controller ?: return@LaunchedEffect
        while (true) {
            state.snapshot = activeController.snapshot()
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
                selectedDestination = state.selectedDestination,
                catalog = state.catalog,
                favoriteCount = state.favoriteMediaIds.size,
                onSelectDestination = { state.selectedDestination = it },
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                        TvDestinationHeader(
                        selectedDestination = state.selectedDestination,
                        catalog = state.catalog,
                        snapshot = state.snapshot,
                    )
                }
                if (state.selectedDestination != TvDestination.Pc) {
                    item {
                        TvPlayerPanel(
                            controller = state.controller,
                            snapshot = state.snapshot,
                            playbackError = state.playbackError,
                            onSeekTo = { state.controller?.dispatch(PlaybackCommand.SeekTo(it)) },
                            onSelectAudio = {
                                state.controller?.dispatch(PlaybackCommand.SelectAudioTrack(it))
                            },
                            onSelectSubtitle = {
                                state.controller?.dispatch(PlaybackCommand.SelectSubtitleTrack(it))
                            },
                            onPlay = { state.controller?.dispatch(PlaybackCommand.Play) },
                            onPause = { state.controller?.dispatch(PlaybackCommand.Pause) },
                            onVolumeDown = {
                                state.controller?.dispatch(
                                    PlaybackCommand.SetVolume((state.snapshot.volumePercent - 10).coerceAtLeast(0)),
                                )
                            },
                            onVolumeUp = {
                                state.controller?.dispatch(
                                    PlaybackCommand.SetVolume((state.snapshot.volumePercent + 10).coerceAtMost(100)),
                                )
                            },
                        )
                    }
                }

                when (state.selectedDestination) {
                    TvDestination.Pc -> {
                        item {
                            TvPcConnectionPanel(
                                serverUrl = state.serverUrl,
                                onServerUrlChange = { state.serverUrl = it },
                                pairingToken = state.pairingToken,
                                onPairingTokenChange = { state.pairingToken = it },
                                savedConnections = state.savedConnections,
                                selectedBaseUrl = state.serverUrl.trim().trimEnd('/'),
                                libraryError = state.libraryError,
                                refreshPcFocusRequester = refreshPcFocusRequester,
                                discoverPcFocusRequester = discoverPcFocusRequester,
                                onRefresh = actionHandler::refreshLibrary,
                                onDiscover = actionHandler::discoverPc,
                                onSave = actionHandler::saveConnection,
                                onSelectConnection = actionHandler::selectConnection,
                                onForgetConnection = actionHandler::forgetConnection,
                            )
                        }
                    }
                    TvDestination.Home -> {
                        item {
                            TvHomePanel(
                                catalog = state.catalog,
                                playbackProgresses = state.playbackProgresses,
                                posterEndpoint = state.posterEndpoint,
                                onShowLibrary = { state.selectedDestination = TvDestination.Library },
                                onShowPc = { state.selectedDestination = TvDestination.Pc },
                                onPlay = actionHandler::playItem,
                            )
                        }
                    }
                    TvDestination.Library,
                    TvDestination.Search,
                    TvDestination.Favorites,
                    -> {
                        item {
                            LibraryItems(
                                catalog = state.catalog,
                                posterEndpoint = state.posterEndpoint,
                                playbackProgresses = state.playbackProgresses,
                                favoriteMediaIds = state.favoriteMediaIds,
                                initialFavoriteFilter = if (state.selectedDestination == TvDestination.Favorites) {
                                    LibraryFavoriteFilter.FAVORITES_ONLY
                                } else {
                                    LibraryFavoriteFilter.ANY
                                },
                                focusSearchOnStart = state.selectedDestination == TvDestination.Search,
                                onSetFavorite = actionHandler::setFavorite,
                                onPlay = actionHandler::playItem,
                            )
                        }
                    }
                }
            }
        }
    }
}
