package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.filteredItems
import app.danmaku.domain.nextItem
import app.danmaku.domain.previousItem
import app.danmaku.domain.resumePositionMs
import app.danmaku.domain.toPlaybackProgress
import app.danmaku.library.LanPlaybackPreparation
import app.danmaku.library.LanPlaybackPreparer
import app.danmaku.library.LanPlaybackProgressSync
import app.danmaku.library.LanPlaybackTarget
import app.danmaku.library.jvm.JvmLanLibraryClient
import app.danmaku.server.LocalLibraryDiscoveryAnnouncer
import app.danmaku.server.LocalLibraryServerEvent
import app.danmaku.server.PublishedLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

fun main() = application {
    val windowState = rememberWindowState()
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Danmaku",
    ) {
        DesktopShell(windowState)
    }
}

private data class DesktopDiagnosticLogEntry(
    val occurredAtEpochMs: Long,
    val category: String,
    val message: String,
)

private data class DesktopPlaybackProgressItem(
    val mediaItem: LibraryMediaItem,
    val progress: PlaybackProgress,
)

private data class PreparedLocalPlaybackResult(
    val preparation: DesktopLocalPlaybackPreparation,
    val dandanplayResolution: DesktopDandanplayDanmakuResolution?,
    val dandanplayError: Throwable?,
)

