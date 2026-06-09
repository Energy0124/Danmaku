package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.danmaku.domain.DanmakuDisplaySettings
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryEpisodeDetail
import app.danmaku.domain.LibraryFavoriteFilter
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibraryNextUpItem
import app.danmaku.domain.LibraryNextUpReason
import app.danmaku.domain.LibraryPlaybackProgressItem
import app.danmaku.domain.LibrarySeries
import app.danmaku.domain.LibrarySeriesWatchSummary
import app.danmaku.domain.LibrarySubtitleFilter
import app.danmaku.domain.LibraryWatchState
import app.danmaku.domain.LibraryWatchStatus
import app.danmaku.domain.PlaybackCommand
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackStatus
import app.danmaku.domain.PlaybackTrack
import app.danmaku.domain.PlaybackTrackKind
import app.danmaku.domain.continueWatchingItems
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.filteredItems
import app.danmaku.domain.groupedSeries
import app.danmaku.domain.nextItem
import app.danmaku.domain.nextUpItems
import app.danmaku.domain.previousItem
import app.danmaku.domain.recentlyWatchedItems
import app.danmaku.domain.toPlaybackProgress
import app.danmaku.domain.seriesWatchSummaryById
import app.danmaku.domain.watchStatusByMediaId
import app.danmaku.library.LanLibraryConnectionSession
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
import java.awt.Rectangle
import java.awt.Window as AwtWindow
import kotlin.math.roundToInt
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.jetbrains.skia.Image as SkiaImage

fun main(args: Array<String>) = application {
    val launchOptions = remember(args) { DesktopLaunchOptions.parse(args) }
    val windowState = rememberWindowState()
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Danmaku",
    ) {
        DesktopShell(
            awtWindow = window,
            windowState = windowState,
            launchOptions = launchOptions,
            onRequestExit = ::exitApplication,
        )
    }
}

private data class DesktopDiagnosticLogEntry(
    val occurredAtEpochMs: Long,
    val category: String,
    val message: String,
)

private data class PreparedLocalPlaybackResult(
    val preparation: DesktopLocalPlaybackPreparation,
    val dandanplayResolution: DesktopDandanplayDanmakuResolution?,
    val dandanplayError: Throwable?,
)

