package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.jvm.JvmLanLibraryClient
import app.danmaku.server.LocalLibraryDiscoveryAnnouncer
import app.danmaku.server.PublishedLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

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
    val aniRssCredentialStore = remember(catalogStore) {
        AniRssCredentialStore(catalogStore)
    }
    val localPlaybackPreparer = remember(catalogStore) {
        DesktopLocalPlaybackPreparer(catalogStore)
    }
    val mpvCommandLog = remember { mutableStateListOf<DesktopMpvCommand>() }
    var mpvVideoWindowId by remember { mutableStateOf<Long?>(null) }
    val mpvRuntime = remember(mpvVideoWindowId) {
        DesktopMpvCommandExecutorRuntimeFactory().create(
            nativeOptions = mpvVideoWindowId
                ?.let(DesktopMpvWindowsOptions::forWindowId)
                .orEmpty(),
        ) { command ->
            mpvCommandLog += command
        }
    }
    val playbackController = remember(mpvRuntime) {
        DesktopMpvPlaybackController(mpvRuntime.executor)
    }
    val syntheticOverlayTrack = remember { DesktopSyntheticDanmakuAssTrack.createDefault() }
    var overlayStatus by remember { mutableStateOf("Synthetic danmaku overlay: waiting for media load") }
    val playbackSession = remember(playbackController, syntheticOverlayTrack, mpvRuntime) {
        DesktopPlaybackSession(playbackController) {
            runCatching {
                syntheticOverlayTrack.attachTo(mpvRuntime.executor)
            }.onSuccess {
                overlayStatus = "Synthetic danmaku overlay: attached to mpv video"
            }.onFailure { error ->
                overlayStatus = "Synthetic danmaku overlay error: ${error.message}"
            }
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
    val serverRuntime = remember(catalogStore, rootScanner, scope) {
        DesktopLibraryServerRuntime.start(
            catalogStore = catalogStore,
            rootScanner = rootScanner,
            aniRssWebhookToken = aniRssCredentialStore.loadOrCreateWebhookToken(),
            onLibraryPublished = { library ->
                scope.launch {
                    indexedLibrary = library
                    registeredRoots = rootRegistry.loadRoots()
                    selectedLocalPlaybackPreparation = null
                    libraryError = null
                }
            },
        )
    }
    val server = serverRuntime.server
    val discoveryAnnouncer = remember(server) {
        LocalLibraryDiscoveryAnnouncer(server.localPort).apply {
            start()
        }
    }
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

    DisposableEffect(mpvRuntime) {
        onDispose {
            mpvRuntime.close()
        }
    }

    DisposableEffect(serverRuntime, discoveryAnnouncer) {
        onDispose {
            discoveryAnnouncer.close()
            serverRuntime.close()
            catalogStore.close()
        }
    }

    var selectedTab by remember { mutableStateOf(DesktopShellTab.HOME) }

    MaterialTheme(colors = DanmakuDarkColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DanmakuColors.Background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ShellHeader(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    playerStatus = playbackSnapshot.status.name,
                    episodeCount = indexedLibrary?.catalog?.items?.size ?: 0,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    when (selectedTab) {
                        DesktopShellTab.HOME -> HomeTab(
                            playbackSnapshot = playbackSnapshot,
                            registeredRoots = registeredRoots,
                            episodeCount = indexedLibrary?.catalog?.items?.size ?: 0,
                            networkUrls = networkUrls,
                            pairingToken = server.pairingToken,
                            overlayStatus = overlayStatus,
                            libraryError = libraryError,
                            lastScanStats = lastScanStats,
                        )
                        DesktopShellTab.PLAYBACK -> PlaybackTab(
                            playbackSnapshot = playbackSnapshot,
                            mpvRuntimeStatus = mpvRuntime.statusMessage,
                            videoHostStatus = if (mpvVideoWindowId == null) {
                                "waiting for native window"
                            } else {
                                "attached"
                            },
                            overlayStatus = overlayStatus,
                            mpvCommandLog = mpvCommandLog,
                            onWindowIdChanged = { mpvVideoWindowId = it },
                            onOpenMediaFile = {
                                selectMediaFile(
                                    title = "Choose media file for Windows playback",
                                )?.let { mediaFile ->
                                    playbackSnapshot = playbackSession.load(
                                        mediaFile.toDirectLocalPlaybackRequest(),
                                    )
                                }
                            },
                            onPlay = {
                                playbackController.dispatch(PlaybackCommand.Play)
                                playbackSnapshot = playbackController.snapshot()
                            },
                            onPause = {
                                playbackController.dispatch(PlaybackCommand.Pause)
                                playbackSnapshot = playbackController.snapshot()
                            },
                            onSeekBackward = {
                                playbackController.dispatch(
                                    PlaybackCommand.SeekTo(
                                        maxOf(0, playbackSnapshot.position.positionMs - 10_000),
                                    ),
                                )
                                playbackSnapshot = playbackController.snapshot()
                            },
                            onSeekForward = {
                                playbackController.dispatch(
                                    PlaybackCommand.SeekTo(
                                        playbackSnapshot.position.positionMs + 10_000,
                                    ),
                                )
                                playbackSnapshot = playbackController.snapshot()
                            },
                            canOpenMedia = mpvVideoWindowId != null,
                        )
                        DesktopShellTab.MEDIA_LIBRARY -> MediaLibraryTab(
                            registeredRoots = registeredRoots,
                            indexedLibrary = indexedLibrary,
                            isIndexing = isIndexing,
                            isPreparingLocalPlayback = isPreparingLocalPlayback,
                            selectedLocalPlaybackPreparation = selectedLocalPlaybackPreparation,
                            libraryError = libraryError,
                            lastScanStats = lastScanStats,
                            onAddLibraryFolder = {
                                selectLibraryDirectory(
                                    title = "Choose anime library folder",
                                )?.let(::registerAndScanUserRoot)
                            },
                            onRescanRegisteredRoots = ::rescanRegisteredRoots,
                            onPrepareLocalPlayback = { item ->
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
                            onLoadPreparedPlayback = { preparation ->
                                playbackSnapshot = playbackSession.load(preparation.toPlaybackRequest())
                                selectedTab = DesktopShellTab.PLAYBACK
                            },
                            remoteBrowser = {
                                RemoteLibraryBrowser(
                                    defaultServerUrl = server.baseUrl(),
                                    defaultPairingToken = server.pairingToken,
                                    playbackSession = playbackSession,
                                    onPlaybackSnapshotChanged = {
                                        playbackSnapshot = it
                                        selectedTab = DesktopShellTab.PLAYBACK
                                    },
                                )
                            },
                        )
                        DesktopShellTab.DOWNLOADS -> DownloadsTab(
                            isIndexing = isIndexing,
                            webhookUrls = serverRuntime.aniRssWebhookUrls(),
                            webhookToken = serverRuntime.aniRssWebhookToken,
                            registeredRoots = registeredRoots,
                            onAddAniRssOutputFolder = {
                                selectLibraryDirectory(
                                    title = "Choose ani-rss completed-media folder",
                                )?.let(::importAndScanAniRssRoot)
                            },
                        )
                        DesktopShellTab.PROFILE -> ProfileTab(
                            mpvRuntimeStatus = mpvRuntime.statusMessage,
                            videoHostStatus = if (mpvVideoWindowId == null) {
                                "waiting for native window"
                            } else {
                                "attached"
                            },
                            serverBaseUrl = server.baseUrl(),
                            networkUrls = networkUrls,
                            pairingToken = server.pairingToken,
                        )
                    }
                }
            }
        }
    }
}

