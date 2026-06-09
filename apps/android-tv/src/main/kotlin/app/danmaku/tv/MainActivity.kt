package app.danmaku.tv

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
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
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryItemMetadataStatus
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

private val TvAppBackground = Color(0xFF090B0E)
private val TvPanelColor = Color(0xFF15191D)
private val TvPanelRaisedColor = Color(0xFF20262B)
private val TvCardColor = Color(0xFF111820)
private val TvAccentBlue = Color(0xFF7DD3FC)
private val TvMutedText = Color(0xFFB7C0C9)

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

@Composable
private fun Modifier.tvFocusHalo(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TvAppBackground)
                .padding(40.dp),
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
            }
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
            LibraryItems(
                catalog = catalog,
                posterEndpoint = posterEndpoint,
                playbackProgresses = playbackProgresses,
                favoriteMediaIds = favoriteMediaIds,
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
private fun TvLibraryNavigationRail(
    catalog: LibraryCatalog?,
    filteredCount: Int,
    totalCount: Int,
    favoriteCount: Int,
    hasActiveFilters: Boolean,
) {
    Column(
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(TvCardColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            catalog?.rootName ?: "No PC connected",
            color = TvMutedText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TvRailPill(if (catalog == null) "PC offline" else "PC ready", active = catalog != null)
        TvRailPill("$filteredCount / $totalCount episodes")
        TvRailPill("$favoriteCount favorites", active = favoriteCount > 0)
        if (hasActiveFilters) {
            TvRailPill("Filters active", active = true)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TvRailNavigationItem("Home")
        TvRailNavigationItem("Library", selected = true)
        TvRailNavigationItem("Search", selected = hasActiveFilters)
        TvRailNavigationItem("Favorites", selected = favoriteCount > 0)
        TvRailNavigationItem("PC", selected = catalog != null)
    }
}

@Composable
private fun TvRailNavigationItem(
    label: String,
    selected: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF273747) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (selected) Color.White else TvMutedText)
    }
}

@Composable
private fun TvRailPill(
    label: String,
    active: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (active) Color(0xFF273747) else TvPanelRaisedColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (active) TvAccentBlue else TvMutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvPosterTile(
    item: LibraryMediaItem,
    title: String,
    posterEndpoint: LibraryPosterEndpoint?,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val posterUrl = posterEndpoint?.posterUrl(item)
    val posterImage = rememberPosterImage(posterUrl)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF26313A)),
        contentAlignment = Alignment.Center,
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
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (posterImage.state == PosterImageLoadState.LOADING) {
            TvPosterPill(
                label = "Loading",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
        }
        label?.let {
            TvPosterPill(
                label = it,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun TvPosterPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun TvPcConnectionPanel(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    pairingToken: String,
    onPairingTokenChange: (String) -> Unit,
    savedConnections: List<LanLibraryConnectionProfile>,
    selectedBaseUrl: String,
    libraryError: String?,
    refreshPcFocusRequester: FocusRequester,
    discoverPcFocusRequester: FocusRequester,
    onRefresh: () -> Unit,
    onDiscover: () -> Unit,
    onSave: () -> Unit,
    onSelectConnection: (LanLibraryConnectionProfile) -> Unit,
    onForgetConnection: (LanLibraryConnectionProfile) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TvPanelRaisedColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("PC Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Discover, pair, and save the Windows library server.", color = TvMutedText)
            }
            TvRailPill(if (serverUrl.isBlank()) "No server" else "Server set", active = serverUrl.isNotBlank())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRefresh,
                modifier = Modifier
                    .focusRequester(refreshPcFocusRequester)
                    .focusProperties {
                        right = discoverPcFocusRequester
                    }
                    .tvFocusHalo(RoundedCornerShape(18.dp)),
            ) {
                Text("Refresh")
            }
            Button(
                onClick = onDiscover,
                modifier = Modifier
                    .focusRequester(discoverPcFocusRequester)
                    .focusProperties {
                        left = refreshPcFocusRequester
                    }
                    .tvFocusHalo(RoundedCornerShape(18.dp)),
            ) {
                Text("Discover")
            }
            Button(
                onClick = onSave,
                modifier = Modifier.tvFocusHalo(RoundedCornerShape(18.dp)),
            ) {
                Text("Save")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvTextInput(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                placeholder = "PC server URL",
                modifier = Modifier.weight(1f),
            )
            TvTextInput(
                value = pairingToken,
                onValueChange = onPairingTokenChange,
                placeholder = "Pairing token",
                modifier = Modifier.weight(1f),
            )
        }
        if (savedConnections.isNotEmpty()) {
            Text("Saved PCs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(savedConnections, key = { it.id }) { connection ->
                    TvSavedConnectionCard(
                        connection = connection,
                        isSelected = connection.normalizedBaseUrl == selectedBaseUrl,
                        onSelect = { onSelectConnection(connection) },
                        onForget = { onForgetConnection(connection) },
                    )
                }
            }
        }
        libraryError?.let {
            Text("Library error: $it", color = Color(0xFFFCA5A5))
        }
    }
}

