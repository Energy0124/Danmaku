package app.danmaku.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.danmaku.library.LanLibraryConnectionSession
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.android.AndroidLanLibraryConnectionStore
import app.danmaku.library.android.AndroidLibraryFavoriteStore
import app.danmaku.library.android.LanLibraryClient
import app.danmaku.library.android.LanLibraryDiscoveryClient
import app.danmaku.player.android.Media3PlaybackServiceConnection
import kotlinx.coroutines.delay

@Composable
internal fun TvPlayerScreen() {
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
        val savedConnections = connectionStore.loadProfiles()
        TvPlayerState(
            initialSavedConnections = savedConnections,
            initialFavoriteMediaIds = favoriteStore.loadFavoriteMediaIds(),
            initialServerUrl = savedConnections.firstOrNull()?.baseUrl
                ?: BuildConfig.DEFAULT_SERVER_URL.trim(),
            initialPairingToken = savedConnections.firstOrNull()?.pairingToken
                ?: BuildConfig.DEFAULT_PAIRING_TOKEN.trim(),
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
                val controller = Media3TvPlaybackController(it)
                state.controller = controller
                state.snapshot = controller.snapshot()
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

    LaunchedEffect(actionHandler) {
        if (state.catalog == null && state.serverUrl.isNotBlank() && state.pairingToken.isNotBlank()) {
            actionHandler.refreshLibrary()
        }
    }

    TvPlayerContent(
        state = state,
        actionHandler = actionHandler,
        refreshPcFocusRequester = refreshPcFocusRequester,
        discoverPcFocusRequester = discoverPcFocusRequester,
    )
}
