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
import app.danmaku.domain.ExternalAnimeInfo
import app.danmaku.domain.ExternalAnimeMatchCandidate
import app.danmaku.domain.ExternalAnimeMatchQuery
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeMappingSource
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeSyncFailure
import app.danmaku.domain.ExternalAnimeTrackingPlan
import app.danmaku.domain.ExternalAnimeTrackingPlanUpdate
import app.danmaku.domain.LibraryAnimeMetadata
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
import app.danmaku.server.PublishedLibrary
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
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.jetbrains.skia.Image as SkiaImage

fun main(args: Array<String>) = application {
    val launchOptions = remember(args) { DesktopLaunchOptions.parse(args) }
    val windowState = rememberWindowState(width = 1600.dp, height = 960.dp)
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

private enum class SettingsConnectionTestOutcome {
    TESTING,
    SUCCESS,
    FAILURE,
}

private data class SettingsConnectionTestStatus(
    val outcome: SettingsConnectionTestOutcome,
    val detail: String,
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
    val mpvCommandLog = remember { mutableStateListOf<DesktopMpvCommand>() }
    val diagnosticLog = remember { mutableStateListOf<DesktopDiagnosticLogEntry>() }
    val serverEvents = remember { mutableStateListOf<LocalLibraryServerEvent>() }
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
        serverEvents += event
        while (serverEvents.size > MAX_SERVER_DASHBOARD_EVENTS) {
            serverEvents.removeAt(0)
        }
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

    var selectedTab by remember { mutableStateOf(DesktopShellTab.HOME) }
    var globalSearchText by remember { mutableStateOf("") }
    var librarySearchSeed by remember { mutableStateOf("") }
    var librarySearchSeedVersion by remember { mutableStateOf(0) }
    val globalSearchFocusRequester = remember { FocusRequester() }
    var desktopLanguage by remember(catalogStore) {
        mutableStateOf(
            DesktopUiLanguage.fromStorageValue(
                catalogStore.loadSetting(DESKTOP_UI_LANGUAGE_SETTING_KEY)?.value,
            ),
        )
    }
    val desktopStrings = remember(desktopLanguage) { desktopLanguage.strings }
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

    fun submitGlobalSearch() {
        val query = globalSearchText.trim()
        if (query.isBlank()) {
            return
        }
        librarySearchSeed = query
        librarySearchSeedVersion += 1
        selectedTab = DesktopShellTab.MEDIA_LIBRARY
        appendDiagnostic("shell", "Global search routed to Library: $query")
    }

    fun selectShellTabFromShortcut(tab: DesktopShellTab): Boolean {
        selectedTab = tab
        appendDiagnostic("shell", "Shortcut routed to ${desktopStrings.tabTitle(tab)}")
        return true
    }

    fun setDesktopLanguage(language: DesktopUiLanguage) {
        if (desktopLanguage == language) {
            return
        }
        desktopLanguage = language
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    catalogStore.saveSetting(
                        DesktopAppSetting(
                            key = DESKTOP_UI_LANGUAGE_SETTING_KEY,
                            value = language.storageValue,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                }
            }.onSuccess {
                appendDiagnostic("settings", "Desktop language set to ${language.displayName}")
            }.onFailure {
                appendDiagnostic("settings", "Failed to save desktop language: ${it.message}")
            }
        }
    }

    fun triggerPrimaryPageAction(): Boolean {
        return when (selectedTab) {
            DesktopShellTab.HOME,
            DesktopShellTab.MEDIA_LIBRARY -> {
                if (registeredRoots.isEmpty() || isIndexing) {
                    false
                } else {
                    appendDiagnostic("shell", "Primary shortcut requested library rescan from ${desktopStrings.tabTitle(selectedTab)}")
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
                if (!isFullscreen && selectedTab != DesktopShellTab.PLAYBACK) {
                    globalSearchFocusRequester.requestFocus()
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
            Key.One -> selectShellTabFromShortcut(DesktopShellTab.HOME)
            Key.Two -> selectShellTabFromShortcut(DesktopShellTab.MEDIA_LIBRARY)
            Key.Three -> selectShellTabFromShortcut(DesktopShellTab.DOWNLOADS)
            Key.Four -> selectShellTabFromShortcut(DesktopShellTab.PLAYBACK)
            Key.Five -> selectShellTabFromShortcut(DesktopShellTab.TRACKING)
            Key.Six -> selectShellTabFromShortcut(DesktopShellTab.PROFILE)
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

    LaunchedEffect(activeProgressMediaId, activeProgressTarget, indexedLibrary?.catalog) {
        val mediaId = activeProgressMediaId ?: return@LaunchedEffect
        if (activeProgressTarget != null) {
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
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent(::handleDesktopShellShortcut),
            color = DanmakuColors.Background,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                val showAppChrome = !isFullscreen && selectedTab != DesktopShellTab.PLAYBACK
                if (showAppChrome) {
                    AppNavigationRail(
                        selectedTab = selectedTab,
                        strings = desktopStrings,
                        onTabSelected = { selectedTab = it },
                        playbackLabel = activePlaybackLabel,
                        playbackStatus = playbackSnapshot.status,
                        episodeCount = indexedLibrary?.catalog?.items?.size ?: 0,
                        modifier = Modifier.width(224.dp),
                    )
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    if (showAppChrome) {
                        ShellHeader(
                            selectedTab = selectedTab,
                            strings = desktopStrings,
                            searchText = globalSearchText,
                            onSearchTextChange = { globalSearchText = it },
                            onSubmitSearch = ::submitGlobalSearch,
                            searchFocusRequester = globalSearchFocusRequester,
                            onRefresh = ::rescanRegisteredRoots,
                            onShowSettings = { selectedTab = DesktopShellTab.PROFILE },
                            playerStatus = playbackSnapshot.status.name,
                            episodeCount = indexedLibrary?.catalog?.items?.size ?: 0,
                            isRefreshEnabled = registeredRoots.isNotEmpty() && !isIndexing,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                if (isFullscreen || selectedTab == DesktopShellTab.PLAYBACK) {
                                    0.dp
                                } else {
                                    18.dp
                                },
                            ),
                    ) {
                    if (selectedTab == DesktopShellTab.PLAYBACK) {
                        val activeLocalMediaId = activeProgressMediaId.takeIf { activeProgressTarget == null }
                        val previousLocalPlaybackItem = activeLocalMediaId
                            ?.let { mediaId -> indexedLibrary?.catalog?.previousItem(mediaId) }
                        val nextLocalPlaybackItem = activeLocalMediaId
                            ?.let { mediaId -> indexedLibrary?.catalog?.nextItem(mediaId) }
                        PlaybackTab(
                            strings = desktopStrings,
                            playbackLabel = activePlaybackLabel,
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
                            onSaveDanmakuSettings = ::saveDanmakuSettings,
                            onShowHome = { selectedTab = DesktopShellTab.HOME },
                            onShowLibrary = { selectedTab = DesktopShellTab.MEDIA_LIBRARY },
                            canOpenMedia = mpvVideoWindowId != null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    when (selectedTab) {
                        DesktopShellTab.HOME -> HomeTab(
                            strings = desktopStrings,
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
                            onOpenLibrary = { selectedTab = DesktopShellTab.MEDIA_LIBRARY },
                            onOpenDownloads = { selectedTab = DesktopShellTab.DOWNLOADS },
                            onOpenSettings = { selectedTab = DesktopShellTab.PROFILE },
                            onOpenTracking = { selectedTab = DesktopShellTab.TRACKING },
                            onRefreshMetadata = {
                                displayIndexedLibrary?.let(::refreshMissingSeriesPosters)
                            },
                            onPlayLocalPlayback = { item ->
                                prepareLocalPlayback(item, loadAfterPrepare = true)
                            },
                        )
                        DesktopShellTab.PLAYBACK -> Unit
                        DesktopShellTab.TRACKING -> TrackingTab(
                            strings = desktopStrings,
                            indexedLibrary = displayIndexedLibrary,
                            externalAnimeMappings = externalAnimeMappings,
                            playbackProgresses = playbackProgresses,
                            externalAnimeSyncFailures = externalAnimeSyncFailures,
                            isExternalAnimeSyncing = isExternalAnimeSyncing,
                            externalAnimeProviderSettings = externalAnimeProviderSettings,
                            onSyncExternalAnimePlan = ::syncExternalAnimePlan,
                            onOpenSettings = { selectedTab = DesktopShellTab.PROFILE },
                            onOpenLibrary = { selectedTab = DesktopShellTab.MEDIA_LIBRARY },
                        )
                        DesktopShellTab.MEDIA_LIBRARY -> MediaLibraryTab(
                            strings = desktopStrings,
                            registeredRoots = registeredRoots,
                            indexedLibrary = displayIndexedLibrary,
                            searchSeed = librarySearchSeed,
                            searchSeedVersion = librarySearchSeedVersion,
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
                            autoNextLocalPlayback = autoNextLocalPlayback,
                            libraryError = libraryError,
                            lastScanStats = lastScanStats,
                            onAddLibraryFolder = {
                                selectLibraryDirectory(
                                    title = "Choose anime library folder",
                                )?.let(::registerAndScanUserRoot)
                            },
                            onImportAniRssOutputFolder = {
                                selectLibraryDirectory(
                                    title = "Choose ani-rss completed-media folder",
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
                                    strings = desktopStrings,
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
                            strings = desktopStrings,
                            isIndexing = isIndexing,
                            webhookUrls = serverRuntime.aniRssWebhookUrls(),
                            webhookToken = serverRuntime.aniRssWebhookToken,
                            registeredRoots = registeredRoots,
                            downloadQueueItems = downloadQueueItems,
                            onAddAniRssOutputFolder = {
                                selectLibraryDirectory(
                                    title = "Choose ani-rss completed-media folder",
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
                            desktopLanguage = desktopLanguage,
                            strings = desktopStrings,
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
                            onDesktopLanguageChange = ::setDesktopLanguage,
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

private enum class DesktopShellTab(
    val title: String,
) {
    HOME("Home"),
    PLAYBACK("Playback"),
    MEDIA_LIBRARY("Library"),
    DOWNLOADS("Downloads"),
    TRACKING("Tracking"),
    PROFILE("Settings"),
}

private object DanmakuColors {
    val Background = Color(0xFF121316)
    val Surface = Color(0xFF1B1E23)
    val SurfaceRaised = Color(0xFF282D34)
    val Accent = Color(0xFFFFB547)
    val AccentSoft = Color(0xFF4B3420)
    val TextMuted = Color(0xFFB7C0CB)
    val Good = Color(0xFF5CE0A3)
    val Warning = Color(0xFFFFC857)
    val Info = Color(0xFF6BA9FF)
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

private enum class DesktopUiLanguage(
    val storageValue: String,
    val displayName: String,
    val strings: DesktopStrings,
) {
    ENGLISH(
        storageValue = "en",
        displayName = "English",
        strings = DesktopStrings(
            mediaHub = "Media hub",
            shellSubtitle = "Local anime library host",
            searchLabel = "Search anime, episodes, files (Ctrl+K)",
            playerStatusPrefix = "Player",
            episodesSuffix = "episodes",
            rescanLibrary = "Rescan library",
            settingsTitle = "Settings",
            settingsDescription = "App, providers, playback, and diagnostics",
            languageTitle = "Language",
            languageDescription = "Choose the desktop shell language. Page content localization will expand from this shared string layer.",
            uiLanguageLabel = "UI language",
            uiLanguagesValue = "English, Traditional Chinese",
            appLabel = "App",
            primaryTargetsLabel = "Primary targets",
            supportedLabel = "Supported",
            privacyTitle = "Privacy",
            credentialsPrivacyText = "Credentials are stored in local protected settings where supported and omitted from diagnostics.",
            playbackNeedsAttentionTitle = "Playback needs attention",
            preparingPlaybackTitle = "Preparing playback",
            noMediaLoadedLabel = "No media loaded",
            playingLabel = "Playing",
            videoHostLabel = "Video host",
            openMediaFileAction = "Open media file",
            homeAction = "Home",
            libraryAction = "Library",
            showPlayerChromeAction = "Show player chrome (H)",
            hidePlayerChromeAction = "Hide player chrome (H)",
            previousEpisodeAction = "Previous episode",
            previousEpisodeWithTitle = { title -> "Previous episode: $title" },
            nextEpisodeAction = "Next episode",
            nextEpisodeWithTitle = { title -> "Next episode: $title" },
            backThirtySecondsAction = "Back 30 seconds",
            backTenSecondsAction = "Back 10 seconds",
            playAction = "Play",
            forwardTenSecondsAction = "Forward 10 seconds",
            forwardThirtySecondsAction = "Forward 30 seconds",
            volumeLabel = "Volume",
            subtitleShortLabel = "Sub",
            hideDanmakuPanelAction = "Hide danmaku panel",
            showDanmakuPanelAction = "Show danmaku panel",
            exitFullscreenAction = "Exit fullscreen",
            enterFullscreenAction = "Enter fullscreen",
            libraryStepLabel = "Library",
            preparingMediaStepText = "Preparing media, subtitles, metadata, and danmaku...",
            playerRuntimeStepLabel = "Player runtime",
            libraryPreparationStepLabel = "Library preparation",
            danmakuTitle = "Danmaku",
            shownLabel = "Shown",
            hiddenLabel = "Hidden",
            cacheLabel = "Cache",
            audioLabel = "Audio",
            subtitleLabel = "Subtitle",
            defaultLabel = "Default",
            offLabel = "Off",
            hideAction = "Hide",
            showAction = "Show",
            opacityLabel = "Opacity",
            densityLabel = "Density",
            fontLabel = "Font",
            speedLabel = "Speed",
            areaLabel = "Area",
            offsetLabel = "Offset",
            librarySlicesTitle = "Library slices",
            animeSeriesSliceLabel = "Anime Series",
            moviesSliceLabel = "Movies",
            ovasSpecialsSliceLabel = "OVAs / Specials",
            allEpisodesSliceLabel = "All Episodes",
            favoritesSliceLabel = "Favorites",
            nowPlayingTitle = "Now Playing",
            recentlyAddedTitle = "Recently Added",
            browseAllAction = "Browse all",
            newlyAddedEmptyText = "Newly added episodes will appear here after a library scan.",
            recentlyAddedDetailLabel = { indexedAtEpochMs, sizeText -> "Added ${indexedAtEpochMs.formatEpochTime()} - $sizeText" },
            recentlyWatchedTitle = "Recently Watched",
            recentPlaybackEmptyText = "Recent playback will appear here after you watch local episodes.",
            watchedAtLabel = { positionMs -> "Watched ${positionMs.formatPlaybackTime()}" },
            myLibraryTitle = "My Library",
            seriesSummaryTitle = "Series",
            matchedGroupsCaption = "matched groups",
            favoritesSummaryTitle = "Favorites",
            savedEpisodesCaption = "saved episodes",
            watchingSummaryTitle = "Watching",
            inProgressCaption = "in progress",
            homeLibraryEmptyText = "Add a local anime folder to build your Home dashboard.",
            librarySnapshotTitle = "Library Snapshot",
            continueWatchingTitle = "Continue Watching",
            noResumeQueueText = "No resume queue yet. Browse the library to start playback.",
            loadedNowLabel = "Loaded now",
            resumeAtLabel = { positionMs -> "Resume at ${positionMs.formatPlaybackTime()}" },
            resumeSavedPositionLabel = "Resume at saved position",
            savedPositionLabel = "saved position",
            lastWatchedLabel = "last watched",
            nextAfterLabel = { positionMs -> "Next after ${positionMs?.formatPlaybackTime() ?: "last watched"}" },
            startWatchingLibraryLabel = "Start watching this library",
            episodeCountShortLabel = { count -> "$count eps" },
            libraryViewTitles = mapOf(
                WindowsLibraryView.CONTINUE_WATCHING to "Continue",
                WindowsLibraryView.NEXT_UP to "Next up",
                WindowsLibraryView.ALL_SERIES to "All Series",
                WindowsLibraryView.RECENTLY_WATCHED to "History",
                WindowsLibraryView.FAVORITES to "Favorites",
                WindowsLibraryView.FILES to "Files",
                WindowsLibraryView.EXTERNAL_SYNC to "External Sync",
                WindowsLibraryView.PAIRED to "Paired",
            ),
            libraryHostSubtitle = "Library host",
            librarySearchLabel = "Search anime, episode, path",
            libraryToolbarCompactDescription = "Search and filter without leaving context.",
            libraryToolbarDescription = "Search, filter, and select media without leaving context.",
            episodeCountSummary = { visible, total -> "$visible / $total episodes" },
            favoriteCountSummary = { count -> "$count favorites" },
            subtitlesOnlyLabel = "Subtitles only",
            pathSortLabel = "Path sort",
            requireSubtitlesAction = "Require subtitles",
            showAllSubtitlesAction = "Show all subtitles",
            favoritesOnlyAction = "Favorites only",
            showAllFavoritesAction = "Show all favorites",
            sortByPathAction = "Sort by path",
            sortByTitleAction = "Sort by title",
            openLibraryImportPanelAction = "Open library import panel",
            rescanFoldersAction = "Rescan folders",
            localPcLabel = "Local PC",
            indexingLabel = "Indexing...",
            externalListsLabel = "External lists",
            noLibraryLabel = "No library",
            pairedDevicesLabel = "Paired devices",
            lanBrowserReadyLabel = "LAN browser ready",
            foldersLabel = "Folders",
            noFoldersLabel = "No folders yet",
            moreItemsLabel = { count -> "+$count more" },
            lastScanSummary = { reused, refreshed -> "Last scan: $reused unchanged, $refreshed refreshed" },
            lastScanCountsSummary = { reused, refreshed -> "$reused unchanged, $refreshed refreshed" },
            libraryImportTitle = "Library Import",
            aniRssRootCountLabel = { count -> "$count ani-rss" },
            scanRunningLabel = "scan running",
            publishedLabel = "published",
            lastScanTitle = "Last scan",
            reusedCountLabel = { count -> "$count reused" },
            notRunLabel = "not run",
            addFolderAction = "Add folder",
            importAniRssOutputAction = "Import ani-rss output",
            rescanAllAction = "Rescan all",
            rescanAction = "Rescan",
            scanningAction = "Scanning...",
            indexingLibraryRootsLabel = "Indexing library roots",
            registeredRootsTitle = "Registered roots",
            libraryImportEmptyText = "Add a local anime folder or import an ani-rss completed-media folder to build the library.",
            removeLibraryFolderTitle = "Remove library folder?",
            removeLibraryFolderText = { name ->
                "This removes $name from Danmaku and drops its indexed episode rows. It does not delete files from disk."
            },
            removeFolderAction = "Remove folder",
            userSelectedFolderLabel = "User selected folder",
            aniRssOutputFolderLabel = "ani-rss output folder",
            libraryRootAvailableLabel = "Available",
            libraryRootMissingLabel = "Missing",
            lastScannedAtLabel = { epochMs -> "Last scanned ${epochMs.formatEpochTime()}" },
            notScannedYetLabel = "Not scanned yet",
            inspectorResizeHandleLabel = "Resize details panel",
            resetInspectorWidthAction = "Reset details width",
            inspectorTitle = "Inspector",
            inspectorEmptyText = "Select a series or episode to inspect playback, subtitles, and danmaku readiness.",
            nextPlayableLabel = { title -> "Next playable: $title" },
            loadingAction = "Loading...",
            preparingAction = "Preparing...",
            prepareAction = "Prepare",
            prepareShortAction = "Prep",
            detailsAction = "Details",
            favoriteAction = "Favorite",
            unfavoriteAction = "Unfavorite",
            showEpisodeDetailsAction = "Show episode details",
            checkCachedDanmakuAction = "Check cached danmaku",
            moreEpisodeActionsAction = "More episode actions",
            preparePlaybackAction = "Prepare playback",
            refreshEpisodeMetadataAction = "Refresh episode metadata",
            refreshingEpisodeMetadataAction = "Refreshing episode metadata",
            refreshSeriesMetadataAction = "Refresh series metadata",
            refreshingSeriesMetadataAction = "Refreshing series metadata",
            loadIntoPlayerAction = "Load into player",
            refreshDanmakuAction = "Refresh danmaku",
            attachLocalDanmakuAction = "Attach local danmaku",
            attachLocalDanmakuShortAction = "Attach local",
            removeOverlayAction = "Remove overlay",
            clearDanmakuCacheAction = "Clear danmaku cache",
            clearCacheAction = "Clear cache",
            enableAutoNextAction = "Enable auto-next",
            disableAutoNextAction = "Disable auto-next",
            autoNextOnLabel = "Auto-next on",
            autoNextOffLabel = "Auto-next off",
            readinessTitle = "Readiness",
            preparedPlaybackLabel = "Prepared playback",
            prepareToInspectTracksLabel = "Prepare to inspect tracks",
            notPreparedLabel = "Not prepared",
            resumeValueLabel = { positionMs -> "Resume ${positionMs.formatPlaybackTime()}" },
            notCheckedYetLabel = "Not checked yet",
            subtitlesIndexedLabel = { count -> "$count indexed" },
            episodesTitle = "Episodes",
            advancedTitle = "Advanced",
            favoriteStatusLabel = "Favorite",
            pairedLibraryTitle = "Desktop paired library client",
            pairedLibraryDescription = "Defaults to this app's embedded same-machine server. Enter another desktop URL to browse remotely.",
            pairedLibraryServerUrlLabel = "Library server URL",
            pairingCodeLabel = "Pairing code",
            loadPairedCatalogAction = "Load paired server catalog",
            pairedLibraryErrorLabel = { message -> "Paired library error: $message" },
            pairedEpisodesLabel = { count -> "Paired episodes: $count" },
            pairedProgressLabel = "Paired progress",
            savedRowsLabel = { count -> "$count saved rows" },
            pairedNextUpTitle = "Paired Next Up",
            pairedNextUpEmptyText = "No paired next-up item yet.",
            pairedContinueWatchingTitle = "Paired Continue Watching",
            pairedContinueWatchingEmptyText = "No in-progress paired episodes yet.",
            searchPairedEpisodesLabel = "Search paired episodes",
            sortTitleAction = "Sort title",
            sortPathAction = "Sort path",
            showingLabel = "Showing",
            pairedEpisodesCountLabel = { visible, total -> "$visible / $total paired episodes" },
            pairedCatalogEmptyText = "Load a paired server catalog to browse remote episodes.",
            pairedServerEmptyText = "The paired server did not publish any episodes.",
            pairedFilterEmptyText = "No paired episodes match the current filters.",
            resetFiltersAction = "Reset filters",
            preparedDesktopPlaybackLabel = { series, episode -> "Prepared desktop playback: $series - $episode" },
            sourceValueLabel = { value -> "Source: $value" },
            resumeValueText = { value -> "Resume: $value" },
            startFromBeginningLabel = "start from beginning",
            loadIntoDesktopControllerAction = "Load into desktop controller",
            unknownMediaLabel = "unknown media",
            unknownDurationLabel = "unknown",
            noSubtitlesLabel = "no subtitles",
            subtitleCountLabel = { count -> "$count subtitles" },
            downloadFilterTitles = mapOf(
                DownloadQueueFilter.ALL to "All",
                DownloadQueueFilter.ACTIVE to "Active",
                DownloadQueueFilter.QUEUED to "Queued",
                DownloadQueueFilter.COMPLETED to "Completed",
                DownloadQueueFilter.FAILED to "Failed",
            ),
            downloadsActiveTitle = "Active",
            downloadsActiveCaption = "currently running",
            downloadsQueuedTitle = "Queued",
            downloadsQueuedCaption = "waiting",
            downloadsCompletedTitle = "Completed",
            downloadsCompletedCaption = "ready to import",
            downloadsFailedTitle = "Failed",
            downloadsFailedCaption = "needs attention",
            downloadQueueTitle = "Download Queue",
            refreshDownloadQueueAction = "Refresh download queue",
            downloadQueuePolicyText = "Queue execution is reserved for authorized source contracts. ani-rss completed-media imports are available today.",
            downloadQueueEmptyText = "No persisted queue items yet. Completed ani-rss downloads can be imported from a trusted output folder.",
            downloadQueueFilterEmptyText = { filter -> "No ${filter.lowercase()} queue items match this filter." },
            openOutputFolderAction = "Open output folder",
            removeQueueItemAction = "Remove queue item",
            removeDownloadTitle = "Remove download queue item?",
            removeDownloadText = { outputPath ->
                "This removes the persisted queue row for $outputPath. It does not delete downloaded files from disk."
            },
            removeAction = "Remove",
            downloadSetupAuthorizedSourcesTitle = "Authorized Sources",
            downloadSetupAuthorizedSourcesText = "Use providers you are authorized to access. Danmaku imports completed media and does not bypass DRM or service rules.",
            authorizedImportsOnlyLabel = "Authorized imports only",
            queueExecutionPlannedLabel = "Queue execution planned",
            importRootCountLabel = { count -> "$count import roots" },
            addAniRssOutputFolderAction = "Add ani-rss output folder",
            aniRssWebhookTitle = "ani-rss Webhook",
            noWebhookUrlText = "No webhook URL is available yet.",
            webhookUrlLabel = "DOWNLOAD_END URL",
            webhookHeaderLabel = "Header",
            webhookTokenLabel = "Token",
            importRootsTitle = "Import Roots",
            noAniRssRootsText = "No ani-rss output folders registered.",
            downloadInspectorTitle = "Download Inspector",
            downloadInspectorEmptyText = "Select a queue item to inspect source, output path, progress, and available actions.",
            stateLabel = "State",
            progressLabel = "Progress",
            createdLabel = "Created",
            updatedLabel = "Updated",
            outputLabel = "Output",
            sourceLabel = "Source",
            failureLabel = "Failure",
            openFolderAction = "Open folder",
            pauseAction = "Pause",
            resumeAction = "Resume",
            retryAction = "Retry",
            cancelAction = "Cancel",
            downloadExecutionPlannedText = "Pause, resume, cancel, and retry will be enabled after authorized download source contracts and queue execution are implemented.",
            dandanplayProvidersTitle = "Danmaku Providers",
            dandanplayProvidersDescription = "dandanplay-compatible API settings for auto-match and fetched danmaku tracks.",
            cacheExpiryLabel = "Cache expiry",
            lastTestLabel = "Last test",
            apiBaseUrlLabel = "API base URL",
            appIdOptionalLabel = "AppId (optional)",
            appSecretKeepLabel = "AppSecret (leave blank to keep saved secret)",
            appSecretOptionalLabel = "AppSecret (optional)",
            cacheMaxAgeDaysLabel = "Cache max age days",
            signedAuthAction = "Signed auth",
            credentialAuthAction = "Credential auth",
            currentAuthLabel = { mode -> "Current: $mode" },
            saveDandanplaySettingsAction = "Save dandanplay settings",
            testSavedAction = "Test saved",
            clearAction = "Clear",
            cleanExpiredCacheAction = "Clean expired cache",
            clearDandanplayTitle = "Clear dandanplay settings?",
            clearDandanplayText = "This removes saved dandanplay credentials from local protected settings. API URL and cache defaults can be entered again later.",
            cleanExpiredDandanplayTitle = "Clean expired dandanplay cache?",
            cleanExpiredDandanplayText = "Expired cached danmaku entries will be removed according to the current cache age setting. Fresh valid entries are kept.",
            externalAnimeListsTitle = "External Anime Lists",
            externalAnimeListsDescription = "MyAnimeList and Bangumi settings for anime search, manual mapping, and future progress sync.",
            myAnimeListTestLabel = "MAL test",
            bangumiTestLabel = "Bangumi test",
            myAnimeListClientIdLabel = "MyAnimeList client ID",
            myAnimeListAccessTokenKeepLabel = "MyAnimeList access token (leave blank to keep saved token)",
            myAnimeListAccessTokenOptionalLabel = "MyAnimeList access token (optional)",
            myAnimeListClientSecretKeepLabel = "MyAnimeList client secret (leave blank to keep saved secret)",
            myAnimeListClientSecretOptionalLabel = "MyAnimeList client secret (optional for OAuth)",
            bangumiApiBaseUrlLabel = "Bangumi API base URL",
            bangumiUserAgentLabel = "Bangumi User-Agent",
            bangumiUserAgentRequiredError = "Bangumi User-Agent is required.",
            bangumiAccessTokenKeepLabel = "Bangumi access token (leave blank to keep saved token)",
            bangumiAccessTokenOptionalLabel = "Bangumi access token (optional)",
            saveExternalListsAction = "Save external lists",
            connectMyAnimeListAction = "Connect MAL",
            testMyAnimeListAction = "Test MAL",
            testBangumiAction = "Test Bangumi",
            clearMyAnimeListAction = "Clear MAL",
            clearBangumiAction = "Clear Bangumi",
            clearMyAnimeListTitle = "Clear MyAnimeList credentials?",
            clearMyAnimeListText = "This removes the saved MyAnimeList client secret and access token from local protected settings. Existing local anime mappings are not removed.",
            clearBangumiTitle = "Clear Bangumi credentials?",
            clearBangumiText = "This removes the saved Bangumi access token from local protected settings. Existing local anime mappings are not removed.",
            localServerTitle = "Local Server",
            serverBaseUrlLabel = "Base URL",
            lanUrlLabel = "LAN URL",
            lanUrlsLabel = "LAN URLs",
            lanUrlNumberedLabel = { index -> "LAN URL $index" },
            discoveryLabel = "Discovery",
            openServerDashboardAction = "Open dashboard",
            testLocalServerAction = "Test local server",
            serverDashboardTitle = "Server Dashboard",
            pairingAndLanAccessTitle = "Pairing and LAN access",
            healthTitle = "Health",
            notCheckedThisSessionLabel = "Not checked this session",
            recentRequestsLabel = "Recent requests",
            connectedClientsLabel = "Connected clients",
            connectedClientsPlannedText = "Planned: client identity is not instrumented yet",
            bandwidthLabel = "Bandwidth",
            bandwidthPlannedText = "Planned: byte counters are not instrumented yet",
            recentServerRequestsTitle = "Recent server requests",
            noServerRequestsText = "No server requests recorded this session.",
            testServerAction = "Test server",
            copyAction = "Copy",
            testingStatusLabel = "Testing",
            okStatusLabel = "OK",
            failedStatusLabel = "Failed",
            homeServerStatusTitle = "Server Status",
            attentionNeededLabel = "Attention needed",
            onlineLabel = "Online",
            noLanUrlDetectedLabel = "No LAN URL detected",
            pairingLabel = "Pairing",
            episodesLabel = "Episodes",
            errorLabel = "Error",
            metadataAndPostersTitle = "Metadata and Posters",
            loadingLabel = "Loading",
            partialLabel = "Partial",
            waitingLabel = "Waiting",
            metadataLoadingLabel = "Metadata loading",
            metadataFailedLabel = "Metadata failed",
            metadataReadyLabel = "Metadata ready",
            metadataPartialLabel = "Metadata partial",
            metadataNeededLabel = "Metadata needed",
            metadataFailedShortLabel = "Failed",
            metadataReadyShortLabel = "Ready",
            metadataNeededShortLabel = "Needed",
            metadataLoadingDetail = "Poster and anime match are refreshing",
            metadataFailedDetail = "Refresh episode metadata to retry matching",
            metadataReadyDetail = "Matched anime title and poster are available",
            metadataPartialMatchedNoPosterDetail = "Matched anime title is ready; poster is missing",
            metadataPartialPosterNoTitleDetail = "Poster is cached; anime title is missing",
            metadataPartialGenericDetail = "Some metadata is cached; refresh to complete the match",
            metadataNeededDetail = "Refresh metadata to match anime title and poster",
            seriesMetadataLoadingDetail = "Series poster and episode metadata are refreshing",
            seriesMetadataFailedDetail = "One or more episode metadata matches failed",
            seriesMetadataReadyDetail = "Series poster and episode metadata are available",
            seriesMetadataPartialDetail = "Some poster or episode metadata is available",
            seriesMetadataNeededDetail = "Refresh metadata to match this series",
            fileGroupLabel = { title -> "File group: $title" },
            postersReadySummary = { ready, total -> "$ready/$total posters ready" },
            refreshAction = "Refresh",
            refreshingAction = "Refreshing",
            lastScanLabel = "Last scan",
            groupsLabel = "Groups",
            externalSyncTitle = "External Sync",
            notMappedLabel = "Not mapped",
            readyUpdatesLabel = { count -> "$count ready updates" },
            openTrackingAction = "Open Tracking",
            downloadQueueReadyLabel = "Queue ready",
            downloadsImportDetail = "ani-rss output and imports",
            openDownloadsAction = "Open Downloads",
            cachedDanmakuTitle = "Cached Danmaku",
            notCheckedLabel = "Not checked",
            manageCacheAction = "Manage Cache",
            danmakuCacheSettingsTitle = "Danmaku Cache",
            danmakuCacheSettingsDescription = "Review persisted dandanplay comment caches, inspect selected entries, and clean stale rows using the configured cache age.",
            cachedEpisodesLabel = "Cached episodes",
            openCacheManagerAction = "Open cache manager",
            danmakuCacheManagerTitle = "Danmaku Cache Manager",
            cachedSummaryTitle = "Cached",
            expiredSummaryTitle = "Expired",
            commentsSummaryTitle = "Comments",
            cachedEventsCaption = "cached events",
            cacheDayRuleCaption = { days -> "$days day rule" },
            persistedEntriesTitle = "Persisted entries",
            noDandanplayCachesText = "No dandanplay comment caches are persisted yet.",
            selectedCacheTitle = "Selected cache",
            selectCachePromptText = "Select an episode cache after preparing or refreshing danmaku.",
            expiredStatusLabel = "Expired",
            cacheEntryCommentSummary = { count, fetchedAtEpochMs, fileName ->
                "$count comments - ${fetchedAtEpochMs.formatEpochTime()} - $fileName"
            },
            animeLabel = "Anime",
            episodeLabel = "Episode",
            mediaIdLabel = "Media ID",
            fileLabel = "File",
            fileSizeLabel = "File size",
            commentsLabel = "Comments",
            fetchedLabel = "Fetched",
            shiftLabel = "Shift",
            unknownAnimeLabel = "Unknown anime",
            unknownEpisodeLabel = "Unknown episode",
            noneLabel = "None",
            shiftSecondsLabel = { seconds -> "$seconds seconds" },
            assCacheLabel = "ASS cache",
            deleteCachedDanmakuTitle = "Delete cached danmaku?",
            deleteCachedDanmakuText = { title ->
                "This removes the persisted dandanplay cache for $title. It does not remove the original media file."
            },
            deleteCacheAction = "Delete cache",
            cleanExpiredDanmakuCachesTitle = "Clean expired danmaku caches?",
            cleanExpiredDanmakuCachesText = { days ->
                "This removes dandanplay comment caches older than $days days. Current valid caches remain available."
            },
            cleanExpiredAction = "Clean expired",
            providerSettingsAction = "Provider settings",
            myAnimeListClientSavedLabel = "Client ID saved",
            trackingCredentialsNeededLabel = "Configure API credentials to search and sync",
            mappedSummaryTitle = "Mapped",
            mappedSummaryCaption = "series links",
            readySummaryTitle = "Ready",
            readySummaryCaption = "provider writes",
            conflictsSummaryTitle = "Conflicts",
            conflictsSummaryCaption = "need review",
            failuresSummaryTitle = "Failures",
            failuresSummaryCaption = "retry tracked",
            trackingSyncPreviewTitle = "Tracking Sync Preview",
            openLibraryAction = "Open Library",
            trackingNoLibraryText = "Index a local library before reviewing external progress sync.",
            trackingTableTitle = "Tracking Table",
            trackingTableDescription = "Local and provider progress",
            syncAllReadyAction = "Sync all ready",
            syncingAction = "Syncing",
            trackingRowsEmptyText = "No tracking rows are available yet. Link local series to MyAnimeList or Bangumi from Library details.",
            localSeriesHeader = "Local series",
            providerHeader = "Provider",
            actionHeader = "Action",
            mappingInspectorTitle = "Mapping Inspector",
            mappingInspectorEmptyText = "Select a tracking row to inspect provider IDs, progress, and planned sync behavior.",
            animeIdLabel = "Anime ID",
            providerUrlLabel = "Provider URL",
            localSeriesIdLabel = "Local series ID",
            localProgressLabel = "Local progress",
            providerProgressLabel = "Provider progress",
            confidenceLabel = "Confidence",
            statusLabel = "Status",
            conflictLabel = "Conflict",
            externalWatchedLabel = "External watched",
            nextRetryLabel = "Next retry",
            skippedLabel = "Skipped",
            syncSelectedAction = "Sync selected",
            refreshProviderStateAction = "Refresh provider state",
            removeMappingAction = "Remove mapping",
            resolveConflictAction = "Resolve conflict",
            trackingPlannedControlsText = "Provider readback, mapping removal, and conflict resolution controls are planned; ready updates can sync through the existing provider sync path.",
            readbackPendingLabel = "Readback pending",
            readyStatusLabel = "Ready",
            reviewConflictAction = "Review conflict",
            conflictStatusLabel = "Conflict",
            externalProviderLabel = "External provider",
            notLinkedLabel = "Not linked",
            noLinkLabel = "No link",
            needsMappingLabel = "Needs mapping",
            missingLocalSeriesLabel = "Missing local series",
            retryAtLabel = { epochMs -> "Retry ${epochMs.formatEpochTime()}" },
            failedAttemptsLabel = { attempts -> "Failed x$attempts" },
            watchedCountLabel = { count -> "$count watched" },
            searchAction = "Search",
            searchingAction = "Searching...",
            closeAction = "Close",
            useAction = "Use",
            mappedAction = "Mapped",
            posterLoadingLabel = "Loading",
            posterUnavailableLabel = "Poster unavailable",
            danmakuDisplaySettingsTitle = "Danmaku Display",
            danmakuDisplaySettingsDescription = "Controls generated ASS danmaku overlays for matched and manually attached comment tracks. Reload media or refresh cached danmaku to apply renderer changes.",
            visibilityLabel = "Visibility",
            fontScaleLabel = "Font scale",
            displayAreaLabel = "Display area",
            showDanmakuAction = "Show danmaku",
            hideDanmakuAction = "Hide danmaku",
            opacityPercentLabel = "Opacity %",
            fontScalePercentLabel = "Font scale %",
            speedPercentLabel = "Speed %",
            densityPercentLabel = "Density %",
            displayAreaPercentLabel = "Display area %",
            offsetMsLabel = "Offset ms",
            keywordFiltersLabel = "Keyword filters, one per line",
            regexFiltersLabel = "Regex filters, one per line",
            offsetMsValueLabel = { offsetMs -> "$offsetMs ms" },
            saveDanmakuDisplayAction = "Save danmaku display",
            resetDraftAction = "Reset draft",
            metadataMatchTitle = "Metadata Match",
            metadataMatchDescription = { title ->
                "Search provider metadata for $title and save a series-level mapping."
            },
            metadataMatchSearchTitleLabel = "Search title",
            metadataMatchSelectProviderError = "Select at least one provider to search.",
            metadataMatchNoCandidates = { title -> "No provider candidates matched \"$title\"." },
            metadataMatchMyAnimeListUnavailable = "MyAnimeList search needs a client ID in Settings > Providers",
            metadataMatchBangumiUnavailable = "Bangumi search needs a valid API URL and User-Agent",
            metadataMatchCurrentMappingsPrefix = "Current mappings:",
            metadataMatchEmptyState = "Search MyAnimeList or Bangumi to review candidates and save a mapping.",
            metadataMatchMatchedTitlePrefix = "matched:",
        ),
    ),
    ZH_TW(
        storageValue = "zh-TW",
        displayName = "繁體中文",
        strings = DesktopStrings(
            tabTitles = mapOf(
                DesktopShellTab.HOME to "首頁",
                DesktopShellTab.PLAYBACK to "播放",
                DesktopShellTab.MEDIA_LIBRARY to "媒體庫",
                DesktopShellTab.DOWNLOADS to "下載",
                DesktopShellTab.TRACKING to "追蹤",
                DesktopShellTab.PROFILE to "設定",
            ),
            settingsSectionTitles = mapOf(
                DesktopSettingsSection.GENERAL to "一般",
                DesktopSettingsSection.LIBRARY to "媒體庫",
                DesktopSettingsSection.PLAYBACK to "播放",
                DesktopSettingsSection.DANMAKU to "彈幕",
                DesktopSettingsSection.PROVIDERS to "服務",
                DesktopSettingsSection.SERVER to "伺服器",
                DesktopSettingsSection.STORAGE to "儲存空間",
                DesktopSettingsSection.PRIVACY to "隱私",
                DesktopSettingsSection.DIAGNOSTICS to "診斷",
            ),
            mediaHub = "媒體中心",
            shellSubtitle = "本機動畫媒體庫主機",
            searchLabel = "搜尋動畫、集數、檔案 (Ctrl+K)",
            playerStatusPrefix = "播放器",
            episodesSuffix = "集",
            rescanLibrary = "重新掃描媒體庫",
            settingsTitle = "設定",
            settingsDescription = "應用程式、服務、播放與診斷",
            languageTitle = "語言",
            languageDescription = "選擇桌面介面語言。頁面內容會沿著這個共用字串層逐步在地化。",
            uiLanguageLabel = "介面語言",
            uiLanguagesValue = "英文、繁體中文",
            appLabel = "應用程式",
            primaryTargetsLabel = "主要平台",
            supportedLabel = "支援",
            privacyTitle = "隱私",
            credentialsPrivacyText = "憑證會盡可能儲存在本機受保護設定中，並且不會寫入診斷資訊。",
            playbackNeedsAttentionTitle = "播放需要處理",
            preparingPlaybackTitle = "正在準備播放",
            noMediaLoadedLabel = "尚未載入媒體",
            playingLabel = "播放中",
            videoHostLabel = "影片主機",
            openMediaFileAction = "開啟媒體檔案",
            homeAction = "首頁",
            libraryAction = "媒體庫",
            showPlayerChromeAction = "顯示播放器介面 (H)",
            hidePlayerChromeAction = "隱藏播放器介面 (H)",
            previousEpisodeAction = "上一集",
            previousEpisodeWithTitle = { title -> "上一集：$title" },
            nextEpisodeAction = "下一集",
            nextEpisodeWithTitle = { title -> "下一集：$title" },
            backThirtySecondsAction = "倒退 30 秒",
            backTenSecondsAction = "倒退 10 秒",
            playAction = "播放",
            forwardTenSecondsAction = "快轉 10 秒",
            forwardThirtySecondsAction = "快轉 30 秒",
            volumeLabel = "音量",
            subtitleShortLabel = "字幕",
            hideDanmakuPanelAction = "隱藏彈幕面板",
            showDanmakuPanelAction = "顯示彈幕面板",
            exitFullscreenAction = "離開全螢幕",
            enterFullscreenAction = "進入全螢幕",
            libraryStepLabel = "媒體庫",
            preparingMediaStepText = "正在準備媒體、字幕、中繼資料與彈幕...",
            playerRuntimeStepLabel = "播放器執行階段",
            libraryPreparationStepLabel = "媒體庫準備",
            danmakuTitle = "彈幕",
            shownLabel = "顯示",
            hiddenLabel = "隱藏",
            cacheLabel = "快取",
            audioLabel = "音訊",
            subtitleLabel = "字幕",
            defaultLabel = "預設",
            offLabel = "關閉",
            hideAction = "隱藏",
            showAction = "顯示",
            opacityLabel = "透明度",
            densityLabel = "密度",
            fontLabel = "字型",
            speedLabel = "速度",
            areaLabel = "範圍",
            offsetLabel = "時間偏移",
            librarySlicesTitle = "媒體庫分類",
            animeSeriesSliceLabel = "動畫系列",
            moviesSliceLabel = "電影",
            ovasSpecialsSliceLabel = "OVA / 特別篇",
            allEpisodesSliceLabel = "所有集數",
            favoritesSliceLabel = "收藏",
            nowPlayingTitle = "播放中",
            recentlyAddedTitle = "最近加入",
            browseAllAction = "瀏覽全部",
            newlyAddedEmptyText = "掃描媒體庫後，新加入的集數會顯示在這裡。",
            recentlyAddedDetailLabel = { indexedAtEpochMs, sizeText -> "加入 ${indexedAtEpochMs.formatEpochTime()} - $sizeText" },
            recentlyWatchedTitle = "最近觀看",
            recentPlaybackEmptyText = "觀看本機集數後，最近播放會顯示在這裡。",
            watchedAtLabel = { positionMs -> "已觀看 ${positionMs.formatPlaybackTime()}" },
            myLibraryTitle = "我的媒體庫",
            seriesSummaryTitle = "系列",
            matchedGroupsCaption = "已對應群組",
            favoritesSummaryTitle = "收藏",
            savedEpisodesCaption = "已收藏集數",
            watchingSummaryTitle = "觀看中",
            inProgressCaption = "進行中",
            homeLibraryEmptyText = "加入本機動畫資料夾以建立首頁儀表板。",
            librarySnapshotTitle = "媒體庫快照",
            continueWatchingTitle = "繼續觀看",
            noResumeQueueText = "尚無續播佇列。瀏覽媒體庫開始播放。",
            loadedNowLabel = "目前載入",
            resumeAtLabel = { positionMs -> "從 ${positionMs.formatPlaybackTime()} 繼續" },
            resumeSavedPositionLabel = "從已儲存位置繼續",
            savedPositionLabel = "已儲存位置",
            lastWatchedLabel = "上次觀看位置",
            nextAfterLabel = { positionMs -> "接著 ${positionMs?.formatPlaybackTime() ?: "上次觀看位置"}" },
            startWatchingLibraryLabel = "開始觀看此媒體庫",
            episodeCountShortLabel = { count -> "$count 集" },
            libraryViewTitles = mapOf(
                WindowsLibraryView.CONTINUE_WATCHING to "繼續觀看",
                WindowsLibraryView.NEXT_UP to "接著看",
                WindowsLibraryView.ALL_SERIES to "所有系列",
                WindowsLibraryView.RECENTLY_WATCHED to "觀看紀錄",
                WindowsLibraryView.FAVORITES to "收藏",
                WindowsLibraryView.FILES to "檔案",
                WindowsLibraryView.EXTERNAL_SYNC to "外部同步",
                WindowsLibraryView.PAIRED to "已配對",
            ),
            libraryHostSubtitle = "媒體庫主機",
            librarySearchLabel = "搜尋動畫、集數、路徑",
            libraryToolbarCompactDescription = "在目前內容中搜尋與篩選。",
            libraryToolbarDescription = "搜尋、篩選並選取媒體，不離開目前內容。",
            episodeCountSummary = { visible, total -> "$visible / $total 集" },
            favoriteCountSummary = { count -> "$count 個收藏" },
            subtitlesOnlyLabel = "只顯示字幕",
            pathSortLabel = "路徑排序",
            requireSubtitlesAction = "只顯示有字幕",
            showAllSubtitlesAction = "顯示所有字幕狀態",
            favoritesOnlyAction = "只顯示收藏",
            showAllFavoritesAction = "顯示所有收藏狀態",
            sortByPathAction = "依路徑排序",
            sortByTitleAction = "依標題排序",
            openLibraryImportPanelAction = "開啟媒體庫匯入面板",
            rescanFoldersAction = "重新掃描資料夾",
            localPcLabel = "本機電腦",
            indexingLabel = "索引中...",
            externalListsLabel = "外部清單",
            noLibraryLabel = "尚無媒體庫",
            pairedDevicesLabel = "已配對裝置",
            lanBrowserReadyLabel = "區網瀏覽已就緒",
            foldersLabel = "資料夾",
            noFoldersLabel = "尚無資料夾",
            moreItemsLabel = { count -> "還有 $count 個" },
            lastScanSummary = { reused, refreshed -> "上次掃描：$reused 個未變更，$refreshed 個已更新" },
            lastScanCountsSummary = { reused, refreshed -> "$reused 個未變更，$refreshed 個已更新" },
            libraryImportTitle = "媒體庫匯入",
            aniRssRootCountLabel = { count -> "$count 個 ani-rss" },
            scanRunningLabel = "掃描中",
            publishedLabel = "已發佈",
            lastScanTitle = "上次掃描",
            reusedCountLabel = { count -> "$count 個已重用" },
            notRunLabel = "尚未執行",
            addFolderAction = "新增資料夾",
            importAniRssOutputAction = "匯入 ani-rss 輸出",
            rescanAllAction = "重新掃描全部",
            rescanAction = "重新掃描",
            scanningAction = "掃描中...",
            indexingLibraryRootsLabel = "正在索引媒體庫根目錄",
            registeredRootsTitle = "已註冊根目錄",
            libraryImportEmptyText = "新增本機動畫資料夾，或匯入 ani-rss 已完成媒體資料夾以建立媒體庫。",
            removeLibraryFolderTitle = "要移除媒體庫資料夾嗎？",
            removeLibraryFolderText = { name ->
                "這會從 Danmaku 移除 $name 並刪除已索引的集數資料列，不會刪除磁碟上的檔案。"
            },
            removeFolderAction = "移除資料夾",
            userSelectedFolderLabel = "使用者選取的資料夾",
            aniRssOutputFolderLabel = "ani-rss 輸出資料夾",
            libraryRootAvailableLabel = "可用",
            libraryRootMissingLabel = "遺失",
            lastScannedAtLabel = { epochMs -> "上次掃描 ${epochMs.formatEpochTime()}" },
            notScannedYetLabel = "尚未掃描",
            inspectorResizeHandleLabel = "調整詳情面板寬度",
            resetInspectorWidthAction = "重設詳情寬度",
            inspectorTitle = "檢視器",
            inspectorEmptyText = "選取系列或集數以檢視播放、字幕與彈幕準備狀態。",
            nextPlayableLabel = { title -> "下一個可播放：$title" },
            loadingAction = "載入中...",
            preparingAction = "準備中...",
            prepareAction = "準備",
            prepareShortAction = "準備",
            detailsAction = "詳情",
            favoriteAction = "收藏",
            unfavoriteAction = "取消收藏",
            showEpisodeDetailsAction = "顯示集數詳情",
            checkCachedDanmakuAction = "檢查彈幕快取",
            moreEpisodeActionsAction = "更多集數動作",
            preparePlaybackAction = "準備播放",
            refreshEpisodeMetadataAction = "重新整理集數中繼資料",
            refreshingEpisodeMetadataAction = "正在重新整理集數中繼資料",
            refreshSeriesMetadataAction = "重新整理系列中繼資料",
            refreshingSeriesMetadataAction = "正在重新整理系列中繼資料",
            loadIntoPlayerAction = "載入播放器",
            refreshDanmakuAction = "重新整理彈幕",
            attachLocalDanmakuAction = "附加本機彈幕",
            attachLocalDanmakuShortAction = "附加本機",
            removeOverlayAction = "移除覆蓋",
            clearDanmakuCacheAction = "清除彈幕快取",
            clearCacheAction = "清除快取",
            enableAutoNextAction = "啟用自動下一集",
            disableAutoNextAction = "停用自動下一集",
            autoNextOnLabel = "自動下一集開啟",
            autoNextOffLabel = "自動下一集關閉",
            readinessTitle = "準備狀態",
            preparedPlaybackLabel = "播放已準備",
            prepareToInspectTracksLabel = "準備後檢視軌道",
            notPreparedLabel = "尚未準備",
            resumeValueLabel = { positionMs -> "續播 ${positionMs.formatPlaybackTime()}" },
            notCheckedYetLabel = "尚未檢查",
            subtitlesIndexedLabel = { count -> "已索引 $count 筆" },
            episodesTitle = "集數",
            advancedTitle = "進階",
            favoriteStatusLabel = "已收藏",
            pairedLibraryTitle = "桌面配對媒體庫用戶端",
            pairedLibraryDescription = "預設連到此應用程式內建的同機伺服器。輸入其他桌面 URL 可遠端瀏覽。",
            pairedLibraryServerUrlLabel = "媒體庫伺服器 URL",
            pairingCodeLabel = "配對碼",
            loadPairedCatalogAction = "載入配對伺服器目錄",
            pairedLibraryErrorLabel = { message -> "配對媒體庫錯誤：$message" },
            pairedEpisodesLabel = { count -> "配對集數：$count" },
            pairedProgressLabel = "配對進度",
            savedRowsLabel = { count -> "$count 筆已儲存" },
            pairedNextUpTitle = "配對下一集",
            pairedNextUpEmptyText = "尚無配對下一集項目。",
            pairedContinueWatchingTitle = "配對繼續觀看",
            pairedContinueWatchingEmptyText = "尚無進行中的配對集數。",
            searchPairedEpisodesLabel = "搜尋配對集數",
            sortTitleAction = "依標題排序",
            sortPathAction = "依路徑排序",
            showingLabel = "顯示",
            pairedEpisodesCountLabel = { visible, total -> "$visible / $total 個配對集數" },
            pairedCatalogEmptyText = "載入配對伺服器目錄以瀏覽遠端集數。",
            pairedServerEmptyText = "配對伺服器未發佈任何集數。",
            pairedFilterEmptyText = "沒有符合目前篩選的配對集數。",
            resetFiltersAction = "重設篩選",
            preparedDesktopPlaybackLabel = { series, episode -> "已準備桌面播放：$series - $episode" },
            sourceValueLabel = { value -> "來源：$value" },
            resumeValueText = { value -> "續播：$value" },
            startFromBeginningLabel = "從頭開始",
            loadIntoDesktopControllerAction = "載入桌面控制器",
            unknownMediaLabel = "未知媒體",
            unknownDurationLabel = "未知",
            noSubtitlesLabel = "無字幕",
            subtitleCountLabel = { count -> "$count 個字幕" },
            downloadFilterTitles = mapOf(
                DownloadQueueFilter.ALL to "全部",
                DownloadQueueFilter.ACTIVE to "進行中",
                DownloadQueueFilter.QUEUED to "佇列",
                DownloadQueueFilter.COMPLETED to "已完成",
                DownloadQueueFilter.FAILED to "失敗",
            ),
            downloadsActiveTitle = "進行中",
            downloadsActiveCaption = "目前執行",
            downloadsQueuedTitle = "佇列",
            downloadsQueuedCaption = "等待中",
            downloadsCompletedTitle = "已完成",
            downloadsCompletedCaption = "可匯入",
            downloadsFailedTitle = "失敗",
            downloadsFailedCaption = "需要處理",
            downloadQueueTitle = "下載佇列",
            refreshDownloadQueueAction = "重新整理下載佇列",
            downloadQueuePolicyText = "佇列執行會等授權來源合約完成後再啟用。目前可匯入 ani-rss 已完成媒體。",
            downloadQueueEmptyText = "尚無已儲存的佇列項目。可以從信任的輸出資料夾匯入 ani-rss 已完成下載。",
            downloadQueueFilterEmptyText = { filter -> "沒有符合「$filter」篩選的佇列項目。" },
            openOutputFolderAction = "開啟輸出資料夾",
            removeQueueItemAction = "移除佇列項目",
            removeDownloadTitle = "要移除下載佇列項目嗎？",
            removeDownloadText = { outputPath ->
                "這只會移除 $outputPath 的已儲存佇列資料列，不會刪除磁碟上的下載檔案。"
            },
            removeAction = "移除",
            downloadSetupAuthorizedSourcesTitle = "授權來源",
            downloadSetupAuthorizedSourcesText = "請使用你已授權存取的服務。Danmaku 只匯入已完成媒體，不會繞過 DRM 或服務規則。",
            authorizedImportsOnlyLabel = "僅限授權匯入",
            queueExecutionPlannedLabel = "佇列執行規劃中",
            importRootCountLabel = { count -> "$count 個匯入根目錄" },
            addAniRssOutputFolderAction = "新增 ani-rss 輸出資料夾",
            aniRssWebhookTitle = "ani-rss Webhook",
            noWebhookUrlText = "目前沒有可用的 webhook URL。",
            webhookUrlLabel = "DOWNLOAD_END URL",
            webhookHeaderLabel = "Header",
            webhookTokenLabel = "Token",
            importRootsTitle = "匯入根目錄",
            noAniRssRootsText = "尚未註冊 ani-rss 輸出資料夾。",
            downloadInspectorTitle = "下載檢視",
            downloadInspectorEmptyText = "選取佇列項目以檢視來源、輸出路徑、進度與可用動作。",
            stateLabel = "狀態",
            progressLabel = "進度",
            createdLabel = "建立時間",
            updatedLabel = "更新時間",
            outputLabel = "輸出",
            sourceLabel = "來源",
            failureLabel = "失敗原因",
            openFolderAction = "開啟資料夾",
            pauseAction = "暫停",
            resumeAction = "繼續",
            retryAction = "重試",
            cancelAction = "取消",
            downloadExecutionPlannedText = "暫停、繼續、取消與重試會在授權下載來源合約與佇列執行完成後啟用。",
            dandanplayProvidersTitle = "彈幕服務",
            dandanplayProvidersDescription = "用於自動對應與抓取彈幕軌的 dandanplay 相容 API 設定。",
            cacheExpiryLabel = "快取期限",
            lastTestLabel = "上次測試",
            apiBaseUrlLabel = "API 基礎網址",
            appIdOptionalLabel = "AppId（選填）",
            appSecretKeepLabel = "AppSecret（留空以保留已儲存密鑰）",
            appSecretOptionalLabel = "AppSecret（選填）",
            cacheMaxAgeDaysLabel = "快取最長天數",
            signedAuthAction = "簽章驗證",
            credentialAuthAction = "帳密驗證",
            currentAuthLabel = { mode -> "目前：$mode" },
            saveDandanplaySettingsAction = "儲存 dandanplay 設定",
            testSavedAction = "測試已儲存設定",
            clearAction = "清除",
            cleanExpiredCacheAction = "清除過期快取",
            clearDandanplayTitle = "清除 dandanplay 設定？",
            clearDandanplayText = "這會從本機受保護設定中移除已儲存的 dandanplay 憑證。之後仍可重新輸入 API 網址與快取預設值。",
            cleanExpiredDandanplayTitle = "清除過期 dandanplay 快取？",
            cleanExpiredDandanplayText = "會依目前快取期限移除過期彈幕快取；仍有效的新鮮快取會保留。",
            externalAnimeListsTitle = "外部動畫清單",
            externalAnimeListsDescription = "MyAnimeList 與 Bangumi 設定，用於動畫搜尋、手動對應與未來進度同步。",
            myAnimeListTestLabel = "MAL 測試",
            bangumiTestLabel = "Bangumi 測試",
            myAnimeListClientIdLabel = "MyAnimeList Client ID",
            myAnimeListAccessTokenKeepLabel = "MyAnimeList 存取權杖（留空以保留已儲存權杖）",
            myAnimeListAccessTokenOptionalLabel = "MyAnimeList 存取權杖（選填）",
            myAnimeListClientSecretKeepLabel = "MyAnimeList Client Secret（留空以保留已儲存密鑰）",
            myAnimeListClientSecretOptionalLabel = "MyAnimeList Client Secret（OAuth 選填）",
            bangumiApiBaseUrlLabel = "Bangumi API 基礎網址",
            bangumiUserAgentLabel = "Bangumi User-Agent",
            bangumiUserAgentRequiredError = "Bangumi User-Agent 為必填。",
            bangumiAccessTokenKeepLabel = "Bangumi 存取權杖（留空以保留已儲存權杖）",
            bangumiAccessTokenOptionalLabel = "Bangumi 存取權杖（選填）",
            saveExternalListsAction = "儲存外部清單",
            connectMyAnimeListAction = "連線 MAL",
            testMyAnimeListAction = "測試 MAL",
            testBangumiAction = "測試 Bangumi",
            clearMyAnimeListAction = "清除 MAL",
            clearBangumiAction = "清除 Bangumi",
            clearMyAnimeListTitle = "清除 MyAnimeList 憑證？",
            clearMyAnimeListText = "這會從本機受保護設定中移除已儲存的 MyAnimeList client secret 與存取權杖。既有本機動畫對應不會被移除。",
            clearBangumiTitle = "清除 Bangumi 憑證？",
            clearBangumiText = "這會從本機受保護設定中移除已儲存的 Bangumi 存取權杖。既有本機動畫對應不會被移除。",
            localServerTitle = "本機伺服器",
            serverBaseUrlLabel = "Base URL",
            lanUrlLabel = "LAN URL",
            lanUrlsLabel = "LAN URLs",
            lanUrlNumberedLabel = { index -> "LAN URL $index" },
            discoveryLabel = "探索",
            openServerDashboardAction = "開啟儀表板",
            testLocalServerAction = "測試本機伺服器",
            serverDashboardTitle = "伺服器儀表板",
            pairingAndLanAccessTitle = "配對與 LAN 存取",
            healthTitle = "健康狀態",
            notCheckedThisSessionLabel = "此工作階段尚未檢查",
            recentRequestsLabel = "最近請求",
            connectedClientsLabel = "已連線用戶端",
            connectedClientsPlannedText = "規劃中：尚未記錄用戶端身分",
            bandwidthLabel = "頻寬",
            bandwidthPlannedText = "規劃中：尚未記錄位元組計數",
            recentServerRequestsTitle = "最近伺服器請求",
            noServerRequestsText = "此工作階段尚無伺服器請求紀錄。",
            testServerAction = "測試伺服器",
            copyAction = "複製",
            testingStatusLabel = "測試中",
            okStatusLabel = "正常",
            failedStatusLabel = "失敗",
            homeServerStatusTitle = "伺服器狀態",
            attentionNeededLabel = "需要處理",
            onlineLabel = "線上",
            noLanUrlDetectedLabel = "沒有偵測到 LAN 網址",
            pairingLabel = "配對",
            episodesLabel = "集數",
            errorLabel = "錯誤",
            metadataAndPostersTitle = "中繼資料與海報",
            loadingLabel = "載入中",
            partialLabel = "部分完成",
            waitingLabel = "等待中",
            metadataLoadingLabel = "中繼資料載入中",
            metadataFailedLabel = "中繼資料失敗",
            metadataReadyLabel = "中繼資料就緒",
            metadataPartialLabel = "中繼資料部分完成",
            metadataNeededLabel = "需要中繼資料",
            metadataFailedShortLabel = "失敗",
            metadataReadyShortLabel = "就緒",
            metadataNeededShortLabel = "需要",
            metadataLoadingDetail = "正在重新整理海報與動畫對應",
            metadataFailedDetail = "重新整理單集資料以重試對應",
            metadataReadyDetail = "已取得符合的動畫標題與海報",
            metadataPartialMatchedNoPosterDetail = "動畫標題已符合；缺少海報",
            metadataPartialPosterNoTitleDetail = "海報已快取；缺少動畫標題",
            metadataPartialGenericDetail = "已快取部分中繼資料；重新整理可完成對應",
            metadataNeededDetail = "重新整理中繼資料以對應動畫標題與海報",
            seriesMetadataLoadingDetail = "正在重新整理系列海報與單集中繼資料",
            seriesMetadataFailedDetail = "一或多個單集中繼資料對應失敗",
            seriesMetadataReadyDetail = "系列海報與單集中繼資料已就緒",
            seriesMetadataPartialDetail = "已有部分海報或單集中繼資料",
            seriesMetadataNeededDetail = "重新整理中繼資料以對應此系列",
            fileGroupLabel = { title -> "檔案群組：$title" },
            postersReadySummary = { ready, total -> "$ready/$total 張海報就緒" },
            refreshAction = "重新整理",
            refreshingAction = "重新整理中",
            lastScanLabel = "上次掃描",
            groupsLabel = "群組",
            externalSyncTitle = "外部同步",
            notMappedLabel = "尚未對應",
            readyUpdatesLabel = { count -> "$count 個更新可同步" },
            openTrackingAction = "開啟追蹤",
            downloadQueueReadyLabel = "佇列就緒",
            downloadsImportDetail = "ani-rss 輸出與匯入",
            openDownloadsAction = "開啟下載",
            cachedDanmakuTitle = "已快取彈幕",
            notCheckedLabel = "尚未檢查",
            manageCacheAction = "管理快取",
            danmakuCacheSettingsTitle = "彈幕快取",
            danmakuCacheSettingsDescription = "檢視已儲存的 dandanplay 彈幕快取、檢查所選項目，並依目前快取期限清理過期資料。",
            cachedEpisodesLabel = "已快取集數",
            openCacheManagerAction = "開啟快取管理",
            danmakuCacheManagerTitle = "彈幕快取管理",
            cachedSummaryTitle = "已快取",
            expiredSummaryTitle = "已過期",
            commentsSummaryTitle = "彈幕",
            cachedEventsCaption = "已快取事件",
            cacheDayRuleCaption = { days -> "$days 天規則" },
            persistedEntriesTitle = "已儲存項目",
            noDandanplayCachesText = "目前沒有已儲存的 dandanplay 彈幕快取。",
            selectedCacheTitle = "所選快取",
            selectCachePromptText = "準備或重新整理彈幕後，選取一個集數快取檢視內容。",
            expiredStatusLabel = "已過期",
            cacheEntryCommentSummary = { count, fetchedAtEpochMs, fileName ->
                "$count 則彈幕 - ${fetchedAtEpochMs.formatEpochTime()} - $fileName"
            },
            animeLabel = "動畫",
            episodeLabel = "集數",
            mediaIdLabel = "媒體 ID",
            fileLabel = "檔案",
            fileSizeLabel = "檔案大小",
            commentsLabel = "彈幕",
            fetchedLabel = "取得時間",
            shiftLabel = "時間位移",
            unknownAnimeLabel = "未知動畫",
            unknownEpisodeLabel = "未知集數",
            noneLabel = "無",
            shiftSecondsLabel = { seconds -> "$seconds 秒" },
            assCacheLabel = "ASS 快取",
            deleteCachedDanmakuTitle = "要刪除已快取彈幕嗎？",
            deleteCachedDanmakuText = { title ->
                "這會移除「$title」的 dandanplay 持久快取，不會刪除原始媒體檔案。"
            },
            deleteCacheAction = "刪除快取",
            cleanExpiredDanmakuCachesTitle = "要清理過期彈幕快取嗎？",
            cleanExpiredDanmakuCachesText = { days ->
                "這會移除超過 $days 天的 dandanplay 彈幕快取。目前仍有效的快取會保留。"
            },
            cleanExpiredAction = "清理過期項目",
            providerSettingsAction = "服務設定",
            myAnimeListClientSavedLabel = "Client ID 已儲存",
            trackingCredentialsNeededLabel = "設定 API 憑證後即可搜尋與同步",
            mappedSummaryTitle = "已對應",
            mappedSummaryCaption = "系列連結",
            readySummaryTitle = "可同步",
            readySummaryCaption = "服務寫入",
            conflictsSummaryTitle = "衝突",
            conflictsSummaryCaption = "需要檢視",
            failuresSummaryTitle = "失敗",
            failuresSummaryCaption = "已追蹤重試",
            trackingSyncPreviewTitle = "追蹤同步預覽",
            openLibraryAction = "開啟媒體庫",
            trackingNoLibraryText = "先建立本機媒體庫索引，再檢視外部進度同步。",
            trackingTableTitle = "追蹤表格",
            trackingTableDescription = "本機與服務進度",
            syncAllReadyAction = "同步所有可寫入項目",
            syncingAction = "同步中",
            trackingRowsEmptyText = "目前沒有追蹤資料列。請先在媒體庫詳情中連結本機系列到 MyAnimeList 或 Bangumi。",
            localSeriesHeader = "本機系列",
            providerHeader = "服務",
            actionHeader = "動作",
            mappingInspectorTitle = "對應檢視",
            mappingInspectorEmptyText = "選取追蹤資料列以檢視服務 ID、進度與預計同步行為。",
            animeIdLabel = "動畫 ID",
            providerUrlLabel = "服務網址",
            localSeriesIdLabel = "本機系列 ID",
            localProgressLabel = "本機進度",
            providerProgressLabel = "服務進度",
            confidenceLabel = "信心值",
            statusLabel = "狀態",
            conflictLabel = "衝突",
            externalWatchedLabel = "服務已看",
            nextRetryLabel = "下次重試",
            skippedLabel = "已略過",
            syncSelectedAction = "同步所選項目",
            refreshProviderStateAction = "重新讀取服務狀態",
            removeMappingAction = "移除對應",
            resolveConflictAction = "解決衝突",
            trackingPlannedControlsText = "服務讀回、移除對應與衝突解決控制仍在規劃中；可同步項目會走現有服務同步路徑。",
            readbackPendingLabel = "尚未讀回",
            readyStatusLabel = "可同步",
            reviewConflictAction = "檢視衝突",
            conflictStatusLabel = "衝突",
            externalProviderLabel = "外部服務",
            notLinkedLabel = "未連結",
            noLinkLabel = "無連結",
            needsMappingLabel = "需要對應",
            missingLocalSeriesLabel = "缺少本機系列",
            retryAtLabel = { epochMs -> "重試 ${epochMs.formatEpochTime()}" },
            failedAttemptsLabel = { attempts -> "失敗 x$attempts" },
            watchedCountLabel = { count -> "已看 $count 集" },
            searchAction = "搜尋",
            searchingAction = "搜尋中...",
            closeAction = "關閉",
            useAction = "使用",
            mappedAction = "已對應",
            posterLoadingLabel = "載入中",
            posterUnavailableLabel = "無法載入海報",
            danmakuDisplaySettingsTitle = "彈幕顯示",
            danmakuDisplaySettingsDescription = "控制符合或手動附加彈幕軌產生的 ASS 彈幕覆蓋。重新載入媒體或重新整理已快取彈幕後，播放器才會套用渲染變更。",
            visibilityLabel = "可見性",
            fontScaleLabel = "字型縮放",
            displayAreaLabel = "顯示範圍",
            showDanmakuAction = "顯示彈幕",
            hideDanmakuAction = "隱藏彈幕",
            opacityPercentLabel = "透明度 %",
            fontScalePercentLabel = "字型縮放 %",
            speedPercentLabel = "速度 %",
            densityPercentLabel = "密度 %",
            displayAreaPercentLabel = "顯示範圍 %",
            offsetMsLabel = "時間偏移 ms",
            keywordFiltersLabel = "關鍵字篩選，每行一個",
            regexFiltersLabel = "Regex 篩選，每行一個",
            offsetMsValueLabel = { offsetMs -> "$offsetMs ms" },
            saveDanmakuDisplayAction = "儲存彈幕顯示",
            resetDraftAction = "重設草稿",
            metadataMatchTitle = "中繼資料對應",
            metadataMatchDescription = { title ->
                "搜尋「$title」的服務中繼資料，並儲存系列層級對應。"
            },
            metadataMatchSearchTitleLabel = "搜尋標題",
            metadataMatchSelectProviderError = "請至少選擇一個要搜尋的服務。",
            metadataMatchNoCandidates = { title -> "找不到符合「$title」的服務候選項目。" },
            metadataMatchMyAnimeListUnavailable = "MyAnimeList 搜尋需要在「設定 > 服務」填入 Client ID",
            metadataMatchBangumiUnavailable = "Bangumi 搜尋需要有效的 API URL 與 User-Agent",
            metadataMatchCurrentMappingsPrefix = "目前對應：",
            metadataMatchEmptyState = "搜尋 MyAnimeList 或 Bangumi 以檢視候選項目並儲存對應。",
            metadataMatchMatchedTitlePrefix = "符合標題：",
        ),
    );

    companion object {
        fun fromStorageValue(value: String?): DesktopUiLanguage =
            entries.firstOrNull { it.storageValue == value } ?: ENGLISH
    }
}

private data class DesktopStrings(
    val tabTitles: Map<DesktopShellTab, String> = emptyMap(),
    val settingsSectionTitles: Map<DesktopSettingsSection, String> = emptyMap(),
    val mediaHub: String,
    val shellSubtitle: String,
    val searchLabel: String,
    val playerStatusPrefix: String,
    val episodesSuffix: String,
    val rescanLibrary: String,
    val settingsTitle: String,
    val settingsDescription: String,
    val languageTitle: String,
    val languageDescription: String,
    val uiLanguageLabel: String,
    val uiLanguagesValue: String,
    val appLabel: String,
    val primaryTargetsLabel: String,
    val supportedLabel: String,
    val privacyTitle: String,
    val credentialsPrivacyText: String,
    val playbackNeedsAttentionTitle: String,
    val preparingPlaybackTitle: String,
    val noMediaLoadedLabel: String,
    val playingLabel: String,
    val videoHostLabel: String,
    val openMediaFileAction: String,
    val homeAction: String,
    val libraryAction: String,
    val showPlayerChromeAction: String,
    val hidePlayerChromeAction: String,
    val previousEpisodeAction: String,
    val previousEpisodeWithTitle: (String) -> String,
    val nextEpisodeAction: String,
    val nextEpisodeWithTitle: (String) -> String,
    val backThirtySecondsAction: String,
    val backTenSecondsAction: String,
    val playAction: String,
    val forwardTenSecondsAction: String,
    val forwardThirtySecondsAction: String,
    val volumeLabel: String,
    val subtitleShortLabel: String,
    val hideDanmakuPanelAction: String,
    val showDanmakuPanelAction: String,
    val exitFullscreenAction: String,
    val enterFullscreenAction: String,
    val libraryStepLabel: String,
    val preparingMediaStepText: String,
    val playerRuntimeStepLabel: String,
    val libraryPreparationStepLabel: String,
    val danmakuTitle: String,
    val shownLabel: String,
    val hiddenLabel: String,
    val cacheLabel: String,
    val audioLabel: String,
    val subtitleLabel: String,
    val defaultLabel: String,
    val offLabel: String,
    val hideAction: String,
    val showAction: String,
    val opacityLabel: String,
    val densityLabel: String,
    val fontLabel: String,
    val speedLabel: String,
    val areaLabel: String,
    val offsetLabel: String,
    val librarySlicesTitle: String,
    val animeSeriesSliceLabel: String,
    val moviesSliceLabel: String,
    val ovasSpecialsSliceLabel: String,
    val allEpisodesSliceLabel: String,
    val favoritesSliceLabel: String,
    val nowPlayingTitle: String,
    val recentlyAddedTitle: String,
    val browseAllAction: String,
    val newlyAddedEmptyText: String,
    val recentlyAddedDetailLabel: (Long, String) -> String,
    val recentlyWatchedTitle: String,
    val recentPlaybackEmptyText: String,
    val watchedAtLabel: (Long) -> String,
    val myLibraryTitle: String,
    val seriesSummaryTitle: String,
    val matchedGroupsCaption: String,
    val favoritesSummaryTitle: String,
    val savedEpisodesCaption: String,
    val watchingSummaryTitle: String,
    val inProgressCaption: String,
    val homeLibraryEmptyText: String,
    val librarySnapshotTitle: String,
    val continueWatchingTitle: String,
    val noResumeQueueText: String,
    val loadedNowLabel: String,
    val resumeAtLabel: (Long) -> String,
    val resumeSavedPositionLabel: String,
    val savedPositionLabel: String,
    val lastWatchedLabel: String,
    val nextAfterLabel: (Long?) -> String,
    val startWatchingLibraryLabel: String,
    val episodeCountShortLabel: (Int) -> String,
    val libraryViewTitles: Map<WindowsLibraryView, String> = emptyMap(),
    val libraryHostSubtitle: String,
    val librarySearchLabel: String,
    val libraryToolbarCompactDescription: String,
    val libraryToolbarDescription: String,
    val episodeCountSummary: (Int, Int) -> String,
    val favoriteCountSummary: (Int) -> String,
    val subtitlesOnlyLabel: String,
    val pathSortLabel: String,
    val requireSubtitlesAction: String,
    val showAllSubtitlesAction: String,
    val favoritesOnlyAction: String,
    val showAllFavoritesAction: String,
    val sortByPathAction: String,
    val sortByTitleAction: String,
    val openLibraryImportPanelAction: String,
    val rescanFoldersAction: String,
    val localPcLabel: String,
    val indexingLabel: String,
    val externalListsLabel: String,
    val noLibraryLabel: String,
    val pairedDevicesLabel: String,
    val lanBrowserReadyLabel: String,
    val foldersLabel: String,
    val noFoldersLabel: String,
    val moreItemsLabel: (Int) -> String,
    val lastScanSummary: (Int, Int) -> String,
    val lastScanCountsSummary: (Int, Int) -> String,
    val libraryImportTitle: String,
    val aniRssRootCountLabel: (Int) -> String,
    val scanRunningLabel: String,
    val publishedLabel: String,
    val lastScanTitle: String,
    val reusedCountLabel: (Int) -> String,
    val notRunLabel: String,
    val addFolderAction: String,
    val importAniRssOutputAction: String,
    val rescanAllAction: String,
    val rescanAction: String,
    val scanningAction: String,
    val indexingLibraryRootsLabel: String,
    val registeredRootsTitle: String,
    val libraryImportEmptyText: String,
    val removeLibraryFolderTitle: String,
    val removeLibraryFolderText: (String) -> String,
    val removeFolderAction: String,
    val userSelectedFolderLabel: String,
    val aniRssOutputFolderLabel: String,
    val libraryRootAvailableLabel: String,
    val libraryRootMissingLabel: String,
    val lastScannedAtLabel: (Long) -> String,
    val notScannedYetLabel: String,
    val inspectorResizeHandleLabel: String,
    val resetInspectorWidthAction: String,
    val inspectorTitle: String,
    val inspectorEmptyText: String,
    val nextPlayableLabel: (String) -> String,
    val loadingAction: String,
    val preparingAction: String,
    val prepareAction: String,
    val prepareShortAction: String,
    val detailsAction: String,
    val favoriteAction: String,
    val unfavoriteAction: String,
    val showEpisodeDetailsAction: String,
    val checkCachedDanmakuAction: String,
    val moreEpisodeActionsAction: String,
    val preparePlaybackAction: String,
    val refreshEpisodeMetadataAction: String,
    val refreshingEpisodeMetadataAction: String,
    val refreshSeriesMetadataAction: String,
    val refreshingSeriesMetadataAction: String,
    val loadIntoPlayerAction: String,
    val refreshDanmakuAction: String,
    val attachLocalDanmakuAction: String,
    val attachLocalDanmakuShortAction: String,
    val removeOverlayAction: String,
    val clearDanmakuCacheAction: String,
    val clearCacheAction: String,
    val enableAutoNextAction: String,
    val disableAutoNextAction: String,
    val autoNextOnLabel: String,
    val autoNextOffLabel: String,
    val readinessTitle: String,
    val preparedPlaybackLabel: String,
    val prepareToInspectTracksLabel: String,
    val notPreparedLabel: String,
    val resumeValueLabel: (Long) -> String,
    val notCheckedYetLabel: String,
    val subtitlesIndexedLabel: (Int) -> String,
    val episodesTitle: String,
    val advancedTitle: String,
    val favoriteStatusLabel: String,
    val pairedLibraryTitle: String,
    val pairedLibraryDescription: String,
    val pairedLibraryServerUrlLabel: String,
    val pairingCodeLabel: String,
    val loadPairedCatalogAction: String,
    val pairedLibraryErrorLabel: (String) -> String,
    val pairedEpisodesLabel: (Int) -> String,
    val pairedProgressLabel: String,
    val savedRowsLabel: (Int) -> String,
    val pairedNextUpTitle: String,
    val pairedNextUpEmptyText: String,
    val pairedContinueWatchingTitle: String,
    val pairedContinueWatchingEmptyText: String,
    val searchPairedEpisodesLabel: String,
    val sortTitleAction: String,
    val sortPathAction: String,
    val showingLabel: String,
    val pairedEpisodesCountLabel: (Int, Int) -> String,
    val pairedCatalogEmptyText: String,
    val pairedServerEmptyText: String,
    val pairedFilterEmptyText: String,
    val resetFiltersAction: String,
    val preparedDesktopPlaybackLabel: (String, String) -> String,
    val sourceValueLabel: (String) -> String,
    val resumeValueText: (String) -> String,
    val startFromBeginningLabel: String,
    val loadIntoDesktopControllerAction: String,
    val unknownMediaLabel: String,
    val unknownDurationLabel: String,
    val noSubtitlesLabel: String,
    val subtitleCountLabel: (Int) -> String,
    val downloadFilterTitles: Map<DownloadQueueFilter, String> = emptyMap(),
    val downloadsActiveTitle: String,
    val downloadsActiveCaption: String,
    val downloadsQueuedTitle: String,
    val downloadsQueuedCaption: String,
    val downloadsCompletedTitle: String,
    val downloadsCompletedCaption: String,
    val downloadsFailedTitle: String,
    val downloadsFailedCaption: String,
    val downloadQueueTitle: String,
    val refreshDownloadQueueAction: String,
    val downloadQueuePolicyText: String,
    val downloadQueueEmptyText: String,
    val downloadQueueFilterEmptyText: (String) -> String,
    val openOutputFolderAction: String,
    val removeQueueItemAction: String,
    val removeDownloadTitle: String,
    val removeDownloadText: (String) -> String,
    val removeAction: String,
    val downloadSetupAuthorizedSourcesTitle: String,
    val downloadSetupAuthorizedSourcesText: String,
    val authorizedImportsOnlyLabel: String,
    val queueExecutionPlannedLabel: String,
    val importRootCountLabel: (Int) -> String,
    val addAniRssOutputFolderAction: String,
    val aniRssWebhookTitle: String,
    val noWebhookUrlText: String,
    val webhookUrlLabel: String,
    val webhookHeaderLabel: String,
    val webhookTokenLabel: String,
    val importRootsTitle: String,
    val noAniRssRootsText: String,
    val downloadInspectorTitle: String,
    val downloadInspectorEmptyText: String,
    val stateLabel: String,
    val progressLabel: String,
    val createdLabel: String,
    val updatedLabel: String,
    val outputLabel: String,
    val sourceLabel: String,
    val failureLabel: String,
    val openFolderAction: String,
    val pauseAction: String,
    val resumeAction: String,
    val retryAction: String,
    val cancelAction: String,
    val downloadExecutionPlannedText: String,
    val dandanplayProvidersTitle: String,
    val dandanplayProvidersDescription: String,
    val cacheExpiryLabel: String,
    val lastTestLabel: String,
    val apiBaseUrlLabel: String,
    val appIdOptionalLabel: String,
    val appSecretKeepLabel: String,
    val appSecretOptionalLabel: String,
    val cacheMaxAgeDaysLabel: String,
    val signedAuthAction: String,
    val credentialAuthAction: String,
    val currentAuthLabel: (String) -> String,
    val saveDandanplaySettingsAction: String,
    val testSavedAction: String,
    val clearAction: String,
    val cleanExpiredCacheAction: String,
    val clearDandanplayTitle: String,
    val clearDandanplayText: String,
    val cleanExpiredDandanplayTitle: String,
    val cleanExpiredDandanplayText: String,
    val externalAnimeListsTitle: String,
    val externalAnimeListsDescription: String,
    val myAnimeListTestLabel: String,
    val bangumiTestLabel: String,
    val myAnimeListClientIdLabel: String,
    val myAnimeListAccessTokenKeepLabel: String,
    val myAnimeListAccessTokenOptionalLabel: String,
    val myAnimeListClientSecretKeepLabel: String,
    val myAnimeListClientSecretOptionalLabel: String,
    val bangumiApiBaseUrlLabel: String,
    val bangumiUserAgentLabel: String,
    val bangumiUserAgentRequiredError: String,
    val bangumiAccessTokenKeepLabel: String,
    val bangumiAccessTokenOptionalLabel: String,
    val saveExternalListsAction: String,
    val connectMyAnimeListAction: String,
    val testMyAnimeListAction: String,
    val testBangumiAction: String,
    val clearMyAnimeListAction: String,
    val clearBangumiAction: String,
    val clearMyAnimeListTitle: String,
    val clearMyAnimeListText: String,
    val clearBangumiTitle: String,
    val clearBangumiText: String,
    val localServerTitle: String,
    val serverBaseUrlLabel: String,
    val lanUrlLabel: String,
    val lanUrlsLabel: String,
    val lanUrlNumberedLabel: (Int) -> String,
    val discoveryLabel: String,
    val openServerDashboardAction: String,
    val testLocalServerAction: String,
    val serverDashboardTitle: String,
    val pairingAndLanAccessTitle: String,
    val healthTitle: String,
    val notCheckedThisSessionLabel: String,
    val recentRequestsLabel: String,
    val connectedClientsLabel: String,
    val connectedClientsPlannedText: String,
    val bandwidthLabel: String,
    val bandwidthPlannedText: String,
    val recentServerRequestsTitle: String,
    val noServerRequestsText: String,
    val testServerAction: String,
    val copyAction: String,
    val testingStatusLabel: String,
    val okStatusLabel: String,
    val failedStatusLabel: String,
    val homeServerStatusTitle: String,
    val attentionNeededLabel: String,
    val onlineLabel: String,
    val noLanUrlDetectedLabel: String,
    val pairingLabel: String,
    val episodesLabel: String,
    val errorLabel: String,
    val metadataAndPostersTitle: String,
    val loadingLabel: String,
    val partialLabel: String,
    val waitingLabel: String,
    val metadataLoadingLabel: String,
    val metadataFailedLabel: String,
    val metadataReadyLabel: String,
    val metadataPartialLabel: String,
    val metadataNeededLabel: String,
    val metadataFailedShortLabel: String,
    val metadataReadyShortLabel: String,
    val metadataNeededShortLabel: String,
    val metadataLoadingDetail: String,
    val metadataFailedDetail: String,
    val metadataReadyDetail: String,
    val metadataPartialMatchedNoPosterDetail: String,
    val metadataPartialPosterNoTitleDetail: String,
    val metadataPartialGenericDetail: String,
    val metadataNeededDetail: String,
    val seriesMetadataLoadingDetail: String,
    val seriesMetadataFailedDetail: String,
    val seriesMetadataReadyDetail: String,
    val seriesMetadataPartialDetail: String,
    val seriesMetadataNeededDetail: String,
    val fileGroupLabel: (String) -> String,
    val postersReadySummary: (Int, Int) -> String,
    val refreshAction: String,
    val refreshingAction: String,
    val lastScanLabel: String,
    val groupsLabel: String,
    val externalSyncTitle: String,
    val notMappedLabel: String,
    val readyUpdatesLabel: (Int) -> String,
    val openTrackingAction: String,
    val downloadQueueReadyLabel: String,
    val downloadsImportDetail: String,
    val openDownloadsAction: String,
    val cachedDanmakuTitle: String,
    val notCheckedLabel: String,
    val manageCacheAction: String,
    val danmakuCacheSettingsTitle: String,
    val danmakuCacheSettingsDescription: String,
    val cachedEpisodesLabel: String,
    val openCacheManagerAction: String,
    val danmakuCacheManagerTitle: String,
    val cachedSummaryTitle: String,
    val expiredSummaryTitle: String,
    val commentsSummaryTitle: String,
    val cachedEventsCaption: String,
    val cacheDayRuleCaption: (Int) -> String,
    val persistedEntriesTitle: String,
    val noDandanplayCachesText: String,
    val selectedCacheTitle: String,
    val selectCachePromptText: String,
    val expiredStatusLabel: String,
    val cacheEntryCommentSummary: (Int, Long, String) -> String,
    val animeLabel: String,
    val episodeLabel: String,
    val mediaIdLabel: String,
    val fileLabel: String,
    val fileSizeLabel: String,
    val commentsLabel: String,
    val fetchedLabel: String,
    val shiftLabel: String,
    val unknownAnimeLabel: String,
    val unknownEpisodeLabel: String,
    val noneLabel: String,
    val shiftSecondsLabel: (Double) -> String,
    val assCacheLabel: String,
    val deleteCachedDanmakuTitle: String,
    val deleteCachedDanmakuText: (String) -> String,
    val deleteCacheAction: String,
    val cleanExpiredDanmakuCachesTitle: String,
    val cleanExpiredDanmakuCachesText: (Int) -> String,
    val cleanExpiredAction: String,
    val providerSettingsAction: String,
    val myAnimeListClientSavedLabel: String,
    val trackingCredentialsNeededLabel: String,
    val mappedSummaryTitle: String,
    val mappedSummaryCaption: String,
    val readySummaryTitle: String,
    val readySummaryCaption: String,
    val conflictsSummaryTitle: String,
    val conflictsSummaryCaption: String,
    val failuresSummaryTitle: String,
    val failuresSummaryCaption: String,
    val trackingSyncPreviewTitle: String,
    val openLibraryAction: String,
    val trackingNoLibraryText: String,
    val trackingTableTitle: String,
    val trackingTableDescription: String,
    val syncAllReadyAction: String,
    val syncingAction: String,
    val trackingRowsEmptyText: String,
    val localSeriesHeader: String,
    val providerHeader: String,
    val actionHeader: String,
    val mappingInspectorTitle: String,
    val mappingInspectorEmptyText: String,
    val animeIdLabel: String,
    val providerUrlLabel: String,
    val localSeriesIdLabel: String,
    val localProgressLabel: String,
    val providerProgressLabel: String,
    val confidenceLabel: String,
    val statusLabel: String,
    val conflictLabel: String,
    val externalWatchedLabel: String,
    val nextRetryLabel: String,
    val skippedLabel: String,
    val syncSelectedAction: String,
    val refreshProviderStateAction: String,
    val removeMappingAction: String,
    val resolveConflictAction: String,
    val trackingPlannedControlsText: String,
    val readbackPendingLabel: String,
    val readyStatusLabel: String,
    val reviewConflictAction: String,
    val conflictStatusLabel: String,
    val externalProviderLabel: String,
    val notLinkedLabel: String,
    val noLinkLabel: String,
    val needsMappingLabel: String,
    val missingLocalSeriesLabel: String,
    val retryAtLabel: (Long) -> String,
    val failedAttemptsLabel: (Int) -> String,
    val watchedCountLabel: (Int) -> String,
    val searchAction: String,
    val searchingAction: String,
    val closeAction: String,
    val useAction: String,
    val mappedAction: String,
    val posterLoadingLabel: String,
    val posterUnavailableLabel: String,
    val danmakuDisplaySettingsTitle: String,
    val danmakuDisplaySettingsDescription: String,
    val visibilityLabel: String,
    val fontScaleLabel: String,
    val displayAreaLabel: String,
    val showDanmakuAction: String,
    val hideDanmakuAction: String,
    val opacityPercentLabel: String,
    val fontScalePercentLabel: String,
    val speedPercentLabel: String,
    val densityPercentLabel: String,
    val displayAreaPercentLabel: String,
    val offsetMsLabel: String,
    val keywordFiltersLabel: String,
    val regexFiltersLabel: String,
    val offsetMsValueLabel: (Long) -> String,
    val saveDanmakuDisplayAction: String,
    val resetDraftAction: String,
    val metadataMatchTitle: String,
    val metadataMatchDescription: (String) -> String,
    val metadataMatchSearchTitleLabel: String,
    val metadataMatchSelectProviderError: String,
    val metadataMatchNoCandidates: (String) -> String,
    val metadataMatchMyAnimeListUnavailable: String,
    val metadataMatchBangumiUnavailable: String,
    val metadataMatchCurrentMappingsPrefix: String,
    val metadataMatchEmptyState: String,
    val metadataMatchMatchedTitlePrefix: String,
) {
    fun tabTitle(tab: DesktopShellTab): String = tabTitles[tab] ?: tab.title
    fun settingsSectionTitle(section: DesktopSettingsSection): String =
        settingsSectionTitles[section] ?: section.title
    fun libraryViewTitle(view: WindowsLibraryView): String = libraryViewTitles[view] ?: view.label
    fun downloadFilterTitle(filter: DownloadQueueFilter): String = downloadFilterTitles[filter] ?: filter.label
}

@Composable
private fun ShellHeader(
    selectedTab: DesktopShellTab,
    strings: DesktopStrings,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    searchFocusRequester: FocusRequester,
    onRefresh: () -> Unit,
    onShowSettings: () -> Unit,
    playerStatus: String,
    episodeCount: Int,
    isRefreshEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.Surface)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.width(180.dp)) {
            Text(strings.tabTitle(selectedTab), style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            Text(strings.shellSubtitle, color = DanmakuColors.TextMuted, maxLines = 1)
        }
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            label = { Text(strings.searchLabel) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            modifier = Modifier
                .weight(1f)
                .focusRequester(searchFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        onSubmitSearch()
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
        )
        StatusPill("${strings.playerStatusPrefix} $playerStatus", icon = Icons.Filled.PlayArrow, active = playerStatus == PlaybackStatus.PLAYING.name)
        StatusPill("$episodeCount ${strings.episodesSuffix}", icon = Icons.AutoMirrored.Filled.LibraryBooks)
        PlayerIconButton(
            imageVector = Icons.Filled.Refresh,
            contentDescription = strings.rescanLibrary,
            enabled = isRefreshEnabled,
            onClick = onRefresh,
        )
        PlayerIconButton(
            imageVector = Icons.Filled.MoreHoriz,
            contentDescription = strings.settingsTitle,
            onClick = onShowSettings,
        )
    }
}

@Composable
private fun AppNavigationRail(
    selectedTab: DesktopShellTab,
    strings: DesktopStrings,
    onTabSelected: (DesktopShellTab) -> Unit,
    playbackLabel: String?,
    playbackStatus: PlaybackStatus,
    episodeCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(DanmakuColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DanmakuColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("D", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Danmaku", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Text(strings.mediaHub, color = DanmakuColors.TextMuted, maxLines = 1)
            }
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        AppRailItem(
            icon = Icons.Filled.Home,
            label = strings.tabTitle(DesktopShellTab.HOME),
            selected = selectedTab == DesktopShellTab.HOME,
            onClick = { onTabSelected(DesktopShellTab.HOME) },
        )
        AppRailItem(
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            label = strings.tabTitle(DesktopShellTab.MEDIA_LIBRARY),
            count = episodeCount,
            selected = selectedTab == DesktopShellTab.MEDIA_LIBRARY,
            onClick = { onTabSelected(DesktopShellTab.MEDIA_LIBRARY) },
        )
        AppRailItem(
            icon = Icons.Filled.FolderOpen,
            label = strings.tabTitle(DesktopShellTab.DOWNLOADS),
            selected = selectedTab == DesktopShellTab.DOWNLOADS,
            onClick = { onTabSelected(DesktopShellTab.DOWNLOADS) },
        )
        AppRailItem(
            icon = Icons.Filled.Refresh,
            label = strings.tabTitle(DesktopShellTab.TRACKING),
            selected = selectedTab == DesktopShellTab.TRACKING,
            onClick = { onTabSelected(DesktopShellTab.TRACKING) },
        )
        AppRailItem(
            icon = Icons.Filled.PlayArrow,
            label = strings.tabTitle(DesktopShellTab.PLAYBACK),
            selected = selectedTab == DesktopShellTab.PLAYBACK,
            onClick = { onTabSelected(DesktopShellTab.PLAYBACK) },
        )
        AppRailItem(
            icon = Icons.Filled.MoreHoriz,
            label = strings.tabTitle(DesktopShellTab.PROFILE),
            selected = selectedTab == DesktopShellTab.PROFILE,
            onClick = { onTabSelected(DesktopShellTab.PROFILE) },
        )
        Divider(color = DanmakuColors.SurfaceRaised)
        Text(strings.librarySlicesTitle, color = DanmakuColors.TextMuted, fontWeight = FontWeight.Bold)
        listOf(
            strings.animeSeriesSliceLabel,
            strings.moviesSliceLabel,
            strings.ovasSpecialsSliceLabel,
            strings.allEpisodesSliceLabel,
            strings.favoritesSliceLabel,
        ).forEach { label ->
            Text(
                text = label,
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onTabSelected(DesktopShellTab.MEDIA_LIBRARY) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        NowPlayingRailCard(
            strings = strings,
            playbackLabel = playbackLabel,
            playbackStatus = playbackStatus,
            onOpenPlayer = { onTabSelected(DesktopShellTab.PLAYBACK) },
        )
    }
}

@Composable
private fun AppRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
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
        Icon(icon, contentDescription = label, tint = if (selected) DanmakuColors.Accent else DanmakuColors.TextMuted)
        Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        count?.takeIf { it > 0 }?.let {
            Text(
                text = it.toString(),
                color = if (selected) Color.White else DanmakuColors.TextMuted,
                maxLines = 1,
                modifier = Modifier
                    .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(999.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun NowPlayingRailCard(
    strings: DesktopStrings,
    playbackLabel: String?,
    playbackStatus: PlaybackStatus,
    onOpenPlayer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.72f))
            .clickable(onClick = onOpenPlayer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(strings.nowPlayingTitle, color = DanmakuColors.TextMuted, fontWeight = FontWeight.Bold)
        Text(
            playbackLabel ?: strings.noMediaLoadedLabel,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        StatusPill(
            text = playbackStatus.name.lowercase().replaceFirstChar { it.uppercase() },
            icon = Icons.Filled.PlayArrow,
            active = playbackStatus == PlaybackStatus.PLAYING,
        )
    }
}

@Composable
private fun HomeTab(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    seriesPosterById: Map<String, Path?>,
    playbackProgresses: List<PlaybackProgress>,
    favoriteMediaIds: Set<String>,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    externalAnimeSyncFailures: List<ExternalAnimeSyncFailure>,
    isIndexing: Boolean,
    isPreparingLocalPlayback: Boolean,
    isRefreshingSeriesPosters: Boolean,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    episodeCount: Int,
    networkUrls: List<String>,
    pairingToken: String,
    overlayStatus: String,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTracking: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val catalog = indexedLibrary?.catalog
    val series = remember(catalog) { catalog?.groupedSeries().orEmpty() }
    val seriesByMediaId = remember(series) {
        series
            .flatMap { librarySeries -> librarySeries.seasons.flatMap { season -> season.items.map { it.id to librarySeries } } }
            .toMap()
    }
    val seriesWatchSummaryById = remember(catalog, playbackProgresses) {
        catalog?.seriesWatchSummaryById(playbackProgresses).orEmpty()
    }
    val continueWatchingItems = remember(catalog, playbackProgresses) {
        catalog?.continueWatchingItems(playbackProgresses).orEmpty()
    }
    val nextUpItems = remember(catalog, playbackProgresses) {
        catalog?.nextUpItems(playbackProgresses).orEmpty()
    }
    val recentlyWatchedItems = remember(catalog, playbackProgresses) {
        catalog?.recentlyWatchedItems(playbackProgresses).orEmpty()
    }
    val recentlyAddedItems = remember(catalog) {
        catalog
            ?.items
            .orEmpty()
            .sortedWith(
                compareByDescending<LibraryMediaItem> { it.indexedAtEpochMs }
                    .thenByDescending { it.relativePath },
            )
    }
    val externalTrackingPlan = remember(catalog, externalAnimeMappings, playbackProgresses, externalAnimeSyncFailures) {
        catalog?.externalAnimeTrackingPlan(
            mappings = externalAnimeMappings,
            progresses = playbackProgresses,
            failures = externalAnimeSyncFailures,
        )
    }
    val metadataRefreshingCount = refreshingMetadataMediaIds.size + refreshingMetadataSeriesIds.size
    val posterReadyCount = seriesPosterById.values.count { it != null }

    TabScaffold {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 1120.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeMainColumn(
                        strings = strings,
                        playbackSnapshot = playbackSnapshot,
                        continueWatchingItems = continueWatchingItems,
                        nextUpItems = nextUpItems,
                        recentlyWatchedItems = recentlyWatchedItems,
                        recentlyAddedItems = recentlyAddedItems,
                        series = series,
                        seriesByMediaId = seriesByMediaId,
                        seriesPosterById = seriesPosterById,
                        seriesWatchSummaryById = seriesWatchSummaryById,
                        favoriteMediaIds = favoriteMediaIds,
                        isPreparingLocalPlayback = isPreparingLocalPlayback,
                        onOpenLibrary = onOpenLibrary,
                        onPlayLocalPlayback = onPlayLocalPlayback,
                    )
                    HomeStatusColumn(
                        strings = strings,
                        registeredRoots = registeredRoots,
                        episodeCount = episodeCount,
                        seriesCount = series.size,
                        networkUrls = networkUrls,
                        pairingToken = pairingToken,
                        overlayStatus = overlayStatus,
                        libraryError = libraryError,
                        lastScanStats = lastScanStats,
                        isIndexing = isIndexing,
                        isRefreshingSeriesPosters = isRefreshingSeriesPosters,
                        metadataRefreshingCount = metadataRefreshingCount,
                        posterReadyCount = posterReadyCount,
                        externalTrackingPlan = externalTrackingPlan,
                        dandanplayCacheStatus = dandanplayCacheStatus,
                        diagnosticLog = diagnosticLog,
                        onOpenLibrary = onOpenLibrary,
                        onOpenDownloads = onOpenDownloads,
                        onOpenSettings = onOpenSettings,
                        onOpenTracking = onOpenTracking,
                        onRefreshMetadata = onRefreshMetadata,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HomeMainColumn(
                        strings = strings,
                        playbackSnapshot = playbackSnapshot,
                        continueWatchingItems = continueWatchingItems,
                        nextUpItems = nextUpItems,
                        recentlyWatchedItems = recentlyWatchedItems,
                        recentlyAddedItems = recentlyAddedItems,
                        series = series,
                        seriesByMediaId = seriesByMediaId,
                        seriesPosterById = seriesPosterById,
                        seriesWatchSummaryById = seriesWatchSummaryById,
                        favoriteMediaIds = favoriteMediaIds,
                        isPreparingLocalPlayback = isPreparingLocalPlayback,
                        onOpenLibrary = onOpenLibrary,
                        onPlayLocalPlayback = onPlayLocalPlayback,
                        modifier = Modifier.weight(1f),
                    )
                    HomeStatusColumn(
                        strings = strings,
                        registeredRoots = registeredRoots,
                        episodeCount = episodeCount,
                        seriesCount = series.size,
                        networkUrls = networkUrls,
                        pairingToken = pairingToken,
                        overlayStatus = overlayStatus,
                        libraryError = libraryError,
                        lastScanStats = lastScanStats,
                        isIndexing = isIndexing,
                        isRefreshingSeriesPosters = isRefreshingSeriesPosters,
                        metadataRefreshingCount = metadataRefreshingCount,
                        posterReadyCount = posterReadyCount,
                        externalTrackingPlan = externalTrackingPlan,
                        dandanplayCacheStatus = dandanplayCacheStatus,
                        diagnosticLog = diagnosticLog,
                        onOpenLibrary = onOpenLibrary,
                        onOpenDownloads = onOpenDownloads,
                        onOpenSettings = onOpenSettings,
                        onOpenTracking = onOpenTracking,
                        onRefreshMetadata = onRefreshMetadata,
                        modifier = Modifier.width(332.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMainColumn(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    recentlyWatchedItems: List<LibraryPlaybackProgressItem>,
    recentlyAddedItems: List<LibraryMediaItem>,
    series: List<LibrarySeries>,
    seriesByMediaId: Map<String, LibrarySeries>,
    seriesPosterById: Map<String, Path?>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    favoriteMediaIds: Set<String>,
    isPreparingLocalPlayback: Boolean,
    onOpenLibrary: () -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeHeroCard(
            strings = strings,
            playbackSnapshot = playbackSnapshot,
            continueWatchingItems = continueWatchingItems,
            nextUpItems = nextUpItems,
            seriesByMediaId = seriesByMediaId,
            seriesPosterById = seriesPosterById,
            isPreparingLocalPlayback = isPreparingLocalPlayback,
            onOpenLibrary = onOpenLibrary,
            onPlayLocalPlayback = onPlayLocalPlayback,
        )
        HomeSectionHeader(
            title = strings.recentlyAddedTitle,
            actionLabel = strings.browseAllAction,
            onAction = onOpenLibrary,
        )
        if (recentlyAddedItems.isEmpty()) {
            EmptyState(strings.newlyAddedEmptyText)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recentlyAddedItems.take(4).forEach { mediaItem ->
                    HomeEpisodeCard(
                        strings = strings,
                        mediaItem = mediaItem,
                        coverPath = seriesByMediaId[mediaItem.id]?.let { seriesPosterById[it.id] },
                        progressPercent = null,
                        detail = mediaItem.recentlyAddedDetail(strings),
                        isPreparing = isPreparingLocalPlayback,
                        playContentDescription = strings.playAction,
                        onPlay = { onPlayLocalPlayback(mediaItem) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat((4 - recentlyAddedItems.take(4).size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        HomeSectionHeader(
            title = strings.recentlyWatchedTitle,
            actionLabel = strings.openLibraryAction,
            onAction = onOpenLibrary,
        )
        if (recentlyWatchedItems.isEmpty()) {
            EmptyState(strings.recentPlaybackEmptyText)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recentlyWatchedItems.take(4).forEach { item ->
                    HomeEpisodeCard(
                        strings = strings,
                        mediaItem = item.mediaItem,
                        coverPath = seriesByMediaId[item.mediaItem.id]?.let { seriesPosterById[it.id] },
                        progressPercent = item.progress.progressPercent(),
                        detail = strings.watchedAtLabel(item.progress.positionMs),
                        isPreparing = isPreparingLocalPlayback,
                        playContentDescription = strings.playAction,
                        onPlay = { onPlayLocalPlayback(item.mediaItem) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat((4 - recentlyWatchedItems.take(4).size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        HomeSectionHeader(
            title = strings.myLibraryTitle,
            actionLabel = strings.browseAllAction,
            onAction = onOpenLibrary,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = strings.seriesSummaryTitle,
                value = series.size.toString(),
                caption = strings.matchedGroupsCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.favoritesSummaryTitle,
                value = favoriteMediaIds.size.toString(),
                caption = strings.savedEpisodesCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.watchingSummaryTitle,
                value = continueWatchingItems.size.toString(),
                caption = strings.inProgressCaption,
                modifier = Modifier.weight(1f),
            )
        }
        if (series.isEmpty()) {
            EmptyState(strings.homeLibraryEmptyText, strings.openLibraryAction, onOpenLibrary)
        } else {
            SectionCard(strings.librarySnapshotTitle) {
                series.take(6).forEach { librarySeries ->
                    HomeSeriesSummaryRow(
                        strings = strings,
                        series = librarySeries,
                        coverPath = seriesPosterById[librarySeries.id],
                        watchSummary = seriesWatchSummaryById[librarySeries.id],
                        onClick = onOpenLibrary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeroCard(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    seriesByMediaId: Map<String, LibrarySeries>,
    seriesPosterById: Map<String, Path?>,
    isPreparingLocalPlayback: Boolean,
    onOpenLibrary: () -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val resumeCards = buildList {
        continueWatchingItems.take(3).forEach { item ->
            add(
                HomeResumeCardModel(
                    mediaItem = item.mediaItem,
                    detail = strings.resumeAtLabel(item.progress.positionMs),
                    progressPercent = item.progress.progressPercent(),
                    actionLabel = strings.resumeAction,
                ),
            )
        }
        nextUpItems.take(3).forEach { item ->
            add(
                HomeResumeCardModel(
                    mediaItem = item.mediaItem,
                    detail = item.nextUpLabel(strings),
                    progressPercent = item.progress?.progressPercent(),
                    actionLabel = item.nextUpActionLabel(strings),
                ),
            )
        }
    }.distinctBy { it.mediaItem.id }.take(3)

    SectionCard(strings.continueWatchingTitle) {
        if (resumeCards.isEmpty()) {
            EmptyState(strings.noResumeQueueText, strings.openLibraryAction, onOpenLibrary)
            return@SectionCard
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            resumeCards.forEach { card ->
                HomeResumeCard(
                    strings = strings,
                    card = card,
                    coverPath = seriesByMediaId[card.mediaItem.id]?.let { seriesPosterById[it.id] },
                    isPreparing = isPreparingLocalPlayback,
                    onPlay = { onPlayLocalPlayback(card.mediaItem) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        playbackSnapshot.source?.let { source ->
            MetadataRow(strings.loadedNowLabel, source.toString().redactToken())
        }
    }
}

private data class HomeResumeCardModel(
    val mediaItem: LibraryMediaItem,
    val detail: String,
    val progressPercent: Int?,
    val actionLabel: String,
)

@Composable
private fun HomeResumeCard(
    strings: DesktopStrings,
    card: HomeResumeCardModel,
    coverPath: Path?,
    isPreparing: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.74f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .clip(RoundedCornerShape(6.dp))
                .background(DanmakuColors.AccentSoft),
        ) {
            SeriesPosterImage(
                coverPath = coverPath,
                title = card.mediaItem.displaySeriesTitle(),
                modifier = Modifier.fillMaxSize(),
            )
            PlayerIconButton(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = card.actionLabel,
                enabled = !isPreparing,
                onClick = onPlay,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(card.mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(card.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.mediaItem.localSeriesLabel(strings)?.let { label ->
            Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniProgressBar(percent = card.progressPercent)
        Text(card.detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeEpisodeCard(
    strings: DesktopStrings,
    mediaItem: LibraryMediaItem,
    coverPath: Path?,
    progressPercent: Int?,
    detail: String,
    isPreparing: Boolean,
    playContentDescription: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.Surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SeriesPosterImage(
            coverPath = coverPath,
            title = mediaItem.displaySeriesTitle(),
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Text(mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        mediaItem.localSeriesLabel(strings)?.let { label ->
            Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniProgressBar(percent = progressPercent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            PlayerIconButton(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = playContentDescription,
                enabled = !isPreparing,
                onClick = onPlay,
            )
        }
    }
}

@Composable
private fun HomeSeriesSummaryRow(
    strings: DesktopStrings,
    series: LibrarySeries,
    coverPath: Path?,
    watchSummary: LibrarySeriesWatchSummary?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SeriesPosterImage(
            coverPath = coverPath,
            title = series.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(series.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(watchSummary.progressLabel(), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusPill(strings.episodeCountShortLabel(series.episodeCount))
    }
}

@Composable
private fun HomeStatusColumn(
    strings: DesktopStrings,
    registeredRoots: List<DesktopLibraryRoot>,
    episodeCount: Int,
    seriesCount: Int,
    networkUrls: List<String>,
    pairingToken: String,
    overlayStatus: String,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    isIndexing: Boolean,
    isRefreshingSeriesPosters: Boolean,
    metadataRefreshingCount: Int,
    posterReadyCount: Int,
    externalTrackingPlan: ExternalAnimeTrackingPlan?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTracking: () -> Unit,
    onRefreshMetadata: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OperationalStatusCard(
            icon = Icons.Filled.Computer,
            title = strings.homeServerStatusTitle,
            value = when {
                libraryError != null -> strings.attentionNeededLabel
                isIndexing -> strings.indexingLabel
                else -> strings.onlineLabel
            },
            detail = networkUrls.firstOrNull() ?: strings.noLanUrlDetectedLabel,
            statusColor = when {
                libraryError != null -> DanmakuColors.Warning
                isIndexing -> DanmakuColors.Info
                else -> DanmakuColors.Good
            },
            actionLabel = strings.openLibraryAction,
            onAction = onOpenLibrary,
        ) {
            MetadataRow(strings.pairingLabel, pairingToken)
            MetadataRow(strings.foldersLabel, registeredRoots.size.toString())
            MetadataRow(strings.episodesLabel, episodeCount.toString())
            libraryError?.let { MetadataRow(strings.errorLabel, it, DanmakuColors.Warning) }
        }
        OperationalStatusCard(
            icon = Icons.Filled.GridView,
            title = strings.metadataAndPostersTitle,
            value = when {
                isRefreshingSeriesPosters || metadataRefreshingCount > 0 -> strings.loadingLabel
                posterReadyCount > 0 -> strings.readyStatusLabel
                seriesCount > 0 -> strings.partialLabel
                else -> strings.waitingLabel
            },
            detail = strings.postersReadySummary(posterReadyCount, seriesCount),
            statusColor = when {
                isRefreshingSeriesPosters || metadataRefreshingCount > 0 -> DanmakuColors.Info
                posterReadyCount > 0 -> DanmakuColors.Good
                seriesCount > 0 -> DanmakuColors.Warning
                else -> DanmakuColors.TextMuted
            },
            actionLabel = if (isRefreshingSeriesPosters || metadataRefreshingCount > 0) strings.refreshingAction else strings.refreshAction,
            actionEnabled = !isRefreshingSeriesPosters,
            onAction = onRefreshMetadata,
        ) {
            lastScanStats?.let {
                MetadataRow(strings.lastScanLabel, strings.lastScanCountsSummary(it.reusedItemCount, it.refreshedItemCount))
            }
            MetadataRow(strings.groupsLabel, seriesCount.toString())
        }
        OperationalStatusCard(
            icon = Icons.Filled.Refresh,
            title = strings.externalSyncTitle,
            value = externalTrackingPlan?.summary?.label ?: strings.notMappedLabel,
            detail = strings.readyUpdatesLabel(externalTrackingPlan?.summary?.updateCount ?: 0),
            statusColor = if ((externalTrackingPlan?.summary?.updateCount ?: 0) > 0) DanmakuColors.Info else DanmakuColors.TextMuted,
            actionLabel = strings.openTrackingAction,
            onAction = onOpenTracking,
        )
        OperationalStatusCard(
            icon = Icons.Filled.FolderOpen,
            title = strings.tabTitle(DesktopShellTab.DOWNLOADS),
            value = strings.downloadQueueReadyLabel,
            detail = strings.downloadsImportDetail,
            statusColor = DanmakuColors.TextMuted,
            actionLabel = strings.openDownloadsAction,
            onAction = onOpenDownloads,
        )
        OperationalStatusCard(
            icon = if (dandanplayCacheStatus?.summary?.isDandanplayWarningStatus() == true) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            title = strings.cachedDanmakuTitle,
            value = dandanplayCacheStatus?.summary ?: strings.notCheckedLabel,
            detail = overlayStatus,
            statusColor = when {
                dandanplayCacheStatus == null -> DanmakuColors.TextMuted
                dandanplayCacheStatus.summary.isDandanplayWarningStatus() -> DanmakuColors.Warning
                else -> DanmakuColors.Good
            },
            actionLabel = strings.manageCacheAction,
            onAction = onOpenSettings,
        )
        DiagnosticsPanel(diagnosticLog, modifier = Modifier.heightIn(max = 280.dp))
    }
}

@Composable
private fun OperationalStatusCard(
    icon: ImageVector,
    title: String,
    value: String,
    detail: String,
    statusColor: Color,
    actionLabel: String,
    actionEnabled: Boolean = true,
    onAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    SectionCard(title) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = DanmakuColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        content()
        LibraryActionButton(
            imageVector = Icons.Filled.PlayArrow,
            label = actionLabel,
            enabled = actionEnabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = onAction,
        )
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        LibraryActionButton(
            imageVector = Icons.AutoMirrored.Filled.ViewList,
            label = actionLabel,
            onClick = onAction,
        )
    }
}

@Composable
private fun TrackingTab(
    strings: DesktopStrings,
    indexedLibrary: IndexedLocalLibrary?,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    playbackProgresses: List<PlaybackProgress>,
    externalAnimeSyncFailures: List<ExternalAnimeSyncFailure>,
    isExternalAnimeSyncing: Boolean,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSyncExternalAnimePlan: (ExternalAnimeTrackingPlan) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val catalog = indexedLibrary?.catalog
    val plan = remember(catalog, externalAnimeMappings, playbackProgresses, externalAnimeSyncFailures) {
        catalog?.externalAnimeTrackingPlan(
            mappings = externalAnimeMappings,
            progresses = playbackProgresses,
            failures = externalAnimeSyncFailures,
        )
    }
    TabScaffold {
        val seriesById = remember(catalog) {
            catalog?.groupedSeries()?.associateBy(LibrarySeries::id).orEmpty()
        }
        val trackingRows = remember(plan, externalAnimeMappings, seriesById) {
            buildTrackingTableRows(
                strings = strings,
                plan = plan,
                mappings = externalAnimeMappings,
                seriesById = seriesById,
            )
        }
        var selectedTrackingRowId by remember(trackingRows) {
            mutableStateOf(trackingRows.firstOrNull()?.id)
        }
        val selectedTrackingRow = trackingRows.firstOrNull { it.id == selectedTrackingRowId }
            ?: trackingRows.firstOrNull()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackingProviderCard(
                title = "MyAnimeList",
                status = externalAnimeProviderSettings.myAnimeListStatusText,
                ready = externalAnimeProviderSettings.hasMyAnimeListAccessToken,
                detail = if (externalAnimeProviderSettings.myAnimeListClientId != null) {
                    strings.myAnimeListClientSavedLabel
                } else {
                    strings.trackingCredentialsNeededLabel
                },
                actionLabel = strings.providerSettingsAction,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
            TrackingProviderCard(
                title = "Bangumi",
                status = externalAnimeProviderSettings.bangumiStatusText,
                ready = externalAnimeProviderSettings.hasBangumiAccessToken,
                detail = externalAnimeProviderSettings.bangumiBaseUrl,
                actionLabel = strings.providerSettingsAction,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = strings.mappedSummaryTitle,
                value = externalAnimeMappings.size.toString(),
                caption = strings.mappedSummaryCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.readySummaryTitle,
                value = (plan?.summary?.updateCount ?: 0).toString(),
                caption = strings.readySummaryCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.conflictsSummaryTitle,
                value = (plan?.summary?.conflictCount ?: 0).toString(),
                caption = strings.conflictsSummaryCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.failuresSummaryTitle,
                value = (plan?.summary?.failureCount ?: 0).toString(),
                caption = strings.failuresSummaryCaption,
                modifier = Modifier.weight(1f),
            )
        }
        HomeSectionHeader(
            title = strings.trackingSyncPreviewTitle,
            actionLabel = strings.openLibraryAction,
            onAction = onOpenLibrary,
        )
        if (catalog == null) {
            EmptyState(strings.trackingNoLibraryText, strings.openLibraryAction, onOpenLibrary)
        } else {
            TrackingWorkspace(
                strings = strings,
                plan = plan,
                rows = trackingRows,
                selectedRow = selectedTrackingRow,
                isSyncing = isExternalAnimeSyncing,
                onSelectRow = { row -> selectedTrackingRowId = row.id },
                onSync = onSyncExternalAnimePlan,
            )
        }
    }
}

private enum class TrackingRowKind {
    UPDATE,
    CONFLICT,
    SKIPPED,
    FAILURE,
}

private data class TrackingTableRow(
    val id: String,
    val kind: TrackingRowKind,
    val seriesTitle: String,
    val localSeriesId: String,
    val providerLabel: String,
    val animeIdText: String,
    val providerUrl: String?,
    val localProgress: String,
    val providerProgress: String,
    val plannedAction: String,
    val confidence: String,
    val statusLabel: String,
    val statusColor: Color,
    val mapping: ExternalAnimeMapping?,
    val update: ExternalAnimeTrackingPlanUpdate? = null,
    val conflict: app.danmaku.domain.ExternalAnimeTrackingPlanConflict? = null,
    val skip: app.danmaku.domain.ExternalAnimeTrackingPlanSkip? = null,
    val failure: ExternalAnimeSyncFailure? = null,
)

private fun buildTrackingTableRows(
    strings: DesktopStrings,
    plan: ExternalAnimeTrackingPlan?,
    mappings: List<ExternalAnimeMapping>,
    seriesById: Map<String, LibrarySeries>,
): List<TrackingTableRow> {
    if (plan == null) {
        return emptyList()
    }
    val mappingsByAnimeId = mappings.associateBy(ExternalAnimeMapping::animeId)
    val mappingsBySeriesAndProvider = mappings.associateBy { it.localSeriesId to it.animeId.provider }
    val updateRows = plan.updates.map { update ->
        val mapping = update.mapping
        TrackingTableRow(
            id = "update-${mapping.localSeriesId}-${mapping.animeId.provider}-${mapping.animeId.value}",
            kind = TrackingRowKind.UPDATE,
            seriesTitle = update.series.title,
            localSeriesId = mapping.localSeriesId,
            providerLabel = mapping.animeId.provider.displayName,
            animeIdText = mapping.animeId.value.toString(),
            providerUrl = mapping.animeId.webUrl,
            localProgress = "${update.update.watchedEpisodes ?: 0}/${update.series.episodeCount}",
            providerProgress = strings.readbackPendingLabel,
            plannedAction = "${update.update.status.displayName}, ${strings.watchedCountLabel(update.update.watchedEpisodes ?: 0)}",
            confidence = mapping.confidence.formatConfidence(),
            statusLabel = strings.readyStatusLabel,
            statusColor = DanmakuColors.Good,
            mapping = mapping,
            update = update,
        )
    }
    val conflictRows = plan.conflicts.map { conflict ->
        val mapping = conflict.mapping
        TrackingTableRow(
            id = "conflict-${mapping.localSeriesId}-${mapping.animeId.provider}-${mapping.animeId.value}",
            kind = TrackingRowKind.CONFLICT,
            seriesTitle = conflict.series.title,
            localSeriesId = mapping.localSeriesId,
            providerLabel = mapping.animeId.provider.displayName,
            animeIdText = mapping.animeId.value.toString(),
            providerUrl = mapping.animeId.webUrl,
            localProgress = "${conflict.localUpdate.watchedEpisodes ?: 0}/${conflict.series.episodeCount}",
            providerProgress = "${conflict.externalEntry.watchedEpisodes ?: 0}/${conflict.series.episodeCount}",
            plannedAction = strings.reviewConflictAction,
            confidence = mapping.confidence.formatConfidence(),
            statusLabel = strings.conflictStatusLabel,
            statusColor = DanmakuColors.Warning,
            mapping = mapping,
            conflict = conflict,
        )
    }
    val skippedRows = plan.skipped.map { skip ->
        val mapping = skip.provider?.let { provider ->
            mappingsBySeriesAndProvider[skip.localSeriesId to provider]
        }
        val series = seriesById[skip.localSeriesId]
        TrackingTableRow(
            id = "skip-${skip.localSeriesId}-${skip.provider?.name ?: "provider"}-${skip.reason.name}",
            kind = TrackingRowKind.SKIPPED,
            seriesTitle = series?.title ?: skip.localSeriesId,
            localSeriesId = skip.localSeriesId,
            providerLabel = skip.provider?.displayName ?: strings.externalProviderLabel,
            animeIdText = mapping?.animeId?.value?.toString() ?: strings.notLinkedLabel,
            providerUrl = mapping?.animeId?.webUrl,
            localProgress = series?.let { "0/${it.episodeCount}" } ?: "-",
            providerProgress = "-",
            plannedAction = skip.reason.displayName,
            confidence = mapping?.confidence?.formatConfidence() ?: strings.noLinkLabel,
            statusLabel = if (mapping == null) strings.needsMappingLabel else strings.missingLocalSeriesLabel,
            statusColor = DanmakuColors.TextMuted,
            mapping = mapping,
            skip = skip,
        )
    }
    val failureRows = plan.failures.map { failure ->
        val mapping = mappingsByAnimeId[failure.animeId]
        val series = mapping?.let { seriesById[it.localSeriesId] }
        TrackingTableRow(
            id = "failure-${failure.animeId.provider}-${failure.animeId.value}-${failure.failedAtEpochMs}",
            kind = TrackingRowKind.FAILURE,
            seriesTitle = series?.title ?: failure.animeId.webUrl,
            localSeriesId = mapping?.localSeriesId ?: "-",
            providerLabel = failure.animeId.provider.displayName,
            animeIdText = failure.animeId.value.toString(),
            providerUrl = failure.animeId.webUrl,
            localProgress = "-",
            providerProgress = strings.retryAtLabel(failure.retryAfterEpochMs),
            plannedAction = failure.message,
            confidence = mapping?.confidence?.formatConfidence() ?: "-",
            statusLabel = strings.failedAttemptsLabel(failure.attemptCount),
            statusColor = DanmakuColors.Warning,
            mapping = mapping,
            failure = failure,
        )
    }
    return (conflictRows + updateRows + failureRows + skippedRows).sortedWith(
        compareBy<TrackingTableRow> { it.kind.ordinal }
            .thenBy { it.seriesTitle.lowercase() }
            .thenBy { it.providerLabel }
            .thenBy { it.animeIdText },
    )
}

@Composable
private fun TrackingWorkspace(
    strings: DesktopStrings,
    plan: ExternalAnimeTrackingPlan?,
    rows: List<TrackingTableRow>,
    selectedRow: TrackingTableRow?,
    isSyncing: Boolean,
    onSelectRow: (TrackingTableRow) -> Unit,
    onSync: (ExternalAnimeTrackingPlan) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 1180.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TrackingTablePanel(
                    strings = strings,
                    plan = plan,
                    rows = rows,
                    selectedRow = selectedRow,
                    isSyncing = isSyncing,
                    onSelectRow = onSelectRow,
                    onSync = onSync,
                )
                TrackingInspectorPanel(
                    strings = strings,
                    selectedRow = selectedRow,
                    isSyncing = isSyncing,
                    onSync = onSync,
                )
                ExternalSyncPreviewView(plan = plan, isSyncing = isSyncing, onSync = onSync)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TrackingTablePanel(
                        strings = strings,
                        plan = plan,
                        rows = rows,
                        selectedRow = selectedRow,
                        isSyncing = isSyncing,
                        onSelectRow = onSelectRow,
                        onSync = onSync,
                    )
                    ExternalSyncPreviewView(plan = plan, isSyncing = isSyncing, onSync = onSync)
                }
                TrackingInspectorPanel(
                    strings = strings,
                    selectedRow = selectedRow,
                    isSyncing = isSyncing,
                    onSync = onSync,
                    modifier = Modifier.width(380.dp),
                )
            }
        }
    }
}

@Composable
private fun TrackingTablePanel(
    strings: DesktopStrings,
    plan: ExternalAnimeTrackingPlan?,
    rows: List<TrackingTableRow>,
    selectedRow: TrackingTableRow?,
    isSyncing: Boolean,
    onSelectRow: (TrackingTableRow) -> Unit,
    onSync: (ExternalAnimeTrackingPlan) -> Unit,
) {
    SectionCard(strings.trackingTableTitle) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(strings.trackingTableDescription, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(
                enabled = plan?.updates?.isNotEmpty() == true && !isSyncing,
                onClick = { plan?.let(onSync) },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isSyncing) strings.syncingAction else strings.syncAllReadyAction)
            }
        }
        if (rows.isEmpty()) {
            EmptyState(strings.trackingRowsEmptyText)
            return@SectionCard
        }
        TrackingTableHeader(strings)
        LazyColumn(
            modifier = Modifier.heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(rows, key = TrackingTableRow::id) { row ->
                TrackingTableRowView(
                    row = row,
                    selected = selectedRow?.id == row.id,
                    onClick = { onSelectRow(row) },
                )
            }
        }
    }
}

@Composable
private fun TrackingTableHeader(strings: DesktopStrings) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(strings.localSeriesHeader, color = DanmakuColors.TextMuted, modifier = Modifier.weight(1.6f), maxLines = 1)
        Text(strings.providerHeader, color = DanmakuColors.TextMuted, modifier = Modifier.weight(1.0f), maxLines = 1)
        Text(strings.progressLabel, color = DanmakuColors.TextMuted, modifier = Modifier.weight(0.9f), maxLines = 1)
        Text(strings.actionHeader, color = DanmakuColors.TextMuted, modifier = Modifier.weight(1.2f), maxLines = 1)
        Text(strings.statusLabel, color = DanmakuColors.TextMuted, modifier = Modifier.weight(0.9f), maxLines = 1)
    }
}

@Composable
private fun TrackingTableRowView(
    row: TrackingTableRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.AccentSoft else DanmakuColors.SurfaceRaised.copy(alpha = 0.58f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1.6f)) {
            Text(row.seriesTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.localSeriesId, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(modifier = Modifier.weight(1.0f)) {
            Text(row.providerLabel, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("#${row.animeIdText}", color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(modifier = Modifier.weight(0.9f)) {
            Text(row.localProgress, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.providerProgress, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(row.plannedAction, color = Color.White, modifier = Modifier.weight(1.2f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        StatusPill(row.statusLabel, active = row.kind == TrackingRowKind.UPDATE, color = row.statusColor, modifier = Modifier.weight(0.9f))
    }
}

@Composable
private fun TrackingInspectorPanel(
    strings: DesktopStrings,
    selectedRow: TrackingTableRow?,
    isSyncing: Boolean,
    onSync: (ExternalAnimeTrackingPlan) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(strings.mappingInspectorTitle, modifier = modifier) {
        if (selectedRow == null) {
            EmptyState(strings.mappingInspectorEmptyText)
            return@SectionCard
        }
        Text(selectedRow.seriesTitle, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        MetadataRow(strings.providerHeader, selectedRow.providerLabel)
        MetadataRow(strings.animeIdLabel, selectedRow.animeIdText)
        selectedRow.providerUrl?.let { MetadataRow(strings.providerUrlLabel, it) }
        MetadataRow(strings.localSeriesIdLabel, selectedRow.localSeriesId)
        MetadataRow(strings.localProgressLabel, selectedRow.localProgress)
        MetadataRow(strings.providerProgressLabel, selectedRow.providerProgress)
        MetadataRow(strings.confidenceLabel, selectedRow.confidence)
        MetadataRow(strings.statusLabel, selectedRow.statusLabel, selectedRow.statusColor)
        selectedRow.conflict?.let { conflict ->
            MetadataRow(strings.conflictLabel, conflict.reason.displayName, DanmakuColors.Warning)
            MetadataRow(strings.externalWatchedLabel, (conflict.externalEntry.watchedEpisodes ?: 0).toString())
        }
        selectedRow.failure?.let { failure ->
            MetadataRow(strings.failureLabel, failure.message, DanmakuColors.Warning)
            MetadataRow(strings.nextRetryLabel, failure.retryAfterEpochMs.formatEpochTime())
        }
        selectedRow.skip?.let { skip ->
            MetadataRow(strings.skippedLabel, skip.reason.displayName)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = selectedRow.update != null && !isSyncing,
                onClick = {
                    selectedRow.update?.let { update ->
                        onSync(
                            ExternalAnimeTrackingPlan(
                                updates = listOf(update),
                                skipped = emptyList(),
                            ),
                        )
                    }
                },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isSyncing) strings.syncingAction else strings.syncSelectedAction)
            }
            Button(enabled = false, onClick = {}) {
                Text(strings.refreshProviderStateAction)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = false, onClick = {}) {
                Text(strings.removeMappingAction)
            }
            Button(enabled = false, onClick = {}) {
                Text(strings.resolveConflictAction)
            }
        }
        Text(
            strings.trackingPlannedControlsText,
            color = DanmakuColors.TextMuted,
        )
    }
}

@Composable
private fun TrackingProviderCard(
    title: String,
    status: String,
    ready: Boolean,
    detail: String,
    actionLabel: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(title, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (ready) DanmakuColors.Good else DanmakuColors.Warning,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(status, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LibraryActionButton(
            imageVector = Icons.Filled.MoreHoriz,
            label = actionLabel,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenSettings,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun PlaybackTab(
    strings: DesktopStrings,
    playbackLabel: String?,
    playbackSnapshot: PlaybackSnapshot,
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    overlayStatus: String,
    danmakuSettings: DanmakuDisplaySettings,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    isPreparingLocalPlayback: Boolean,
    libraryError: String?,
    onWindowIdChanged: (Long?) -> Unit,
    onMpvPointerMove: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    onMpvPrimaryClick: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    onMpvWheel: (x: Int, y: Int, width: Int, height: Int, rotation: Int) -> Unit,
    previousEpisodeLabel: String?,
    nextEpisodeLabel: String?,
    onPlayPreviousEpisode: (() -> Unit)?,
    onPlayNextEpisode: (() -> Unit)?,
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
    onSaveDanmakuSettings: (DanmakuDisplaySettings) -> Unit,
    onShowHome: () -> Unit,
    onShowLibrary: () -> Unit,
    canOpenMedia: Boolean,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteractionSerial by remember { mutableStateOf(0L) }
    var isFocusMode by remember { mutableStateOf(false) }
    var rightPanelVisible by remember { mutableStateOf(true) }
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
        revealControls()
        when (shortcut) {
            DesktopPlayerShortcut.TOGGLE_FOCUS_MODE -> {
                isFocusMode = !isFocusMode
            }
            DesktopPlayerShortcut.TOGGLE_PLAY_PAUSE -> {
                if (!hasMedia) return false
                if (playbackSnapshot.status == PlaybackStatus.PLAYING) {
                    onPause()
                } else {
                    onPlay()
                }
            }
            DesktopPlayerShortcut.SEEK_BACKWARD -> {
                if (!hasMedia) return false
                onSeekBackward()
            }
            DesktopPlayerShortcut.SEEK_BACKWARD_LARGE -> {
                if (!hasMedia) return false
                onSeekBackwardLarge()
            }
            DesktopPlayerShortcut.SEEK_FORWARD -> {
                if (!hasMedia) return false
                onSeekForward()
            }
            DesktopPlayerShortcut.SEEK_FORWARD_LARGE -> {
                if (!hasMedia) return false
                onSeekForwardLarge()
            }
            DesktopPlayerShortcut.VOLUME_UP -> {
                if (!hasMedia) return false
                onSetVolume((playbackSnapshot.volumePercent + 5).coerceIn(0, 100))
            }
            DesktopPlayerShortcut.VOLUME_DOWN -> {
                if (!hasMedia) return false
                onSetVolume((playbackSnapshot.volumePercent - 5).coerceIn(0, 100))
            }
            DesktopPlayerShortcut.CYCLE_PLAYBACK_RATE -> {
                if (!hasMedia) return false
                onSetPlaybackRate(playbackSnapshot.playbackRate.nextPlaybackRate())
            }
            DesktopPlayerShortcut.CYCLE_AUDIO_TRACK -> {
                if (!hasMedia) return false
                playbackSnapshot.nextTrackId(PlaybackTrackKind.AUDIO)?.let(onSelectAudioTrack) ?: return false
            }
            DesktopPlayerShortcut.CYCLE_SUBTITLE_TRACK -> {
                if (!hasMedia) return false
                if (playbackSnapshot.tracks.none { it.kind == PlaybackTrackKind.SUBTITLE }) return false
                onSelectSubtitleTrack(playbackSnapshot.nextSubtitleTrackId())
            }
            DesktopPlayerShortcut.CYCLE_ASPECT_MODE -> {
                if (!hasMedia) return false
                onSetVideoAspectMode(videoAspectMode.nextAspectMode())
            }
            DesktopPlayerShortcut.TOGGLE_FULLSCREEN -> {
                if (!hasMedia) return false
                onSetFullscreen(!isFullscreen)
            }
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
            title = playbackLabel ?: playbackSnapshot.source?.toString()?.redactToken() ?: strings.noMediaLoadedLabel,
            status = playbackSnapshot.status.name,
            overlayStatus = overlayStatus,
            positionMs = 0L,
            durationMs = null,
            isPlaying = false,
            volumePercent = playbackSnapshot.volumePercent,
            playbackRate = playbackSnapshot.playbackRate,
            audioText = strings.audioLabel,
            subtitleText = strings.subtitleShortLabel,
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
        if (hasMedia && !isFullscreen && !isFocusMode) {
            PlaybackWindowNavigationHeader(
                strings = strings,
                title = playbackLabel ?: playbackSnapshot.source?.toString()?.redactToken() ?: strings.playingLabel,
                status = playbackSnapshot.status.name,
                overlayStatus = overlayStatus,
                isFocusMode = isFocusMode,
                onShowHome = onShowHome,
                onShowLibrary = onShowLibrary,
                onToggleFocusMode = {
                    isFocusMode = !isFocusMode
                    revealControls()
                },
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
                if (!isFocusMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PLAYER_TOP_CONTROLS_HEIGHT_DP.dp)
                            .align(Alignment.TopCenter)
                            .revealControlsOnPointerInput(),
                    ) {
                        PlayerTopOverlay(
                            strings = strings,
                            title = playbackLabel ?: playbackSnapshot.source?.toString()?.redactToken() ?: strings.noMediaLoadedLabel,
                            status = playbackSnapshot.status.name,
                            overlayStatus = overlayStatus,
                            isFullscreen = isFullscreen,
                            isFocusMode = isFocusMode,
                            onShowHome = onShowHome,
                            onShowLibrary = onShowLibrary,
                            onToggleFocusMode = {
                                isFocusMode = !isFocusMode
                                revealControls()
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .revealControlsOnPointerInput(),
                        )
                    }
                }
                PlayerEmptyOverlay(
                    strings = strings,
                    canOpenMedia = canOpenMedia,
                    mpvRuntimeStatus = mpvRuntimeStatus,
                    videoHostStatus = videoHostStatus,
                    onOpenMediaFile = onOpenMediaFile,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PLAYER_BOTTOM_CONTROLS_HEIGHT_DP.dp)
                        .align(Alignment.BottomCenter)
                        .revealControlsOnPointerInput(),
                ) {
                    PlayerBottomOverlay(
                        strings = strings,
                        playbackSnapshot = playbackSnapshot,
                        isFullscreen = isFullscreen,
                        videoAspectMode = videoAspectMode,
                        previousEpisodeLabel = previousEpisodeLabel,
                        nextEpisodeLabel = nextEpisodeLabel,
                        onPlayPreviousEpisode = onPlayPreviousEpisode,
                        onPlayNextEpisode = onPlayNextEpisode,
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
                        isFocusMode = isFocusMode,
                        rightPanelVisible = rightPanelVisible,
                        onToggleFocusMode = {
                            isFocusMode = !isFocusMode
                            revealControls()
                        },
                        onToggleRightPanel = {
                            rightPanelVisible = !rightPanelVisible
                            revealControls()
                        },
                        canOpenMedia = canOpenMedia,
                        modifier = Modifier
                            .fillMaxSize()
                            .revealControlsOnPointerInput(),
                    )
                }
            }
            if (
                controlsVisible &&
                !isFocusMode &&
                (isPreparingLocalPlayback || playbackSnapshot.errorMessage != null || libraryError != null)
            ) {
                PlayerPreparationStatusOverlay(
                    strings = strings,
                    isPreparingLocalPlayback = isPreparingLocalPlayback,
                    playbackError = playbackSnapshot.errorMessage,
                    libraryError = libraryError,
                    dandanplayCacheStatus = dandanplayCacheStatus,
                    modifier = Modifier
                        .align(if (hasMedia) Alignment.TopCenter else Alignment.Center)
                        .padding(top = if (hasMedia) 84.dp else 0.dp)
                        .width(420.dp)
                        .revealControlsOnPointerInput(),
                )
            }
            if (hasMedia && controlsVisible && !isFocusMode && !isFullscreen && rightPanelVisible) {
                PlayerRightPanel(
                    strings = strings,
                    playbackSnapshot = playbackSnapshot,
                    overlayStatus = overlayStatus,
                    dandanplayCacheStatus = dandanplayCacheStatus,
                    danmakuSettings = danmakuSettings,
                    onSaveDanmakuSettings = onSaveDanmakuSettings,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 74.dp, end = 18.dp, bottom = 132.dp)
                        .width(320.dp)
                        .revealControlsOnPointerInput(),
                )
            }
            if (isFocusMode && controlsVisible && !isFullscreen) {
                PlayerFocusRestoreButton(
                    onClick = {
                        isFocusMode = false
                        revealControls()
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .revealControlsOnPointerInput(),
                )
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
        Key.H -> DesktopPlayerShortcutKey.H
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
    strings: DesktopStrings,
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
        Text(strings.noMediaLoadedLabel, color = Color.White, style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "$mpvRuntimeStatus ${strings.videoHostLabel}: $videoHostStatus",
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenMediaFile, enabled = canOpenMedia) {
            Text(strings.openMediaFileAction)
        }
    }
}

@Composable
private fun PlaybackWindowNavigationHeader(
    strings: DesktopStrings,
    title: String,
    status: String,
    overlayStatus: String,
    isFocusMode: Boolean,
    onShowHome: () -> Unit,
    onShowLibrary: () -> Unit,
    onToggleFocusMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayerIconButton(Icons.Filled.Home, strings.homeAction, onClick = onShowHome)
        PlayerIconButton(Icons.AutoMirrored.Filled.LibraryBooks, strings.libraryAction, onClick = onShowLibrary)
        PlayerIconButton(
            imageVector = if (isFocusMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = if (isFocusMode) strings.showPlayerChromeAction else strings.hidePlayerChromeAction,
            active = isFocusMode,
            onClick = onToggleFocusMode,
        )
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
    strings: DesktopStrings,
    title: String,
    status: String,
    overlayStatus: String,
    isFullscreen: Boolean,
    isFocusMode: Boolean,
    onShowHome: () -> Unit,
    onShowLibrary: () -> Unit,
    onToggleFocusMode: () -> Unit,
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
        PlayerIconButton(Icons.Filled.Home, strings.homeAction, onClick = onShowHome)
        PlayerIconButton(Icons.AutoMirrored.Filled.LibraryBooks, strings.libraryAction, onClick = onShowLibrary)
        PlayerIconButton(
            imageVector = if (isFocusMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = if (isFocusMode) strings.showPlayerChromeAction else strings.hidePlayerChromeAction,
            active = isFocusMode,
            onClick = onToggleFocusMode,
        )
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
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    isFullscreen: Boolean,
    videoAspectMode: DesktopVideoAspectMode,
    previousEpisodeLabel: String?,
    nextEpisodeLabel: String?,
    onPlayPreviousEpisode: (() -> Unit)?,
    onPlayNextEpisode: (() -> Unit)?,
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
    isFocusMode: Boolean,
    rightPanelVisible: Boolean,
    onToggleFocusMode: () -> Unit,
    onToggleRightPanel: () -> Unit,
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
                PlayerIconButton(Icons.Filled.FolderOpen, strings.openMediaFileAction, enabled = canOpenMedia, onClick = onOpenMediaFile)
                PlayerIconButton(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = previousEpisodeLabel?.let(strings.previousEpisodeWithTitle) ?: strings.previousEpisodeAction,
                    enabled = hasMedia && onPlayPreviousEpisode != null,
                    onClick = { onPlayPreviousEpisode?.invoke() },
                )
                if (!isNarrow) {
                    PlayerIconButton(Icons.Filled.FastRewind, strings.backThirtySecondsAction, enabled = hasMedia, onClick = onSeekBackwardLarge)
                }
                PlayerIconButton(Icons.Filled.Replay10, strings.backTenSecondsAction, enabled = hasMedia, onClick = onSeekBackward)
                PlayerIconButton(
                    imageVector = if (playbackSnapshot.status == PlaybackStatus.PLAYING) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (playbackSnapshot.status == PlaybackStatus.PLAYING) strings.pauseAction else strings.playAction,
                    enabled = hasMedia,
                    onClick = if (playbackSnapshot.status == PlaybackStatus.PLAYING) onPause else onPlay,
                )
                PlayerIconButton(Icons.Filled.Forward10, strings.forwardTenSecondsAction, enabled = hasMedia, onClick = onSeekForward)
                if (!isNarrow) {
                    PlayerIconButton(Icons.Filled.FastForward, strings.forwardThirtySecondsAction, enabled = hasMedia, onClick = onSeekForwardLarge)
                }
                PlayerIconButton(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = nextEpisodeLabel?.let(strings.nextEpisodeWithTitle) ?: strings.nextEpisodeAction,
                    enabled = hasMedia && onPlayNextEpisode != null,
                    onClick = { onPlayNextEpisode?.invoke() },
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = strings.volumeLabel,
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
                        text = playbackSnapshot.selectedTrackButtonText(PlaybackTrackKind.AUDIO, strings.audioLabel),
                        enabled = hasMedia && playbackSnapshot.tracks.any { it.kind == PlaybackTrackKind.AUDIO },
                        modifier = Modifier.width(108.dp),
                        onClick = {
                            playbackSnapshot.nextTrackId(PlaybackTrackKind.AUDIO)?.let(onSelectAudioTrack)
                        },
                    )
                    PlayerOverlayButton(
                        text = playbackSnapshot.selectedTrackButtonText(PlaybackTrackKind.SUBTITLE, strings.subtitleShortLabel),
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
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = if (rightPanelVisible) {
                        strings.hideDanmakuPanelAction
                    } else {
                        strings.showDanmakuPanelAction
                    },
                    enabled = hasMedia,
                    active = rightPanelVisible,
                    onClick = onToggleRightPanel,
                )
                PlayerIconButton(
                    imageVector = if (isFocusMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFocusMode) strings.showPlayerChromeAction else strings.hidePlayerChromeAction,
                    enabled = true,
                    active = isFocusMode,
                    onClick = onToggleFocusMode,
                )
                PlayerIconButton(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullscreen) strings.exitFullscreenAction else strings.enterFullscreenAction,
                    enabled = hasMedia,
                    onClick = { onSetFullscreen(!isFullscreen) },
                )
            }
        }
    }
}

@Composable
private fun PlayerPreparationStatusOverlay(
    strings: DesktopStrings,
    isPreparingLocalPlayback: Boolean,
    playbackError: String?,
    libraryError: String?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    modifier: Modifier = Modifier,
) {
    val hasError = playbackError != null || libraryError != null
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = if (hasError) Icons.Filled.Warning else Icons.Filled.Refresh,
                contentDescription = null,
                tint = if (hasError) DanmakuColors.Warning else DanmakuColors.Info,
            )
            Text(
                text = if (hasError) strings.playbackNeedsAttentionTitle else strings.preparingPlaybackTitle,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPreparingLocalPlayback) {
            PlayerPreparationStepRow(
                label = strings.libraryStepLabel,
                value = strings.preparingMediaStepText,
                color = DanmakuColors.Info,
                icon = Icons.Filled.Refresh,
            )
        }
        playbackError?.let { error ->
            PlayerPreparationStepRow(
                label = strings.playerRuntimeStepLabel,
                value = error,
                color = DanmakuColors.Warning,
                icon = Icons.Filled.Warning,
            )
        }
        libraryError?.let { error ->
            PlayerPreparationStepRow(
                label = strings.libraryPreparationStepLabel,
                value = error,
                color = DanmakuColors.Warning,
                icon = Icons.Filled.Warning,
            )
        }
        dandanplayCacheStatus?.let { status ->
            PlayerPreparationStepRow(
                label = strings.danmakuTitle,
                value = status.summary,
                color = if (status.summary.isDandanplayWarningStatus()) DanmakuColors.Warning else DanmakuColors.Good,
                icon = if (status.summary.isDandanplayWarningStatus()) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            )
        }
    }
}

@Composable
private fun PlayerPreparationStepRow(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, color = color, maxLines = 1, modifier = Modifier.width(118.dp))
        Text(
            value,
            color = DanmakuColors.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlayerRightPanel(
    strings: DesktopStrings,
    playbackSnapshot: PlaybackSnapshot,
    overlayStatus: String,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    danmakuSettings: DanmakuDisplaySettings,
    onSaveDanmakuSettings: (DanmakuDisplaySettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitleTracks = playbackSnapshot.tracks.filter { it.kind == PlaybackTrackKind.SUBTITLE }
    val audioTracks = playbackSnapshot.tracks.filter { it.kind == PlaybackTrackKind.AUDIO }
    val selectedSubtitle = subtitleTracks.firstOrNull(PlaybackTrack::selected)
    val selectedAudio = audioTracks.firstOrNull(PlaybackTrack::selected)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Subtitles, contentDescription = null, tint = DanmakuColors.Accent)
            Text(strings.danmakuTitle, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StatusPill(if (danmakuSettings.visible) strings.shownLabel else strings.hiddenLabel, active = danmakuSettings.visible)
        }
        Text(overlayStatus, color = DanmakuColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        dandanplayCacheStatus?.let { status ->
            MetadataRow(strings.cacheLabel, status.summary, if (status.summary.isDandanplayWarningStatus()) DanmakuColors.Warning else DanmakuColors.Good)
        } ?: MetadataRow(strings.cacheLabel, strings.notCheckedLabel, DanmakuColors.TextMuted)
        MetadataRow(strings.audioLabel, selectedAudio?.label ?: strings.defaultLabel)
        MetadataRow(strings.subtitleLabel, selectedSubtitle?.label ?: strings.offLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSaveDanmakuSettings(danmakuSettings.copy(visible = !danmakuSettings.visible)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (danmakuSettings.visible) Icons.Filled.Subtitles else Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (danmakuSettings.visible) strings.hideAction else strings.showAction)
            }
        }
        PlayerDanmakuSlider(
            label = strings.opacityLabel,
            value = danmakuSettings.opacityPercent,
            range = 0..100,
            suffix = "%",
            onValueChange = { onSaveDanmakuSettings(danmakuSettings.copy(opacityPercent = it)) },
        )
        PlayerDanmakuSlider(
            label = strings.densityLabel,
            value = danmakuSettings.densityPercent,
            range = 10..200,
            suffix = "%",
            onValueChange = { onSaveDanmakuSettings(danmakuSettings.copy(densityPercent = it)) },
        )
        PlayerDanmakuSlider(
            label = strings.fontLabel,
            value = danmakuSettings.fontScalePercent,
            range = 50..200,
            suffix = "%",
            onValueChange = { onSaveDanmakuSettings(danmakuSettings.copy(fontScalePercent = it)) },
        )
        PlayerDanmakuSlider(
            label = strings.speedLabel,
            value = danmakuSettings.speedPercent,
            range = 25..300,
            suffix = "%",
            onValueChange = { onSaveDanmakuSettings(danmakuSettings.copy(speedPercent = it)) },
        )
        PlayerDanmakuSlider(
            label = strings.areaLabel,
            value = danmakuSettings.displayAreaPercent,
            range = 10..100,
            suffix = "%",
            onValueChange = { onSaveDanmakuSettings(danmakuSettings.copy(displayAreaPercent = it)) },
        )
        PlayerDanmakuOffsetControl(
            strings = strings,
            offsetMs = danmakuSettings.offsetMs,
            onOffsetChange = { onSaveDanmakuSettings(danmakuSettings.copy(offsetMs = it)) },
        )
    }
}

@Composable
private fun PlayerDanmakuSlider(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = DanmakuColors.TextMuted, modifier = Modifier.weight(1f), maxLines = 1)
            Text("$value$suffix", color = Color.White, maxLines = 1)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun PlayerDanmakuOffsetControl(
    strings: DesktopStrings,
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.offsetLabel, color = DanmakuColors.TextMuted, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${offsetMs}ms", color = Color.White, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerOverlayButton(
                text = "-500",
                modifier = Modifier.weight(1f),
                onClick = { onOffsetChange((offsetMs - 500L).coerceIn(-3_600_000L, 3_600_000L)) },
            )
            PlayerOverlayButton(
                text = "Reset",
                modifier = Modifier.weight(1f),
                onClick = { onOffsetChange(0L) },
            )
            PlayerOverlayButton(
                text = "+500",
                modifier = Modifier.weight(1f),
                onClick = { onOffsetChange((offsetMs + 500L).coerceIn(-3_600_000L, 3_600_000L)) },
            )
        }
    }
}

@Composable
private fun PlayerFocusRestoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.FullscreenExit,
            contentDescription = null,
            tint = Color.White,
        )
        Text(
            text = "Show chrome (H)",
            color = Color.White,
            style = MaterialTheme.typography.body2,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val backgroundColor = when {
        !enabled -> Color.White.copy(alpha = 0.06f)
        active -> DanmakuColors.AccentSoft
        else -> Color.White.copy(alpha = 0.14f)
    }
    Box(
        modifier = modifier
            .width(36.dp)
            .height(34.dp)
            .background(color = backgroundColor, shape = RoundedCornerShape(6.dp))
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
private fun LibraryActionButton(
    imageVector: ImageVector,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    strings: DesktopStrings,
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    searchSeed: String,
    searchSeedVersion: Int,
    seriesPosterById: Map<String, Path?>,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    externalAnimeItemMappingsByMediaId: Map<String, List<DesktopExternalAnimeItemMapping>>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    originalSeriesTitleByMediaId: Map<String, String>,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
    playbackProgresses: List<PlaybackProgress>,
    favoriteMediaIds: Set<String>,
    externalAnimeSyncFailures: List<ExternalAnimeSyncFailure>,
    isExternalAnimeSyncing: Boolean,
    isIndexing: Boolean,
    isPreparingLocalPlayback: Boolean,
    selectedLocalPlaybackPreparation: DesktopLocalPlaybackPreparation?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    autoNextLocalPlayback: Boolean,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    onAddLibraryFolder: () -> Unit,
    onImportAniRssOutputFolder: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    onRemoveRegisteredRoot: (DesktopLibraryRoot) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    onInspectCachedDandanplay: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onSetAutoNextLocalPlayback: (Boolean) -> Unit,
    onRefreshDandanplay: (DesktopLocalPlaybackPreparation) -> Unit,
    onSelectDandanplayMatch: (DesktopLocalPlaybackPreparation, DandanplayMatch) -> Unit,
    onClearDandanplayCache: (DesktopLocalPlaybackPreparation) -> Unit,
    onClearDanmakuOverlay: (DesktopLocalPlaybackPreparation) -> Unit,
    onAttachManualDanmaku: (DesktopLocalPlaybackPreparation) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onRefreshSeriesMetadata: (LibrarySeries) -> Unit,
    onSaveExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider) -> Unit,
    onSaveExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider) -> Unit,
    onSearchExternalAnimeMatches: suspend (ExternalAnimeMatchQuery, Set<ExternalAnimeProvider>) -> Result<List<ExternalAnimeMatchCandidate>>,
    onFetchMetadataMatchPoster: suspend (String?) -> Path?,
    onSyncExternalAnimePlan: (ExternalAnimeTrackingPlan) -> Unit,
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
        val externalTrackingPlan = remember(
            indexedLibrary?.catalog,
            externalAnimeMappings,
            playbackProgresses,
            externalAnimeSyncFailures,
        ) {
            indexedLibrary?.catalog?.externalAnimeTrackingPlan(
                mappings = externalAnimeMappings,
                progresses = playbackProgresses,
                failures = externalAnimeSyncFailures,
            )
        }
        fun selectSeries(series: LibrarySeries) {
            selectedSeriesId = series.id
            selectedEpisodeId = null
        }
        fun showEpisodeDetails(item: LibraryMediaItem) {
            selectedEpisodeId = item.id
            series
                .firstOrNull { librarySeries ->
                    librarySeries.seasons.any { season -> season.items.any { seriesItem -> seriesItem.id == item.id } }
                }
                ?.let { selectedSeriesId = it.id }
            onInspectCachedDandanplay(item)
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
            strings = strings,
            registeredRoots = registeredRoots,
            indexedLibrary = indexedLibrary,
            searchSeed = searchSeed,
            searchSeedVersion = searchSeedVersion,
            series = series,
            seriesPosterById = seriesPosterById,
            externalTrackingPlan = externalTrackingPlan,
            isExternalAnimeSyncing = isExternalAnimeSyncing,
            externalAnimeMappings = externalAnimeMappings,
            externalAnimeItemMappingsByMediaId = externalAnimeItemMappingsByMediaId,
            externalAnimeProviderSettings = externalAnimeProviderSettings,
            originalSeriesTitleByMediaId = originalSeriesTitleByMediaId,
            refreshingMetadataMediaIds = refreshingMetadataMediaIds,
            refreshingMetadataSeriesIds = refreshingMetadataSeriesIds,
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
            onImportAniRssOutputFolder = onImportAniRssOutputFolder,
            onRescanRegisteredRoots = onRescanRegisteredRoots,
            onRemoveRegisteredRoot = onRemoveRegisteredRoot,
            onSelectSeries = ::selectSeries,
            onShowDetails = ::showEpisodeDetails,
            onInspectCachedDandanplay = onInspectCachedDandanplay,
            onSetFavorite = onSetFavorite,
            onSetAutoNextLocalPlayback = onSetAutoNextLocalPlayback,
            onRefreshDandanplay = onRefreshDandanplay,
            onSelectDandanplayMatch = onSelectDandanplayMatch,
            onClearDandanplayCache = onClearDandanplayCache,
            onClearDanmakuOverlay = onClearDanmakuOverlay,
            onAttachManualDanmaku = onAttachManualDanmaku,
            onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
            onRefreshSeriesMetadata = onRefreshSeriesMetadata,
            onSaveExternalAnimeMapping = onSaveExternalAnimeMapping,
            onDeleteExternalAnimeMapping = onDeleteExternalAnimeMapping,
            onSaveExternalAnimeItemMapping = onSaveExternalAnimeItemMapping,
            onDeleteExternalAnimeItemMapping = onDeleteExternalAnimeItemMapping,
            onSearchExternalAnimeMatches = onSearchExternalAnimeMatches,
            onFetchMetadataMatchPoster = onFetchMetadataMatchPoster,
            onSyncExternalAnimePlan = onSyncExternalAnimePlan,
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
    EXTERNAL_SYNC("External Sync"),
    PAIRED("Paired"),
}

@Composable
private fun WindowsLibraryWorkspace(
    strings: DesktopStrings,
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    searchSeed: String,
    searchSeedVersion: Int,
    series: List<LibrarySeries>,
    seriesPosterById: Map<String, Path?>,
    externalTrackingPlan: ExternalAnimeTrackingPlan?,
    isExternalAnimeSyncing: Boolean,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    externalAnimeItemMappingsByMediaId: Map<String, List<DesktopExternalAnimeItemMapping>>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    originalSeriesTitleByMediaId: Map<String, String>,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
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
    onImportAniRssOutputFolder: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    onRemoveRegisteredRoot: (DesktopLibraryRoot) -> Unit,
    onSelectSeries: (LibrarySeries) -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onInspectCachedDandanplay: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onSetAutoNextLocalPlayback: (Boolean) -> Unit,
    onRefreshDandanplay: (DesktopLocalPlaybackPreparation) -> Unit,
    onSelectDandanplayMatch: (DesktopLocalPlaybackPreparation, DandanplayMatch) -> Unit,
    onClearDandanplayCache: (DesktopLocalPlaybackPreparation) -> Unit,
    onClearDanmakuOverlay: (DesktopLocalPlaybackPreparation) -> Unit,
    onAttachManualDanmaku: (DesktopLocalPlaybackPreparation) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onRefreshSeriesMetadata: (LibrarySeries) -> Unit,
    onSaveExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider) -> Unit,
    onSaveExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider) -> Unit,
    onSearchExternalAnimeMatches: suspend (ExternalAnimeMatchQuery, Set<ExternalAnimeProvider>) -> Result<List<ExternalAnimeMatchCandidate>>,
    onFetchMetadataMatchPoster: suspend (String?) -> Path?,
    onSyncExternalAnimePlan: (ExternalAnimeTrackingPlan) -> Unit,
    onLoadPreparedPlayback: (DesktopLocalPlaybackPreparation) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    remoteBrowser: @Composable () -> Unit,
) {
    var selectedView by remember { mutableStateOf(WindowsLibraryView.ALL_SERIES) }
    var showLibraryImportPanel by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibraryCatalogSort.TITLE) }
    var subtitleFilter by remember { mutableStateOf(LibrarySubtitleFilter.ANY) }
    var favoriteFilter by remember { mutableStateOf(LibraryFavoriteFilter.ANY) }
    LaunchedEffect(searchSeed, searchSeedVersion) {
        val query = searchSeed.trim()
        if (query.isNotBlank()) {
            selectedView = WindowsLibraryView.ALL_SERIES
            searchText = query
        }
    }
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
        val visibleMediaIds = filteredEpisodes.mapTo(mutableSetOf(), LibraryMediaItem::id)
        if (visibleMediaIds.isEmpty() && filtersAreDefault) {
            series
        } else {
            series.filter { librarySeries ->
                librarySeries.seasons.any { season ->
                    season.items.any { item -> item.id in visibleMediaIds }
                }
            }
        }
    }
    val selectedInspectorSeries = selectedEpisodeDetail?.series ?: selectedSeries
    val selectedInspectorItem = selectedEpisodeDetail?.mediaItem
        ?: selectedLocalPlaybackPreparation?.item
        ?: selectedSeries?.let { nextPlayableEpisode(it, watchStatusById) }
    LaunchedEffect(selectedInspectorItem?.id) {
        selectedInspectorItem?.let(onInspectCachedDandanplay)
    }
    val selectedInspectorSeriesMappings = selectedInspectorSeries
        ?.let { series -> externalAnimeMappings.filter { mapping -> mapping.localSeriesId == series.id } }
        .orEmpty()
    val selectedInspectorItemMappings = selectedInspectorItem
        ?.let { item -> externalAnimeItemMappingsByMediaId[item.id] }
        .orEmpty()
    var inspectorWidthOverride by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactWorkspace = maxWidth < 1380.dp
        val railWidth = if (compactWorkspace) 204.dp else 230.dp
        val defaultInspectorWidth = if (compactWorkspace) 372f else 460f
        val minInspectorWidth = if (compactWorkspace) 328f else 380f
        val maxInspectorWidth = minOf(if (compactWorkspace) 460f else 620f, maxWidth.value * 0.45f)
        val inspectorWidth = (inspectorWidthOverride ?: defaultInspectorWidth)
            .coerceIn(minInspectorWidth, maxInspectorWidth)
        val panelGap = if (compactWorkspace) 10.dp else 14.dp
        val workspaceMinHeight = if (compactWorkspace) 660.dp else 720.dp
        val density = LocalDensity.current

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = workspaceMinHeight),
            horizontalArrangement = Arrangement.spacedBy(panelGap),
        ) {
            LibraryWorkspaceRail(
                strings = strings,
                selectedView = selectedView,
                registeredRoots = registeredRoots,
                localEpisodeCount = catalog?.items?.size ?: 0,
                nextUpCount = nextUpItems.size,
                continueWatchingCount = continueWatchingItems.size,
                recentlyWatchedCount = recentlyWatchedItems.size,
                favoriteCount = favoriteMediaIds.size,
                seriesCount = series.size,
                externalTrackingPlan = externalTrackingPlan,
                isIndexing = isIndexing,
                libraryError = libraryError,
                lastScanStats = lastScanStats,
                compact = compactWorkspace,
                onSelectView = { selectedView = it },
                onOpenImportPanel = { showLibraryImportPanel = true },
                onRescanRegisteredRoots = onRescanRegisteredRoots,
                modifier = Modifier.width(railWidth),
            )
            LibraryCenterWorkspace(
                strings = strings,
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
                selectedMediaId = selectedInspectorItem?.id,
                coverBySeriesId = seriesPosterById,
                originalSeriesTitleByMediaId = originalSeriesTitleByMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                refreshingMetadataSeriesIds = refreshingMetadataSeriesIds,
                continueWatchingItems = continueWatchingItems,
                nextUpItems = nextUpItems,
                recentlyWatchedItems = recentlyWatchedItems,
                watchStatusById = watchStatusById,
                seriesWatchSummaryById = seriesWatchSummaryById,
                favoriteMediaIds = favoriteMediaIds,
                externalTrackingPlan = externalTrackingPlan,
                isExternalAnimeSyncing = isExternalAnimeSyncing,
                isPreparing = isPreparing,
                compact = compactWorkspace,
                onSelectSeries = onSelectSeries,
                onShowDetails = onShowDetails,
                onSetFavorite = onSetFavorite,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onRefreshSeriesMetadata = onRefreshSeriesMetadata,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
                onSyncExternalAnimePlan = onSyncExternalAnimePlan,
                onResetFilters = {
                    searchText = ""
                    sort = LibraryCatalogSort.TITLE
                    subtitleFilter = LibrarySubtitleFilter.ANY
                    favoriteFilter = LibraryFavoriteFilter.ANY
                },
                remoteBrowser = remoteBrowser,
                modifier = Modifier.weight(1f),
            )
            InspectorResizeHandle(
                label = strings.inspectorResizeHandleLabel,
                resetLabel = strings.resetInspectorWidthAction,
                isCustomWidth = inspectorWidthOverride != null,
                onReset = { inspectorWidthOverride = null },
                modifier = Modifier
                    .pointerInput(compactWorkspace, minInspectorWidth, maxInspectorWidth) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            val deltaDp = with(density) { dragAmount.toDp().value }
                            inspectorWidthOverride = (inspectorWidth - deltaDp)
                                .coerceIn(minInspectorWidth, maxInspectorWidth)
                        }
                    },
            )
            LibraryInspectorPane(
                strings = strings,
                selectedSeries = selectedInspectorSeries,
                selectedEpisodeDetail = selectedEpisodeDetail,
                selectedItem = selectedInspectorItem,
                selectedLocalPlaybackPreparation = selectedLocalPlaybackPreparation,
                dandanplayCacheStatus = dandanplayCacheStatus,
                autoNextLocalPlayback = autoNextLocalPlayback,
                externalAnimeMappings = selectedInspectorSeriesMappings,
                externalAnimeItemMappings = selectedInspectorItemMappings,
                externalAnimeProviderSettings = externalAnimeProviderSettings,
                originalSeriesTitleByMediaId = originalSeriesTitleByMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                refreshingMetadataSeriesIds = refreshingMetadataSeriesIds,
                coverPath = selectedInspectorSeries?.let { seriesPosterById[it.id] },
                watchSummary = selectedInspectorSeries?.let { seriesWatchSummaryById[it.id] },
                watchStatusById = watchStatusById,
                favoriteMediaIds = favoriteMediaIds,
                isPreparing = isPreparing,
                compact = compactWorkspace,
                onShowDetails = onShowDetails,
                onInspectCachedDandanplay = onInspectCachedDandanplay,
                onSetFavorite = onSetFavorite,
                onSetAutoNextLocalPlayback = onSetAutoNextLocalPlayback,
                onRefreshDandanplay = onRefreshDandanplay,
                onSelectDandanplayMatch = onSelectDandanplayMatch,
                onClearDandanplayCache = onClearDandanplayCache,
                onClearDanmakuOverlay = onClearDanmakuOverlay,
                onAttachManualDanmaku = onAttachManualDanmaku,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onRefreshSeriesMetadata = onRefreshSeriesMetadata,
                onSaveExternalAnimeMapping = onSaveExternalAnimeMapping,
                onDeleteExternalAnimeMapping = onDeleteExternalAnimeMapping,
                onSaveExternalAnimeItemMapping = onSaveExternalAnimeItemMapping,
                onDeleteExternalAnimeItemMapping = onDeleteExternalAnimeItemMapping,
                onSearchExternalAnimeMatches = onSearchExternalAnimeMatches,
                onFetchMetadataMatchPoster = onFetchMetadataMatchPoster,
                onLoadPreparedPlayback = onLoadPreparedPlayback,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
                modifier = Modifier.width(inspectorWidth.dp),
            )
        }
    }
    if (showLibraryImportPanel) {
        LibraryImportPanelDialog(
            strings = strings,
            registeredRoots = registeredRoots,
            indexedLibrary = indexedLibrary,
            isIndexing = isIndexing,
            libraryError = libraryError,
            lastScanStats = lastScanStats,
            onAddLibraryFolder = onAddLibraryFolder,
            onImportAniRssOutputFolder = onImportAniRssOutputFolder,
            onRescanRegisteredRoots = onRescanRegisteredRoots,
            onRemoveRegisteredRoot = onRemoveRegisteredRoot,
            onDismiss = { showLibraryImportPanel = false },
        )
    }
}

@Composable
private fun InspectorResizeHandle(
    label: String,
    resetLabel: String,
    isCustomWidth: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isCustomWidth) {
        DanmakuColors.AccentSoft.copy(alpha = 0.88f)
    } else {
        DanmakuColors.SurfaceRaised.copy(alpha = 0.74f)
    }
    val contentColor = if (isCustomWidth) DanmakuColors.Accent else DanmakuColors.TextMuted
    Box(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .clickable(enabled = isCustomWidth, onClick = onReset),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = if (isCustomWidth) resetLabel else label,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LibraryWorkspaceRail(
    strings: DesktopStrings,
    selectedView: WindowsLibraryView,
    registeredRoots: List<DesktopLibraryRoot>,
    localEpisodeCount: Int,
    nextUpCount: Int,
    continueWatchingCount: Int,
    recentlyWatchedCount: Int,
    favoriteCount: Int,
    seriesCount: Int,
    externalTrackingPlan: ExternalAnimeTrackingPlan?,
    isIndexing: Boolean,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    compact: Boolean,
    onSelectView: (WindowsLibraryView) -> Unit,
    onOpenImportPanel: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier.fillMaxHeight()) {
        Text("Danmaku", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        if (!compact) {
            Text(strings.libraryHostSubtitle, color = DanmakuColors.TextMuted, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LibraryRailNavigationItem(
            icon = Icons.Filled.History,
            label = strings.libraryViewTitle(WindowsLibraryView.CONTINUE_WATCHING),
            count = continueWatchingCount,
            selected = selectedView == WindowsLibraryView.CONTINUE_WATCHING,
            onClick = { onSelectView(WindowsLibraryView.CONTINUE_WATCHING) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.PlayArrow,
            label = strings.libraryViewTitle(WindowsLibraryView.NEXT_UP),
            count = nextUpCount,
            selected = selectedView == WindowsLibraryView.NEXT_UP,
            onClick = { onSelectView(WindowsLibraryView.NEXT_UP) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.GridView,
            label = strings.libraryViewTitle(WindowsLibraryView.ALL_SERIES),
            count = seriesCount,
            selected = selectedView == WindowsLibraryView.ALL_SERIES,
            onClick = { onSelectView(WindowsLibraryView.ALL_SERIES) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.History,
            label = strings.libraryViewTitle(WindowsLibraryView.RECENTLY_WATCHED),
            count = recentlyWatchedCount,
            selected = selectedView == WindowsLibraryView.RECENTLY_WATCHED,
            onClick = { onSelectView(WindowsLibraryView.RECENTLY_WATCHED) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.Star,
            label = strings.libraryViewTitle(WindowsLibraryView.FAVORITES),
            count = favoriteCount,
            selected = selectedView == WindowsLibraryView.FAVORITES,
            onClick = { onSelectView(WindowsLibraryView.FAVORITES) },
        )
        LibraryRailNavigationItem(
            icon = Icons.AutoMirrored.Filled.ViewList,
            label = strings.libraryViewTitle(WindowsLibraryView.FILES),
            count = localEpisodeCount,
            selected = selectedView == WindowsLibraryView.FILES,
            onClick = { onSelectView(WindowsLibraryView.FILES) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.Refresh,
            label = strings.libraryViewTitle(WindowsLibraryView.EXTERNAL_SYNC),
            count = externalTrackingPlan?.summary?.updateCount ?: 0,
            selected = selectedView == WindowsLibraryView.EXTERNAL_SYNC,
            onClick = { onSelectView(WindowsLibraryView.EXTERNAL_SYNC) },
        )
        LibraryRailNavigationItem(
            icon = Icons.Filled.Devices,
            label = strings.libraryViewTitle(WindowsLibraryView.PAIRED),
            count = 0,
            selected = selectedView == WindowsLibraryView.PAIRED,
            onClick = { onSelectView(WindowsLibraryView.PAIRED) },
        )
        Divider(color = DanmakuColors.SurfaceRaised)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerIconButton(
                imageVector = Icons.Filled.Add,
                contentDescription = strings.openLibraryImportPanelAction,
                enabled = !isIndexing,
                onClick = onOpenImportPanel,
            )
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = strings.rescanFoldersAction,
                enabled = registeredRoots.isNotEmpty() && !isIndexing,
                onClick = onRescanRegisteredRoots,
            )
        }
        LibrarySourceStatus(
            icon = Icons.Filled.Computer,
            label = strings.localPcLabel,
            value = if (isIndexing) strings.indexingLabel else "$localEpisodeCount ${strings.episodesSuffix}",
            statusColor = if (libraryError == null) DanmakuColors.Good else DanmakuColors.Warning,
        )
        LibrarySourceStatus(
            icon = Icons.Filled.Refresh,
            label = strings.externalListsLabel,
            value = externalTrackingPlan?.summary?.label ?: strings.noLibraryLabel,
            statusColor = if ((externalTrackingPlan?.summary?.updateCount ?: 0) > 0) {
                DanmakuColors.Good
            } else {
                DanmakuColors.TextMuted
            },
        )
        if (!compact) {
            LibrarySourceStatus(
                icon = Icons.Filled.Devices,
                label = strings.pairedDevicesLabel,
                value = strings.lanBrowserReadyLabel,
                statusColor = DanmakuColors.Accent,
            )
        }
        libraryError?.let { error ->
            Text(error, color = DanmakuColors.Warning, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        lastScanStats?.let { stats ->
            Text(
                strings.lastScanSummary(stats.reusedItemCount, stats.refreshedItemCount),
                color = DanmakuColors.TextMuted,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        Text(strings.foldersLabel, color = DanmakuColors.TextMuted, fontWeight = FontWeight.Bold)
        if (registeredRoots.isEmpty()) {
            Text(strings.noFoldersLabel, color = DanmakuColors.TextMuted)
        } else {
            val visibleRootCount = if (compact) 3 else 5
            registeredRoots.take(visibleRootCount).forEach { root ->
                Text(root.displayName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (registeredRoots.size > visibleRootCount) {
                Text(strings.moreItemsLabel(registeredRoots.size - visibleRootCount), color = DanmakuColors.TextMuted)
            }
        }
    }
}

@Composable
private fun LibraryImportPanelDialog(
    strings: DesktopStrings,
    registeredRoots: List<DesktopLibraryRoot>,
    indexedLibrary: IndexedLocalLibrary?,
    isIndexing: Boolean,
    libraryError: String?,
    lastScanStats: LocalMediaLibraryScanStats?,
    onAddLibraryFolder: () -> Unit,
    onImportAniRssOutputFolder: () -> Unit,
    onRescanRegisteredRoots: () -> Unit,
    onRemoveRegisteredRoot: (DesktopLibraryRoot) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingRemovalRoot by remember { mutableStateOf<DesktopLibraryRoot?>(null) }
    val aniRssRootCount = registeredRoots.count {
        it.provenance == DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER
    }

    AlertDialog(
        modifier = Modifier.width(880.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.libraryImportTitle) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(
                        title = strings.foldersLabel,
                        value = registeredRoots.size.toString(),
                        caption = strings.aniRssRootCountLabel(aniRssRootCount),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = strings.episodesLabel,
                        value = (indexedLibrary?.catalog?.items?.size ?: 0).toString(),
                        caption = if (isIndexing) strings.scanRunningLabel else strings.publishedLabel,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = strings.lastScanTitle,
                        value = lastScanStats?.refreshedItemCount?.toString() ?: "-",
                        caption = lastScanStats?.let {
                            strings.reusedCountLabel(it.reusedItemCount)
                        } ?: strings.notRunLabel,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryActionButton(
                        imageVector = Icons.Filled.FolderOpen,
                        label = strings.addFolderAction,
                        enabled = !isIndexing,
                        onClick = onAddLibraryFolder,
                    )
                    LibraryActionButton(
                        imageVector = Icons.Filled.Refresh,
                        label = strings.importAniRssOutputAction,
                        enabled = !isIndexing,
                        onClick = onImportAniRssOutputFolder,
                    )
                    LibraryActionButton(
                        imageVector = Icons.Filled.Refresh,
                        label = if (isIndexing) strings.scanningAction else strings.rescanAllAction,
                        enabled = registeredRoots.isNotEmpty() && !isIndexing,
                        onClick = onRescanRegisteredRoots,
                    )
                }

                libraryError?.let { error ->
                    Text(error, color = DanmakuColors.Warning, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (isIndexing) {
                    StatusPill(
                        text = strings.indexingLibraryRootsLabel,
                        icon = Icons.Filled.Refresh,
                        active = true,
                        color = DanmakuColors.Accent,
                    )
                }

                Divider(color = DanmakuColors.SurfaceRaised)
                Text(strings.registeredRootsTitle, fontWeight = FontWeight.Bold)
                if (registeredRoots.isEmpty()) {
                    Text(
                        strings.libraryImportEmptyText,
                        color = DanmakuColors.TextMuted,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 330.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(registeredRoots, key = DesktopLibraryRoot::id) { root ->
                            LibraryImportRootRow(
                                strings = strings,
                                root = root,
                                enabled = !isIndexing,
                                onRemove = { pendingRemovalRoot = root },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRescanRegisteredRoots,
                enabled = registeredRoots.isNotEmpty() && !isIndexing,
            ) {
                Text(if (isIndexing) strings.scanningAction else strings.rescanAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeAction)
            }
        },
    )

    pendingRemovalRoot?.let { root ->
        SettingsConfirmationDialog(
            title = strings.removeLibraryFolderTitle,
            text = strings.removeLibraryFolderText(root.displayName),
            confirmLabel = strings.removeFolderAction,
            onConfirm = { onRemoveRegisteredRoot(root) },
            onDismiss = { pendingRemovalRoot = null },
        )
    }
}

@Composable
private fun LibraryImportRootRow(
    strings: DesktopStrings,
    root: DesktopLibraryRoot,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.58f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (root.provenance == DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER) {
                Icons.Filled.Refresh
            } else {
                Icons.Filled.FolderOpen
            },
            contentDescription = null,
            tint = DanmakuColors.Accent,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(root.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(
                    text = root.state.displayLabel(strings),
                    icon = if (root.state == DesktopLibraryRootState.AVAILABLE) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    active = root.state == DesktopLibraryRootState.AVAILABLE,
                    color = if (root.state == DesktopLibraryRootState.AVAILABLE) DanmakuColors.Good else DanmakuColors.Warning,
                )
            }
            Text(root.provenance.displayLabel(strings), color = DanmakuColors.TextMuted, maxLines = 1)
            Text(
                root.normalizedPath.toString(),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                root.lastScannedAtEpochMs?.let(strings.lastScannedAtLabel) ?: strings.notScannedYetLabel,
                color = DanmakuColors.TextMuted,
                maxLines = 1,
            )
            root.lastError?.let { error ->
                Text(error, color = DanmakuColors.Warning, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        LibraryActionButton(
            imageVector = Icons.Filled.Delete,
            label = strings.removeAction,
            enabled = enabled,
            onClick = onRemove,
        )
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
            Text(
                count.toString(),
                color = if (selected) Color.White else DanmakuColors.TextMuted,
                maxLines = 1,
                modifier = Modifier
                    .background(
                        if (selected) Color.White.copy(alpha = 0.16f) else DanmakuColors.SurfaceRaised,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
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
    strings: DesktopStrings,
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
    selectedMediaId: String?,
    coverBySeriesId: Map<String, Path?>,
    originalSeriesTitleByMediaId: Map<String, String>,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    recentlyWatchedItems: List<LibraryPlaybackProgressItem>,
    watchStatusById: Map<String, LibraryWatchStatus>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    favoriteMediaIds: Set<String>,
    externalTrackingPlan: ExternalAnimeTrackingPlan?,
    isExternalAnimeSyncing: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onSelectSeries: (LibrarySeries) -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onRefreshSeriesMetadata: (LibrarySeries) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    onSyncExternalAnimePlan: (ExternalAnimeTrackingPlan) -> Unit,
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
            strings = strings,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(
                strings.episodeCountSummary(filteredEpisodes.size, catalog?.items?.size ?: 0),
                icon = Icons.AutoMirrored.Filled.ViewList,
            )
            StatusPill(strings.favoriteCountSummary(favoriteMediaIds.size), icon = Icons.Filled.Star)
            externalTrackingPlan?.summary?.let { summary ->
                StatusPill(
                    summary.label,
                    icon = Icons.Filled.Refresh,
                    active = summary.updateCount > 0,
                )
            }
            if (subtitleFilter != LibrarySubtitleFilter.ANY) {
                StatusPill(strings.subtitlesOnlyLabel, icon = Icons.Filled.Subtitles, active = true)
            }
            if (selectedView == WindowsLibraryView.FAVORITES || favoriteFilter == LibraryFavoriteFilter.FAVORITES_ONLY) {
                StatusPill(strings.favoritesOnlyAction, icon = Icons.Filled.Star, active = true)
            }
            if (sort != LibraryCatalogSort.TITLE) {
                StatusPill(strings.pathSortLabel, icon = Icons.Filled.FolderOpen, active = true)
            }
            if (!compact) {
                selectedSeries?.let { StatusPill(it.title) }
            }
        }
        when (selectedView) {
            WindowsLibraryView.CONTINUE_WATCHING -> ContinueWatchingList(
                strings = strings,
                items = continueWatchingItems,
                selectedMediaId = selectedMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                isPreparing = isPreparing,
                compact = compact,
                onShowDetails = onShowDetails,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.NEXT_UP -> NextUpList(
                strings = strings,
                items = nextUpItems,
                selectedMediaId = selectedMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                isPreparing = isPreparing,
                compact = compact,
                onShowDetails = onShowDetails,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.RECENTLY_WATCHED -> RecentlyWatchedList(
                strings = strings,
                items = recentlyWatchedItems,
                selectedMediaId = selectedMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
                isPreparing = isPreparing,
                compact = compact,
                onShowDetails = onShowDetails,
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.FAVORITES,
            WindowsLibraryView.FILES -> EpisodeListView(
                strings = strings,
                episodes = filteredEpisodes,
                selectedMediaId = selectedMediaId,
                watchStatusById = watchStatusById,
                favoriteMediaIds = favoriteMediaIds,
                originalSeriesTitleByMediaId = originalSeriesTitleByMediaId,
                refreshingMetadataMediaIds = refreshingMetadataMediaIds,
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
                onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                onPrepareLocalPlayback = onPrepareLocalPlayback,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
            WindowsLibraryView.EXTERNAL_SYNC -> ExternalSyncPreviewView(
                plan = externalTrackingPlan,
                isSyncing = isExternalAnimeSyncing,
                onSync = onSyncExternalAnimePlan,
            )
            WindowsLibraryView.ALL_SERIES,
            WindowsLibraryView.PAIRED -> AllSeriesView(
                strings = strings,
                catalog = catalog,
                visibleSeries = visibleSeries,
                selectedSeries = selectedSeries,
                coverBySeriesId = coverBySeriesId,
                refreshingMetadataSeriesIds = refreshingMetadataSeriesIds,
                continueWatchingItems = continueWatchingItems,
                nextUpItems = nextUpItems,
                watchStatusById = watchStatusById,
                seriesWatchSummaryById = seriesWatchSummaryById,
                isPreparing = isPreparing,
                compact = compact,
                onResetFilters = onResetFilters,
                onSelectSeries = onSelectSeries,
                onShowDetails = onShowDetails,
                onRefreshSeriesMetadata = onRefreshSeriesMetadata,
                onPlayLocalPlayback = onPlayLocalPlayback,
            )
        }
    }
}

@Composable
private fun ExternalSyncPreviewView(
    plan: ExternalAnimeTrackingPlan?,
    isSyncing: Boolean,
    onSync: (ExternalAnimeTrackingPlan) -> Unit,
) {
    if (plan == null) {
        EmptyState("No indexed library is available for external sync preview.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(
                title = "Ready",
                value = plan.summary.updateCount.toString(),
                caption = "provider updates",
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "Conflicts",
                value = plan.summary.conflictCount.toString(),
                caption = "external ahead",
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "Skipped",
                value = plan.summary.skippedCount.toString(),
                caption = "mapping checks",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = plan.updates.isNotEmpty() && !isSyncing,
                onClick = { onSync(plan) },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isSyncing) "Syncing updates" else "Sync ready updates")
            }
            Text(
                if (plan.updates.isEmpty()) {
                    "No provider writes are ready."
                } else {
                    "Writes ${plan.updates.size} ready updates to connected external lists."
                },
                color = DanmakuColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (plan.summary.providerUpdateCounts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                plan.summary.providerUpdateCounts.toSortedMap(compareBy { it.name }).forEach { (provider, count) ->
                    StatusPill("${provider.displayName}: $count", icon = Icons.Filled.Refresh, active = count > 0)
                }
            }
        }
        Text("Dry-run updates", fontWeight = FontWeight.Bold)
        if (plan.updates.isEmpty()) {
            EmptyState("No external progress updates are ready.")
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.updates, key = { update -> "${update.mapping.localSeriesId}-${update.mapping.animeId.provider}" }) { update ->
                    ExternalSyncUpdateRow(update)
                }
            }
        }
        Text("Conflicts", fontWeight = FontWeight.Bold)
        if (plan.conflicts.isEmpty()) {
            EmptyState("No external progress conflicts are detected.")
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.conflicts, key = { conflict -> "${conflict.mapping.localSeriesId}-${conflict.mapping.animeId.provider}" }) { conflict ->
                    ExternalSyncConflictRow(conflict)
                }
            }
        }
        Text("Sync failures", fontWeight = FontWeight.Bold)
        if (plan.failures.isEmpty()) {
            EmptyState("No sync failures recorded.")
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.failures, key = { failure -> "${failure.animeId.provider}-${failure.animeId.value}" }) { failure ->
                    ExternalSyncFailureRow(failure)
                }
            }
        }
        if (plan.skipped.isNotEmpty()) {
            Text("Skipped", fontWeight = FontWeight.Bold)
            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.skipped.take(40), key = { skip -> "${skip.localSeriesId}-${skip.provider}-${skip.reason}" }) { skip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = DanmakuColors.TextMuted)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(skip.provider?.displayName ?: "External provider", color = Color.White, maxLines = 1)
                            Text(skip.reason.displayName, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(skip.localSeriesId, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (plan.skipped.size > 40) {
                Text("+${plan.skipped.size - 40} more skipped", color = DanmakuColors.TextMuted)
            }
        }
    }
}

@Composable
private fun ExternalSyncConflictRow(
    conflict: app.danmaku.domain.ExternalAnimeTrackingPlanConflict,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = DanmakuColors.Warning)
        Column(modifier = Modifier.weight(1f)) {
            Text(conflict.series.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${conflict.mapping.animeId.provider.displayName} #${conflict.mapping.animeId.value}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(conflict.reason.displayName, color = DanmakuColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Local ${conflict.localUpdate.watchedEpisodes ?: 0}", color = DanmakuColors.TextMuted, maxLines = 1)
            Text("External ${conflict.externalEntry.watchedEpisodes ?: 0}", color = DanmakuColors.Warning, maxLines = 1)
        }
    }
}

@Composable
private fun ExternalSyncFailureRow(
    failure: app.danmaku.domain.ExternalAnimeSyncFailure,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = DanmakuColors.Warning)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${failure.animeId.provider.displayName} #${failure.animeId.value}",
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(failure.message, color = DanmakuColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Attempt ${failure.attemptCount}", color = DanmakuColors.TextMuted, maxLines = 1)
            Text("Retry ${failure.retryAfterEpochMs.formatEpochTime()}", color = DanmakuColors.TextMuted, maxLines = 1)
        }
    }
}

@Composable
private fun ExternalSyncUpdateRow(
    update: app.danmaku.domain.ExternalAnimeTrackingPlanUpdate,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DanmakuColors.SurfaceRaised, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DanmakuColors.Good)
        Column(modifier = Modifier.weight(1f)) {
            Text(update.series.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${update.mapping.animeId.provider.displayName} #${update.mapping.animeId.value}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(update.update.status.displayName, color = DanmakuColors.Good, maxLines = 1)
            Text(
                "${update.update.watchedEpisodes ?: 0}/${update.series.episodeCount} watched",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LibraryWorkspaceToolbar(
    strings: DesktopStrings,
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
                    strings.requireSubtitlesAction
                } else {
                    strings.showAllSubtitlesAction
                },
                active = subtitleFilter != LibrarySubtitleFilter.ANY,
                onClick = onToggleSubtitleFilter,
            )
            PlayerIconButton(
                imageVector = Icons.Filled.Star,
                contentDescription = if (favoriteFilter == LibraryFavoriteFilter.ANY) {
                    strings.favoritesOnlyAction
                } else {
                    strings.showAllFavoritesAction
                },
                active = selectedView == WindowsLibraryView.FAVORITES || favoriteFilter != LibraryFavoriteFilter.ANY,
                onClick = onToggleFavoriteFilter,
            )
            PlayerIconButton(
                imageVector = if (sort == LibraryCatalogSort.TITLE) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = if (sort == LibraryCatalogSort.TITLE) strings.sortByPathAction else strings.sortByTitleAction,
                active = sort != LibraryCatalogSort.TITLE,
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
            label = { Text(strings.librarySearchLabel) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = strings.searchAction) },
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
                        Text(strings.libraryViewTitle(selectedView), style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                        Text(strings.libraryToolbarCompactDescription, color = DanmakuColors.TextMuted, maxLines = 1)
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
                    Text(strings.libraryViewTitle(selectedView), style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                    Text(strings.libraryToolbarDescription, color = DanmakuColors.TextMuted)
                }
                LibrarySearchField(Modifier.width(360.dp))
                ToolbarActions()
            }
        }
    }
}

@Composable
private fun AllSeriesView(
    strings: DesktopStrings,
    catalog: LibraryCatalog?,
    visibleSeries: List<LibrarySeries>,
    selectedSeries: LibrarySeries?,
    coverBySeriesId: Map<String, Path?>,
    refreshingMetadataSeriesIds: Set<String>,
    continueWatchingItems: List<LibraryPlaybackProgressItem>,
    nextUpItems: List<LibraryNextUpItem>,
    watchStatusById: Map<String, LibraryWatchStatus>,
    seriesWatchSummaryById: Map<String, LibrarySeriesWatchSummary>,
    isPreparing: Boolean,
    compact: Boolean,
    onResetFilters: () -> Unit,
    onSelectSeries: (LibrarySeries) -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshSeriesMetadata: (LibrarySeries) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    LibraryProgressOverview(
        strings = strings,
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
                    strings = strings,
                    series = librarySeries,
                    coverPath = coverBySeriesId[librarySeries.id],
                    watchSummary = seriesWatchSummaryById[librarySeries.id],
                    isSelected = librarySeries.id == selectedSeries?.id,
                    isPreparing = isPreparing,
                    isRefreshingMetadata = librarySeries.id in refreshingMetadataSeriesIds,
                    onSelect = { onSelectSeries(librarySeries) },
                    onRefreshMetadata = { onRefreshSeriesMetadata(librarySeries) },
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
    strings: DesktopStrings,
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
                    title = item.mediaItem.displaySeriesTitle(),
                    subtitle = item.mediaItem.episodeTitle,
                    fileGroupLabel = item.mediaItem.localSeriesLabel(strings),
                    detail = strings.resumeAtLabel(item.progress.positionMs),
                    progressPercent = item.progress.progressPercent(),
                    actionLabel = strings.resumeAction,
                    mediaItem = item.mediaItem,
                ),
            )
        }
        nextUpItems.take(2).forEach { item ->
            add(
                LibraryProgressCardModel(
                    title = item.mediaItem.displaySeriesTitle(),
                    subtitle = item.mediaItem.episodeTitle,
                    fileGroupLabel = item.mediaItem.localSeriesLabel(strings),
                    detail = item.nextUpLabel(strings),
                    progressPercent = item.progress?.progressPercent(),
                    actionLabel = item.nextUpActionLabel(strings),
                    mediaItem = item.mediaItem,
                ),
            )
        }
    }.distinctBy { it.mediaItem.id }.take(if (compact) 3 else 4)
    if (cards.isEmpty()) return

    Text(strings.continueWatchingTitle, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
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
    val fileGroupLabel: String?,
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
        card.fileGroupLabel?.let { label ->
            Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniProgressBar(percent = card.progressPercent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.detail, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) "Loading..." else card.actionLabel,
                onClick = { onPlayLocalPlayback(card.mediaItem) },
                enabled = !isPreparing,
            )
        }
    }
}

@Composable
private fun ContinueWatchingList(
    strings: DesktopStrings,
    items: List<LibraryPlaybackProgressItem>,
    selectedMediaId: String?,
    refreshingMetadataMediaIds: Set<String>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState("No in-progress local episodes yet.")
    } else {
        var selectedIndex by remember(items) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
        val selectedMediaIndex = items.indexOfFirst { it.mediaItem.id == selectedMediaId }
        val activeIndex = selectedMediaIndex.takeIf { it >= 0 } ?: boundedSelectedIndex
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = items.size,
                    selectedIndex = activeIndex,
                    onSelectedIndexChange = { index ->
                        selectedIndex = index
                        items.getOrNull(index)?.mediaItem?.let(onShowDetails)
                    },
                    onOpenSelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onShowDetails) },
                    onPlaySelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onPlayLocalPlayback) },
                ),
        ) {
            itemsIndexed(items, key = { _, item -> item.mediaItem.id }) { rowIndex, item ->
                ContinueWatchingRow(
                    strings = strings,
                    item = item,
                    selected = item.mediaItem.id == selectedMediaId || (selectedMediaId == null && rowIndex == boundedSelectedIndex),
                    isRefreshingMetadata = item.mediaItem.id in refreshingMetadataMediaIds,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = {
                        selectedIndex = rowIndex
                        onShowDetails(it)
                    },
                    onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
private fun NextUpList(
    strings: DesktopStrings,
    items: List<LibraryNextUpItem>,
    selectedMediaId: String?,
    refreshingMetadataMediaIds: Set<String>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState("No next-up item yet. Start watching from All Series.")
    } else {
        var selectedIndex by remember(items) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
        val selectedMediaIndex = items.indexOfFirst { it.mediaItem.id == selectedMediaId }
        val activeIndex = selectedMediaIndex.takeIf { it >= 0 } ?: boundedSelectedIndex
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = items.size,
                    selectedIndex = activeIndex,
                    onSelectedIndexChange = { index ->
                        selectedIndex = index
                        items.getOrNull(index)?.mediaItem?.let(onShowDetails)
                    },
                    onOpenSelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onShowDetails) },
                    onPlaySelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onPlayLocalPlayback) },
                ),
        ) {
            itemsIndexed(items, key = { _, item -> item.mediaItem.id }) { rowIndex, item ->
                NextUpRow(
                    strings = strings,
                    item = item,
                    selected = item.mediaItem.id == selectedMediaId || (selectedMediaId == null && rowIndex == boundedSelectedIndex),
                    isRefreshingMetadata = item.mediaItem.id in refreshingMetadataMediaIds,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = {
                        selectedIndex = rowIndex
                        onShowDetails(it)
                    },
                    onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                    onPrepareLocalPlayback = onPrepareLocalPlayback,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
private fun RecentlyWatchedList(
    strings: DesktopStrings,
    items: List<LibraryPlaybackProgressItem>,
    selectedMediaId: String?,
    refreshingMetadataMediaIds: Set<String>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState("No recently watched local episodes yet.")
    } else {
        var selectedIndex by remember(items) { mutableStateOf(0) }
        val boundedSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)
        val selectedMediaIndex = items.indexOfFirst { it.mediaItem.id == selectedMediaId }
        val activeIndex = selectedMediaIndex.takeIf { it >= 0 } ?: boundedSelectedIndex
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = items.size,
                    selectedIndex = activeIndex,
                    onSelectedIndexChange = { index ->
                        selectedIndex = index
                        items.getOrNull(index)?.mediaItem?.let(onShowDetails)
                    },
                    onOpenSelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onShowDetails) },
                    onPlaySelected = { items.getOrNull(activeIndex)?.mediaItem?.let(onPlayLocalPlayback) },
                ),
        ) {
            itemsIndexed(items, key = { _, item -> item.mediaItem.id }) { rowIndex, item ->
                RecentlyWatchedRow(
                    strings = strings,
                    item = item,
                    selected = item.mediaItem.id == selectedMediaId || (selectedMediaId == null && rowIndex == boundedSelectedIndex),
                    isRefreshingMetadata = item.mediaItem.id in refreshingMetadataMediaIds,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = {
                        selectedIndex = rowIndex
                        onShowDetails(it)
                    },
                    onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                    onPrepareLocalPlayback = onPrepareLocalPlayback,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
private fun EpisodeListView(
    strings: DesktopStrings,
    episodes: List<LibraryMediaItem>,
    selectedMediaId: String?,
    watchStatusById: Map<String, LibraryWatchStatus>,
    favoriteMediaIds: Set<String>,
    originalSeriesTitleByMediaId: Map<String, String>,
    refreshingMetadataMediaIds: Set<String>,
    isPreparing: Boolean,
    emptyText: String,
    compact: Boolean,
    onResetFilters: () -> Unit,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
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
        val selectedMediaIndex = episodes.indexOfFirst { it.id == selectedMediaId }
        val activeIndex = selectedMediaIndex.takeIf { it >= 0 } ?: boundedSelectedIndex
        LazyColumn(
            modifier = Modifier
                .height(560.dp)
                .libraryCollectionKeyboard(
                    itemCount = episodes.size,
                    selectedIndex = activeIndex,
                    onSelectedIndexChange = { index ->
                        selectedIndex = index
                        episodes.getOrNull(index)?.let(onShowDetails)
                    },
                    onOpenSelected = { episodes.getOrNull(activeIndex)?.let(onShowDetails) },
                    onPlaySelected = { episodes.getOrNull(activeIndex)?.let(onPlayLocalPlayback) },
                ),
        ) {
            itemsIndexed(episodes, key = { _, item -> item.id }) { rowIndex, item ->
                EpisodeRow(
                    strings = strings,
                    item = item,
                    selected = item.id == selectedMediaId || (selectedMediaId == null && rowIndex == boundedSelectedIndex),
                    watchStatus = watchStatusById[item.id],
                    isFavorite = item.id in favoriteMediaIds,
                    originalSeriesTitle = originalSeriesTitleByMediaId[item.id],
                    isRefreshingMetadata = item.id in refreshingMetadataMediaIds,
                    isPreparing = isPreparing,
                    compact = compact,
                    onShowDetails = {
                        selectedIndex = rowIndex
                        onShowDetails(it)
                    },
                    onSetFavorite = onSetFavorite,
                    onRefreshEpisodeMetadata = onRefreshEpisodeMetadata,
                    onPrepareLocalPlayback = onPrepareLocalPlayback,
                    onPlayLocalPlayback = onPlayLocalPlayback,
                )
            }
        }
    }
}

@Composable
private fun LibraryInspectorPane(
    strings: DesktopStrings,
    selectedSeries: LibrarySeries?,
    selectedEpisodeDetail: LibraryEpisodeDetail?,
    selectedItem: LibraryMediaItem?,
    selectedLocalPlaybackPreparation: DesktopLocalPlaybackPreparation?,
    dandanplayCacheStatus: DandanplayPlaybackUiStatus?,
    autoNextLocalPlayback: Boolean,
    externalAnimeMappings: List<ExternalAnimeMapping>,
    externalAnimeItemMappings: List<DesktopExternalAnimeItemMapping>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    originalSeriesTitleByMediaId: Map<String, String>,
    refreshingMetadataMediaIds: Set<String>,
    refreshingMetadataSeriesIds: Set<String>,
    coverPath: Path?,
    watchSummary: LibrarySeriesWatchSummary?,
    watchStatusById: Map<String, LibraryWatchStatus>,
    favoriteMediaIds: Set<String>,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onInspectCachedDandanplay: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onSetAutoNextLocalPlayback: (Boolean) -> Unit,
    onRefreshDandanplay: (DesktopLocalPlaybackPreparation) -> Unit,
    onSelectDandanplayMatch: (DesktopLocalPlaybackPreparation, DandanplayMatch) -> Unit,
    onClearDandanplayCache: (DesktopLocalPlaybackPreparation) -> Unit,
    onClearDanmakuOverlay: (DesktopLocalPlaybackPreparation) -> Unit,
    onAttachManualDanmaku: (DesktopLocalPlaybackPreparation) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onRefreshSeriesMetadata: (LibrarySeries) -> Unit,
    onSaveExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider) -> Unit,
    onSaveExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider) -> Unit,
    onSearchExternalAnimeMatches: suspend (ExternalAnimeMatchQuery, Set<ExternalAnimeProvider>) -> Result<List<ExternalAnimeMatchCandidate>>,
    onFetchMetadataMatchPoster: suspend (String?) -> Path?,
    onLoadPreparedPlayback: (DesktopLocalPlaybackPreparation) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkspacePanel(modifier = modifier.fillMaxHeight()) {
        if (selectedSeries == null || selectedItem == null) {
            Text(strings.inspectorTitle, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
            EmptyState(strings.inspectorEmptyText)
            return@WorkspacePanel
        }
        val activePreparation = selectedLocalPlaybackPreparation?.takeIf { it.item.id == selectedItem.id }
        val isFavorite = selectedItem.id in favoriteMediaIds
        val status = dandanplayCacheStatus?.takeIf { it.mediaId == selectedItem.id }
        val hasDanmakuOverlay = activePreparation?.subtitles?.any(DesktopPlaybackSubtitle::isDanmakuOverlay) == true
        val isRefreshingEpisodeMetadata = selectedItem.id in refreshingMetadataMediaIds
        val isRefreshingSeriesMetadata = selectedSeries.id in refreshingMetadataSeriesIds
        val metadataReadiness = selectedItem.metadataReadiness(
            strings = strings,
            isRefreshing = isRefreshingEpisodeMetadata || isRefreshingSeriesMetadata,
            hasPoster = coverPath != null || selectedItem.posterPath != null,
        )
        val originalSeriesTitle = originalSeriesTitleByMediaId[selectedItem.id]
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
            selectedEpisodeDetail?.mediaItem?.episodeTitle ?: strings.nextPlayableLabel(selectedItem.episodeTitle),
            color = DanmakuColors.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        originalSeriesTitle
            ?.takeIf { it.isNotBlank() && it != selectedItem.seriesTitle }
            ?.let { fileGroup ->
                Text(strings.fileGroupLabel(fileGroup), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        MiniProgressBar(percent = watchStatusById[selectedItem.id]?.progress?.progressPercent())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onPlayLocalPlayback(selectedItem) },
                enabled = !isPreparing,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = strings.playAction, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isPreparing) {
                        strings.loadingAction
                    } else {
                        selectedItem.primaryPlaybackActionLabel(watchStatusById[selectedItem.id], strings)
                    },
                )
            }
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else if (compact) strings.prepareShortAction else strings.prepareAction,
                modifier = Modifier.weight(1f),
                enabled = !isPreparing,
                onClick = { onPrepareLocalPlayback(selectedItem) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerIconButton(
                imageVector = Icons.Filled.Star,
                contentDescription = if (isFavorite) strings.unfavoriteAction else strings.favoriteAction,
                onClick = { onSetFavorite(selectedItem, !isFavorite) },
            )
            PlayerIconButton(
                imageVector = Icons.Filled.Subtitles,
                contentDescription = strings.showEpisodeDetailsAction,
                onClick = { onShowDetails(selectedItem) },
            )
            PlayerIconButton(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = strings.checkCachedDanmakuAction,
                active = status != null && !status.summary.isDandanplayWarningStatus(),
                onClick = { onInspectCachedDandanplay(selectedItem) },
            )
            Box {
                PlayerIconButton(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = strings.moreEpisodeActionsAction,
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
                        Text(strings.preparePlaybackAction)
                    }
                    DropdownMenuItem(
                        enabled = !isPreparing && !isRefreshingEpisodeMetadata,
                        onClick = {
                            episodeActionsExpanded = false
                            onRefreshEpisodeMetadata(selectedItem)
                        },
                    ) {
                        Text(strings.refreshEpisodeMetadataAction)
                    }
                    DropdownMenuItem(
                        enabled = !isPreparing && !isRefreshingSeriesMetadata,
                        onClick = {
                            episodeActionsExpanded = false
                            onRefreshSeriesMetadata(selectedSeries)
                        },
                    ) {
                        Text(strings.refreshSeriesMetadataAction)
                    }
                    activePreparation?.let { preparation ->
                        DropdownMenuItem(
                            onClick = {
                                episodeActionsExpanded = false
                                onLoadPreparedPlayback(preparation)
                            },
                        ) {
                            Text(strings.loadIntoPlayerAction)
                        }
                        DropdownMenuItem(
                            enabled = !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onRefreshDandanplay(preparation)
                            },
                        ) {
                            Text(strings.refreshDanmakuAction)
                        }
                        DropdownMenuItem(
                            enabled = !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onAttachManualDanmaku(preparation)
                            },
                        ) {
                            Text(strings.attachLocalDanmakuAction)
                        }
                        DropdownMenuItem(
                            enabled = hasDanmakuOverlay && !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onClearDanmakuOverlay(preparation)
                            },
                        ) {
                            Text(strings.removeOverlayAction)
                        }
                        DropdownMenuItem(
                            enabled = !isPreparing,
                            onClick = {
                                episodeActionsExpanded = false
                                onClearDandanplayCache(preparation)
                            },
                        ) {
                            Text(strings.clearDanmakuCacheAction)
                        }
                    }
                    DropdownMenuItem(
                        onClick = {
                            episodeActionsExpanded = false
                            onSetAutoNextLocalPlayback(!autoNextLocalPlayback)
                        },
                    ) {
                        Text(if (autoNextLocalPlayback) strings.disableAutoNextAction else strings.enableAutoNextAction)
                    }
                }
            }
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        Text(strings.readinessTitle, fontWeight = FontWeight.Bold)
        InspectorStatusRow(
            icon = if (activePreparation != null) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            label = if (activePreparation != null) strings.preparedPlaybackLabel else strings.prepareToInspectTracksLabel,
            value = activePreparation?.resumePositionMs?.let(strings.resumeValueLabel) ?: strings.notPreparedLabel,
            color = if (activePreparation != null) DanmakuColors.Good else DanmakuColors.TextMuted,
        )
        InspectorStatusRow(
            icon = if (status?.summary?.isDandanplayWarningStatus() == true) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            label = strings.danmakuTitle,
            value = status?.summary ?: strings.notCheckedYetLabel,
            color = when {
                status == null -> DanmakuColors.TextMuted
                status.summary.isDandanplayWarningStatus() -> DanmakuColors.Warning
                else -> DanmakuColors.Good
            },
        )
        InspectorStatusRow(
            icon = metadataReadiness.icon,
            label = metadataReadiness.label,
            value = metadataReadiness.detail,
            color = metadataReadiness.color,
        )
        InspectorStatusRow(
            icon = Icons.Filled.Subtitles,
            label = strings.subtitleLabel,
            value = strings.subtitlesIndexedLabel(selectedItem.subtitles.size),
            color = if (selectedItem.subtitles.isNotEmpty()) DanmakuColors.Good else DanmakuColors.TextMuted,
        )
        ExternalAnimeMappingPanel(
            strings = strings,
            selectedSeries = selectedSeries,
            selectedItem = selectedItem,
            seriesMappings = externalAnimeMappings,
            itemMappings = externalAnimeItemMappings,
            externalAnimeProviderSettings = externalAnimeProviderSettings,
            onSaveExternalAnimeMapping = onSaveExternalAnimeMapping,
            onDeleteExternalAnimeMapping = onDeleteExternalAnimeMapping,
            onSaveExternalAnimeItemMapping = onSaveExternalAnimeItemMapping,
            onDeleteExternalAnimeItemMapping = onDeleteExternalAnimeItemMapping,
            onSearchExternalAnimeMatches = onSearchExternalAnimeMatches,
            onFetchMetadataMatchPoster = onFetchMetadataMatchPoster,
        )
        selectedEpisodeDetail?.let { detail ->
            MetadataRow("Season", detail.season.label)
            MetadataRow("Watch", detail.watchStatus.statusLabel())
            MetadataRow("Size", detail.mediaItem.sizeBytes.formatLibrarySize())
        }
        Divider(color = DanmakuColors.SurfaceRaised)
        Text(strings.episodesTitle, fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
            selectedSeries.seasons.forEach { season ->
                item(key = season.id) {
                    Text(season.label, color = DanmakuColors.TextMuted, fontWeight = FontWeight.Bold)
                }
                items(season.items, key = { it.id }) { item ->
                    CompactInspectorEpisodeRow(
                        strings = strings,
                        item = item,
                        selected = item.id == selectedItem.id,
                        watchStatus = watchStatusById[item.id],
                        originalSeriesTitle = originalSeriesTitleByMediaId[item.id],
                        isRefreshingMetadata = item.id in refreshingMetadataMediaIds,
                        onClick = { onShowDetails(item) },
                    )
                }
            }
        }
        activePreparation?.let { preparation ->
            Divider(color = DanmakuColors.SurfaceRaised)
            Text(strings.advancedTitle, fontWeight = FontWeight.Bold)
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = strings.loadIntoPlayerAction,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onLoadPreparedPlayback(preparation) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryActionButton(
                    imageVector = Icons.Filled.Refresh,
                    label = strings.refreshDanmakuAction,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing,
                    onClick = { onRefreshDandanplay(preparation) },
                )
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.attachLocalDanmakuShortAction,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing,
                    onClick = { onAttachManualDanmaku(preparation) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryActionButton(
                    imageVector = Icons.Filled.Refresh,
                    label = if (isRefreshingEpisodeMetadata) strings.metadataLoadingLabel else strings.refreshEpisodeMetadataAction,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing && !isRefreshingEpisodeMetadata,
                    onClick = { onRefreshEpisodeMetadata(selectedItem) },
                )
                LibraryActionButton(
                    imageVector = Icons.Filled.Refresh,
                    label = if (isRefreshingSeriesMetadata) strings.metadataLoadingLabel else strings.refreshSeriesMetadataAction,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing && !isRefreshingSeriesMetadata,
                    onClick = { onRefreshSeriesMetadata(selectedSeries) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.removeOverlayAction,
                    modifier = Modifier.weight(1f),
                    enabled = hasDanmakuOverlay && !isPreparing,
                    onClick = { onClearDanmakuOverlay(preparation) },
                )
                LibraryActionButton(
                    imageVector = Icons.Filled.Refresh,
                    label = strings.clearCacheAction,
                    modifier = Modifier.weight(1f),
                    enabled = !isPreparing,
                    onClick = { onClearDandanplayCache(preparation) },
                )
            }
            LibraryActionButton(
                imageVector = Icons.Filled.FastForward,
                label = if (autoNextLocalPlayback) strings.autoNextOnLabel else strings.autoNextOffLabel,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSetAutoNextLocalPlayback(!autoNextLocalPlayback) },
            )
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
private fun ExternalAnimeMappingPanel(
    strings: DesktopStrings,
    selectedSeries: LibrarySeries,
    selectedItem: LibraryMediaItem,
    seriesMappings: List<ExternalAnimeMapping>,
    itemMappings: List<DesktopExternalAnimeItemMapping>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSaveExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider) -> Unit,
    onSaveExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider, String) -> Unit,
    onDeleteExternalAnimeItemMapping: (LibraryMediaItem, ExternalAnimeProvider) -> Unit,
    onSearchExternalAnimeMatches: suspend (ExternalAnimeMatchQuery, Set<ExternalAnimeProvider>) -> Result<List<ExternalAnimeMatchCandidate>>,
    onFetchMetadataMatchPoster: suspend (String?) -> Path?,
) {
    var showMatchDialog by remember(selectedSeries.id) { mutableStateOf(false) }
    val malMapping = seriesMappings.firstOrNull { it.animeId.provider == ExternalAnimeProvider.MY_ANIME_LIST }
    val bangumiMapping = seriesMappings.firstOrNull { it.animeId.provider == ExternalAnimeProvider.BANGUMI }
    val dandanplayItemMapping = itemMappings.firstOrNull { it.animeId.provider == ExternalAnimeProvider.DANDANPLAY }
    val displayedDandanplayId = dandanplayItemMapping?.animeId?.value
        ?: selectedItem.animeMetadata
            ?.animeId
            ?.takeIf { it.provider == ExternalAnimeProvider.DANDANPLAY }
            ?.value

    Divider(color = DanmakuColors.SurfaceRaised)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("External IDs", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        LibraryActionButton(
            imageVector = Icons.Filled.Search,
            label = "Match",
            onClick = { showMatchDialog = true },
        )
    }
    ExternalSeriesMappingRow(
        provider = ExternalAnimeProvider.MY_ANIME_LIST,
        mapping = malMapping,
        selectedSeries = selectedSeries,
        onSave = onSaveExternalAnimeMapping,
        onDelete = onDeleteExternalAnimeMapping,
    )
    ExternalSeriesMappingRow(
        provider = ExternalAnimeProvider.BANGUMI,
        mapping = bangumiMapping,
        selectedSeries = selectedSeries,
        onSave = onSaveExternalAnimeMapping,
        onDelete = onDeleteExternalAnimeMapping,
    )
    ExternalItemMappingRow(
        provider = ExternalAnimeProvider.DANDANPLAY,
        currentId = displayedDandanplayId,
        hasManualMapping = dandanplayItemMapping != null,
        selectedItem = selectedItem,
        onSave = onSaveExternalAnimeItemMapping,
        onDelete = onDeleteExternalAnimeItemMapping,
    )
    if (showMatchDialog) {
        MetadataMatchDialog(
            strings = strings,
            selectedSeries = selectedSeries,
            currentMappings = seriesMappings,
            externalAnimeProviderSettings = externalAnimeProviderSettings,
            onSearchExternalAnimeMatches = onSearchExternalAnimeMatches,
            onFetchMetadataMatchPoster = onFetchMetadataMatchPoster,
            onSaveExternalAnimeMapping = onSaveExternalAnimeMapping,
            onDismiss = { showMatchDialog = false },
        )
    }
}

@Composable
private fun MetadataMatchDialog(
    strings: DesktopStrings,
    selectedSeries: LibrarySeries,
    currentMappings: List<ExternalAnimeMapping>,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSearchExternalAnimeMatches: suspend (ExternalAnimeMatchQuery, Set<ExternalAnimeProvider>) -> Result<List<ExternalAnimeMatchCandidate>>,
    onFetchMetadataMatchPoster: suspend (String?) -> Path?,
    onSaveExternalAnimeMapping: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var queryText by remember(selectedSeries.id) { mutableStateOf(selectedSeries.title) }
    val myAnimeListSearchAvailable = externalAnimeProviderSettings.myAnimeListClientId != null
    val bangumiSearchAvailable = externalAnimeProviderSettings.bangumiBaseUrl.isNotBlank() &&
        externalAnimeProviderSettings.bangumiUserAgent.isNotBlank()
    var includeMyAnimeList by remember(selectedSeries.id, myAnimeListSearchAvailable) {
        mutableStateOf(myAnimeListSearchAvailable && currentMappings.none { it.animeId.provider == ExternalAnimeProvider.MY_ANIME_LIST })
    }
    var includeBangumi by remember(selectedSeries.id, bangumiSearchAvailable) {
        mutableStateOf(bangumiSearchAvailable && currentMappings.none { it.animeId.provider == ExternalAnimeProvider.BANGUMI })
    }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var candidates by remember(selectedSeries.id) { mutableStateOf<List<ExternalAnimeMatchCandidate>>(emptyList()) }

    fun runSearch() {
        val title = queryText.trim()
        if (title.isBlank() || isSearching) return
        val providers = buildSet {
            if (includeMyAnimeList) add(ExternalAnimeProvider.MY_ANIME_LIST)
            if (includeBangumi) add(ExternalAnimeProvider.BANGUMI)
        }
        if (providers.isEmpty()) {
            searchError = strings.metadataMatchSelectProviderError
            candidates = emptyList()
            return
        }
        isSearching = true
        searchError = null
        scope.launch {
            val result = onSearchExternalAnimeMatches(
                ExternalAnimeMatchQuery(
                    title = title,
                    episodeCount = selectedSeries.episodeCount.takeIf { it > 0 },
                ),
                providers,
            )
            result.onSuccess {
                candidates = it
                searchError = if (it.isEmpty()) strings.metadataMatchNoCandidates(title) else null
            }.onFailure {
                candidates = emptyList()
                searchError = it.readableMessage()
            }
            isSearching = false
        }
    }

    AlertDialog(
        modifier = Modifier.width(860.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.metadataMatchTitle) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    strings.metadataMatchDescription(selectedSeries.title),
                    color = DanmakuColors.TextMuted,
                )
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    label = { Text(strings.metadataMatchSearchTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetadataMatchProviderToggle(
                        label = "MyAnimeList",
                        detail = externalAnimeProviderSettings.myAnimeListStatusText,
                        selected = includeMyAnimeList,
                        enabled = myAnimeListSearchAvailable,
                        onToggle = {
                            if (myAnimeListSearchAvailable) {
                                includeMyAnimeList = !includeMyAnimeList
                            }
                        },
                    )
                    MetadataMatchProviderToggle(
                        label = "Bangumi",
                        detail = externalAnimeProviderSettings.bangumiStatusText,
                        selected = includeBangumi,
                        enabled = bangumiSearchAvailable,
                        onToggle = {
                            if (bangumiSearchAvailable) {
                                includeBangumi = !includeBangumi
                            }
                        },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    LibraryActionButton(
                        imageVector = Icons.Filled.Search,
                        label = if (isSearching) strings.searchingAction else strings.searchAction,
                        enabled = !isSearching && queryText.isNotBlank(),
                        onClick = ::runSearch,
                    )
                }
                if (!myAnimeListSearchAvailable || !bangumiSearchAvailable) {
                    Text(
                        buildList {
                            if (!myAnimeListSearchAvailable) add(strings.metadataMatchMyAnimeListUnavailable)
                            if (!bangumiSearchAvailable) add(strings.metadataMatchBangumiUnavailable)
                        }.joinToString("; "),
                        color = DanmakuColors.Warning,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                currentMappings.takeIf { it.isNotEmpty() }?.let { mappings ->
                    Text(
                        strings.metadataMatchCurrentMappingsPrefix + " " +
                            mappings.joinToString { "${it.animeId.provider.displayName} #${it.animeId.value}" },
                        color = DanmakuColors.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                searchError?.let { error ->
                    Text(error, color = DanmakuColors.Warning, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Divider(color = DanmakuColors.SurfaceRaised)
                if (candidates.isEmpty()) {
                    EmptyState(strings.metadataMatchEmptyState)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(candidates, key = { "${it.anime.id.provider.name}:${it.anime.id.value}" }) { candidate ->
                            MetadataMatchCandidateRow(
                                strings = strings,
                                candidate = candidate,
                                alreadyMapped = currentMappings.any { it.animeId == candidate.anime.id },
                                onFetchPoster = onFetchMetadataMatchPoster,
                                onUse = {
                                    onSaveExternalAnimeMapping(
                                        selectedSeries,
                                        candidate.anime.id.provider,
                                        candidate.anime.id.value.toString(),
                                    )
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = ::runSearch,
                enabled = !isSearching && queryText.isNotBlank(),
            ) {
                Text(if (isSearching) strings.searchingAction else strings.searchAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeAction)
            }
        },
    )
}

@Composable
private fun MetadataMatchProviderToggle(
    label: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val backgroundColor = when {
        selected -> DanmakuColors.AccentSoft
        enabled -> DanmakuColors.SurfaceRaised
        else -> DanmakuColors.SurfaceRaised.copy(alpha = 0.48f)
    }
    val iconColor = when {
        selected -> DanmakuColors.Good
        enabled -> DanmakuColors.TextMuted
        else -> DanmakuColors.Warning
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = when {
                selected -> Icons.Filled.CheckCircle
                enabled -> Icons.Filled.Search
                else -> Icons.Filled.Warning
            },
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(label, maxLines = 1, color = if (enabled) Color.White else DanmakuColors.TextMuted)
            Text(detail, maxLines = 1, color = if (enabled) DanmakuColors.TextMuted else DanmakuColors.Warning)
        }
    }
}

@Composable
private fun MetadataMatchCandidateRow(
    strings: DesktopStrings,
    candidate: ExternalAnimeMatchCandidate,
    alreadyMapped: Boolean,
    onFetchPoster: suspend (String?) -> Path?,
    onUse: () -> Unit,
) {
    val anime = candidate.anime
    var posterPath by remember(anime.id, anime.imageUrl) { mutableStateOf<Path?>(null) }
    var isPosterLoading by remember(anime.id, anime.imageUrl) { mutableStateOf(!anime.imageUrl.isNullOrBlank()) }

    LaunchedEffect(anime.id, anime.imageUrl) {
        val imageUrl = anime.imageUrl
        posterPath = null
        isPosterLoading = !imageUrl.isNullOrBlank()
        if (!imageUrl.isNullOrBlank()) {
            posterPath = runCatching { onFetchPoster(imageUrl) }.getOrNull()
            isPosterLoading = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DanmakuColors.SurfaceRaised.copy(alpha = 0.62f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetadataMatchPosterPreview(
            posterPath = posterPath,
            title = anime.titles.primary,
            isLoading = isPosterLoading,
            loadingLabel = strings.posterLoadingLabel,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(anime.titles.primary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildList {
                    add("${anime.id.provider.displayName} #${anime.id.value}")
                    anime.episodeCount?.let { add("$it ${strings.episodesSuffix}") }
                    anime.startYear?.let { add(it.toString()) }
                    candidate.matchedTitle?.takeIf { it != anime.titles.primary }?.let {
                        add("${strings.metadataMatchMatchedTitlePrefix} $it")
                    }
                    if (anime.imageUrl != null && posterPath == null) {
                        add(if (isPosterLoading) strings.posterLoadingLabel else strings.posterUnavailableLabel)
                    }
                }.joinToString(" - "),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            anime.summary?.let {
                Text(it, color = DanmakuColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        StatusPill(
            text = candidate.confidence.formatConfidence(),
            icon = Icons.Filled.CheckCircle,
            active = candidate.confidence >= 0.7,
            color = if (candidate.confidence >= 0.7) DanmakuColors.Good else DanmakuColors.Accent,
        )
        Button(
            enabled = !alreadyMapped,
            onClick = onUse,
        ) {
            Text(if (alreadyMapped) strings.mappedAction else strings.useAction)
        }
    }
}

@Composable
private fun MetadataMatchPosterPreview(
    posterPath: Path?,
    title: String,
    isLoading: Boolean,
    loadingLabel: String,
) {
    val bitmap = rememberLocalImageBitmap(posterPath)
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(DanmakuColors.AccentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.initialsForPoster(),
                color = Color.White,
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.56f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = loadingLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ExternalSeriesMappingRow(
    provider: ExternalAnimeProvider,
    mapping: ExternalAnimeMapping?,
    selectedSeries: LibrarySeries,
    onSave: (LibrarySeries, ExternalAnimeProvider, String) -> Unit,
    onDelete: (LibrarySeries, ExternalAnimeProvider) -> Unit,
) {
    var animeIdText by remember(selectedSeries.id, provider, mapping?.animeId?.value) {
        mutableStateOf(mapping?.animeId?.value?.toString().orEmpty())
    }
    ExternalMappingEditRow(
        label = provider.displayName,
        value = animeIdText,
        onValueChange = { animeIdText = it.filter(Char::isDigit) },
        saveLabel = if (mapping == null) "Link" else "Replace",
        deleteEnabled = mapping != null,
        onSave = { onSave(selectedSeries, provider, animeIdText) },
        onDelete = { onDelete(selectedSeries, provider) },
    )
}

@Composable
private fun ExternalItemMappingRow(
    provider: ExternalAnimeProvider,
    currentId: Long?,
    hasManualMapping: Boolean,
    selectedItem: LibraryMediaItem,
    onSave: (LibraryMediaItem, ExternalAnimeProvider, String) -> Unit,
    onDelete: (LibraryMediaItem, ExternalAnimeProvider) -> Unit,
) {
    var animeIdText by remember(selectedItem.id, provider, currentId) {
        mutableStateOf(currentId?.toString().orEmpty())
    }
    ExternalMappingEditRow(
        label = "${provider.displayName} episode",
        value = animeIdText,
        onValueChange = { animeIdText = it.filter(Char::isDigit) },
        saveLabel = if (hasManualMapping) "Replace" else "Correct",
        deleteEnabled = hasManualMapping,
        onSave = { onSave(selectedItem, provider, animeIdText) },
        onDelete = { onDelete(selectedItem, provider) },
    )
}

@Composable
private fun ExternalMappingEditRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    saveLabel: String,
    deleteEnabled: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            enabled = value.toLongOrNull()?.let { it > 0 } == true,
            onClick = onSave,
        ) {
            Text(saveLabel)
        }
        Button(
            enabled = deleteEnabled,
            onClick = onDelete,
        ) {
            Text("Remove")
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
    strings: DesktopStrings,
    item: LibraryMediaItem,
    selected: Boolean,
    watchStatus: LibraryWatchStatus?,
    originalSeriesTitle: String?,
    isRefreshingMetadata: Boolean,
    onClick: () -> Unit,
) {
    val metadataReadiness = item.metadataReadiness(strings = strings, isRefreshing = isRefreshingMetadata)
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
        Column(modifier = Modifier.weight(1f)) {
            Text(item.episodeTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            originalSeriesTitle
                ?.takeIf { it.isNotBlank() && it != item.seriesTitle }
                ?.let { Text(strings.fileGroupLabel(it), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(watchStatus.statusLabel(), color = DanmakuColors.TextMuted, maxLines = 1)
            Text(metadataReadiness.shortLabel, color = metadataReadiness.color, maxLines = 1)
        }
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

private fun DesktopDownloadQueueItem.downloadProgressPercent(): Int? =
    totalBytes
        ?.takeIf { it > 0L }
        ?.let { total -> ((positionBytes.coerceAtLeast(0L) * 100L) / total).coerceIn(0L, 100L).toInt() }

private fun DesktopDownloadQueueItem.downloadProgressLabel(): String =
    totalBytes
        ?.let { total -> "${positionBytes.formatLibrarySize()} / ${total.formatLibrarySize()}" }
        ?: positionBytes.formatLibrarySize()

private fun DesktopDownloadQueueItem.isActiveDownload(): Boolean =
    state.equals("active", ignoreCase = true) || state.equals("running", ignoreCase = true)

private fun DesktopDownloadQueueItem.isQueuedDownload(): Boolean =
    state.equals("queued", ignoreCase = true) || state.equals("pending", ignoreCase = true)

private fun DesktopDownloadQueueItem.isCompletedDownload(): Boolean =
    state.equals("completed", ignoreCase = true) || state.equals("done", ignoreCase = true)

private fun DesktopDownloadQueueItem.isFailedDownload(): Boolean =
    failureMessage != null || state.equals("failed", ignoreCase = true)

private fun DesktopDownloadQueueItem.downloadStateColor(): Color =
    when {
        isFailedDownload() -> DanmakuColors.Warning
        isCompletedDownload() -> DanmakuColors.Good
        isActiveDownload() -> DanmakuColors.Accent
        else -> DanmakuColors.TextMuted
    }

private fun openDownloadOutputFolder(item: DesktopDownloadQueueItem): Result<Unit> =
    runCatching {
        require(Desktop.isDesktopSupported()) { "Desktop open is not supported on this system." }
        val desktop = Desktop.getDesktop()
        require(desktop.isSupported(Desktop.Action.OPEN)) { "Desktop open action is not supported on this system." }
        val outputPath = Path.of(item.outputPath)
        val folder = if (Files.isDirectory(outputPath)) {
            outputPath
        } else {
            outputPath.parent ?: outputPath
        }
        require(Files.exists(folder)) { "Output folder does not exist: $folder" }
        desktop.open(folder.toFile())
    }

private fun LibraryMediaItem.primaryPlaybackActionLabel(
    watchStatus: LibraryWatchStatus?,
    strings: DesktopStrings,
): String =
    if (watchStatus?.state == LibraryWatchState.IN_PROGRESS) {
        strings.resumeAction
    } else {
        strings.playAction
    }

private fun LibraryMediaItem.displaySeriesTitle(): String =
    animeMetadata?.displayTitle?.takeIf { it.isNotBlank() } ?: seriesTitle

private fun LibraryMediaItem.localSeriesLabel(strings: DesktopStrings): String? {
    val displayTitle = displaySeriesTitle()
    return seriesTitle
        .takeIf { it.isNotBlank() && it != displayTitle }
        ?.let(strings.fileGroupLabel)
}

private fun LibraryMediaItem.recentlyAddedDetail(strings: DesktopStrings): String =
    if (indexedAtEpochMs > 0) {
        strings.recentlyAddedDetailLabel(indexedAtEpochMs, sizeBytes.formatLibrarySize())
    } else {
        "${mediaType.uppercase()} - ${sizeBytes.formatLibrarySize()}"
    }

private data class LibraryMetadataReadiness(
    val label: String,
    val shortLabel: String,
    val detail: String,
    val color: Color,
    val icon: ImageVector,
)

private fun LibraryMediaItem.metadataReadiness(
    strings: DesktopStrings,
    isRefreshing: Boolean,
    hasPoster: Boolean = posterPath != null,
): LibraryMetadataReadiness {
    val hasMatchedTitle = animeMetadata != null
    return when {
        isRefreshing || metadataStatus == LibraryItemMetadataStatus.LOADING -> LibraryMetadataReadiness(
            label = strings.metadataLoadingLabel,
            shortLabel = strings.loadingLabel,
            detail = strings.metadataLoadingDetail,
            color = DanmakuColors.Accent,
            icon = Icons.Filled.Refresh,
        )
        metadataStatus == LibraryItemMetadataStatus.FAILED -> LibraryMetadataReadiness(
            label = strings.metadataFailedLabel,
            shortLabel = strings.metadataFailedShortLabel,
            detail = strings.metadataFailedDetail,
            color = DanmakuColors.Warning,
            icon = Icons.Filled.Warning,
        )
        hasMatchedTitle && hasPoster -> LibraryMetadataReadiness(
            label = strings.metadataReadyLabel,
            shortLabel = strings.metadataReadyShortLabel,
            detail = strings.metadataReadyDetail,
            color = DanmakuColors.Good,
            icon = Icons.Filled.CheckCircle,
        )
        hasMatchedTitle || hasPoster || metadataStatus == LibraryItemMetadataStatus.READY -> LibraryMetadataReadiness(
            label = strings.metadataPartialLabel,
            shortLabel = strings.partialLabel,
            detail = when {
                hasMatchedTitle && !hasPoster -> strings.metadataPartialMatchedNoPosterDetail
                hasPoster && !hasMatchedTitle -> strings.metadataPartialPosterNoTitleDetail
                else -> strings.metadataPartialGenericDetail
            },
            color = DanmakuColors.Info,
            icon = Icons.Filled.Refresh,
        )
        else -> LibraryMetadataReadiness(
            label = strings.metadataNeededLabel,
            shortLabel = strings.metadataNeededShortLabel,
            detail = strings.metadataNeededDetail,
            color = DanmakuColors.TextMuted,
            icon = Icons.Filled.Refresh,
        )
    }
}

private fun LibrarySeries.metadataReadiness(
    strings: DesktopStrings,
    isRefreshing: Boolean,
    hasPoster: Boolean,
): LibraryMetadataReadiness {
    val items = seasons.flatMap { season -> season.items }
    val hasAnyMatchedTitle = items.any { it.animeMetadata != null }
    val hasAnyFailed = items.any { it.metadataStatus == LibraryItemMetadataStatus.FAILED }
    val allItemsMatched = items.isNotEmpty() && items.all { item ->
        item.animeMetadata != null || item.metadataStatus == LibraryItemMetadataStatus.READY
    }
    return when {
        isRefreshing || items.any { it.metadataStatus == LibraryItemMetadataStatus.LOADING } -> LibraryMetadataReadiness(
            label = strings.metadataLoadingLabel,
            shortLabel = strings.loadingLabel,
            detail = strings.seriesMetadataLoadingDetail,
            color = DanmakuColors.Accent,
            icon = Icons.Filled.Refresh,
        )
        hasAnyFailed -> LibraryMetadataReadiness(
            label = strings.metadataFailedLabel,
            shortLabel = strings.metadataFailedShortLabel,
            detail = strings.seriesMetadataFailedDetail,
            color = DanmakuColors.Warning,
            icon = Icons.Filled.Warning,
        )
        hasPoster && allItemsMatched -> LibraryMetadataReadiness(
            label = strings.metadataReadyLabel,
            shortLabel = strings.metadataReadyShortLabel,
            detail = strings.seriesMetadataReadyDetail,
            color = DanmakuColors.Good,
            icon = Icons.Filled.CheckCircle,
        )
        hasPoster || hasAnyMatchedTitle -> LibraryMetadataReadiness(
            label = strings.metadataPartialLabel,
            shortLabel = strings.partialLabel,
            detail = strings.seriesMetadataPartialDetail,
            color = DanmakuColors.Info,
            icon = Icons.Filled.Refresh,
        )
        else -> LibraryMetadataReadiness(
            label = strings.metadataNeededLabel,
            shortLabel = strings.metadataNeededShortLabel,
            detail = strings.seriesMetadataNeededDetail,
            color = DanmakuColors.TextMuted,
            icon = Icons.Filled.Refresh,
        )
    }
}

@Composable
private fun SeriesPosterCard(
    strings: DesktopStrings,
    series: LibrarySeries,
    coverPath: Path?,
    watchSummary: LibrarySeriesWatchSummary?,
    isSelected: Boolean,
    isPreparing: Boolean,
    isRefreshingMetadata: Boolean,
    onSelect: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onPlay: () -> Unit,
) {
    val metadataReadiness = series.metadataReadiness(
        strings = strings,
        isRefreshing = isRefreshingMetadata,
        hasPoster = coverPath != null,
    )
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
            Text(
                text = metadataReadiness.shortLabel,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(metadataReadiness.color.copy(alpha = 0.86f), RoundedCornerShape(bottomEnd = 4.dp))
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
            text = "${watchSummary.progressLabel()} - ${metadataReadiness.label}",
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onRefreshMetadata,
                enabled = !isPreparing && !isRefreshingMetadata,
                modifier = Modifier.weight(0.46f),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh metadata", modifier = Modifier.size(16.dp))
            }
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) "Loading..." else "Play",
                modifier = Modifier.weight(0.54f),
                enabled = !isPreparing,
                onClick = onPlay,
            )
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

internal fun IndexedLocalLibrary.withExternalAnimeMetadata(
    metadataResolver: DesktopAnimeMetadataResolver,
): IndexedLocalLibrary {
    val displayCatalog = catalog.withExternalAnimeMetadata(metadataResolver)
    return if (displayCatalog == catalog) {
        this
    } else {
        copy(catalog = displayCatalog)
    }
}

internal fun LibraryCatalog.withExternalAnimeMetadata(
    metadataResolver: DesktopAnimeMetadataResolver,
): LibraryCatalog {
    var changed = false
    val displayItems = items.map { item ->
        val animeInfo = metadataResolver.cachedAnimeInfoForItem(item)
        val posterPath = metadataResolver.cachedPosterForItem(item)?.let { "/posters/${item.id}" }
        val metadata = animeInfo?.toLibraryAnimeMetadata()
        val status = when {
            metadata != null || posterPath != null -> LibraryItemMetadataStatus.READY
            else -> LibraryItemMetadataStatus.NOT_AVAILABLE
        }
        if (
            item.animeMetadata == metadata &&
            item.posterPath == posterPath &&
            item.metadataStatus == status
        ) {
            item
        } else {
            changed = true
            item.copy(
                animeMetadata = metadata,
                posterPath = posterPath,
                metadataStatus = status,
            )
        }
    }
    return if (changed) copy(items = displayItems) else this
}

internal fun ExternalAnimeInfo.libraryDisplayTitle(): String =
    titles.chinese ?: titles.primary

private fun ExternalAnimeInfo.toLibraryAnimeMetadata(): LibraryAnimeMetadata =
    LibraryAnimeMetadata(
        animeId = id,
        displayTitle = libraryDisplayTitle(),
        primaryTitle = titles.primary,
        chineseTitle = titles.chinese,
        englishTitle = titles.english,
        japaneseTitle = titles.japanese,
        imageUrl = imageUrl,
        episodeCount = episodeCount,
        startYear = startYear,
    )

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
    strings: DesktopStrings,
    isIndexing: Boolean,
    webhookUrls: List<String>,
    webhookToken: String,
    registeredRoots: List<DesktopLibraryRoot>,
    downloadQueueItems: List<DesktopDownloadQueueItem>,
    onAddAniRssOutputFolder: () -> Unit,
    onRefreshQueue: () -> Unit,
    onRemoveQueueItem: (DesktopDownloadQueueItem) -> Unit,
    onOpenOutputFolder: (DesktopDownloadQueueItem) -> Unit,
) {
    TabScaffold {
        val aniRssRoots = registeredRoots.filter {
            it.provenance == DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER
        }
        val activeDownloads = downloadQueueItems.count(DesktopDownloadQueueItem::isActiveDownload)
        val queuedDownloads = downloadQueueItems.count(DesktopDownloadQueueItem::isQueuedDownload)
        val completedDownloads = downloadQueueItems.count(DesktopDownloadQueueItem::isCompletedDownload)
        val failedDownloads = downloadQueueItems.count(DesktopDownloadQueueItem::isFailedDownload)
        var selectedFilter by remember { mutableStateOf(DownloadQueueFilter.ALL) }
        val filteredDownloadQueueItems = remember(downloadQueueItems, selectedFilter) {
            downloadQueueItems.filter(selectedFilter::matches)
        }
        var selectedDownloadId by remember(downloadQueueItems) {
            mutableStateOf(downloadQueueItems.firstOrNull()?.id)
        }
        val selectedDownload = filteredDownloadQueueItems.firstOrNull { it.id == selectedDownloadId }
            ?: filteredDownloadQueueItems.firstOrNull()
            ?: downloadQueueItems.firstOrNull()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = strings.downloadsActiveTitle,
                value = activeDownloads.toString(),
                caption = strings.downloadsActiveCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.downloadsQueuedTitle,
                value = queuedDownloads.toString(),
                caption = strings.downloadsQueuedCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.downloadsCompletedTitle,
                value = completedDownloads.toString(),
                caption = strings.downloadsCompletedCaption,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = strings.downloadsFailedTitle,
                value = failedDownloads.toString(),
                caption = strings.downloadsFailedCaption,
                modifier = Modifier.weight(1f),
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 1180.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DownloadsQueuePanel(
                        strings = strings,
                        downloadQueueItems = filteredDownloadQueueItems,
                        totalItemCount = downloadQueueItems.size,
                        selectedFilter = selectedFilter,
                        selectedItem = selectedDownload,
                        onFilterChange = { selectedFilter = it },
                        onSelectItem = { selectedDownloadId = it.id },
                        onRefreshQueue = onRefreshQueue,
                        onRemoveQueueItem = onRemoveQueueItem,
                        onOpenOutputFolder = onOpenOutputFolder,
                    )
                    DownloadInspectorPanel(
                        strings = strings,
                        selectedItem = selectedDownload,
                        onRemoveQueueItem = onRemoveQueueItem,
                        onOpenOutputFolder = onOpenOutputFolder,
                    )
                    DownloadsSetupPanel(
                        strings = strings,
                        isIndexing = isIndexing,
                        webhookUrls = webhookUrls,
                        webhookToken = webhookToken,
                        aniRssRoots = aniRssRoots,
                        onAddAniRssOutputFolder = onAddAniRssOutputFolder,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DownloadsQueuePanel(
                        strings = strings,
                        downloadQueueItems = filteredDownloadQueueItems,
                        totalItemCount = downloadQueueItems.size,
                        selectedFilter = selectedFilter,
                        selectedItem = selectedDownload,
                        onFilterChange = { selectedFilter = it },
                        onSelectItem = { selectedDownloadId = it.id },
                        onRefreshQueue = onRefreshQueue,
                        onRemoveQueueItem = onRemoveQueueItem,
                        onOpenOutputFolder = onOpenOutputFolder,
                        modifier = Modifier.weight(1f),
                    )
                    Column(
                        modifier = Modifier.width(360.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DownloadInspectorPanel(
                            strings = strings,
                            selectedItem = selectedDownload,
                            onRemoveQueueItem = onRemoveQueueItem,
                            onOpenOutputFolder = onOpenOutputFolder,
                        )
                        DownloadsSetupPanel(
                            strings = strings,
                            isIndexing = isIndexing,
                            webhookUrls = webhookUrls,
                            webhookToken = webhookToken,
                            aniRssRoots = aniRssRoots,
                            onAddAniRssOutputFolder = onAddAniRssOutputFolder,
                        )
                    }
                }
            }
        }
    }
}

private enum class DownloadQueueFilter(
    val label: String,
) {
    ALL("All"),
    ACTIVE("Active"),
    QUEUED("Queued"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    ;

    fun matches(item: DesktopDownloadQueueItem): Boolean =
        when (this) {
            ALL -> true
            ACTIVE -> item.isActiveDownload()
            QUEUED -> item.isQueuedDownload()
            COMPLETED -> item.isCompletedDownload()
            FAILED -> item.isFailedDownload()
        }
}

@Composable
private fun DownloadsQueuePanel(
    strings: DesktopStrings,
    downloadQueueItems: List<DesktopDownloadQueueItem>,
    totalItemCount: Int,
    selectedFilter: DownloadQueueFilter,
    selectedItem: DesktopDownloadQueueItem?,
    onFilterChange: (DownloadQueueFilter) -> Unit,
    onSelectItem: (DesktopDownloadQueueItem) -> Unit,
    onRefreshQueue: () -> Unit,
    onRemoveQueueItem: (DesktopDownloadQueueItem) -> Unit,
    onOpenOutputFolder: (DesktopDownloadQueueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemoval by remember { mutableStateOf<DesktopDownloadQueueItem?>(null) }
    SectionCard(strings.downloadQueueTitle, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DownloadQueueFilter.entries.forEach { filter ->
                    Button(
                        onClick = { onFilterChange(filter) },
                        enabled = selectedFilter != filter,
                    ) {
                        Text(strings.downloadFilterTitle(filter))
                    }
                }
            }
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = strings.refreshDownloadQueueAction,
                onClick = onRefreshQueue,
            )
        }
        Text(
            strings.downloadQueuePolicyText,
            color = DanmakuColors.TextMuted,
        )
        if (downloadQueueItems.isEmpty()) {
            EmptyState(
                if (totalItemCount == 0) {
                    strings.downloadQueueEmptyText
                } else {
                    strings.downloadQueueFilterEmptyText(strings.downloadFilterTitle(selectedFilter))
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(downloadQueueItems, key = DesktopDownloadQueueItem::id) { item ->
                    DownloadQueueRow(
                        strings = strings,
                        item = item,
                        selected = selectedItem?.id == item.id,
                        onSelect = { onSelectItem(item) },
                        onOpenOutputFolder = { onOpenOutputFolder(item) },
                        onRemoveQueueItem = { pendingRemoval = item },
                    )
                }
            }
        }
    }
    pendingRemoval?.let { item ->
        SettingsConfirmationDialog(
            title = strings.removeDownloadTitle,
            text = strings.removeDownloadText(item.outputPath),
            confirmLabel = strings.removeAction,
            onConfirm = { onRemoveQueueItem(item) },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun DownloadsSetupPanel(
    strings: DesktopStrings,
    isIndexing: Boolean,
    webhookUrls: List<String>,
    webhookToken: String,
    aniRssRoots: List<DesktopLibraryRoot>,
    onAddAniRssOutputFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(strings.downloadSetupAuthorizedSourcesTitle) {
            Text(
                strings.downloadSetupAuthorizedSourcesText,
                color = DanmakuColors.TextMuted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(strings.authorizedImportsOnlyLabel, icon = Icons.Filled.CheckCircle, active = true, color = DanmakuColors.Good)
                StatusPill(strings.queueExecutionPlannedLabel, icon = Icons.Filled.Warning, color = DanmakuColors.Warning)
                StatusPill(strings.importRootCountLabel(aniRssRoots.size), icon = Icons.Filled.FolderOpen)
            }
            LibraryActionButton(
                imageVector = Icons.Filled.FolderOpen,
                label = strings.addAniRssOutputFolderAction,
                enabled = !isIndexing,
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddAniRssOutputFolder,
            )
        }
        SectionCard(strings.aniRssWebhookTitle) {
            if (webhookUrls.isEmpty()) {
                EmptyState(strings.noWebhookUrlText)
            } else {
                webhookUrls.forEach { url ->
                    MetadataRow(strings.webhookUrlLabel, url)
                }
            }
            MetadataRow(strings.webhookHeaderLabel, "X-Danmaku-Webhook-Token")
            MetadataRow(strings.webhookTokenLabel, webhookToken)
        }
        SectionCard(strings.importRootsTitle) {
            if (aniRssRoots.isEmpty()) {
                EmptyState(strings.noAniRssRootsText)
            } else {
                aniRssRoots.forEach { root -> MediaRootRow(root) }
            }
        }
    }
}

@Composable
private fun DownloadQueueRow(
    strings: DesktopStrings,
    item: DesktopDownloadQueueItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenOutputFolder: () -> Unit,
    onRemoveQueueItem: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.AccentSoft else DanmakuColors.SurfaceRaised)
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (item.failureMessage == null) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (item.failureMessage == null) DanmakuColors.TextMuted else DanmakuColors.Warning,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.sourceUri.redactToken(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.outputPath, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.failureMessage?.let {
                Text(it, color = DanmakuColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MiniProgressBar(percent = item.downloadProgressPercent())
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusPill(item.state, color = item.downloadStateColor())
            Text(item.downloadProgressLabel(), color = DanmakuColors.TextMuted, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayerIconButton(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = strings.openOutputFolderAction,
                    onClick = onOpenOutputFolder,
                )
                PlayerIconButton(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = strings.removeQueueItemAction,
                    onClick = onRemoveQueueItem,
                )
            }
        }
    }
}

@Composable
private fun DownloadInspectorPanel(
    strings: DesktopStrings,
    selectedItem: DesktopDownloadQueueItem?,
    onRemoveQueueItem: (DesktopDownloadQueueItem) -> Unit,
    onOpenOutputFolder: (DesktopDownloadQueueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemoval by remember { mutableStateOf<DesktopDownloadQueueItem?>(null) }
    SectionCard(strings.downloadInspectorTitle, modifier = modifier) {
        if (selectedItem == null) {
            EmptyState(strings.downloadInspectorEmptyText)
            return@SectionCard
        }
        Text(selectedItem.sourceUri.redactToken(), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        MetadataRow(strings.stateLabel, selectedItem.state, selectedItem.downloadStateColor())
        MetadataRow(strings.progressLabel, selectedItem.downloadProgressLabel())
        MetadataRow(strings.createdLabel, selectedItem.createdAtEpochMs.formatEpochTime())
        MetadataRow(strings.updatedLabel, selectedItem.updatedAtEpochMs.formatEpochTime())
        MetadataRow(strings.outputLabel, selectedItem.outputPath)
        MetadataRow(strings.sourceLabel, selectedItem.sourceUri.redactToken())
        selectedItem.failureMessage?.let { failure ->
            MetadataRow(strings.failureLabel, failure, DanmakuColors.Warning)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryActionButton(
                imageVector = Icons.Filled.FolderOpen,
                label = strings.openFolderAction,
                onClick = { onOpenOutputFolder(selectedItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Delete,
                label = strings.removeAction,
                onClick = { pendingRemoval = selectedItem },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryActionButton(
                imageVector = Icons.Filled.Pause,
                label = strings.pauseAction,
                enabled = false,
                onClick = {},
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = strings.resumeAction,
                enabled = false,
                onClick = {},
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = strings.retryAction,
                enabled = false,
                onClick = {},
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Delete,
                label = strings.cancelAction,
                enabled = false,
                onClick = {},
            )
        }
        Text(
            strings.downloadExecutionPlannedText,
            color = DanmakuColors.TextMuted,
        )
    }
    pendingRemoval?.let { item ->
        SettingsConfirmationDialog(
            title = strings.removeDownloadTitle,
            text = strings.removeDownloadText(item.outputPath),
            confirmLabel = strings.removeAction,
            onConfirm = { onRemoveQueueItem(item) },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun SettingsSectionRail(
    selectedSection: DesktopSettingsSection,
    strings: DesktopStrings,
    onSectionSelected: (DesktopSettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(DanmakuColors.Surface, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(strings.settingsTitle, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        Text(strings.settingsDescription, color = DanmakuColors.TextMuted, maxLines = 2)
        Divider(color = DanmakuColors.SurfaceRaised)
        DesktopSettingsSection.entries.forEach { section ->
            AppRailItem(
                icon = section.icon,
                label = strings.settingsSectionTitle(section),
                selected = selectedSection == section,
                onClick = { onSectionSelected(section) },
            )
        }
    }
}

private enum class DesktopSettingsSection(
    val title: String,
    val icon: ImageVector,
) {
    GENERAL("General", Icons.Filled.MoreHoriz),
    LIBRARY("Library", Icons.AutoMirrored.Filled.LibraryBooks),
    PLAYBACK("Playback", Icons.Filled.PlayArrow),
    DANMAKU("Danmaku", Icons.Filled.Subtitles),
    PROVIDERS("Providers", Icons.Filled.Refresh),
    SERVER("Server", Icons.Filled.Devices),
    STORAGE("Storage", Icons.Filled.FolderOpen),
    PRIVACY("Privacy", Icons.Filled.Warning),
    DIAGNOSTICS("Diagnostics", Icons.Filled.Computer),
}

@Composable
private fun ProfileTab(
    desktopLanguage: DesktopUiLanguage,
    strings: DesktopStrings,
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    serverBaseUrl: String,
    networkUrls: List<String>,
    pairingToken: String,
    recentServerEvents: List<LocalLibraryServerEvent>,
    appLogPath: Path,
    mpvLogPath: Path,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    danmakuSettings: DanmakuDisplaySettings,
    onSaveDanmakuSettings: (DanmakuDisplaySettings) -> Unit,
    dandanplayCacheEntries: List<DesktopDandanplayCommentCache>,
    onRefreshDandanplayCacheEntries: () -> Unit,
    onDeleteDandanplayCacheEntry: (String) -> Unit,
    onCleanupExpiredDandanplayCaches: () -> Unit,
    onDesktopLanguageChange: (DesktopUiLanguage) -> Unit,
    dandanplaySettings: DandanplayProviderSettings,
    onSaveDandanplaySettings: (String, String?, String?, DandanplayAuthenticationMode, Int) -> Unit,
    onClearDandanplaySettings: () -> Unit,
    dandanplayConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestDandanplayConnection: () -> Unit,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSaveExternalAnimeProviderSettings: (String?, String?, String?, String, String, String?) -> Unit,
    onStartMyAnimeListOAuth: (String?, String?) -> Unit,
    myAnimeListConnectionTestStatus: SettingsConnectionTestStatus?,
    bangumiConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestMyAnimeListConnection: () -> Unit,
    onTestBangumiConnection: () -> Unit,
    onClearMyAnimeListSettings: () -> Unit,
    onClearBangumiSettings: () -> Unit,
    localServerConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestLocalServerConnection: () -> Unit,
) {
    var selectedSection by remember { mutableStateOf(DesktopSettingsSection.GENERAL) }
    TabScaffold {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 1120.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsSectionRail(
                        selectedSection = selectedSection,
                        strings = strings,
                        onSectionSelected = { selectedSection = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingsSectionContent(
                        selectedSection = selectedSection,
                        desktopLanguage = desktopLanguage,
                        strings = strings,
                        mpvRuntimeStatus = mpvRuntimeStatus,
                        videoHostStatus = videoHostStatus,
                        serverBaseUrl = serverBaseUrl,
                        networkUrls = networkUrls,
                        pairingToken = pairingToken,
                        recentServerEvents = recentServerEvents,
                        appLogPath = appLogPath,
                        mpvLogPath = mpvLogPath,
                        diagnosticLog = diagnosticLog,
                        danmakuSettings = danmakuSettings,
                        onSaveDanmakuSettings = onSaveDanmakuSettings,
                        dandanplayCacheEntries = dandanplayCacheEntries,
                        onRefreshDandanplayCacheEntries = onRefreshDandanplayCacheEntries,
                        onDeleteDandanplayCacheEntry = onDeleteDandanplayCacheEntry,
                        onCleanupExpiredDandanplayCaches = onCleanupExpiredDandanplayCaches,
                        onDesktopLanguageChange = onDesktopLanguageChange,
                        dandanplaySettings = dandanplaySettings,
                        onSaveDandanplaySettings = onSaveDandanplaySettings,
                        onClearDandanplaySettings = onClearDandanplaySettings,
                        dandanplayConnectionTestStatus = dandanplayConnectionTestStatus,
                        onTestDandanplayConnection = onTestDandanplayConnection,
                        externalAnimeProviderSettings = externalAnimeProviderSettings,
                        onSaveExternalAnimeProviderSettings = onSaveExternalAnimeProviderSettings,
                        onStartMyAnimeListOAuth = onStartMyAnimeListOAuth,
                        myAnimeListConnectionTestStatus = myAnimeListConnectionTestStatus,
                        bangumiConnectionTestStatus = bangumiConnectionTestStatus,
                        onTestMyAnimeListConnection = onTestMyAnimeListConnection,
                        onTestBangumiConnection = onTestBangumiConnection,
                        onClearMyAnimeListSettings = onClearMyAnimeListSettings,
                        onClearBangumiSettings = onClearBangumiSettings,
                        localServerConnectionTestStatus = localServerConnectionTestStatus,
                        onTestLocalServerConnection = onTestLocalServerConnection,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsSectionRail(
                        selectedSection = selectedSection,
                        strings = strings,
                        onSectionSelected = { selectedSection = it },
                        modifier = Modifier.width(238.dp),
                    )
                    SettingsSectionContent(
                        selectedSection = selectedSection,
                        desktopLanguage = desktopLanguage,
                        strings = strings,
                        mpvRuntimeStatus = mpvRuntimeStatus,
                        videoHostStatus = videoHostStatus,
                        serverBaseUrl = serverBaseUrl,
                        networkUrls = networkUrls,
                        pairingToken = pairingToken,
                        recentServerEvents = recentServerEvents,
                        appLogPath = appLogPath,
                        mpvLogPath = mpvLogPath,
                        diagnosticLog = diagnosticLog,
                        danmakuSettings = danmakuSettings,
                        onSaveDanmakuSettings = onSaveDanmakuSettings,
                        dandanplayCacheEntries = dandanplayCacheEntries,
                        onRefreshDandanplayCacheEntries = onRefreshDandanplayCacheEntries,
                        onDeleteDandanplayCacheEntry = onDeleteDandanplayCacheEntry,
                        onCleanupExpiredDandanplayCaches = onCleanupExpiredDandanplayCaches,
                        onDesktopLanguageChange = onDesktopLanguageChange,
                        dandanplaySettings = dandanplaySettings,
                        onSaveDandanplaySettings = onSaveDandanplaySettings,
                        onClearDandanplaySettings = onClearDandanplaySettings,
                        dandanplayConnectionTestStatus = dandanplayConnectionTestStatus,
                        onTestDandanplayConnection = onTestDandanplayConnection,
                        externalAnimeProviderSettings = externalAnimeProviderSettings,
                        onSaveExternalAnimeProviderSettings = onSaveExternalAnimeProviderSettings,
                        onStartMyAnimeListOAuth = onStartMyAnimeListOAuth,
                        myAnimeListConnectionTestStatus = myAnimeListConnectionTestStatus,
                        bangumiConnectionTestStatus = bangumiConnectionTestStatus,
                        onTestMyAnimeListConnection = onTestMyAnimeListConnection,
                        onTestBangumiConnection = onTestBangumiConnection,
                        onClearMyAnimeListSettings = onClearMyAnimeListSettings,
                        onClearBangumiSettings = onClearBangumiSettings,
                        localServerConnectionTestStatus = localServerConnectionTestStatus,
                        onTestLocalServerConnection = onTestLocalServerConnection,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopLanguageSettingsCard(
    selectedLanguage: DesktopUiLanguage,
    strings: DesktopStrings,
    onLanguageSelected: (DesktopUiLanguage) -> Unit,
) {
    SectionCard(strings.languageTitle) {
        Text(strings.languageDescription, color = DanmakuColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopUiLanguage.entries.forEach { language ->
                Button(
                    onClick = { onLanguageSelected(language) },
                    enabled = selectedLanguage != language,
                ) {
                    Text(language.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionContent(
    selectedSection: DesktopSettingsSection,
    desktopLanguage: DesktopUiLanguage,
    strings: DesktopStrings,
    mpvRuntimeStatus: String,
    videoHostStatus: String,
    serverBaseUrl: String,
    networkUrls: List<String>,
    pairingToken: String,
    recentServerEvents: List<LocalLibraryServerEvent>,
    appLogPath: Path,
    mpvLogPath: Path,
    diagnosticLog: List<DesktopDiagnosticLogEntry>,
    danmakuSettings: DanmakuDisplaySettings,
    onSaveDanmakuSettings: (DanmakuDisplaySettings) -> Unit,
    dandanplayCacheEntries: List<DesktopDandanplayCommentCache>,
    onRefreshDandanplayCacheEntries: () -> Unit,
    onDeleteDandanplayCacheEntry: (String) -> Unit,
    onCleanupExpiredDandanplayCaches: () -> Unit,
    onDesktopLanguageChange: (DesktopUiLanguage) -> Unit,
    dandanplaySettings: DandanplayProviderSettings,
    onSaveDandanplaySettings: (String, String?, String?, DandanplayAuthenticationMode, Int) -> Unit,
    onClearDandanplaySettings: () -> Unit,
    dandanplayConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestDandanplayConnection: () -> Unit,
    externalAnimeProviderSettings: ExternalAnimeProviderSettings,
    onSaveExternalAnimeProviderSettings: (String?, String?, String?, String, String, String?) -> Unit,
    onStartMyAnimeListOAuth: (String?, String?) -> Unit,
    myAnimeListConnectionTestStatus: SettingsConnectionTestStatus?,
    bangumiConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestMyAnimeListConnection: () -> Unit,
    onTestBangumiConnection: () -> Unit,
    onClearMyAnimeListSettings: () -> Unit,
    onClearBangumiSettings: () -> Unit,
    localServerConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestLocalServerConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showServerDashboard by remember { mutableStateOf(false) }
    var showDanmakuCacheManager by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (selectedSection) {
            DesktopSettingsSection.GENERAL -> {
                SectionCard(strings.settingsSectionTitle(DesktopSettingsSection.GENERAL)) {
                    MetadataRow(strings.appLabel, "Danmaku desktop")
                    MetadataRow(strings.primaryTargetsLabel, "Windows desktop, Android mobile/tablet, Android TV")
                    MetadataRow(strings.uiLanguageLabel, desktopLanguage.displayName)
                    MetadataRow(strings.supportedLabel, strings.uiLanguagesValue)
                }
                DesktopLanguageSettingsCard(
                    selectedLanguage = desktopLanguage,
                    strings = strings,
                    onLanguageSelected = onDesktopLanguageChange,
                )
                SectionCard(strings.privacyTitle) {
                    Text(
                        strings.credentialsPrivacyText,
                        color = DanmakuColors.TextMuted,
                    )
                    MetadataRow("MyAnimeList", externalAnimeProviderSettings.myAnimeListStatusText)
                    MetadataRow("Bangumi", externalAnimeProviderSettings.bangumiStatusText)
                    MetadataRow("dandanplay", dandanplaySettings.statusText)
                }
            }
            DesktopSettingsSection.LIBRARY -> {
                SectionCard("Library") {
                    Text(
                        "Library folders, imports, metadata refresh, mapping, and episode preparation are managed from the Library page.",
                        color = DanmakuColors.TextMuted,
                    )
                    MetadataRow("Metadata", "Series and episode refresh actions are available in Library details.")
                    MetadataRow("Imports", "ani-rss output folders are managed from Downloads.")
                }
            }
            DesktopSettingsSection.PLAYBACK -> {
                SectionCard("Playback Runtime") {
                    MetadataRow("mpv executor", mpvRuntimeStatus)
                    MetadataRow("Video host", videoHostStatus)
                    MetadataRow("Renderer", "mpv video output with generated ASS danmaku overlay")
                    MetadataRow("Focus mode", "Player can hide non-video chrome with H")
                }
            }
            DesktopSettingsSection.DANMAKU -> {
                DanmakuDisplaySettingsCard(
                    strings = strings,
                    settings = danmakuSettings,
                    onSave = onSaveDanmakuSettings,
                )
                SectionCard(strings.danmakuCacheSettingsTitle) {
                    Text(
                        strings.danmakuCacheSettingsDescription,
                        color = DanmakuColors.TextMuted,
                    )
                    MetadataRow(strings.cachedEpisodesLabel, dandanplayCacheEntries.size.toString())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryActionButton(
                            imageVector = Icons.Filled.Subtitles,
                            label = strings.openCacheManagerAction,
                            onClick = {
                                onRefreshDandanplayCacheEntries()
                                showDanmakuCacheManager = true
                            },
                        )
                        LibraryActionButton(
                            imageVector = Icons.Filled.Refresh,
                            label = strings.refreshAction,
                            onClick = onRefreshDandanplayCacheEntries,
                        )
                    }
                }
            }
            DesktopSettingsSection.PROVIDERS -> {
                DandanplayProviderCard(
                    strings = strings,
                    settings = dandanplaySettings,
                    onSave = onSaveDandanplaySettings,
                    onClear = onClearDandanplaySettings,
                    connectionTestStatus = dandanplayConnectionTestStatus,
                    onTestConnection = onTestDandanplayConnection,
                    onCleanupExpiredCaches = onCleanupExpiredDandanplayCaches,
                )
                ExternalAnimeProviderSettingsCard(
                    strings = strings,
                    settings = externalAnimeProviderSettings,
                    onSave = onSaveExternalAnimeProviderSettings,
                    onStartMyAnimeListOAuth = onStartMyAnimeListOAuth,
                    myAnimeListConnectionTestStatus = myAnimeListConnectionTestStatus,
                    bangumiConnectionTestStatus = bangumiConnectionTestStatus,
                    onTestMyAnimeListConnection = onTestMyAnimeListConnection,
                    onTestBangumiConnection = onTestBangumiConnection,
                    onClearMyAnimeList = onClearMyAnimeListSettings,
                    onClearBangumi = onClearBangumiSettings,
                )
            }
            DesktopSettingsSection.SERVER -> {
                SectionCard(strings.localServerTitle) {
                    MetadataRow(strings.serverBaseUrlLabel, serverBaseUrl)
                    MetadataRow(strings.pairingCodeLabel, pairingToken)
                    networkUrls.forEach { MetadataRow(strings.lanUrlLabel, it) }
                    MetadataRow(
                        strings.discoveryLabel,
                        "UDP ${app.danmaku.domain.LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT}",
                    )
                    localServerConnectionTestStatus?.let {
                        SettingsConnectionTestStatusRow(strings = strings, label = strings.lastTestLabel, status = it)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryActionButton(
                            imageVector = Icons.Filled.Devices,
                            label = strings.openServerDashboardAction,
                            onClick = { showServerDashboard = true },
                        )
                        LibraryActionButton(
                            imageVector = Icons.Filled.Refresh,
                            label = strings.testLocalServerAction,
                            onClick = onTestLocalServerConnection,
                        )
                    }
                }
            }
            DesktopSettingsSection.STORAGE -> {
                SectionCard("Storage") {
                    MetadataRow("App log", appLogPath.toString())
                    MetadataRow("mpv log", mpvLogPath.toString())
                    Text(
                        "Cache cleanup is available from provider settings. Download destination controls will be enabled when source contracts are implemented.",
                        color = DanmakuColors.TextMuted,
                    )
                }
            }
            DesktopSettingsSection.PRIVACY -> {
                SectionCard("Privacy and Credentials") {
                    Text(
                        "Secrets are masked in forms and diagnostics. Clearing provider settings removes saved provider credentials.",
                        color = DanmakuColors.TextMuted,
                    )
                    MetadataRow("MyAnimeList", externalAnimeProviderSettings.myAnimeListStatusText)
                    MetadataRow("Bangumi", externalAnimeProviderSettings.bangumiStatusText)
                    MetadataRow("dandanplay", dandanplaySettings.statusText)
                }
            }
            DesktopSettingsSection.DIAGNOSTICS -> {
                SectionCard("Desktop Runtime") {
                    MetadataRow("mpv executor", mpvRuntimeStatus)
                    MetadataRow("Video host", videoHostStatus)
                    MetadataRow("App log", appLogPath.toString())
                    MetadataRow("mpv log", mpvLogPath.toString())
                }
                DiagnosticsPanel(diagnosticLog)
            }
        }
    }
    if (showServerDashboard) {
        ServerDashboardDialog(
            strings = strings,
            serverBaseUrl = serverBaseUrl,
            networkUrls = networkUrls,
            pairingToken = pairingToken,
            recentServerEvents = recentServerEvents,
            localServerConnectionTestStatus = localServerConnectionTestStatus,
            onTestLocalServerConnection = onTestLocalServerConnection,
            onDismiss = { showServerDashboard = false },
        )
    }
    if (showDanmakuCacheManager) {
        DanmakuCacheManagerDialog(
            strings = strings,
            cacheEntries = dandanplayCacheEntries,
            cacheMaxAgeDays = dandanplaySettings.cacheMaxAgeDays,
            onRefresh = onRefreshDandanplayCacheEntries,
            onDeleteEntry = onDeleteDandanplayCacheEntry,
            onCleanupExpired = onCleanupExpiredDandanplayCaches,
            onDismiss = { showDanmakuCacheManager = false },
        )
    }
}

@Composable
private fun DanmakuDisplaySettingsCard(
    strings: DesktopStrings,
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
    val opacityError = integerRangeError(opacityText, 0..100, "%")
    val fontScaleError = integerRangeError(fontScaleText, 50..200, "%")
    val speedError = integerRangeError(speedText, 25..300, "%")
    val densityError = integerRangeError(densityText, 10..200, "%")
    val displayAreaError = integerRangeError(displayAreaText, 10..100, "%")
    val offsetError = longRangeError(offsetText, -3_600_000L..3_600_000L, "ms")
    val draftSettings = if (
        opacityError == null &&
        fontScaleError == null &&
        speedError == null &&
        densityError == null &&
        displayAreaError == null &&
        offsetError == null
    ) {
        DanmakuDisplaySettings(
            visible = visible,
            opacityPercent = opacityText.toInt(),
            fontScalePercent = fontScaleText.toInt(),
            speedPercent = speedText.toInt(),
            densityPercent = densityText.toInt(),
            displayAreaPercent = displayAreaText.toInt(),
            offsetMs = offsetText.toLong(),
            keywordFilters = keywordFiltersText.toFilterEntries(),
            regexFilters = regexFiltersText.toFilterEntries(),
        )
    } else {
        null
    }
    val isDirty = draftSettings != null && draftSettings != settings

    SectionCard(strings.danmakuDisplaySettingsTitle) {
        Text(
            strings.danmakuDisplaySettingsDescription,
            color = DanmakuColors.TextMuted,
        )
        MetadataRow(strings.visibilityLabel, if (settings.visible) strings.shownLabel else strings.hiddenLabel)
        MetadataRow(strings.opacityLabel, "${settings.opacityPercent}%")
        MetadataRow(strings.fontScaleLabel, "${settings.fontScalePercent}%")
        MetadataRow(strings.speedLabel, "${settings.speedPercent}%")
        MetadataRow(strings.densityLabel, "${settings.densityPercent}%")
        MetadataRow(strings.displayAreaLabel, "${settings.displayAreaPercent}%")
        MetadataRow(strings.offsetLabel, strings.offsetMsValueLabel(settings.offsetMs))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { visible = true },
                enabled = !visible,
            ) {
                Text(strings.showDanmakuAction)
            }
            Button(
                onClick = { visible = false },
                enabled = visible,
            ) {
                Text(strings.hideDanmakuAction)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = opacityText,
                onValueChange = { opacityText = it },
                label = { Text(strings.opacityPercentLabel) },
                isError = opacityError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = fontScaleText,
                onValueChange = { fontScaleText = it },
                label = { Text(strings.fontScalePercentLabel) },
                isError = fontScaleError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = speedText,
                onValueChange = { speedText = it },
                label = { Text(strings.speedPercentLabel) },
                isError = speedError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        SettingsValidationText(listOfNotNull(opacityError, fontScaleError, speedError))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = densityText,
                onValueChange = { densityText = it },
                label = { Text(strings.densityPercentLabel) },
                isError = densityError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = displayAreaText,
                onValueChange = { displayAreaText = it },
                label = { Text(strings.displayAreaPercentLabel) },
                isError = displayAreaError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = offsetText,
                onValueChange = { offsetText = it },
                label = { Text(strings.offsetMsLabel) },
                isError = offsetError != null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        SettingsValidationText(listOfNotNull(densityError, displayAreaError, offsetError))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = keywordFiltersText,
                onValueChange = { keywordFiltersText = it },
                label = { Text(strings.keywordFiltersLabel) },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = regexFiltersText,
                onValueChange = { regexFiltersText = it },
                label = { Text(strings.regexFiltersLabel) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    draftSettings?.let(onSave)
                },
                enabled = isDirty,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.saveDanmakuDisplayAction)
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
                Text(strings.resetDraftAction)
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
private fun SettingsValidationText(errors: List<String>) {
    if (errors.isEmpty()) {
        return
    }
    Text(
        text = errors.joinToString(separator = "\n"),
        color = DanmakuColors.Warning,
    )
}

@Composable
private fun SettingsConnectionTestStatusRow(
    strings: DesktopStrings,
    label: String,
    status: SettingsConnectionTestStatus,
) {
    val statusLabel = when (status.outcome) {
        SettingsConnectionTestOutcome.TESTING -> strings.testingStatusLabel
        SettingsConnectionTestOutcome.SUCCESS -> strings.okStatusLabel
        SettingsConnectionTestOutcome.FAILURE -> strings.failedStatusLabel
    }
    val color = when (status.outcome) {
        SettingsConnectionTestOutcome.TESTING -> DanmakuColors.Info
        SettingsConnectionTestOutcome.SUCCESS -> DanmakuColors.Good
        SettingsConnectionTestOutcome.FAILURE -> DanmakuColors.Warning
    }
    val icon = when (status.outcome) {
        SettingsConnectionTestOutcome.TESTING -> Icons.Filled.Refresh
        SettingsConnectionTestOutcome.SUCCESS -> Icons.Filled.CheckCircle
        SettingsConnectionTestOutcome.FAILURE -> Icons.Filled.Warning
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = DanmakuColors.TextMuted,
            modifier = Modifier.width(140.dp),
            maxLines = 1,
        )
        StatusPill(
            text = statusLabel,
            icon = icon,
            active = status.outcome != SettingsConnectionTestOutcome.FAILURE,
            color = color,
        )
        Text(
            text = status.detail,
            color = DanmakuColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ServerDashboardDialog(
    strings: DesktopStrings,
    serverBaseUrl: String,
    networkUrls: List<String>,
    pairingToken: String,
    recentServerEvents: List<LocalLibraryServerEvent>,
    localServerConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestLocalServerConnection: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun copyToClipboard(value: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
    val recentEvents = recentServerEvents.takeLast(10).asReversed()

    AlertDialog(
        modifier = Modifier.width(760.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.serverDashboardTitle) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.pairingAndLanAccessTitle, fontWeight = FontWeight.Bold)
                    ServerDashboardCopyRow(
                        strings = strings,
                        label = strings.serverBaseUrlLabel,
                        value = serverBaseUrl,
                        onCopy = { copyToClipboard(serverBaseUrl) },
                    )
                    ServerDashboardCopyRow(
                        strings = strings,
                        label = strings.pairingCodeLabel,
                        value = pairingToken,
                        onCopy = { copyToClipboard(pairingToken) },
                    )
                    if (networkUrls.isEmpty()) {
                        MetadataRow(strings.lanUrlsLabel, strings.noLanUrlDetectedLabel, DanmakuColors.TextMuted)
                    } else {
                        networkUrls.forEachIndexed { index, url ->
                            ServerDashboardCopyRow(
                                strings = strings,
                                label = strings.lanUrlNumberedLabel(index + 1),
                                value = url,
                                onCopy = { copyToClipboard(url) },
                            )
                        }
                    }
                    MetadataRow(
                        strings.discoveryLabel,
                        "UDP ${app.danmaku.domain.LanLibraryServerAnnouncement.DEFAULT_DISCOVERY_PORT}",
                    )
                }

                Divider(color = DanmakuColors.SurfaceRaised)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.healthTitle, fontWeight = FontWeight.Bold)
                    localServerConnectionTestStatus?.let {
                        SettingsConnectionTestStatusRow(strings = strings, label = strings.lastTestLabel, status = it)
                    } ?: MetadataRow(strings.lastTestLabel, strings.notCheckedThisSessionLabel, DanmakuColors.TextMuted)
                    MetadataRow(strings.recentRequestsLabel, recentServerEvents.size.toString())
                    MetadataRow(strings.connectedClientsLabel, strings.connectedClientsPlannedText, DanmakuColors.TextMuted)
                    MetadataRow(strings.bandwidthLabel, strings.bandwidthPlannedText, DanmakuColors.TextMuted)
                }

                Divider(color = DanmakuColors.SurfaceRaised)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.recentServerRequestsTitle, fontWeight = FontWeight.Bold)
                    if (recentEvents.isEmpty()) {
                        Text(strings.noServerRequestsText, color = DanmakuColors.TextMuted)
                    } else {
                        recentEvents.forEach { event ->
                            ServerDashboardEventRow(event)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onTestLocalServerConnection) {
                Text(strings.testServerAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeAction)
            }
        },
    )
}

@Composable
private fun ServerDashboardCopyRow(
    strings: DesktopStrings,
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = DanmakuColors.TextMuted, modifier = Modifier.width(110.dp), maxLines = 1)
        Text(
            value,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LibraryActionButton(
            imageVector = Icons.Filled.ContentCopy,
            label = strings.copyAction,
            onClick = onCopy,
        )
    }
}

@Composable
private fun ServerDashboardEventRow(event: LocalLibraryServerEvent) {
    val isHealthyStatus = event.status in 200..399
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            event.occurredAtEpochMs.formatEpochTime(),
            color = DanmakuColors.TextMuted,
            modifier = Modifier.width(118.dp),
            maxLines = 1,
        )
        StatusPill(
            text = event.status.toString(),
            icon = if (isHealthyStatus) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            active = isHealthyStatus,
            color = if (isHealthyStatus) DanmakuColors.Good else DanmakuColors.Warning,
        )
        Text(
            "${event.method} ${event.path}",
            modifier = Modifier.weight(1.15f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            event.detail.redactToken(),
            color = DanmakuColors.TextMuted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DanmakuCacheManagerDialog(
    strings: DesktopStrings,
    cacheEntries: List<DesktopDandanplayCommentCache>,
    cacheMaxAgeDays: Int,
    onRefresh: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    onCleanupExpired: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMediaId by remember(cacheEntries) {
        mutableStateOf(cacheEntries.firstOrNull()?.mediaId)
    }
    var pendingDeleteEntry by remember { mutableStateOf<DesktopDandanplayCommentCache?>(null) }
    var confirmCleanupExpired by remember { mutableStateOf(false) }
    val selectedEntry = cacheEntries.firstOrNull { it.mediaId == selectedMediaId }
        ?: cacheEntries.firstOrNull()
    val staleCount = cacheEntries.count { it.isExpiredForCacheManager(cacheMaxAgeDays) }

    AlertDialog(
        modifier = Modifier.width(880.dp),
        onDismissRequest = onDismiss,
        title = { Text(strings.danmakuCacheManagerTitle) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(
                        title = strings.cachedSummaryTitle,
                        value = cacheEntries.size.toString(),
                        caption = strings.episodesLabel,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = strings.expiredSummaryTitle,
                        value = staleCount.toString(),
                        caption = strings.cacheDayRuleCaption(cacheMaxAgeDays),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCard(
                        title = strings.commentsSummaryTitle,
                        value = cacheEntries.sumOf { it.commentCountForCacheManager() }.toString(),
                        caption = strings.cachedEventsCaption,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(strings.persistedEntriesTitle, fontWeight = FontWeight.Bold)
                        if (cacheEntries.isEmpty()) {
                            Text(strings.noDandanplayCachesText, color = DanmakuColors.TextMuted)
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 360.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(cacheEntries, key = DesktopDandanplayCommentCache::mediaId) { entry ->
                                    DanmakuCacheEntryRow(
                                        strings = strings,
                                        entry = entry,
                                        selected = selectedEntry?.mediaId == entry.mediaId,
                                        cacheMaxAgeDays = cacheMaxAgeDays,
                                        onSelect = { selectedMediaId = entry.mediaId },
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(strings.selectedCacheTitle, fontWeight = FontWeight.Bold)
                        if (selectedEntry == null) {
                            Text(
                                strings.selectCachePromptText,
                                color = DanmakuColors.TextMuted,
                            )
                        } else {
                            DanmakuCacheEntryDetails(
                                strings = strings,
                                entry = selectedEntry,
                                cacheMaxAgeDays = cacheMaxAgeDays,
                                onDelete = { pendingDeleteEntry = selectedEntry },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onRefresh) {
                Text(strings.refreshAction)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { confirmCleanupExpired = true },
                    enabled = cacheEntries.isNotEmpty(),
                ) {
                    Text(strings.cleanExpiredAction)
                }
                TextButton(onClick = onDismiss) {
                    Text(strings.closeAction)
                }
            }
        },
    )

    pendingDeleteEntry?.let { entry ->
        SettingsConfirmationDialog(
            title = strings.deleteCachedDanmakuTitle,
            text = strings.deleteCachedDanmakuText(entry.displayTitleForCacheManager()),
            confirmLabel = strings.deleteCacheAction,
            cancelLabel = strings.cancelAction,
            onConfirm = { onDeleteEntry(entry.mediaId) },
            onDismiss = { pendingDeleteEntry = null },
        )
    }
    if (confirmCleanupExpired) {
        SettingsConfirmationDialog(
            title = strings.cleanExpiredDanmakuCachesTitle,
            text = strings.cleanExpiredDanmakuCachesText(cacheMaxAgeDays),
            confirmLabel = strings.cleanExpiredAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onCleanupExpired,
            onDismiss = { confirmCleanupExpired = false },
        )
    }
}

@Composable
private fun DanmakuCacheEntryRow(
    strings: DesktopStrings,
    entry: DesktopDandanplayCommentCache,
    selected: Boolean,
    cacheMaxAgeDays: Int,
    onSelect: () -> Unit,
) {
    val isExpired = entry.isExpiredForCacheManager(cacheMaxAgeDays)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) DanmakuColors.AccentSoft else DanmakuColors.SurfaceRaised.copy(alpha = 0.56f))
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill(
            text = if (isExpired) strings.expiredStatusLabel else strings.readyStatusLabel,
            icon = if (isExpired) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            active = !isExpired,
            color = if (isExpired) DanmakuColors.Warning else DanmakuColors.Good,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.displayTitleForCacheManager(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                strings.cacheEntryCommentSummary(
                    entry.commentCountForCacheManager(),
                    entry.fetchedAtEpochMs,
                    entry.fileName,
                ),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DanmakuCacheEntryDetails(
    strings: DesktopStrings,
    entry: DesktopDandanplayCommentCache,
    cacheMaxAgeDays: Int,
    onDelete: () -> Unit,
) {
    val isExpired = entry.isExpiredForCacheManager(cacheMaxAgeDays)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetadataRow(
            strings.statusLabel,
            if (isExpired) strings.expiredStatusLabel else strings.readyStatusLabel,
            if (isExpired) DanmakuColors.Warning else DanmakuColors.Good,
        )
        MetadataRow(strings.animeLabel, entry.animeTitle ?: strings.unknownAnimeLabel)
        MetadataRow(strings.episodeLabel, entry.episodeTitle ?: entry.episodeId?.toString() ?: strings.unknownEpisodeLabel)
        MetadataRow(strings.mediaIdLabel, entry.mediaId)
        MetadataRow(strings.fileLabel, entry.fileName)
        MetadataRow(strings.fileSizeLabel, entry.fileSizeBytes.formatLibrarySize())
        MetadataRow(strings.commentsLabel, entry.commentCountForCacheManager().toString())
        MetadataRow(strings.fetchedLabel, entry.fetchedAtEpochMs.formatEpochTime())
        MetadataRow(strings.shiftLabel, entry.shiftSeconds?.let(strings.shiftSecondsLabel) ?: strings.noneLabel)
        entry.renderedAssPath?.let { MetadataRow(strings.assCacheLabel, it) }
        LibraryActionButton(
            imageVector = Icons.Filled.Delete,
            label = strings.deleteCacheAction,
            onClick = onDelete,
        )
    }
}

@Composable
private fun SettingsConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    cancelLabel: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        },
    )
}

private fun integerRangeError(
    text: String,
    range: IntRange,
    unit: String,
): String? {
    val value = text.trim().toIntOrNull()
        ?: return "Enter a whole number from ${range.first} to ${range.last} $unit."
    return if (value in range) {
        null
    } else {
        "Enter a value from ${range.first} to ${range.last} $unit."
    }
}

private fun longRangeError(
    text: String,
    range: LongRange,
    unit: String,
): String? {
    val value = text.trim().toLongOrNull()
        ?: return "Enter a whole number from ${range.first} to ${range.last} $unit."
    return if (value in range) {
        null
    } else {
        "Enter a value from ${range.first} to ${range.last} $unit."
    }
}

private fun httpUrlError(
    text: String,
    label: String,
): String? {
    val value = text.trim()
    if (value.isEmpty()) {
        return "$label is required."
    }
    val uri = runCatching { java.net.URI(value) }.getOrNull()
        ?: return "$label must be a valid HTTP or HTTPS URL."
    val scheme = uri.scheme?.lowercase()
    return when {
        scheme != "http" && scheme != "https" -> "$label must use HTTP or HTTPS."
        uri.host.isNullOrBlank() -> "$label must include a host."
        else -> null
    }
}

@Composable
private fun DandanplayProviderCard(
    strings: DesktopStrings,
    settings: DandanplayProviderSettings,
    onSave: (String, String?, String?, DandanplayAuthenticationMode, Int) -> Unit,
    onClear: () -> Unit,
    connectionTestStatus: SettingsConnectionTestStatus?,
    onTestConnection: () -> Unit,
    onCleanupExpiredCaches: () -> Unit,
) {
    var baseUrl by remember(settings) { mutableStateOf(settings.baseUrl) }
    var appId by remember(settings) { mutableStateOf(settings.appId.orEmpty()) }
    var appSecret by remember(settings) { mutableStateOf("") }
    var authenticationMode by remember(settings) { mutableStateOf(settings.authenticationMode) }
    var cacheMaxAgeDaysText by remember(settings) { mutableStateOf(settings.cacheMaxAgeDays.toString()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showCleanupConfirm by remember { mutableStateOf(false) }
    val baseUrlError = httpUrlError(baseUrl, strings.apiBaseUrlLabel)
    val cacheMaxAgeDaysError = integerRangeError(cacheMaxAgeDaysText, 1..3650, "days")
    val normalizedAppId = appId.trim().ifEmpty { null }
    val parsedCacheMaxAgeDays = cacheMaxAgeDaysText.toIntOrNull()
    val isDirty = baseUrl.trim() != settings.baseUrl ||
        normalizedAppId != settings.appId ||
        appSecret.isNotBlank() ||
        authenticationMode != settings.authenticationMode ||
        parsedCacheMaxAgeDays != settings.cacheMaxAgeDays
    val canSave = isDirty && baseUrlError == null && cacheMaxAgeDaysError == null

    SectionCard(strings.dandanplayProvidersTitle) {
        Text(
            strings.dandanplayProvidersDescription,
            color = DanmakuColors.TextMuted,
        )
        Spacer(modifier = Modifier.height(10.dp))
        MetadataRow("dandanplay", settings.statusText)
        MetadataRow(strings.cacheExpiryLabel, "${settings.cacheMaxAgeDays} days")
        connectionTestStatus?.let {
            SettingsConnectionTestStatusRow(strings = strings, label = strings.lastTestLabel, status = it)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(strings.apiBaseUrlLabel) },
            modifier = Modifier.fillMaxWidth(),
            isError = baseUrlError != null,
            singleLine = true,
        )
        SettingsValidationText(listOfNotNull(baseUrlError))
        OutlinedTextField(
            value = appId,
            onValueChange = { appId = it },
            label = { Text(strings.appIdOptionalLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = appSecret,
            onValueChange = { appSecret = it },
            label = {
                Text(
                    if (settings.hasAppSecret) {
                        strings.appSecretKeepLabel
                    } else {
                        strings.appSecretOptionalLabel
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
            label = { Text(strings.cacheMaxAgeDaysLabel) },
            modifier = Modifier.fillMaxWidth(),
            isError = cacheMaxAgeDaysError != null,
            singleLine = true,
        )
        SettingsValidationText(listOfNotNull(cacheMaxAgeDaysError))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { authenticationMode = DandanplayAuthenticationMode.SIGNED },
                enabled = authenticationMode != DandanplayAuthenticationMode.SIGNED,
            ) {
                Text(strings.signedAuthAction)
            }
            Button(
                onClick = { authenticationMode = DandanplayAuthenticationMode.CREDENTIAL },
                enabled = authenticationMode != DandanplayAuthenticationMode.CREDENTIAL,
            ) {
                Text(strings.credentialAuthAction)
            }
            Text(
                strings.currentAuthLabel(authenticationMode.name.lowercase()),
                color = DanmakuColors.TextMuted,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        baseUrl.trim(),
                        normalizedAppId,
                        appSecret.takeIf(String::isNotBlank),
                        authenticationMode,
                        parsedCacheMaxAgeDays ?: settings.cacheMaxAgeDays,
                    )
                    appSecret = ""
                },
                enabled = canSave,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.saveDandanplaySettingsAction)
            }
            Button(onClick = onTestConnection) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.testSavedAction)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showClearConfirm = true }) {
                Text(strings.clearAction)
            }
            Button(onClick = { showCleanupConfirm = true }) {
                Text(strings.cleanExpiredCacheAction)
            }
        }
    }
    if (showClearConfirm) {
        SettingsConfirmationDialog(
            title = strings.clearDandanplayTitle,
            text = strings.clearDandanplayText,
            confirmLabel = strings.clearAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onClear,
            onDismiss = { showClearConfirm = false },
        )
    }
    if (showCleanupConfirm) {
        SettingsConfirmationDialog(
            title = strings.cleanExpiredDandanplayTitle,
            text = strings.cleanExpiredDandanplayText,
            confirmLabel = strings.cleanExpiredCacheAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onCleanupExpiredCaches,
            onDismiss = { showCleanupConfirm = false },
        )
    }
}

@Composable
private fun ExternalAnimeProviderSettingsCard(
    strings: DesktopStrings,
    settings: ExternalAnimeProviderSettings,
    onSave: (String?, String?, String?, String, String, String?) -> Unit,
    onStartMyAnimeListOAuth: (String?, String?) -> Unit,
    myAnimeListConnectionTestStatus: SettingsConnectionTestStatus?,
    bangumiConnectionTestStatus: SettingsConnectionTestStatus?,
    onTestMyAnimeListConnection: () -> Unit,
    onTestBangumiConnection: () -> Unit,
    onClearMyAnimeList: () -> Unit,
    onClearBangumi: () -> Unit,
) {
    var myAnimeListClientId by remember(settings) { mutableStateOf(settings.myAnimeListClientId.orEmpty()) }
    var myAnimeListClientSecret by remember(settings) { mutableStateOf("") }
    var myAnimeListAccessToken by remember(settings) { mutableStateOf("") }
    var bangumiBaseUrl by remember(settings) { mutableStateOf(settings.bangumiBaseUrl) }
    var bangumiUserAgent by remember(settings) { mutableStateOf(settings.bangumiUserAgent) }
    var bangumiAccessToken by remember(settings) { mutableStateOf("") }
    var showClearMyAnimeListConfirm by remember { mutableStateOf(false) }
    var showClearBangumiConfirm by remember { mutableStateOf(false) }
    val bangumiBaseUrlError = httpUrlError(bangumiBaseUrl, strings.bangumiApiBaseUrlLabel)
    val bangumiUserAgentError = if (bangumiUserAgent.isBlank()) {
        strings.bangumiUserAgentRequiredError
    } else {
        null
    }
    val normalizedMyAnimeListClientId = myAnimeListClientId.trim().ifEmpty { null }
    val isDirty = normalizedMyAnimeListClientId != settings.myAnimeListClientId ||
        myAnimeListClientSecret.isNotBlank() ||
        myAnimeListAccessToken.isNotBlank() ||
        bangumiBaseUrl.trim() != settings.bangumiBaseUrl ||
        bangumiUserAgent.trim() != settings.bangumiUserAgent ||
        bangumiAccessToken.isNotBlank()
    val canSave = isDirty && bangumiBaseUrlError == null && bangumiUserAgentError == null

    SectionCard(strings.externalAnimeListsTitle) {
        Text(
            strings.externalAnimeListsDescription,
            color = DanmakuColors.TextMuted,
        )
        MetadataRow("MyAnimeList", settings.myAnimeListStatusText)
        MetadataRow("Bangumi", settings.bangumiStatusText)
        myAnimeListConnectionTestStatus?.let {
            SettingsConnectionTestStatusRow(strings = strings, label = strings.myAnimeListTestLabel, status = it)
        }
        bangumiConnectionTestStatus?.let {
            SettingsConnectionTestStatusRow(strings = strings, label = strings.bangumiTestLabel, status = it)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = myAnimeListClientId,
            onValueChange = { myAnimeListClientId = it },
            label = { Text(strings.myAnimeListClientIdLabel) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = myAnimeListAccessToken,
            onValueChange = { myAnimeListAccessToken = it },
            label = {
                Text(
                    if (settings.hasMyAnimeListAccessToken) {
                        strings.myAnimeListAccessTokenKeepLabel
                    } else {
                        strings.myAnimeListAccessTokenOptionalLabel
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = myAnimeListClientSecret,
            onValueChange = { myAnimeListClientSecret = it },
            label = {
                Text(
                    if (settings.hasMyAnimeListClientSecret) {
                        strings.myAnimeListClientSecretKeepLabel
                    } else {
                        strings.myAnimeListClientSecretOptionalLabel
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = bangumiBaseUrl,
                onValueChange = { bangumiBaseUrl = it },
                label = { Text(strings.bangumiApiBaseUrlLabel) },
                modifier = Modifier.weight(1f),
                isError = bangumiBaseUrlError != null,
                singleLine = true,
            )
            OutlinedTextField(
                value = bangumiUserAgent,
                onValueChange = { bangumiUserAgent = it },
                label = { Text(strings.bangumiUserAgentLabel) },
                modifier = Modifier.weight(1f),
                isError = bangumiUserAgentError != null,
                singleLine = true,
            )
        }
        SettingsValidationText(listOfNotNull(bangumiBaseUrlError, bangumiUserAgentError))
        OutlinedTextField(
            value = bangumiAccessToken,
            onValueChange = { bangumiAccessToken = it },
            label = {
                Text(
                    if (settings.hasBangumiAccessToken) {
                        strings.bangumiAccessTokenKeepLabel
                    } else {
                        strings.bangumiAccessTokenOptionalLabel
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        normalizedMyAnimeListClientId,
                        myAnimeListClientSecret.takeIf(String::isNotBlank),
                        myAnimeListAccessToken.takeIf(String::isNotBlank),
                        bangumiBaseUrl.trim(),
                        bangumiUserAgent.trim(),
                        bangumiAccessToken.takeIf(String::isNotBlank),
                    )
                    myAnimeListClientSecret = ""
                    myAnimeListAccessToken = ""
                    bangumiAccessToken = ""
                },
                enabled = canSave,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.saveExternalListsAction)
            }
            Button(
                onClick = {
                    onStartMyAnimeListOAuth(myAnimeListClientId, myAnimeListClientSecret)
                    myAnimeListClientSecret = ""
                },
                enabled = myAnimeListClientId.isNotBlank(),
            ) {
                Text(strings.connectMyAnimeListAction)
            }
            Button(
                onClick = onTestMyAnimeListConnection,
                enabled = settings.myAnimeListClientId != null,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.testMyAnimeListAction)
            }
            Button(onClick = onTestBangumiConnection) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.testBangumiAction)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showClearMyAnimeListConfirm = true }) {
                Text(strings.clearMyAnimeListAction)
            }
            Button(onClick = { showClearBangumiConfirm = true }) {
                Text(strings.clearBangumiAction)
            }
        }
    }
    if (showClearMyAnimeListConfirm) {
        SettingsConfirmationDialog(
            title = strings.clearMyAnimeListTitle,
            text = strings.clearMyAnimeListText,
            confirmLabel = strings.clearMyAnimeListAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onClearMyAnimeList,
            onDismiss = { showClearMyAnimeListConfirm = false },
        )
    }
    if (showClearBangumiConfirm) {
        SettingsConfirmationDialog(
            title = strings.clearBangumiTitle,
            text = strings.clearBangumiText,
            confirmLabel = strings.clearBangumiAction,
            cancelLabel = strings.cancelAction,
            onConfirm = onClearBangumi,
            onDismiss = { showClearBangumiConfirm = false },
        )
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
        shape = RoundedCornerShape(8.dp),
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
        shape = RoundedCornerShape(8.dp),
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
private fun StatusPill(
    text: String,
    icon: ImageVector? = null,
    active: Boolean = false,
    color: Color? = null,
    modifier: Modifier = Modifier,
) {
    val contentColor = color ?: if (active) Color.White else DanmakuColors.TextMuted
    Row(
        modifier = modifier
            .background(if (active) DanmakuColors.AccentSoft else DanmakuColors.SurfaceRaised, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(text = text, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
    strings: DesktopStrings,
    item: LibraryNextUpItem,
    selected: Boolean,
    isRefreshingMetadata: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val metadataReadiness = item.mediaItem.metadataReadiness(strings = strings, isRefreshing = isRefreshingMetadata)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .clickable { onShowDetails(item.mediaItem) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.mediaItem.localSeriesLabel(strings)?.let { label ->
                Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${item.nextUpLabel(strings)} - ${metadataReadiness.label}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            } else {
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            }
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = if (isRefreshingMetadata) {
                    strings.refreshingEpisodeMetadataAction
                } else {
                    strings.refreshEpisodeMetadataAction
                },
                enabled = !isPreparing && !isRefreshingMetadata,
                active = isRefreshingMetadata,
                onClick = { onRefreshEpisodeMetadata(item.mediaItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else if (compact) strings.prepareShortAction else strings.prepareAction,
                enabled = !isPreparing,
                onClick = { onPrepareLocalPlayback(item.mediaItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else item.nextUpActionLabel(strings),
                enabled = !isPreparing,
                onClick = { onPlayLocalPlayback(item.mediaItem) },
            )
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    strings: DesktopStrings,
    item: LibraryPlaybackProgressItem,
    selected: Boolean,
    isRefreshingMetadata: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val metadataReadiness = item.mediaItem.metadataReadiness(strings = strings, isRefreshing = isRefreshingMetadata)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .clickable { onShowDetails(item.mediaItem) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.mediaItem.localSeriesLabel(strings)?.let { label ->
                Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Resume at ${item.progress.positionMs.formatPlaybackTime()} / " +
                    (item.progress.durationMs?.formatPlaybackTime() ?: "unknown") +
                    " - ${metadataReadiness.label}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            } else {
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            }
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = if (isRefreshingMetadata) {
                    strings.refreshingEpisodeMetadataAction
                } else {
                    strings.refreshEpisodeMetadataAction
                },
                enabled = !isPreparing && !isRefreshingMetadata,
                active = isRefreshingMetadata,
                onClick = { onRefreshEpisodeMetadata(item.mediaItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else strings.resumeAction,
                enabled = !isPreparing,
                onClick = { onPlayLocalPlayback(item.mediaItem) },
            )
        }
    }
}

@Composable
private fun RecentlyWatchedRow(
    strings: DesktopStrings,
    item: LibraryPlaybackProgressItem,
    selected: Boolean,
    isRefreshingMetadata: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val metadataReadiness = item.mediaItem.metadataReadiness(strings = strings, isRefreshing = isRefreshingMetadata)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .clickable { onShowDetails(item.mediaItem) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.mediaItem.displaySeriesTitle(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.mediaItem.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.mediaItem.localSeriesLabel(strings)?.let { label ->
                Text(label, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Last seen ${item.progress.updatedAtEpochMs.toDiagnosticTime()} at " +
                    "${item.progress.positionMs.formatPlaybackTime()} / " +
                    (item.progress.durationMs?.formatPlaybackTime() ?: "unknown") +
                    " - ${metadataReadiness.label}",
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (compact) {
                PlayerIconButton(
                    imageVector = Icons.Filled.Subtitles,
                    contentDescription = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            } else {
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.detailsAction,
                    onClick = { onShowDetails(item.mediaItem) },
                )
            }
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = if (isRefreshingMetadata) {
                    strings.refreshingEpisodeMetadataAction
                } else {
                    strings.refreshEpisodeMetadataAction
                },
                enabled = !isPreparing && !isRefreshingMetadata,
                active = isRefreshingMetadata,
                onClick = { onRefreshEpisodeMetadata(item.mediaItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else if (compact) strings.prepareShortAction else strings.prepareAction,
                enabled = !isPreparing,
                onClick = { onPrepareLocalPlayback(item.mediaItem) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else strings.playAction,
                enabled = !isPreparing,
                onClick = { onPlayLocalPlayback(item.mediaItem) },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    strings: DesktopStrings,
    item: LibraryMediaItem,
    selected: Boolean,
    watchStatus: LibraryWatchStatus?,
    isFavorite: Boolean,
    originalSeriesTitle: String?,
    isRefreshingMetadata: Boolean,
    isPreparing: Boolean,
    compact: Boolean,
    onShowDetails: (LibraryMediaItem) -> Unit,
    onSetFavorite: (LibraryMediaItem, Boolean) -> Unit,
    onRefreshEpisodeMetadata: (LibraryMediaItem) -> Unit,
    onPrepareLocalPlayback: (LibraryMediaItem) -> Unit,
    onPlayLocalPlayback: (LibraryMediaItem) -> Unit,
) {
    val metadataReadiness = item.metadataReadiness(strings = strings, isRefreshing = isRefreshingMetadata)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DanmakuColors.SurfaceRaised.copy(alpha = 0.70f) else Color.Transparent)
            .clickable { onShowDetails(item) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.displaySeriesTitle(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(metadataReadiness.shortLabel, color = metadataReadiness.color, maxLines = 1)
            }
            Text(item.episodeTitle, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            (item.localSeriesLabel(strings) ?: originalSeriesTitle
                ?.takeIf { it.isNotBlank() && it != item.displaySeriesTitle() }
                ?.let(strings.fileGroupLabel))
                ?.let { label ->
                    Text(
                        label,
                        color = DanmakuColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            Text(
                listOfNotNull(
                    watchStatus.statusLabel(),
                    strings.favoriteStatusLabel.takeIf { isFavorite },
                    metadataReadiness.label,
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
                    contentDescription = strings.detailsAction,
                    onClick = { onShowDetails(item) },
                )
                PlayerIconButton(
                    imageVector = Icons.Filled.Star,
                    contentDescription = if (isFavorite) strings.unfavoriteAction else strings.favoriteAction,
                    onClick = { onSetFavorite(item, !isFavorite) },
                )
            } else {
                LibraryActionButton(
                    imageVector = Icons.Filled.Subtitles,
                    label = strings.detailsAction,
                    onClick = { onShowDetails(item) },
                )
                LibraryActionButton(
                    imageVector = Icons.Filled.Star,
                    label = if (isFavorite) strings.unfavoriteAction else strings.favoriteAction,
                    onClick = { onSetFavorite(item, !isFavorite) },
                )
            }
            PlayerIconButton(
                imageVector = Icons.Filled.Refresh,
                contentDescription = if (isRefreshingMetadata) {
                    strings.refreshingEpisodeMetadataAction
                } else {
                    strings.refreshEpisodeMetadataAction
                },
                enabled = !isPreparing && !isRefreshingMetadata,
                active = isRefreshingMetadata,
                onClick = { onRefreshEpisodeMetadata(item) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else if (compact) strings.prepareShortAction else strings.prepareAction,
                enabled = !isPreparing,
                onClick = { onPrepareLocalPlayback(item) },
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else strings.playAction,
                enabled = !isPreparing,
                onClick = { onPlayLocalPlayback(item) },
            )
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
    strings: DesktopStrings,
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

    Text(strings.pairedLibraryTitle)
    Text(strings.pairedLibraryDescription)
    OutlinedTextField(
        value = serverUrl,
        onValueChange = {
            serverUrl = it
            selectedPlaybackPreparation = null
        },
        label = { Text(strings.pairedLibraryServerUrlLabel) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = pairingToken,
        onValueChange = {
            pairingToken = it
            selectedPlaybackPreparation = null
        },
        label = { Text(strings.pairingCodeLabel) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Button(
        onClick = ::refreshCatalog,
        enabled = !isLoading,
    ) {
        Text(if (isLoading) strings.loadingAction else strings.loadPairedCatalogAction)
    }
    libraryError?.let { Text(strings.pairedLibraryErrorLabel(it)) }
    Text(strings.pairedEpisodesLabel(totalItems.size))
    MetadataRow(strings.pairedProgressLabel, strings.savedRowsLabel(playbackProgresses.size))
    if (catalog != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.pairedNextUpTitle, fontWeight = FontWeight.Bold)
                if (nextUpItems.isEmpty()) {
                    EmptyState(strings.pairedNextUpEmptyText)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(nextUpItems, key = { it.mediaItem.id }) { item ->
                            RemoteNextUpRow(
                                strings = strings,
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
                Text(strings.pairedContinueWatchingTitle, fontWeight = FontWeight.Bold)
                if (continueWatchingItems.isEmpty()) {
                    EmptyState(strings.pairedContinueWatchingEmptyText)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(continueWatchingItems, key = { it.mediaItem.id }) { item ->
                            RemoteContinueWatchingRow(
                                strings = strings,
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
        label = { Text(strings.searchPairedEpisodesLabel) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { sort = LibraryCatalogSort.TITLE },
            enabled = sort != LibraryCatalogSort.TITLE,
        ) {
            Text(strings.sortTitleAction)
        }
        Button(
            onClick = { sort = LibraryCatalogSort.PATH },
            enabled = sort != LibraryCatalogSort.PATH,
        ) {
            Text(strings.sortPathAction)
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
            Text(if (subtitleFilter == LibrarySubtitleFilter.ANY) strings.requireSubtitlesAction else strings.allEpisodesSliceLabel)
        }
    }
    MetadataRow(strings.showingLabel, strings.pairedEpisodesCountLabel(filteredItems.size, totalItems.size))
    when {
        catalog == null -> EmptyState(strings.pairedCatalogEmptyText)
        totalItems.isEmpty() -> EmptyState(strings.pairedServerEmptyText)
        filteredItems.isEmpty() -> EmptyState(
            text = strings.pairedFilterEmptyText,
            actionLabel = strings.resetFiltersAction,
            onAction = {
                searchText = ""
                sort = LibraryCatalogSort.TITLE
                subtitleFilter = LibrarySubtitleFilter.ANY
            },
        )
        else -> LazyColumn(modifier = Modifier.height(180.dp)) {
            items(filteredItems, key = { it.id }) { item ->
                RemoteEpisodeRow(
                    strings = strings,
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
        Text(strings.preparedDesktopPlaybackLabel(preparation.item.seriesTitle, preparation.item.episodeTitle))
        Text(strings.sourceValueLabel(preparation.source.url.redactToken()))
        Text(strings.resumeValueText(preparation.resumePositionMs?.let { "$it ms" } ?: strings.startFromBeginningLabel))
        Button(
            onClick = {
                onLoadPreparedPlayback(preparation)
            },
        ) {
            Text(strings.loadIntoDesktopControllerAction)
        }
    }
}

@Composable
private fun RemoteNextUpRow(
    strings: DesktopStrings,
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
            Text(item.nextUpLabel(strings), color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else strings.prepareAction,
                enabled = !isPreparing,
                onClick = onPrepareRemotePlayback,
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else item.nextUpActionLabel(strings),
                enabled = !isPreparing,
                onClick = onPlayRemotePlayback,
            )
        }
    }
}

@Composable
private fun RemoteContinueWatchingRow(
    strings: DesktopStrings,
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
                "${strings.resumeAtLabel(item.progress.positionMs)} / " +
                    (item.progress.durationMs?.formatPlaybackTime() ?: strings.unknownDurationLabel),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LibraryActionButton(
            imageVector = Icons.Filled.PlayArrow,
            label = if (isPreparing) strings.loadingAction else strings.resumeAction,
            enabled = !isPreparing,
            onClick = onPlayRemotePlayback,
        )
    }
}

@Composable
private fun RemoteEpisodeRow(
    strings: DesktopStrings,
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
                    item.mediaType.ifBlank { strings.unknownMediaLabel },
                    item.sizeBytes.formatLibrarySize(),
                    if (item.subtitles.isEmpty()) strings.noSubtitlesLabel else strings.subtitleCountLabel(item.subtitles.size),
                ).joinToString(separator = " - "),
                color = DanmakuColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.relativePath, color = DanmakuColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryActionButton(
                imageVector = Icons.Filled.Refresh,
                label = if (isPreparing) strings.preparingAction else strings.prepareAction,
                enabled = !isPreparing,
                onClick = onPrepareRemotePlayback,
            )
            LibraryActionButton(
                imageVector = Icons.Filled.PlayArrow,
                label = if (isPreparing) strings.loadingAction else strings.playAction,
                enabled = !isPreparing,
                onClick = onPlayRemotePlayback,
            )
        }
    }
}

internal fun IndexedLocalLibrary.toPublishedLibrary(
    metadataResolver: DesktopAnimeMetadataResolver? = null,
): PublishedLibrary {
    val publishedCatalog = metadataResolver?.let { catalog.withExternalAnimeMetadata(it) } ?: catalog
    val posterFilesById = metadataResolver
        ?.let { resolver ->
            publishedCatalog.items
                .mapNotNull { item ->
                    item.posterPath?.let { _ ->
                        resolver.cachedPosterForItem(item)?.let { poster -> item.id to poster }
                    }
                }
                .toMap()
        }
        .orEmpty()
    return PublishedLibrary(
        catalog = publishedCatalog,
        filesById = filesById,
        subtitleFilesById = subtitleFilesById,
        posterFilesById = posterFilesById,
    )
}

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

private fun String.escapeHtml(): String =
    buildString(length) {
        this@escapeHtml.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

private fun Long.toDiagnosticTime(): String =
    DIAGNOSTIC_TIME_FORMATTER.format(
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()),
    )

private fun Long.formatEpochTime(): String =
    DIAGNOSTIC_TIME_FORMATTER.format(
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()),
    )

private fun DesktopDandanplayCommentCache.displayTitleForCacheManager(): String =
    listOfNotNull(
        animeTitle?.takeIf(String::isNotBlank),
        episodeTitle?.takeIf(String::isNotBlank),
    ).joinToString(" - ")
        .ifBlank { fileName }

private fun DesktopDandanplayCommentCache.commentCountForCacheManager(): Int =
    runCatching { LocalDanmakuParser.parseNormalizedJson(commentsJson).size }
        .getOrDefault(0)

private fun DesktopDandanplayCommentCache.isExpiredForCacheManager(cacheMaxAgeDays: Int): Boolean =
    System.currentTimeMillis() - fetchedAtEpochMs > cacheMaxAgeDays * 24L * 60L * 60L * 1_000L

private fun DesktopLibraryRootProvenance.displayLabel(strings: DesktopStrings): String =
    when (this) {
        DesktopLibraryRootProvenance.USER_SELECTED -> strings.userSelectedFolderLabel
        DesktopLibraryRootProvenance.ANI_RSS_OUTPUT_FOLDER -> strings.aniRssOutputFolderLabel
    }

private fun DesktopLibraryRootState.displayLabel(strings: DesktopStrings): String =
    when (this) {
        DesktopLibraryRootState.AVAILABLE -> strings.libraryRootAvailableLabel
        DesktopLibraryRootState.MISSING -> strings.libraryRootMissingLabel
    }

private fun Double.formatConfidence(): String =
    "${((coerceIn(0.0, 1.0) * 100.0) + 0.5).toInt()}%"

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

private fun LibraryNextUpItem.nextUpLabel(strings: DesktopStrings): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> progress?.positionMs?.let(strings.resumeAtLabel) ?: strings.resumeSavedPositionLabel
        LibraryNextUpReason.NEXT_EPISODE -> strings.nextAfterLabel(sourceProgress?.positionMs)
        LibraryNextUpReason.START -> strings.startWatchingLibraryLabel
    }

private fun LibraryNextUpItem.nextUpActionLabel(strings: DesktopStrings): String =
    when (reason) {
        LibraryNextUpReason.RESUME -> strings.resumeAction
        LibraryNextUpReason.NEXT_EPISODE,
        LibraryNextUpReason.START -> strings.playAction
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

private const val MAX_SERVER_DASHBOARD_EVENTS = 80

private const val MAX_BACKGROUND_POSTER_REFRESH_SERIES = 32

private const val LOCAL_AUTO_NEXT_SETTING_KEY = "playback.local_auto_next"

private const val DESKTOP_UI_LANGUAGE_SETTING_KEY = "desktop.ui_language"

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