@Composable
private fun TvTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TvCardColor)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .focusable(),
        decorationBox = { innerTextField ->
            if (value.isBlank()) {
                Text(placeholder, color = TvMutedText)
            }
            innerTextField()
        },
    )
}

@Composable
private fun TvSavedConnectionCard(
    connection: LanLibraryConnectionProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onForget: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0xFF273747) else TvCardColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            connection.displayName,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(connection.normalizedBaseUrl, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSelect,
                modifier = Modifier
                    .tvFocusHalo(RoundedCornerShape(16.dp))
                    .testTag("saved-connection:${connection.id}"),
            ) {
                Text(if (isSelected) "Selected" else "Use")
            }
            Button(
                onClick = onForget,
                modifier = Modifier
                    .tvFocusHalo(RoundedCornerShape(16.dp))
                    .testTag("saved-connection-forget:${connection.id}"),
            ) {
                Text("Forget")
            }
        }
    }
}

@Composable
internal fun LibraryItems(
    catalog: LibraryCatalog?,
    posterEndpoint: LibraryPosterEndpoint? = null,
    playbackProgresses: List<PlaybackProgress> = emptyList(),
    favoriteMediaIds: Set<String> = emptySet(),
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit = { _, _ -> },
    onPlay: (LibraryMediaItem) -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
    var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
    var favoriteFilter by remember { mutableStateOf(LibraryFavoriteFilter.ANY) }
    var selectedSeriesId by remember(catalog) { mutableStateOf<String?>(null) }
    var selectedEpisodeId by remember(catalog) { mutableStateOf<String?>(null) }
    val totalItems = catalog?.items.orEmpty()
    val filteredItems = catalog
        ?.filteredItems(
            LibraryCatalogQuery(
                searchText = searchText,
                sort = sort,
                subtitleFilter = subtitleFilter,
                favoriteFilter = favoriteFilter,
                favoriteMediaIds = favoriteMediaIds,
            ),
        )
        .orEmpty()
    val series = catalog?.groupedSeries().orEmpty().take(10)
    val nextUpItems = catalog?.nextUpItems(playbackProgresses, limit = 6).orEmpty()
    val continueWatchingItems = catalog?.continueWatchingItems(playbackProgresses, limit = 6).orEmpty()
    val recentlyWatchedItems = catalog?.recentlyWatchedItems(playbackProgresses, limit = 6).orEmpty()
    val nextUpFocusRequester = remember { FocusRequester() }
    val watchStatusById = catalog?.watchStatusByMediaId(playbackProgresses).orEmpty()
    val seriesWatchSummaryById = catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty()
    val selectedSeries = series.firstOrNull { it.id == selectedSeriesId }
    val selectedDetailId = selectedEpisodeId
        ?.takeIf { id -> filteredItems.any { it.id == id } }
        ?: filteredItems.firstOrNull()?.id
    val selectedEpisodeDetail = selectedDetailId
        ?.let { id -> catalog?.episodeDetail(id, playbackProgresses) }

    LaunchedEffect(nextUpItems.firstOrNull()?.mediaItem?.id) {
        if (nextUpItems.isNotEmpty()) {
            nextUpFocusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(TvPanelColor)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TvLibraryNavigationRail(
            catalog = catalog,
            filteredCount = filteredItems.size,
            totalCount = totalItems.size,
            favoriteCount = favoriteMediaIds.size,
            hasActiveFilters = searchText.isNotBlank() ||
                sort != LibraryCatalogSort.TITLE ||
                subtitleFilter != LibrarySubtitleFilter.ANY ||
                favoriteFilter != LibraryFavoriteFilter.ANY,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        color = TvMutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TvRailPill("${filteredItems.size} / ${totalItems.size} episodes", active = totalItems.isNotEmpty())
            }
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(TvCardColor)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .focusable(),
                decorationBox = { innerTextField ->
                    if (searchText.isBlank()) {
                        Text("Search library", color = TvMutedText)
                    }
                    innerTextField()
                },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Button(
                        onClick = {
                            favoriteFilter = if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                                LibraryFavoriteFilter.FAVORITES_ONLY
                            } else {
                                LibraryFavoriteFilter.ANY
                            }
                        },
                        modifier = Modifier.testTag("library-favorites-filter"),
                    ) {
                        Text(if (favoriteFilter == LibraryFavoriteFilter.ANY) "Favorites" else "All episodes")
                    }
                }
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
                    posterEndpoint = posterEndpoint,
                    initialFocusRequester = nextUpFocusRequester,
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
                    posterEndpoint = posterEndpoint,
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
                    posterEndpoint = posterEndpoint,
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
                            modifier = Modifier
                                .tvFocusHalo(RoundedCornerShape(18.dp))
                                .testTag("series:all"),
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
                            modifier = Modifier
                                .tvFocusHalo(RoundedCornerShape(22.dp))
                                .testTag("series:${summary.title}"),
                        ) {
                            Column {
                                TvPosterTile(
                                    item = summary.latestIndexedItem,
                                    title = summary.title,
                                    posterEndpoint = posterEndpoint,
                                    label = seriesWatchSummaryById[summary.id].shortProgressLabel(),
                                    modifier = Modifier
                                        .width(180.dp)
                                        .aspectRatio(0.75f),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
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
                    posterEndpoint = posterEndpoint,
                    isFavorite = detail.mediaItem.id in favoriteMediaIds,
                    onSetFavorite = { onSetFavorite(detail.mediaItem, it) },
                    onPlay = onPlay,
                    onSelectEpisode = { selectedEpisodeId = it.id },
                )
            }
            when {
                catalog == null || totalItems.isEmpty() -> {
                    TvLibraryEmptyPanel(
                        title = "No PC library connected",
                        body = "Discover or pair with a Windows library server to browse episodes on TV.",
                    )
                }
                filteredItems.isEmpty() -> {
                    TvLibraryEmptyPanel(
                        title = "No matching episodes",
                        body = "Reset search, favorites, and subtitle filters to return to the full library.",
                        actionLabel = "Reset filters",
                        onAction = {
                            searchText = ""
                            sort = LibraryCatalogSort.TITLE
                            subtitleFilter = LibrarySubtitleFilter.ANY
                            favoriteFilter = LibraryFavoriteFilter.ANY
                            selectedSeriesId = null
                            selectedEpisodeId = null
                        },
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredItems, key = LibraryMediaItem::id) { item ->
                            TvEpisodeButton(
                                item = item,
                                posterEndpoint = posterEndpoint,
                                watchStatus = watchStatusById[item.id],
                                isFavorite = item.id in favoriteMediaIds,
                                onSetFavorite = { onSetFavorite(item, it) },
                                onShowDetails = { selectedEpisodeId = item.id },
                                onPlay = { onPlay(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLibraryEmptyPanel(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
            .padding(16.dp)
            .testTag("library-empty-state"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(body)
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.testTag("library-reset-filters"),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun TvEpisodeDetail(
    detail: LibraryEpisodeDetail,
    posterEndpoint: LibraryPosterEndpoint?,
    isFavorite: Boolean,
    onSetFavorite: (Boolean) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
    onSelectEpisode: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
            .padding(14.dp)
            .testTag("episode-detail:${detail.mediaItem.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvPosterTile(
                item = detail.mediaItem,
                title = detail.series.title,
                posterEndpoint = posterEndpoint,
                label = detail.watchStatus.statusLabel().substringBefore(" / "),
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(0.72f),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    detail.mediaItem.episodeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${detail.series.title} / ${detail.season.label} / ${detail.watchStatus.statusLabel()}")
                detail.mediaItem.animeMetadata?.let { metadata ->
                    Text(
                        "Matched anime: ${metadata.displayTitle}",
                        color = TvAccentBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                detail.mediaItem.metadataStatusLabel()?.let { label ->
                    Text(
                        label,
                        color = TvAccentBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(detail.mediaItem.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (detail.mediaItem.posterPath == null) {
                    "${detail.mediaItem.subtitles.size} subtitles"
                } else {
                    "${detail.mediaItem.subtitles.size} subtitles / poster"
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onPlay(detail.mediaItem) }) {
                Text("Play")
            }
            Button(
                onClick = { onSetFavorite(!isFavorite) },
                modifier = Modifier.testTag("episode-detail-favorite:${detail.mediaItem.id}"),
            ) {
                Text(if (isFavorite) "Unfavorite" else "Favorite")
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
    posterEndpoint: LibraryPosterEndpoint?,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
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
                var cardHasFocus by remember(item.mediaItem.id) { mutableStateOf(false) }
                val cardScale by animateFloatAsState(
                    targetValue = if (cardHasFocus) 1.025f else 1f,
                    label = "$itemTagPrefix-card-focus-scale",
                )
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .scale(cardScale)
                        .clip(RoundedCornerShape(20.dp))
                        .background(TvCardColor)
                        .border(
                            width = if (cardHasFocus) 2.dp else 1.dp,
                            color = if (cardHasFocus) TvAccentBlue else Color.Transparent,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TvPosterTile(
                        item = item.mediaItem,
                        title = item.mediaItem.seriesTitle,
                        posterEndpoint = posterEndpoint,
                        label = "Resume",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    )
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
                            modifier = Modifier
                                .onFocusChanged { cardHasFocus = it.isFocused }
                                .tvFocusHalo(RoundedCornerShape(16.dp))
                                .testTag("$itemTagPrefix-details:${item.mediaItem.id}"),
                        ) {
                            Text("Details")
                        }
                        Button(
                            onClick = { onPlay(item.mediaItem) },
                            modifier = Modifier
                                .onFocusChanged { cardHasFocus = it.isFocused }
                                .tvFocusHalo(RoundedCornerShape(16.dp))
                                .testTag("$itemTagPrefix:${item.mediaItem.id}"),
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
    posterEndpoint: LibraryPosterEndpoint?,
    initialFocusRequester: FocusRequester? = null,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlay: (LibraryMediaItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
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
            val firstItemId = items.firstOrNull()?.mediaItem?.id
            items(items, key = { it.mediaItem.id }) { item ->
                var cardHasFocus by remember(item.mediaItem.id) { mutableStateOf(false) }
                val cardScale by animateFloatAsState(
                    targetValue = if (cardHasFocus) 1.025f else 1f,
                    label = "next-up-card-focus-scale",
                )
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .scale(cardScale)
                        .clip(RoundedCornerShape(20.dp))
                        .background(TvCardColor)
                        .border(
                            width = if (cardHasFocus) 2.dp else 1.dp,
                            color = if (cardHasFocus) TvAccentBlue else Color.Transparent,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TvPosterTile(
                        item = item.mediaItem,
                        title = item.mediaItem.seriesTitle,
                        posterEndpoint = posterEndpoint,
                        label = item.nextUpActionLabel(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    )
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
                            modifier = Modifier
                                .onFocusChanged { cardHasFocus = it.isFocused }
                                .tvFocusHalo(RoundedCornerShape(16.dp))
                                .testTag("next-up-details:${item.mediaItem.id}"),
                        ) {
                            Text("Details")
                        }
                        Button(
                            onClick = { onPlay(item.mediaItem) },
                            modifier = Modifier
                                .onFocusChanged { cardHasFocus = it.isFocused }
                                .then(
                                    if (item.mediaItem.id == firstItemId && initialFocusRequester != null) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                )
                                .tvFocusHalo(RoundedCornerShape(16.dp))
                                .testTag("next-up:${item.mediaItem.id}"),
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

private fun String.initials(): String {
    val words = trim()
        .split(' ', '.', '_', '-', '[', ']')
        .filter(String::isNotBlank)
    return words
        .take(2)
        .joinToString(separator = "") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
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
            .clip(RoundedCornerShape(22.dp))
            .background(TvPanelRaisedColor)
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
                                .tvFocusHalo(RoundedCornerShape(16.dp))
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
    posterEndpoint: LibraryPosterEndpoint?,
    watchStatus: LibraryWatchStatus?,
    isFavorite: Boolean,
    onSetFavorite: (Boolean) -> Unit,
    onShowDetails: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onShowDetails,
            modifier = Modifier
                .weight(1f)
                .tvFocusHalo(RoundedCornerShape(18.dp))
                .testTag("episode:${item.id}"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvPosterTile(
                    item = item,
                    title = item.seriesTitle,
                    posterEndpoint = posterEndpoint,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
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
                        item.tvMetadataLabel(watchStatus, isFavorite),
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
            onClick = onPlay,
            modifier = Modifier
                .tvFocusHalo(RoundedCornerShape(18.dp))
                .testTag("episode-play:${item.id}"),
        ) {
            Text("Play")
        }
        Button(
            onClick = { onSetFavorite(!isFavorite) },
            modifier = Modifier
                .tvFocusHalo(RoundedCornerShape(18.dp))
                .testTag("episode-favorite:${item.id}"),
        ) {
            Text(if (isFavorite) "Unfavorite" else "Favorite")
        }
    }
}

private fun LibrarySeries.episodeLabel(): String =
    if (episodeCount == 1) "1 episode" else "$episodeCount episodes"

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

private fun LibraryMediaItem.tvMetadataLabel(
    watchStatus: LibraryWatchStatus?,
    isFavorite: Boolean,
): String =
    buildList {
        add(watchStatus.statusLabel())
        animeMetadata?.let { add("Matched: ${it.displayTitle}") }
        if (posterPath != null) {
            add("Poster")
        }
        metadataStatusLabel()?.let(::add)
        if (isFavorite) {
            add("Favorite")
        }
    }.joinToString(" / ")

private fun LibraryMediaItem.metadataStatusLabel(): String? =
    when (metadataStatus) {
        LibraryItemMetadataStatus.LOADING -> "Poster/metadata loading"
        LibraryItemMetadataStatus.FAILED -> "Metadata refresh failed"
        LibraryItemMetadataStatus.READY -> null
        LibraryItemMetadataStatus.NOT_AVAILABLE -> null
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