@Composable
private fun DesktopShell(
    awtWindow: AwtWindow,
    windowState: WindowState,
    launchOptions: DesktopLaunchOptions = DesktopLaunchOptions(),
    onRequestExit: () -> Unit = {},
) {
    val hostPlatform = remember { DesktopHostPlatform.current() }
    val selectionStore = remember { LocalLibrarySelectionStore.default() }
    val catalogStore = remember { DesktopLibraryCatalogStore.default() }
    val playbackPreferencesStore = remember(catalogStore) {
        DesktopPlaybackPreferencesStore(catalogStore)
    }
    var playbackPreferences by remember(playbackPreferencesStore) {
        mutableStateOf(playbackPreferencesStore.load())
    }
    var danmakuSettings by remember(playbackPreferencesStore) {
        mutableStateOf(playbackPreferences.danmakuSettings)
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
    val posterCache = remember { DesktopAnimePosterCache.default() }
    val animeMetadataResolver = remember(catalogStore, dandanplayCredentialStore, posterCache) {
        DesktopAnimeMetadataResolver(
            catalogStore = catalogStore,
            loadConnection = dandanplayCredentialStore::loadConnection,
            posterCache = posterCache,
        )
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
            danmakuSettings = { danmakuSettings },
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
    val requiresNativeVideoHost = hostPlatform.requiresEmbeddedMpvVideoHost
    val nativeVideoHostReady = !requiresNativeVideoHost || mpvVideoWindowId != null
    val mpvControlScriptPath: Result<Path?> = remember(hostPlatform, mpvVideoWindowId) {
        if (hostPlatform == DesktopHostPlatform.WINDOWS) {
            runCatching { DesktopMpvScriptResources.installDanmakuOscScript() }
        } else {
            Result.success(null)
        }
    }
    LaunchedEffect(mpvControlScriptPath) {
        mpvControlScriptPath
            .onSuccess { scriptPath ->
                if (scriptPath != null) {
                    val sizeText = runCatching { Files.size(scriptPath) }
                        .map { " ($it bytes)" }
                        .getOrDefault("")
                    appendDiagnostic("mpv", "Installed custom mpv OSC script: $scriptPath$sizeText")
                }
            }
            .onFailure { error ->
                appendDiagnostic("mpv", "Custom mpv OSC script unavailable: ${error.message}")
            }
    }
    val videoHostStatus = if (requiresNativeVideoHost) {
        if (mpvVideoWindowId == null) {
            "waiting for native window"
        } else {
            "attached"
        }
    } else {
        "${hostPlatform.displayName} mpv-managed output"
    }
    val mpvNativeOptions = remember(hostPlatform, mpvVideoWindowId, diagnosticFileLog.mpvLogPath, mpvControlScriptPath) {
        buildMap {
            put("config", "no")
            put("terminal", "no")
            put("msg-level", "all=v")
            put("log-file", diagnosticFileLog.mpvLogPath.toAbsolutePath().normalize().toString())
            if (hostPlatform == DesktopHostPlatform.WINDOWS) {
                mpvVideoWindowId
                    ?.let { windowId ->
                        DesktopMpvWindowsOptions.forWindowId(
                            windowId = windowId,
                            controlScriptPath = mpvControlScriptPath.getOrNull(),
                        )
                    }
                    ?.let(::putAll)
            }
        }
    }
    val mpvRuntime = remember(hostPlatform, nativeVideoHostReady, mpvNativeOptions) {
        if (!nativeVideoHostReady) {
            DesktopMpvCommandExecutorRuntime(
                executor = DesktopMpvCommandExecutor { command ->
                    mpvCommandLog += command
                    appendDiagnostic("mpv", "command ${command.args.joinToString(separator = " ")}")
                },
                mode = DesktopMpvCommandExecutorMode.COMMAND_LOG_ONLY,
                statusMessage = "Native mpv video host is not ready on ${hostPlatform.displayName}. " +
                    "Command-log-only mode is active.",
            )
        } else {
            DesktopMpvCommandExecutorRuntimeFactory(platform = hostPlatform).create(
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
            if (requiresNativeVideoHost && mpvVideoWindowId == null) {
                "Created mpv runtime without video window on ${hostPlatform.displayName}"
            } else if (requiresNativeVideoHost) {
                "Created mpv runtime for native window $mpvVideoWindowId on ${hostPlatform.displayName}"
            } else {
                "Created mpv runtime with ${hostPlatform.displayName} mpv-managed output"
            },
        )
        appendDiagnostic("mpv", "Runtime mode: ${mpvRuntime.mode}; ${mpvRuntime.statusMessage}")
        appendDiagnostic("diagnostics", "App log: ${diagnosticFileLog.appLogPath}")
        appendDiagnostic("diagnostics", "mpv log: ${diagnosticFileLog.mpvLogPath}")
    }
    fun forwardMpvOscBinding(
        bindingName: String,
        x: Int? = null,
        y: Int? = null,
        sourceWidth: Int? = null,
        sourceHeight: Int? = null,
    ) {
        if (hostPlatform != DesktopHostPlatform.WINDOWS) {
            return
        }
        val args = buildList {
            add("script-binding")
            add("danmaku_osc/$bindingName")
            if (x != null && y != null) {
                add(
                    listOfNotNull(
                        x.coerceAtLeast(0),
                        y.coerceAtLeast(0),
                        sourceWidth?.coerceAtLeast(1),
                        sourceHeight?.coerceAtLeast(1),
                    ).joinToString(separator = ","),
                )
            }
        }
        runCatching {
            mpvRuntime.executor.execute(DesktopMpvCommand(args))
        }.onFailure { error ->
            appendDiagnostic("mpv", "Custom OSC input forward failed: ${error.message ?: error::class.simpleName}")
        }
    }
    fun forwardMpvOscWheel(
        x: Int,
        y: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        rotation: Int,
    ) {
        val bindingName = if (rotation < 0) {
            "danmaku-osc-wheel-up"
        } else {
            "danmaku-osc-wheel-down"
        }
        repeat(kotlin.math.abs(rotation).coerceIn(1, 5)) {
            forwardMpvOscBinding(bindingName, x, y, sourceWidth, sourceHeight)
        }
    }
    var overlayStatus by remember { mutableStateOf("Danmaku overlay: waiting for matched comments") }
    val playbackSession = remember(playbackController, mpvRuntime) {
        DesktopPlaybackSession(
            controller = playbackController,
            afterLoad = { request ->
                if (request.subtitles.any(DesktopPlaybackSubtitle::isDanmakuOverlay)) {
                    overlayStatus = "Matched danmaku overlay: attached to mpv video"
                    appendDiagnostic("overlay", "Matched/manual danmaku overlay attached for ${request.label}")
                } else {
                    overlayStatus = "No matched danmaku overlay attached"
                    appendDiagnostic("overlay", "Loaded ${request.label} without danmaku overlay")
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
    var floatingWindowBounds by remember { mutableStateOf(Rectangle(awtWindow.bounds)) }
    var pendingFloatingWindowRestore by remember { mutableStateOf(false) }
    LaunchedEffect(isFullscreen, pendingFloatingWindowRestore) {
        if (!isFullscreen && pendingFloatingWindowRestore) {
            delay(150)
            awtWindow.bounds = awtWindow.scaledRestoreBounds(floatingWindowBounds)
            awtWindow.validate()
            delay(150)
            awtWindow.bounds = awtWindow.scaledRestoreBounds(floatingWindowBounds)
            awtWindow.validate()
            pendingFloatingWindowRestore = false
        }
    }
    fun setWindowFullscreen(enabled: Boolean, source: String) {
        appendDiagnostic("playback", "$source set window fullscreen ${if (enabled) "on" else "off"}")
        if (enabled) {
            pendingFloatingWindowRestore = false
            floatingWindowBounds = Rectangle(awtWindow.bounds)
            windowState.placement = WindowPlacement.Fullscreen
        } else {
            windowState.placement = WindowPlacement.Floating
            pendingFloatingWindowRestore = true
        }
    }
    LaunchedEffect(mpvRuntime, hostPlatform, isFullscreen) {
        if (hostPlatform != DesktopHostPlatform.WINDOWS) {
            return@LaunchedEffect
        }
        runCatching {
            mpvRuntime.executor.execute(
                DesktopMpvCommand(
                    listOf(
                        "script-binding",
                        if (isFullscreen) {
                            MPV_OSC_APP_FULLSCREEN_ON_BINDING
                        } else {
                            MPV_OSC_APP_FULLSCREEN_OFF_BINDING
                        },
                    ),
                ),
            )
        }.onFailure { error ->
            appendDiagnostic("mpv", "Custom OSC fullscreen state sync failed: ${error.message ?: error::class.simpleName}")
        }
    }
    LaunchedEffect(mpvRuntime, hostPlatform, isFullscreen) {
        if (hostPlatform != DesktopHostPlatform.WINDOWS) {
            return@LaunchedEffect
        }
        val reader = mpvRuntime.propertyReader ?: return@LaunchedEffect
        var lastRequest = reader.readProperty(MPV_OSC_FULLSCREEN_REQUEST_PROPERTY)
        while (true) {
            delay(200)
            val nextRequest = reader.readProperty(MPV_OSC_FULLSCREEN_REQUEST_PROPERTY)
            if (!nextRequest.isNullOrBlank() && nextRequest != lastRequest) {
                lastRequest = nextRequest
                setWindowFullscreen(!isFullscreen, "mpv OSC")
            }
        }
    }
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
    var seriesPosterById by remember(indexedLibrary) {
        mutableStateOf(loadSeriesPosterById(indexedLibrary, animeMetadataResolver))
    }
    var playbackProgresses by remember { mutableStateOf(catalogStore.loadPlaybackProgress()) }
    var favoriteMediaIds by remember { mutableStateOf(catalogStore.loadFavoriteMediaIds()) }
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
                    seriesPosterById = loadSeriesPosterById(library, animeMetadataResolver)
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
        seriesPosterById = loadSeriesPosterById(library, animeMetadataResolver)
        playbackProgresses = catalogStore.loadPlaybackProgress()
        favoriteMediaIds = catalogStore.loadFavoriteMediaIds()
        registeredRoots = rootRegistry.loadRoots()
        selectedLocalPlaybackPreparation = null
        libraryError = null
    }

    fun setFavorite(item: LibraryMediaItem, isFavorite: Boolean) {
        val updatedFavorites = if (isFavorite) {
            favoriteMediaIds + item.id
        } else {
            favoriteMediaIds - item.id
        }
        runCatching {
            catalogStore.saveFavoriteMediaIds(updatedFavorites)
            catalogStore.loadFavoriteMediaIds()
        }.onSuccess { loadedFavorites ->
            favoriteMediaIds = loadedFavorites
            appendDiagnostic(
                "library",
                if (isFavorite) {
                    "Favorited ${item.id}"
                } else {
                    "Removed favorite ${item.id}"
                },
            )
        }.onFailure { error ->
            libraryError = error.message
            appendDiagnostic("library", "Failed to update favorite ${item.id}: ${error.message}")
        }
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
    var smokePlaybackQueued by remember { mutableStateOf(false) }
    var smokePlaybackExitStarted by remember { mutableStateOf(false) }
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
            if (requiresNativeVideoHost) {
                "Queued playback until native video host attaches: ${request.label}; source=${request.source.toString().redactToken()}"
            } else {
                "Queued playback for ${hostPlatform.displayName} mpv output: ${request.label}; source=${request.source.toString().redactToken()}"
            },
        )
        selectedTab = DesktopShellTab.PLAYBACK
    }

    fun queueSmokePlayback(options: DesktopSmokePlaybackOptions) {
        val mediaPath = options.mediaPath.toAbsolutePath().normalize()
        if (!Files.isRegularFile(mediaPath)) {
            appendDiagnostic("smoke", "Smoke playback media does not exist: $mediaPath")
            return
        }
        appendDiagnostic(
            "smoke",
            "Queueing smoke playback: media=$mediaPath; duration=${options.playbackDuration.inWholeSeconds}s; " +
                "autoExit=${options.autoExit}",
        )
        queuePlaybackUntilHostReady(
            mediaPath.toDirectLocalPlaybackRequest().copy(
                label = "Smoke playback - ${mediaPath.fileName ?: mediaPath}",
            ),
        )
    }

    fun prepareLocalPlayback(
        item: LibraryMediaItem,
        loadAfterPrepare: Boolean = false,
        refreshDandanplay: Boolean = false,
        preferredDandanplayEpisodeId: Long? = null,
    ) {
        val library = indexedLibrary
        if (library == null) {
            dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = item.id,
                summary = "library is not indexed; cannot check danmaku",
                details = item.dandanplayStatusContext(dandanplaySettings),
            )
            libraryError = "Index or scan a local library before preparing playback."
            appendDiagnostic("playback", "Cannot prepare local playback; library is not indexed")
            return
        }
        scope.launch {
            appendDiagnostic("playback", "Preparing local library playback: ${item.id}")
            isPreparingLocalPlayback = true
            dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = item.id,
                summary = when {
                    preferredDandanplayEpisodeId != null -> "loading selected dandanplay match..."
                    refreshDandanplay -> "refreshing dandanplay match..."
                    else -> "checking dandanplay match..."
                },
                details = item.dandanplayStatusContext(dandanplaySettings) +
                    listOfNotNull(
                        preferredDandanplayEpisodeId?.let {
                            DandanplayPlaybackUiDetail("Requested episode ID", it.toString())
                        },
                    ),
            )
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
                                preferredEpisodeId = preferredDandanplayEpisodeId,
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
                result.dandanplayResolution?.match?.animeId?.let { animeId ->
                    scope.launch {
                        val refreshedSeriesId = withContext(Dispatchers.IO) {
                            val series = indexedLibrary
                                ?.catalog
                                ?.groupedSeries()
                                ?.firstOrNull { librarySeries ->
                                    librarySeries.seasons.any { season ->
                                        season.items.any { seriesItem -> seriesItem.id == item.id }
                                    }
                                }
                            series?.let {
                                runCatching {
                                    animeMetadataResolver.refreshDandanplayMetadataForSeries(
                                        series = it,
                                        animeId = animeId,
                                        forceRefresh = refreshDandanplay,
                                    )
                                    it.id
                                }.getOrNull()
                            }
                        }
                        if (refreshedSeriesId != null) {
                            seriesPosterById = loadSeriesPosterById(indexedLibrary, animeMetadataResolver)
                            appendDiagnostic("metadata", "Refreshed dandanplay poster metadata for ${item.seriesTitle}")
                        }
                    }
                }
                appendDiagnostic(
                    "playback",
                    "Prepared local playback: ${item.id}; source=${result.preparation.source.path}; resume=${result.preparation.resumePositionMs}",
                )
                when {
                    !dandanplaySettings.isFetchEnabled ->
                        appendDiagnostic("danmaku", "dandanplay fetching is not configured; no automatic danmaku overlay attached")
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
                dandanplayCacheStatus = when {
                    !dandanplaySettings.isFetchEnabled ->
                        dandanplayStatusMessage(
                            mediaId = item.id,
                            summary = "dandanplay fetching is not configured",
                            details = item.dandanplayStatusContext(dandanplaySettings) +
                                DandanplayPlaybackUiDetail(
                                    "Result",
                                    "automatic danmaku matching skipped",
                                ),
                        )
                    result.dandanplayError != null ->
                        dandanplayStatusMessage(
                            mediaId = item.id,
                            summary = "dandanplay match failed",
                            details = item.dandanplayStatusContext(dandanplaySettings) +
                                DandanplayPlaybackUiDetail(
                                    "Error",
                                    result.dandanplayError.readableMessage(),
                                ),
                        )
                    result.dandanplayResolution != null -> {
                        val resolution = result.dandanplayResolution
                        val playbackDetail = when {
                            resolution.subtitle == null -> null
                            loadAfterPrepare -> DandanplayPlaybackUiDetail("Playback", "loading into player now")
                            else -> DandanplayPlaybackUiDetail(
                                "Playback",
                                "overlay prepared; use Load into player to attach it",
                            )
                        }
                        val status = dandanplayStatusFromResolution(item.id, resolution)
                        status.copy(
                            details = item.dandanplayStatusContext(dandanplaySettings) +
                                status.details +
                                listOfNotNull(playbackDetail),
                        )
                    }
                    else ->
                        dandanplayStatusMessage(
                            mediaId = item.id,
                            summary = "dandanplay returned no result",
                            details = item.dandanplayStatusContext(dandanplaySettings),
                        )
                }
                if (loadAfterPrepare) {
                    appendDiagnostic("playback", "Auto-loading prepared local playback: ${item.id}")
                    queuePlaybackUntilHostReady(result.preparation.toPlaybackRequest())
                }
            }.onFailure {
                libraryError = it.message
                dandanplayCacheStatus = dandanplayStatusMessage(
                    mediaId = item.id,
                    summary = "prepare failed before danmaku could load",
                    details = item.dandanplayStatusContext(dandanplaySettings) +
                        DandanplayPlaybackUiDetail("Error", it.readableMessage()),
                )
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
                details = preparation.item.dandanplayStatusContext(dandanplaySettings) +
                    DandanplayPlaybackUiDetail(
                        "Result",
                        "automatic danmaku refresh skipped",
                    ),
            )
            appendDiagnostic("danmaku", "Cannot refresh dandanplay cache; provider fetching is not configured")
            return
        }
        appendDiagnostic("danmaku", "Refreshing dandanplay cache for ${preparation.item.id}")
        prepareLocalPlayback(preparation.item, refreshDandanplay = true)
    }

    fun selectPreparedDandanplayMatch(
        preparation: DesktopLocalPlaybackPreparation,
        match: DandanplayMatch,
    ) {
        appendDiagnostic(
            "danmaku",
            "Selecting dandanplay match ${match.displayTitle} (${match.episodeId}) for ${preparation.item.id}",
        )
        prepareLocalPlayback(
            item = preparation.item,
            refreshDandanplay = true,
            preferredDandanplayEpisodeId = match.episodeId,
        )
    }

    fun clearPreparedDandanplayCache(preparation: DesktopLocalPlaybackPreparation) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayDanmakuResolver.clearCache(preparation.item.id)
                }
            }.onSuccess {
                selectedLocalPlaybackPreparation = preparation.withoutDanmakuOverlays()
                dandanplayCacheStatus = dandanplayStatusMessage(
                    mediaId = preparation.item.id,
                    summary = "dandanplay cache cleared",
                    details = preparation.item.dandanplayStatusContext(dandanplaySettings),
                )
                appendDiagnostic("danmaku", "Cleared dandanplay cache for ${preparation.item.id}")
            }.onFailure {
                appendDiagnostic("danmaku", "Failed to clear dandanplay cache for ${preparation.item.id}: ${it.message}")
            }
        }
    }

    fun clearPreparedDanmakuOverlay(preparation: DesktopLocalPlaybackPreparation) {
        selectedLocalPlaybackPreparation = preparation.withoutDanmakuOverlays()
        dandanplayCacheStatus = dandanplayStatusMessage(
            mediaId = preparation.item.id,
            summary = "prepared danmaku overlay removed",
            details = preparation.item.dandanplayStatusContext(dandanplaySettings),
        )
        appendDiagnostic("danmaku", "Removed prepared danmaku overlay for ${preparation.item.id}")
    }

    fun attachManualDanmaku(preparation: DesktopLocalPlaybackPreparation) {
        val danmakuPath = selectDanmakuFile("Choose local danmaku XML or JSON file") ?: return
        scope.launch {
            appendDiagnostic("danmaku", "Rendering manual danmaku overlay: $danmakuPath")
            runCatching {
                withContext(Dispatchers.IO) {
                    DesktopManualDanmakuOverlayRenderer.render(
                        inputPath = danmakuPath,
                        settings = danmakuSettings,
                    )
                }
            }.onSuccess { overlay ->
                selectedLocalPlaybackPreparation = preparation.withManualDanmakuOverlay(overlay)
                dandanplayCacheStatus = dandanplayStatusMessage(
                    mediaId = preparation.item.id,
                    summary = "manual danmaku: attached ${overlay.eventCount} comments",
                    details = preparation.item.dandanplayStatusContext(dandanplaySettings) +
                        listOf(
                            DandanplayPlaybackUiDetail("Source file", overlay.inputPath.toString()),
                            DandanplayPlaybackUiDetail("Comments", "${overlay.eventCount} comments"),
                        ),
                )
                appendDiagnostic(
                    "danmaku",
                    "Attached manual danmaku ${overlay.inputPath.fileName} with ${overlay.eventCount} comments",
                )
            }.onFailure {
                appendDiagnostic("danmaku", "Manual danmaku attach failed: ${it.message}")
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
    fun saveDanmakuSettings(settings: DanmakuDisplaySettings) {
        runCatching {
            playbackPreferencesStore.saveDanmakuSettings(settings)
            playbackPreferencesStore.load()
        }.onSuccess { updatedPreferences ->
            playbackPreferences = updatedPreferences
            danmakuSettings = updatedPreferences.danmakuSettings
            overlayStatus = if (updatedPreferences.danmakuSettings.visible) {
                "Danmaku settings saved; reload media or refresh cache to apply"
            } else {
                "Danmaku hidden by display settings"
            }
            appendDiagnostic("settings", "Saved danmaku display settings")
        }.onFailure {
            appendDiagnostic("settings", "Failed to save danmaku display settings: ${it.message}")
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

    LaunchedEffect(launchOptions.smokePlayback) {
        val smokePlayback = launchOptions.smokePlayback ?: return@LaunchedEffect
        if (smokePlaybackQueued) {
            return@LaunchedEffect
        }
        smokePlaybackQueued = true
        queueSmokePlayback(smokePlayback)
    }

    LaunchedEffect(selectedTab, nativeVideoHostReady, pendingPlaybackRequest, playbackSession) {
        val request = pendingPlaybackRequest ?: return@LaunchedEffect
        if (selectedTab != DesktopShellTab.PLAYBACK || !nativeVideoHostReady) {
            return@LaunchedEffect
        }
        appendDiagnostic(
            "playback",
            if (requiresNativeVideoHost) {
                "Native video host ready; waiting ${PLAYBACK_HOST_SETTLE_DELAY_MS}ms before queued load: ${request.label}"
            } else {
                "Native mpv runtime ready; loading queued playback: ${request.label}"
            },
        )
        if (requiresNativeVideoHost) {
            delay(PLAYBACK_HOST_SETTLE_DELAY_MS)
        }
        if (pendingPlaybackRequest != request || selectedTab != DesktopShellTab.PLAYBACK || !nativeVideoHostReady) {
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

    LaunchedEffect(launchOptions.smokePlayback, playbackSnapshot.status) {
        val smokePlayback = launchOptions.smokePlayback ?: return@LaunchedEffect
        if (!smokePlayback.autoExit || smokePlaybackExitStarted || playbackSnapshot.status != PlaybackStatus.PLAYING) {
            return@LaunchedEffect
        }
        smokePlaybackExitStarted = true
        appendDiagnostic(
            "smoke",
            "Smoke playback reached PLAYING; waiting ${smokePlayback.playbackDuration.inWholeSeconds}s before exit",
        )
        delay(smokePlayback.playbackDuration)
        val finalSnapshot = playbackController.snapshot()
        appendDiagnostic(
            "smoke",
            "Smoke playback complete: status=${finalSnapshot.status}; " +
                "position=${finalSnapshot.position.positionMs}ms; duration=${finalSnapshot.position.durationMs ?: "unknown"}",
        )
        onRequestExit()
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
                            videoHostStatus = videoHostStatus,
                            overlayStatus = overlayStatus,
                            onWindowIdChanged = { windowId ->
                                if (mpvVideoWindowId != windowId) {
                                    appendDiagnostic(
                                        "video-host",
                                        windowId
                                            ?.let { "Native video window attached: $it" }
                                            ?: "Native video window detached",
                                    )
                                }
                                mpvVideoWindowId = windowId
                            },
                            onMpvPointerMove = { x, y, width, height ->
                                forwardMpvOscBinding("danmaku-osc-mouse-move", x, y, width, height)
                            },
                            onMpvPrimaryClick = { x, y, width, height ->
                                forwardMpvOscBinding("danmaku-osc-left-click", x, y, width, height)
                            },
                            onMpvWheel = { x, y, width, height, rotation ->
                                forwardMpvOscWheel(x, y, width, height, rotation)
                            },
                            onOpenMediaFile = {
                                appendDiagnostic("playback", "Opening direct media file picker")
                                selectMediaFile(
                                    title = "Choose media file for ${hostPlatform.displayName} playback",
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
                            onSetFullscreen = { enabled -> setWindowFullscreen(enabled, "playback") },
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
                            seriesPosterById = seriesPosterById,
                            playbackProgresses = playbackProgresses,
                            favoriteMediaIds = favoriteMediaIds,
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
                            onSetFavorite = ::setFavorite,
                            onSetAutoNextLocalPlayback = ::setAutoNextLocalPlayback,
                            onRefreshDandanplay = ::refreshPreparedDandanplay,
                            onSelectDandanplayMatch = ::selectPreparedDandanplayMatch,
                            onClearDandanplayCache = ::clearPreparedDandanplayCache,
                            onClearDanmakuOverlay = ::clearPreparedDanmakuOverlay,
                            onAttachManualDanmaku = ::attachManualDanmaku,
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
                                            "Loading remote stream into ${hostPlatform.displayName} controller: ${preparation.item.id}; source=${preparation.source.url}",
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
                            videoHostStatus = videoHostStatus,
                            serverBaseUrl = server.baseUrl(),
                            networkUrls = networkUrls,
                            pairingToken = server.pairingToken,
                            appLogPath = diagnosticFileLog.appLogPath,
                            mpvLogPath = diagnosticFileLog.mpvLogPath,
                            diagnosticLog = diagnosticLog,
                            danmakuSettings = danmakuSettings,
                            onSaveDanmakuSettings = ::saveDanmakuSettings,
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
    onMpvPointerMove: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    onMpvPrimaryClick: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    onMpvWheel: (x: Int, y: Int, width: Int, height: Int, rotation: Int) -> Unit,
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
    var controlsInteractionSerial by remember { mutableStateOf(0L) }
    val focusRequester = remember { FocusRequester() }
    val hasMedia = playbackSnapshot.source != null
    val shouldAutoHide = hasMedia && playbackSnapshot.status == PlaybackStatus.PLAYING
    fun revealControls() {
        controlsInteractionSerial += 1
        controlsVisible = true
    }
    fun Modifier.revealControlsOnPointerInput(): Modifier =
        onPointerEvent(PointerEventType.Move) {
            revealControls()
        }.onPointerEvent(PointerEventType.Enter) {
            revealControls()
        }.onPointerEvent(PointerEventType.Press) {
            revealControls()
        }.onPointerEvent(PointerEventType.Scroll) {
            revealControls()
        }

    fun handleShortcut(shortcut: DesktopPlayerShortcut): Boolean {
        if (!hasMedia) return false
        revealControls()
        when (shortcut) {
            DesktopPlayerShortcut.TOGGLE_PLAY_PAUSE -> {
                if (playbackSnapshot.status == PlaybackStatus.PLAYING) {
                    onPause()
                } else {
                    onPlay()
                }
            }
            DesktopPlayerShortcut.SEEK_BACKWARD -> onSeekBackward()
            DesktopPlayerShortcut.SEEK_BACKWARD_LARGE -> onSeekBackwardLarge()
            DesktopPlayerShortcut.SEEK_FORWARD -> onSeekForward()
            DesktopPlayerShortcut.SEEK_FORWARD_LARGE -> onSeekForwardLarge()
            DesktopPlayerShortcut.VOLUME_UP -> onSetVolume((playbackSnapshot.volumePercent + 5).coerceIn(0, 100))
            DesktopPlayerShortcut.VOLUME_DOWN -> onSetVolume((playbackSnapshot.volumePercent - 5).coerceIn(0, 100))
            DesktopPlayerShortcut.CYCLE_PLAYBACK_RATE -> onSetPlaybackRate(playbackSnapshot.playbackRate.nextPlaybackRate())
            DesktopPlayerShortcut.CYCLE_AUDIO_TRACK -> {
                playbackSnapshot.nextTrackId(PlaybackTrackKind.AUDIO)?.let(onSelectAudioTrack) ?: return false
            }
            DesktopPlayerShortcut.CYCLE_SUBTITLE_TRACK -> {
                if (playbackSnapshot.tracks.none { it.kind == PlaybackTrackKind.SUBTITLE }) return false
                onSelectSubtitleTrack(playbackSnapshot.nextSubtitleTrackId())
            }
            DesktopPlayerShortcut.CYCLE_ASPECT_MODE -> onSetVideoAspectMode(videoAspectMode.nextAspectMode())
            DesktopPlayerShortcut.TOGGLE_FULLSCREEN -> onSetFullscreen(!isFullscreen)
        }
        return true
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(controlsVisible, controlsInteractionSerial, shouldAutoHide) {
        if (controlsVisible && shouldAutoHide) {
            delay(PLAYER_CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }
    val playbackModifier = modifier
        .fillMaxSize()
        .background(Color.Black)
        .onPreviewKeyEvent { event ->
            event.toDesktopPlayerShortcutInput()
                ?.let(::resolveDesktopPlayerShortcut)
                ?.let(::handleShortcut)
                ?: false
        }
        .focusRequester(focusRequester)
        .focusable()
        .revealControlsOnPointerInput()

    val emptyHostControlsState = if (!hasMedia) {
        DesktopMpvVideoControlsState(
            visible = true,
            hasMedia = false,
            title = playbackLabel ?: playbackSnapshot.source?.toString()?.redactToken() ?: "No media loaded",
            status = playbackSnapshot.status.name,
            overlayStatus = overlayStatus,
            positionMs = 0L,
            durationMs = null,
            isPlaying = false,
            volumePercent = playbackSnapshot.volumePercent,
            playbackRate = playbackSnapshot.playbackRate,
            audioText = "Audio",
            subtitleText = "Sub",
            aspectText = videoAspectMode.label,
            isFullscreen = isFullscreen,
            canOpenMedia = canOpenMedia,
            canCycleAudio = false,
            canCycleSubtitle = false,
        )
    } else {
        null
    }
    val emptyHostControlsActions = if (!hasMedia) {
        DesktopMpvVideoControlsActions(
            onShowHome = onShowHome,
            onShowLibrary = onShowLibrary,
            onOpenMediaFile = onOpenMediaFile,
            onPlayPause = {},
            onSeekBackward = {},
            onSeekBackwardLarge = {},
            onSeekForward = {},
            onSeekForwardLarge = {},
            onSeekTo = { _: Long -> },
            onSetVolume = { _: Int -> },
            onCyclePlaybackRate = {},
            onCycleAudioTrack = {},
            onCycleSubtitleTrack = {},
            onCycleAspectMode = {},
            onToggleFullscreen = { onSetFullscreen(!isFullscreen) },
        )
    } else {
        null
    }

    Column(modifier = playbackModifier) {
        if (hasMedia && !isFullscreen) {
            PlaybackWindowNavigationHeader(
                title = playbackLabel ?: playbackSnapshot.source?.toString()?.redactToken() ?: "Playing",
                status = playbackSnapshot.status.name,
                overlayStatus = overlayStatus,
                onShowHome = onShowHome,
                onShowLibrary = onShowLibrary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PLAYER_WINDOW_NAVIGATION_HEIGHT_DP.dp)
                    .revealControlsOnPointerInput(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            DesktopMpvVideoHost(
                onWindowIdChanged = onWindowIdChanged,
                onUserInput = ::revealControls,
                onMpvPointerMove = onMpvPointerMove,
                onMpvPrimaryClick = onMpvPrimaryClick,
                onMpvWheel = onMpvWheel,
                controlsState = emptyHostControlsState,
                controlsActions = emptyHostControlsActions,
                modifier = Modifier.fillMaxSize(),
            )
            if (!hasMedia) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PLAYER_TOP_CONTROLS_HEIGHT_DP.dp)
                        .align(Alignment.TopCenter)
                        .revealControlsOnPointerInput(),
                ) {
                    PlayerTopOverlay(
                        title = playbackLabel ?: playbackSnapshot.source?.toString()?.redactToken() ?: "No media loaded",
                        status = playbackSnapshot.status.name,
                        overlayStatus = overlayStatus,
                        isFullscreen = isFullscreen,
                        onShowHome = onShowHome,
                        onShowLibrary = onShowLibrary,
                        modifier = Modifier
                            .fillMaxSize()
                            .revealControlsOnPointerInput(),
                    )
                }
                PlayerEmptyOverlay(
                    canOpenMedia = canOpenMedia,
                    mpvRuntimeStatus = mpvRuntimeStatus,
                    videoHostStatus = videoHostStatus,
                    onOpenMediaFile = onOpenMediaFile,
                )
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PLAYER_BOTTOM_CONTROLS_HEIGHT_DP.dp)
                        .align(Alignment.BottomCenter)
                        .revealControlsOnPointerInput(),
                ) {
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
                        modifier = Modifier
                            .fillMaxSize()
                            .revealControlsOnPointerInput(),
                    )
                }
            }
        }
    }
}

private fun KeyEvent.toDesktopPlayerShortcutInput(): DesktopPlayerShortcutInput? {
    if (type != KeyEventType.KeyUp) return null

    val shortcutKey = when (key) {
        Key.Spacebar -> DesktopPlayerShortcutKey.SPACE
        Key.K -> DesktopPlayerShortcutKey.K
        Key.DirectionLeft -> DesktopPlayerShortcutKey.LEFT
        Key.DirectionRight -> DesktopPlayerShortcutKey.RIGHT
        Key.DirectionUp -> DesktopPlayerShortcutKey.UP
        Key.DirectionDown -> DesktopPlayerShortcutKey.DOWN
        Key.R -> DesktopPlayerShortcutKey.R
        Key.A -> DesktopPlayerShortcutKey.A
        Key.S -> DesktopPlayerShortcutKey.S
        Key.V -> DesktopPlayerShortcutKey.V
        Key.F -> DesktopPlayerShortcutKey.F
        else -> return null
    }

    return DesktopPlayerShortcutInput(
        key = shortcutKey,
        shiftPressed = isShiftPressed,
        ctrlOrMetaPressed = isCtrlPressed || isMetaPressed,
        altPressed = isAltPressed,
    )
}

@Composable
private fun PlayerEmptyOverlay(
    canOpenMedia: Boolean,
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    onOpenMediaFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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
private fun PlaybackWindowNavigationHeader(
    title: String,
    status: String,
    overlayStatus: String,
    onShowHome: () -> Unit,
    onShowLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayerIconButton(Icons.Filled.Home, "Home", onClick = onShowHome)
        PlayerIconButton(Icons.AutoMirrored.Filled.LibraryBooks, "Library", onClick = onShowLibrary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = overlayStatus,
                color = DanmakuColors.TextMuted,
                style = MaterialTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = status,
            color = DanmakuColors.TextMuted,
            style = MaterialTheme.typography.caption,
            maxLines = 1,
        )
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
    seriesPosterById: Map<String, Path?>,
    playbackProgresses: List<PlaybackProgress>,
    favoriteMediaIds: Set<String>,
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
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onSetAutoNextLocalPlayback: (Boolean) -> Unit,
    onRefreshDandanplay: (DesktopLocalPlaybackPreparation) -> Unit,
    onSelectDandanplayMatch: (DesktopLocalPlaybackPreparation, DandanplayMatch) -> Unit,
    onClearDandanplayCache: (DesktopLocalPlaybackPreparation) -> Unit,
    onClearDanmakuOverlay: (DesktopLocalPlaybackPreparation) -> Unit,
    onAttachManualDanmaku: (DesktopLocalPlaybackPreparation) -> Unit,
    onLoadPreparedPlayback: (DesktopLocalPlaybackPreparation) -> Unit,
    remoteBrowser: @Composable () -> Unit,
) {
    TabScaffold {
        val nextUpItems = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.nextUpItems(playbackProgresses).orEmpty()
        }
        val continueWatchingItems = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.continueWatchingItems(playbackProgresses).orEmpty()
        }
        val recentlyWatchedItems = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.recentlyWatchedItems(playbackProgresses).orEmpty()
        }
        val watchStatusById = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.watchStatusByMediaId(playbackProgresses).orEmpty()
        }
        val seriesWatchSummaryById = remember(indexedLibrary, playbackProgresses) {
            indexedLibrary?.catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty()
        }
        var selectedSeriesId by remember(indexedLibrary?.catalog) { mutableStateOf<String?>(null) }
        var selectedEpisodeId by remember(indexedLibrary?.catalog) { mutableStateOf<String?>(null) }
        val series = remember(indexedLibrary?.catalog) {
            indexedLibrary?.catalog?.groupedSeries().orEmpty()
        }
        val selectedSeries = series.firstOrNull { it.id == selectedSeriesId } ?: series.firstOrNull()
        val selectedEpisodeDetail = remember(
            indexedLibrary?.catalog,
            playbackProgresses,
            selectedEpisodeId,
            selectedLocalPlaybackPreparation?.item?.id,
        ) {
            val catalog = indexedLibrary?.catalog
            selectedEpisodeId
                ?.let { id -> catalog?.episodeDetail(id, playbackProgresses) }
                ?: selectedLocalPlaybackPreparation
                    ?.item
                    ?.id
                    ?.let { id -> catalog?.episodeDetail(id, playbackProgresses) }
        }
        WindowsLibraryWorkspace(
            registeredRoots = registeredRoots,
            indexedLibrary = indexedLibrary,
            series = series,
            seriesPosterById = seriesPosterById,
            selectedSeries = selectedSeries,
            selectedEpisodeDetail = selectedEpisodeDetail,
            selectedLocalPlaybackPreparation = selectedLocalPlaybackPreparation,
            dandanplayCacheStatus = dandanplayCacheStatus,
            autoNextLocalPlayback = autoNextLocalPlayback,
            nextUpItems = nextUpItems,
            continueWatchingItems = continueWatchingItems,
            recentlyWatchedItems = recentlyWatchedItems,
            watchStatusById = watchStatusById,
            seriesWatchSummaryById = seriesWatchSummaryById,
            favoriteMediaIds = favoriteMediaIds,
            isIndexing = isIndexing,
            isPreparing = isPreparingLocalPlayback,
            libraryError = libraryError,
            lastScanStats = lastScanStats,
            onAddLibraryFolder = onAddLibraryFolder,
            onRescanRegisteredRoots = onRescanRegisteredRoots,
            onSelectSeries = { selectedSeriesId = it.id },
            onShowDetails = { selectedEpisodeId = it.id },
            onSetFavorite = onSetFavorite,
            onSetAutoNextLocalPlayback = onSetAutoNextLocalPlayback,
            onRefreshDandanplay = onRefreshDandanplay,
            onSelectDandanplayMatch = onSelectDandanplayMatch,
            onClearDandanplayCache = onClearDandanplayCache,
            onClearDanmakuOverlay = onClearDanmakuOverlay,
            onAttachManualDanmaku = onAttachManualDanmaku,
            onLoadPreparedPlayback = onLoadPreparedPlayback,
            onPrepareLocalPlayback = onPrepareLocalPlayback,
            onPlayLocalPlayback = onPlayLocalPlayback,
            remoteBrowser = remoteBrowser,
        )
    }
}

private enum class WindowsLibraryView(
    val label: String,
) {
    CONTINUE_WATCHING("Continue"),
    NEXT_UP("Next up"),
    ALL_SERIES("All Series"),
    RECENTLY_WATCHED("History"),
    FAVORITES("Favorites"),
    FILES("Files"),
    PAIRED("Paired"),
}

@Composable
private fun WindowsLibraryWorkspace(
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    series: List<LibrarySeries>,
    seriesPosterById: Map<String, Path?>,
    selectedSeries: LibrarySeries?,
    selectedEpisodeDetail: LibraryEpisodeDetail?,
    selectedLocalPlaybackPreparation: DesktopLocalPlaybackPreparation?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    autoNextLocalPlayback: Boolean,
    nextUpItems: List<LibraryNextUpItem>,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    recentlyWatchedItems: List<LibraryPlaybackProgressItem>,
    watchStatusById: Map<String, LibraryWatchStatus>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    favoriteMediaIds: Set<String>,
    isIndexing: Boolean,
    isPreparing: Boolean,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    onAddLibraryFolder: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    onSelectSeries: (LibrarySeries) -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onSetAutoNextLocalPlayback: (Boolean) -> Unit,
    onRefreshDandanplay: (DesktopLocalPlaybackPreparation) -> Unit,
    onSelectDandanplayMatch: (DesktopLocalPlaybackPreparation, DandanplayMatch) -> Unit,
    onClearDandanplayCache: (DesktopLocalPlaybackPreparation) -> Unit,
    onClearDanmakuOverlay: (DesktopLocalPlaybackPreparation) -> Unit,
    onAttachManualDanmaku: (DesktopLocalPlaybackPreparation) -> Unit,
    onLoadPreparedPlayback: (DesktopLocalPlaybackPreparation) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    remoteBrowser: @Composable () -> Unit,
) {
    var selectedView by remember { mutableStateOf(WindowsLibraryView.ALL_SERIES) }
    var searchText by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
    var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
    var favoriteFilter by remember { mutableStateOf(LibraryFavoriteFilter.ANY) }
    val catalog = indexedLibrary?.catalog
    val effectiveFavoriteFilter = if (selectedView == WindowsLibraryView.FAVORITES) {
        LibraryFavoriteFilter.FAVORITES_ONLY
    } else {
        favoriteFilter
    }
    val filteredEpisodes = remember(catalog, searchText, sort, subtitleFilter, effectiveFavoriteFilter, favoriteMediaIds) {
        catalog
            ?.filteredItems(
                LibraryCatalogQuery(
                    searchText = searchText,
                    sort = sort,
                    subtitleFilter = subtitleFilter,
                    favoriteFilter = effectiveFavoriteFilter,
                    favoriteMediaIds = favoriteMediaIds,
                ),
            )
            .orEmpty()
    }
    val filtersAreDefault = searchText.isBlank() &&
        sort == LibraryCatalogSort.TITLE &&
        subtitleFilter == LibrarySubtitleFilter.ANY &&
        effectiveFavoriteFilter == LibraryFavoriteFilter.ANY
    val visibleSeries = remember(series, filteredEpisodes, filtersAreDefault) {
        val visibleTitles = filteredEpisodes.mapTo(mutableSetOf()) { it.seriesTitle.trim() }
        if (visibleTitles.isEmpty() && filtersAreDefault) {
            series
        } else {
            series.filter { it.title in visibleTitles }
        }
    }
    val selectedInspectorSeries = selectedEpisodeDetail?.series ?: selectedSeries
    val selectedInspectorItem = selectedEpisodeDetail?.mediaItem
        ?: selectedLocalPlaybackPreparation?.item
        ?: selectedSeries?.let { nextPlayableEpisode(it, watchStatusById) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactWorkspace = maxWidth < 1380.dp
        val railWidth = if (compactWorkspace) 204.dp else 230.dp
        val inspectorWidth = if (compactWorkspace) 304.dp else 340.dp
        val panelGap = if (compactWorkspace) 10.dp else 14.dp
        val workspaceMinHeight = if (compactWorkspace) 660.dp else 720.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = workspaceMinHeight),
            horizontalArrangement = Arrangement.spacedBy(panelGap),
        ) {
            LibraryWorkspaceRail(
                selectedView = selectedView,
                registeredRoots = registeredRoots,
                localEpisodeCount = catalog?.items?.size ?: 0,
                nextUpCount = nextUpItems.size,
                continueWatchingCount = continueWatchingItems.size,
                recentlyWatchedCount = recentlyWatchedItems.size,
                favoriteCount = favoriteMediaIds.size,
                seriesCount = series.size,
                isIndexing = isIndexing,
                libraryError = libraryError,
                lastScanStats = lastScanStats,
                compact = compactWorkspace,
                onSelectView = { selectedView = it },
                onAddLibraryFolder = onAddLibraryFolder,
                onRescanRegisteredRoots = onRescanRegisteredRoots,
                modifier = Modifier.width(railWidth),
            )
            LibraryCenterWorkspace(
                selectedView = selectedView,
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                sort = sort,
                onSortChange = { sort = it },
                subtitleFilter = subtitleFilter,
                onToggleSubtitleFilter = {
                    subtitleFilter = if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                        LibrarySubtitleFilter.WITH_SUBTITLES
                    } else {
                        LibrarySubtitleFilter.ANY
                    }
                },
                favoriteFilter = favoriteFilter,
                onToggleFavoriteFilter = {
                    favoriteFilter = if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                        LibraryFavoriteFilter.FAVORITES_ONLY
                    } else {
                        LibraryFavoriteFilter.ANY
                    }
                },
                catalog = catalog,
                visibleSeries = visibleSeries,
                selectedSeries = selectedSeries,
                filteredEpisodes = filteredEpisodes,
                coverBySeriesId = seriesPosterById,
                continueWatchingItems = continueWatchingItems,
                nextUpItems = nextUpItems,
                recentlyWatchedItems = recentlyWatchedItems,
                watchStatusById = watchStatusById,
                seriesWatchSummaryById = seriesWatchSummaryById,
                favoriteMediaIds = favoriteMediaIds,
                isPreparing = isPreparing,
                compact = compactWorkspace,
                onSelectSeries = onSelectSeries,
                onShowDetails = onShowDetails,
                onSetFavorite = onSetFavorite,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
                onResetFilters = {
                    searchText = ""
                    sort = LibraryCatalogSort.TITLE
                    subtitleFilter = LibrarySubtitleFilter.ANY
                    favoriteFilter = LibraryFavoriteFilter.ANY
                },
                remoteBrowser = remoteBrowser,
                modifier = Modifier.weight(1f),
            )
            LibraryInspectorPane(
                selectedSeries = selectedInspectorSeries,
                selectedEpisodeDetail = selectedEpisodeDetail,
                selectedItem = selectedInspectorItem,
                selectedLocalPlaybackPreparation = selectedLocalPlaybackPreparation,
                dandanplayCacheStatus = dandanplayCacheStatus,
                autoNextLocalPlayback = autoNextLocalPlayback,
                coverPath = selectedInspectorSeries?.let { seriesPosterById[it.id] },
                watchSummary = selectedInspectorSeries?.let { seriesWatchSummaryById[it.id] },
                watchStatusById = watchStatusById,
                favoriteMediaIds = favoriteMediaIds,
                isPreparing = isPreparing,
                compact = compactWorkspace,
                onShowDetails = onShowDetails,
                onSetFavorite = onSetFavorite,
                onSetAutoNextLocalPlayback = onSetAutoNextLocalPlayback,
                onRefreshDandanplay = onRefreshDandanplay,
                onSelectDandanplayMatch = onSelectDandanplayMatch,
                onClearDandanplayCache = onClearDandanplayCache,
                onClearDanmakuOverlay = onClearDanmakuOverlay,
                onAttachManualDanmaku = onAttachManualDanmaku,
                onLoadPreparedPlayback = onLoadPreparedPlayback,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
                modifier = Modifier.width(inspectorWidth),
            )
        }
    }
}

