package app.danmaku.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
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
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.ExternalAnimeTrackingPlanUpdate
import app.danmaku.domain.LibraryCatalog
import app.danmaku.domain.LibraryCatalogQuery
import app.danmaku.domain.LibraryCatalogSort
import app.danmaku.domain.LibraryItemMetadataStatus
import app.danmaku.domain.LocalDanmakuParser
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
import app.danmaku.domain.displayName
import app.danmaku.domain.episodeDetail
import app.danmaku.domain.externalAnimeTrackingPlan
import app.danmaku.domain.externalAnimeSyncRetryAfterEpochMs
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
import app.danmaku.server.PublicGetHookResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlin.math.roundToInt
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun DesktopShell(
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
    val externalAnimeCredentialStore = remember(catalogStore) {
        ExternalAnimeCredentialStore(catalogStore)
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
    var externalAnimeProviderSettings by remember(externalAnimeCredentialStore) {
        mutableStateOf(externalAnimeCredentialStore.loadSettings())
    }
    var dandanplayConnectionTestStatus by remember { mutableStateOf<SettingsConnectionTestStatus?>(null) }
    var myAnimeListConnectionTestStatus by remember { mutableStateOf<SettingsConnectionTestStatus?>(null) }
    var bangumiConnectionTestStatus by remember { mutableStateOf<SettingsConnectionTestStatus?>(null) }
    var localServerConnectionTestStatus by remember { mutableStateOf<SettingsConnectionTestStatus?>(null) }
    var dandanplayCacheEntries by remember { mutableStateOf(catalogStore.loadDandanplayCommentCaches()) }
    val myAnimeListOAuthService = remember(externalAnimeCredentialStore) {
        MyAnimeListOAuthService(externalAnimeCredentialStore)
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
    val diagnosticsState = rememberDesktopShellDiagnosticsState()
    val mpvCommandLog = diagnosticsState.mpvCommandLog
    val diagnosticLog = diagnosticsState.diagnosticLog
    val serverEvents = diagnosticsState.serverEvents
    val diagnosticFileLog = diagnosticsState.fileLog
    fun appendDiagnostic(category: String, message: String) =
        diagnosticsState.appendDiagnostic(category, message)
    fun appendServerEvent(event: LocalLibraryServerEvent) =
        diagnosticsState.appendServerEvent(event)
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
    var libraryMetadataVersion by remember { mutableStateOf(0) }
    val displayIndexedLibrary = remember(indexedLibrary, libraryMetadataVersion, animeMetadataResolver) {
        indexedLibrary?.withExternalAnimeMetadata(animeMetadataResolver)
    }
    val originalSeriesTitleByMediaId = remember(indexedLibrary) {
        indexedLibrary?.catalog?.items?.associate { item -> item.id to item.seriesTitle }.orEmpty()
    }
    val seriesPosterById = remember(displayIndexedLibrary, libraryMetadataVersion, animeMetadataResolver) {
        loadSeriesPosterById(displayIndexedLibrary, animeMetadataResolver)
    }
    val externalAnimeMappings = remember(displayIndexedLibrary, libraryMetadataVersion, catalogStore) {
        displayIndexedLibrary
            ?.catalog
            ?.groupedSeries()
            ?.flatMap { series -> catalogStore.loadExternalAnimeMappings(series.id) }
            .orEmpty()
    }
    val externalAnimeItemMappingsByMediaId = remember(displayIndexedLibrary, libraryMetadataVersion, catalogStore) {
        displayIndexedLibrary
            ?.catalog
            ?.items
            ?.associate { item -> item.id to catalogStore.loadExternalAnimeItemMappings(item.id) }
            .orEmpty()
    }
    var playbackProgresses by remember { mutableStateOf(catalogStore.loadPlaybackProgress()) }
    var favoriteMediaIds by remember { mutableStateOf(catalogStore.loadFavoriteMediaIds()) }
    var downloadQueueItems by remember { mutableStateOf(catalogStore.loadDownloads()) }
    var selectedLocalPlaybackPreparation by remember {
        mutableStateOf<DesktopLocalPlaybackPreparation?>(null)
    }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var isIndexing by remember { mutableStateOf(false) }
    var isPreparingLocalPlayback by remember { mutableStateOf(false) }
    var lastScanStats by remember { mutableStateOf<LocalMediaLibraryScanStats?>(null) }
    var dandanplayCacheStatus by remember { mutableStateOf<DandanplayPlaybackUiStatus?>(null) }
    var isRefreshingSeriesPosters by remember { mutableStateOf(false) }
    var refreshingMetadataMediaIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refreshingMetadataSeriesIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var externalAnimeSyncFailures by remember { mutableStateOf<List<ExternalAnimeSyncFailure>>(emptyList()) }
    var isExternalAnimeSyncing by remember { mutableStateOf(false) }
    val serverRuntime = remember(catalogStore, rootScanner, animeMetadataResolver, scope) {
        DesktopLibraryServerRuntime.start(
            catalogStore = catalogStore,
            rootScanner = rootScanner,
            metadataResolver = animeMetadataResolver,
            aniRssWebhookToken = aniRssCredentialStore.loadOrCreateWebhookToken(),
            onLibraryPublished = { library ->
                scope.launch {
                    indexedLibrary = library
                    libraryMetadataVersion += 1
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
            onMyAnimeListOAuthCallback = { query ->
                runCatching {
                    myAnimeListOAuthService.completeAuthorization(query)
                }.fold(
                    onSuccess = { updatedSettings ->
                        scope.launch {
                            externalAnimeProviderSettings = updatedSettings
                            appendDiagnostic("settings", "MyAnimeList OAuth authorization complete")
                        }
                        PublicGetHookResponse(
                            status = 200,
                            contentType = "text/html; charset=utf-8",
                            body = "<!doctype html><title>Danmaku</title><h1>MyAnimeList connected</h1><p>You can close this tab and return to Danmaku.</p>",
                        )
                    },
                    onFailure = { error ->
                        scope.launch {
                            appendDiagnostic("settings", "MyAnimeList OAuth authorization failed: ${error.message}")
                        }
                        PublicGetHookResponse(
                            status = 400,
                            contentType = "text/html; charset=utf-8",
                            body = "<!doctype html><title>Danmaku</title><h1>MyAnimeList authorization failed</h1><p>${(error.message ?: "Unknown error").escapeHtml()}</p>",
                        )
                    },
                )
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

    fun refreshMissingSeriesPosters(library: IndexedLocalLibrary) {
        if (isRefreshingSeriesPosters) return
        if (!dandanplaySettings.isFetchEnabled) {
            appendDiagnostic("metadata", "Skipping poster refresh; dandanplay provider is not configured")
            return
        }
        val missingSeries = library.catalog
            .groupedSeries()
            .filter { series ->
                findSeriesCoverImage(series, library) == null &&
                    animeMetadataResolver.cachedPosterForSeries(series) == null
            }
            .take(MAX_BACKGROUND_POSTER_REFRESH_SERIES)
        if (missingSeries.isEmpty()) return

        scope.launch {
            isRefreshingSeriesPosters = true
            try {
                appendDiagnostic("metadata", "Refreshing posters for ${missingSeries.size} local series")
                val failures = mutableListOf<String>()
                val refreshedCount = withContext(Dispatchers.IO) {
                    missingSeries.count { series ->
                        val result = runCatching {
                            animeMetadataResolver.ensureDandanplayPosterForSeries(
                                series = series,
                                mediaPathById = library.filesById,
                            )
                        }
                        result.onFailure { error ->
                            failures += "${series.title}: ${error.message ?: error::class.simpleName}"
                        }
                        result.getOrNull() != null
                    }
                }
                libraryMetadataVersion += 1
                failures.take(3).forEach { failure ->
                    appendDiagnostic("metadata", "Poster refresh failed for $failure")
                }
                val omittedFailureCount = failures.size - 3
                if (omittedFailureCount > 0) {
                    appendDiagnostic("metadata", "Poster refresh had $omittedFailureCount additional failures")
                }
                appendDiagnostic(
                    "metadata",
                    "Poster refresh complete: $refreshedCount/${missingSeries.size} series updated",
                )
            } finally {
                isRefreshingSeriesPosters = false
            }
        }
    }

    fun externalAnimeSyncFailure(
        update: ExternalAnimeTrackingPlanUpdate,
        message: String,
        previousFailures: Map<ExternalAnimeId, ExternalAnimeSyncFailure>,
        failedAtEpochMs: Long,
    ): ExternalAnimeSyncFailure {
        val attemptCount = (previousFailures[update.mapping.animeId]?.attemptCount ?: 0) + 1
        return ExternalAnimeSyncFailure(
            animeId = update.mapping.animeId,
            message = message,
            failedAtEpochMs = failedAtEpochMs,
            attemptCount = attemptCount,
            retryAfterEpochMs = externalAnimeSyncRetryAfterEpochMs(failedAtEpochMs, attemptCount),
        )
    }

    fun syncExternalAnimePlan(plan: ExternalAnimeTrackingPlan) {
        if (isExternalAnimeSyncing || plan.updates.isEmpty()) {
            return
        }
        isExternalAnimeSyncing = true
        scope.launch {
            try {
                val previousFailures = externalAnimeSyncFailures.associateBy(ExternalAnimeSyncFailure::animeId)
                val result = withContext(Dispatchers.IO) {
                    val clients = buildMap {
                        externalAnimeCredentialStore.loadMyAnimeListTrackingClient()
                            ?.let { put(it.provider, it) }
                        externalAnimeCredentialStore.loadBangumiTrackingClient()
                            ?.let { put(it.provider, it) }
                    }
                    var successCount = 0
                    val failures = mutableListOf<ExternalAnimeSyncFailure>()
                    plan.updates.forEach { update ->
                        val failedAtEpochMs = System.currentTimeMillis()
                        val client = clients[update.mapping.animeId.provider]
                        if (client == null) {
                            failures += externalAnimeSyncFailure(
                                update = update,
                                message = "${update.mapping.animeId.provider.displayName} access token is not configured",
                                previousFailures = previousFailures,
                                failedAtEpochMs = failedAtEpochMs,
                            )
                            return@forEach
                        }
                        runCatching {
                            client.updateListEntry(update.update)
                        }.onSuccess {
                            successCount += 1
                        }.onFailure { error ->
                            failures += externalAnimeSyncFailure(
                                update = update,
                                message = error.message ?: error.javaClass.simpleName.ifBlank { "External sync failed" },
                                previousFailures = previousFailures,
                                failedAtEpochMs = failedAtEpochMs,
                            )
                        }
                    }
                    successCount to failures
                }
                val (successCount, failures) = result
                externalAnimeSyncFailures = failures
                appendDiagnostic(
                    "external-sync",
                    "External list sync complete: $successCount succeeded, ${failures.size} failed",
                )
            } finally {
                isExternalAnimeSyncing = false
            }
        }
    }

    LaunchedEffect(indexedLibrary, dandanplaySettings.isFetchEnabled) {
        indexedLibrary?.let(::refreshMissingSeriesPosters)
    }

    fun applyPublishedLibrary(library: IndexedLocalLibrary) {
        appendDiagnostic("library", "Publishing library: ${library.catalog.items.size} items")
        server.publish(library.toPublishedLibrary(animeMetadataResolver))
        indexedLibrary = library
        libraryMetadataVersion += 1
        playbackProgresses = catalogStore.loadPlaybackProgress()
        favoriteMediaIds = catalogStore.loadFavoriteMediaIds()
        registeredRoots = rootRegistry.loadRoots()
        selectedLocalPlaybackPreparation = null
        libraryError = null
        refreshMissingSeriesPosters(library)
    }

    fun cleanupLegacySeriesAnimeMappings() {
        scope.launch {
            withContext(Dispatchers.IO) {
                catalogStore.deleteExternalAnimeMappings(
                    app.danmaku.domain.ExternalAnimeProvider.DANDANPLAY,
                    app.danmaku.domain.ExternalAnimeMappingSource.AUTO,
                )
            }
            appendDiagnostic("metadata", "Cleaned legacy automatic series anime mappings")
        }
    }

    fun refreshEpisodeAnimeMetadata(item: LibraryMediaItem, forceRefresh: Boolean = true) {
        val library = indexedLibrary
        if (library == null) {
            appendDiagnostic("metadata", "Cannot refresh metadata; library is not indexed")
            return
        }
        if (!dandanplaySettings.isFetchEnabled) {
            appendDiagnostic("metadata", "Cannot refresh metadata; dandanplay provider is not configured")
            return
        }
        if (item.id in refreshingMetadataMediaIds) return

        scope.launch {
            refreshingMetadataMediaIds = refreshingMetadataMediaIds + item.id
            dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = item.id,
                summary = "refreshing anime metadata and poster...",
                details = item.dandanplayStatusContext(dandanplaySettings),
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val animeId = animeMetadataResolver.cachedDandanplayAnimeIdForItem(item)
                        ?: run {
                            val mediaPath = library.filesById[item.id]
                                ?: error("Indexed media file is missing for ${item.id}")
                            dandanplayDanmakuResolver
                                .resolve(mediaId = item.id, mediaPath = mediaPath, forceRefresh = false)
                                .match
                                ?.animeId
                        }
                        ?: error("dandanplay found no anime match for ${item.id}")
                    animeMetadataResolver.refreshDandanplayMetadataForItem(
                        item = item,
                        animeId = animeId,
                        forceRefresh = forceRefresh,
                    )
                }
            }
            result.onSuccess {
                libraryMetadataVersion += 1
                appendDiagnostic("metadata", "Refreshed anime metadata/poster for ${item.episodeTitle}")
            }.onFailure { error ->
                appendDiagnostic(
                    "metadata",
                    "Anime metadata refresh failed for ${item.episodeTitle}: ${error.message ?: error::class.simpleName}",
                )
            }
            refreshingMetadataMediaIds = refreshingMetadataMediaIds - item.id
        }
    }

    fun refreshSeriesAnimeMetadata(series: LibrarySeries) {
        val library = indexedLibrary
        if (library == null) {
            appendDiagnostic("metadata", "Cannot refresh series metadata; library is not indexed")
            return
        }
        if (!dandanplaySettings.isFetchEnabled) {
            appendDiagnostic("metadata", "Cannot refresh series metadata; dandanplay provider is not configured")
            return
        }
        if (series.id in refreshingMetadataSeriesIds) return

        val items = series.seasons.flatMap { it.items }
        scope.launch {
            refreshingMetadataSeriesIds = refreshingMetadataSeriesIds + series.id
            refreshingMetadataMediaIds = refreshingMetadataMediaIds + items.mapTo(mutableSetOf()) { it.id }
            appendDiagnostic("metadata", "Refreshing anime metadata/posters for ${series.title}")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    items.count { item ->
                        val animeId = animeMetadataResolver.cachedDandanplayAnimeIdForItem(item)
                            ?: run {
                                val mediaPath = library.filesById[item.id] ?: return@count false
                                dandanplayDanmakuResolver
                                    .resolve(mediaId = item.id, mediaPath = mediaPath, forceRefresh = false)
                                    .match
                                    ?.animeId
                            }
                            ?: return@count false
                        animeMetadataResolver.refreshDandanplayMetadataForItem(
                            item = item,
                            animeId = animeId,
                            forceRefresh = true,
                        ) != null
                    }
                }
            }
            result.onSuccess { refreshedCount ->
                libraryMetadataVersion += 1
                appendDiagnostic("metadata", "Series metadata refresh complete: $refreshedCount/${items.size} episodes updated")
            }.onFailure { error ->
                appendDiagnostic(
                    "metadata",
                    "Series metadata refresh failed for ${series.title}: ${error.message ?: error::class.simpleName}",
                )
            }
            refreshingMetadataSeriesIds = refreshingMetadataSeriesIds - series.id
            refreshingMetadataMediaIds = refreshingMetadataMediaIds - items.mapTo(mutableSetOf()) { it.id }
        }
    }

    fun saveManualExternalAnimeMapping(
        series: LibrarySeries,
        provider: ExternalAnimeProvider,
        animeIdText: String,
    ) {
        val animeId = animeIdText.trim().toLongOrNull()?.takeIf { it > 0 }
        if (animeId == null) {
            appendDiagnostic("metadata", "Cannot link ${series.title}; ${provider.displayName} ID must be positive")
            return
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                catalogStore.saveExternalAnimeMapping(
                    ExternalAnimeMapping(
                        localSeriesId = series.id,
                        animeId = ExternalAnimeId(provider, animeId),
                        source = ExternalAnimeMappingSource.MANUAL,
                        confidence = 1.0,
                        mappedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
            libraryMetadataVersion += 1
            appendDiagnostic("metadata", "Linked ${series.title} to ${provider.displayName} #$animeId")
        }
    }

    fun deleteManualExternalAnimeMapping(series: LibrarySeries, provider: ExternalAnimeProvider) {
        scope.launch {
            withContext(Dispatchers.IO) {
                catalogStore.deleteExternalAnimeMapping(series.id, provider)
            }
            libraryMetadataVersion += 1
            appendDiagnostic("metadata", "Removed ${provider.displayName} link for ${series.title}")
        }
    }

    fun saveManualExternalAnimeItemMapping(
        item: LibraryMediaItem,
        provider: ExternalAnimeProvider,
        animeIdText: String,
    ) {
        val animeId = animeIdText.trim().toLongOrNull()?.takeIf { it > 0 }
        if (animeId == null) {
            appendDiagnostic("metadata", "Cannot link ${item.episodeTitle}; ${provider.displayName} ID must be positive")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    catalogStore.saveExternalAnimeItemMapping(
                        DesktopExternalAnimeItemMapping(
                            localMediaId = item.id,
                            animeId = ExternalAnimeId(provider, animeId),
                            source = ExternalAnimeMappingSource.MANUAL,
                            confidence = 1.0,
                            mappedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    if (provider == ExternalAnimeProvider.DANDANPLAY && dandanplaySettings.isFetchEnabled) {
                        animeMetadataResolver.refreshDandanplayMetadataForAnime(
                            animeId = animeId,
                            forceRefresh = false,
                        )
                    }
                }
            }
            result.onSuccess {
                libraryMetadataVersion += 1
                appendDiagnostic("metadata", "Linked ${item.episodeTitle} to ${provider.displayName} #$animeId")
            }.onFailure { error ->
                appendDiagnostic(
                    "metadata",
                    "Episode link failed for ${item.episodeTitle}: ${error.message ?: error::class.simpleName}",
                )
            }
        }
    }

    fun deleteManualExternalAnimeItemMapping(item: LibraryMediaItem, provider: ExternalAnimeProvider) {
        scope.launch {
            withContext(Dispatchers.IO) {
                catalogStore.deleteExternalAnimeItemMapping(item.id, provider)
            }
            libraryMetadataVersion += 1
            appendDiagnostic("metadata", "Removed ${provider.displayName} episode link for ${item.episodeTitle}")
        }
    }

    suspend fun searchExternalAnimeMatches(
        query: ExternalAnimeMatchQuery,
        providers: Set<ExternalAnimeProvider>,
    ): Result<List<ExternalAnimeMatchCandidate>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val clients = buildList {
                    if (ExternalAnimeProvider.MY_ANIME_LIST in providers) {
                        externalAnimeCredentialStore.loadMyAnimeListSearchConnection()
                            ?.let { add(MyAnimeListAnimeSearchClient(it)) }
                    }
                    if (ExternalAnimeProvider.BANGUMI in providers) {
                        add(externalAnimeCredentialStore.loadBangumiSearchClient())
                    }
                }
                check(clients.isNotEmpty()) {
                    "No external anime search providers are configured. Add a MyAnimeList client ID or enable Bangumi settings."
                }
                ExternalAnimeSearchService(
                    clients = clients,
                    catalogStore = catalogStore,
                ).searchAndCache(
                    query = query,
                    providers = clients.mapTo(mutableSetOf(), ExternalAnimeSearchClient::provider),
                    limitPerProvider = 8,
                )
            }
        }

    suspend fun fetchMetadataMatchPoster(imageUrl: String?): Path? =
        withContext(Dispatchers.IO) {
            posterCache.fetch(imageUrl)
        }

    LaunchedEffect(Unit) {
        cleanupLegacySeriesAnimeMappings()
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

    fun removeRegisteredRoot(root: DesktopLibraryRoot) {
        scope.launch {
            appendDiagnostic("library", "Removing library root: ${root.normalizedPath}")
            isIndexing = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        catalogStore.deleteLibraryRoot(root.id)
                        catalogStore.loadRegisteredLibrary()
                    }
                }.onSuccess { library ->
                    registeredRoots = rootRegistry.loadRoots()
                    lastScanStats = null
                    applyPublishedLibrary(library)
                    appendDiagnostic("library", "Removed library root ${root.displayName}")
                }.onFailure { error ->
                    libraryError = error.message
                    registeredRoots = rootRegistry.loadRoots()
                    appendDiagnostic("library", "Failed to remove library root ${root.displayName}: ${error.message}")
                }
            } finally {
                isIndexing = false
            }
        }
    }

    val navigationState = rememberDesktopShellNavigationState(catalogStore, scope, ::appendDiagnostic)
    val playbackState = rememberDesktopShellPlaybackState(catalogStore)

    fun shouldPersistPlaybackProgress(
        progress: PlaybackProgress,
        force: Boolean,
    ): Boolean {
        if (progress.positionMs <= 0) {
            return false
        }
        val lastSaved = playbackState.lastSavedPlaybackProgress
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
        val mediaId = playbackState.activeProgressMediaId ?: return
        val progress = snapshot.toPlaybackProgress(mediaId, System.currentTimeMillis()) ?: return
        if (!shouldPersistPlaybackProgress(progress, force)) {
            return
        }
        playbackState.lastSavedPlaybackProgress = progress
        val target = playbackState.activeProgressTarget
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
        playbackState.markPlaybackLoaded(request)
        return playbackSession.load(request).also {
            playbackSnapshot = it
        }
    }

    fun queuePlaybackUntilHostReady(request: DesktopPlaybackRequest) {
        playbackState.pendingPlaybackRequest = request
        appendDiagnostic(
            "playback",
            if (requiresNativeVideoHost) {
                "Queued playback until native video host attaches: ${request.label}; source=${request.source.toString().redactToken()}"
            } else {
                "Queued playback for ${hostPlatform.displayName} mpv output: ${request.label}; source=${request.source.toString().redactToken()}"
            },
        )
        navigationState.selectedTab = DesktopShellTab.PLAYBACK
    }

    fun triggerPrimaryPageAction(): Boolean {
        return when (navigationState.selectedTab) {
            DesktopShellTab.HOME,
            DesktopShellTab.MEDIA_LIBRARY -> {
                if (registeredRoots.isEmpty() || isIndexing) {
                    false
                } else {
                    appendDiagnostic("shell", "Primary shortcut requested library rescan from ${navigationState.desktopStrings.tabTitle(navigationState.selectedTab)}")
                    rescanRegisteredRoots()
                    true
                }
            }
            DesktopShellTab.DOWNLOADS -> {
                downloadQueueItems = catalogStore.loadDownloads()
                appendDiagnostic("downloads", "Primary shortcut refreshed download queue")
                true
            }
            DesktopShellTab.PLAYBACK -> {
                if (playbackSnapshot.source == null || playbackSnapshot.status == PlaybackStatus.LOADING) {
                    false
                } else if (playbackSnapshot.status == PlaybackStatus.PLAYING) {
                    appendDiagnostic("playback", "Primary shortcut paused playback")
                    playbackController.dispatch(PlaybackCommand.Pause)
                    playbackSnapshot = playbackController.snapshot()
                    persistActivePlaybackProgress(playbackSnapshot, force = true)
                    true
                } else {
                    appendDiagnostic("playback", "Primary shortcut started playback")
                    playbackController.dispatch(PlaybackCommand.Play)
                    playbackSnapshot = playbackController.snapshot()
                    true
                }
            }
            DesktopShellTab.TRACKING -> {
                val plan = indexedLibrary
                    ?.catalog
                    ?.externalAnimeTrackingPlan(
                        mappings = externalAnimeMappings,
                        progresses = playbackProgresses,
                        failures = externalAnimeSyncFailures,
                    )
                    ?: return false
                if (isExternalAnimeSyncing || plan.updates.isEmpty()) {
                    false
                } else {
                    appendDiagnostic("external-sync", "Primary shortcut started ${plan.updates.size} tracking update(s)")
                    syncExternalAnimePlan(plan)
                    true
                }
            }
            DesktopShellTab.PROFILE -> false
        }
    }

    fun handleDesktopShellShortcut(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }
        val ctrlOrMetaPressed = event.isCtrlPressed || event.isMetaPressed
        if (!ctrlOrMetaPressed || event.isAltPressed || event.isShiftPressed) {
            return false
        }
        return when (event.key) {
            Key.K -> {
                if (!isFullscreen && navigationState.selectedTab != DesktopShellTab.PLAYBACK) {
                    navigationState.globalSearchFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            }
            Key.Enter -> triggerPrimaryPageAction()
            Key.R -> {
                if (registeredRoots.isNotEmpty() && !isIndexing) {
                    appendDiagnostic("shell", "Shortcut requested library rescan")
                    rescanRegisteredRoots()
                    true
                } else {
                    false
                }
            }
            Key.One -> navigationState.selectTabFromShortcut(DesktopShellTab.HOME)
            Key.Two -> navigationState.selectTabFromShortcut(DesktopShellTab.MEDIA_LIBRARY)
            Key.Three -> navigationState.selectTabFromShortcut(DesktopShellTab.DOWNLOADS)
            Key.Four -> navigationState.selectTabFromShortcut(DesktopShellTab.PLAYBACK)
            Key.Five -> navigationState.selectTabFromShortcut(DesktopShellTab.TRACKING)
            Key.Six -> navigationState.selectTabFromShortcut(DesktopShellTab.PROFILE)
            else -> false
        }
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
                        val metadataRefreshResult = withContext(Dispatchers.IO) {
                            runCatching {
                                animeMetadataResolver.refreshDandanplayMetadataForItem(
                                    item = item,
                                    animeId = animeId,
                                    forceRefresh = refreshDandanplay,
                                )
                            }
                        }
                        metadataRefreshResult.onSuccess { posterPath ->
                            if (posterPath == null) {
                                appendDiagnostic(
                                    "metadata",
                                    "Refreshed dandanplay metadata for ${item.seriesTitle}; no poster URL available",
                                )
                            } else {
                                appendDiagnostic(
                                    "metadata",
                                    "Refreshed dandanplay poster metadata for ${item.seriesTitle}",
                                )
                            }
                            libraryMetadataVersion += 1
                        }.onFailure { error ->
                            appendDiagnostic(
                                "metadata",
                                "Dandanplay poster metadata refresh failed for ${item.seriesTitle}: " +
                                    (error.message ?: error::class.simpleName),
                            )
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

    fun inspectCachedDandanplay(item: LibraryMediaItem) {
        val library = indexedLibrary
        if (library == null) {
            dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = item.id,
                summary = "library is not indexed; cannot check cached danmaku",
                details = item.dandanplayStatusContext(dandanplaySettings),
            )
            return
        }
        val existingStatus = dandanplayCacheStatus
        val hasPreparedOverlay = selectedLocalPlaybackPreparation
            ?.takeIf { it.item.id == item.id }
            ?.subtitles
            ?.any(DesktopPlaybackSubtitle::isDanmakuOverlay) == true
        if (hasPreparedOverlay || existingStatus?.mediaId == item.id && !existingStatus.summary.contains("checking")) {
            return
        }

        scope.launch {
            dandanplayCacheStatus = dandanplayStatusMessage(
                mediaId = item.id,
                summary = "checking cached danmaku...",
                details = item.dandanplayStatusContext(dandanplaySettings),
            )
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val mediaPath = library.filesById[item.id]
                        ?: error("Indexed media file is missing for ${item.id}")
                    dandanplayDanmakuResolver.resolveCached(item.id, mediaPath)
                }
            }
            result.onSuccess { resolution ->
                dandanplayCacheStatus = if (resolution == null) {
                    dandanplayStatusMessage(
                        mediaId = item.id,
                        summary = "no valid cached danmaku",
                        details = item.dandanplayStatusContext(dandanplaySettings) +
                            DandanplayPlaybackUiDetail(
                                "Cache",
                                "prepare or refresh danmaku to fetch comments",
                            ),
                    )
                } else {
                    val status = dandanplayCachedInspectionStatus(item.id, resolution)
                    status.copy(
                        details = item.dandanplayStatusContext(dandanplaySettings) + status.details,
                    )
                }
            }.onFailure { error ->
                dandanplayCacheStatus = dandanplayStatusMessage(
                    mediaId = item.id,
                    summary = "cached danmaku check failed",
                    details = item.dandanplayStatusContext(dandanplaySettings) +
                        DandanplayPlaybackUiDetail("Error", error.readableMessage()),
                )
            }
        }
    }

    LaunchedEffect(playbackState.activeProgressMediaId, playbackState.activeProgressTarget, indexedLibrary?.catalog) {
        val mediaId = playbackState.activeProgressMediaId ?: return@LaunchedEffect
        if (playbackState.activeProgressTarget != null) {
            return@LaunchedEffect
        }
        val activeItem = indexedLibrary
            ?.catalog
            ?.items
            ?.firstOrNull { item -> item.id == mediaId }
            ?: return@LaunchedEffect
        inspectCachedDandanplay(activeItem)
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
        playbackState.autoNextLocalPlayback = enabled
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

    fun saveExternalAnimeProviderSettings(
        myAnimeListClientId: String?,
        myAnimeListClientSecret: String?,
        myAnimeListAccessToken: String?,
        bangumiBaseUrl: String,
        bangumiUserAgent: String,
        bangumiAccessToken: String?,
    ) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    externalAnimeCredentialStore.saveSettings(
                        myAnimeListClientId = myAnimeListClientId,
                        myAnimeListClientSecret = myAnimeListClientSecret,
                        myAnimeListAccessToken = myAnimeListAccessToken,
                        bangumiBaseUrl = bangumiBaseUrl,
                        bangumiUserAgent = bangumiUserAgent,
                        bangumiAccessToken = bangumiAccessToken,
                    )
                }
            }.onSuccess { updatedSettings ->
                externalAnimeProviderSettings = updatedSettings
                appendDiagnostic("settings", "Saved MyAnimeList/Bangumi provider settings")
            }.onFailure {
                appendDiagnostic("settings", "Failed to save external anime provider settings: ${it.message}")
            }
        }
    }

    fun startMyAnimeListOAuth(
        myAnimeListClientId: String?,
        myAnimeListClientSecret: String?,
    ) {
        scope.launch {
            runCatching {
                val updatedSettings = withContext(Dispatchers.IO) {
                    externalAnimeCredentialStore.saveSettings(
                        myAnimeListClientId = myAnimeListClientId,
                        myAnimeListClientSecret = myAnimeListClientSecret,
                        myAnimeListAccessToken = null,
                        bangumiBaseUrl = externalAnimeProviderSettings.bangumiBaseUrl,
                        bangumiUserAgent = externalAnimeProviderSettings.bangumiUserAgent,
                        bangumiAccessToken = null,
                    )
                }
                externalAnimeProviderSettings = updatedSettings
                val redirectUri = "${server.baseUrl()}${DesktopLibraryServerRuntime.MY_ANIME_LIST_OAUTH_CALLBACK_PATH}"
                val clientSecret = myAnimeListClientSecret?.trim()?.takeIf(String::isNotBlank)
                    ?: withContext(Dispatchers.IO) {
                        externalAnimeCredentialStore.loadMyAnimeListClientSecret()
                    }
                val authorizationUri = myAnimeListOAuthService.beginAuthorization(
                    redirectUri = redirectUri,
                    clientSecret = clientSecret,
                )
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(authorizationUri)
                    "Opened MyAnimeList authorization in browser; callback=$redirectUri"
                } else {
                    "Open this MyAnimeList authorization URL: $authorizationUri"
                }
            }.onSuccess { message ->
                appendDiagnostic("settings", message)
            }.onFailure {
                appendDiagnostic("settings", "Failed to start MyAnimeList OAuth: ${it.message}")
            }
        }
    }

    fun clearMyAnimeListSettings() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    externalAnimeCredentialStore.clearMyAnimeListSettings()
                }
            }.onSuccess { updatedSettings ->
                externalAnimeProviderSettings = updatedSettings
                appendDiagnostic("settings", "Cleared MyAnimeList provider settings")
            }.onFailure {
                appendDiagnostic("settings", "Failed to clear MyAnimeList provider settings: ${it.message}")
            }
        }
    }

    fun clearBangumiSettings() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    externalAnimeCredentialStore.clearBangumiSettings()
                }
            }.onSuccess { updatedSettings ->
                externalAnimeProviderSettings = updatedSettings
                appendDiagnostic("settings", "Cleared Bangumi provider settings")
            }.onFailure {
                appendDiagnostic("settings", "Failed to clear Bangumi provider settings: ${it.message}")
            }
        }
    }

    fun testDandanplayConnection() {
        dandanplayConnectionTestStatus = SettingsConnectionTestStatus(
            outcome = SettingsConnectionTestOutcome.TESTING,
            detail = "Checking saved dandanplay settings...",
        )
        scope.launch {
            appendDiagnostic("settings", "Testing saved dandanplay connection...")
            runCatching {
                withContext(Dispatchers.IO) {
                    DandanplayDanmakuClient(dandanplayCredentialStore.loadConnection())
                        .fetchAnimeDetails(1L)
                }
            }.onSuccess { anime ->
                appendDiagnostic(
                    "settings",
                    "dandanplay connection OK: ${anime.titles.primary} (#${anime.id.value})",
                )
                dandanplayConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = "${anime.titles.primary} (#${anime.id.value})",
                )
            }.onFailure {
                val message = it.readableMessage()
                appendDiagnostic("settings", "dandanplay connection test failed: $message")
                dandanplayConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.FAILURE,
                    detail = message,
                )
            }
        }
    }

    fun testMyAnimeListConnection() {
        val clientId = externalAnimeProviderSettings.myAnimeListClientId
        if (clientId.isNullOrBlank()) {
            appendDiagnostic("settings", "MyAnimeList connection test needs a saved client ID")
            myAnimeListConnectionTestStatus = SettingsConnectionTestStatus(
                outcome = SettingsConnectionTestOutcome.FAILURE,
                detail = "Save a MyAnimeList client ID first.",
            )
            return
        }
        myAnimeListConnectionTestStatus = SettingsConnectionTestStatus(
            outcome = SettingsConnectionTestOutcome.TESTING,
            detail = "Searching MyAnimeList with the saved client ID...",
        )
        scope.launch {
            appendDiagnostic("settings", "Testing saved MyAnimeList connection...")
            runCatching {
                withContext(Dispatchers.IO) {
                    MyAnimeListAnimeSearchClient(MyAnimeListSearchConnection(clientId))
                        .search(ExternalAnimeMatchQuery(title = "Frieren"), limit = 1)
                }
            }.onSuccess { results ->
                appendDiagnostic(
                    "settings",
                    "MyAnimeList connection OK: ${results.firstOrNull()?.titles?.primary ?: "no anime returned"}",
                )
                myAnimeListConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = results.firstOrNull()?.titles?.primary ?: "No anime returned.",
                )
            }.onFailure {
                val message = it.readableMessage()
                appendDiagnostic("settings", "MyAnimeList connection test failed: $message")
                myAnimeListConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.FAILURE,
                    detail = message,
                )
            }
        }
    }

    fun testBangumiConnection() {
        val settings = externalAnimeProviderSettings
        bangumiConnectionTestStatus = SettingsConnectionTestStatus(
            outcome = SettingsConnectionTestOutcome.TESTING,
            detail = "Searching Bangumi with the saved base URL and User-Agent...",
        )
        scope.launch {
            appendDiagnostic("settings", "Testing saved Bangumi connection...")
            runCatching {
                withContext(Dispatchers.IO) {
                    BangumiAnimeSearchClient(
                        baseUri = URI(settings.bangumiBaseUrl),
                        userAgent = settings.bangumiUserAgent,
                    ).search(ExternalAnimeMatchQuery(title = "Frieren"), limit = 1)
                }
            }.onSuccess { results ->
                appendDiagnostic(
                    "settings",
                    "Bangumi connection OK: ${results.firstOrNull()?.titles?.primary ?: "no anime returned"}",
                )
                bangumiConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = results.firstOrNull()?.titles?.primary ?: "No anime returned.",
                )
            }.onFailure {
                val message = it.readableMessage()
                appendDiagnostic("settings", "Bangumi connection test failed: $message")
                bangumiConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.FAILURE,
                    detail = message,
                )
            }
        }
    }

    fun testLocalServerConnection() {
        val baseUrl = server.baseUrl()
        val pairingToken = server.pairingToken
        localServerConnectionTestStatus = SettingsConnectionTestStatus(
            outcome = SettingsConnectionTestOutcome.TESTING,
            detail = "Checking status and pairing-token catalog access...",
        )
        scope.launch {
            appendDiagnostic("settings", "Testing local server at $baseUrl...")
            runCatching {
                withContext(Dispatchers.IO) {
                    val client = JvmLanLibraryClient()
                    val session = LanLibraryConnectionSession(client)
                    val status = session.validateServer(baseUrl)
                    val catalog = client.fetchCatalog(baseUrl, pairingToken)
                    status to catalog.items.size
                }
            }.onSuccess { (status, itemCount) ->
                appendDiagnostic(
                    "settings",
                    "Local server OK: API ${status.apiVersion}, streaming=${status.mediaStreaming}, items=$itemCount",
                )
                localServerConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.SUCCESS,
                    detail = "API ${status.apiVersion}, streaming=${status.mediaStreaming}, $itemCount items",
                )
            }.onFailure {
                val message = it.readableMessage()
                appendDiagnostic("settings", "Local server test failed: $message")
                localServerConnectionTestStatus = SettingsConnectionTestStatus(
                    outcome = SettingsConnectionTestOutcome.FAILURE,
                    detail = message,
                )
            }
        }
    }

    fun cleanupExpiredDandanplayCaches() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayDanmakuResolver.cleanupExpiredCaches()
                    catalogStore.loadDandanplayCommentCaches()
                }
            }.onSuccess { updatedEntries ->
                dandanplayCacheEntries = updatedEntries
                appendDiagnostic("danmaku", "Cleaned up expired dandanplay cache entries")
            }.onFailure {
                appendDiagnostic("danmaku", "Failed to clean expired dandanplay cache entries: ${it.message}")
            }
        }
    }
    fun refreshDandanplayCacheEntries() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    catalogStore.loadDandanplayCommentCaches()
                }
            }.onSuccess {
                dandanplayCacheEntries = it
                appendDiagnostic("danmaku", "Reloaded ${it.size} dandanplay cache entr${if (it.size == 1) "y" else "ies"}")
            }.onFailure {
                appendDiagnostic("danmaku", "Failed to reload dandanplay cache entries: ${it.message}")
            }
        }
    }
    fun deleteDandanplayCacheEntry(mediaId: String) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    dandanplayDanmakuResolver.clearCache(mediaId)
                    catalogStore.loadDandanplayCommentCaches()
                }
            }.onSuccess { updatedEntries ->
                dandanplayCacheEntries = updatedEntries
                if (dandanplayCacheStatus?.mediaId == mediaId) {
                    dandanplayCacheStatus = dandanplayStatusMessage(
                        mediaId = mediaId,
                        summary = "dandanplay cache cleared",
                    )
                }
                appendDiagnostic("danmaku", "Deleted dandanplay cache entry for $mediaId")
            }.onFailure {
                appendDiagnostic("danmaku", "Failed to delete dandanplay cache entry for $mediaId: ${it.message}")
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
        indexedLibrary?.toPublishedLibrary(animeMetadataResolver)?.let(server::publish)
        if (registeredRoots.isNotEmpty()) {
            rescanRegisteredRoots()
        } else {
            legacySelectedLibraryRoot?.let(::registerAndScanUserRoot)
        }
    }

    LaunchedEffect(launchOptions.smokePlayback) {
        val smokePlayback = launchOptions.smokePlayback ?: return@LaunchedEffect
        if (playbackState.smokePlaybackQueued) {
            return@LaunchedEffect
        }
        playbackState.smokePlaybackQueued = true
        queueSmokePlayback(smokePlayback)
    }

    LaunchedEffect(navigationState.selectedTab, nativeVideoHostReady, playbackState.pendingPlaybackRequest, playbackSession) {
        val request = playbackState.pendingPlaybackRequest ?: return@LaunchedEffect
        if (navigationState.selectedTab != DesktopShellTab.PLAYBACK || !nativeVideoHostReady) {
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
        if (playbackState.pendingPlaybackRequest != request || navigationState.selectedTab != DesktopShellTab.PLAYBACK || !nativeVideoHostReady) {
            appendDiagnostic("playback", "Queued playback changed before load; skipping stale request: ${request.label}")
            return@LaunchedEffect
        }
        appendDiagnostic("playback", "Loading queued playback after host settle: ${request.label}")
        loadPlaybackRequest(request)
        playbackState.pendingPlaybackRequest = null
    }

    LaunchedEffect(playbackController) {
        while (true) {
            delay(PLAYBACK_SNAPSHOT_POLL_INTERVAL_MS)
            val nextSnapshot = playbackController.snapshot()
            if (nextSnapshot != playbackSnapshot) {
                playbackSnapshot = nextSnapshot
            }
            persistActivePlaybackProgress(nextSnapshot)
            val mediaId = playbackState.activeProgressMediaId
            if (
                playbackState.autoNextLocalPlayback &&
                nextSnapshot.status == PlaybackStatus.ENDED &&
                mediaId != null &&
                playbackState.activeProgressTarget == null &&
                playbackState.lastAutoNextMediaId != mediaId
            ) {
                val nextItem = indexedLibrary?.catalog?.nextItem(mediaId)
                playbackState.lastAutoNextMediaId = mediaId
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
        if (!smokePlayback.autoExit || playbackState.smokePlaybackExitStarted || playbackSnapshot.status != PlaybackStatus.PLAYING) {
            return@LaunchedEffect
        }
        playbackState.smokePlaybackExitStarted = true
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
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent(::handleDesktopShellShortcut),
            color = DanmakuColors.Background,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                val showAppChrome = !isFullscreen && navigationState.selectedTab != DesktopShellTab.PLAYBACK
                if (showAppChrome) {
                    AppNavigationRail(
                        selectedTab = navigationState.selectedTab,
                        strings = navigationState.desktopStrings,
                        onTabSelected = { navigationState.selectedTab = it },
                        playbackLabel = playbackState.activePlaybackLabel,
                        playbackStatus = playbackSnapshot.status,
                        episodeCount = indexedLibrary?.catalog?.items?.size ?: 0,
                        modifier = Modifier.width(224.dp),
                    )
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    if (showAppChrome) {
                        ShellHeader(
                            selectedTab = navigationState.selectedTab,
                            strings = navigationState.desktopStrings,
                            searchText = navigationState.globalSearchText,
                            onSearchTextChange = { navigationState.globalSearchText = it },
                            onSubmitSearch = navigationState::submitGlobalSearch,
                            searchFocusRequester = navigationState.globalSearchFocusRequester,
                            onRefresh = ::rescanRegisteredRoots,
                            onShowSettings = { navigationState.selectedTab = DesktopShellTab.PROFILE },
                            playerStatus = playbackSnapshot.status.name,
                            episodeCount = indexedLibrary?.catalog?.items?.size ?: 0,
                            isRefreshEnabled = registeredRoots.isNotEmpty() && !isIndexing,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                if (isFullscreen || navigationState.selectedTab == DesktopShellTab.PLAYBACK) {
                                    0.dp
                                } else {
                                    18.dp
                                },
                            ),
                    ) {
                    if (navigationState.selectedTab == DesktopShellTab.PLAYBACK) {
                        val activeLocalMediaId = playbackState.activeProgressMediaId.takeIf { playbackState.activeProgressTarget == null }
                        val previousLocalPlaybackItem = activeLocalMediaId
                            ?.let { mediaId -> indexedLibrary?.catalog?.previousItem(mediaId) }
                        val nextLocalPlaybackItem = activeLocalMediaId
                            ?.let { mediaId -> indexedLibrary?.catalog?.nextItem(mediaId) }
                        PlaybackTab(
                            strings = navigationState.desktopStrings,
                            playbackLabel = playbackState.activePlaybackLabel,
                            playbackSnapshot = playbackSnapshot,
                            mpvRuntimeStatus = mpvRuntime.statusMessage,
                            videoHostStatus = videoHostStatus,
                            overlayStatus = overlayStatus,
                            danmakuSettings = danmakuSettings,
                            dandanplayCacheStatus = dandanplayCacheStatus,
                            isPreparingLocalPlayback = isPreparingLocalPlayback,
                            libraryError = libraryError,
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
                            previousEpisodeLabel = previousLocalPlaybackItem?.episodeTitle,
                            nextEpisodeLabel = nextLocalPlaybackItem?.episodeTitle,
                            onPlayPreviousEpisode = previousLocalPlaybackItem?.let { item ->
                                {
                                    appendDiagnostic("playback", "Preparing previous local episode ${item.id}")
                                    prepareLocalPlayback(item, loadAfterPrepare = true)
                                }
                            },
                            onPlayNextEpisode = nextLocalPlaybackItem?.let { item ->
                                {
                                    appendDiagnostic("playback", "Preparing next local episode ${item.id}")
                                    prepareLocalPlayback(item, loadAfterPrepare = true)
                                }
                            },
                            onOpenMediaFile = {
                                appendDiagnostic("playback", "Opening direct media file picker")
                                selectMediaFile(
                                    title = navigationState.desktopStrings.chooseMediaFileTitle(hostPlatform.displayName),
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
                            onSaveDanmakuSettings = ::saveDanmakuSettings,
                            onShowHome = { navigationState.selectedTab = DesktopShellTab.HOME },
                            onShowLibrary = { navigationState.selectedTab = DesktopShellTab.MEDIA_LIBRARY },
                            canOpenMedia = mpvVideoWindowId != null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    when (navigationState.selectedTab) {
                        DesktopShellTab.HOME -> HomeTab(
                            strings = navigationState.desktopStrings,
                            playbackSnapshot = playbackSnapshot,
                            registeredRoots = registeredRoots,
                            indexedLibrary = displayIndexedLibrary,
                            seriesPosterById = seriesPosterById,
                            playbackProgresses = playbackProgresses,
                            favoriteMediaIds = favoriteMediaIds,
                            externalAnimeMappings = externalAnimeMappings,
                            externalAnimeSyncFailures = externalAnimeSyncFailures,
                            isIndexing = isIndexing,
                            isPreparingLocalPlayback = isPreparingLocalPlayback,
                            isRefreshingSeriesPosters = isRefreshingSeriesPosters,
                            refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                            refreshingMetadataSeriesIds = refreshingMetadataSeriesIds,
                            dandanplayCacheStatus = dandanplayCacheStatus,
                            episodeCount = indexedLibrary?.catalog?.items?.size ?: 0,
                            networkUrls = networkUrls,
                            pairingToken = server.pairingToken,
                            overlayStatus = overlayStatus,
                            libraryError = libraryError,
                            lastScanStats = lastScanStats,
                            diagnosticLog = diagnosticLog,
                            onOpenLibrary = { navigationState.selectedTab = DesktopShellTab.MEDIA_LIBRARY },
                            onOpenDownloads = { navigationState.selectedTab = DesktopShellTab.DOWNLOADS },
                            onOpenSettings = { navigationState.selectedTab = DesktopShellTab.PROFILE },
                            onOpenTracking = { navigationState.selectedTab = DesktopShellTab.TRACKING },
                            onRefreshMetadata = {
                                displayIndexedLibrary?.let(::refreshMissingSeriesPosters)
                            },
                            onPlayLocalPlayback = { item ->
                                prepareLocalPlayback(item, loadAfterPrepare = true)
                            },
                        )
                        DesktopShellTab.PLAYBACK -> Unit
                        DesktopShellTab.TRACKING -> TrackingTab(
                            strings = navigationState.desktopStrings,
                            indexedLibrary = displayIndexedLibrary,
                            externalAnimeMappings = externalAnimeMappings,
                            playbackProgresses = playbackProgresses,
                            externalAnimeSyncFailures = externalAnimeSyncFailures,
                            isExternalAnimeSyncing = isExternalAnimeSyncing,
                            externalAnimeProviderSettings = externalAnimeProviderSettings,
                            onSyncExternalAnimePlan = ::syncExternalAnimePlan,
                            onOpenSettings = { navigationState.selectedTab = DesktopShellTab.PROFILE },
                            onOpenLibrary = { navigationState.selectedTab = DesktopShellTab.MEDIA_LIBRARY },
                        )
                        DesktopShellTab.MEDIA_LIBRARY -> MediaLibraryTab(
                            strings = navigationState.desktopStrings,
                            registeredRoots = registeredRoots,
                            indexedLibrary = displayIndexedLibrary,
                            searchSeed = navigationState.librarySearchSeed,
                            searchSeedVersion = navigationState.librarySearchSeedVersion,
                            seriesPosterById = seriesPosterById,
                            externalAnimeMappings = externalAnimeMappings,
                            externalAnimeItemMappingsByMediaId = externalAnimeItemMappingsByMediaId,
                            externalAnimeProviderSettings = externalAnimeProviderSettings,
                            originalSeriesTitleByMediaId = originalSeriesTitleByMediaId,
                            refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                            refreshingMetadataSeriesIds = refreshingMetadataSeriesIds,
                            playbackProgresses = playbackProgresses,
                            favoriteMediaIds = favoriteMediaIds,
                            externalAnimeSyncFailures = externalAnimeSyncFailures,
                            isExternalAnimeSyncing = isExternalAnimeSyncing,
                            isIndexing = isIndexing,
                            isPreparingLocalPlayback = isPreparingLocalPlayback,
                            selectedLocalPlaybackPreparation = selectedLocalPlaybackPreparation,
                            dandanplayCacheStatus = dandanplayCacheStatus,
                            autoNextLocalPlayback = playbackState.autoNextLocalPlayback,
                            libraryError = libraryError,
                            lastScanStats = lastScanStats,
                            onAddLibraryFolder = {
                                selectLibraryDirectory(
                                    title = navigationState.desktopStrings.chooseAnimeLibraryFolderTitle,
                                )?.let(::registerAndScanUserRoot)
                            },
                            onImportAniRssOutputFolder = {
                                selectLibraryDirectory(
                                    title = navigationState.desktopStrings.chooseAniRssCompletedMediaFolderTitle,
                                )?.let(::importAndScanAniRssRoot)
                            },
                            onRescanRegisteredRoots = ::rescanRegisteredRoots,
                            onRemoveRegisteredRoot = ::removeRegisteredRoot,
                            onPrepareLocalPlayback = { item -> prepareLocalPlayback(item) },
                            onPlayLocalPlayback = { item ->
                                prepareLocalPlayback(item, loadAfterPrepare = true)
                            },
                            onInspectCachedDandanplay = ::inspectCachedDandanplay,
                            onSetFavorite = ::setFavorite,
                            onSetAutoNextLocalPlayback = ::setAutoNextLocalPlayback,
                            onRefreshDandanplay = ::refreshPreparedDandanplay,
                            onSelectDandanplayMatch = ::selectPreparedDandanplayMatch,
                            onClearDandanplayCache = ::clearPreparedDandanplayCache,
                            onClearDanmakuOverlay = ::clearPreparedDanmakuOverlay,
                            onAttachManualDanmaku = ::attachManualDanmaku,
                            onRefreshEpisodeMetadata = ::refreshEpisodeAnimeMetadata,
                            onRefreshSeriesMetadata = ::refreshSeriesAnimeMetadata,
                            onSaveExternalAnimeMapping = ::saveManualExternalAnimeMapping,
                            onDeleteExternalAnimeMapping = ::deleteManualExternalAnimeMapping,
                            onSaveExternalAnimeItemMapping = ::saveManualExternalAnimeItemMapping,
                            onDeleteExternalAnimeItemMapping = ::deleteManualExternalAnimeItemMapping,
                            onSearchExternalAnimeMatches = ::searchExternalAnimeMatches,
                            onFetchMetadataMatchPoster = ::fetchMetadataMatchPoster,
                            onSyncExternalAnimePlan = ::syncExternalAnimePlan,
                            onLoadPreparedPlayback = { preparation ->
                                appendDiagnostic(
                                    "playback",
                                    "Loading prepared local playback: ${preparation.item.id}; source=${preparation.source.path}",
                                )
                                queuePlaybackUntilHostReady(preparation.toPlaybackRequest())
                            },
                            remoteBrowser = {
                                RemoteLibraryBrowser(
                                    strings = navigationState.desktopStrings,
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
                            strings = navigationState.desktopStrings,
                            isIndexing = isIndexing,
                            webhookUrls = serverRuntime.aniRssWebhookUrls(),
                            webhookToken = serverRuntime.aniRssWebhookToken,
                            registeredRoots = registeredRoots,
                            downloadQueueItems = downloadQueueItems,
                            onAddAniRssOutputFolder = {
                                selectLibraryDirectory(
                                    title = navigationState.desktopStrings.chooseAniRssCompletedMediaFolderTitle,
                                )?.let(::importAndScanAniRssRoot)
                            },
                            onRefreshQueue = {
                                downloadQueueItems = catalogStore.loadDownloads()
                            },
                            onRemoveQueueItem = { item ->
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            catalogStore.deleteDownload(item.id)
                                        }
                                    }.onSuccess {
                                        downloadQueueItems = catalogStore.loadDownloads()
                                        appendDiagnostic("downloads", "Removed download queue item ${item.id}")
                                    }.onFailure { error ->
                                        appendDiagnostic("downloads", "Failed to remove download queue item ${item.id}: ${error.message}")
                                    }
                                }
                            },
                            onOpenOutputFolder = { item ->
                                openDownloadOutputFolder(item)
                                    .onSuccess {
                                        appendDiagnostic("downloads", "Opened download output folder for ${item.id}")
                                    }
                                    .onFailure { error ->
                                        appendDiagnostic("downloads", "Failed to open download output folder for ${item.id}: ${error.message}")
                                    }
                            },
                        )
                        DesktopShellTab.PROFILE -> ProfileTab(
                            desktopLanguage = navigationState.desktopLanguage,
                            strings = navigationState.desktopStrings,
                            mpvRuntimeStatus = mpvRuntime.statusMessage,
                            videoHostStatus = videoHostStatus,
                            serverBaseUrl = server.baseUrl(),
                            networkUrls = networkUrls,
                            pairingToken = server.pairingToken,
                            recentServerEvents = serverEvents,
                            appLogPath = diagnosticFileLog.appLogPath,
                            mpvLogPath = diagnosticFileLog.mpvLogPath,
                            diagnosticLog = diagnosticLog,
                            danmakuSettings = danmakuSettings,
                            onSaveDanmakuSettings = ::saveDanmakuSettings,
                            dandanplayCacheEntries = dandanplayCacheEntries,
                            onRefreshDandanplayCacheEntries = ::refreshDandanplayCacheEntries,
                            onDeleteDandanplayCacheEntry = ::deleteDandanplayCacheEntry,
                            onCleanupExpiredDandanplayCaches = ::cleanupExpiredDandanplayCaches,
                            onDesktopLanguageChange = navigationState::updateDesktopLanguage,
                            dandanplaySettings = dandanplaySettings,
                            onSaveDandanplaySettings = ::saveDandanplaySettings,
                            onClearDandanplaySettings = ::clearDandanplaySettings,
                            dandanplayConnectionTestStatus = dandanplayConnectionTestStatus,
                            onTestDandanplayConnection = ::testDandanplayConnection,
                            externalAnimeProviderSettings = externalAnimeProviderSettings,
                            onSaveExternalAnimeProviderSettings = ::saveExternalAnimeProviderSettings,
                            onStartMyAnimeListOAuth = ::startMyAnimeListOAuth,
                            myAnimeListConnectionTestStatus = myAnimeListConnectionTestStatus,
                            bangumiConnectionTestStatus = bangumiConnectionTestStatus,
                            onTestMyAnimeListConnection = ::testMyAnimeListConnection,
                            onTestBangumiConnection = ::testBangumiConnection,
                            onClearMyAnimeListSettings = ::clearMyAnimeListSettings,
                            onClearBangumiSettings = ::clearBangumiSettings,
                            localServerConnectionTestStatus = localServerConnectionTestStatus,
                            onTestLocalServerConnection = ::testLocalServerConnection,
                        )
                    }
                }
            }
        }
    }
}
}