private enum class DesktopShellTab(
    val title: String,
) {
    HOME("Home"),
    PLAYBACK("Playback"),
    MEDIA_LIBRARY("Media Library"),
    DOWNLOADS("Downloads"),
    PROFILE("Profile"),
}

private object DanmakuColors {
    val Background = Color(0xFF151515)
    val Surface = Color(0xFF202020)
    val SurfaceRaised = Color(0xFF2A2A2A)
    val Accent = Color(0xFFFF2D63)
    val AccentSoft = Color(0xFF6D2140)
    val TextMuted = Color(0xFFB8B8B8)
    val Good = Color(0xFF5CE0A3)
    val Warning = Color(0xFFFFC857)
}

private val DanmakuDarkColors = darkColors(
    primary = DanmakuColors.Accent,
    primaryVariant = DanmakuColors.AccentSoft,
    secondary = Color(0xFF6C5CE7),
    background = DanmakuColors.Background,
    surface = DanmakuColors.Surface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
private fun ShellHeader(
    selectedTab: DesktopShellTab,
    onTabSelected: (DesktopShellTab) -> Unit,
    playerStatus: String,
    episodeCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.Surface)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Danmaku",
                    style = MaterialTheme.typography.h4,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Local anime library, LAN streaming, and danmaku playback",
                    color = DanmakuColors.TextMuted,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            StatusPill("Player $playerStatus")
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill("$episodeCount episodes")
        }
        TabRow(
            selectedTabIndex = DesktopShellTab.entries.indexOf(selectedTab),
            backgroundColor = DanmakuColors.Surface,
            contentColor = DanmakuColors.Accent,
        ) {
            DesktopShellTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeTab(
    playbackSnapshot: PlaybackSnapshot,
    registeredRoots: List<DesktopLibraryRoot>,
    episodeCount: Int,
    networkUrls: List<String>,
    pairingToken: String,
    overlayStatus: String,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
) {
    TabScaffold {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SummaryCard(
                title = "Library",
                value = "$episodeCount",
                caption = "indexed episodes",
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "Folders",
                value = "${registeredRoots.size}",
                caption = "registered roots",
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "Pairing",
                value = pairingToken,
                caption = "LAN code",
                modifier = Modifier.weight(1f),
            )
        }
        SectionCard("Current Playback") {
            MetadataRow("State", playbackSnapshot.status.name)
            MetadataRow("Source", playbackSnapshot.source?.toString()?.redactToken() ?: "No media loaded")
            MetadataRow("Overlay", overlayStatus)
            playbackSnapshot.errorMessage?.let { MetadataRow("Error", it, valueColor = DanmakuColors.Warning) }
        }
        SectionCard("Server") {
            networkUrls.ifEmpty { listOf("No LAN URL detected") }.forEach { url ->
                MetadataRow("Library URL", url)
            }
            MetadataRow("Registered folders", registeredRoots.joinToString { it.displayName }.ifBlank { "None yet" })
            lastScanStats?.let {
                MetadataRow("Last scan", "${it.reusedItemCount} unchanged, ${it.refreshedItemCount} refreshed")
            }
            libraryError?.let { MetadataRow("Library error", it, valueColor = DanmakuColors.Warning) }
        }
    }
}