@Composable
private fun LibraryWorkspaceRail(
    selectedView: WindowsLibraryView,
    registeredRoots: List<DesktopLibraryRoot>,
    localEpisodeCount: Int,
    nextUpCount: Int,
    continueWatchingCount: Int,
    recentlyWatchedCount: Int,
    favoriteCount: Int,
    seriesCount: Int,
    isIndexing: Boolean,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    compact: Boolean,
    onSelectView: (WindowsLibraryView) -> Unit,
    onAddLibraryFolder: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier.fillMaxHeight()) {
        Text("Danmaku", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        if (!compact) {
            Text("Library host", color = DanmakuColors.TextMuted, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LibraryRailNavigationItem(
            icon = Icons.Filled.History,
            label = WindowsLibraryView.CONTINUE_WATCHING.label,
            count = continueWatchingCount,
            selected = selectedView == WindowsLibraryView.CONTINUE_WATCHING,
            onClick = { onSelectView(WindowsLibraryView.CONTINUE_WATCHING) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.PlayArrow,
            label = WindowsLibraryView.NEXT_UP.label,
            count = nextUpCount,
            selected = selectedView == WindowsLibraryView.NEXT_UP,
            onClick = { onSelectView(WindowsLibraryView.NEXT_UP) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.GridView,
            label = WindowsLibraryView.ALL_SERIES.label,
            count = seriesCount,
            selected = selectedView == WindowsLibraryView.ALL_SERIES,
            onClick = { onSelectView(WindowsLibraryView.ALL_SERIES) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.History,
            label = WindowsLibraryView.RECENTLY_WATCHED.label,
            count = recentlyWatchedCount,
            selected = selectedView == WindowsLibraryView.RECENTLY_WATCHED,
            onClick = { onSelectView(WindowsLibraryView.RECENTLY_WATCHED) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.Star,
            label = WindowsLibraryView.FAVORITES.label,
            count = favoriteCount,
            selected = selectedView == WindowsLibraryView.FAVORITES,
            onClick = { onSelectView(WindowsLibraryView.FAVORITES) },
        )
        LibraryRailNavigationItem(
            icon = Icons.AutoMirrored.Filled.ViewList,
            label = WindowsLibraryView.FILES.label,
            count = localEpisodeCount,
            selected = selectedView == WindowsLibraryView.FILES,
            onClick = { onSelectView(WindowsLibraryView.FILES) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.Devices,
            label = WindowsLibraryView.PAIRED.label,
            count = 0,
            selected = selectedView == WindowsLibraryView.PAIRED,
            onClick = { onSelectView(WindowsLibraryView.PAIRED) },
        )
        Divider(color = DanmakuColors.SurfaceRaised)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerIconButton(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add library folder",
                enabled = !isIndexing,
                onClick = onAddLibraryFolder,
            )
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Rescan folders",
                enabled = registeredRoots.isNotEmpty() && !isIndexing,
                onClick = onRescanRegisteredRoots,
            )
        }
        LibrarySourceStatus(
            icon = Icons.Filled.Computer,
            label = "Local PC",
            value = if (isIndexing) "Indexing..." else "$localEpisodeCount episodes",
            statusColor = if (libraryError == null) DanmakuColors.Good else DanmakuColors.Warning,
        )
        if (!compact) {
            LibrarySourceStatus(
                icon = Icons.Filled.Devices,
                label = "Paired devices",
                value = "LAN browser ready",
                statusColor = DanmakuColors.Accent,
            )
        }
        libraryError?.let { error ->
            Text(error, color = DanmakuColors.Warning, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        lastScanStats?.let { stats ->
            Text(
                "Last scan: ${stats.reusedItemCount} unchanged, ${stats.refreshedItemCount} refreshed",
                color = DanmakuColors.TextMuted,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        Text("Folders", color = DanmakuColors.TextMuted, fontWeight = FontWeight.Bold)
        if (registeredRoots.isEmpty()) {
            Text("No folders yet", color = DanmakuColors.TextMuted)
        } else {
            val visibleRootCount = if (compact) 3 else 5
            registeredRoots.take(visibleRootCount).forEach { root ->
                Text(root.displayName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (registeredRoots.size > visibleRootCount) {
                Text("+${registeredRoots.size - visibleRootCount} more", color = DanmakuColors.TextMuted)
            }
        }
    }
}

@Composable
private fun LibraryRailNavigationItem(
    icon: ImageVector,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Color.White else DanmakuColors.TextMuted)
        Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(count.toString(), color = DanmakuColors.TextMuted, maxLines = 1)
        }
    }
}

@Composable
private fun LibrarySourceStatus(
    icon: ImageVector,
    label: String,
    value: String,
    statusColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = DanmakuColors.TextMuted)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, maxLines = 1)
            Text(value, color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LibraryCenterWorkspace(
    selectedView: WindowsLibraryView,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    sort: LibraryCatalogSort,
    onSortChange: (LibraryCatalogSort) -> Unit,
    subtitleFilter: LibrarySubtitleFilter,
    onToggleSubtitleFilter: () -> Unit,
    favoriteFilter: LibraryFavoriteFilter,
    onToggleFavoriteFilter: () -> Unit,
    catalog: LibraryCatalog?,
    visibleSeries: List<LibrarySeries>,
    selectedSeries: LibrarySeries?,
    filteredEpisodes: List<LibraryMediaItem>,
    coverBySeriesId: Map<String, Path?>,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    recentlyWatchedItems: List<LibraryPlaybackProgressItem>,
    watchStatusById: Map<String, LibraryWatchStatus>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    favoriteMediaIds: Set<String>,
    isPreparing: Boolean,
    compact: Boolean,
    onSelectSeries: (LibrarySeries) -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    onResetFilters: () -> Unit,
    remoteBrowser: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier.fillMaxHeight()) {
        if (selectedView == WindowsLibraryView.PAIRED) {
            Text("Paired Library", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            Text("Browse another trusted desktop server without leaving the library workspace.", color = DanmakuColors.TextMuted)
            Divider(color = DanmakuColors.SurfaceRaised)
            remoteBrowser()
            return@WorkspacePanel
        }

        LibraryWorkspaceToolbar(
            selectedView = selectedView,
            searchText = searchText,
            onSearchTextChange = onSearchTextChange,
            sort = sort,
            onSortChange = onSortChange,
            subtitleFilter = subtitleFilter,
            onToggleSubtitleFilter = onToggleSubtitleFilter,
            favoriteFilter = favoriteFilter,
            onToggleFavoriteFilter = onToggleFavoriteFilter,
            compact = compact,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("${filteredEpisodes.size} / ${catalog?.items?.size ?: 0} episodes")
            StatusPill("${favoriteMediaIds.size} favorites")
            if (!compact) {
                selectedSeries?.let { StatusPill(it.title) }
            }
        }
        when (selectedView) {
            WindowsLibraryView.CONTINUE_WATCHING -> ContinueWatchingList(
                items = continueWatchingItems,
                isPreparing = isPreparing,
                compact = compact,
                onShowDetails = onShowDetails,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.NEXT_UP -> NextUpList(
                items = nextUpItems,
                isPreparing = isPreparing,
                compact = compact,
                onShowDetails = onShowDetails,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.RECENTLY_WATCHED -> RecentlyWatchedList(
                items = recentlyWatchedItems,
                isPreparing = isPreparing,
                compact = compact,
                onShowDetails = onShowDetails,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.FAVORITES,
            WindowsLibraryView.FILES -> EpisodeListView(
                episodes = filteredEpisodes,
                watchStatusById = watchStatusById,
                favoriteMediaIds = favoriteMediaIds,
                isPreparing = isPreparing,
                emptyText = if (selectedView == WindowsLibraryView.FAVORITES) {
                    "No favorite episodes match the current filters."
                } else {
                    "No episodes match the current filters."
                },
                compact = compact,
                onResetFilters = onResetFilters,
                onShowDetails = onShowDetails,
                onSetFavorite = onSetFavorite,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.ALL_SERIES,
            WindowsLibraryView.PAIRED -> AllSeriesView(
                catalog = catalog,
                visibleSeries = visibleSeries,
                selectedSeries = selectedSeries,
                coverBySeriesId = coverBySeriesId,
                continueWatchingItems = continueWatchingItems,
                nextUpItems = nextUpItems,
                watchStatusById = watchStatusById,
                seriesWatchSummaryById = seriesWatchSummaryById,
                isPreparing = isPreparing,
                compact = compact,
                onResetFilters = onResetFilters,
                onSelectSeries = onSelectSeries,
                onShowDetails = onShowDetails,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
        }
    }
}

@Composable
private fun LibraryWorkspaceToolbar(
    selectedView: WindowsLibraryView,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    sort: LibraryCatalogSort,
    onSortChange: (LibraryCatalogSort) -> Unit,
    subtitleFilter: LibrarySubtitleFilter,
    onToggleSubtitleFilter: () -> Unit,
    favoriteFilter: LibraryFavoriteFilter,
    onToggleFavoriteFilter: () -> Unit,
    compact: Boolean,
) {
    @Composable
    fun ToolbarActions() {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerIconButton(
                imageVector = Icons.Filled.FilterList,
                contentDescription = if (subtitleFilter == LibrarySubtitleFilter.ANY) {
                    "Require subtitles"
                } else {
                    "Show all subtitles"
                },
                onClick = onToggleSubtitleFilter,
            )
            PlayerIconButton(
                imageVector = Icons.Filled.Star,
                contentDescription = if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                    "Favorites only"
                } else {
                    "Show all favorites"
                },
                onClick = onToggleFavoriteFilter,
            )
            PlayerIconButton(
                imageVector = if (sort == LibraryCatalogSort.TITLE) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = if (sort == LibraryCatalogSort.TITLE) "Sort by path" else "Sort by title",
                onClick = {
                    onSortChange(
                        if (sort == LibraryCatalogSort.TITLE) {
                            LibraryCatalogSort.PATH
                        } else {
                            LibraryCatalogSort.TITLE
                        },
                    )
                },
            )
        }
    }

    @Composable
    fun LibrarySearchField(modifier: Modifier) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            label = { Text("Search anime, episode, path") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            singleLine = true,
            modifier = modifier,
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackSearch = compact || maxWidth < 760.dp
        if (stackSearch) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedView.label, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                        Text("Search and filter without leaving context.", color = DanmakuColors.TextMuted, maxLines = 1)
                    }
                    ToolbarActions()
                }
                LibrarySearchField(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(selectedView.label, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                    Text("Search, filter, and select media without leaving context.", color = DanmakuColors.TextMuted)
                }
                LibrarySearchField(Modifier.width(360.dp))
                ToolbarActions()
            }
        }
    }
}

@Composable
private fun AllSeriesView(
    catalog: LibraryCatalog?,
    visibleSeries: List<LibrarySeries>,
    selectedSeries: LibrarySeries?,
    coverBySeriesId: Map<String, Path?>,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    watchStatusById: Map<String, LibraryWatchStatus>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    isPreparing: Boolean,
    compact: Boolean,
    onResetFilters: () -> Unit,
    onSelectSeries: (LibrarySeries) -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    LibraryProgressOverview(
        continueWatchingItems = continueWatchingItems,
        nextUpItems = nextUpItems,
        isPreparing = isPreparing,
        compact = compact,
        onShowDetails = onShowDetails,
        onPlayLocalPlayback = onPlayLocalPlayback,
    )
    Text("All Series", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
    when {
        catalog == null || catalog.items.isEmpty() -> EmptyState("No indexed series yet. Add a folder and scan to build the cover library.")
        visibleSeries.isEmpty() -> EmptyState(
            text = "No series match the current filters.",
            actionLabel = "Reset filters",
            onAction = onResetFilters,
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (compact) 132.dp else 150.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 460.dp else 500.dp)
                .libraryCollectionKeyboard(
                    itemCount = visibleSeries.size,
                    selectedIndex = visibleSeries.indexOfFirst { it.id == selectedSeries?.id }.coerceAtLeast(0),
                    columnStride = if (compact) 4 else 6,
                    onSelectedIndexChange = { index -> visibleSeries.getOrNull(index)?.let(onSelectSeries) },
                    onOpenSelected = {
                        visibleSeries
                            .getOrNull(visibleSeries.indexOfFirst { it.id == selectedSeries?.id }.coerceAtLeast(0))
                            ?.let(onSelectSeries)
                    },
                    onPlaySelected = {
                        visibleSeries
                            .getOrNull(visibleSeries.indexOfFirst { it.id == selectedSeries?.id }.coerceAtLeast(0))
                            ?.let { librarySeries ->
                                onPlayLocalPlayback(
                                    nextPlayableEpisode(
                                        librarySeries = librarySeries,
                                        watchStatusById = watchStatusById,
                                    ),
                                )
                            }
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 20.dp),
        ) {
            items(visibleSeries, key = { it.id }) { librarySeries ->
                SeriesPosterCard(
                    series = librarySeries,
                    coverPath = coverBySeriesId[librarySeries.id],
                    watchSummary = seriesWatchSummaryById[librarySeries.id],
                    isSelected = librarySeries.id == selectedSeries?.id,
                    isPreparing = isPreparing,
                    onSelect = { onSelectSeries(librarySeries) },
                    onPlay = {
                        onPlayLocalPlayback(
                            nextPlayableEpisode(
                                librarySeries = librarySeries,
                                watchStatusById = watchStatusById,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryProgressOverview(
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val cards = buildList {
        continueWatchingItems.take(2).forEach { item ->
            add(
                LibraryProgressCardModel(
                    title = item.mediaItem.seriesTitle,
                    subtitle = item.mediaItem.episodeTitle,
                    detail = "Resume at ${item.progress.positionMs.formatPlaybackTime()}",
                    progressPercent = item.progress.progressPercent(),
                    actionLabel = "Resume",
                    mediaItem = item.mediaItem,
                ),
            )
        }
        nextUpItems.take(2).forEach { item ->
            add(
                LibraryProgressCardModel(
                    title = item.mediaItem.seriesTitle,
                    subtitle = item.mediaItem.episodeTitle,
                    detail = item.nextUpLabel(),
                    progressPercent = item.progress?.progressPercent(),
                    actionLabel = item.nextUpActionLabel(),
                    mediaItem = item.mediaItem,
                ),
            )
        }
    }.distinctBy { it.mediaItem.id }.take(if (compact) 3 else 4)
    if (cards.isEmpty()) return

    Text("Continue Watching", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cards.forEach { card ->
            LibraryProgressCard(
                card = card,
                isPreparing = isPreparing,
                onShowDetails = onShowDetails,
                onPlayLocalPlayback = onPlayLocalPlayback,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private data class LibraryProgressCardModel(
    val title: String,
    val subtitle: String,
    val detail: String,
    val progressPercent: Int?,
    val actionLabel: String,
    val mediaItem: LibraryMediaItem,
)

@Composable
private fun LibraryProgressCard(
    card: LibraryProgressCardModel,
    isPreparing: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.72f))
            .clickable { onShowDetails(card.mediaItem) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(card.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(card.subtitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        MiniProgressBar(percent = card.progressPercent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Button(
                onClick = { onPlayLocalPlayback(card.mediaItem) },
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Loading..." else card.actionLabel)
            }
        }
    }
}

@Composable
private fun ContinueWatchingList(
    items: List<LibraryPlaybackProgressItem>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState("No in-progress local episodes yet.")
    } else {
        var selectedIndex by remember(items) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = items.size,
                    selectedIndex = boundedSelectedIndex,
                    onSelectedIndexChange = { selectedIndex = it },
                    onOpenSelected = { items.getOrNull(boundedSelectedIndex)?.mediaItem?.let(onShowDetails) },
                    onPlaySelected = { items.getOrNull(boundedSelectedIndex)?.mediaItem?.let(onPlayLocalPlayback) },
                ),
        ) {
            items(items, key = { it.mediaItem.id }) { item ->
                ContinueWatchingRow(
                    item = item,
                    selected = items.indexOf(item) == boundedSelectedIndex,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = onShowDetails,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
private fun NextUpList(
    items: List<LibraryNextUpItem>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState("No next-up item yet. Start watching from All Series.")
    } else {
        var selectedIndex by remember(items) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = items.size,
                    selectedIndex = boundedSelectedIndex,
                    onSelectedIndexChange = { selectedIndex = it },
                    onOpenSelected = { items.getOrNull(boundedSelectedIndex)?.mediaItem?.let(onShowDetails) },
                    onPlaySelected = { items.getOrNull(boundedSelectedIndex)?.mediaItem?.let(onPlayLocalPlayback) },
                ),
        ) {
            items(items, key = { it.mediaItem.id }) { item ->
                NextUpRow(
                    item = item,
                    selected = items.indexOf(item) == boundedSelectedIndex,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = onShowDetails,
                    onPrepareLocalPlayback = onPrepareLocalPlayback,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
private fun RecentlyWatchedList(
    items: List<LibraryPlaybackProgressItem>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState("No recently watched local episodes yet.")
    } else {
        var selectedIndex by remember(items) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = items.size,
                    selectedIndex = boundedSelectedIndex,
                    onSelectedIndexChange = { selectedIndex = it },
                    onOpenSelected = { items.getOrNull(boundedSelectedIndex)?.mediaItem?.let(onShowDetails) },
                    onPlaySelected = { items.getOrNull(boundedSelectedIndex)?.mediaItem?.let(onPlayLocalPlayback) },
                ),
        ) {
            items(items, key = { it.mediaItem.id }) { item ->
                RecentlyWatchedRow(
                    item = item,
                    selected = items.indexOf(item) == boundedSelectedIndex,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = onShowDetails,
                    onPrepareLocalPlayback = onPrepareLocalPlayback,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
private fun EpisodeListView(
    episodes: List<LibraryMediaItem>,
    watchStatusById: Map<String, LibraryWatchStatus>,
    favoriteMediaIds: Set<String>,
    isPreparing: Boolean,
    emptyText: String,
    compact: Boolean,
    onResetFilters: () -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (episodes.isEmpty()) {
        EmptyState(
            text = emptyText,
            actionLabel = "Reset filters",
            onAction = onResetFilters,
        )
    } else {
        var selectedIndex by remember(episodes) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, episodes.lastIndex)
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = episodes.size,
                    selectedIndex = boundedSelectedIndex,
                    onSelectedIndexChange = { selectedIndex = it },
                    onOpenSelected = { episodes.getOrNull(boundedSelectedIndex)?.let(onShowDetails) },
                    onPlaySelected = { episodes.getOrNull(boundedSelectedIndex)?.let(onPlayLocalPlayback) },
                ),
        ) {
            items(episodes, key = { it.id }) { item ->
                EpisodeRow(
                    item = item,
                    selected = episodes.indexOf(item) == boundedSelectedIndex,
                    watchStatus = watchStatusById[item.id],
                    isFavorite = item.id in favoriteMediaIds,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = onShowDetails,
                    onSetFavorite = onSetFavorite,
                    onPrepareLocalPlayback = onPrepareLocalPlayback,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
private fun LibraryInspectorPane(
    selectedSeries: LibrarySeries?,
    selectedEpisodeDetail: LibraryEpisodeDetail?,
    selectedItem: LibraryMediaItem?,
    selectedLocalPlaybackPreparation: DesktopLocalPlaybackPreparation?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    autoNextLocalPlayback: Boolean,
    coverPath: Path?,
    watchSummary: LibrarySeriesWatchSummary?,
    watchStatusById: Map<String, LibraryWatchStatus>,
    favoriteMediaIds: Set<String>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onSetAutoNextLocalPlayback: (Boolean) -> Unit,
    onRefreshDandanplay: (DesktopLocalPlaybackPreparation) -> Unit,
    onSelectDandanplayMatch: (DesktopLocalPlaybackPreparation, DandanplayMatch) -> Unit,
    onClearDandanplayCache: (DesktopLocalPlaybackPreparation) -> Unit,
    onClearDanmakuOverlay: (DesktopLocalPlaybackPreparation) -> Unit,
    onAttachManualDanmaku: (DesktopLocalPlaybackPreparation) -> Unit,
    onLoadPreparedPlayback: (DesktopLocalPlaybackPreparation) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier.fillMaxHeight()) {
        if (selectedSeries == null || selectedItem == null) {
            Text("Inspector", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            EmptyState("Select a series or episode to inspect playback, subtitles, and danmaku readiness.")
            return@WorkspacePanel
        }
        val activePreparation = selectedLocalPlaybackPreparation?.takeIf { it.item.id == selectedItem.id }
        val isFavorite = selectedItem.id in favoriteMediaIds
        val status = dandanplayCacheStatus?.takeIf { it.mediaId == selectedItem.id }
        val hasDanmakuOverlay = activePreparation?.subtitles?.any(DesktopPlaybackSubtitle::isDanmakuOverlay) == true
        var episodeActionsExpanded by remember(selectedItem.id, activePreparation?.item?.id) { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 166.dp else 210.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DanmakuColors.SurfaceRaised),
        ) {
            SeriesPosterImage(
                coverPath = coverPath,
                title = selectedSeries.title,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                watchSummary.progressLabel(),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(topEnd = 6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                maxLines = 1,
            )
        }
        Text(selectedSeries.title, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            selectedEpisodeDetail?.mediaItem?.episodeTitle ?: "Next playable: ${selectedItem.episodeTitle}",
            color = DanmakuColors.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        MiniProgressBar(percent = watchStatusById[selectedItem.id]?.progress?.progressPercent())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onPlayLocalPlayback(selectedItem) },
                enabled = !isPreparing,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isPreparing) "Loading..." else selectedItem.primaryPlaybackActionLabel(watchStatusById[selectedItem.id]))
            }
            Button(
                onClick = { onPrepareLocalPlayback(selectedItem) },
                enabled = !isPreparing,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isPreparing) "Preparing..." else if (compact) "Prep" else "Prepare")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerIconButton(
                imageVector = Icons.Filled.Star,
                contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                onClick = { onSetFavorite(selectedItem, !isFavorite) },
            )
            PlayerIconButton(
                imageVector = Icons.Filled.Subtitles,
                contentDescription = "Show episode details",
                onClick = { onShowDetails(selectedItem) },
            )
            Box {
                PlayerIconButton(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = "More episode actions",
                    onClick = { episodeActionsExpanded = true },
                )
                DropdownMenu(
                    expanded = episodeActionsExpanded,
                    onDismissRequest = { episodeActionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        enabled = !isPreparing,
                        onClick = {
                            episodeActionsExpanded = false
                            onPrepareLocalPlayback(selectedItem)
                        },
                    ) {
                        Text("Prepare playback")
                    }
                    activePreparation?.let { preparation ->
                        DropdownMenuItem(
                            onClick = {
                                episodeActionsExpanded = false
                                onLoadPreparedPlayback(preparation)
                            },
                        ) {
                            Text("Load into player")
                        }
                        DropdownMenuItem(
                            enabled = !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onRefreshDandanplay(preparation)
                            },
                        ) {
                            Text("Refresh danmaku")
                        }
                        DropdownMenuItem(
                            enabled = !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onAttachManualDanmaku(preparation)
                            },
                        ) {
                            Text("Attach local danmaku")
                        }
                        DropdownMenuItem(
                            enabled = hasDanmakuOverlay && !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onClearDanmakuOverlay(preparation)
                            },
                        ) {
                            Text("Remove overlay")
                        }
                        DropdownMenuItem(
                            enabled = !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onClearDandanplayCache(preparation)
                            },
                        ) {
                            Text("Clear danmaku cache")
                        }
                    }
                    DropdownMenuItem(
                        onClick = {
                            episodeActionsExpanded = false
                            onSetAutoNextLocalPlayback(!autoNextLocalPlayback)
                        },
                    ) {
                        Text(if (autoNextLocalPlayback) "Disable auto-next" else "Enable auto-next")
                    }
                }
            }
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        Text("Readiness", fontWeight = FontWeight.Bold)
        InspectorStatusRow(
            icon = if (activePreparation != null) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            label = if (activePreparation != null) "Prepared playback" else "Prepare to inspect tracks",
            value = activePreparation?.resumePositionMs?.let { "Resume ${it.formatPlaybackTime()}" } ?: "Not prepared",
            color = if (activePreparation != null) DanmakuColors.Good else DanmakuColors.TextMuted,
        )
        InspectorStatusRow(
            icon = if (status?.summary?.isDandanplayWarningStatus() == true) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            label = "Danmaku",
            value = status?.summary ?: "Not checked yet",
            color = when {
                status == null -> DanmakuColors.TextMuted
                status.summary.isDandanplayWarningStatus() -> DanmakuColors.Warning
                else -> DanmakuColors.Good
            },
        )
        InspectorStatusRow(
            icon = Icons.Filled.Subtitles,
            label = "Subtitles",
            value = "${selectedItem.subtitles.size} indexed",
            color = if (selectedItem.subtitles.isNotEmpty()) DanmakuColors.Good else DanmakuColors.TextMuted,
        )
        selectedEpisodeDetail?.let { detail ->
            MetadataRow("Season", detail.season.label)
            MetadataRow("Watch", detail.watchStatus.statusLabel())
            MetadataRow("Size", detail.mediaItem.sizeBytes.formatLibrarySize())
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        Text("Episodes", fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
            selectedSeries.seasons.forEach { season ->
                item(key = season.id) {
                    Text(season.label, color = DanmakuColors.TextMuted, fontWeight = FontWeight.Bold)
                }
                items(season.items, key = { it.id }) { item ->
                    CompactInspectorEpisodeRow(
                        item = item,
                        selected = item.id == selectedItem.id,
                        watchStatus = watchStatusById[item.id],
                        onClick = { onShowDetails(item) },
                    )
                }
            }
        }
        activePreparation?.let { preparation ->
            Divider(color = DanmakuColors.SurfaceRaised)
            Text("Advanced", fontWeight = FontWeight.Bold)
            Button(onClick = { onLoadPreparedPlayback(preparation) }, modifier = Modifier.fillMaxWidth()) {
                Text("Load into player")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onRefreshDandanplay(preparation) },
                    enabled = !isPreparing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Refresh danmaku")
                }
                Button(
                    onClick = { onAttachManualDanmaku(preparation) },
                    enabled = !isPreparing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Attach local")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onClearDanmakuOverlay(preparation) },
                    enabled = hasDanmakuOverlay && !isPreparing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Remove overlay")
                }
                Button(
                    onClick = { onClearDandanplayCache(preparation) },
                    enabled = !isPreparing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Clear cache")
                }
            }
            Button(
                onClick = { onSetAutoNextLocalPlayback(!autoNextLocalPlayback) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (autoNextLocalPlayback) "Auto-next on" else "Auto-next off")
            }
            status?.details?.forEach { detail ->
                MetadataRow(detail.label, detail.value)
            }
            status?.let {
                DandanplayMatchCandidatePicker(
                    preparation = preparation,
                    status = it,
                    isPreparing = isPreparing,
                    onSelectDandanplayMatch = onSelectDandanplayMatch,
                )
            }
        }
    }
}

@Composable
private fun InspectorStatusRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.62f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactInspectorEpisodeRow(
    item: LibraryMediaItem,
    selected: Boolean,
    watchStatus: LibraryWatchStatus?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) DanmakuColors.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(item.episodeTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(watchStatus.statusLabel(), color = DanmakuColors.TextMuted, maxLines = 1)
    }
}

@Composable
private fun MiniProgressBar(
    percent: Int?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((percent ?: 0).coerceIn(0, 100) / 100f)
                .fillMaxHeight()
                .background(DanmakuColors.Accent),
        )
    }
}

private fun Modifier.libraryCollectionKeyboard(
    itemCount: Int,
    selectedIndex: Int,
    columnStride: Int = 1,
    onSelectedIndexChange: (Int) -> Unit,
    onOpenSelected: () -> Unit,
    onPlaySelected: () -> Unit,
): Modifier {
    if (itemCount <= 0) return this
    val boundedSelectedIndex = selectedIndex.coerceIn(0, itemCount - 1)
    fun moveTo(index: Int) {
        onSelectedIndexChange(index.coerceIn(0, itemCount - 1))
    }
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionUp -> {
                    moveTo(boundedSelectedIndex - columnStride)
                    true
                }
                Key.DirectionDown -> {
                    moveTo(boundedSelectedIndex + columnStride)
                    true
                }
                Key.DirectionLeft -> {
                    if (columnStride > 1) {
                        moveTo(boundedSelectedIndex - 1)
                        true
                    } else {
                        false
                    }
                }
                Key.DirectionRight -> {
                    if (columnStride > 1) {
                        moveTo(boundedSelectedIndex + 1)
                        true
                    } else {
                        false
                    }
                }
                Key.Enter -> {
                    onOpenSelected()
                    true
                }
                Key.Spacebar -> {
                    onPlaySelected()
                    true
                }
                else -> false
            }
        }
    }.focusable()
}

@Composable
private fun WorkspacePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

private fun PlaybackProgress.progressPercent(): Int? =
    durationMs
        ?.takeIf { it > 0L }
        ?.let { duration -> ((positionMs.coerceAtLeast(0L) * 100L) / duration).coerceIn(0L, 100L).toInt() }

private fun LibraryMediaItem.primaryPlaybackActionLabel(watchStatus: LibraryWatchStatus?): String =
    if (watchStatus?.state == LibraryWatchState.IN_PROGRESS) {
        "Resume"
    } else {
        "Play"
    }

@Composable
private fun SeriesPosterCard(
    series: LibrarySeries,
    coverPath: Path?,
    watchSummary: LibrarySeriesWatchSummary?,
    isSelected: Boolean,
    isPreparing: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) DanmakuColors.SurfaceRaised else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.70f)
                .clip(RoundedCornerShape(6.dp))
                .background(DanmakuColors.SurfaceRaised),
        ) {
            SeriesPosterImage(
                coverPath = coverPath,
                title = series.title,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = "${watchSummary?.watchedCount ?: 0}/${series.episodeCount}",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(topEnd = 4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                maxLines = 1,
            )
        }
        Text(
            text = series.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = watchSummary.progressLabel(),
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Button(
            onClick = onPlay,
            enabled = !isPreparing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isPreparing) "Loading..." else "Play")
        }
    }
}

@Composable
private fun SeriesPosterImage(
    coverPath: Path?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberLocalImageBitmap(coverPath)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(DanmakuColors.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.initialsForPoster(),
                color = Color.White,
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun rememberLocalImageBitmap(path: Path?): ImageBitmap? =
    remember(path) {
        path
            ?.takeIf(Files::isRegularFile)
            ?.let { imagePath ->
                runCatching {
                    SkiaImage.makeFromEncoded(Files.readAllBytes(imagePath)).toComposeImageBitmap()
                }.getOrNull()
            }
    }

private fun nextPlayableEpisode(
    librarySeries: LibrarySeries,
    watchStatusById: Map<String, LibraryWatchStatus>,
): LibraryMediaItem {
    val items = librarySeries.seasons.flatMap { it.items }
    return items.firstOrNull { watchStatusById[it.id]?.state == LibraryWatchState.IN_PROGRESS }
        ?: items.firstOrNull { watchStatusById[it.id]?.state != LibraryWatchState.WATCHED }
        ?: items.first()
}

private fun findSeriesCoverImage(
    series: LibrarySeries,
    indexedLibrary: IndexedLocalLibrary?,
): Path? {
    val firstItem = series.seasons.firstOrNull()?.items?.firstOrNull() ?: return null
    val firstMediaPath = indexedLibrary?.filesById?.get(firstItem.id) ?: return null
    return findCoverImageNear(firstMediaPath)
}

private fun loadSeriesPosterById(
    indexedLibrary: IndexedLocalLibrary?,
    metadataResolver: DesktopAnimeMetadataResolver,
): Map<String, Path?> {
    val library = indexedLibrary ?: return emptyMap()
    return library.catalog.groupedSeries().associate { series ->
        series.id to (
            findSeriesCoverImage(series, library)
                ?: metadataResolver.cachedPosterForSeries(series)
            )
    }
}

private fun findCoverImageNear(mediaPath: Path): Path? {
    val directories = sequenceOf(mediaPath.parent, mediaPath.parent?.parent)
        .filterNotNull()
        .distinct()
        .toList()
    return directories.firstNotNullOfOrNull(::findPreferredCoverImage)
}

private fun findPreferredCoverImage(directory: Path): Path? {
    if (!Files.isDirectory(directory)) return null
    val images = Files.list(directory).use { paths ->
        paths
            .filter(Files::isRegularFile)
            .filter { it.fileName.toString().substringAfterLast('.', "").lowercase() in COVER_IMAGE_EXTENSIONS }
            .sorted(compareBy<Path> { coverImageRank(it.fileName.toString()) }.thenBy { it.fileName.toString().lowercase() })
            .toList()
    }
    return images.firstOrNull()
}

private fun coverImageRank(fileName: String): Int {
    val baseName = fileName.substringBeforeLast('.').lowercase()
    return COVER_IMAGE_NAMES.indexOf(baseName).takeIf { it >= 0 } ?: Int.MAX_VALUE
}

private fun String.initialsForPoster(): String =
    split(Regex("""\s+"""))
        .filter(String::isNotBlank)
        .take(2)
        .joinToString(separator = "") { it.first().uppercaseChar().toString() }
        .ifBlank { take(1).uppercase(Locale.US) }

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
    danmakuSettings: DanmakuDisplaySettings,
    onSaveDanmakuSettings: (DanmakuDisplaySettings) -> Unit,
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
        SectionCard("Desktop Runtime") {
            MetadataRow("mpv executor", mpvRuntimeStatus)
            MetadataRow("Video host", videoHostStatus)
            MetadataRow("Renderer", "mpv video output with generated ASS danmaku overlay")
            MetadataRow("App log", appLogPath.toString())
            MetadataRow("mpv log", mpvLogPath.toString())
        }
        DanmakuDisplaySettingsCard(
            settings = danmakuSettings,
            onSave = onSaveDanmakuSettings,
        )
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
private fun DanmakuDisplaySettingsCard(
    settings: DanmakuDisplaySettings,
    onSave: (DanmakuDisplaySettings) -> Unit,
) {
    var visible by remember(settings) { mutableStateOf(settings.visible) }
    var opacityText by remember(settings) { mutableStateOf(settings.opacityPercent.toString()) }
    var fontScaleText by remember(settings) { mutableStateOf(settings.fontScalePercent.toString()) }
    var speedText by remember(settings) { mutableStateOf(settings.speedPercent.toString()) }
    var densityText by remember(settings) { mutableStateOf(settings.densityPercent.toString()) }
    var displayAreaText by remember(settings) { mutableStateOf(settings.displayAreaPercent.toString()) }
    var offsetText by remember(settings) { mutableStateOf(settings.offsetMs.toString()) }
    var keywordFiltersText by remember(settings) { mutableStateOf(settings.keywordFilters.joinToString("\n")) }
    var regexFiltersText by remember(settings) { mutableStateOf(settings.regexFilters.joinToString("\n")) }

    SectionCard("Danmaku Display") {
        Text(
            "Controls generated ASS danmaku overlays for matched and manually attached comment tracks. Reload media or refresh cached danmaku to apply renderer changes.",
            color = DanmakuColors.TextMuted,
        )
        MetadataRow("Visibility", if (settings.visible) "Shown" else "Hidden")
        MetadataRow("Opacity", "${settings.opacityPercent}%")
        MetadataRow("Font scale", "${settings.fontScalePercent}%")
        MetadataRow("Speed", "${settings.speedPercent}%")
        MetadataRow("Density", "${settings.densityPercent}%")
        MetadataRow("Display area", "${settings.displayAreaPercent}%")
        MetadataRow("Offset", "${settings.offsetMs} ms")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { visible = true },
                enabled = !visible,
            ) {
                Text("Show danmaku")
            }
            Button(
                onClick = { visible = false },
                enabled = visible,
            ) {
                Text("Hide danmaku")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = opacityText,
                onValueChange = { opacityText = it },
                label = { Text("Opacity %") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = fontScaleText,
                onValueChange = { fontScaleText = it },
                label = { Text("Font scale %") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = speedText,
                onValueChange = { speedText = it },
                label = { Text("Speed %") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = densityText,
                onValueChange = { densityText = it },
                label = { Text("Density %") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = displayAreaText,
                onValueChange = { displayAreaText = it },
                label = { Text("Display area %") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = offsetText,
                onValueChange = { offsetText = it },
                label = { Text("Offset ms") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = keywordFiltersText,
                onValueChange = { keywordFiltersText = it },
                label = { Text("Keyword filters, one per line") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = regexFiltersText,
                onValueChange = { regexFiltersText = it },
                label = { Text("Regex filters, one per line") },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        DanmakuDisplaySettings(
                            visible = visible,
                            opacityPercent = opacityText.toIntOrNull()?.coerceIn(0, 100)
                                ?: settings.opacityPercent,
                            fontScalePercent = fontScaleText.toIntOrNull()?.coerceIn(50, 200)
                                ?: settings.fontScalePercent,
                            speedPercent = speedText.toIntOrNull()?.coerceIn(25, 300)
                                ?: settings.speedPercent,
                            densityPercent = densityText.toIntOrNull()?.coerceIn(10, 200)
                                ?: settings.densityPercent,
                            displayAreaPercent = displayAreaText.toIntOrNull()?.coerceIn(10, 100)
                                ?: settings.displayAreaPercent,
                            offsetMs = offsetText.toLongOrNull()?.coerceIn(-3_600_000L, 3_600_000L)
                                ?: settings.offsetMs,
                            keywordFilters = keywordFiltersText.toFilterEntries(),
                            regexFilters = regexFiltersText.toFilterEntries(),
                        ),
                    )
                },
            ) {
                Text("Save danmaku display")
            }
            Button(
                onClick = {
                    visible = true
                    opacityText = "100"
                    fontScaleText = "100"
                    speedText = "100"
                    densityText = "100"
                    displayAreaText = "100"
                    offsetText = "0"
                    keywordFiltersText = ""
                    regexFiltersText = ""
                },
            ) {
                Text("Reset draft")
            }
        }
    }
}

private fun String.toFilterEntries(): List<String> =
    lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()

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
            "dandanplay-compatible API settings for auto-match and fetched danmaku tracks.",
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
            visualTransformation = PasswordVisualTransformation(),
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
private fun EmptyState(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text, color = DanmakuColors.TextMuted)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
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
private fun DesktopSeriesRow(
    series: LibrarySeries,
    watchSummary: LibrarySeriesWatchSummary?,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) DanmakuColors.SurfaceRaised else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(series.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${series.episodeCount} episodes, ${series.seasons.size} seasons",
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            watchSummary.progressLabel(),
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${series.subtitleTrackCount} subtitle tracks, ${series.totalSizeBytes.formatLibrarySize()}",
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DandanplayMatchCandidatePicker(
    preparation: DesktopLocalPlaybackPreparation,
    status: DandanplayPlaybackUiStatus,
    isPreparing: Boolean,
    onSelectDandanplayMatch: (DesktopLocalPlaybackPreparation, DandanplayMatch) -> Unit,
) {
    val candidates = status.matchCandidates.distinctBy(DandanplayMatch::episodeId)
    if (candidates.size <= 1) return

    Spacer(modifier = Modifier.height(8.dp))
    Divider(color = DanmakuColors.SurfaceRaised)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Match candidates",
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(6.dp))
    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
        items(candidates, key = { it.episodeId }) { match ->
            val isSelected = match.episodeId == status.selectedEpisodeId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        match.displayTitle,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        dandanplayMatchCandidateDetail(match),
                        color = DanmakuColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = { onSelectDandanplayMatch(preparation, match) },
                    enabled = !isPreparing && !isSelected,
                ) {
                    Text(if (isSelected) "Selected" else "Use match")
                }
            }
        }
    }
}

private fun dandanplayMatchCandidateDetail(match: DandanplayMatch): String =
    buildList {
        add("Episode ID ${match.episodeId}")
        match.animeId?.let { add("Anime ID $it") }
        match.shiftSeconds?.let { add("Shift ${it}s") }
    }.joinToString(" / ")

private fun LibrarySeriesWatchSummary?.progressLabel(): String =
    if (this == null) {
        "0 watched, 0 watching, 0 new"
    } else {
        "$watchedCount watched, $inProgressCount watching, $newCount new"
    }

@Composable
private fun NextUpRow(
    item: LibraryNextUpItem,
    selected: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.nextUpLabel(),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = "Details",
                    onClick = { onShowDetails(item.mediaItem) },
                )
            } else {
                Button(onClick = { onShowDetails(item.mediaItem) }) {
                    Text("Details")
                }
            }
            Button(
                onClick = { onPrepareLocalPlayback(item.mediaItem) },
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Preparing..." else if (compact) "Prep" else "Prepare")
            }
            Button(
                onClick = { onPlayLocalPlayback(item.mediaItem) },
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Loading..." else item.nextUpActionLabel())
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    item: LibraryPlaybackProgressItem,
    selected: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = "Details",
                    onClick = { onShowDetails(item.mediaItem) },
                )
            } else {
                Button(onClick = { onShowDetails(item.mediaItem) }) {
                    Text("Details")
                }
            }
            Button(
                onClick = { onPlayLocalPlayback(item.mediaItem) },
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Loading..." else "Resume")
            }
        }
    }
}

@Composable
private fun RecentlyWatchedRow(
    item: LibraryPlaybackProgressItem,
    selected: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = "Details",
                    onClick = { onShowDetails(item.mediaItem) },
                )
            } else {
                Button(onClick = { onShowDetails(item.mediaItem) }) {
                    Text("Details")
                }
            }
            Button(
                onClick = { onPrepareLocalPlayback(item.mediaItem) },
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Preparing..." else if (compact) "Prep" else "Prepare")
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
    selected: Boolean,
    watchStatus: LibraryWatchStatus?,
    isFavorite: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    watchStatus.statusLabel(),
                    "Favorite".takeIf { isFavorite },
                ).joinToString(separator = " - "),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = "Details",
                    onClick = { onShowDetails(item) },
                )
                PlayerIconButton(
                    imageVector = Icons.Filled.Star,
                    contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                    onClick = { onSetFavorite(item, !isFavorite) },
                )
            } else {
                Button(onClick = { onShowDetails(item) }) {
                    Text("Details")
                }
                Button(onClick = { onSetFavorite(item, !isFavorite) }) {
                    Text(if (isFavorite) "Unfavorite" else "Favorite")
                }
            }
            Button(
                onClick = { onPrepareLocalPlayback(item) },
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Preparing..." else if (compact) "Prep" else "Prepare")
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

private fun LibraryWatchStatus?.statusLabel(): String =
    when (this?.state) {
        LibraryWatchState.WATCHED -> "Watched"
        LibraryWatchState.IN_PROGRESS -> {
            val progress = progress
            "In progress" + if (progress == null) {
                ""
            } else {
                " at ${progress.positionMs.formatPlaybackTime()} / " +
                    (progress.durationMs?.formatPlaybackTime() ?: "unknown")
            }
        }
        LibraryWatchState.NEW,
        null -> "New"
    }

@Composable
private fun RemoteLibraryBrowser(
    defaultServerUrl: String,
    defaultPairingToken: String,
    appendDiagnostic: (String, String) -> Unit,
    onLoadPreparedPlayback: (LanPlaybackPreparation) -> Unit,
) {
    val libraryClient = remember { JvmLanLibraryClient() }
    val libraryConnectionSession = remember(libraryClient) { LanLibraryConnectionSession(libraryClient) }
    val playbackPreparer = remember(libraryClient) { LanPlaybackPreparer(libraryClient) }
    val scope = rememberCoroutineScope()
    var serverUrl by remember(defaultServerUrl) { mutableStateOf(defaultServerUrl) }
    var pairingToken by remember(defaultPairingToken) { mutableStateOf(defaultPairingToken) }
    var catalog by remember { mutableStateOf<LibraryCatalog?>(null) }
    var playbackProgresses by remember { mutableStateOf(emptyList<PlaybackProgress>()) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var selectedPlaybackPreparation by remember {
        mutableStateOf<LanPlaybackPreparation?>(null)
    }
    var isLoading by remember { mutableStateOf(false) }
    var isPreparingPlayback by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
    var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
    val totalItems = catalog?.items.orEmpty()
    val filteredItems = remember(catalog, searchText, sort, subtitleFilter) {
        catalog
            ?.filteredItems(
                LibraryCatalogQuery(
                    searchText = searchText,
                    sort = sort,
                    subtitleFilter = subtitleFilter,
                ),
            )
            .orEmpty()
    }
    val watchStatusById = remember(catalog, playbackProgresses) {
        catalog?.watchStatusByMediaId(playbackProgresses).orEmpty()
    }
    val nextUpItems = remember(catalog, playbackProgresses) {
        catalog?.nextUpItems(playbackProgresses).orEmpty().take(4)
    }
    val continueWatchingItems = remember(catalog, playbackProgresses) {
        catalog?.continueWatchingItems(playbackProgresses).orEmpty().take(4)
    }

    fun refreshCatalog() {
        val requestedServerUrl = serverUrl
        val requestedPairingToken = pairingToken
        appendDiagnostic("remote-client", "Fetching catalog from $requestedServerUrl")
        scope.launch {
            isLoading = true
            selectedPlaybackPreparation = null
            runCatching {
                withContext(Dispatchers.IO) {
                    libraryConnectionSession.fetchCatalogWithProgress(requestedServerUrl, requestedPairingToken)
                }
            }.onSuccess {
                catalog = it.catalog
                playbackProgresses = it.playbackProgresses
                libraryError = null
                appendDiagnostic(
                    "remote-client",
                    "Fetched catalog: ${it.catalog.items.size} items, ${it.playbackProgresses.size} progress rows",
                )
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

    Text("Desktop paired library client")
    Text("Defaults to this app's embedded same-machine server. Enter another desktop URL to browse remotely.")
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
    Text("Paired episodes: ${totalItems.size}")
    MetadataRow("Paired progress", "${playbackProgresses.size} saved rows")
    if (catalog != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Paired Next Up", fontWeight = FontWeight.Bold)
                if (nextUpItems.isEmpty()) {
                    EmptyState("No paired next-up item yet.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(nextUpItems, key = { it.mediaItem.id }) { item ->
                            RemoteNextUpRow(
                                item = item,
                                isPreparing = isPreparingPlayback,
                                onPrepareRemotePlayback = {
                                    prepareRemotePlayback(item.mediaItem, loadAfterPrepare = false)
                                },
                                onPlayRemotePlayback = {
                                    prepareRemotePlayback(item.mediaItem, loadAfterPrepare = true)
                                },
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Paired Continue Watching", fontWeight = FontWeight.Bold)
                if (continueWatchingItems.isEmpty()) {
                    EmptyState("No in-progress paired episodes yet.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(continueWatchingItems, key = { it.mediaItem.id }) { item ->
                            RemoteContinueWatchingRow(
                                item = item,
                                isPreparing = isPreparingPlayback,
                                onPlayRemotePlayback = {
                                    prepareRemotePlayback(item.mediaItem, loadAfterPrepare = true)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    OutlinedTextField(
        value = searchText,
        onValueChange = { searchText = it },
        label = { Text("Search paired episodes") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
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
    MetadataRow("Showing", "${filteredItems.size} / ${totalItems.size} paired episodes")
    when {
        catalog == null -> EmptyState("Load a paired server catalog to browse remote episodes.")
        totalItems.isEmpty() -> EmptyState("The paired server did not publish any episodes.")
        filteredItems.isEmpty() -> EmptyState(
            text = "No paired episodes match the current filters.",
            actionLabel = "Reset filters",
            onAction = {
                searchText = ""
                sort = LibraryCatalogSort.TITLE
                subtitleFilter = LibrarySubtitleFilter.ANY
            },
        )
        else -> LazyColumn(modifier = Modifier.height(180.dp)) {
            items(filteredItems, key = { it.id }) { item ->
                RemoteEpisodeRow(
                    item = item,
                    watchStatus = watchStatusById[item.id],
                    isPreparing = isPreparingPlayback,
                    onPrepareRemotePlayback = { prepareRemotePlayback(item, loadAfterPrepare = false) },
                    onPlayRemotePlayback = { prepareRemotePlayback(item, loadAfterPrepare = true) },
                )
            }
        }
    }
    selectedPlaybackPreparation?.let { preparation ->
        Text("Prepared desktop playback: ${preparation.item.seriesTitle} - ${preparation.item.episodeTitle}")
        Text("Source: ${preparation.source.url.redactToken()}")
        Text("Resume: ${preparation.resumePositionMs?.let { "$it ms" } ?: "start from beginning"}")
        Button(
            onClick = {
                onLoadPreparedPlayback(preparation)
            },
        ) {
            Text("Load into desktop controller")
        }
    }
}

@Composable
private fun RemoteNextUpRow(
    item: LibraryNextUpItem,
    isPreparing: Boolean,
    onPrepareRemotePlayback: () -> Unit,
    onPlayRemotePlayback: () -> Unit,
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
            Text(item.nextUpLabel(), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPrepareRemotePlayback,
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Preparing..." else "Prepare")
            }
            Button(
                onClick = onPlayRemotePlayback,
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Loading..." else item.nextUpActionLabel())
            }
        }
    }
}

@Composable
private fun RemoteContinueWatchingRow(
    item: LibraryPlaybackProgressItem,
    isPreparing: Boolean,
    onPlayRemotePlayback: () -> Unit,
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
            onClick = onPlayRemotePlayback,
            enabled = !isPreparing,
        ) {
            Text(if (isPreparing) "Loading..." else "Resume")
        }
    }
}

@Composable
private fun RemoteEpisodeRow(
    item: LibraryMediaItem,
    watchStatus: LibraryWatchStatus?,
    isPreparing: Boolean,
    onPrepareRemotePlayback: () -> Unit,
    onPlayRemotePlayback: () -> Unit,
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
            Text(
                listOf(
                    watchStatus.statusLabel(),
                    item.mediaType.ifBlank { "unknown media" },
                    item.sizeBytes.formatLibrarySize(),
                    if (item.subtitles.isEmpty()) "no subtitles" else "${item.subtitles.size} subtitles",
                ).joinToString(separator = " - "),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.relativePath, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPrepareRemotePlayback,
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Preparing..." else "Prepare")
            }
            Button(
                onClick = onPlayRemotePlayback,
                enabled = !isPreparing,
            ) {
                Text(if (isPreparing) "Loading..." else "Play")
            }
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

private fun selectDanmakuFile(title: String) =
    JFileChooser().run {
        fileSelectionMode = JFileChooser.FILES_ONLY
        dialogTitle = title
        fileFilter = FileNameExtensionFilter(
            "Danmaku files",
            "xml",
            "json",
            "danmaku",
        )
        takeIf { showOpenDialog(null) == JFileChooser.APPROVE_OPTION }
            ?.selectedFile
            ?.toPath()
    }

private fun LibraryMediaItem.dandanplayStatusContext(
    settings: DandanplayProviderSettings,
): List<DandanplayPlaybackUiDetail> =
    listOf(
        DandanplayPlaybackUiDetail("Library episode", "$seriesTitle - $episodeTitle"),
        DandanplayPlaybackUiDetail("Media ID", id),
        DandanplayPlaybackUiDetail("Library file", relativePath),
        DandanplayPlaybackUiDetail("Provider", settings.statusText),
    )

private fun Throwable.readableMessage(): String =
    message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

private fun String.isDandanplayWarningStatus(): Boolean {
    val normalized = lowercase()
    return listOf(
        "failed",
        "not configured",
        "not indexed",
        "no match",
        "no comments",
        "no result",
        "not checked",
        "cannot",
        "skipped",
    ).any(normalized::contains)
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

private fun Long.formatLibrarySize(): String {
    val bytes = coerceAtLeast(0).toDouble()
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) {
        String.format(Locale.US, "%.1f GiB", gib)
    } else {
        String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0))
    }
}

private fun LibraryNextUpItem.nextUpLabel(): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> "Resume at ${progress?.positionMs?.formatPlaybackTime() ?: "saved position"}"
        LibraryNextUpReason.NEXT_EPISODE -> "Next after ${sourceProgress?.positionMs?.formatPlaybackTime() ?: "last watched"}"
        LibraryNextUpReason.START -> "Start watching this library"
    }

private fun LibraryNextUpItem.nextUpActionLabel(): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> "Resume"
        LibraryNextUpReason.NEXT_EPISODE,
        LibraryNextUpReason.START -> "Play"
    }

private fun AwtWindow.scaledRestoreBounds(bounds: Rectangle): Rectangle {
    val transform = graphicsConfiguration?.defaultTransform
    val scaleX = transform?.scaleX?.takeIf { it > 1.0 } ?: 1.0
    val scaleY = transform?.scaleY?.takeIf { it > 1.0 } ?: 1.0
    return Rectangle(
        bounds.x,
        bounds.y,
        (bounds.width * scaleX).roundToInt().coerceAtLeast(1),
        (bounds.height * scaleY).roundToInt().coerceAtLeast(1),
    )
}

private const val PLAYBACK_HOST_SETTLE_DELAY_MS = 300L

private const val PLAYBACK_SNAPSHOT_POLL_INTERVAL_MS = 500L

private const val WINDOWS_PROGRESS_SAVE_INTERVAL_MS = 5_000L

private const val MAX_DIAGNOSTIC_LOG_ENTRIES = 200

private const val LOCAL_AUTO_NEXT_SETTING_KEY = "playback.local_auto_next"

private const val MPV_OSC_FULLSCREEN_REQUEST_PROPERTY = "user-data/danmaku-osc/fullscreen-toggle-request"

private const val MPV_OSC_APP_FULLSCREEN_ON_BINDING = "danmaku_osc/danmaku-osc-app-fullscreen-on"

private const val MPV_OSC_APP_FULLSCREEN_OFF_BINDING = "danmaku_osc/danmaku-osc-app-fullscreen-off"

private const val PLAYER_CONTROLS_AUTO_HIDE_MS = 6_000L

private const val PLAYER_WINDOW_NAVIGATION_HEIGHT_DP = 44

private const val PLAYER_TOP_CONTROLS_HEIGHT_DP = 74

private const val PLAYER_BOTTOM_CONTROLS_HEIGHT_DP = 154

private val COVER_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

private val COVER_IMAGE_NAMES = listOf(
    "poster",
    "cover",
    "folder",
    "keyvisual",
    "key_visual",
    "key-visual",
    "thumbnail",
    "thumb",
)

private val PLAYBACK_RATE_STEPS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

private val DIAGNOSTIC_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss")