@Composable
private fun DesktopShell(windowState: WindowState) {
    val selectionStore = remember { LocalLibrarySelectionStore.default() }
    val catalogStore = remember { DesktopLibraryCatalogStore.default() }
    val playbackPreferencesStore = remember(catalogStore) {
        DesktopPlaybackPreferencesStore(catalogStore)
    }
    var playbackPreferences by remember(playbackPreferencesStore) {
        mutableStateOf(playbackPreferencesStore.load())
    }
    val rootRegistry = remember(catalogStore) { DesktopLibraryRootRegistry(catalogStore) }
    val rootScanner = remember(catalogStore, rootRegistry) {
        DesktopLibraryRootScanner(catalogStore, rootRegistry)
    }
    val aniRssCredentialStore = remember(catalogStore) {
        AniRssCredentialStore(catalogStore)
    }
    val dandanplayCredentialStore = remember(catalogStore) {
        DandanplayCredentialStore(catalogStore)
    }
    var dandanplaySettings by remember(dandanplayCredentialStore) {
        mutableStateOf(dandanplayCredentialStore.loadSettings())
    }
    val localPlaybackPreparer = remember(catalogStore) {
        DesktopLocalPlaybackPreparer(catalogStore)
    }
    val dandanplayDanmakuResolver = remember(dandanplayCredentialStore) {
        DesktopDandanplayDanmakuResolver(
            loadConnection = dandanplayCredentialStore::loadConnection,
            cacheMaxAgeDays = { dandanplayCredentialStore.loadSettings().cacheMaxAgeDays },
            cacheStore = catalogStore,
        )
    }
    val lanProgressSync = remember {
        LanPlaybackProgressSync(
            libraryClient = JvmLanLibraryClient(),
            currentTimeMillis = System::currentTimeMillis,
        )
    }
    val scope = rememberCoroutineScope()
    val mpvCommandLog = remember { mutableStateListOf<DesktopMpvCommand>() }
    val diagnosticLog = remember { mutableStateListOf<DesktopDiagnosticLogEntry>() }
    val diagnosticFileLog = remember { DesktopDiagnosticFileLog.createDefault() }
    fun appendDiagnostic(category: String, message: String) {
        val entry = DesktopDiagnosticLogEntry(
            occurredAtEpochMs = System.currentTimeMillis(),
            category = category,
            message = message.redactToken(),
        )
        diagnosticLog += entry
        diagnosticFileLog.append(entry.occurredAtEpochMs, entry.category, entry.message)
        while (diagnosticLog.size > MAX_DIAGNOSTIC_LOG_ENTRIES) {
            diagnosticLog.removeAt(0)
        }
    }
    fun appendServerEvent(event: LocalLibraryServerEvent) {
        val entry = DesktopDiagnosticLogEntry(
            occurredAtEpochMs = event.occurredAtEpochMs,
            category = "server:${event.category}",
            message = "${event.method} ${event.path} -> ${event.status} ${event.detail}".redactToken(),
        )
        diagnosticLog += entry
        diagnosticFileLog.append(entry.occurredAtEpochMs, entry.category, entry.message)
        while (diagnosticLog.size > MAX_DIAGNOSTIC_LOG_ENTRIES) {
            diagnosticLog.removeAt(0)
        }
    }
    var mpvVideoWindowId by remember { mutableStateOf<Long?>(null) }
    val mpvNativeOptions = remember(mpvVideoWindowId, diagnosticFileLog.mpvLogPath) {
        buildMap {
            put("config", "no")
            put("terminal", "no")
            put("msg-level", "all=v")
            put("log-file", diagnosticFileLog.mpvLogPath.toAbsolutePath().normalize().toString())
            mpvVideoWindowId
                ?.let(DesktopMpvWindowsOptions::forWindowId)
                ?.let(::putAll)
        }
    }
    val mpvRuntime = remember(mpvVideoWindowId, mpvNativeOptions) {
        if (mpvVideoWindowId == null) {
            DesktopMpvCommandExecutorRuntime(
                executor = DesktopMpvCommandExecutor { command ->
                    mpvCommandLog += command
                    appendDiagnostic("mpv", "command ${command.args.joinToString(separator = " ")}")
                },
                mode = DesktopMpvCommandExecutorMode.COMMAND_LOG_ONLY,
                statusMessage = "Native mpv video host is not ready. Command-log-only mode is active.",
            )
        } else {
            DesktopMpvCommandExecutorRuntimeFactory().create(
                nativeOptions = mpvNativeOptions,
            ) { command ->
                mpvCommandLog += command
                appendDiagnostic("mpv", "command ${command.args.joinToString(separator = " ")}")
            }
        }
    }
    val playbackController = remember(mpvRuntime) {
        DesktopMpvPlaybackController(
            commandExecutor = mpvRuntime.executor,
            propertyReader = mpvRuntime.propertyReader,
            initialSnapshot = PlaybackSnapshot(
                playbackRate = playbackPreferences.playbackRate,
                volumePercent = playbackPreferences.volumePercent,
            ),
            initialVideoAspectMode = playbackPreferences.videoAspectMode,
        )
    }
    LaunchedEffect(mpvRuntime, mpvVideoWindowId) {
        appendDiagnostic(
            "mpv",
            if (mpvVideoWindowId == null) {
                "Created mpv runtime without video window"
            } else {
                "Created mpv runtime for native window $mpvVideoWindowId"
            },
        )
        appendDiagnostic("diagnostics", "App log: ${diagnosticFileLog.appLogPath}")
        appendDiagnostic("diagnostics", "mpv log: ${diagnosticFileLog.mpvLogPath}")
    }
    val syntheticOverlayTrack = remember { DesktopSyntheticDanmakuAssTrack.createDefault() }
    var overlayStatus by remember { mutableStateOf("Synthetic danmaku overlay: waiting for media load") }
    val playbackSession = remember(playbackController, syntheticOverlayTrack, mpvRuntime) {
        DesktopPlaybackSession(
            controller = playbackController,
            afterLoad = { request ->
                if (request.subtitles.any(DesktopPlaybackSubtitle::isDanmakuOverlay)) {
                    overlayStatus = "Fetched danmaku overlay: attached to mpv video"
                    appendDiagnostic("overlay", "Skipping synthetic overlay because fetched danmaku is attached")
                } else {
                    runCatching {
                        appendDiagnostic("overlay", "Attaching synthetic ASS danmaku track after loading ${request.label}")
                        syntheticOverlayTrack.attachTo(mpvRuntime.executor)
                    }.onSuccess {
                        overlayStatus = "Synthetic danmaku overlay: attached to mpv video"
                        appendDiagnostic("overlay", "Synthetic ASS danmaku track attached")
                    }.onFailure { error ->
                        overlayStatus = "Synthetic danmaku overlay error: ${error.message}"
                        appendDiagnostic("overlay", "Synthetic ASS danmaku attach failed: ${error.message}")
                    }
                }
            },
            attachSubtitle = { subtitle ->
                runCatching {
                    mpvRuntime.executor.execute(
                        DesktopMpvCommandPlanner.addSubtitle(subtitle.source, subtitle.label),
                    )
                }.onSuccess {
                    appendDiagnostic("subtitle", "Attached subtitle track ${subtitle.label}: ${subtitle.source.redactToken()}")
                }.onFailure { error ->
                    appendDiagnostic("subtitle", "Subtitle attach failed for ${subtitle.label}: ${error.message}")
                }
            },
        )
    }
    val legacySelectedLibraryRoot = remember { selectionStore.load() }
    var registeredRoots by remember { mutableStateOf(rootRegistry.loadRoots()) }
    var playbackSnapshot by remember(playbackController) { mutableStateOf(playbackController.snapshot()) }
    val isFullscreen = windowState.placement == WindowPlacement.Fullscreen
    var videoAspectMode by remember(playbackController) { mutableStateOf(playbackController.videoAspectMode) }
    var indexedLibrary by remember {
        mutableStateOf(
            if (registeredRoots.isNotEmpty()) {
                catalogStore.loadRegisteredLibrary()
            } else {
                legacySelectedLibraryRoot?.let(catalogStore::load)
            },
        )
    }
    var playbackProgresses by remember { mutableStateOf(catalogStore.loadPlaybackProgress()) }
    var selectedLocalPlaybackPreparation by remember {
        mutableStateOf<DesktopLocalPlaybackPreparation?>(null)
    }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var isIndexing by remember { mutableStateOf(false) }
    var isPreparingLocalPlayback by remember { mutableStateOf(false) }
    var lastScanStats by remember { mutableStateOf<LocalMediaLibraryScanStats?>(null) }
    var dandanplayCacheStatus by remember { mutableStateOf<DandanplayPlaybackUiStatus?>(null) }
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
                    appendDiagnostic("library", "Published updated library from server runtime: ${library.catalog.items.size} items")
                }
            },
            onServerEvent = { event ->
                scope.launch {
                    appendServerEvent(event)
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

    LaunchedEffect(playbackController) {
        playbackController.dispatch(PlaybackCommand.SetPlaybackRate(playbackPreferences.playbackRate))
        playbackController.dispatch(PlaybackCommand.SetVolume(playbackPreferences.volumePercent))
        playbackController.setVideoAspectMode(playbackPreferences.videoAspectMode)
        playbackSnapshot = playbackController.snapshot()
        appendDiagnostic(
            "settings",
            "Applied playback defaults: rate=${playbackPreferences.playbackRate}x, " +
                "volume=${playbackPreferences.volumePercent}%, aspect=${playbackPreferences.videoAspectMode.label}",
        )
    }

    fun applyPublishedLibrary(library: IndexedLocalLibrary) {
        appendDiagnostic("library", "Publishing library: ${library.catalog.items.size} items")
        server.publish(library.toPublishedLibrary())
        indexedLibrary = library
        playbackProgresses = catalogStore.loadPlaybackProgress()
        registeredRoots = rootRegistry.loadRoots()
        selectedLocalPlaybackPreparation = null
        libraryError = null
    }

    fun registerAndScanUserRoot(root: Path) {
        scope.launch {
            appendDiagnostic("library", "Scanning user library root: $root")
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
                    appendDiagnostic(
                        "library",
                        "Scan complete: ${result.publishedLibrary.catalog.items.size} published items",
                    )
                }.onFailure { error ->
                    libraryError = error.message
                    registeredRoots = rootRegistry.loadRoots()
                    appendDiagnostic("library", "Scan failed: ${error.message}")
                }
            } finally {
                isIndexing = false
            }
        }
    }

    fun importAndScanAniRssRoot(root: Path) {
        scope.launch {
            appendDiagnostic("downloads", "Importing ani-rss output root: $root")
            isIndexing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        rootScanner.importAniRssOutputRoot(root)
                    }
                }.onSuccess { result ->
                    lastScanStats = result.indexedLibrary?.scanStats
                    applyPublishedLibrary(result.publishedLibrary)
                    appendDiagnostic(
                        "downloads",
                        "ani-rss root scan complete: ${result.publishedLibrary.catalog.items.size} published items",
                    )
                }.onFailure { error ->
                    libraryError = error.message
                    registeredRoots = rootRegistry.loadRoots()
                    appendDiagnostic("downloads", "ani-rss root scan failed: ${error.message}")
                }
            } finally {
                isIndexing = false
            }
        }
    }

    fun rescanRegisteredRoots() {
        scope.launch {
            appendDiagnostic("library", "Rescanning ${registeredRoots.size} registered folders")
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
                    appendDiagnostic(
                        "library",
                        "Rescan complete: ${batch.publishedLibrary.catalog.items.size} published items",
                    )
                }.onFailure { error ->
                    libraryError = error.message
                    registeredRoots = rootRegistry.loadRoots()
                    appendDiagnostic("library", "Rescan failed: ${error.message}")
                }
            } finally {
                isIndexing = false
            }
        }
    }

    var selectedTab by remember { mutableStateOf(DesktopShellTab.HOME) }
    var pendingPlaybackRequest by remember { mutableStateOf<DesktopPlaybackRequest?>(null) }
    var activePlaybackLabel by remember { mutableStateOf<String?>(null) }
    var activeProgressMediaId by remember { mutableStateOf<String?>(null) }
    var activeProgressTarget by remember { mutableStateOf<LanPlaybackTarget?>(null) }
    var lastSavedPlaybackProgress by remember { mutableStateOf<PlaybackProgress?>(null) }
    var lastAutoNextMediaId by remember { mutableStateOf<String?>(null) }
    var autoNextLocalPlayback by remember {
        mutableStateOf(catalogStore.loadSetting(LOCAL_AUTO_NEXT_SETTING_KEY)?.value == "true")
    }

    fun shouldPersistPlaybackProgress(
        progress: PlaybackProgress,
        force: Boolean,
    ): Boolean {
        if (progress.positionMs <= 0) {
            return false
        }
        val lastSaved = lastSavedPlaybackProgress
        return force ||
            lastSaved == null ||
            lastSaved.mediaId != progress.mediaId ||
            progress.positionMs - lastSaved.positionMs >= WINDOWS_PROGRESS_SAVE_INTERVAL_MS ||
            lastSaved.positionMs - progress.positionMs >= WINDOWS_PROGRESS_SAVE_INTERVAL_MS ||
            progress.durationMs != lastSaved.durationMs
    }

    fun persistActivePlaybackProgress(
        snapshot: PlaybackSnapshot,
        force: Boolean = false,
    ) {
        val mediaId = activeProgressMediaId ?: return
        val progress = snapshot.toPlaybackProgress(mediaId, System.currentTimeMillis()) ?: return
        if (!shouldPersistPlaybackProgress(progress, force)) {
            return
        }
        lastSavedPlaybackProgress = progress
        val target = activeProgressTarget
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (target == null) {
                        catalogStore.saveProgress(progress)
                        catalogStore.loadPlaybackProgress()
                    } else {
                        lanProgressSync.saveProgress(target, snapshot)
                        null
                    }
                }
            }.onSuccess { updatedProgresses ->
                updatedProgresses?.let {
                    playbackProgresses = it
                }
                val destination = if (target == null) "local catalog" else "paired LAN server"
                appendDiagnostic(
                    "progress",
                    "Saved $destination progress for $mediaId at ${progress.positionMs}ms/${progress.durationMs ?: "unknown"}",
                )
            }.onFailure { error ->
                appendDiagnostic("progress", "Save progress failed for $mediaId: ${error.message}")
            }
        }
    }

    fun loadPlaybackRequest(request: DesktopPlaybackRequest): PlaybackSnapshot {
        activePlaybackLabel = request.label
        activeProgressMediaId = request.progressMediaId
        activeProgressTarget = request.progressTarget
        lastSavedPlaybackProgress = null
        lastAutoNextMediaId = null
        return playbackSession.load(request).also {
            playbackSnapshot = it
        }
    }

    fun queuePlaybackUntilHostReady(request: DesktopPlaybackRequest) {
        pendingPlaybackRequest = request
        appendDiagnostic(
            "playback",
            "Queued playback until native video host attaches: ${request.label}; source=${request.source.toString().redactToken()}",
        )
        selectedTab = DesktopShellTab.PLAYBACK
    }

    fun prepareLocalPlayback(
        item: LibraryMediaItem,
        loadAfterPrepare: Boolean = false,
        refreshDandanplay: Boolean = false,
    ) {
        val library = indexedLibrary ?: return
        scope.launch {
            appendDiagnostic("playback", "Preparing local library playback: ${item.id}")
            isPreparingLocalPlayback = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val preparation = localPlaybackPreparer.prepare(library, item)
                    if (!dandanplaySettings.isFetchEnabled) {
                        PreparedLocalPlaybackResult(
                            preparation = preparation,
                            dandanplayResolution = null,
                            dandanplayError = null,
                        )
                    } else {
                        val mediaPath = library.filesById[item.id]
                            ?: error("Indexed media file is missing for ${item.id}")
                        val dandanplayResult = runCatching {
                            dandanplayDanmakuResolver.resolve(
                                mediaId = item.id,
                                mediaPath = mediaPath,
                                forceRefresh = refreshDandanplay,
                            )
                        }
                        val dandanplaySubtitle = dandanplayResult
                            .getOrNull()
                            ?.subtitle
                        PreparedLocalPlaybackResult(
                            preparation = if (dandanplaySubtitle == null) {
                                preparation
                            } else {
                                preparation.copy(subtitles = preparation.subtitles + dandanplaySubtitle)
                            },
                            dandanplayResolution = dandanplayResult.getOrNull(),
                            dandanplayError = dandanplayResult.exceptionOrNull(),
                        )
                    }
                }
            }.onSuccess { result ->
                selectedLocalPlaybackPreparation = result.preparation
                libraryError = null
                appendDiagnostic(
                    "playback",
                    "Prepared local playback: ${item.id}; source=${result.preparation.source.path}; resume=${result.preparation.resumePositionMs}",
                )
                when {
                    !dandanplaySettings.isFetchEnabled ->
                        appendDiagnostic("danmaku", "dandanplay fetching is not configured; using local/synthetic overlay")
                    result.dandanplayError != null ->
                        appendDiagnostic("danmaku", "dandanplay fetch failed for ${item.id}: ${result.dandanplayError.message}")
                    result.dandanplayResolution?.subtitle != null ->
                        appendDiagnostic(
                            "danmaku",
                            "Attached dandanplay match ${result.dandanplayResolution.match?.displayTitle ?: "unknown"} " +
                                "with ${result.dandanplayResolution.eventCount} comments from ${result.dandanplayResolution.source.name.lowercase()}",
                        )
                    result.dandanplayResolution?.match != null ->
                        appendDiagnostic(
                            "danmaku",
                            "dandanplay matched ${result.dandanplayResolution.match.displayTitle} but returned no comments",
                        )
                    else ->
                        appendDiagnostic("danmaku", "dandanplay found no match for ${item.id}")
                }
                result.dandanplayResolution?.let { resolution ->
                    dandanplayCacheStatus = dandanplayStatusFromResolution(item.id, resolution)
                }
                if (loadAfterPrepare) {
                    appendDiagnostic("playback", "Auto-loading prepared local playback: ${item.id}")
                    queuePlaybackUntilHostReady(result.preparation.toPlaybackRequest())
                }
            }.onFailure {
                libraryError = it.message
                appendDiagnostic("playback", "Prepare local playback failed: ${it.message}")
            }
            isPreparingLocalPlayback = false
        }
    }

    fun refreshPreparedDandanplay(preparation: DesktopLocalPlaybackPreparation) {
        if (!dandanplaySettings.isFetchEnabled) {
            dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = preparation.item.id,
                summary = "dandanplay fetching is not configured",
            )
            appendDiagnostic("danmaku", "Cannot refresh dandanplay cache; provider fetching is not configured")
            return
        }
        appendDiagnostic("danmaku", "Refreshing dandanplay cache for ${preparation.item.id}")
        prepareLocalPlayback(preparation.item, refreshDandanplay = true)
    }

    fun clearPreparedDandanplayCache(preparation: DesktopLocalPlaybackPreparation) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayDanmakuResolver.clearCache(preparation.item.id)
                }
            }.onSuccess {
                selectedLocalPlaybackPreparation = preparation.copy(
                    subtitles = preparation.subtitles.filterNot(DesktopPlaybackSubtitle::isDanmakuOverlay),
                )
                dandanplayCacheStatus = dandanplayStatusMessage(
                    mediaId = preparation.item.id,
                    summary = "dandanplay cache cleared",
                )
                appendDiagnostic("danmaku", "Cleared dandanplay cache for ${preparation.item.id}")
            }.onFailure {
                appendDiagnostic("danmaku", "Failed to clear dandanplay cache for ${preparation.item.id}: ${it.message}")
            }
        }
    }

    fun setAutoNextLocalPlayback(enabled: Boolean) {
        autoNextLocalPlayback = enabled
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    catalogStore.saveSetting(
                        DesktopAppSetting(
                            key = LOCAL_AUTO_NEXT_SETTING_KEY,
                            value = enabled.toString(),
                            updatedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                }
            }.onSuccess {
                appendDiagnostic("settings", "Local auto-next ${if (enabled) "enabled" else "disabled"}")
            }.onFailure {
                appendDiagnostic("settings", "Failed to save local auto-next setting: ${it.message}")
            }
        }
    }

    fun savePlaybackPreference(
        label: String,
        save: DesktopPlaybackPreferencesStore.() -> Unit,
    ) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    playbackPreferencesStore.save()
                    playbackPreferencesStore.load()
                }
            }.onSuccess { updatedPreferences ->
                playbackPreferences = updatedPreferences
                appendDiagnostic("settings", "Saved $label playback preference")
            }.onFailure {
                appendDiagnostic("settings", "Failed to save $label playback preference: ${it.message}")
            }
        }
    }

    fun saveDandanplaySettings(
        baseUrl: String,
        appId: String?,
        appSecret: String?,
        authenticationMode: DandanplayAuthenticationMode,
        cacheMaxAgeDays: Int,
    ) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayCredentialStore.saveSettings(
                        baseUrl = baseUrl,
                        appId = appId,
                        appSecret = appSecret,
                        authenticationMode = authenticationMode,
                        cacheMaxAgeDays = cacheMaxAgeDays,
                    )
                }
            }.onSuccess { updatedSettings ->
                dandanplaySettings = updatedSettings
                appendDiagnostic("settings", "Saved dandanplay provider settings for ${updatedSettings.baseUrl}")
            }.onFailure {
                appendDiagnostic("settings", "Failed to save dandanplay provider settings: ${it.message}")
            }
        }
    }

    fun clearDandanplaySettings() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayCredentialStore.deleteSettings()
                    dandanplayCredentialStore.loadSettings()
                }
            }.onSuccess { updatedSettings ->
                dandanplaySettings = updatedSettings
                appendDiagnostic("settings", "Cleared dandanplay provider settings")
            }.onFailure {
                appendDiagnostic("settings", "Failed to clear dandanplay provider settings: ${it.message}")
            }
        }
    }

    fun cleanupExpiredDandanplayCaches() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayDanmakuResolver.cleanupExpiredCaches()
                }
            }.onSuccess {
                appendDiagnostic("danmaku", "Cleaned up expired dandanplay cache entries")
            }.onFailure {
                appendDiagnostic("danmaku", "Failed to clean expired dandanplay cache entries: ${it.message}")
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

    LaunchedEffect(selectedTab, mpvVideoWindowId, pendingPlaybackRequest, playbackSession) {
        val request = pendingPlaybackRequest ?: return@LaunchedEffect
        if (selectedTab != DesktopShellTab.PLAYBACK || mpvVideoWindowId == null) {
            return@LaunchedEffect
        }
        appendDiagnostic(
            "playback",
            "Native video host ready; waiting ${PLAYBACK_HOST_SETTLE_DELAY_MS}ms before queued load: ${request.label}",
        )
        delay(PLAYBACK_HOST_SETTLE_DELAY_MS)
        if (pendingPlaybackRequest != request || selectedTab != DesktopShellTab.PLAYBACK || mpvVideoWindowId == null) {
            appendDiagnostic("playback", "Queued playback changed before load; skipping stale request: ${request.label}")
            return@LaunchedEffect
        }
        appendDiagnostic("playback", "Loading queued playback after host settle: ${request.label}")
        loadPlaybackRequest(request)
        pendingPlaybackRequest = null
    }

    LaunchedEffect(playbackController) {
        while (true) {
            delay(PLAYBACK_SNAPSHOT_POLL_INTERVAL_MS)
            val nextSnapshot = playbackController.snapshot()
            if (nextSnapshot != playbackSnapshot) {
                playbackSnapshot = nextSnapshot
            }
            persistActivePlaybackProgress(nextSnapshot)
            val mediaId = activeProgressMediaId
            if (
                autoNextLocalPlayback &&
                nextSnapshot.status == PlaybackStatus.ENDED &&
                mediaId != null &&
                activeProgressTarget == null &&
                lastAutoNextMediaId != mediaId
            ) {
                val nextItem = indexedLibrary?.catalog?.nextItem(mediaId)
                lastAutoNextMediaId = mediaId
                if (nextItem == null) {
                    appendDiagnostic("playback", "Auto-next reached end of local catalog at $mediaId")
                } else {
                    appendDiagnostic("playback", "Auto-next preparing ${nextItem.id} after $mediaId")
                    prepareLocalPlayback(nextItem, loadAfterPrepare = true)
                }
            }
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

    DisposableEffect(diagnosticFileLog) {
        onDispose {
            diagnosticFileLog.close()
        }
    }

    MaterialTheme(colors = DanmakuDarkColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DanmakuColors.Background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isFullscreen && selectedTab != DesktopShellTab.PLAYBACK) {
                    ShellHeader(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        playerStatus = playbackSnapshot.status.name,
                        episodeCount = indexedLibrary?.catalog?.items?.size ?: 0,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            if (isFullscreen || selectedTab == DesktopShellTab.PLAYBACK) {
                                0.dp
                            } else {
                                24.dp
                            },
                        ),
                ) {
                    if (selectedTab == DesktopShellTab.PLAYBACK) {
                        PlaybackTab(
                            playbackLabel = activePlaybackLabel,
                            playbackSnapshot = playbackSnapshot,
                            mpvRuntimeStatus = mpvRuntime.statusMessage,
                            videoHostStatus = if (mpvVideoWindowId == null) {
                                "waiting for native window"
                            } else {
                                "attached"
                            },
                            overlayStatus = overlayStatus,
                            onWindowIdChanged = { mpvVideoWindowId = it },
                            onOpenMediaFile = {
                                appendDiagnostic("playback", "Opening direct media file picker")
                                selectMediaFile(
                                    title = "Choose media file for Windows playback",
                                )?.let { mediaFile ->
                                    appendDiagnostic("playback", "Loading direct media file: $mediaFile")
                                    loadPlaybackRequest(mediaFile.toDirectLocalPlaybackRequest())
                                }
                            },
                            onPlay = {
                                appendDiagnostic("playback", "Dispatch Play")
                                playbackController.dispatch(PlaybackCommand.Play)
                                playbackSnapshot = playbackController.snapshot()
                            },
                            onPause = {
                                appendDiagnostic("playback", "Dispatch Pause")
                                playbackController.dispatch(PlaybackCommand.Pause)
                                playbackSnapshot = playbackController.snapshot()
                                persistActivePlaybackProgress(playbackSnapshot, force = true)
                            },
                            onSeekBackward = {
                                appendDiagnostic("playback", "Dispatch Seek -10s")
                                playbackController.dispatch(
                                    PlaybackCommand.SeekTo(
                                        maxOf(0, playbackSnapshot.position.positionMs - 10_000),
                                    ),
                                )
                                playbackSnapshot = playbackController.snapshot()
                                persistActivePlaybackProgress(playbackSnapshot, force = true)
                            },
                            onSeekBackwardLarge = {
                                appendDiagnostic("playback", "Dispatch Seek -30s")
                                playbackController.dispatch(
                                    PlaybackCommand.SeekTo(
                                        maxOf(0, playbackSnapshot.position.positionMs - 30_000),
                                    ),
                                )
                                playbackSnapshot = playbackController.snapshot()
                                persistActivePlaybackProgress(playbackSnapshot, force = true)
                            },
                            onSeekForward = {
                                appendDiagnostic("playback", "Dispatch Seek +10s")
                                playbackController.dispatch(
                                    PlaybackCommand.SeekTo(
                                        playbackSnapshot.position.positionMs + 10_000,
                                    ),
                                )
                                playbackSnapshot = playbackController.snapshot()
                                persistActivePlaybackProgress(playbackSnapshot, force = true)
                            },
                            onSeekForwardLarge = {
                                appendDiagnostic("playback", "Dispatch Seek +30s")
                                playbackController.dispatch(
                                    PlaybackCommand.SeekTo(
                                        playbackSnapshot.position.positionMs + 30_000,
                                    ),
                                )
                                playbackSnapshot = playbackController.snapshot()
                                persistActivePlaybackProgress(playbackSnapshot, force = true)
                            },
                            onSeekTo = { positionMs ->
                                appendDiagnostic("playback", "Dispatch Seek ${positionMs}ms")
                                playbackController.dispatch(PlaybackCommand.SeekTo(positionMs))
                                playbackSnapshot = playbackController.snapshot()
                                persistActivePlaybackProgress(playbackSnapshot, force = true)
                            },
                            onSetPlaybackRate = { rate ->
                                appendDiagnostic("playback", "Dispatch playback rate ${rate}x")
                                playbackController.dispatch(PlaybackCommand.SetPlaybackRate(rate))
                                playbackSnapshot = playbackController.snapshot()
                                savePlaybackPreference("rate") {
                                    savePlaybackRate(rate)
                                }
                            },
                            onSetVolume = { volumePercent ->
                                appendDiagnostic("playback", "Dispatch volume $volumePercent%")
                                playbackController.dispatch(PlaybackCommand.SetVolume(volumePercent))
                                playbackSnapshot = playbackController.snapshot()
                                savePlaybackPreference("volume") {
                                    saveVolumePercent(volumePercent)
                                }
                            },
                            onSelectAudioTrack = { trackId ->
                                appendDiagnostic("playback", "Dispatch audio track $trackId")
                                playbackController.dispatch(PlaybackCommand.SelectAudioTrack(trackId))
                                playbackSnapshot = playbackController.snapshot()
                            },
                            onSelectSubtitleTrack = { trackId ->
                                appendDiagnostic("playback", "Dispatch subtitle track ${trackId ?: "off"}")
                                playbackController.dispatch(PlaybackCommand.SelectSubtitleTrack(trackId))
                                playbackSnapshot = playbackController.snapshot()
                            },
                            isFullscreen = isFullscreen,
                            videoAspectMode = videoAspectMode,
                            onSetFullscreen = { enabled ->
                                appendDiagnostic("playback", "Set window fullscreen ${if (enabled) "on" else "off"}")
                                windowState.placement = if (enabled) {
                                    WindowPlacement.Fullscreen
                                } else {
                                    WindowPlacement.Floating
                                }
                            },
                            onSetVideoAspectMode = { mode ->
                                appendDiagnostic("playback", "Dispatch video aspect ${mode.label}")
                                playbackController.setVideoAspectMode(mode)
                                videoAspectMode = playbackController.videoAspectMode
                                playbackSnapshot = playbackController.snapshot()
                                savePlaybackPreference("aspect") {
                                    saveVideoAspectMode(mode)
                                }
                            },
                            onShowHome = { selectedTab = DesktopShellTab.HOME },
                            onShowLibrary = { selectedTab = DesktopShellTab.MEDIA_LIBRARY },
                            canOpenMedia = mpvVideoWindowId != null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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
                            diagnosticLog = diagnosticLog,
                        )
                        DesktopShellTab.PLAYBACK -> Unit
                        DesktopShellTab.MEDIA_LIBRARY -> MediaLibraryTab(
                            registeredRoots = registeredRoots,
                            indexedLibrary = indexedLibrary,
                            playbackProgresses = playbackProgresses,
                            isIndexing = isIndexing,
                            isPreparingLocalPlayback = isPreparingLocalPlayback,
                            selectedLocalPlaybackPreparation = selectedLocalPlaybackPreparation,
                            dandanplayCacheStatus = dandanplayCacheStatus,
                            autoNextLocalPlayback = autoNextLocalPlayback,
                            libraryError = libraryError,
                            lastScanStats = lastScanStats,
                            onAddLibraryFolder = {
                                selectLibraryDirectory(
                                    title = "Choose anime library folder",
                                )?.let(::registerAndScanUserRoot)
                            },
                            onRescanRegisteredRoots = ::rescanRegisteredRoots,
                            onPrepareLocalPlayback = { item -> prepareLocalPlayback(item) },
                            onPlayLocalPlayback = { item ->
                                prepareLocalPlayback(item, loadAfterPrepare = true)
                            },
                            onSetAutoNextLocalPlayback = ::setAutoNextLocalPlayback,
                            onRefreshDandanplay = ::refreshPreparedDandanplay,
                            onClearDandanplayCache = ::clearPreparedDandanplayCache,
                            onLoadPreparedPlayback = { preparation ->
                                appendDiagnostic(
                                    "playback",
                                    "Loading prepared local playback: ${preparation.item.id}; source=${preparation.source.path}",
                                )
                                queuePlaybackUntilHostReady(preparation.toPlaybackRequest())
                            },
                            remoteBrowser = {
                                RemoteLibraryBrowser(
                                    defaultServerUrl = server.baseUrl(),
                                    defaultPairingToken = server.pairingToken,
                                    appendDiagnostic = ::appendDiagnostic,
                                    onLoadPreparedPlayback = { preparation ->
                                        appendDiagnostic(
                                            "playback",
                                            "Loading remote stream into Windows controller: ${preparation.item.id}; source=${preparation.source.url}",
                                        )
                                        queuePlaybackUntilHostReady(preparation.toDesktopPlaybackRequest())
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
                            appLogPath = diagnosticFileLog.appLogPath,
                            mpvLogPath = diagnosticFileLog.mpvLogPath,
                            diagnosticLog = diagnosticLog,
                            dandanplaySettings = dandanplaySettings,
                            onSaveDandanplaySettings = ::saveDandanplaySettings,
                            onClearDandanplaySettings = ::clearDandanplaySettings,
                            onCleanupExpiredDandanplayCaches = ::cleanupExpiredDandanplayCaches,
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
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
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
        DiagnosticsPanel(diagnosticLog)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun PlaybackTab(
    playbackLabel: String?,
    playbackSnapshot: PlaybackSnapshot,
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    overlayStatus: String,
    onWindowIdChanged: (Long?) -> Unit,
    onOpenMediaFile: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekBackwardLarge: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekForwardLarge: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetPlaybackRate: (Float) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSelectAudioTrack: (String) -> Unit,
    onSelectSubtitleTrack: (String?) -> Unit,
    isFullscreen: Boolean,
    videoAspectMode: DesktopVideoAspectMode,
    onSetFullscreen: (Boolean) -> Unit,
    onSetVideoAspectMode: (DesktopVideoAspectMode) -> Unit,
    onShowHome: () -> Unit,
    onShowLibrary: () -> Unit,
    canOpenMedia: Boolean,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    val hasMedia = playbackSnapshot.source != null
    val shouldAutoHide = hasMedia && playbackSnapshot.status == PlaybackStatus.PLAYING

    LaunchedEffect(controlsVisible, shouldAutoHide) {
        if (controlsVisible && shouldAutoHide) {
            delay(PLAYER_CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPointerEvent(PointerEventType.Move) {
                controlsVisible = true
            }
            .onPointerEvent(PointerEventType.Enter) {
                controlsVisible = true
            },
    ) {
        if (controlsVisible || !shouldAutoHide) {
            PlayerTopOverlay(
                title = playbackLabel ?: playbackSnapshot.source?.toString()?.redactToken() ?: "No media loaded",
                status = playbackSnapshot.status.name,
                overlayStatus = overlayStatus,
                isFullscreen = isFullscreen,
                onShowHome = onShowHome,
                onShowLibrary = onShowLibrary,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            DesktopMpvVideoHost(
                onWindowIdChanged = onWindowIdChanged,
                modifier = Modifier.fillMaxSize(),
            )
            if (!hasMedia) {
                PlayerEmptyOverlay(
                    canOpenMedia = canOpenMedia,
                    mpvRuntimeStatus = mpvRuntimeStatus,
                    videoHostStatus = videoHostStatus,
                    onOpenMediaFile = onOpenMediaFile,
                )
            }
            playbackSnapshot.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = DanmakuColors.Warning,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        if (controlsVisible || !shouldAutoHide) {
            PlayerBottomOverlay(
                playbackSnapshot = playbackSnapshot,
                isFullscreen = isFullscreen,
                videoAspectMode = videoAspectMode,
                onOpenMediaFile = onOpenMediaFile,
                onPlay = onPlay,
                onPause = onPause,
                onSeekBackward = onSeekBackward,
                onSeekBackwardLarge = onSeekBackwardLarge,
                onSeekForward = onSeekForward,
                onSeekForwardLarge = onSeekForwardLarge,
                onSeekTo = onSeekTo,
                onSetPlaybackRate = onSetPlaybackRate,
                onSetVolume = onSetVolume,
                onSelectAudioTrack = onSelectAudioTrack,
                onSelectSubtitleTrack = onSelectSubtitleTrack,
                onSetFullscreen = onSetFullscreen,
                onSetVideoAspectMode = onSetVideoAspectMode,
                canOpenMedia = canOpenMedia,
            )
        }
    }
}

@Composable
private fun PlayerEmptyOverlay(
    canOpenMedia: Boolean,
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    onOpenMediaFile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No media loaded", color = Color.White, style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "$mpvRuntimeStatus Video host: $videoHostStatus",
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenMediaFile, enabled = canOpenMedia) {
            Text("Open media file")
        }
    }
}

@Composable
private fun PlayerTopOverlay(
    title: String,
    status: String,
    overlayStatus: String,
    isFullscreen: Boolean,
    onShowHome: () -> Unit,
    onShowLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayerIconButton(Icons.Filled.Home, "Home", onClick = onShowHome)
        PlayerIconButton(Icons.AutoMirrored.Filled.LibraryBooks, "Library", onClick = onShowLibrary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isFullscreen) {
                Text(
                    text = overlayStatus,
                    color = DanmakuColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(status, color = DanmakuColors.TextMuted, maxLines = 1)
    }
}

@Composable
private fun PlayerBottomOverlay(
    playbackSnapshot: PlaybackSnapshot,
    isFullscreen: Boolean,
    videoAspectMode: DesktopVideoAspectMode,
    onOpenMediaFile: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekBackwardLarge: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekForwardLarge: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetPlaybackRate: (Float) -> Unit,
    onSetVolume: (Int) -> Unit,
    onSelectAudioTrack: (String) -> Unit,
    onSelectSubtitleTrack: (String?) -> Unit,
    onSetFullscreen: (Boolean) -> Unit,
    onSetVideoAspectMode: (DesktopVideoAspectMode) -> Unit,
    canOpenMedia: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasMedia = playbackSnapshot.source != null
    val durationMs = playbackSnapshot.position.durationMs?.takeIf { it > 0 }
    val currentPositionMs = durationMs
        ?.let { playbackSnapshot.position.positionMs.coerceIn(0, it) }
        ?: playbackSnapshot.position.positionMs
    var sliderPositionMs by remember(playbackSnapshot.source, durationMs) {
        mutableStateOf(currentPositionMs.toFloat())
    }
    var isDragging by remember(playbackSnapshot.source) { mutableStateOf(false) }

    LaunchedEffect(currentPositionMs, durationMs, isDragging) {
        if (!isDragging) {
            sliderPositionMs = currentPositionMs.toFloat()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = currentPositionMs.formatPlaybackTime(),
                color = Color.White,
                modifier = Modifier.width(64.dp),
                maxLines = 1,
            )
            Slider(
                value = durationMs
                    ?.let { sliderPositionMs.coerceIn(0f, it.toFloat()) }
                    ?: 0f,
                onValueChange = {
                    isDragging = true
                    sliderPositionMs = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    durationMs?.let {
                        onSeekTo(sliderPositionMs.toLong().coerceIn(0, it))
                    }
                },
                valueRange = 0f..(durationMs ?: 1L).toFloat(),
                enabled = durationMs != null,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = durationMs?.formatPlaybackTime() ?: "--:--",
                color = Color.White,
                modifier = Modifier.width(64.dp),
                maxLines = 1,
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 860.dp
            val isNarrow = maxWidth < 700.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerIconButton(Icons.Filled.FolderOpen, "Open media file", enabled = canOpenMedia, onClick = onOpenMediaFile)
                if (!isNarrow) {
                    PlayerIconButton(Icons.Filled.FastRewind, "Back 30 seconds", enabled = hasMedia, onClick = onSeekBackwardLarge)
                }
                PlayerIconButton(Icons.Filled.Replay10, "Back 10 seconds", enabled = hasMedia, onClick = onSeekBackward)
                PlayerIconButton(
                    imageVector = if (playbackSnapshot.status == PlaybackStatus.PLAYING) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (playbackSnapshot.status == PlaybackStatus.PLAYING) "Pause" else "Play",
                    enabled = hasMedia,
                    onClick = if (playbackSnapshot.status == PlaybackStatus.PLAYING) onPause else onPlay,
                )
                PlayerIconButton(Icons.Filled.Forward10, "Forward 10 seconds", enabled = hasMedia, onClick = onSeekForward)
                if (!isNarrow) {
                    PlayerIconButton(Icons.Filled.FastForward, "Forward 30 seconds", enabled = hasMedia, onClick = onSeekForwardLarge)
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    tint = DanmakuColors.TextMuted,
                )
                Text(
                    text = "${playbackSnapshot.volumePercent}%",
                    color = DanmakuColors.TextMuted,
                    modifier = Modifier.width(44.dp),
                    maxLines = 1,
                )
                Slider(
                    value = playbackSnapshot.volumePercent.toFloat(),
                    onValueChange = { onSetVolume(it.toInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f,
                    enabled = hasMedia,
                    modifier = Modifier.width(if (isCompact) 72.dp else 120.dp),
                )
                PlayerOverlayButton(
                    text = "${playbackSnapshot.playbackRate}x",
                    enabled = hasMedia,
                    onClick = { onSetPlaybackRate(playbackSnapshot.playbackRate.nextPlaybackRate()) },
                )
                if (!isCompact) {
                    PlayerOverlayButton(
                        text = playbackSnapshot.selectedTrackButtonText(PlaybackTrackKind.AUDIO, "Audio"),
                        enabled = hasMedia && playbackSnapshot.tracks.any { it.kind == PlaybackTrackKind.AUDIO },
                        modifier = Modifier.width(108.dp),
                        onClick = {
                            playbackSnapshot.nextTrackId(PlaybackTrackKind.AUDIO)?.let(onSelectAudioTrack)
                        },
                    )
                    PlayerOverlayButton(
                        text = playbackSnapshot.selectedTrackButtonText(PlaybackTrackKind.SUBTITLE, "Sub"),
                        enabled = hasMedia && playbackSnapshot.tracks.any { it.kind == PlaybackTrackKind.SUBTITLE },
                        modifier = Modifier.width(108.dp),
                        onClick = {
                            onSelectSubtitleTrack(playbackSnapshot.nextSubtitleTrackId())
                        },
                    )
                    PlayerOverlayButton(
                        text = videoAspectMode.label,
                        enabled = hasMedia,
                        onClick = { onSetVideoAspectMode(videoAspectMode.nextAspectMode()) },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                PlayerIconButton(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                    enabled = hasMedia,
                    onClick = { onSetFullscreen(!isFullscreen) },
                )
            }
        }
    }
}

@Composable
private fun PlayerIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .width(36.dp)
            .height(34.dp)
            .background(
                color = if (enabled) {
                    Color.White.copy(alpha = 0.14f)
                } else {
                    Color.White.copy(alpha = 0.06f)
                },
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else DanmakuColors.TextMuted,
        )
    }
}

@Composable
private fun PlayerOverlayButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (enabled) Color.White else DanmakuColors.TextMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .height(34.dp)
            .background(
                color = if (enabled) {
                    Color.White.copy(alpha = 0.14f)
                } else {
                    Color.White.copy(alpha = 0.06f)
                },
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private fun Float.nextPlaybackRate(): Float {
    val currentIndex = PLAYBACK_RATE_STEPS.indexOfFirst { rate ->
        val delta = rate - this
        delta > -0.01f && delta < 0.01f
    }
    return if (currentIndex == -1) {
        PLAYBACK_RATE_STEPS.first()
    } else {
        PLAYBACK_RATE_STEPS[(currentIndex + 1) % PLAYBACK_RATE_STEPS.size]
    }
}

private fun PlaybackSnapshot.selectedTrackButtonText(
    kind: PlaybackTrackKind,
    fallback: String,
): String {
    val selectedTrack = tracks.firstOrNull { it.kind == kind && it.selected }
    return if (selectedTrack == null) {
        "$fallback: off"
    } else {
        "$fallback: ${selectedTrack.label}"
    }
}

private fun PlaybackSnapshot.nextTrackId(kind: PlaybackTrackKind): String? {
    val tracksOfKind = tracks.filter { it.kind == kind }
    if (tracksOfKind.isEmpty()) {
        return null
    }
    val selectedIndex = tracksOfKind.indexOfFirst(PlaybackTrack::selected)
    return tracksOfKind[(selectedIndex + 1).floorMod(tracksOfKind.size)].id
}

private fun PlaybackSnapshot.nextSubtitleTrackId(): String? {
    val subtitleTracks = tracks.filter { it.kind == PlaybackTrackKind.SUBTITLE }
    if (subtitleTracks.isEmpty()) {
        return null
    }
    val selectedIndex = subtitleTracks.indexOfFirst(PlaybackTrack::selected)
    return when {
        selectedIndex == -1 -> subtitleTracks.first().id
        selectedIndex == subtitleTracks.lastIndex -> null
        else -> subtitleTracks[selectedIndex + 1].id
    }
}

private fun DesktopVideoAspectMode.nextAspectMode(): DesktopVideoAspectMode {
    val entries = DesktopVideoAspectMode.entries
    return entries[(entries.indexOf(this) + 1).floorMod(entries.size)]
}

private fun Int.floorMod(size: Int): Int =
    ((this % size) + size) % size

@Composable
private fun MediaLibraryTab(
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    playbackProgresses: List<PlaybackProgress>,
    isIndexing: Boolean,
    isPreparingLocalPlayback: Boolean,
    selectedLocalPlaybackPreparation: DesktopLocalPlaybackPreparation?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    autoNextLocalPlayback: Boolean,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    onAddLibraryFolder: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    onSetAutoNextLocalPlayback: (Boolean) -> Unit,
    onRefreshDandanplay: (DesktopLocalPlaybackPreparation) -> Unit,
    onClearDandanplayCache: (DesktopLocalPlaybackPreparation) -> Unit,
    onLoadPreparedPlayback: (DesktopLocalPlaybackPreparation) -> Unit,
    remoteBrowser: @Composable () -> Unit,
) {
    TabScaffold {
        val continueWatchingItems = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.continueWatchingItems(playbackProgresses).orEmpty()
        }
        val recentlyWatchedItems = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.recentlyWatchedItems(playbackProgresses).orEmpty()
        }
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
                title = "Continue Watching",
                modifier = Modifier.weight(1f),
            ) {
                if (continueWatchingItems.isEmpty()) {
                    EmptyState("No in-progress local episodes yet.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(continueWatchingItems, key = { it.mediaItem.id }) { item ->
                            ContinueWatchingRow(
                                item = item,
                                isPreparing = isPreparingLocalPlayback,
                                onPlayLocalPlayback = onPlayLocalPlayback,
                            )
                        }
                    }
                }
            }
            SectionCard(
                title = "Recently Watched",
                modifier = Modifier.weight(1f),
            ) {
                if (recentlyWatchedItems.isEmpty()) {
                    EmptyState("No saved local playback activity yet.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(recentlyWatchedItems, key = { it.mediaItem.id }) { item ->
                            RecentlyWatchedRow(
                                item = item,
                                isPreparing = isPreparingLocalPlayback,
                                onPrepareLocalPlayback = onPrepareLocalPlayback,
                                onPlayLocalPlayback = onPlayLocalPlayback,
                            )
                        }
                    }
                }
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
                var searchText by remember { mutableStateOf("") }
                var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
                var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
                val catalog = indexedLibrary?.catalog
                val totalItems = catalog?.items.orEmpty()
                val items = catalog
                    ?.filteredItems(
                        LibraryCatalogQuery(
                            searchText = searchText,
                            sort = sort,
                            subtitleFilter = subtitleFilter,
                        ),
                    )
                    .orEmpty()
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search episodes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { sort = LibraryCatalogSort.TITLE },
                        enabled = sort != LibraryCatalogSort.TITLE,
                    ) {
                        Text("Sort title")
                    }
                    Button(
                        onClick = { sort = LibraryCatalogSort.PATH },
                        enabled = sort != LibraryCatalogSort.PATH,
                    ) {
                        Text("Sort path")
                    }
                    Button(
                        onClick = {
                            subtitleFilter = if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                                LibrarySubtitleFilter.WITH_SUBTITLES
                            } else {
                                LibrarySubtitleFilter.ANY
                            }
                        },
                    ) {
                        Text(if (subtitleFilter == LibrarySubtitleFilter.ANY) "Require subtitles" else "All episodes")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                MetadataRow("Showing", "${items.size} / ${totalItems.size} episodes")
                if (totalItems.isEmpty()) {
                    EmptyState("No indexed episodes yet.")
                } else if (items.isEmpty()) {
                    EmptyState("No episodes match the current filters.")
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(items, key = { it.id }) { item ->
                            EpisodeRow(
                                item = item,
                                isPreparing = isPreparingLocalPlayback,
                                onPrepareLocalPlayback = onPrepareLocalPlayback,
                                onPlayLocalPlayback = onPlayLocalPlayback,
                            )
                        }
                    }
                }
            }
        }
        selectedLocalPlaybackPreparation?.let { preparation ->
            SectionCard("Prepared Playback") {
                val previousItem = indexedLibrary?.catalog?.previousItem(preparation.item.id)
                val nextItem = indexedLibrary?.catalog?.nextItem(preparation.item.id)
                MetadataRow(
                    "Episode",
                    "${preparation.item.seriesTitle} - ${preparation.item.episodeTitle}",
                )
                MetadataRow("Source", preparation.source.path)
                MetadataRow("Resume", preparation.resumePositionMs?.let { "$it ms" } ?: "start from beginning")
                dandanplayCacheStatus
                    ?.takeIf { it.mediaId == preparation.item.id }
                    ?.let { status ->
                        MetadataRow("Danmaku status", status.summary)
                        status.details.forEach { detail ->
                            MetadataRow(detail.label, detail.value)
                        }
                    }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onLoadPreparedPlayback(preparation) }) {
                        Text("Load into player")
                    }
                    Button(
                        onClick = { onRefreshDandanplay(preparation) },
                        enabled = !isPreparingLocalPlayback,
                    ) {
                        Text(if (isPreparingLocalPlayback) "Refreshing..." else "Refresh danmaku")
                    }
                    Button(
                        onClick = { onClearDandanplayCache(preparation) },
                        enabled = !isPreparingLocalPlayback,
                    ) {
                        Text("Clear danmaku cache")
                    }
                    Button(
                        onClick = {
                            previousItem?.let(onPrepareLocalPlayback)
                        },
                        enabled = previousItem != null && !isPreparingLocalPlayback,
                    ) {
                        Text("Previous episode")
                    }
                    Button(
                        onClick = {
                            nextItem?.let(onPrepareLocalPlayback)
                        },
                        enabled = nextItem != null && !isPreparingLocalPlayback,
                    ) {
                        Text("Next episode")
                    }
                    Button(
                        onClick = { onSetAutoNextLocalPlayback(!autoNextLocalPlayback) },
                    ) {
                        Text(if (autoNextLocalPlayback) "Auto-next on" else "Auto-next off")
                    }
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
    appLogPath: Path,
    mpvLogPath: Path,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    dandanplaySettings: DandanplayProviderSettings,
    onSaveDandanplaySettings: (String, String?, String?, DandanplayAuthenticationMode, Int) -> Unit,
    onClearDandanplaySettings: () -> Unit,
    onCleanupExpiredDandanplayCaches: () -> Unit,
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
            MetadataRow("App log", appLogPath.toString())
            MetadataRow("mpv log", mpvLogPath.toString())
        }
        DandanplayProviderCard(
            settings = dandanplaySettings,
            onSave = onSaveDandanplaySettings,
            onClear = onClearDandanplaySettings,
            onCleanupExpiredCaches = onCleanupExpiredDandanplayCaches,
        )
        DiagnosticsPanel(diagnosticLog)
    }
}

@Composable
private fun DandanplayProviderCard(
    settings: DandanplayProviderSettings,
    onSave: (String, String?, String?, DandanplayAuthenticationMode, Int) -> Unit,
    onClear: () -> Unit,
    onCleanupExpiredCaches: () -> Unit,
) {
    var baseUrl by remember(settings) { mutableStateOf(settings.baseUrl) }
    var appId by remember(settings) { mutableStateOf(settings.appId.orEmpty()) }
    var appSecret by remember(settings) { mutableStateOf("") }
    var authenticationMode by remember(settings) { mutableStateOf(settings.authenticationMode) }
    var cacheMaxAgeDaysText by remember(settings) { mutableStateOf(settings.cacheMaxAgeDays.toString()) }

    SectionCard("Danmaku Providers") {
        Text(
            "dandanplay-compatible API settings for future auto-match and fetched danmaku tracks.",
            color = DanmakuColors.TextMuted,
        )
        Spacer(modifier = Modifier.height(10.dp))
        MetadataRow("dandanplay", settings.statusText)
        MetadataRow("Cache expiry", "${settings.cacheMaxAgeDays} days")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("API base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = appId,
            onValueChange = { appId = it },
            label = { Text("AppId (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = appSecret,
            onValueChange = { appSecret = it },
            label = {
                Text(
                    if (settings.hasAppSecret) {
                        "AppSecret (leave blank to keep saved secret)"
                    } else {
                        "AppSecret (optional)"
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = cacheMaxAgeDaysText,
            onValueChange = { cacheMaxAgeDaysText = it },
            label = { Text("Cache max age days") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { authenticationMode = DandanplayAuthenticationMode.SIGNED },
                enabled = authenticationMode != DandanplayAuthenticationMode.SIGNED,
            ) {
                Text("Signed auth")
            }
            Button(
                onClick = { authenticationMode = DandanplayAuthenticationMode.CREDENTIAL },
                enabled = authenticationMode != DandanplayAuthenticationMode.CREDENTIAL,
            ) {
                Text("Credential auth")
            }
            Text(
                "Current: ${authenticationMode.name.lowercase()}",
                color = DanmakuColors.TextMuted,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        baseUrl,
                        appId,
                        appSecret,
                        authenticationMode,
                        cacheMaxAgeDaysText.toIntOrNull()?.coerceAtLeast(1) ?: settings.cacheMaxAgeDays,
                    )
                    appSecret = ""
                },
            ) {
                Text("Save dandanplay settings")
            }
            Button(onClick = onClear) {
                Text("Clear")
            }
            Button(onClick = onCleanupExpiredCaches) {
                Text("Clean expired cache")
            }
        }
    }
}

@Composable
private fun TabScaffold(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
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
private fun DiagnosticsPanel(
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "Diagnostics",
        modifier = modifier,
    ) {
        if (diagnosticLog.isEmpty()) {
            EmptyState("No diagnostics yet. Start playback or scan a library to populate this log.")
            return@SectionCard
        }
        LazyColumn(modifier = Modifier.height(240.dp)) {
            items(diagnosticLog.asReversed()) { entry ->
                DiagnosticLogRow(entry)
            }
        }
    }
}

@Composable
private fun DiagnosticLogRow(entry: DesktopDiagnosticLogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = entry.occurredAtEpochMs.toDiagnosticTime(),
            color = DanmakuColors.TextMuted,
            modifier = Modifier.width(76.dp),
            maxLines = 1,
        )
        Text(
            text = entry.category,
            color = DanmakuColors.Accent,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.message,
            color = Color.White,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun LibraryCatalog.continueWatchingItems(
    progresses: List<PlaybackProgress>,
): List<DesktopPlaybackProgressItem> {
    val progressByMediaId = progresses.associateBy(PlaybackProgress::mediaId)
    return items
        .mapNotNull { item ->
            val progress = progressByMediaId[item.id]
                ?.takeIf { it.resumePositionMs() != null }
                ?: return@mapNotNull null
            DesktopPlaybackProgressItem(item, progress)
        }
        .sortedByDescending { it.progress.updatedAtEpochMs }
}

private fun LibraryCatalog.recentlyWatchedItems(
    progresses: List<PlaybackProgress>,
    limit: Int = 8,
): List<DesktopPlaybackProgressItem> {
    require(limit > 0) { "limit must be positive" }
    val itemsById = items.associateBy(LibraryMediaItem::id)
    return progresses
        .mapNotNull { progress ->
            val item = itemsById[progress.mediaId] ?: return@mapNotNull null
            DesktopPlaybackProgressItem(item, progress)
        }
        .sortedByDescending { it.progress.updatedAtEpochMs }
        .take(limit)
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
private fun ContinueWatchingRow(
    item: DesktopPlaybackProgressItem,
    isPreparing: Boolean,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Resume at ${item.progress.positionMs.formatPlaybackTime()} / " +
                    (item.progress.durationMs?.formatPlaybackTime() ?: "unknown"),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = { onPlayLocalPlayback(item.mediaItem) },
            enabled = !isPreparing,
        ) {
            Text(if (isPreparing) "Loading..." else "Resume")
        }
    }
}

@Composable
private fun RecentlyWatchedRow(
    item: DesktopPlaybackProgressItem,
    isPreparing: Boolean,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Last seen ${item.progress.updatedAtEpochMs.toDiagnosticTime()} at " +
                    "${item.progress.positionMs.formatPlaybackTime()} / " +
                    (item.progress.durationMs?.formatPlaybackTime() ?: "unknown"),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onPrepareLocalPlayback(item.mediaItem) },
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Preparing..." else "Prepare")
            }
            Button(
                onClick = { onPlayLocalPlayback(item.mediaItem) },
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Loading..." else "Play")
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    item: LibraryMediaItem,
    isPreparing: Boolean,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onPrepareLocalPlayback(item) },
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Preparing..." else "Prepare")
            }
            Button(
                onClick = { onPlayLocalPlayback(item) },
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Loading..." else "Play")
            }
        }
    }
}

@Composable
private fun RemoteLibraryBrowser(
    defaultServerUrl: String,
    defaultPairingToken: String,
    appendDiagnostic: (String, String) -> Unit,
    onLoadPreparedPlayback: (LanPlaybackPreparation) -> Unit,
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
        appendDiagnostic("remote-client", "Fetching catalog from $requestedServerUrl")
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
                appendDiagnostic("remote-client", "Fetched catalog: ${it.items.size} items")
            }.onFailure {
                libraryError = it.message
                appendDiagnostic("remote-client", "Fetch catalog failed: ${it.message}")
            }
            isLoading = false
        }
    }

    fun prepareRemotePlayback(
        item: LibraryMediaItem,
        loadAfterPrepare: Boolean,
    ) {
        val requestedServerUrl = serverUrl
        val requestedPairingToken = pairingToken
        appendDiagnostic(
            "remote-client",
            "Preparing remote playback: ${item.id} from $requestedServerUrl",
        )
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
                appendDiagnostic(
                    "remote-client",
                    "Prepared remote playback: ${item.id}; stream=${it.source.url}; resume=${it.resumePositionMs}; subtitles=${it.subtitles.size}",
                )
                if (loadAfterPrepare) {
                    appendDiagnostic("remote-client", "Loading prepared remote playback: ${item.id}")
                    onLoadPreparedPlayback(it)
                }
            }.onFailure {
                libraryError = it.message
                appendDiagnostic("remote-client", "Prepare remote playback failed: ${it.message}")
            }
            isPreparingPlayback = false
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
                    onClick = { prepareRemotePlayback(item, loadAfterPrepare = false) },
                    enabled = !isPreparingPlayback,
                ) {
                    Text(if (isPreparingPlayback) "Preparing..." else "Prepare")
                }
                Button(
                    onClick = { prepareRemotePlayback(item, loadAfterPrepare = true) },
                    enabled = !isPreparingPlayback,
                ) {
                    Text(if (isPreparingPlayback) "Loading..." else "Play stream")
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
                onLoadPreparedPlayback(preparation)
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

private fun Long.toDiagnosticTime(): String =
    DIAGNOSTIC_TIME_FORMATTER.format(
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()),
    )

private fun Long.formatPlaybackTime(): String {
    val totalSeconds = (this / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private const val PLAYBACK_HOST_SETTLE_DELAY_MS = 300L

private const val PLAYBACK_SNAPSHOT_POLL_INTERVAL_MS = 500L

private const val WINDOWS_PROGRESS_SAVE_INTERVAL_MS = 5_000L

private const val MAX_DIAGNOSTIC_LOG_ENTRIES = 200

private const val LOCAL_AUTO_NEXT_SETTING_KEY = "playback.local_auto_next"

private const val PLAYER_CONTROLS_AUTO_HIDE_MS = 3_000L

private val PLAYBACK_RATE_STEPS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

private val DIAGNOSTIC_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss")