@Composable
private fun PlaybackTab(
    playbackSnapshot: PlaybackSnapshot,
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    overlayStatus: String,
    mpvCommandLog: List<DesktopMpvCommand>,
    onWindowIdChanged: (Long?) -> Unit,
    onOpenMediaFile: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    canOpenMedia: Boolean,
) {
    TabScaffold {
        SectionCard("Video Playback") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onOpenMediaFile, enabled = canOpenMedia) {
                    Text("Open media file")
                }
                Button(onClick = onPlay, enabled = playbackSnapshot.source != null) {
                    Text("Play")
                }
                Button(onClick = onPause, enabled = playbackSnapshot.source != null) {
                    Text("Pause")
                }
                Button(onClick = onSeekBackward, enabled = playbackSnapshot.source != null) {
                    Text("-10s")
                }
                Button(onClick = onSeekForward, enabled = playbackSnapshot.source != null) {
                    Text("+10s")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            DesktopMpvVideoHost(
                onWindowIdChanged = onWindowIdChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.height(12.dp))
            MetadataRow("State", playbackSnapshot.status.name)
            MetadataRow("Source", playbackSnapshot.source?.toString()?.redactToken() ?: "No media loaded")
            MetadataRow("Position", "${playbackSnapshot.position.positionMs} ms")
            MetadataRow("Overlay", overlayStatus)
            playbackSnapshot.errorMessage?.let { MetadataRow("Error", it, valueColor = DanmakuColors.Warning) }
        }
        SectionCard("Runtime") {
            MetadataRow("mpv executor", mpvRuntimeStatus)
            MetadataRow("Video host", videoHostStatus)
            if (mpvCommandLog.isEmpty()) {
                Text("No mpv commands yet.", color = DanmakuColors.TextMuted)
            } else {
                LazyColumn(modifier = Modifier.height(120.dp)) {
                    items(mpvCommandLog.takeLast(20)) { command ->
                        Text(
                            text = command.args.joinToString(separator = " "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = DanmakuColors.TextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaLibraryTab(
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    isIndexing: Boolean,
    isPreparingLocalPlayback: Boolean,
    selectedLocalPlaybackPreparation: DesktopLocalPlaybackPreparation?,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    onAddLibraryFolder: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onLoadPreparedPlayback: (DesktopLocalPlaybackPreparation) -> Unit,
    remoteBrowser: @Composable () -> Unit,
) {
    TabScaffold {
        SectionCard("Local Media Library") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAddLibraryFolder, enabled = !isIndexing) {
                    Text(if (isIndexing) "Indexing..." else "Add library folder")
                }
                Button(
                    onClick = onRescanRegisteredRoots,
                    enabled = registeredRoots.isNotEmpty() && !isIndexing,
                ) {
                    Text("Rescan folders")
                }
            }
            libraryError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                MetadataRow("Library error", it, valueColor = DanmakuColors.Warning)
            }
            lastScanStats?.let {
                MetadataRow("Last scan", "${it.reusedItemCount} unchanged, ${it.refreshedItemCount} refreshed")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(
                title = "Registered Folders",
                modifier = Modifier.weight(1f),
            ) {
                if (registeredRoots.isEmpty()) {
                    EmptyState("No library folders added yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(180.dp)) {
                        items(registeredRoots, key = { it.id }) { root ->
                            MediaRootRow(root)
                        }
                    }
                }
            }
            SectionCard(
                title = "Episodes",
                modifier = Modifier.weight(1.3f),
            ) {
                val items = indexedLibrary?.catalog?.items.orEmpty()
                if (items.isEmpty()) {
                    EmptyState("No indexed episodes yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(items, key = { it.id }) { item ->
                            EpisodeRow(
                                item = item,
                                isPreparing = isPreparingLocalPlayback,
                                onPrepareLocalPlayback = onPrepareLocalPlayback,
                            )
                        }
                    }
                }
            }
        }
        selectedLocalPlaybackPreparation?.let { preparation ->
            SectionCard("Prepared Playback") {
                MetadataRow(
                    "Episode",
                    "${preparation.item.seriesTitle} - ${preparation.item.episodeTitle}",
                )
                MetadataRow("Source", preparation.source.path)
                MetadataRow("Resume", preparation.resumePositionMs?.let { "$it ms" } ?: "start from beginning")
                Button(onClick = { onLoadPreparedPlayback(preparation) }) {
                    Text("Load into player")
                }
            }
        }
        SectionCard("Paired Library Client") {
            remoteBrowser()
        }
    }
}

@Composable
private fun DownloadsTab(
    isIndexing: Boolean,
    webhookUrls: List<String>,
    webhookToken: String,
    registeredRoots: List<DesktopLibraryRoot>,
    onAddAniRssOutputFolder: () -> Unit,
) {
    TabScaffold {
        SectionCard("ani-rss Integration") {
            Text(
                "Use ani-rss for authorized acquisition, then point Danmaku at the completed-media folder.",
                color = DanmakuColors.TextMuted,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onAddAniRssOutputFolder, enabled = !isIndexing) {
                Text("Add ani-rss output folder")
            }
        }
        SectionCard("Webhook") {
            webhookUrls.forEach { url ->
                MetadataRow("DOWNLOAD_END URL", url)
            }
            MetadataRow("Header", "X-Danmaku-Webhook-Token")
            MetadataRow("Token", webhookToken)
        }
        SectionCard("Download Roots") {
            val aniRssRoots = registeredRoots.filter {
                it.provenance == DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER
            }
            if (aniRssRoots.isEmpty()) {
                EmptyState("No ani-rss output folders registered.")
            } else {
                aniRssRoots.forEach { root -> MediaRootRow(root) }
            }
        }
    }
}

@Composable
private fun ProfileTab(
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    serverBaseUrl: String,
    networkUrls: List<String>,
    pairingToken: String,
) {
    TabScaffold {
        SectionCard("Local Server") {
            MetadataRow("Base URL", serverBaseUrl)
            MetadataRow("Pairing code", pairingToken)
            networkUrls.forEach { MetadataRow("LAN URL", it) }
            MetadataRow(
                "Discovery",
                "UDP ${app.danmaku.domain.LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT}",
            )
        }
        SectionCard("Windows Runtime") {
            MetadataRow("mpv executor", mpvRuntimeStatus)
            MetadataRow("Video host", videoHostStatus)
            MetadataRow("Renderer", "mpv child window with generated ASS danmaku overlay")
        }
    }
}

@Composable
private fun TabScaffold(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = DanmakuColors.Surface,
        elevation = 8.dp,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            Divider(color = DanmakuColors.SurfaceRaised)
            content()
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 112.dp),
        backgroundColor = DanmakuColors.Surface,
        elevation = 8.dp,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, color = DanmakuColors.TextMuted)
            Text(value, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
            Text(caption, color = DanmakuColors.TextMuted)
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = DanmakuColors.TextMuted,
    )
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = DanmakuColors.TextMuted,
            modifier = Modifier.width(140.dp),
            maxLines = 1,
        )
        Text(
            text = value,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DanmakuColors.TextMuted)
    }
}

