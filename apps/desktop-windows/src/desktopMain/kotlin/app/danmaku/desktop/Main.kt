package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.danmaku.domain.DanmakuEvent
import app.danmaku.domain.DanmakuStyle
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.MeasuredDanmakuEvent
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.ScrollingDanmakuLaneScheduler
import app.danmaku.domain.ScrollingDanmakuLayoutConfig
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.jvm.JvmLanLibraryClient
import app.danmaku.server.LocalLibraryDiscoveryAnnouncer
import app.danmaku.server.LocalLibraryServer
import app.danmaku.server.PublishedLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.JFileChooser

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Danmaku",
    ) {
        DesktopShell()
    }
}

@Composable
private fun DesktopShell() {
    val selectionStore = remember { LocalLibrarySelectionStore.default() }
    val catalogStore = remember { DesktopLibraryCatalogStore.default() }
    val rootRegistry = remember(catalogStore) { DesktopLibraryRootRegistry(catalogStore) }
    val rootScanner = remember(catalogStore, rootRegistry) {
        DesktopLibraryRootScanner(catalogStore, rootRegistry)
    }
    val localPlaybackPreparer = remember(catalogStore) {
        DesktopLocalPlaybackPreparer(catalogStore)
    }
    val mpvCommandLog = remember { mutableStateListOf<DesktopMpvCommand>() }
    val playbackController = remember {
        DesktopMpvPlaybackController { command ->
            mpvCommandLog += command
        }
    }
    val playbackSession = remember(playbackController) {
        DesktopPlaybackSession(playbackController)
    }
    val server = remember(catalogStore) {
        LocalLibraryServer(progressStore = catalogStore).apply {
            start()
        }
    }
    val discoveryAnnouncer = remember(server) {
        LocalLibraryDiscoveryAnnouncer(server.localPort).apply {
            start()
        }
    }
    val scope = rememberCoroutineScope()
    val legacySelectedLibraryRoot = remember { selectionStore.load() }
    var registeredRoots by remember { mutableStateOf(rootRegistry.loadRoots()) }
    var playbackSnapshot by remember { mutableStateOf(PlaybackSnapshot()) }
    var indexedLibrary by remember {
        mutableStateOf(
            if (registeredRoots.isNotEmpty()) {
                catalogStore.loadRegisteredLibrary()
            } else {
                legacySelectedLibraryRoot?.let(catalogStore::load)
            },
        )
    }
    var selectedLocalPlaybackPreparation by remember {
        mutableStateOf<DesktopLocalPlaybackPreparation?>(null)
    }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var isIndexing by remember { mutableStateOf(false) }
    var isPreparingLocalPlayback by remember { mutableStateOf(false) }
    var lastScanStats by remember { mutableStateOf<LocalMediaLibraryScanStats?>(null) }
    val networkUrls = remember(server) { server.networkUrls() }

    fun applyPublishedLibrary(library: IndexedLocalLibrary) {
        server.publish(library.toPublishedLibrary())
        indexedLibrary = library
        registeredRoots = rootRegistry.loadRoots()
        selectedLocalPlaybackPreparation = null
        libraryError = null
    }

    fun registerAndScanUserRoot(root: Path) {
        scope.launch {
            isIndexing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        rootRegistry.addUserSelectedRoot(root).let(rootScanner::scan).also {
                            selectionStore.save(root)
                        }
                    }
                }.onSuccess { result ->
                    lastScanStats = result.indexedLibrary?.scanStats
                    applyPublishedLibrary(result.publishedLibrary)
                }.onFailure { error ->
                    libraryError = error.message
                    registeredRoots = rootRegistry.loadRoots()
                }
            } finally {
                isIndexing = false
            }
        }
    }

    fun importAndScanAniRssRoot(root: Path) {
        scope.launch {
            isIndexing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        rootScanner.importAniRssOutputRoot(root)
                    }
                }.onSuccess { result ->
                    lastScanStats = result.indexedLibrary?.scanStats
                    applyPublishedLibrary(result.publishedLibrary)
                }.onFailure { error ->
                    libraryError = error.message
                    registeredRoots = rootRegistry.loadRoots()
                }
            } finally {
                isIndexing = false
            }
        }
    }

    fun rescanRegisteredRoots() {
        scope.launch {
            isIndexing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        rootScanner.scanAll()
                    }
                }.onSuccess { batch ->
                    lastScanStats = LocalMediaLibraryScanStats(
                        reusedItemCount = batch.results.sumOf {
                            it.indexedLibrary?.scanStats?.reusedItemCount ?: 0
                        },
                        refreshedItemCount = batch.results.sumOf {
                            it.indexedLibrary?.scanStats?.refreshedItemCount ?: 0
                        },
                    )
                    applyPublishedLibrary(batch.publishedLibrary)
                }.onFailure { error ->
                    libraryError = error.message
                    registeredRoots = rootRegistry.loadRoots()
                }
            } finally {
                isIndexing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        indexedLibrary?.toPublishedLibrary()?.let(server::publish)
        if (registeredRoots.isNotEmpty()) {
            rescanRegisteredRoots()
        } else {
            legacySelectedLibraryRoot?.let(::registerAndScanUserRoot)
        }
    }

    DisposableEffect(server, discoveryAnnouncer) {
        onDispose {
            discoveryAnnouncer.close()
            server.close()
            catalogStore.close()
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Danmaku",
                    style = MaterialTheme.typography.h4,
                )
                Text("Windows playback foundation")
                Text("Player state: ${playbackSnapshot.status}")
                playbackSnapshot.source?.let { Text("Player source: $it") }
                Text("Synthetic overlay demo: collision-aware shared lane scheduler")
                SyntheticOverlayDemo()
                Text("Windows anime library server")
                Button(
                    onClick = {
                        selectLibraryDirectory(
                            title = "Choose anime library folder",
                        )?.let(::registerAndScanUserRoot)
                    },
                    enabled = !isIndexing,
                ) {
                    Text(if (isIndexing) "Indexing..." else "Add anime library folder")
                }
                Button(
                    onClick = {
                        selectLibraryDirectory(
                            title = "Choose ani-rss completed-media folder",
                        )?.let(::importAndScanAniRssRoot)
                    },
                    enabled = !isIndexing,
                ) {
                    Text("Add ani-rss output folder")
                }
                Button(
                    onClick = ::rescanRegisteredRoots,
                    enabled = registeredRoots.isNotEmpty() && !isIndexing,
                ) {
                    Text("Rescan registered folders")
                }
                Text("Registered folders: ${registeredRoots.size}")
                LazyColumn(modifier = Modifier.height(100.dp)) {
                    items(registeredRoots, key = { it.id }) { root ->
                        Text(
                            "${root.displayName} | ${root.provenance} | ${root.state} | " +
                                root.normalizedPath,
                        )
                    }
                }
                networkUrls.forEach { url ->
                    Text("Library URL: $url")
                }
                Text("LAN discovery: UDP port ${app.danmaku.domain.LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT}")
                Text("Pairing code: ${server.pairingToken}")
                libraryError?.let { Text("Library error: $it") }
                Text("Indexed episodes: ${indexedLibrary?.catalog?.items?.size ?: 0}")
                lastScanStats?.let { stats ->
                    Text(
                        "Last scan: ${stats.reusedItemCount} unchanged, " +
                            "${stats.refreshedItemCount} refreshed",
                    )
                }
                LazyColumn(modifier = Modifier.height(140.dp)) {
                    items(indexedLibrary?.catalog?.items.orEmpty(), key = { it.id }) { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val library = indexedLibrary
                                    if (library != null) {
                                        scope.launch {
                                            isPreparingLocalPlayback = true
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    localPlaybackPreparer.prepare(library, item)
                                                }
                                            }.onSuccess {
                                                selectedLocalPlaybackPreparation = it
                                                libraryError = null
                                            }.onFailure {
                                                libraryError = it.message
                                            }
                                            isPreparingLocalPlayback = false
                                        }
                                    }
                                },
                                enabled = !isPreparingLocalPlayback,
                            ) {
                                Text(
                                    if (isPreparingLocalPlayback) {
                                        "Preparing..."
                                    } else {
                                        "Prepare local playback"
                                    },
                                )
                            }
                            Text("${item.seriesTitle} - ${item.episodeTitle}")
                        }
                    }
                }
                selectedLocalPlaybackPreparation?.let { preparation ->
                    Text(
                        "Prepared local playback: " +
                            "${preparation.item.seriesTitle} - ${preparation.item.episodeTitle}",
                    )
                    Text("Source: ${preparation.source.path}")
                    Text("Resume: ${preparation.resumePositionMs?.let { "$it ms" } ?: "start from beginning"}")
                    Button(
                        onClick = {
                            playbackSnapshot = playbackSession.load(preparation.toPlaybackRequest())
                        },
                    ) {
                        Text("Load into Windows controller")
                    }
                }
                RemoteLibraryBrowser(
                    defaultServerUrl = server.baseUrl(),
                    defaultPairingToken = server.pairingToken,
                    playbackSession = playbackSession,
                    onPlaybackSnapshotChanged = {
                        playbackSnapshot = it
                    },
                )
                if (mpvCommandLog.isNotEmpty()) {
                    Text("Planned mpv commands")
                    LazyColumn(modifier = Modifier.height(100.dp)) {
                        items(mpvCommandLog) { command ->
                            Text(command.args.joinToString(separator = " "))
                        }
                    }
                }
                Text("Next: connect the desktop controller command executor to native libmpv.")
            }
        }
    }
}

@Composable
private fun RemoteLibraryBrowser(
    defaultServerUrl: String,
    defaultPairingToken: String,
    playbackSession: DesktopPlaybackSession,
    onPlaybackSnapshotChanged: (PlaybackSnapshot) -> Unit,
) {
    val libraryClient = remember { JvmLanLibraryClient() }
    val playbackPreparer = remember(libraryClient) { LanPlaybackPreparer(libraryClient) }
    val scope = rememberCoroutineScope()
    var serverUrl by remember(defaultServerUrl) { mutableStateOf(defaultServerUrl) }
    var pairingToken by remember(defaultPairingToken) { mutableStateOf(defaultPairingToken) }
    var catalog by remember { mutableStateOf<LibraryCatalog?>(null) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var selectedPlaybackPreparation by remember {
        mutableStateOf<LanPlaybackPreparation?>(null)
    }
    var isLoading by remember { mutableStateOf(false) }
    var isPreparingPlayback by remember { mutableStateOf(false) }

    fun refreshCatalog() {
        val requestedServerUrl = serverUrl
        val requestedPairingToken = pairingToken
        scope.launch {
            isLoading = true
            selectedPlaybackPreparation = null
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryClient.fetchCatalog(requestedServerUrl, requestedPairingToken)
                }
            }.onSuccess {
                catalog = it
                libraryError = null
            }.onFailure {
                libraryError = it.message
            }
            isLoading = false
        }
    }

    Text("Windows paired library client")
    Text("Defaults to this app's embedded same-PC server. Enter another PC URL to browse remotely.")
    OutlinedTextField(
        value = serverUrl,
        onValueChange = {
            serverUrl = it
            selectedPlaybackPreparation = null
        },
        label = { Text("Library server URL") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = pairingToken,
        onValueChange = {
            pairingToken = it
            selectedPlaybackPreparation = null
        },
        label = { Text("Pairing code") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Button(
        onClick = ::refreshCatalog,
        enabled = !isLoading,
    ) {
        Text(if (isLoading) "Loading..." else "Load paired server catalog")
    }
    libraryError?.let { Text("Paired library error: $it") }
    Text("Paired episodes: ${catalog?.items?.size ?: 0}")
    LazyColumn(modifier = Modifier.height(140.dp)) {
        items(catalog?.items.orEmpty(), key = { it.id }) { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val requestedServerUrl = serverUrl
                        val requestedPairingToken = pairingToken
                        scope.launch {
                            isPreparingPlayback = true
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    playbackPreparer.prepare(
                                        baseUrl = requestedServerUrl,
                                        pairingToken = requestedPairingToken,
                                        item = item,
                                    )
                                }
                            }.onSuccess {
                                selectedPlaybackPreparation = it
                                libraryError = null
                            }.onFailure {
                                libraryError = it.message
                            }
                            isPreparingPlayback = false
                        }
                    },
                    enabled = !isPreparingPlayback,
                ) {
                    Text(if (isPreparingPlayback) "Preparing..." else "Prepare playback")
                }
                Text("${item.seriesTitle} - ${item.episodeTitle}")
            }
        }
    }
    selectedPlaybackPreparation?.let { preparation ->
        Text("Prepared Windows playback: ${preparation.item.seriesTitle} - ${preparation.item.episodeTitle}")
        Text("Source: ${preparation.source.url.redactToken()}")
        Text("Resume: ${preparation.resumePositionMs?.let { "$it ms" } ?: "start from beginning"}")
        Button(
            onClick = {
                onPlaybackSnapshotChanged(
                    playbackSession.load(preparation.toDesktopPlaybackRequest()),
                )
            },
        ) {
            Text("Load into Windows controller")
        }
    }
}