@Composable
private fun MediaRootRow(root: DesktopLibraryRoot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(root.displayName, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(root.state.name, color = DanmakuColors.Good)
        }
        Text(root.provenance.name, color = DanmakuColors.TextMuted)
        Text(
            root.normalizedPath.toString(),
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EpisodeRow(
    item: LibraryMediaItem,
    isPreparing: Boolean,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = { onPrepareLocalPlayback(item) },
            enabled = !isPreparing,
        ) {
            Text(if (isPreparing) "Preparing..." else "Prepare")
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

internal fun IndexedLocalLibrary.toPublishedLibrary(): PublishedLibrary =
    PublishedLibrary(
        catalog = catalog,
        filesById = filesById,
        subtitleFilesById = subtitleFilesById,
    )

private fun selectLibraryDirectory(title: String) =
    JFileChooser().run {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
        takeIf { showOpenDialog(null) == JFileChooser.APPROVE_OPTION }
            ?.selectedFile
            ?.toPath()
    }

private fun selectMediaFile(title: String) =
    JFileChooser().run {
        fileSelectionMode = JFileChooser.FILES_ONLY
        dialogTitle = title
        fileFilter = FileNameExtensionFilter(
            "Video files",
            "mkv",
            "mp4",
            "m4v",
            "avi",
            "mov",
            "webm",
            "ts",
            "m2ts",
        )
        takeIf { showOpenDialog(null) == JFileChooser.APPROVE_OPTION }
            ?.selectedFile
            ?.toPath()
    }

private fun String.redactToken(): String =
    replace(Regex("([?&]token=)[^&]+"), "\$1...")