private fun IndexedLocalLibrary.toPublishedLibrary(): PublishedLibrary =
    PublishedLibrary(
        catalog = catalog,
        filesById = filesById,
    )

private fun selectLibraryDirectory(title: String) =
    JFileChooser().run {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
        takeIf { showOpenDialog(null) == JFileChooser.APPROVE_OPTION }
            ?.selectedFile
            ?.toPath()
    }

private fun String.redactToken(): String =
    replace(Regex("([?&]token=)[^&]+"), "\$1...")

@Composable
private fun SyntheticOverlayDemo() {
    var playbackPositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val startedAtMs = withFrameMillis { it }
        while (true) {
            withFrameMillis { frameTimeMs ->
                playbackPositionMs = (frameTimeMs - startedAtMs) % DEMO_DURATION_MS
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color(0xFF111827), RoundedCornerShape(8.dp))
            .clipToBounds(),
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val schedule = remember(viewportWidthPx, density) {
            val measuredEvents = SYNTHETIC_EVENTS.map { event ->
                MeasuredDanmakuEvent(
                    event = event,
                    widthPx = with(density) {
                        (event.text.length * 9 + 28).dp.toPx()
                    },
                )
            }
            ScrollingDanmakuLaneScheduler.schedule(
                events = measuredEvents,
                config = ScrollingDanmakuLayoutConfig(
                    viewportWidthPx = viewportWidthPx,
                    laneCount = DEMO_LANE_COUNT,
                    travelDurationMs = 6_000,
                    horizontalGapPx = with(density) { 24.dp.toPx() },
                ),
            )
        }

        schedule.visibleAt(playbackPositionMs).forEach { placement ->
            Text(
                text = placement.event.text,
                color = Color.White,
                modifier = Modifier
                    .offset(
                        x = with(density) {
                            placement.leftEdgeAt(playbackPositionMs).toDp()
                        },
                        y = (placement.laneIndex * 44 + 16).dp,
                    )
                    .background(Color(0xB33B82F6), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

private const val DEMO_DURATION_MS = 12_000L
private const val DEMO_LANE_COUNT = 4

private val SYNTHETIC_EVENTS = listOf(
    syntheticEvent(id = "one", timestampMs = 0, text = "Shared Kotlin scheduler"),
    syntheticEvent(id = "two", timestampMs = 650, text = "Windows overlay composition"),
    syntheticEvent(id = "three", timestampMs = 1_100, text = "Collision-aware lanes"),
    syntheticEvent(id = "four", timestampMs = 1_750, text = "Seek-safe deterministic layout"),
    syntheticEvent(id = "five", timestampMs = 2_900, text = "Compose Desktop demo"),
    syntheticEvent(id = "six", timestampMs = 4_200, text = "libmpv clock comes next"),
    syntheticEvent(id = "seven", timestampMs = 6_400, text = "Looping synthetic track"),
    syntheticEvent(id = "eight", timestampMs = 7_200, text = "Danmaku"),
)

private fun syntheticEvent(
    id: String,
    timestampMs: Long,
    text: String,
): DanmakuEvent =
    DanmakuEvent(
        id = id,
        timestampMs = timestampMs,
        text = text,
        style = DanmakuStyle(),
    )
